#!/usr/bin/env python3
# FotoFrame icon — "Fo" wordmark. Letters are drawn from geometry (no font, no licence).
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

S = 3
N = 1024
HALF = 412.0
EXP = 5.2
OUT = os.path.dirname(os.path.abspath(__file__))

PAPER = (243, 238, 228)
DARK = (21, 19, 16)
GRAPH = (26, 24, 21)
BONE = (238, 231, 217)
BRONZE = (162, 101, 47)

DUSK = [
    (0.00, (95, 123, 147)),
    (0.28, (138, 163, 175)),
    (0.50, (200, 180, 159)),
    (0.66, (217, 164, 115)),
    (0.85, (176, 118, 75)),
    (1.00, (126, 78, 56)),
]

# ── master proportions ─────────────────────────────────────────────────────
STROKE_F = 92
STROKE_O = 86
CAP = 480
FW = 298
GAP = 38
OXH = 340
W = FW + GAP + OXH
LEFT = 512 - W / 2
TOP = 512 - (CAP + 8) / 2
BASE = TOP + CAP
MID = TOP + CAP * 0.53


def grad_img(size, stops):
    w, h = size
    ys = np.linspace(0, 1, h)
    pos = np.array([s[0] for s in stops])
    cols = np.array([s[1] for s in stops], dtype=float)
    out = np.zeros((h, 3))
    for c in range(3):
        out[:, c] = np.interp(ys, pos, cols[:, c])
    return Image.fromarray(
        np.repeat(out[:, None, :], w, axis=1).astype(np.uint8), "RGB"
    )


def squircle_mask():
    size = N * S
    a = HALF * S
    xs = (np.arange(size) + 0.5 - size / 2) / a
    ys = (np.arange(size) + 0.5 - size / 2) / a
    v = np.abs(xs)[None, :] ** EXP + np.abs(ys)[:, None] ** EXP
    return np.clip((1.02 - v) / 0.05, 0, 1)


MASK = squircle_mask()


class C:
    def __init__(self, bg):
        self.im = Image.new("RGBA", (N * S, N * S), bg + (255,))
        self.d = ImageDraw.Draw(self.im)

    def rect(self, box, fill):
        self.d.rectangle([v * S for v in box], fill=fill)

    def disc(self, cx, cy, r, fill):
        self.d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill)

    def photo_disc(self, cx, cy, r):
        d = int(2 * r * S)
        g = grad_img((d, d), DUSK).convert("RGBA")
        ov = Image.new("RGBA", (d, d), (0, 0, 0, 0))
        ImageDraw.Draw(ov).rectangle([0, int(d * 0.68), d, d], fill=(52, 30, 20, 52))
        g = Image.alpha_composite(g, ov)
        dd = ImageDraw.Draw(g)
        sx, sy, sr = d * 0.64, d * 0.36, d * 0.10
        dd.ellipse([sx - sr, sy - sr, sx + sr, sy + sr], fill=(247, 233, 210, 255))
        m = Image.new("L", (d, d), 0)
        ImageDraw.Draw(m).ellipse([0, 0, d - 1, d - 1], fill=255)
        g.putalpha(m)
        self.im.alpha_composite(g, (int((cx - r) * S), int((cy - r) * S)))
        self.d = ImageDraw.Draw(self.im)

    def finish(self, path):
        a = np.array(self.im)
        a[:, :, 3] = (a[:, :, 3].astype(np.float32) * MASK).astype(np.uint8)
        im = Image.fromarray(a, "RGBA").resize((N, N), Image.LANCZOS)
        im.save(path)
        return im


def geo(stroke_f, stroke_o, gap, oxh, cap, fw):
    w = fw + gap + oxh
    left = 512 - w / 2
    top = 512 - (cap + 8) / 2
    return dict(left=left, top=top, base=top + cap, mid=top + cap * 0.53,
                stroke_f=stroke_f, stroke_o=stroke_o, gap=gap, oxh=oxh,
                cap=cap, fw=fw)


def draw_mark(c, g, fcolor, ocolor=None, photo=False, bg=None):
    left, top, mid = g["left"], g["top"], g["mid"]
    sf, fw, cap = g["stroke_f"], g["fw"], g["cap"]
    c.rect((left, top, left + sf, top + cap), fcolor)
    c.rect((left, top, left + fw, top + sf), fcolor)
    c.rect((left, mid - sf / 2, left + fw * 0.87, mid + sf / 2), fcolor)
    cx = left + fw + g["gap"] + g["oxh"] / 2
    cy = g["base"] - g["oxh"] / 2
    ro = g["oxh"] / 2 + 4
    col = ocolor or fcolor
    if photo:
        c.disc(cx, cy, ro, col)
        c.photo_disc(cx, cy, ro - g["stroke_o"] + 2)
    else:
        c.disc(cx, cy, ro, col)
        c.disc(cx, cy, ro - g["stroke_o"], bg)


