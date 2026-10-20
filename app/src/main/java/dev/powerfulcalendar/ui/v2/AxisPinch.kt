package dev.powerfulcalendar.ui.v2

import kotlin.math.abs

/**
 * One axis of a two-finger pinch. Keeping X and Y independent prevents a horizontal pinch
 * from changing the temporal viewport. Small spans are unreliable and ignored.
 */
internal object AxisPinch {
    fun ratio(before: Float, after: Float, minimumSpan: Float): Float {
        if (!before.isFinite() || !after.isFinite() ||
            before < minimumSpan || after < minimumSpan) return 1f
        val factor = (after / before).coerceIn(0.7f, 1.3f)
        return if (abs(factor - 1f) < 0.004f) 1f else factor
    }
}
