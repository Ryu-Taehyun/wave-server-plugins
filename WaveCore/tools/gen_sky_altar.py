"""
천공의 제단 (Sky Altar) 스케매틱 생성기
- 출력: Sponge Schematic v2 (.schem), WorldEdit / FAWE 의 //schem load 로 불러온다.
- 기준점(Offset): 중앙 제단 윗면. //paste 하면 서 있던 자리에 제단 중앙이 온다.

사용: python3 gen_sky_altar.py <출력.schem> [미리보기_폴더]
"""
import gzip
import math
import random
import struct
import sys

random.seed(1004)

R = 32                 # 반지름 (경기장 바닥)
W = L = R * 2 + 1      # 65
H = 42
CX = CZ = R
FLOOR = 19             # 바닥 높이
DATA_VERSION = 3955    # 1.21.1 (WorldEdit 이 최신 버전으로 자동 변환)

blocks = {}            # (x,y,z) -> blockstate


def put(x, y, z, b):
    if 0 <= x < W and 0 <= y < H and 0 <= z < L:
        blocks[(x, y, z)] = b


def dist(x, z):
    return math.hypot(x - CX, z - CZ)


def angle(x, z):
    return math.atan2(z - CZ, x - CX)


# ---------------------------------------------------------------- 공중섬 (아랫면)
for y in range(0, FLOOR):
    t = (y + 1) / FLOOR                       # 0(아래) ~ 1(위)
    base_r = 30.5 * (t ** 0.75)
    for x in range(W):
        for z in range(L):
            d = dist(x, z)
            wobble = 1.6 * math.sin(angle(x, z) * 5 + y * 0.7) + random.uniform(-0.6, 0.6)
            if d <= base_r + wobble * (1 - t * 0.6):
                if y < 3:
                    b = "minecraft:glowstone" if d < 1.5 else "minecraft:calcite"
                elif d > base_r - 1.5:
                    b = random.choice(["minecraft:calcite", "minecraft:calcite", "minecraft:diorite", "minecraft:smooth_quartz"])
                else:
                    b = "minecraft:calcite"
                put(x, y, z, b)

# 아랫면 빛 줄기 (섬 바닥에 매달린 엔드 막대)
for k in range(12):
    a = k * math.pi * 2 / 12
    r = 14 + (k % 3) * 4
    x, z = round(CX + math.cos(a) * r), round(CZ + math.sin(a) * r)
    for y in range(FLOOR - 1, -1, -1):
        if (x, y, z) not in blocks:
            put(x, y, z, "minecraft:end_rod[facing=down]")
            break

# 구름 (섬 테두리 아래)
for _ in range(14):
    a = random.uniform(0, math.pi * 2)
    r = random.uniform(24, 30)
    cx, cz, cy = CX + math.cos(a) * r, CZ + math.sin(a) * r, random.randint(10, 16)
    for x in range(int(cx) - 4, int(cx) + 5):
        for z in range(int(cz) - 4, int(cz) + 5):
            for y in range(cy - 1, cy + 2):
                if math.hypot(x - cx, (y - cy) * 2, z - cz) < 3.6 + random.uniform(-0.5, 0.5):
                    if (x, y, z) not in blocks:
                        put(x, y, z, "minecraft:white_wool")

# ---------------------------------------------------------------- 바닥 (태양 문양)
for x in range(W):
    for z in range(L):
        d = dist(x, z)
        if d > 30.5:
            continue
        a = angle(x, z)
        ray = abs(((a / (math.pi * 2) * 16) % 1) - 0.5) < 0.12     # 16갈래 빛살
        if d <= 2.5:
            b = "minecraft:chiseled_quartz_block"
        elif 3.5 <= d < 4.5 or 11.5 <= d < 12.3 or 19.5 <= d < 20.3 or d >= 29.5:
            b = "minecraft:gold_block"
        elif ray and 5 <= d < 29 and round(d) % 3 == 0:
            b = "minecraft:sea_lantern"
        elif ray and 5 <= d < 29:
            b = "minecraft:yellow_stained_glass" if d < 12 else "minecraft:quartz_bricks"
        elif d < 12:
            b = "minecraft:smooth_quartz"
        elif d < 20:
            b = "minecraft:quartz_block"
        else:
            b = "minecraft:smooth_quartz"
        put(x, FLOOR, z, b)

