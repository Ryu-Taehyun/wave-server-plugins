"""
진단: 시스템 자원 · 시간대별 접속자 · 로그 속 오류를 묶어서 의심 플러그인 찾기
"""
from __future__ import annotations

import re
import shutil
import time
from collections import OrderedDict
from pathlib import Path

from .db import DB

EXC_RE = re.compile(r"((?:[a-z_$][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error|Throwable))(?::\s*(.*))?")
AT_RE = re.compile(r"^\s*at\s+([\w$.]+)\.[\w$<>]+\(")
TAG_RE = re.compile(r"\[(?:\d\d:\d\d:\d\d)\s+(ERROR|WARN)\]:?\s*(?:\[([\w\- ]+)\])?")
PASS_RE = re.compile(r"(?:Could not pass event \w+ to|Error occurred while (?:enabling|disabling)|Exception (?:handling|while) .*? in plugin|Plugin) ([\w\-]+)(?: v[\w.\-+]+)?")
JDK = ("java.", "javax.", "sun.", "jdk.", "net.minecraft.", "org.bukkit.", "io.papermc.", "com.destroystokyo.", "org.spigotmc.",
       "com.mojang.", "io.netty.", "org.purpurmc.", "it.unimi.", "com.google.", "org.apache.", "ca.spottedleaf.", "net.kyori.")


def system(path: Path) -> dict:
    out: dict = {}
    try:
        import psutil
        out["sys_cpu"] = psutil.cpu_percent(None)
        vm = psutil.virtual_memory()
        out["sys_mem"] = vm.percent
        out["mem_total_gb"] = round(vm.total / 1073741824, 1)
        out["mem_used_gb"] = round((vm.total - vm.available) / 1073741824, 1)
    except Exception:
        pass
    try:
        u = shutil.disk_usage(path if path.exists() else Path("."))
        out["disk_free_gb"] = round(u.free / 1073741824, 1)
        out["disk_total_gb"] = round(u.total / 1073741824, 1)
    except OSError:
        pass
    return out


def heatmap(db: DB, days: int = 28) -> list[dict]:
    """요일(0 = 일요일) × 시간별 평균 · 최고 접속자"""
    return db.q("""
        SELECT CAST(strftime('%w', ts, 'unixepoch', 'localtime') AS INTEGER) AS dow,
               CAST(strftime('%H', ts, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               ROUND(AVG(players), 2) AS avg,
               MAX(players)           AS peak
        FROM metrics WHERE ts >= ? AND online = 1
        GROUP BY dow, hour""", (int(time.time()) - days * 86400,))


def plugin_packages(plugins: list[dict]) -> dict[str, str]:
    """플러그인 main 클래스의 패키지 → 플러그인 이름"""
    out = {}
    for p in plugins:
        main = p.get("main") or ""
        if "." in main:
            pkg = ".".join(main.split(".")[:-1])
            parts = pkg.split(".")
            out[".".join(parts[:3]) if len(parts) >= 3 else pkg] = p.get("name") or p.get("file")
    return out


def suspect(frames: list[str], tag_plugin: str | None, pkgs: dict[str, str]) -> str | None:
    if tag_plugin:
        return tag_plugin
    for f in frames:
        if f.startswith(JDK):
            continue
        for pkg, name in pkgs.items():
            if f.startswith(pkg):
                return name
        return ".".join(f.split(".")[:3])   # 모르는 패키지: 앞부분이라도
    return None


def errors(lines: list[str], plugins: list[dict], limit: int = 30) -> list[dict]:
    pkgs = plugin_packages(plugins)
    groups: "OrderedDict[str, dict]" = OrderedDict()
    i, n = 0, len(lines)
    while i < n:
        line = lines[i]
        tag = TAG_RE.search(line)
        exc = EXC_RE.search(line)
        if not (tag or (exc and "Exception" in line)):
            i += 1
            continue
        level = tag.group(1) if tag else "ERROR"
        tag_plugin = tag.group(2) if tag and tag.group(2) and tag.group(2) not in ("STDOUT", "STDERR", "Server", "Manager") else None
        pm = PASS_RE.search(line)
        if pm:
            tag_plugin = pm.group(1)
        block = [line]
        frames: list[str] = []
        j = i + 1
        while j < n and (lines[j].startswith(("\tat ", "    at ", "Caused by", "\t... ", "    ... ")) or AT_RE.match(lines[j]) or (exc is None and EXC_RE.search(lines[j]) and j == i + 1)):
            block.append(lines[j])
            m = AT_RE.match(lines[j])
            if m:
                frames.append(m.group(1))
            if exc is None:
                exc = EXC_RE.search(lines[j])
            j += 1
        if level == "WARN" and not exc and not frames:   # 단순 경고는 건너뜀
            i = j
            continue
        name = exc.group(1).split(".")[-1] if exc else "오류"
        msg = (exc.group(2) or "") if exc else re.sub(r"^\[[^\]]+\]:?\s*", "", line)
        who = suspect(frames, tag_plugin, pkgs)
        key = f"{who}|{name}|{msg[:80]}"
        g = groups.get(key)
        tm = re.match(r"^\[(\d\d:\d\d:\d\d)", line)
        if g:
            g["count"] += 1
            g["last"] = tm.group(1) if tm else g["last"]
            groups.move_to_end(key)
        else:
            groups[key] = {"plugin": who, "exception": name, "message": msg[:300], "level": level, "count": 1,
                           "last": tm.group(1) if tm else "", "sample": block[:14]}
        i = j
    return list(reversed(list(groups.values())))[:limit]


def crash_reports(server_dir: Path, limit: int = 5) -> list[dict]:
    d = server_dir / "crash-reports"
    if not d.is_dir():
        return []
    out = []
    for f in sorted(d.glob("*.txt"), key=lambda p: p.stat().st_mtime, reverse=True)[:limit]:
        desc = ""
        try:
            for line in f.read_text(encoding="utf-8", errors="replace").splitlines()[:40]:
                if line.startswith("Description:"):
                    desc = line.split(":", 1)[1].strip()
                    break
        except OSError:
            pass
        out.append({"file": f.name, "mtime": int(f.stat().st_mtime), "description": desc})
    return out
