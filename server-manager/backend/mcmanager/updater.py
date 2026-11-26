"""
서버 자동 업데이트 (Purpur · Paper)
- 지금 버전: 서버 폴더의 version_history.json (Paper · Purpur 가 켤 때 적음) + jar 해시
- 새 빌드 확인: Purpur API v2 / Paper Fill API v3
- 업데이트: (백업) → 내려받기 · 해시 확인 → 서버 끄기 → 옛 jar 를 data/jar-backup 으로 → 바꾸기 → 다시 켜기
- 되돌리기: data/jar-backup 의 직전 jar 로 같은 순서
"""
from __future__ import annotations

import asyncio
import hashlib
import json
import re
import shutil
import time
import urllib.request
from pathlib import Path

from .config import Config
from .db import DB
from .process import split_command

PURPUR = "https://api.purpurmc.org/v2/purpur"
FILL = "https://fill.papermc.io/v3/projects/paper"
UA = "mc-server-manager (self-hosted)"
VER_RE = re.compile(r"(\d+\.\d+(?:\.\d+)?)")
KEEP = 5


class UpdateError(Exception):
    pass


def http_get(url: str, timeout: float = 20) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json, */*"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def file_hash(p: Path, algo: str) -> str:
    h = hashlib.new(algo)
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


class Updater:
    def __init__(self, cfg: Config, db: DB, server, backups=None, on_event=None, on_alert=None, fetch=http_get):
        self.cfg, self.db, self.server, self.backups = cfg, db, server, backups
        self.on_event, self.on_alert, self.fetch = on_event, on_alert, fetch
        self.busy: str | None = None
        self.last_check: dict | None = None

    # ---------------- 경로 · 설정 ----------------

    @property
    def server_dir(self) -> Path:
        return self.cfg.path(self.cfg.server.dir)

    @property
    def backup_dir(self) -> Path:
        d = self.cfg.path("data/jar-backup")
        d.mkdir(parents=True, exist_ok=True)
        return d

    def jar(self) -> Path:
        if self.cfg.server.jar:
            p = Path(self.cfg.server.jar)
            return p if p.is_absolute() else self.server_dir / p
        args = split_command(self.cfg.server.start_command)
        for i, a in enumerate(args[:-1]):
            if a == "-jar":
                p = Path(args[i + 1])
                return p if p.is_absolute() else self.server_dir / p
        raise UpdateError("시작 명령에서 -jar 파일을 찾지 못했습니다 (manager.toml [server] 에 jar = purpur.jar 처럼 적어 주세요)")

    def settings(self) -> dict:
        raw = json.loads(self.db.setting("updater", "{}") or "{}")
        cur = self.current()
        return {"project": raw.get("project") or cur.get("project") or "purpur", "version": raw.get("version") or cur.get("mc") or "",
                "auto_check": raw.get("auto_check", True), "backup_first": raw.get("backup_first", True)}

    def save_settings(self, data: dict) -> dict:
        s = self.settings()
        if data.get("project") in ("purpur", "paper"):
            s["project"] = data["project"]
        if "version" in data:
            v = str(data["version"] or "").strip()
            if v and not re.fullmatch(r"\d+\.\d+(\.\d+)?", v):
                raise UpdateError("마인크래프트 버전은 1.21.11 처럼 적어 주세요")
            s["version"] = v
        for k in ("auto_check", "backup_first"):
            if k in data:
                s[k] = bool(data[k])
        self.db.set_setting("updater", json.dumps(s))
        return s

    # ---------------- 지금 버전 ----------------

    def current(self) -> dict:
        out: dict = {"project": None, "mc": None, "build": None, "raw": None}
        p = self.server_dir / "version_history.json"
        if p.exists():
            try:
                raw = json.loads(p.read_text(encoding="utf-8")).get("currentVersion") or ""
            except (OSError, ValueError):
                raw = ""
            out["raw"] = raw or None
            low = raw.lower()
            out["project"] = "purpur" if "purpur" in low else "paper" if "paper" in low else None
            m = re.search(r"\(MC: ([\d.]+)\)", raw)
            out["mc"] = m.group(1) if m else None
            m = re.search(r"purpur-(\d+)", raw, re.I) or re.search(r"^(?:git-Paper-|[\d.]+-)(\d+)", raw)
            out["build"] = int(m.group(1)) if m else None
        try:
            jar = self.jar()
            out["jar"] = jar.name
            out["jar_exists"] = jar.exists()
            if jar.exists():
                st = jar.stat()
                out["jar_size"], out["jar_mtime"] = st.st_size, int(st.st_mtime)
        except UpdateError as e:
            out["jar"], out["jar_error"] = None, str(e)
        return out

    # ---------------- 새 빌드 ----------------

    def _latest_sync(self, project: str, version: str) -> dict:
        if not version:
            raise UpdateError("마인크래프트 버전을 알 수 없습니다. 서버를 한 번 켜거나 설정에서 버전을 적어 주세요")
        try:
            if project == "purpur":
                info = json.loads(self.fetch(f"{PURPUR}/{version}"))
                build = str(info["builds"]["latest"])
                b = json.loads(self.fetch(f"{PURPUR}/{version}/{build}"))
                if b.get("result") not in (None, "SUCCESS"):
                    raise UpdateError(f"최신 빌드 #{build} 가 실패한 빌드입니다")
                return {"project": project, "version": version, "build": int(build), "time": int(b.get("timestamp", 0)) // 1000,
                        "changes": [c.get("description", "").split("\n")[0] for c in (b.get("commits") or [])][:10],
                        "hash": b.get("md5"), "algo": "md5", "url": f"{PURPUR}/{version}/{build}/download", "file": f"purpur-{version}-{build}.jar"}
            b = json.loads(self.fetch(f"{FILL}/versions/{version}/builds/latest"))
            dl = (b.get("downloads") or {}).get("server:default") or {}
            if not dl.get("url"):
                raise UpdateError("내려받기 주소가 없습니다")
            t = b.get("time") or ""
            try:
                ts = int(time.mktime(time.strptime(t[:19], "%Y-%m-%dT%H:%M:%S")))
            except ValueError:
                ts = 0
            return {"project": project, "version": version, "build": int(b["id"]), "time": ts, "channel": b.get("channel"),
                    "changes": [c.get("message", "").split("\n")[0] for c in (b.get("commits") or [])][:10],
                    "hash": (dl.get("checksums") or {}).get("sha256"), "algo": "sha256", "url": dl["url"], "file": dl.get("name") or f"paper-{version}-{b['id']}.jar"}
        except UpdateError:
            raise
        except urllib.error.HTTPError as e:
            raise UpdateError(f"버전 {version} 을 찾지 못했습니다 (HTTP {e.code})" if e.code == 404 else f"업데이트 서버 오류 (HTTP {e.code})")
        except Exception as e:
            raise UpdateError(f"업데이트 서버에 연결하지 못했습니다: {e}")

    async def check(self, notify: bool = False) -> dict:
        s = self.settings()
        cur = self.current()
        latest = await asyncio.to_thread(self._latest_sync, s["project"], s["version"])
        same_project = (cur.get("project") or s["project"]) == s["project"]
        available = None
        if cur.get("build") and same_project and cur.get("mc") == s["version"]:
            available = latest["build"] > cur["build"]
        elif cur.get("jar_exists") and latest.get("hash"):
            available = await asyncio.to_thread(file_hash, self.jar(), latest["algo"]) != latest["hash"]
        self.last_check = {"ts": int(time.time()), "current": cur, "latest": latest, "available": available}
        self.db.set_setting("updater_last", json.dumps(self.last_check, ensure_ascii=False))
        if notify and available and self.on_alert:
            seen = self.db.setting("updater_notified")
            key = f"{latest['project']}-{latest['version']}-{latest['build']}"
            if seen != key:
                self.db.set_setting("updater_notified", key)
                await self.on_alert("update_available", f"새 서버 빌드: {latest['project'].title()} {latest['version']} #{latest['build']}",
                                    "\n".join("· " + c for c in latest["changes"][:5]) or "관리 화면 → 업데이트에서 설치할 수 있습니다")
        return self.last_check

    def status(self) -> dict:
        if self.last_check is None:
            raw = self.db.setting("updater_last")
            self.last_check = json.loads(raw) if raw else None
        return {"settings": self.settings(), "current": self.current(), "last_check": self.last_check, "busy": self.busy,
                "backups": self.jar_backups(), "history": self.db.q("SELECT * FROM audit WHERE action LIKE 'update.%' ORDER BY id DESC LIMIT 20")}

    def jar_backups(self) -> list[dict]:
        out = []
        for f in sorted(self.backup_dir.glob("*.jar"), key=lambda f: f.stat().st_mtime, reverse=True):
            meta = {}
            m = f.with_suffix(".json")
            if m.exists():
                try:
                    meta = json.loads(m.read_text(encoding="utf-8"))
                except ValueError:
                    pass
            out.append({"file": f.name, "size": f.stat().st_size, "ts": int(f.stat().st_mtime), **meta})
        return out

    # ---------------- 설치 · 되돌리기 ----------------

    async def _progress(self, phase: str, pct: float | None = None, done: bool = False, ok: bool = True) -> None:
        if self.on_event:
            await self.on_event("update", {"phase": phase, "pct": pct, "done": done, "ok": ok})
        self.server.note(f"[업데이트] {phase}")

    def _download_sync(self, info: dict, dest: Path) -> None:
        req = urllib.request.Request(info["url"], headers={"User-Agent": UA})
        h = hashlib.new(info["algo"]) if info.get("hash") else None
        with urllib.request.urlopen(req, timeout=60) as r, open(dest, "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
                if h:
                    h.update(chunk)
        if dest.stat().st_size < 1_000_000:
            raise UpdateError("내려받은 파일이 너무 작습니다 (서버 jar 가 아닌 것 같습니다)")
        if h and h.hexdigest() != info["hash"]:
            raise UpdateError("내려받은 파일의 해시가 맞지 않습니다 (깨진 파일)")

    async def _swap(self, new_file: Path, label: dict, by: str | None) -> str:
        """서버 끄기 → 지금 jar 를 jar-backup 으로 → new_file 을 jar 자리에 → (켜져 있었으면) 다시 켜기"""
        jar = self.jar()
        was_running = self.server.state in ("running", "starting") or await self.server.reachable()
        if was_running and not self.server.managed and not self.server.rcon.configured:
            raise UpdateError("이 도구가 켜지 않은 서버이고 RCON 도 없어 끌 수 없습니다")
        if was_running:
            await self._progress("서버를 끄는 중", 70)
            await self.server.stop()
        if jar.exists():
            cur = self.current()
            stamp = time.strftime("%Y%m%d-%H%M%S")
            bk = self.backup_dir / f"{jar.stem}.{stamp}.jar"
            n = 1
            while bk.exists():
                n += 1
                bk = self.backup_dir / f"{jar.stem}.{stamp}-{n}.jar"
            shutil.move(str(jar), bk)
            bk.with_suffix(".json").write_text(json.dumps({"project": cur.get("project"), "mc": cur.get("mc"), "build": cur.get("build"),
                                                           "raw": cur.get("raw"), "by": by}, ensure_ascii=False), encoding="utf-8")
        shutil.move(str(new_file), jar)
        self._prune()
        await self._progress("jar 교체 완료", 85)
        if was_running:
            await self._progress("서버를 켜는 중", 90)
            await self.server.start()
        return f"{label.get('project', '').title()} {label.get('version') or label.get('mc') or ''} #{label.get('build') or '?'}".strip()

    def _prune(self) -> None:
        for f in self.jar_backups()[KEEP:]:
            (self.backup_dir / f["file"]).unlink(missing_ok=True)
            (self.backup_dir / f["file"]).with_suffix(".json").unlink(missing_ok=True)

    async def update(self, by: str | None = None) -> str:
        if self.busy:
            raise UpdateError(f"이미 {self.busy} 중입니다")
        self.busy = "업데이트"
        tmp = None
        try:
            await self._progress("최신 빌드 확인", 5)
            chk = await self.check()
            info = chk["latest"]
            if chk["available"] is False:
                raise UpdateError(f"이미 최신입니다 (#{info['build']})")
            if self.settings()["backup_first"] and self.backups:
                await self._progress("월드 백업 중", 10)
                b = await self.backups.create(kind="update", by=by, note=f"업데이트 직전 (→ #{info['build']})")
                if not b.get("ok", True):
                    raise UpdateError("백업에 실패해 업데이트를 멈췄습니다")
            await self._progress(f"내려받는 중 ({info['file']})", 40)
            tmp = self.backup_dir / ".download.tmp"
            await asyncio.to_thread(self._download_sync, info, tmp)
            await self._progress("해시 확인 완료", 60)
            label = await self._swap(tmp, info, by)
            tmp = None
            await self._progress(f"{label} 로 업데이트했습니다", 100, done=True)
            return label
        except Exception as e:
            await self._progress(f"실패: {e}", None, done=True, ok=False)
            raise
        finally:
            if tmp and tmp.exists():
                tmp.unlink()
            self.busy = None

    async def rollback(self, file: str | None = None, by: str | None = None) -> str:
        if self.busy:
            raise UpdateError(f"이미 {self.busy} 중입니다")
        backups = self.jar_backups()
        if not backups:
            raise UpdateError("되돌릴 이전 jar 가 없습니다")
        pick = next((b for b in backups if b["file"] == file), None) if file else backups[0]
        if not pick or "/" in pick["file"] or "\\" in pick["file"]:
            raise UpdateError("없는 백업입니다")
        self.busy = "되돌리기"
        try:
            src = self.backup_dir / pick["file"]
            meta = src.with_suffix(".json")
            tmp = self.backup_dir / ".rollback.tmp"
            meta_text = meta.read_text(encoding="utf-8") if meta.exists() else None
            shutil.move(str(src), tmp)        # 꺼낸 백업은 목록에서 뺌 (지금 jar 는 새 백업으로 들어감)
            meta.unlink(missing_ok=True)
            await self._progress(f"{pick['file']} 로 되돌리는 중", 50)
            try:
                label = await self._swap(tmp, pick, by)
            except Exception:
                if tmp.exists():
                    shutil.move(str(tmp), src)
                    if meta_text:
                        meta.write_text(meta_text, encoding="utf-8")
                raise
            await self._progress(f"{label} 로 되돌렸습니다", 100, done=True)
            return label
        except Exception as e:
            await self._progress(f"되돌리기 실패: {e}", None, done=True, ok=False)
            raise
        finally:
            self.busy = None

    async def run(self, every_hours: float = 6) -> None:
        """주기적으로 새 빌드 확인 → 알림"""
        await asyncio.sleep(60)
        while True:
            if self.settings()["auto_check"] and not self.busy:
                try:
                    await self.check(notify=True)
                except Exception:
                    pass
            await asyncio.sleep(every_hours * 3600)
