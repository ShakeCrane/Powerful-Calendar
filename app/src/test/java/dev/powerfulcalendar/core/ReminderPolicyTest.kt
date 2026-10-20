package dev.powerfulcalendar.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPolicyTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test fun defaultsSeparateEventKindFromDeliveryMethod() {
        val form = DraftForm.fresh(
            TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 21), shanghai
        )
        assertEquals(0, form.reminderMinutes)
        assertEquals(ReminderMethod.MESSAGE, form.reminderMethod)
        assertEquals(ReminderTiming.OFFSET, form.reminderTiming)
        val deadline = DraftForm.fresh(
            TemporalKind.DEADLINE, LocalDate.of(2026, 10, 21), shanghai
        )
        assertEquals(ReminderMethod.MESSAGE, deadline.reminderMethod)
        assertEquals(ReminderTiming.DAY_START, deadline.reminderTiming)
        assertEquals(0, deadline.reminderMinutes)
        assertTrue(deadline.copy(title = "交报告").isPublishable())
    }

    @Test fun dateOnlyDeadlineTriggersAtLocalMidnightWithoutInventingDeadlineTime() {
        val day = LocalDate.of(2026, 10, 21)
        val form = DraftForm.fresh(TemporalKind.DEADLINE, day, shanghai).copy(title = "论文")
        val record = (form.build(5, 1) as FormBuild.Ready).record
        assertEquals(day.toString(), record.deadlineDate)
        assertNull(record.deadlineMillis)
        assertEquals(day.atStartOfDay(shanghai).toInstant().toEpochMilli(),
            ReminderPolicy.instant(record, record.anchorMillis()!!))
    }

    @Test fun sameDayMidnightUsesCalendarDayAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val occurrence = LocalDateTime.of(2026, 3, 8, 18, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val record = TemporalRecord(
            eventSeq = 7, revision = 1, kind = TemporalKind.ACTIVITY,
            title = "DST", startMillis = occurrence,
            endMillis = occurrence + 60_000,
            reminderMinutes = 0, reminderTiming = ReminderTiming.DAY_START,
            zoneId = zone.id
        )
        assertEquals(LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant().toEpochMilli(),
            ReminderPolicy.instant(record, occurrence))
    }

    @Test fun sameActivityCanUseMessageOrExactAlarmWithoutChangingKind() {
        val time = LocalDateTime.of(2026, 10, 21, 10, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val event = TemporalRecord(
            eventSeq = 10, revision = 1, kind = TemporalKind.ACTIVITY,
            title = "组会", startMillis = time, endMillis = time + 3600000,
            reminderMinutes = 30, reminderMethod = ReminderMethod.MESSAGE
        )
        assertEquals(time - 30 * 60_000, ReminderPolicy.instant(event, time))
        assertEquals(ReminderPolicy.instant(event, time),
            ReminderPolicy.instant(event.copy(reminderMethod = ReminderMethod.ALARM), time))
        assertNull(ReminderPolicy.instant(event.copy(reminderMinutes = -1), time))
    }
}
