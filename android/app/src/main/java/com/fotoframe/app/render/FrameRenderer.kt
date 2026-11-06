package com.fotoframe.app.render

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.fotoframe.app.core.Point
import com.fotoframe.app.core.catalog.CameraAsset
import com.fotoframe.app.core.compose.FrameLayout
import com.fotoframe.app.core.compose.FrameTheme
import com.fotoframe.app.core.compose.LayoutEngine
import java.io.ByteArrayOutputStream

data class RenderResult(val bitmap: Bitmap, val layout: FrameLayout)

/**
 * Canvas renderer: paper background → bottom photo → camera back (multiply) →
 * black screen → photo perspective-warped into the screen quad.
 */
object FrameRenderer {

    fun render(
        store: CameraImageStore,
        camera: CameraAsset,
        photo: Bitmap,
        theme: FrameTheme,
    ): RenderResult {
        val layout = LayoutEngine.compute(
            theme = theme,
            camera = camera,
            photoW = photo.width,
            photoH = photo.height,
        )
        val output = Bitmap.createBitmap(layout.canvasW, layout.canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Paper background.
        canvas.drawColor(theme.backgroundColor)

        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Bottom original photo, bleeding to left/right/bottom edges.
        val pr = layout.photoRect
        canvas.drawBitmap(
            photo,
            null,
            RectF(pr.x.toFloat(), pr.y.toFloat(), pr.right.toFloat(), pr.bottom.toFloat()),
            bitmapPaint,
        )

        // Camera back: multiply lets the product shot's white background melt into paper.
        val cameraBitmap = store.imageFor(
            camera,
            store.desiredCameraWidth(layout.canvasW, theme.cameraMarginRatio),
        )
        val cr = layout.cameraRect
        val cameraPaint = Paint(bitmapPaint).apply {
            // Skia's MULTIPLY keeps the destination wherever the camera asset is
            // transparent. (PorterDuff.Mode.MULTIPLY would punch transparent holes.)
            blendMode = BlendMode.MULTIPLY
        }
        canvas.drawBitmap(
            cameraBitmap,
            null,
            RectF(cr.x.toFloat(), cr.y.toFloat(), cr.right.toFloat(), cr.bottom.toFloat()),
            cameraPaint,
        )

        drawPhotoInScreen(canvas, layout, camera, photo, bitmapPaint)
        return RenderResult(output, layout)
    }

    private fun drawPhotoInScreen(
        canvas: Canvas,
        layout: FrameLayout,
        camera: CameraAsset,
        photo: Bitmap,
        paint: Paint,
    ) {
        // If the asset annotates the glass opening, black it out first so the photo
        // gets a clean, uniform black border.
        layout.openingQuadOnCanvas?.let { opening ->
            canvas.drawPath(quadPath(opening), Paint().apply { color = Color.BLACK })
        }

        val screenPath = quadPath(layout.screenQuadOnCanvas)
        canvas.drawPath(screenPath, Paint().apply { color = Color.BLACK })

        // Project the full photo bitmap onto the canvas, then clip to the screen.
        // LayoutEngine handles cover (overflow) vs contain (letterbox offsets).
        val dst = LayoutEngine.photoQuadOnCanvas(layout, camera, photo.width, photo.height)
        val src = floatArrayOf(
            0f, 0f,
            photo.width.toFloat(), 0f,
            photo.width.toFloat(), photo.height.toFloat(),
            0f, photo.height.toFloat(),
        )
        val dstPoints = FloatArray(8) { i ->
            if (i % 2 == 0) dst[i / 2].x.toFloat() else dst[i / 2].y.toFloat()
        }
        val matrix = Matrix()
        check(matrix.setPolyToPoly(src, 0, dstPoints, 0, 4)) { "无法计算屏幕透视" }

        canvas.save()
        canvas.clipPath(screenPath)
        canvas.concat(matrix)
        canvas.drawBitmap(photo, 0f, 0f, paint)
        canvas.restore()
    }

    private fun quadPath(q: List<Point>): Path = Path().apply {
        moveTo(q[0].x.toFloat(), q[0].y.toFloat())
        lineTo(q[1].x.toFloat(), q[1].y.toFloat())
        lineTo(q[2].x.toFloat(), q[2].y.toFloat())
        lineTo(q[3].x.toFloat(), q[3].y.toFloat())
        close()
    }

    fun encodePng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream(bitmap.byteCount / 4)
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG 编码失败" }
        return out.toByteArray()
    }
}