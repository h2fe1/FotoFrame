/// 渲染管线端到端测试：合成渲染 → 像素级断言。
library;

import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/core/catalog/camera_asset.dart';
import 'package:fotoframe/core/compose/layout.dart';
import 'package:fotoframe/render/frame_renderer.dart';

/// 生成纯色测试图。
Future<ui.Image> solidImage(int w, int h, int argb) async {
  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);
  canvas.drawRect(
    ui.Rect.fromLTWH(0, 0, w.toDouble(), h.toDouble()),
    ui.Paint()..color = ui.Color(argb),
  );
  return recorder.endRecording().toImage(w, h);
}

Future<int> pixelAt(ui.Image img, int x, int y) async {
  final data = await img.toByteData(format: ui.ImageByteFormat.rawRgba);
  final off = (y * img.width + x) * 4;
  return (data!.getUint8(off) << 24) |
      (data.getUint8(off + 1) << 16) |
      (data.getUint8(off + 2) << 8) |
      data.getUint8(off + 3);
}

void main() {
  test('端到端：照片被映射进屏幕区域，布局正确', () async {
    // 相机图：灰色机身，屏幕区域纯黑（2000x1300，屏幕 600~1400 x 300~900）
    final camRec = ui.PictureRecorder();
    final camCanvas = ui.Canvas(camRec);
    camCanvas.drawRect(ui.Rect.fromLTWH(0, 0, 2000, 1300),
        ui.Paint()..color = const ui.Color(0xFF888888));
    camCanvas.drawRect(ui.Rect.fromLTWH(600, 300, 800, 600),
        ui.Paint()..color = const ui.Color(0xFF000000));
    final camImg = await camRec.endRecording().toImage(2000, 1300);

    final camera = CameraAsset(
      brand: 'Test',
      model: 'TestCam',
      slug: 'test',
      imageFile: 'back.png',
      width: 2000,
      height: 1300,
      screen: const ScreenQuad(
          [(600, 300), (1400, 300), (1400, 900), (600, 900)]),
      exif: const ExifSignature(makePatterns: [], modelPatterns: []),
      directory: 'assets/cameras',
    );

    // 照片：纯红 1600x1200（4:3，与屏幕 4:3 一致，无 letterbox 黑边）
    final photo = await solidImage(1600, 1200, 0xFFFF0000);

    const theme = FrameTheme();
    final layout = LayoutEngine.compute(
      theme: theme, camera: camera, photoW: 1600, photoH: 1200);

    final out = await FrameRenderer.render(
      layout: layout,
      camera: camera,
      cameraImage: camImg,
      photo: photo,
      theme: theme,
    );

    expect(out.width, layout.canvasW);
    expect(out.height, layout.canvasH);

    // 屏幕中心（画布坐标）：应为照片的红色
    final cx = ((layout.screenQuadOnCanvas[0].$1 +
                layout.screenQuadOnCanvas[2].$1) /
            2)
        .round();
    final cy = ((layout.screenQuadOnCanvas[0].$2 +
                layout.screenQuadOnCanvas[2].$2) /
            2)
        .round();
    final centerPixel = await pixelAt(out, cx, cy);
    expect(centerPixel >> 24, 0xFF, reason: '屏幕中心 R 通道');
    expect((centerPixel >> 16) & 0xFF, lessThan(30), reason: '屏幕中心 G 通道');

    // 底部照片区中心：也应为红色
    final px = (layout.photoRect.cx).round();
    final py = (layout.photoRect.cy).round();
    final photoPixel = await pixelAt(out, px, py);
    expect(photoPixel >> 24, 0xFF, reason: '照片区 R 通道');

    // 底部照片出血：左右下三边直接贴画布边缘，边缘像素也应是照片
    for (final (x, y) in [
      (2, layout.canvasH - 2),
      (layout.canvasW - 2, layout.canvasH - 2),
      (2, layout.photoRect.y.round() + 2),
      (layout.canvasW - 2, layout.photoRect.y.round() + 2),
    ]) {
      final edgePixel = await pixelAt(out, x, y);
      expect(edgePixel >> 24, 0xFF, reason: '边缘 ($x,$y) R 通道应为照片');
      expect((edgePixel >> 16) & 0xFF, lessThan(30),
          reason: '边缘 ($x,$y) G 通道应为照片');
    }

    // 画布顶部两角：应为固定背景色（测试环境有色彩管理，容差比较）
    final corner = await pixelAt(out, 2, 2);
    expect((corner >> 24) & 0xFF, closeTo(0xE9, 8), reason: '背景 R');
    expect((corner >> 16) & 0xFF, closeTo(0xE9, 8), reason: '背景 G');
    expect((corner >> 8) & 0xFF, closeTo(0xE6, 8), reason: '背景 B');

    // 相机区但屏幕外（机身边缘）：应为灰色机身
    final bodyX = (layout.cameraRect.x + 5).round();
    final bodyY = (layout.cameraRect.cy).round();
    final bodyPixel = await pixelAt(out, bodyX, bodyY);
    expect((bodyPixel >> 24) & 0xFF, closeTo(0x88, 16), reason: '机身 R');
    expect((bodyPixel >> 16) & 0xFF, closeTo(0x88, 16), reason: '机身 G');
    expect((bodyPixel >> 8) & 0xFF, closeTo(0x88, 16), reason: '机身 B');
  });

  test('标注了屏幕开口：开口压黑，照片仍只铺 3:2 显示区', () async {
    // 机身 #888888；屏幕开口 560~1440 x 260~940 涂 #808080（模拟玻璃反光）；
    // 3:2 显示区 600~1400 x 300~900 纯黑（照片要贴进去的区域）。
    final camRec = ui.PictureRecorder();
    final camCanvas = ui.Canvas(camRec);
    camCanvas.drawRect(ui.Rect.fromLTWH(0, 0, 2000, 1300),
        ui.Paint()..color = const ui.Color(0xFF888888));
    camCanvas.drawRect(ui.Rect.fromLTWH(560, 260, 880, 680),
        ui.Paint()..color = const ui.Color(0xFF808080));
    camCanvas.drawRect(ui.Rect.fromLTWH(600, 300, 800, 600),
        ui.Paint()..color = const ui.Color(0xFF000000));
    final camImg = await camRec.endRecording().toImage(2000, 1300);

    final camera = CameraAsset(
      brand: 'Test',
      model: 'TestCam',
      slug: 'test',
      imageFile: 'back.png',
      width: 2000,
      height: 1300,
      screen: const ScreenQuad(
          [(600, 300), (1400, 300), (1400, 900), (600, 900)]),
      opening: const ScreenQuad(
          [(560, 260), (1440, 260), (1440, 940), (560, 940)]),
      exif: const ExifSignature(makePatterns: [], modelPatterns: []),
      directory: 'assets/cameras',
    );
    final photo = await solidImage(1600, 1200, 0xFFFF0000);
    const theme = FrameTheme();
    final layout = LayoutEngine.compute(
      theme: theme, camera: camera, photoW: 1600, photoH: 1200);

    expect(layout.openingQuadOnCanvas, isNotNull);

    final out = await FrameRenderer.render(
      layout: layout,
      camera: camera,
      cameraImage: camImg,
      photo: photo,
      theme: theme,
    );

    // 开口内、显示区外的灰环：应被压成黑色
    final ringX = layout.openingQuadOnCanvas![0].$1 +
        (layout.screenQuadOnCanvas[0].$1 -
                layout.openingQuadOnCanvas![0].$1) /
            2;
    final ringY = ((layout.screenQuadOnCanvas[0].$2 +
                layout.screenQuadOnCanvas[2].$2) /
            2);
    final ringPixel = await pixelAt(out, ringX.round(), ringY.round());
    expect((ringPixel >> 16) & 0xFF, lessThan(20), reason: '灰环 G 通道应为黑');

    // 显示区中心仍是照片（红）
    final cx = ((layout.screenQuadOnCanvas[0].$1 +
                layout.screenQuadOnCanvas[2].$1) /
            2)
        .round();
    final cy = ((layout.screenQuadOnCanvas[0].$2 +
                layout.screenQuadOnCanvas[2].$2) /
            2)
        .round();
    final centerPixel = await pixelAt(out, cx, cy);
    expect(centerPixel >> 24, 0xFF, reason: '屏幕中心 R 通道');
    expect((centerPixel >> 16) & 0xFF, lessThan(30), reason: '屏幕中心 G 通道');
  });

  test('开口四边不等宽时，照片在开口里居中', () {
    final camera = CameraAsset(
      brand: 'Test',
      model: 'TestCam',
      slug: 'test',
      imageFile: 'back.png',
      width: 2000,
      height: 1300,
      screen: const ScreenQuad(
          [(600, 300), (1400, 300), (1400, 900), (600, 900)]),
      // 左边 60、右边 20、上 40、下 80：开口中心相对显示区偏 (-20, +20)
      opening: const ScreenQuad(
          [(540, 260), (1420, 260), (1420, 980), (540, 980)]),
      exif: const ExifSignature(makePatterns: [], modelPatterns: []),
      directory: 'assets/cameras',
    );

    const theme = FrameTheme();
    final layout = LayoutEngine.compute(
      theme: theme, camera: camera, photoW: 1600, photoH: 1200);

    double cx(List<(double, double)> q) => (q[0].$1 + q[2].$1) / 2;
    double cy(List<(double, double)> q) => (q[0].$2 + q[2].$2) / 2;

    final q = layout.screenQuadOnCanvas;
    final o = layout.openingQuadOnCanvas!;
    expect(cx(q), closeTo(cx(o), 0.01), reason: '水平居中');
    expect(cy(q), closeTo(cy(o), 0.01), reason: '垂直居中');

    // 尺寸不变：还是 800x600 的显示区（按画布缩放）
    final s = layout.cameraRect.w / 2000;
    expect(q[1].$1 - q[0].$1, closeTo(800 * s, 0.01));
    expect(q[3].$2 - q[0].$2, closeTo(600 * s, 0.01));
  });
}
