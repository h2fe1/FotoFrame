/// 机型匹配测试：现有资产的正向匹配 + 近型号防误配。
///
/// 目前只保留 Hasselblad X2D 和 Ricoh GR III 两台，其余机型在重建中
/// （旧资产归档在 `.work/cameras_archive/`）。
library;

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/core/catalog/camera_asset.dart';
import 'package:fotoframe/core/catalog/matcher.dart';

CameraCatalog loadCatalogFromDisk() {
  final f = File('assets/cameras/catalog.json');
  if (!f.existsSync()) {
    throw StateError('assets/cameras/catalog.json 不存在，先跑 tools/sync_assets.sh');
  }
  final list = jsonDecode(f.readAsStringSync()) as List;
  return CameraCatalog([
    for (final item in list)
      CameraAsset.fromJson((item as Map).cast<String, dynamic>(),
          directory: 'assets/cameras'),
  ]);
}

void main() {
  late CameraCatalog catalog;

  setUpAll(() {
    catalog = loadCatalogFromDisk();
  });

  test('资产基本完整（尺寸/屏幕四角齐全）', () {
    expect(catalog.assets.length, greaterThanOrEqualTo(2));
    for (final a in catalog.assets) {
      expect(a.width, greaterThan(0), reason: a.slug);
      expect(a.height, greaterThan(0), reason: a.slug);
      final xs = a.screen.corners.map((c) => c.$1);
      final ys = a.screen.corners.map((c) => c.$2);
      expect(xs.reduce((p, q) => p < q ? p : q), greaterThanOrEqualTo(0),
          reason: a.slug);
      expect(ys.reduce((p, q) => p < q ? p : q), greaterThanOrEqualTo(0),
          reason: a.slug);
      expect(xs.reduce((p, q) => p > q ? p : q), lessThanOrEqualTo(a.width),
          reason: a.slug);
      expect(ys.reduce((p, q) => p > q ? p : q), lessThanOrEqualTo(a.height),
          reason: a.slug);
    }
  });

  /// 每台资产都能被「厂商会写进 EXIF 的 Make/Model」匹配到。
  const realistic = <String, (String, String)>{
    'x2d': ('Hasselblad', 'X2D 100C'),
    'gr3': ('RICOH IMAGING COMPANY, LTD.', 'RICOH GR III'),
    'a7m4': ('SONY', 'ILCE-7M4'),
    'q3': ('LEICA CAMERA AG', 'LEICA Q3'),
    's1r2': ('Panasonic', 'DC-S1RM2'),
  };

  test('每台资产都能被真实 EXIF 匹配', () {
    final missing = <String>[];
    for (final entry in realistic.entries) {
      if (catalog.bySlug(entry.key) == null) continue;
      final (make, model) = entry.value;
      final hit = catalog.match(make: make, model: model);
      if (hit == null || hit.asset.slug != entry.key) {
        missing.add('${entry.key}: 期望命中，实际 ${hit?.asset.slug ?? "null"}');
      }
    }
    expect(missing, isEmpty, reason: missing.join('\n'));
  });

  test('Q3 43 复用 Q3 资产', () {
    final hit = catalog.match(make: 'LEICA CAMERA AG', model: 'LEICA Q3 43');
    expect(hit?.asset.slug, 'q3');
  });

  test('已删除的机型不再命中', () {
    // 这些机型目前没有资产（归档/待重建）
    const removed = <(String, String)>[
      ('LEICA CAMERA AG', 'LEICA M11'),
      ('Canon', 'Canon EOS R5m2'),
      ('FUJIFILM', 'X-T5'),
      ('NIKON CORPORATION', 'NIKON Z 8'),
    ];
    for (final (make, model) in removed) {
      final hit = catalog.match(make: make, model: model);
      expect(hit, isNull, reason: '$make $model 应无资产，实际命中 ${hit?.asset.slug}');
    }
  });

  test('型号不认识时不匹配', () {
    expect(catalog.match(make: 'Canon', model: 'Canon EOS R1'), isNull);
    expect(catalog.match(make: 'Hasselblad', model: 'X1D II 50C'), isNull);
    expect(catalog.match(make: 'RICOH IMAGING COMPANY, LTD.', model: 'RICOH GR DIGITAL'),
        isNull);
  });

  test('空 EXIF 返回 null', () {
    expect(catalog.match(make: null, model: null), isNull);
    expect(catalog.match(make: '', model: ''), isNull);
    expect(catalog.match(make: '   ', model: '\x00'), isNull);
  });
}