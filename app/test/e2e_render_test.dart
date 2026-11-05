/// 端到端出图：用真实资产 + 真实样片渲染成品，输出到 ../renders/ 供人工检查。
///
/// 运行：tools/flutter.sh test test/e2e_render_test.dart
library;

import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/core/catalog/camera_asset.dart';
import 'package:fotoframe/core/compose/layout.dart';
import 'package:fotoframe/render/frame_renderer.dart';

Future<ui.Image> loadImageFile(String path) async {
  final bytes = await File(path).readAsBytes();
  final codec = await ui.instantiateImageCodec(bytes);
  return (await codec.getNextFrame()).image;
}

void main() {
  test('用真实资产渲染样张到 ../renders/', () async {
    // 品牌样片映射（EXIF 匹配用），渲染本身用哪张样片都行
    const brandSamples = {
      'leica': '../samples/leica_m11.jpg',
      'hasselblad': '../samples/hasselblad_x2d.jpg',
      'ricoh': '../samples/ricoh_gr3.jpg',
      'sony': '../samples/sony_a7m4.jpg',
      'canon': '../samples/canon_r5m2.jpg',
      'fujifilm': '../samples/fujifilm_xt5.jpg',
      'panasonic': '../samples/hasselblad_x2d.jpg',
    };
    final outDir = Directory('../renders');
    if (!outDir.existsSync()) outDir.createSync();

    final assetsRoot = Directory('../assets/cameras');
    var rendered = 0;
    await for (final brandDir in assetsRoot.list()) {
      if (brandDir is! Directory) continue;
      final brand = brandDir.path.split('/').last;
      final samplePath = brandSamples[brand];
      if (samplePath == null || !File(samplePath).existsSync()) continue;
      await for (final camDir in brandDir.list()) {
        if (camDir is! Directory) continue;
        final metaFile = File('${camDir.path}/meta.json');
        if (!metaFile.existsSync()) continue;
        final meta =
            jsonDecode(metaFile.readAsStringSync()) as Map<String, dynamic>;
        if (meta['screen'] == null) continue;

        final camera = CameraAsset.fromJson(meta, directory: camDir.path);
        final imgFile = File('${camDir.path}/${camera.imageFile}');
        if (!imgFile.existsSync()) continue;
        final camImg = await loadImageFile(imgFile.path);
        final photo = await loadImageFile(samplePath);

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
        final png = await out.toByteData(format: ui.ImageByteFormat.png);
        final name = '${camera.slug}_framed.png';
        await File('${outDir.path}/$name')
            .writeAsBytes(png!.buffer.asUint8List());
        rendered++;
        // ignore: avoid_print
        print('rendered $name (${out.width}x${out.height})');
      }
    }
    expect(rendered, greaterThan(0));
  });
}
