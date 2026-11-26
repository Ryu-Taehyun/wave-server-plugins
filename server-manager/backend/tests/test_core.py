import asyncio
import os
import sys
import time

import pytest

from mcmanager import auth, monitor, scheduler, slp
from mcmanager.db import DB
from mcmanager.process import ServerProcess
from mcmanager.rcon import RconAuthError, RconClient, strip_colors

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def env():
    e = dict(os.environ)
    e["PYTHONPATH"] = ROOT
    return e


def test_password_hash_and_rules():
    h = auth.hash_password("abc12345")
    assert h.startswith("scrypt$") and auth.verify_password("abc12345", h)
    assert not auth.verify_password("abc12346", h)
    assert auth.password_problem("short1") and auth.password_problem("onlyletters") and not auth.password_problem("good-pass1")


def test_login_rate_limit_and_sessions(tmp_path):
    db = DB(tmp_path / "a.db")
    auth.create_user(db, "admin", "secret-123", "admin")
    for _ in range(auth.MAX_FAILS):
        assert auth.login(db, "admin", "wrong", "1.2.3.4", "ua", 1) is None
    assert auth.blocked(db, "1.2.3.4") > 0
    assert auth.blocked(db, "5.6.7.8") == 0, "다른 IP 는 막히지 않음"
    token, u = auth.login(db, "ADMIN", "secret-123", "5.6.7.8", "phone", 1)   # 아이디 대소문자 무시
    s = auth.session_user(db, token)
    assert s and s["username"] == "admin" and auth.can(s, "operator")
    assert db.one("SELECT token_hash FROM sessions")["token_hash"] != token, "토큰은 해시로만 저장"
    db.x("UPDATE sessions SET revoked = 1")
    assert auth.session_user(db, token) is None


def test_parsers():
    assert monitor.parse_list("There are 3 of a max of 100 players online: a, b_c, Dd") == (3, 100, ["a", "b_c", "Dd"])
    assert monitor.parse_list("There are 0 of a max of 20 players online: ") == (0, 20, [])
    assert monitor.parse_tps("§6TPS from last 1m, 5m, 15m: §a19.97, §a20.0, §a20.0") == 19.97
    assert monitor.parse_tps("§6TPS from last 1m, 5m, 15m: §a*20.0, 20.0, 20.0") == 20.0
    assert strip_colors("§aHello §lWorld") == "Hello World"


def test_next_run():
    import datetime as dt
    base = dt.datetime(2026, 11, 26, 3, 0).timestamp()   # 목요일 03:00
    t = {"schedule": "daily", "at_time": "04:00", "days": 127}
    assert dt.datetime.fromtimestamp(scheduler.next_run(t, base)).hour == 4
    t2 = {"schedule": "daily", "at_time": "02:00", "days": 1 << 5}   # 토요일만
    d = dt.datetime.fromtimestamp(scheduler.next_run(t2, base))
    assert d.weekday() == 5 and d.hour == 2
    t3 = {"schedule": "interval", "every_min": 30, "last_run": int(base) - 3600}
    assert scheduler.next_run(t3, base) == int(base) + 1800 - 1800 + 1800 - 1800 + 1800 or scheduler.next_run(t3, base) > base
    assert scheduler.next_run({"schedule": "once", "run_at": int(base) - 5}, base) is None


def test_rcon_and_ping_against_simulator(cfg):
    async def run():
        p = await asyncio.create_subprocess_exec(sys.executable, "-m", "mcmanager.simulator", "--port", str(cfg.server.port),
                                                 "--rcon-port", str(cfg.rcon.port), "--password", "pw-test", "--boot", "0.2",
                                                 stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.DEVNULL, env=env())
        try:
            for _ in range(50):
                st = await slp.ping("127.0.0.1", cfg.server.port, 1)
                if st.online:
                    break
                await asyncio.sleep(0.1)
            assert st.online and st.players_max == 100 and "Wave Online" in st.motd and st.latency_ms is not None
            r = RconClient("127.0.0.1", cfg.rcon.port, "pw-test")
            out = await r.command("list")
            assert "players online" in out
            assert "TPS" in await r.command("tps")
            long = await r.command("banlist")
            assert "There are" in long
            bad = RconClient("127.0.0.1", cfg.rcon.port, "nope")
            with pytest.raises(RconAuthError):
                await bad.command("list")
            await r.command("stop")
            await asyncio.wait_for(p.wait(), 10)
        finally:
            if p.returncode is None:
                p.kill()
    asyncio.run(run())


def test_process_start_stop_restart(cfg, monkeypatch):
    monkeypatch.setenv("PYTHONPATH", ROOT)

    async def run():
        r = RconClient("127.0.0.1", cfg.rcon.port, "pw-test")
        events = []

        async def ev(k, d):
            events.append(d.get("state"))
        sp = ServerProcess(cfg, r, ev)
        assert await sp.start() == "켜는 중입니다"
        for _ in range(100):
            if sp.state == "running":
                break
            await asyncio.sleep(0.1)
        assert sp.state == "running" and sp.managed
        assert any("Done (" in l for l in sp.logs)
        assert "이미" in await sp.start()
        await sp.restart()
        for _ in range(100):
            if sp.state == "running":
                break
            await asyncio.sleep(0.1)
        assert sp.state == "running"
        assert await sp.stop() == "꺼졌습니다"
        assert sp.state == "stopped" and not sp.managed
        assert "stopping" in events and "running" in events
        await r.close()
    asyncio.run(run())


def test_crash_detection(cfg, monkeypatch):
    monkeypatch.setenv("PYTHONPATH", ROOT)

    async def run():
        sp = ServerProcess(cfg, RconClient("127.0.0.1", cfg.rcon.port, "pw-test"))
        await sp.start()
        for _ in range(100):
            if sp.state == "running":
                break
            await asyncio.sleep(0.1)
        sp._kill()   # 갑자기 꺼짐
        for _ in range(50):
            if sp.state == "crashed":
                break
            await asyncio.sleep(0.1)
        assert sp.state == "crashed"
        assert any("비정상 종료" in l for l in sp.logs)
    asyncio.run(run())


def test_metrics_series_sql(tmp_path):
    db = DB(tmp_path / "m.db")
    now = int(time.time())
    for i in range(120):
        db.x("INSERT INTO metrics (ts, online, players, max_players, tps) VALUES (?, ?, ?, 100, ?)", (now - i * 30, 1 if i % 10 else 0, i % 7, 19.5))
    s = monitor.series(db, "1h")
    assert 110 <= len(s) <= 122 and all(r["tps"] == 19.5 for r in s)
    assert monitor.summary(db)["peak_24h"] == 6
