#!/usr/bin/env python3
"""Write the chosen "Fo" master into the macOS AppIcon set.

Default: variant A (paper bg, graphite letters).
Override: python3 install.py B   (A/B/C/D/E)
"""
import os
import shutil
import sys

from PIL import Image

import fo as F

SET = "/Users/fei/Coding/FotoFrame/app/macos/Runner/Assets.xcassets/AppIcon.appiconset"
BACKUP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_appicon_backup")
SRC = os.path.dirname(os.path.abspath(__file__))

# filename → (size, master)  master "big" = normal cut, "small" = optical cut
MAP = {
    "app_icon_1024.png": (1024, "big"),
    "app_icon_512.png": (512, "big"),
    "app_icon_256.png": (256, "big"),
    "app_icon_128.png": (128, "big"),
    "app_icon_64.png": (64, "small"),
    "app_icon_32.png": (32, "small"),
    "app_icon_16.png": (16, "small"),
}

VARIANTS = {k: (name, kw) for k, name, kw in F.VARIANTS}


def main():
    key = (sys.argv[1] if len(sys.argv) > 1 else "A").upper()
    if key not in VARIANTS:
        sys.exit(f"unknown variant {key}; choose from {sorted(VARIANTS)}")
    kw = VARIANTS[key][1]

    if not os.path.isdir(BACKUP):
        shutil.copytree(SET, BACKUP)
        print("backed up originals →", BACKUP)

    big = F.fo(os.path.join(SRC, f"_master_{key}_1024.png"), g=F.NORMAL, **kw)
    small = F.fo(os.path.join(SRC, f"_master_{key}_optical.png"), g=F.OPTICAL, **kw)

    for name, (size, master) in sorted(MAP.items(), key=lambda kv: kv[1][0]):
        src = big if master == "big" else small
        im = src.resize((size, size), Image.LANCZOS)
        im.save(os.path.join(SET, name))
        print(f"✓ {name}  {size}×{size}  ({master})")

    print(f"\ninstalled variant {key} ({VARIANTS[key][1]})")


if __name__ == "__main__":
    main()