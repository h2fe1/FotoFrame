package com.fotoframe.app.core.exif

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pulls the largest embedded JPEG preview out of a TIFF / DNG file.
 *
 * Android's BitmapFactory cannot decode TIFF or camera RAW, but almost every DNG
 * (and many TIFFs) carries a full-size JPEG preview in `JPEGInterchangeFormat`
 * (0x0201) / `JPEGInterchangeFormatLength` (0x0202), usually on IFD0 or a SubIFD.
 */
object TiffPreview {

    data class Preview(val jpeg: ByteArray, val width: Int?, val height: Int?)

    fun looksLikeTiff(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return (u8(bytes, 0) == 0x49 && u8(bytes, 1) == 0x49 &&
            u8(bytes, 2) == 0x2A && u8(bytes, 3) == 0x00) ||
            (u8(bytes, 0) == 0x4D && u8(bytes, 1) == 0x4D &&
                u8(bytes, 2) == 0x00 && u8(bytes, 3) == 0x2A)
    }

    fun extractJpeg(bytes: ByteArray): Preview? {
        if (!looksLikeTiff(bytes)) return null
        val little = u8(bytes, 0) == 0x49
        val buffer = ByteBuffer.wrap(bytes).apply {
            order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        }
        val reader = Reader(bytes, buffer, little)
        val firstIfd = reader.u32(4)
        if (firstIfd <= 0 || firstIfd >= bytes.size) return null

        var best: Preview? = null
        val visited = HashSet<Long>()
        val pending = ArrayDeque<Long>()
        pending.add(firstIfd)

        while (pending.isNotEmpty() && visited.size < 24) {
            val offset = pending.removeFirst()
            if (!visited.add(offset) || offset <= 0 || offset + 2 > bytes.size) continue
            val count = reader.u16(offset.toInt())
            if (count <= 0 || count > 512) continue

            var jpegOffset: Long? = null
            var jpegLength: Long? = null
            var width: Int? = null
            var height: Int? = null
            val subIfds = mutableListOf<Long>()

            for (i in 0 until count) {
                val entry = offset.toInt() + 2 + i * 12
                if (entry + 12 > bytes.size) break
                val tag = reader.u16(entry)
                val type = reader.u16(entry + 2)
                val n = reader.u32(entry + 4)
                val valueOffset = entry + 8
                when (tag) {
                    0x0100 -> width = reader.firstLong(type, n, valueOffset)?.toInt()
                    0x0101 -> height = reader.firstLong(type, n, valueOffset)?.toInt()
                    0x0201 -> jpegOffset = reader.firstLong(type, n, valueOffset)
                    0x0202 -> jpegLength = reader.firstLong(type, n, valueOffset)
                    0x014A, 0x8769 -> {
                        subIfds += reader.longValues(type, n, valueOffset)
                    }
                }
            }

            val start = jpegOffset
            val len = jpegLength
            if (start != null && len != null && len > 4 &&
                start > 0 && start + len <= bytes.size
            ) {
                val s = start.toInt()
                val l = len.toInt()
                if (u8(bytes, s) == 0xFF && u8(bytes, s + 1) == 0xD8) {
                    if (best == null || l > best!!.jpeg.size) {
                        best = Preview(bytes.copyOfRange(s, s + l), width, height)
                    }
                }
            }

            for (sub in subIfds) {
                if (sub > 0 && sub < bytes.size) pending.add(sub)
            }
        }
        return best
    }

    private class Reader(
        private val bytes: ByteArray,
        private val buffer: ByteBuffer,
        private val little: Boolean,
    ) {
        fun u16(off: Int): Int {
            if (off < 0 || off + 2 > bytes.size) return 0
            return buffer.getShort(off).toInt() and 0xFFFF
        }

        fun u32(off: Int): Long {
            if (off < 0 || off + 4 > bytes.size) return 0
            return buffer.getInt(off).toLong() and 0xFFFFFFFFL
        }

        fun firstLong(type: Int, count: Long, valueOffset: Int): Long? =
            longValues(type, count, valueOffset).firstOrNull()

        fun longValues(type: Int, count: Long, valueOffset: Int): List<Long> {
            if (count <= 0 || count > 4096) return emptyList()
            val size = when (type) {
                3 -> 2L
                4, 13 -> 4L
                else -> return emptyList()
            }
            val total = size * count
            val base = if (total <= 4) valueOffset else u32(valueOffset).toInt()
            if (total > 4 && (base <= 0 || base.toLong() + total > bytes.size)) return emptyList()
            if (total <= 4 && valueOffset + 4 > bytes.size) return emptyList()
            val out = ArrayList<Long>(count.toInt())
            for (i in 0 until count.toInt()) {
                val p = base + i * size.toInt()
                out += if (type == 3) u16(p).toLong() else u32(p)
            }
            return out
        }
    }

    private fun u8(b: ByteArray, off: Int): Int =
        if (off in b.indices) b[off].toInt() and 0xFF else 0
}