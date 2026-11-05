#!/usr/bin/env python3
# FotoFrame app icon — 9 concept directions, flat / paper-ink-bronze.
# Drawn at 3x, masked with a squircle, downsampled to 1024.

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

S = 3
N = 1024
HALF = 412.0
EXP = 5.2

OUT = os.path.dirname(os.path.abspath(__file__))

# ── palette ────────────────────────────────────────────────────────────────
PAPER  = (243, 238, 228)
DARK   = (20, 18, 15)
GRAPH  = (27, 25, 22)
INK    = (15, 14, 12)
BONE   = (237, 230, 216)
BRONZE = (162, 101, 47)
GLASS  = (86, 104, 118)
SUN    = (247, 233, 210)

DUSK = [
    (0.00, (95, 123, 147)),
    (0.28, (138, 163, 175)),
    (0.50, (200, 180, 159)),
    (0.66, (217, 164, 115)),
    (0.85, (176, 118, 75)),
    (1.00, (126, 78, 56)),
]


# ── helpers ────────────────────────────────────────────────────────────────
def grad_img(size, stops):
    w, h = size
    ys = np.linspace(0, 1, h)
    pos = np.array([s[0] for s in stops])
    cols = np.array([s[1] for s in stops], dtype=float)
    out = np.zeros((h, 3))
    for c in range(3):
        out[:, c] = np.interp(ys, pos, cols[:, c])
    arr = np.repeat(out[:, None, :], w, axis=1).astype(np.uint8)
    return Image.fromarray(arr, "RGB")


def photo_img(size, radius, corners=None, horizon=0.68,
              sun=(0.72, 0.30, 0.058)):
    w, h = size
    g = grad_img(size, DUSK).convert("RGBA")
    ov = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).rectangle(
        [0, int(h * horizon), w, h], fill=(52, 30, 20, 52)
    )
    g = Image.alpha_composite(g, ov)
    d = ImageDraw.Draw(g)
    cx, cy, r = w * sun[0], h * sun[1], min(w, h) * sun[2]
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=SUN + (255,))
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [0, 0, w - 1, h - 1], radius=int(radius * S), fill=255, corners=corners
    )
    g.putalpha(m)
    return g


def squircle_mask():
    size = N * S
    a = HALF * S
    xs = (np.arange(size) + 0.5 - size / 2) / a
    ys = (np.arange(size) + 0.5 - size / 2) / a
    v = np.abs(xs)[None, :] ** EXP + np.abs(ys)[:, None] ** EXP
    return np.clip((1.02 - v) / 0.05, 0, 1)


MASK = squircle_mask()


