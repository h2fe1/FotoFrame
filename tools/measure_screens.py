#!/usr/bin/env python3
"""精测相机背图的屏幕显示区边界，与 meta.json 现有标注对比。

方法：在现有标注框中心做水平/垂直亮度剖面。显示区（熄屏反光的深灰，
或亮屏内容）与屏幕边框（纯黑）之间存在亮度台阶；从中心向外找台阶，
即得显示区四边。对熄屏纯黑图（剖面无台阶）退化为原标注。

用法：
  tools/measure_screens.py            # 全部机型，输出表格
  tools/measure_screens.py --debug    # 另外生成每台剖面叠加图
"""
import json
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets", "cameras")

# 官方屏幕规格：dots → 分辨率 → 显示区宽高比。
# 相机行业惯例 dots = 宽×高×3（RGB 子像素）。
# 来源：各品牌官方规格页（见 docs/design/README.md 的屏幕一节）。
SPECS = {
    # ---- Leica ----
    "m9":              ("2.5\"", 230400,  320, 240, "4:3"),
    "m9-p":            ("2.5\"", 230400,  320, 240, "4:3"),
    "m9-monochrom":    ("2.5\"", 230400,  320, 240, "4:3"),
    "m-e-typ220":      ("2.7\"", 230400,  320, 240, "4:3"),
    "m-typ240":        ("3.0\"", 921600,  640, 480, "4:3"),
    "m-p-typ240":      ("3.0\"", 921600,  640, 480, "4:3"),
    "m-typ262":        ("3.0\"", 921600,  640, 480, "4:3"),
    "m-e-typ240":      ("3.0\"", 921600,  640, 480, "4:3"),
    "m-monochrom-typ246": ("3.0\"", 921600, 640, 480, "4:3"),
    "m10":             ("3.0\"", 1036800, 720, 480, "3:2"),
    "m10-p":           ("3.0\"", 1036800, 720, 480, "3:2"),
    "m10-r":           ("3.0\"", 1036800, 720, 480, "3:2"),
    "m10-monochrom":   ("3.0\"", 1036800, 720, 480, "3:2"),
    "m11":             ("3.0\"", 2332800, 1080, 720, "3:2"),
    "m11-p":           ("3.0\"", 2332800, 1080, 720, "3:2"),
    "m11-monochrom":   ("3.0\"", 2332800, 1080, 720, "3:2"),
    # ---- Hasselblad ----
    "x2d":             ("3.6\"", 2359296, 1024, 768, "4:3"),
    "x2d2":            ("3.6\"", 2359296, 1024, 768, "4:3"),
    # ---- Ricoh ----
    "gr3":             ("3.0\"", 1036800, 720, 480, "3:2"),
    "gr3x":            ("3.0\"", 1036800, 720, 480, "3:2"),
    "gr4":             ("3.0\"", 1036800, 720, 480, "3:2"),
    # ---- Sony ----
    "a6600":           ("3.0\"", 921600,  None, None, "16:9"),  # APS-C 系为 wide type TFT
    "a6700":           ("3.0\"", 1036800, 720, 480, "3:2"),
    "a1":              ("3.0\"", 1440000, 800, 600, "4:3"),
    "a7c":             ("3.0\"", 921600,  640, 480, "4:3"),
    "a7c2":            ("3.0\"", 1036800, 720, 480, "3:2"),
    "a7cr":            ("3.0\"", 1036800, 720, 480, "3:2"),
    "a7m3":            ("3.0\"", 921600,  640, 480, "4:3"),
    "a7m4":            ("3.0\"", 1036800, 720, 480, "3:2"),
    "a7r3":            ("3.0\"", 1440000, 800, 600, "4:3"),
    "a7r4":            ("3.0\"", 1440000, 800, 600, "4:3"),
    "a7r5":            ("3.2\"", 2095104, None, None, "3:2"),
    "a7s3":            ("3.0\"", 1440000, 800, 600, "4:3"),
    "a9":              ("3.0\"", 1440000, 800, 600, "4:3"),
    "a9m2":            ("3.0\"", 1440000, 800, 600, "4:3"),
    "a9m3":            ("3.2\"", 2095104, None, None, "3:2"),
    "zv-e1":           ("3.0\"", 1036800, 720, 480, "3:2"),
}
AR_OF = {"4:3": 4 / 3, "3:2": 3 / 2, "16:9": 16 / 9}


