/// 极简 EXIF 读取器（纯 Dart，无第三方依赖）。
///
/// 支持四种容器：
/// - JPEG（APP1 `Exif\0\0` 段）
/// - TIFF / DNG（文件头直接是 TIFF 头）
/// - PNG（`eXIf` chunk，PNG 规范 1.5 扩展）
/// - HEIC / HEIF（ISO BMFF：`meta` → `iinf`/`iloc`/`idat` 里的 Exif item）
///
/// 提取的字段：
/// - IFD0：Make(0x010F)、Model(0x0110)、Software(0x0131)、Orientation(0x0112)、
///   DateTime(0x0132，作为拍摄日期的兜底)
/// - EXIF 子 IFD：LensModel(0xA434)、FNumber(0x829D)、ExposureTime(0x829A)、
///   ISOSpeedRatings(0x8827)、ExposureBiasValue(0x9204)、FocalLength(0x920A)、
///   DateTimeOriginal(0x9003)
/// - GPS 子 IFD：LatitudeRef/DMS(0x0001/0x0002)、LongitudeRef/DMS(0x0003/0x0004)
///
/// 文件本身不含 EXIF（例如导出的 PNG、截图）时抛 [ExifMissingException]，
/// 与「格式损坏」区分开，界面可以给出不同的提示。
library;

import 'dart:typed_data';

class ExifInfo {
  final String? make;
  final String? model;
  final String? lensModel;
  final String? software;
  final int? orientation;

  /// 光圈 F 值。
  final double? fNumber;

  /// 快门（秒）。
  final double? exposureTime;

  /// 感光度。
  final int? iso;

  /// 曝光补偿（EV）。
  final double? exposureBias;

  /// 焦距（mm）。
  final double? focalLength;

  /// 拍摄日期（EXIF 原始格式 "YYYY:MM:DD HH:MM:SS"）。
  final String? dateTimeOriginal;

  /// GPS 纬度（十进制，南纬为负）。
  final double? latitude;

  /// GPS 经度（十进制，西经为负）。
  final double? longitude;

  const ExifInfo({
    this.make,
    this.model,
    this.lensModel,
    this.software,
    this.orientation,
    this.fNumber,
    this.exposureTime,
    this.iso,
    this.exposureBias,
    this.focalLength,
    this.dateTimeOriginal,
    this.latitude,
    this.longitude,
  });

  @override
  String toString() => 'ExifInfo(make: $make, model: $model, lens: $lensModel)';
}

/// 文件合法但确实没有 EXIF（不是解析失败）。
class ExifMissingException implements Exception {
  final String message;
  const ExifMissingException(this.message);

  @override
  String toString() => message;
}

class ExifReader {
  static ExifInfo read(Uint8List bytes) {
    if (bytes.length < 8) {
      throw const FormatException('file too small');
    }
    // JPEG
    if (bytes[0] == 0xFF && bytes[1] == 0xD8) {
      return _readJpeg(bytes);
    }
    // TIFF 小端 / 大端（含 DNG）
    if ((bytes[0] == 0x49 && bytes[1] == 0x49 && bytes[2] == 0x2A) ||
        (bytes[0] == 0x4D && bytes[1] == 0x4D && bytes[2] == 0x00)) {
      return _TiffParser(bytes, 0, bytes.length).parse();
    }
    // PNG
    if (_isPng(bytes)) {
      return _readPng(bytes);
    }
    // HEIC / HEIF（第 4~8 字节是 ftyp）
    if (bytes.length >= 12 &&
        String.fromCharCodes(bytes.sublist(4, 8)) == 'ftyp') {
      return _readHeif(bytes);
    }
    throw const FormatException('unrecognized image format');
  }

  // ---------------- JPEG ----------------

