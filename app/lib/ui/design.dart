/// 视觉基础：颜色 token、字体与通用文本样式。
///
/// 只描述「长什么样」，不含业务逻辑。浅色/深色两套 token 跟随 macOS 外观。
library;

import 'package:flutter/material.dart';

/// 一套完整的界面配色。
@immutable
class Palette {
  // 工具栏
  final Color toolbarTop;
  final Color toolbarBottom;
  final Color toolbarLine;
  final Color toolbarHi;
  final Color toolbarInk;
  final Color toolbarDim;
  final Color toolbarFaint;

  // 纸面 / 舞台
  final Color paper;
  final Color stage;

  // 墨色
  final Color ink;
  final Color ink2;
  final Color ink3;

  // 分隔线
  final Color line;
  final Color line2;

  // 强调
  final Color accent;
  final Color accentInk;
  final Color accentSoft;

  // 状态
  final Color ok;
  final Color error;

  // 成图投影
  final List<BoxShadow> frameShadow;

  const Palette({
    required this.toolbarTop,
    required this.toolbarBottom,
    required this.toolbarLine,
    required this.toolbarHi,
    required this.toolbarInk,
    required this.toolbarDim,
    required this.toolbarFaint,
    required this.paper,
    required this.stage,
    required this.ink,
    required this.ink2,
    required this.ink3,
    required this.line,
    required this.line2,
    required this.accent,
    required this.accentInk,
    required this.accentSoft,
    required this.ok,
    required this.error,
    required this.frameShadow,
  });

  static const light = Palette(
    toolbarTop: Color(0xFFFBF8F2),
    toolbarBottom: Color(0xFFF1ECE2),
    toolbarLine: Color(0xFFDCD4C4),
    toolbarHi: Color(0xE6FFFFFF),
    toolbarInk: Color(0xFF1A1814),
    toolbarDim: Color(0xFF5E564A),
    toolbarFaint: Color(0xFF8D8474),
    paper: Color(0xFFF7F4ED),
    stage: Color(0xFFFBF8F1),
    ink: Color(0xFF191713),
    ink2: Color(0xFF6F675A),
    ink3: Color(0xFFA49A88),
    line: Color(0xFFDDD5C6),
    line2: Color(0xFFE7E0D2),
    accent: Color(0xFFA2652F),
    accentInk: Color(0xFFFFF6E8),
    accentSoft: Color(0xFFF2E7D8),
    ok: Color(0xFF6F8A5B),
    error: Color(0xFFA3482E),
    frameShadow: [
      BoxShadow(
        color: Color(0x573C2D19),
        blurRadius: 50,
        spreadRadius: -18,
        offset: Offset(0, 26),
      ),
      BoxShadow(
        color: Color(0x1A3C2D19),
        blurRadius: 10,
        offset: Offset(0, 3),
      ),
    ],
  );

  static const dark = Palette(
    toolbarTop: Color(0xFF161514),
    toolbarBottom: Color(0xFF0F0E0D),
    toolbarLine: Color(0xFF2B2721),
    toolbarHi: Color(0x0EFFFFFF),
    toolbarInk: Color(0xFFECE6DA),
    toolbarDim: Color(0xFF8B8377),
    toolbarFaint: Color(0xFF5D564C),
    paper: Color(0xFF171512),
    stage: Color(0xFF0D0C0B),
    ink: Color(0xFFECE6DA),
    ink2: Color(0xFFA9A092),
    ink3: Color(0xFF70685C),
    line: Color(0xFF2E2A23),
    line2: Color(0xFF232019),
    accent: Color(0xFFD9A465),
    accentInk: Color(0xFF171208),
    accentSoft: Color(0xFF2C2318),
    ok: Color(0xFF8FAE74),
    error: Color(0xFFE08A6A),
    frameShadow: [
      BoxShadow(
        color: Color(0xBF000000),
        blurRadius: 60,
        spreadRadius: -14,
        offset: Offset(0, 30),
      ),
      BoxShadow(
        color: Color(0x66000000),
        blurRadius: 14,
        offset: Offset(0, 4),
      ),
    ],
  );

  static Palette of(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? dark : light;

  LinearGradient get toolbarGradient => LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [toolbarTop, toolbarBottom],
      );

  RadialGradient get stageGradient => RadialGradient(
        center: const Alignment(0, -0.04),
        radius: 0.8,
        colors: [stage, paper],
        stops: const [0.0, 0.72],
      );

  LinearGradient get stageGradientLinear => LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [stage, paper],
      );
}

/// 字体与排印常量。
class AppType {
  AppType._();

  static const String serif = 'Songti SC';
  static const String mono = 'SF Mono';

  /// 8.5px 全大写、大字距的小标签。
  static TextStyle label(Palette p) => TextStyle(
        fontSize: 8.5,
        height: 1.2,
        letterSpacing: 1.5,
        color: p.ink3,
        fontWeight: FontWeight.w400,
      );

  /// 衬线标题（机型名等）。
  static TextStyle serifTitle(Palette p, {double size = 23}) => TextStyle(
        fontFamily: serif,
        fontSize: size,
        height: 1.12,
        letterSpacing: 0.2,
        color: p.ink,
      );

  static TextStyle body(Palette p,
          {double size = 11.5, Color? color, FontWeight? weight}) =>
      TextStyle(
        fontSize: size,
        height: 1.35,
        letterSpacing: 0.05,
        color: color ?? p.ink,
        fontWeight: weight,
      );

  static TextStyle monoText(Palette p, {double size = 10.5, Color? color}) =>
      TextStyle(
        fontFamily: mono,
        fontSize: size,
        height: 1.3,
        letterSpacing: 0.03,
        color: color ?? p.ink,
      );
}
