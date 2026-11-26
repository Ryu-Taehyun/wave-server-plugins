"""
마인크래프트 서버 프로세스 관리
- start: start_command 를 서버 폴더에서 실행 (표준 출력 = 실시간 로그)
- stop: RCON 'stop' → 안 되면 콘솔 입력 'stop' → stop_timeout 초 뒤에도 살아 있으면 강제 종료
- 이 도구가 켜지 않은 서버(이미 켜져 있던 서버)도 RCON 으로 끄기 · 재시작 가능, 로그는 logs/latest.log 를 읽음
- 크래시(정지 요청 없이 꺼짐) 시 자동 재시작 (10분에 3번까지)
"""
from __future__ import annotations

import asyncio
import collections
import os
import re
import shlex
import signal
import sys
import time
from typing import Awaitable, Callable

from .config import Config
from .rcon import RconClient, RconError
from . import slp

DONE_RE = re.compile(r"Done \(\d+[.,]\d+s\)!")


def split_command(cmd: str) -> list[str]:
    """실행 명령을 나눔. Windows 는 "C:\\Program Files\\...\\java.exe" 처럼 따옴표로 감싼 경로의 따옴표를 벗김"""
    if sys.platform != "win32":
        return shlex.split(cmd)
    return [a[1:-1] if len(a) >= 2 and a[0] == a[-1] == '"' else a for a in shlex.split(cmd, posix=False)]


