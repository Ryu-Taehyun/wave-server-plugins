import os
import time

from fastapi.testclient import TestClient

from mcmanager import auth
from mcmanager.app import create_app

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
H = {"X-MCM": "1"}


def wait(client, cond, secs=15):
    end = time.time() + secs
    while time.time() < end:
        s = client.get("/api/status").json()
        if cond(s):
            return s
        time.sleep(0.25)
    raise AssertionError(f"시간 초과: {s}")


def test_full_flow(cfg, monkeypatch):
    monkeypatch.setenv("PYTHONPATH", ROOT)
    app = create_app(cfg)
    db = app.state.db
    auth.create_user(db, "boss", "boss-pass1", "admin")
    auth.create_user(db, "watch", "watch-pass1", "viewer")
    with TestClient(app) as c:
        assert c.get("/api/status").status_code == 401, "로그인 전에는 못 봄"
        assert c.post("/api/auth/login", json={"username": "boss", "password": "x"}).status_code == 401
        r = c.post("/api/auth/login", json={"username": "boss", "password": "boss-pass1"})
        assert r.status_code == 200 and "mcm_session" in r.cookies
        assert c.post("/api/server/start").status_code == 403, "X-MCM 머리글 없이 상태 변경 불가"
        assert c.post("/api/server/start", headers=H).json()["ok"]
        s = wait(c, lambda s: s["online"] and s["state"] == "running" and s["players_online"] > 0)
        assert s["managed"] and s["rcon"] and s["tps"] and len(s["players"]) == s["players_online"]
        # 콘솔
        out = c.post("/api/console", json={"command": "/list"}, headers=H).json()["output"]
        assert "players online" in out
        # 플레이어 강퇴
        name = s["players"][0]
        assert "Kicked" in c.post(f"/api/players/{name}/kick", json={"reason": "test"}, headers=H).json()["output"]
        assert c.post("/api/players/bad;name/ban", json={}, headers=H).status_code == 400
        # 예약 작업
        t = c.post("/api/tasks", json={"name": "공지", "kind": "broadcast", "payload": "안녕", "schedule": "interval", "every_min": 60}, headers=H).json()
        assert t["next_run"] > time.time()
        assert c.post("/api/tasks", json={"name": "x", "kind": "restart", "schedule": "daily", "at_time": "25:00"}, headers=H).status_code == 400
        assert c.post(f"/api/tasks/{t['id']}/run", headers=H).json()["ok"]
        assert c.get(f"/api/tasks/{t['id']}/runs").json()[0]["ok"] == 1
        # 기록 · 접속 기록 · 감사
        time.sleep(1.5)
        assert len(c.get("/api/metrics?range=1h").json()) >= 1
        assert c.get("/api/players").json()["recent"]
        actions = [a["action"] for a in c.get("/api/audit").json()]
        assert "server.start" in actions and "console" in actions and "player.kick" in actions and "login" in actions
        # 기기
        assert len(c.get("/api/auth/sessions").json()) == 1
        # 끄기
        assert c.post("/api/server/stop", headers=H).json()["ok"]
        wait(c, lambda s: s["state"] == "stopped" and not s["online"])
        # 사용자 관리 · 권한
        assert c.post("/api/users", json={"username": "op1", "password": "op-pass12", "role": "operator"}, headers=H).status_code == 200
        c.post("/api/auth/logout", headers=H)
        assert c.get("/api/status").status_code == 401
        c.post("/api/auth/login", json={"username": "watch", "password": "watch-pass1"})
        assert c.get("/api/status").status_code == 200
        assert c.post("/api/server/start", headers=H).status_code == 403, "보기 권한은 서버 조작 불가"
        assert c.get("/api/users").status_code == 403


def test_websocket_requires_login(cfg):
    app = create_app(cfg)
    auth.create_user(app.state.db, "boss", "boss-pass1", "admin")
    with TestClient(app) as c:
        import pytest
        from starlette.websockets import WebSocketDisconnect
        with pytest.raises(WebSocketDisconnect):
            with c.websocket_connect("/api/ws") as ws:
                ws.receive_text()
        c.post("/api/auth/login", json={"username": "boss", "password": "boss-pass1"})
        with c.websocket_connect("/api/ws") as ws:
            import json
            hello = json.loads(ws.receive_text())
            assert hello["type"] == "hello" and "status" in hello["data"] and isinstance(hello["data"]["logs"], list)
