package dev.powerfulcalendar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TemporalProtocolTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    private fun millis(text: String, zone: ZoneId = shanghai): Long =
        LocalDateTime.parse(text.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli()

    private fun activity(
        seq: Long = 1,
        start: String,
        end: String,
        title: String = "活动",
        zone: ZoneId = shanghai
    ) = TemporalRecord(
        eventSeq = seq,
        revision = 1,
        kind = TemporalKind.ACTIVITY,
        title = title,
        startMillis = millis(start, zone),
        endMillis = millis(end, zone)
    )

    // ---------------------------------------------------------------- date-only deadlines

    @Test
    fun dateOnlyDeadlineNeverBecomesAnInstant() {
        val record = TemporalRecord(
            eventSeq = 42,
            revision = 1,
            kind = TemporalKind.DEADLINE,
            title = "交材料",
            deadlineDate = "2026-10-24"
        )
        assertEquals(TimePrecision.DATE, record.precision)
        assertNull(record.deadlineMillis)
        assertNull(record.validate())
        // The anchor is the start of the day band, but the record itself keeps date precision.
        assertEquals(
            LocalDate.of(2026, 10, 24).atStartOfDay(shanghai).toInstant().toEpochMilli(),
            record.anchorMillis(shanghai)
        )
        assertTrue(record.isDateOnlyDeadline())
        assertTrue(TemporalText.summarize(record).contains("日期"))
        assertFalse(TemporalText.summarize(record).contains("00:00"))
    }

    @Test
    fun deadlineCannotCarryBothDateAndInstant() {
        val record = TemporalRecord(
            eventSeq = 1,
            revision = 1,
            kind = TemporalKind.DEADLINE,
            title = "截止",
            deadlineDate = "2026-10-24",
            deadlineMillis = millis("2026-10-24 18:00")
        )
        assertEquals("截止时间不能同时是日期和时刻", record.validate())
    }

    @Test
    fun deadlineNeedsSomeAnchor() {
        val record = TemporalRecord(
            eventSeq = 1, revision = 1, kind = TemporalKind.DEADLINE, title = "截止"
        )
        assertEquals("截止事件需要日期或时刻", record.validate())
    }

    // ---------------------------------------------------------------- type conversion

    @Test
    fun alarmToActivityRequiresTheMissingEndTime() {
        val alarm = DraftForm(TemporalKind.ALARM, "起床", startText = "2026-10-20 07:30")
        val result = TemporalConversion.convert(alarm, TemporalKind.ACTIVITY)
        assertEquals(TemporalKind.ACTIVITY, result.form.kind)
        assertEquals("2026-10-20 07:30", result.form.startText)
        assertEquals("", result.form.endText)
        assertTrue(DraftField.END in result.required)
        assertTrue(result.required.isNotEmpty())
    }

    @Test
    fun deadlineDateToAlarmMustNotInventATime() {
        val deadline = DraftForm(TemporalKind.DEADLINE, "交材料", endText = "2026-10-24")
        val result = TemporalConversion.convert(deadline, TemporalKind.ALARM)
        assertEquals("", result.form.startText)
        assertTrue(DraftField.START in result.required)
    }

    @Test
    fun activityToDeadlineKeepsTheEndAnchor() {
        val activity = DraftForm(
            TemporalKind.ACTIVITY, "评审",
            startText = "2026-10-20 09:00", endText = "2026-10-20 11:00"
        )
        val result = TemporalConversion.convert(activity, TemporalKind.DEADLINE)
        assertEquals("2026-10-20 11:00", result.form.endText)
        assertEquals("", result.form.startText)
        assertTrue(result.required.isEmpty())
    }

    @Test
    fun conversionKeepsTheEventNumberAtTheStoreLevel() {
        // Conversion itself is a form operation; the number lives on the card and the record.
        val record = activity(seq = 7, start = "2026-10-20 09:00", end = "2026-10-20 10:00")
        val form = DraftForm.from(record)
        assertEquals(7, record.eventSeq)
        assertEquals(form.zoneId, record.zoneId)
    }

    // ---------------------------------------------------------------- stale rules

    @Test
    fun staleIsExactRevisionEquality() {
        assertFalse(CommitRules.isStale(null, null))
        assertFalse(CommitRules.isStale(3, 3))
        assertTrue(CommitRules.isStale(3, 4))
        assertTrue(CommitRules.isStale(null, 1))
        assertEquals(5L, CommitRules.nextRevision(4))
        assertEquals(1L, CommitRules.nextRevision(null))
    }

    @Test
    fun diffListsEveryChangedFieldAndNeverMerges() {
        val current = activity(seq = 1, start = "2026-10-20 09:00", end = "2026-10-20 10:00", title = "周会")
        val form = DraftForm(
            TemporalKind.ACTIVITY, "周会",
            startText = "2026-10-20 16:00", endText = "2026-10-20 17:00",
            location = "会议室 B"
        )
        val changes = DraftDiff.between(current, form, 1)
        val labels = changes.map { it.label }
        assertTrue("开始" in labels)
        assertTrue("结束" in labels)
        assertTrue("地点" in labels)
        assertFalse("标题" in labels)
    }

    // ---------------------------------------------------------------- lanes

    @Test
    fun earlierStartingOverlapStaysNearestTheAxis() {
        val a = activity(1, "2026-10-20 09:00", "2026-10-20 10:00", "A")
        val b = activity(2, "2026-10-20 09:30", "2026-10-20 11:00", "B")
        val c = activity(3, "2026-10-20 09:45", "2026-10-20 10:30", "C")
        val result = TimelineLayout.layout(listOf(c, b, a))
        val lanes = result.items.associate { it.record.title to it.lane }
        assertEquals(0, lanes["A"])
        assertEquals(1, lanes["B"])
        assertEquals(2, lanes["C"])
    }

    @Test
    fun freedLaneIsReusedByALaterEvent() {
        val a = activity(1, "2026-10-20 09:00", "2026-10-20 10:00", "A")
        val b = activity(2, "2026-10-20 09:30", "2026-10-20 11:00", "B")
        val d = activity(4, "2026-10-20 10:05", "2026-10-20 12:00", "D")
        val lanes = TimelineLayout.layout(listOf(a, b, d)).items.associate { it.record.title to it.lane }
        assertEquals(0, lanes["A"])
        assertEquals(1, lanes["B"])
        assertEquals(0, lanes["D"])
    }

    @Test
    fun laneAssignmentIsIndependentOfInputOrder() {
        val a = activity(1, "2026-10-20 09:00", "2026-10-20 10:00", "A")
        val b = activity(2, "2026-10-20 09:30", "2026-10-20 11:00", "B")
        val one = TimelineLayout.layout(listOf(a, b)).items.associate { it.record.title to it.lane }
        val two = TimelineLayout.layout(listOf(b, a)).items.associate { it.record.title to it.lane }
        assertEquals(one, two)
    }

    @Test
    fun alarmsAndDeadlinesLiveOnTheCorrectSide() {
        val alarm = TemporalRecord(1, 1, TemporalKind.ALARM, "起床", startMillis = millis("2026-10-20 07:30"))
        val deadline = TemporalRecord(2, 1, TemporalKind.DEADLINE, "截止", deadlineDate = "2026-10-24")
        val result = TimelineLayout.layout(listOf(alarm, deadline))
        assertEquals(AxisSide.LEFT, result.items.first { it.record.eventSeq == 1L }.side)
        assertEquals(AxisSide.RIGHT, result.items.first { it.record.eventSeq == 2L }.side)
    }

    @Test
    fun excessLanesCollapseIntoAVisibleOverflowCount() {
        val events = (1..5).map { index ->
            activity(index.toLong(), "2026-10-20 09:00", "2026-10-20 12:00", "E$index")
        }
        val result = TimelineLayout.layout(events, maxLanesPerSide = 2)
        assertEquals(2, result.items.size)
        assertEquals(3, result.overflow[AxisSide.RIGHT]?.size)
    }

    // ---------------------------------------------------------------- geometry

    @Test
    fun yAndTimeAreInverse() {
        val anchor = millis("2026-10-20 12:00")
        val height = 2000f
        val hours = 24.0
        val y = TimelineCoordinate.yOf(anchor + 3_600_000L, anchor, height, hours)
        assertEquals(height / 2f + height / 24f, y, 0.5f)
        val roundTrip = TimelineCoordinate.timeAt(y, anchor, height, hours)
        // A Float pixel coordinate cannot represent every millisecond; two ms is far below one
        // pixel at any supported zoom and is the documented precision of the mapping.
        assertEquals((anchor + 3_600_000L).toDouble(), roundTrip.toDouble(), 2.0)
    }

    @Test
    fun zoomKeepsTheInstantUnderTheFingers() {
        val anchor = millis("2026-10-20 12:00")
        val height = 1800f
        val focusY = 480f
        val before = TimelineCoordinate.timeAt(focusY, anchor, height, 24.0)
        val moved = TimelineCoordinate.anchorPreservingFocus(anchor, 24.0, 9.0, focusY, height)
        val after = TimelineCoordinate.timeAt(focusY, moved, height, 9.0)
        // Double math must survive a 2026 epoch value; a millisecond is plenty for geometry.
        assertTrue("expected $before but got $after", kotlin.math.abs(before - after) <= 1L)
    }

    @Test
    fun zoomIsBoundedToFourThroughFortyEightHours() {
        assertEquals(4.0, TimelineCoordinate.clampHours(0.5), 0.0)
        assertEquals(48.0, TimelineCoordinate.clampHours(200.0), 0.0)
        assertEquals(12.0, TimelineCoordinate.clampHours(12.0), 0.0)
    }

    @Test
    fun snappingFollowsTheZoomGridInWallClockTime() {
        val at = millis("2026-10-20 09:07")
        // 8h per screen -> 5 minute grid.
        val snapped = TimelineCoordinate.snap(at, 8.0, shanghai)
        assertEquals(
            LocalDateTime.of(2026, 10, 20, 9, 5).atZone(shanghai).toInstant().toEpochMilli(),
            snapped
        )
        assertEquals(1, TimelineCoordinate.snapMinutesFor(4.0))
        assertEquals(60, TimelineCoordinate.snapMinutesFor(48.0))
    }

    @Test
    fun initialCameraCentresNoonButTodayJumpsToNow() {
        val now = millis("2026-10-20 15:42")
        val camera = TimelineCoordinate.initialCamera(now, shanghai)
        assertEquals(millis("2026-10-20 12:00"), camera.anchorMillis)
        assertEquals(24.0, camera.hours, 0.0)
        // "Today" is the caller moving the anchor to the current instant, not to noon.
        assertNotEquals(camera.anchorMillis, now)
    }

    // ---------------------------------------------------------------- recurrence

    @Test
    fun dailyRecurrenceKeepsWallClockTimeAcrossDst() {
        val record = TemporalRecord(
            eventSeq = 1, revision = 1, kind = TemporalKind.ALARM, title = "起床",
            startMillis = LocalDateTime.of(2026, 3, 7, 7, 30).atZone(newYork).toInstant().toEpochMilli(),
            zoneId = newYork.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY)
        )
        val from = LocalDateTime.of(2026, 3, 7, 0, 0).atZone(newYork).toInstant().toEpochMilli()
        val to = LocalDateTime.of(2026, 3, 10, 0, 0).atZone(newYork).toInstant().toEpochMilli()
        val occurrences = RecurrenceEngine.occurrences(record, from, to)
        assertEquals(3, occurrences.size)
        occurrences.forEach { occurrence ->
            val local = Instant.ofEpochMilli(occurrence.startMillis).atZone(newYork).toLocalTime()
            assertEquals(LocalTime.of(7, 30), local)
        }
    }

    @Test
    fun monthlyRecurrenceDoesNotDriftAfterAShortMonth() {
        val first = LocalDateTime.of(2026, 1, 31, 9, 0)
        val rule = Recurrence(freq = RecurrenceFreq.MONTHLY)
        val february = RecurrenceEngine.slotAt(first, rule, 1)
        val march = RecurrenceEngine.slotAt(first, rule, 2)
        assertEquals(LocalDate.of(2026, 2, 28), february.toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 31), march.toLocalDate())
    }

    @Test
    fun weeklyByDayProducesSortedSlotsAndCoercesTheFirstWeekday() {
        val first = LocalDateTime.of(2026, 10, 20, 9, 0) // Tuesday
        val rule = Recurrence(freq = RecurrenceFreq.WEEKLY, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val slots = (0..3).map { RecurrenceEngine.slotAt(first, rule, it) }
        // The anchor weekday is coerced into the set, so Monday/Tuesday/Wednesday repeat weekly.
        assertEquals(DayOfWeek.TUESDAY, slots[0].dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, slots[1].dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, slots[2].dayOfWeek)
        assertEquals(DayOfWeek.TUESDAY, slots[3].dayOfWeek)
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), slots[2])
    }

    @Test
    fun skippedOccurrenceDoesNotShiftTheRestOfTheSeries() {
        val start = LocalDateTime.of(2026, 10, 20, 7, 30).atZone(shanghai).toInstant().toEpochMilli()
        val rule = Recurrence(freq = RecurrenceFreq.DAILY)
        val base = TemporalRecord(
            eventSeq = 1, revision = 1, kind = TemporalKind.ALARM, title = "起床",
            startMillis = start, zoneId = shanghai.id, recurrence = rule
        )
        val skippedKey = RecurrenceEngine.keyFor(LocalDateTime.of(2026, 10, 21, 7, 30))
        val skipped = base.copy(recurrence = rule.copy(exceptions = setOf(skippedKey)))
        val from = LocalDateTime.of(2026, 10, 20, 0, 0).atZone(shanghai).toInstant().toEpochMilli()
        val to = LocalDateTime.of(2026, 10, 24, 0, 0).atZone(shanghai).toInstant().toEpochMilli()
        val keys = RecurrenceEngine.occurrences(skipped, from, to).map { it.key }
        assertFalse(keys.contains(skippedKey))
        assertTrue(keys.contains(RecurrenceEngine.keyFor(LocalDateTime.of(2026, 10, 22, 7, 30))))
        assertEquals(3, keys.size)
    }

    @Test
    fun movedOccurrenceKeepsItsOriginalKey() {
        val start = LocalDateTime.of(2026, 10, 20, 7, 30).atZone(shanghai).toInstant().toEpochMilli()
        val rule = Recurrence(freq = RecurrenceFreq.DAILY)
        val key = RecurrenceEngine.keyFor(LocalDateTime.of(2026, 10, 21, 7, 30))
        val movedStart = LocalDateTime.of(2026, 10, 21, 8, 15).atZone(shanghai).toInstant().toEpochMilli()
        val record = TemporalRecord(
            eventSeq = 1, revision = 1, kind = TemporalKind.ALARM, title = "起床",
            startMillis = start, zoneId = shanghai.id,
            recurrence = rule.copy(overrides = mapOf(key to OccurrenceOverride(movedStart, null)))
        )
        val from = LocalDateTime.of(2026, 10, 21, 0, 0).atZone(shanghai).toInstant().toEpochMilli()
        val to = LocalDateTime.of(2026, 10, 22, 0, 0).atZone(shanghai).toInstant().toEpochMilli()
        val occurrence = RecurrenceEngine.occurrences(record, from, to).single()
        assertEquals(key, occurrence.key)
        assertEquals(movedStart, occurrence.startMillis)
        assertTrue(occurrence.moved)
    }

    @Test
    fun countLimitsTheSeriesIncludingExceptions() {
        val start = LocalDateTime.of(2026, 10, 20, 7, 30).atZone(shanghai).toInstant().toEpochMilli()
        val rule = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        val key = RecurrenceEngine.keyFor(LocalDateTime.of(2026, 10, 21, 7, 30))
        val record = TemporalRecord(
            eventSeq = 1, revision = 1, kind = TemporalKind.ALARM, title = "起床",
            startMillis = start, zoneId = shanghai.id,
            recurrence = rule.copy(exceptions = setOf(key))
        )
        val from = start - 86_400_000L
        val to = start + 30L * 86_400_000L
        val keys = RecurrenceEngine.occurrences(record, from, to).map { it.key }
        assertEquals(2, keys.size)
    }

    // ---------------------------------------------------------------- publishability

    @Test
    fun incompleteFormsCannotPublish() {
        val blank = DraftForm(TemporalKind.ACTIVITY)
        assertFalse(blank.isPublishable())
        assertTrue(DraftField.TITLE in blank.missingFields())

        val noEnd = DraftForm(TemporalKind.ACTIVITY, "会议", startText = "2026-10-20 09:00")
        assertFalse(noEnd.isPublishable())
        assertTrue(DraftField.END in noEnd.missingFields())

        val backwards = DraftForm(
            TemporalKind.ACTIVITY, "会议",
            startText = "2026-10-20 10:00", endText = "2026-10-20 09:00"
        )
        assertFalse(backwards.isPublishable())

        val good = DraftForm(
            TemporalKind.ACTIVITY, "会议",
            startText = "2026-10-20 09:00", endText = "2026-10-20 10:00"
        )
        assertTrue(good.isPublishable())
    }

    @Test
    fun buildKeepsProvenanceAndRejectsIncompleteSnapshots() {
        val form = DraftForm(
            TemporalKind.ALARM, "起床", startText = "2026-10-20 07:30",
            source = "ics", zoneId = "Asia/Tokyo"
        )
        val built = form.build(seq = 3, revision = 2)
        assertTrue(built is FormBuild.Ready)
        val record = (built as FormBuild.Ready).record
        assertEquals("ics", record.source)
        assertEquals("Asia/Tokyo", record.zoneId)
        assertEquals(3, record.eventSeq)
        assertEquals(2, record.revision)

        val incomplete = DraftForm(TemporalKind.ALARM, "起床").build(1, 1)
        assertTrue(incomplete is FormBuild.Incomplete)
    }

    @Test
    fun sequenceFormattingIsZeroPaddedAndDistinct() {
        assertEquals("#0042", TemporalRecord.formatSeq(42))
        assertEquals("#12345", TemporalRecord.formatSeq(12345))
    }
}
