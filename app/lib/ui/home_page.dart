/// FotoFrame 主界面：工具栏 + 左栏（当前照片/参数）+ 中央作品 + 右栏（队列）。
library;

import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/catalog/loader.dart';
import '../core/catalog/matcher.dart';
import '../core/compose/layout.dart';
import '../core/exif/exif_reader.dart';
import '../model/batch_item.dart';
import '../platform/platform_bridge.dart';
import '../services/render_service.dart';
import 'design.dart';
import 'widgets/app_toolbar.dart';
import 'widgets/preview_stage.dart';
import 'widgets/queue_panel.dart';
import 'widgets/settings_panel.dart';

const _photoExtensions = ['jpg', 'jpeg', 'png', 'heic', 'tif', 'tiff', 'dng'];

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => HomePageState();
}

class HomePageState extends State<HomePage> {
  CameraCatalog? _catalog;
  Future<void>? _catalogFuture;
  final List<BatchItem> _items = [];
  int _selected = -1;

  // 主题设置（背景固定，不提供选择）
  LayoutDensity _density = LayoutDensity.normal;
  int _longEdge = 2400;

  // 预览
  ui.Image? _preview;
  bool _previewing = false;
  int _renderToken = 0;

  // 批量导出
  bool _exporting = false;
  String _progress = '';
  String? _error;
  StreamSubscription<List<String>>? _dropSub;

  FrameTheme get _theme => FrameTheme(
        longEdge: _longEdge,
        cameraMarginRatio: _density.cameraMarginRatio,
      );

  BatchItem? get _current =>
      (_selected >= 0 && _selected < _items.length) ? _items[_selected] : null;

  /// 供测试断言用：队列里第一张照片。
  BatchItem? indexOfFirstItem() => _items.isEmpty ? null : _items.first;

  @override
  void initState() {
    super.initState();
    _catalogFuture = _loadCatalog();
    _dropSub = PlatformBridge.onFilesDropped.listen(_onFilesDropped);
    WidgetsBinding.instance.addPostFrameCallback((_) => maybeSelfTest());
  }

  @override
  void dispose() {
    _dropSub?.cancel();
    _preview?.dispose();
    for (final i in _items) {
      i.dispose();
    }
    super.dispose();
  }

  /// 确保相机目录已加载（导入/自检都依赖它）。
  Future<void> _ensureCatalog() async {
    if (_catalog != null) return;
    await (_catalogFuture ??= _loadCatalog());
  }

  Future<void> _loadCatalog() async {
    try {
      final catalog = await loadBundledCatalog();
      if (mounted) setState(() => _catalog = catalog);
    } catch (e) {
      if (mounted) {
        setState(() => _error = '相机资产加载失败：$e\n（先运行 tools/sync_assets.sh）');
      }
    }
  }

  // ---------------- 导入 ----------------

  Future<void> importPhotos() async {
    const group = XTypeGroup(label: '照片', extensions: _photoExtensions);
    final files = await openFiles(acceptedTypeGroups: [group]);
    if (files.isEmpty) return;
    await addPaths(files.map((f) => f.path).toList());
  }

  Future<void> _onFilesDropped(List<String> paths) => addPaths(paths);

  Future<void> addPaths(List<String> paths) async {
    final valid = paths
        .where((p) {
          final ext = p.split('.').last.toLowerCase();
          return _photoExtensions.contains(ext);
        })
        .toList();
    if (valid.isEmpty) return;
    await _ensureCatalog();

    final newItems = <BatchItem>[];
    for (final p in valid) {
      final f = File(p);
      if (!f.existsSync()) continue;
      final item = BatchItem(f, p.split('/').last);
      await _readMetadata(item);
      newItems.add(item);
    }
    if (newItems.isEmpty) return;

    setState(() {
      _items.addAll(newItems);
      if (_selected < 0) _selected = _items.length - newItems.length;
      _error = null;
    });
    await _refreshPreview();
  }

  /// 读 EXIF 并匹配机型。
  Future<void> _readMetadata(BatchItem item) async {
    try {
      final bytes = await item.file.readAsBytes();
      final exif = ExifReader.read(bytes);
      item.exif = exif;
      final catalog = _catalog;
      final hit = catalog?.match(make: exif.make, model: exif.model);
      if (hit != null) {
        item.camera = hit.asset;
        final how = switch (hit.how) {
          'alias' => '型号别名',
          'exact' => '精确匹配',
          'suffix' => '型号匹配',
          'pattern' => '规则匹配',
          _ => hit.how,
        };
        item.matchInfo = '$how · ${exif.make ?? ''} ${exif.model ?? ''}'.trim();
      } else {
        item.matchInfo =
            '未识别 · ${exif.make ?? '?'} ${exif.model ?? '?'}'.trim();
      }
      item.status = ItemStatus.pending;
    } on ExifMissingException {
      // 文件本身没写 EXIF（导出的 PNG / 截图），不是解析故障
      item.exif = const ExifInfo();
      item.matchInfo = '无 EXIF 机型信息';
      item.status = ItemStatus.pending;
      item.error = null;
    } catch (e) {
      item.exif = const ExifInfo();
      item.matchInfo = 'EXIF 读取失败';
      item.status = ItemStatus.failed;
      item.error = '$e';
    }
  }

