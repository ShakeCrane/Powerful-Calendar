package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class NativeMonthProjectionTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun projectionPreservesCrossDayRowsOverrideTextAndOriginalOccurrenceKey() {
        val alarmStart = LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val originalSlot = "2026-10-21T07:30"
        val alarm = TemporalRecord(
            eventSeq = 10,
            revision = 1,
            kind = TemporalKind.ALARM,
            title = "每天提醒",
            startMillis = alarmStart,
            zoneId = zone.id,
            recurrence = Recurrence(
                freq = RecurrenceFreq.DAILY,
                count = 2,
                overrides = mapOf(originalSlot to OccurrenceOverride(
                    LocalDateTime.of(2026, 10, 21, 8, 0).atZone(zone).toInstant().toEpochMilli(),
                    null,
                    "周二改名"
                ))
            )
        )
        val activity = TemporalRecord(
            eventSeq = 11,
            revision = 1,
            kind = TemporalKind.ACTIVITY,
            title = "跨午夜活动",
            startMillis = LocalDateTime.of(2026, 10, 20, 23, 30)
                .atZone(zone).toInstant().toEpochMilli(),
            endMillis = LocalDateTime.of(2026, 10, 21, 1, 0)
                .atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id
        )
        val dateOnlyDeadline = TemporalRecord(
            eventSeq = 12,
            revision = 1,
            kind = TemporalKind.DEADLINE,
            title = "日期截止",
            deadlineDate = "2026-10-22",
            zoneId = zone.id
        )

        val projection = projectNativeMonth(
            listOf(alarm, activity, dateOnlyDeadline), YearMonth.of(2026, 10), zone
        )
        val overridden = projection.rows.single { it.title == "周二改名" }
        val activityRow = projection.rows.single { it.title == "跨午夜活动" }
        val deadlineRow = projection.rows.single { it.title == "日期截止" }

        assertEquals(listOf("2026-10-21"), overridden.visibleDateKeys)
        assertEquals("10:$originalSlot", overridden.rowKey)
        assertEquals(listOf("2026-10-20", "2026-10-21"), activityRow.visibleDateKeys)
        assertEquals(listOf("2026-10-22"), deadlineRow.visibleDateKeys)
        assertTrue(deadlineRow.summary.contains("（日期）"))
        assertNull(projection.openTargets[deadlineRow.rowKey]?.occurrenceKey)
    }

    @Test
    fun openTargetsResolveOwnerAndOriginalSlotAndClearWhenMonthChanges() {
        val start = LocalDateTime.of(2026, 10, 20, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val owner = TemporalRecord(
            eventSeq = 45,
            revision = 3,
            kind = TemporalKind.ALARM,
            title = "每天闹钟",
            startMillis = start,
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 2)
        )
        val expectedOriginalKey = RecurrenceEngine.keyFor(
            LocalDateTime.of(2026, 10, 21, 9, 0)
        )
        val session = NativeMonthProjectionSession(listOf(owner), zone)
        val rows = session.rowsFor(YearMonth.of(2026, 10))
        val secondOccurrence = rows.single { it.visibleDateKeys == listOf("2026-10-21") }
        val target = session.openTarget(secondOccurrence.rowKey)

        assertEquals(owner, target?.owner)
        assertEquals(expectedOriginalKey, target?.occurrenceKey)
        assertTrue(session.rowsFor(YearMonth.of(2026, 11)).isEmpty())
        assertNull(session.openTarget(secondOccurrence.rowKey))
    }

    @Test
    fun nonRepeatingOpenTargetDoesNotInventOccurrenceScope() {
        val record = TemporalRecord(
            eventSeq = 60,
            revision = 1,
            kind = TemporalKind.DEADLINE,
            title = "单次截止",
            deadlineDate = "2026-10-23",
            zoneId = zone.id
        )
        val target = projectNativeMonth(
            listOf(record), YearMonth.of(2026, 10), zone
        ).openTargets.values.single()

        assertSame(record, target.owner)
        assertNull(target.occurrenceKey)
    }
}
