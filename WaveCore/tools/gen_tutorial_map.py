"""
튜토리얼 섬 (Tutorial Island) 스케매틱 생성기
- 출력: Sponge Schematic v2 (.schem), WorldEdit / FAWE 의 //schem load 로 불러온다.
- 기준점(Offset): 남쪽 시작 광장 가운데 (플레이어가 서는 칸). //paste 하면 서 있던 자리에 시작 광장이 온다.
- 구성: 시작 광장 → 둥근 길을 따라 튜토리얼 8단계 정거장(표지판에 할 일) → 가운데 Wave 기념탑(완료 지점)

사용: python3 gen_tutorial_map.py <출력.schem> [미리보기.png]
"""
import gzip
import json
import math
import random
import struct
import sys

random.seed(2026)

W = L = 63
H = 46
CX = CZ = 31
FLOOR = 24             # 잔디 윗면 높이
DATA_VERSION = 3955    # 1.21.1 (WorldEdit 이 최신 버전으로 자동 변환)
RING = 13.5            # 둥근 길 반지름
ST_R = 20              # 정거장 중심 반지름

blocks = {}            # (x,y,z) -> blockstate
signs = []             # (x,y,z, [4줄], 색)
roofs = set()          # 미리보기 '지붕 없이' 용
labels = []            # 미리보기용 (x,y,z, 글자)


def put(x, y, z, b):
    if 0 <= x < W and 0 <= y < H and 0 <= z < L:
        blocks[(x, y, z)] = b


def get(x, y, z):
    return blocks.get((x, y, z))


def ang(x, z):
    return math.atan2(z - CZ, x - CX)


def island_r(a):
    return 28.2 + 1.3 * math.sin(3 * a + 0.4) + 0.8 * math.sin(7 * a + 1.3)


# ---------------------------------------------------------------- 섬 몸체
for x in range(W):
    for z in range(L):
        d = math.hypot(x - CX, z - CZ)
        a = ang(x, z)
        R = island_r(a)
        if d > R:
            continue
        # 윗면
        put(x, FLOOR, z, "minecraft:grass_block")
        for y in range(FLOOR - 3, FLOOR):
            put(x, y, z, "minecraft:dirt")
        # 아랫면: 아래로 갈수록 좁아지는 바위
        edge = R - d
        depth = 4 + int(min(FLOOR - 4, (edge ** 0.85) * 1.55 + random.uniform(-1.2, 1.2)))
        for y in range(FLOOR - 3 - depth, FLOOR - 3):
            if y < 0:
                continue
            k = random.random()
            b = "minecraft:stone" if k < 0.55 else "minecraft:andesite" if k < 0.8 else "minecraft:tuff" if k < 0.93 else "minecraft:moss_block"
            put(x, y, z, b)

# 섬 가장자리 아래 매달린 이끼 · 덩굴
for x in range(W):
    for z in range(L):
        if get(x, FLOOR - 3, z) and random.random() < 0.08:
            y = FLOOR - 4
            while y > 0 and get(x, y, z):
                y -= 1
            for k in range(random.randint(1, 3)):
                if y - k > 0 and not get(x, y - k, z):
                    put(x, y - k, z, "minecraft:cave_vines_plant[berries=true]" if random.random() < 0.3 else "minecraft:hanging_roots")


def ground(x, z, b):
    if get(x, FLOOR, z):
        put(x, FLOOR, z, b)


# ---------------------------------------------------------------- 둥근 길 + 가로등
for x in range(W):
    for z in range(L):
        d = math.hypot(x - CX, z - CZ)
        if RING - 1.3 <= d <= RING + 1.3:
            ground(x, z, "minecraft:dirt_path" if random.random() < 0.85 else "minecraft:coarse_dirt")

for k in range(16):
    a = (k + 0.5) * math.pi * 2 / 16
    x, z = round(CX + math.cos(a) * (RING + 2.6)), round(CZ + math.sin(a) * (RING + 2.6))
    if k % 2 == 0:
        continue
    put(x, FLOOR + 1, z, "minecraft:spruce_fence")
    put(x, FLOOR + 2, z, "minecraft:spruce_fence")
    put(x, FLOOR + 3, z, "minecraft:lantern")

