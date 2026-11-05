/// 屏幕填充方式：cover（铺满）与 contain（等比缩放 + 黑边，真实相机回放）。
library;

import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/core/catalog/camera_asset.dart';
import 'package:fotoframe/core/compose/layout.dart';
import 'package:fotoframe/render/frame_renderer.dart';

Future<ui.Image> solid(int w, int h, int argb) async {
  final rec = ui.PictureRecorder();
  final c = ui.Canvas(rec);
  c.drawRect(
    ui.Rect.fromLTWH(0, 0, w.toDouble(), h.toDouble()),
    ui.Paint()..color = ui.Color(argb),
  );
  return rec.endRecording().toImage(w, h);
}

Future<(int r, int g, int b)> pixel(ui.Image img, int x, int y) async {
  final data = await img.toByteData(format: ui.ImageByteFormat.rawRgba);
  final off = (y * img.width + x) * 4;
  return (
    data!.getUint8(off),
    data.getUint8(off + 1),
    data.getUint8(off + 2),
  );
}

/// 16:9 屏幕的测试相机（模拟 a6600 的宽屏）。
CameraAsset wideScreen(ScreenFit fit) => CameraAsset(
      brand: 'Test',
      model: 'TestCam',
      slug: 'test',
      imageFile: 'back.png',
      width: 2000,
      height: 1300,
      screen: const ScreenQuad(
          [(600, 300), (1400, 300), (1400, 750), (600, 750)]), // 800x450 = 16:9
      screenFit: fit,
      exif: const ExifSignature(makePatterns: [], modelPatterns: []),
      directory: 'assets/cameras',
    );

Future<ui.Image> render(
    CameraAsset camera, ui.Image camImg, ui.Image photo) async {
  const theme = FrameTheme();
  final layout = LayoutEngine.compute(
    theme: theme,
    camera: camera,
    photoW: photo.width,
    photoH: photo.height,
  );
  return FrameRenderer.render(
    layout: layout,
    camera: camera,
    cameraImage: camImg,
    photo: photo,
    theme: theme,
  );
}

Future<ui.Image> sceneImage() async {
  final rec = ui.PictureRecorder();
  final c = ui.Canvas(rec);
  c.drawRect(ui.Rect.fromLTWH(0, 0, 2000, 1300),
      ui.Paint()..color = const ui.Color(0xFF888888));
  c.drawRect(ui.Rect.fromLTWH(600, 300, 800, 450),
      ui.Paint()..color = const ui.Color(0xFF000000));
  return rec.endRecording().toImage(2000, 1300);
}