  static ExifInfo _readJpeg(Uint8List bytes) {
    var offset = 2;
    while (offset + 4 <= bytes.length) {
      if (bytes[offset] != 0xFF) break;
      final marker = bytes[offset + 1];
      if (marker == 0xDA || marker == 0xD9) break; // SOS / EOI，后面是图像数据
      // 无长度的标记（RSTn、TEM、连续 0xFF 填充）
      if (marker == 0xFF) {
        offset += 1;
        continue;
      }
      if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
        offset += 2;
        continue;
      }
      final segLen = (bytes[offset + 2] << 8) | bytes[offset + 3];
      if (segLen < 2) break;
      if (marker == 0xE1 && offset + 4 + segLen - 2 <= bytes.length) {
        final payloadStart = offset + 4;
        final tiff = _tiffInsideExifHeader(bytes, payloadStart,
            payloadStart + segLen - 2);
        if (tiff != null) {
          return _TiffParser(bytes, tiff, bytes.length).parse();
        }
      }
      offset += 2 + segLen;
    }
    throw const ExifMissingException('JPEG 不含 EXIF 信息');
  }

  /// 若 [start] 处是 `Exif\0\0`，返回其后 TIFF 头的绝对偏移。
  static int? _tiffInsideExifHeader(Uint8List b, int start, int end) {
    if (start + 6 > end) return null;
    if (b[start] == 0x45 && // E
        b[start + 1] == 0x78 && // x
        b[start + 2] == 0x69 && // i
        b[start + 3] == 0x66 && // f
        b[start + 4] == 0x00 &&
        b[start + 5] == 0x00) {
      return start + 6;
    }
    return null;
  }

  // ---------------- PNG ----------------

  static bool _isPng(Uint8List b) =>
      b[0] == 0x89 &&
      b[1] == 0x50 &&
      b[2] == 0x4E &&
      b[3] == 0x47 &&
      b[4] == 0x0D &&
      b[5] == 0x0A &&
      b[6] == 0x1A &&
      b[7] == 0x0A;

  static ExifInfo _readPng(Uint8List bytes) {
    var offset = 8;
    while (offset + 8 <= bytes.length) {
      final len = _u32be(bytes, offset);
      if (len < 0 || offset + 12 + len > bytes.length) break;
      final type = String.fromCharCodes(bytes.sublist(offset + 4, offset + 8));
      final dataStart = offset + 8;
      if (type == 'eXIf') {
        // eXIf chunk 的内容就是裸 TIFF 头（II/MM 开头）
        return _TiffParser(bytes, dataStart, dataStart + len).parse();
      }
      if (type == 'IEND') break;
      offset = dataStart + len + 4; // + CRC
    }
    throw const ExifMissingException('PNG 不含 EXIF 信息');
  }

  // ---------------- HEIC / HEIF ----------------

  static ExifInfo _readHeif(Uint8List bytes) {
    final meta = _findTopLevelBox(bytes, 'meta');
    if (meta == null) {
      throw const ExifMissingException('HEIC 不含 EXIF 信息');
    }
    // meta 是 FullBox，前 4 字节是 version + flags
    final metaBody = meta.$1 + 4;
    final metaEnd = meta.$2;

    // 1. 找 Exif item 的 ID
    int? exifItemId;
    for (final infe in _childBoxes(bytes, metaBody, metaEnd,
        type: 'iinf', fullBox: true)) {
      var p = infe.$1 + 8; // 跳过 box header
      final end = infe.$2;
      final version = bytes[p];
      p += 4; // version + flags
      if (version == 0) {
        p += 2; // entry_count (u16)
      } else {
        p += 4; // entry_count (u32)
      }
      while (p + 8 <= end) {
        final size = _u32be(bytes, p);
        if (size < 8 || p + size > end) break;
        final type = String.fromCharCodes(bytes.sublist(p + 4, p + 8));
        if (type == 'infe') {
          final id = _exifItemIdInInfe(bytes, p, p + size);
          if (id != null) {
            exifItemId = id;
            break;
          }
        }
        p += size;
      }
      if (exifItemId != null) break;
    }
    if (exifItemId == null) {
      throw const ExifMissingException('HEIC 不含 EXIF 信息');
    }

    // 2. 从 iloc 找该 item 的数据位置（idat 是 meta 的子盒）
    final idats = _childBoxes(bytes, metaBody, metaEnd, type: 'idat');
    // idat 的 body 从盒头之后开始
    final idat = idats.isEmpty ? null : (idats.first.$1 + 8, idats.first.$2);
    Uint8List? payload;
    for (final iloc in _childBoxes(bytes, metaBody, metaEnd,
        type: 'iloc', fullBox: true)) {
      payload = _readIlocItem(bytes, iloc.$1, iloc.$2, exifItemId, idat);
      if (payload != null) break;
    }
    if (payload == null || payload.isEmpty) {
      throw const ExifMissingException('HEIC 不含 EXIF 信息');
    }

    // 3. 定位 TIFF 头
    final tiffStart = _findTiffStart(payload);
    if (tiffStart == null) {
      throw const FormatException('HEIC Exif 数据损坏');
    }
    // 需要绝对偏移来读 TIFF 内部引用，这里把 payload 复制出来后按本地偏移解析
    return _TiffParser(payload, tiffStart, payload.length).parse();
  }

  /// infe 里若 item_type == 'Exif'，返回 item_ID。
  static int? _exifItemIdInInfe(Uint8List b, int start, int end) {
    var p = start + 8; // 跳过 box header
    if (p + 4 > end) return null;
    final version = b[p];
    p += 4; // version + flags
    int? itemId;
    if (version >= 2) {
      if (version == 2) {
        if (p + 2 > end) return null;
        itemId = _u16be(b, p);
        p += 4; // item_ID(2) + protection_index(2)
      } else {
        if (p + 4 > end) return null;
        itemId = _u32be(b, p);
        p += 6; // item_ID(4) + protection_index(2)
      }
      if (p + 4 > end) return null;
      final itemType = String.fromCharCodes(b.sublist(p, p + 4));
      if (itemType == 'Exif') return itemId;
      return null;
    }
    return null;
  }

  /// 解析 iloc（版本 0/1/2），返回指定 item 的原始字节。
  static Uint8List? _readIlocItem(
      Uint8List b, int start, int end, int wantId, (int, int)? idat) {
    var p = start + 8; // 跳过 box header
    if (p + 4 > end) return null;
    final version = b[p];
    p += 4; // version + flags
    if (p + 2 > end) return null;
    final sizes = _u16be(b, p);
    p += 2;
    final offsetSize = (sizes >> 12) & 0xF;
    final lengthSize = (sizes >> 8) & 0xF;
    final baseOffsetSize = (sizes >> 4) & 0xF;
    final indexSize = (version >= 1) ? (sizes & 0xF) : 0;

    int itemCount;
    if (version < 2) {
      if (p + 2 > end) return null;
      itemCount = _u16be(b, p);
      p += 2;
    } else {
      if (p + 4 > end) return null;
      itemCount = _u32be(b, p);
      p += 4;
    }

    for (var i = 0; i < itemCount; i++) {
      if (p >= end) return null;
      int itemId;
      if (version < 2) {
        if (p + 2 > end) return null;
        itemId = _u16be(b, p);
        p += 2;
      } else {
        if (p + 4 > end) return null;
        itemId = _u32be(b, p);
        p += 4;
      }
      var constructionMethod = 0;
      if (version == 1 || version == 2) {
        if (p + 2 > end) return null;
        constructionMethod = _u16be(b, p) & 0xF;
        p += 2;
      }
      if (p + 2 > end) return null;
      p += 2; // data_reference_index
      final baseOffset = _readN(b, p, baseOffsetSize);
      p += baseOffsetSize;
      if (p + 2 > end) return null;
      final extentCount = _u16be(b, p);
      p += 2;

      final chunks = <Uint8List>[];
      var matched = itemId == wantId;
      for (var e = 0; e < extentCount; e++) {
        if (indexSize > 0) p += indexSize;
        final extOffset = _readN(b, p, offsetSize);
        p += offsetSize;
        final extLength = _readN(b, p, lengthSize);
        p += lengthSize;
        if (!matched) continue;
        final abs = baseOffset + extOffset;
        int srcStart;
        if (constructionMethod == 1) {
          // 数据在 meta 内的 idat 盒里
          if (idat == null) return null;
          srcStart = idat.$1 + abs;
        } else {
          srcStart = abs;
        }
        final srcEnd = srcStart + extLength;
        if (srcStart < 0 || srcEnd > b.length || extLength <= 0) return null;
        chunks.add(Uint8List.sublistView(b, srcStart, srcEnd));
      }
      if (matched && chunks.isNotEmpty) {
        if (chunks.length == 1) return chunks.first;
        final total = chunks.fold<int>(0, (s, c) => s + c.length);
        final out = Uint8List(total);
        var o = 0;
        for (final c in chunks) {
          out.setRange(o, o + c.length, c);
          o += c.length;
        }
        return out;
      }
    }
    return null;
  }

  /// HEIF Exif item 的前 4 字节是「到 TIFF 头的偏移」，
  /// 不同写入器给出的基准不一致，这里逐个候选校验。
  static int? _findTiffStart(Uint8List b) {
    if (b.length < 8) return null;
    final off4 = _u32be(b, 0);
    final candidates = <int>[
      off4 + 4,
      off4,
      4,
      6,
      0,
    ];
    for (final c in candidates) {
      if (_looksLikeTiff(b, c)) return c;
    }
    // 兜底：在前 64 字节里扫 TIFF 头
    final limit = b.length < 64 ? b.length : 64;
    for (var i = 0; i + 4 <= limit; i++) {
      if (_looksLikeTiff(b, i)) return i;
    }
    return null;
  }

  static bool _looksLikeTiff(Uint8List b, int off) {
    if (off < 0 || off + 4 > b.length) return false;
    if (b[off] == 0x49 && b[off + 1] == 0x49 && b[off + 2] == 0x2A && b[off + 3] == 0x00) {
      return true;
    }
    if (b[off] == 0x4D && b[off + 1] == 0x4D && b[off + 2] == 0x00 && b[off + 3] == 0x2A) {
      return true;
    }
    return false;
  }

  /// 顶层盒：返回 (bodyStart, boxEnd)。
  static (int, int)? _findTopLevelBox(Uint8List b, String type) {
    var p = 0;
    while (p + 8 <= b.length) {
      final size = _u32be(b, p);
      final t = String.fromCharCodes(b.sublist(p + 4, p + 8));
      var boxSize = size;
      var headerLen = 8;
      if (size == 1) {
        if (p + 16 > b.length) return null;
        boxSize = _u64be(b, p + 8);
        headerLen = 16;
      } else if (size == 0) {
        boxSize = b.length - p;
      }
      if (boxSize < headerLen) return null;
      if (t == type) return (p + headerLen, p + boxSize);
      p += boxSize;
    }
    return null;
  }

  /// 遍历 [start, end) 内的子盒，返回指定 type 的 (boxStart, boxEnd)。
  /// 注意返回的是盒头起点，解析内容时需自行 +8。
  static List<(int, int)> _childBoxes(
    Uint8List b,
    int start,
    int end, {
    required String type,
    bool fullBox = false,
  }) {
    final out = <(int, int)>[];
    var p = start;
    while (p + 8 <= end) {
      final size = _u32be(b, p);
      if (size < 8) break;
      final t = String.fromCharCodes(b.sublist(p + 4, p + 8));
      final boxEnd = p + size;
      if (boxEnd > end) break;
      if (t == type) {
        out.add((p, boxEnd));
      }
      p = boxEnd;
    }
    return out;
  }

  static int _readN(Uint8List b, int off, int n) {
    var v = 0;
    for (var i = 0; i < n; i++) {
      v = (v << 8) | b[off + i];
    }
    return v;
  }

  static int _u16be(Uint8List b, int off) => (b[off] << 8) | b[off + 1];

  static int _u32be(Uint8List b, int off) =>
      (b[off] << 24) | (b[off + 1] << 16) | (b[off + 2] << 8) | b[off + 3];

  static int _u64be(Uint8List b, int off) {
    var v = 0;
    for (var i = 0; i < 8; i++) {
      v = (v << 8) | b[off + i];
    }
    return v;
  }
}

