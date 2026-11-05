# FotoFrame 架构设计

## 目标

导入照片 → 读取 EXIF 自动识别相机型号 → 把照片合成到对应相机背面图的屏幕区域
→ 下方排布原始照片 → 导出成图（样式参考用户提供的两张样图）。

首发 macOS，后续全平台（Windows / Android / iOS）。

## 技术选型

Flutter 单代码库覆盖 macOS / Windows / Android / iOS。
为保证可移植性，代码分三层：

```
app/
├── lib/
│   ├── core/            # 纯 Dart，零 Flutter 依赖 —— 全平台复用的核心
│   │   ├── exif/        # EXIF 解析（JPEG / TIFF / PNG / HEIC）
│   │   ├── catalog/     # 相机资产目录 + EXIF→机型匹配
│   │   └── compose/     # 版面几何计算（屏幕四边形映射、版式布局）
│   ├── render/          # Flutter Canvas 渲染层（薄）
│   ├── platform/        # 平台能力抽象（文件选择、保存、沙盒路径）
│   └── ui/              # macOS 界面
├── assets/              # 相机资产在仓库根 assets/，构建时打包
└── test/
assets/cameras/<brand>/<slug>/
    ├── back.png         # 抠好白底/透明的相机背面图
    └── meta.json        # 资产描述（含屏幕四边形坐标，见下）
tools/                   # 资产处理脚本（裁剪、标注辅助）
docs/                    # 设计文档
```

**复用原则**：`lib/core` 不 import 任何 `package:flutter/*`，
输入输出都是字节、几何数据和不可变模型；渲染与平台 IO 只放在 `render/` 与 `platform/`。
这样未来即使换壳（如 CLI、Web），核心逻辑也原样可用。

## 相机资产 meta.json 规范

```jsonc
{
  "brand": "Leica",
  "model": "Leica M11",
  "slug": "m11",
  "image": "back.png",
  "width": 2000,              // back.png 像素宽
  "height": 1300,             // back.png 像素高
  "screen": {                 // 屏幕显示区域（像素坐标，顺时针从左上角开始）
    "quad": [[x1,y1],[x2,y2],[x3,y3],[x4,y4]],
    "opening": [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]  // 可选：玻璃内沿（见下）
  },
  "exif": {                   // 用于匹配的 EXIF 特征
    "make_patterns": ["^LEICA"],
    "model_patterns": ["^LEICA M11$", "^M11"]
  },
  "source_url": "...",        // 资产来源（版权追溯）
  "source_page": "..."
}
```

屏幕区域用四边形而非矩形：多数相机背面图带轻微透视，四点投影映射更准。

## EXIF 容器支持

`lib/core/exif/exif_reader.dart` 纯 Dart 实现，四种容器：

| 容器 | EXIF 位置 | 备注 |
|---|---|---|
| JPEG | APP1 段 `Exif\0\0` + TIFF | 相机直出 |
| TIFF / DNG | 文件头即 TIFF | 徕卡/索尼 RAW |
| PNG | `eXIf` chunk（规范 1.5 扩展），内容为裸 TIFF | Lightroom 等导出 |
| HEIC / HEIF | `meta` → `iinf` 找 `item_type='Exif'` → `iloc` 定位（含 `idat` 分支）| iPhone 默认格式 |

HEIC 的 Exif item 前 4 字节是到 TIFF 头的偏移，不同写入器基准不一致，
解析器用「候选偏移 + 头部特征校验」兜底，不依赖固定值。

文件**合法但不含 EXIF**（截图、部分导出的 PNG）抛 `ExifMissingException`，
与「格式损坏」的 `FormatException` 区分开：前者界面提示「无 EXIF 机型信息」，
后者才标为读取失败。

## EXIF → 机型匹配

1. 读取 `Make` / `Model`（必要时参考 `LensModel`、`Software`）。
2. 规范化（去多余空格、统一大小写）。
3. 按 catalog 中各机型 `exif` 规则匹配；精确规则优先于前缀规则。
4. 匹配不到时：不套壳，界面提示该 EXIF 机型暂不支持（不提供手动选择）。

