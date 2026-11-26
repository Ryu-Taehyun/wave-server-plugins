"""비밀번호(scrypt) · 로그인 세션(기기별 토큰) · 로그인 시도 제한 · 권한"""
from __future__ import annotations

import base64
import hashlib
import hmac
import secrets
import time

from .db import DB

ROLES = {"viewer": 0, "operator": 1, "admin": 2}
MAX_FAILS, FAIL_WINDOW = 8, 600   # 10분에 8번 실패하면 잠시 막음


def hash_password(pw: str) -> str:
    salt = secrets.token_bytes(16)
    n, r, p = 2 ** 14, 8, 1
    h = hashlib.scrypt(pw.encode(), salt=salt, n=n, r=r, p=p, dklen=32)
    return f"scrypt${n}${r}${p}${base64.b64encode(salt).decode()}${base64.b64encode(h).decode()}"


def verify_password(pw: str, stored: str) -> bool:
    try:
        algo, n, r, p, salt, h = stored.split("$")
        if algo != "scrypt":
            return False
        got = hashlib.scrypt(pw.encode(), salt=base64.b64decode(salt), n=int(n), r=int(r), p=int(p), dklen=32)
        return hmac.compare_digest(got, base64.b64decode(h))
    except (ValueError, TypeError):
        return False


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def password_problem(pw: str) -> str | None:
    if len(pw) < 8:
        return "비밀번호는 8자 이상이어야 합니다"
    if pw.isdigit() or pw.isalpha():
        return "비밀번호에 글자와 숫자(또는 기호)를 섞어 주세요"
    return None


def create_user(db: DB, username: str, pw: str, role: str = "viewer") -> int:
    if role not in ROLES:
        raise ValueError("role 은 admin · operator · viewer 중 하나")
    if not username or len(username) > 32 or not all(c.isalnum() or c in "_-." for c in username):
        raise ValueError("아이디는 영문 · 숫자 · _ - . 로 1~32자")
    prob = password_problem(pw)
    if prob:
        raise ValueError(prob)
    return db.x("INSERT INTO users (username, pw_hash, role, created_at) VALUES (?, ?, ?, ?)",
                (username, hash_password(pw), role, int(time.time())))


def blocked(db: DB, ip: str) -> int:
    """막혀 있으면 남은 초, 아니면 0"""
    since = int(time.time()) - FAIL_WINDOW
    r = db.one("SELECT COUNT(*) AS n, MIN(ts) AS first FROM login_attempts WHERE ip = ? AND success = 0 AND ts >= ?", (ip, since))
    if r and r["n"] >= MAX_FAILS:
        return max(1, r["first"] + FAIL_WINDOW - int(time.time()))
    return 0


def login(db: DB, username: str, pw: str, ip: str, user_agent: str, days: int) -> tuple[str, dict] | None:
    now = int(time.time())
    u = db.one("SELECT * FROM users WHERE username = ?", (username,))
    ok = bool(u) and verify_password(pw, u["pw_hash"])
    if not u:
        verify_password(pw, "scrypt$16384$8$1$AAAAAAAAAAAAAAAAAAAAAA==$AAAA")   # 없는 아이디도 같은 시간이 걸리게
    db.x("INSERT INTO login_attempts (ts, ip, username, success) VALUES (?, ?, ?, ?)", (now, ip, username[:64], int(ok)))
    if not ok:
        return None
    token = secrets.token_urlsafe(32)
    db.x("INSERT INTO sessions (token_hash, user_id, created_at, last_seen, expires_at, ip, user_agent) VALUES (?, ?, ?, ?, ?, ?, ?)",
         (token_hash(token), u["id"], now, now, now + days * 86400, ip, (user_agent or "")[:300]))
    db.x("UPDATE users SET last_login = ? WHERE id = ?", (now, u["id"]))
    db.x("DELETE FROM login_attempts WHERE ts < ?", (now - 86400 * 7,))
    return token, u


def session_user(db: DB, token: str | None) -> dict | None:
    if not token:
        return None
    now = int(time.time())
    r = db.one("""SELECT s.id AS session_id, s.last_seen, u.id, u.username, u.role
                  FROM sessions s JOIN users u ON u.id = s.user_id
                  WHERE s.token_hash = ? AND s.revoked = 0 AND s.expires_at > ?""", (token_hash(token), now))
    if r and now - r["last_seen"] > 60:
        db.x("UPDATE sessions SET last_seen = ? WHERE id = ?", (now, r["session_id"]))
    return r


def can(user: dict | None, need: str) -> bool:
    return bool(user) and ROLES.get(user["role"], -1) >= ROLES[need]
