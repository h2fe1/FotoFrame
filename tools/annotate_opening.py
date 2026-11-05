#!/usr/bin/env python3
"""检测每台相机「屏幕开口」（玻璃内沿），写入 meta.json 的 screen.opening。

为什么需要：产品图里玻璃通常比 3:2 显示区大一圈，合成时照片只铺显示区，
露出来的那圈玻璃反光就是照片外面那道「灰边」（M10 系最明显）。
渲染层会把 screen.opening 整块压黑，灰边就变成纯黑边框。

用法：
    tools/annotate_opening.py                    # 只报告
    tools/annotate_opening.py --write            # 写入 meta.json（覆盖已有 opening）
    tools/annotate_opening.py --write m10 m10-p  # 只处理指定 slug

算法：从显示区四条边的中点向两侧各打 7 条射线，找「玻璃 → 边框」的台阶：
亮度在 2px 内跌幅 > 10，且之后 5px 是一条低方差平台。取 7 条射线的中位数。
按台阶而不是按绝对黑（<20）来找，是因为不少机身本身是深灰（28~40），
绝对阈值会一路穿到机身上去（a7m4 曾因此测得 89px）。
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "assets", "cameras")

MAX_RATIO = 0.06  # 单边最多搜到图宽的 6%
CLAMP = 26        # 单边结果上限（再大基本都是误判）


def _profile(a, x0, y0, x1, y1, side, f, maxm):
    h, w = a.shape
    if side in ("L", "R"):
        y = int(round(y0 + (y1 - y0) * f))
        s = int(round(x0 if side == "L" else x1))
        d = -1 if side == "L" else 1
    else:
        x = int(round(x0 + (x1 - x0) * f))
        s = int(round(y0 if side == "T" else y1))
        d = -1 if side == "T" else 1
    prof = []
    for t in range(0, maxm + 1):
        xx = s + d * t if side in ("L", "R") else x
        yy = y if side in ("L", "R") else s + d * t
        if not (0 <= xx < w and 0 <= yy < h):
            return None
        prof.append(a[yy, xx])
    return prof


def _edge_from_profile(prof):
    """找一个「亮度台阶 + 平台」的位置，返回内缩像素数；找不到返回 0。"""
    if prof is None or len(prof) < 8:
        return 0
    p = np.array(prof)
    for t in range(1, len(p) - 6):
        drop = p[t - 1] - p[t + 2]
        if drop <= 10:
            continue
        tail = p[t + 2:t + 7]
        if tail.std() < 4 and tail.mean() < p[:max(1, t - 1)].mean() - 8:
            return t
    return 0


def edge_margin(a, x0, y0, x1, y1, side, maxm):
    vals = []
    for f in np.linspace(0.2, 0.8, 7):
        m = _edge_from_profile(_profile(a, x0, y0, x1, y1, side, f, maxm))
        if m > 0:
            vals.append(m)
    return min(int(np.median(vals)), CLAMP) if vals else 0


def detect(image_path, quad):
    a = np.asarray(Image.open(image_path).convert("L")).astype(float)
    xs = [p[0] for p in quad]
    ys = [p[1] for p in quad]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    maxm = max(8, int(a.shape[1] * MAX_RATIO))
    return tuple(edge_margin(a, x0, y0, x1, y1, s, maxm) for s in "LRTB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("slugs", nargs="*", help="只处理这些 slug（默认全部）")
    ap.add_argument("--write", action="store_true", help="写回 meta.json")
    ap.add_argument("--min", type=int, default=3, help="任一边 ≥ 该值才算有开口")
    args = ap.parse_args()

    touched = 0
    for brand in sorted(os.listdir(ASSETS)):
        bdir = os.path.join(ASSETS, brand)
        if not os.path.isdir(bdir):
            continue
        for slug in sorted(os.listdir(bdir)):
            cdir = os.path.join(bdir, slug)
            meta_path = os.path.join(cdir, "meta.json")
            if not os.path.exists(meta_path):
                continue
            if args.slugs and slug not in args.slugs:
                continue

            with open(meta_path) as f:
                meta = json.load(f)
            if "screen" not in meta:
                continue

            quad = meta["screen"]["quad"]
            L, R, T, B = detect(os.path.join(cdir, meta["image"]), quad)
            big = max(L, R, T, B) >= args.min
            print(f"{brand}/{slug:26s} L={L:3d} R={R:3d} T={T:3d} B={B:3d}"
                  f"  {'有开口' if big else '贴合'}")
            if not (args.write and big):
                continue

            xs = [p[0] for p in quad]
            ys = [p[1] for p in quad]
            x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
            meta["screen"]["opening"] = [
                [round(x0 - L, 1), round(y0 - T, 1)],
                [round(x1 + R, 1), round(y0 - T, 1)],
                [round(x1 + R, 1), round(y1 + B, 1)],
                [round(x0 - L, 1), round(y1 + B, 1)],
            ]
            with open(meta_path, "w") as f:
                json.dump(meta, f, ensure_ascii=False, indent=2)
                f.write("\n")
            touched += 1

    if args.write:
        print(f"\n写入 {touched} 台；接着跑 tools/sync_assets.sh 同步到 app 资产。")
    else:
        print("\n只报告，没写入；加 --write 落盘。")


if __name__ == "__main__":
    sys.exit(main())