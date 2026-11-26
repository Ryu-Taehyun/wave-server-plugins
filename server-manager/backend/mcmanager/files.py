"""
설정 파일 편집 · 플러그인 관리 (서버 폴더 안만)
- 편집 가능한 파일: .yml .yaml .properties .json .txt .toml .conf .cfg (1MB 이하)
- 저장 전 형식 검사 (YAML 은 같은 키가 두 번 있는 것도 막음), 저장 직전 내용은 data/file-history 에 보관
- 플러그인 jar: 올리면 '대기' 폴더에 두었다가 적용할 때 서버를 끄고 → 바꾸고 → 켬 (켜진 채로 jar 를 바꾸면 zip file closed 오류)
"""
from __future__ import annotations

import io
import json
import os
import re
import shutil
import time
import zipfile
from pathlib import Path, PurePosixPath

import yaml

from .config import Config
from .db import DB

TEXT_EXT = {".yml", ".yaml", ".properties", ".json", ".txt", ".toml", ".conf", ".cfg", ".sk"}
MAX_EDIT = 1024 * 1024
SKIP_DIRS = {"world", "world_nether", "world_the_end", "logs", "crash-reports", "cache", "libraries", "versions", ".git", "node_modules"}


class FileError(Exception):
    pass


# ---------------- 형식 검사 ----------------

class _DupKeyLoader(yaml.SafeLoader):
    pass


def _no_dup(loader, node, deep=False):
    seen: dict = {}
    for key_node, _ in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in seen:
            raise yaml.constructor.ConstructorError(None, None, f"'{key}' 키가 두 번 있습니다 ({seen[key] + 1}줄과 {key_node.start_mark.line + 1}줄). 앞의 값이 지워집니다", key_node.start_mark)
        seen[key] = key_node.start_mark.line
    return loader.construct_mapping(node, deep)


_DupKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _no_dup)
# Bukkit 아이템 등 알 수 없는 태그는 그냥 통과
_DupKeyLoader.add_multi_constructor("", lambda loader, suffix, node: None)


def validate(name: str, text: str) -> str | None:
    """문제가 있으면 설명, 없으면 None"""
    ext = Path(name).suffix.lower()
    try:
        if ext in (".yml", ".yaml"):
            if "\t" in text.split("#")[0] and re.search(r"^\t", text, re.M):
                return "YAML 에서는 들여쓰기에 탭을 쓸 수 없습니다 (스페이스를 쓰세요)"
            yaml.load(text, Loader=_DupKeyLoader)
        elif ext == ".json":
            json.loads(text)
        elif ext == ".properties":
            for i, line in enumerate(text.splitlines(), 1):
                s = line.strip()
                if s and not s.startswith(("#", "!")) and "=" not in s and ":" not in s:
                    return f"{i}줄: '키=값' 형식이 아닙니다"
        elif ext == ".toml":
            import tomllib
            tomllib.loads(text)
    except yaml.YAMLError as e:
        mark = getattr(e, "problem_mark", None) or getattr(e, "context_mark", None)
        where = f"{mark.line + 1}줄: " if mark else ""
        return f"{where}{getattr(e, 'problem', None) or e}"
    except json.JSONDecodeError as e:
        return f"{e.lineno}줄: JSON 형식 오류 ({e.msg})"
    except ValueError as e:
        return f"형식 오류: {e}"
    return None


