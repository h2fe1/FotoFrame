#!/usr/bin/env python3
"""生成带 EXIF 的测试照片，用于端到端验证。

用法：tools/make_samples.py
输出：samples/<brand>_<model>.jpg
      samples/canon_r5m2.png   （PNG eXIf chunk 夹具）
      samples/fujifilm_xt5.heic（HEIC Exif item 夹具，依赖 macOS sips）
"""
import os
import struct

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "samples")

SAMPLES = [
    ("LEICA CAMERA AG", "LEICA M11", "leica_m11"),
    ("LEICA CAMERA AG", "LEICA M10-P", "leica_m10p"),
    ("Hasselblad", "X2D 100C", "hasselblad_x2d"),
    ("Hasselblad", "X2D II 100C", "hasselblad_x2d2"),
    ("RICOH IMAGING COMPANY, LTD.", "RICOH GR III", "ricoh_gr3"),
    ("RICOH IMAGING COMPANY, LTD.", "RICOH GR IIIx", "ricoh_gr3x"),
    ("RICOH IMAGING COMPANY, LTD.", "RICOH GR IV", "ricoh_gr4"),
    ("SONY", "ILCE-7M4", "sony_a7m4"),
    ("SONY", "ILCE-7RM5", "sony_a7r5"),
    ("SONY", "ILCE-1", "sony_a1"),
    # 佳能：EXIF Model 用厂商的真实写法（Mark II → m2）
    ("Canon", "Canon EOS R5m2", "canon_r5m2"),
    ("Canon", "Canon EOS R6m3", "canon_r6m3"),
    ("Canon", "Canon PowerShot G7 X Mark III", "canon_g7x3"),
    # 富士
    ("FUJIFILM", "X-T5", "fujifilm_xt5"),
    ("FUJIFILM", "X100VI", "fujifilm_x100vi"),
    ("FUJIFILM", "X-HF1", "fujifilm_xhalf"),
]


def build_tiff(make: str, model: str) -> bytes:
    """构造小端 TIFF（不含 'Exif\\0\\0' 前缀，PNG/HEIC 用）。"""
    entries = []
    data = b""

    def add(tag: int, value: str):
        nonlocal data
        b = value.encode() + b"\x00"
        entries.append((tag, 2, len(b), len(data)))
        data += b

    add(0x010F, make)
    add(0x0110, model)

    ifd_offset = 8
    count = len(entries)
    data_start = ifd_offset + 2 + 12 * count + 4

    tiff = bytearray()
    tiff += b"II*\x00"
    tiff += struct.pack("<I", ifd_offset)
    tiff += struct.pack("<H", count)
    for tag, typ, num, off in entries:
        tiff += struct.pack("<HHI", tag, typ, num)
        tiff += struct.pack("<I", data_start + off)
    tiff += struct.pack("<I", 0)  # next IFD
    tiff += data
    return bytes(tiff)


def build_exif(make: str, model: str) -> bytes:
    """JPEG APP1 用：'Exif\\0\\0' + TIFF。"""
    return b"Exif\x00\x00" + build_tiff(make, model)


def make_photo(w: int, h: int, label: str) -> Image.Image:
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    # 彩色渐变 + 斜线，方便检查透视映射是否正确
    for y in range(h):
        d.line([(0, y), (w, y)], fill=(int(255 * y / h), 120, 255 - int(255 * y / h)))
    for i in range(0, w, 200):
        d.line([(i, 0), (i + h // 2, h)], fill=(255, 255, 255), width=4)
    d.rectangle([40, 40, w - 40, h - 40], outline=(0, 0, 0), width=8)
    d.text((80, 80), label, fill=(0, 0, 0))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for make, model, name in SAMPLES:
        # 3:2 屏幕的机身用 3:2 样片，避免预览里被裁掉太多
        three_two = name.startswith(("canon_", "fujifilm_"))
        w, h = (1800, 1200) if three_two else (1600, 1200)
        img = make_photo(w, h, f"{make} | {model}")
        img.save(
            os.path.join(OUT, f"{name}.jpg"),
            "JPEG",
            quality=92,
            exif=build_exif(make, model),
        )
        print(f"[ok] {name}.jpg  make={make!r} model={model!r}")

    # ---- PNG（eXIf chunk）----
    png_make, png_model = "RICOH IMAGING COMPANY, LTD.", "RICOH GR III"
    img = make_photo(1600, 1200, f"{png_make} | {png_model}")
    png_path = os.path.join(OUT, "ricoh_gr3.png")
    img.save(png_path, "PNG", exif=build_tiff(png_make, png_model))
    print(f"[ok] ricoh_gr3.png  make={png_make!r} model={png_model!r}")

    # ---- HEIC（Exif item）：先用 JPEG 打底，再用 macOS sips 转 HEIC ----
    heic_make, heic_model = "Hasselblad", "X2D 100C"
    tmp_jpg = os.path.join(OUT, "_tmp_x2d.jpg")
    img2 = make_photo(1600, 1200, f"{heic_make} | {heic_model}")
    img2.save(tmp_jpg, "JPEG", quality=92, exif=build_exif(heic_make, heic_model))
    heic_path = os.path.join(OUT, "hasselblad_x2d.heic")
    rc = os.system(f'sips -s format heic "{tmp_jpg}" --out "{heic_path}" >/dev/null 2>&1')
    os.remove(tmp_jpg)
    if rc == 0 and os.path.exists(heic_path):
        print(f"[ok] hasselblad_x2d.heic  make={heic_make!r} model={heic_model!r}")
    else:
        print("[warn] sips 不可用，跳过 HEIC 样片")

    # ---- 再来一份 Apple 原生编码的 HEIC（Leica M11），校验 iloc=idat 分支 ----
    leica_heic = os.path.join(OUT, "leica_m11.heic")
    rc = os.system(
        f'sips -s format heic "{os.path.join(OUT, "leica_m11.jpg")}" '
        f'--out "{leica_heic}" >/dev/null 2>&1')
    if rc == 0 and os.path.exists(leica_heic):
        print("[ok] leica_m11.heic  (Apple ImageIO 编码)")


if __name__ == "__main__":
    main()
