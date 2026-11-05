/// 核心逻辑单元测试：EXIF 解析、机型匹配、布局几何。
library;

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/core/catalog/camera_asset.dart';
import 'package:fotoframe/core/catalog/matcher.dart';
import 'package:fotoframe/core/compose/layout.dart';
import 'package:fotoframe/core/exif/exif_reader.dart';

/// 构造一个带 EXIF 的极简 JPEG（Make/Model 可配）。
Uint8List buildTestJpeg({String make = '', String model = ''}) {
  // TIFF 小端：II*\0 + IFD0 偏移 8
  final entries = <List<int>>[];
  final dataParts = <int>[];
  var dataOffset = 8 + 2 + 12 * 2 + 4; // IFD0 后的数据区起点

  void addAscii(int tag, String value) {
    final bytes = [...value.codeUnits, 0];
    if (bytes.length <= 4) {
      final padded = List.filled(4, 0);
      for (var i = 0; i < bytes.length; i++) {
        padded[i] = bytes[i];
      }
      entries.add([tag, 2, bytes.length, ...padded]);
    } else {
      entries.add([tag, 2, bytes.length, dataOffset & 0xFF,
          (dataOffset >> 8) & 0xFF, (dataOffset >> 16) & 0xFF, (dataOffset >> 24) & 0xFF]);
      dataParts.addAll(bytes);
      dataOffset += bytes.length;
    }
  }

  addAscii(0x010F, make);
  addAscii(0x0110, model);

  final tiff = <int>[
    0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // 头
    0x02, 0x00, // 2 个条目
  ];
  for (final e in entries) {
    tiff.add(e[0] & 0xFF);
    tiff.add((e[0] >> 8) & 0xFF);
    tiff.add(e[1] & 0xFF);
    tiff.add((e[1] >> 8) & 0xFF);
    tiff.add(e[2] & 0xFF);
    tiff.add((e[2] >> 8) & 0xFF);
    tiff.add((e[2] >> 16) & 0xFF);
    tiff.add((e[2] >> 24) & 0xFF);
    tiff.addAll(e.sublist(3));
  }
  tiff.addAll([0, 0, 0, 0]); // next IFD = 0
  tiff.addAll(dataParts);

  // 包一层 JPEG：SOI + APP1
  final exifPayload = [0x45, 0x78, 0x69, 0x66, 0, 0, ...tiff];
  final app1Len = exifPayload.length + 2;
  final jpeg = <int>[
    0xFF, 0xD8,
    0xFF, 0xE1, (app1Len >> 8) & 0xFF, app1Len & 0xFF,
    ...exifPayload,
    0xFF, 0xD9,
  ];
  return Uint8List.fromList(jpeg);
}

CameraAsset testAsset(String brand, String model, String slug,
        {List<String>? makePatterns, List<String>? modelPatterns}) =>
    CameraAsset(
      brand: brand,
      model: model,
      slug: slug,
      imageFile: 'back.png',
      width: 2000,
      height: 1300,
      screen: const ScreenQuad([(400, 300), (1200, 300), (1200, 900), (400, 900)]),
      exif: ExifSignature(
        makePatterns: makePatterns ?? const [],
        modelPatterns: modelPatterns ?? const [],
      ),
      directory: 'assets/cameras',
    );

