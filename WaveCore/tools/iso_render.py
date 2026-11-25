"""
스케매틱 미리보기용 아이소메트릭 렌더러 (블록 색 · 간단한 음영, 텍스쳐 파일 없이)
render(blocks, W, H, L, labels, out_png)
  blocks: {(x,y,z): "minecraft:이름[상태]"}, labels: [(x,y,z,"글자"), ...]
"""
import math
import random

from PIL import Image, ImageDraw, ImageFont

C = {
    "grass_block": (98, 160, 62), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59), "dirt_path": (165, 137, 82),
    "stone": (125, 125, 125), "andesite": (136, 136, 137), "tuff": (108, 109, 102), "moss_block": (89, 110, 45),
    "stone_bricks": (122, 121, 122), "chiseled_stone_bricks": (118, 118, 118), "polished_andesite": (132, 135, 134),
    "polished_andesite_wall": (132, 135, 134), "prismarine": (99, 156, 151), "prismarine_bricks": (99, 171, 158),
    "dark_prismarine": (51, 91, 75), "prismarine_slab": (99, 156, 151), "sea_lantern": (190, 220, 212), "water": (52, 104, 214),
    "light_blue_stained_glass": (120, 190, 235), "beacon": (120, 220, 215), "end_rod": (250, 245, 230), "smooth_quartz": (236, 230, 223),
    "spruce_planks": (115, 85, 49), "stripped_spruce_log": (116, 90, 52), "spruce_slab": (115, 85, 49), "spruce_fence": (102, 76, 44),
    "spruce_sign": (115, 85, 49), "lantern": (255, 196, 92), "oak_log": (109, 85, 50), "oak_leaves": (72, 140, 44),
    "cherry_log": (54, 33, 44), "cherry_leaves": (240, 170, 205), "short_grass": (110, 170, 70), "poppy": (220, 40, 40),
    "dandelion": (250, 220, 40), "cornflower": (70, 110, 230), "azure_bluet": (230, 235, 240), "oxeye_daisy": (240, 240, 230),
    "allium": (190, 110, 230), "hanging_roots": (160, 110, 90), "cave_vines_plant": (95, 125, 40),
    "light_blue_wool": (58, 175, 217), "purple_wool": (122, 42, 173), "lime_wool": (112, 185, 26), "brown_wool": (114, 72, 41),
    "magenta_wool": (190, 69, 180), "yellow_wool": (249, 198, 40), "pink_wool": (238, 141, 172), "orange_wool": (241, 118, 20),
    "lodestone": (147, 149, 152), "lectern": (173, 137, 83), "cartography_table": (104, 79, 52), "respawn_anchor": (60, 40, 90),
    "crying_obsidian": (40, 12, 70), "amethyst_cluster": (200, 150, 240), "amethyst_block": (134, 98, 191), "emerald_block": (42, 203, 88),
    "barrel": (134, 100, 58), "chest": (162, 120, 50), "ender_chest": (35, 55, 55), "gold_block": (246, 208, 61), "bell": (250, 210, 80),
    "honeycomb_block": (229, 148, 29), "polished_deepslate": (72, 72, 73), "diamond_block": (98, 237, 228), "enchanting_table": (160, 40, 50),
    "bookshelf": (117, 94, 60), "chiseled_bookshelf": (120, 92, 58), "cracked_stone_bricks": (118, 117, 118), "prismarine_wall": (99, 156, 151),
    "quartz_slab": (236, 230, 223), "blue_orchid": (40, 170, 240), "blue_stained_glass": (60, 80, 200), "conduit": (160, 140, 110),
    "chiseled_quartz_block": (231, 226, 218), "mossy_stone_bricks": (115, 121, 105), "quartz_pillar": (236, 230, 220),
    "red_nether_bricks": (69, 7, 9), "nether_wart_block": (115, 3, 2), "lime_stained_glass_pane": (128, 204, 31),
    "yellow_stained_glass_pane": (229, 229, 51), "red_stained_glass_pane": (170, 40, 40), "stone_brick_slab": (122, 121, 122),
    "white_wool": (234, 236, 237), "spruce_wall_sign": (115, 85, 49), "deepslate_tiles": (54, 54, 55), "lapis_block": (31, 67, 140),
    "purple_stained_glass": (127, 63, 178), "deepslate_tile_slab": (54, 54, 55), "smooth_stone": (159, 159, 159),
    "iron_block": (220, 220, 220), "copper_block": (192, 107, 79), "dark_oak_planks": (66, 43, 20), "dark_oak_log": (60, 46, 26),
    "dark_oak_slab": (66, 43, 20), "birch_log": (216, 215, 210), "birch_leaves": (128, 167, 85),
}
GLOW = {"sea_lantern", "lantern", "end_rod", "beacon", "light_blue_stained_glass"}
# 작은 모양: (가로 반폭, 높이, 아래에서 시작, 매달림)
SMALL = {
    "short_grass": (0.18, 0.45, 0), "poppy": (0.12, 0.55, 0), "dandelion": (0.12, 0.5, 0), "cornflower": (0.12, 0.55, 0),
    "azure_bluet": (0.12, 0.45, 0), "oxeye_daisy": (0.12, 0.55, 0), "allium": (0.12, 0.6, 0), "lantern": (0.18, 0.45, 0),
    "spruce_fence": (0.12, 1.0, 0), "polished_andesite_wall": (0.25, 1.0, 0), "end_rod": (0.06, 1.0, 0),
    "amethyst_cluster": (0.25, 0.5, 0), "bell": (0.25, 0.6, 0), "hanging_roots": (0.15, 1.0, 0), "cave_vines_plant": (0.15, 1.0, 0),
    "spruce_sign": (0.4, 0.75, 0.25), "spruce_wall_sign": (0.45, 0.5, 0.25), "prismarine_wall": (0.25, 1.0, 0), "conduit": (0.2, 0.4, 0), "blue_orchid": (0.12, 0.55, 0),
}
HALF = {"quartz_slab": 0.5, "stone_brick_slab": 0.5, "deepslate_tile_slab": 0.5, "dark_oak_slab": 0.5, "prismarine_slab": 0.5, "spruce_slab": 0.5, "water": 0.85, "lectern": 0.9, "enchanting_table": 0.75, "chest": 0.88, "ender_chest": 0.88}


