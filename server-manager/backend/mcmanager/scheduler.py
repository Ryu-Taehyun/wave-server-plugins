"""
예약 작업: 매일 정해진 시각 · N분마다 · 한 번
재시작 · 끄기는 warn_minutes 전부터 게임 안에 카운트다운 공지 (5분 · 1분 · 30초 · 10초 …)
"""
from __future__ import annotations

import asyncio
import datetime as dt
import json
import time

from .db import DB
from .process import ServerProcess
from .rcon import RconClient, RconError

WARN_POINTS = [1800, 900, 600, 300, 120, 60, 30, 10, 5, 3, 2, 1]


def next_run(task: dict, after: float | None = None) -> int | None:
    now = dt.datetime.fromtimestamp(after if after is not None else time.time())
    if task["schedule"] == "once":
        return task["run_at"] if task.get("run_at") and task["run_at"] > now.timestamp() else None
    if task["schedule"] == "interval":
        m = max(1, int(task.get("every_min") or 60))
        base = task.get("last_run") or int(now.timestamp())
        nxt = base + m * 60
        while nxt <= now.timestamp():
            nxt += m * 60
        return int(nxt)
    hh, mm = (int(x) for x in (task.get("at_time") or "04:00").split(":"))
    days = int(task.get("days") or 127) or 127
    for add in range(0, 8):
        d = (now + dt.timedelta(days=add)).replace(hour=hh, minute=mm, second=0, microsecond=0)
        if d > now and days & (1 << d.weekday()):
            return int(d.timestamp())
    return None


def describe_left(sec: int) -> str:
    if sec >= 60:
        return f"{sec // 60}분"
    return f"{sec}초"


class Scheduler:
    def __init__(self, db: DB, server: ServerProcess, rcon: RconClient, on_event=None):
        self.db, self.server, self.rcon, self.on_event = db, server, rcon, on_event
        self.active: dict[int, asyncio.Task] = {}

    def refresh(self, task_id: int | None = None) -> None:
        rows = self.db.q("SELECT * FROM tasks" + (" WHERE id = ?" if task_id else ""), (task_id,) if task_id else ())
        for t in rows:
            nr = next_run(t) if t["enabled"] else None
            self.db.x("UPDATE tasks SET next_run = ? WHERE id = ?", (nr, t["id"]))
            job = self.active.pop(t["id"], None)
            if job and not job.done():
                job.cancel()

    async def say(self, text: str, color: str = "gold") -> None:
        msg = json.dumps([{"text": "[서버] ", "color": "yellow", "bold": True}, {"text": text, "color": color}], ensure_ascii=False)
        try:
            await self.rcon.command(f"tellraw @a {msg}")
        except RconError:
            try:
                await self.rcon.command(f"say {text}")
            except RconError:
                pass

    async def execute(self, t: dict, by: str = "예약") -> tuple[bool, str]:
        kind, payload = t["kind"], t.get("payload") or ""
        try:
            if kind == "restart":
                return True, await self.server.restart()
            if kind == "stop":
                return True, await self.server.stop()
            if kind == "start":
                return True, await self.server.start()
            if kind == "broadcast":
                await self.say(payload, "white")
                return True, "공지했습니다"
            if kind == "command":
                outs = []
                for line in [l.strip() for l in payload.splitlines() if l.strip()]:
                    outs.append(await self.rcon.command(line.lstrip("/")))
                return True, "\n".join(o for o in outs if o) or "실행했습니다"
            return False, f"알 수 없는 작업 {kind}"
        except Exception as e:
            return False, str(e)

    async def _job(self, t: dict) -> None:
        at = t["next_run"]
        warn = int(t.get("warn_minutes") or 0) * 60 if t["kind"] in ("restart", "stop") else 0
        verb = "재시작됩니다" if t["kind"] == "restart" else "꺼집니다"
        try:
            for p in [w for w in WARN_POINTS if w <= warn] if warn else []:
                wait = at - p - time.time()
                if wait < -1:
                    continue
                await asyncio.sleep(max(0, wait))
                await self.say(f"서버가 {describe_left(p)} 뒤 {verb}.", "red" if p <= 60 else "gold")
            await asyncio.sleep(max(0, at - time.time()))
            self.server.note(f"예약 작업 실행: {t['name']}")
            ok, out = await self.execute(t)
            now = int(time.time())
            self.db.x("INSERT INTO task_runs (task_id, ts, ok, output) VALUES (?, ?, ?, ?)", (t["id"], now, int(ok), out[:4000]))
            self.db.audit("예약", None, f"task.{t['kind']}", f"{t['name']}: {'성공' if ok else '실패'} {out[:200]}")
            cur = self.db.one("SELECT * FROM tasks WHERE id = ?", (t["id"],))
            if cur:
                cur["last_run"] = now
                nr = next_run(cur, after=now + 1)
                self.db.x("UPDATE tasks SET last_run = ?, next_run = ?, enabled = ? WHERE id = ?",
                          (now, nr, 0 if cur["schedule"] == "once" else cur["enabled"], t["id"]))
            if self.on_event:
                await self.on_event("task", {"id": t["id"], "name": t["name"], "ok": ok, "output": out[:500]})
        except asyncio.CancelledError:
            pass
        finally:
            self.active.pop(t["id"], None)

    async def run(self) -> None:
        self.refresh()
        while True:
            now = time.time()
            for t in self.db.q("SELECT * FROM tasks WHERE enabled = 1 AND next_run IS NOT NULL"):
                if t["id"] in self.active:
                    continue
                lead = (int(t.get("warn_minutes") or 0) * 60 if t["kind"] in ("restart", "stop") else 0) + 2
                if now >= t["next_run"] - lead:
                    self.active[t["id"]] = asyncio.create_task(self._job(t))
            await asyncio.sleep(1)
