"""웹 서버: REST API + 실시간 WebSocket + 빌드된 화면(frontend/dist) 제공"""
from __future__ import annotations

import asyncio
import contextlib
import json
import time
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Request, Response, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field

from . import __version__, auth, monitor as mon
from .config import Config, load
from .db import DB
from .process import ServerProcess
from .rcon import RconClient, RconError
from .scheduler import Scheduler, next_run

COOKIE = "mcm_session"


# ---------------- 요청 본문 ----------------

class LoginIn(BaseModel):
    username: str = Field(max_length=64)
    password: str = Field(max_length=256)


class PwIn(BaseModel):
    current: str
    new: str


class CmdIn(BaseModel):
    command: str = Field(min_length=1, max_length=1000)


class PlayerAct(BaseModel):
    reason: str = Field(default="", max_length=200)


class TaskIn(BaseModel):
    name: str = Field(min_length=1, max_length=60)
    kind: str
    payload: str = Field(default="", max_length=2000)
    schedule: str
    at_time: str | None = None
    days: int = 127
    every_min: int | None = None
    run_at: int | None = None
    warn_minutes: int = 0
    enabled: bool = True

    def check(self):
        if self.kind not in ("restart", "stop", "start", "command", "broadcast"):
            raise HTTPException(400, "작업 종류가 올바르지 않습니다")
        if self.schedule not in ("daily", "interval", "once"):
            raise HTTPException(400, "일정 종류가 올바르지 않습니다")
        if self.schedule == "daily":
            try:
                hh, mm = (int(x) for x in (self.at_time or "").split(":"))
                assert 0 <= hh < 24 and 0 <= mm < 60 and 1 <= self.days <= 127
            except (ValueError, AssertionError):
                raise HTTPException(400, "시각은 HH:MM, 요일을 하나 이상 고르세요")
        if self.schedule == "interval" and not (self.every_min and 1 <= self.every_min <= 10080):
            raise HTTPException(400, "간격은 1분 ~ 7일")
        if self.schedule == "once" and not (self.run_at and self.run_at > time.time()):
            raise HTTPException(400, "실행 시각은 지금 이후여야 합니다")
        if self.kind in ("command", "broadcast") and not self.payload.strip():
            raise HTTPException(400, "명령어 · 공지 내용을 입력하세요")
        if not 0 <= self.warn_minutes <= 30:
            raise HTTPException(400, "미리 알림은 0 ~ 30분")


class UserIn(BaseModel):
    username: str
    password: str
    role: str = "viewer"


class RoleIn(BaseModel):
    role: str


class Hub:
    """접속한 화면들에 실시간으로 보냄"""

    def __init__(self):
        self.clients: set[WebSocket] = set()

    async def send(self, kind: str, data) -> None:
        msg = json.dumps({"type": kind, "data": data}, ensure_ascii=False)
        for ws in list(self.clients):
            try:
                await ws.send_text(msg)
            except Exception:
                self.clients.discard(ws)


