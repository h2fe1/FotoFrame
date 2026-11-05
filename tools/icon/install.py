#!/usr/bin/env python3
"""Write the "Fo" icon into the macOS app.

Two things are produced:

1. `AppIcon.appiconset` — the bundle icon (.icns), light variant A.
   Used by Finder, and by the Dock while the app isn't running.
2. `IconLight.imageset` / `IconDark.imageset` — the two 1024 masters that
   `AppDelegate.swift` swaps into `NSApp.applicationIconImage` when the system
   switches between light and dark appearance.

    python3 install.py            # light = A (paper), dark = B (graphite)
    python3 install.py C D        # any pair from A/B/C/D/E
"""
import json
import os
import shutil
import sys

from PIL import Image

import fo as F

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
XCASSETS = os.path.join(ROOT, "app/macos/Runner/Assets.xcassets")
SET = os.path.join(XCASSETS, "AppIcon.appiconset")
BACKUP = os.path.join(ROOT, ".work/icon_concepts/_appicon_backup")
SRC = os.path.join(ROOT, ".work/icon_concepts/_masters")
os.makedirs(SRC, exist_ok=True)

SIZES = [
    (16, "app_icon_16.png", "small"),
    (32, "app_icon_32.png", "small"),
    (64, "app_icon_64.png", "small"),
    (128, "app_icon_128.png", "big"),
    (256, "app_icon_256.png", "big"),
    (512, "app_icon_512.png", "big"),
    (1024, "app_icon_1024.png", "big"),
]

SLOTS = [
    ("16x16", "1x", "app_icon_16.png"),
    ("16x16", "2x", "app_icon_32.png"),
    ("32x32", "1x", "app_icon_32.png"),
    ("32x32", "2x", "app_icon_64.png"),
    ("128x128", "1x", "app_icon_128.png"),
    ("128x128", "2x", "app_icon_256.png"),
    ("256x256", "1x", "app_icon_256.png"),
    ("256x256", "2x", "app_icon_512.png"),
    ("512x512", "1x", "app_icon_512.png"),
    ("512x512", "2x", "app_icon_1024.png"),
]

VARIANTS = {k: (name, kw) for k, name, kw in F.VARIANTS}


def masters(key):
    _name, kw = VARIANTS[key]
    out = {}
    for cut in ("big", "small"):
        out[cut] = F.fo(
            os.path.join(SRC, f"_master_{key}_{cut}.png"),
            g=F.NORMAL if cut == "big" else F.OPTICAL,
            **kw,
        )
    return out


def write_imageset(name, icon):
    d = os.path.join(XCASSETS, f"{name}.imageset")
    os.makedirs(d, exist_ok=True)
    for scale, size in (("1x", 512), ("2x", 1024)):
        icon.resize((size, size), Image.LANCZOS).save(
            os.path.join(d, f"{name.lower()}_{size}.png")
        )
    manifest = {
        "images": [
            {"filename": f"{name.lower()}_512.png", "idiom": "universal", "scale": "1x"},
            {"filename": f"{name.lower()}_1024.png", "idiom": "universal", "scale": "2x"},
        ],
        "info": {"author": "xcode", "version": 1},
    }
    with open(os.path.join(d, "Contents.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")


def main():
    keys = sys.argv[1:] or ["A", "B"]
    if len(keys) != 2 or any(k.upper() not in VARIANTS for k in keys):
        sys.exit("usage: install.py [light] [dark]   e.g. install.py A B")
    light_key, dark_key = keys[0].upper(), keys[1].upper()

    if not os.path.isdir(BACKUP):
        shutil.copytree(SET, BACKUP)
        print("backed up originals →", BACKUP)

    light, dark = masters(light_key), masters(dark_key)

    # 1. bundle icon (light only — macOS has no dark slot for .icns)
    for name in os.listdir(SET):
        if name.startswith("app_icon_dark_"):
            os.remove(os.path.join(SET, name))
    for size, name, cut in SIZES:
        light[cut].resize((size, size), Image.LANCZOS).save(os.path.join(SET, name))

    images = [
        {"filename": fn, "idiom": "mac", "scale": scale, "size": size}
        for size, scale, fn in SLOTS
    ]
    with open(os.path.join(SET, "Contents.json"), "w") as f:
        json.dump({"images": images, "info": {"author": "xcode", "version": 1}},
                  f, indent=2)
        f.write("\n")
    print(f"✓ AppIcon.appiconset  7 尺寸（{light_key}）")

    # 2. runtime appearance pair
    write_imageset("IconLight", light["big"])
    write_imageset("IconDark", dark["big"])
    print("✓ IconLight.imageset / IconDark.imageset  512 + 1024")

    print(f"\nlight = {light_key} ({VARIANTS[light_key][0]})")
    print(f"dark  = {dark_key} ({VARIANTS[dark_key][0]})")


if __name__ == "__main__":
    main()