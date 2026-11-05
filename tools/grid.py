#!/usr/bin/env python3
"""给图片加坐标网格，方便人工读取屏幕四角像素坐标。

用法：tools/grid.py <图片路径> [间隔像素]
输出：同目录 <原名>_grid.jpg
"""
import sys

from PIL import Image, ImageDraw


def main():
    path = sys.argv[1]
    step = int(sys.argv[2]) if len(sys.argv) > 2 else 50
    img = Image.open(path).convert("RGB")
    w, h = img.size
    d = ImageDraw.Draw(img)
    for x in range(0, w, step):
        d.line([(x, 0), (x, h)], fill=(255, 0, 0), width=1)
        d.text((x + 2, 2), str(x), fill=(255, 0, 0))
        d.text((x + 2, h - 14), str(x), fill=(255, 0, 0))
    for y in range(0, h, step):
        d.line([(0, y), (w, y)], fill=(255, 0, 0), width=1)
        d.text((2, y + 2), str(y), fill=(255, 0, 0))
        d.text((w - 34, y + 2), str(y), fill=(255, 0, 0))
    out = path.rsplit(".", 1)[0] + "_grid.jpg"
    img.save(out, quality=90)
    print(out)


if __name__ == "__main__":
    main()
