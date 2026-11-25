"""
스폰 섬 (Wave Spawn) 스케매틱 생성기
- 출력: Sponge Schematic v2 (.schem), WorldEdit / FAWE 의 //schem load 로 불러온다.
- 기준점(Offset): 스폰 지점 (광장 남쪽, 분수를 바라보는 칸). //paste 하면 서 있던 자리가 스폰 지점이 된다.
- 구성: 가운데 Wave 분수 광장 + 물길 · 다리
        북쪽 포탈 광장 (야생 · 보호구역 · 천공의 제단), 동쪽 시장 (상점 노점 6개), 서쪽 경험치 구역, 남쪽 랭킹 단상 · 안내 게시판

사용: python3 gen_spawn_map.py <출력.schem> [미리보기.png]
"""
import math
import random
import sys

import schem_util

random.seed(77)

W = L = 105
H = 64
CX = CZ = 52
FLOOR = 28
CANAL_IN, CANAL_OUT = 14.5, 17.5
SPAWN = (CX, FLOOR + 1, CZ + 9)

blocks = {}
signs = []
labels = []


def put(x, y, z, b):
    if 0 <= x < W and 0 <= y < H and 0 <= z < L:
        blocks[(x, y, z)] = b


def get(x, y, z):
    return blocks.get((x, y, z))


def clear(x, y, z):
    blocks.pop((x, y, z), None)


def ang(x, z):
    return math.atan2(z - CZ, x - CX)


def dist(x, z):
    return math.hypot(x - CX, z - CZ)


def island_r(a):
    return 49.5 + 1.5 * math.sin(3 * a + 0.3) + 1.0 * math.sin(5 * a + 1.1)


def ground(x, z, b):
    if get(x, FLOOR, z):
        put(x, FLOOR, z, b)


def sign(x, y, z, lines, rot=0, color="black", wall=None):
    """rot: 세우는 표지판 방향(0 = 남쪽을 봄), wall: 벽 표지판이면 바라보는 방향(north 등)"""
    signs.append((x, y, z, lines, rot, color, wall))


# ---------------------------------------------------------------- 섬 몸체
for x in range(W):
    for z in range(L):
        d, a = dist(x, z), ang(x, z)
        R = island_r(a)
        if d > R:
            continue
        put(x, FLOOR, z, "minecraft:grass_block")
        for y in range(FLOOR - 3, FLOOR):
            put(x, y, z, "minecraft:dirt")
        depth = 4 + int(min(FLOOR - 5, ((R - d) ** 0.8) * 1.4 + random.uniform(-1.2, 1.2)))
        for y in range(FLOOR - 3 - depth, FLOOR - 3):
            if y >= 0:
                k = random.random()
                put(x, y, z, "minecraft:stone" if k < 0.5 else "minecraft:andesite" if k < 0.75 else "minecraft:tuff" if k < 0.9 else "minecraft:moss_block")
for x in range(W):
    for z in range(L):
        if get(x, FLOOR - 3, z) and random.random() < 0.06:
            y = FLOOR - 4
            while y > 0 and get(x, y, z):
                y -= 1
            for k in range(random.randint(1, 4)):
                if y - k > 0 and not get(x, y - k, z):
                    put(x, y - k, z, "minecraft:cave_vines_plant[berries=true]" if random.random() < 0.3 else "minecraft:hanging_roots")

# ---------------------------------------------------------------- 길 (십자) + 가로등
ROAD_END = 44


def on_road(x, z):
    dx, dz = abs(x - CX), abs(z - CZ)
    return (dx <= 2 and dz <= ROAD_END) or (dz <= 2 and dx <= ROAD_END)


for x in range(W):
    for z in range(L):
        if on_road(x, z) and dist(x, z) > 11:
            edge = (abs(x - CX) == 2 and abs(z - CZ) > 2) or (abs(z - CZ) == 2 and abs(x - CX) > 2)
            ground(x, z, "minecraft:polished_andesite" if edge else "minecraft:stone_bricks" if random.random() < 0.8 else "minecraft:cracked_stone_bricks")
for r in range(20, ROAD_END, 6):
    for ux, uz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for side in (-1, 1):
            # 길 가운데에서 r 만큼, 옆으로 4칸
            x = CX + ux * r + uz * side * 4
            z = CZ + uz * r + ux * side * 4
            if get(x, FLOOR, z) == "minecraft:grass_block":
                put(x, FLOOR + 1, z, "minecraft:polished_andesite_wall")
                put(x, FLOOR + 2, z, "minecraft:polished_andesite_wall")
                put(x, FLOOR + 3, z, "minecraft:lantern")

