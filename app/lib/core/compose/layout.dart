/// 版面几何计算（纯 Dart）。
///
/// 职责：
/// - 计算输出画布上「相机区 / 照片区」的布局矩形
/// - 计算照片 → 屏幕四边形的单应变换（3x3 矩阵，行主序）
///
/// 渲染层（Flutter Canvas / Skia）拿到这些数据直接画即可。
library;

import '../catalog/camera_asset.dart';

/// 固定背景色（偏中性的相纸灰）。
///
/// 背景不提供选择：相机背面图用 multiply 融合白底，
/// 需要固定在一个浅色中性底上，深色底会让黑机身隐形。
const int kFrameBackgroundColor = 0xFFE9E9E6;

/// 输出主题参数。
class FrameTheme {
  /// 背景色 ARGB（固定值，界面不提供选择）。
  final int backgroundColor;

  /// 输出长边像素。
  final int longEdge;

  /// 相机图四周的留白（相对画布宽）。
  ///
  /// 四边等宽：越大相机越小、边框越宽。
  final double cameraMarginRatio;

  /// 底部照片在左/右/下三边的留白（相对画布宽）。
  ///
  /// 0（默认）= 三边出血贴边：照片宽度即画布宽度，底边即画布底边。
  final double photoMarginRatio;

  const FrameTheme({
    this.backgroundColor = kFrameBackgroundColor,
    this.longEdge = 2400,
    this.cameraMarginRatio = 0.10,
    this.photoMarginRatio = 0.0,
  }) : assert(cameraMarginRatio >= 0 && cameraMarginRatio < 0.5);

  FrameTheme copyWith({
    int? backgroundColor,
    int? longEdge,
    double? cameraMarginRatio,
    double? photoMarginRatio,
  }) {
    return FrameTheme(
      backgroundColor: backgroundColor ?? this.backgroundColor,
      longEdge: longEdge ?? this.longEdge,
      cameraMarginRatio: cameraMarginRatio ?? this.cameraMarginRatio,
      photoMarginRatio: photoMarginRatio ?? this.photoMarginRatio,
    );
  }
}

/// 版式松紧度预设。
enum LayoutDensity {
  compact('紧凑', 0.06),
  normal('标准', 0.10),
  relaxed('宽松', 0.14);

  final String label;
  final double cameraMarginRatio;

  const LayoutDensity(this.label, this.cameraMarginRatio);
}

class Rect {
  final double x, y, w, h;
  const Rect(this.x, this.y, this.w, this.h);
  double get cx => x + w / 2;
  double get cy => y + h / 2;
  @override
  String toString() => 'Rect($x, $y, $w, $h)';
}

class FrameLayout {
  /// 输出画布尺寸。
  final int canvasW, canvasH;

  /// 相机背面图绘制区域（已含整体缩放与居中）。
  final Rect cameraRect;

  /// 屏幕四边形映射到画布坐标后的四角（照片透视变换目标）。
  final List<(double, double)> screenQuadOnCanvas;

  /// 屏幕开口（玻璃内沿）映射到画布坐标后的四角；机型没标注开口时为 null。
  final List<(double, double)>? openingQuadOnCanvas;

  /// 底部照片绘制区域。
  final Rect photoRect;

  const FrameLayout({
    required this.canvasW,
    required this.canvasH,
    required this.cameraRect,
    required this.screenQuadOnCanvas,
    this.openingQuadOnCanvas,
    required this.photoRect,
  });
}

