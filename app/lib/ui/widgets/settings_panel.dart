/// 左栏：当前照片 + 版式 / 输出 / 照片信息。
library;

import 'package:flutter/material.dart';

import '../../core/compose/layout.dart';
import '../../model/batch_item.dart';
import '../design.dart';

class SettingsPanel extends StatelessWidget {
  const SettingsPanel({
    super.key,
    required this.palette,
    required this.item,
    required this.density,
    required this.longEdge,
    required this.onDensity,
    required this.onLongEdge,
    required this.canvasSpec,
    this.error,
  });

  final Palette palette;
  final BatchItem? item;
  final LayoutDensity density;
  final int longEdge;
  final ValueChanged<LayoutDensity> onDensity;
  final ValueChanged<int> onLongEdge;
  final String canvasSpec;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final p = palette;
    return Container(
      width: 322,
      decoration: BoxDecoration(
        color: p.paper,
        border: Border(right: BorderSide(color: p.line)),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          return SingleChildScrollView(
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: constraints.maxHeight),
              child: IntrinsicHeight(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(44, 28, 34, 24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _header(p),
                      const SizedBox(height: 22),
                      _hair(p),
                      const SizedBox(height: 22),
                      _section(p, '版式 / LAYOUT'),
                      const SizedBox(height: 9),
                      _layoutOptions(p),
                      const SizedBox(height: 22),
                      _hair(p),
                      const SizedBox(height: 20),
                      _section(p, '输出长边 / SIZE'),
                      const SizedBox(height: 9),
                      _sizeOptions(p),
                      const SizedBox(height: 22),
                      _hair(p),
                      const SizedBox(height: 20),
                      _section(p, '照片信息 / CAPTURE'),
                      const SizedBox(height: 6),
                      ..._captureRows(p),
                      const SizedBox(height: 10),
                      _status(p),
                      if (error != null) ...[
                        const SizedBox(height: 12),
                        Text(
                          error!,
                          style: AppType.body(p, size: 10, color: p.error),
                        ),
                      ],
                      const Spacer(),
                      const SizedBox(height: 18),
                      _hair(p),
                      const SizedBox(height: 14),
                      _footer(p),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _header(Palette p) {
    final current = item;
    if (current == null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('当前照片', style: AppType.label(p).copyWith(letterSpacing: 1.1)),
          const SizedBox(height: 10),
          Text(
            '尚未导入照片',
            style: AppType.serifTitle(p, size: 21)
                .copyWith(color: p.ink3),
          ),
          const SizedBox(height: 10),
          Text(
            '拖入照片开始 · ⌘O',
            style: AppType.body(p, size: 10, color: p.ink3),
          ),
        ],
      );
    }
    final camera = current.camera;
    final brand = (current.exif?.make ?? camera?.brand ?? '').trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('当前照片', style: AppType.label(p).copyWith(letterSpacing: 1.1)),
        const SizedBox(height: 9),
        Text(
          camera?.displayName ?? '未识别机型',
          style: AppType.serifTitle(p),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        const SizedBox(height: 7),
        Text(
          [current.name, if (brand.isNotEmpty) brand].join(' · '),
          style: AppType.monoText(p, size: 9, color: p.ink3)
              .copyWith(letterSpacing: 1.1),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }

  Widget _layoutOptions(Palette p) {
    return Row(
      children: [
        for (final d in LayoutDensity.values) ...[
          _TextOption(
            palette: p,
            label: d.label,
            selected: d == density,
            onTap: () => onDensity(d),
          ),
          if (d != LayoutDensity.values.last) const SizedBox(width: 18),
        ],
      ],
    );
  }

  Widget _sizeOptions(Palette p) {
    return Row(
      children: [
        for (final px in const [1600, 2400, 3200]) ...[
          _TextOption(
            palette: p,
            label: '$px',
            selected: px == longEdge,
            onTap: () => onLongEdge(px),
          ),
          if (px != 3200) const SizedBox(width: 14),
        ],
      ],
    );
  }

  List<Widget> _captureRows(Palette p) {
    final current = item;
    if (current == null) {
      return [
        _kv(p, '文件', '—'),
        _kv(p, '机型', '—'),
        _kv(p, '镜头', '—'),
        _kv(p, '日期', '—'),
        _kv(p, '光圈', '—'),
        _kv(p, '快门', '—'),
        _kv(p, 'ISO', '—'),
        _kv(p, '补偿', '—'),
      ];
    }
    final exif = current.exif;
    final lens = exif?.lensModel;
    final rows = [
      _kv(p, '文件', current.name, mono: true),
      _kv(p, '机型', current.camera?.displayName ?? '未识别'),
      _kv(p, '镜头', (lens == null || lens.isEmpty) ? '—' : lens),
      _kv(p, '厂商', (exif?.make ?? '—').trim()),
      _kv(p, '日期', _fmtDate(exif?.dateTimeOriginal)),
      _kv(p, '光圈', _fmtAperture(exif?.fNumber)),
      _kv(p, '快门', _fmtShutter(exif?.exposureTime)),
      _kv(p, 'ISO', exif?.iso?.toString() ?? '—'),
      _kv(p, '补偿', _fmtBias(exif?.exposureBias)),
    ];
    // 只有照片带了 GPS 才显示地点行
    final lat = exif?.latitude;
    final lon = exif?.longitude;
    if (lat != null && lon != null) {
      rows.add(_kv(p, '地点', _fmtCoordinate(lat, lon)));
    }
    return rows;
  }

  /// "2023:05:01 12:30:45" → "2023-05-01 12:30"
  static String _fmtDate(String? raw) {
    final s = raw?.trim() ?? '';
    if (s.isEmpty) return '—';
    if (s.length >= 16 && s[4] == ':' && s[7] == ':') {
      return '${s.substring(0, 4)}-${s.substring(5, 7)}-${s.substring(8, 10)}'
          ' ${s.substring(11, 16)}';
    }
    return s;
  }

  static String _fmtAperture(double? f) =>
      f == null ? '—' : 'f/${f.toStringAsFixed(1)}';

  static String _fmtShutter(double? t) {
    if (t == null || t <= 0) return '—';
    if (t < 1) return '1/${(1 / t).round()} s';
    return t == t.roundToDouble()
        ? '${t.toStringAsFixed(0)} s'
        : '${t.toStringAsFixed(1)} s';
  }

  /// 曝光补偿按 1/3 档显示，如 +0.7 EV → "+2/3 EV"。
  static String _fmtBias(double? ev) {
    if (ev == null) return '—';
    if (ev == 0) return '0 EV';
    final thirds = (ev.abs() * 3).round();
    final whole = thirds ~/ 3;
    final rem = thirds % 3;
    final mag = rem == 0
        ? '$whole'
        : (whole == 0 ? '$rem/3' : '$whole-$rem/3');
    return '${ev > 0 ? '+' : '-'}$mag EV';
  }

  static String _fmtCoordinate(double lat, double lon) {
    final ns = lat >= 0 ? 'N' : 'S';
    final ew = lon >= 0 ? 'E' : 'W';
    return '${lat.abs().toStringAsFixed(4)}° $ns  '
        '${lon.abs().toStringAsFixed(4)}° $ew';
  }

  Widget _kv(Palette p, String k, String v, {bool mono = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5.5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Text(
            k,
            style: AppType.label(p).copyWith(letterSpacing: 1.1),
          ),
          const Spacer(),
          Flexible(
            child: Text(
              v,
              textAlign: TextAlign.right,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: mono
                  ? AppType.monoText(p, size: 10, color: p.ink)
                  : AppType.body(p, size: 11, color: p.ink),
            ),
          ),
        ],
      ),
    );
  }

  Widget _status(Palette p) {
    final current = item;
    final ready = current != null && current.camera != null;
    final color = ready ? p.ok : p.ink3;
    return Row(
      children: [
        Container(
          width: 5,
          height: 5,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 7),
        Text(
          ready ? '就绪 · 可导出' : (current == null ? '等待照片' : '未识别 · 无法导出'),
          style: TextStyle(
            fontSize: 9,
            letterSpacing: 1.4,
            color: color,
          ),
        ),
      ],
    );
  }

  Widget _footer(Palette p) {
    final style = AppType.label(p).copyWith(letterSpacing: 1.2, height: 1.8);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text.rich(
          TextSpan(children: [
            TextSpan(
              text: '相纸灰  ',
              style: style.copyWith(color: p.ink2),
            ),
            TextSpan(text: '#E9E9E6', style: style.copyWith(color: p.ink3)),
          ]),
        ),
        Text.rich(
          TextSpan(children: [
            TextSpan(
              text: '画布  ',
              style: style.copyWith(color: p.ink2),
            ),
            TextSpan(text: canvasSpec, style: style.copyWith(color: p.ink3)),
          ]),
        ),
      ],
    );
  }

  Widget _section(Palette p, String text) =>
      Text(text, style: AppType.label(p));

  Widget _hair(Palette p) => Container(height: 1, color: p.line);
}

class _TextOption extends StatefulWidget {
  const _TextOption({
    required this.palette,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final Palette palette;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  State<_TextOption> createState() => _TextOptionState();
}

class _TextOptionState extends State<_TextOption> {
  bool _hover = false;

  @override
  Widget build(BuildContext context) {
    final p = widget.palette;
    final color = widget.selected
        ? p.ink
        : (_hover ? p.ink2 : p.ink3);
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onTap,
        child: Padding(
          padding: const EdgeInsets.only(bottom: 4),
          child: Container(
            decoration: BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: widget.selected ? p.ink : Colors.transparent,
                  width: 1,
                ),
              ),
            ),
            padding: const EdgeInsets.only(bottom: 5),
            child: Text(
              widget.label,
              style: TextStyle(
                fontSize: 11.5,
                height: 1,
                letterSpacing: 0.4,
                color: color,
                fontWeight:
                    widget.selected ? FontWeight.w500 : FontWeight.w400,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
