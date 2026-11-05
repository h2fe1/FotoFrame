# FotoFrame

把照片「套」回拍摄它的相机里：读取照片 EXIF 自动识别相机型号，
把照片合成进对应相机官方背面图的屏幕区域，下方排布原始照片，导出成图。

## 当前状态

- ✅ macOS 版可构建运行（Flutter 3.47.9 stable）
- ✅ 核心逻辑纯 Dart、零 Flutter 依赖（`app/lib/core/`），为 Windows/Android/iOS 移植做好准备
- ✅ **26 台相机资产**（全部按实物素材逐一标定屏幕四角；Q3 43 与 Q3 外观相同，共用 Q3 背面图）
  - Hasselblad：X2D 100C、X2D II 100C、CFV 100C
  - Ricoh：GR II、GR III、GR IIIx、GR III HDF、GR IV
  - Sony：A1 II、A7C II、A7 III、A7 IV、A7 V、A7R III、A7R IV、A7R V、RX1R II、RX1R III
  - Panasonic：S1R II、S1 II、S9
  - Leica：D-LUX 8、Q2、Q3（含 Q3 43 别名）、SL2、SL3
  （更早的一批旧资产（48 台）已不再保留，不参与构建）
- ✅ 界面：A 工具栏 + C 纸面排版的工作台（当前照片 · 作品 · 队列），浅色/深色跟随 macOS 自动切换
- ✅ 拖拽导入、批量导出、复制到剪贴板
- ✅ 端到端验证通过：真实 macOS 应用读样片 → EXIF 识别（索尼 ILCE 别名）→ 渲染导出
- ⚠️ 徕卡 M-D / M10-D / M11-D 无屏幕，不支持套壳

## 功能

| 功能 | 说明 |
|---|---|
| 自动识别 | 读 EXIF Make/Model 自动匹配机型，索尼按 ILCE 内部编号映射；不支持时明确提示 |
| 拖拽导入 | 把 JPG/PNG/HEIC/TIFF/DNG 直接拖进窗口（原生 NSView 拖拽目标） |
| 容器格式 | EXIF 解析覆盖 JPEG(APP1) · TIFF/DNG · PNG(`eXIf`) · HEIC/HEIF(`meta`→`iinf`/`iloc`)；无 EXIF 的图给出温和提示 |
| 批量队列 | 多选导入，队列逐张显示识别结果，支持逐条移除 |
| 批量导出 | 选一个文件夹，按 `<原名>_framed.png` 全部导出 |
| 主题 | 固定相纸灰背景 · 底部原图左右下三边出血贴边 · 3 档版式松紧 · 输出长边 1600/2400/3200 |
| 外观 | 跟随系统浅色/深色；窗口无标题栏，工具栏与红绿灯同高 |
| 屏幕 | 相机屏幕按官方显示区比例（4:3 / 3:2 / 16:9，逐台校准）cover 裁剪填入，四周保留边框；比例不符的机型（如 a6600 的 16:9 屏）按真实相机留黑边；标注了 `screen.opening` 的 29 台先把玻璃开口压黑，照片外不会留一圈玻璃灰 |
| 复制 | ⌘⇧C 把成图以 PNG 写入系统剪贴板（NSPasteboard） |
| 快捷键 | ⌘O 导入 · ⌘E 导出当前 · ⌘⇧C 复制 |

## 目录

```
app/            Flutter 应用（macOS 先行，可扩展 Windows/Android/iOS）
  lib/core/     纯 Dart 核心：EXIF 解析、机型匹配、版面几何、单应变换
  lib/render/   Flutter Canvas 渲染层
  lib/services/ 渲染服务（解码/合成/编码，UI 无关）
  lib/platform/ 平台通道（拖拽、剪贴板）
  lib/ui/       界面
assets/cameras/ 相机背面图资产（官方产品图 + meta.json 屏幕标注 + EXIF 匹配规则）
tools/          资产与构建工具
samples/        带真实 EXIF 的测试样片（jpg / png / heic）
renders/        界面截图（全机型渲染样张由 `flutter test` 按需重新生成）
docs/           架构与机型文档
```

## 工具

| 脚本 | 作用 |
|---|---|
| `tools/flutter.sh` | Flutter 环境封装（隔离 HOME/PUB_CACHE 到 .toolchain） |
| `tools/sync_assets.sh` | 把 assets/cameras 打平同步进 app 资产目录，生成 catalog.json |
| `tools/annotate_screen.py` | 屏幕区域自动检测（白底轮廓 + 局部平滑度最大矩形） |
| `tools/measure_screens.py` | 屏幕标注审计：标注/实测/官方比例对照（`--debug` 出叠加图） |
| `tools/annotate_opening.py` | 检测屏幕开口（玻璃内沿）写入 `screen.opening`，成图上把照片外那圈玻璃灰压黑 |
| `tools/trim_assets.py` | 裁剪白边并同步平移屏幕标注 |
| `tools/grid.py` | 坐标网格叠加，手工精修标注用 |
| `tools/make_samples.py` | 生成带 EXIF 的测试照片 |
| `tools/icon/` | app 图标「Fo」字标：几何自绘、7 个尺寸一键装进 AppIcon |

## 开发

```bash
cd app
../tools/flutter.sh pub get
../tools/sync_assets.sh              # 资产变更后
../tools/flutter.sh run -d macos     # 运行（需 Xcode 可写 ~/Library）
../tools/flutter.sh test             # 全部测试（含像素级渲染断言 + 全机出图）
../tools/flutter.sh build macos      # 出包
```

无头自检（CI 可用）：

```bash
../tools/flutter.sh build macos --debug \
  --dart-define=SELFTEST=<照片路径> --dart-define=SELFTEST_OUT=<输出.png>
```

## 资产工作流

1. 收集官方背面图 → `assets/cameras/<brand>/<slug>/back.jpg` + `meta.json`
2. `tools/annotate_screen.py` 自动检测屏幕四边形，生成 `screen_debug.png` 供核对
3. `tools/trim_assets.py` 裁白边（原图备份为 back_untrimmed）
4. 手工修正个别标注（亮屏图、反光渐变图自动检测会偏差）
5. `tools/sync_assets.sh` 打包

## 移植路线

- `lib/core/` 纯 Dart（EXIF 解析、机型匹配、版面几何、单应变换）——直接复用
- `lib/render/` 仅依赖 Flutter Canvas——全 Flutter 平台通用
- `lib/platform/` 与 UI 层按需替换：Windows 用同套 Flutter 桌面支持；
  Android/iOS 替换文件选择/保存为系统相册 API