class LayoutEngine {
  /// 计算整版布局。
  ///
  /// [photoW]/[photoH]：原始照片尺寸（已按 EXIF orientation 旋转后的显示尺寸）。
  /// [camera]：相机资产（提供背面图宽高与屏幕四边形）。
  static FrameLayout compute({
    required FrameTheme theme,
    required CameraAsset camera,
    required int photoW,
    required int photoH,
  }) {
    // 画布：照片按长边缩放到 longEdge 决定比例，再加顶部区域。
    final photoAspect = photoW / photoH;
    final photoScale = theme.longEdge /
        (photoW >= photoH ? photoW : photoH);
    final scaledPhotoW = photoW * photoScale;

    // 画布宽度 = 照片宽度 + 左右留白。默认留白为 0，照片左右出血贴边。
    // 取整到像素，避免渲染时边缘出现不足 1px 的背景缝。
    final canvasW =
        (scaledPhotoW / (1 - 2 * theme.photoMarginRatio)).roundToDouble();

    // 相机图：四周留等宽白边（相对画布宽），相机按宽度等比缩放。
    final camMargin = canvasW * theme.cameraMarginRatio;
    final camW = canvasW - 2 * camMargin;
    final scale = camW / camera.width;
    final camH = camera.height * scale;
    final camX = camMargin;
    final camY = camMargin;
    final cameraRect = Rect(camX, camY, camW, camH);

    // 屏幕四边形 → 画布坐标
    final quadOnCanvas = [
      for (final c in camera.screen.corners)
        (camX + c.$1 * scale, camY + c.$2 * scale),
    ];

    // 屏幕开口（玻璃内沿）→ 画布坐标；没标注就是 null。
    //
    // 开口四周取**等宽**：照片贴着显示区不动，外面一圈黑边四边一样宽，
    // 看着才是"照片在屏幕正中"。逐边用检测值会歪——玻璃内沿的标注/检测
    // 四边本来就差 10~20px（有的边直接检成 0），照抄就会一边宽一边窄。
    // 宽度取四边中位数：多数边能盖住玻璃，个别更宽的边可能留 1~2px，
    // 但胜在永远居中、也不会把机身吃掉。
    final openingOnCanvas = camera.opening == null
        ? null
        : _uniformOpening(quadOnCanvas, [
            for (final c in camera.opening!.corners)
              (camX + c.$1 * scale, camY + c.$2 * scale),
          ]);

    // 底部照片：宽度 = 画布宽 - 左右留白；高度按原图比例。
    // 宽高都取整，保证「贴边」时左右/底边都不留半像素缝。
    final photoRectW = canvasW * (1 - 2 * theme.photoMarginRatio);
    final photoRectX = canvasW * theme.photoMarginRatio;
    final photoRectH = (photoRectW / photoAspect).roundToDouble();
    // 照片紧接相机下方的等宽留白，四边边框保持一致。
    final photoY = (camY + camH + camMargin).roundToDouble();

    // 底部留白与左右一致（默认 0 → 照片底边即画布底边）
    final canvasH =
        photoY + photoRectH + canvasW * theme.photoMarginRatio;

    return FrameLayout(
      canvasW: canvasW.round(),
      canvasH: canvasH.round(),
      cameraRect: cameraRect,
      screenQuadOnCanvas: quadOnCanvas,
      openingQuadOnCanvas: openingOnCanvas,
      photoRect: Rect(photoRectX, photoY, photoRectW, photoRectH),
    );
  }

  /// 把「逐边不等宽的开口」变成「以显示区为中心、四边等宽的黑边」。
  ///
  /// 边宽取四边内缩量的中位数：既不会被某一条误检成 0 的边带偏，
  /// 也不会把机身吃掉。
  static List<(double, double)> _uniformOpening(
      List<(double, double)> quad, List<(double, double)> opening) {
    final (qx, qy, qw, qh) = _bounds(quad);
    final (ox, oy, ow, oh) = _bounds(opening);
    final margins = [qx - ox, (ox + ow) - (qx + qw), qy - oy, (oy + oh) - (qy + qh)]
      ..sort();
    final m0 = (margins[1] + margins[2]) / 2; // 中间两个的中值
    final m = m0 < 0 ? 0.0 : m0;
    return [
      (qx - m, qy - m),
      (qx + qw + m, qy - m),
      (qx + qw + m, qy + qh + m),
      (qx - m, qy + qh + m),
    ];
  }

  /// 四边形外接矩形 (x, y, w, h)。
  static (double, double, double, double) _bounds(
      List<(double, double)> q) {
    var minX = q.first.$1, maxX = q.first.$1;
    var minY = q.first.$2, maxY = q.first.$2;
    for (final (x, y) in q) {
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
    }
    return (minX, minY, maxX - minX, maxY - minY);
  }

