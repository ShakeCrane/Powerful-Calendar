package dev.powerfulcalendar.ui.v2

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TimelineSmallScreenInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val start = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone)
        .toInstant().toEpochMilli()
    private val event = TemporalRecord(
        eventSeq = 215,
        revision = 1,
        kind = TemporalKind.ACTIVITY,
        title = "SmallScreen_Clickable_Event",
        startMillis = start,
        endMillis = start + 60_000L,
        zoneId = zone.id
    )

    private fun occurrenceKey(): String {
        val localStart = Instant.ofEpochMilli(event.startMillis!!).atZone(event.zone)
            .toLocalDateTime()
        return "${event.eventSeq}:${RecurrenceEngine.keyFor(localStart)}"
    }

    private fun eventTag(): String {
        val key = occurrenceKey()
        return "timeline-event-${event.eventSeq}-${key.hashCode().toUInt().toString(16)}"
    }

    private fun eventGeometryTag(): String =
        eventTag().replace("timeline-event-", "timeline-event-geometry-")

    private fun titleTag(): String {
        val stableId = "${event.eventSeq}:${occurrenceKey()}"
        return "timeline-title-${event.eventSeq}-${stableId.hashCode().toUInt().toString(16)}"
    }

    @Test
    fun smallScreenSubPixelEventStaysReachableAndAccessible() {
        var opened: TemporalRecord? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                PowerCalTheme {
                    TimelineScreen(
                        records = listOf(event),
                        camera = TimelineCoordinate.Camera(start, 24.0),
                        nowMillis = start + 6 * 60 * 60_000L,
                        zone = zone,
                        pendingCount = 0,
                        aiLocked = false,
                        onCameraChange = {},
                        onOpenEvent = { opened = it },
                        onMoveEvent = { _, _ -> },
                        onCreate = { _, _ -> },
                        onToday = {},
                        onOpenDeck = {},
                        onOpenTools = {}
                    )
                }
            }
        }
        compose.waitForIdle()

        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("small-screen viewport must be portrait: $viewport", viewport.height > viewport.width)
        val hit = compose.onNodeWithTag(eventTag()).assertExists().assertHasClickAction()
        val hitBounds = hit.fetchSemanticsNode().boundsInRoot
        val visualBounds = compose.onNodeWithTag(eventGeometryTag(), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("sub-pixel event must keep a reachable hit target", hitBounds.height > 0f)
        assertTrue("hit-target fallback must not enlarge true event duration", visualBounds.height < hitBounds.height)
        assertTrue(
            "full title remains available to accessibility",
            hit.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                .any { event.title in it }
        )
        val title = compose.onNodeWithTag(titleTag()).assertExists()
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        println("SMALL_SCREEN_EVENT_FRAME hit=$hitBounds visual=$visualBounds title=$titleBounds viewport=$viewport")

        title.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("visible title must open its underlying event", event.title, opened?.title) }
        compose.runOnIdle { opened = null }

        hit.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(event.title, opened?.title) }

        val screenshot = File(
            requireNotNull(compose.activity.externalCacheDir),
            "pc-issue-152-small-screen.png"
        )
        screenshot.outputStream().use { output ->
            assertTrue(
                "small-screen emulator screenshot must encode",
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            )
        }
        assertTrue("screenshot must contain rendered pixels", screenshot.length() > 0L)
        println("SMALL_SCREEN_SCREENSHOT=${screenshot.absolutePath} bytes=${screenshot.length()}")
    }
}
