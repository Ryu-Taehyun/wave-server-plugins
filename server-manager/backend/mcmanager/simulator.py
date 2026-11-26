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
import re
import time
from pathlib import Path

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
        self.whitelist: set[str] = set()
        self.whitelist_on = False
        self.maintenance = False
        self.hot_until = 0.0
        self.hot_mult = 2.0

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
            sub = args[0] if args else "list"
            if sub == "on":
                self.whitelist_on = True
                return "Whitelist is now turned on"
            if sub == "off":
                self.whitelist_on = False
                return "Whitelist is now turned off"
            if sub == "add" and len(args) > 1:
                self.whitelist.add(args[1])
                return f"Added {args[1]} to the whitelist"
            if sub == "remove" and len(args) > 1:
                self.whitelist.discard(args[1])
                return f"Removed {args[1]} from the whitelist"
            return f"There are {len(self.whitelist)} whitelisted player(s): " + ", ".join(sorted(self.whitelist)) if self.whitelist else "There are no whitelisted players"
        if c in ("save-off", "save-on"):
            return "Automatic saving is now disabled" if c == "save-off" else "Automatic saving is now enabled"
        if c == "핫타임":
            if args[:1] == ["시작"]:
                mins = int(args[1]) if len(args) > 1 else 60
                self.hot_mult = float(args[2]) if len(args) > 2 else 2.0
                self.hot_until = time.time() + mins * 60
                self.log(f"[핫타임] 지금부터 {mins}분 동안 보상 x{self.hot_mult:g}!")
                return ""
            if args[:1] == ["종료"]:
                self.hot_until = 0
                return "§7핫타임이 종료되었습니다."
            if self.hot_until > time.time():
                left = int(self.hot_until - time.time())
                return f"§6[ 핫타임 ] §6§l🔥 진행 중! §fx{self.hot_mult:g} §7· 남은 시간 §f{left // 60}:{left % 60:02d}"
            return "§6[ 핫타임 ] §7다음 핫타임 : §f오늘 20:00"
        if c == "점검모드":
            if args[:1] == ["켜기"]:
                self.maintenance = True
                self.players.clear()
                return "§c[🛑] 서버 점검 모드를 활성화했습니다."
            if args[:1] == ["끄기"]:
                self.maintenance = False
                return "§a[🟢] 서버 점검 모드를 비활성화했습니다."
            return "§7현재 상태 : " + ("§c점검 중" if self.maintenance else "§a정상 운영 중")
        if c == "wave" and args[:1] == ["reload"]:
            return "§aconfig.yml, private.yml 을 다시 불러왔습니다."
        if c in ("time", "weather", "difficulty", "gamerule", "save-all", "give", "tp", "effect"):
            if c == "save-all":
                self.log("Saving the game (this may take a moment!)")
                self.log("Saved the game")
                return "Saving the game (this may take a moment!)Saved the game"
            return f"Executed: {cmd}"
        if c in ("경고", "경고삭제", "임시밴", "serverban", "unserverban", "뮤트", "뮤트해제"):
            return self.sanction(c, args)
        if c == "tellraw" and len(args) >= 2:
            try:
                text = "".join(p.get("text", "") for p in json.loads(cmd.split(" ", 2)[2]))
            except (ValueError, AttributeError):
                return "Invalid JSON"
            self.log(text) if args[0] == "@a" else None
            return ""
        if c == "stop":
            self.stopping.set()
            return "Stopping the server"
        return f"Unknown or incomplete command, see below for error\n{cmd}<--[HERE]"

    # ---------------- WaveCore 제재 흉내 (서버 폴더의 plugins/WaveCore 파일을 바꿈) ----------------
    WC = Path("plugins/WaveCore")

    def _pfile(self, name: str) -> Path | None:
        for f in (self.WC / "playerdata").glob("*.yml"):
            if re.search(rf"^name: {re.escape(name)}$", f.read_text(encoding="utf-8"), re.M | re.I):
                return f
        return None

    def _set(self, f: Path, key: str, value) -> None:
        t = f.read_text(encoding="utf-8")
        t = re.sub(rf"^{key}:.*$", f"{key}: {value}", t, flags=re.M) if re.search(rf"^{key}:", t, re.M) else t + f"{key}: {value}\n"
        f.write_text(t, encoding="utf-8")

    def _get(self, f: Path, key: str) -> str:
        m = re.search(rf"^{key}: ?(.*)$", f.read_text(encoding="utf-8"), re.M)
        return m.group(1).strip() if m else ""

    def _record(self, kind: str, name: str, f: Path, detail: str) -> None:
        (self.WC / "logs").mkdir(parents=True, exist_ok=True)
        with open(self.WC / "logs" / "sanctions.log", "a", encoding="utf-8") as out:
            out.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} | {kind} | {name}({f.stem}) | {detail} | Console\n")

    @staticmethod
    def _dur(s: str) -> int:
        m = re.fullmatch(r"(\d+)([mhdw])", s or "")
        return int(m.group(1)) * {"m": 60, "h": 3600, "d": 86400, "w": 604800}[m.group(2)] * 1000 if m else 0

    def _bans(self, update=None) -> None:
        g = self.WC / "global.yml"
        t = g.read_text(encoding="utf-8") if g.exists() else "maintenance: false\n"
        head, _, rest = t.partition("bans:\n")
        blocks = re.findall(r"(  [0-9a-f-]{36}:\n(?:    .*\n)*)", rest)
        blocks = update(blocks) if update else blocks
        g.write_text(head.rstrip("\n") + "\n" + ("bans:\n" + "".join(blocks) if blocks else ""), encoding="utf-8")

    def sanction(self, c: str, args: list[str]) -> str:
        if not args:
            return "§c사용법을 확인하세요"
        f = self._pfile(args[0])
        if not f:
            return f"§c{args[0]} 플레이어를 찾을 수 없습니다."
        name, rest = self._get(f, "name"), args[1:]
        now = int(time.time() * 1000)
        if c == "경고":
            n = int(self._get(f, "warnings") or 0) + 1
            self._set(f, "warnings", n)
            self._record("경고", name, f, f"{' '.join(rest)} ({n}회)")
            if name in self.players:
                self.log(f"[⚠] {name} 님이 경고를 받았습니다: {' '.join(rest)}")
            return f"§e{name} §f님에게 경고를 줬습니다. (누적 {n}회)"
        if c == "경고삭제":
            cur = int(self._get(f, "warnings") or 0)
            n = 0 if not rest or rest[0] == "전체" else max(0, cur - int(rest[0]))
            self._set(f, "warnings", n)
            self._record("경고삭제", name, f, f"{cur}회 → {n}회")
            return f"§a{name} 님의 경고를 {cur - n}회 지웠습니다. (남은 {n}회)"
        if c in ("임시밴", "serverban"):
            until = 0
            if c == "임시밴":
                until = now + self._dur(rest[0] if rest else "")
                if until == now:
                    return "§c기간은 30m 2h 7d 1w 처럼 적어 주세요"
                rest = rest[1:]
            reason = " ".join(rest)
            block = f"  {f.stem}:\n    reason: {reason}\n    code: {random.randrange(16**6):06X}\n    until: {until}\n    by: Console\n    at: {now}\n"
            self._bans(lambda bl: [b for b in bl if not b.startswith(f"  {f.stem}:")] + [block])
            self._record("임시밴" if until else "영구밴", name, f, (f"{args[1]} · " if until else "") + reason)
            if name in self.players:
                self.players.remove(name)
                self.log(f"{name} lost connection: 서버에서 차단되었습니다")
            self.banned.add(name)
            return f"§c{name} 님을 차단했습니다." + (f" ({args[1]})" if until else " (영구)")
        if c == "unserverban":
            self._bans(lambda bl: [b for b in bl if not b.startswith(f"  {f.stem}:")])
            self.banned.discard(name)
            self._record("차단해제", name, f, "-")
            return f"§a{name} 님의 차단을 풀었습니다."
        if c == "뮤트":
            d = self._dur(rest[0] if rest else "")
            if not d:
                return "§c기간은 30m 2h 7d 1w 처럼 적어 주세요"
            self._set(f, "mute-until", now + d)
            self._set(f, "mute-reason", " ".join(rest[1:]))
            self._record("뮤트", name, f, f"{rest[0]} · {' '.join(rest[1:])}")
            return f"§e{name} 님을 {rest[0]} 동안 채팅 금지했습니다."
        if c == "뮤트해제":
            self._set(f, "mute-until", 0)
            self._record("뮤트해제", name, f, "-")
            return f"§a{name} 님의 채팅 금지를 풀었습니다."
        return ""

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
                    if body.strip() and body.split()[0].lower() not in ("list", "tps", "mspt", "핫타임", "whitelist"):   # 상태 확인용 명령은 로그에 안 남김
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
            elif r < 0.97 and self.players:
                self.chat(random.choice(self.players))
            elif self.players:
                p = random.choice(self.players)
                t = random.choice([n for n in NAMES if n != p])
                self.log(f"{p} issued server command: /report {t} " + random.choice(["욕설했어요", "핵 쓰는 것 같아요", "사기 거래", "도배"]))

    def chat(self, p: str) -> None:
        """WaveCore 채팅 형식: [Local] 팀 | [칭호] - 닉네임 : 메시지"""
        r = random.random()
        if r < 0.12:
            t = random.choice([n for n in self.players if n != p] or [p])
            self.log(f"{p} issued server command: /귓 {t} " + random.choice(["거래 ㄱ?", "파티 할래요?", "ㅇㅋ"]))
            return
        msg = random.choice(["안녕하세요!", "보스 언제 나와요?", "ㅋㅋㅋㅋ", "핫타임 시작!", "상점 어디에요?", "gg", "다이아 삽니다",
                             "레벨 30 찍었다", "천공의 심판자 떴다!!", "같이 던전 가실 분", "ㄹㅇ 개꿀", "스폰 어디임"]
                            + (["싸게 팝니다 cheapmc.xyz 접속", "바 보 멍 청 이"] if random.random() < 0.1 else []))
        ch = random.choice(["", "", "", "[Local] ", "[TEAM] "])
        team = random.choice(["", "", "블루 | ", "레드 | "]) if ch != "[Local] " else ""
        title = random.choice(["", "[🥇 S1 부의 제왕 1위] ", "[⚔ 학살자] ", "[🌱 새싹] "])
        self.log(f"{ch}{team}{title}- {p} : {msg}")


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
    # 데모 화면용: 켜자마자 채팅 몇 줄 (광고 · 신고 포함)
    for line in ("- {0} : 안녕하세요~", "[Local] [🌱 새싹] - {1} : 스폰 어디임", "블루 | [⚔ 학살자] - {2} : 천공의 심판자 떴다!!",
                 "- {1} : 싸게 팝니다 cheapmc.xyz 접속", "{0} issued server command: /report {1} 광고해요"):
        await asyncio.sleep(0.4)
        sim.log(line.format(*(sim.players * 3)))

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
