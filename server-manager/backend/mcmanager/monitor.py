"""
상태 모니터: 몇 초마다 서버 목록 핑 + RCON(list · tps) + 프로세스 CPU · 메모리 → metrics 표에 기록
플레이어 목록이 바뀌면 player_sessions 에 접속 · 퇴장 기록
"""
from __future__ import annotations

import asyncio
import re
import time
from dataclasses import asdict

from . import slp
from .db import DB
from .process import ServerProcess
from .rcon import RconClient, RconError, strip_colors

LIST_RE = re.compile(r"There are (\d+) (?:of a max of|/) ?(\d+) players online:?\s*(.*)", re.S | re.I)
TPS_RE = re.compile(r"(\d+(?:\.\d+)?)")


def parse_list(text: str) -> tuple[int, int, list[str]] | None:
    m = LIST_RE.search(strip_colors(text))
    if not m:
        return None
    names = [n.strip() for n in re.split(r"[,\s]+", m.group(3)) if n.strip()]
    names = [n.split(":")[-1] for n in names]   # 'default: a, b' 같은 그룹 표시 제거
    return int(m.group(1)), int(m.group(2)), names


def parse_tps(text: str) -> float | None:
    t = strip_colors(text)
    if ":" not in t:
        return None
    nums = TPS_RE.findall(t.split(":", 1)[1])
    return min(20.0, float(nums[0])) if nums else None


class Monitor:
    def __init__(self, db: DB, server: ServerProcess, rcon: RconClient, host: str, port: int, every: int, keep_days: int):
        self.db, self.server, self.rcon = db, server, rcon
        self.host, self.port, self.every, self.keep_days = host, port, every, keep_days
        self.snapshot: dict = {"online": False, "players": [], "checked_at": 0}
        self._ps_cache: dict[int, object] = {}
        self._last_names: set[str] | None = None
        self._last_cleanup = 0.0
        self.on_change = None   # async (snapshot) -> None

    def _proc_usage(self) -> tuple[float | None, float | None]:
        pid = self.server.pid
        if not pid:
            return None, None
        try:
            import psutil
            root = psutil.Process(pid)
            procs = [root] + root.children(recursive=True)
            cpu = mem = 0.0
            for p in procs:
                cached = self._ps_cache.setdefault(p.pid, p)
                cpu += cached.cpu_percent(None)
                mem += cached.memory_info().rss
            return round(cpu / max(1, psutil.cpu_count() or 1), 1), round(mem / 1048576, 1)
        except Exception:
            return None, None

    async def sample(self) -> dict:
        st = await slp.ping(self.host, self.port)
        await self.server.refresh_external(st.online)
        names: list[str] = list(st.sample)
        count, mx = st.players_online, st.players_max
        tps = None
        rcon_ok = False
        if st.online and self.rcon.configured:
            try:
                parsed = parse_list(await self.rcon.command("list"))
                if parsed:
                    count, mx, names = parsed
                rcon_ok = True
                tps = parse_tps(await self.rcon.command("tps"))
            except RconError:
                pass
        cpu, mem = self._proc_usage()
        now = int(time.time())
        snap = {
            "checked_at": now, "online": st.online, "latency_ms": st.latency_ms, "version": st.version, "motd": st.motd,
            "players_online": count, "players_max": mx, "players": sorted(names, key=str.lower) if st.online else [],
            "tps": tps, "cpu": cpu, "mem_mb": mem, "rcon": rcon_ok, "error": st.error,
            "state": self.server.state, "managed": self.server.managed, "uptime": self.server.uptime(),
        }
        self.db.x("INSERT INTO metrics (ts, online, players, max_players, tps, latency_ms, cpu, mem_mb) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                  (now, int(st.online), count if st.online else 0, mx, tps, st.latency_ms, cpu, mem))
        self._track_players(set(names) if st.online else set(), now)
        if time.time() - self._last_cleanup > 3600:
            self._last_cleanup = time.time()
            self.db.x("DELETE FROM metrics WHERE ts < ?", (now - self.keep_days * 86400,))
        self.snapshot = snap
        return snap

    def _track_players(self, names: set[str], now: int) -> None:
        if self._last_names is None:   # 이 도구를 켠 직후: 기록상 접속 중인데 지금 없는 사람은 정리
            open_rows = {r["name"] for r in self.db.q("SELECT name FROM player_sessions WHERE left_at IS NULL")}
            for n in open_rows - names:
                self.db.x("UPDATE player_sessions SET left_at = ? WHERE name = ? AND left_at IS NULL", (now, n))
            for n in names - open_rows:
                self.db.x("INSERT INTO player_sessions (name, joined_at) VALUES (?, ?)", (n, now))
            self._last_names = names
            return
        for n in names - self._last_names:
            self.db.x("INSERT INTO player_sessions (name, joined_at) VALUES (?, ?)", (n, now))
        for n in self._last_names - names:
            self.db.x("UPDATE player_sessions SET left_at = ? WHERE name = ? AND left_at IS NULL", (now, n))
        self._last_names = names

    async def run(self) -> None:
        while True:
            try:
                snap = await self.sample()
                if self.on_change:
                    await self.on_change(snap)
            except Exception as e:   # 모니터는 멈추면 안 됨
                self.server.note(f"상태 확인 오류: {e}")
            await asyncio.sleep(self.every)


# ---------------- 기록 조회 (SQL 로 구간 평균) ----------------

RANGES = {"1h": (3600, 30), "6h": (21600, 300), "24h": (86400, 600), "7d": (604800, 3600), "30d": (2592000, 14400)}


def series(db: DB, rng: str) -> list[dict]:
    span, bucket = RANGES.get(rng, RANGES["1h"])
    since = int(time.time()) - span
    return db.q("""
        SELECT (ts / :b) * :b            AS t,
               ROUND(AVG(online) * 100)  AS uptime_pct,
               ROUND(AVG(players), 2)    AS players,
               MAX(players)              AS players_peak,
               ROUND(AVG(tps), 2)        AS tps,
               ROUND(AVG(latency_ms), 1) AS latency_ms,
               ROUND(AVG(cpu), 1)        AS cpu,
               ROUND(AVG(mem_mb))        AS mem_mb
        FROM metrics WHERE ts >= :since
        GROUP BY t ORDER BY t""", {"b": bucket, "since": since})


def summary(db: DB) -> dict:
    now = int(time.time())
    day = db.one("""SELECT ROUND(AVG(online) * 100, 1) AS uptime_pct, MAX(players) AS peak, ROUND(AVG(tps), 2) AS tps
                    FROM metrics WHERE ts >= ?""", (now - 86400,)) or {}
    uniq = db.one("SELECT COUNT(DISTINCT name) AS n FROM player_sessions WHERE joined_at >= ?", (now - 86400,)) or {}
    return {"uptime_24h": day.get("uptime_pct"), "peak_24h": day.get("peak"), "tps_24h": day.get("tps"), "unique_24h": uniq.get("n", 0)}


def recent_players(db: DB, limit: int = 50) -> list[dict]:
    now = int(time.time())
    return db.q("""
        SELECT name,
               MAX(joined_at)                                        AS last_join,
               MAX(CASE WHEN left_at IS NULL THEN 1 ELSE 0 END)      AS online,
               COUNT(*)                                              AS sessions,
               SUM(COALESCE(left_at, :now) - joined_at)              AS playtime
        FROM player_sessions GROUP BY name ORDER BY online DESC, last_join DESC LIMIT :lim""", {"now": now, "lim": limit})
