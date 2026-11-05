/// UI 截图测试：把完整界面渲染成 PNG，方便人工检查布局（浅色 / 深色各一张）。
///
/// 运行：tools/flutter.sh test test/ui_snapshot_test.dart --update-goldens
/// 输出：test/goldens/ui_home_light.png、ui_home_dark.png
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/main.dart';
import 'package:fotoframe/ui/home_page.dart';

Future<void> _loadFont(String path, List<String> families) async {
  final f = File(path);
  if (!f.existsSync()) return;
  final bytes = f.readAsBytesSync();
  for (final family in families) {
    final loader = FontLoader(family)
      ..addFont(Future.value(ByteData.sublistView(bytes)));
    await loader.load();
  }
}

Future<void> _loadSystemFonts() async {
  // 测试环境默认用 Ahem，文字会变成方块；加载含中文的系统字体让截图可读。
  // 衬线标题走 Songti，其余（默认 Roboto 回退）用 Arial Unicode 覆盖中文。
  await _loadFont('/System/Library/Fonts/Supplemental/Songti.ttc',
      ['Songti SC']);
  await _loadFont('/System/Library/Fonts/SFNSMono.ttf', ['SF Mono']);
  await _loadFont('/Library/Fonts/Arial Unicode.ttf', [
    'Roboto',
    '.SF NS',
    'Helvetica',
    'SF Pro Text',
  ]);
}

void main() {
  testWidgets('主界面截图（浅色 / 深色）', (tester) async {
    await _loadSystemFonts();
    tester.view.physicalSize = const Size(1440, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    tester.platformDispatcher.platformBrightnessTestValue = Brightness.light;
    addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);

    await tester.pumpWidget(const RepaintBoundary(child: FotoFrameApp()));
    await tester.pump(const Duration(milliseconds: 100));

    // 真实异步（文件 IO / 图片解码）必须放在 runAsync 里，否则事件循环被冻结
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 400));
      final state = tester.state<HomePageState>(find.byType(HomePage));
      final sample = File('../samples/leica_m11.jpg');
      if (sample.existsSync()) {
        await state.addPaths([sample.path]);
      }
      await Future<void>.delayed(const Duration(seconds: 4));
    });
    await tester.pump(const Duration(milliseconds: 300));

    await expectLater(
      find.byType(RepaintBoundary).first,
      matchesGoldenFile('goldens/ui_home_light.png'),
    );

    tester.platformDispatcher.platformBrightnessTestValue = Brightness.dark;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    await expectLater(
      find.byType(RepaintBoundary).first,
      matchesGoldenFile('goldens/ui_home_dark.png'),
    );
  });
}
