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
    jar: str = ""                   # 서버 jar (자동 업데이트용, 비우면 start_command 의 -jar 뒤 파일)


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
class BackupCfg:
    dir: str = "data/backups"           # 백업 zip 저장 폴더
    keep: int = 7                       # 자동 백업 보관 개수 (직접 만든 백업은 지우지 않음)
    include: list = field(default_factory=lambda: ["world", "world_nether", "world_the_end", "plugins", "config",
                                                   "server.properties", "bukkit.yml", "spigot.yml", "purpur.yml",
                                                   "ops.json", "whitelist.json", "banned-players.json", "banned-ips.json"])
    exclude: list = field(default_factory=lambda: ["plugins/*/logs", "plugins/*/backups", "plugins/dynmap/web/tiles", "plugins/BlueMap/web/maps", "*.lock"])
    copy_to: str = ""                   # 다른 곳(외장 디스크 · 동기화 폴더)에도 사본 저장 (비우면 안 함)


@dataclass
class NotifyCfg:
    discord_webhook: str = ""           # 화면(설정 → 알림)에서도 바꿀 수 있음
    push_contact: str = "mailto:admin@example.com"   # 웹 푸시 연락처 (푸시 서비스에 보내는 값, 아무 메일 형식)


@dataclass
class WaveCfg:
    plugin_dir: str = "plugins/WaveCore"   # 서버 폴더 기준 WaveCore 데이터 폴더


@dataclass
class Config:
    server: ServerCfg = field(default_factory=ServerCfg)
    rcon: RconCfg = field(default_factory=RconCfg)
    web: WebCfg = field(default_factory=WebCfg)
    app: AppCfg = field(default_factory=AppCfg)
    backup: BackupCfg = field(default_factory=BackupCfg)
    notify: NotifyCfg = field(default_factory=NotifyCfg)
    wave: WaveCfg = field(default_factory=WaveCfg)
    base: Path = Path(".")

    def path(self, p: str) -> Path:
        q = Path(p)
        return q if q.is_absolute() else (self.base / q)


INSTALL_DIR = Path(__file__).resolve().parents[2]   # server-manager 폴더 (서버 폴더 안에 넣어도 백업 · 파일 편집에서 빠짐)


def is_manager_path(p: Path) -> bool:
    p = p.resolve()
    return p == INSTALL_DIR or INSTALL_DIR in p.parents


SECTIONS = ("server", "rcon", "web", "app", "backup", "notify", "wave")


def _apply(obj, data: dict) -> None:
    for k, v in data.items():
        k = k.replace("-", "_")
        if hasattr(obj, k):
            cur = getattr(obj, k)
            setattr(obj, k, v if isinstance(cur, list) else type(cur)(v) if not isinstance(cur, bool) else bool(v))


def load(path: str | os.PathLike | None = None) -> Config:
    cfg = Config()
    p = Path(path or os.environ.get("MCM_CONFIG", "manager.toml"))
    cfg.base = p.resolve().parent
    if p.exists():
        try:
            raw = p.read_bytes()
            try:
                text = raw.decode("utf-8-sig")
            except UnicodeDecodeError:   # 메모장에서 ANSI 로 저장한 경우
                text = raw.decode("cp949", errors="replace")
            data = tomllib.loads(text)
        except tomllib.TOMLDecodeError as e:
            msg = str(e)
            hint = ""
            if "twice" in msg or "already" in msg:
                hint = "\n  → 같은 [구역] (예: [server]) 이 두 번 있습니다. 하나로 합치세요."
            elif "Unescaped" in msg:
                hint = "\n  → 경로의 \\ 를 / 로 바꾸세요 (예: dir = \"C:/Users/이름/Desktop/s\"). 또는 작은따옴표로: dir = 'C:\\Users\\이름\\Desktop\\s'"
            elif "Invalid" in msg or "Expected" in msg:
                hint = "\n  → 따옴표 · 경로를 확인하세요. 경로는 C:/mc/server 처럼 / 로, start_command 는 작은따옴표 '...' 로 감싸고 한 줄로."
            raise SystemExit(f"[설정 오류] {p.resolve()} 를 읽지 못했습니다: {msg}{hint}")
        for section in SECTIONS:
            _apply(getattr(cfg, section), data.get(section, {}))
    # 환경 변수 (비밀번호는 파일 대신 여기에 두어도 됨): MCM_RCON_PASSWORD, MCM_SERVER_DIR ...
    for section in SECTIONS:
        obj = getattr(cfg, section)
        for k in list(vars(obj)):
            env = os.environ.get(f"MCM_{section}_{k}".upper())
            if env is not None and not isinstance(getattr(obj, k), list):
                cur = getattr(obj, k)
                setattr(obj, k, env.lower() in ("1", "true", "yes") if isinstance(cur, bool) else type(cur)(env))
    return cfg