# ---------------------------------------------------------------- 시작 광장 (남쪽)
SPX, SPZ = CX, CZ + 23
for x in range(W):
    for z in range(L):
        d = math.hypot(x - SPX, z - SPZ)
        if d <= 4.6:
            ground(x, z, "minecraft:chiseled_stone_bricks" if d < 0.6 else "minecraft:polished_andesite" if d < 3.5 else "minecraft:stone_bricks")
# 광장 → 둥근 길
for z in range(CZ + int(RING), SPZ - 3):
    for x in range(CX - 1, CX + 2):
        ground(x, z, "minecraft:dirt_path")
# 광장 둘레 등불
for k in range(6):
    a = math.pi / 2 + (k - 2.5) * 0.62
    x, z = round(SPX + math.cos(a) * 5.4), round(SPZ + math.sin(a) * 5.4)
    if get(x, FLOOR, z):
        put(x, FLOOR + 1, z, "minecraft:polished_andesite_wall")
        put(x, FLOOR + 2, z, "minecraft:lantern")
# 환영 표지판 (광장 북쪽 끝, 남쪽을 바라봄)
signs.append((SPX, FLOOR + 1, SPZ - 3, ["", "Wave Online", "튜토리얼 섬", ""], 0, "dark_aqua"))
signs.append((SPX - 2, FLOOR + 1, SPZ - 3, ["화면 위", "보스바 안내를", "따라가세요", ""], 0, "black"))
signs.append((SPX + 2, FLOOR + 1, SPZ - 3, ["/튜토리얼", "로 할 일", "목록 보기", ""], 0, "black"))
labels.append((SPX, FLOOR + 1, SPZ, "시작 (스폰)"))

# ---------------------------------------------------------------- 가운데 Wave 기념탑 (완료 지점)
for x in range(W):
    for z in range(L):
        d = math.hypot(x - CX, z - CZ)
        if d <= 7.4:
            put(x, FLOOR, z, "minecraft:prismarine_bricks" if d > 6.4 else "minecraft:water" if d > 2.2 else "minecraft:dark_prismarine")
            if 2.2 < d <= 6.4:
                put(x, FLOOR - 1, z, "minecraft:sea_lantern" if (x + z) % 5 == 0 else "minecraft:prismarine")
        if 6.4 < d <= 7.4:
            put(x, FLOOR + 1, z, "minecraft:prismarine_slab[type=bottom]")
# 탑
for y in range(FLOOR + 1, FLOOR + 12):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if abs(dx) + abs(dz) == 2 and y > FLOOR + 9:
                continue
            put(CX + dx, y, CZ + dz, "minecraft:sea_lantern" if (y - FLOOR) % 4 == 0 else "minecraft:dark_prismarine" if abs(dx) + abs(dz) == 2 else "minecraft:prismarine_bricks")
# 물결 고리 (탑 둘레를 도는 파란 유리)
for k in range(40):
    a = k * math.pi * 2 / 40
    y = FLOOR + 7 + round(1.5 * math.sin(a * 2))
    put(round(CX + math.cos(a) * 3.6), y, round(CZ + math.sin(a) * 3.6), "minecraft:light_blue_stained_glass")
put(CX, FLOOR + 12, CZ, "minecraft:beacon")
put(CX, FLOOR + 13, CZ, "minecraft:end_rod[facing=up]")
# 기념탑 남쪽 다리 (길에서 물 건너 탑 앞까지)
for z in range(CZ + 2, CZ + int(RING)):
    for x in range(CX - 1, CX + 2):
        if get(x, FLOOR, z) in ("minecraft:water", "minecraft:prismarine_bricks", "minecraft:grass_block", "minecraft:dirt_path", "minecraft:coarse_dirt"):
            put(x, FLOOR, z, "minecraft:smooth_quartz")
signs.append((CX, FLOOR + 1, CZ + 2, ["모든 단계를", "마치면 보상과", "함께 스폰으로", "이동합니다!"], 0, "dark_aqua"))
labels.append((CX, FLOOR + 13, CZ, "완료 기념탑"))