# ---------------------------------------------------------------- 가운데 광장 + 물길
for x in range(W):
    for z in range(L):
        d, a = dist(x, z), ang(x, z)
        if d <= 11.5:
            wave = math.sin(3 * a + d * 0.55) > 0.55
            put(x, FLOOR, z, "minecraft:polished_andesite" if d > 10.6 else "minecraft:prismarine_bricks" if wave else "minecraft:smooth_quartz")
        elif d <= CANAL_IN:
            ground(x, z, "minecraft:prismarine_bricks" if d > CANAL_IN - 1 else "minecraft:grass_block")
            if d > CANAL_IN - 1 and not on_road(x, z):
                put(x, FLOOR + 1, z, "minecraft:prismarine_wall")
        elif d <= CANAL_OUT:
            if on_road(x, z):
                put(x, FLOOR, z, "minecraft:smooth_quartz")       # 다리
                if abs(x - CX) == 2 or abs(z - CZ) == 2:
                    put(x, FLOOR + 1, z, "minecraft:quartz_slab[type=bottom]")
            else:
                put(x, FLOOR, z, "minecraft:water")
                put(x, FLOOR - 1, z, "minecraft:water")
                put(x, FLOOR - 2, z, "minecraft:sea_lantern" if (x * 7 + z * 3) % 11 == 0 else "minecraft:prismarine")
        elif d <= CANAL_OUT + 1:
            ground(x, z, "minecraft:prismarine_bricks")
            if not on_road(x, z):
                put(x, FLOOR + 1, z, "minecraft:prismarine_wall")
# 광장 안 잔디 화단 (네 귀퉁이)
for x in range(W):
    for z in range(L):
        d = dist(x, z)
        if 11.5 < d < CANAL_IN - 1 and not on_road(x, z) and random.random() < 0.35:
            put(x, FLOOR + 1, z, random.choice(["minecraft:azure_bluet", "minecraft:cornflower", "minecraft:short_grass", "minecraft:blue_orchid"]))

# ---------------------------------------------------------------- Wave 분수
for x in range(CX - 6, CX + 7):
    for z in range(CZ - 6, CZ + 7):
        d = dist(x, z)
        if d <= 4.2:
            put(x, FLOOR, z, "minecraft:water")
            put(x, FLOOR - 1, z, "minecraft:sea_lantern" if d < 1.5 else "minecraft:prismarine")
        elif d <= 5.3:
            put(x, FLOOR + 1, z, "minecraft:prismarine_bricks")
            put(x, FLOOR, z, "minecraft:prismarine_bricks")
for y in range(FLOOR, FLOOR + 7):
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        put(CX + dx, y, CZ + dz, "minecraft:sea_lantern" if (dx, dz) == (0, 0) and y % 3 == 0 else "minecraft:dark_prismarine" if (dx, dz) != (0, 0) else "minecraft:prismarine_bricks")
# 말려 올라가는 물결 4개 (유리)
for k in range(4):
    a0 = k * math.pi / 2 + 0.4
    for i in range(30):
        t = i / 29
        r = 1.3 + 4.2 * t
        a = a0 + t * 1.5
        y = FLOOR + 7 + round(3.2 * math.sin(math.pi * t ** 0.8))
        put(round(CX + math.cos(a) * r), y, round(CZ + math.sin(a) * r), "minecraft:blue_stained_glass" if t > 0.8 else "minecraft:light_blue_stained_glass")
put(CX, FLOOR + 7, CZ, "minecraft:sea_lantern")
put(CX, FLOOR + 8, CZ, "minecraft:conduit")
labels.append((CX, FLOOR + 11, CZ, "Wave 분수"))

# 스폰 지점
put(SPAWN[0], FLOOR, SPAWN[2], "minecraft:chiseled_quartz_block")
labels.append((SPAWN[0], FLOOR + 1, SPAWN[2], "스폰 지점"))

