/// EXIF 容器解析测试：JPEG / TIFF / PNG / HEIC 四条路径。
library;

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:fotoframe/core/exif/exif_reader.dart';

/// 构造小端 TIFF，仅含 Make / Model（可选 Orientation）。
Uint8List buildTiff({
  String? make,
  String? model,
  int? orientation,
  bool little = true,
}) {
  final entries = <(int, int, int, Uint8List)>[];
  final values = <int, Uint8List>{};
  if (make != null) values[0x010F] = Uint8List.fromList(make.codeUnits);
  if (model != null) values[0x0110] = Uint8List.fromList(model.codeUnits);

  final data = BytesBuilder();
  final ordered = values.entries.toList()..sort((a, b) => a.key - b.key);
  for (final e in ordered) {
    final v = Uint8List.fromList([...e.value, 0]);
    entries.add((e.key, 2, v.length, v));
  }
  if (orientation != null) {
    entries.add((0x0112, 3, 1, Uint8List(2)));
  }

  final ifdOffset = 8;
  var dataStart = ifdOffset + 2 + 12 * entries.length + 4;

  final head = BytesBuilder();
  head.add(little ? [0x49, 0x49, 0x2A, 0x00] : [0x4D, 0x4D, 0x00, 0x2A]);
  head.add(little
      ? [(ifdOffset >> 0) & 0xFF, (ifdOffset >> 8) & 0xFF, 0, 0]
      : [(ifdOffset >> 24) & 0xFF, (ifdOffset >> 16) & 0xFF,
         (ifdOffset >> 8) & 0xFF, ifdOffset & 0xFF]);
  head.add(little
      ? [entries.length & 0xFF, (entries.length >> 8) & 0xFF]
      : [(entries.length >> 8) & 0xFF, entries.length & 0xFF]);

  for (final (tag, type, count, v) in entries) {
    void u16(int n) => head.add(little ? [n & 0xFF, (n >> 8) & 0xFF] : [(n >> 8) & 0xFF, n & 0xFF]);
    void u32(int n) => head.add(little
        ? [n & 0xFF, (n >> 8) & 0xFF, (n >> 16) & 0xFF, (n >> 24) & 0xFF]
        : [(n >> 24) & 0xFF, (n >> 16) & 0xFF, (n >> 8) & 0xFF, n & 0xFF]);
    u16(tag);
    u16(type);
    u32(count);
    if (tag == 0x0112) {
      // SHORT 内联
      head.add(little
          ? [orientation! & 0xFF, (orientation >> 8) & 0xFF, 0, 0]
          : [(orientation! >> 8) & 0xFF, orientation & 0xFF, 0, 0]);
    } else {
      u32(dataStart);
      dataStart += v.length;
      data.add(v);
    }
  }
  head.add([0, 0, 0, 0]); // next IFD
  head.add(data.toBytes());
  return head.toBytes();
}

Uint8List box(String type, List<int> body) {
  final b = BytesBuilder();
  final total = 8 + body.length;
  b.add([(total >> 24) & 0xFF, (total >> 16) & 0xFF, (total >> 8) & 0xFF, total & 0xFF]);
  b.add(type.codeUnits);
  b.add(body);
  return b.toBytes();
}

