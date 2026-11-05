#!/usr/bin/env python3
"""自动标注相机背面图的屏幕区域（黑机身白底官方图优化版）。

思路：
1. 白底上找机身轮廓（最大深色连通域）。
2. 屏幕是机身内「最大面积的均匀深色矩形」——屏幕玻璃均匀纯黑，
   而机身蒙皮有纹理（方差大）、按键有字符。用积分图快速算局部均值/方差，
   在机身 bbox 内找方差极低且暗的最大矩形。

用法：
  tools/annotate_screen.py                # 处理所有缺 screen 标注的资产
  tools/annotate_screen.py leica/m11 ...  # 只处理指定资产
  tools/annotate_screen.py --force        # 重标所有
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets", "cameras")

WORK_W = 600  # 分析用缩放宽度


def load_gray(path):
    img = Image.open(path).convert("L")
    w, h = img.size
    scale = WORK_W / w if w > WORK_W else 1.0
    if scale < 1.0:
        img = img.resize((WORK_W, max(1, round(h * scale))))
    return img, 1.0 / scale


def integral(px, w, h):
    """返回 (sum, sumsq) 两张积分图（(w+1)x(h+1)）。"""
    s = [[0.0] * (w + 1) for _ in range(h + 1)]
    sq = [[0.0] * (w + 1) for _ in range(h + 1)]
    for y in range(h):
        row_s = 0.0
        row_sq = 0.0
        for x in range(w):
            v = px[x, y]
            row_s += v
            row_sq += v * v
            s[y + 1][x + 1] = s[y][x + 1] + row_s
            sq[y + 1][x + 1] = sq[y][x + 1] + row_sq
    return s, sq


def rect_stats(s, sq, x1, y1, x2, y2):
    """[x1,x2) [y1,y2) 的 (mean, std)。"""
    n = (x2 - x1) * (y2 - y1)
    if n <= 0:
        return 255.0, 999.0
    total = s[y2][x2] - s[y1][x2] - s[y2][x1] + s[y1][x1]
    total_sq = sq[y2][x2] - sq[y1][x2] - sq[y2][x1] + sq[y1][x1]
    mean = total / n
    var = max(0.0, total_sq / n - mean * mean)
    return mean, math.sqrt(var)


def body_bbox(px, w, h, thresh=100):
    """白底上的机身 bbox：逐行/列找深色像素范围。"""
    xs, ys = [], []
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if px[x, y] < thresh:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def find_screen(img):
    """在缩放图上找屏幕矩形，返回 (x1,y1,x2,y2)（缩放图坐标）。

    方法：局部平滑度掩码（屏幕玻璃整体平滑，蒙皮/按键/字符都不平滑），
    然后在机身 bbox 内求最大全 1 矩形（直方图法）。
    """
    w, h = img.size
    px = img.load()
    bb = body_bbox(px, w, h)
    if bb is None:
        return None
    bx1, by1, bx2, by2 = bb
    s, sq = integral(px, w, h)

    WIN = 5  # 局部窗口半径（窗口 11x11）
    STD_MAX = 9.0
    # 平滑度掩码
    mask = [[0] * w for _ in range(h)]
    for y in range(by1, by2 + 1):
        y1, y2 = max(0, y - WIN), min(h, y + WIN + 1)
        for x in range(bx1, bx2 + 1):
            x1, x2 = max(0, x - WIN), min(w, x + WIN + 1)
            _, sd = rect_stats(s, sq, x1, y1, x2, y2)
            if sd < STD_MAX:
                mask[y][x] = 1

    # 最大全 1 矩形（在机身 bbox 内，直方图法）
    best = None  # (area, x1, y1, x2, y2)
    heights = [0] * (bx2 - bx1 + 1)
    for y in range(by1, by2 + 1):
        for i, x in enumerate(range(bx1, bx2 + 1)):
            heights[i] = heights[i] + 1 if mask[y][x] else 0
        # 直方图最大矩形
        stack = []
        n = len(heights)
        for i in range(n + 1):
            cur = heights[i] if i < n else 0
            start = i
            while stack and stack[-1][1] > cur:
                idx, ht = stack.pop()
                area = ht * (i - idx)
                if best is None or area > best[0]:
                    best = (area, bx1 + idx, y - ht + 1, bx1 + i, y + 1)
                start = idx
            if not stack or stack[-1][1] < cur:
                stack.append((start, cur))
    if best is None:
        return None
    area, x1, y1, x2, y2 = best
    bw, bh = x2 - x1, y2 - y1
    if bw <= 0 or bh <= 0:
        return None
    ar = bw / bh
    body_area = (bx2 - bx1) * (by2 - by1)
    if area < body_area * 0.06:
        return None
    if not (1.05 < ar < 2.4):
        return None
    return x1, y1, x2, y2


def process(camdir: str, force: bool) -> bool:
    meta_path = os.path.join(camdir, "meta.json")
    if not os.path.exists(meta_path):
        print(f"[skip] {os.path.relpath(camdir, ASSETS)}: 无 meta.json")
        return False
    with open(meta_path) as f:
        meta = json.load(f)
    if "screen" in meta and not force:
        print(f"[skip] {os.path.relpath(camdir, ASSETS)}: 已有标注")
        return False
    img_path = None
    for ext in ("png", "jpg", "jpeg", "webp"):
        cand = os.path.join(camdir, f"back.{ext}")
        if os.path.exists(cand):
            img_path = cand
            break
    if img_path is None:
        print(f"[skip] {os.path.relpath(camdir, ASSETS)}: 无 back 图")
        return False

    small, inv = load_gray(img_path)
    rect = find_screen(small)
    if rect is None:
        print(f"[miss] {os.path.relpath(camdir, ASSETS)}: 未检测到屏幕，需手工标注")
        return False
    x1, y1, x2, y2 = rect
    X1, Y1, X2, Y2 = x1 * inv, y1 * inv, x2 * inv, y2 * inv

    img = Image.open(img_path)
    meta["width"], meta["height"] = img.size
    meta["image"] = os.path.basename(img_path)
    meta["screen"] = {"quad": [[X1, Y1], [X2, Y1], [X2, Y2], [X1, Y2]]}
    meta["screen"]["quad"] = [[round(a, 1), round(b, 1)] for a, b in meta["screen"]["quad"]]
    with open(meta_path, "w") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    dbg = img.convert("RGB")
    d = ImageDraw.Draw(dbg)
    d.polygon([(X1, Y1), (X2, Y1), (X2, Y2), (X1, Y2)],
              outline=(255, 0, 0), width=max(2, img.size[0] // 400))
    dbg.save(os.path.join(camdir, "screen_debug.png"))
    print(f"[ok] {os.path.relpath(camdir, ASSETS)}: {meta['screen']['quad']}")
    return True


def main():
    force = "--force" in sys.argv
    targets = [a for a in sys.argv[1:] if not a.startswith("--")]
    dirs = []
    if targets:
        dirs = [os.path.join(ASSETS, t) for t in targets]
    else:
        for brand in sorted(os.listdir(ASSETS)):
            bdir = os.path.join(ASSETS, brand)
            if not os.path.isdir(bdir):
                continue
            for slug in sorted(os.listdir(bdir)):
                dirs.append(os.path.join(bdir, slug))
    ok = sum(1 for d in dirs if os.path.isdir(d) and process(d, force))
    print(f"完成 {ok}/{len(dirs)}")


if __name__ == "__main__":
    main()
