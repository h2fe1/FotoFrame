/// 1.4px 描边的线性图标（自绘，避免 Material 图标的气质）。
library;

import 'package:flutter/material.dart';

enum StrokeGlyph { import, export, copy, plus }

class StrokeIcon extends StatelessWidget {
  const StrokeIcon(this.glyph, {super.key, this.size = 14, required this.color});

  final StrokeGlyph glyph;
  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(painter: _GlyphPainter(glyph, color)),
    );
  }
}

class _GlyphPainter extends CustomPainter {
  _GlyphPainter(this.glyph, this.color);

  final StrokeGlyph glyph;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 16.0;
    canvas.save();
    canvas.scale(scale);
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    switch (glyph) {
      case StrokeGlyph.import:
        // 箭头向下进托盘
        canvas.drawLine(const Offset(8, 2.5), const Offset(8, 10.5), paint);
        canvas.drawPath(
          Path()
            ..moveTo(4.8, 7.3)
            ..lineTo(8, 10.5)
            ..lineTo(11.2, 7.3),
          paint,
        );
        canvas.drawLine(const Offset(2.5, 12.5), const Offset(13.5, 12.5), paint);
      case StrokeGlyph.export:
        // 箭头向上出托盘
        canvas.drawLine(const Offset(8, 10.5), const Offset(8, 2.5), paint);
        canvas.drawPath(
          Path()
            ..moveTo(4.8, 5.7)
            ..lineTo(8, 2.5)
            ..lineTo(11.2, 5.7),
          paint,
        );
        canvas.drawLine(const Offset(2.5, 12.5), const Offset(13.5, 12.5), paint);
      case StrokeGlyph.copy:
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            const Rect.fromLTWH(5, 5, 8, 8),
            const Radius.circular(1.2),
          ),
          paint,
        );
        canvas.drawPath(
          Path()
            ..moveTo(3, 10.5)
            ..lineTo(3, 3.5)
            ..lineTo(10, 3.5),
          paint,
        );
      case StrokeGlyph.plus:
        canvas.drawLine(const Offset(8, 3), const Offset(8, 13), paint);
        canvas.drawLine(const Offset(3, 8), const Offset(13, 8), paint);
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_GlyphPainter old) =>
      old.glyph != glyph || old.color != color;
}
