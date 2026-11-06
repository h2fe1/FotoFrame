package com.fotoframe.app.core.exif

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal, dependency-free EXIF reader.
 *
 * Containers supported (the same set as the macOS core):
 * - JPEG (APP1 `Exif\0\0` segment)
 * - TIFF / DNG (bare TIFF header)
 * - PNG (`eXIf` chunk, PNG 1.5 extension)
 * - HEIC / HEIF (ISO BMFF: `meta` → `iinf` / `iloc` / `idat` Exif item)
 *
 * Extracted fields: Make, Model, LensModel, Software, Orientation, FNumber,
 * ExposureTime, ISO, ExposureBias, FocalLength, DateTimeOriginal, GPS.
 */
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val software: String? = null,
    val orientation: Int? = null,
    val fNumber: Double? = null,
    val exposureTime: Double? = null,
    val iso: Int? = null,
    val exposureBias: Double? = null,
    val focalLength: Double? = null,
    val dateTimeOriginal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val displayCamera: String
        get() = listOfNotNull(make, model).joinToString(" ").trim()
}

/** The file is valid but simply has no EXIF (exported PNG, screenshot, …). */
class ExifMissingException(message: String) : Exception(message)

object ExifReader {

    fun read(bytes: ByteArray): ExifInfo {
        if (bytes.size < 8) throw IllegalArgumentException("file too small")

        // JPEG
        if (u8(bytes, 0) == 0xFF && u8(bytes, 1) == 0xD8) {
            return readJpeg(bytes)
        }
        // TIFF little / big endian (includes DNG)
        if ((u8(bytes, 0) == 0x49 && u8(bytes, 1) == 0x49 && u8(bytes, 2) == 0x2A) ||
            (u8(bytes, 0) == 0x4D && u8(bytes, 1) == 0x4D && u8(bytes, 2) == 0x00)
        ) {
            return TiffParser(bytes, 0, bytes.size).parse()
        }
        // PNG
        if (isPng(bytes)) {
            return readPng(bytes)
        }
        // HEIC / HEIF (ftyp at bytes 4..8)
        if (bytes.size >= 12 && ascii(bytes, 4, 8) == "ftyp") {
            return readHeif(bytes)
        }
        throw IllegalArgumentException("unrecognized image format")
    }

    // ---------------- JPEG ----------------

    private fun readJpeg(bytes: ByteArray): ExifInfo {
        var offset = 2
        while (offset + 4 <= bytes.size) {
            if (u8(bytes, offset) != 0xFF) break
            val marker = u8(bytes, offset + 1)
            if (marker == 0xDA || marker == 0xD9) break // SOS / EOI
            if (marker == 0xFF) {
                offset += 1
                continue
            }
            if (marker == 0x01 || marker in 0xD0..0xD7) {
                offset += 2
                continue
            }
            val segLen = (u8(bytes, offset + 2) shl 8) or u8(bytes, offset + 3)
            if (segLen < 2) break
            if (marker == 0xE1 && offset + 4 + segLen - 2 <= bytes.size) {
                val payloadStart = offset + 4
                val tiff = tiffInsideExifHeader(bytes, payloadStart, payloadStart + segLen - 2)
                if (tiff != null) {
                    return TiffParser(bytes, tiff, bytes.size).parse()
                }
            }
            offset += 2 + segLen
        }
        throw ExifMissingException("JPEG 不含 EXIF 信息")
    }

    private fun tiffInsideExifHeader(b: ByteArray, start: Int, end: Int): Int? {
        if (start + 6 > end) return null
        if (u8(b, start) == 0x45 && u8(b, start + 1) == 0x78 &&
            u8(b, start + 2) == 0x69 && u8(b, start + 3) == 0x66 &&
            u8(b, start + 4) == 0x00 && u8(b, start + 5) == 0x00
        ) {
            return start + 6
        }
        return null
    }

    // ---------------- PNG ----------------

    private fun isPng(b: ByteArray): Boolean =
        u8(b, 0) == 0x89 && u8(b, 1) == 0x50 && u8(b, 2) == 0x4E && u8(b, 3) == 0x47 &&
            u8(b, 4) == 0x0D && u8(b, 5) == 0x0A && u8(b, 6) == 0x1A && u8(b, 7) == 0x0A

