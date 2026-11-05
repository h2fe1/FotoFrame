/// 渲染服务：把 BatchItem 的解码、合成、编码集中在一处（UI 无关）。
library;

import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/services.dart' show rootBundle;

import '../core/catalog/camera_asset.dart';
import '../core/compose/layout.dart';
import '../model/batch_item.dart';
import '../render/frame_renderer.dart';

class RenderService {
  RenderService._();

  /// 相机图缓存（按资产路径），避免批量时反复解码。
  static final Map<String, ui.Image> _cameraCache = {};

  static Future<ui.Image> cameraImage(CameraAsset camera) async {
    final cached = _cameraCache[camera.imagePath];
    if (cached != null) return cached;
    final data = await rootBundle.load(camera.imagePath);
    final codec = await ui.instantiateImageCodec(data.buffer.asUint8List());
    final img = (await codec.getNextFrame()).image;
    _cameraCache[camera.imagePath] = img;
    return img;
  }

  /// 带宽度上限的解码（用于预览，控制内存）。
  static Future<ui.Image> decode(Uint8List bytes, {int? targetWidth}) async {
    final codec = await ui.instantiateImageCodec(
      bytes,
      targetWidth: targetWidth,
    );
    return (await codec.getNextFrame()).image;
  }

  /// 生成预览图（低分辨率）。
  static Future<ui.Image> makePreview(BatchItem item) async {
    final bytes = await item.file.readAsBytes();
    final raw = await decode(bytes, targetWidth: 1400);
    return FrameRenderer.applyOrientation(raw, item.exif?.orientation);
  }

  /// 合成整张成图。[targetWidth] 为解码宽度上限，导出时应 >= 主题长边。
  static Future<ui.Image> renderItem(
    BatchItem item, {
    required FrameTheme theme,
    int? decodeWidth,
  }) async {
    final camera = item.camera;
    if (camera == null) {
      throw StateError('未识别机型：${item.name}');
    }
    final bytes = await item.file.readAsBytes();
    final photoRaw = await decode(bytes, targetWidth: decodeWidth ?? theme.longEdge);
    final photo =
        await FrameRenderer.applyOrientation(photoRaw, item.exif?.orientation);

    final camImage = await cameraImage(camera);
    final layout = LayoutEngine.compute(
      theme: theme,
      camera: camera,
      photoW: photo.width,
      photoH: photo.height,
    );
    final out = await FrameRenderer.render(
      layout: layout,
      camera: camera,
      cameraImage: camImage,
      photo: photo,
      theme: theme,
    );
    photoRaw.dispose();
    if (!identical(photo, photoRaw)) photo.dispose();
    return out;
  }

  static Future<Uint8List> toPng(ui.Image image) async {
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    return data!.buffer.asUint8List();
  }
}