# ---------------------------------------------------------------- 정거장 8개 (튜토리얼 단계)
STATIONS = [
    ("서버 메뉴", ["1. 서버 메뉴", "Shift + F", "를 눌러 보세요", ""], "minecraft:light_blue_wool", "menu"),
    ("이동 메뉴", ["2. 이동 메뉴", "서버 메뉴 →", "이동 메뉴 열기", ""], "minecraft:purple_wool", "warp"),
    ("상점", ["3. 상점", "/상점", "", ""], "minecraft:lime_wool", "shop"),
    ("배낭", ["4. 배낭", "/배낭", "", ""], "minecraft:brown_wool", "backpack"),
    ("아이템 뽑기", ["5. 아이템 뽑기", "/상자", "", ""], "minecraft:magenta_wool", "crate"),
    ("프리미엄 패스", ["6. 프리미엄 패스", "/pass", "", ""], "minecraft:yellow_wool", "pass"),
    ("컬렉션", ["7. 컬렉션", "/컬렉션", "", ""], "minecraft:pink_wool", "collection"),
    ("업적", ["8. 업적", "/업적", "", ""], "minecraft:orange_wool", "ach"),
]


def rotation_toward(dx, dz):
    """표지판 앞면이 (dx, dz) 쪽을 보게 하는 rotation (0 = 남쪽)"""
    return round(math.degrees(math.atan2(-dx, dz)) / 22.5) % 16


def feature(kind, x, y, z):
    if kind == "menu":
        put(x, y, z, "minecraft:lodestone")
        put(x - 1, y, z, "minecraft:lectern[facing=east]")
        put(x + 1, y, z, "minecraft:cartography_table")
    elif kind == "warp":
        put(x, y, z, "minecraft:respawn_anchor[charges=4]")
        for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            put(x + dx, y - 1, z + dz, "minecraft:crying_obsidian")
            put(x + dx, y, z + dz, "minecraft:amethyst_cluster[facing=up]")
    elif kind == "shop":
        for dx in (-1, 0, 1):
            put(x + dx, y, z, "minecraft:emerald_block" if dx == 0 else "minecraft:barrel[facing=up]")
        put(x, y + 1, z, "minecraft:emerald_block")
    elif kind == "backpack":
        put(x - 1, y, z, "minecraft:chest[facing=south]")
        put(x, y, z, "minecraft:barrel[facing=up]")
        put(x, y + 1, z, "minecraft:barrel[facing=up]")
        put(x + 1, y, z, "minecraft:chest[facing=south]")
    elif kind == "crate":
        put(x, y - 1, z, "minecraft:crying_obsidian")
        put(x, y, z, "minecraft:ender_chest[facing=south]")
        put(x - 1, y, z, "minecraft:amethyst_block")
        put(x + 1, y, z, "minecraft:amethyst_block")
        put(x - 1, y + 1, z, "minecraft:amethyst_cluster[facing=up]")
        put(x + 1, y + 1, z, "minecraft:amethyst_cluster[facing=up]")
    elif kind == "pass":
        put(x, y, z, "minecraft:gold_block")
        put(x, y + 1, z, "minecraft:bell[attachment=floor,facing=south]")
        put(x - 1, y, z, "minecraft:honeycomb_block")
        put(x + 1, y, z, "minecraft:honeycomb_block")
    elif kind == "collection":
        for dx in (-1, 0, 1):
            put(x + dx, y, z, "minecraft:polished_deepslate")
            put(x + dx, y + 1, z, "minecraft:amethyst_cluster[facing=up]" if dx else "minecraft:diamond_block")
    elif kind == "ach":
        put(x, y, z, "minecraft:enchanting_table")
        put(x - 1, y, z, "minecraft:bookshelf")
        put(x + 1, y, z, "minecraft:bookshelf")
        put(x - 1, y + 1, z, "minecraft:bookshelf")
        put(x + 1, y + 1, z, "minecraft:bookshelf")


