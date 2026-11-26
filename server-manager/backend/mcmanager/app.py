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

from . import __version__, auth, diagnostics, monitor as mon
from .backup import BackupError, BackupManager
from .files import FileError, Files
from .notify import Notifier, Watchdog
from .wave import Wave
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
        if self.kind not in ("restart", "stop", "start", "command", "broadcast", "backup"):
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


class NoteIn(BaseModel):
    note: str = Field(default="", max_length=200)


class NotifyIn(BaseModel):
    discord_webhook: str | None = None
    events: dict | None = None
    tps_below: float | None = None
    tps_minutes: float | None = None
    disk_below_gb: float | None = None
    down_minutes: float | None = None


class PushSubIn(BaseModel):
    endpoint: str = Field(max_length=1000)
    keys: dict


class FileSaveIn(BaseModel):
    path: str = Field(max_length=500)
    text: str = Field(max_length=1024 * 1024)
    mtime: int | None = None
    force: bool = False


class StageIn(BaseModel):
    op: str
    file: str = Field(max_length=200)


class HotTimeIn(BaseModel):
    action: str
    minutes: int = 60
    multiplier: float = 2.0


class TextIn(BaseModel):
    text: str = Field(min_length=1, max_length=300)


class SeasonIn(BaseModel):
    number: int = Field(ge=1, le=9999)


class OnIn(BaseModel):
    on: bool


