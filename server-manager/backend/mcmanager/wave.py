"""
WaveCore 연동: plugins/WaveCore 의 playerdata/*.yml · global.yml · config.yml 을 직접 읽음 (서버가 꺼져 있어도 됨)
조작은 RCON 명령: 핫타임 시작/종료, 점검모드 켜기/끄기, wave reload, whitelist ...
플레이어 파일에는 인벤토리 · 우편함이 들어 있어 커서, 필요한 맨 윗줄 값만 빠르게 읽음 (파일이 바뀐 것만 다시 읽음)
"""
from __future__ import annotations

import re
import time
from pathlib import Path

import yaml

from .config import Config
from .rcon import RconClient, RconError, strip_colors

SCALARS = {"name", "money", "money-total", "exmoney", "exmoney-total", "level", "exp", "kills", "attend-streak", "attend-total",
           "last-seen", "warnings", "active-title", "mute-until", "mute-reason", "nick-color", "newbie-start"}
LISTS = {"titles", "sanctions", "ach-claimed", "ach-reached", "ranks"}
MAPS = {"stats"}


def _scalar(v: str):
    v = v.strip()
    if v in ("", "null", "~"):
        return None
    if (v.startswith("'") and v.endswith("'")) or (v.startswith('"') and v.endswith('"')):
        return v[1:-1].replace("''", "'")
    try:
        return int(v)
    except ValueError:
        try:
            return float(v)
        except ValueError:
            return v


def parse_player(text: str) -> dict:
    out: dict = {}
    cur_list: str | None = None
    cur_map: str | None = None
    for line in text.splitlines():
        if not line or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        s = line.strip()
        if indent == 0 and not s.startswith("- "):
            cur_list = cur_map = None
            if ":" not in s:
                continue
            k, _, v = s.partition(":")
            if k in SCALARS:
                out[k] = _scalar(v)
            elif k in LISTS:
                v = v.strip()
                out[k] = [] if v in ("", "[]") else [x.strip() for x in v.strip("[]").split(",") if x.strip()]
                cur_list = k if v == "" else None
            elif k in MAPS:
                out[k] = {}
                cur_map = k
        elif cur_list and s.startswith("- ") and indent <= 2:
            out[cur_list].append(_scalar(s[2:]))
        elif cur_map and indent == 2 and ":" in s:
            k, _, v = s.partition(":")
            out[cur_map][k.strip()] = _scalar(v)
    return out


def strip_mm(s) -> str:
    """&a · <gradient:...> 같은 꾸밈 제거"""
    if not isinstance(s, str):
        return ""
    s = re.sub(r"<[^>]{1,60}>", "", s)
    s = re.sub(r"[&§][0-9a-fk-orx]", "", s, flags=re.I)
    return s.strip()