def letter_f(c, color, left=LEFT, top=TOP, s=STROKE_F, cap=CAP, fw=FW, mid=MID):
    c.rect((left, top, left + s, top + cap), color)          # stem
    c.rect((left, top, left + fw, top + s), color)           # top arm
    c.rect((left, mid - s / 2, left + fw * 0.87, mid + s / 2), color)  # middle arm


def letter_o(c, cx, cy, color, bg, oxh=OXH, stroke=STROKE_O):
    ro = oxh / 2 + 4          # overshoot
    c.disc(cx, cy, ro, color)
    c.disc(cx, cy, ro - stroke, bg)


NORMAL = geo(STROKE_F, STROKE_O, GAP, OXH, CAP, FW)
OPTICAL = geo(106, 98, 30, 354, 494, 304)


def fo(path, bg, fcolor, ocolor=None, photo=False, g=None):
    c = C(bg)
    draw_mark(c, g or NORMAL, fcolor, ocolor=ocolor, photo=photo, bg=bg)
    return c.finish(path)


CN = "/System/Library/Fonts/Hiragino Sans GB.ttc"


def font(path, size, index=0):
    try:
        return ImageFont.truetype(path, size, index=index)
    except Exception:
        return ImageFont.load_default()


VARIANTS = [
    ("A", "浅 · 纯字标", dict(bg=PAPER, fcolor=GRAPH)),
    ("B", "深 · 纯字标", dict(bg=DARK, fcolor=BONE)),
    ("C", "浅 · 铜色 o", dict(bg=PAPER, fcolor=GRAPH, ocolor=BRONZE)),
    ("D", "浅 · 照片 o", dict(bg=PAPER, fcolor=GRAPH, photo=True)),
    ("E", "深 · 照片 o", dict(bg=DARK, fcolor=BONE, photo=True)),
]


def main():
    made = []
    for key, name, kw in VARIANTS:
        p = os.path.join(OUT, f"fo_{key}.png")
        made.append((key, name, fo(p, **kw)))
        print("✓", os.path.basename(p))

    # ── variants board ────────────────────────────────────────────────────
    W_, H_ = 1740, 900
    board = Image.new("RGB", (W_, H_), (233, 230, 223))
    d = ImageDraw.Draw(board)
    d.text((80, 58), "FotoFrame  “Fo”", font=font(CN, 50, 1), fill=(28, 25, 22))
    d.text((80, 124), "几何自绘字标  ·  无字体版权  ·  纸—墨—铜",
           font=font(CN, 25, 0), fill=(122, 113, 100))
    tw, gap, x0, y0 = 300, 34, 80, 215
    for i, (key, name, icon) in enumerate(made):
        x = x0 + i * (tw + gap)
        t = icon.resize((tw, tw), Image.LANCZOS)
        board.paste(t, (x, y0), t)
        d.text((x + 2, y0 + tw + 26), key, font=font("/System/Library/Fonts/HelveticaNeue.ttc", 22),
               fill=(150, 106, 58))
        d.text((x + 34, y0 + tw + 20), name, font=font(CN, 24, 1), fill=(28, 25, 22))
    board.save(os.path.join(OUT, "fo_board.png"))
    print("✓ board")

    # ── dock mock: light + dark desktop, 128 px ───────────────────────────
    D = 128
    mock = Image.new("RGB", (1060, 560), (255, 255, 255))
    d = ImageDraw.Draw(mock)
    d.rectangle([0, 0, 1060, 280], fill=(236, 233, 226))
    d.rectangle([0, 280, 1060, 560], fill=(38, 36, 33))
    for row, (bgc, y) in enumerate([((228, 225, 217), 60), ((46, 44, 40), 340)]):
        dock = Image.new("RGBA", (990, 190), (0, 0, 0, 0))
        dd = ImageDraw.Draw(dock)
        dd.rounded_rectangle([0, 0, 989, 189], radius=34,
                             fill=(255, 255, 255, 90) if row == 0 else (255, 255, 255, 26))
        mock.paste(dock, (30, y), dock)
        x = 70
        for key, name, icon in made:
            t = icon.resize((D, D), Image.LANCZOS)
            mock.paste(t, (x, y + 30), t)
            x += D + 62
    mock.save(os.path.join(OUT, "fo_dock.png"))
    print("✓ dock mock")


if __name__ == "__main__":
    main()