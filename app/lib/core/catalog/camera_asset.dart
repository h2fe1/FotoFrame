/// 相机资产描述（对应 `assets/cameras/<brand>/<slug>/meta.json`）。
///
/// 纯数据模型，零 Flutter 依赖，全平台复用。
library;

/// 照片在屏幕显示区里的填充方式。
///
/// - [cover]：铺满显示区，超出部分裁掉（默认，出图最满）。
/// - [contain]：完整显示照片，比例不符时留黑边 —— 和真实相机回放一致。
enum ScreenFit {
  cover('cover'),
  contain('contain');

  final String name;
  const ScreenFit(this.name);

  static ScreenFit parse(String? raw) => raw == 'contain'
      ? ScreenFit.contain
      : ScreenFit.cover;
}

class ScreenQuad {
  /// 屏幕显示区域四角像素坐标，顺序：左上、右上、右下、左下。
  final List<(double, double)> corners;

  const ScreenQuad(this.corners);

  factory ScreenQuad.fromJson(List<dynamic> json) {
    assert(json.length == 4);
    return ScreenQuad([
      for (final p in json) ((p[0] as num).toDouble(), (p[1] as num).toDouble()),
    ]);
  }

  List<List<double>> toJson() => [
        for (final p in corners) [p.$1, p.$2],
      ];

  /// 外接矩形（用于快速排版估算）。
  (double, double, double, double) get bounds {
    final xs = corners.map((c) => c.$1);
    final ys = corners.map((c) => c.$2);
    final minX = xs.reduce((a, b) => a < b ? a : b);
    final maxX = xs.reduce((a, b) => a > b ? a : b);
    final minY = ys.reduce((a, b) => a < b ? a : b);
    final maxY = ys.reduce((a, b) => a > b ? a : b);
    return (minX, minY, maxX - minX, maxY - minY);
  }
}

class ExifSignature {
  /// 大小写不敏感正则，匹配 EXIF Make 字段。
  final List<String> makePatterns;

  /// 大小写不敏感正则，匹配 EXIF Model 字段。
  final List<String> modelPatterns;

  const ExifSignature({
    required this.makePatterns,
    required this.modelPatterns,
  });

  factory ExifSignature.fromJson(Map<String, dynamic> json) => ExifSignature(
        makePatterns:
            (json['make_patterns'] as List? ?? const []).cast<String>(),
        modelPatterns:
            (json['model_patterns'] as List? ?? const []).cast<String>(),
      );
}

class CameraAsset {
  final String brand;
  final String model; // 展示用完整型号名
  final String slug; // 目录名 / 内部 id
  final String imageFile; // 背面图文件名（相对本资产目录）
  final int width;
  final int height;
  final ScreenQuad screen;

  /// 屏幕**开口**（玻璃内沿）四角，可选；meta.json `screen.opening`。
  ///
  /// 产品图里玻璃往往比 3:2 显示区大一圈，露出玻璃的反光和渐变，
  /// 合成后就是照片外面那圈"灰边"。标注了开口的机型，渲染时会先把
  /// 开口整块压黑，再把照片贴进 [screen]，那圈就变成纯黑边框。
  final ScreenQuad? opening;

  /// 照片在屏幕里的填充方式（meta.json `screen.fit`，缺省为 cover）。
  final ScreenFit screenFit;

  final ExifSignature exif;
  final String? sourceUrl;
  final String? sourcePage;

  /// 资产目录绝对/相对路径（由加载方注入，运行时拼图片路径用）。
  final String directory;

  const CameraAsset({
    required this.brand,
    required this.model,
    required this.slug,
    required this.imageFile,
    required this.width,
    required this.height,
    required this.screen,
    this.opening,
    this.screenFit = ScreenFit.cover,
    required this.exif,
    this.sourceUrl,
    this.sourcePage,
    required this.directory,
  });

  String get imagePath => '$directory/$imageFile';

  /// 界面展示用的完整机型名。
  ///
  /// 部分 meta.json 的 model 本身已带品牌（如 "Leica M10-R"、"Sony A7 IV"），
  /// 直接拼 brand 会出现 "Leica Leica M10-R" 这种重复。
  String get displayName =>
      model.toLowerCase().startsWith(brand.toLowerCase())
          ? model
          : '$brand $model';

  factory CameraAsset.fromJson(Map<String, dynamic> json,
      {required String directory}) {
    return CameraAsset(
      brand: json['brand'] as String,
      model: json['model'] as String,
      slug: json['slug'] as String,
      imageFile: (json['image'] as String?) ?? 'back.png',
      width: (json['width'] as num).toInt(),
      height: (json['height'] as num).toInt(),
      screen: ScreenQuad.fromJson(json['screen']['quad'] as List),
      opening: (json['screen'] as Map?)?['opening'] == null
          ? null
          : ScreenQuad.fromJson(
              (json['screen'] as Map)['opening'] as List),
      screenFit: ScreenFit.parse(
          (json['screen'] as Map?)?['fit'] as String?),
      exif: ExifSignature.fromJson(
          (json['exif'] as Map?)?.cast<String, dynamic>() ?? const {}),
      sourceUrl: json['source_url'] as String?,
      sourcePage: json['source_page'] as String?,
      directory: directory,
    );
  }
}
