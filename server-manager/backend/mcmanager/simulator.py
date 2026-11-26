"""
데모용 가짜 마인크래프트 서버 (실제 서버 없이 이 도구를 써 보거나 테스트할 때)
- 서버 목록 핑 · RCON(list, tps, say, kick, ban, pardon, op, deop, time, weather, stop, …)에 응답
- Paper 처럼 로그를 출력하고, 플레이어가 무작위로 들어오고 나감

python -m mcmanager.simulator --port 25565 --rcon-port 25575 --password demo
"""
from __future__ import annotations

import argparse
import asyncio
import json
import random
import struct
import sys
import time

from .rcon import encode, read_packet
from .slp import mc_string, packet, read_varint, varint

NAMES = ["Pixel_Art", "Steve_KR", "Alex99", "Hunter", "Rich_01", "Lucky", "Notch_fan", "Builder", "Miner42", "Sky_Walker",
         "Frosty", "Crescent", "Ember", "Glacier", "Payer", "Ruby", "Neo", "Kim_Dev", "Lee_PvP", "Park_Archer"]


class Sim:
    def __init__(self, password: str, max_players: int, boot: float):
        self.password, self.max_players, self.boot = password, max_players, boot
        self.players: list[str] = random.sample(NAMES, 4)
        self.banned: set[str] = set()
        self.ops: set[str] = set()
        self.tps = 20.0
        self.stopping = asyncio.Event()
        self.ready = False

    def log(self, msg: str, level="INFO") -> None:
        print(time.strftime("[%H:%M:%S ") + f"{level}]: {msg}", flush=True)

    # ---------------- 서버 목록 핑 ----------------
    async def handle_slp(self, reader, writer):
        try:
            await read_varint(reader)
            pid = await read_varint(reader)
            if pid != 0:
                return
            await read_varint(reader)             # protocol
            n = await read_varint(reader)
            await reader.readexactly(n + 2)        # host, port
            nxt = await read_varint(reader)
            if nxt != 1:
                return
            await read_varint(reader)
            await read_varint(reader)              # status request
            data = {"version": {"name": "Purpur 1.21.11", "protocol": 774},
                    "players": {"max": self.max_players, "online": len(self.players),
                                "sample": [{"name": p, "id": "00000000-0000-0000-0000-000000000000"} for p in self.players[:12]]},
                    "description": {"text": "", "extra": [{"text": "Wave Online", "color": "aqua"}, {"text": " · 데모 서버"}]}}
            writer.write(packet(0x00, mc_string(json.dumps(data, ensure_ascii=False))))
            await writer.drain()
            await read_varint(reader)
            pid = await read_varint(reader)
            payload = await reader.readexactly(8)
            if pid == 1:
                await asyncio.sleep(random.uniform(0.002, 0.02))
                writer.write(packet(0x01, payload))
                await writer.drain()
        except (asyncio.IncompleteReadError, ConnectionError, ValueError):
            pass
        finally:
            writer.close()

    # ---------------- RCON ----------------
    def run(self, cmd: str) -> str:
        parts = cmd.strip().lstrip("/").split()
        if not parts:
            return ""
        c, args = parts[0].lower(), parts[1:]
        if c == "list":
            return f"There are {len(self.players)} of a max of {self.max_players} players online: " + ", ".join(self.players)
        if c == "tps":
            t = [round(min(20.0, self.tps + random.uniform(-0.2, 0.05)), 2) for _ in range(3)]
            return f"§6TPS from last 1m, 5m, 15m: §a{t[0]}, §a{t[1]}, §a{t[2]}"
        if c == "mspt":
            return f"§6Server tick times §e(§7avg§e/§7min§e/§7max§e)§6 from last 5s§7,§6 10s§7,§6 1m§e:\n§6◴ §a{random.uniform(8, 22):.1f}§7/§a5.1§7/§a41.0"
        if c in ("say", "broadcast"):
            self.log(f"[Server] {' '.join(args)}")
            return ""
        if c == "kick" and args:
            if args[0] in self.players:
                self.players.remove(args[0])
                self.log(f"Kicked {args[0]}: {' '.join(args[1:]) or 'Kicked by an operator'}")
                return f"Kicked {args[0]}: {' '.join(args[1:]) or 'Kicked by an operator'}"
            return "No player was found"
        if c == "ban" and args:
            self.banned.add(args[0])
            if args[0] in self.players:
                self.players.remove(args[0])
            return f"Banned {args[0]}: {' '.join(args[1:]) or 'Banned by an operator.'}"
        if c == "pardon" and args:
            if args[0] in self.banned:
                self.banned.discard(args[0])
                return f"Unbanned {args[0]}"
            return "Nothing changed. The player isn't banned"
        if c == "banlist":
            return f"There are {len(self.banned)} ban(s):" + "".join(f"\n{b} was banned by Server: Banned by an operator." for b in sorted(self.banned)) if self.banned else "There are no bans"
        if c == "op" and args:
            self.ops.add(args[0])
            return f"Made {args[0]} a server operator"
        if c == "deop" and args:
            self.ops.discard(args[0])
            return f"Made {args[0]} no longer a server operator"
        if c == "whitelist":
            return "Whitelist is now turned on" if args[:1] == ["on"] else "There are 0 whitelisted player(s):"
        if c in ("time", "weather", "difficulty", "gamerule", "save-all", "give", "tp", "effect"):
            if c == "save-all":
                self.log("Saving the game (this may take a moment!)")
                self.log("Saved the game")
                return "Saving the game (this may take a moment!)Saved the game"
            return f"Executed: {cmd}"
        if c == "stop":
            self.stopping.set()
            return "Stopping the server"
        return f"Unknown or incomplete command, see below for error\n{cmd}<--[HERE]"

    async def handle_rcon(self, reader, writer):
        authed = False
        try:
            while True:
                rid, kind, body = await read_packet(reader)
                if kind == 3:
                    authed = body == self.password
                    writer.write(encode(rid if authed else -1, 2, ""))
                elif not authed:
                    writer.write(encode(-1, 2, ""))
                elif kind == 2:
                    out = self.run(body)
                    if body.strip() and body.split()[0].lower() not in ("list", "tps", "mspt"):   # 상태 확인용 명령은 로그에 안 남김
                        self.log(f"Rcon issued server command: /{body.strip()}")
                    writer.write(encode(rid, 0, out))
                else:   # 빈 표시 패킷: 실제 서버처럼 그대로 돌려줌
                    writer.write(encode(rid, 0, ""))
                await writer.drain()
        except (asyncio.IncompleteReadError, ConnectionError):
            pass
        finally:
            writer.close()

    # ---------------- 생활 ----------------
    async def life(self):
        while not self.stopping.is_set():
            await asyncio.sleep(random.uniform(3, 8))
            self.tps = max(14.0, min(20.0, self.tps + random.uniform(-0.6, 0.6) + (20 - self.tps) * 0.3))
            r = random.random()
            outside = [n for n in NAMES if n not in self.players and n not in self.banned]
            if r < 0.35 and outside and len(self.players) < self.max_players:
                p = random.choice(outside)
                self.players.append(p)
                self.log(f"{p} joined the game")
            elif r < 0.6 and len(self.players) > 1:
                p = random.choice(self.players)
                self.players.remove(p)
                self.log(f"{p} left the game")
            elif r < 0.8 and self.players:
                p = random.choice(self.players)
                self.log(f"<{p}> " + random.choice(["안녕하세요!", "보스 언제 나와요?", "ㅋㅋㅋㅋ", "핫타임 시작!", "상점 어디에요?", "gg"]))


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=25565)
    ap.add_argument("--rcon-port", type=int, default=25575)
    ap.add_argument("--password", default="demo")
    ap.add_argument("--max", type=int, default=100)
    ap.add_argument("--boot", type=float, default=2.0, help="켜지는 데 걸리는 시간(초)")
    a = ap.parse_args()
    sim = Sim(a.password, a.max, a.boot)
    sim.log("Starting minecraft server version 1.21.11")
    sim.log("Loading properties")
    sim.log("This server is running Purpur version 1.21.11 (MC: 1.21.11) (Demo)")
    await asyncio.sleep(a.boot / 2)
    sim.log('Preparing level "world"')
    s1 = await asyncio.start_server(sim.handle_slp, a.host, a.port)
    s2 = await asyncio.start_server(sim.handle_rcon, a.host, a.rcon_port)
    await asyncio.sleep(a.boot / 2)
    sim.log(f"RCON running on {a.host}:{a.rcon_port}")
    sim.log(f'Done ({a.boot:.3f}s)! For help, type "help"')
    life = asyncio.create_task(sim.life())

    async def stdin_loop():
        loop = asyncio.get_running_loop()
        while not sim.stopping.is_set():
            line = await loop.run_in_executor(None, sys.stdin.readline)
            if not line:
                return
            out = sim.run(line)
            if out:
                sim.log(out)

    asyncio.create_task(stdin_loop())
    await sim.stopping.wait()
    sim.log("Stopping the server")
    sim.log("Saving players")
    life.cancel()
    s1.close()
    s2.close()
    await asyncio.sleep(0.5)
    sim.log("Saving worlds")
    sim.log("All dimensions are saved")
    sys.stdout.flush()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
    sys.stdout.flush()
    import os
    os._exit(0)   # 표준 입력을 기다리는 스레드가 있어도 바로 끝냄
