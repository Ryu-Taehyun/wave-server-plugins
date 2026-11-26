import asyncio
import io
import json
import os
import time
import zipfile

import pytest
from fastapi.testclient import TestClient

from mcmanager import auth, diagnostics, files as files_mod
from mcmanager.app import create_app
from mcmanager.db import DB
from mcmanager.notify import Notifier, Watchdog
from mcmanager.wave import Wave, parse_player

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
H = {"X-MCM": "1"}


def wait(cond, secs=20):
    end = time.time() + secs
    while time.time() < end:
        v = cond()
        if v:
            return v
        time.sleep(0.25)
    raise AssertionError("시간 초과")


# ---------------- 형식 검사 ----------------

def test_yaml_duplicate_key_detected():
    bad = "worlds:\n  spawn: a\n  wild: b\nother: 1\nworlds:\n  tutorial: x\n"
    prob = files_mod.validate("config.yml", bad)
    assert prob and "worlds" in prob and "두 번" in prob
    assert files_mod.validate("config.yml", "a: 1\nb:\n  c: [1, 2]\n") is None
    assert "탭" in files_mod.validate("x.yml", "a:\n\tb: 1\n")
    assert files_mod.validate("x.json", "{bad") is not None
    assert files_mod.validate("server.properties", "a=1\n# c\nbroken line\n").startswith("3줄")
    # Bukkit 아이템 태그가 있어도 통과
    assert files_mod.validate("p.yml", "item: !!org.bukkit.Foo\n  a: 1\n") is None


# ---------------- WaveCore ----------------

def make_wave(server_dir):
    base = server_dir / "plugins" / "WaveCore"
    (base / "playerdata").mkdir(parents=True)
    for i, (n, money, kills, lv) in enumerate([("Alpha", 50000, 12, 30), ("Beta", 120000, 3, 12), ("Gamma", 900, 40, 5)]):
        (base / "playerdata" / f"0000000{i}-0000-0000-0000-000000000000.yml").write_text(
            f"name: {n}\nmoney: {money}\nmoney-total: {money * 2}\nexmoney: 100\nlevel: {lv}\nkills: {kills}\n"
            f"titles:\n- ace\n- 7days\nactive-title: ach|pvp\nsanctions:\n- '2026-11-01 12:00 | 경고 | 욕설 | admin'\n"
            f"mailbox:\n- ==: org.bukkit.inventory.ItemStack\n  id: minecraft:diamond\n  count: 3\n"
            f"stats:\n  pvp: {kills}\n  playtime: 600\nach-claimed:\n- pvp:0\n- level:0\nlast-seen: {int(time.time() * 1000)}\n", encoding="utf-8")
    (base / "config.yml").write_text("# 설정\nworlds:\n  spawn: spawn-world\n\nhot-time:\n  enabled: true\n  multiplier: 2.0\n  schedule:\n  - \"20:00 60\"\n\n"
                                     "season-reset:\n  last: ''\n  money: true # 주석 유지\n  number: 5\n", encoding="utf-8")
    (base / "global.yml").write_text("maintenance: false\nwarps: {}\n", encoding="utf-8")
    return base


def test_wave_reading_and_season(cfg):
    sd = cfg.path(cfg.server.dir)
    base = make_wave(sd)
    w = Wave(cfg, None)
    p = parse_player((base / "playerdata" / "00000000-0000-0000-0000-000000000000.yml").read_text(encoding="utf-8"))
    assert p["name"] == "Alpha" and p["money"] == 50000 and p["titles"] == ["ace", "7days"] and p["stats"]["pvp"] == 12 and len(p["ach-claimed"]) == 2
    ov = w.overview()
    assert ov["players"] == 3 and ov["economy"]["total"] == 170900
    assert [t["name"] for t in ov["top_money"]] == ["Beta", "Alpha", "Gamma"]
    assert ov["top_kills"][0]["name"] == "Gamma"
    assert ov["season"] == 5 and ov["maintenance"] is False and ov["hot_time"]["enabled"]
    w.set_season(1)
    text = (base / "config.yml").read_text(encoding="utf-8")
    assert "number: 1" in text and "# 주석 유지" in text and "# 설정" in text
    assert Wave(cfg, None).season() == 1
    assert w.player("beta")["level"] == 12