def name(b):
    return b.split(":")[1].split("[")[0]


def render(blocks, W, H, L, labels, out, title="", s=24, scale=0.5, rot180=False):
    """rot180: 반대쪽(북서쪽 위)에서 본 모습"""
    rnd = random.Random(7)
    if rot180:
        flip = {"north": "south", "south": "north", "east": "west", "west": "east"}
        def fb(b):
            for k, v in flip.items():
                if "facing=" + k in b:
                    return b.replace("facing=" + k, "facing=" + v)
            return b
        blocks = {(W - 1 - x, y, L - 1 - z): fb(b) for (x, y, z), b in blocks.items()}
        labels = [(W - 1 - x, y, L - 1 - z, t) for (x, y, z, t) in labels]
    full = {p for p, b in blocks.items() if name(b) not in SMALL and name(b) not in HALF and "glass" not in b}
    cx, cy = s * math.cos(math.radians(30)), s * 0.5

    def proj(x, y, z):
        return ((x - z) * cx, (x + z) * cy - y * s)

    xs = [proj(x, y, z)[0] for x in (0, W) for z in (0, L) for y in (0, H)]
    ys = [proj(x, y, z)[1] for x in (0, W) for z in (0, L) for y in (0, H)]
    pad = 40
    ox, oy = -min(xs) + pad, -min(ys) + pad + 60
    img = Image.new("RGB", (int(max(xs) - min(xs) + pad * 2), int(max(ys) - min(ys) + pad * 2 + 60)))
    # 하늘 그라데이션
    dr = ImageDraw.Draw(img)
    for yy in range(img.height):
        t = yy / img.height
        dr.line([(0, yy), (img.width, yy)], fill=(int(120 + 60 * t), int(170 + 50 * t), int(235 + 15 * t)))

    def P(x, y, z):
        a, b = proj(x, y, z)
        return (a + ox, b + oy)

    def shade(c, f, tint):
        return tuple(max(0, min(255, int(v * f * tint))) for v in c)

    order = sorted(blocks.items(), key=lambda kv: (kv[0][0] + kv[0][1] + kv[0][2], kv[0][1]))
    bx0 = by0 = 1e9
    bx1 = by1 = -1e9
    for (x, y, z), b in order:
        n = name(b)
        col = C.get(n, (200, 0, 200))
        tint = 1 + rnd.uniform(-0.05, 0.05)
        glow = n in GLOW
        if n.endswith("_pane"):
            x0, x1, z0, z1, y0, y1 = x, x + 1, z + 0.44, z + 0.56, y, y + 1
        elif n in SMALL:
            hw, h, back = SMALL[n]
            x0, x1, z0, z1 = x + 0.5 - hw, x + 0.5 + hw, z + 0.5 - hw, z + 0.5 + hw
            if n == "spruce_sign":
                z0, z1 = z + 0.45, z + 0.55
            if n == "spruce_wall_sign":
                z0, z1 = (z + 0.88, z + 1.0) if "facing=north" in b else (z, z + 0.12)
                y0, y1 = y + 0.25, y + 0.75
            y0, y1 = y, y + h
            if n in ("hanging_roots", "cave_vines_plant"):
                y0, y1 = y + 1 - h, y + 1
        else:
            x0, x1, z0, z1 = x, x + 1, z, z + 1
            y0, y1 = y, y + HALF.get(n, 1.0)
            top_hidden = (x, y + 1, z) in full
            east_hidden = (x + 1, y, z) in full
            south_hidden = (x, y, z + 1) in full
            if top_hidden and east_hidden and south_hidden:
                continue
        top = [P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)]
        east = [P(x1, y0, z0), P(x1, y1, z0), P(x1, y1, z1), P(x1, y0, z1)]
        south = [P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)]
        fs = (1.0, 0.62, 0.8) if not glow else (1.1, 0.95, 1.0)
        for q in top + east + south:
            bx0, by0, bx1, by1 = min(bx0, q[0]), min(by0, q[1]), max(bx1, q[0]), max(by1, q[1])
        for poly, f in ((south, fs[2]), (east, fs[1]), (top, fs[0])):
            c = shade(col, f, tint)
            dr.polygon(poly, fill=c, outline=shade(c, 0.9, 1) if n not in SMALL else None)
        if n == "grass_block" and not (x, y + 1, z) in full:
            # 잔디 옆면 위쪽 띠
            for poly_pts in ([P(x0, y1 - 0.2, z1), P(x1, y1 - 0.2, z1), P(x1, y1, z1), P(x0, y1, z1)],
                             [P(x1, y1 - 0.2, z0), P(x1, y1, z0), P(x1, y1, z1), P(x1, y1 - 0.2, z1)]):
                dr.polygon(poly_pts, fill=shade(C["grass_block"], 0.72, tint))

    # 내용만 남기고 자르기 (제목 · 이름표 자리 위쪽 여유)
    cut = (max(0, int(bx0) - 30), max(0, int(by0) - 120), min(img.width, int(bx1) + 30), min(img.height, int(by1) + 30))
    img = img.crop(cut)
    ox -= cut[0]
    oy -= cut[1]
    img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", 15)
    big = ImageFont.truetype("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", 26)
    if title:
        dr.text((18, 14), title, font=big, fill=(20, 30, 50))
    for (x, y, z, text) in labels:
        px, py = P(x + 0.5, y + 1, z + 0.5)
        px, py = px * scale, py * scale
        tw = dr.textlength(text, font=font)
        bx, by = px - tw / 2 - 8, py - 44
        dr.line([(px, py), (px, by + 24)], fill=(255, 255, 255), width=2)
        dr.rounded_rectangle([bx, by, bx + tw + 16, by + 24], radius=7, fill=(20, 24, 34), outline=(255, 214, 74), width=2)
        dr.text((bx + 8, by + 3), text, font=font, fill=(255, 236, 170))
    img.save(out)