/// TIFF/EXIF 结构解析器（实例化以携带字节序，避免全局可变状态）。
class _TiffParser {
  _TiffParser(this.bytes, this.base, this.length);

  final Uint8List bytes;
  final int base;
  final int length;
  late ByteData _d;
  var _little = true;

  int _u16(int off) => _little
      ? _d.getUint16(off, Endian.little)
      : _d.getUint16(off, Endian.big);

  int _u32(int off) => _little
      ? _d.getUint32(off, Endian.little)
      : _d.getUint32(off, Endian.big);

  ExifInfo parse() {
    if (base + 8 > bytes.length) {
      throw const FormatException('TIFF 头越界');
    }
    _d = ByteData.sublistView(bytes);
    final order = ((bytes[base] & 0xFF) << 8) | (bytes[base + 1] & 0xFF);
    if (order == 0x4949) {
      _little = true;
    } else if (order == 0x4D4D) {
      _little = false;
    } else {
      throw const FormatException('bad TIFF byte order');
    }
    if (_u16(base + 2) != 42) {
      throw const FormatException('bad TIFF magic');
    }
    final ifd0 = base + _u32(base + 4);
    final fields = <int, Object?>{};
    int? exifIfdOffset;
    int? gpsIfdOffset;
    _readIfd(ifd0, fields, (tag, value) {
      if (tag == 0x8769) exifIfdOffset = value as int?;
      if (tag == 0x8825) gpsIfdOffset = value as int?;
    });

    if (exifIfdOffset != null) {
      final sub = base + exifIfdOffset!;
      if (sub + 2 <= base + length) {
        _readIfd(sub, fields, null);
      }
    }

    // GPS 标签号小（0x0001~0x0004），单独一张表避免与 IFD0 冲突
    final gps = <int, Object?>{};
    if (gpsIfdOffset != null) {
      final sub = base + gpsIfdOffset!;
      if (sub + 2 <= base + length) {
        _readIfd(sub, gps, null);
      }
    }
    final latitude = _dmsToDecimal(gps[0x0002], gps[0x0001] as String?);
    final longitude = _dmsToDecimal(gps[0x0004], gps[0x0003] as String?);

    final isoRaw = fields[0x8827];
    return ExifInfo(
      make: fields[0x010F] as String?,
      model: fields[0x0110] as String?,
      software: fields[0x0131] as String?,
      lensModel: fields[0xA434] as String?,
      orientation: fields[0x0112] as int?,
      fNumber: (fields[0x829D] as num?)?.toDouble(),
      exposureTime: (fields[0x829A] as num?)?.toDouble(),
      iso: isoRaw is int
          ? isoRaw
          : (isoRaw is List && isoRaw.isNotEmpty)
              ? (isoRaw.first as num?)?.toInt()
              : null,
      exposureBias: (fields[0x9204] as num?)?.toDouble(),
      focalLength: (fields[0x920A] as num?)?.toDouble(),
      dateTimeOriginal:
          (fields[0x9003] as String?) ?? (fields[0x0132] as String?),
      latitude: latitude,
      longitude: longitude,
    );
  }