class ServerProcess:
    def __init__(self, cfg: Config, rcon: RconClient, on_event: Callable[[str, dict], Awaitable[None]] | None = None):
        self.cfg, self.rcon = cfg, rcon
        self.proc: asyncio.subprocess.Process | None = None
        self.state = "stopped"          # stopped · starting · running · stopping · crashed
        self.started_at: float | None = None
        self.logs: collections.deque[str] = collections.deque(maxlen=3000)
        self.subscribers: set[asyncio.Queue] = set()
        self._stop_requested = False
        self._crashes: list[float] = []
        self._on_event = on_event
        self._op_lock = asyncio.Lock()
        self._tail_task: asyncio.Task | None = None

    # ---------------- 로그 ----------------

    def _emit_line(self, line: str) -> None:
        line = line.rstrip("\r\n")
        self.logs.append(line)
        for q in list(self.subscribers):
            if q.qsize() < 2000:
                q.put_nowait(line)

    def note(self, text: str) -> None:
        """이 도구가 남기는 로그 줄"""
        self._emit_line(time.strftime("[%H:%M:%S] ") + "[Manager] " + text)

    async def _event(self, kind: str, data: dict) -> None:
        if self._on_event:
            await self._on_event(kind, data)

    @property
    def managed(self) -> bool:
        return self.proc is not None and self.proc.returncode is None

    @property
    def pid(self) -> int | None:
        return self.proc.pid if self.managed else None

    # ---------------- 상태 ----------------

    async def reachable(self) -> bool:
        st = await slp.ping(self.cfg.server.host, self.cfg.server.port, timeout=2)
        return st.online

    async def refresh_external(self, online: bool) -> None:
        """이 도구가 켜지 않은 서버의 상태를 핑 결과로 맞춤 + 로그 파일 따라 읽기"""
        if self.managed or self.state in ("starting", "stopping"):
            return
        if online and self.state != "running":
            self.state = "running"
            self.started_at = self.started_at or time.time()
            self.note("이미 켜져 있는 서버를 찾았습니다 (이 도구가 켠 서버가 아님: 끄기 · 재시작은 RCON 으로)")
            self._start_tail()
        elif not online and self.state == "running":
            self.state = "stopped"
            self.started_at = None
            self._stop_tail()

    def _start_tail(self) -> None:
        if self._tail_task is None or self._tail_task.done():
            self._tail_task = asyncio.create_task(self._tail_log())

    def _stop_tail(self) -> None:
        if self._tail_task and not self._tail_task.done():
            self._tail_task.cancel()

    async def _tail_log(self) -> None:
        path = self.cfg.path(self.cfg.server.dir) / self.cfg.server.log_file
        pos = None
        while True:
            try:
                if path.exists():
                    size = path.stat().st_size
                    if pos is None or size < pos:
                        pos = max(0, size - 20000) if pos is None else 0
                    if size > pos:
                        with open(path, "rb") as f:
                            f.seek(pos)
                            chunk = f.read()
                            pos = f.tell()
                        for line in chunk.decode("utf-8", "replace").splitlines():
                            self._emit_line(line)
            except OSError:
                pass
            await asyncio.sleep(1)

    # ---------------- 켜기 ----------------

    async def start(self) -> str:
        async with self._op_lock:
            if self.managed or self.state in ("starting", "running"):
                return "이미 켜져 있습니다"
            if await self.reachable():
                await self.refresh_external(True)
                return "이미 켜져 있습니다 (다른 곳에서 켠 서버)"
            cwd = self.cfg.path(self.cfg.server.dir)
            if not cwd.is_dir():
                raise RuntimeError(f"서버 폴더가 없습니다: {cwd}")
            self._stop_requested = False
            self.state = "starting"
            self.started_at = time.time()
            self.note(f"서버를 켭니다: {self.cfg.server.start_command}")
            kwargs = {}
            if sys.platform != "win32":
                kwargs["start_new_session"] = True   # 이 도구를 꺼도 Ctrl+C 가 서버로 가지 않게
            args = split_command(self.cfg.server.start_command)
            self.proc = await asyncio.create_subprocess_exec(
                *args, cwd=str(cwd), stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT, **kwargs)
            asyncio.create_task(self._pump(self.proc))
            await self._event("state", {"state": self.state})
            return "켜는 중입니다"

    async def _pump(self, proc: asyncio.subprocess.Process) -> None:
        assert proc.stdout
        while True:
            line = await proc.stdout.readline()
            if not line:
                break
            text = line.decode("utf-8", "replace")
            self._emit_line(text)
            if self.state == "starting" and DONE_RE.search(text):
                self.state = "running"
                self.note("서버가 켜졌습니다")
                await self._event("state", {"state": self.state})
        code = await proc.wait()
        if proc is not self.proc:
            return
        crashed = not self._stop_requested
        self.note(f"서버 프로세스가 끝났습니다 (종료 코드 {code})" + (" - 비정상 종료" if crashed else ""))
        self.state = "crashed" if crashed else "stopped"
        self.started_at = None
        await self._event("state", {"state": self.state, "code": code})
        if crashed and self.cfg.server.auto_restart:
            now = time.time()
            self._crashes = [t for t in self._crashes if now - t < 600] + [now]
            if len(self._crashes) <= 3:
                self.note(f"자동 재시작 ({len(self._crashes)}/3) 5초 뒤")
                await asyncio.sleep(5)
                if self.state == "crashed":
                    try:
                        await self.start()
                    except Exception as e:
                        self.note(f"자동 재시작 실패: {e}")
            else:
                self.note("10분 안에 3번 넘게 꺼져서 자동 재시작을 멈춥니다. 로그를 확인하세요.")

    # ---------------- 끄기 ----------------

    async def stop(self) -> str:
        async with self._op_lock:
            return await self._stop()

    async def _stop(self) -> str:
        external = not self.managed
        if external and not await self.reachable():
            self.state = "stopped"
            return "이미 꺼져 있습니다"
        self._stop_requested = True
        self.state = "stopping"
        await self._event("state", {"state": self.state})
        self.note("서버를 끕니다")
        sent = False
        try:
            await self.rcon.command("stop")
            sent = True
        except RconError as e:
            self.note(f"RCON 으로 끄기 실패: {e}")
        if not sent and self.managed and self.proc and self.proc.stdin:
            try:
                self.proc.stdin.write(b"stop\n")
                await self.proc.stdin.drain()
                sent = True
            except (OSError, ConnectionError):
                pass
        if external:
            if not sent:
                self.state = "running"
                raise RuntimeError("이 도구가 켜지 않은 서버라 RCON 으로만 끌 수 있는데, RCON 연결에 실패했습니다")
            for _ in range(self.cfg.server.stop_timeout):
                await asyncio.sleep(1)
                if not await self.reachable():
                    break
            self._stop_tail()
            self.state = "stopped"
            self.started_at = None
            await self._event("state", {"state": self.state})
            return "꺼졌습니다"
        assert self.proc
        try:
            await asyncio.wait_for(self.proc.wait(), self.cfg.server.stop_timeout)
        except asyncio.TimeoutError:
            self.note(f"{self.cfg.server.stop_timeout}초 안에 꺼지지 않아 강제로 종료합니다")
            self._kill()
            await self.proc.wait()
        await asyncio.sleep(0.2)   # _pump 가 상태를 정리하도록
        self.state = "stopped"
        return "꺼졌습니다"

    def _kill(self) -> None:
        if not self.proc or self.proc.returncode is not None:
            return
        try:
            if sys.platform != "win32":
                os.killpg(self.proc.pid, signal.SIGKILL)
            else:
                self.proc.kill()
        except (ProcessLookupError, PermissionError):
            self.proc.kill()

    async def restart(self) -> str:
        async with self._op_lock:
            if self.managed or await self.reachable():
                await self._stop()
        await asyncio.sleep(1)
        return await self.start()

    async def console(self, line: str) -> None:
        """RCON 없이 콘솔에 직접 입력 (이 도구가 켠 서버만)"""
        if not (self.managed and self.proc and self.proc.stdin):
            raise RuntimeError("이 도구가 켠 서버가 아닙니다")
        self.proc.stdin.write((line + "\n").encode())
        await self.proc.stdin.drain()

    def uptime(self) -> int | None:
        return int(time.time() - self.started_at) if self.started_at and self.state == "running" else None
