package com.fotoframe.app.core.catalog

import android.content.res.AssetManager
import org.json.JSONArray

/** Result of matching an EXIF make/model to a camera asset. */
data class CameraMatch(val asset: CameraAsset, val how: MatchQuality)

/** How confident the match is; shows up as a short Chinese label in the UI. */
enum class MatchQuality(val label: String) {
    Alias("型号别名"),
    Exact("精确匹配"),
    Suffix("型号匹配"),
    Pattern("规则匹配"),
}

/**
 * Known manufacturer-internal model → asset slug aliases.
 *
 * Mostly Sony, whose EXIF `Model` uses the ILCE code rather than the marketing name.
 */
val kModelAliases: Map<String, String> = mapOf(
    // Sony ILCE → slug
    "ILCE-7M3" to "a7m3",
    "ILCE-7RM3" to "a7r3",
    "ILCE-7RM3A" to "a7r3",
    "ILCE-7RM4" to "a7r4",
    "ILCE-7RM4A" to "a7r4",
    "ILCE-7RM5" to "a7r5",
    "ILCE-9" to "a9",
    "ILCE-9M2" to "a9m2",
    "ILCE-9M3" to "a9m3",
    "ILCE-7SM3" to "a7s3",
    "ILCE-7M4" to "a7m4",
    "ILCE-7M5" to "a7m5",
    "ILCE-7C" to "a7c",
    "ILCE-7CM2" to "a7c2",
    "ILCE-7CR" to "a7cr",
    "ILCE-1" to "a1",
    "ILCE-1M2" to "a1m2",
    "ILCE-6700" to "a6700",
    "ILCE-6600" to "a6600",
    "ZV-E1" to "zv-e1",
    "DSC-RX1RM2" to "rx1r2",
    "DSC-RX1RM3" to "rx1r3",
)

/** Strip NUL / control characters and surrounding whitespace from EXIF strings. */
fun normalizeExifString(raw: String?): String {
    if (raw == null) return ""
    return raw.replace(Regex("[\\x00-\\x1F]"), "").trim()
}

/** EXIF (Make, Model) → camera asset matcher. Mirrors the Dart implementation. */
class CameraCatalog(val assets: List<CameraAsset>) {

    private val bySlug: Map<String, CameraAsset> = assets.associateBy { it.slug }

    private val byNormalizedModel: Map<String, CameraAsset> =
        assets.associateBy { normalizeModel(it.model) }

    fun bySlug(slug: String): CameraAsset? = bySlug[slug]

    fun match(make: String?, model: String?): CameraMatch? {
        val mk = normalizeExifString(make)
        val md = normalizeExifString(model)
        if (md.isEmpty() && mk.isEmpty()) return null

        // 1. Alias table (Sony internal codes, etc.)
        val alias = kModelAliases[md.uppercase()]
        if (alias != null) {
            bySlug[alias]?.let { return CameraMatch(it, MatchQuality.Alias) }
        }

        // 2. Normalized exact model ("LEICA M11" / "Leica M11" / "leicam11")
        val norm = normalizeModel(md)
        byNormalizedModel[norm]?.let { return CameraMatch(it, MatchQuality.Exact) }

        // 2.5 Suffix match: EXIF often prefixes the brand ("RICOH GR III" vs asset "GR III").
        // Pick the longest asset model so "GR III" cannot steal "GR IIIx".
        var suffixHit: CameraAsset? = null
        var suffixLen = 3
        for (asset in assets) {
            val an = normalizeModel(asset.model)
            if (an.length > suffixLen && norm.endsWith(an)) {
                suffixLen = an.length
                suffixHit = asset
            }
        }
        if (suffixHit != null) return CameraMatch(suffixHit, MatchQuality.Suffix)

        // 3. Per-asset regex rules; the more specific (longer) pattern wins.
        var patternHit: CameraAsset? = null
        var patternScore = 0
        for (asset in assets) {
            val makeOk = asset.exif.makePatterns.isEmpty() ||
                asset.exif.makePatterns.any { p ->
                    runCatching { Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(mk) }
                        .getOrDefault(false)
                }
            if (!makeOk) continue
            for (p in asset.exif.modelPatterns) {
                val hit = runCatching {
                    Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(md)
                }.getOrDefault(false)
                if (hit && p.length > patternScore) {
                    patternScore = p.length
                    patternHit = asset
                }
            }
        }
        if (patternHit != null) return CameraMatch(patternHit, MatchQuality.Pattern)

        return null
    }

    companion object {
        private fun normalizeModel(s: String): String =
            s.lowercase().replace(Regex("[\\s\\-_]+"), "")

        /** Parse the bundled catalog JSON. Pure JVM-friendly. */
        fun fromJson(raw: String): CameraCatalog {
            val array = JSONArray(raw)
            val list = ArrayList<CameraAsset>(array.length())
            for (i in 0 until array.length()) {
                list += CameraAsset.fromJson(array.getJSONObject(i))
            }
            return CameraCatalog(list)
        }

        fun loadFromAssets(assets: AssetManager, path: String = "cameras/catalog.json"): CameraCatalog {
            val text = assets.open(path).bufferedReader().use { it.readText() }
            return fromJson(text)
        }
    }
}