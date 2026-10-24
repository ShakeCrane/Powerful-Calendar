package dev.powerfulcalendar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class EditorDefaultsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun clock(value: String) = Clock.fixed(Instant.parse(value), ZoneOffset.UTC)

    @Test
    fun defaultsUseOneCapturedNowAndCeilCurrentInstantsToMinute() {
        val captured = EditorDefaults.capture(clock("2026-10-25T15:07:42.987Z"), zone)
        val alarm = captured.form(TemporalKind.ALARM)
        val activity = captured.form(TemporalKind.ACTIVITY)

        assertEquals("2026-10-25 23:08", alarm.startText)
        assertEquals("2026-10-25 23:08", activity.startText)
        assertEquals("2026-10-26 00:08", activity.endText)
        assertEquals(3_600_000L, activity.parseEnd()!! - activity.parseStart()!!)
        assertTrue(activity.parseStart()!! >= captured.nowMillis)
        assertTrue(activity.parseStart()!! - captured.nowMillis < 60_000L)
    }

    @Test
    fun dateOnlyDeadlineAndUndatedMemoKeepTheirPrecision() {
        val captured = EditorDefaults.capture(clock("2026-10-25T23:30:00Z"), ZoneId.of("Asia/Tokyo"))

        val deadline = captured.form(TemporalKind.DEADLINE)
        assertEquals("2026-10-26", deadline.endText)
        assertEquals(DeadlineValue.Date(LocalDate.of(2026, 10, 26)), deadline.parseDeadline())
        assertEquals(0, deadline.reminderMinutes)
        assertEquals(ReminderTiming.DAY_START, deadline.reminderTiming)

        val memo = captured.form(TemporalKind.MEMO)
        assertEquals("", memo.startText)
        assertEquals("", memo.endText)
        assertTrue(memo.trackingEnabled)
    }

    @Test
    fun explicitTimelineInstantAndMonthDateOverrideOnlyTheirSelectedComponents() {
        val captured = EditorDefaults.capture(clock("2026-10-25T15:07:42Z"), zone)
        val selectedInstant = Instant.parse("2026-11-02T03:46:59Z").toEpochMilli()
        val timelineActivity = captured.form(
            TemporalKind.ACTIVITY, EditorSelection.AtInstant(selectedInstant)
        )
        assertEquals("2026-11-02 11:46", timelineActivity.startText)
        assertEquals("2026-11-02 12:46", timelineActivity.endText)

        val timelineDeadline = captured.form(
            TemporalKind.DEADLINE, EditorSelection.AtInstant(selectedInstant)
        )
        assertEquals("2026-11-02 11:46", timelineDeadline.endText)
        assertEquals(-1, timelineDeadline.reminderMinutes)
        assertEquals(ReminderTiming.OFFSET, timelineDeadline.reminderTiming)

        val monthActivity = captured.form(
            TemporalKind.ACTIVITY, EditorSelection.OnDate(LocalDate.of(2027, 1, 3))
        )
        assertEquals("2027-01-03 23:07", monthActivity.startText)
        assertEquals("2027-01-04 00:07", monthActivity.endText)

        val selectedToday = captured.form(
            TemporalKind.ACTIVITY, EditorSelection.OnDate(captured.today)
        )
        assertEquals("2026-10-25 23:08", selectedToday.startText)

        val monthDeadline = captured.form(
            TemporalKind.DEADLINE, EditorSelection.OnDate(LocalDate.of(2027, 1, 3))
        )
        assertEquals("2027-01-03", monthDeadline.endText)
    }

    @Test
    fun zoneAndMinuteBoundariesAreDeterministicAcrossLocalMidnight() {
        val instant = "2026-10-25T23:59:59.999Z"
        val tokyo = EditorDefaults.capture(clock(instant), ZoneId.of("Asia/Tokyo"))
        val losAngeles = EditorDefaults.capture(clock(instant), ZoneId.of("America/Los_Angeles"))
        assertEquals("2026-10-26 09:00", tokyo.form(TemporalKind.ALARM).startText)
        assertEquals("2026-10-25 17:00", losAngeles.form(TemporalKind.ALARM).startText)
        assertEquals("2026-10-26", tokyo.form(TemporalKind.DEADLINE).endText)
        assertEquals("2026-10-25", losAngeles.form(TemporalKind.DEADLINE).endText)

        val boundary = EditorDefaults.capture(clock("2026-10-25T23:59:00Z"), ZoneId.of("UTC"))
        assertEquals("2026-10-25 23:59", boundary.form(TemporalKind.ALARM).startText)

        val lastSecond = EditorDefaults.capture(
            clock("2026-10-25T23:59:59.999Z"), ZoneId.of("UTC")
        )
        assertEquals("2026-10-25 23:59", lastSecond.form(TemporalKind.ALARM).startText)
        assertEquals("2026-10-25 23:59", lastSecond.form(TemporalKind.ACTIVITY).startText)
        assertTrue(lastSecond.form(TemporalKind.ALARM).parseStart()!! <= lastSecond.nowMillis)
    }

    @Test
    fun meaningfulDiffMatchesParsedSaveSemanticsInsteadOfRawInputFormatting() {
        val baseline = DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Review",
            startText = "2026-10-25 09:00",
            endText = "2026-10-25 10:00",
            notes = "notes",
            zoneId = zone.id
        )
        val sameMeaning = baseline.copy(
            title = " Review ",
            startText = "2026-10-25T09:00:00",
            endText = "2026/10/25 10:00:00",
            notes = "notes  "
        )
        assertFalse(sameMeaning.hasMeaningfulDiffFrom(baseline))

        val edited = sameMeaning.copy(title = "Changed")
        assertTrue(edited.hasMeaningfulDiffFrom(baseline))
        assertFalse(edited.copy(title = baseline.title).hasMeaningfulDiffFrom(baseline))

        val dateOnly = DraftForm(kind = TemporalKind.DEADLINE, title = "Due", endText = "2026-10-25")
        assertFalse(dateOnly.copy(endText = "2026/10/25").hasMeaningfulDiffFrom(dateOnly))
        assertTrue(dateOnly.copy(endText = "2026-10-25 00:00").hasMeaningfulDiffFrom(dateOnly))
    }

    @Test
    fun pickerSeedUsesTheCapturedZoneAndMinutePrecision() {
        val captured = EditorDefaults.capture(clock("2026-10-25T15:07:42Z"), zone)
        assertEquals(
            LocalDateTime.parse("2026-10-25T23:08").atZone(zone).toInstant().toEpochMilli(),
            captured.pickerInstant()
        )
        assertEquals(
            LocalDateTime.parse("2026-10-26T23:07").atZone(zone).toInstant().toEpochMilli(),
            captured.pickerInstant(LocalDate.of(2026, 10, 26))
        )
    }
}
