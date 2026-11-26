"""
Server List Ping (1.7+): 마인크래프트 클라이언트가 서버 목록에서 하는 것과 같은 방식으로 접속 확인
handshake(다음 상태 1) → status 요청 → JSON 응답 → ping/pong 으로 지연 시간(ms)
"""
from __future__ import annotations

import asyncio
import json
import struct
import time
from dataclasses import dataclass, field


def varint(n: int) -> bytes:
    n &= 0xFFFFFFFF
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


async def read_varint(reader: asyncio.StreamReader) -> int:
    n = shift = 0
    while True:
        b = (await reader.readexactly(1))[0]
        n |= (b & 0x7F) << shift
        if not b & 0x80:
            break
        shift += 7
        if shift > 35:
            raise ValueError("varint 가 너무 깁니다")
    return n - (1 << 32) if n & (1 << 31) else n


def packet(pid: int, data: bytes = b"") -> bytes:
    body = varint(pid) + data
    return varint(len(body)) + body


def mc_string(s: str) -> bytes:
    b = s.encode("utf-8")
    return varint(len(b)) + b


def motd_text(desc) -> str:
    """description(문자열 또는 채팅 컴포넌트 JSON)을 글자만"""
    if isinstance(desc, str):
        from .rcon import strip_colors
        return strip_colors(desc)
    if isinstance(desc, dict):
        return motd_text(desc.get("text", "")) + "".join(motd_text(e) for e in desc.get("extra", []))
    if isinstance(desc, list):
        return "".join(motd_text(e) for e in desc)
    return ""


@dataclass
class Status:
    online: bool
    latency_ms: float | None = None
    version: str = ""
    protocol: int = 0
    players_online: int = 0
    players_max: int = 0
    sample: list[str] = field(default_factory=list)
    motd: str = ""
    error: str = ""


async def ping(host: str, port: int, timeout: float = 3.0) -> Status:
    try:
        reader, writer = await asyncio.wait_for(asyncio.open_connection(host, port), timeout)
    except (OSError, asyncio.TimeoutError) as e:
        return Status(online=False, error=f"접속 불가: {e.__class__.__name__}")
    try:
        writer.write(packet(0x00, varint(-1 & 0xFFFFFFFF) + mc_string(host) + struct.pack(">H", port) + varint(1)))
        writer.write(packet(0x00))
        await writer.drain()

        async def read_status():
            await read_varint(reader)          # 길이
            pid = await read_varint(reader)
            if pid != 0:
                raise ValueError(f"예상하지 못한 패킷 {pid}")
            n = await read_varint(reader)
            return json.loads((await reader.readexactly(n)).decode("utf-8"))

        data = await asyncio.wait_for(read_status(), timeout)
        t0 = time.perf_counter()
        writer.write(packet(0x01, struct.pack(">q", int(time.time() * 1000))))
        await writer.drain()
        latency = None
        try:
            async def read_pong():
                await read_varint(reader)
                await read_varint(reader)
                await reader.readexactly(8)
            await asyncio.wait_for(read_pong(), timeout)
            latency = round((time.perf_counter() - t0) * 1000, 1)
        except (asyncio.TimeoutError, asyncio.IncompleteReadError):
            pass
        players = data.get("players") or {}
        ver = data.get("version") or {}
        return Status(online=True, latency_ms=latency, version=str(ver.get("name", "")), protocol=int(ver.get("protocol", 0) or 0),
                      players_online=int(players.get("online", 0) or 0), players_max=int(players.get("max", 0) or 0),
                      sample=[p.get("name", "") for p in players.get("sample") or [] if isinstance(p, dict)],
                      motd=motd_text(data.get("description", "")).strip())
    except (OSError, ValueError, asyncio.TimeoutError, asyncio.IncompleteReadError, json.JSONDecodeError) as e:
        return Status(online=False, error=f"응답 오류: {e.__class__.__name__}")
    finally:
        writer.close()
        try:
            await writer.wait_closed()
        except Exception:
            pass
