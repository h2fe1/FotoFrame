package com.fotoframe.app.render

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.fotoframe.app.core.exif.TiffPreview
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bitmap decoding tuned for phone memory:
 * bounds pass → power-of-two sample → exact long-edge scale → EXIF orientation.
 */
object PhotoDecoder {

    /** Read at most 128 MB of the source, enough for any supported still image. */
    private const val MAX_EXIF_BYTES = 128L * 1024 * 1024

    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.lastPathSegment?.substringAfterLast('/') ?: "photo"
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "photo"
    }

    fun readBytes(context: Context, uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("无法读取照片")
        return stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                check(total <= MAX_EXIF_BYTES) { "照片文件过大" }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }

    /**
     * Framework fallback for orientation, notably HEIF's `irot` property, which is
     * not always mirrored into the EXIF IFD.
     */
    fun frameworkOrientation(context: Context, uri: Uri): Int? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            ).takeIf { it in 1..8 }
        }
    }.getOrNull()

    /** Read the EXIF-rotated pixel size without decoding pixels. */
    fun readSize(context: Context, uri: Uri, orientation: Int?): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            return orientedSize(bounds.outWidth, bounds.outHeight, orientation)
        }
        return runCatching {
            val preview = TiffPreview.extractJpeg(readBytes(context, uri))
                ?: return@runCatching null
            val previewBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(preview.jpeg, 0, preview.jpeg.size, previewBounds)
            if (previewBounds.outWidth <= 0) return@runCatching null
            orientedSize(previewBounds.outWidth, previewBounds.outHeight, orientation)
        }.getOrNull()
    }

    /**
     * Decode [uri] so that its longest edge is at most [maxLongEdge].
     * The returned bitmap is already EXIF-rotated.
     */
    fun decode(
        context: Context,
        uri: Uri,
        maxLongEdge: Int,
        orientation: Int?,
    ): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: error("无法打开照片")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return decodeTiffPreview(context, uri, maxLongEdge, orientation)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return decodeTiffPreview(context, uri, maxLongEdge, orientation)

        val scaled = scaleDown(raw, maxLongEdge)
        return applyOrientation(scaled, orientation)
    }

    /**
     * TIFF/DNG fallback: BitmapFactory has no TIFF codec, so decode the embedded
     * JPEG preview that camera RAWs normally carry.
     */
    private fun decodeTiffPreview(
        context: Context,
        uri: Uri,
        maxLongEdge: Int,
        orientation: Int?,
    ): Bitmap {
        val bytes = runCatching { readBytes(context, uri) }.getOrNull()
        if (bytes == null || !TiffPreview.looksLikeTiff(bytes)) error("无法解码照片")
        val preview = TiffPreview.extractJpeg(bytes)
            ?: error("这张 TIFF/DNG 没有内嵌 JPEG 预览，Android 暂无法解码")
        return decode(preview.jpeg, maxLongEdge, orientation)
    }

    /** Decode an in-memory payload (used by tests / share intents). */
    fun decode(bytes: ByteArray, maxLongEdge: Int, orientation: Int?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("无法解码照片")
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("无法解码照片")
        val scaled = scaleDown(raw, maxLongEdge)
        return applyOrientation(scaled, orientation)
    }

    private fun sampleSize(w: Int, h: Int, maxLongEdge: Int): Int {
        var sample = 1
        val longEdge = max(w, h)
        while (longEdge / (sample * 2) >= maxLongEdge) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / longEdge
        val w = max(1, (bitmap.width * scale).roundToInt())
        val h = max(1, (bitmap.height * scale).roundToInt())
        val out = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (out !== bitmap) bitmap.recycle()
        return out
    }

    /**
     * Apply EXIF orientation 1..8. Uses a Canvas exactly like the Flutter renderer,
     * so mirrored/transposed cases resolve the same way.
     */
    fun applyOrientation(src: Bitmap, orientation: Int?): Bitmap {
        val o = orientation?.takeIf { it in 1..8 } ?: 1
        if (o == 1) return src
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val swap = o >= 5
        val outW = if (swap) src.height else src.width
        val outH = if (swap) src.width else src.height
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.translate(outW / 2f, outH / 2f)
        when (o) {
            2 -> canvas.scale(-1f, 1f)
            3 -> canvas.rotate(180f)
            4 -> canvas.scale(1f, -1f)
            5 -> {
                canvas.rotate(90f)
                canvas.scale(1f, -1f)
            }
            6 -> canvas.rotate(90f)
            7 -> {
                canvas.rotate(-90f)
                canvas.scale(1f, -1f)
            }
            8 -> canvas.rotate(-90f)
        }
        canvas.drawBitmap(src, -w / 2f, -h / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (out !== src) src.recycle()
        return out
    }

    /** Decode a small preview for the queue strip; keeps memory tiny. */
    fun decodeThumbnail(context: Context, uri: Uri, orientation: Int?, maxEdge: Int = 320): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: error("无法打开照片")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return decodeTiffPreview(context, uri, maxEdge, orientation)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return decodeTiffPreview(context, uri, maxEdge, orientation)
        val scaled = scaleDown(raw, maxEdge)
        return applyOrientation(scaled, orientation)
    }

    /** Rotate dimensions the same way [applyOrientation] does. */
    fun orientedSize(w: Int, h: Int, orientation: Int?): Pair<Int, Int> =
        if ((orientation ?: 1) >= 5) h to w else w to h

    private fun orientedSize(
        decodedW: Int,
        decodedH: Int,
        previewW: Int?,
        previewH: Int?,
    ): Pair<Int, Int> {
        if (previewW != null && previewH != null && previewW > 0 && previewH > 0) {
            return previewW to previewH
        }
        return orientedSize(decodedW, decodedH, null)
    }

    fun downscaleForPreview(src: Bitmap, maxLongEdge: Int): Bitmap = scaleDown(src, maxLongEdge)

    /** Utility used by unit tests to avoid depending on Android's image decoder. */
    internal fun fitSize(w: Int, h: Int, maxLongEdge: Int): Pair<Int, Int> {
        val longEdge = max(w, h)
        if (longEdge <= maxLongEdge) return w to h
        val scale = maxLongEdge.toDouble() / longEdge
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }


}