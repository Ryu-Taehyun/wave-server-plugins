"""
알림: 디스코드 웹훅 + 웹 푸시(홈 화면에 추가한 폰 · PC 브라우저)
종류마다 켜고 끄기 · 같은 알림은 10분에 한 번
"""
from __future__ import annotations

import asyncio
import base64
import json
import time
import urllib.request

from .config import Config
from .db import DB

# 종류: (이름, 기본 켜짐, 디스코드 색)
KINDS = {
    "crash": ("서버 비정상 종료", True, 0xFF453A),
    "down": ("서버가 꺼짐 (예상하지 못함)", True, 0xFF453A),
    "up": ("서버가 다시 켜짐", True, 0x30D158),
    "tps": ("TPS 낮음", True, 0xFF9F0A),
    "disk": ("디스크 공간 부족", True, 0xFF9F0A),
    "backup_failed": ("백업 실패", True, 0xFF453A),
    "backup_done": ("백업 완료", False, 0x30D158),
    "new_device": ("새 기기에서 로그인", True, 0x0A84FF),
    "login_blocked": ("로그인 실패가 계속됨", True, 0xFF453A),
    "power": ("누군가 서버를 켜고 끔", False, 0x8E8E93),
}
DEFAULTS = {"tps_below": 15.0, "tps_minutes": 2, "disk_below_gb": 5.0, "down_minutes": 2}
COOLDOWN = 600