def create_app(cfg: Config | None = None) -> FastAPI:
    cfg = cfg or load()
    db = DB(cfg.path(cfg.app.db))
    rcon = RconClient(cfg.rcon.host, cfg.rcon.port, cfg.rcon.password)
    hub = Hub()

    async def on_event(kind: str, data: dict) -> None:
        await hub.send(kind, data)

    server = ServerProcess(cfg, rcon, on_event)
    monitor = mon.Monitor(db, server, rcon, cfg.server.host, cfg.server.port, cfg.app.sample_seconds, cfg.app.keep_days)
    scheduler = Scheduler(db, server, rcon, on_event)

    async def on_status(snap: dict) -> None:
        await hub.send("status", snap)

    monitor.on_change = on_status

    @contextlib.asynccontextmanager
    async def lifespan(app: FastAPI):
        tasks = [asyncio.create_task(monitor.run()), asyncio.create_task(scheduler.run())]
        server.note(f"관리 도구 {__version__} 시작 (웹 {cfg.web.host}:{cfg.web.port})")
        try:
            yield
        finally:
            for t in tasks:
                t.cancel()
            await rcon.close()

    app = FastAPI(title="MC Server Manager", version=__version__, lifespan=lifespan, docs_url=None, redoc_url=None)
    app.state.cfg, app.state.db, app.state.server, app.state.monitor, app.state.scheduler, app.state.rcon = cfg, db, server, monitor, scheduler, rcon

    # ---------------- 공통 ----------------

    def client_ip(request: Request | WebSocket) -> str:
        if cfg.web.trusted_proxy:
            fwd = request.headers.get("x-forwarded-for")
            if fwd:
                return fwd.split(",")[0].strip()
        return request.client.host if request.client else "?"

    def current_user(request: Request) -> dict:
        u = auth.session_user(db, request.cookies.get(COOKIE))
        if not u:
            raise HTTPException(401, "로그인이 필요합니다")
        # 상태를 바꾸는 요청은 이 화면에서 보낸 것만 (다른 사이트가 몰래 보내는 요청 막기)
        if request.method not in ("GET", "HEAD", "OPTIONS") and request.headers.get("x-mcm") != "1":
            raise HTTPException(403, "잘못된 요청입니다")
        return u

    def need(role: str):
        def dep(request: Request, u: dict = Depends(current_user)) -> dict:
            if not auth.can(u, role):
                raise HTTPException(403, "권한이 없습니다")
            u = dict(u)
            u["ip"] = client_ip(request)
            return u
        return dep

    viewer, operator, admin = need("viewer"), need("operator"), need("admin")

    def audit(u: dict, action: str, detail: str = "") -> None:
        db.audit(u["username"], u.get("ip"), action, detail)
        asyncio.create_task(hub.send("audit", {"ts": int(time.time()), "username": u["username"], "action": action, "detail": detail}))

    # ---------------- 로그인 ----------------


    @app.post("/api/auth/login")
    async def login(body: LoginIn, request: Request, response: Response):
        ip = client_ip(request)
        wait = auth.blocked(db, ip)
        if wait:
            raise HTTPException(429, f"로그인 시도가 너무 많습니다. {wait // 60 + 1}분 뒤 다시 시도하세요")
        res = await asyncio.to_thread(auth.login, db, body.username.strip(), body.password, ip,
                                      request.headers.get("user-agent", ""), cfg.web.session_days)
        if not res:
            db.audit(body.username[:32], ip, "login.fail")
            raise HTTPException(401, "아이디 또는 비밀번호가 틀렸습니다")
        token, u = res
        response.set_cookie(COOKIE, token, max_age=cfg.web.session_days * 86400, httponly=True,
                            samesite="strict", secure=cfg.web.secure_cookies, path="/")
        db.audit(u["username"], ip, "login", request.headers.get("user-agent", "")[:120])
        return {"username": u["username"], "role": u["role"]}

    @app.post("/api/auth/logout")
    async def logout(request: Request, response: Response, u: dict = Depends(viewer)):
        db.x("UPDATE sessions SET revoked = 1 WHERE id = ?", (u["session_id"],))
        response.delete_cookie(COOKIE, path="/")
        return {"ok": True}

    @app.get("/api/auth/me")
    async def me(u: dict = Depends(viewer)):
        return {"username": u["username"], "role": u["role"], "version": __version__}


    @app.post("/api/auth/password")
    async def change_pw(body: PwIn, u: dict = Depends(viewer)):
        row = db.one("SELECT pw_hash FROM users WHERE id = ?", (u["id"],))
        if not row or not auth.verify_password(body.current, row["pw_hash"]):
            raise HTTPException(400, "지금 비밀번호가 틀렸습니다")
        prob = auth.password_problem(body.new)
        if prob:
            raise HTTPException(400, prob)
        db.x("UPDATE users SET pw_hash = ? WHERE id = ?", (auth.hash_password(body.new), u["id"]))
        db.x("UPDATE sessions SET revoked = 1 WHERE user_id = ? AND id != ?", (u["id"], u["session_id"]))   # 다른 기기는 로그아웃
        audit(u, "password.change")
        return {"ok": True}

    @app.get("/api/auth/sessions")
    async def sessions(u: dict = Depends(viewer)):
        rows = db.q("""SELECT id, created_at, last_seen, ip, user_agent FROM sessions
                       WHERE user_id = ? AND revoked = 0 AND expires_at > ? ORDER BY last_seen DESC""", (u["id"], int(time.time())))
        for r in rows:
            r["current"] = r["id"] == u["session_id"]
        return rows

    @app.delete("/api/auth/sessions/{sid}")
    async def revoke(sid: int, u: dict = Depends(viewer)):
        n = db.x("UPDATE sessions SET revoked = 1 WHERE id = ? AND user_id = ?", (sid, u["id"]))
        if not n:
            raise HTTPException(404, "없는 기기입니다")
        audit(u, "session.revoke", str(sid))
        return {"ok": True}

    # ---------------- 서버 ----------------

    @app.get("/api/status")
    async def status(u: dict = Depends(viewer)):
        snap = dict(monitor.snapshot)
        snap.update(state=server.state, managed=server.managed, uptime=server.uptime(), summary=mon.summary(db),
                    rcon_configured=rcon.configured, server_dir=str(cfg.path(cfg.server.dir)),
                    address=f"{cfg.server.host}:{cfg.server.port}")
        return snap

    async def power(action: str, u: dict):
        fn = {"start": server.start, "stop": server.stop, "restart": server.restart}[action]
        audit(u, f"server.{action}")

        async def run():
            try:
                msg = await fn()
                await hub.send("toast", {"ok": True, "text": msg})
            except Exception as e:
                server.note(f"{action} 실패: {e}")
                await hub.send("toast", {"ok": False, "text": str(e)})
            snap = await monitor.sample()
            await hub.send("status", snap)

        asyncio.create_task(run())
        return {"ok": True, "message": {"start": "켜는 중", "stop": "끄는 중", "restart": "재시작 중"}[action]}

    @app.post("/api/server/{action}")
    async def server_power(action: str, u: dict = Depends(operator)):
        if action not in ("start", "stop", "restart"):
            raise HTTPException(404)
        return await power(action, u)


    @app.post("/api/console")
    async def console(body: CmdIn, u: dict = Depends(operator)):
        cmd = body.command.strip().lstrip("/")
        audit(u, "console", cmd)
        server.note(f"{u['username']}: /{cmd}")
        try:
            out = await rcon.command(cmd)
        except RconError as e:
            if server.managed:
                await server.console(cmd)
                return {"output": "(RCON 연결 실패: 콘솔로 직접 보냈습니다)"}
            raise HTTPException(503, str(e))
        for line in out.splitlines():
            server.note("  " + line)
        return {"output": out}

    @app.get("/api/logs")
    async def logs(u: dict = Depends(viewer), limit: int = 500):
        return list(server.logs)[-min(limit, 3000):]

    # ---------------- 플레이어 ----------------

    @app.get("/api/players")
    async def players(u: dict = Depends(viewer)):
        return {"online": monitor.snapshot.get("players", []), "recent": mon.recent_players(db)}


    @app.post("/api/players/{name}/{act}")
    async def player_action(name: str, act: str, body: PlayerAct, u: dict = Depends(operator)):
        if act not in ("kick", "ban", "pardon", "op", "deop") or not name.replace("_", "").isalnum() or len(name) > 16:
            raise HTTPException(400, "잘못된 요청")
        reason = body.reason.replace("\n", " ").strip()
        cmd = f"{act} {name}" + (f" {reason}" if reason and act in ("kick", "ban") else "")
        try:
            out = await rcon.command(cmd)
        except RconError as e:
            raise HTTPException(503, str(e))
        audit(u, f"player.{act}", f"{name} {reason}".strip())
        asyncio.create_task(monitor.sample())
        return {"output": out}

    # ---------------- 기록 ----------------

    @app.get("/api/metrics")
    async def metrics(range: str = "1h", u: dict = Depends(viewer)):
        return mon.series(db, range)

    @app.get("/api/audit")
    async def audit_log(u: dict = Depends(viewer), limit: int = 200, before: int | None = None):
        if before:
            return db.q("SELECT * FROM audit WHERE id < ? ORDER BY id DESC LIMIT ?", (before, min(limit, 500)))
        return db.q("SELECT * FROM audit ORDER BY id DESC LIMIT ?", (min(limit, 500),))

    # ---------------- 예약 작업 ----------------


    def task_out(t: dict) -> dict:
        t["running"] = t["id"] in scheduler.active
        t["last"] = db.one("SELECT ts, ok, output FROM task_runs WHERE task_id = ? ORDER BY ts DESC LIMIT 1", (t["id"],))
        return t

    @app.get("/api/tasks")
    async def tasks(u: dict = Depends(viewer)):
        return [task_out(t) for t in db.q("SELECT * FROM tasks ORDER BY enabled DESC, next_run IS NULL, next_run")]

    @app.post("/api/tasks")
    async def add_task(body: TaskIn, u: dict = Depends(operator)):
        body.check()
        tid = db.x("""INSERT INTO tasks (name, kind, payload, schedule, at_time, days, every_min, run_at, warn_minutes, enabled, created_by)
                      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                   (body.name, body.kind, body.payload, body.schedule, body.at_time, body.days, body.every_min, body.run_at,
                    body.warn_minutes, int(body.enabled), u["username"]))
        scheduler.refresh(tid)
        audit(u, "task.add", body.name)
        return task_out(db.one("SELECT * FROM tasks WHERE id = ?", (tid,)))

    @app.put("/api/tasks/{tid}")
    async def edit_task(tid: int, body: TaskIn, u: dict = Depends(operator)):
        body.check()
        n = db.x("""UPDATE tasks SET name=?, kind=?, payload=?, schedule=?, at_time=?, days=?, every_min=?, run_at=?, warn_minutes=?, enabled=?
                    WHERE id = ?""", (body.name, body.kind, body.payload, body.schedule, body.at_time, body.days, body.every_min,
                                      body.run_at, body.warn_minutes, int(body.enabled), tid))
        if not n:
            raise HTTPException(404)
        scheduler.refresh(tid)
        audit(u, "task.edit", body.name)
        return task_out(db.one("SELECT * FROM tasks WHERE id = ?", (tid,)))

    @app.delete("/api/tasks/{tid}")
    async def del_task(tid: int, u: dict = Depends(operator)):
        t = db.one("SELECT name FROM tasks WHERE id = ?", (tid,))
        if not t:
            raise HTTPException(404)
        job = scheduler.active.pop(tid, None)
        if job:
            job.cancel()
        db.x("DELETE FROM tasks WHERE id = ?", (tid,))
        audit(u, "task.delete", t["name"])
        return {"ok": True}

    @app.post("/api/tasks/{tid}/run")
    async def run_task(tid: int, u: dict = Depends(operator)):
        t = db.one("SELECT * FROM tasks WHERE id = ?", (tid,))
        if not t:
            raise HTTPException(404)
        audit(u, "task.run", t["name"])
        ok, out = await scheduler.execute(t)
        db.x("INSERT INTO task_runs (task_id, ts, ok, output) VALUES (?, ?, ?, ?)", (tid, int(time.time()), int(ok), out[:4000]))
        return {"ok": ok, "output": out}

    @app.get("/api/tasks/{tid}/runs")
    async def task_runs(tid: int, u: dict = Depends(viewer)):
        return db.q("SELECT ts, ok, output FROM task_runs WHERE task_id = ? ORDER BY ts DESC LIMIT 30", (tid,))

    # ---------------- 사용자 (관리자) ----------------

    @app.get("/api/users")
    async def users(u: dict = Depends(admin)):
        return db.q("""SELECT u.id, u.username, u.role, u.created_at, u.last_login,
                              (SELECT COUNT(*) FROM sessions s WHERE s.user_id = u.id AND s.revoked = 0 AND s.expires_at > strftime('%s','now')) AS devices
                       FROM users u ORDER BY u.id""")


    @app.post("/api/users")
    async def add_user(body: UserIn, u: dict = Depends(admin)):
        try:
            uid = await asyncio.to_thread(auth.create_user, db, body.username, body.password, body.role)
        except ValueError as e:
            raise HTTPException(400, str(e))
        except Exception:
            raise HTTPException(400, "이미 있는 아이디입니다")
        audit(u, "user.add", f"{body.username} ({body.role})")
        return {"id": uid}


    @app.put("/api/users/{uid}")
    async def set_role(uid: int, body: RoleIn, u: dict = Depends(admin)):
        if body.role not in auth.ROLES:
            raise HTTPException(400, "권한이 올바르지 않습니다")
        if uid == u["id"] and body.role != "admin":
            raise HTTPException(400, "자기 자신의 관리자 권한은 뺄 수 없습니다")
        db.x("UPDATE users SET role = ? WHERE id = ?", (body.role, uid))
        audit(u, "user.role", f"{uid} → {body.role}")
        return {"ok": True}

    @app.delete("/api/users/{uid}")
    async def del_user(uid: int, u: dict = Depends(admin)):
        if uid == u["id"]:
            raise HTTPException(400, "자기 자신은 지울 수 없습니다")
        t = db.one("SELECT username FROM users WHERE id = ?", (uid,))
        if not t:
            raise HTTPException(404)
        db.x("DELETE FROM users WHERE id = ?", (uid,))
        audit(u, "user.delete", t["username"])
        return {"ok": True}

    # ---------------- 실시간 ----------------

    @app.websocket("/api/ws")
    async def ws(socket: WebSocket):
        u = auth.session_user(db, socket.cookies.get(COOKIE))
        if not u:
            await socket.close(code=4401)
            return
        await socket.accept()
        hub.clients.add(socket)
        q: asyncio.Queue = asyncio.Queue()
        server.subscribers.add(q)
        try:
            snap = dict(monitor.snapshot)
            snap.update(state=server.state, managed=server.managed, uptime=server.uptime())
            await socket.send_text(json.dumps({"type": "hello", "data": {"status": snap, "logs": list(server.logs)[-400:]}}, ensure_ascii=False))

            async def pump():
                while True:
                    lines = [await q.get()]
                    while not q.empty() and len(lines) < 200:
                        lines.append(q.get_nowait())
                    await socket.send_text(json.dumps({"type": "log", "data": lines}, ensure_ascii=False))

            async def listen():
                while True:
                    await socket.receive_text()   # 연결 유지 (ping)

            done, pending = await asyncio.wait([asyncio.create_task(pump()), asyncio.create_task(listen())], return_when=asyncio.FIRST_COMPLETED)
            for p in pending:
                p.cancel()
        except WebSocketDisconnect:
            pass
        finally:
            hub.clients.discard(socket)
            server.subscribers.discard(q)

    # ---------------- 화면 (빌드된 frontend) ----------------

    dist = Path(__file__).resolve().parents[2] / "frontend" / "dist"

    @app.get("/{path:path}", include_in_schema=False)
    async def spa(path: str):
        if path.startswith("api/"):
            raise HTTPException(404)
        f = (dist / path).resolve()
        if path and f.is_file() and dist.resolve() in f.parents:
            headers = {"Cache-Control": "no-cache"} if f.name in ("sw.js", "index.html", "manifest.webmanifest") else {"Cache-Control": "public, max-age=31536000, immutable"} if "/assets/" in f.as_posix() else {}
            return FileResponse(f, headers=headers)
        index = dist / "index.html"
        if index.exists():
            return FileResponse(index, headers={"Cache-Control": "no-cache"})
        return JSONResponse({"message": "화면이 아직 빌드되지 않았습니다. frontend 에서 npm run build 를 실행하세요."}, 404)

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        resp = await call_next(request)
        resp.headers.setdefault("X-Content-Type-Options", "nosniff")
        resp.headers.setdefault("X-Frame-Options", "DENY")
        resp.headers.setdefault("Referrer-Policy", "same-origin")
        if request.url.path.startswith("/api/"):
            resp.headers.setdefault("Cache-Control", "no-store")
        return resp

    return app
