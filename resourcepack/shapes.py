"""
Wave 특별 아이템 텍스쳐 (형태를 새로 그림, 32 칸 좌표로 설계해 128x128 고해상도로 그림)
python3 shapes.py <출력 폴더>   -> 리소스팩 폴더 (pack.mcmeta + assets/wave/...)
바닐라 텍스쳐를 쓰지 않고 도형으로 직접 그립니다.
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

N = 32          # 설계 좌표 (32 칸)
OUT = 128       # 실제 텍스쳐 크기 (픽셀이 보이지 않게 고해상도)
SS = 4          # 4 배로 크게 그린 뒤 줄여서 가장자리를 부드럽게 (안티에일리어싱)
RES = OUT * SS
K = RES / N


class SD:
    """32 칸 좌표로 그리면 128 픽셀로 늘려 그림"""
    def __init__(self, d): self.d = d
    @staticmethod
    def p(pt): return (pt[0] * K + K / 2, pt[1] * K + K / 2)
    def _pts(self, pts): return [self.p(q) for q in pts]
    def _box(self, b): return [b[0] * K + K / 2, b[1] * K + K / 2, b[2] * K + K / 2, b[3] * K + K / 2]
    def line(self, pts, fill=255, width=1, joint=None):
        self.d.line(self._pts(pts), fill=fill, width=max(1, round(width * K)), joint='curve')
        for q in (pts[0], pts[-1]):   # 끝을 둥글게
            c = self.p(q); r = width * K / 2; self.d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=fill)
    def polygon(self, pts, fill=255): self.d.polygon(self._pts(pts), fill=fill)
    def ellipse(self, b, fill=None, outline=None, width=1): self.d.ellipse(self._box(b), fill=fill, outline=outline, width=max(1, round(width * K)))
    def rectangle(self, b, fill=255): self.d.rectangle(self._box(b), fill=fill)
    def rounded_rectangle(self, b, radius=1, fill=255): self.d.rounded_rectangle(self._box(b), radius=radius * K, fill=fill)
    def arc(self, b, start, end, fill=255, width=1): self.d.arc(self._box(b), start, end, fill=fill, width=max(1, round(width * K)))
    def chord(self, b, start, end, fill=255): self.d.chord(self._box(b), start, end, fill=fill)

def hx(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def mix(a, b, f): return tuple(round(a[i] + (b[i] - a[i]) * f) for i in range(3))

def shade(c, f): return tuple(max(0, min(255, round(v * f))) for v in c)

def ramp(stops, t):
    t = max(0.0, min(1.0, t)); n = len(stops) - 1; i = min(int(t * n), n - 1)
    return mix(hx(stops[i]), hx(stops[i + 1]), t * n - i)

def ramp_np(stops, t):
    """t 배열(0~1)을 색 띠로 바꿈"""
    cs = np.array([hx(c) for c in stops], dtype=np.float32); n = len(cs) - 1
    t = np.clip(t, 0, 1) * n; i = np.minimum(t.astype(int), n - 1); f = (t - i)[..., None]
    return cs[i] * (1 - f) + cs[i + 1] * f


def shift(m, dx, dy):
    """m 을 (dx, dy) 만큼 밀었을 때 그 자리가 m 안인지 (밖은 False)"""
    out = np.zeros_like(m); h, w = m.shape
    ys = slice(max(0, -dy), min(h, h - dy)); xs = slice(max(0, -dx), min(w, w - dx))
    yd = slice(max(0, dy), min(h, h + dy)); xd = slice(max(0, dx), min(w, w + dx))
    out[ys, xs] = m[yd, xd]; return out


class Sprite:
    def __init__(self, outline):
        self.rgb = np.zeros((RES, RES, 3), np.float32); self.a = np.zeros((RES, RES), bool)
        self.outline = hx(outline)

    def layer(self, draw, stops, g=(1, -1), bevel=True, flat=None):
        im = Image.new('L', (RES, RES), 0); draw(SD(ImageDraw.Draw(im)))
        m = np.asarray(im) > 127
        if not m.any(): return
        if flat:
            c = np.broadcast_to(np.array(hx(flat), np.float32), (RES, RES, 3)).copy()
        else:
            ys, xs = np.nonzero(m)
            gl = math.hypot(*g) or 1; gx, gy = g[0] / gl, g[1] / gl
            yy, xx = np.mgrid[0:RES, 0:RES].astype(np.float32)
            pr = xx * gx + yy * gy; sel = pr[m]; lo, hi = sel.min(), sel.max()
            t = (pr - lo) / (hi - lo) if hi > lo else np.full_like(pr, 0.5)
            c = ramp_np(stops[1:-1] if len(stops) > 3 else stops, t)
            if bevel:
                # 가장자리로 갈수록 부드럽게 밝아지거나(왼쪽 위) 어두워짐(오른쪽 아래)
                B = max(2, round(K * 0.7)); lit = np.zeros((RES, RES), np.float32); dark = np.zeros_like(lit)
                for k in range(1, B + 1):
                    w = 1 - (k - 1) / B
                    lit = np.maximum(lit, w * (~(shift(m, -k, 0) & shift(m, 0, -k))))
                    dark = np.maximum(dark, w * (~(shift(m, k, 0) & shift(m, 0, k))))
                dark = np.where(lit > 0, 0, dark)
                c = c + (np.array(hx(stops[-1]), np.float32) - c) * (lit * 0.75)[..., None]
                c = c + (np.array(hx(stops[0]), np.float32) - c) * (dark * 0.65)[..., None]
        self.rgb[m] = c[m]; self.a |= m

    def dots(self, pts, color, r=0.6):
        c = np.array(hx(color), np.float32); hi = np.array(mix(hx(color), (255, 255, 255), 0.7), np.float32)
        lo = np.array(shade(hx(color), 0.6), np.float32)
        for x, y in pts:
            cx, cy = SD.p((x, y)); rr = r * K
            yy, xx = np.ogrid[0:RES, 0:RES]
            d = np.hypot(xx - cx, yy - cy); m = d <= rr
            # 보석: 아래는 어둡게, 가운데는 원래 색, 왼쪽 위에 반짝임
            f = np.clip((yy - cy) / rr, 0, 1)[..., None] * 0.6
            col = c * (1 - f) + lo * f
            sh = np.hypot(xx - (cx - rr * 0.35), yy - (cy - rr * 0.35)) <= rr * 0.38
            col = np.where(sh[..., None], hi, col)
            self.rgb[m] = np.broadcast_to(col, (RES, RES, 3))[m]; self.a |= m

    def done(self):
        u = Image.fromarray((self.a * 255).astype(np.uint8))
        grown = np.asarray(u.filter(ImageFilter.GaussianBlur(K * 0.45))) > 24   # 둥근 외곽선
        rgb = np.where(self.a[..., None], self.rgb, np.array(self.outline, np.float32))
        alpha = (grown | self.a).astype(np.float32)
        big = np.dstack([rgb, alpha * 255])
        # Pillow 는 RGBA 를 줄일 때 알파를 곱해서 계산하므로 테두리 색이 번지지 않음
        return Image.fromarray(np.clip(big, 0, 255).astype(np.uint8), 'RGBA').resize((OUT, OUT), Image.BOX)

# ---------------- 도형 도우미 ----------------
def bez(p0, p1, p2, n=24):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in [i / n for i in range(n + 1)]]

def ribbon(axis, widths):
    """축(점 목록)을 따라 폭(반지름 목록)만큼의 띠 다각형"""
    L, R = [], []
    for i, (x, y) in enumerate(axis):
        a = axis[max(0, i - 1)]; b = axis[min(len(axis) - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]; l = math.hypot(dx, dy) or 1; nx, ny = -dy / l, dx / l
        w = widths[i]; L.append((x + nx * w, y + ny * w)); R.append((x - nx * w, y - ny * w))
    return L + R[::-1]

def line_pts(a, b, n): return [(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n) for i in range(n + 1)]

LEATHER = ["#20130B", "#4A2E1A", "#7A5130", "#A87A4E"]
GOLDTRIM = ["#3D2A05", "#9C7417", "#E2BD4C", "#FFF0B0"]
SILVERTRIM = ["#262A33", "#7C8696", "#C9D1DC", "#FFFFFF"]
CRIMSONTRIM = ["#2A0606", "#6E1414", "#B8322E", "#F08A80"]

def hilt(sp, guard_c, tipdir, gem, trim=GOLDTRIM, guard_len=7, wing=0):
    """가드 · 손잡이 · 손잡이 끝 보석 (검 공통). guard_c = 가드 중심, tipdir = 칼끝 방향 단위벡터"""
    gx, gy = guard_c; tx, ty = tipdir; px_, py_ = -ty, tx
    a = (gx + px_ * guard_len, gy + py_ * guard_len); b = (gx - px_ * guard_len, gy - py_ * guard_len)
    if wing:
        wa = (a[0] + tx * wing, a[1] + ty * wing); wb = (b[0] + tx * wing, b[1] + ty * wing)
        sp.layer(lambda d: d.line([wa, a, (gx, gy), b, wb], fill=255, width=3, joint='curve'), trim, g=(1, -1))
    else:
        sp.layer(lambda d: d.line([a, b], fill=255, width=3), trim, g=(1, -1))
    end = (gx - tx * 7, gy - ty * 7)
    sp.layer(lambda d: d.line([(gx, gy), end], fill=255, width=3), LEATHER, g=(1, -1))
    sp.layer(lambda d: d.ellipse([end[0] - 2.2, end[1] - 2.2, end[0] + 2.2, end[1] + 2.2], fill=255), trim)
    sp.dots([(round(end[0]), round(end[1]))], gem)
    sp.dots([(round(gx), round(gy)), (round(gx) + 1, round(gy))], gem)

DIAG = (1 / math.sqrt(2), -1 / math.sqrt(2))

def sword(pal, gem, rune, kind="long", trim=GOLDTRIM):
    sp = Sprite(shade(hx(pal[0]), 0.5) and '#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    G = (10.5, 21.5); T = (29, 3)
    axis = line_pts(G, T, 22)
    if kind == "great": w = [3.4 - 0.6 * i / 22 if i < 18 else 3.0 * (22 - i) / 4 for i in range(23)]
    elif kind == "flame": w = [2.4 + 0.9 * math.sin(i * 0.9) if i < 19 else 2.4 * (22 - i) / 3 for i in range(23)]
    elif kind == "katana":
        axis = bez(G, (17, 8), T, 22); w = [2.0 if i < 20 else 2.0 * (22 - i) / 2 for i in range(23)]
    else: w = [2.3 if i < 18 else 2.3 * (22 - i) / 4 for i in range(23)]
    sp.layer(lambda d: d.polygon(ribbon(axis, w), fill=255), pal, g=(1, 1))
    if kind == "ice":   # 얼음 가시
        for i in (6, 11, 16):
            x, y = axis[i]; sp.layer(lambda d, x=x, y=y: d.polygon([(x - 1, y - 2), (x - 5, y - 6), (x + 1, y - 1)], fill=255), pal, g=(1, 1))
    if kind == "quantum":   # 마디마다 빛나는 핵
        for i in (4, 9, 14):
            x, y = axis[i]; sp.dots([(round(x), round(y)), (round(x) + 1, round(y))], rune)
    elif kind != "ice":
        for i in range(3, 17, 3):
            x, y = axis[i]; sp.dots([(round(x), round(y))], rune)
    if kind == "clock":   # 시계 톱니 가드
        sp.layer(lambda d: d.ellipse([G[0] - 4.5, G[1] - 4.5, G[0] + 4.5, G[1] + 4.5], fill=255), GOLDTRIM)
        sp.layer(lambda d: d.ellipse([G[0] - 2.5, G[1] - 2.5, G[0] + 2.5, G[1] + 2.5], fill=255), pal, bevel=False)
        sp.dots([(round(G[0]), round(G[1]) - 2), (round(G[0]), round(G[1]) - 1), (round(G[0]) + 1, round(G[1]))], rune)
        end = (G[0] - 5, G[1] + 5)
        sp.layer(lambda d: d.line([(G[0] - 3, G[1] + 3), end], fill=255, width=3), LEATHER)
        sp.layer(lambda d: d.ellipse([end[0] - 2.2, end[1] - 2.2, end[0] + 2.2, end[1] + 2.2], fill=255), GOLDTRIM)
        sp.dots([(round(end[0]), round(end[1]))], gem)
    else:
        hilt(sp, G, DIAG, gem, trim=trim, guard_len=6 if kind == "great" else 5, wing=2 if kind in ("great", "ice") else 0)
    return sp.done()

def scythe(pal, gem, rune, double=False):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.line([(4, 29), (20, 5)], fill=255, width=3), LEATHER)
    blade = bez((20, 5), (4, 0), (2, 14), 20)
    w = [2.8 * (1 - i / 20) + 0.3 for i in range(21)]
    sp.layer(lambda d: d.polygon(ribbon(blade, w), fill=255), pal, g=(-1, 1))
    if double:
        b2 = bez((19, 7), (30, 6), (29, 18), 16); w2 = [2.2 * (1 - i / 16) + 0.3 for i in range(17)]
        sp.layer(lambda d: d.polygon(ribbon(b2, w2), fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.ellipse([17.5, 3, 22.5, 8], fill=255), GOLDTRIM)
    sp.dots([(20, 5), (20, 6)], gem); sp.dots([(round(x), round(y)) for x, y in blade[4:16:4]], rune)
    sp.layer(lambda d: d.ellipse([2, 27, 6, 31], fill=255), GOLDTRIM); sp.dots([(4, 29)], gem)
    return sp.done()

def axe(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.line([(5, 29), (21, 7)], fill=255, width=3), LEATHER)
    sp.layer(lambda d: d.polygon([(17, 6), (13, 1), (11, 6), (12, 12), (18, 11)], fill=255), pal, g=(-1, -1))
    sp.layer(lambda d: d.polygon([(20, 10), (29, 7), (30, 15), (27, 20), (21, 14)], fill=255), pal, g=(1, -1))
    sp.layer(lambda d: d.polygon([(25, 10), (23, 14), (26, 13), (24, 17)], fill=255), ["#FFFFFF", "#FFFFFF", "#FFFFFF"], flat=rune)
    sp.layer(lambda d: d.ellipse([16.5, 6.5, 21.5, 11.5], fill=255), GOLDTRIM); sp.dots([(19, 9)], gem)
    sp.layer(lambda d: d.ellipse([3, 27, 7, 31], fill=255), GOLDTRIM); sp.dots([(5, 29)], gem)
    return sp.done()

def pickaxe(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.line([(4, 29), (20, 8)], fill=255, width=3), LEATHER)
    head = bez((4, 7), (18, -2), (29, 21), 24); w = [1.0 + 1.6 * math.sin(math.pi * i / 24) for i in range(25)]
    sp.layer(lambda d: d.polygon(ribbon(head, w), fill=255), pal, g=(1, 1))
    sp.dots([(round(x), round(y)) for x, y in head[5:20:5]], rune)
    sp.layer(lambda d: d.ellipse([16.5, 3.5, 22.5, 9.5], fill=255), GOLDTRIM); sp.dots([(19, 6), (20, 6)], gem)
    sp.layer(lambda d: d.ellipse([2, 27, 6, 31], fill=255), GOLDTRIM); sp.dots([(4, 29)], gem)
    return sp.done()

def bow(pal, gem, rune, pull=0):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    top, bot = (27, 3), (4, 26)
    mid = (8 + pull * 1.5, 8 + pull * 1.5)
    limb = bez(top, mid, bot, 28); w = [1.0 + 1.4 * math.sin(math.pi * i / 28) for i in range(29)]
    sp.layer(lambda d: d.polygon(ribbon(limb, w), fill=255), pal, g=(-1, -1))
    for e in (top, bot): sp.layer(lambda d, e=e: d.ellipse([e[0] - 1.8, e[1] - 1.8, e[0] + 1.8, e[1] + 1.8], fill=255), GOLDTRIM)
    s = (18 + pull * 3, 18 + pull * 3)
    sp.layer(lambda d: d.line([top, s, bot], fill=255, width=1), ["#DDDDEE", "#DDDDEE", "#FFFFFF"], bevel=False, flat="#E6E6F0")
    if pull:
        sp.layer(lambda d: d.line([s, (s[0] - 15, s[1] - 15)], fill=255, width=1), LEATHER, bevel=False, flat="#8A6A44")
        sp.dots([(round(s[0] - 15), round(s[1] - 15)), (round(s[0] - 14), round(s[1] - 15)), (round(s[0] - 15), round(s[1] - 14))], rune)
    c = limb[14]; sp.layer(lambda d: d.ellipse([c[0] - 2.5, c[1] - 2.5, c[0] + 2.5, c[1] + 2.5], fill=255), GOLDTRIM)
    sp.dots([(round(c[0]), round(c[1]))], gem)
    return sp.done()

def staff(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.line([(4, 29), (22, 10)], fill=255, width=3), pal, g=(1, -1))
    # 모래시계 머리
    sp.layer(lambda d: d.polygon([(20, 3), (30, 3), (25, 8), (30, 13), (20, 13), (25, 8)], fill=255), GOLDTRIM)
    sp.layer(lambda d: d.polygon([(22, 5), (28, 5), (25, 7.5)], fill=255), pal, bevel=False, flat=rune)
    sp.layer(lambda d: d.polygon([(25, 9), (28, 11.5), (22, 11.5)], fill=255), pal, bevel=False, flat=rune)
    for (x, y) in ((17, 9), (12, 15), (9, 20)): sp.dots([(x, y)], rune)
    sp.dots([(25, 8)], gem)
    return sp.done()

def bell(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.ellipse([12, 2, 20, 9], outline=255, width=2), GOLDTRIM)
    sp.layer(lambda d: d.polygon([(10, 8), (22, 8), (25, 22), (28, 26), (4, 26), (7, 22)], fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.rectangle([3, 25, 29, 27], fill=255), GOLDTRIM)
    sp.layer(lambda d: d.ellipse([13.5, 26, 18.5, 31], fill=255), GOLDTRIM); sp.dots([(16, 28)], gem)
    for x in (12, 16, 20): sp.dots([(x, 15), (x, 16)], rune)
    return sp.done()

def chain(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    for i, (x, y) in enumerate(line_pts((5, 27), (21, 11), 5)):
        sp.layer(lambda d, x=x, y=y, i=i: d.ellipse([x - 3, y - 2, x + 3, y + 2] if i % 2 else [x - 2, y - 3, x + 2, y + 3], outline=255, width=2), pal)
    sp.layer(lambda d: d.polygon([(21, 11), (30, 2), (27, 12), (23, 13)], fill=255), pal, g=(1, -1))
    sp.dots([(25, 9), (26, 8)], rune); sp.dots([(22, 11)], gem)
    return sp.done()

def orb(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.ellipse([4, 4, 28, 28], fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.arc([8, 8, 24, 24], 200, 470, fill=255, width=2), pal, bevel=False, flat=rune)
    sp.layer(lambda d: d.ellipse([13, 13, 19, 19], fill=255), pal, bevel=False, flat=gem)
    sp.dots([(10, 9), (11, 8), (9, 10)], "#FFFFFF")
    return sp.done()

def crescent(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    m = lambda d: (d.ellipse([3, 3, 29, 29], fill=255), d.ellipse([10, 0, 34, 24], fill=0))
    sp.layer(m, pal, g=(-1, 1))
    sp.dots([(24, 22), (25, 21), (25, 22), (25, 23), (26, 22)], rune)
    sp.dots([(7, 16), (8, 16)], gem)
    return sp.done()

def helmet(pal, gem, rune, horns=False, crest=False):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    if horns:
        sp.layer(lambda d: d.polygon([(7, 13), (1, 4), (3, 2), (10, 10)], fill=255), ["#1A1A1A", "#4A4A4A", "#B0B0B0", "#E8E8E8"])
        sp.layer(lambda d: d.polygon([(25, 13), (31, 4), (29, 2), (22, 10)], fill=255), ["#1A1A1A", "#4A4A4A", "#B0B0B0", "#E8E8E8"])
    if crest:
        sp.layer(lambda d: d.polygon([(14, 2), (18, 2), (19, 9), (13, 9)], fill=255), GOLDTRIM)
    sp.layer(lambda d: d.chord([6, 6, 26, 30], 180, 360, fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.rectangle([6, 17, 26, 27], fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.rounded_rectangle([9, 20, 23, 22.5], radius=1, fill=255), pal, bevel=False, flat='#%02x%02x%02x' % shade(hx(pal[0]), 0.6))
    sp.layer(lambda d: d.rectangle([15, 19, 17, 27], fill=255), pal, g=(1, 1))
    sp.dots([(15, 11), (16, 11), (17, 11)], gem); sp.dots([(11, 15), (21, 15)], rune)
    return sp.done()

def chest(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.polygon([(4, 6), (11, 4), (16, 8), (21, 4), (28, 6), (27, 14), (24, 14), (24, 28), (8, 28), (8, 14), (5, 14)], fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.ellipse([12, 12, 20, 20], fill=255), GOLDTRIM)
    sp.layer(lambda d: d.ellipse([14, 14, 18, 18], fill=255), pal, bevel=False, flat=gem)
    sp.dots([(12, 23), (16, 24), (20, 23)], rune)
    return sp.done()

def legs(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.polygon([(7, 3), (25, 3), (26, 29), (19, 29), (16, 12), (13, 29), (6, 29)], fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.rectangle([7, 3, 25, 6], fill=255), GOLDTRIM)
    for x, y in ((5, 14), (5, 21), (27, 14), (27, 21)):
        sp.layer(lambda d, x=x, y=y: d.polygon([(x, y - 2), (x - 4 if x < 16 else x + 4, y), (x, y + 2)], fill=255), pal)
    sp.dots([(16, 4), (15, 4)], gem); sp.dots([(10, 12), (22, 12), (10, 20), (22, 20)], rune)
    return sp.done()

def wings(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    for side in (-1, 1):
        cx = 16 + side * 2
        for k, (ln, wd) in enumerate(((13, 5), (11, 4.5), (9, 4), (7, 3.5))):
            ang = math.radians(-60 + k * 30); ex = cx + side * math.cos(ang) * ln; ey = 12 + math.sin(ang) * ln + k * 3
            sp.layer(lambda d, ex=ex, ey=ey, wd=wd, k=k: d.polygon([(cx, 9 + k * 3), (ex, ey - wd / 2), (ex + side * 2, ey + wd / 2), (cx, 13 + k * 3)], fill=255), pal, g=(side, 1))
    sp.dots([(15, 10), (16, 10), (17, 10)], gem); sp.dots([(9, 12), (23, 12)], rune)
    return sp.done()

def boots(pal, gem, rune, winged=False, leaf=False):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    for ox in (2, 17):
        sp.layer(lambda d, ox=ox: d.polygon([(ox + 2, 8), (ox + 9, 8), (ox + 9, 22), (ox + 13, 24), (ox + 13, 28), (ox + 1, 28)], fill=255), pal, g=(1, 1))
        sp.layer(lambda d, ox=ox: d.rectangle([ox + 1, 8, ox + 10, 10], fill=255), GOLDTRIM)
        if winged:
            sp.layer(lambda d, ox=ox: d.polygon([(ox + 1, 14), (ox - 3, 9), (ox - 2, 15), (ox + 1, 18)], fill=255), ["#6B5A20", "#E8D080", "#FFF6D0", "#FFFFFF"])
        if leaf:
            sp.layer(lambda d, ox=ox: d.ellipse([ox + 9, 11, ox + 15, 15], fill=255), ["#1E4A0A", "#3E8A1A", "#8AD04A", "#D8FFA0"])
        sp.dots([(ox + 5, 18)], gem)
    sp.dots([(6, 24), (21, 24)], rune)
    return sp.done()

def scroll(pal, gem, rune, ticket=False):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    if ticket:
        sp.layer(lambda d: d.polygon([(3, 9), (29, 9), (29, 13), (27, 16), (29, 19), (29, 23), (3, 23), (3, 19), (5, 16), (3, 13)], fill=255), pal, g=(1, 1))
        sp.layer(lambda d: d.ellipse([12, 12, 20, 20], fill=255), pal, bevel=False, flat=gem)
        for a in range(0, 360, 45):
            x = 16 + math.cos(math.radians(a)) * 6; y = 16 + math.sin(math.radians(a)) * 6; sp.dots([(round(x), round(y))], gem)
        return sp.done()
    sp.layer(lambda d: d.rectangle([7, 6, 25, 26], fill=255), pal, g=(1, 1))
    for y in (4, 25):
        sp.layer(lambda d, y=y: d.rounded_rectangle([5, y, 27, y + 3], radius=1.5, fill=255), ["#3B2416", "#7A5130", "#B08050", "#E0B888"])
    for y in (10, 13, 16): sp.layer(lambda d, y=y: d.line([(10, y), (22, y)], fill=255), pal, bevel=False, flat='#%02x%02x%02x' % shade(hx(pal[1]), 0.8))
    sp.layer(lambda d: d.ellipse([18, 17, 25, 24], fill=255), [gem, gem, gem], flat=gem)
    sp.dots([(21, 19), (20, 20)], '#%02x%02x%02x' % mix(hx(gem), (255, 255, 255), 0.5))
    sp.layer(lambda d: d.polygon([(19, 23), (18, 28), (20, 27), (21, 24)], fill=255), [gem, gem, gem], flat='#%02x%02x%02x' % shade(hx(gem), 0.7))
    return sp.done()

def hourglass(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.polygon([(9, 5), (23, 5), (17, 16), (23, 27), (9, 27), (15, 16)], fill=255), ["#C8E8F0", "#E8F8FF", "#FFFFFF"], bevel=False, flat="#DDF2F8")
    sp.layer(lambda d: d.polygon([(11, 7), (21, 7), (16, 14)], fill=255), pal, bevel=False, flat=rune)
    sp.layer(lambda d: d.polygon([(16, 19), (21, 25), (11, 25)], fill=255), pal, g=(0, 1))
    for y in (3, 27): sp.layer(lambda d, y=y: d.rectangle([7, y, 25, y + 2], fill=255), pal, g=(1, 0))
    sp.dots([(16, 16), (16, 17)], gem)
    return sp.done()

def crystal(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.polygon([(16, 2), (26, 16), (16, 30), (6, 16)], fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.polygon([(16, 2), (16, 30), (6, 16)], fill=255), pal, g=(-1, 1))
    sp.layer(lambda d: d.arc([2, 11, 30, 21], 0, 360, fill=255, width=1), pal, bevel=False, flat=rune)
    sp.dots([(16, 15), (16, 16), (17, 16)], gem)
    return sp.done()

def ingots(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    for ox, oy in ((3, 18), (15, 18), (9, 9)):
        sp.layer(lambda d, ox=ox, oy=oy: d.polygon([(ox + 2, oy), (ox + 12, oy), (ox + 14, oy + 7), (ox, oy + 7)], fill=255), pal, g=(1, 1))
    sp.dots([(15, 11), (16, 11)], rune); sp.dots([(9, 21), (21, 21)], gem)
    return sp.done()

def key(pal, gem, rune, bow_kind="ring"):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.line([(13, 17), (27, 31)], fill=255, width=3), pal, g=(1, 1))
    sp.layer(lambda d: d.polygon([(21, 25), (25, 21), (27, 23), (23, 27)], fill=255), pal)
    sp.layer(lambda d: d.polygon([(24, 28), (28, 24), (30, 26), (26, 30)], fill=255), pal)
    if bow_kind == "sword":
        sp.layer(lambda d: d.polygon([(2, 2), (9, 4), (14, 14), (4, 9)], fill=255), pal, g=(1, 1))
        sp.layer(lambda d: d.line([(8, 16), (16, 8)], fill=255, width=3), GOLDTRIM)
    elif bow_kind == "snow":
        for a in range(0, 360, 60):
            x = 8 + math.cos(math.radians(a)) * 7; y = 8 + math.sin(math.radians(a)) * 7
            sp.layer(lambda d, x=x, y=y: d.line([(8, 8), (x, y)], fill=255, width=2), pal)
    elif bow_kind == "skull":
        sp.layer(lambda d: d.ellipse([1, 1, 16, 15], fill=255), pal, g=(1, 1))
        sp.layer(lambda d: d.rectangle([4, 12, 13, 17], fill=255), pal)
        sp.dots([(5.5, 7.5), (10.5, 7.5)], gem, r=1.1)
        return sp.done()
    else:
        sp.layer(lambda d: d.ellipse([1, 1, 16, 16], outline=255, width=3), pal, g=(1, 1))
    sp.layer(lambda d: d.ellipse([5.5, 5.5, 11.5, 11.5], fill=255), GOLDTRIM)
    sp.dots([(8, 8), (9, 8), (8, 9), (9, 9)], gem); sp.dots([(19, 23)], rune)
    return sp.done()

def crown(pal, gem, rune):
    sp = Sprite('#%02x%02x%02x' % shade(hx(pal[0]), 0.55))
    sp.layer(lambda d: d.polygon([(3, 26), (3, 10), (9, 17), (16, 5), (23, 17), (29, 10), (29, 26)], fill=255), pal, g=(1, 1))
    sp.layer(lambda d: d.rectangle([3, 22, 29, 26], fill=255), GOLDTRIM)
    for x, y in ((3, 9), (16, 4), (29, 9)): sp.layer(lambda d, x=x, y=y: d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=255), pal, flat=rune)
    sp.dots([(9, 24), (16, 24), (23, 24), (15, 24), (17, 24)], gem)
    return sp.done()

# ---------------- 아이템 ----------------
P = {
 "heaven": ["#4A3000", "#D49A00", "#FFE07A", "#FFFCE0"], "judge": ["#3E0B0B", "#A8770A", "#EEC955", "#FFF4C8"],
 "frost": ["#082640", "#2785C0", "#8FD6F5", "#F0FBFF"], "sealed": ["#06263F", "#176FAE", "#7FC4F0", "#ECF7FF"],
 "quantum": ["#3E3109", "#A88624", "#E2CD7E", "#FDFBF0"], "ember": ["#330B00", "#B3390A", "#F28A3A", "#FFDDBB"],
 "soul": ["#140A22", "#45306E", "#8669BC", "#D8CBFF"], "time": ["#332914", "#857246", "#C8B585", "#ECF0E0"],
 "thunder": ["#232D0B", "#7F9726", "#CFE27E", "#E8FAFF"], "ruiner": ["#1C0008", "#7E0818", "#CC3A44", "#FFAAAA"],
 "chaos": ["#0A0A0C", "#36363C", "#83838C", "#DEDEE2"], "philo": ["#0A2935", "#1B6C86", "#73BCD1", "#ECF5F7"],
 "void": ["#18191E", "#4D4F57", "#9EA1AA", "#F0F0F0"], "bell": ["#241F38", "#665A9E", "#B3A8DD", "#F0EDFF"],
 "shade": ["#0D0D12", "#2E2E44", "#666684", "#B3B3C8"], "moon": ["#10133E", "#3942A6", "#959BE2", "#FAD4D4"],
 "nature": ["#263A00", "#739A00", "#C2E35E", "#EEFFD2"], "parch": ["#5C4A2E", "#C9B48A", "#EDE2C6", "#FFFBF0"],
 "vib": ["#262012", "#62521E", "#AA924E", "#E9E0C6"], "wkey": ["#1B3442", "#4787A8", "#A2D2EA", "#EEF8FF"],
 "hkey": ["#3A3A40", "#8C8C94", "#D2D2D8", "#FFFFFF"],
}
ITEMS = [
 ("claymore_sd", lambda: sword(P["heaven"], "#4FC3F7", "#FFF6C0", "great", SILVERTRIM), "handheld"),
 ("claymore_js", lambda: sword(P["judge"], "#E53935", "#FFE0A0", "great", CRIMSONTRIM), "handheld"),
 ("frost_grave", lambda: sword(P["frost"], "#E8FBFF", "#C8F2FF", "ice"), "handheld"),
 ("sealed_sword", lambda: sword(P["sealed"], "#FFFFFF", "#B8E6FF", "long"), "handheld"),
 ("quantum_sword", lambda: sword(P["quantum"], "#B388FF", "#FFF5D6", "quantum", SILVERTRIM), "handheld"),
 ("ember_brand", lambda: sword(P["ember"], "#FFE14D", "#FFC38A", "flame"), "handheld"),
 ("soul_splitter", lambda: sword(P["soul"], "#69F0AE", "#CDBDFF", "katana"), "handheld"),
 ("chronos", lambda: sword(P["time"], "#9BD4A2", "#FFE6E0", "clock"), "handheld"),
 ("thunder_axe", lambda: axe(P["thunder"], "#FFF176", "#FFF59D"), "handheld"),
 ("ruiner_scythe", lambda: scythe(P["ruiner"], "#FF1744", "#FF8A80"), "handheld"),
 ("chaos_apocalypse", lambda: scythe(P["chaos"], "#B388FF", "#D8D8E0", True), "handheld"),
 ("aristotle_pickaxe", lambda: pickaxe(P["philo"], "#FFFFFF", "#B2EBF2"), "handheld"),
 ("void_bow", lambda: bow(P["void"], "#B388FF", "#FFFFFF"), "bow"),
 ("time_piecer_wind", lambda: staff(P["time"], "#9BD4A2", "#F5E6B8"), "handheld_rod"),
 ("echo_bell", lambda: bell(P["bell"], "#E1D9FF", "#E1D9FF"), "generated"),
 ("umbral_chain", lambda: chain(P["shade"], "#B388FF", "#B388FF"), "handheld"),
 ("darkness_wave", lambda: orb(P["chaos"], "#B388FF", "#8C8C94"), "generated"),
 ("new_characteristic", lambda: crescent(P["moon"], "#FAD4D4", "#FFFFFF"), "generated"),
 ("void_chest", lambda: chest(P["void"], "#B388FF", "#D8D8E0"), "generated"),
 ("russell_helmet", lambda: helmet(P["philo"], "#FFFFFF", "#B2EBF2", crest=True), "generated"),
 ("chaos_helmet", lambda: helmet(P["chaos"], "#B388FF", "#D8D8E0", horns=True), "generated"),
 ("ruiner_pants", lambda: legs(P["ruiner"], "#FF1744", "#FF8A80"), "generated"),
 ("sealed_wings", lambda: wings(P["sealed"], "#FFFFFF", "#B8E6FF"), "generated"),
 ("aurora_boots", lambda: boots(P["quantum"], "#B388FF", "#FFF5D6", winged=True), "generated"),
 ("nature_boots", lambda: boots(P["nature"], "#FFE14D", "#EEFFD2", leaf=True), "generated"),
 ("legend_voucher", lambda: scroll(P["parch"], "#43A047", "#43A047"), "generated"),
 ("mythic_voucher", lambda: scroll(P["parch"], "#FFB300", "#FFB300"), "generated"),
 ("altar_ticket", lambda: scroll(["#5A4C00", "#CDB43C", "#F6E8A0", "#FFFDEB"], "#4FC3F7", "#4FC3F7", ticket=True), "generated"),
 ("time_shard", lambda: hourglass(P["time"], "#9BD4A2", "#E8D49A"), "generated"),
 ("quantum_matter", lambda: crystal(P["quantum"], "#FFFFFF", "#B388FF"), "generated"),
 ("vibranium", lambda: ingots(P["vib"], "#FFFFFF", "#FFF5D6"), "generated"),
 ("weapon_key", lambda: key(P["wkey"], "#FFE14D", "#FFFFFF", "sword"), "generated"),
 ("heaven_key", lambda: key(P["hkey"], "#43E07A", "#FF5252", "ring"), "generated"),
 ("sealed_key", lambda: key(P["sealed"], "#FFFFFF", "#B8E6FF", "snow"), "generated"),
 ("ruiner_key", lambda: key(P["ruiner"], "#FF1744", "#FF8A80", "skull"), "generated"),
 ("crown_mark", lambda: crown(P["heaven"], "#E53935", "#FFFCE0"), "generated"),
]

def main(out):
    A = os.path.join(out, 'assets', 'wave')
    for d in ('items', 'models/item', 'textures/item'): os.makedirs(os.path.join(A, d), exist_ok=True)
    parents = {'handheld': 'minecraft:item/handheld', 'handheld_rod': 'minecraft:item/handheld_rod', 'generated': 'minecraft:item/generated', 'bow': 'minecraft:item/bow'}
    def model(key, parent): json.dump({'parent': parents[parent], 'textures': {'layer0': 'wave:item/' + key}}, open(os.path.join(A, 'models/item', key + '.json'), 'w'), indent=1)
    m = lambda k: {'type': 'minecraft:model', 'model': 'wave:item/' + k}
    for key, draw, parent in ITEMS:
        draw().save(os.path.join(A, 'textures/item', key + '.png')); model(key, parent)
        d = {'model': m(key)}
        if parent == 'bow':
            pal = P["void"]
            for i in range(3):
                bow(pal, "#B388FF", "#FFFFFF", pull=i + 1).save(os.path.join(A, 'textures/item', '%s_pulling_%d.png' % (key, i)))
                model('%s_pulling_%d' % (key, i), 'bow')
            d = {'model': {'type': 'minecraft:condition', 'property': 'minecraft:using_item', 'on_false': m(key),
                 'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:use_duration', 'scale': 0.05,
                             'entries': [{'threshold': 0.65, 'model': m(key + '_pulling_1')}, {'threshold': 0.9, 'model': m(key + '_pulling_2')}],
                             'fallback': m(key + '_pulling_0')}}}
        json.dump(d, open(os.path.join(A, 'items', key + '.json'), 'w'), indent=1)
    json.dump({'pack': {'description': 'Wave Online 특별 아이템', 'pack_format': 75, 'min_format': [75, 0], 'max_format': [75, 0]}},
              open(os.path.join(out, 'pack.mcmeta'), 'w'), ensure_ascii=False, indent=1)
    print(len(ITEMS), 'items')

if __name__ == '__main__':
    main(sys.argv[1])
