package dev.powerfulcalendar.ui.v2

import org.junit.Assert.*
import org.junit.Test

class AxisPinchVectorTest {
    @Test fun diagonalGestureChangesBothScales() {
        val v = AxisPinch.decompose(90f, 110f, 110f, 132f, 8f)
        assertTrue(v.x > 1f)
        assertTrue(v.y > 1f)
    }
    @Test fun horizontalPinchDoesNotChangeTimeScale() {
        val v = AxisPinch.decompose(90f, 110f, 140f, 110f, 8f)
        assertTrue(v.x > 1f)
        assertEquals(1f, v.y, 0.0001f)
    }
    @Test fun verticalPinchDoesNotChangeRightEventZoom() {
        val v = AxisPinch.decompose(90f, 110f, 90f, 145f, 8f)
        assertEquals(1f, v.x, 0.0001f)
        assertTrue(v.y > 1f)
    }
    @Test fun verticalResponseIsDeliberatelyLessSensitiveThanHorizontal() {
        val x = AxisPinch.decompose(120f, 120f, 140f, 120f, 8f).x
        val y = AxisPinch.decompose(120f, 120f, 120f, 140f, 8f).y
        assertTrue("horizontal scale must be more responsive", x - 1f > (y - 1f) * 2f)
        assertTrue("vertical scale is over-sensitive", y < 1.065f)
    }
    @Test fun absoluteGestureSamplesAreContinuousAndNotCompoundedByFrames() {
        val before = AxisPinch.decompose(110f, 90f, 129f, 90f, 8f)
        val after = AxisPinch.decompose(110f, 90f, 130f, 90f, 8f)
        assertTrue(kotlin.math.abs(after.x - before.x) < 0.02f)
        assertEquals(1f, before.y, 0.00001f)
        assertEquals(1f, after.y, 0.00001f)
        assertEquals(0f, AxisPinch.dampCentroidPan(0f), 0f)
        assertTrue(AxisPinch.dampCentroidPan(1f) < 0.04f)
    }

    @Test fun tinyOrNonfiniteGesturesAreIgnored() {
        assertEquals(AxisPinch.Components(1f, 1f),
            AxisPinch.decompose(3f, 3f, 20f, 20f, 8f))
        assertEquals(AxisPinch.Components(1f, 1f),
            AxisPinch.decompose(Float.NaN, 80f, 90f, 70f, 8f))
    }
}
