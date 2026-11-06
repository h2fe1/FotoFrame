package com.fotoframe.app.core.compose

import com.fotoframe.app.core.Point
import com.fotoframe.app.core.RectD
import com.fotoframe.app.core.bounds
import com.fotoframe.app.core.catalog.CameraAsset
import com.fotoframe.app.core.catalog.ScreenFit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Fixed paper-grey background. The camera back is multiplied onto this light base. */
const val FRAME_BACKGROUND_COLOR: Int = 0xFFE9E9E6.toInt()

/** Output theme knobs. Background is intentionally not user-selectable. */
data class FrameTheme(
    val backgroundColor: Int = FRAME_BACKGROUND_COLOR,
    val longEdge: Int = 2400,
    val cameraMarginRatio: Double = 0.10,
    val photoMarginRatio: Double = 0.0,
) {
    init {
        require(cameraMarginRatio >= 0 && cameraMarginRatio < 0.5)
    }
}

/** Layout tightness presets, same labels as the Mac app. */
enum class LayoutDensity(val label: String, val cameraMarginRatio: Double) {
    Compact("紧凑", 0.06),
    Normal("标准", 0.10),
    Relaxed("宽松", 0.14),
}

/** Fully computed geometry for one output image. */
data class FrameLayout(
    val canvasW: Int,
    val canvasH: Int,
    val cameraRect: RectD,
    val screenQuadOnCanvas: List<Point>,
    val openingQuadOnCanvas: List<Point>?,
    val photoRect: RectD,
)

/** Photo → unit-square mapping used when projecting onto the screen quad. */
data class ScreenMapping(
    val sx: Double,
    val tx: Double,
    val sy: Double,
    val ty: Double,
) {
    fun u(px: Double): Double = px * sx + tx
    fun v(py: Double): Double = py * sy + ty
}

/**
 * Pure geometry: canvas layout plus the homography that projects the photo onto the
 * four corners of the camera screen.
 */
object LayoutEngine {

    fun compute(
        theme: FrameTheme,
        camera: CameraAsset,
        photoW: Int,
        photoH: Int,
    ): FrameLayout {
        require(photoW > 0 && photoH > 0)

        // Canvas width is driven by the bottom photo, which bleeds to the edges.
        val photoAspect = photoW.toDouble() / photoH.toDouble()
        val photoScale = theme.longEdge.toDouble() / max(photoW, photoH).toDouble()
        val scaledPhotoW = photoW * photoScale
        val canvasW =
            (scaledPhotoW / (1 - 2 * theme.photoMarginRatio)).roundToLong().toDouble()

        // Camera: equal margins on all four sides, scaled by width.
        val camMargin = canvasW * theme.cameraMarginRatio
        val camW = canvasW - 2 * camMargin
        val scale = camW / camera.width
        val camH = camera.height * scale
        val camX = camMargin
        val camY = camMargin
        val cameraRect = RectD(camX, camY, camW, camH)

        // Screen quad → canvas coordinates.
        val quadOnCanvas = camera.screen.corners.map { c ->
            Point(camX + c.x * scale, camY + c.y * scale)
        }

        // Screen opening (inner glass edge) → canvas. Make the black border uniform:
        // per-edge detection differs by 10–20 px, which would visibly misalign the photo.
        val openingOnCanvas = camera.opening?.let { opening ->
            uniformOpening(
                quadOnCanvas,
                opening.corners.map { c -> Point(camX + c.x * scale, camY + c.y * scale) },
            )
        }

        val photoRectW = canvasW * (1 - 2 * theme.photoMarginRatio)
        val photoRectX = canvasW * theme.photoMarginRatio
        val photoRectH = (photoRectW / photoAspect).roundToLong().toDouble()
        val photoY = (camY + camH + camMargin).roundToLong().toDouble()

        val canvasH = photoY + photoRectH + canvasW * theme.photoMarginRatio

        return FrameLayout(
            canvasW = canvasW.roundToInt(),
            canvasH = canvasH.roundToInt(),
            cameraRect = cameraRect,
            screenQuadOnCanvas = quadOnCanvas,
            openingQuadOnCanvas = openingOnCanvas,
            photoRect = RectD(photoRectX, photoY, photoRectW, photoRectH),
        )
    }

    private fun uniformOpening(quad: List<Point>, opening: List<Point>): List<Point> {
        val q = quad.bounds()
        val o = opening.bounds()
        val margins = sortedOf(
            q.x - o.x,
            (o.x + o.w) - (q.x + q.w),
            q.y - o.y,
            (o.y + o.h) - (q.y + q.h),
        )
        val m0 = (margins[1] + margins[2]) / 2
        val m = max(0.0, m0)
        return listOf(
            Point(q.x - m, q.y - m),
            Point(q.x + q.w + m, q.y - m),
            Point(q.x + q.w + m, q.y + q.h + m),
            Point(q.x - m, q.y + q.h + m),
        )
    }