class Canvas:
    def __init__(self, bg):
        self.im = Image.new("RGBA", (N * S, N * S), bg + (255,))
        self.refresh()

    def refresh(self):
        self.d = ImageDraw.Draw(self.im)

    # coords in 1024-space
    def rr(self, box, r, fill, corners=None):
        self.d.rounded_rectangle(
            [v * S for v in box], radius=int(r * S), fill=fill, corners=corners
        )

    def circle(self, cx, cy, r, fill):
        self.d.ellipse(
            [(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill
        )

    def ring(self, cx, cy, r, w, fill):
        self.d.ellipse(
            [(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S],
            outline=fill, width=int(w * S),
        )

    def poly(self, pts, fill):
        self.d.polygon([(x * S, y * S) for x, y in pts], fill=fill)

    def photo(self, box, r, corners=None, **kw):
        x1, y1, x2, y2 = box
        w, h = int((x2 - x1) * S), int((y2 - y1) * S)
        p = photo_img((w, h), r, corners=corners, **kw)
        self.im.alpha_composite(p, (int(x1 * S), int(y1 * S)))
        self.refresh()

    def finish(self, path):
        a = np.array(self.im)
        a[:, :, 3] = (a[:, :, 3].astype(np.float32) * MASK).astype(np.uint8)
        im = Image.fromarray(a, "RGBA").resize((N, N), Image.LANCZOS)
        im.save(path)
        return im


def lens(c, cx, cy, r, shell, ring=BRONZE, glass=GLASS, catch=True):
    c.circle(cx, cy, r, ring)
    c.circle(cx, cy, r * 0.74, shell)
    c.circle(cx, cy, r * 0.44, glass)
    if catch:
        c.circle(cx - r * 0.16, cy - r * 0.16, r * 0.10, BONE)


def card_img(w, h, border, r, paper=BONE):
    """Photo print: paper margin + photo, at S resolution."""
    im = Image.new("RGBA", (int(w * S), int(h * S)), (0, 0, 0, 0))
    ImageDraw.Draw(im).rounded_rectangle(
        [0, 0, int(w * S) - 1, int(h * S) - 1], radius=int(r * S), fill=paper
    )
    b = int(border * S)
    p = photo_img((int(w * S) - 2 * b, int(h * S) - 2 * b), r * 0.5)
    im.alpha_composite(p, (b, b))
    return im


# ── 01 back screen ─────────────────────────────────────────────────────────
def i01(path):
    c = Canvas(PAPER)
    c.rr((414, 214, 610, 270), 18, GRAPH)
    c.rr((160, 262, 864, 794), 70, GRAPH)
    c.photo((224, 312, 800, 744), 14)
    return c.finish(path)


# ── 02 slide in ────────────────────────────────────────────────────────────
def i02(path):
    c = Canvas(PAPER)
    card = card_img(272, 362, 18, 30).rotate(-12, resample=Image.BICUBIC, expand=True)
    c.im.alpha_composite(
        card, (int(656 * S - card.width / 2), int(402 * S - card.height / 2))
    )
    c.refresh()
    c.rr((168, 384, 792, 766), 82, GRAPH)
    c.rr((424, 342, 568, 390), 16, GRAPH)
    lens(c, 344, 578, 120, INK)
    return c.finish(path)


# ── 03 ivory shell over photo ──────────────────────────────────────────────
def i03(path):
    c = Canvas(DARK)
    card = card_img(420, 520, 26, 34, paper=(222, 209, 184)).rotate(
        -10, resample=Image.BICUBIC, expand=True)
    c.im.alpha_composite(
        card, (int(636 * S - card.width / 2), int(590 * S - card.height / 2))
    )
    c.refresh()
    c.rr((272, 286, 404, 336), 16, BONE)
    c.rr((140, 330, 660, 700), 92, BONE)
    lens(c, 296, 482, 104, GRAPH, ring=GRAPH, glass=INK)
    return c.finish(path)


# ── 04 camera as frame ─────────────────────────────────────────────────────
def i04(path):
    c = Canvas(PAPER)
    c.rr((546, 140, 700, 188), 16, GRAPH)
    c.rr((160, 160, 864, 864), 178, GRAPH)
    c.photo((292, 292, 732, 732), 46)
    lens(c, 292, 292, 110, GRAPH, ring=BRONZE, glass=INK)
    return c.finish(path)


# ── 05 split reveal ────────────────────────────────────────────────────────
def i05(path):
    c = Canvas(DARK)
    c.rr((140, 292, 884, 732), 118, BONE)
    lens(c, 336, 512, 132, GRAPH, ring=GRAPH, glass=INK)
    c.photo((524, 292, 884, 732), 118, corners=(False, True, True, False))
    c.rr((504, 292, 524, 732), 2, DARK)
    return c.finish(path)


# ── 06 viewfinder ──────────────────────────────────────────────────────────
def i06(path):
    c = Canvas(PAPER)
    c.photo((300, 300, 724, 724), 12)
    t, arm = 56, 172
    # bottom-left: goes right + up
    c.rr((244 - t / 2, 780 - t / 2, 244 + t / 2, 780 + t / 2), 10, BRONZE)
    c.rr((244 - t / 2, 780 - t / 2, 244 + arm, 780 + t / 2), 10, BRONZE)
    c.rr((244 - t / 2, 780 - arm, 244 + t / 2, 780 + t / 2), 10, BRONZE)
    # bottom-right: goes left + up
    c.rr((780 - t / 2, 780 - t / 2, 780 + t / 2, 780 + t / 2), 10, BRONZE)
    c.rr((780 - arm, 780 - t / 2, 780 + t / 2, 780 + t / 2), 10, BRONZE)
    c.rr((780 - t / 2, 780 - arm, 780 + t / 2, 780 + t / 2), 10, BRONZE)
    # top-right: goes left + down
    c.rr((780 - t / 2, 244 - t / 2, 780 + t / 2, 244 + t / 2), 10, BRONZE)
    c.rr((780 - arm, 244 - t / 2, 780 + t / 2, 244 + t / 2), 10, BRONZE)
    c.rr((780 - t / 2, 244 - t / 2, 780 + t / 2, 244 + arm), 10, BRONZE)
    # top-left: lens instead of a bracket
    lens(c, 244, 244, 100, PAPER, ring=BRONZE, glass=INK)
    return c.finish(path)


# ── 07 monogram F ──────────────────────────────────────────────────────────
def i07(path):
    c = Canvas(PAPER)
    c.rr((282, 200, 398, 824), 24, GRAPH)
    c.rr((282, 200, 700, 316), 24, GRAPH)
    c.rr((282, 452, 620, 568), 24, GRAPH)
    c.photo((448, 596, 724, 824), 24)
    lens(c, 744, 258, 100, GRAPH, ring=BRONZE, glass=INK)
    return c.finish(path)


# ── 08 sticker shell on photo ──────────────────────────────────────────────
def i08(path):
    c = Canvas(PAPER)
    c.photo((208, 208, 816, 816), 136, sun=(0.28, 0.70, 0.052))
    layer = Image.new("RGBA", (N * S, N * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([286 * S, 396 * S, 738 * S, 736 * S], radius=58 * S, fill=GRAPH)
    d.rounded_rectangle([462 * S, 360 * S, 584 * S, 404 * S], radius=14 * S, fill=GRAPH)
    d.ellipse(
        [(512 - 126) * S, (566 - 126) * S, (512 + 126) * S, (566 + 126) * S],
        fill=(0, 0, 0, 0),
    )
    c.im.alpha_composite(layer)
    c.refresh()
    c.ring(512, 566, 126, 18, BONE)
    c.circle(684, 438, 20, BONE)
    return c.finish(path)


# ── 09 iris ────────────────────────────────────────────────────────────────
def i09(path):
    c = Canvas(DARK)
    R, r = 310, 178
    hexpts = [
        (512 + r * math.cos(math.radians(90 + 60 * i)),
         512 + r * math.sin(math.radians(90 + 60 * i)))
        for i in range(6)
    ]
    m = Image.new("L", (N * S, N * S), 0)
    ImageDraw.Draw(m).polygon([(x * S, y * S) for x, y in hexpts], fill=255)
    px = photo_img((430 * S, 430 * S), 0)
    c.im.alpha_composite(px, (int((512 - 215) * S), int((512 - 215) * S)))
    arr = np.array(c.im)
    arr[:, :, 3] = (arr[:, :, 3] * (np.array(m) / 255)).astype(np.uint8)
    c.im = Image.fromarray(arr, "RGBA")
    c.refresh()

    for i in range(6):
        a0, a1 = 90 + 60 * i, 90 + 60 * (i + 1)
        V0 = (512 + r * math.cos(math.radians(a0)), 512 + r * math.sin(math.radians(a0)))
        V1 = (512 + r * math.cos(math.radians(a1)), 512 + r * math.sin(math.radians(a1)))
        P1 = (512 + R * math.cos(math.radians(a1 + 16)), 512 + R * math.sin(math.radians(a1 + 16)))
        P2 = (512 + R * math.cos(math.radians(a0 + 16)), 512 + R * math.sin(math.radians(a0 + 16)))
        c.poly([V0, V1, P1, P2], (74, 66, 55) if i % 2 else (52, 46, 38))
        c.d.line([(V0[0] * S, V0[1] * S), (P2[0] * S, P2[1] * S)],
                 fill=(96, 86, 71), width=int(5 * S))
    c.ring(512, 512, R, 42, BRONZE)
    return c.finish(path)


ICONS = [
    ("01", "背屏", "相机后背 + 满屏照片", i01),
    ("02", "滑入", "照片卡正插进相机", i02),
    ("03", "叠壳", "骨白外壳压住照片", i03),
    ("04", "画框", "厚框成了相机本体", i04),
    ("05", "剖开", "左壳右片，一刀见分界", i05),
    ("06", "取景", "取景角标 + 角落镜头", i06),
    ("07", "字标", "F 的横臂长出镜头", i07),
    ("08", "贴壳", "相机剪影贴在照片上", i08),
    ("09", "光圈", "光圈开口里是一张照片", i09),
]


def build():
    made = []
    for num, name, desc, fn in ICONS:
        p = os.path.join(OUT, f"{num}_{name}.png")
        made.append((num, name, desc, fn(p)))
        print("✓", os.path.basename(p))
    return made


CN = "/System/Library/Fonts/Hiragino Sans GB.ttc"

def font(path, size, index=0):
    try:
        return ImageFont.truetype(path, size, index=index)
    except Exception:
        return ImageFont.load_default()


def board(made):
    W, H = 1700, 1906
    im = Image.new("RGB", (W, H), (233, 230, 223))
    d = ImageDraw.Draw(im)
    d.text((86, 70), "FotoFrame", font=font(CN, 54, 1), fill=(28, 25, 22))
    d.text((86, 140),
           "Icon directions  ·  9 个方向  ·  扁平 / 相机壳包照片 / 纸—墨—铜",
           font=font(CN, 26, 0), fill=(122, 113, 100))

    tile, gap, x0, y0 = 380, 32, 86, 232
    for k, (num, name, desc, icon) in enumerate(made):
        col, row = k % 3, k // 3
        x = x0 + col * (tile + gap)
        y = y0 + row * (tile + 118)
        thumb = icon.resize((tile, tile), Image.LANCZOS)
        im.paste(thumb, (x, y), thumb)
        d.text((x + 2, y + tile + 26), num, font=font("/System/Library/Fonts/HelveticaNeue.ttc", 26), fill=(150, 106, 58))
        d.text((x + 46, y + tile + 16), name, font=font(CN, 38, 1), fill=(28, 25, 22))
        d.text((x + 2, y + tile + 66), desc, font=font(CN, 24, 0), fill=(120, 111, 99))

    im.save(os.path.join(OUT, "00_board.png"))
    print("✓ board")


def small_board(made):
    sizes, W, H = [128, 64, 32], 1210, 640
    im = Image.new("RGB", (W, H), (233, 230, 223))
    d = ImageDraw.Draw(im)
    f = font(CN, 22, 0)
    y = 62
    for s in sizes:
        x = 62
        d.text((62, y - 32), f"{s} px", font=f, fill=(130, 121, 108))
        for num, name, desc, icon in made:
            t = icon.resize((s, s), Image.LANCZOS)
            im.paste(t, (x, y), t)
            x += s + 66
        y += s + 80
    im.save(os.path.join(OUT, "00_small.png"))
    print("✓ small board")


if __name__ == "__main__":
    m = build()
    board(m)
    small_board(m)