class WhitelistIn(BaseModel):
    action: str
    name: str = Field(default="", max_length=16)


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

    notifier = Notifier(cfg, db, None)

    async def on_event(kind: str, data: dict) -> None:
        await hub.send(kind, data)
        if kind == "state" and data.get("state") == "crashed":
            tail = "\n".join(list(server.logs)[-6:])
            await notifier.alert("crash", "서버가 비정상 종료되었습니다", f"종료 코드 {data.get('code')}\n```\n{tail[-900:]}\n```")

    notifier.on_event = lambda k, d: hub.send(k, d)
    server = ServerProcess(cfg, rcon, on_event)
    monitor = mon.Monitor(db, server, rcon, cfg.server.host, cfg.server.port, cfg.app.sample_seconds, cfg.app.keep_days)
    scheduler = Scheduler(db, server, rcon, on_event)
    backups = BackupManager(cfg, db, server, rcon, on_event, notifier.alert)
    scheduler.backups = backups
    files = Files(cfg, db)
    wave = Wave(cfg, rcon)
    monitor.watchdog = Watchdog(notifier)
    monitor.server_dir = cfg.path(cfg.server.dir)

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
    app.state.backups, app.state.files, app.state.wave, app.state.notifier = backups, files, wave, notifier

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
            if auth.blocked(db, ip):
                await notifier.alert("login_blocked", "로그인 실패가 계속되어 잠시 막았습니다", f"IP {ip} · 아이디 '{body.username[:32]}'")
            raise HTTPException(401, "아이디 또는 비밀번호가 틀렸습니다")
        token, u = res
        ua = request.headers.get("user-agent", "")[:300]
        known = db.one("SELECT 1 FROM sessions WHERE user_id = ? AND user_agent = ? AND token_hash != ?", (u["id"], ua, auth.token_hash(token)))
        if not known:
            from .format_ua import device
            await notifier.alert("new_device", f"{u['username']} 님이 새 기기에서 로그인했습니다", f"{device(ua)} · IP {ip}")
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
        asyncio.create_task(notifier.alert("power", f"{u['username']} 님이 서버를 {dict(start='켰습니다', stop='껐습니다', restart='재시작했습니다')[action]}", f"IP {u.get('ip')}"))

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

    # ---------------- 백업 ----------------

    @app.get("/api/backups")
    async def backup_list(u: dict = Depends(viewer)):
        return {"items": backups.list(), "disk": backups.disk(), "busy": backups.busy, "include": cfg.backup.include, "keep": cfg.backup.keep}

    async def run_bg(label: str, coro):
        try:
            res = await coro
            msg = res if isinstance(res, str) else f"{label} 완료"
            await hub.send("toast", {"ok": True, "text": msg})
            if label == "백업" and isinstance(res, dict):
                await notifier.alert("backup_done", "백업 완료", f"{res['file']} · {res['size'] / 1048576:.1f} MB")
        except Exception as e:
            server.note(f"{label} 실패: {e}")
            await hub.send("toast", {"ok": False, "text": f"{label} 실패: {e}"})
        await hub.send("backup", {"phase": "끝", "pct": None})

    @app.post("/api/backups")
    async def backup_create(body: NoteIn, u: dict = Depends(operator)):
        if backups.lock.locked():
            raise HTTPException(409, f"이미 진행 중입니다: {backups.busy}")
        audit(u, "backup.create", body.note)
        asyncio.create_task(run_bg("백업", backups.create("manual", u["username"], body.note)))
        return {"ok": True}

    @app.post("/api/backups/{bid}/restore")
    async def backup_restore(bid: int, u: dict = Depends(admin)):
        try:
            backups.file_of(bid)
        except BackupError as e:
            raise HTTPException(404, str(e))
        if backups.lock.locked():
            raise HTTPException(409, f"이미 진행 중입니다: {backups.busy}")
        audit(u, "backup.restore", f"#{bid}")
        asyncio.create_task(run_bg("복원", backups.restore(bid, u["username"])))
        return {"ok": True}

    @app.delete("/api/backups/{bid}")
    async def backup_delete(bid: int, u: dict = Depends(admin)):
        try:
            backups.delete(bid)
        except BackupError as e:
            raise HTTPException(404, str(e))
        audit(u, "backup.delete", f"#{bid}")
        return {"ok": True}

    @app.get("/api/backups/{bid}/download")
    async def backup_download(bid: int, u: dict = Depends(admin)):
        try:
            p = backups.file_of(bid)
        except BackupError as e:
            raise HTTPException(404, str(e))
        audit(u, "backup.download", f"#{bid}")
        return FileResponse(p, filename=p.name, media_type="application/zip")

    # ---------------- 알림 ----------------

    @app.get("/api/notify")
    async def notify_get(u: dict = Depends(viewer)):
        s = notifier.public_settings()
        s["my_devices"] = db.q("SELECT id, user_agent, created_at FROM push_subs WHERE user_id = ?", (u["id"],))
        s["vapid_public"] = notifier.vapid_public()
        return s

    @app.put("/api/notify")
    async def notify_put(body: NotifyIn, u: dict = Depends(admin)):
        try:
            notifier.save_settings({k: v for k, v in body.model_dump().items() if v is not None})
        except ValueError as e:
            raise HTTPException(400, str(e))
        audit(u, "notify.settings")
        return notifier.public_settings()

    @app.post("/api/notify/test")
    async def notify_test(u: dict = Depends(viewer)):
        await notifier.alert("test", "테스트 알림", f"{u['username']} 님이 보낸 테스트입니다", force=True)
        return {"ok": True}

    @app.post("/api/push/subscribe")
    async def push_sub(body: PushSubIn, request: Request, u: dict = Depends(viewer)):
        if not body.endpoint.startswith("https://") or not body.keys.get("p256dh") or not body.keys.get("auth"):
            raise HTTPException(400, "잘못된 구독 정보")
        db.x("""INSERT INTO push_subs (user_id, endpoint, p256dh, auth, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(endpoint) DO UPDATE SET user_id = excluded.user_id, p256dh = excluded.p256dh, auth = excluded.auth""",
             (u["id"], body.endpoint, body.keys["p256dh"], body.keys["auth"], request.headers.get("user-agent", "")[:300], int(time.time())))
        return {"ok": True}

    @app.post("/api/push/unsubscribe")
    async def push_unsub(body: PushSubIn, u: dict = Depends(viewer)):
        db.x("DELETE FROM push_subs WHERE endpoint = ? AND user_id = ?", (body.endpoint, u["id"]))
        return {"ok": True}

    @app.get("/api/alerts")
    async def alerts(u: dict = Depends(viewer)):
        return db.q("SELECT * FROM alerts ORDER BY id DESC LIMIT 100")

    # ---------------- 파일 (관리자) ----------------

    @app.get("/api/files")
    async def file_tree(u: dict = Depends(admin)):
        return await asyncio.to_thread(files.tree)

    @app.get("/api/files/read")
    async def file_read(path: str, u: dict = Depends(admin)):
        try:
            return files.read(path)
        except FileError as e:
            raise HTTPException(400, str(e))

    @app.post("/api/files/validate")
    async def file_validate(body: FileSaveIn, u: dict = Depends(admin)):
        from .files import validate
        return {"problem": validate(body.path, body.text)}

    @app.put("/api/files")
    async def file_save(body: FileSaveIn, u: dict = Depends(admin)):
        try:
            r = files.save(body.path, body.text, u["username"], body.mtime, body.force)
        except FileError as e:
            raise HTTPException(400, str(e))
        audit(u, "file.save", body.path + (" (형식 검사 무시)" if body.force else ""))
        return r

    @app.get("/api/files/history")
    async def file_hist(path: str, u: dict = Depends(admin)):
        return files.history(path)

    @app.get("/api/files/history/{hid}")
    async def file_hist_one(hid: int, u: dict = Depends(admin)):
        try:
            return files.history_text(hid)
        except FileError as e:
            raise HTTPException(404, str(e))

    # ---------------- 플러그인 ----------------

    @app.get("/api/plugins")
    async def plugin_list(u: dict = Depends(viewer)):
        return await asyncio.to_thread(files.plugins)

    @app.post("/api/plugins/upload")
    async def plugin_upload(filename: str, request: Request, u: dict = Depends(admin)):
        data = await request.body()
        if len(data) > 200 * 1048576:
            raise HTTPException(413, "200MB 보다 큰 파일은 올릴 수 없습니다")
        try:
            item = files.stage_upload(filename, data)
        except FileError as e:
            raise HTTPException(400, str(e))
        audit(u, "plugin.upload", f"{filename} ({len(data) // 1024} KB)")
        return item

    @app.post("/api/plugins/stage")
    async def plugin_stage(body: StageIn, u: dict = Depends(admin)):
        try:
            files.stage(body.op, body.file)
        except FileError as e:
            raise HTTPException(400, str(e))
        return files.plugins()

    @app.delete("/api/plugins/stage")
    async def plugin_unstage(file: str, u: dict = Depends(admin)):
        files.unstage(file)
        return files.plugins()

    @app.post("/api/plugins/apply")
    async def plugin_apply(u: dict = Depends(admin)):
        if not files.plugins()["staged"]:
            raise HTTPException(400, "적용할 변경이 없습니다")
        audit(u, "plugin.apply")

        async def run():
            was = server.state in ("running", "starting") or await server.reachable()
            if was:
                server.note("플러그인 변경을 적용하려고 서버를 끕니다")
                await server.stop()
            done = await asyncio.to_thread(files.apply_staged)
            server.note("플러그인 변경: " + (", ".join(done) or "없음"))
            if was:
                await server.start()
            return f"플러그인 변경 {len(done)}건 적용" + (" · 서버를 다시 켜는 중" if was else "")

        asyncio.create_task(run_bg("플러그인 적용", run()))
        return {"ok": True}

    # ---------------- WaveCore · 점검 · 화이트리스트 ----------------

    @app.get("/api/wave")
    async def wave_overview(u: dict = Depends(viewer)):
        ov = await asyncio.to_thread(wave.overview)
        online = monitor.snapshot.get("online")
        ov["hot_time"]["status"] = await wave.hot_time_status() if online and rcon.configured else {"active": None}
        ov["whitelist"] = await wave.whitelist() if online and rcon.configured else {"on": None, "names": []}
        return ov

    @app.get("/api/wave/player/{name}")
    async def wave_player(name: str, u: dict = Depends(viewer)):
        p = await asyncio.to_thread(wave.player, name)
        if not p:
            raise HTTPException(404, "WaveCore 데이터에 없는 플레이어입니다")
        p["sessions"] = db.q("SELECT joined_at, left_at FROM player_sessions WHERE name = ? ORDER BY joined_at DESC LIMIT 10", (name,))
        return p

    async def wave_cmd(u: dict, action: str, cmd: str) -> dict:
        try:
            out = await wave.cmd(cmd)
        except RuntimeError as e:
            raise HTTPException(503, str(e))
        audit(u, action, cmd)
        return {"output": out}

    @app.post("/api/wave/hottime")
    async def wave_hot(body: HotTimeIn, u: dict = Depends(operator)):
        if body.action == "start":
            if not (1 <= body.minutes <= 1440 and 1 <= body.multiplier <= 10):
                raise HTTPException(400, "시간은 1~1440분, 배율은 1~10")
            return await wave_cmd(u, "wave.hottime", f"핫타임 시작 {body.minutes} {body.multiplier:g}")
        if body.action == "stop":
            return await wave_cmd(u, "wave.hottime", "핫타임 종료")
        raise HTTPException(400, "잘못된 요청")

    @app.post("/api/wave/broadcast")
    async def wave_broadcast(body: TextIn, u: dict = Depends(operator)):
        await scheduler.say(body.text.replace("\n", " "), "white")
        audit(u, "broadcast", body.text)
        return {"ok": True}

    @app.post("/api/wave/season")
    async def wave_season(body: SeasonIn, u: dict = Depends(admin)):
        if not (wave.base / "config.yml").exists():
            raise HTTPException(404, "WaveCore config.yml 이 없습니다")
        await asyncio.to_thread(wave.set_season, body.number)
        audit(u, "wave.season", str(body.number))
        reloaded = False
        if monitor.snapshot.get("online"):
            try:
                await wave.cmd("wave reload")
                reloaded = True
            except RuntimeError:
                pass
        return {"ok": True, "reloaded": reloaded}

    @app.post("/api/wave/maintenance")
    async def wave_maint(body: OnIn, u: dict = Depends(operator)):
        return await wave_cmd(u, "wave.maintenance", "점검모드 " + ("켜기" if body.on else "끄기"))

    @app.post("/api/whitelist")
    async def whitelist(body: WhitelistIn, u: dict = Depends(operator)):
        if body.action in ("on", "off"):
            return await wave_cmd(u, "whitelist", f"whitelist {body.action}")
        if body.action in ("add", "remove") and body.name.replace("_", "").isalnum():
            return await wave_cmd(u, "whitelist", f"whitelist {body.action} {body.name}")
        raise HTTPException(400, "잘못된 요청")

    # ---------------- 진단 ----------------

    @app.get("/api/diagnostics")
    async def diag(u: dict = Depends(viewer)):
        def build():
            lines = list(server.logs)
            log = cfg.path(cfg.server.dir) / cfg.server.log_file
            if not server.managed and log.exists():
                try:
                    with open(log, "rb") as f:
                        f.seek(max(0, log.stat().st_size - 400_000))
                        lines = f.read().decode("utf-8", "replace").splitlines()
                except OSError:
                    pass
            plugins = files.plugins()["active"]
            return {"system": diagnostics.system(cfg.path(cfg.server.dir)), "heatmap": diagnostics.heatmap(db),
                    "errors": diagnostics.errors(lines, plugins), "crashes": diagnostics.crash_reports(cfg.path(cfg.server.dir))}
        return await asyncio.to_thread(build)

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