    private fun readPng(bytes: ByteArray): ExifInfo {
        var offset = 8
        while (offset + 8 <= bytes.size) {
            val len = u32be(bytes, offset).toLong()
            if (len < 0 || offset + 12L + len > bytes.size) break
            val type = ascii(bytes, offset + 4, offset + 8)
            val dataStart = offset + 8
            if (type == "eXIf") {
                val end = (dataStart + len).toInt()
                return TiffParser(bytes, dataStart, end).parse()
            }
            if (type == "IEND") break
            offset = (dataStart + len + 4).toInt()
        }
        throw ExifMissingException("PNG 不含 EXIF 信息")
    }

    // ---------------- HEIC / HEIF ----------------

    private fun readHeif(bytes: ByteArray): ExifInfo {
        val meta = findTopLevelBox(bytes, "meta")
            ?: throw ExifMissingException("HEIC 不含 EXIF 信息")
        // meta is a FullBox: skip version + flags.
        val metaBody = meta.first + 4
        val metaEnd = meta.second

        var exifItemId: Long? = null
        outer@ for (infe in childBoxes(bytes, metaBody, metaEnd, "iinf")) {
            var p = infe.first + 8
            val end = infe.second
            if (p + 4 > end) continue
            val version = u8(bytes, p)
            p += 4
            p += if (version == 0) 2 else 4
            while (p + 8 <= end) {
                val size = u32be(bytes, p).toLong()
                if (size < 8 || p + size > end) break
                val type = ascii(bytes, p + 4, p + 8)
                if (type == "infe") {
                    val id = exifItemIdInInfe(bytes, p, (p + size).toInt())
                    if (id != null) {
                        exifItemId = id
                        break@outer
                    }
                }
                p = (p + size).toInt()
            }
        }
        val wantId = exifItemId ?: throw ExifMissingException("HEIC 不含 EXIF 信息")

        val idats = childBoxes(bytes, metaBody, metaEnd, "idat")
        val idat = idats.firstOrNull()?.let { (it.first + 8) to it.second }

        var payload: ByteArray? = null
        for (iloc in childBoxes(bytes, metaBody, metaEnd, "iloc")) {
            payload = readIlocItem(bytes, iloc.first, iloc.second, wantId, idat)
            if (payload != null) break
        }
        if (payload == null || payload.isEmpty()) {
            throw ExifMissingException("HEIC 不含 EXIF 信息")
        }

        val tiffStart = findTiffStart(payload)
            ?: throw IllegalArgumentException("HEIC Exif 数据损坏")
        return TiffParser(payload, tiffStart, payload.size).parse()
    }

    private fun exifItemIdInInfe(b: ByteArray, start: Int, end: Int): Long? {
        var p = start + 8
        if (p + 4 > end) return null
        val version = u8(b, p)
        p += 4
        var itemId: Long
        if (version >= 2) {
            if (version == 2) {
                if (p + 2 > end) return null
                itemId = u16be(b, p).toLong()
                p += 4
            } else {
                if (p + 4 > end) return null
                itemId = u32be(b, p).toLong()
                p += 6
            }
            if (p + 4 > end) return null
            return if (ascii(b, p, p + 4) == "Exif") itemId else null
        }
        return null
    }

