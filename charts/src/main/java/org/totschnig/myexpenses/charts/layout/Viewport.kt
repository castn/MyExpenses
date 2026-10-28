package org.totschnig.myexpenses.charts.layout

/**
 * The part of a chart that is shown when it is zoomed: a point of the chart at x, y is drawn at
 * x * [scale] + [offsetX], y * [scale] + [offsetY]. The chart is never smaller than its view and
 * never moved out of it, so at scale 1 it is shown whole.
 */
data class Viewport(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {

    val isZoomed: Boolean get() = scale > 1f

    /**
     * Zoomed by [factor] around [focusX], [focusY] in the view, which stays in place, and moved by
     * [panX], [panY]; within [MIN_SCALE] and [maxScale] and kept inside a view of [width] × [height]
     */
    fun transformed(
        factor: Float,
        focusX: Float,
        focusY: Float,
        panX: Float,
        panY: Float,
        width: Float,
        height: Float,
        maxScale: Float = MAX_SCALE,
    ): Viewport {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, maxScale)
        val applied = newScale / scale
        return Viewport(
            newScale,
            focusX - (focusX - offsetX) * applied + panX,
            focusY - (focusY - offsetY) * applied + panY,
        ).clamped(width, height)
    }

    /** Kept inside a view of [width] × [height] */
    fun clamped(width: Float, height: Float) = copy(
        offsetX = offsetX.coerceIn(minOf(0f, width * (1 - scale)), 0f),
        offsetY = offsetY.coerceIn(minOf(0f, height * (1 - scale)), 0f),
    )

    /** The point of the chart drawn at [x], [y] of the view */
    fun toChartX(x: Float) = (x - offsetX) / scale
    fun toChartY(y: Float) = (y - offsetY) / scale

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
    }
}