def profile_edges(px, w, h, cx, cy):
    """以 (cx,cy) 为中心扫水平/垂直剖面，返回显示区边缘估计。"""
    row = [px[x, cy] for x in range(w)]
    col = [px[cx, y] for y in range(h)]

    def edges(prof, lo, hi):
        # 中心附近取显示区基准亮度（中位数附近即可，用中心 11 点均值）
        mid = len(prof) // 2
        base = sum(prof[max(lo, mid - 5):mid + 6]) / 11
        # 从中心向两边找与 base 差异超过阈值的第一个点
        thr = max(6, abs(base) * 0.12 + 4)
        left = lo
        for x in range(mid, lo, -1):
            if abs(prof[x] - base) > thr:
                left = x + 1
                break
        right = hi
        for x in range(mid, hi):
            if abs(prof[x] - base) > thr:
                right = x
                break
        return left, right, base, thr

    x1, x2, br, tr = edges(row, 0, w)
    y1, y2, bc, tc = edges(col, 0, h)
    return x1, y1, x2, y2, br


def measure(camdir):
    meta_path = os.path.join(camdir, "meta.json")
    if not os.path.exists(meta_path):
        return None
    meta = json.load(open(meta_path))
    if "screen" not in meta:
        return None
    img_path = None
    for ext in ("png", "jpg", "jpeg", "webp"):
        c = os.path.join(camdir, f"back.{ext}")
        if os.path.exists(c):
            img_path = c
            break
    if img_path is None:
        return None

    q = meta["screen"]["quad"]
    xs = [p[0] for p in q]
    ys = [p[1] for p in q]
    mx1, my1, mx2, my2 = min(xs), min(ys), max(xs), max(ys)
    mw, mh = mx2 - mx1, my2 - my1

    g = Image.open(img_path).convert("L")
    W, H = g.size
    px = g.load()
    cx, cy = int((mx1 + mx2) / 2), int((my1 + my2) / 2)
    cx = min(max(cx, 0), W - 1)
    cy = min(max(cy, 0), H - 1)
    x1, y1, x2, y2, base = profile_edges(px, W, H, cx, cy)

    slug = meta["slug"]
    spec = SPECS.get(slug)
    ar_official = AR_OF[spec[4]] if spec else None
    return {
        "slug": slug, "brand": meta["brand"], "img": (W, H),
        "meta": (mx1, my1, mx2, my2, mw, mh, mw / mh),
        "meas": (x1, y1, x2, y2, x2 - x1, y2 - y1, (x2 - x1) / max(1, y2 - y1)),
        "base": base, "official": ar_official, "spec": spec,
    }


def main():
    debug = "--debug" in sys.argv
    rows = []
    for brand in sorted(os.listdir(ASSETS)):
        b = os.path.join(ASSETS, brand)
        if not os.path.isdir(b):
            continue
        for slug in sorted(os.listdir(b)):
            r = measure(os.path.join(b, slug))
            if r:
                rows.append(r)

    print(f"{'camera':28s} {'metaAR':>7s} {'measAR':>7s} {'offAR':>6s} {'Δmeta':>6s} {'Δmeas':>6s}  spec")
    for r in rows:
        mo = r["meta"][6]
        me = r["meas"][6]
        of = r["official"]
        dm = (mo / of - 1) * 100 if of else None
        de = (me / of - 1) * 100 if of else None
        spec = r["spec"]
        spec_s = f"{spec[0]} {spec[1]} {spec[4]}" if spec else "?"
        print(f"{r['brand']}/{r['slug']:<22s} {mo:7.3f} {me:7.3f} "
              f"{of if of else 0:6.3f} "
              f"{('%+5.1f%%' % dm) if dm is not None else '     -':>6s} "
              f"{('%+5.1f%%' % de) if de is not None else '     -':>6s}  {spec_s}")

    if debug:
        outdir = os.path.join(ROOT, ".work", "measure_debug")
        os.makedirs(outdir, exist_ok=True)
        for r in rows:
            brand_dir = os.path.join(ASSETS, r["brand"], r["slug"])
            img_path = None
            for ext in ("png", "jpg", "jpeg", "webp"):
                c = os.path.join(brand_dir, f"back.{ext}")
                if os.path.exists(c):
                    img_path = c
                    break
            if not img_path:
                continue
            img = Image.open(img_path).convert("RGB")
            d = ImageDraw.Draw(img)
            mx1, my1, mx2, my2, _, _, _ = r["meta"]
            x1, y1, x2, y2, _, _, _ = r["meas"]
            lw = max(2, img.size[0] // 500)
            d.rectangle([mx1, my1, mx2, my2], outline=(255, 0, 0), width=lw)
            d.rectangle([x1, y1, x2, y2], outline=(0, 160, 255), width=lw)
            img.save(os.path.join(outdir, f"{r['brand']}__{r['slug']}.png"))
        print(f"debug 图已写入 {outdir}（红=现有标注，蓝=实测）")


if __name__ == "__main__":
    main()