    private fun readIlocItem(
        b: ByteArray,
        start: Int,
        end: Int,
        wantId: Long,
        idat: Pair<Int, Int>?,
    ): ByteArray? {
        var p = start + 8
        if (p + 4 > end) return null
        val version = u8(b, p)
        p += 4
        if (p + 2 > end) return null
        val sizes = u16be(b, p)
        p += 2
        val offsetSize = (sizes shr 12) and 0xF
        val lengthSize = (sizes shr 8) and 0xF
        val baseOffsetSize = (sizes shr 4) and 0xF
        val indexSize = if (version >= 1) sizes and 0xF else 0

        val itemCount: Long = if (version < 2) {
            if (p + 2 > end) return null
            u16be(b, p).toLong().also { p += 2 }
        } else {
            if (p + 4 > end) return null
            u32be(b, p).toLong().also { p += 4 }
        }

        for (i in 0 until itemCount) {
            if (p >= end) return null
            val itemId: Long = if (version < 2) {
                if (p + 2 > end) return null
                u16be(b, p).toLong().also { p += 2 }
            } else {
                if (p + 4 > end) return null
                u32be(b, p).toLong().also { p += 4 }
            }
            var constructionMethod = 0
            if (version == 1 || version == 2) {
                if (p + 2 > end) return null
                constructionMethod = u16be(b, p) and 0xF
                p += 2
            }
            if (p + 2 > end) return null
            p += 2 // data_reference_index
            val baseOffset = readN(b, p, baseOffsetSize)
            p += baseOffsetSize
            if (p + 2 > end) return null
            val extentCount = u16be(b, p)
            p += 2

            val chunks = ArrayList<ByteArray>()
            val matched = itemId == wantId
            for (e in 0 until extentCount) {
                if (indexSize > 0) p += indexSize
                val extOffset = readN(b, p, offsetSize)
                p += offsetSize
                val extLength = readN(b, p, lengthSize)
                p += lengthSize
                if (!matched) continue
                val abs = baseOffset + extOffset
                val srcStart = if (constructionMethod == 1) {
                    idat?.first?.plus(abs) ?: return null
                } else {
                    abs
                }
                val srcEnd = srcStart + extLength
                if (srcStart < 0 || srcEnd > b.size || extLength <= 0) return null
                chunks += b.copyOfRange(srcStart.toInt(), srcEnd.toInt())
            }
            if (matched && chunks.isNotEmpty()) {
                if (chunks.size == 1) return chunks.first()
                val total = chunks.sumOf { it.size }
                val out = ByteArray(total)
                var o = 0
                for (c in chunks) {
                    c.copyInto(out, o)
                    o += c.size
                }
                return out
            }
        }
        return null
    }

    /** HEIF Exif item starts with a 4-byte offset to TIFF; writers disagree on the base. */
    private fun findTiffStart(b: ByteArray): Int? {
        if (b.size < 8) return null
        val off4 = u32be(b, 0).toLong()
        val candidates = listOf(off4 + 4, off4, 4L, 6L, 0L)
        for (c in candidates) {
            if (c in 0..Int.MAX_VALUE.toLong() && looksLikeTiff(b, c.toInt())) return c.toInt()
        }
        val limit = minOf(b.size, 64)
        for (i in 0 until limit - 3) {
            if (looksLikeTiff(b, i)) return i
        }
        return null
    }

    private fun looksLikeTiff(b: ByteArray, off: Int): Boolean {
        if (off < 0 || off + 4 > b.size) return false
        return (u8(b, off) == 0x49 && u8(b, off + 1) == 0x49 &&
            u8(b, off + 2) == 0x2A && u8(b, off + 3) == 0x00) ||
            (u8(b, off) == 0x4D && u8(b, off + 1) == 0x4D &&
                u8(b, off + 2) == 0x00 && u8(b, off + 3) == 0x2A)
    }

    /** Top-level box: (bodyStart, boxEnd). */
    private fun findTopLevelBox(b: ByteArray, type: String): Pair<Int, Int>? {
        var p = 0
        while (p + 8 <= b.size) {
            val size = u32be(b, p).toLong()
            val t = ascii(b, p + 4, p + 8)
            var boxSize = size
            var headerLen = 8
            if (size == 1L) {
                if (p + 16 > b.size) return null
                boxSize = u64be(b, p + 8)
                headerLen = 16
            } else if (size == 0L) {
                boxSize = (b.size - p).toLong()
            }
            if (boxSize < headerLen) return null
            if (t == type) return (p + headerLen) to (p + boxSize).toInt()
            p = (p + boxSize).toInt()
        }
        return null
    }

