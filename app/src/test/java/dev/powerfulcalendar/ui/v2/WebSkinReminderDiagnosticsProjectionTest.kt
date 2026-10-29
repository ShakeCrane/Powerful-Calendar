package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinReminderDiagnosticsProjectionTest {
    @Test
    fun projectionIsStableCappedAtFortyAndExcludesUnneededEventContent() {
        val rows = (1L..45L).map { eventSeq ->
            val instant = 1_800_000_000_000L + eventSeq * 60_000L
            ReminderDisplay(
                record = TemporalRecord(
                    eventSeq = eventSeq,
                    revision = 2,
                    kind = TemporalKind.ALARM,
                    title = "提醒 $eventSeq",
                    notes = "private notes sentinel",
                    location = "private location sentinel",
                    startMillis = instant,
                    reminderMinutes = 10,
                    reminderMethod = ReminderMethod.ALARM,
                    zoneId = "UTC"
                ),
                intent = PowerStore.ScheduleIntent(
                    eventSeq = eventSeq,
                    triggerId = "primary",
                    revision = 2,
                    occurrenceKey = "single",
                    instantMillis = instant,
                    requirement = PowerStore.TriggerRequirement.EXACT,
                    desired = PowerStore.DesiredSchedule.BLOCKED,
                    androidHint = "需要精确闹钟权限"
                ),
                execution = "MISSED"
            )
        }

        val projected = WebSkinReminderDiagnosticsProjection.build(rows)

        assertEquals(40, projected.length())
        val first = projected.getJSONObject(0)
        val last = projected.getJSONObject(39)
        assertEquals(1L, first.getLong("eventSeq"))
        assertEquals(40L, last.getLong("eventSeq"))
        assertEquals("#0001", first.getString("displaySeq"))
        assertEquals("提醒 1", first.getString("title"))
        assertEquals("MISSED", first.getString("lastExecutionResult"))
        val intent = first.getJSONObject("intent")
        assertEquals("BLOCKED", intent.getString("desiredState"))
        assertEquals("EXACT", intent.getString("requirement"))
        assertEquals("需要精确闹钟权限", intent.getString("androidHint"))
        assertFalse(projected.toString().contains("private notes sentinel"))
        assertFalse(projected.toString().contains("private location sentinel"))
    }

    @Test
    fun projectionPreservesMissingIntentAndExecutionAsNull() {
        val row = ReminderDisplay(
            record = TemporalRecord(
                eventSeq = 7,
                revision = 1,
                kind = TemporalKind.ALARM,
                title = "No current intent",
                startMillis = 1_800_000_000_000L,
                reminderMinutes = 0,
                zoneId = "UTC"
            ),
            intent = null,
            execution = null
        )

        val projected = WebSkinReminderDiagnosticsProjection.build(listOf(row)).getJSONObject(0)

        assertTrue(projected.isNull("intent"))
        assertTrue(projected.isNull("lastExecutionResult"))
    }
}
