# 相机资产清单与机身布局家族

同一机身设计的型号背面布局一致（屏幕位置相同），
同家族图片可复用屏幕标注；不同来源图片尺寸不同仍需逐张检测。

## Leica（EXIF Make: `LEICA CAMERA AG`）

| 家族 | 机型 | EXIF Model | 备注 |
|---|---|---|---|
| M9 系 | M9 / M9-P / M Monochrom(M9系) / M-E Typ220 | `LEICA M9` / `LEICA M9-P` / `LEICA M MONOCHROM` / `LEICA M-E` | 2.5–2.7" 230k · 4:3 |
| M240 系 | M Typ240 / M-P Typ240 / M Monochrom Typ246 / M Typ262 | `LEICA M (Typ 240)` / `LEICA M-P (Typ 240)` / `LEICA M MONOCHROM (Typ 246)` / `LEICA M (Typ 262)` | 3.0" 921k · 4:3 |
| M10 系 | M10 / M10-P / M10-R / M10 Monochrom | `LEICA M10` / `LEICA M10-P` / `LEICA M10-R` / `LEICA M10 Monochrom` | 3.0" 1.04M · 3:2 |
| M11 系 | M11 / M11-P / M11 Monochrom | `LEICA M11` / `LEICA M11-P` / `LEICA M11 Monochrom` | 3.0" 2.33M · 3:2 |
| 无屏（不支持） | M-D Typ262 / M10-D / M11-D | — | 无屏幕无法套壳，资产已移除 |

## Canon（EXIF Make: `Canon`）

| 机型 | EXIF Model | 屏幕 |
|---|---|---|
| EOS R5 Mark II | `Canon EOS R5m2` / `Canon EOS R5 Mark II` | 3.2" 2.1M · 3:2 |
| EOS R6 Mark III | `Canon EOS R6m3` | 3.0" 1.62M · 3:2 |
| EOS R8 Mark II | `Canon EOS R8m2` | 3.0" 1.62M · 3:2 |
| EOS R50 V | `Canon EOS R50 V` | 3.0" 1.62M · 3:2 |
| EOS R6 V | `Canon EOS R6 V` | 3.0" 1.62M · 3:2 |
| PowerShot G7 X Mark III | `Canon PowerShot G7 X Mark III` | 3.0" 1.04M · 3:2 |

注意：佳能 Mark 系列在 EXIF 里写成 `R5m2` / `R6m3`（不是 `Mark II`），
两套写法都在 `model_patterns` 里；一代 R5 / R6 不会被误吞。

## Fujifilm（EXIF Make: `FUJIFILM`）

| 机型 | EXIF Model | 屏幕 |
|---|---|---|
| X100VI | `X100VI` | 3.0" 1.62M · 3:2 |
| X-T5 | `X-T5` | 3.0" 1.84M · 3:2 |
| X-E5 | `X-E5` | 3.0" 1.84M · 3:2 |
| X-M5 | `X-M5` | 3.0" 1.04M · 3:2 |
| X-T30 III | `X-T30 III` | 3.0" 1.62M · 3:2 |
| X-half | `X-HF1` / `X half` | 3.0" 1.62M · **竖屏 3:4** |

X-half 是竖持机身，背图与屏幕都是竖的，标注比例是 0.75（3:4 旋转 90°）。

## Hasselblad（EXIF Make: `Hasselblad`）

| 机型 | EXIF Model |
|---|---|
| X2D 100C | `X2D 100C` |
| X2D II 100C | `X2D II 100C` |

## Ricoh（EXIF Make: `RICOH IMAGING COMPANY, LTD.`）

| 机型 | EXIF Model | 备注 |
|---|---|---|
| GR III | `RICOH GR III` | 注意匹配顺序：先 GR IIIx 再 GR III |
| GR IIIx | `RICOH GR IIIx` | 背面与 GR III 相同 |
| GR IV | `RICOH GR IV` | 新机身 |

## Sony（EXIF Make: `SONY`，Model 为内部编号）

| 家族 | 机型 | EXIF Model |
|---|---|---|
| A7M3 系 | A7 III / A7R III(A) | `ILCE-7M3` / `ILCE-7RM3(A)` |
| A9 系 | A9 / A9 II | `ILCE-9` / `ILCE-9M2` |
| A7R4 系 | A7R IV(A) | `ILCE-7RM4(A)` |
| 四代机身 | A7 IV / A7R V / A7S III / A1 | `ILCE-7M4` / `ILCE-7RM5` / `ILCE-7SM3` / `ILCE-1` |
| A9 III | A9 III | `ILCE-9M3` |
| A7C 系 | A7C / A7C II / A7CR | `ILCE-7C` / `ILCE-7CM2` / `ILCE-7CR` |
| ZV | ZV-E1 | `ZV-E1` |
| APS-C | A6600 / A6700 | `ILCE-6600` / `ILCE-6700` |

索尼注意：翻转屏打开的图不能用作「标准背面」，优先屏幕闭合图。

## 屏幕显示区比例（套壳基准）

`meta.json` 的 `screen.quad` 一律标注**显示区**（玻璃内侧真正成像的区域），
宽高比按官方屏幕规格校准：dots = 宽×高×3（RGB 子像素）。

| 比例 | 机型 |
|---|---|
| 4:3 | Leica M9 系 / M240 系（921k = 640×480×3）· Sony a1 / a7c / a7m3 / a7r3 / a7r4 / a7s3 / a9 / a9m2（1.44M = 800×600×3）· Hasselblad X2D 系（2.36M ≈ 1024×768×3） |
| 3:2 | Leica M10 系 / M11 系 · Ricoh GR 全系 · Sony a6700 / a7c2 / a7cr / a7m4 / a7r5 / a9m3 / zv-e1 · Canon 全系 · Fujifilm 全系（X-half 除外） |
| 16:9 | Sony a6600（APS-C 系 wide type TFT）。屏幕与原生照片比例不一致，`screen.fit = contain`，按真实相机留左右黑边 |
| 3:4（竖屏） | Fujifilm X-half |

匹配规则：每台资产都有显式 `exif.make_patterns` + `exif.model_patterns`（锚定正则），
不再依赖型号字符串的后缀猜测；`test/catalog_match_test.dart` 同时校验「现有资产都能匹配」
与「近型号不误配（R5 ≠ R5m2、X-T5 ≠ X-T50 等）」。

校验工具：`tools/measure_screens.py` 打印每台的「标注比例 / 实测比例 / 官方比例」对照。

## 屏幕开口（玻璃内沿）

`screen.quad` 是 3:2 显示区，产品图里玻璃通常还比它大一圈；那圈在成图里会露出
玻璃的反光，就是照片外面的一道灰边。需要消掉的机型在 `meta.json` 里再加一个
`screen.opening`（玻璃内沿四角），渲染时先把它整块压黑再贴照片。

- 检测/写入：`tools/annotate_opening.py`（不带参数只报告，`--write` 落盘）
- 渲染时四边取同一内缩（中位数），照片始终在屏幕正中；不会照抄检测出来的不等宽
- 已标注 29 台：Leica M10 系 / M-e、Canon 全系、Sony 主力、Fuji 多数。
- 没标的 19 台：机身本身是深灰（28~40）时，检测分不清「玻璃」和「机身」，
  写进去会把机身涂黑（x2d、m9-p 曾出现），所以留空；确认后
  `tools/annotate_opening.py --write <slug>` 单台补。