class Wave:
    def __init__(self, cfg: Config, rcon: RconClient):
        self.cfg, self.rcon = cfg, rcon
        self._cache: dict[str, tuple[float, dict]] = {}
        self._scan_at = 0.0
        self._players: list[dict] = []

    @property
    def base(self) -> Path:
        return self.cfg.path(self.cfg.server.dir) / self.cfg.wave.plugin_dir

    @property
    def available(self) -> bool:
        return (self.base / "playerdata").is_dir() or (self.base / "config.yml").is_file()

    # ---------------- 플레이어 데이터 ----------------

    def players(self, max_age: float = 30) -> list[dict]:
        if time.time() - self._scan_at < max_age:
            return self._players
        out = []
        d = self.base / "playerdata"
        if d.is_dir():
            for f in d.glob("*.yml"):
                try:
                    mt = f.stat().st_mtime
                except OSError:
                    continue
                c = self._cache.get(f.name)
                if not c or c[0] != mt:
                    try:
                        data = parse_player(f.read_text(encoding="utf-8", errors="replace"))
                    except OSError:
                        continue
                    data["uuid"] = f.stem
                    c = (mt, data)
                    self._cache[f.name] = c
                if c[1].get("name"):
                    out.append(c[1])
        self._players, self._scan_at = out, time.time()
        return out

    @staticmethod
    def _num(p: dict, k: str) -> float:
        v = p.get(k)
        return v if isinstance(v, (int, float)) else 0

    def overview(self) -> dict:
        ps = self.players()
        money = [self._num(p, "money") for p in ps]
        top = lambda key, n=10: [{"name": p["name"], "value": self._num(p, key), "level": self._num(p, "level")}
                                 for p in sorted(ps, key=lambda p: self._num(p, key), reverse=True)[:n] if self._num(p, key) > 0]
        week = time.time() * 1000 - 7 * 86400 * 1000
        return {
            "available": self.available,
            "players": len(ps),
            "active_7d": sum(1 for p in ps if self._num(p, "last-seen") >= week),
            "economy": {"total": sum(money), "avg": round(sum(money) / len(money)) if money else 0,
                        "paid_total": sum(self._num(p, "exmoney") for p in ps), "richest": max(money) if money else 0},
            "top_money": top("money"), "top_kills": top("kills"), "top_level": top("level"),
            "season": self.season(), "maintenance": self.maintenance(), "hot_time": self.hot_time_config(),
            "achievements": sum(len(p.get("ach-claimed") or []) for p in ps),
        }

    def player(self, name: str) -> dict | None:
        for p in self.players(max_age=5):
            if str(p.get("name", "")).lower() == name.lower():
                d = dict(p)
                d["active_title_text"] = strip_mm(d.get("active-title"))
                d["sanctions"] = [str(s) for s in (d.get("sanctions") or [])][-20:]
                d["achievements"] = len(d.get("ach-claimed") or [])
                return d
        return None

    # ---------------- 설정 · 상태 파일 ----------------

    def _config_text(self) -> str:
        p = self.base / "config.yml"
        return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""

    def _config(self) -> dict:
        try:
            loader = type("Loose", (yaml.SafeLoader,), {})
            loader.add_multi_constructor("", lambda l, s, n: None)
            return yaml.load(self._config_text(), Loader=loader) or {}
        except yaml.YAMLError:
            return {}

    def season(self) -> int | None:
        sr = self._config().get("season-reset") or {}
        n = sr.get("number") if isinstance(sr, dict) else None
        return int(n) if isinstance(n, int) else None

    def hot_time_config(self) -> dict:
        h = self._config().get("hot-time") or {}
        return {"enabled": bool(h.get("enabled", False)), "multiplier": h.get("multiplier"), "schedule": h.get("schedule") or []} if isinstance(h, dict) else {}

    def maintenance(self) -> bool | None:
        p = self.base / "global.yml"
        if not p.exists():
            return None
        m = re.search(r"^maintenance:\s*(true|false)", p.read_text(encoding="utf-8", errors="replace"), re.M)
        return m.group(1) == "true" if m else False

    def set_season(self, n: int) -> None:
        """config.yml 의 season-reset.number 만 바꿈 (주석 · 다른 값 그대로)"""
        p = self.base / "config.yml"
        text = self._config_text()
        lines = text.splitlines(keepends=True)
        start = next((i for i, l in enumerate(lines) if re.match(r"^season-reset:\s*(#.*)?$", l)), None)
        if start is None:
            text = text.rstrip("\n") + f"\nseason-reset:\n  number: {n}\n"
        else:
            end = next((i for i in range(start + 1, len(lines)) if lines[i].strip() and not lines[i].startswith((" ", "#", "\t"))), len(lines))
            for i in range(start + 1, end):
                if re.match(r"^\s+number:", lines[i]):
                    lines[i] = re.sub(r"(number:\s*)\d+", rf"\g<1>{n}", lines[i])
                    break
            else:
                lines.insert(start + 1, f"  number: {n}\n")
            text = "".join(lines)
        tmp = p.with_name("config.yml.mcm-tmp")
        tmp.write_text(text, encoding="utf-8")
        tmp.replace(p)

    # ---------------- RCON 조작 ----------------

    async def cmd(self, c: str) -> str:
        try:
            return strip_colors(await self.rcon.command(c))
        except RconError as e:
            raise RuntimeError(f"RCON 연결 실패 (서버가 켜져 있어야 합니다): {e}") from e

    async def hot_time_status(self) -> dict:
        try:
            out = await self.cmd("핫타임")
        except RuntimeError:
            return {"active": None, "text": ""}
        active = "진행 중" in out
        left = re.search(r"남은 시간\s*([\d:]+)", out)
        mult = re.search(r"x\s*([\d.]+)", out)
        return {"active": active, "left": left.group(1) if left else None, "multiplier": float(mult.group(1)) if mult else None,
                "text": " ".join(l.strip() for l in out.splitlines() if l.strip())[:300]}

    async def whitelist(self) -> dict:
        props = self.cfg.path(self.cfg.server.dir) / "server.properties"
        on = False
        if props.exists():
            m = re.search(r"^white-list\s*=\s*(true|false)", props.read_text(encoding="utf-8", errors="replace"), re.M)
            on = bool(m and m.group(1) == "true")
        names: list[str] = []
        try:
            out = await self.cmd("whitelist list")
            if ":" in out:
                names = [n.strip() for n in out.split(":", 1)[1].replace(" and ", ",").split(",") if n.strip()]
        except RuntimeError:
            pass
        return {"on": on, "names": names}
