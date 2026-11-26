"""
python -m mcmanager run                      웹 대시보드 실행
python -m mcmanager create-user <아이디> [--role admin|operator|viewer]
python -m mcmanager set-password <아이디>
python -m mcmanager users
python -m mcmanager demo                     가짜 서버로 바로 써 보기 (demo/ 폴더 · 관리자 demo / demo1234)
"""
from __future__ import annotations

import argparse
import getpass
import os
import sys
from pathlib import Path

from . import auth, config
from .db import DB


def _db(cfg) -> DB:
    return DB(cfg.path(cfg.app.db))


def main() -> None:
    ap = argparse.ArgumentParser(prog="mcmanager")
    ap.add_argument("--config", default=None, help="설정 파일 (기본 manager.toml)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("run")
    cu = sub.add_parser("create-user")
    cu.add_argument("username")
    cu.add_argument("--role", default="admin", choices=list(auth.ROLES))
    sp = sub.add_parser("set-password")
    sp.add_argument("username")
    sub.add_parser("users")
    sub.add_parser("demo")
    a = ap.parse_args()

    if a.cmd == "demo":
        base = Path("demo")
        (base / "server").mkdir(parents=True, exist_ok=True)
        toml = base / "manager.toml"
        if not toml.exists():
            toml.write_text(f'''[server]
dir = "server"
start_command = '"{sys.executable}" -m mcmanager.simulator --port 25565 --rcon-port 25575 --password demo'
host = "127.0.0.1"
port = 25565
stop_timeout = 15

[rcon]
host = "127.0.0.1"
port = 25575
password = "demo"

[web]
host = "0.0.0.0"
port = 8080

[app]
db = "manager.db"
sample_seconds = 3
''', encoding="utf-8")
        cfg = config.load(toml)
        db = _db(cfg)
        if not db.one("SELECT 1 FROM users"):
            auth.create_user(db, "demo", "demo1234", "admin")
            print("데모 관리자: demo / demo1234")
        os.environ["MCM_CONFIG"] = str(toml)
        _serve(cfg)
        return

    cfg = config.load(a.config)
    if a.cmd == "run":
        db = _db(cfg)
        if not db.one("SELECT 1 FROM users"):
            print("사용자가 없습니다. 먼저 관리자를 만드세요: python -m mcmanager create-user admin", file=sys.stderr)
            sys.exit(1)
        if not cfg.rcon.password:
            print("경고: RCON 비밀번호가 없습니다 (manager.toml [rcon] password 또는 MCM_RCON_PASSWORD). 콘솔 · 플레이어 기능이 제한됩니다.", file=sys.stderr)
        _serve(cfg)
    elif a.cmd == "create-user":
        pw = getpass.getpass("비밀번호: ")
        if pw != getpass.getpass("비밀번호 확인: "):
            sys.exit("비밀번호가 서로 다릅니다")
        try:
            auth.create_user(_db(cfg), a.username, pw, a.role)
        except Exception as e:
            sys.exit(f"만들지 못했습니다: {e}")
        print(f"사용자 '{a.username}' ({a.role}) 를 만들었습니다")
    elif a.cmd == "set-password":
        db = _db(cfg)
        if not db.one("SELECT 1 FROM users WHERE username = ?", (a.username,)):
            sys.exit("없는 사용자입니다")
        pw = getpass.getpass("새 비밀번호: ")
        prob = auth.password_problem(pw)
        if prob:
            sys.exit(prob)
        db.x("UPDATE users SET pw_hash = ? WHERE username = ?", (auth.hash_password(pw), a.username))
        db.x("UPDATE sessions SET revoked = 1 WHERE user_id = (SELECT id FROM users WHERE username = ?)", (a.username,))
        print("바꿨습니다 (로그인한 기기는 모두 로그아웃됨)")
    elif a.cmd == "users":
        for u in _db(cfg).q("SELECT username, role, last_login FROM users ORDER BY id"):
            print(f"{u['username']:<20} {u['role']:<9} 마지막 로그인 {u['last_login'] or '-'}")


def _serve(cfg) -> None:
    import asyncio
    import uvicorn
    from .app import create_app
    loop = "auto"
    if sys.platform == "win32":
        # Windows: 서버 프로세스를 켜려면 Proactor 이벤트 루프가 필요 (Selector 루프는 하위 프로세스를 못 만듦)
        asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())
        loop = "none"
    print(f"서버 관리 도구: http://{'localhost' if cfg.web.host in ('0.0.0.0', '::') else cfg.web.host}:{cfg.web.port}  (끄려면 Ctrl+C)")
    uvicorn.run(create_app(cfg), host=cfg.web.host, port=cfg.web.port, log_level="warning", proxy_headers=cfg.web.trusted_proxy, loop=loop)


if __name__ == "__main__":
    main()
