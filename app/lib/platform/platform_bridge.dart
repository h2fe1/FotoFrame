/// 平台能力：拖拽导入 + 图片写入剪贴板。
///
/// 通道名 `fotoframe/platform`，macOS 侧由 Runner 原生实现
/// （见 macos/Runner/MainFlutterWindow.swift）。
/// 其他平台只需实现同名通道即可复用 UI 层。
library;

import 'dart:async';


import 'package:flutter/services.dart';

class PlatformBridge {
  static const _channel = MethodChannel('fotoframe/platform');

  /// 拖拽进入窗口的文件路径流。
  static Stream<List<String>> get onFilesDropped {
    _ensureHandler();
    return _dropController!.stream;
  }

  static StreamController<List<String>>? _dropController;

  static void _ensureHandler() {
    if (_dropController != null) return;
    _dropController = StreamController<List<String>>.broadcast();
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'filesDropped') {
        final paths = (call.arguments as List).cast<String>();
        _dropController?.add(paths);
      }
      return null;
    });
  }

  /// 把 PNG 字节写入系统剪贴板（macOS: NSPasteboard）。
  static Future<bool> copyImageToClipboard(Uint8List pngBytes) async {
    try {
      final ok = await _channel
          .invokeMethod<bool>('copyImage', {'png': pngBytes});
      return ok ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }
}
