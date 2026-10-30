package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.*
import org.junit.Test

class ActivityGeometryTest {
    private val anchor = 1_800_000_000_000L
    private val hour = 3_600_000L

    @Test fun startAndEndAlignExactlyWithTheSharedTimelineCoordinates() {
        val start = anchor - hour
        val end = anchor + 2 * hour
        val span = ActivityGeometry.visible(start, end, anchor, 1200f, 12.0, CoreNativeTimelineGeometry)!!
        assertEquals(TimelineCoordinate.yOf(start, anchor, 1200f, 12.0), span.topPx, 0.00001f)
        assertEquals(TimelineCoordinate.yOf(end, anchor, 1200f, 12.0), span.bottomPx, 0.00001f)
        assertEquals(300f, span.heightPx, 0.00001f)
        assertFalse(span.startClipped)
        assertFalse(span.endClipped)
    }

    @Test fun oneMinuteIntervalStaysOneMinuteHighEvenWhenNoTextFits() {
        val start = anchor
        val end = anchor + 60_000L
        val span = ActivityGeometry.visible(start, end, anchor, 960f, 24.0, CoreNativeTimelineGeometry)!!
        assertEquals(960f / 1440f, span.heightPx, 0.0001f)
        assertTrue(span.heightPx < 1f)
        assertTrue(span.heightPx < 60f)
    }

    @Test fun viewportClipDoesNotInventDifferentUnderlyingTimestamps() {
        val span = ActivityGeometry.visible(anchor - 15 * hour,
            anchor + hour, anchor, 1000f, 24.0, CoreNativeTimelineGeometry)!!
        assertEquals(0f, span.topPx, 0f)
        assertEquals(TimelineCoordinate.yOf(anchor + hour, anchor, 1000f, 24.0),
            span.bottomPx, 0.001f)
        assertTrue(span.startClipped)
        assertFalse(span.endClipped)
    }

    @Test fun completeOutsideOrInvalidIntervalHasNoRectangle() {
        assertNull(ActivityGeometry.visible(anchor, anchor, anchor, 1000f, 24.0, CoreNativeTimelineGeometry))
        assertNull(ActivityGeometry.visible(anchor + 20 * hour,
            anchor + 21 * hour, anchor, 1000f, 24.0, CoreNativeTimelineGeometry))
    }
}
