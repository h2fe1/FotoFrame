#!/usr/bin/env python3
"""裁掉相机背面图四周的空白，让版面紧凑并让机身真正居中。

- 内容判定：有透明底时按 alpha，白底图按「与纯白的差值」
- 抗噪：按行/列占用率（≥1%，至少 8 像素）判定内容边界，
  远处一两个孤立的噪点不会再被当成机身，把整张图「焊」在原地
- 首次运行把原图备份为 back_untrimmed.<ext>
- 裁剪后同步更新 meta.json 的 width/height、screen.quad 与 screen.opening
- 裁剪过度（比如把机身裁掉）会拒绝执行并告警

用法：
  tools/trim_assets.py [brand/slug ...] [--pad 20] [--force]
  tools/trim_assets.py --audit          # 只体检：打印每台机身的居中偏移，不改文件
"""
import json
import os
import sys

from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets", "cameras")

WHITE = 245  # 白底图的「白」阈值（灰度）
ALPHA = 16   # 透明底图的「有内容」阈值（低于此的淡投影尾迹忽略）
OCCUPANCY = 0.01  # 一行/一列至少 1% 像素有内容，才算机身，忽略孤立噪点
MIN_OCCUPANCY = 8  # 小图兜底：至少 8 个像素


def content_mask(img: Image.Image) -> Image.Image:
    """返回内容掩码（L 模式，255 = 有内容）。"""
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    alpha = img.getchannel("A")
    if alpha.getextrema()[0] < 250:
        # 透明底：alpha 就是内容
        return alpha.point(lambda v: 255 if v > ALPHA else 0)
    # 白底：与纯白做差
    gray = img.convert("L")
    bg = Image.new("L", gray.size, 255)
    diff = ImageChops.difference(gray, bg)
    return diff.point(lambda v: 255 if v > (255 - WHITE) else 0)


def _occupied_flags(mask: Image.Image, minimum: int) -> list:
    """逐行判断该行是否有 >= minimum 个内容像素。"""
    w, h = mask.size
    data = mask.tobytes()
    return [data[y * w:(y + 1) * w].count(255) >= minimum for y in range(h)]


def content_bbox(img: Image.Image):
    """内容包围盒 (x1, y1, x2, y2)；忽略稀疏噪点，无内容返回 None。"""
    mask = content_mask(img)
    w, h = mask.size
    rows = _occupied_flags(mask, max(MIN_OCCUPANCY, int(w * OCCUPANCY)))
    cols = _occupied_flags(mask.transpose(Image.Transpose.TRANSPOSE),
                           max(MIN_OCCUPANCY, int(h * OCCUPANCY)))
    ys = [i for i, filled in enumerate(rows) if filled]
    xs = [i for i, filled in enumerate(cols) if filled]
    if not ys or not xs:
        return None
    return xs[0], ys[0], xs[-1] + 1, ys[-1] + 1


def trim(img: Image.Image, pad: int):
    """返回 (裁剪矩形, 内容 bbox)；内容为空返回 (None, None)。"""
    bbox = content_bbox(img)
    if bbox is None:
        return None, None
    w, h = img.size
    x1 = max(0, bbox[0] - pad)
    y1 = max(0, bbox[1] - pad)
    x2 = min(w, bbox[2] + pad)
    y2 = min(h, bbox[3] + pad)
    # 拒绝裁掉超过 75% 面积（防止误判）
    if (x2 - x1) * (y2 - y1) < w * h * 0.25:
        return None, bbox
    return (x1, y1, x2, y2), bbox


def _center_offset(bbox, size):
    """内容中心相对整图中心的偏移（像素）。"""
    w, h = size
    return ((bbox[0] + bbox[2]) / 2 - w / 2,
            (bbox[1] + bbox[3]) / 2 - h / 2)


def process(camdir: str, pad: int, force: bool, audit: bool) -> bool:
    meta_path = os.path.join(camdir, "meta.json")
    if not os.path.exists(meta_path):
        return False
    with open(meta_path) as f:
        meta = json.load(f)
    rel = os.path.relpath(camdir, ASSETS)
    if meta.get("trimmed") and not force and not audit:
        print(f"[skip] {rel}: 已裁剪过（--force 可重裁）")
        return False
    img_name = meta.get("image", "back.jpg")
    img_path = os.path.join(camdir, img_name)
    if not os.path.exists(img_path):
        print(f"[skip] {rel}: 无图")
        return False

    img = Image.open(img_path)
    if img.mode not in ("RGB", "RGBA"):
        img = img.convert("RGBA")
    rect, bbox = trim(img, pad)
    if rect is None:
        print(f"[miss] {rel}: 无法自动裁剪")
        return False
    x1, y1, x2, y2 = rect
    off = _center_offset(bbox, img.size)
    if audit:
        print(f"[audit] {rel}: {img.size[0]}x{img.size[1]} "
              f"内容 bbox={bbox} 居中偏移=({off[0]:+.0f}, {off[1]:+.0f})")
        return False
    if (x2 - x1, y2 - y1) == img.size:
        print(f"[skip] {rel}: 无需裁剪")
        return False

    cropped = img.crop(rect)
    if cropped.mode == "RGBA":
        cropped.save(img_path)  # PNG 保留透明与投影
    else:
        cropped.save(img_path, quality=95)

    # 备份原图（同一目录，sync_assets.sh 只认 back.<ext>，不会打包备份）
    stem, ext = os.path.splitext(img_name)
    backup = os.path.join(camdir, f"{stem}_untrimmed{ext}")
    if not os.path.exists(backup):
        img.save(backup)

    meta["width"], meta["height"] = cropped.size
    for key in ("quad", "opening"):
        quad = meta.get("screen", {}).get(key)
        if quad:
            meta["screen"][key] = [
                [round(x - x1, 1), round(y - y1, 1)] for x, y in quad
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

    new_bbox = content_bbox(cropped)
    new_off = _center_offset(new_bbox, cropped.size) if new_bbox else (0, 0)
    print(f"[ok] {rel}: {img.size} -> {cropped.size} "
          f"居中偏移 ({off[0]:+.0f}, {off[1]:+.0f}) -> "
          f"({new_off[0]:+.0f}, {new_off[1]:+.0f})")
    return True


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    pad = 16
    for a in flags:
        if a.startswith("--pad"):
            pad = int(a.split("=")[1]) if "=" in a else int(
                sys.argv[sys.argv.index(a) + 1])
    force = "--force" in flags
    audit = "--audit" in flags
    dirs = ([os.path.join(ASSETS, t) for t in args] if args else [
        os.path.join(ASSETS, b, s)
        for b in sorted(os.listdir(ASSETS))
        if os.path.isdir(os.path.join(ASSETS, b))
        for s in sorted(os.listdir(os.path.join(ASSETS, b)))
    ])
    ok = sum(1 for d in dirs if os.path.isdir(d) and process(d, pad, force, audit))
    if not audit:
        print(f"完成 {ok}/{len(dirs)}")


if __name__ == "__main__":
    main()