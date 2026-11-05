#!/usr/bin/env python3
"""裁掉相机背面图四周的白边，让版面紧凑（参考样图相机占比大）。

- 白边判定：从四边向内扫描「接近纯白」的行/列
- 首次运行把原图备份为 back_untrimmed.<ext>
- 裁剪后同步更新 meta.json 的 width/height 和 screen.quad（平移坐标）
- 裁剪过度（比如把机身裁掉）会拒绝执行并告警

用法：tools/trim_assets.py [brand/slug ...] [--pad 20]
"""
import json
import os
import sys

from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets", "cameras")

WHITE = 245  # 判定白边的灰度阈值


def trim(img: Image.Image, pad: int):
    """返回 (裁剪后的图, dx, dy)。"""
    gray = img.convert("L")
    # 与全白图做差，得到非白内容的 bbox
    bg = Image.new("L", gray.size, 255)
    diff = ImageChops.difference(gray, bg)
    # 差值阈值化，去掉 JPEG 噪点
    mask = diff.point(lambda v: 255 if v > (255 - WHITE) else 0)
    bbox = mask.getbbox()
    if bbox is None:
        return None
    x1, y1, x2, y2 = bbox
    w, h = img.size
    x1 = max(0, x1 - pad)
    y1 = max(0, y1 - pad)
    x2 = min(w, x2 + pad)
    y2 = min(h, y2 + pad)
    # 拒绝裁掉超过 60% 面积（防止误判）
    if (x2 - x1) * (y2 - y1) < w * h * 0.25:
        return None
    return img.crop((x1, y1, x2, y2)), x1, y1


def process(camdir: str, pad: int) -> bool:
    meta_path = os.path.join(camdir, "meta.json")
    if not os.path.exists(meta_path):
        return False
    with open(meta_path) as f:
        meta = json.load(f)
    if meta.get("trimmed"):
        print(f"[skip] {os.path.relpath(camdir, ASSETS)}: 已裁剪过")
        return False
    img_name = meta.get("image", "back.jpg")
    img_path = os.path.join(camdir, img_name)
    if not os.path.exists(img_path):
        print(f"[skip] {os.path.relpath(camdir, ASSETS)}: 无图")
        return False

    img = Image.open(img_path).convert("RGB")
    result = trim(img, pad)
    if result is None:
        print(f"[miss] {os.path.relpath(camdir, ASSETS)}: 无法自动裁剪")
        return False
    cropped, dx, dy = result
    if cropped.size == img.size:
        print(f"[skip] {os.path.relpath(camdir, ASSETS)}: 无需裁剪")
        return False

    # 备份原图
    stem, ext = os.path.splitext(img_name)
    backup = os.path.join(camdir, f"{stem}_untrimmed{ext}")
    if not os.path.exists(backup):
        os.rename(img_path, backup)
    else:
        os.remove(img_path)
    cropped.save(img_path, quality=95)

    meta["width"], meta["height"] = cropped.size
    if "screen" in meta:
        meta["screen"]["quad"] = [
            [round(x - dx, 1), round(y - dy, 1)] for x, y in meta["screen"]["quad"]
        ]
    meta["trimmed"] = True
    with open(meta_path, "w") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    # 更新 debug 叠加图
    if "screen" in meta:
        from PIL import ImageDraw
        dbg = cropped.copy()
        d = ImageDraw.Draw(dbg)
        d.polygon([tuple(p) for p in meta["screen"]["quad"]],
                  outline=(255, 0, 0), width=max(2, cropped.size[0] // 400))
        dbg.save(os.path.join(camdir, "screen_debug.png"))

    print(f"[ok] {os.path.relpath(camdir, ASSETS)}: {img.size} -> {cropped.size}")
    return True


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    pad = 16
    for a in sys.argv[1:]:
        if a.startswith("--pad"):
            pad = int(a.split("=")[1]) if "=" in a else int(sys.argv[sys.argv.index(a) + 1])
    dirs = ([os.path.join(ASSETS, t) for t in args] if args else [
        os.path.join(ASSETS, b, s)
        for b in sorted(os.listdir(ASSETS))
        if os.path.isdir(os.path.join(ASSETS, b))
        for s in sorted(os.listdir(os.path.join(ASSETS, b)))
    ])
    ok = sum(1 for d in dirs if os.path.isdir(d) and process(d, pad))
    print(f"完成 {ok}/{len(dirs)}")


if __name__ == "__main__":
    main()
