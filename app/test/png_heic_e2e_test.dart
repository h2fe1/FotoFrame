/// PNG / HEIC 端到端：容器 → EXIF → 机型匹配 → 解码 → 渲染成图。
///
/// 这两条链路以前是断的（PNG 抛 unrecognized、HEIC 抛 UnsupportedError）。
library;

import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/core/catalog/camera_asset.dart';
import 'package:fotoframe/core/catalog/matcher.dart';
import 'package:fotoframe/core/compose/layout.dart';
import 'package:fotoframe/core/exif/exif_reader.dart';
import 'package:fotoframe/render/frame_renderer.dart';

Future<ui.Image> _decode(String path) async {
  final bytes = await File(path).readAsBytes();
  final codec = await ui.instantiateImageCodec(bytes);
  return (await codec.getNextFrame()).image;
}

CameraCatalog _catalog() {
  final raw = jsonDecode(File('assets/cameras/catalog.json').readAsStringSync());
  return CameraCatalog([
    for (final item in raw as List)
      CameraAsset.fromJson(
        (item as Map).cast<String, dynamic>(),
        directory: 'assets/cameras',
      ),
  ]);
}

/// 整条链路跑通，返回成图尺寸与「屏幕中心像素是否被照片覆盖」。
Future<({int w, int h, bool screenFilled, String slug})> _run(
    String samplePath, CameraCatalog catalog) async {
  final exif = ExifReader.read(await File(samplePath).readAsBytes());
  final hit = catalog.match(make: exif.make, model: exif.model);
  expect(hit, isNotNull,
      reason: '$samplePath 未被识别：make=${exif.make} model=${exif.model}');
  final camera = hit!.asset;

  final camImg = await _decode(File('${camera.directory}/${camera.imageFile}')
      .absolute
      .path);
  final photo = await _decode(samplePath);

  const theme = FrameTheme();
  final layout = LayoutEngine.compute(
    theme: theme,
    camera: camera,
    photoW: photo.width,
    photoH: photo.height,
  );
  final out = await FrameRenderer.render(
    layout: layout,
    camera: camera,
    cameraImage: camImg,
    photo: photo,
    theme: theme,
  );

  // 屏幕中心应该被照片覆盖（样片是彩色渐变，不是相纸灰 #E9E9E6）
  final cx = ((layout.screenQuadOnCanvas[0].$1 +
              layout.screenQuadOnCanvas[2].$1) /
          2)
      .round();
  final cy = ((layout.screenQuadOnCanvas[0].$2 +
              layout.screenQuadOnCanvas[2].$2) /
          2)
      .round();
  final data = await out.toByteData(format: ui.ImageByteFormat.rawRgba);
  final off = (cy * out.width + cx) * 4;
  final r = data!.getUint8(off);
  final g = data.getUint8(off + 1);
  final b = data.getUint8(off + 2);
  final isBackground = (r - 0xE9).abs() < 10 &&
      (g - 0xE9).abs() < 10 &&
      (b - 0xE6).abs() < 10;

  return (
    w: out.width,
    h: out.height,
    screenFilled: !isBackground,
    slug: camera.slug,
  );
}

void main() {
  late CameraCatalog catalog;

  setUpAll(() {
    catalog = _catalog();
  });

  test('PNG（eXIf chunk）走通全链路', () async {
    final f = File('../samples/ricoh_gr3.png');
    expect(f.existsSync(), isTrue);
    final r = await _run(f.path, catalog);
    expect(r.slug, 'gr3');
    expect(r.screenFilled, isTrue, reason: '屏幕中心应被照片填满');
    expect(r.w, greaterThan(1000));
  });

  test('HEIC（Exif item）走通全链路', () async {
    final f = File('../samples/hasselblad_x2d.heic');
    expect(f.existsSync(), isTrue);
    final r = await _run(f.path, catalog);
    expect(r.slug, 'x2d');
    expect(r.screenFilled, isTrue);
    expect(r.w, greaterThan(1000));
  });

  test('PNG 无 EXIF 时抛出 ExifMissingException，而不是解析崩溃', () {
    final out = BytesBuilder();
    out.add([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
    out.add([0, 0, 0, 13]);
    out.add('IHDR'.codeUnits);
    out.add([0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0]);
    out.add([0, 0, 0, 0]); // CRC 占位（解析器不校验）
    out.add([0, 0, 0, 0]);
    out.add('IEND'.codeUnits);
    out.add([0, 0, 0, 0]);
    expect(
      () => ExifReader.read(out.toBytes()),
      throwsA(isA<ExifMissingException>()),
    );
  });
}
