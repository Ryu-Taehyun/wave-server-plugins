"""제재 관리 · 실시간 채팅 · 서버 자동 업데이트"""
import asyncio
import hashlib
import json
import time

import pytest
from fastapi.testclient import TestClient

from mcmanager import auth
from mcmanager.app import create_app
from mcmanager.chat import Chat
from mcmanager.db import DB
from mcmanager.moderation import Moderation
from mcmanager.updater import Updater, UpdateError

from test_features import H, ROOT, make_wave, wait


# ---------------- 채팅 ----------------

class Sink:
    def __init__(self):
        self.events, self.alerts = [], []

    async def event(self, k, d):
        self.events.append((k, d))

    async def alert(self, k, t, b="", force=False):
        self.alerts.append((k, t, b))


def test_chat_parse_store_and_flags(tmp_path):
    db = DB(tmp_path / "c.db")
    s = Sink()
    chat = Chat(db, s.event, s.alert)
    chat.save_settings({"words": ["바보", " ", "멍청이"], "allow_domains": ["wave.kr"]})
    lines = [
        "[20:00:01 INFO]: [Local] [⚔ 학살자] - Hunter : 안녕하세요",
        "\x1b[36m[20:00:02 INFO]: [TEAM] 블루 | - Neo : 모여\x1b[0m",
        "[20:00:03 INFO]: - Alex99 : 바 보 ㅋㅋ",                      # 띄어 써도 금지어
        "[20:00:04] [Async Chat Thread - #0/INFO]: <Steve_KR> 싸게 팝니다 cheapmc.xyz",
        "[20:00:05 INFO]: <Steve_KR> 우리 서버 play.wave.kr 홍보 환영",   # 허용 도메인
        "[20:00:06 INFO]: Ruby issued server command: /귓 Neo 거래 ㄱ?",
        "[20:00:07 INFO]: Ruby issued server command: /report Hunter 욕설했어요",
        "[20:00:08 INFO]: Ruby issued server command: /spawn",
        "[20:00:09 INFO]: [Manager] boss: /say hi",
        "[20:00:10 INFO]: Done (2.000s)! For help, type \"help\"",
    ]

    async def go():
        for l in lines:
            await chat.feed(l)
    asyncio.run(go())
    rows = list(reversed(chat.search()))
    assert [(r["name"], r["channel"]) for r in rows] == [("Hunter", "local"), ("Neo", "team"), ("Alex99", "global"),
                                                         ("Steve_KR", "global"), ("Steve_KR", "global"), ("Ruby", "whisper")]
    assert rows[5]["to_name"] == "Neo" and rows[5]["message"] == "거래 ㄱ?"
    assert rows[2]["flagged"].startswith("금지어") and rows[3]["flagged"].startswith("광고") and rows[4]["flagged"] is None
    assert [a[0] for a in s.alerts] == ["chat_flag", "chat_flag", "report"]
    assert db.one("SELECT * FROM reports")["target"] == "Hunter"
    # 검색
    assert [r["name"] for r in chat.search(name="steve_kr")] == ["Steve_KR", "Steve_KR"]
    assert len(chat.search(q="모여")) == 1 and len(chat.search(flagged=True)) == 2
    assert len(chat.search(before=rows[2]["id"])) == 2


# ---------------- 제재 ----------------

def seed_sanctions(sd):
    base = make_wave(sd)
    now = int(time.time() * 1000)
    (base / "global.yml").write_text(f"maintenance: false\nbans:\n  00000002-0000-0000-0000-000000000000:\n    reason: 핵\n    code: ABC123\n    until: 0\n    by: boss\n    at: {now}\n"
                                     f"  00000009-0000-0000-0000-000000000000:\n    reason: 지난 밴\n    code: X\n    until: {now - 1000}\n    by: boss\n    at: {now - 9999}\n", encoding="utf-8")
    p = base / "playerdata" / "00000001-0000-0000-0000-000000000000.yml"
    p.write_text(p.read_text(encoding="utf-8") + f"warnings: 2\nmute-until: {now + 3600_000}\nmute-reason: 도배\n", encoding="utf-8")
    (base / "logs").mkdir()
    (base / "logs" / "sanctions.log").write_text("2026-11-20 10:00:00 | 경고 | Beta(00000001-0000-0000-0000-000000000000) | 욕설 (1회) | boss\n"
                                                 "2026-11-21 10:00:00 | 영구밴 | Gamma(00000002-0000-0000-0000-000000000000) | 핵 | boss\n", encoding="utf-8")
    return base


