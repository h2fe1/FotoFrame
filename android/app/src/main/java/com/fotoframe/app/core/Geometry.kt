package com.fotoframe.app.core

import kotlin.math.max
import kotlin.math.min

/** A point in image / canvas coordinates. */
data class Point(val x: Double, val y: Double)

/** A rectangle matching the Dart core's `Rect` (x, y, width, height). */
data class RectD(val x: Double, val y: Double, val w: Double, val h: Double) {
    val cx: Double get() = x + w / 2
    val cy: Double get() = y + h / 2
    val right: Double get() = x + w
    val bottom: Double get() = y + h
}

/** Bounding box of four arbitrary corners. */
data class Bounds(val x: Double, val y: Double, val w: Double, val h: Double)

fun List<Point>.bounds(): Bounds {
    require(isNotEmpty())
    var minX = first().x
    var maxX = first().x
    var minY = first().y
    var maxY = first().y
    for (p in this) {
        minX = min(minX, p.x)
        maxX = max(maxX, p.x)
        minY = min(minY, p.y)
        maxY = max(maxY, p.y)
    }
    return Bounds(minX, minY, maxX - minX, maxY - minY)
}