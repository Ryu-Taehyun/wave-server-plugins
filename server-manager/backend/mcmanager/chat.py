"""
실시간 채팅: 서버 로그에서 채팅 줄을 골라 SQL 에 기록, 금지어 · 광고 감지
지원 형식
- WaveCore:  [Local] 팀 | [칭호] - 닉네임 : 메시지   (채널 · 팀 · 칭호는 있을 수도 없을 수도)
- 바닐라:    <닉네임> 메시지
- 귓속말:    닉네임 issued server command: /귓 받는사람 메시지  (/w /msg /tell 도)
- 신고:      닉네임 issued server command: /report 대상 사유
"""
from __future__ import annotations

import json
import re
import time
import unicodedata

from .db import DB

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
TIME = re.compile(r"^\[\d\d:\d\d:\d\d\] \[[^\]]+/[A-Z]+\]:?\s*|^\[\d\d:\d\d:\d\d(?: [A-Z]+)?\]:?\s*")
WAVE = re.compile(r"^(?:\[(Local|TEAM)\]\s*)?.*?(?:^|\s)-\s([A-Za-z0-9_]{3,16})\s:\s(.*)$")
VANILLA = re.compile(r"^(?:\[Not Secure\]\s*)?<([A-Za-z0-9_]{3,16})>\s(.*)$")
CMD = re.compile(r"^([A-Za-z0-9_]{3,16}) issued server command: /(\S+)\s*(.*)$")
WHISPER_CMDS = {"귓", "귓속말", "w", "msg", "tell", "whisper", "m", "r", "reply", "답장"}
AD = re.compile(r"(\b\d{1,3}(?:\.\d{1,3}){3}\b)|(\b[a-z0-9-]+\.(?:kr|com|net|gg|xyz|me|io|org|co|so|kro\.kr|r-e\.kr)\b)", re.I)


def clean(line: str) -> str:
    return TIME.sub("", ANSI.sub("", line)).strip()


def normalize(s: str) -> str:
    """띄어쓰기 · 기호를 빼서 '바 보' 같은 우회도 잡음"""
    s = unicodedata.normalize("NFKC", s).lower()
    return re.sub(r"[\s\W_]+", "", s)


class Chat:
    def __init__(self, db: DB, on_event=None, on_alert=None, own_domains: list[str] | None = None):
        self.db, self.on_event, self.on_alert = db, on_event, on_alert
        self.own_domains = [d.lower() for d in (own_domains or [])]

    # ---------------- 설정 ----------------

    def settings(self) -> dict:
        raw = json.loads(self.db.setting("chat", "{}") or "{}")
        return {"words": raw.get("words", []), "block_ads": raw.get("block_ads", True), "allow_domains": raw.get("allow_domains", [])}

    def save_settings(self, data: dict) -> dict:
        cur = self.settings()
        if "words" in data:
            cur["words"] = sorted({w.strip() for w in data["words"] if w and w.strip()})[:500]
        if "block_ads" in data:
            cur["block_ads"] = bool(data["block_ads"])
        if "allow_domains" in data:
            cur["allow_domains"] = sorted({d.strip().lower() for d in data["allow_domains"] if d and d.strip()})[:100]
        self.db.set_setting("chat", json.dumps(cur, ensure_ascii=False))
        return cur

    def check(self, message: str) -> str | None:
        s = self.settings()
        norm = normalize(message)
        for w in s["words"]:
            if normalize(w) and normalize(w) in norm:
                return f"금지어: {w}"
        if s["block_ads"]:
            for m in AD.finditer(message):
                hit = (m.group(0) or "").lower()
                if not any(hit.endswith(d) for d in s["allow_domains"] + self.own_domains):
                    return f"광고 의심: {hit}"
        return None

    # ---------------- 로그 한 줄 ----------------

    def parse(self, line: str) -> dict | None:
        s = clean(line)
        if not s:
            return None
        m = CMD.match(s)
        if m:
            name, cmd, rest = m.group(1), m.group(2).lower().split(":")[-1], m.group(3)
            if cmd in WHISPER_CMDS:
                parts = rest.split(" ", 1)
                if cmd in ("r", "reply", "답장"):
                    return {"kind": "chat", "name": name, "channel": "whisper", "to_name": None, "message": rest}
                if len(parts) == 2:
                    return {"kind": "chat", "name": name, "channel": "whisper", "to_name": parts[0], "message": parts[1]}
            if cmd in ("report", "신고"):
                parts = rest.split(" ", 1)
                return {"kind": "report", "reporter": name, "target": parts[0] if parts and parts[0] else None, "reason": parts[1] if len(parts) > 1 else ""}
            return None
        m = VANILLA.match(s)
        if m:
            return {"kind": "chat", "name": m.group(1), "channel": "global", "to_name": None, "message": m.group(2)}
        if (" - " in s or s.startswith("- ")) and " : " in s:
            m = WAVE.match(s)
            if m:
                ch = {"Local": "local", "TEAM": "team"}.get(m.group(1) or "", "global")
                return {"kind": "chat", "name": m.group(2), "channel": ch, "to_name": None, "message": m.group(3)}
        return None

    async def feed(self, line: str) -> None:
        p = self.parse(line)
        if not p:
            return
        now = int(time.time())
        if p["kind"] == "report":
            rid = self.db.x("INSERT INTO reports (ts, reporter, target, reason) VALUES (?, ?, ?, ?)", (now, p["reporter"], p["target"], (p["reason"] or "")[:500]))
            if self.on_event:
                await self.on_event("report", {"id": rid, "reporter": p["reporter"], "target": p["target"], "reason": p["reason"]})
            if self.on_alert:
                await self.on_alert("report", f"신고: {p['reporter']} → {p['target'] or '-'}", p["reason"] or "")
            return
        flag = self.check(p["message"])
        cid = self.db.x("INSERT INTO chat (ts, name, channel, to_name, message, flagged) VALUES (?, ?, ?, ?, ?, ?)",
                        (now, p["name"], p["channel"], p["to_name"], p["message"][:1000], flag))
        row = {"id": cid, "ts": now, **{k: p[k] for k in ("name", "channel", "to_name", "message")}, "flagged": flag}
        if self.on_event:
            await self.on_event("chat", row)
        if flag and self.on_alert:
            await self.on_alert("chat_flag", f"채팅 감지 · {p['name']}", f"{flag}\n> {p['message'][:300]}")

    def add_admin(self, username: str, message: str) -> dict:
        now = int(time.time())
        cid = self.db.x("INSERT INTO chat (ts, name, channel, message) VALUES (?, ?, 'admin', ?)", (now, username, message))
        return {"id": cid, "ts": now, "name": username, "channel": "admin", "to_name": None, "message": message, "flagged": None}

    def search(self, q: str = "", name: str = "", flagged: bool = False, before: int | None = None, limit: int = 200) -> list[dict]:
        sql, args = "SELECT * FROM chat WHERE 1=1", []
        if q:
            sql += " AND message LIKE ?"
            args.append(f"%{q}%")
        if name:
            sql += " AND name = ?"
            args.append(name)
        if flagged:
            sql += " AND flagged IS NOT NULL"
        if before:
            sql += " AND id < ?"
            args.append(before)
        sql += " ORDER BY id DESC LIMIT ?"
        args.append(min(limit, 500))
        return self.db.q(sql, args)

    def cleanup(self, keep_days: int = 90) -> None:
        self.db.x("DELETE FROM chat WHERE ts < ?", (int(time.time()) - keep_days * 86400,))