void main() {
  // 屏幕中心附近取点：photo 是 3:2，屏幕 16:9
  test('cover：3:2 照片铺满 16:9 屏幕（裁掉上下）', () async {
    final camera = wideScreen(ScreenFit.cover);
    final camImg = await sceneImage();
    final photo = await solid(1500, 1000, 0xFFFF0000); // 3:2
    final out = await render(camera, camImg, photo);

    final layout = LayoutEngine.compute(
      theme: const FrameTheme(),
      camera: camera,
      photoW: photo.width,
      photoH: photo.height,
    );
    final q = layout.screenQuadOnCanvas;
    // 屏幕四角内侧都应是照片（红色）
    final x1 = (q[0].$1 + 4).round();
    final x2 = (q[1].$1 - 4).round();
    final y1 = (q[0].$2 + 4).round();
    final y2 = (q[2].$2 - 4).round();
    for (final (x, y) in [(x1, y1), (x2, y1), (x1, y2), (x2, y2)]) {
      final (r, g, b) = await pixel(out, x, y);
      expect(r, greaterThan(200), reason: '($x,$y) 应被照片覆盖');
      expect(g, lessThan(40));
      expect(b, lessThan(40));
    }
  });

  test('contain：3:2 照片在 16:9 屏幕上左右留黑边', () async {
    final camera = wideScreen(ScreenFit.contain);
    final camImg = await sceneImage();
    final photo = await solid(1500, 1000, 0xFFFF0000); // 3:2
    final out = await render(camera, camImg, photo);

    final layout = LayoutEngine.compute(
      theme: const FrameTheme(),
      camera: camera,
      photoW: photo.width,
      photoH: photo.height,
    );
    final q = layout.screenQuadOnCanvas;
    final yMid = ((q[0].$2 + q[2].$2) / 2).round();
    final xLeftEdge = (q[0].$1 + 4).round();
    final xRightEdge = (q[1].$1 - 4).round();
    final xMid = ((q[0].$1 + q[1].$1) / 2).round();

    // 左右边缘应是黑边
    for (final x in [xLeftEdge, xRightEdge]) {
      final (r, g, b) = await pixel(out, x, yMid);
      expect(r, lessThan(30), reason: '($x,$yMid) 应是黑边，实际 R=$r');
      expect(g, lessThan(30));
      expect(b, lessThan(30));
    }
    // 中间是照片
    final (r, g, b) = await pixel(out, xMid, yMid);
    expect(r, greaterThan(200));
    expect(g, lessThan(40));
    expect(b, lessThan(40));

    // 上下方向应当占满（照片比屏幕"高"，按高度贴合）
    final yTop = (q[0].$2 + 4).round();
    final yBottom = (q[2].$2 - 4).round();
    for (final y in [yTop, yBottom]) {
      final (rr, gg, bb) = await pixel(out, xMid, y);
      expect(rr, greaterThan(200), reason: '($xMid,$y) 竖向应被照片覆盖');
      expect(gg, lessThan(40));
      expect(bb, lessThan(40));
    }
  });

  test('contain：比例相同时与 cover 结果一致（不留边）', () async {
    final camera = wideScreen(ScreenFit.contain);
    final camImg = await sceneImage();
    // 16:9 照片
    final photo = await solid(1600, 900, 0xFFFF0000);
    final out = await render(camera, camImg, photo);
    final layout = LayoutEngine.compute(
      theme: const FrameTheme(),
      camera: camera,
      photoW: photo.width,
      photoH: photo.height,
    );
    final q = layout.screenQuadOnCanvas;
    final yMid = ((q[0].$2 + q[2].$2) / 2).round();
    for (final x in [(q[0].$1 + 4).round(), (q[1].$1 - 4).round()]) {
      final (r, _, _) = await pixel(out, x, yMid);
      expect(r, greaterThan(200), reason: '同比例不应留边');
    }
  });

  test('screenMapping 的数学：contain 收缩、cover 溢出', () {
    // 照片 3:2（1.5），屏幕 16:9（1.778）：照片相对更高
    const photoW = 1500, photoH = 1000;
    const screenAspect = 16 / 9;

    final (csx, ctx, csy, cty) = LayoutEngine.screenMapping(
        camera: wideScreen(ScreenFit.contain), photoW: photoW, photoH: photoH);
    final crw = csx * photoW;
    final crh = csy * photoH;
    expect(crw, closeTo(1.5 / screenAspect, 1e-9)); // 左右留边
    expect(crh, closeTo(1.0, 1e-9));
    expect(ctx, closeTo((1 - crw) / 2, 1e-9));
    expect(cty, closeTo(0, 1e-9));

    final (vsx, vtx, vsy, vty) = LayoutEngine.screenMapping(
        camera: wideScreen(ScreenFit.cover), photoW: photoW, photoH: photoH);
    final vrw = vsx * photoW;
    final vrh = vsy * photoH;
    expect(vrw, closeTo(1.0, 1e-9));
    expect(vrh, closeTo(screenAspect / 1.5, 1e-9)); // 上下被裁
    expect(vtx, closeTo(0, 1e-9));
    expect(vty, lessThan(0), reason: 'cover 时纵向溢出，偏移为负');
  });

  test('asset 未写 fit 时默认 cover', () {
    expect(ScreenFit.parse(null), ScreenFit.cover);
    expect(ScreenFit.parse(''), ScreenFit.cover);
    expect(ScreenFit.parse('cover'), ScreenFit.cover);
    expect(ScreenFit.parse('contain'), ScreenFit.contain);
  });
}