package dev.powerfulcalendar.ui.v2

/**
 * Exact mapping of an activity's REAL [start, end] interval to the screen's time ruler.
 * No fake minimum duration is applied to the rendered interval.
 *
 * Screen boundaries may clip an interval; clip flags describe that situation explicitly.
 * Pixel rasterization cannot represent two distinct edges less than one physical pixel apart,
 * but the underlying timestamps and geometry are never modified.
 */
object ActivityGeometry {
    data class Span(
        val topPx: Float,
        val bottomPx: Float,
        val startClipped: Boolean,
        val endClipped: Boolean
    ) {
        val heightPx: Float get() = bottomPx - topPx
    }

    fun visible(
        startMillis: Long,
        endMillis: Long,
        anchorMillis: Long,
        viewportHeightPx: Float,
        hours: Double,
        geometry: NativeTimelineGeometry
    ): Span? {
        if (endMillis <= startMillis || viewportHeightPx <= 0f) return null
        val top = geometry.yOf(startMillis, anchorMillis, viewportHeightPx, hours)
        val bottom = geometry.yOf(endMillis, anchorMillis, viewportHeightPx, hours)
        if (bottom <= 0f || top >= viewportHeightPx) return null
        val clippedTop = top.coerceAtLeast(0f)
        val clippedBottom = bottom.coerceAtMost(viewportHeightPx)
        if (clippedBottom <= clippedTop) return null
        return Span(clippedTop, clippedBottom, top < 0f, bottom > viewportHeightPx)
    }
}