# ---------------- 진단 ----------------

def test_error_grouping():
    log = [
        "[12:00:00 INFO]: Done",
        "[12:00:01 ERROR]: Could not pass event PlayerJoinEvent to WaveCore v1.0.0",
        "java.lang.NullPointerException: Cannot invoke \"x\" because \"y\" is null",
        "\tat kr.wave.core.module.CoreModule.onJoin(CoreModule.java:120)",
        "\tat org.bukkit.plugin.SimplePluginManager.callEvent(SimplePluginManager.java:1)",
        "[12:00:05 ERROR]: Could not pass event PlayerJoinEvent to WaveCore v1.0.0",
        "java.lang.NullPointerException: Cannot invoke \"x\" because \"y\" is null",
        "\tat kr.wave.core.module.CoreModule.onJoin(CoreModule.java:120)",
        "[12:01:00 WARN]: Can't keep up! Is the server overloaded?",
        "[12:02:00 ERROR]: Unhandled exception",
        "java.lang.IllegalStateException: boom",
        "\tat com.example.other.Thing.run(Thing.java:5)",
    ]
    plugins = [{"name": "WaveCore", "main": "kr.wave.core.WaveCore"}, {"name": "Other", "main": "com.example.other.OtherPlugin"}]
    g = diagnostics.errors(log, plugins)
    assert g[0]["plugin"] == "Other" and g[0]["exception"] == "IllegalStateException"
    wc = next(x for x in g if x["plugin"] == "WaveCore")
    assert wc["count"] == 2 and wc["exception"] == "NullPointerException" and wc["last"] == "12:00:05"
    assert not any("keep up" in x["message"] for x in g), "단순 경고는 빼기"


# ---------------- 알림 ----------------

def test_notify_settings_and_watchdog(tmp_path, cfg):
    db = DB(tmp_path / "n.db")
    sent = []

    async def ev(k, d):
        sent.append((k, d))
    n = Notifier(cfg, db, ev)
    with pytest.raises(ValueError):
        n.save_settings({"discord_webhook": "https://evil.example.com/x"})
    n.save_settings({"tps_below": 18, "down_minutes": 0, "events": {"power": True}})
    s = n.settings()
    assert s["tps_below"] == 18 and s["events"]["power"] and s["events"]["crash"]
    assert n.public_settings()["discord_set"] is False
    assert n.vapid_public() and len(n.vapid_public()) > 60, "웹 푸시 키 자동 생성"

    async def run():
        wd = Watchdog(n)
        await wd.check({"online": False, "error": "접속 불가"}, expected_down=True)
        assert not sent, "사람이 끈 건 알림 없음"
        await wd.check({"online": False, "error": "접속 불가"}, expected_down=False)
        assert sent and sent[-1][1]["kind"] == "down"
        await wd.check({"online": True, "version": "x"}, expected_down=False)
        assert sent[-1][1]["kind"] == "up"
        assert await n.alert("crash", "a", "b") and not await n.alert("crash", "a", "b"), "같은 알림은 10분에 한 번"
        await wd.check({"online": True, "disk_free_gb": 1.2}, False)
        assert sent[-1][1]["kind"] == "disk"
    asyncio.run(run())
    assert db.one("SELECT COUNT(*) AS n FROM alerts")["n"] >= 4


# ---------------- API: 백업 · 파일 · 플러그인 · WaveCore · 화이트리스트 ----------------

def jar(name="Demo", version="1.0"):
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w") as z:
        z.writestr("plugin.yml", f"name: {name}\nversion: {version}\nmain: com.demo.{name}Plugin\n")
        z.writestr("com/demo/X.class", b"\xca\xfe")
    return b.getvalue()