for i, (name, lines, roof, kind) in enumerate(STATIONS):
    a = math.pi / 2 + (i + 0.5) * math.pi * 2 / 8      # 시작 광장(남쪽)에서 시계 방향
    sx, sz = round(CX + math.cos(a) * ST_R), round(CZ + math.sin(a) * ST_R)
    dx, dz = CX - sx, CZ - sz
    n = math.hypot(dx, dz)
    ux, uz = dx / n, dz / n
    # 둥근 길 → 정거장 연결
    for t in range(0, 8):
        px, pz = sx + ux * (2 + t * 0.7), sz + uz * (2 + t * 0.7)
        for ox in (0, 1):
            ground(int(round(px - 0.5 + ox)), int(round(pz)), "minecraft:dirt_path")
    # 바닥 5x5
    for x in range(sx - 2, sx + 3):
        for z in range(sz - 2, sz + 3):
            edge = abs(x - sx) == 2 or abs(z - sz) == 2
            put(x, FLOOR, z, "minecraft:spruce_planks" if not edge else "minecraft:stripped_spruce_log[axis=y]")
            for y in range(FLOOR + 1, FLOOR + 6):
                if (x, y, z) in blocks:
                    del blocks[(x, y, z)]
    # 기둥 + 등불
    for cx, cz in ((sx - 2, sz - 2), (sx + 2, sz - 2), (sx - 2, sz + 2), (sx + 2, sz + 2)):
        for y in range(FLOOR + 1, FLOOR + 5):
            put(cx, y, cz, "minecraft:stripped_spruce_log[axis=y]")
    # 지붕 (양털 + 가장자리 반 블록)
    for x in range(sx - 3, sx + 4):
        for z in range(sz - 3, sz + 4):
            edge = abs(x - sx) == 3 or abs(z - sz) == 3
            roofs.update({(x, FLOOR + 5, z), (x, FLOOR + 6, z)})
            if edge:
                put(x, FLOOR + 5, z, "minecraft:spruce_slab[type=bottom]")
            else:
                put(x, FLOOR + 5, z, roof)
                if abs(x - sx) <= 1 and abs(z - sz) <= 1:
                    put(x, FLOOR + 6, z, roof)
    put(sx, FLOOR + 7, sz, "minecraft:lantern")
    roofs.add((sx, FLOOR + 7, sz))
    for cx, cz in ((sx - 1, sz - 1), (sx + 1, sz + 1)):
        put(cx, FLOOR + 4, cz, "minecraft:lantern[hanging=true]")
    feature(kind, sx, FLOOR + 1, sz)
    # 표지판 (가운데 쪽 가장자리, 길을 바라봄)
    gx, gz = round(sx + ux * 2.6), round(sz + uz * 2.6)
    put(gx, FLOOR, gz, "minecraft:spruce_planks")
    signs.append((gx, FLOOR + 1, gz, lines, rotation_toward(ux, uz), "black"))
    labels.append((sx, FLOOR + 7, sz, f"{i + 1}. {name}"))

# ---------------------------------------------------------------- 나무 · 꽃
def tree(x, z, cherry):
    h = random.randint(4, 6)
    log = "minecraft:cherry_log[axis=y]" if cherry else "minecraft:oak_log[axis=y]"
    leaf = "minecraft:cherry_leaves[persistent=true]" if cherry else "minecraft:oak_leaves[persistent=true]"
    for y in range(FLOOR + 1, FLOOR + 1 + h):
        put(x, y, z, log)
    top = FLOOR + h
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                if math.hypot(dx, dz, dy * 1.4) <= 2.9 + random.uniform(-0.4, 0.3) and not get(x + dx, top + dy, z + dz):
                    put(x + dx, top + dy, z + dz, leaf)


busy = set()
for (x, y, z), b in list(blocks.items()):
    if y == FLOOR and b != "minecraft:grass_block":
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                busy.add((x + dx, z + dz))
trees = 0
for _ in range(400):
    a = random.uniform(0, math.pi * 2)
    r = random.uniform(16, 26)
    x, z = round(CX + math.cos(a) * r), round(CZ + math.sin(a) * r)
    if (x, z) in busy or get(x, FLOOR, z) != "minecraft:grass_block" or math.hypot(x - CX, z - CZ) > island_r(a) - 2.5:
        continue
    tree(x, z, random.random() < 0.4)
    trees += 1
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            busy.add((x + dx, z + dz))
    if trees >= 11:
        break

