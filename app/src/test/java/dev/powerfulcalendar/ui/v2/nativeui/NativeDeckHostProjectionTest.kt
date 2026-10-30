package dev.powerfulcalendar.ui.v2.nativeui

import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class NativeDeckHostProjectionTest {
    private val zone = ZoneId.of("America/New_York")
    private val capturedNow = Instant.parse("2026-11-01T05:30:00Z").toEpochMilli()

    @Test
    fun editableProjectionRoundTripPreservesHostOwnedFieldsAndRecurrenceMetadata() {
        val start = DraftForm.formatInstant(capturedNow + 3_600_000L, zone)
        val form = DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "跨时区活动",
            startText = start,
            endText = DraftForm.formatInstant(capturedNow + 7_200_000L, zone),
            notes = "原始备注",
            location = "原始地点",
            reminderMinutes = 30,
            reminderMethod = ReminderMethod.ALARM,
            reminderTiming = ReminderTiming.OFFSET,
            enabled = false,
            source = "ai-proposal",
            zoneId = zone.id,
            recurrence = Recurrence(
                freq = RecurrenceFreq.WEEKLY,
                interval = 2,
                count = 12,
                activeFromDate = "2026-11-01",
                activeUntilDate = "2027-02-01",
                byDay = setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY),
                exceptions = setOf("2026-11-08T07:30"),
                overrides = mapOf("2026-11-15T07:30" to OccurrenceOverride(capturedNow, capturedNow + 1_800_000L))
            ),
            trackingEnabled = true,
            completedKeys = setOf("2026-11-08T07:30"),
            recurrenceEditScope = RecurrenceEditScope.WHOLE_SERIES,
            editOccurrenceKey = "2026-11-15T07:30"
        )

        val projected = form.toNativeDeckForm(capturedNow)
        val edited = projected.copy(title = "更新标题", reminder = projected.reminder.copy(minutes = 45))
            .toCoreForm(form)

        assertEquals("更新标题", edited.title)
        assertEquals(45, edited.reminderMinutes)
        assertEquals(form.kind, edited.kind)
        assertEquals(form.zoneId, edited.zoneId)
        assertEquals(form.source, edited.source)
        assertEquals(form.completedKeys, edited.completedKeys)
        assertEquals(form.recurrenceEditScope, edited.recurrenceEditScope)
        assertEquals(form.editOccurrenceKey, edited.editOccurrenceKey)
        assertEquals(form.recurrence.copy(freq = RecurrenceFreq.WEEKLY), edited.recurrence)
        assertTrue(projected.recoveryScoped)
        assertTrue(projected.occurrenceScoped)
        assertFalse(projected.restrictOccurrenceFields)
    }

    @Test
    fun dateOnlyDeadlineRemainsDatePrecisionAcrossTheNativeFormBridge() {
        val form = DraftForm(
            kind = TemporalKind.DEADLINE,
            title = "纯日期截止",
            endText = "2026-11-01",
            zoneId = zone.id
        )
        val projected = form.toNativeDeckForm(capturedNow)
        val mappedBack = projected.copy(title = "仍是日期").toCoreForm(form)

        assertTrue(projected.deadlineDateOnly)
        assertTrue(projected.publishable)
        assertEquals("2026-11-01", mappedBack.endText)
        assertEquals(TemporalKind.DEADLINE, mappedBack.kind)
    }

    @Test
    fun choosingNoRecurrenceUsesTheExistingCoreClearSemantics() {
        val form = DraftForm(
            kind = TemporalKind.ALARM,
            title = "重复闹钟",
            startText = DraftForm.formatInstant(capturedNow, zone),
            zoneId = zone.id,
            recurrence = Recurrence(
                freq = RecurrenceFreq.DAILY,
                interval = 4,
                count = 8,
                exceptions = setOf("2026-11-02T01:30")
            )
        )

        val mappedBack = form.toNativeDeckForm(capturedNow).copy(
            recurrenceFreq = dev.powerfulcalendar.ui.v2.NativeDeckRecurrenceFreq.NONE
        ).toCoreForm(form)

        assertEquals(Recurrence.NONE, mappedBack.recurrence)
    }
}
