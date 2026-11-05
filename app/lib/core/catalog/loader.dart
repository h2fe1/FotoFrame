/// Flutter 侧的资产目录加载：读 app/assets/cameras/catalog.json。
library;

import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import 'camera_asset.dart';
import 'matcher.dart';

const kCatalogAssetPath = 'assets/cameras/catalog.json';

Future<CameraCatalog> loadBundledCatalog() async {
  final raw = await rootBundle.loadString(kCatalogAssetPath);
  final list = jsonDecode(raw) as List;
  final assets = [
    for (final item in list)
      CameraAsset.fromJson(
        (item as Map).cast<String, dynamic>(),
        directory: 'assets/cameras',
      ),
  ];
  return CameraCatalog(assets);
}
