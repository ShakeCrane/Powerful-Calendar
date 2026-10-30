package dev.powerfulcalendar.ui.v2


import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp-xhdpi")
class TimelineForcedLandscapeComposeTest {
    @get:Rule val ui = createComposeRule()

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
    fun forcedLandscapeWindowKeepsGreedyTitlesAndEveryEventUsable() {
        val events = listOf(
            event(201, "08:00", "Landscape_A"),
            event(202, "08:10", "Landscape_B"),
            event(203, "11:00", "Landscape_C"),
            event(204, "11:10", "Landscape_D")
        )
        var opened: TemporalRecord? = null

        ui.setContent {
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
        ui.waitForIdle()

        val viewport = ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("fixture must force a landscape-sized viewport: $viewport", viewport.width > viewport.height)
        ui.onNodeWithContentDescription("双击当前时间刻度尺返回24小时视图").assertExists()
        val frameA = ui.onNodeWithTag(eventTag(events[0])).fetchSemanticsNode().boundsInRoot
        val frameC = ui.onNodeWithTag(eventTag(events[2])).fetchSemanticsNode().boundsInRoot
        println("FORCED_LANDSCAPE_EVENT_FRAMES A=$frameA C=$frameC viewport=$viewport")
        events.forEach { record ->
            val hitBounds = ui.onNodeWithTag(eventTag(record)).fetchSemanticsNode().boundsInRoot
            val visualBounds = ui.onNodeWithTag(
                eventGeometryTag(record), useUnmergedTree = true
            ).fetchSemanticsNode().boundsInRoot
            assertTrue("sub-pixel event must retain a nonzero click target", hitBounds.height > 0f)
            assertEquals("visible event geometry remains true to its sub-pixel duration", 0f, visualBounds.height, 0f)
        }

        ui.onNodeWithTag(titleTag(events[0])).assertExists()
        ui.onNodeWithTag(titleTag(events[1])).assertDoesNotExist()
        ui.onNodeWithTag(titleTag(events[2])).assertExists()
        ui.onNodeWithTag(titleTag(events[3])).assertDoesNotExist()
        val leadA = ui.onNodeWithTag(titleTag(events[0])).fetchSemanticsNode().boundsInRoot
        val leadC = ui.onNodeWithTag(titleTag(events[2])).fetchSemanticsNode().boundsInRoot
        println("FORCED_LANDSCAPE_TITLE_RECTS A=$leadA C=$leadC viewport=$viewport")
        assertTrue("successive visible leads must have non-overlapping text rectangles", leadA.bottom <= leadC.top)

        events.forEach { record ->
            val node = ui.onNodeWithTag(eventTag(record)).assertExists().assertHasClickAction()
            assertTrue(
                "full event title remains accessible for ${record.eventSeq}",
                node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                    .any { record.title in it }
            )
        }
        ui.onNodeWithTag(eventTag(events[1])).performTouchInput { click(center) }
        ui.runOnIdle { assertEquals("Landscape_B", opened?.title) }
    }
}
