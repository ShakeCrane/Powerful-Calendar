package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.TimelineScreen

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.sp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TimelineTitleComposeTest {
    @get:Rule val ui = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone)
        .toInstant().toEpochMilli()

    private fun event(seq: Long, time: String, title: String) = TemporalRecord(
        eventSeq = seq,
        revision = 1,
        kind = TemporalKind.ACTIVITY,
        title = title,
        startMillis = LocalDate.of(2026, 10, 20).atTime(java.time.LocalTime.parse(time))
            .atZone(zone).toInstant().toEpochMilli(),
        endMillis = LocalDate.of(2026, 10, 20).atTime(java.time.LocalTime.parse(time))
            .atZone(zone).plusMinutes(1).toInstant().toEpochMilli(),
        zoneId = zone.id
    )

    @Test
    fun longTitleMeasurementKeepsUniformSizeAndEllipsizesOnOneLine() {
        val title = "标题".repeat(100)
        val availableWidthPx = 180
        var measurement: TimelineTitleTextMeasurement? = null

        ui.setContent {
            val textMeasurer = rememberTextMeasurer()
            val measured = measureTimelineTitleText(
                textMeasurer = textMeasurer,
                text = title,
                baseStyle = TextStyle(fontSize = 13.sp),
                availableWidthPx = availableWidthPx
            )
            SideEffect { measurement = measured }
        }
        ui.waitForIdle()

        ui.runOnIdle {
            val measured = requireNotNull(measurement)
            assertEquals(13f, measured.fontSizeSp, 0f)
            assertEquals(1, measured.layout.lineCount)
            assertTrue(measured.displayText.length < title.length)
            assertTrue(measured.displayText.endsWith("…"))
            assertEquals(measured.displayText, measured.layout.layoutInput.text.text)
            assertTrue(measured.layout.size.width <= availableWidthPx)
            assertTrue(measured.drawWidthPx <= availableWidthPx)
        }
    }

    @Test
    fun changingToLongTitleDoesNotChangeEventFrameBounds() {
        val original = event(105, "09:00", "Short")
        val longTitle = "VeryLongTitle".repeat(100)
        val records = mutableStateOf(listOf(original))
        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = records.value,
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

        val frame = ui.onNodeWithTag(eventTag(original))
        frame.assertExists()
        val initialBounds = frame.fetchSemanticsNode().boundsInRoot

        ui.runOnIdle {
            records.value = listOf(original.copy(title = longTitle))
        }
        ui.waitForIdle()

        val updatedFrame = ui.onNodeWithTag(eventTag(original))
        updatedFrame.assertExists()
        assertEquals(initialBounds, updatedFrame.fetchSemanticsNode().boundsInRoot)
        assertTrue(
            updatedFrame.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                .any { longTitle in it }
        )
        val visibleTitle = ui.onNodeWithTag(titleTag(original)).fetchSemanticsNode()
        assertTrue(visibleTitle.boundsInRoot.width < initialBounds.width)
    }

    private fun conflictRun() = listOf(
        event(101, "09:00", "Greedy_A_title"),
        event(102, "09:10", "Greedy_B_title"),
        event(103, "10:15", "Greedy_C_title"),
        event(104, "10:25", "Greedy_D_title")
    )

    private fun occurrenceKey(record: TemporalRecord): String {
        val localStart = Instant.ofEpochMilli(record.startMillis!!).atZone(record.zone).toLocalDateTime()
        return "${record.eventSeq}:${dev.powerfulcalendar.core.RecurrenceEngine.keyFor(localStart)}"
    }

    private fun titleTag(record: TemporalRecord): String {
        val stableId = "${record.eventSeq}:${occurrenceKey(record)}"
        return "timeline-title-${record.eventSeq}-${stableId.hashCode().toUInt().toString(16)}"
    }

    private fun eventTag(record: TemporalRecord): String {
        val key = occurrenceKey(record)
        return "timeline-event-${record.eventSeq}-${key.hashCode().toUInt().toString(16)}"
    }

    @Test
    fun measuredGreedyTitlesFoldButEveryEventRemainsClickableAndAccessible() {
        val events = conflictRun()
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

        ui.onNodeWithTag(titleTag(events[0])).assertExists()
        ui.onNodeWithTag(titleTag(events[1])).assertDoesNotExist()
        ui.onNodeWithTag(titleTag(events[2])).assertExists()
        ui.onNodeWithTag(titleTag(events[3])).assertDoesNotExist()

        val visibleRects = listOf(events[0], events[2]).map { event ->
            val bounds = ui.onNodeWithTag(titleTag(event)).fetchSemanticsNode().boundsInRoot
            "${event.title}=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]"
        }
        println("GREEDY_TITLE_VISIBLE_RECTS_TEST_PX ${visibleRects.joinToString("; ")}")

        events.forEach { event ->
            val eventNode = ui.onNodeWithTag(eventTag(event))
            eventNode.assertExists().assertHasClickAction()
            val contentDescriptions = eventNode.fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
            assertTrue(
                "Event ${event.eventSeq} must retain its full title in accessibility semantics",
                contentDescriptions.any { event.title in it }
            )
        }
        ui.onNodeWithTag(eventTag(events[1])).performClick()
        ui.runOnIdle { assertEquals("Greedy_B_title", opened?.title) }
    }

    @Test
    fun openingProjectedRecurringSlotPassesOwnerAndOriginalOccurrenceKey() {
        val owner = TemporalRecord(
            eventSeq = 502,
            revision = 3,
            kind = TemporalKind.ACTIVITY,
            title = "每日活动",
            startMillis = LocalDate.of(2026, 10, 20).atTime(7, 30)
                .atZone(zone).toInstant().toEpochMilli(),
            endMillis = LocalDate.of(2026, 10, 20).atTime(8, 30)
                .atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        )
        val selectedKey = "2026-10-21T07:30"
        val selectedDisplay = owner.copy(
            startMillis = LocalDate.of(2026, 10, 21).atTime(7, 30)
                .atZone(zone).toInstant().toEpochMilli(),
            endMillis = LocalDate.of(2026, 10, 21).atTime(8, 30)
                .atZone(zone).toInstant().toEpochMilli()
        )
        val selectedNoon = LocalDate.of(2026, 10, 21).atTime(12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        var openedOwner: TemporalRecord? = null
        var openedKey: String? = null
        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = listOf(owner),
                    camera = TimelineCoordinate.Camera(selectedNoon, 24.0),
                    nowMillis = noon,
                    zone = zone,
                    pendingCount = 0,
                    aiLocked = false,
                    onCameraChange = {},
                    onOpenEvent = {},
                    onOpenOccurrence = { record, key -> openedOwner = record; openedKey = key },
                    onMoveEvent = { _, _ -> },
                    onCreate = { _, _ -> },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {}
                )
            }
        }
        val selectedHit = eventTag(selectedDisplay)
        ui.onNodeWithTag(selectedHit).assertExists().performClick()
        ui.runOnIdle {
            assertEquals(owner, openedOwner)
            assertEquals(selectedKey, openedKey)
        }
    }

    @Test
    fun leftAlarmWingFoldsTitlesWithoutHidingEventTouchTargets() {
        fun alarm(seq: Long, time: String) = TemporalRecord(
            eventSeq = seq, revision = 1, kind = TemporalKind.ALARM,
            title = "Alarm_" + seq,
            startMillis = LocalDate.of(2026, 10, 20)
                .atTime(java.time.LocalTime.parse(time)).atZone(zone)
                .toInstant().toEpochMilli(),
            zoneId = zone.id
        )
        val alarms = listOf(alarm(601, "09:00"), alarm(602, "09:05"),
            alarm(603, "10:30"))
        var opened: TemporalRecord? = null
        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = alarms,
                    camera = TimelineCoordinate.Camera(noon, 24.0),
                    nowMillis = noon, zone = zone, pendingCount = 0, aiLocked = false,
                    onCameraChange = {}, onOpenEvent = { opened = it },
                    onMoveEvent = { _, _ -> }, onCreate = { _, _ -> },
                    onToday = {}, onOpenDeck = {}, onOpenTools = {}
                )
            }
        }
        ui.onNodeWithTag(titleTag(alarms[0])).assertExists()
        val leftTitleTop = ui.onNodeWithTag(titleTag(alarms[0]))
            .fetchSemanticsNode().boundsInRoot.top
        val leftLineTop = ui.onNodeWithTag(eventTag(alarms[0]))
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Alarm title must sit BELOW the original left-side line", leftTitleTop >= leftLineTop)
        ui.onNodeWithTag(titleTag(alarms[1])).assertDoesNotExist()
        ui.onNodeWithTag(titleTag(alarms[2])).assertExists()
        alarms.forEach { ui.onNodeWithTag(eventTag(it)).assertExists().assertHasClickAction() }
        ui.onNodeWithTag(eventTag(alarms[1])).performClick()
        ui.runOnIdle { assertEquals(602L, opened?.eventSeq) }
    }

    @Test
    fun outsideTitleModeUsesTheSameGreedyVisibilityRules() {
        val events = conflictRun()
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
                    onOpenEvent = {},
                    onMoveEvent = { _, _ -> },
                    onCreate = { _, _ -> },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {},
                    titlesOutside = true
                )
            }
        }
        ui.onNodeWithTag(titleTag(events[0])).assertExists()
        ui.onNodeWithTag(titleTag(events[1])).assertDoesNotExist()
        ui.onNodeWithTag(titleTag(events[2])).assertExists()
        ui.onNodeWithTag(titleTag(events[3])).assertDoesNotExist()
        events.forEach { ui.onNodeWithTag(eventTag(it)).assertExists().assertHasClickAction() }
    }

    @Test
    fun scaleChangesRecomputeTitleRectsAndAxisResetRestoresThe24HourFold() {
        val events = conflictRun()
        val activeRecords = mutableStateOf(events)
        val camera = mutableStateOf(TimelineCoordinate.Camera(
            LocalDate.of(2026, 10, 20).atTime(9, 45).atZone(zone).toInstant().toEpochMilli(),
            24.0
        ))
        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = activeRecords.value,
                    camera = camera.value,
                    nowMillis = noon,
                    zone = zone,
                    pendingCount = 0,
                    aiLocked = false,
                    onCameraChange = { camera.value = it },
                    onOpenEvent = {},
                    onMoveEvent = { _, _ -> },
                    onCreate = { _, _ -> },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {}
                )
            }
        }
        ui.onNodeWithTag(titleTag(events[0])).assertExists()
        ui.onNodeWithTag(titleTag(events[1])).assertDoesNotExist()

        ui.runOnIdle { camera.value = camera.value.copy(hours = 2.0) }
        ui.waitForIdle()
        events.forEach { ui.onNodeWithTag(titleTag(it)).assertExists() }

        ui.runOnIdle { camera.value = camera.value.copy(hours = 2.0) }
        ui.onNodeWithContentDescription("双击当前时间刻度尺返回24小时视图")
            .performTouchInput { doubleClick(center) }
        ui.runOnIdle { assertEquals(24.0, camera.value.hours, 0.0) }
        ui.onNodeWithTag(titleTag(events[0])).assertExists()
        ui.onNodeWithTag(titleTag(events[1])).assertDoesNotExist()
        ui.onNodeWithTag(titleTag(events[2])).assertExists()
        ui.onNodeWithTag(titleTag(events[3])).assertDoesNotExist()

        val nextDayEvents = events.map { event ->
            event.copy(
                eventSeq = event.eventSeq + 100,
                startMillis = event.startMillis!! + 24 * 60 * 60_000L,
                endMillis = event.endMillis!! + 24 * 60 * 60_000L
            )
        }
        ui.runOnIdle {
            activeRecords.value = nextDayEvents
            camera.value = camera.value.copy(
                anchorMillis = camera.value.anchorMillis + 24 * 60 * 60_000L
            )
        }
        ui.waitForIdle()
        nextDayEvents.forEach { ui.onNodeWithTag(eventTag(it)).assertExists().assertHasClickAction() }
        ui.onNodeWithTag(titleTag(nextDayEvents[0])).assertExists()
        ui.onNodeWithTag(titleTag(nextDayEvents[1])).assertDoesNotExist()
        ui.onNodeWithTag(titleTag(nextDayEvents[2])).assertExists()
        ui.onNodeWithTag(titleTag(nextDayEvents[3])).assertDoesNotExist()
    }

    @Test
    fun horizontalScrollReclipsMeasuredTitleRectsWithoutHidingLaneEvents() {
        val events = (1L..10L).map { seq ->
            TemporalRecord(
                eventSeq = 200L + seq,
                revision = 1,
                kind = TemporalKind.ACTIVITY,
                title = "并行泳道标题 $seq",
                startMillis = noon - 30 * 60_000L,
                endMillis = noon + 30 * 60_000L,
                zoneId = zone.id
            )
        }
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
                    onOpenEvent = {},
                    onMoveEvent = { _, _ -> },
                    onCreate = { _, _ -> },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {}
                )
            }
        }
        ui.onNodeWithTag(titleTag(events.first())).assertExists()
        ui.onNodeWithTag(titleTag(events.last())).assertDoesNotExist()
        events.forEach { ui.onNodeWithTag(eventTag(it)).assertExists().assertHasClickAction() }

        val wing = ui.onNodeWithTag("timeline-right-lanes")
        repeat(10) {
            wing.performTouchInput { swipeLeft() }
        }
        ui.waitForIdle()
        ui.onNodeWithTag(titleTag(events.last())).assertExists()
        events.forEach { ui.onNodeWithTag(eventTag(it)).assertExists() }
    }

    @Test
    fun tapOnVisibleTitleCanvasOpensItsUnderlyingEvent() {
        val record = TemporalRecord(
            eventSeq = 300,
            revision = 1,
            kind = TemporalKind.ACTIVITY,
            title = "Tap_under_visible_title",
            startMillis = LocalDate.of(2026, 10, 20).atTime(9, 0).atZone(zone)
                .toInstant().toEpochMilli(),
            endMillis = LocalDate.of(2026, 10, 20).atTime(10, 0).atZone(zone)
                .toInstant().toEpochMilli(),
            zoneId = zone.id
        )
        var opened: TemporalRecord? = null
        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = listOf(record),
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

        val visibleTitle = ui.onNodeWithTag(titleTag(record))
        visibleTitle.assertExists()
        visibleTitle.performTouchInput { click(center) }
        ui.waitForIdle()
        ui.runOnIdle { assertEquals(record.title, opened?.title) }
    }

    @Test
    fun titleCanvasDoesNotInterceptLongPressDragOnItsEvent() {
        val record = TemporalRecord(
            eventSeq = 301,
            revision = 1,
            kind = TemporalKind.ACTIVITY,
            title = "Drag_under_visible_title",
            startMillis = LocalDate.of(2026, 10, 20).atTime(9, 0).atZone(zone)
                .toInstant().toEpochMilli(),
            endMillis = LocalDate.of(2026, 10, 20).atTime(10, 0).atZone(zone)
                .toInstant().toEpochMilli(),
            zoneId = zone.id
        )
        var moved: Pair<TemporalRecord, Long>? = null
        ui.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = listOf(record),
                    camera = TimelineCoordinate.Camera(noon, 24.0),
                    nowMillis = noon,
                    zone = zone,
                    pendingCount = 0,
                    aiLocked = false,
                    onCameraChange = {},
                    onOpenEvent = {},
                    onMoveEvent = { event, target -> moved = event to target },
                    onCreate = { _, _ -> },
                    onToday = {},
                    onOpenDeck = {},
                    onOpenTools = {}
                )
            }
        }

        val title = ui.onNodeWithTag(titleTag(record))
        title.assertExists()
        title.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 80f))
            up()
        }
        ui.waitForIdle()
        ui.runOnIdle {
            assertEquals(record.eventSeq, moved?.first?.eventSeq)
            assertTrue(moved?.second != record.startMillis)
        }
    }
}
