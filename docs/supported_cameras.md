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

> Leica Q3 43 与 Q3 机身外观相同，不单独建资产：EXIF `LEICA Q3 43` 直接复用 Q3 的背面图和屏幕标定。

## Leica 徕卡（15 台）

EXIF Make：`LEICA CAMERA AG`

| 机型 | EXIF Model | 机身家族 |
|---|---|---|
| Leica M9 | `LEICA M9` | M9 系（2.5" 小屏） |
| Leica M9-P | `LEICA M9-P` | M9 系 |
| Leica M Monochrom | `LEICA M MONOCHROM` | M9 系 |
| Leica M-E (Typ 220) | `LEICA M-E` | M9 系 |
| Leica M (Typ 240) | `LEICA M (Typ 240)` | M240 系 |
| Leica M-P (Typ 240) | `LEICA M-P (Typ 240)` | M240 系 |
| Leica M Monochrom (Typ 246) | `LEICA M MONOCHROM (Typ 246)` | M240 系 |
| Leica M (Typ 262) | `LEICA M (Typ 262)` | M240 系 |
| Leica M10 | `LEICA M10` | M10 系 |
| Leica M10-P | `LEICA M10-P` | M10 系 |
| Leica M10-R | `LEICA M10-R` | M10 系 |
| Leica M10 Monochrom | `LEICA M10 Monochrom` | M10 系 |
| Leica M11 | `LEICA M11` | M11 系 |
| Leica M11-P | `LEICA M11-P` | M11 系 |
| Leica M11 Monochrom | `LEICA M11 Monochrom` | M11 系 |

> ⚠️ M-D (Typ 262) / M10-D / M11-D 无屏幕，无法套壳，不支持（资产已移除）。

## Canon 佳能（6 台）

EXIF Make：`Canon`。Mark 系列在 EXIF 里写作 `R5m2` / `R6m3`，两种写法都支持。

| 机型 | EXIF Model | 屏幕 |
|---|---|---|
| EOS R5 Mark II | `Canon EOS R5m2` / `Canon EOS R5 Mark II` | 3.2" 2.1M · 3:2 |
| EOS R6 Mark III | `Canon EOS R6m3` / `Canon EOS R6 Mark III` | 3.0" 1.62M · 3:2 |
| EOS R8 Mark II | `Canon EOS R8m2` / `Canon EOS R8 Mark II` | 3.0" 1.62M · 3:2 |
| EOS R50 V | `Canon EOS R50 V` | 3.0" 1.62M · 3:2 |
| EOS R6 V | `Canon EOS R6 V` | 3.0" 1.62M · 3:2 |
| PowerShot G7 X Mark III | `Canon PowerShot G7 X Mark III` | 3.0" 1.04M · 3:2 |

> 一代 R5 / R6、R6 Mark II、R50、R8 均不会误配到上表机型。

## Fujifilm 富士（6 台）

EXIF Make：`FUJIFILM`

| 机型 | EXIF Model | 屏幕 |
|---|---|---|
| X100VI | `X100VI` | 3.0" 1.62M · 3:2 |
| X-T5 | `X-T5` | 3.0" 1.84M · 3:2 |
| X-E5 | `X-E5` | 3.0" 1.84M · 3:2 |
| X-M5 | `X-M5` | 3.0" 1.04M · 3:2 |
| X-T30 III | `X-T30 III` | 3.0" 1.62M · 3:2 |
| X-half | `X-HF1` / `X half` | 3.0" 1.62M · 竖屏 3:4 |

> X-T50 / X-T30 II / X100V / X-E4 等未收录型号不会误配。

## Hasselblad 哈苏（2 台）

EXIF Make：`Hasselblad`

| 机型 | EXIF Model |
|---|---|
| X2D 100C | `X2D 100C` |
| X2D II 100C | `X2D II 100C` |

## Ricoh 理光（3 台）

EXIF Make：`RICOH IMAGING COMPANY, LTD.`

| 机型 | EXIF Model | 备注 |
|---|---|---|
| GR III | `RICOH GR III` | 锚定正则，GR IIIx 不会被误吞 |
| GR IIIx | `RICOH GR IIIx` | 背面与 GR III 相同 |
| GR IV | `RICOH GR IV` | 新机身 |

## Sony 索尼（16 台）

EXIF Make：`SONY`，Model 为内部编号，经别名表映射。

| 机型 | EXIF Model | 别名（同样支持） |
|---|---|---|
| A1 | `ILCE-1` | — |
| A9 | `ILCE-9` | — |
| A9 II | `ILCE-9M2` | — |
| A9 III | `ILCE-9M3` | — |
| A7 III | `ILCE-7M3` | — |
| A7R III | `ILCE-7RM3` | `ILCE-7RM3A` |
| A7R IV | `ILCE-7RM4` | `ILCE-7RM4A` |
| A7R V | `ILCE-7RM5` | — |
| A7 IV | `ILCE-7M4` | — |
| A7S III | `ILCE-7SM3` | — |
| A7C | `ILCE-7C` | — |
| A7C II | `ILCE-7CM2` | — |
| A7CR | `ILCE-7CR` | — |
| A6600 | `ILCE-6600` | — |
| A6700 | `ILCE-6700` | — |
| ZV-E1 | `ZV-E1` | — |

---

*数据来源：`app/assets/cameras/catalog.json`（由 `tools/sync_assets.sh` 从 `assets/cameras/` 生成）。机身布局家族与图片来源详见 [cameras.md](cameras.md)。*
