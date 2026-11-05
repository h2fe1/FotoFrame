/// 无 EXIF 的 PNG（截图 / 导出图）：应温和提示「无 EXIF 机型信息」，
/// 而不是报「EXIF 读取失败」并标成失败状态。
library;

import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/main.dart';
import 'package:fotoframe/model/batch_item.dart';
import 'package:fotoframe/ui/home_page.dart';

/// 1×1 透明 PNG（合法、无 eXIf chunk）。
const _png1x1Base64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

void main() {
  testWidgets('无 EXIF 的 PNG 给出温和提示', (tester) async {
    tester.view.physicalSize = const Size(1440, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    final dir = Directory.systemTemp.createTempSync('ff_noexif');
    final file = File('${dir.path}/screenshot.png')
      ..writeAsBytesSync(base64Decode(_png1x1Base64));

    await tester.pumpWidget(const RepaintBoundary(child: FotoFrameApp()));
    await tester.pump(const Duration(milliseconds: 100));

    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 300));
      final state = tester.state<HomePageState>(find.byType(HomePage));
      await state.addPaths([file.path]);
      await Future<void>.delayed(const Duration(seconds: 1));
    });
    await tester.pump(const Duration(milliseconds: 200));

    final state = tester.state<HomePageState>(find.byType(HomePage));
    final item = state.indexOfFirstItem();
    expect(item, isNotNull);
    expect(item!.status, isNot(ItemStatus.failed),
        reason: '没有 EXIF 不是解析故障，不该标红');
    expect(item.matchInfo, '无 EXIF 机型信息');
    expect(item.camera, isNull);

    // 界面文案不应出现「EXIF 读取失败」
    expect(find.text('EXIF 读取失败'), findsNothing);

    dir.deleteSync(recursive: true);
  });
}