  /// 计算把单位矩形 (0,0)-(1,1) 映射到四边形 [dst] 的单应矩阵（3x3，行主序）。
  /// 渲染时把照片先缩放到 (0,0)-(1,1) 再套此矩阵即可。
  static List<double> homographyToQuad(List<(double, double)> dst) {
    assert(dst.length == 4);
    // 源点：单位正方形四角（与 dst 同序：左上、右上、右下、左下）
    const src = [(0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0)];
    return _homography(src, dst);
  }

  /// 由 4 对点求单应矩阵（DLT 解法，8 未知数线性方程组）。
  static List<double> _homography(
      List<(double, double)> src, List<(double, double)> dst) {
    // A * h = b，8x8
    final a = List.generate(8, (_) => List.filled(8, 0.0));
    final b = List.filled(8, 0.0);
    for (var i = 0; i < 4; i++) {
      final (x, y) = src[i];
      final (u, v) = dst[i];
      final r1 = 2 * i, r2 = 2 * i + 1;
      a[r1][0] = x; a[r1][1] = y; a[r1][2] = 1;
      a[r1][6] = -u * x; a[r1][7] = -u * y;
      b[r1] = u;
      a[r2][3] = x; a[r2][4] = y; a[r2][5] = 1;
      a[r2][6] = -v * x; a[r2][7] = -v * y;
      b[r2] = v;
    }
    final h = _solve8(a, b);
    return [h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0];
  }

  /// 高斯消元解 8x8。
  static List<double> _solve8(List<List<double>> a, List<double> b) {
    const n = 8;
    for (var col = 0; col < n; col++) {
      var pivot = col;
      for (var r = col + 1; r < n; r++) {
        if (a[r][col].abs() > a[pivot][col].abs()) pivot = r;
      }
      if (pivot != col) {
        final tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp;
        final t = b[col]; b[col] = b[pivot]; b[pivot] = t;
      }
      final d = a[col][col];
      if (d.abs() < 1e-12) {
        throw StateError('homography: singular matrix (四点共线?)');
      }
      for (var r = col + 1; r < n; r++) {
        final f = a[r][col] / d;
        for (var c = col; c < n; c++) {
          a[r][c] -= f * a[col][c];
        }
        b[r] -= f * b[col];
      }
    }
    final x = List.filled(n, 0.0);
    for (var r = n - 1; r >= 0; r--) {
      var s = b[r];
      for (var c = r + 1; c < n; c++) {
        s -= a[r][c] * x[c];
      }
      x[r] = s / a[r][r];
    }
    return x;
  }

  /// 照片 → 屏幕的映射参数。
  ///
  /// 把照片像素坐标映射到单位正方形（屏幕四边形的归一化坐标）：
  ///   u = px * sx + tx
  ///   v = py * sy + ty
  ///
  /// 按相机资产的 [ScreenFit] 选择填充方式：
  /// - cover：保持比例铺满显示区，超出部分裁掉；
  /// - contain：完整显示照片，比例不符时由渲染层补黑边（真实相机回放的样子）。
  static (double sx, double tx, double sy, double ty) screenMapping({
    required CameraAsset camera,
    required int photoW,
    required int photoH,
  }) {
    final (_, _, sw, sh) = camera.screen.bounds;
    final screenAspect = sw / sh;
    final photoAspect = photoW / photoH;
    final contain = camera.screenFit == ScreenFit.contain;

    // 单位正方形（显示区）内，照片映射后的宽高（>1 表示超出被裁，<1 表示留边）
    double rw, rh;
    final wider = photoAspect > screenAspect;
    if (contain) {
      // 整体放进显示区：宽的贴宽、高的贴高
      rw = wider ? 1.0 : photoAspect / screenAspect;
      rh = wider ? screenAspect / photoAspect : 1.0;
    } else {
      // 铺满显示区：宽的贴高、高的贴宽（超出部分被裁掉）
      rw = wider ? photoAspect / screenAspect : 1.0;
      rh = wider ? 1.0 : screenAspect / photoAspect;
    }

    final rx = (1 - rw) / 2;
    final ry = (1 - rh) / 2;
    return (
      rw / photoW,
      rx,
      rh / photoH,
      ry,
    );
  }
}
