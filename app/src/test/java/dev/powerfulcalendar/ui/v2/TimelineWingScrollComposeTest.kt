package dev.powerfulcalendar.ui.v2


import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TimelineWingScrollComposeTest {
    @get:Rule val ui = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone)
        .toInstant().toEpochMilli()

    @Test
    fun leftAndRightWingsScrollIndependentlyAndKeepTheirViewportBounds() {
        val leftEvents = (1L..8L).map { seq ->
            TemporalRecord(
                eventSeq = seq,
                revision = 1,
                kind = TemporalKind.ALARM,
                title = "Left alarm $seq",
                startMillis = noon,
                zoneId = zone.id
            )
        }
        val rightEvents = (101L..108L).map { seq ->
            TemporalRecord(
                eventSeq = seq,
                revision = 1,
                kind = TemporalKind.ACTIVITY,
                title = "Right activity $seq",
                startMillis = noon - 30 * 60_000L,
                endMillis = noon + 30 * 60_000L,
                zoneId = zone.id
            )
        }
        val allEvents = leftEvents + rightEvents

        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = allEvents,
                    camera = TimelineCoordinate.Camera(noon, 24.0),
                    nowMillis = noon,
                    zone = zone,
                    pendingCount = 0,
                    aiLocked = false,
                    onCameraChange = {},
                    onOpenEvent = {},
                    onMoveEvent = { _, _ -> },
                    onCreate = { _, _ -> },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {}
                )
            }
        }

        val leftWing = ui.onNodeWithTag("timeline-left-lanes")
        val rightWing = ui.onNodeWithTag("timeline-right-lanes")
        val leftBounds = leftWing.fetchSemanticsNode().boundsInRoot
        val rightBounds = rightWing.fetchSemanticsNode().boundsInRoot
        leftWing.assertIsDisplayed()
        rightWing.assertIsDisplayed()
        ui.onNodeWithTag(eventTag(leftEvents.first())).assertIsDisplayed()
        ui.onNodeWithTag(eventTag(leftEvents.last())).assertIsNotDisplayed()
        ui.onNodeWithTag(eventTag(rightEvents.first())).assertIsDisplayed()
        ui.onNodeWithTag(eventTag(rightEvents.last())).assertIsNotDisplayed()
        allEvents.forEach { ui.onNodeWithTag(eventTag(it)).assertExists() }

        repeat(10) { leftWing.performTouchInput { swipeRight() } }
        ui.waitForIdle()

        ui.onNodeWithTag(eventTag(leftEvents.last())).assertIsDisplayed()
        ui.onNodeWithTag(eventTag(leftEvents.first())).assertIsNotDisplayed()
        ui.onNodeWithTag(eventTag(rightEvents.last())).assertIsNotDisplayed()
        assertEquals(leftBounds, leftWing.fetchSemanticsNode().boundsInRoot)
        assertEquals(rightBounds, rightWing.fetchSemanticsNode().boundsInRoot)

        repeat(10) { rightWing.performTouchInput { swipeLeft() } }
        ui.waitForIdle()

        ui.onNodeWithTag(eventTag(rightEvents.last())).assertIsDisplayed()
        ui.onNodeWithTag(eventTag(leftEvents.last())).assertIsDisplayed()
        allEvents.forEach { ui.onNodeWithTag(eventTag(it)).assertExists() }
    }

    private fun eventTag(record: TemporalRecord): String {
        val anchor = record.anchorMillis()!!
        val localStart = Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime()
        val key = "${record.eventSeq}:${dev.powerfulcalendar.core.RecurrenceEngine.keyFor(localStart)}"
        return "timeline-event-${record.eventSeq}-${key.hashCode().toUInt().toString(16)}"
    }
}
