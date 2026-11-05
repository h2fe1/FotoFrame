# 已支持机型清单

共 **26 台**相机。匹配规则见 `app/lib/core/catalog/matcher.dart`：
别名表（索尼 ILCE）→ 规范化精确 → 后缀 → 资产自带正则；
每台资产都带显式 `make_patterns` / `model_patterns`，`test/catalog_match_test.dart` 会验证
「现有资产都能被真实 EXIF 命中」且「已删除机型不再误配」。

| 机型 | EXIF Make / Model |
|---|---|
| Hasselblad X2D 100C | `Hasselblad` / `X2D 100C` |
| Hasselblad X2D II 100C | `Hasselblad` / `X2D II 100C` |
| Hasselblad CFV 100C | `Hasselblad` / `CFV 100C` |
| Leica D-LUX 8 | `LEICA CAMERA AG` / `LEICA D-LUX 8` |
| Leica Q2 | `LEICA CAMERA AG` / `LEICA Q2` |
| Leica Q3 | `LEICA CAMERA AG` / `LEICA Q3` |
| Leica SL2 | `LEICA CAMERA AG` / `LEICA SL2` |
| Leica SL3 | `LEICA CAMERA AG` / `LEICA SL3` |
| Panasonic S1 II | `Panasonic` / `DC-S1M2` |
| Panasonic S1R II | `Panasonic` / `DC-S1RM2` |
| Panasonic S9 | `Panasonic` / `DC-S9` |
| Ricoh GR II | `RICOH IMAGING COMPANY, LTD.` / `RICOH GR II` |
| Ricoh GR III | `RICOH IMAGING COMPANY, LTD.` / `RICOH GR III` |
| Ricoh GR IIIx | `RICOH IMAGING COMPANY, LTD.` / `RICOH GR IIIx` |
| Ricoh GR III HDF | `RICOH IMAGING COMPANY, LTD.` / `RICOH GR III HDF` |
| Ricoh GR IV | `RICOH IMAGING COMPANY, LTD.` / `RICOH GR IV` |
| Sony A1 II | `SONY` / `ILCE-1M2` |
| Sony A7C II | `SONY` / `ILCE-7CM2` |
| Sony A7 III | `SONY` / `ILCE-7M3` |
| Sony A7 IV | `SONY` / `ILCE-7M4` |
| Sony A7 V | `SONY` / `ILCE-7M5` |
| Sony A7R III | `SONY` / `ILCE-7RM3` |
| Sony A7R IV | `SONY` / `ILCE-7RM4` |
| Sony A7R V | `SONY` / `ILCE-7RM5` |
| Sony RX1R II | `SONY` / `DSC-RX1RM2` |
| Sony RX1R III | `SONY` / `DSC-RX1RM3` |

---

*数据来源：`app/assets/cameras/catalog.json`（由 `tools/sync_assets.sh` 从 `assets/cameras/` 生成）。机身布局家族与图片来源详见 [cameras.md](cameras.md)。*