def test_moderation_reads_and_commands(cfg):
    seed_sanctions(cfg.path(cfg.server.dir))
    from mcmanager.rcon import RconClient
    from mcmanager.wave import Wave
    m = Moderation(DB(cfg.path("m.db")), Wave(cfg, RconClient("127.0.0.1", 1, "")))
    bans = m.bans()
    assert [(b["name"], b["until"], b["code"]) for b in bans] == [("Gamma", 0, "ABC123")], "만료된 임시밴은 빠짐"
    assert m.mutes()[0]["name"] == "Beta" and m.mutes()[0]["reason"] == "도배"
    assert m.warnings()[0] == {"name": "Beta", "warnings": 2, "last": "2026-11-01 12:00 | 경고 | 욕설 | admin"}
    h = m.history()
    assert h[0]["kind"] == "영구밴" and h[0]["name"] == "Gamma" and h[1]["detail"] == "욕설 (1회)"
    assert Moderation.command("tempban", "Beta", "7d", "도배  \n 반복") == "임시밴 Beta 7d 도배 반복"
    assert Moderation.command("warn_clear", "Beta") == "경고삭제 Beta 전체"
    assert Moderation.command("mute", "Beta", "30m") == "뮤트 Beta 30m"
    for bad in (("tempban", "Beta", "7days", "x"), ("warn", "Beta", "", ""), ("ban", "bad name", "", "x"), ("nuke", "Beta", "", "x")):
        with pytest.raises(ValueError):
            Moderation.command(*bad)