  // ---------------- 预览 ----------------

  Future<void> _refreshPreview() async {
    final item = _current;
    if (item == null) {
      setState(() {
        _preview?.dispose();
        _preview = null;
      });
      return;
    }
    final token = ++_renderToken;
    setState(() => _previewing = true);
    try {
      if (item.preview == null) {
        item.preview = await RenderService.makePreview(item);
        item.width = item.preview!.width;
        item.height = item.preview!.height;
      }
      if (token != _renderToken) return;
      if (item.camera == null) {
        final raw = '${item.exif?.make ?? ''} ${item.exif?.model ?? ''}'.trim();
        setState(() {
          _preview?.dispose();
          _preview = null;
          _error = raw.isEmpty
              ? '照片没有 EXIF 机型信息，无法自动匹配'
              : 'EXIF 机型「$raw」暂不支持';
        });
        return;
      }
      final previewTheme = _theme.copyWith(
        longEdge: _theme.longEdge < 1600 ? _theme.longEdge : 1600,
      );
      final img = await RenderService.renderItem(
        item,
        theme: previewTheme,
        decodeWidth: 1600,
      );
      if (token != _renderToken) {
        img.dispose();
        return;
      }
      setState(() {
        _preview?.dispose();
        _preview = img;
        _error = null;
      });
    } catch (e) {
      if (token == _renderToken) {
        setState(() => _error = '预览失败：$e');
      }
    } finally {
      if (mounted && token == _renderToken) {
        setState(() => _previewing = false);
      }
    }
  }

  void _select(int index) {
    if (index == _selected) return;
    setState(() {
      _selected = index;
      _error = null;
    });
    _refreshPreview();
  }

  void _removeAt(int index) {
    if (index < 0 || index >= _items.length) return;
    final removed = _items.removeAt(index);
    removed.dispose();
    setState(() {
      if (_items.isEmpty) {
        _selected = -1;
        _preview?.dispose();
        _preview = null;
      } else if (_selected >= _items.length) {
        _selected = _items.length - 1;
      } else if (_selected > index) {
        _selected -= 1;
      }
    });
    _refreshPreview();
  }

  void _setTheme(VoidCallback change) {
    setState(change);
    _refreshPreview();
  }

  // ---------------- 导出 ----------------

  Future<void> exportCurrent() async {
    final item = _current;
    if (item == null || item.camera == null) {
      setState(() => _error = '当前照片还没有识别到机型');
      return;
    }
    final loc = await getSaveLocation(suggestedName: '${item.stem}_framed.png');
    if (loc == null) return;
    setState(() => _exporting = true);
    try {
      final img = await RenderService.renderItem(
        item,
        theme: _theme,
      );
      final bytes = await RenderService.toPng(img);
      img.dispose();
      await File(loc.path).writeAsBytes(bytes);
      if (mounted) {
        _toast('已导出：${loc.path}');
      }
    } catch (e) {
      setState(() => _error = '导出失败：$e');
    } finally {
      if (mounted) setState(() => _exporting = false);
    }
  }

  Future<void> exportAll() async {
    final targets = <BatchItem>[];
    for (final item in _items) {
      if (item.camera == null) continue;
      targets.add(item);
    }
    if (targets.isEmpty) {
      setState(() => _error =
          _items.isEmpty ? '队列里还没有照片' : '队列里的照片都未识别到机型');
      return;
    }
    final skipped = _items.length - targets.length;
    final dir = await getDirectoryPath(confirmButtonText: '导出到此处');
    if (dir == null) return;

    setState(() {
      _exporting = true;
      _progress = '0/${targets.length}';
    });
    var done = 0;
    for (final item in targets) {
      item.status = ItemStatus.processing;
      setState(() {});
      try {
        final img = await RenderService.renderItem(
          item,
          theme: _theme,
        );
        final bytes = await RenderService.toPng(img);
        img.dispose();
        final outPath = '$dir/${item.stem}_framed.png';
        await File(outPath).writeAsBytes(bytes);
        item.status = ItemStatus.done;
        item.error = null;
      } catch (e) {
        item.status = ItemStatus.failed;
        item.error = '$e';
      }
      done++;
      if (mounted) setState(() => _progress = '$done/${targets.length}');
    }
    if (mounted) {
      setState(() => _exporting = false);
      final skippedNote = skipped > 0 ? '，跳过 $skipped 张未识别机型' : '';
      _toast('批量导出完成：$done 张$skippedNote · $dir');
    }
  }