/// 构造一个最小 HEIF：ftyp + meta(iinf/iloc) + mdat，Exif item 指向 mdat。
Uint8List buildHeifWithExif(Uint8List tiff, {bool viaIdat = false}) {
  // Exif item 载荷：4 字节偏移 + "Exif\0\0" + TIFF
  final payload = BytesBuilder();
  payload.add([0, 0, 0, 6]);
  payload.add('Exif'.codeUnits);
  payload.add([0, 0]);
  payload.add(tiff);
  final item = payload.toBytes();

  // infe: version 2, item_ID=1, protection=0, type='Exif'
  final infeBody = BytesBuilder();
  infeBody.add([2, 0, 0, 0]); // version 2 + flags
  infeBody.add([0, 1]); // item_ID
  infeBody.add([0, 0]); // protection_index
  infeBody.add('Exif'.codeUnits);
  infeBody.add([0]); // item_name ""
  final infe = box('infe', infeBody.toBytes());

  // iinf: version 0 + entry_count(2) + infe
  final iinfBody = BytesBuilder();
  iinfBody.add([0, 0, 0, 0]);
  iinfBody.add([0, 1]);
  iinfBody.add(infe);
  final iinf = box('iinf', iinfBody.toBytes());

  // iloc: version 0, offset_size=4,length_size=4,base=0, item_count=1
  final ilocBody = BytesBuilder();
  ilocBody.add([0, 0, 0, 0]); // version 0 + flags
  ilocBody.add([0x44, 0x00]); // offset=4, length=4, base=0, index=0
  ilocBody.add([0, 1]); // item_count
  ilocBody.add([0, 1]); // item_ID
  ilocBody.add([0, 0]); // data_reference_index
  // base_offset (0 字节)
  ilocBody.add([0, 1]); // extent_count
  // extent_offset / extent_length 稍后按实际布局填
  // 先占位，后面重算
  ilocBody.add([0, 0, 0, 0]);
  ilocBody.add([0, 0, 0, 0]);
  final ilocNoOffset = ilocBody.toBytes();

  final ftyp = box('ftyp', [...'heic'.codeUnits, 0, 0, 0, 0, ...'mif1heic'.codeUnits]);

  // 两遍：先按 offset=0 组装，算出 mdat 数据起点，再回填
  Uint8List assemble(int dataOffset, {required bool idat}) {
    final ilocBody2 = BytesBuilder();
    ilocBody2.add(ilocNoOffset.sublist(0, ilocNoOffset.length - 8));
    ilocBody2.add([
      (dataOffset >> 24) & 0xFF, (dataOffset >> 16) & 0xFF,
      (dataOffset >> 8) & 0xFF, dataOffset & 0xFF,
    ]);
    ilocBody2.add([
      (item.length >> 24) & 0xFF, (item.length >> 16) & 0xFF,
      (item.length >> 8) & 0xFF, item.length & 0xFF,
    ]);
    final iloc = box('iloc', ilocBody2.toBytes());
    final metaBody = BytesBuilder();
    metaBody.add([0, 0, 0, 0]); // meta fullbox
    metaBody.add(iinf);
    metaBody.add(iloc);
    if (idat) metaBody.add(box('idat', item));
    final meta = box('meta', metaBody.toBytes());
    final mdat = box('mdat', idat ? <int>[] : item);
    return Uint8List.fromList([...ftyp, ...meta, ...mdat]);
  }

  if (viaIdat) {
    // idat 方案：construction_method=1，offset 相对 idat 数据起点
    final ilocBody3 = BytesBuilder();
    ilocBody3.add([1, 0, 0, 0]); // version 1 才有 construction_method
    ilocBody3.add([0x44, 0x00]);
    ilocBody3.add([0, 1]);
    ilocBody3.add([0, 1]);
    ilocBody3.add([0x00, 0x01]); // reserved(12b)=0 + construction_method(4b)=1
    ilocBody3.add([0, 0]); // data_reference_index
    ilocBody3.add([0, 1]);
    ilocBody3.add([0, 0, 0, 0]);
    ilocBody3.add([
      (item.length >> 24) & 0xFF, (item.length >> 16) & 0xFF,
      (item.length >> 8) & 0xFF, item.length & 0xFF,
    ]);
    final iloc = box('iloc', ilocBody3.toBytes());
    final metaBody = BytesBuilder();
    metaBody.add([0, 0, 0, 0]);
    metaBody.add(iinf);
    metaBody.add(iloc);
    metaBody.add(box('idat', item));
    final meta = box('meta', metaBody.toBytes());
    return Uint8List.fromList([...ftyp, ...meta]);
  }

  // 文件偏移方案：mdat 数据起点 = len(ftyp)+len(meta)+8
  final probe = assemble(0, idat: false);
  final metaLen = probe.length - 8 - item.length;
  final dataOffset = metaLen + 8;
  return assemble(dataOffset, idat: false);
}

/// CRC32（PNG 规范用的那个多项式），让测试夹具是合法 PNG。
int crc32(List<int> data) {
  var c = 0xFFFFFFFF;
  for (final b in data) {
    c ^= b & 0xFF;
    for (var k = 0; k < 8; k++) {
      c = (c & 1) != 0 ? (0xEDB88320 ^ (c >> 1)) : (c >> 1);
    }
  }
  return c ^ 0xFFFFFFFF;
}

/// 合法 PNG chunk：长度 + 类型 + 数据 + CRC。
Uint8List pngChunk(String type, List<int> body) {
  final out = BytesBuilder();
  final total = body.length;
  out.add([(total >> 24) & 0xFF, (total >> 16) & 0xFF, (total >> 8) & 0xFF, total & 0xFF]);
  out.add(type.codeUnits);
  out.add(body);
  final crc = crc32([...type.codeUnits, ...body]);
  out.add([(crc >> 24) & 0xFF, (crc >> 16) & 0xFF, (crc >> 8) & 0xFF, crc & 0xFF]);
  return out.toBytes();
}