  /// GPS 度分秒（RATIONAL[3]）转十进制度数，按半球参考加符号。
  static double? _dmsToDecimal(Object? dms, String? ref) {
    if (dms is! List || dms.length < 3) return null;
    final d = (dms[0] as num?)?.toDouble();
    final m = (dms[1] as num?)?.toDouble();
    final s = (dms[2] as num?)?.toDouble();
    if (d == null || m == null || s == null) return null;
    var dec = d + m / 60 + s / 3600;
    final r = ref?.trim().toUpperCase();
    if (r == 'S' || r == 'W') dec = -dec;
    return dec;
  }

  void _readIfd(int ifdOffset, Map<int, Object?> out,
      void Function(int tag, Object? value)? onTag) {
    if (ifdOffset < base || ifdOffset + 2 > base + length) return;
    if (ifdOffset + 2 > bytes.length) return;
    final count = _u16(ifdOffset);
    // 防御：异常文件里 count 可能是天文数字
    final maxEntries = (length - (ifdOffset - base)) ~/ 12;
    final n = count > maxEntries ? maxEntries : count;
    for (var i = 0; i < n; i++) {
      final entry = ifdOffset + 2 + i * 12;
      if (entry + 12 > base + length) break;
      if (entry + 12 > bytes.length) break;
      final tag = _u16(entry);
      final type = _u16(entry + 2);
      final num_ = _u32(entry + 4);
      final value = _readValue(entry + 8, type, num_);
      if (value != null) {
        out[tag] = value;
        onTag?.call(tag, value);
      }
    }
  }