void main() {
  group('ExifReader', () {
    test('解析 JPEG EXIF Make/Model（小端 TIFF）', () {
      final jpeg = buildTestJpeg(make: 'SONY', model: 'ILCE-7M4');
      final info = ExifReader.read(jpeg);
      expect(info.make, 'SONY');
      expect(info.model, 'ILCE-7M4');
    });

    test('解析徕卡机型', () {
      final jpeg = buildTestJpeg(make: 'LEICA CAMERA AG', model: 'LEICA M11');
      final info = ExifReader.read(jpeg);
      expect(info.model, 'LEICA M11');
    });

    test('解析真实样片（tools/make_samples.py 生成）', () {
      final cases = {
        '../samples/sony_a7m4.jpg': ('SONY', 'ILCE-7M4'),
        '../samples/leica_m11.jpg': ('LEICA CAMERA AG', 'LEICA M11'),
        '../samples/ricoh_gr3x.jpg':
            ('RICOH IMAGING COMPANY, LTD.', 'RICOH GR IIIx'),
        '../samples/hasselblad_x2d2.jpg': ('Hasselblad', 'X2D II 100C'),
      };
      for (final e in cases.entries) {
        final f = File(e.key);
        if (!f.existsSync()) continue; // 样片未生成时跳过
        final info = ExifReader.read(f.readAsBytesSync());
        expect((info.make, info.model), e.value, reason: e.key);
      }
    });
  });

  group('CameraCatalog.match', () {
    final catalog = CameraCatalog([
      testAsset('Sony', 'A7 IV', 'a7m4'),
      testAsset('Sony', 'A7R V', 'a7r5'),
      testAsset('Leica', 'Leica M11', 'm11'),
      testAsset('Leica', 'Leica M11-P', 'm11-p',
          modelPatterns: [r'^LEICA M11-P$']),
      testAsset('Ricoh', 'GR III', 'gr3',
          modelPatterns: [r'^RICOH GR III$']),
      testAsset('Ricoh', 'GR IIIx', 'gr3x',
          modelPatterns: [r'^RICOH GR IIIx$']),
    ]);

    test('索尼 ILCE 别名匹配', () {
      expect(catalog.match(make: 'SONY', model: 'ILCE-7M4')?.asset.slug, 'a7m4');
      expect(catalog.match(make: 'SONY', model: 'ILCE-7RM5')?.asset.slug, 'a7r5');
    });

    test('徕卡规范化精确匹配', () {
      final hit = catalog.match(make: 'LEICA CAMERA AG', model: 'LEICA M11');
      expect(hit?.asset.slug, 'm11');
    });

    test('理光正则匹配，GR IIIx 不被 GR III 抢走', () {
      expect(
          catalog.match(make: 'RICOH IMAGING COMPANY, LTD.', model: 'RICOH GR IIIx')?.asset.slug,
          'gr3x');
      expect(
          catalog.match(make: 'RICOH IMAGING COMPANY, LTD.', model: 'RICOH GR III')?.asset.slug,
          'gr3');
    });

    test('后缀匹配：EXIF 带品牌前缀的资产型号', () {
      // 仅靠前缀不同的型号名也能命中（无 exif 规则的资产）
      final c2 = CameraCatalog([
        testAsset('Ricoh', 'GR III', 'gr3'),
        testAsset('Ricoh', 'GR IIIx', 'gr3x'),
        testAsset('Ricoh', 'GR IV', 'gr4'),
      ]);
      expect(c2.match(make: 'RICOH', model: 'RICOH GR IIIx')?.asset.slug, 'gr3x');
      expect(c2.match(make: 'RICOH', model: 'RICOH GR III')?.asset.slug, 'gr3');
      expect(c2.match(make: 'RICOH', model: 'RICOH GR IV')?.asset.slug, 'gr4');
      // 哈苏
      final c3 = CameraCatalog([
        testAsset('Hasselblad', 'X2D 100C', 'x2d'),
        testAsset('Hasselblad', 'X2D II 100C', 'x2d2'),
      ]);
      expect(c3.match(make: 'Hasselblad', model: 'X2D 100C')?.asset.slug, 'x2d');
      expect(
          c3.match(make: 'Hasselblad', model: 'X2D II 100C')?.asset.slug, 'x2d2');
    });

    test('无 EXIF 返回 null', () {
      expect(catalog.match(make: null, model: null), isNull);
      expect(catalog.match(make: 'Canon', model: 'EOS R5'), isNull);
    });
  });

  group('CameraAsset.displayName', () {
    test('model 已含品牌时不再重复拼接', () {
      expect(testAsset('Leica', 'Leica M10-R', 'm10-r').displayName,
          'Leica M10-R');
      expect(
          testAsset('Sony', 'Sony A7 IV (ILCE-7M4)', 'a7m4').displayName,
          'Sony A7 IV (ILCE-7M4)');
      expect(testAsset('Ricoh', 'GR III', 'gr3').displayName, 'Ricoh GR III');
    });
  });

  group('LayoutEngine', () {
    test('单应矩阵：单位正方形映射到轴对齐矩形', () {
      final h = LayoutEngine.homographyToQuad(
          [(100, 50), (300, 50), (300, 200), (100, 200)]);
      // 点 (1,1) 应映射到 (300,200)
      final x = (h[0] * 1 + h[1] * 1 + h[2]) / (h[6] * 1 + h[7] * 1 + h[8]);
      final y = (h[3] * 1 + h[4] * 1 + h[5]) / (h[6] * 1 + h[7] * 1 + h[8]);
      expect(x, closeTo(300, 1e-6));
      expect(y, closeTo(200, 1e-6));
    });

    test('整体布局：相机区在上、照片区在下且不重叠', () {
      final cam = testAsset('Leica', 'Leica M11', 'm11');
      final layout = LayoutEngine.compute(
        theme: const FrameTheme(),
        camera: cam,
        photoW: 6000,
        photoH: 4000,
      );
      expect(layout.canvasW, greaterThan(0));
      expect(layout.canvasH, greaterThan(layout.canvasW));
      expect(layout.photoRect.y,
          greaterThan(layout.cameraRect.y + layout.cameraRect.h));
      // 默认版式：照片左右下三边出血贴边
      expect(layout.photoRect.x, 0);
      expect(layout.photoRect.w, closeTo(layout.canvasW, 1e-9));
      expect(layout.photoRect.x + layout.photoRect.w,
          closeTo(layout.canvasW, 1e-9));
      expect(layout.photoRect.y + layout.photoRect.h,
          closeTo(layout.canvasH, 1e-9));
      // 照片保持原图宽高比（允许取整带来的 1px 误差）
      expect(layout.photoRect.w / layout.photoRect.h, closeTo(1.5, 0.001));
    });

    test('照片留白 > 0 时三边留白等宽且不贴边', () {
      final cam = testAsset('Leica', 'Leica M11', 'm11');
      final layout = LayoutEngine.compute(
        theme: const FrameTheme(photoMarginRatio: 0.04),
        camera: cam,
        photoW: 6000,
        photoH: 4000,
      );
      final leftMargin = layout.photoRect.x;
      final rightMargin = layout.canvasW - layout.photoRect.x - layout.photoRect.w;
      final bottomMargin =
          layout.canvasH - layout.photoRect.y - layout.photoRect.h;
      expect(leftMargin, greaterThan(0));
      expect(rightMargin, closeTo(leftMargin, 1));
      expect(bottomMargin, closeTo(leftMargin, 1));
    });
  });
}