class Notifier:
    def __init__(self, cfg: Config, db: DB, on_event=None):
        self.cfg, self.db, self.on_event = cfg, db, on_event
        self._last: dict[str, float] = {}

    # ---------------- 설정 ----------------

    def settings(self) -> dict:
        raw = json.loads(self.db.setting("notify", "{}") or "{}")
        events = {k: raw.get("events", {}).get(k, v[1]) for k, v in KINDS.items()}
        out = {**DEFAULTS, **{k: raw[k] for k in DEFAULTS if k in raw}, "events": events}
        out["discord_webhook"] = raw.get("discord_webhook", self.cfg.notify.discord_webhook)
        return out

    def save_settings(self, data: dict) -> None:
        cur = self.settings()
        for k in DEFAULTS:
            if k in data:
                cur[k] = float(data[k])
        if "events" in data:
            cur["events"] = {k: bool(data["events"].get(k, cur["events"][k])) for k in KINDS}
        if "discord_webhook" in data:
            url = (data["discord_webhook"] or "").strip()
            if url and not url.startswith(("https://discord.com/api/webhooks/", "https://discordapp.com/api/webhooks/", "https://canary.discord.com/api/webhooks/")):
                raise ValueError("디스코드 웹훅 주소가 아닙니다")
            cur["discord_webhook"] = url
        self.db.set_setting("notify", json.dumps(cur, ensure_ascii=False))

    def public_settings(self) -> dict:
        s = self.settings()
        hook = s.pop("discord_webhook")
        s["discord_set"] = bool(hook)
        s["discord_hint"] = (hook[:40] + "…") if hook else ""   # 비밀 값은 앞부분만
        s["kinds"] = {k: v[0] for k, v in KINDS.items()}
        s["push_ready"] = bool(self.vapid_public())
        return s

    # ---------------- 보내기 ----------------

    async def alert(self, kind: str, title: str, body: str = "", force: bool = False) -> bool:
        s = self.settings()
        if not force and not s["events"].get(kind, False):
            return False
        now = time.time()
        if not force and now - self._last.get(kind + title, 0) < COOLDOWN:
            return False
        self._last[kind + title] = now
        self.db.x("INSERT INTO alerts (ts, kind, title, body) VALUES (?, ?, ?, ?)", (int(now), kind, title, body[:1000]))
        if self.on_event:
            await self.on_event("alert", {"kind": kind, "title": title, "body": body})
        await asyncio.gather(self._discord(s["discord_webhook"], kind, title, body), self._push_all(title, body, kind), return_exceptions=True)
        return True

    async def _discord(self, url: str, kind: str, title: str, body: str) -> None:
        if not url:
            return
        payload = json.dumps({"username": "서버 관리", "embeds": [{"title": title, "description": body[:2000], "color": KINDS.get(kind, ("", False, 0x8E8E93))[2],
                                                                    "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}]}).encode()

        def send():
            req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json", "User-Agent": "mc-server-manager"})
            urllib.request.urlopen(req, timeout=8).read()

        try:
            await asyncio.to_thread(send)
        except Exception:
            pass

    # ---------------- 웹 푸시 ----------------

    def vapid_public(self) -> str | None:
        self._ensure_vapid()
        return self.db.setting("vapid_public")

    def _ensure_vapid(self) -> None:
        if self.db.setting("vapid_private"):
            return
        try:
            from cryptography.hazmat.primitives import serialization
            from cryptography.hazmat.primitives.asymmetric import ec
        except ImportError:
            return
        key = ec.generate_private_key(ec.SECP256R1())
        priv = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()).decode()
        pub = key.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
        self.db.set_setting("vapid_private", priv)
        self.db.set_setting("vapid_public", base64.urlsafe_b64encode(pub).decode().rstrip("="))

    async def _push_all(self, title: str, body: str, kind: str) -> None:
        subs = self.db.q("SELECT * FROM push_subs")
        if not subs:
            return
        try:
            from pywebpush import WebPushException, webpush
        except ImportError:
            return
        priv = self.db.setting("vapid_private")
        if not priv:
            return
        data = json.dumps({"title": title, "body": body[:300], "kind": kind, "url": "/#overview"}, ensure_ascii=False)

        def send(sub):
            try:
                webpush({"endpoint": sub["endpoint"], "keys": {"p256dh": sub["p256dh"], "auth": sub["auth"]}}, data,
                        vapid_private_key=priv, vapid_claims={"sub": self.cfg.notify.push_contact}, ttl=3600)
            except WebPushException as e:
                code = getattr(getattr(e, "response", None), "status_code", 0)
                if code in (404, 410):   # 구독이 사라진 기기
                    self.db.x("DELETE FROM push_subs WHERE id = ?", (sub["id"],))
            except Exception:
                pass

        await asyncio.gather(*(asyncio.to_thread(send, s) for s in subs))


class Watchdog:
    """모니터 결과를 보고 알림을 보냄 (TPS · 디스크 · 예상하지 못한 꺼짐)"""

    def __init__(self, notifier: Notifier):
        self.n = notifier
        self._low_tps_since: float | None = None
        self._down_since: float | None = None
        self._down_alerted = False

    async def check(self, snap: dict, expected_down: bool) -> None:
        s = self.n.settings()
        now = time.time()
        tps = snap.get("tps")
        if snap.get("online") and tps is not None and tps < s["tps_below"]:
            self._low_tps_since = self._low_tps_since or now
            if now - self._low_tps_since >= s["tps_minutes"] * 60:
                await self.n.alert("tps", f"TPS 낮음: {tps:.1f}", f"{s['tps_minutes']:.0f}분 넘게 TPS 가 {s['tps_below']:.0f} 아래입니다. 접속자 {snap.get('players_online', 0)}명")
        else:
            self._low_tps_since = None
        free = snap.get("disk_free_gb")
        if free is not None and free < s["disk_below_gb"]:
            await self.n.alert("disk", f"디스크 공간 부족: {free:.1f} GB 남음", "백업이나 월드 저장이 실패할 수 있습니다. 오래된 백업을 지우세요.")
        if snap.get("online"):
            if self._down_alerted:
                await self.n.alert("up", "서버가 다시 켜졌습니다", f"접속 가능 · {snap.get('version', '')}")
            self._down_since, self._down_alerted = None, False
        elif not expected_down:
            self._down_since = self._down_since or now
            if not self._down_alerted and now - self._down_since >= s["down_minutes"] * 60:
                self._down_alerted = True
                await self.n.alert("down", "서버에 접속할 수 없습니다", f"{s['down_minutes']:.0f}분 넘게 응답이 없습니다. {snap.get('error', '')}")
        else:
            self._down_since = None