  static const _typeSize = [0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8];

  Object? _readValue(int valueOffset, int type, int count) {
    if (type <= 0 || type >= _typeSize.length || count < 0) return null;
    final size = _typeSize[type] * count;
    if (size <= 0) return null;
    int off;
    if (size <= 4) {
      off = valueOffset;
    } else {
      off = base + _u32(valueOffset);
      if (off < base || off + size > base + length) return null;
    }
    if (off + size > bytes.length) return null;
    switch (type) {
      case 2: // ASCII
        var end = off;
        for (var i = off; i < off + size; i++) {
          if (bytes[i] == 0) break;
          end = i + 1;
        }
        return String.fromCharCodes(bytes.sublist(off, end));
      case 3: // SHORT
        if (count == 1) {
          return _little
              ? _d.getUint16(off, Endian.little)
              : ((bytes[off] << 8) | bytes[off + 1]);
        }
        return [for (var i = 0; i < count; i++) _u16(off + i * 2)];
      case 4: // LONG
        if (count == 1) return _u32(off);
        return [for (var i = 0; i < count; i++) _u32(off + i * 4)];
      case 5: // RATIONAL
        if (count == 1) return _rational(off);
        return [for (var i = 0; i < count; i++) _rational(off + i * 8)];
      case 10: // SRATIONAL
        if (count == 1) return _rational(off, signed: true);
        return [
          for (var i = 0; i < count; i++) _rational(off + i * 8, signed: true)
        ];
      default:
        return null;
    }
  }

  double? _rational(int off, {bool signed = false}) {
    final int num_, den;
    if (signed) {
      num_ = _little
          ? _d.getInt32(off, Endian.little)
          : _d.getInt32(off, Endian.big);
      den = _little
          ? _d.getInt32(off + 4, Endian.little)
          : _d.getInt32(off + 4, Endian.big);
    } else {
      num_ = _u32(off);
      den = _u32(off + 4);
    }
    if (den == 0) return null;
    return num_ / den;
  }
}