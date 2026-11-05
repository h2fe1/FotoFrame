# FotoFrame 图标源文件

图标是「Fo」字标，字母全部用几何图形画出来（矩形 + 圆环），不依赖任何字体，
所以没有字体授权问题，也可以任意调粗细 / 字距 / 配色。

```
python3 install.py        # 浅色 = A（纸白底 + 墨色字），深色 = B（墨底 + 骨白字）
python3 install.py C D    # 也可以换任意一对
```

会写两处东西：

| 位置 | 用途 |
|---|---|
| `Assets.xcassets/AppIcon.appiconset/` | 包内 `.icns`，浅色版，7 个尺寸 |
| `Assets.xcassets/IconLight.imageset` · `IconDark.imageset` | 512 + 1024 母版，运行时切换用 |

16 / 32 / 64 用一份加粗的「光学尺寸」版本，避免小尺寸糊掉。

## 浅色 / 深色怎么切

macOS 的 `.icns` **没有**暗色槽位（asset catalog 里的 dark appearance 只对 iOS 有效，
给 macOS 加会被 actool 丢掉），所以：

1. 包里的 `AppIcon.icns` 固定是浅色版 → Finder、以及 app 没运行时的 Dock 用它。
2. `app/macos/Runner/AppDelegate.swift` 里监听 `NSApp.effectiveAppearance`
   （外加 `AppleInterfaceThemeChangedNotification` 兜底），把对应的一张塞进
   `NSApp.applicationIconImage` → 运行中的 Dock 图标随系统外观即时切换。

限制：Finder 里永远是浅色版；Dock 里未运行时也是浅色版。

## 字形参数

| 参数 | 值 | 说明 |
|---|---|---|
| 大写高度 | 480 | 画布 1024，内容区 824 |
| F 字干 | 92 | 圆形的 o 用 86，光学补偿 |
| F 宽 | 298 | |
| 字距 | 38 | F 到 o 的净空 |
| o 外径 | 340 | 即 x-height |
| 圆角形状 | superellipse n=5.2, 824×824 | 接近 macOS 的 squircle |

改一个参数重跑即可，例如想让字更粗：`STROKE_F = 104, STROKE_O = 96`。

`python3 fo.py` 只出预览图到 `.work/icon_concepts/`，不碰 app。
原 Flutter 默认图标备份在 `.work/icon_concepts/_appicon_backup/`。