# 바깥 낮은 벽 (입구 4곳)
for x in range(W):
    for z in range(L):
        d = dist(x, z)
        if 29.5 <= d <= 30.5:
            a = math.degrees(angle(x, z)) % 90
            if a < 5 or a > 85:           # 동서남북 입구
                continue
            put(x, FLOOR + 1, z, "minecraft:quartz_bricks")
            put(x, FLOOR + 2, z, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]")

# ---------------------------------------------------------------- 중앙 제단 (보스 스폰)
for x in range(W):
    for z in range(L):
        d = dist(x, z)
        if d <= 6.5:
            put(x, FLOOR + 1, z, "minecraft:gold_block" if 5.5 < d else "minecraft:smooth_quartz")
        if d <= 4.5:
            put(x, FLOOR + 2, z, "minecraft:chiseled_quartz_block" if d < 1 else
                ("minecraft:gold_block" if 3.6 < d else "minecraft:quartz_bricks"))
# 제단 계단 (4방향)
for dx, dz, facing in [(0, -7, "south"), (0, 7, "north"), (-7, 0, "east"), (7, 0, "west")]:
    for w in range(-1, 2):
        x = CX + dx + (w if dx == 0 else 0)
        z = CZ + dz + (w if dz == 0 else 0)
        put(x, FLOOR + 1, z, f"minecraft:quartz_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]")

# ---------------------------------------------------------------- 기둥 8개 + 후광
PILLAR_R = 24
TOP = FLOOR + 15
for k in range(8):
    a = math.pi / 8 + k * math.pi / 4
    px, pz = round(CX + math.cos(a) * PILLAR_R), round(CZ + math.sin(a) * PILLAR_R)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            put(px + dx, FLOOR + 1, pz + dz, "minecraft:chiseled_quartz_block")
            put(px + dx, TOP, pz + dz, "minecraft:gold_block")
    for y in range(FLOOR + 2, TOP):
        put(px, y, pz, "minecraft:quartz_pillar[axis=y]")
    put(px, TOP - 1, pz, "minecraft:sea_lantern")
    put(px, TOP + 1, pz, "minecraft:end_rod[facing=up]")
    # 기둥 꼭대기 사이를 잇는 빛의 고리
for k in range(720):
    a = k * math.pi * 2 / 720
    x, z = round(CX + math.cos(a) * PILLAR_R), round(CZ + math.sin(a) * PILLAR_R)
    if (x, TOP, z) not in blocks:
        put(x, TOP, z, "minecraft:yellow_stained_glass")

# 제단 위 공중 후광 (심판의 고리)
HALO_Y = FLOOR + 13
for k in range(360):
    a = k * math.pi * 2 / 360
    for r in (5.0, 5.6):
        put(round(CX + math.cos(a) * r), HALO_Y, round(CZ + math.sin(a) * r), "minecraft:gold_block")
for k in range(8):
    a = k * math.pi / 4
    x, z = round(CX + math.cos(a) * 5.3), round(CZ + math.sin(a) * 5.3)
    put(x, HALO_Y - 1, z, "minecraft:end_rod[facing=down]")
    put(x, HALO_Y + 1, z, "minecraft:end_rod[facing=up]")
# 후광 중심 빛
put(CX, HALO_Y + 3, CZ, "minecraft:sea_lantern")
for y in range(HALO_Y + 4, H):
    put(CX, y, CZ, "minecraft:end_rod[facing=up]") if y == HALO_Y + 4 else None

# ---------------------------------------------------------------- NBT 쓰기 (Sponge v2)


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def s_str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag(t, name, payload):
    return bytes([t]) + s_str(name) + payload


