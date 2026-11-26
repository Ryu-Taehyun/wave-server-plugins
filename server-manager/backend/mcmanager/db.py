"""SQLite 접근 (스레드마다 연결, 쓰기는 잠금)"""
from __future__ import annotations

import sqlite3
import threading
import time
from importlib import resources
from pathlib import Path


class DB:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.path = str(path)
        self._local = threading.local()
        self._wlock = threading.Lock()
        schema = resources.files("mcmanager").joinpath("schema.sql").read_text(encoding="utf-8")
        with self._wlock:
            self.conn.executescript(schema)

    @property
    def conn(self) -> sqlite3.Connection:
        c = getattr(self._local, "conn", None)
        if c is None:
            c = sqlite3.connect(self.path, timeout=10, check_same_thread=False)
            c.row_factory = sqlite3.Row
            c.execute("PRAGMA foreign_keys = ON")
            self._local.conn = c
        return c

    def q(self, sql: str, args=()) -> list[dict]:
        return [dict(r) for r in self.conn.execute(sql, args).fetchall()]

    def one(self, sql: str, args=()) -> dict | None:
        r = self.conn.execute(sql, args).fetchone()
        return dict(r) if r else None

    def x(self, sql: str, args=()) -> int:
        """쓰기. 새 행 ID (또는 바뀐 행 수)"""
        with self._wlock:
            cur = self.conn.execute(sql, args)
            self.conn.commit()
            return cur.lastrowid if sql.lstrip().upper().startswith("INSERT") else cur.rowcount

    def audit(self, user: str | None, ip: str | None, action: str, detail: str = "") -> None:
        self.x("INSERT INTO audit (ts, username, ip, action, detail) VALUES (?, ?, ?, ?, ?)",
               (int(time.time()), user, ip, action, detail[:2000]))
