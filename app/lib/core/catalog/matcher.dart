/// EXIF (Make, Model) → 相机资产匹配器。
///
/// 纯 Dart，零 Flutter 依赖。
library;

import 'camera_asset.dart';

/// 已知厂商内部型号 → 资产 slug 的别名表。
/// 用于 EXIF Model 与资产目录命名不一致的情况（主要是索尼）。
const Map<String, String> kModelAliases = {
  // Sony ILCE → slug
  'ILCE-7M3': 'a7m3',
  'ILCE-7RM3': 'a7r3',
  'ILCE-7RM3A': 'a7r3',
  'ILCE-7RM4': 'a7r4',
  'ILCE-7RM4A': 'a7r4',
  'ILCE-7RM5': 'a7r5',
  'ILCE-9': 'a9',
  'ILCE-9M2': 'a9m2',
  'ILCE-9M3': 'a9m3',
  'ILCE-7SM3': 'a7s3',
  'ILCE-7M4': 'a7m4',
  'ILCE-7C': 'a7c',
  'ILCE-7CM2': 'a7c2',
  'ILCE-7CR': 'a7cr',
  'ILCE-1': 'a1',
  'ILCE-6700': 'a6700',
  'ILCE-6600': 'a6600',
  'ZV-E1': 'zv-e1',
};

String normalizeExifString(String? raw) {
  if (raw == null) return '';
  // 去掉尾部 NUL、多余空白
  return raw.replaceAll(RegExp(r'[\x00-\x1F]'), '').trim();
}

class CameraMatch {
  final CameraAsset asset;

  /// 匹配可信度：exact > alias > pattern > none。
  final String how;

  const CameraMatch(this.asset, this.how);
}

class CameraCatalog {
  final List<CameraAsset> assets;

  /// slug → asset 快速索引。
  late final Map<String, CameraAsset> _bySlug = {
    for (final a in assets) a.slug: a,
  };

  /// 型号规范化名（小写、去空格横杠）→ asset，用于精确匹配。
  late final Map<String, CameraAsset> _byNormalizedModel = {
    for (final a in assets) _normalizeModel(a.model): a,
  };

  CameraCatalog(this.assets);

  CameraAsset? bySlug(String slug) => _bySlug[slug];

  static String _normalizeModel(String s) =>
      s.toLowerCase().replaceAll(RegExp(r'[\s\-_]+'), '');

  /// 主入口：根据 EXIF 找相机。
  CameraMatch? match({String? make, String? model}) {
    final mk = normalizeExifString(make);
    final md = normalizeExifString(model);
    if (md.isEmpty && mk.isEmpty) return null;

    // 1. 别名表（索尼等内部型号）
    final alias = kModelAliases[md.toUpperCase()];
    if (alias != null) {
      final hit = _bySlug[alias];
      if (hit != null) return CameraMatch(hit, 'alias');
    }

    // 2. 规范化型号精确匹配（"LEICA M11" / "Leica M11" / "leicam11"）
    final norm = _normalizeModel(md);
    final exact = _byNormalizedModel[norm];
    if (exact != null) return CameraMatch(exact, 'exact');

    // 2.5 后缀匹配：EXIF 型号常以品牌开头（"RICOH GR III" vs 资产 "GR III"）。
    // 取型号名最长的命中，避免 "GR III" 抢走 "GR IIIx"。
    CameraAsset? suffixHit;
    var suffixLen = 3; // 至少 4 个字符才考虑，防止误配
    for (final a in assets) {
      final an = _normalizeModel(a.model);
      if (an.length > suffixLen && norm.endsWith(an)) {
        suffixLen = an.length;
        suffixHit = a;
      }
    }
    if (suffixHit != null) return CameraMatch(suffixHit, 'suffix');

    // 3. 各资产自带的正则规则
    CameraAsset? patternHit;
    var patternScore = 0;
    for (final a in assets) {
      final makeOk = a.exif.makePatterns.isEmpty ||
          a.exif.makePatterns.any((p) => RegExp(p, caseSensitive: false).hasMatch(mk));
      if (!makeOk) continue;
      for (final p in a.exif.modelPatterns) {
        final re = RegExp(p, caseSensitive: false);
        if (re.hasMatch(md)) {
          // 规则越具体（模式越长）越优先
          if (p.length > patternScore) {
            patternScore = p.length;
            patternHit = a;
          }
        }
      }
    }
    if (patternHit != null) return CameraMatch(patternHit, 'pattern');

    return null;
  }
}