Uint8List buildPngWithExif(Uint8List tiff) {
  const sig = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
  final out = BytesBuilder()..add(sig);
  // IHDR：1x1 8-bit RGB
  out.add(pngChunk('IHDR', [0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0]));
  out.add(pngChunk('eXIf', tiff));
  out.add(pngChunk('IDAT', [0]));
  out.add(pngChunk('IEND', []));
  return out.toBytes();
}

/// 构造含 IFD0 + EXIF 子 IFD + GPS 子 IFD 的小端 TIFF，
/// 覆盖拍摄参数（光圈/快门/ISO/补偿/焦距/日期）与 GPS 坐标。
Uint8List buildRichTiff() {
  List<int> ascii(String s) => [...s.codeUnits, 0];
  List<int> u16(int v) => [v & 0xFF, (v >> 8) & 0xFF];
  List<int> u32(int v) =>
      [v & 0xFF, (v >> 8) & 0xFF, (v >> 16) & 0xFF, (v >> 24) & 0xFF];
  List<int> rat(int n, int d) => [...u32(n), ...u32(d)];

  final exifEntries = <(int, int, int, List<int>)>[
    (0x829A, 5, 1, rat(1, 250)), // ExposureTime
    (0x829D, 5, 1, rat(18, 10)), // FNumber
    (0x8827, 3, 1, u16(400)), // ISO（SHORT 内联）
    (0x9003, 2, 20, ascii('2023:05:01 12:30:45')),
    (0x9204, 10, 1, rat(-1, 3)), // ExposureBias（SRATIONAL）
    (0x920A, 5, 1, rat(50, 1)), // FocalLength
  ];
  final gpsEntries = <(int, int, int, List<int>)>[
    (0x0001, 2, 2, ascii('N')),
    (0x0002, 5, 3, [...rat(31, 1), ...rat(13, 1), ...rat(4944, 100)]),
    (0x0003, 2, 2, ascii('E')),
    (0x0004, 5, 3, [...rat(121, 1), ...rat(28, 1), ...rat(2532, 100)]),
  ];
  final ifd0Entries = <(int, int, int, List<int>)>[
    (0x010F, 2, 8, ascii('TestCam')),
    (0x0110, 2, 5, ascii('TC-1')),
    (0x8769, 4, 1, []), // EXIF IFD 指针，布局后回填
    (0x8825, 4, 1, []), // GPS IFD 指针，布局后回填
  ];

  // 布局：每个 IFD = 2 + 12*n + 4，溢出数据紧随其后
  int ifdSize(List<(int, int, int, List<int>)> entries) {
    var size = 2 + 12 * entries.length + 4;
    for (final e in entries) {
      if (e.$4.length > 4) size += e.$4.length;
    }
    return size;
  }

  const ifd0Off = 8;
  final exifOff = ifd0Off + ifdSize(ifd0Entries);
  final gpsOff = exifOff + ifdSize(exifEntries);
  ifd0Entries[2] = (0x8769, 4, 1, u32(exifOff));
  ifd0Entries[3] = (0x8825, 4, 1, u32(gpsOff));

  final out = BytesBuilder();
  out.add([0x49, 0x49, 0x2A, 0x00, ...u32(ifd0Off)]);

  void emitIfd(int at, List<(int, int, int, List<int>)> entries) {
    out.add(u16(entries.length));
    var dataAt = at + 2 + 12 * entries.length + 4;
    final overflow = BytesBuilder();
    for (final (tag, type, count, data) in entries) {
      out.add(u16(tag));
      out.add(u16(type));
      out.add(u32(count));
      if (data.length <= 4) {
        out.add([...data, ...List.filled(4 - data.length, 0)]);
      } else {
        out.add(u32(dataAt));
        overflow.add(data);
        dataAt += data.length;
      }
    }
    out.add([0, 0, 0, 0]); // next IFD
    out.add(overflow.toBytes());
  }

  emitIfd(ifd0Off, ifd0Entries);
  emitIfd(exifOff, exifEntries);
  emitIfd(gpsOff, gpsEntries);
  return out.toBytes();
}

