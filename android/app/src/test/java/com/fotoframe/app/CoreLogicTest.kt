package com.fotoframe.app

import com.fotoframe.app.core.Point
import com.fotoframe.app.core.catalog.CameraAsset
import com.fotoframe.app.core.catalog.CameraCatalog
import com.fotoframe.app.core.catalog.ExifSignature
import com.fotoframe.app.core.catalog.MatchQuality
import com.fotoframe.app.core.catalog.ScreenFit
import com.fotoframe.app.core.catalog.ScreenQuad
import com.fotoframe.app.core.compose.FrameTheme
import com.fotoframe.app.core.compose.LayoutDensity
import com.fotoframe.app.core.compose.LayoutEngine
import com.fotoframe.app.core.exif.ExifReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class CoreLogicTest {

    private val repoRoot: File = File("../../").canonicalFile
    private val catalogFile = File(repoRoot, "app/assets/cameras/catalog.json")

    private fun testAsset(
        brand: String,
        model: String,
        slug: String,
        makePatterns: List<String> = emptyList(),
        modelPatterns: List<String> = emptyList(),
    ) = CameraAsset(
        brand = brand,
        model = model,
        slug = slug,
        imageFile = "back.png",
        width = 2000,
        height = 1300,
        screen = ScreenQuad(
            listOf(
                Point(400.0, 300.0),
                Point(1200.0, 300.0),
                Point(1200.0, 900.0),
                Point(400.0, 900.0),
            )
        ),
        exif = ExifSignature(makePatterns, modelPatterns),
    )

    @Test
    fun `catalog loads all 26 camera assets`() {
        val catalog = CameraCatalog.fromJson(catalogFile.readText())
        assertEquals(26, catalog.assets.size)
        val slugs = catalog.assets.map { it.slug }
        assertTrue(slugs.contains("a7m4"))
        assertTrue(slugs.contains("gr4"))
        assertTrue(slugs.contains("x2d2"))
        assertTrue(slugs.contains("s1r2"))
        for (asset in catalog.assets) {
            assertTrue("${asset.slug} width", asset.width > 0)
            assertTrue("${asset.slug} height", asset.height > 0)
            assertEquals("${asset.slug} quad", 4, asset.screen.corners.size)
            val image = File(repoRoot, "app/assets/cameras/${asset.imageFile}")
            assertTrue("${asset.slug} image ${asset.imageFile}", image.isFile)
            val header = image.inputStream().use { input ->
                val bytes = ByteArray(24)
                if (input.read(bytes) != bytes.size) return@use null
                bytes
            }
            assertNotNull(header)
            assertEquals("${asset.slug} png", "PNG", String(header!!, 1, 3))
            val w = ((header[16].toInt() and 0xFF) shl 24) or
                ((header[17].toInt() and 0xFF) shl 16) or
                ((header[18].toInt() and 0xFF) shl 8) or
                (header[19].toInt() and 0xFF)
            val h = ((header[20].toInt() and 0xFF) shl 24) or
                ((header[21].toInt() and 0xFF) shl 16) or
                ((header[22].toInt() and 0xFF) shl 8) or
                (header[23].toInt() and 0xFF)
            assertEquals("${asset.slug} width", asset.width, w)
            assertEquals("${asset.slug} height", asset.height, h)
            for (p in asset.screen.corners) {
                assertTrue("${asset.slug} x in range", p.x in 0.0..asset.width.toDouble())
                assertTrue("${asset.slug} y in range", p.y in 0.0..asset.height.toDouble())
            }
        }
    }

    @Test
    fun `real EXIF values resolve to the expected cameras`() {
        val catalog = CameraCatalog.fromJson(catalogFile.readText())
        val cases = mapOf(
            ("SONY" to "ILCE-7M4") to "a7m4",
            ("SONY" to "ILCE-7RM5") to "a7r5",
            ("LEICA CAMERA AG" to "LEICA Q3") to "q3",
            ("LEICA CAMERA AG" to "LEICA Q3 43") to "q3",
            ("RICOH IMAGING COMPANY, LTD." to "RICOH GR IIIx") to "gr3x",
            ("RICOH IMAGING COMPANY, LTD." to "RICOH GR III") to "gr3",
            ("Hasselblad" to "X2D II 100C") to "x2d2",
            ("Panasonic" to "DC-S1RM2") to "s1r2",
        )
        for ((exif, slug) in cases) {
            val hit = catalog.match(exif.first, exif.second)
            assertEquals("${exif.first} ${exif.second}", slug, hit?.asset?.slug)
        }
        assertEquals(MatchQuality.Alias, catalog.match("SONY", "ILCE-7M4")?.how)
    }

    @Test
    fun `unknown camera does not match`() {
        val catalog = CameraCatalog.fromJson(catalogFile.readText())
        assertNull(catalog.match("Canon", "Canon EOS R1"))
        assertNull(catalog.match("", ""))
        assertNull(catalog.match(null, null))
        assertNull(catalog.match("Canon", "EOS R5"))
    }

    @Test
    fun `suffix match prefers the longest model`() {
        val catalog = CameraCatalog(
            listOf(
                testAsset("Ricoh", "GR III", "gr3"),
                testAsset("Ricoh", "GR IIIx", "gr3x"),
                testAsset("Ricoh", "GR IV", "gr4"),
            )
        )
        assertEquals("gr3x", catalog.match("RICOH", "RICOH GR IIIx")?.asset?.slug)
        assertEquals("gr3", catalog.match("RICOH", "RICOH GR III")?.asset?.slug)
    }

    @Test
    fun `display name avoids duplicated brand`() {
        assertEquals("Leica M10-R", testAsset("Leica", "Leica M10-R", "m10-r").displayName)
        assertEquals("Ricoh GR III", testAsset("Ricoh", "GR III", "gr3").displayName)
    }

    @Test
    fun `homography maps unit square corners`() {
        val h = LayoutEngine.homographyToQuad(
            listOf(
                Point(100.0, 50.0),
                Point(300.0, 50.0),
                Point(300.0, 200.0),
                Point(100.0, 200.0),
            )
        )
        val den = h[6] + h[7] + h[8]
        val x = (h[0] + h[1] + h[2]) / den
        val y = (h[3] + h[4] + h[5]) / den
        assertTrue(abs(x - 300.0) < 1e-6)
        assertTrue(abs(y - 200.0) < 1e-6)
    }

    @Test
    fun `layout places camera above a full bleed photo`() {
        val camera = testAsset("Leica", "Leica M11", "m11")
        val layout = LayoutEngine.compute(FrameTheme(), camera, 6000, 4000)
        assertTrue(layout.canvasH > layout.canvasW)
        assertTrue(layout.photoRect.y > layout.cameraRect.y + layout.cameraRect.h)
        assertEquals(0.0, layout.photoRect.x, 1e-9)
        assertEquals(layout.canvasW.toDouble(), layout.photoRect.w, 1e-9)
        assertEquals(
            layout.canvasH.toDouble(),
            layout.photoRect.y + layout.photoRect.h,
            1.01
        )
        assertEquals(1.5, layout.photoRect.w / layout.photoRect.h, 0.001)
    }

    @Test
    fun `density presets match the Mac values`() {
        assertEquals(0.06, LayoutDensity.Compact.cameraMarginRatio, 1e-9)
        assertEquals(0.10, LayoutDensity.Normal.cameraMarginRatio, 1e-9)
        assertEquals(0.14, LayoutDensity.Relaxed.cameraMarginRatio, 1e-9)
    }

    @Test
    fun `screen mapping cover vs contain`() {
        fun wide(fit: ScreenFit) = testAsset("Test", "TestCam", "test").copy(
            width = 2000,
            height = 1300,
            screen = ScreenQuad(
                listOf(
                    Point(600.0, 300.0),
                    Point(1400.0, 300.0),
                    Point(1400.0, 750.0),
                    Point(600.0, 750.0),
                )
            ),
            screenFit = fit,
        )

        val contain = LayoutEngine.screenMapping(wide(ScreenFit.Contain), 1500, 1000)
        assertEquals(1.5 / (16.0 / 9.0), contain.sx * 1500, 1e-9)
        assertEquals(1.0, contain.sy * 1000, 1e-9)
        assertEquals((1 - 1.5 / (16.0 / 9.0)) / 2, contain.tx, 1e-9)

        val cover = LayoutEngine.screenMapping(wide(ScreenFit.Cover), 1500, 1000)
        assertEquals(1.0, cover.sx * 1500, 1e-9)
        assertEquals((16.0 / 9.0) / 1.5, cover.sy * 1000, 1e-9)
        assertTrue(cover.ty < 0)
    }

    @Test
    fun `real samples parse EXIF`() {
        val cases = mapOf(
            "samples/sony_a7m4.jpg" to ("SONY" to "ILCE-7M4"),
            "samples/leica_m11.jpg" to ("LEICA CAMERA AG" to "LEICA M11"),
            "samples/ricoh_gr3x.jpg" to ("RICOH IMAGING COMPANY, LTD." to "RICOH GR IIIx"),
            "samples/hasselblad_x2d2.jpg" to ("Hasselblad" to "X2D II 100C"),
            "samples/hasselblad_x2d.heic" to ("Hasselblad" to "X2D 100C"),
            "samples/leica_m11.heic" to ("LEICA CAMERA AG" to "LEICA M11"),
            "samples/fujifilm_xt5.heic" to ("FUJIFILM" to "X-T5"),
            "samples/ricoh_gr3.png" to ("RICOH IMAGING COMPANY, LTD." to "RICOH GR III"),
        )
        val missing = mutableListOf<String>()
        for ((path, expected) in cases) {
            val f = File(repoRoot, path)
            if (!f.exists()) {
                missing += path
                continue
            }
            val info = ExifReader.read(f.readBytes())
            assertEquals("$path make", expected.first, info.make)
            assertEquals("$path model", expected.second, info.model)
            info.orientation?.let { assertTrue("$path orientation", it in 1..8) }
        }
        assertTrue("missing samples: $missing", missing.isEmpty())
    }

    @Test
    fun `projected photo covers the screen for cover and stays inside for contain`() {
        val base = testAsset("Test", "TestCam", "test").copy(
            width = 2000,
            height = 1300,
            screen = ScreenQuad(
                listOf(
                    Point(600.0, 300.0),
                    Point(1400.0, 300.0),
                    Point(1400.0, 750.0),
                    Point(600.0, 750.0),
                )
            ),
        )
        val photoW = 1500
        val photoH = 1000
        for ((fit, shouldOverflow) in listOf(ScreenFit.Cover to true, ScreenFit.Contain to false)) {
            val camera = base.copy(screenFit = fit)
            val layout = LayoutEngine.compute(FrameTheme(), camera, photoW, photoH)
            val quad = LayoutEngine.photoQuadOnCanvas(layout, camera, photoW, photoH)
            val screen = layout.screenQuadOnCanvas
            val screenTop = screen.minOf { it.y }
            val screenBottom = screen.maxOf { it.y }
            val screenLeft = screen.minOf { it.x }
            val screenRight = screen.maxOf { it.x }
            if (shouldOverflow) {
                assertTrue("cover should overflow vertically", quad.minOf { it.y } < screenTop)
                assertTrue("cover should overflow vertically", quad.maxOf { it.y } > screenBottom)
            } else {
                assertTrue("contain left", quad.minOf { it.x } >= screenLeft - 0.01)
                assertTrue("contain right", quad.maxOf { it.x } <= screenRight + 0.01)
                assertTrue("contain top", quad.minOf { it.y } >= screenTop - 0.01)
                assertTrue("contain bottom", quad.maxOf { it.y } <= screenBottom + 0.01)
            }
        }
    }

    @Test
    fun `tiff preview extractor finds an embedded jpeg`() {
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0, 16, 0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
            0xFF.toByte(), 0xD9.toByte(),
        )
        val byteBuffer = java.nio.ByteBuffer.allocate(4096)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // TIFF header, IFD at 8, five entries
        byteBuffer.put(0x49); byteBuffer.put(0x49)
        byteBuffer.put(0x2A); byteBuffer.put(0)
        byteBuffer.putInt(8)
        val ifd = 8
        byteBuffer.putShort(ifd, 5)
        val previewOffset = ifd + 2 + 5 * 12 + 4
        fun entry(index: Int, tag: Int, type: Int, count: Int, value: Int) {
            val p = ifd + 2 + index * 12
            byteBuffer.putShort(p, tag.toShort())
            byteBuffer.putShort(p + 2, type.toShort())
            byteBuffer.putInt(p + 4, count)
            byteBuffer.putInt(p + 8, value)
        }
        entry(0, 0x0100, 4, 1, 1600)
        entry(1, 0x0101, 4, 1, 1200)
        entry(2, 0x0201, 4, 1, previewOffset)
        entry(3, 0x0202, 4, 1, jpeg.size)
        entry(4, 0x010F, 2, 5, 0x00594E4F) // "SONY" inline ASCII
        byteBuffer.putInt(ifd + 2 + 5 * 12, 0)
        byteBuffer.position(previewOffset)
        byteBuffer.put(jpeg)
        val tiff = byteBuffer.array().copyOf(previewOffset + jpeg.size)

        val preview = com.fotoframe.app.core.exif.TiffPreview.extractJpeg(tiff)
        assertNotNull(preview)
        assertEquals(jpeg.size, preview!!.jpeg.size)
        assertEquals(0xFF, preview.jpeg[0].toInt() and 0xFF)
        assertEquals(0xD8, preview.jpeg[1].toInt() and 0xFF)
        assertNull(com.fotoframe.app.core.exif.TiffPreview.extractJpeg(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `capture formats match the Mac panel`() {
        assertEquals("2023-05-01 12:30", com.fotoframe.app.model.CaptureFormats.date("2023:05:01 12:30:45"))
        assertEquals("f/2.8", com.fotoframe.app.model.CaptureFormats.aperture(2.8))
        assertEquals("1/250 s", com.fotoframe.app.model.CaptureFormats.shutter(0.004))
        assertEquals("+2/3 EV", com.fotoframe.app.model.CaptureFormats.bias(0.7))
        assertEquals("-1 EV", com.fotoframe.app.model.CaptureFormats.bias(-1.0))
        assertEquals("0 EV", com.fotoframe.app.model.CaptureFormats.bias(0.0))
    }

    @Test
    fun `png without EXIF is reported as missing`() {
        val f = File(repoRoot, "samples/ricoh_gr3.png")
        if (!f.exists()) return
        // ricoh_gr3.png carries EXIF; build a tiny PNG without metadata instead.
        val bytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52,
        )
        var threw = false
        try {
            ExifReader.read(bytes)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }
}