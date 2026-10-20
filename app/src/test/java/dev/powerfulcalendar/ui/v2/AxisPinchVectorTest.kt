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
    @Test fun tinyOrNonfiniteGesturesAreIgnored() {
        assertEquals(AxisPinch.Components(1f, 1f),
            AxisPinch.decompose(3f, 3f, 20f, 20f, 8f))
        assertEquals(AxisPinch.Components(1f, 1f),
            AxisPinch.decompose(Float.NaN, 80f, 90f, 70f, 8f))
    }
}