  Future<void> copyCurrent() async {
    final item = _current;
    if (item == null || item.camera == null) return;
    try {
      final img = await RenderService.renderItem(
        item,
        theme: _theme,
      );
      final bytes = await RenderService.toPng(img);
      img.dispose();
      final ok = await PlatformBridge.copyImageToClipboard(bytes);
      if (!mounted) return;
      _toast(ok ? '已复制到剪贴板（PNG）' : '当前平台不支持复制图片');
    } catch (e) {
      setState(() => _error = '复制失败：$e');
    }
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        behavior: SnackBarBehavior.floating,
        backgroundColor: Palette.of(context).ink,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  // ---------------- 无头自检 ----------------

  Future<void> maybeSelfTest() async {
    const input = String.fromEnvironment('SELFTEST');
    if (input.isEmpty) return;
    const out = String.fromEnvironment('SELFTEST_OUT',
        defaultValue: '/tmp/fotoframe_selftest.png');
    try {
      await addPaths([input]);
      final item = _current;
      if (item == null || item.camera == null) {
        throw StateError('未识别机型：${item?.matchInfo}');
      }
      final img = await RenderService.renderItem(
        item,
        theme: _theme,
      );
      final bytes = await RenderService.toPng(img);
      img.dispose();
      await File(out).writeAsBytes(bytes);
      // ignore: avoid_print
      print('SELFTEST_OK $out camera=${item.camera!.slug} ${item.matchInfo}');
      const testClipboard = String.fromEnvironment('SELFTEST_CLIPBOARD');
      if (testClipboard == '1') {
        final ok = await PlatformBridge.copyImageToClipboard(bytes);
        // ignore: avoid_print
        print('SELFTEST_CLIPBOARD_OK=$ok');
      }
    } catch (e) {
      // ignore: avoid_print
      print('SELFTEST_FAIL $e');
    }
  }

  // ---------------- UI ----------------

  ({int w, int h})? get _canvasSize {
    final item = _current;
    final camera = item?.camera;
    if (item == null || camera == null || item.width == 0) return null;
    final layout = LayoutEngine.compute(
      theme: _theme,
      camera: camera,
      photoW: item.width,
      photoH: item.height,
    );
    return (w: layout.canvasW, h: layout.canvasH);
  }

  @override
  Widget build(BuildContext context) {
    final p = Palette.of(context);
    final item = _current;
    final camera = item?.camera;
    final canvas = _canvasSize;

    final captionLeft = [
      (camera?.displayName ?? ' ').toUpperCase(),
      '$_longEdge PX',
    ].join(' · ');

    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.keyO, meta: true): importPhotos,
        const SingleActivator(LogicalKeyboardKey.keyE, meta: true): exportCurrent,
        const SingleActivator(LogicalKeyboardKey.keyC, meta: true, shift: true):
            copyCurrent,
        const SingleActivator(LogicalKeyboardKey.delete):
            () => _removeAt(_selected),
        const SingleActivator(LogicalKeyboardKey.backspace):
            () => _removeAt(_selected),
      },
      child: Focus(
        autofocus: true,
        child: Scaffold(
          backgroundColor: p.paper,
          body: Column(
            children: [
              AppToolbar(
                palette: p,
                onImport: importPhotos,
                onCopy: copyCurrent,
                onExport: exportCurrent,
                canCopy: camera != null,
                canExport: camera != null,
                exporting: _exporting,
                progress: _progress,
              ),
              Expanded(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    SettingsPanel(
                      palette: p,
                      item: item,
                      density: _density,
                      longEdge: _longEdge,
                      onDensity: (d) => _setTheme(() => _density = d),
                      onLongEdge: (v) => _setTheme(() => _longEdge = v),
                      canvasSpec: canvas == null
                          ? '$_longEdge px · PNG'
                          : '${canvas.w} × ${canvas.h} px · PNG',
                      error: _error,
                    ),
                    Expanded(
                      child: PreviewStage(
                        palette: p,
                        image: _preview,
                        previewing: _previewing,
                        captionLeft: captionLeft,
                        captionRight: canvas == null
                            ? '—'
                            : '${canvas.w} × ${canvas.h}',
                        onImport: importPhotos,
                      ),
                    ),
                    QueuePanel(
                      palette: p,
                      items: _items,
                      selected: _selected,
                      onSelect: _select,
                      onRemove: _removeAt,
                      onClear: _clearAll,
                      onExportAll: exportAll,
                      exporting: _exporting,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _clearAll() {
    for (final i in _items) {
      i.dispose();
    }
    setState(() {
      _items.clear();
      _selected = -1;
      _preview?.dispose();
      _preview = null;
      _error = null;
    });
  }
}
