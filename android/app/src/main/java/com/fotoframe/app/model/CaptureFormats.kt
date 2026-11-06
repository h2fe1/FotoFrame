package com.fotoframe.app.model

import com.fotoframe.app.core.exif.ExifInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/** Human formatting for the EXIF info sheet, matching the Mac panel wording. */
object CaptureFormats {

    /** "2023:05:01 12:30:45" → "2023-05-01 12:30" */
    fun date(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return "—"
        if (s.length >= 16 && s[4] == ':' && s[7] == ':') {
            return "${s.substring(0, 4)}-${s.substring(5, 7)}-${s.substring(8, 10)} " +
                s.substring(11, 16)
        }
        return s
    }

    fun aperture(f: Double?): String = if (f == null) "—" else "f/${"%.1f".format(f)}"

    fun shutter(t: Double?): String {
        if (t == null || t <= 0) return "—"
        if (t < 1) return "1/${(1 / t).roundToInt()} s"
        return if (t == t.roundToInt().toDouble()) {
            "${t.roundToInt()} s"
        } else {
            "${"%.1f".format(t)} s"
        }
    }

    /** Exposure compensation in 1/3 stops: +0.7 EV → "+2/3 EV". */
    fun bias(ev: Double?): String {
        if (ev == null) return "—"
        if (abs(ev) < 1e-9) return "0 EV"
        val thirds = (abs(ev) * 3).roundToInt()
        val whole = thirds / 3
        val rem = thirds % 3
        val mag = when {
            rem == 0 -> "$whole"
            whole == 0 -> "$rem/3"
            else -> "$whole-$rem/3"
        }
        return "${if (ev > 0) "+" else "-"}$mag EV"
    }

    fun coordinate(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "${"%.4f".format(abs(lat))}° $ns  ${"%.4f".format(abs(lon))}° $ew"
    }

    fun iso(exif: ExifInfo?): String = exif?.iso?.toString() ?: "—"

    fun lens(exif: ExifInfo?): String =
        exif?.lensModel?.takeIf { it.isNotBlank() } ?: "—"

    fun focal(exif: ExifInfo?): String =
        exif?.focalLength?.let { "${it.roundToInt()} mm" } ?: "—"
}