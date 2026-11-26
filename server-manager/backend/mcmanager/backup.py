"""
백업 · 복원
- 백업: 서버가 켜져 있으면 RCON save-off → save-all flush (저장 끝날 때까지) → zip → save-on
  zip 은 서버 폴더 기준 상대 경로. 자동 백업은 keep 개만 남김
- 복원: 서버 끄기 → 지금 상태를 safety 백업 → 백업에 들어 있는 항목만 지우고 풀기 → (켜져 있었으면) 다시 켜기
"""
from __future__ import annotations

import asyncio
import fnmatch
import os
import shutil
import time
import zipfile
from pathlib import Path, PurePosixPath

from .config import Config
from .db import DB
from .process import ServerProcess
from .rcon import RconClient, RconError


class BackupError(Exception):
    pass


class BackupManager:
    def __init__(self, cfg: Config, db: DB, server: ServerProcess, rcon: RconClient, on_event=None, on_alert=None):
        self.cfg, self.db, self.server, self.rcon = cfg, db, server, rcon
        self.on_event, self.on_alert = on_event, on_alert
        self.lock = asyncio.Lock()
        self.busy: str | None = None   # 진행 중인 작업 설명

    @property
    def server_dir(self) -> Path:
        return self.cfg.path(self.cfg.server.dir)

    @property
    def dir(self) -> Path:
        d = self.cfg.path(self.cfg.backup.dir)
        d.mkdir(parents=True, exist_ok=True)
        return d

    async def _progress(self, phase: str, pct: float | None = None) -> None:
        self.busy = phase
        if self.on_event:
            await self.on_event("backup", {"phase": phase, "pct": pct})

    # ---------------- 파일 고르기 ----------------

    def _excluded(self, rel: str) -> bool:
        """exclude 패턴과 맞으면 (폴더 패턴이면 그 안 전부)"""
        return any(fnmatch.fnmatch(rel, pat) or fnmatch.fnmatch(rel, pat.rstrip("/") + "/*") for pat in self.cfg.backup.exclude)

    def files(self) -> list[tuple[Path, str]]:
        out: list[tuple[Path, str]] = []
        root = self.server_dir
        backup_dir = self.dir.resolve()
        for item in self.cfg.backup.include:
            p = root / item
            if p.is_file():
                out.append((p, item))
            elif p.is_dir():
                for dirpath, dirnames, filenames in os.walk(p):
                    dp = Path(dirpath)
                    if dp.resolve() == backup_dir or backup_dir in dp.resolve().parents:
                        dirnames[:] = []
                        continue
                    rel_dir = dp.relative_to(root).as_posix()
                    dirnames[:] = [d for d in dirnames if not self._excluded(f"{rel_dir}/{d}")]
                    for f in filenames:
                        rel = f"{rel_dir}/{f}"
                        if not self._excluded(rel):
                            out.append((dp / f, rel))
        return out

    # ---------------- 백업 ----------------

    async def _rcon(self, cmd: str) -> str | None:
        try:
            return await self.rcon.command(cmd)
        except RconError:
            return None

    async def create(self, kind: str = "manual", by: str | None = None, note: str = "") -> dict:
        if self.lock.locked():
            raise BackupError(f"이미 진행 중입니다: {self.busy}")
        async with self.lock:
            t0 = time.time()
            name = time.strftime(f"%Y%m%d-%H%M%S-{kind}.zip")
            path = self.dir / name
            online = self.server.state == "running"
            saving_paused = False
            try:
                if online:
                    await self._progress("월드 저장 중")
                    if await self._rcon("save-off") is not None:
                        saving_paused = True
                        await self._rcon("save-all flush")
                        await asyncio.sleep(2)   # 디스크에 다 쓸 시간
                await self._progress("파일 목록 만드는 중", 0)
                files = await asyncio.to_thread(self.files)
                if not files:
                    raise BackupError("백업할 파일이 없습니다 (서버 폴더 · backup.include 를 확인하세요)")
                total = sum(f.stat().st_size for f, _ in files if f.exists()) or 1
                loop = asyncio.get_running_loop()
                done = 0
                last = 0.0

                def write():
                    nonlocal done, last
                    with zipfile.ZipFile(path.with_suffix(".part"), "w", zipfile.ZIP_DEFLATED, compresslevel=5) as z:
                        for f, rel in files:
                            try:
                                if f.suffix in (".mca", ".jar", ".png", ".zip", ".gz"):
                                    z.write(f, rel, compress_type=zipfile.ZIP_STORED)   # 이미 압축된 파일은 그대로
                                else:
                                    z.write(f, rel)
                                done += f.stat().st_size
                            except (FileNotFoundError, PermissionError):
                                continue   # 도중에 사라졌거나 잠긴 파일
                            if time.time() - last > 0.5:
                                last = time.time()
                                asyncio.run_coroutine_threadsafe(self._progress("압축 중", round(done / total * 100, 1)), loop)
                    path.with_suffix(".part").replace(path)

                await asyncio.to_thread(write)
            except BaseException as e:
                path.with_suffix(".part").unlink(missing_ok=True)
                self.db.x("INSERT INTO backups (ts, file, size, ok, kind, note, created_by) VALUES (?, ?, 0, 0, ?, ?, ?)",
                          (int(t0), name, kind, f"실패: {e}"[:300], by))
                if self.on_alert:
                    await self.on_alert("backup_failed", "백업 실패", str(e))
                raise
            finally:
                if saving_paused:
                    await self._rcon("save-on")
                self.busy = None
            size = path.stat().st_size
            if self.cfg.backup.copy_to:
                try:
                    dest = Path(self.cfg.backup.copy_to)
                    dest.mkdir(parents=True, exist_ok=True)
                    await asyncio.to_thread(shutil.copy2, path, dest / name)
                except OSError as e:
                    note = (note + f" (사본 저장 실패: {e})").strip()
            bid = self.db.x("INSERT INTO backups (ts, file, size, ok, kind, note, created_by) VALUES (?, ?, ?, 1, ?, ?, ?)",
                            (int(t0), name, size, kind, note or f"{len(files)}개 파일 · {time.time() - t0:.0f}초", by))
            self.prune()
            await self._progress("완료", 100)
            self.busy = None
            return self.db.one("SELECT * FROM backups WHERE id = ?", (bid,))

    def prune(self) -> None:
        """자동 백업은 keep 개만, 복원 전 백업(safety)은 3개만"""
        for kind, keep in (("auto", max(1, self.cfg.backup.keep)), ("safety", 3), ("update", 3)):
            old = self.db.q("SELECT id, file FROM backups WHERE kind = ? AND ok = 1 ORDER BY ts DESC LIMIT -1 OFFSET ?", (kind, keep))
            for r in old:
                self.delete(r["id"])

    def delete(self, bid: int) -> None:
        r = self.db.one("SELECT file FROM backups WHERE id = ?", (bid,))
        if not r:
            raise BackupError("없는 백업입니다")
        (self.dir / Path(r["file"]).name).unlink(missing_ok=True)
        self.db.x("DELETE FROM backups WHERE id = ?", (bid,))

    def file_of(self, bid: int) -> Path:
        r = self.db.one("SELECT file, ok FROM backups WHERE id = ?", (bid,))
        if not r or not r["ok"]:
            raise BackupError("없는 백업입니다")
        p = self.dir / Path(r["file"]).name
        if not p.exists():
            raise BackupError("백업 파일이 지워졌습니다")
        return p

    def list(self) -> list[dict]:
        rows = self.db.q("SELECT * FROM backups ORDER BY ts DESC LIMIT 200")
        for r in rows:
            r["exists"] = (self.dir / Path(r["file"]).name).exists() if r["ok"] else False
        return rows

    def disk(self) -> dict:
        u = shutil.disk_usage(self.dir)
        used = sum(f.stat().st_size for f in self.dir.glob("*.zip"))
        return {"backups_bytes": used, "free_bytes": u.free, "total_bytes": u.total}

    # ---------------- 복원 ----------------

    async def restore(self, bid: int, by: str | None = None) -> str:
        src = self.file_of(bid)
        was_running = self.server.state in ("running", "starting") or await self.server.reachable()
        if was_running:
            await self._progress("서버 끄는 중")
            await self.server.stop()
        await self.create(kind="safety", by=by, note=f"#{bid} 복원 직전 상태")
        async with self.lock:
            try:
                await self._progress("복원 중", 0)

                def extract():
                    root = self.server_dir.resolve()
                    with zipfile.ZipFile(src) as z:
                        names = [n for n in z.namelist() if not n.endswith("/")]
                        for n in names:   # 경로 검사 (zip 안에 ../ 같은 위험한 경로)
                            p = PurePosixPath(n)
                            if p.is_absolute() or ".." in p.parts:
                                raise BackupError(f"잘못된 경로가 들어 있습니다: {n}")
                        tops = {PurePosixPath(n).parts[0] for n in names}
                        for t in tops:   # 백업에 들어 있는 항목만 지우고 다시 풀기 (다른 파일은 건드리지 않음)
                            target = root / t
                            if target.is_dir():
                                shutil.rmtree(target)
                            elif target.exists():
                                target.unlink()
                        z.extractall(root)
                    return len(names)

                n = await asyncio.to_thread(extract)
            finally:
                self.busy = None
        msg = f"백업 #{bid} 에서 {n}개 파일을 복원했습니다"
        self.server.note(msg)
        await self._progress("완료", 100)
        self.busy = None
        if was_running:
            await self.server.start()
            msg += " · 서버를 다시 켜는 중"
        return msg