def test_features_api(cfg, monkeypatch):
    monkeypatch.setenv("PYTHONPATH", ROOT)
    sd = cfg.path(cfg.server.dir)
    (sd / "world").mkdir()
    (sd / "world" / "level.dat").write_bytes(b"LEVEL-v1")
    (sd / "server.properties").write_text("motd=hi\nwhite-list=false\n", encoding="utf-8")
    (sd / "plugins").mkdir()
    (sd / "plugins" / "Demo-1.0.jar").write_bytes(jar("Demo", "1.0"))
    (sd / "plugins" / "Demo" ).mkdir()
    (sd / "plugins" / "Demo" / "logs").mkdir()
    (sd / "plugins" / "Demo" / "logs" / "big.log").write_text("x" * 100)
    make_wave(sd)
    app = create_app(cfg)
    auth.create_user(app.state.db, "boss", "boss-pass1", "admin")
    with TestClient(app) as c:
        c.post("/api/auth/login", json={"username": "boss", "password": "boss-pass1"})
        # 파일 편집: 중복 키는 저장 거부, 정상 저장은 기록 남김
        tree = [f["path"] for f in c.get("/api/files").json()]
        assert "server.properties" in tree and "plugins/WaveCore/config.yml" in tree
        f = c.get("/api/files/read", params={"path": "server.properties"}).json()
        r = c.put("/api/files", json={"path": "plugins/WaveCore/config.yml", "text": "a: 1\na: 2\n"}, headers=H)
        assert r.status_code == 400 and "두 번" in r.json()["detail"]
        assert c.put("/api/files", json={"path": "server.properties", "text": f["text"] + "max-players=50\n", "mtime": f["mtime"]}, headers=H).json()["ok"]
        assert "max-players=50" in (sd / "server.properties").read_text()
        h = c.get("/api/files/history", params={"path": "server.properties"}).json()
        assert len(h) == 1 and "max-players" not in c.get(f"/api/files/history/{h[0]['id']}").json()["text"]
        assert c.get("/api/files/read", params={"path": "../manager.toml"}).status_code == 400
        assert c.get("/api/files/read", params={"path": "plugins/Demo-1.0.jar"}).status_code == 400
        # 서버 켜기
        c.post("/api/server/start", headers=H)
        wait(lambda: c.get("/api/status").json()["state"] == "running")
        wait(lambda: c.get("/api/status").json()["online"])
        # 백업 (켜진 상태: save-off → zip → save-on)
        assert c.post("/api/backups", json={"note": "테스트"}, headers=H).json()["ok"]
        items = wait(lambda: [b for b in c.get("/api/backups").json()["items"] if b["ok"]])
        b = items[0]
        assert b["size"] > 0 and b["kind"] == "manual"
        z = zipfile.ZipFile(io.BytesIO(c.get(f"/api/backups/{b['id']}/download").content))
        names = z.namelist()
        assert "world/level.dat" in names and "server.properties" in names and "plugins/Demo-1.0.jar" in names
        assert not any("logs/big.log" in n for n in names), "exclude 적용"
        # 월드가 망가졌다고 치고 복원 → 서버 다시 켜짐
        (sd / "world" / "level.dat").write_bytes(b"BROKEN")
        (sd / "world" / "junk.dat").write_bytes(b"junk")
        assert c.post(f"/api/backups/{b['id']}/restore", headers=H).json()["ok"]
        wait(lambda: (sd / "world" / "level.dat").read_bytes() == b"LEVEL-v1", 40)
        assert not (sd / "world" / "junk.dat").exists()
        wait(lambda: c.get("/api/status").json()["state"] == "running", 30)
        kinds = [x["kind"] for x in c.get("/api/backups").json()["items"]]
        assert "safety" in kinds, "복원 직전 상태도 백업"
        # 플러그인: 새 버전 올리기 → 적용 (서버 껐다 켬)
        r = c.post("/api/plugins/upload", params={"filename": "Demo-2.0.jar"}, content=jar("Demo", "2.0"), headers=H).json()
        assert r["replaces"] == ["Demo-1.0.jar"]
        assert c.post("/api/plugins/upload", params={"filename": "notplugin.jar"}, content=b"PK-not-zip", headers=H).status_code == 400
        assert c.post("/api/plugins/apply", headers=H).json()["ok"]
        wait(lambda: (sd / "plugins" / "Demo-2.0.jar").exists() and not (sd / "plugins" / "Demo-1.0.jar").exists(), 40)
        wait(lambda: c.get("/api/status").json()["state"] == "running", 30)
        pl = c.get("/api/plugins").json()
        assert pl["active"][0]["version"] == "2.0" and not pl["staged"]
        # WaveCore 패널 · 핫타임 · 점검 · 화이트리스트
        wait(lambda: c.get("/api/status").json()["online"])
        ov = c.get("/api/wave").json()
        assert ov["players"] == 3 and ov["season"] == 5 and ov["top_money"][0]["name"] == "Beta"
        assert c.post("/api/wave/hottime", json={"action": "start", "minutes": 30, "multiplier": 1.5}, headers=H).status_code == 200
        assert c.get("/api/wave").json()["hot_time"]["status"]["active"] is True
        c.post("/api/wave/hottime", json={"action": "stop"}, headers=H)
        assert "점검" in c.post("/api/wave/maintenance", json={"on": True}, headers=H).json()["output"]
        c.post("/api/whitelist", json={"action": "add", "name": "Alpha"}, headers=H)
        assert "Alpha" in c.get("/api/wave").json()["whitelist"]["names"]
        assert c.post("/api/whitelist", json={"action": "add", "name": "bad;name"}, headers=H).status_code == 400
        assert c.post("/api/wave/season", json={"number": 2}, headers=H).json()["reloaded"]
        assert c.get("/api/wave/player/alpha").json()["achievements"] == 2
        # 진단
        d = c.get("/api/diagnostics").json()
        assert "disk_free_gb" in d["system"] and isinstance(d["heatmap"], list)
        # 예약 백업 작업
        t = c.post("/api/tasks", json={"name": "백업", "kind": "backup", "schedule": "daily", "at_time": "04:00"}, headers=H)
        assert t.status_code == 200
        acts = [a["action"] for a in c.get("/api/audit").json()]
        assert {"backup.create", "backup.restore", "plugin.upload", "plugin.apply", "file.save", "wave.hottime", "wave.season"} <= set(acts)
        c.post("/api/server/stop", headers=H)
        wait(lambda: c.get("/api/status").json()["state"] == "stopped", 30)


