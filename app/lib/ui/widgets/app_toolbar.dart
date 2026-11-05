/// 顶部工具栏：字标 + 三个动作（导入 / 复制 / 导出）。
///
/// 高度 54，左侧给 macOS 红绿灯留出空间（窗口用 fullSizeContentView）。
library;

import 'package:flutter/material.dart';

import '../design.dart';
import 'stroke_icon.dart';

class AppToolbar extends StatelessWidget {
  const AppToolbar({
    super.key,
    required this.palette,
    required this.onImport,
    required this.onCopy,
    required this.onExport,
    this.canCopy = false,
    this.canExport = false,
    this.exporting = false,
    this.progress = '',
  });

  final Palette palette;
  final VoidCallback onImport;
  final VoidCallback onCopy;
  final VoidCallback onExport;
  final bool canCopy;
  final bool canExport;
  final bool exporting;
  final String progress;

  @override
  Widget build(BuildContext context) {
    final p = palette;
    return Container(
      height: 54,
      decoration: BoxDecoration(
        gradient: p.toolbarGradient,
        border: Border(bottom: BorderSide(color: p.toolbarLine)),
      ),
      child: Column(
        children: [
          // 顶部 1px 高光，让这条栏像一块「机顶」
          Container(height: 1, color: p.toolbarHi),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(left: 92, right: 18),
              child: Row(
                children: [
                  Text(
                    'FOTOFRAME',
                    style: TextStyle(
                      fontSize: 11,
                      height: 1,
                      letterSpacing: 2.6,
                      fontWeight: FontWeight.w600,
                      color: p.toolbarInk,
                    ),
                  ),
                  const Spacer(),
                  if (exporting)
                    Padding(
                      padding: const EdgeInsets.only(right: 12),
                      child: Text(
                        '导出中 $progress',
                        style: TextStyle(
                          fontSize: 10.5,
                          letterSpacing: 0.6,
                          color: p.toolbarDim,
                        ),
                      ),
                    ),
                  _ToolbarAction(
                    palette: p,
                    glyph: StrokeGlyph.import,
                    label: '导入',
                    shortcut: '⌘O',
                    onTap: exporting ? null : onImport,
                  ),
                  _ToolbarAction(
                    palette: p,
                    glyph: StrokeGlyph.copy,
                    label: '复制',
                    shortcut: '⌘⇧C',
                    onTap: (exporting || !canCopy) ? null : onCopy,
                  ),
                  _ToolbarAction(
                    palette: p,
                    glyph: StrokeGlyph.export,
                    label: '导出',
                    shortcut: '⌘E',
                    primary: true,
                    onTap: (exporting || !canExport) ? null : onExport,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ToolbarAction extends StatefulWidget {
  const _ToolbarAction({
    required this.palette,
    required this.glyph,
    required this.label,
    required this.shortcut,
    required this.onTap,
    this.primary = false,
  });

  final Palette palette;
  final StrokeGlyph glyph;
  final String label;
  final String shortcut;
  final VoidCallback? onTap;
  final bool primary;

  @override
  State<_ToolbarAction> createState() => _ToolbarActionState();
}

class _ToolbarActionState extends State<_ToolbarAction> {
  bool _hover = false;

  @override
  Widget build(BuildContext context) {
    final p = widget.palette;
    final enabled = widget.onTap != null;
    final primary = widget.primary;
    final fg = primary ? p.accentInk : p.toolbarDim;

    final bg = primary
        ? p.accent
        : (_hover && enabled ? p.toolbarDim.withValues(alpha: 0.09) : Colors.transparent);

    return MouseRegion(
      cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onTap,
        child: AnimatedOpacity(
          duration: const Duration(milliseconds: 120),
          opacity: enabled ? 1 : 0.38,
          child: Container(
            height: 28,
            margin: const EdgeInsets.only(left: 2),
            padding: EdgeInsets.symmetric(horizontal: primary ? 13 : 10),
            decoration: BoxDecoration(
              color: bg,
              borderRadius: BorderRadius.circular(4),
            ),
            child: Row(
              children: [
                StrokeIcon(widget.glyph, size: 14, color: fg),
                const SizedBox(width: 7),
                Text(
                  widget.label,
                  style: TextStyle(
                    fontSize: 12,
                    height: 1,
                    letterSpacing: 0.3,
                    fontWeight: primary ? FontWeight.w600 : FontWeight.w400,
                    color: fg,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  widget.shortcut,
                  style: TextStyle(
                    fontSize: 9,
                    height: 1,
                    letterSpacing: 0.3,
                    color: fg.withValues(alpha: 0.5),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