void main() {
  final tiff = buildTiff(make: 'Canon', model: 'Canon EOS R5 Mark II');

  test('PNG eXIf chunk', () {
    final png = buildPngWithExif(tiff);
    final info = ExifReader.read(png);
    expect(info.make, 'Canon');
    expect(info.model, 'Canon EOS R5 Mark II');
  });

  test('PNG 没有 eXIf → ExifMissingException', () {
    final out = BytesBuilder()
      ..add([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
      ..add(pngChunk('IHDR', [0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0]))
      ..add(pngChunk('IDAT', [0]))
      ..add(pngChunk('IEND', []));
    expect(
      () => ExifReader.read(out.toBytes()),
      throwsA(isA<ExifMissingException>()),
    );
  });

  test('HEIC：Exif item 走 mdat', () {
    final heif = buildHeifWithExif(tiff);
    final info = ExifReader.read(heif);
    expect(info.make, 'Canon');
    expect(info.model, 'Canon EOS R5 Mark II');
  });

  test('HEIC：Exif item 走 idat（construction_method=1）', () {
    final heif = buildHeifWithExif(tiff, viaIdat: true);
    final info = ExifReader.read(heif);
    expect(info.make, 'Canon');
  });

  test('真实 Apple 编码的 HEIC（sips 产出）能读出 EXIF', () {
    final f = File('../samples/leica_m11.heic');
    if (!f.existsSync()) return; // 夹具不存在时跳过
    final info = ExifReader.read(f.readAsBytesSync());
    expect(info.make, 'LEICA CAMERA AG');
    expect(info.model, 'LEICA M11');
  });

  test('HEIF 只有图像 item、没有 Exif → ExifMissingException', () {
    final infeBody = BytesBuilder()
      ..add([2, 0, 0, 0])
      ..add([0, 1]) // item_ID
      ..add([0, 0])
      ..add('hvc1'.codeUnits)
      ..add([0]);
    final infe = box('infe', infeBody.toBytes());
    final iinfBody = BytesBuilder()
      ..add([0, 0, 0, 0])
      ..add([0, 1])
      ..add(infe);
    final iinf = box('iinf', iinfBody.toBytes());
    final ilocBody = BytesBuilder()
      ..add([0, 0, 0, 0])
      ..add([0x44, 0x00])
      ..add([0, 1])
      ..add([0, 1])
      ..add([0, 0])
      ..add([0, 1])
      ..add([0, 0, 0, 8])
      ..add([0, 0, 0, 4]);
    final metaBody = BytesBuilder()
      ..add([0, 0, 0, 0])
      ..add(iinf)
      ..add(box('iloc', ilocBody.toBytes()));
    final ftyp = box('ftyp', [...'heic'.codeUnits, 0, 0, 0, 0, ...'mif1'.codeUnits]);
    final heif = Uint8List.fromList(
        [...ftyp, ...box('meta', metaBody.toBytes()), ...box('mdat', [1, 2, 3, 4])]);
    expect(
      () => ExifReader.read(heif),
      throwsA(isA<ExifMissingException>()),
    );
  });

  test('TIFF 大端', () {
    final be = buildTiff(make: 'FUJIFILM', model: 'X-T5', little: false);
    final info = ExifReader.read(be);
    expect(info.make, 'FUJIFILM');
    expect(info.model, 'X-T5');
  });

  test('真实 JPEG 样片仍可解析', () {
    final f = File('../samples/leica_m11.jpg');
    if (!f.existsSync()) return;
    final info = ExifReader.read(f.readAsBytesSync());
    expect(info.make, 'LEICA CAMERA AG');
    expect(info.model, 'LEICA M11');
  });

  test('EXIF 子 IFD 的拍摄参数与 GPS 坐标', () {
    final info = ExifReader.read(buildRichTiff());
    expect(info.make, 'TestCam');
    expect(info.fNumber, closeTo(1.8, 1e-9));
    expect(info.exposureTime, closeTo(0.004, 1e-9));
    expect(info.iso, 400);
    expect(info.exposureBias, closeTo(-1 / 3, 1e-9));
    expect(info.focalLength, closeTo(50, 1e-9));
    expect(info.dateTimeOriginal, '2023:05:01 12:30:45');
    expect(info.latitude, closeTo(31.2304, 1e-4));
    expect(info.longitude, closeTo(121.4737, 1e-4));
  });

  test('无 GPS 子 IFD 时坐标为 null', () {
    final info = ExifReader.read(buildTiff(make: 'Canon', model: 'EOS R5'));
    expect(info.latitude, isNull);
    expect(info.longitude, isNull);
    expect(info.fNumber, isNull);
    expect(info.dateTimeOriginal, isNull);
  });
}