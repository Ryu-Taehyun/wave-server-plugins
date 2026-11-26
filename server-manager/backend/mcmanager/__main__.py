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
jar = "purpur.jar"

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
        _seed_demo(base / "server")
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


def _seed_demo(sd: Path) -> None:
    """데모용 서버 폴더: 설정 파일 · 플러그인 jar · WaveCore 플레이어 데이터 · 월드 (처음 한 번)"""
    import io
    import json
    import random
    import time
    import zipfile
    from .simulator import NAMES
    if (sd / "server.properties").exists():
        return
    rnd = random.Random(7)
    (sd / "world" / "region").mkdir(parents=True, exist_ok=True)
    (sd / "world" / "level.dat").write_bytes(b"demo-level")
    for i in range(4):
        (sd / "world" / "region" / f"r.{i}.0.mca").write_bytes(rnd.randbytes(200_000))
    (sd / "server.properties").write_text("motd=Wave Online\nmax-players=100\nwhite-list=false\nenable-rcon=true\nrcon.port=25575\nview-distance=10\n", encoding="utf-8")
    (sd / "config").mkdir(exist_ok=True)
    (sd / "config" / "paper-global.yml").write_text("proxies:\n  proxy-protocol: false\nchunk-loading-basic:\n  player-max-chunk-load-rate: 100.0\n", encoding="utf-8")
    pl = sd / "plugins"
    for name, ver, main in (("WaveCore", "1.0.0", "kr.wave.core.WaveCore"), ("PremiumPass", "1.0.0", "com.premiumpass.PremiumPass"),
                            ("DailyShop", "1.0.0", "com.dailyshop.DailyShop"), ("LuckPerms", "5.4.150", "me.lucko.luckperms.bukkit.loader.BukkitLoaderPlugin")):
        b = io.BytesIO()
        with zipfile.ZipFile(b, "w") as z:
            z.writestr("plugin.yml", f"name: {name}\nversion: {ver}\nmain: {main}\n")
        (pl / name).mkdir(parents=True, exist_ok=True)
        (pl / f"{name}-{ver}.jar").write_bytes(b.getvalue())
    wc = pl / "WaveCore"
    (wc / "playerdata").mkdir(parents=True, exist_ok=True)
    (wc / "config.yml").write_text("""# WaveCore 설정 (데모)
worlds:
  spawn: spawn-world
  wild: world

hot-time:
  enabled: true
  multiplier: 2.0
  schedule:
    - "20:00 60"
    - "주말 14:00 60"

season-reset:
  money: true
  number: 1
""", encoding="utf-8")
    now = int(time.time() * 1000)
    uid = lambda i: f"{i:08d}-0000-4000-8000-{i:012d}"
    (wc / "global.yml").write_text(f"""maintenance: false
bans:
  {uid(17)}:
    reason: 핵 사용 (킬아우라)
    code: A1B2C3
    until: 0
    by: demo
    at: {now - 3 * 86400000}
  {uid(18)}:
    reason: 도배
    code: D4E5F6
    until: {now + 2 * 86400000}
    by: demo
    at: {now - 86400000}
""", encoding="utf-8")
    (wc / "logs").mkdir(exist_ok=True)
    ts = lambda ms: time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ms / 1000))
    (wc / "logs" / "sanctions.log").write_text("".join(f"{ts(now - d * 3600000)} | {k} | {NAMES[i]}({uid(i)}) | {det} | demo\n" for d, k, i, det in (
        (80, "경고", 4, "욕설 (1회)"), (72, "영구밴", 17, "핵 사용 (킬아우라)"), (30, "임시밴", 18, "7d · 도배"),
        (20, "경고", 9, "광고 (1회)"), (8, "뮤트", 5, "1h · 도배"), (2, "경고", 4, "욕설 (2회)"))), encoding="utf-8")
    (sd / "version_history.json").write_text(json.dumps({"currentVersion": "git-Purpur-2485 (MC: 1.21.11)"}), encoding="utf-8")
    (sd / "purpur.jar").write_bytes(rnd.randbytes(2_000_000))
    for i, n in enumerate(NAMES):
        lv = rnd.randint(1, 50)
        (wc / "playerdata" / f"{i:08d}-0000-4000-8000-{i:012d}.yml").write_text(
            f"name: {n}\nmoney: {rnd.randint(1000, 400000)}\nmoney-total: {rnd.randint(400000, 2000000)}\nexmoney: {rnd.choice([0, 0, 5000, 25000])}\n"
            f"level: {lv}\nkills: {rnd.randint(0, 300)}\nattend-streak: {rnd.randint(0, 14)}\nattend-total: {rnd.randint(1, 90)}\n"
            f"warnings: {rnd.choice([0, 0, 0, 1, 2])}\nlast-seen: {now - rnd.randint(0, 14) * 86400000}\ntitles:\n- ace\nactive-title: ace\n"
            f"sanctions: []\nmute-until: {now + 3600000 if i == 5 else 0}\nmute-reason: {'도배' if i == 5 else ''}\nstats:\n  playtime: {rnd.randint(30, 6000)}\nach-claimed:\n- level:0\n", encoding="utf-8")


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