def test_sanction_chat_api(cfg, monkeypatch):
    monkeypatch.setenv("PYTHONPATH", ROOT)
    sd = cfg.path(cfg.server.dir)
    seed_sanctions(sd)
    app = create_app(cfg)
    auth.create_user(app.state.db, "boss", "boss-pass1", "admin")
    auth.create_user(app.state.db, "look", "look-pass1", "viewer")
    with TestClient(app) as c:
        c.post("/api/auth/login", json={"username": "boss", "password": "boss-pass1"})
        c.post("/api/server/start", headers=H)
        wait(lambda: c.get("/api/status").json()["online"])
        ov = c.get("/api/sanctions").json()
        assert len(ov["bans"]) == 1 and ov["reports_open"] == 0
        # 처리 → 시뮬레이터가 WaveCore 처럼 파일을 바꿈
        r = c.post("/api/sanctions", json={"action": "warn", "name": "Alpha", "reason": "욕설"}, headers=H)
        assert r.status_code == 200 and "경고" in r.json()["output"]
        assert c.post("/api/sanctions", json={"action": "tempban", "name": "Alpha", "duration": "2d", "reason": "도배"}, headers=H).status_code == 200
        assert c.post("/api/sanctions", json={"action": "tempban", "name": "Alpha", "duration": "forever", "reason": "x"}, headers=H).status_code == 400
        ov = c.get("/api/sanctions").json()
        assert {b["name"] for b in ov["bans"]} == {"Alpha", "Gamma"}
        assert [w["name"] for w in ov["warnings"]][:1] in (["Beta"], ["Alpha"]) and ov["history"][0]["kind"] == "임시밴"
        c.post("/api/sanctions", json={"action": "unban", "name": "Alpha"}, headers=H)
        assert {b["name"] for b in c.get("/api/sanctions").json()["bans"]} == {"Gamma"}
        # 로그 줄 → 채팅 · 신고
        srv = app.state.server
        c.portal.call(srv._emit_line, "[21:00:00 INFO]: - Beta : 안녕")
        c.portal.call(srv._emit_line, "[21:00:01 INFO]: Beta issued server command: /신고 Gamma 핵 같아요")
        wait(lambda: c.get("/api/reports").json())
        rep = c.get("/api/reports").json()[0]
        assert rep["reporter"] == "Beta" and rep["target"] == "Gamma"
        assert c.put(f"/api/reports/{rep['id']}", json={"status": "done", "note": "밴 처리"}, headers=H).json()["ok"]
        assert c.get("/api/reports").json() == [] and c.get("/api/reports", params={"status": "done"}).json()[0]["handled_by"] == "boss"
        # 관리자 답장 (tellraw) → 채팅 기록에 admin 으로
        r = c.post("/api/chat", json={"message": "안녕하세요 관리자입니다"}, headers=H).json()
        assert r["channel"] == "admin" and r["name"] == "boss"
        assert c.post("/api/chat", json={"message": "귓말", "to": "Beta"}, headers=H).json()["channel"] == "whisper"
        assert c.post("/api/chat", json={"message": "x", "to": "a b"}, headers=H).status_code == 400
        names = [x["name"] for x in c.get("/api/chat").json()]
        assert names[:3] == ["boss", "boss", "Beta"]
        assert len(c.get("/api/chat", params={"name": "beta"}).json()) == 1
        # 금지어 설정은 관리자만
        assert c.put("/api/chat/settings", json={"words": ["욕"]}, headers=H).json()["words"] == ["욕"]
        acts = {a["action"] for a in c.get("/api/audit").json()}
        assert {"sanction.warn", "sanction.tempban", "sanction.unban", "report.done", "chat.say", "chat.settings"} <= acts
        c.post("/api/auth/logout", headers=H)
        c.post("/api/auth/login", json={"username": "look", "password": "look-pass1"})
        assert c.post("/api/sanctions", json={"action": "warn", "name": "Beta", "reason": "x"}, headers=H).status_code == 403
        assert c.put("/api/chat/settings", json={"words": []}, headers=H).status_code == 403
        assert c.get("/api/chat").status_code == 200
        c.post("/api/auth/logout", headers=H)
        c.post("/api/auth/login", json={"username": "boss", "password": "boss-pass1"})
        c.post("/api/server/stop", headers=H)
        wait(lambda: c.get("/api/status").json()["state"] == "stopped", 30)


# ---------------- 업데이트 ----------------

NEW_JAR = b"PK" + b"new-build" * 200_000


def fake_api(build=2490, md5=None, fail=False):
    md5 = md5 or hashlib.md5(NEW_JAR).hexdigest()

    def fetch(url, timeout=20):
        if fail:
            raise OSError("network down")
        if url.endswith("/purpur/1.21.11"):
            return json.dumps({"builds": {"latest": str(build), "all": [str(build - 1), str(build)]}}).encode()
        if url.endswith(f"/purpur/1.21.11/{build}"):
            return json.dumps({"build": str(build), "result": "SUCCESS", "timestamp": 1764000000000, "md5": md5,
                               "commits": [{"description": "Fix a thing\nlong body"}, {"description": "Upstream"}]}).encode()
        if "/projects/paper/versions/1.21.11/builds/latest" in url:
            return json.dumps({"id": 77, "time": "2026-11-20T10:00:00Z", "channel": "STABLE", "commits": [{"message": "Paper fix"}],
                               "downloads": {"server:default": {"name": "paper-1.21.11-77.jar", "url": "https://x/paper.jar",
                                                                "checksums": {"sha256": hashlib.sha256(NEW_JAR).hexdigest()}}}}).encode()
        raise AssertionError(url)
    return fetch


