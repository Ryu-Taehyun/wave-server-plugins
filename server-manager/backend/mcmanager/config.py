"""설정: manager.toml (없으면 기본값), 환경 변수 MCM_* 가 우선"""
from __future__ import annotations

import os
import tomllib
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class ServerCfg:
    dir: str = "."
    start_command: str = "java -Xms2G -Xmx4G -jar server.jar nogui"
    host: str = "127.0.0.1"         # 접속 확인(서버 목록 핑) 주소
    port: int = 25565
    stop_timeout: int = 90          # stop 후 이 시간(초) 안에 안 꺼지면 강제 종료
    auto_restart: bool = True       # 비정상 종료(크래시) 시 자동 재시작 (10분에 3번까지)
    log_file: str = "logs/latest.log"   # 이 도구가 켠 서버가 아닐 때 읽을 로그


@dataclass
class RconCfg:
    host: str = "127.0.0.1"
    port: int = 25575
    password: str = ""


@dataclass
class WebCfg:
    host: str = "0.0.0.0"           # 다른 기기에서 접속하려면 0.0.0.0
    port: int = 8080
    secure_cookies: bool = False    # HTTPS(리버스 프록시 · 터널) 뒤에서 쓰면 true
    session_days: int = 14
    trusted_proxy: bool = False     # true 면 X-Forwarded-For 의 IP 를 기록


@dataclass
class AppCfg:
    db: str = "data/manager.db"
    sample_seconds: int = 10        # 상태 기록 간격
    keep_days: int = 30             # 상태 기록 보관 일수


@dataclass
class Config:
    server: ServerCfg = field(default_factory=ServerCfg)
    rcon: RconCfg = field(default_factory=RconCfg)
    web: WebCfg = field(default_factory=WebCfg)
    app: AppCfg = field(default_factory=AppCfg)
    base: Path = Path(".")

    def path(self, p: str) -> Path:
        q = Path(p)
        return q if q.is_absolute() else (self.base / q)


def _apply(obj, data: dict) -> None:
    for k, v in data.items():
        k = k.replace("-", "_")
        if hasattr(obj, k):
            cur = getattr(obj, k)
            setattr(obj, k, type(cur)(v) if not isinstance(cur, bool) else bool(v))


def load(path: str | os.PathLike | None = None) -> Config:
    cfg = Config()
    p = Path(path or os.environ.get("MCM_CONFIG", "manager.toml"))
    cfg.base = p.resolve().parent
    if p.exists():
        data = tomllib.loads(p.read_text(encoding="utf-8"))
        for section in ("server", "rcon", "web", "app"):
            _apply(getattr(cfg, section), data.get(section, {}))
    # 환경 변수 (비밀번호는 파일 대신 여기에 두어도 됨): MCM_RCON_PASSWORD, MCM_SERVER_DIR ...
    for section in ("server", "rcon", "web", "app"):
        obj = getattr(cfg, section)
        for k in list(vars(obj)):
            env = os.environ.get(f"MCM_{section}_{k}".upper())
            if env is not None:
                cur = getattr(obj, k)
                setattr(obj, k, env.lower() in ("1", "true", "yes") if isinstance(cur, bool) else type(cur)(env))
    return cfg
