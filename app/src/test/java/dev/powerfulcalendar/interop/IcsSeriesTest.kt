package dev.powerfulcalendar.interop

import dev.powerfulcalendar.core.FormBuild
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IcsSeriesTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun ics(vararg fields: String) = (
        listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT") +
            fields.toList() +
            listOf("END:VEVENT", "END:VCALENDAR")
        ).joinToString("\r\n", postfix = "\r\n")

    @Test fun unboundedWeeklySeriesIsOneDraftAndOneIdentity() {
        val result = IcsCodec.parse(ics(
            "UID:course@university",
            "DTSTART;TZID=Asia/Shanghai:20261019T080000",
            "DTEND;TZID=Asia/Shanghai:20261019T094000",
            "SUMMARY:高等数学",
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE"
        ))
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        assertEquals(0, result.events.size)
        assertEquals(1, result.series.size)
        val form = result.series.single().form
        assertEquals(TemporalKind.ACTIVITY, form.kind)
        assertEquals(RecurrenceFreq.WEEKLY, form.recurrence.freq)
        assertNull(form.recurrence.count)
        assertNull(form.recurrence.untilMillis)
        val ready = form.build(55L, 1L)
        assertTrue(ready.toString(), ready is FormBuild.Ready)
        val record = (ready as FormBuild.Ready).record
        val start = LocalDate.of(2026, 10, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val occurrences = RecurrenceEngine.occurrences(record, start, end)
        assertTrue(occurrences.size >= 8)
        assertEquals(setOf(55L), occurrences.map { record.eventSeq }.toSet())
        assertEquals(occurrences.size, occurrences.map { it.key }.toSet().size)
    }

    @Test fun repeatingAlarmNeedsNoEndAndCanHaveUntilOrBeForever() {
        val result = IcsCodec.parse(ics(
            "DTSTART;TZID=Asia/Shanghai:20261020T073000",
            "SUMMARY:每日吃药提醒",
            "RRULE:FREQ=DAILY"
        ))
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        assertEquals(1, result.series.size)
        assertEquals(TemporalKind.ALARM, result.series.single().form.kind)
        assertNull(result.series.single().form.recurrence.count)
        assertEquals(0, result.series.single().form.reminderMinutes)
    }

    @Test fun finiteCountAndExceptionApplyToTheSameSeries() {
        val result = IcsCodec.parse(ics(
            "DTSTART;TZID=Asia/Shanghai:20261019T100000",
            "DTEND;TZID=Asia/Shanghai:20261019T110000",
            "RRULE:FREQ=WEEKLY;COUNT=5",
            "EXDATE;TZID=Asia/Shanghai:20261026T100000"
        ))
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        val form = result.series.single().form
        assertEquals(5, form.recurrence.count)
        assertEquals(1, form.recurrence.exceptions.size)
        val record = (form.build(101, 1) as FormBuild.Ready).record
        val from = LocalDate.of(2026, 10, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(2026, 12, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(4, RecurrenceEngine.occurrences(record, from, to).size)
    }

    @Test fun missingAnchorAndUnsupportedRruleFailClosedForAi() {
        val noStart = IcsCodec.parse(ics("RRULE:FREQ=DAILY", "SUMMARY:无起点"))
        assertTrue(noStart.series.isEmpty())
        assertTrue(noStart.warnings.any { it.contains("DTSTART") })
        val unsupported = IcsCodec.parse(ics(
            "DTSTART;TZID=Asia/Shanghai:20261019T100000",
            "DTEND;TZID=Asia/Shanghai:20261019T110000",
            "RRULE:FREQ=WEEKLY;BYSETPOS=-1"
        ))
        assertTrue(unsupported.series.isEmpty())
        assertTrue(unsupported.warnings.any { it.contains("BYSETPOS") })
    }

    @Test fun recurrenceIdRequiresClarificationNotAnExtraRecord() {
        val result = IcsCodec.parse(ics(
            "DTSTART;TZID=Asia/Shanghai:20261019T100000",
            "DTEND;TZID=Asia/Shanghai:20261019T110000",
            "RECURRENCE-ID;TZID=Asia/Shanghai:20261026T100000",
            "SUMMARY:改单次"
        ))
        assertTrue(result.events.isEmpty())
        assertTrue(result.series.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test fun monthlyOnThirtyFirstIsNotSilentlyClamped() {
        val result = IcsCodec.parse(ics(
            "DTSTART;TZID=Asia/Shanghai:20261031T100000",
            "DTEND;TZID=Asia/Shanghai:20261031T110000",
            "RRULE:FREQ=MONTHLY"
        ))
        assertTrue(result.series.isEmpty())
        assertTrue(result.warnings.any { it.contains("29") })
    }
}
