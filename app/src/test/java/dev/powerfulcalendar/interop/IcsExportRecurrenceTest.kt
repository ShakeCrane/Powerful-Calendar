package dev.powerfulcalendar.interop

import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class IcsExportRecurrenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val first = LocalDateTime.of(2026, 10, 26, 14, 0)
    private val start = first.atZone(zone).toInstant().toEpochMilli()

    private fun activity(
        recurrence: Recurrence,
        zoneId: String = zone.id,
        at: Long = start
    ): TemporalRecord = TemporalRecord(
        eventSeq = 41, revision = 1, kind = TemporalKind.ACTIVITY,
        title = "Recurring test", startMillis = at,
        endMillis = at + 3_600_000,
        recurrence = recurrence, zoneId = zoneId
    )

    @Test fun dailySeriesExportsRruleAndImportsAsOneSemanticSeries() {
        val text = IcsCodec.exportActivities(listOf(
            activity(Recurrence(freq = RecurrenceFreq.DAILY, interval = 2))
        ))
        assertTrue(text.contains("RRULE:FREQ=DAILY;INTERVAL=2\r\n"))
        assertTrue(text.contains("DTSTART;TZID=Asia/Shanghai:20261026T140000"))
        val parsed = IcsCodec.parse(text)
        assertTrue(parsed.warnings.toString(), parsed.warnings.isEmpty())
        assertEquals(0, parsed.events.size)
        assertEquals(1, parsed.series.size)
        assertEquals(RecurrenceFreq.DAILY, parsed.series.single().form.recurrence.freq)
        assertEquals(2, parsed.series.single().form.recurrence.interval)
        assertEquals(zone.id, parsed.series.single().form.zoneId)
    }

    @Test fun weeklyCountByDayAndExceptionRoundTrip() {
        val rule = Recurrence(
            freq = RecurrenceFreq.WEEKLY, interval = 1,
            count = 9, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            exceptions = setOf("2026-10-28T14:00")
        )
        val text = IcsCodec.exportActivities(listOf(activity(rule)))
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=9;BYDAY=MO,WE"))
        assertTrue(text.contains("EXDATE;TZID=Asia/Shanghai:20261028T140000"))
        val parsed = IcsCodec.parse(text)
        assertTrue(parsed.warnings.toString(), parsed.warnings.isEmpty())
        assertEquals(rule, parsed.series.single().form.recurrence)
    }

    @Test fun utcUntilIsPreservedWithoutReplacingByCount() {
        val until = LocalDateTime.of(2026, 11, 25, 16, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val rule = Recurrence(freq = RecurrenceFreq.DAILY, untilMillis = until)
        val text = IcsCodec.exportActivities(listOf(activity(rule)))
        assertTrue(text.contains(";UNTIL=20261125T080000Z"))
        assertEquals(until, IcsCodec.parse(text).series.single().form.recurrence.untilMillis)
    }

    @Test fun dstZoneKeepsWallClockAnchor() {
        val ny = ZoneId.of("America/New_York")
        val at = LocalDateTime.of(2026, 10, 26, 14, 0)
            .atZone(ny).toInstant().toEpochMilli()
        val text = IcsCodec.exportActivities(listOf(
            activity(Recurrence(freq = RecurrenceFreq.DAILY), ny.id, at)
        ))
        assertTrue(text.contains("DTSTART;TZID=America/New_York:20261026T140000"))
        val parsed = IcsCodec.parse(text)
        assertTrue(parsed.warnings.toString(), parsed.warnings.isEmpty())
        assertEquals("America/New_York", parsed.series.single().form.zoneId)
    }

    @Test fun unsupportedOverridesAndActiveRangesFailClosed() {
        val overridden = Recurrence(
            freq = RecurrenceFreq.DAILY,
            overrides = mapOf("2026-10-27T14:00" to
                OccurrenceOverride(start + 86_400_000, start + 90_000_000))
        )
        assertThrows(IllegalArgumentException::class.java) {
            IcsCodec.exportActivities(listOf(activity(overridden)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IcsCodec.exportActivities(listOf(activity(
                Recurrence(freq = RecurrenceFreq.DAILY, activeFromDate = "2026-10-27"))))
        }
    }

    @Test fun invalidMonthlyDayAndNonActivityAreNotSilentlySerialized() {
        val on31st = LocalDateTime.of(2026, 10, 31, 14, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertThrows(IllegalArgumentException::class.java) {
            IcsCodec.exportActivities(listOf(activity(
                Recurrence(freq = RecurrenceFreq.MONTHLY), at = on31st)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IcsCodec.exportActivities(listOf(activity(Recurrence.NONE)
                .copy(kind = TemporalKind.ALARM)))
        }
    }
}
