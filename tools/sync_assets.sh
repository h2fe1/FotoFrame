#!/usr/bin/env bash
# 把仓库根 assets/cameras/<brand>/<slug>/ 下的资产打平同步到 app/assets/cameras/：
#   - 图片复制为 <brand>__<slug>.<ext>
#   - 汇总生成 catalog.json（含每台相机的 meta + 打平后的图片文件名）
# 用法：tools/sync_assets.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/assets/cameras"
DST="$ROOT/app/assets/cameras"

mkdir -p "$DST"
rm -f "$DST"/*.png "$DST"/*.jpg "$DST"/*.jpeg "$DST"/*.webp 2>/dev/null || true

python3 - "$SRC" "$DST" <<'PY'
import json, os, shutil, sys

src, dst = sys.argv[1], sys.argv[2]
catalog = []
for brand in sorted(os.listdir(src)):
    bdir = os.path.join(src, brand)
    if not os.path.isdir(bdir):
        continue
    for slug in sorted(os.listdir(bdir)):
        cdir = os.path.join(bdir, slug)
        meta_path = os.path.join(cdir, "meta.json")
        if not os.path.isdir(cdir) or not os.path.exists(meta_path):
            continue
        with open(meta_path) as f:
            meta = json.load(f)
        if "screen" not in meta:
            print(f"[skip] {brand}/{slug}: meta.json 缺少 screen 区域标注")
            continue
        img = None
        for ext in ("png", "jpg", "jpeg", "webp"):
            cand = os.path.join(cdir, f"back.{ext}")
            if os.path.exists(cand):
                img = cand
                break
        if img is None:
            print(f"[skip] {brand}/{slug}: 没有 back 图")
            continue
        flat = f"{brand}__{slug}{os.path.splitext(img)[1]}"
        shutil.copy2(img, os.path.join(dst, flat))
        meta["image"] = flat
        catalog.append(meta)
        print(f"[ok] {brand}/{slug} -> {flat}")

with open(os.path.join(dst, "catalog.json"), "w") as f:
    json.dump(catalog, f, ensure_ascii=False, indent=2)
print(f"catalog.json: {len(catalog)} 台相机")
PY
