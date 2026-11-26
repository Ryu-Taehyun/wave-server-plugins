"""
제재 관리 (WaveCore 연동)
- 조회: 차단(global.yml bans) · 뮤트 · 경고(플레이어 데이터) · 제재 기록(logs/sanctions.log) · 신고(reports 표)
- 처리: RCON 으로 WaveCore 명령 (경고 · 경고삭제 · 임시밴 · serverban · unserverban · 뮤트 · 뮤트해제 · kick)
"""
from __future__ import annotations

import re
import time
from pathlib import Path

import yaml

from .db import DB
from .wave import Wave

DUR = re.compile(r"^\d+[mhdw]$")
NAME = re.compile(r"^[A-Za-z0-9_]{3,16}$")


def _loose(text: str) -> dict:
    loader = type("Loose", (yaml.SafeLoader,), {})
    loader.add_multi_constructor("", lambda l, s, n: None)
    try:
        return yaml.load(text, Loader=loader) or {}
    except yaml.YAMLError:
        return {}


class Moderation:
    def __init__(self, db: DB, wave: Wave):
        self.db, self.wave = db, wave

    @property
    def base(self) -> Path:
        return self.wave.base

    def _names(self) -> dict[str, str]:
        return {p.get("uuid", ""): p.get("name", "") for p in self.wave.players(max_age=30)}

    def bans(self) -> list[dict]:
        p = self.base / "global.yml"
        if not p.exists():
            return []
        bans = (_loose(p.read_text(encoding="utf-8", errors="replace")).get("bans") or {})
        names = self._names()
        now = time.time() * 1000
        out = []
        for uid, b in bans.items() if isinstance(bans, dict) else []:
            if not isinstance(b, dict):
                continue
            until = int(b.get("until") or 0)
            if until and until < now:
                continue
            out.append({"uuid": str(uid), "name": names.get(str(uid), str(uid)[:8]), "reason": str(b.get("reason", "")), "code": str(b.get("code", "")),
                        "until": until // 1000 if until else 0, "by": str(b.get("by", "")), "at": int(b.get("at") or 0) // 1000})
        return sorted(out, key=lambda x: -x["at"])

    def mutes(self) -> list[dict]:
        now = time.time() * 1000
        return sorted(({"name": p["name"], "until": int(p["mute-until"]) // 1000, "reason": p.get("mute-reason") or ""}
                       for p in self.wave.players(max_age=30) if isinstance(p.get("mute-until"), (int, float)) and p["mute-until"] > now),
                      key=lambda x: x["until"])

    def warnings(self) -> list[dict]:
        return sorted(({"name": p["name"], "warnings": int(p["warnings"]), "last": (p.get("sanctions") or [""])[-1]}
                       for p in self.wave.players(max_age=30) if isinstance(p.get("warnings"), int) and p["warnings"] > 0),
                      key=lambda x: -x["warnings"])

    def history(self, limit: int = 200) -> list[dict]:
        p = self.base / "logs" / "sanctions.log"
        if not p.exists():
            return []
        try:
            with open(p, "rb") as f:
                f.seek(max(0, p.stat().st_size - 200_000))
                lines = f.read().decode("utf-8", "replace").splitlines()
        except OSError:
            return []
        out = []
        for line in reversed(lines):
            parts = [x.strip() for x in line.split(" | ")]
            if len(parts) >= 5:
                name = parts[2].split("(")[0]
                out.append({"time": parts[0], "kind": parts[1], "name": name, "detail": " | ".join(parts[3:-1]), "by": parts[-1]})
            if len(out) >= limit:
                break
        return out

    def overview(self) -> dict:
        return {"bans": self.bans(), "mutes": self.mutes(), "warnings": self.warnings(), "history": self.history(),
                "reports_open": self.db.one("SELECT COUNT(*) AS n FROM reports WHERE status = 'open'")["n"]}

    # ---------------- 명령 ----------------

    @staticmethod
    def command(action: str, name: str, duration: str = "", reason: str = "", count: str = "") -> str:
        if not NAME.match(name or ""):
            raise ValueError("닉네임이 올바르지 않습니다")
        reason = " ".join((reason or "").split())[:200]
        if action in ("tempban", "mute") and not DUR.match(duration or ""):
            raise ValueError("기간은 30m · 2h · 7d · 1w 처럼 적어 주세요")
        if action in ("warn", "tempban", "ban") and not reason:
            raise ValueError("사유를 적어 주세요")
        return {
            "warn": f"경고 {name} {reason}",
            "warn_clear": f"경고삭제 {name} {count if count.isdigit() else '전체'}",
            "tempban": f"임시밴 {name} {duration} {reason}",
            "ban": f"serverban {name} {reason}",
            "unban": f"unserverban {name}",
            "mute": f"뮤트 {name} {duration} {reason}".strip(),
            "unmute": f"뮤트해제 {name}",
            "kick": f"kick {name} {reason}".strip(),
        }.get(action) or (_ for _ in ()).throw(ValueError("알 수 없는 처리"))

    def reports(self, status: str = "open") -> list[dict]:
        if status == "all":
            return self.db.q("SELECT * FROM reports ORDER BY id DESC LIMIT 200")
        return self.db.q("SELECT * FROM reports WHERE status = ? ORDER BY id DESC LIMIT 200", (status,))

    def set_report(self, rid: int, status: str, by: str, note: str = "") -> None:
        if status not in ("open", "done", "dismissed"):
            raise ValueError("잘못된 상태")
        self.db.x("UPDATE reports SET status = ?, handled_by = ?, handled_at = ?, note = ? WHERE id = ?",
                  (status, by if status != "open" else None, int(time.time()) if status != "open" else None, note[:300], rid))