def test_db_migrates_old_tasks_table(tmp_path):
    import sqlite3
    p = tmp_path / "old.db"
    con = sqlite3.connect(p)
    con.executescript("""CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
        kind TEXT NOT NULL CHECK (kind IN ('restart', 'stop', 'start', 'command', 'broadcast')), payload TEXT NOT NULL DEFAULT '',
        schedule TEXT NOT NULL CHECK (schedule IN ('daily', 'interval', 'once')), at_time TEXT, days INTEGER NOT NULL DEFAULT 127,
        every_min INTEGER, run_at INTEGER, warn_minutes INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1,
        next_run INTEGER, last_run INTEGER, created_by TEXT);
        INSERT INTO tasks (name, kind, schedule, at_time) VALUES ('old', 'restart', 'daily', '04:00');
        CREATE TABLE metrics (ts INTEGER NOT NULL, online INTEGER NOT NULL, players INTEGER NOT NULL DEFAULT 0, max_players INTEGER NOT NULL DEFAULT 0,
        tps REAL, latency_ms REAL, cpu REAL, mem_mb REAL);""")
    con.commit()
    con.close()
    db = DB(p)
    assert db.one("SELECT name FROM tasks")["name"] == "old"
    db.x("INSERT INTO tasks (name, kind, schedule) VALUES ('b', 'backup', 'daily')")
    db.x("INSERT INTO metrics (ts, online, sys_cpu, disk_free_gb) VALUES (1, 1, 5, 10)")
