# 相机资产清单与机身布局家族

当前共 **26 台**相机资产，全部按实物素材逐一标定屏幕四角；
原始图为官方产品图抠透明、保留投影，裁剪白边后打包进 `app/assets/cameras/`。

## Hasselblad（EXIF Make: `Hasselblad`）

| 机型 | EXIF Model | slug |
|---|---|---|
| X2D 100C | `X2D 100C` | `x2d` |
| X2D II 100C | `X2D II 100C` | `x2d2` |
| CFV 100C | `CFV 100C` | `cfv100c` |

## Leica（EXIF Make: `LEICA CAMERA AG`）

| 机型 | EXIF Model | slug | 备注 |
|---|---|---|---|
| D-LUX 8 | `LEICA D-LUX 8` | `d-lux8` | |
| Q2 | `LEICA Q2` | `q2` | |
| Q3 | `LEICA Q3` | `q3` | Q3 43 外观相同，EXIF `LEICA Q3 43` 复用本资产 |
| SL2 | `LEICA SL2` | `sl2` | |
| SL3 | `LEICA SL3` | `sl3` | |

> ⚠️ M-D (Typ 262) / M10-D / M11-D 无屏幕，无法套壳，不支持。

## Panasonic（EXIF Make: `Panasonic`）

| 机型 | EXIF Model | slug |
|---|---|---|
| S1 II | `DC-S1M2` | `s1m2` |
| S1R II | `DC-S1RM2` | `s1r2` |
| S9 | `DC-S9` | `s9` |

## Ricoh（EXIF Make: `RICOH IMAGING COMPANY, LTD.`）

| 机型 | EXIF Model | slug | 备注 |
|---|---|---|---|
| GR II | `RICOH GR II` | `gr2` | |
| GR III | `RICOH GR III` | `gr3` | 锚定正则，不会误吞 GR IIIx |
| GR IIIx | `RICOH GR IIIx` | `gr3x` | |
| GR III HDF | `RICOH GR III HDF` | `gr3-hdf` | |
| GR IV | `RICOH GR IV` | `gr4` | |

## Sony（EXIF Make: `SONY`，Model 为内部编号，经别名表映射）

| 机型 | EXIF Model | slug |
|---|---|---|
| A1 II | `ILCE-1M2` | `a1m2` |
| A7C II | `ILCE-7CM2` | `a7c2` |
| A7 III | `ILCE-7M3` | `a7m3` |
| A7 IV | `ILCE-7M4` | `a7m4` |
| A7 V | `ILCE-7M5` | `a7m5` |
| A7R III | `ILCE-7RM3` | `a7r3` |
| A7R IV | `ILCE-7RM4` | `a7r4` |
| A7R V | `ILCE-7RM5` | `a7r5` |
| RX1R II | `DSC-RX1RM2` | `rx1r2` |
| RX1R III | `DSC-RX1RM3` | `rx1r3` |

索尼注意：翻转屏打开的图不能用作「标准背面」，优先屏幕闭合图。

## 屏幕显示区比例（套壳基准）

`meta.json` 的 `screen.quad` 一律标注**显示区**（玻璃内侧真正成像的区域），
宽高比按官方屏幕规格校准：dots = 宽×高×3（RGB 子像素）。

| 实测标注比例 | 机型 |
|---|---|
| ≈ 4:3（1.32–1.35） | Hasselblad 全系 · Ricoh GR II · Sony A7 III / A7R III / A7R IV / RX1R II / RX1R III |
| ≈ 3:2（1.44–1.50） | Leica 全系 · Panasonic 全系 · Sony A1 II / A7C II / A7 IV / A7 V / A7R V |
| ≈ 1.56–1.58 | Ricoh GR III / GR IIIx / GR III HDF / GR IV（按实机屏幕标注） |

匹配规则：每台资产都有显式 `exif.make_patterns` + `exif.model_patterns`（锚定正则），
不依赖型号字符串的后缀猜测；`test/catalog_match_test.dart` 同时校验「现有资产都能匹配」
与「已删除机型不再误配」。

校验工具：`tools/measure_screens.py` 打印每台的「标注比例 / 实测比例 / 官方比例」对照。

## 屏幕开口（玻璃内沿）

`screen.quad` 是显示区，产品图里玻璃通常还比它大一圈；那圈在成图里会露出
玻璃的反光，就是照片外面的一道灰边。需要消掉的机型可在 `meta.json` 里再加一个
`screen.opening`（玻璃内沿四角），渲染时先把它整块压黑再贴照片。

- 检测/写入：`tools/annotate_opening.py`（不带参数只报告，`--write` 落盘）
- 渲染时四边取同一内缩（中位数），照片始终在屏幕正中；不会照抄检测出来的不等宽
- 当前 26 台资产均未标注 `screen.opening`；机身本身是深灰时检测分不清「玻璃」和「机身」，
  写进去会把机身涂黑，确认后可用 `tools/annotate_opening.py --write <slug>` 单台补