FLOWERS = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:allium"]
for x in range(W):
    for z in range(L):
        if get(x, FLOOR, z) == "minecraft:grass_block" and not get(x, FLOOR + 1, z):
            k = random.random()
            if k < 0.16:
                put(x, FLOOR + 1, z, "minecraft:short_grass")
            elif k < 0.21:
                put(x, FLOOR + 1, z, random.choice(FLOWERS))

for (x, y, z, *_r) in signs:
    blocks.pop((x, y, z), None)
    rot = _r[1]
    put(x, y, z, f"minecraft:spruce_sign[rotation={rot}]")

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


def p_string(s):
    return s_str(s)


def p_list_str(items):
    return bytes([8]) + struct.pack(">i", len(items)) + b"".join(p_string(i) for i in items)


def sign_text(lines, color):
    first = next((i for i, l in enumerate(lines) if l), -1)   # 처음 나오는 글자 줄을 굵게
    msgs = [json.dumps({"text": l, "color": color, "bold": i == first}, ensure_ascii=False) for i, l in enumerate(lines)]
    return (tag(9, "messages", p_list_str(msgs)) + tag(8, "color", p_string("black"))
            + tag(1, "has_glowing_text", bytes([1 if color != "black" else 0])) + b"\x00")


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

entities = b""
for (x, y, z, lines, rot, color) in signs:
    entities += (tag(8, "Id", p_string("minecraft:sign"))
                 + tag(11, "Pos", struct.pack(">i", 3) + struct.pack(">iii", x, y, z))
                 + tag(10, "front_text", sign_text(lines, color))
                 + tag(10, "back_text", sign_text(["", "", "", ""], "black"))
                 + tag(1, "is_waxed", b"\x01") + b"\x00")

pal_payload = b"".join(tag(3, k, struct.pack(">i", v)) for k, v in palette.items()) + b"\x00"
body = b"".join([
    tag(3, "Version", struct.pack(">i", 2)),
    tag(3, "DataVersion", struct.pack(">i", DATA_VERSION)),
    tag(2, "Width", struct.pack(">h", W)),
    tag(2, "Height", struct.pack(">h", H)),
    tag(2, "Length", struct.pack(">h", L)),
    tag(11, "Offset", struct.pack(">i", 3) + struct.pack(">iii", -SPX, -(FLOOR + 1), -SPZ)),
    tag(3, "PaletteMax", struct.pack(">i", len(palette))),
    tag(10, "Palette", pal_payload),
    tag(7, "BlockData", struct.pack(">i", len(data)) + bytes(data)),
    tag(9, "BlockEntities", bytes([10]) + struct.pack(">i", len(signs)) + entities),
])
nbt = tag(10, "Schematic", body + b"\x00")

out = sys.argv[1] if len(sys.argv) > 1 else "tutorial_island.schem"
with gzip.open(out, "wb") as f:
    f.write(nbt)
print(f"{out}: {W}x{H}x{L}, 블록 {sum(v for k, v in counts.items() if k != 'minecraft:air')}개, 팔레트 {len(palette)}종, 표지판 {len(signs)}개")

if len(sys.argv) > 2:
    import iso_render
    iso_render.render(blocks, W, H, L, labels, sys.argv[2], title="튜토리얼 섬")
    print("preview:", sys.argv[2])
    open_view = {p: b for p, b in blocks.items() if p not in roofs}
    low = [(x, FLOOR + 3 if y > FLOOR + 6 and y < FLOOR + 9 else y, z, t) for (x, y, z, t) in labels]
    out2 = sys.argv[2].replace(".png", "_open.png")
    iso_render.render(open_view, W, H, L, low, out2, title="튜토리얼 섬 (지붕 없이 보기)")
    print("preview:", out2)
