/// 渲染层：把 LayoutEngine 算好的几何画到 Flutter Canvas。
///
/// 这一层是「薄壳」：所有计算在 core/compose/layout.dart，
/// 这里只负责把数据翻译成 Canvas 调用。
library;

import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../core/catalog/camera_asset.dart';
import '../core/compose/layout.dart' hide Rect;

class FrameRenderer {
  /// 按 EXIF orientation（1~8）把图像转正。
  /// Flutter 的图片解码不应用 EXIF 方向，必须手动处理。
  static Future<ui.Image> applyOrientation(ui.Image img, int? orientation) async {
    if (orientation == null || orientation == 1) return img;
    final w = img.width.toDouble();
    final h = img.height.toDouble();
    final swap = orientation >= 5; // 5~8 交换宽高
    final outW = swap ? h : w;
    final outH = swap ? w : h;

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    canvas.translate(outW / 2, outH / 2);
    switch (orientation) {
      case 2: // 水平翻转
        canvas.scale(-1, 1);
      case 3: // 旋转 180
        canvas.rotate(3.141592653589793);
      case 4: // 垂直翻转
        canvas.scale(1, -1);
      case 5: // 转置
        canvas.rotate(3.141592653589793 / 2);
        canvas.scale(1, -1);
      case 6: // 顺时针 90
        canvas.rotate(3.141592653589793 / 2);
      case 7: // 转置+翻转
        canvas.rotate(-3.141592653589793 / 2);
        canvas.scale(1, -1);
      case 8: // 逆时针 90
        canvas.rotate(-3.141592653589793 / 2);
    }
    canvas.drawImage(img, Offset(-w / 2, -h / 2), Paint());
    final picture = recorder.endRecording();
    return picture.toImage(outW.round(), outH.round());
  }

  /// 离屏渲染整张成图，返回 ui.Image。
  static Future<ui.Image> render({
    required FrameLayout layout,
    required CameraAsset camera,
    required ui.Image cameraImage,
    required ui.Image photo,
    required FrameTheme theme,
  }) async {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);

    // 背景
    canvas.drawRect(
      Rect.fromLTWH(
          0, 0, layout.canvasW.toDouble(), layout.canvasH.toDouble()),
      Paint()..color = Color(theme.backgroundColor),
    );

    // 底部照片（先画，相机区在上方不会重叠）
    final pr = layout.photoRect;
    canvas.drawImageRect(
      photo,
      Rect.fromLTWH(0, 0, photo.width.toDouble(), photo.height.toDouble()),
      Rect.fromLTWH(pr.x, pr.y, pr.w, pr.h),
      Paint()..filterQuality = FilterQuality.high,
    );

    // 相机背面图：multiply 混合让官方图的纯白底融入画布背景
    final cr = layout.cameraRect;
    canvas.drawImageRect(
      cameraImage,
      Rect.fromLTWH(0, 0, cameraImage.width.toDouble(),
          cameraImage.height.toDouble()),
      Rect.fromLTWH(cr.x, cr.y, cr.w, cr.h),
      Paint()
        ..filterQuality = FilterQuality.high
        ..blendMode = BlendMode.multiply,
    );

    // 照片 → 屏幕四边形（透视变换）
    _drawPhotoInScreen(canvas, layout, photo, camera);

    final picture = recorder.endRecording();
    return picture.toImage(layout.canvasW, layout.canvasH);
  }

  static void _drawPhotoInScreen(Canvas canvas, FrameLayout layout,
      ui.Image photo, CameraAsset camera) {
    // 按相机资产的 fit 决定铺满(cover)还是留边(contain，真实相机回放)
    final (sx, tx, sy, ty) = LayoutEngine.screenMapping(
      camera: camera, photoW: photo.width, photoH: photo.height);

    // 目标四角 = 屏幕四边形画布坐标（letterbox 偏移已合进 S 矩阵）
    final dst = layout.screenQuadOnCanvas;

    // 单应：照片像素坐标 → 画布坐标
    // 先算 单位矩形→四边形，再左乘 照片像素→单位正方形
    final hUnit = LayoutEngine.homographyToQuad(dst);
    // M = H_unit · S，S = [[sx,0,tx],[0,sy,ty],[0,0,1]]
    final m = _mul3x3(hUnit, [sx, 0, tx, 0, sy, ty, 0, 0, 1]);

    // 屏幕开口先整块压黑：标注了 opening 的机型，玻璃比 3:2 显示区大一圈，
    // 不压黑就会在照片外面留一圈玻璃的灰反光（M10 系尤其明显）。
    final opening = layout.openingQuadOnCanvas;
    if (opening != null) {
      canvas.drawPath(_quadPath(opening), Paint()..color = Colors.black);
    }

    // 屏幕区域底色（屏幕未覆盖部分是相机图本身的黑色，补一层纯黑保险）
    final path = Path()
      ..moveTo(dst[0].$1, dst[0].$2)
      ..lineTo(dst[1].$1, dst[1].$2)
      ..lineTo(dst[2].$1, dst[2].$2)
      ..lineTo(dst[3].$1, dst[3].$2)
      ..close();
    canvas.drawPath(path, Paint()..color = Colors.black);

    canvas.save();
    canvas.clipPath(path);
    canvas.transform(Float64List.fromList([
      m[0], m[3], 0, m[6],
      m[1], m[4], 0, m[7],
      0, 0, 1, 0,
      m[2], m[5], 0, 1,
    ]));
    canvas.drawImage(
        photo,
        Offset.zero,
        Paint()..filterQuality = FilterQuality.high);
    canvas.restore();
  }

  static Path _quadPath(List<(double, double)> q) {
    return Path()
      ..moveTo(q[0].$1, q[0].$2)
      ..lineTo(q[1].$1, q[1].$2)
      ..lineTo(q[2].$1, q[2].$2)
      ..lineTo(q[3].$1, q[3].$2)
      ..close();
  }

  static List<double> _mul3x3(List<double> a, List<double> b) {
    final out = List.filled(9, 0.0);
    for (var r = 0; r < 3; r++) {
      for (var c = 0; c < 3; c++) {
        var s = 0.0;
        for (var k = 0; k < 3; k++) {
          s += a[r * 3 + k] * b[k * 3 + c];
        }
        out[r * 3 + c] = s;
      }
    }
    return out;
  }
}
