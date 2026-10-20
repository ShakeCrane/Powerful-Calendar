package dev.powerfulcalendar.ui.v2

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Decompose a TWO-FINGER relative displacement into perpendicular X and Y scale factors.
 * No direction arbitration/threshold cone: a diagonal pinch changes both dimensions smoothly.
 * Softening near zero finger separation avoids jumps. X and Y each have their own axis.
 */
internal object AxisPinch {
    data class Components(val x: Float, val y: Float)

    fun decompose(
        oldDx: Float, oldDy: Float, newDx: Float, newDy: Float, touchSlop: Float
    ): Components {
        if (!listOf(oldDx, oldDy, newDx, newDy, touchSlop).all(Float::isFinite) ||
            hypot(oldDx, oldDy) < touchSlop * 2f) return Components(1f, 1f)
        val softness = touchSlop.coerceAtLeast(12f)
        fun factor(before: Float, after: Float): Float {
            val value = (abs(after) + softness) / (abs(before) + softness)
            return if (abs(value - 1f) < 0.004f) 1f else value.coerceIn(0.75f, 1.25f)
        }
        return Components(factor(oldDx, newDx), factor(oldDy, newDy))
    }

    /** Preserved for old callers, but gesture recognition should use vector decomposition. */
    fun ratio(before: Float, after: Float, minimumSpan: Float): Float {
        if (!before.isFinite() || !after.isFinite() ||
            before < minimumSpan || after < minimumSpan) return 1f
        val factor = (after / before).coerceIn(0.7f, 1.3f)
        return if (abs(factor - 1f) < 0.004f) 1f else factor
    }
}