def make_updater(cfg, **kw):
    sd = cfg.path(cfg.server.dir)
    cfg.server.jar = "purpur.jar"
    (sd / "purpur.jar").write_bytes(b"PK" + b"old" * 1000)
    (sd / "version_history.json").write_text(json.dumps({"currentVersion": "git-Purpur-2485 (MC: 1.21.11)"}))

    class FakeServer:
        state, managed = "stopped", False
        notes, calls = [], []

        class rcon:
            configured = True

        async def reachable(self):
            return self.state == "running"

        async def stop(self):
            self.calls.append("stop")
            self.state = "stopped"

        async def start(self):
            self.calls.append("start")
            self.state = "running"

        def note(self, t):
            self.notes.append(t)

    s = Sink()
    up = Updater(cfg, DB(cfg.path("u.db")), FakeServer(), None, s.event, s.alert, fetch=fake_api(**kw))

    def download(info, dest):
        dest.write_bytes(NEW_JAR)
        if hashlib.new(info["algo"], NEW_JAR).hexdigest() != info["hash"]:
            raise UpdateError("내려받은 파일의 해시가 맞지 않습니다 (깨진 파일)")
    up._download_sync = download
    return up, s


def test_updater_check_install_rollback(cfg):
    up, s = make_updater(cfg)
    cur = up.current()
    assert (cur["project"], cur["mc"], cur["build"], cur["jar"]) == ("purpur", "1.21.11", 2485, "purpur.jar")
    chk = asyncio.run(up.check(notify=True))
    assert chk["available"] is True and chk["latest"]["build"] == 2490 and chk["latest"]["changes"] == ["Fix a thing", "Upstream"]
    asyncio.run(up.check(notify=True))
    assert [a[0] for a in s.alerts] == ["update_available"], "같은 빌드는 한 번만 알림"
    # 켜진 서버 업데이트: 끄고 → 바꾸고 → 다시 켬
    up.server.state = "running"
    assert "2490" in asyncio.run(up.update("boss"))
    sd = cfg.path(cfg.server.dir)
    assert (sd / "purpur.jar").read_bytes() == NEW_JAR and up.server.calls == ["stop", "start"]
    bk = up.jar_backups()
    assert len(bk) == 1 and bk[0]["build"] == 2485 and bk[0]["by"] == "boss"
    assert any(e[1]["done"] and e[1]["ok"] for e in s.events if e[0] == "update")
    # 되돌리기: 옛 jar 가 돌아오고, 바꾸기 전 jar 는 백업으로
    (sd / "version_history.json").write_text(json.dumps({"currentVersion": "git-Purpur-2490 (MC: 1.21.11)"}))
    asyncio.run(up.rollback(by="boss"))
    assert (sd / "purpur.jar").read_bytes().startswith(b"PKold")
    bk = up.jar_backups()
    assert len(bk) == 1 and bk[0]["build"] == 2490
    # 최신이면 업데이트 안 함
    with pytest.raises(UpdateError, match="최신"):
        (sd / "version_history.json").write_text(json.dumps({"currentVersion": "git-Purpur-2490 (MC: 1.21.11)"}))
        asyncio.run(up.update())


def test_updater_bad_hash_keeps_old_jar(cfg):
    up, _ = make_updater(cfg, md5="0" * 32)
    with pytest.raises(UpdateError, match="해시"):
        asyncio.run(up.update())
    assert cfg.path(cfg.server.dir).joinpath("purpur.jar").read_bytes().startswith(b"PKold") and up.server.calls == []
    assert not list(up.backup_dir.glob("*.tmp")) and up.busy is None


def test_updater_paper_and_errors(cfg):
    up, _ = make_updater(cfg)
    up.save_settings({"project": "paper"})
    chk = asyncio.run(up.check())
    assert chk["latest"]["build"] == 77 and chk["latest"]["algo"] == "sha256" and chk["available"] is True   # 다른 종류 → 해시 비교
    with pytest.raises(UpdateError):
        up.save_settings({"version": "latest"})
    up2, _ = make_updater(cfg, fail=True)
    with pytest.raises(UpdateError, match="연결"):
        asyncio.run(up2.check())
    cfg.server.jar = ""
    cfg.server.start_command = "run.bat"
    with pytest.raises(UpdateError, match="jar"):
        up2.jar()
    cfg.server.start_command = 'java -Xmx4G -jar "my server.jar" nogui'
    assert up2.jar().name == "my server.jar"
