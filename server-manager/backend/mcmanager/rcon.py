"""
Minecraft RCON 클라이언트 (asyncio)
패킷: [길이 int32 LE][요청 ID int32][종류 int32][본문 UTF-8][\\x00\\x00]
종류: 3 = 로그인, 2 = 명령어, 0 = 응답
긴 응답(4KB 넘게 쪼개짐)은 명령 뒤에 빈 '표시' 패킷을 보내고 그 응답이 올 때까지 모아서 처리
"""
from __future__ import annotations

import asyncio
import itertools
import struct

AUTH, COMMAND, RESPONSE = 3, 2, 0


class RconError(Exception):
    pass


class RconAuthError(RconError):
    pass


def encode(req_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


async def read_packet(reader: asyncio.StreamReader) -> tuple[int, int, str]:
    (length,) = struct.unpack("<i", await reader.readexactly(4))
    if length < 10 or length > 1 << 20:
        raise RconError(f"잘못된 패킷 길이 {length}")
    data = await reader.readexactly(length)
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, kind, data[8:-2].decode("utf-8", "replace")


class RconClient:
    """연결을 유지하며 명령을 하나씩 보냄 (끊기면 다시 연결)"""

    def __init__(self, host: str, port: int, password: str, timeout: float = 5.0):
        self.host, self.port, self.password, self.timeout = host, port, password, timeout
        self._reader: asyncio.StreamReader | None = None
        self._writer: asyncio.StreamWriter | None = None
        self._lock = asyncio.Lock()
        self._ids = itertools.count(1)

    @property
    def configured(self) -> bool:
        return bool(self.password)

    async def _connect(self) -> None:
        if not self.password:
            raise RconError("RCON 비밀번호가 설정되지 않았습니다 (server.properties 의 rcon.password)")
        self._reader, self._writer = await asyncio.wait_for(asyncio.open_connection(self.host, self.port), self.timeout)
        rid = next(self._ids)
        self._writer.write(encode(rid, AUTH, self.password))
        await self._writer.drain()
        while True:
            got_id, kind, _ = await asyncio.wait_for(read_packet(self._reader), self.timeout)
            if kind == 2 or got_id == -1:   # 로그인 결과 (종류 2), 실패면 ID -1
                if got_id == -1:
                    await self.close()
                    raise RconAuthError("RCON 비밀번호가 틀렸습니다")
                return

    async def close(self) -> None:
        if self._writer is not None:
            try:
                self._writer.close()
                await self._writer.wait_closed()
            except Exception:
                pass
        self._reader = self._writer = None

    async def command(self, cmd: str) -> str:
        """명령어 실행 후 응답 글자 (색 코드 § 포함)"""
        async with self._lock:
            for attempt in range(2):
                try:
                    if self._writer is None:
                        await self._connect()
                    assert self._reader and self._writer
                    rid, marker = next(self._ids), next(self._ids)
                    self._writer.write(encode(rid, COMMAND, cmd))
                    self._writer.write(encode(marker, RESPONSE, ""))   # 응답 끝 표시
                    await self._writer.drain()
                    parts: list[str] = []
                    while True:
                        got_id, _, body = await asyncio.wait_for(read_packet(self._reader), self.timeout)
                        if got_id == marker:
                            break
                        if got_id == rid:
                            parts.append(body)
                    return "".join(parts)
                except RconAuthError:
                    raise
                except (OSError, asyncio.TimeoutError, asyncio.IncompleteReadError, RconError) as e:
                    await self.close()
                    if attempt == 1:
                        raise RconError(f"RCON 연결 실패: {e}") from e
            raise RconError("RCON 연결 실패")


def strip_colors(s: str) -> str:
    """§a 같은 색 코드 제거"""
    out, skip = [], False
    for ch in s:
        if skip:
            skip = False
            continue
        if ch == "§":
            skip = True
            continue
        out.append(ch)
    return "".join(out)
