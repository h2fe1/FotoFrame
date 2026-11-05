/// 中间：成图预览（作品舞台）。
library;

import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../design.dart';

class PreviewStage extends StatelessWidget {
  const PreviewStage({
    super.key,
    required this.palette,
    required this.image,
    required this.captionLeft,
    required this.captionRight,
    required this.onImport,
    required this.previewing,
  });

  final Palette palette;
  final ui.Image? image;
  final String captionLeft;
  final String captionRight;
  final VoidCallback onImport;
  final bool previewing;

  @override
  Widget build(BuildContext context) {
    final p = palette;
    return Container(
      decoration: BoxDecoration(gradient: p.stageGradient),
      child: Stack(
        children: [
          Positioned.fill(
            child: image == null
                ? _EmptyState(palette: p, onImport: onImport)
                : LayoutBuilder(
                    builder: (context, constraints) {
                      final img = image!;
                      final maxW = math.max(120.0, constraints.maxWidth - 116);
                      final maxH = math.max(120.0, constraints.maxHeight - 108);
                      final scale = math.min(
                        maxW / img.width,
                        maxH / img.height,
                      );
                      final w = img.width * scale;
                      final h = img.height * scale;
                      return Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: w,
                              height: h,
                              // 不描边、不铺底色：图片边缘落在中像素上时会与底下
                              // 的颜色做抗锯齿混合，浅底色会在深色照片的左右下
                              // 三边留下一条浅色缝。成图自带相纸灰底，无需再铺。
                              decoration: BoxDecoration(
                                boxShadow: p.frameShadow,
                              ),
                              child: RawImage(
                                image: img,
                                fit: BoxFit.fill,
                                filterQuality: FilterQuality.high,
                              ),
                            ),
                            const SizedBox(height: 15),
                            SizedBox(
                              width: w,
                              child: Container(
                                padding: const EdgeInsets.only(top: 11),
                                decoration: BoxDecoration(
                                  border: Border(
                                    top: BorderSide(color: p.line),
                                  ),
                                ),
                                child: Row(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.baseline,
                                  textBaseline: TextBaseline.alphabetic,
                                  children: [
                                    Expanded(
                                      child: Text(
                                        captionLeft,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                          fontSize: 8.5,
                                          letterSpacing: 1.9,
                                          color: p.ink3,
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Text(
                                      captionRight,
                                      style: TextStyle(
                                        fontSize: 8.5,
                                        letterSpacing: 1.5,
                                        color: p.ink3,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
          if (previewing)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: SizedBox(
                height: 2,
                child: LinearProgressIndicator(
                  minHeight: 2,
                  backgroundColor: Colors.transparent,
                  valueColor: AlwaysStoppedAnimation<Color>(
                    p.accent.withValues(alpha: 0.7),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _EmptyState extends StatefulWidget {
  const _EmptyState({required this.palette, required this.onImport});

  final Palette palette;
  final VoidCallback onImport;

  @override
  State<_EmptyState> createState() => _EmptyStateState();
}

class _EmptyStateState extends State<_EmptyState> {
  bool _hover = false;

  @override
  Widget build(BuildContext context) {
    final p = widget.palette;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '把照片拖进窗口',
            style: AppType.serifTitle(p, size: 22).copyWith(color: p.ink2),
          ),
          const SizedBox(height: 10),
          Text(
            '自动识别机型，套进相机屏幕',
            style: AppType.body(p, size: 10.5, color: p.ink3)
                .copyWith(letterSpacing: 1.2),
          ),
          const SizedBox(height: 22),
          MouseRegion(
            cursor: SystemMouseCursors.click,
            onEnter: (_) => setState(() => _hover = true),
            onExit: (_) => setState(() => _hover = false),
            child: GestureDetector(
              onTap: widget.onImport,
              child: Container(
                padding: const EdgeInsets.only(bottom: 6),
                decoration: BoxDecoration(
                  border: Border(
                    bottom: BorderSide(
                      color: _hover ? p.accent : p.ink3,
                    ),
                  ),
                ),
                child: Text(
                  '选择照片…  ⌘O',
                  style: TextStyle(
                    fontSize: 11,
                    letterSpacing: 0.8,
                    color: _hover ? p.accent : p.ink2,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
