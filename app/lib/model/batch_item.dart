/// 批量队列中的单张照片。
library;

import 'dart:io';
import 'dart:ui' as ui;

import '../core/catalog/camera_asset.dart';
import '../core/exif/exif_reader.dart';

enum ItemStatus {
  pending('待处理'),
  processing('处理中'),
  done('完成'),
  failed('失败');

  final String label;
  const ItemStatus(this.label);
}

class BatchItem {
  final File file;
  final String name;

  ExifInfo? exif;
  CameraAsset? camera;

  /// 识别说明（展示给用户看匹配过程）。
  String matchInfo = '';

  /// 照片显示尺寸（已按 EXIF 方向转正）。
  int width = 0;
  int height = 0;

  /// 低分辨率预览图（选中项用）。
  ui.Image? preview;

  ItemStatus status = ItemStatus.pending;
  String? error;

  BatchItem(this.file, this.name);

  String get stem => name.replaceAll(RegExp(r'\.[^.]+$'), '');

  void dispose() {
    preview?.dispose();
    preview = null;
  }
}