palette = {"minecraft:air": 0}
data = bytearray()
counts = {}
for y in range(H):
    for z in range(L):
        for x in range(W):
            b = blocks.get((x, y, z), "minecraft:air")
            if b not in palette:
                palette[b] = len(palette)
            counts[b] = counts.get(b, 0) + 1
            data += varint(palette[b])

pal_payload = b"".join(tag(3, k, struct.pack(">i", v)) for k, v in palette.items()) + b"\x00"
body = b"".join([
    tag(3, "Version", struct.pack(">i", 2)),
    tag(3, "DataVersion", struct.pack(">i", DATA_VERSION)),
    tag(2, "Width", struct.pack(">h", W)),
    tag(2, "Height", struct.pack(">h", H)),
    tag(2, "Length", struct.pack(">h", L)),
    tag(11, "Offset", struct.pack(">i", 3) + struct.pack(">iii", -CX, -(FLOOR + 3), -CZ)),
    tag(3, "PaletteMax", struct.pack(">i", len(palette))),
    tag(10, "Palette", pal_payload),
    tag(7, "BlockData", struct.pack(">i", len(data)) + bytes(data)),
    tag(9, "BlockEntities", bytes([10]) + struct.pack(">i", 0)),
])
nbt = tag(10, "Schematic", body + b"\x00")

out = sys.argv[1] if len(sys.argv) > 1 else "sky_altar.schem"
with gzip.open(out, "wb") as f:
    f.write(nbt)
print(f"{out}: {W}x{H}x{L}, 블록 {sum(v for k, v in counts.items() if k != 'minecraft:air')}개, 팔레트 {len(palette)}종")
for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:12]:
    print(f"  {v:6d}  {k}")

# ---------------------------------------------------------------- 미리보기
if len(sys.argv) > 2:
    from PIL import Image, ImageDraw
    COLORS = {
        "calcite": (225, 226, 221), "diorite": (190, 190, 190), "smooth_quartz": (236, 230, 223),
        "quartz_block": (235, 229, 222), "quartz_bricks": (234, 228, 219), "chiseled_quartz_block": (231, 226, 218),
        "quartz_pillar": (236, 230, 220), "gold_block": (246, 208, 61), "sea_lantern": (172, 199, 190),
        "yellow_stained_glass": (229, 229, 51), "end_rod": (255, 250, 230), "glowstone": (255, 200, 120),
        "white_wool": (250, 250, 250), "smooth_quartz_slab": (236, 230, 223), "quartz_stairs": (235, 229, 222),
    }

    def col(b):
        n = b.split(":")[1].split("[")[0]
        return COLORS.get(n, (200, 0, 200))

    S = 8
    top = Image.new("RGB", (W * S, L * S), (120, 170, 230))
    for x in range(W):
        for z in range(L):
            for y in range(H - 1, -1, -1):
                b = blocks.get((x, y, z))
                if b:
                    c = col(b)
                    shade = 0.75 + 0.25 * (y / H)
                    top.paste(tuple(int(v * shade) for v in c), (x * S, z * S, x * S + S, z * S + S))
                    break
    side = Image.new("RGB", (W * S, H * S), (120, 170, 230))
    for x in range(W):
        for y in range(H):
            for z in range(L):
                b = blocks.get((x, y, z))
                if b:
                    c = col(b)
                    shade = 0.7 + 0.3 * (1 - z / L)
                    side.paste(tuple(int(v * shade) for v in c), (x * S, (H - 1 - y) * S, x * S + S, (H - y) * S))
                    break
    canvas = Image.new("RGB", (W * S * 2 + 30, max(L, H) * S + 60), (24, 26, 32))
    canvas.paste(top, (10, 50))
    canvas.paste(side, (W * S + 20, 50))
    d = ImageDraw.Draw(canvas)
    d.text((10, 15), "TOP VIEW", fill=(255, 255, 255))
    d.text((W * S + 20, 15), "SIDE VIEW", fill=(255, 255, 255))
    canvas.save(sys.argv[2])
    print("preview:", sys.argv[2])
