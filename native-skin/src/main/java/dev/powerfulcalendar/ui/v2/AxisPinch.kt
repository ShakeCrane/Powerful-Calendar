package dev.powerfulcalendar.ui.v2

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Absolute two-finger X/Y decomposition. Always compare to the gesture's original
 * separation, NEVER accumulate individually rounded frame ratios. This removes
 * FPS-dependent drift and keeps diagonal fingers continuously two-dimensional.
 */
object AxisPinch {
    data class Components(val x: Float, val y: Float)

    fun decompose(
        oldDx: Float, oldDy: Float, newDx: Float, newDy: Float, touchSlop: Float
    ): Components {
        if (!listOf(oldDx, oldDy, newDx, newDy, touchSlop).all(Float::isFinite) ||
            hypot(oldDx, oldDy) < touchSlop * 2f) return Components(1f, 1f)
        // Regularize near-axis orientations. Smooth everywhere including near zero.
        val softness = 42.0
        fun factor(before: Float, after: Float, gain: Double): Float {
            val b = sqrt(before.toDouble() * before + softness * softness)
            val a = sqrt(after.toDouble() * after + softness * softness)
            return exp((ln(a / b) * gain).coerceIn(-3.0, 3.0)).toFloat()
        }
        // Horizontal zoom is deliberately more responsive than vertical time zoom.
        return Components(
            x = factor(oldDx, newDx, 1.2),
            y = factor(oldDy, newDy, 0.32)
        )
    }

    /** Smoothly damp accidental centroid wobble while retaining intentional pan. */
    fun dampCentroidPan(deltaPx: Float): Float =
        deltaPx * abs(deltaPx) / (abs(deltaPx) + 28f)

    /** Legacy ratio retained for backwards-compatible contracts/tests. */
    fun ratio(before: Float, after: Float, minimumSpan: Float): Float {
        if (!before.isFinite() || !after.isFinite() ||
            before < minimumSpan || after < minimumSpan) return 1f
        val factor = (after / before).coerceIn(0.7f, 1.3f)
        return if (abs(factor - 1f) < 0.004f) 1f else factor
    }
}
