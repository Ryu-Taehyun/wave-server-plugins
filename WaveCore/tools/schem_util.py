"""Sponge Schematic v2 쓰기 (블록 + 표지판)"""
import gzip
import json
import struct


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


def _sign_text(lines, color):
    first = next((i for i, l in enumerate(lines) if l), -1)   # 처음 나오는 글자 줄을 굵게
    msgs = [json.dumps({"text": l, "color": color, "bold": i == first}, ensure_ascii=False) for i, l in enumerate(lines)]
    lst = bytes([8]) + struct.pack(">i", len(msgs)) + b"".join(s_str(m) for m in msgs)
    return (tag(9, "messages", lst) + tag(8, "color", s_str("black"))
            + tag(1, "has_glowing_text", bytes([1 if color != "black" else 0])) + b"\x00")


def write(out, blocks, W, H, L, offset, signs=(), data_version=3955):
    """blocks: {(x,y,z): state}, offset: 붙여넣기 기준점 (x,y,z), signs: [(x,y,z,[4줄],rotation,색)]"""
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
        lines = (list(lines) + ["", "", "", ""])[:4]
        entities += (tag(8, "Id", s_str("minecraft:sign"))
                     + tag(11, "Pos", struct.pack(">i", 3) + struct.pack(">iii", x, y, z))
                     + tag(10, "front_text", _sign_text(lines, color))
                     + tag(10, "back_text", _sign_text(["", "", "", ""], "black"))
                     + tag(1, "is_waxed", b"\x01") + b"\x00")
    pal_payload = b"".join(tag(3, k, struct.pack(">i", v)) for k, v in palette.items()) + b"\x00"
    ox, oy, oz = offset
    body = b"".join([
        tag(3, "Version", struct.pack(">i", 2)),
        tag(3, "DataVersion", struct.pack(">i", data_version)),
        tag(2, "Width", struct.pack(">h", W)),
        tag(2, "Height", struct.pack(">h", H)),
        tag(2, "Length", struct.pack(">h", L)),
        tag(11, "Offset", struct.pack(">i", 3) + struct.pack(">iii", -ox, -oy, -oz)),
        tag(3, "PaletteMax", struct.pack(">i", len(palette))),
        tag(10, "Palette", pal_payload),
        tag(7, "BlockData", struct.pack(">i", len(data)) + bytes(data)),
        tag(9, "BlockEntities", bytes([10]) + struct.pack(">i", len(signs)) + entities),
    ])
    with gzip.open(out, "wb") as f:
        f.write(tag(10, "Schematic", body + b"\x00"))
    n = sum(v for k, v in counts.items() if k != "minecraft:air")
    print(f"{out}: {W}x{H}x{L}, 블록 {n}개, 팔레트 {len(palette)}종, 표지판 {len(signs)}개")


def rotation_toward(dx, dz):
    """표지판 앞면이 (dx, dz) 쪽을 보게 하는 rotation (0 = 남쪽)"""
    import math
    return round(math.degrees(math.atan2(-dx, dz)) / 22.5) % 16
