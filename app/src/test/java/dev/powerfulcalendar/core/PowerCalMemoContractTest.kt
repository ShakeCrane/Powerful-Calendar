package dev.powerfulcalendar.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Locks the date-only / optional todo semantics introduced after the 33 design decisions. */
class PowerCalMemoContractTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun instant(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test fun undatedMemoNeedsOnlyTitleAndHasNoFakeTime() {
        val form = DraftForm(kind = TemporalKind.MEMO, title = "清理书桌", zoneId = zone.id)
        assertTrue(form.missingFields().isEmpty())
        val built = form.build(51, 1) as FormBuild.Ready
        val memo = built.record
        assertNull(memo.anchorMillis())
        assertNull(memo.memoStartDate)
        assertTrue(memo.trackingEnabled)
        assertNull(memo.validate())
        assertEquals("无日期 · 待办", TemporalText.summarize(memo))
    }

    @Test fun dateRangeMemoIsOneTaskNotAnAllDayActivity() {
        val form = DraftForm(kind = TemporalKind.MEMO, title = "AI4S 备赛",
            startText = "2026-10-21", endText = "2026-10-23", zoneId = zone.id)
        val memo = (form.build(12, 1) as FormBuild.Ready).record
        assertEquals(TemporalKind.MEMO, memo.kind)
        assertNull(memo.startMillis)
        assertNull(memo.endMillis)
        assertEquals("2026-10-23", memo.memoEndDate)
        assertTrue(memo.trackingEnabled)
        assertNull(memo.validate())
    }

    @Test fun malformedMemoDatesAndUndatedRepeatsAreRejected() {
        val invalid = DraftForm(kind = TemporalKind.MEMO, title = "日期",
            startText = "2026-10-23", endText = "2026-10-21").build(1, 1)
        assertTrue(invalid is FormBuild.Invalid)
        val repeated = DraftForm(kind = TemporalKind.MEMO, title = "无日期重复",
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY)).build(1, 1)
        assertTrue(repeated is FormBuild.Invalid)
    }

    @Test fun legacyActivityIsNotAutomaticallyAnUnfinishedTask() {
        val event = TemporalRecord(3, 1, TemporalKind.ACTIVITY, "历史课程",
            startMillis = instant("2026-10-20T09:00"),
            endMillis = instant("2026-10-20T10:00"))
        assertFalse(event.trackingEnabled)
        val tracked = DraftForm.from(event).copy(trackingEnabled = true)
        val published = (tracked.build(3, 2) as FormBuild.Ready).record
        assertTrue(published.trackingEnabled)
        assertTrue(published.completedKeys.isEmpty())
    }

    @Test fun recurrenceEffectiveDatesDoNotShiftCycleAnchor() {
        val initial = instant("2026-10-01T09:00")
        val record = TemporalRecord(eventSeq = 6, revision = 1, kind = TemporalKind.ALARM,
            title = "每天", startMillis = initial, zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY,
                activeFromDate = "2026-10-03", activeUntilDate = "2026-10-05"))
        val slots = RecurrenceEngine.occurrences(record,
            instant("2026-10-01T00:00"), instant("2026-10-07T00:00"), limit = 50)
        assertEquals(listOf("2026-10-03","2026-10-04","2026-10-05"),
            slots.map { it.key.substringBefore('T') })
        val next = RecurrenceEngine.nextOccurrence(record, instant("2026-10-02T09:00"))
        assertEquals("2026-10-03", next?.key?.substringBefore('T'))
        assertEquals(initial, record.anchorMillis())
    }

    @Test fun overlapClustersExpandIndependentlyAndDoNotCreatePhantomOverlaps() {
        val a = TemporalRecord(1, 1, TemporalKind.ACTIVITY, "A",
            startMillis = instant("2026-10-21T09:00"), endMillis = instant("2026-10-21T09:35"))
        val b = a.copy(eventSeq = 2, title = "B",
            startMillis = instant("2026-10-21T09:50"), endMillis = instant("2026-10-21T10:30"))
        val c = a.copy(eventSeq = 3, title = "C",
            startMillis = instant("2026-10-21T09:55"), endMillis = instant("2026-10-21T10:25"))
        val result = TimelineLayout.layout(listOf(a,b,c), 3).items.associateBy { it.record.eventSeq }
        assertEquals(1, result[1]?.clusterLanes)
        assertEquals(2, result[2]?.clusterLanes)
        assertEquals(2, result[3]?.clusterLanes)
    }
}
