package com.fotoframe.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.fotoframe.app.core.catalog.CameraAsset
import kotlin.math.ceil
import kotlin.math.max

/**
 * Decodes camera back PNGs from APK assets and keeps a small LRU cache so switching
 * between queue items / re-rendering after a theme change stays instant.
 */
class CameraImageStore(context: Context) {

    private val assets = context.assets

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun imageFor(camera: CameraAsset, desiredWidth: Int): Bitmap {
        val target = desiredWidth.coerceIn(480, 2800)
        val key = "${camera.slug}@$target"
        cache.get(key)?.let { if (!it.isRecycled) return it }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(camera.width, target)
        }
        val bitmap = assets.open(camera.imageAssetPath).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("相机素材缺失：${camera.imageFile}")

        cache.put(key, bitmap)
        return bitmap
    }

    /** Width needed for a canvas of [canvasW] with the given side margin ratio. */
    fun desiredCameraWidth(canvasW: Int, cameraMarginRatio: Double): Int =
        ceil(canvasW * (1 - 2 * cameraMarginRatio)).toInt()

    private fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= max(targetWidth, 1)) {
            sample *= 2
        }
        return sample
    }

    fun clear() = cache.evictAll()
}