    private fun sortedOf(a: Double, b: Double, c: Double, d: Double): DoubleArray =
        doubleArrayOf(a, b, c, d).also { it.sort() }

    /** Homography mapping the unit square to [dst] (3×3, row-major). */
    fun homographyToQuad(dst: List<Point>): DoubleArray {
        require(dst.size == 4)
        val src = listOf(
            Point(0.0, 0.0),
            Point(1.0, 0.0),
            Point(1.0, 1.0),
            Point(0.0, 1.0),
        )
        return homography(src, dst)
    }

    private fun homography(src: List<Point>, dst: List<Point>): DoubleArray {
        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)
        for (i in 0 until 4) {
            val (x, y) = src[i]
            val (u, v) = dst[i]
            val r1 = 2 * i
            val r2 = 2 * i + 1
            a[r1][0] = x; a[r1][1] = y; a[r1][2] = 1.0
            a[r1][6] = -u * x; a[r1][7] = -u * y
            b[r1] = u
            a[r2][3] = x; a[r2][4] = y; a[r2][5] = 1.0
            a[r2][6] = -v * x; a[r2][7] = -v * y
            b[r2] = v
        }
        val h = solve8(a, b)
        return doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
    }

    private fun solve8(aIn: Array<DoubleArray>, bIn: DoubleArray): DoubleArray {
        val n = 8
        val a = Array(n) { aIn[it].copyOf() }
        val b = bIn.copyOf()
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            }
            if (pivot != col) {
                val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
                val t = b[col]; b[col] = b[pivot]; b[pivot] = t
            }
            val d = a[col][col]
            if (abs(d) < 1e-12) error("homography: singular matrix (four collinear points?)")
            for (r in col + 1 until n) {
                val f = a[r][col] / d
                for (c in col until n) {
                    a[r][c] -= f * a[col][c]
                }
                b[r] -= f * b[col]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = b[r]
            for (c in r + 1 until n) s -= a[r][c] * x[c]
            x[r] = s / a[r][r]
        }
        return x
    }

    /**
     * Map the photo's pixel rectangle into the screen's normalized unit square.
     * Cover overflows (crop), contain shrinks (letterbox).
     */
    fun screenMapping(camera: CameraAsset, photoW: Int, photoH: Int): ScreenMapping {
        val s = camera.screen.bounds
        val screenAspect = s.w / s.h
        val photoAspect = photoW.toDouble() / photoH.toDouble()
        val contain = camera.screenFit == ScreenFit.Contain

        val rw: Double
        val rh: Double
        val wider = photoAspect > screenAspect
        if (contain) {
            rw = if (wider) 1.0 else photoAspect / screenAspect
            rh = if (wider) screenAspect / photoAspect else 1.0
        } else {
            rw = if (wider) photoAspect / screenAspect else 1.0
            rh = if (wider) 1.0 else screenAspect / photoAspect
        }

        return ScreenMapping(
            sx = rw / photoW,
            tx = (1 - rw) / 2,
            sy = rh / photoH,
            ty = (1 - rh) / 2,
        )
    }

    /**
     * The four canvas points that the full photo bitmap must map to.
     * The renderer clips the result to the screen quad, which implements cover cropping.
     */
    fun photoQuadOnCanvas(
        layout: FrameLayout,
        camera: CameraAsset,
        photoW: Int,
        photoH: Int,
    ): List<Point> {
        val h = homographyToQuad(layout.screenQuadOnCanvas)
        val m = screenMapping(camera, photoW, photoH)
        fun project(px: Double, py: Double): Point {
            val u = m.u(px)
            val v = m.v(py)
            val den = h[6] * u + h[7] * v + h[8]
            return Point(
                (h[0] * u + h[1] * v + h[2]) / den,
                (h[3] * u + h[4] * v + h[5]) / den,
            )
        }
        return listOf(
            project(0.0, 0.0),
            project(photoW.toDouble(), 0.0),
            project(photoW.toDouble(), photoH.toDouble()),
            project(0.0, photoH.toDouble()),
        )
    }

    /** Convenience used by the UI footer: `"3200 × 4267 px · PNG"`. */
    fun spec(layout: FrameLayout?): String =
        if (layout == null) "—" else "${layout.canvasW} × ${layout.canvasH} px · PNG"

    /** Scale a finished output down so it fits the preview stage. */
    fun fitScale(canvasW: Int, canvasH: Int, maxW: Float, maxH: Float): Float {
        if (canvasW <= 0 || canvasH <= 0) return 1f
        return min(maxW / canvasW, maxH / canvasH).coerceAtLeast(0.0001f)
    }
}