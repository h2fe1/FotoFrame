/// FotoFrame 入口。
///
/// 流程：导入照片（拖拽/选择）→ EXIF 自动识别机型 → 预览套壳 → 导出/复制。
/// 支持批量：多选导入后一次性导出整个队列。
/// 外观跟随 macOS 浅色/深色，切换时自动重建。
library;

import 'package:flutter/material.dart';

import 'ui/design.dart';
import 'ui/home_page.dart';

void main() {
  runApp(const FotoFrameApp());
}

class FotoFrameApp extends StatelessWidget {
  const FotoFrameApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FotoFrame',
      debugShowCheckedModeBanner: false,
      theme: _theme(Palette.light, Brightness.light),
      darkTheme: _theme(Palette.dark, Brightness.dark),
      themeMode: ThemeMode.system,
      // 设计里没有滚动条（仍可滚轮滚动），避免它在悬停时闪现
      scrollBehavior: const MaterialScrollBehavior().copyWith(
        scrollbars: false,
      ),
      home: const HomePage(),
    );
  }

  ThemeData _theme(Palette p, Brightness brightness) {
    return ThemeData(
      brightness: brightness,
      useMaterial3: true,
      scaffoldBackgroundColor: p.paper,
      canvasColor: p.paper,
      splashFactory: NoSplash.splashFactory,
      highlightColor: Colors.transparent,
      hoverColor: Colors.transparent,
      colorScheme: ColorScheme.fromSeed(
        seedColor: p.accent,
        brightness: brightness,
      ).copyWith(
        surface: p.paper,
        onSurface: p.ink,
        primary: p.accent,
        onPrimary: p.accentInk,
      ),
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: p.accent,
        selectionColor: p.accent.withValues(alpha: 0.25),
      ),
    );
  }
}