class Files:
    def __init__(self, cfg: Config, db: DB):
        self.cfg, self.db = cfg, db

    @property
    def root(self) -> Path:
        return self.cfg.path(self.cfg.server.dir).resolve()

    @property
    def history_dir(self) -> Path:
        d = self.cfg.path("data/file-history")
        d.mkdir(parents=True, exist_ok=True)
        return d

    def _safe(self, rel: str) -> Path:
        p = PurePosixPath(rel.replace("\\", "/"))
        if p.is_absolute() or ".." in p.parts or not rel.strip():
            raise FileError("잘못된 경로")
        full = (self.root / Path(*p.parts)).resolve()
        if full != self.root and self.root not in full.parents:
            raise FileError("서버 폴더 밖입니다")
        return full

    def editable(self, rel: str) -> Path:
        f = self._safe(rel)
        if f.suffix.lower() not in TEXT_EXT:
            raise FileError("편집할 수 없는 파일 종류입니다")
        return f

    # ---------------- 목록 ----------------

    def tree(self) -> list[dict]:
        """편집할 수 있는 파일 (깊이 4까지)"""
        out = []
        root = self.root
        if not root.is_dir():
            return out
        for dirpath, dirnames, filenames in os.walk(root):
            dp = Path(dirpath)
            depth = len(dp.relative_to(root).parts)
            dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".") and depth < 4
                                 and not (dp.name == "plugins" and depth == 1 and d.endswith(".disabled")))
            for f in sorted(filenames):
                p = dp / f
                if p.suffix.lower() in TEXT_EXT:
                    try:
                        st = p.stat()
                    except OSError:
                        continue
                    if st.st_size <= MAX_EDIT:
                        out.append({"path": p.relative_to(root).as_posix(), "size": st.st_size, "mtime": int(st.st_mtime)})
        return out

    def read(self, rel: str) -> dict:
        f = self.editable(rel)
        if not f.is_file():
            raise FileError("파일이 없습니다")
        if f.stat().st_size > MAX_EDIT:
            raise FileError("1MB 보다 큰 파일은 편집할 수 없습니다")
        raw = f.read_bytes()
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            text = raw.decode("cp949", "replace")
        return {"path": rel, "text": text, "mtime": int(f.stat().st_mtime), "problem": validate(f.name, text)}

    def save(self, rel: str, text: str, by: str, expect_mtime: int | None = None, force: bool = False) -> dict:
        f = self.editable(rel)
        if not force:
            prob = validate(f.name, text)
            if prob:
                raise FileError(f"저장하지 않았습니다 · {prob}")
        if f.exists():
            if expect_mtime and int(f.stat().st_mtime) != expect_mtime:
                raise FileError("그 사이에 파일이 바뀌었습니다 (서버나 다른 사람이 저장함). 다시 열어 주세요")
            snap = self.history_dir / f"{int(time.time() * 1000)}_{rel.replace('/', '__')}"
            shutil.copy2(f, snap)
            self.db.x("INSERT INTO file_history (ts, path, saved_by, snapshot) VALUES (?, ?, ?, ?)", (int(time.time()), rel, by, snap.name))
        f.parent.mkdir(parents=True, exist_ok=True)
        tmp = f.with_name(f.name + ".mcm-tmp")
        tmp.write_text(text, encoding="utf-8", newline="")
        os.replace(tmp, f)
        return {"ok": True, "mtime": int(f.stat().st_mtime)}

    def history(self, rel: str) -> list[dict]:
        return self.db.q("SELECT id, ts, saved_by FROM file_history WHERE path = ? ORDER BY ts DESC LIMIT 30", (rel,))

    def history_text(self, hid: int) -> dict:
        r = self.db.one("SELECT * FROM file_history WHERE id = ?", (hid,))
        if not r:
            raise FileError("없는 기록입니다")
        p = self.history_dir / Path(r["snapshot"]).name
        return {"path": r["path"], "ts": r["ts"], "text": p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""}

    # ---------------- 플러그인 ----------------

    @property
    def plugins_dir(self) -> Path:
        return self.root / "plugins"

    @property
    def staged_dir(self) -> Path:
        d = self.cfg.path("data/staged-plugins")
        d.mkdir(parents=True, exist_ok=True)
        return d

    @staticmethod
    def jar_info(path: Path) -> dict:
        info = {"file": path.name, "size": path.stat().st_size, "mtime": int(path.stat().st_mtime), "name": path.stem, "version": "", "main": ""}
        try:
            with zipfile.ZipFile(path) as z:
                for meta in ("paper-plugin.yml", "plugin.yml"):
                    if meta in z.namelist():
                        y = yaml.safe_load(z.read(meta).decode("utf-8", "replace")) or {}
                        info.update(name=str(y.get("name", info["name"])), version=str(y.get("version", "")), main=str(y.get("main", "")))
                        break
        except (zipfile.BadZipFile, yaml.YAMLError, OSError):
            info["broken"] = True
        return info

    def plugins(self) -> dict:
        d = self.plugins_dir
        active = [self.jar_info(p) for p in sorted(d.glob("*.jar"))] if d.is_dir() else []
        disabled = [self.jar_info(p) for p in sorted(d.glob("*.jar.disabled"))] if d.is_dir() else []
        staged = json.loads(self.db.setting("staged_plugins", "[]") or "[]")
        return {"active": active, "disabled": disabled, "staged": staged}

    def _save_staged(self, items: list) -> None:
        self.db.set_setting("staged_plugins", json.dumps(items, ensure_ascii=False))

    def stage_upload(self, filename: str, data: bytes) -> dict:
        name = Path(filename).name
        if not name.lower().endswith(".jar") or not re.fullmatch(r"[\w.\-+ ()\[\]]+", name):
            raise FileError("jar 파일만 올릴 수 있습니다 (이름에 특수문자 없이)")
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                if not any(n in z.namelist() for n in ("plugin.yml", "paper-plugin.yml")):
                    raise FileError("플러그인 jar 가 아닙니다 (plugin.yml 없음)")
        except zipfile.BadZipFile:
            raise FileError("깨진 jar 파일입니다")
        (self.staged_dir / name).write_bytes(data)
        info = self.jar_info(self.staged_dir / name)
        # 같은 플러그인(이름)의 예전 jar 를 바꿈
        replaces = [p["file"] for p in self.plugins()["active"] if p["name"] == info["name"] and p["file"] != name]
        items = [s for s in json.loads(self.db.setting("staged_plugins", "[]") or "[]") if s.get("file") != name]
        items.append({"op": "install", "file": name, "name": info["name"], "version": info["version"], "replaces": replaces})
        self._save_staged(items)
        return items[-1]

    def stage(self, op: str, file: str) -> None:
        if op not in ("disable", "enable", "remove"):
            raise FileError("잘못된 작업")
        items = [s for s in json.loads(self.db.setting("staged_plugins", "[]") or "[]") if s.get("file") != file]
        items.append({"op": op, "file": Path(file).name})
        self._save_staged(items)

    def unstage(self, file: str) -> None:
        items = json.loads(self.db.setting("staged_plugins", "[]") or "[]")
        self._save_staged([s for s in items if s.get("file") != file])
        (self.staged_dir / Path(file).name).unlink(missing_ok=True)

    def apply_staged(self) -> list[str]:
        """서버가 꺼진 상태에서 호출. 바꾼 jar 는 data/plugin-backup 에 보관"""
        items = json.loads(self.db.setting("staged_plugins", "[]") or "[]")
        bak = self.cfg.path("data/plugin-backup") / time.strftime("%Y%m%d-%H%M%S")
        done = []
        pd = self.plugins_dir
        pd.mkdir(parents=True, exist_ok=True)

        def keep(p: Path):
            bak.mkdir(parents=True, exist_ok=True)
            shutil.move(str(p), bak / p.name)

        for s in items:
            f = Path(s["file"]).name
            if s["op"] == "install":
                src = self.staged_dir / f
                if not src.exists():
                    continue
                for old in s.get("replaces", []):
                    if (pd / old).exists():
                        keep(pd / old)
                if (pd / f).exists():
                    keep(pd / f)
                shutil.move(str(src), pd / f)
                done.append(f"설치 {f}")
            elif s["op"] == "disable" and (pd / f).exists():
                (pd / f).rename(pd / (f + ".disabled"))
                done.append(f"끔 {f}")
            elif s["op"] == "enable":
                src = pd / (f if f.endswith(".disabled") else f + ".disabled")
                if src.exists():
                    src.rename(pd / src.name.removesuffix(".disabled"))
                    done.append(f"켬 {src.name.removesuffix('.disabled')}")
            elif s["op"] == "remove":
                for cand in (pd / f, pd / (f + ".disabled")):
                    if cand.exists():
                        keep(cand)
                        done.append(f"삭제 {f}")
        self._save_staged([])
        return done