# ---------------------------------------------------------------- 북쪽: 포탈 광장
GZ0, GZ1 = CZ - 44, CZ - 27
for x in range(CX - 23, CX + 24):
    for z in range(GZ0, GZ1 + 1):
        if get(x, FLOOR, z):
            put(x, FLOOR, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:stone_bricks")
            for y in range(FLOOR + 1, FLOOR + 3):
                clear(x, y, z)
PORTALS = [
    (CX - 15, "야생", ["야생", "/랜덤스폰", "랜덤 위치로 이동", ""], "minecraft:mossy_stone_bricks", "minecraft:lime_stained_glass_pane", "minecraft:moss_block"),
    (CX, "천공의 제단", ["천공의 제단", "/천공의제단", "이동권 필요", ""], "minecraft:quartz_pillar[axis=y]", "minecraft:yellow_stained_glass_pane", "minecraft:gold_block"),
    (CX + 15, "보호구역", ["보호구역", "/보호구역", "안전 지대", ""], "minecraft:red_nether_bricks", "minecraft:red_stained_glass_pane", "minecraft:nether_wart_block"),
]
PZ = CZ - 39
for (px, name, lines, frame, pane, cap) in PORTALS:
    tall = 11 if name == "천공의 제단" else 9
    for y in range(FLOOR + 1, FLOOR + tall + 1):
        for x in range(px - 3, px + 4):
            if abs(x - px) == 3 or y == FLOOR + tall:
                put(x, y, PZ, frame)
                put(x, y, PZ - 1, frame)
            else:
                put(x, y, PZ, pane.replace("_pane", "_pane[east=true,west=true]"))
    for x in range(px - 4, px + 5):
        put(x, FLOOR + tall + 1, PZ, cap if abs(x - px) <= 2 else "minecraft:stone_brick_slab[type=bottom]")
    put(px, FLOOR + tall + 2, PZ, cap)
    if name == "천공의 제단":
        for dx in (-3, 3):
            put(px + dx, FLOOR + tall + 2, PZ, "minecraft:end_rod[facing=up]")
        put(px, FLOOR + tall + 3, PZ, "minecraft:end_rod[facing=up]")
    for x in range(px - 3, px + 4):
        put(x, FLOOR, PZ + 1, "minecraft:smooth_quartz" if name == "천공의 제단" else frame.split("[")[0])
    sign(px, FLOOR + 1, PZ + 3, lines, 0, "dark_aqua" if name == "천공의 제단" else "black")
    labels.append((px, FLOOR + tall + 2, PZ, name + " 포탈"))

# ---------------------------------------------------------------- 동쪽: 시장 (노점 6개)
STALLS = [
    (CX + 22, -1, "상점", ["상점", "/상점", "", ""], "minecraft:lime_wool", "minecraft:emerald_block"),
    (CX + 31, -1, "유료 캐시 상점", ["유료 캐시 상점", "/yurocash", "", ""], "minecraft:magenta_wool", "minecraft:amethyst_block"),
    (CX + 40, -1, "일일 상점", ["일일 상점", "/일일상점", "매일 바뀌는 특가", ""], "minecraft:yellow_wool", "minecraft:gold_block"),
    (CX + 22, 1, "유저 장터", ["유저 장터", "/장터", "", ""], "minecraft:orange_wool", "minecraft:barrel[facing=up]"),
    (CX + 31, 1, "아이템 뽑기", ["아이템 뽑기", "/상자", "", ""], "minecraft:purple_wool", "minecraft:ender_chest[facing=north]"),
    (CX + 40, 1, "프리미엄 패스", ["프리미엄 패스", "/pass", "", ""], "minecraft:light_blue_wool", "minecraft:honeycomb_block"),
]
for (sx, side, name, lines, wool, ware) in STALLS:
    z_front = CZ + side * 4          # 길 쪽
    z_back = CZ + side * 8
    zs = range(min(z_front, z_back), max(z_front, z_back) + 1)
    for x in range(sx - 3, sx + 4):
        for z in zs:
            put(x, FLOOR, z, "minecraft:spruce_planks")
            for y in range(FLOOR + 1, FLOOR + 6):
                clear(x, y, z)
    # 기둥
    for x in (sx - 3, sx + 3):
        for z in (z_front, z_back):
            for y in range(FLOOR + 1, FLOOR + 4):
                put(x, y, z, "minecraft:spruce_fence")
    # 줄무늬 차양
    for x in range(sx - 3, sx + 4):
        for z in zs:
            put(x, FLOOR + 4, z, wool if (x - sx) % 2 == 0 else "minecraft:white_wool")
        put(x, FLOOR + 4, z_front - side, "minecraft:spruce_slab[type=top]")
    # 판매대 (길 쪽) + 진열
    for x in range(sx - 2, sx + 3):
        put(x, FLOOR + 1, z_front, "minecraft:spruce_slab[type=top]" if x != sx else "minecraft:barrel[facing=up]")
        put(x, FLOOR + 1, z_back, "minecraft:barrel[facing=up]" if x % 2 else "minecraft:chest[facing=" + ("north" if side > 0 else "south") + "]")
    put(sx, FLOOR + 2, z_front, ware)
    put(sx - 2, FLOOR + 2, z_front, "minecraft:lantern")
    sign(sx + 2, FLOOR + 1, z_front - side, lines, 0 if side > 0 else 8)
    labels.append((sx, FLOOR + 5, (z_front + z_back) // 2, name))

# ---------------------------------------------------------------- 서쪽: 경험치 구역 (안쪽이 config xp-zone 상자)
XX0, XX1, XZ0, XZ1 = CX - 41, CX - 32, CZ - 5, CZ + 5
XY0, XY1 = FLOOR + 1, FLOOR + 7
for x in range(XX0 - 1, XX1 + 2):
    for z in range(XZ0 - 1, XZ1 + 2):
        edge = x in (XX0 - 1, XX1 + 1) or z in (XZ0 - 1, XZ1 + 1)
        put(x, FLOOR, z, "minecraft:deepslate_tiles" if edge else ("minecraft:lapis_block" if (x + z) % 4 == 0 else "minecraft:polished_deepslate"))
        for y in range(FLOOR + 1, XY1 + 3):
            clear(x, y, z)
        corner = x in (XX0 - 1, XX1 + 1) and z in (XZ0 - 1, XZ1 + 1)
        if corner:
            for y in range(FLOOR + 1, XY1 + 1):
                put(x, y, z, "minecraft:bookshelf" if y < XY1 else "minecraft:chiseled_bookshelf")
        elif edge and x == XX0 - 1:   # 서쪽 벽 (책장 반벽)
            for y in range(FLOOR + 1, FLOOR + 3):
                put(x, y, z, "minecraft:bookshelf")
        # 지붕: 자수정 유리 돔
        if not edge or corner:
            put(x, XY1 + 1, z, "minecraft:purple_stained_glass" if (x + z) % 3 else "minecraft:amethyst_block")
        else:
            put(x, XY1 + 1, z, "minecraft:deepslate_tile_slab[type=bottom]")
for x in range(XX0 + 2, XX1 - 1):
    for z in range(XZ0 + 2, XZ1 - 1):
        put(x, XY1 + 2, z, "minecraft:purple_stained_glass")
put((XX0 + XX1) // 2, FLOOR + 1, CZ, "minecraft:enchanting_table")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    put((XX0 + XX1) // 2 + dx, FLOOR + 1, CZ + dz, "minecraft:amethyst_cluster[facing=up]")
    put((XX0 + XX1) // 2 + dx, FLOOR, CZ + dz, "minecraft:sea_lantern")
sign(XX1 + 2, FLOOR + 1, CZ + 3, ["경험치 구역", "안에서 20초", "머물면", "경험치 획득"], 4, "dark_purple")
labels.append(((XX0 + XX1) // 2, XY1 + 2, CZ, "경험치 구역"))

# ---------------------------------------------------------------- 남쪽: 랭킹 단상 + 안내 게시판
RZ = CZ + 36
for x in range(CX - 12, CX + 13):
    for z in range(CZ + 27, CZ + 42):
        if get(x, FLOOR, z):
            put(x, FLOOR, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:smooth_stone")
            for y in range(FLOOR + 1, FLOOR + 4):
                clear(x, y, z)
for (px, h, mat, rank) in ((CX, 3, "minecraft:gold_block", "1위"), (CX - 4, 2, "minecraft:iron_block", "2위"), (CX + 4, 1, "minecraft:copper_block", "3위")):
    for x in range(px - 1, px + 2):
        for z in range(RZ - 1, RZ + 2):
            for y in range(FLOOR + 1, FLOOR + 1 + h):
                put(x, y, z, mat)
    sign(px, FLOOR + 1, RZ - 2, [rank, "", "", ""], 8, "black")
# 뒤쪽 순위 벽 + 깃발 기둥
for x in range(CX - 7, CX + 8):
    for y in range(FLOOR + 1, FLOOR + 7):
        put(x, y, RZ + 3, "minecraft:dark_oak_planks" if abs(x - CX) < 7 and y < FLOOR + 6 else "minecraft:dark_oak_log[axis=y]")
for x in range(CX - 7, CX + 8):
    put(x, FLOOR + 7, RZ + 3, "minecraft:gold_block" if x % 2 == 0 else "minecraft:dark_oak_slab[type=bottom]")
sign(CX, FLOOR + 4, RZ + 2, ["랭킹", "/순위", "시즌 TOP 10 보상", ""], color="gold", wall="north")
labels.append((CX, FLOOR + 7, RZ + 3, "랭킹 단상"))

# 안내 게시판 (스폰 남쪽, 분수를 바라보는 사람 등 뒤: 북쪽을 바라봄)
BOARDS = [
    (CX - 7, ["서버 메뉴", "Shift + F", "/메뉴", ""], ["튜토리얼", "/튜토리얼", "", ""]),
    (CX + 7, ["핫타임", "매일 20:00", "주말 14:00", "/핫타임"], ["업적 · 출석", "/업적", "/출석", ""]),
]
BZ = CZ + 22
for (bx, a, b) in BOARDS:
    for x in range(bx - 2, bx + 3):
        for y in range(FLOOR + 1, FLOOR + 4):
            put(x, y, BZ, "minecraft:dark_oak_log[axis=y]" if abs(x - bx) == 2 else "minecraft:dark_oak_planks")
        put(x, FLOOR + 4, BZ, "minecraft:dark_oak_slab[type=bottom]")
    sign(bx - 1, FLOOR + 2, BZ - 1, a, wall="north")
    sign(bx + 1, FLOOR + 2, BZ - 1, b, wall="north")
    labels.append((bx, FLOOR + 4, BZ, "안내 게시판"))

# ---------------------------------------------------------------- 나무 · 꽃
busy = set()
for (x, y, z), b in list(blocks.items()):
    if y >= FLOOR and b not in ("minecraft:grass_block",):
        if y == FLOOR and b in ("minecraft:dirt",):
            continue
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                busy.add((x + dx, z + dz))
for (x, y, z, *_r) in signs:
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            busy.add((x + dx, z + dz))


def tree(x, z, kind):
    h = random.randint(5, 7)
    log = {"cherry": "minecraft:cherry_log[axis=y]", "oak": "minecraft:oak_log[axis=y]", "birch": "minecraft:birch_log[axis=y]"}[kind]
    leaf = {"cherry": "minecraft:cherry_leaves[persistent=true]", "oak": "minecraft:oak_leaves[persistent=true]", "birch": "minecraft:birch_leaves[persistent=true]"}[kind]
    for y in range(FLOOR + 1, FLOOR + 1 + h):
        put(x, y, z, log)
    top = FLOOR + h
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                if math.hypot(dx, dz, dy * 1.3) <= 3.0 + random.uniform(-0.4, 0.3) and not get(x + dx, top + dy, z + dz):
                    put(x + dx, top + dy, z + dz, leaf)


n = 0
for _ in range(3000):
    a = random.uniform(0, math.pi * 2)
    r = random.uniform(20, 47)
    x, z = round(CX + math.cos(a) * r), round(CZ + math.sin(a) * r)
    if (x, z) in busy or get(x, FLOOR, z) != "minecraft:grass_block" or dist(x, z) > island_r(ang(x, z)) - 2.5:
        continue
    tree(x, z, random.choice(["cherry", "oak", "oak", "birch"]))
    n += 1
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            busy.add((x + dx, z + dz))
    if n >= 26:
        break
FLOWERS = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:allium", "minecraft:blue_orchid"]
for x in range(W):
    for z in range(L):
        if get(x, FLOOR, z) == "minecraft:grass_block" and not get(x, FLOOR + 1, z):
            k = random.random()
            if k < 0.15:
                put(x, FLOOR + 1, z, "minecraft:short_grass")
            elif k < 0.2:
                put(x, FLOOR + 1, z, random.choice(FLOWERS))

# 표지판 블록
for (x, y, z, lines, rot, color, wall) in signs:
    if wall:
        put(x, y, z, f"minecraft:spruce_wall_sign[facing={wall}]")
    else:
        put(x, y, z, f"minecraft:spruce_sign[rotation={rot}]")
        if not get(x, y - 1, z):
            put(x, y - 1, z, "minecraft:stone_bricks")

# 스폰 지점 위는 비움
for y in range(FLOOR + 1, FLOOR + 3):
    clear(SPAWN[0], y, SPAWN[2])

out = sys.argv[1] if len(sys.argv) > 1 else "wave_spawn.schem"
schem_util.write(out, blocks, W, H, L, SPAWN, [s_[:6] for s_ in signs])
sx, sy, sz = SPAWN
print(f"경험치 구역 (스폰 지점 기준 상대 좌표): min [{XX0 - sx}, {XY0 - sy}, {XZ0 - sz}]  max [{XX1 - sx}, {XY1 - sy}, {XZ1 - sz}]")

if len(sys.argv) > 2:
    import iso_render
    iso_render.render(blocks, W, H, L, labels, sys.argv[2], title="Wave 스폰 섬 (남동쪽에서)")
    out2 = sys.argv[2].replace(".png", "_back.png")
    iso_render.render(blocks, W, H, L, labels, out2, title="Wave 스폰 섬 (북서쪽에서)", rot180=True)
    print("preview:", sys.argv[2], out2)
