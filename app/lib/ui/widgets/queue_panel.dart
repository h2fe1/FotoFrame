/// 右栏：队列目录（INDEX）+ 批量导出。
library;

import 'package:flutter/material.dart';

import '../../model/batch_item.dart';
import '../design.dart';

class QueuePanel extends StatelessWidget {
  const QueuePanel({
    super.key,
    required this.palette,
    required this.items,
    required this.selected,
    required this.onSelect,
    required this.onRemove,
    required this.onClear,
    required this.onExportAll,
    required this.exporting,
  });

  final Palette palette;
  final List<BatchItem> items;
  final int selected;
  final ValueChanged<int> onSelect;
  final ValueChanged<int> onRemove;
  final VoidCallback onClear;
  final VoidCallback onExportAll;
  final bool exporting;

  @override
  Widget build(BuildContext context) {
    final p = palette;
    final count = items.length.toString().padLeft(2, '0');
    return Container(
      width: 296,
      decoration: BoxDecoration(
        color: p.paper,
        border: Border(left: BorderSide(color: p.line)),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(30, 30, 34, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  'INDEX',
                  style: TextStyle(
                    fontSize: 9,
                    letterSpacing: 2.7,
                    color: p.ink,
                  ),
                ),
                const Spacer(),
                Text(
                  '$count 张',
                  style: TextStyle(
                    fontSize: 9,
                    letterSpacing: 1.6,
                    color: p.ink3,
                  ),
                ),
                if (items.isNotEmpty) ...[
                  const SizedBox(width: 16),
                  MouseRegion(
                    cursor: SystemMouseCursors.click,
                    child: GestureDetector(
                      onTap: exporting ? null : onClear,
                      child: Text(
                        '清空',
                        style: TextStyle(
                          fontSize: 9,
                          letterSpacing: 1.2,
                          color: exporting
                              ? p.ink3.withValues(alpha: 0.4)
                              : p.ink3,
                        ),
                      ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 10),
            Container(height: 1, color: p.ink),
            Expanded(
              child: items.isEmpty
                  ? Center(
                      child: Text(
                        '拖入照片开始',
                        style: AppType.body(p, size: 10.5, color: p.ink3)
                            .copyWith(letterSpacing: 1.2),
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.only(top: 2),
                      itemCount: items.length,
                      itemBuilder: (context, i) {
                        final item = items[i];
                        return _QueueRow(
                          palette: p,
                          index: i,
                          item: item,
                          selected: i == selected,
                          onTap: () => onSelect(i),
                          onRemove: exporting ? null : () => onRemove(i),
                        );
                      },
                    ),
            ),
            const SizedBox(height: 14),
            _exportAll(p),
            const SizedBox(height: 10),
            Text(
              'PNG · ${items.length} 个文件 · 原名保存',
              style: TextStyle(
                fontSize: 8.5,
                letterSpacing: 1.2,
                color: p.ink3,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _exportAll(Palette p) {
    final enabled = items.isNotEmpty && !exporting;
    return MouseRegion(
      cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: enabled ? onExportAll : null,
        child: AnimatedOpacity(
          duration: const Duration(milliseconds: 120),
          opacity: enabled ? 1 : 0.4,
          child: Container(
            padding: const EdgeInsets.only(bottom: 8),
            decoration: BoxDecoration(
              border: Border(bottom: BorderSide(color: p.ink)),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  '导出全部',
                  style: TextStyle(
                    fontSize: 11,
                    letterSpacing: 1.0,
                    color: p.ink,
                  ),
                ),
                const Spacer(),
                Text(
                  '→',
                  style: TextStyle(
                    fontSize: 14,
                    height: 1,
                    color: p.accent,
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

class _QueueRow extends StatefulWidget {
  const _QueueRow({
    required this.palette,
    required this.index,
    required this.item,
    required this.selected,
    required this.onTap,
    required this.onRemove,
  });

  final Palette palette;
  final int index;
  final BatchItem item;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback? onRemove;

  @override
  State<_QueueRow> createState() => _QueueRowState();
}

class _QueueRowState extends State<_QueueRow> {
  bool _hover = false;

  @override
  Widget build(BuildContext context) {
    final p = widget.palette;
    final item = widget.item;
    final selected = widget.selected;
    final no = (widget.index + 1).toString().padLeft(2, '0');
    final brand = (item.camera?.brand ?? '').toUpperCase();
    final failed = item.status == ItemStatus.failed;

    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onTap,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            if (selected)
              Positioned(
                left: -30,
                top: 14,
                child: Container(width: 14, height: 1, color: p.accent),
              ),
            Container(
              padding: const EdgeInsets.symmetric(vertical: 12),
              decoration: BoxDecoration(
                color: _hover && !selected
                    ? p.ink.withValues(alpha: 0.025)
                    : Colors.transparent,
                border: Border(bottom: BorderSide(color: p.line2)),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          brand.isEmpty ? no : '$no / $brand',
                          style: TextStyle(
                            fontSize: 8.5,
                            letterSpacing: 1.5,
                            color: selected ? p.accent : p.ink3,
                          ),
                        ),
                        const SizedBox(height: 5),
                        Row(
                          children: [
                            Flexible(
                              child: Text(
                                item.name,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: AppType.serifTitle(
                                  p,
                                  size: selected ? 14 : 13.5,
                                ).copyWith(
                                  color: selected ? p.ink : p.ink2,
                                ),
                              ),
                            ),
                            if (selected) ...[
                              const SizedBox(width: 8),
                              Container(
                                width: 5,
                                height: 5,
                                color: p.accent,
                                margin: const EdgeInsets.only(top: 2),
                              ),
                            ],
                          ],
                        ),
                        const SizedBox(height: 5),
                        Text(
                          failed
                              ? (item.error ?? '识别失败')
                              : (item.camera?.displayName ?? '未识别机型'),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 9,
                            letterSpacing: 1.1,
                            color: failed ? p.error : p.ink3,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (_hover && widget.onRemove != null)
                    GestureDetector(
                      onTap: widget.onRemove,
                      child: Padding(
                        padding: const EdgeInsets.only(left: 8, top: 2),
                        child: Text(
                          '×',
                          style: TextStyle(
                            fontSize: 14,
                            height: 1,
                            color: p.ink3,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
