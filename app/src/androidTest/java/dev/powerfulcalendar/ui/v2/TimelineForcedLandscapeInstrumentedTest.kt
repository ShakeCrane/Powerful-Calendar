package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.TimelineScreen

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TimelineForcedLandscapeInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone)
        .toInstant().toEpochMilli()

    private fun event(seq: Long, time: String, title: String): TemporalRecord {
        val start = LocalDate.of(2026, 10, 20).atTime(LocalTime.parse(time))
            .atZone(zone).toInstant().toEpochMilli()
        return TemporalRecord(
            eventSeq = seq,
            revision = 1,
            kind = TemporalKind.ACTIVITY,
            title = title,
            startMillis = start,
            endMillis = start + 60_000L,
            zoneId = zone.id
        )
    }

    private fun occurrenceKey(record: TemporalRecord): String {
        val localStart = Instant.ofEpochMilli(record.startMillis!!).atZone(record.zone)
            .toLocalDateTime()
        return "${record.eventSeq}:${RecurrenceEngine.keyFor(localStart)}"
    }

    private fun titleTag(record: TemporalRecord): String {
        val stableId = "${record.eventSeq}:${occurrenceKey(record)}"
        return "timeline-title-${record.eventSeq}-${stableId.hashCode().toUInt().toString(16)}"
    }

    private fun eventTag(record: TemporalRecord): String {
        val key = occurrenceKey(record)
        return "timeline-event-${record.eventSeq}-${key.hashCode().toUInt().toString(16)}"
    }

    private fun eventGeometryTag(record: TemporalRecord): String =
        eventTag(record).replace("timeline-event-", "timeline-event-geometry-")

    @Test
    fun forcedLandscapeShowsGreedyLeadsAndKeepsHiddenEventsActionable() {
        val events = listOf(
            event(201, "08:00", "Landscape_A"),
            event(202, "08:10", "Landscape_B"),
            event(203, "11:00", "Landscape_C"),
            event(204, "11:10", "Landscape_D")
        )
        var opened: TemporalRecord? = null

        compose.runOnUiThread {
            compose.activity.setContent {
                PowerCalTheme {
                    TimelineScreen(
                        records = events,
                        camera = TimelineCoordinate.Camera(noon, 24.0),
                        nowMillis = noon,
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
        assertTrue("test device must be forced to a landscape-sized viewport: $viewport", viewport.width > viewport.height)
        val eventFrames = events.map { compose.onNodeWithTag(eventTag(it)).fetchSemanticsNode().boundsInRoot }
        val visualFrames = events.map {
            compose.onNodeWithTag(eventGeometryTag(it), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }
        println("FORCED_LANDSCAPE_INSTRUMENTED_EVENT_FRAMES=$eventFrames visual=$visualFrames viewport=$viewport")
        events.forEachIndexed { index, record ->
            val node = compose.onNodeWithTag(eventTag(record)).assertExists().assertHasClickAction()
            assertTrue("sub-pixel event hit surface remains usable", eventFrames[index].height > 0f)
            assertTrue(
                "minimum hit target must not inflate event ${record.eventSeq} visual time geometry",
                visualFrames[index].height < eventFrames[index].height
            )
            assertTrue(
                "full event title remains accessible for ${record.eventSeq}",
                node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                    .any { record.title in it }
            )
        }

        compose.onNodeWithTag(titleTag(events[0])).assertExists()
        compose.onNodeWithTag(titleTag(events[1])).assertDoesNotExist()
        compose.onNodeWithTag(titleTag(events[2])).assertExists()
        compose.onNodeWithTag(titleTag(events[3])).assertDoesNotExist()
        val leadA = compose.onNodeWithTag(titleTag(events[0])).fetchSemanticsNode().boundsInRoot
        val leadC = compose.onNodeWithTag(titleTag(events[2])).fetchSemanticsNode().boundsInRoot
        println("FORCED_LANDSCAPE_INSTRUMENTED_TITLE_RECTS A=$leadA C=$leadC viewport=$viewport")
        assertTrue("visible greedy leads must not overlap", leadA.bottom <= leadC.top)

        compose.onNodeWithTag(eventTag(events[1])).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("Landscape_B", opened?.title) }

        val screenshot = File(
            requireNotNull(compose.activity.externalCacheDir),
            "pc-issue-151-forced-landscape.png"
        )
        screenshot.outputStream().use { output ->
            assertTrue(
                "Android emulator screenshot must be encoded",
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            )
        }
        assertTrue("screenshot must contain rendered pixels", screenshot.length() > 0L)
        println("FORCED_LANDSCAPE_SCREENSHOT=${screenshot.absolutePath} bytes=${screenshot.length()}")
    }
}
