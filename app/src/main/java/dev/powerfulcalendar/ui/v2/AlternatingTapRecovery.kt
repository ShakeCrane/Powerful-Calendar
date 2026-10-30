package dev.powerfulcalendar.ui.v2

import kotlin.math.hypot

/**
 * Secret native fallback: 8 brief alternating taps (A B A B A B A B)
 * in two distinct zones. Detection uses positions and does not identify physical fingers.
 *
 * This recognizer consumes *completed* taps; the Activity observes raw touch
 * dispatch before forwarding events to any WebView or Compose skin.
 * It never modifies events or data and cannot be configured by a skin.
 */
internal class AlternatingTapRecovery(
    private val density: Float,
    private val requiredTaps: Int = 8
) {
    private data class Point(val x: Float, val y: Float)
    private var first: Point? = null
    private var second: Point? = null
    private var taps = 0
    private var startMs = 0L
    private var lastMs = 0L

    private fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)
    fun reset() {
        first = null; second = null; taps = 0; startMs = 0L; lastMs = 0L
    }

    /** True exactly on the eighth valid tap. All coordinates are screen pixels. */
    fun completedTap(x: Float, y: Float, atMs: Long, durationMs: Long): Boolean {
        if (durationMs !in 25L..240L) {
            reset()
            return false
        }
        val point = Point(x, y)
        if (taps == 0 || atMs < lastMs || atMs - lastMs > 350 ||
            atMs - startMs > 2400) {
            reset()
            first = point
            startMs = atMs
            lastMs = atMs
            taps = 1
            return false
        }
        val expected = if (taps % 2 == 0) first else second
        if (expected == null) {
            val firstPoint = first!!
            val separation = distance(point, firstPoint)
            if (separation < 48f * density || separation > 290f * density) {
                reset()
                first = point
                startMs = atMs
                lastMs = atMs
                taps = 1
                return false
            }
            second = point
        } else if (distance(point, expected) > 42f * density) {
            // Unexpected position restarts rather than counting unrelated UI taps.
            reset()
            first = point
            startMs = atMs
            lastMs = atMs
            taps = 1
            return false
        }
        taps += 1
        lastMs = atMs
        if (taps == requiredTaps) {
            reset()
            return true
        }
        return false
    }
}
