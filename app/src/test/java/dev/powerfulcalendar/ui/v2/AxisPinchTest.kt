package dev.powerfulcalendar.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisPinchTest {
    @Test fun horizontalSpreadCanChangeWithoutVerticalScale() {
        val x = AxisPinch.ratio(120f, 150f, 24f)
        val y = AxisPinch.ratio(90f, 90f, 24f)
        assertEquals(1.25f, x, 0.0001f)
        assertEquals(1f, y, 0.0001f)
    }

    @Test fun verticalPinchLeavesHorizontalAxisSpacingUntouched() {
        assertEquals(1f, AxisPinch.ratio(105f, 105f, 24f), 0.0001f)
        assertEquals(0.8f, AxisPinch.ratio(100f, 80f, 24f), 0.0001f)
    }

    @Test fun closeOrNonfiniteFingerSpansAreIgnored() {
        assertEquals(1f, AxisPinch.ratio(3f, 20f, 24f), 0.0001f)
        assertEquals(1f, AxisPinch.ratio(Float.NaN, 80f, 24f), 0.0001f)
        assertEquals(1f, AxisPinch.ratio(100f, Float.POSITIVE_INFINITY, 24f), 0.0001f)
    }

    @Test fun oneFramePinchJumpIsBounded() {
        assertEquals(1.3f, AxisPinch.ratio(60f, 300f, 24f), 0.0001f)
        assertEquals(0.7f, AxisPinch.ratio(300f, 60f, 24f), 0.0001f)
    }
}
