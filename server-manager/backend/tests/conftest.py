import socket
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def free_port() -> int:
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    p = s.getsockname()[1]
    s.close()
    return p


@pytest.fixture
def cfg(tmp_path):
    from mcmanager.config import Config
    c = Config()
    c.base = tmp_path
    (tmp_path / "server").mkdir()
    gp, rp = free_port(), free_port()
    c.server.dir = "server"
    c.server.port, c.rcon.port, c.rcon.password = gp, rp, "pw-test"
    c.server.start_command = f'"{sys.executable}" -m mcmanager.simulator --port {gp} --rcon-port {rp} --password pw-test --boot 0.4'
    c.server.stop_timeout = 10
    c.server.auto_restart = False
    c.app.db = "t.db"
    c.app.sample_seconds = 1
    return c
