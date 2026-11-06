package com.fotoframe.app.core.catalog

import com.fotoframe.app.core.Bounds
import com.fotoframe.app.core.Point
import com.fotoframe.app.core.bounds
import org.json.JSONArray
import org.json.JSONObject

/**
 * How the photo fills the camera screen.
 *
 * - [Cover]: fill the screen, cropping the overflow (default; looks fullest).
 * - [Contain]: show the whole photo, letterboxing when the aspect differs — how the
 *   real camera plays back a photo.
 */
enum class ScreenFit(val wireName: String) {
    Cover("cover"),
    Contain("contain");

    companion object {
        fun parse(raw: String?): ScreenFit = if (raw == "contain") Contain else Cover
    }
}

/** Four screen corners, clockwise from top-left. */
data class ScreenQuad(val corners: List<Point>) {
    init {
        require(corners.size == 4) { "screen quad must have 4 corners" }
    }

    val bounds: Bounds get() = corners.bounds()

    companion object {
        fun fromJson(json: JSONArray): ScreenQuad {
            require(json.length() == 4) { "screen quad must have 4 corners" }
            return ScreenQuad(
                (0 until 4).map { i ->
                    val p = json.getJSONArray(i)
                    Point(p.getDouble(0), p.getDouble(1))
                }
            )
        }
    }
}

/** EXIF fingerprints used by the fallback matcher. */
data class ExifSignature(
    val makePatterns: List<String> = emptyList(),
    val modelPatterns: List<String> = emptyList(),
) {
    companion object {
        fun fromJson(json: JSONObject?): ExifSignature {
            if (json == null) return ExifSignature()
            return ExifSignature(
                makePatterns = json.optJSONArray("make_patterns").toStringList(),
                modelPatterns = json.optJSONArray("model_patterns").toStringList(),
            )
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotEmpty) }
        }
    }
}

/** One camera back asset, mirroring the catalog entry used by the macOS app. */
data class CameraAsset(
    val brand: String,
    val model: String,
    val slug: String,
    val imageFile: String,
    val width: Int,
    val height: Int,
    val screen: ScreenQuad,
    val opening: ScreenQuad? = null,
    val screenFit: ScreenFit = ScreenFit.Cover,
    val exif: ExifSignature = ExifSignature(),
    val sourceUrl: String? = null,
    val sourcePage: String? = null,
) {
    /** Packaged asset path inside the APK. */
    val imageAssetPath: String get() = "cameras/$imageFile"

    val displayName: String
        get() = if (model.lowercase().startsWith(brand.lowercase())) model else "$brand $model"

    companion object {
        fun fromJson(json: JSONObject): CameraAsset {
            val screen = json.getJSONObject("screen")
            val openingJson = screen.optJSONArray("opening")
            return CameraAsset(
                brand = json.getString("brand"),
                model = json.getString("model"),
                slug = json.getString("slug"),
                imageFile = json.optString("image", "back.png"),
                width = json.getInt("width"),
                height = json.getInt("height"),
                screen = ScreenQuad.fromJson(screen.getJSONArray("quad")),
                opening = openingJson?.let { ScreenQuad.fromJson(it) },
                screenFit = ScreenFit.parse(screen.optString("fit").takeIf { it.isNotEmpty() }),
                exif = ExifSignature.fromJson(json.optJSONObject("exif")),
                sourceUrl = json.optString("source_url").takeIf { it.isNotEmpty() },
                sourcePage = json.optString("source_page").takeIf { it.isNotEmpty() },
            )
        }
    }
}