    /** Child boxes of [type] inside [start, end): (boxStart, boxEnd). */
    private fun childBoxes(
        b: ByteArray,
        start: Int,
        end: Int,
        type: String,
    ): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var p = start
        while (p + 8 <= end) {
            val size = u32be(b, p).toLong()
            if (size < 8) break
            val t = ascii(b, p + 4, p + 8)
            val boxEnd = p + size
            if (boxEnd > end) break
            if (t == type) out += p to boxEnd.toInt()
            p = boxEnd.toInt()
        }
        return out
    }

    private fun readN(b: ByteArray, off: Int, n: Int): Long {
        var v = 0L
        for (i in 0 until n) {
            v = (v shl 8) or u8(b, off + i).toLong()
        }
        return v
    }

    // ---------------- helpers ----------------

    private fun u8(b: ByteArray, off: Int): Int = b[off].toInt() and 0xFF

    private fun u16be(b: ByteArray, off: Int): Int =
        (u8(b, off) shl 8) or u8(b, off + 1)

    private fun u32be(b: ByteArray, off: Int): Int =
        (u8(b, off) shl 24) or (u8(b, off + 1) shl 16) or
            (u8(b, off + 2) shl 8) or u8(b, off + 3)

    private fun u64be(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or u8(b, off + i).toLong()
        return v
    }

    private fun ascii(b: ByteArray, start: Int, end: Int): String =
        String(b, start, end - start, Charsets.ISO_8859_1)

    // ---------------- TIFF ----------------

    private class TiffParser(
        private val bytes: ByteArray,
        private val base: Int,
        private val length: Int,
    ) {
        private lateinit var buffer: ByteBuffer
        private var little = true

        private fun u16(off: Int): Int =
            if (little) buffer.getShort(off).toInt() and 0xFFFF
            else (u8(off) shl 8) or u8(off + 1)

        private fun u32(off: Int): Long =
            if (little) buffer.getInt(off).toLong() and 0xFFFFFFFFL
            else (u8(off).toLong() shl 24) or (u8(off + 1).toLong() shl 16) or
                (u8(off + 2).toLong() shl 8) or u8(off + 3).toLong()

        private fun i32(off: Int): Int =
            if (little) buffer.getInt(off) else {
                (u8(off) shl 24) or (u8(off + 1) shl 16) or (u8(off + 2) shl 8) or u8(off + 3)
            }

        private fun u8(off: Int): Int = bytes[off].toInt() and 0xFF

        fun parse(): ExifInfo {
            if (base + 8 > bytes.size) throw IllegalArgumentException("TIFF 头越界")
            buffer = ByteBuffer.wrap(bytes)
            val order = ((u8(base) shl 8) or u8(base + 1))
            when (order) {
                0x4949 -> {
                    little = true
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                }
                0x4D4D -> {
                    little = false
                    buffer.order(ByteOrder.BIG_ENDIAN)
                }
                else -> throw IllegalArgumentException("bad TIFF byte order")
            }
            if (u16(base + 2) != 42) throw IllegalArgumentException("bad TIFF magic")

            val ifd0 = base + u32(base + 4).toInt()
            val fields = HashMap<Int, Any?>()
            var exifIfdOffset: Long? = null
            var gpsIfdOffset: Long? = null
            readIfd(ifd0, fields) { tag, value ->
                if (tag == 0x8769) exifIfdOffset = (value as? Long) ?: (value as? Int)?.toLong()
                if (tag == 0x8825) gpsIfdOffset = (value as? Long) ?: (value as? Int)?.toLong()
            }

            if (exifIfdOffset != null) {
                val sub = base + exifIfdOffset!!.toInt()
                if (sub + 2 <= base + length) readIfd(sub, fields, null)
            }

            val gps = HashMap<Int, Any?>()
            if (gpsIfdOffset != null) {
                val sub = base + gpsIfdOffset!!.toInt()
                if (sub + 2 <= base + length) readIfd(sub, gps, null)
            }
            val latitude = dmsToDecimal(gps[0x0002], gps[0x0001] as? String)
            val longitude = dmsToDecimal(gps[0x0004], gps[0x0003] as? String)

            val isoRaw = fields[0x8827]
            return ExifInfo(
                make = fields[0x010F] as? String,
                model = fields[0x0110] as? String,
                software = fields[0x0131] as? String,
                lensModel = fields[0xA434] as? String,
                orientation = (fields[0x0112] as? Number)?.toInt(),
                fNumber = (fields[0x829D] as? Number)?.toDouble(),
                exposureTime = (fields[0x829A] as? Number)?.toDouble(),
                iso = when (isoRaw) {
                    is Number -> isoRaw.toInt()
                    is List<*> -> (isoRaw.firstOrNull() as? Number)?.toInt()
                    else -> null
                },
                exposureBias = (fields[0x9204] as? Number)?.toDouble(),
                focalLength = (fields[0x920A] as? Number)?.toDouble(),
                dateTimeOriginal = (fields[0x9003] as? String) ?: (fields[0x0132] as? String),
                latitude = latitude,
                longitude = longitude,
            )
        }

        private fun dmsToDecimal(dms: Any?, ref: String?): Double? {
            val list = dms as? List<*> ?: return null
            if (list.size < 3) return null
            val d = (list[0] as? Number)?.toDouble() ?: return null
            val m = (list[1] as? Number)?.toDouble() ?: return null
            val s = (list[2] as? Number)?.toDouble() ?: return null
            var dec = d + m / 60 + s / 3600
            val r = ref?.trim()?.uppercase()
            if (r == "S" || r == "W") dec = -dec
            return dec
        }

        private fun readIfd(
            ifdOffset: Int,
            out: MutableMap<Int, Any?>,
            onTag: ((Int, Any?) -> Unit)?,
        ) {
            if (ifdOffset < base || ifdOffset + 2 > base + length) return
            if (ifdOffset + 2 > bytes.size) return
            val count = u16(ifdOffset)
            val maxEntries = (length - (ifdOffset - base)) / 12
            val n = minOf(count, maxEntries)
            for (i in 0 until n) {
                val entry = ifdOffset + 2 + i * 12
                if (entry + 12 > base + length) break
                if (entry + 12 > bytes.size) break
                val tag = u16(entry)
                val type = u16(entry + 2)
                val countValue = u32(entry + 4)
                if (countValue > Int.MAX_VALUE) continue
                val value = readValue(entry + 8, type, countValue.toInt()) ?: continue
                out[tag] = value
                onTag?.invoke(tag, value)
            }
        }

        private fun readValue(valueOffset: Int, type: Int, count: Int): Any? {
            if (type <= 0 || type >= TYPE_SIZE.size || count < 0) return null
            val size = TYPE_SIZE[type].toLong() * count
            if (size <= 0) return null
            val off: Int
            if (size <= 4) {
                off = valueOffset
            } else {
                off = base + u32(valueOffset).toInt()
                if (off < base || off.toLong() + size > base.toLong() + length) return null
            }
            if (off.toLong() + size > bytes.size) return null
            return when (type) {
                2 -> { // ASCII
                    var end = off
                    var i = off
                    while (i < off + size) {
                        if (bytes[i] == 0.toByte()) break
                        end = i + 1
                        i++
                    }
                    String(bytes, off, end - off, Charsets.ISO_8859_1)
                }
                3 -> { // SHORT
                    if (count == 1) u16(off)
                    else IntArray(count) { u16(off + it * 2) }.toList()
                }
                4 -> { // LONG
                    if (count == 1) u32(off) else LongArray(count) { u32(off + it * 4) }.toList()
                }
                5 -> { // RATIONAL
                    if (count == 1) rational(off, false)
                    else (0 until count).map { rational(off + it * 8, false) }
                }
                10 -> { // SRATIONAL
                    if (count == 1) rational(off, true)
                    else (0 until count).map { rational(off + it * 8, true) }
                }
                else -> null
            }
        }

        private fun rational(off: Int, signed: Boolean): Double? {
            val num: Long
            val den: Long
            if (signed) {
                num = i32(off).toLong()
                den = i32(off + 4).toLong()
            } else {
                num = u32(off)
                den = u32(off + 4)
            }
            if (den == 0L) return null
            return num.toDouble() / den.toDouble()
        }

        companion object {
            private val TYPE_SIZE = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8)
        }
    }
}