优先级：别名表（索尼 ILCE）→ 规范化型号精确匹配 → 型号后缀匹配 → 资产自带正则。
每台资产**都**带显式 `make_patterns` / `model_patterns`（锚定正则），
后缀匹配只作为兜底；`test/catalog_match_test.dart` 会验证每台都能被真实 EXIF 命中，
同时断言近型号（R5 vs R5m2、X-T5 vs X-T50、X-T30 II vs III）不会误配。

已知 EXIF 映射要点：

| 品牌 | EXIF Make | EXIF Model 规律 |
|---|---|---|
| Leica | `LEICA CAMERA AG` | `LEICA M11`、`LEICA M10-P`、`LEICA M (Typ 240)` 等，与显示名基本一致 |
| Hasselblad | `Hasselblad` | `X2D 100C`、`X2D II 100C` |
| Ricoh | `RICOH IMAGING COMPANY, LTD.` | `RICOH GR III`、`RICOH GR IIIx`、`RICOH GR IV` |
| Sony | `SONY` | `ILCE-7M4` 等内部型号，需要别名表（ILCE-7M4→A7 IV，见 catalog 数据） |

## 输出版式（参考样图）

```
┌─────────────────────────┐
│   (留白)                 │
│   ┌───────────────┐     │   顶部：相机背面图（缩放适配，四周留白）
│   │ 相机+屏幕内照片 │     │   照片按屏幕四边形透视映射，
│   └───────────────┘     │   保持照片宽高比，居中 letterbox
│                         │
│      原始照片（出血）     │   底部：原图完整显示，
│                         │   左右下三边贴边不留白
└─────────────────────────┘
```

- 背景：固定相纸灰 `#E9E9E6`（中性偏暖，界面不提供选择）。相机图用 multiply
  融合白底，因此底色必须是浅色中性色。
- 输出尺寸：默认长边 2400px，可选。
- 画布不显示机型名称（标题开关已移除）。
- 画布宽度由底部照片决定（照片宽 + 左右留白，默认留白 0 即出血贴边）。
- 相机图按宽度等比缩放，四周留等宽白边（标准版式 10% 画布宽，三档松紧可调，
  见 `FrameTheme.cameraMarginRatio`）；照片紧接相机下方的留白，四边边框保持一致。
- 屏幕内照片默认取 **cover**（`LayoutEngine.screenMapping`）：保持比例、铺满显示区、裁掉多余部分。
- 单台相机可把 `screen.fit` 设为 `contain`，改为「完整显示、比例不符处留黑边」——
  这是真实相机回放的样子。目前只有 Sony a6600 用它（APS-C 系 16:9 宽屏，回放 3:2 照片本就左右留黑边）。
- `screen.quad` 标注的是**显示区**（玻璃内侧成像区），比例按官方屏幕规格校准，
  见 `docs/cameras.md`；校验工具 `tools/measure_screens.py`。
- `screen.opening`（可选）标注**屏幕开口 = 玻璃内沿**。产品图里玻璃常比 3:2 显示区
  大一圈（M10 系约 8–18px），照片只铺显示区，露出来的玻璃反光就成了照片外面那道
  「灰边」。标注了 opening 的机型，渲染时会先把开口整块压黑再贴照片，那圈变纯黑边框；
  没标注就保持原样。自动检测：`tools/annotate_opening.py`（`--write` 落盘后跑
  `tools/sync_assets.sh`）。目前 29 台已标注（Leica M10 系 / M-e / Canon 全系 /
  Sony 主力 / Fuji 多数）；自动检测分不清「玻璃」和「深灰机身」的机型先不写，
  出图人工确认后再逐台补。
- 压黑**不逐边照抄** opening：以显示区为中心、四边取同一内缩（四条内缩量的中位数）
  画黑边，照片位置不动。逐边照抄时，检测误差（有的边直接是 0）会让黑边一边宽一边窄，
  照片看着就「歪」；等宽之后照片永远在屏幕正中，代价是最宽的边可能留 1~2px 玻璃。
