package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class GlassPresentationTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun instant(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun event(seq: Long, from: String = "2026-10-20T09:00", to: String = "2026-10-20T10:00") = TemporalRecord(
        seq, 1, TemporalKind.ACTIVITY, "活动 $seq", startMillis = instant(from), endMillis = instant(to), zoneId = zone.id
    )

    @Test fun pinchAndPanPreserveTheInstantUnderMovingFingers() {
        val camera = TimelineCoordinate.Camera(instant("2026-10-20T12:00"), 24.0)
        val focus = TimelineCoordinate.timeAt(220f, camera.anchorMillis, 800f, camera.hours)
        val next = transformedCamera(camera, 220f, 290f, 1.7f, 800f)
        assertEquals(focus, TimelineCoordinate.timeAt(290f, next.anchorMillis, 800f, next.hours))
    }

    @Test fun pinchStillPreservesFocusAtBothZoomLimits() {
        val camera = TimelineCoordinate.Camera(instant("2026-10-20T12:00"), 24.0)
        for (zoom in listOf(0.01f, 20f)) {
            val next = transformedCamera(camera, 100f, 100f, zoom, 800f)
            assertTrue(next.hours in TimelineCoordinate.MIN_HOURS..48.0)
            assertEquals(TimelineCoordinate.timeAt(100f, camera.anchorMillis, 800f, 24.0), TimelineCoordinate.timeAt(100f, next.anchorMillis, 800f, next.hours))
        }
    }

    @Test fun verticalAndDiagonalGesturesNeverFlipOrDismiss() {
        assertEquals(0, horizontalPageSteps(90f, 260f, 72f, 110f, false))
        assertEquals(0, horizontalPageSteps(100f, 100f, 60f, 110f, true))
        assertEquals(0, horizontalPageSteps(20f, 0f, 60f, 110f, true, 8000f))
    }

    @Test fun insideAlwaysMovesAtMostOneButOutsideCanBrowseMany() {
        assertEquals(1, horizontalPageSteps(-1000f, 10f, 72f, 110f, false, -6000f))
        assertEquals(-1, horizontalPageSteps(1000f, 10f, 72f, 110f, false))
        assertTrue(horizontalPageSteps(-500f, 10f, 60f, 110f, true, -3000f) > 1)
        assertEquals(12, horizontalPageSteps(-10000f, 0f, 60f, 110f, true))
    }

    @Test fun dateOnlyDeadlineIsVisibleInNoonWindowWithoutBecomingAnInstant() {
        val record = TemporalRecord(1, 1, TemporalKind.DEADLINE, "提交报告", deadlineDate = "2026-10-20", zoneId = zone.id)
        val projection = displayOccurrences(listOf(record), instant("2026-10-20T10:00"), instant("2026-10-20T14:00"))
        assertEquals(1, projection.size)
        assertNull(projection.single().record.deadlineMillis)
        assertEquals("2026-10-20", projection.single().record.deadlineDate)
    }

    @Test fun oneOffOccurrenceProjectionHonorsTheCoreOverrideAndExceptionKey() {
        val original = event(7)
        val key = RecurrenceEngine.keyFor(
            Instant.ofEpochMilli(original.startMillis!!).atZone(zone).toLocalDateTime()
        )
        val movedTo = instant("2026-10-22T11:00")
        val overridden = original.copy(
            recurrence = Recurrence(overrides = mapOf(
                key to OccurrenceOverride(movedTo, movedTo + 3_600_000L, "已移动")
            ))
        )

        assertEquals(movedTo, singleDisplayOccurrence(overridden)!!.record.startMillis)
        assertTrue(displayOccurrences(listOf(overridden), instant("2026-10-20T00:00"), instant("2026-10-21T00:00")).isEmpty())
        val moved = displayOccurrences(listOf(overridden), instant("2026-10-22T00:00"), instant("2026-10-23T00:00")).single()
        assertEquals("7:$key", moved.key)
        assertEquals(movedTo, moved.record.startMillis)
        assertEquals("已移动", moved.record.title)

        val excepted = overridden.copy(recurrence = overridden.recurrence.copy(exceptions = setOf(key)))
        assertTrue(displayOccurrences(listOf(excepted), instant("2026-10-22T00:00"), instant("2026-10-23T00:00")).isEmpty())
    }

    @Test fun dayWindowTreatsActivityIntervalsAsHalfOpenAndPointsAsSingleDay() {
        val begin = instant("2026-10-20T00:00")
        val end = instant("2026-10-21T00:00")
        val endsAtMidnight = event(1, "2026-10-19T22:00", "2026-10-20T00:00")
        val crossesMidnight = event(2, "2026-10-19T22:00", "2026-10-20T01:00")
        val pointAtStart = TemporalRecord(3, 1, TemporalKind.ALARM, "起点", startMillis = begin, zoneId = zone.id)
        val pointAtEnd = TemporalRecord(4, 1, TemporalKind.ALARM, "终点", startMillis = end, zoneId = zone.id)

        assertFalse(overlapsDayWindow(endsAtMidnight, begin, end))
        assertTrue(overlapsDayWindow(crossesMidnight, begin, end))
        assertTrue(overlapsDayWindow(pointAtStart, begin, end))
        assertFalse(overlapsDayWindow(pointAtEnd, begin, end))
    }

    @Test fun repeatingSlotsKeepFormalOwnerAndOriginalSlotIdentity() {
        val owner = event(42, "2026-01-01T09:00", "2026-01-01T10:00").copy(recurrence = Recurrence(RecurrenceFreq.DAILY))
        val slots = displayOccurrences(listOf(owner), instant("2026-10-20T00:00"), instant("2026-10-22T00:00"))
        assertEquals(2, slots.size)
        assertTrue(slots.all { it.owner == owner })
        assertEquals(2, slots.map { it.key }.distinct().size)
        assertEquals(instant("2026-10-20T09:00"), slots.first().record.startMillis)
        assertEquals(42L, slots.first().record.eventSeq)
    }

    @Test fun ongoingActivityCrossingMidnightIsVisibleOnBothDays() {
        val record = event(1, "2026-10-19T22:00", "2026-10-20T02:00")
        assertEquals(1, displayOccurrences(listOf(record), instant("2026-10-20T00:00"), instant("2026-10-21T00:00")).size)
    }

    @Test fun repeatingDateOnlyDeadlineNeverAcquiresMinutePrecision() {
        val owner = TemporalRecord(1, 1, TemporalKind.DEADLINE, "周报", deadlineDate = "2026-10-01", zoneId = zone.id, recurrence = Recurrence(RecurrenceFreq.DAILY))
        val slots = displayOccurrences(listOf(owner), instant("2026-10-20T00:00"), instant("2026-10-22T00:00"))
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.record.isDateOnlyDeadline() && it.record.deadlineMillis == null })
    }

    @Test fun repeatingDateDeadlineRemainsVisibleAfterMidnightAndExpiresAtNextDay() {
        val owner = TemporalRecord(1, 1, TemporalKind.DEADLINE, "日结", deadlineDate = "2026-10-01", zoneId = zone.id, recurrence = Recurrence(RecurrenceFreq.DAILY))
        val noon = displayOccurrences(listOf(owner), instant("2026-10-20T10:00"), instant("2026-10-20T14:00"))
        assertEquals(listOf("2026-10-20"), noon.map { it.record.deadlineDate })
        val single = owner.copy(recurrence = Recurrence.NONE, deadlineDate = "2026-10-19")
        assertTrue(displayOccurrences(listOf(single), instant("2026-10-20T00:00"), instant("2026-10-21T00:00")).isEmpty())
    }

    @Test fun fullSnapshotIncludesAllRecurrenceConstraintsAndRemovedTimeFields() {
        val before = event(1).copy(recurrence = Recurrence(RecurrenceFreq.WEEKLY, interval = 2, count = 30,
            untilMillis = instant("2027-01-01T09:00"), byDay = setOf(DayOfWeek.TUESDAY),
            exceptions = setOf("2026-10-27T09:00"), overrides = mapOf("2026-11-03T09:00" to OccurrenceOverride(instant("2026-11-03T11:00"), null, "新标题"))))
        val proposed = TemporalConversion.convert(DraftForm.from(before), TemporalKind.DEADLINE).form.copy(recurrence = Recurrence.NONE, zoneId = "UTC")
        val rows = snapshotRows(before, proposed)
        for (label in listOf("类型", "开始", "时区", "重复频率", "重复间隔", "重复次数上限", "重复截至", "重复星期", "跳过的原始实例", "实例覆盖")) {
            assertTrue("Missing or unchanged: $label", rows.single { it.label == label }.changed)
        }
        assertTrue(rows.single { it.label == "实例覆盖" }.before.contains("新标题"))
        assertEquals(17, rows.size)
    }

    @Test fun unchangedFieldsAndLongNotesRemainAvailableInConflictReview() {
        val record = event(1).copy(notes = "长备注".repeat(1000), source = "ics")
        val rows = snapshotRows(record, DraftForm.from(record))
        assertTrue(rows.none { it.changed })
        assertEquals(record.notes, rows.single { it.label == "备注" }.after)
        assertEquals("ics", rows.single { it.label == "来源" }.after)
    }

    @Test fun overflowGroupsKeepDistantCollisionsReachableInsteadOfOneGlobalBadge() {
        val first = event(1)
        val second = event(2, "2026-10-20T09:30", "2026-10-20T11:00")
        val nextDay = event(3, "2026-10-21T09:00", "2026-10-21T10:00")
        val groups = overflowGroups(mapOf(AxisSide.RIGHT to listOf(nextDay, second, first)), 12 * 60_000L)
        assertEquals(2, groups.size)
        assertEquals(listOf(1L, 2L), groups.first().records.map { it.eventSeq })
        assertEquals(listOf(3L), groups.last().records.map { it.eventSeq })
    }

    @Test fun dstGeometryUsesElapsedTimeAndWallClockRecurrences() {
        val ny = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 10, 31).atTime(9, 0).atZone(ny).toInstant().toEpochMilli()
        val owner = TemporalRecord(1, 1, TemporalKind.ALARM, "起床", startMillis = start, zoneId = ny.id, recurrence = Recurrence(RecurrenceFreq.DAILY))
        val slots = displayOccurrences(listOf(owner), start, start + 3 * 86_400_000L)
        assertEquals(25 * 3_600_000L, slots[1].record.startMillis!! - slots[0].record.startMillis!!)
        assertEquals(9, Instant.ofEpochMilli(slots[1].record.startMillis!!).atZone(ny).hour)
    }

    @Test fun draggingPreservesTheEventZoneAndElapsedDuration() {
        val record = event(42).copy(zoneId = "America/New_York", source = "ics")
        val target = instant("2026-10-22T09:00")
        val form = movedDraftForm(record, target)
        assertEquals(target, form.parseStart())
        assertEquals(3_600_000L, form.parseEnd()!! - form.parseStart()!!)
        assertEquals("America/New_York", form.zoneId)
        assertEquals("ics", form.source)
        assertEquals(record, event(42).copy(zoneId = "America/New_York", source = "ics"))
    }

    @Test fun movingADateDeadlineOnlyChangesItsDateInItsOwnZone() {
        val record = TemporalRecord(42, 1, TemporalKind.DEADLINE, "日级截止", deadlineDate = "2026-10-20", zoneId = "America/New_York")
        val target = Instant.parse("2026-10-22T01:00:00Z").toEpochMilli()
        val form = movedDraftForm(record, target)
        assertEquals("2026-10-21", form.endText)
        assertTrue(form.parseDeadline() is DeadlineValue.Date)
        assertEquals("2026-10-20", record.deadlineDate)
    }
}
