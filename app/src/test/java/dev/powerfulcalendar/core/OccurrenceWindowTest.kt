package dev.powerfulcalendar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * DSH-09 — occurrence queries must use **effective** time.
 *
 * The identity of an instance is always the original slot key; an override only moves the time it
 * actually happens at. A query for a window must therefore find an instance whose original slot is
 * far outside that window but whose effective time is inside it, and must not leak an instance that
 * was moved out.
 */
class OccurrenceWindowTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    private fun local(zone: ZoneId, text: String): Long =
        LocalDateTime.parse(text.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli()

    private fun window(zone: ZoneId, day: String): LongRange {
        val start = LocalDateTime.parse("${day}T00:00").atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.parse("${day}T00:00").plusDays(1).atZone(zone).toInstant().toEpochMilli()
        return start until end
    }

    private fun daily(
        startLocal: String,
        zone: ZoneId = shanghai,
        overrides: Map<String, OccurrenceOverride> = emptyMap(),
        exceptions: Set<String> = emptySet()
    ) = TemporalRecord(
        eventSeq = 1,
        revision = 1,
        kind = TemporalKind.ALARM,
        title = "重复",
        startMillis = local(zone, startLocal),
        zoneId = zone.id,
        recurrence = Recurrence(
            freq = RecurrenceFreq.DAILY,
            overrides = overrides,
            exceptions = exceptions
        )
    )

    private fun key(text: String): String = RecurrenceEngine.keyFor(LocalDateTime.parse(text.replace(' ', 'T')))

    // ---------------------------------------------------------------- moved into the window

    @Test
    fun anInstanceMovedFromTwoMonthsAgoIsFoundInTodaysWindow() {
        val movedTo = local(shanghai, "2026-10-20 09:00")
        val record = daily(
            "2026-01-01 07:30",
            overrides = mapOf(key("2026-08-21 07:30") to OccurrenceOverride(movedTo, null))
        )
        val day = window(shanghai, "2026-10-20")

        val occurrences = RecurrenceEngine.occurrences(record, day.first, day.last + 1)

        // The series still has its natural 2026-10-20 slot; the moved instance is additional.
        assertEquals(2, occurrences.size)
        val moved = occurrences.single { it.key == "2026-08-21T07:30" }
        assertEquals(movedTo, moved.startMillis)
        assertTrue(moved.moved)
    }

    @Test
    fun anInstanceMovedFromAFutureMonthIsFoundInTheEarlierWindow() {
        val movedTo = local(shanghai, "2026-03-15 10:00")
        val record = TemporalRecord(
            eventSeq = 1,
            revision = 1,
            kind = TemporalKind.ACTIVITY,
            title = "月度",
            startMillis = local(shanghai, "2026-01-31 09:00"),
            endMillis = local(shanghai, "2026-01-31 10:00"),
            zoneId = shanghai.id,
            recurrence = Recurrence(
                freq = RecurrenceFreq.MONTHLY,
                overrides = mapOf(
                    key("2026-11-30 09:00") to OccurrenceOverride(
                        movedTo,
                        local(shanghai, "2026-03-15 11:00")
                    )
                )
            )
        )
        val day = window(shanghai, "2026-03-15")

        val occurrences = RecurrenceEngine.occurrences(record, day.first, day.last + 1)

        // The moved instance is found even though its original slot is in a future month.
        val moved = occurrences.single { it.key == "2026-11-30T09:00" }
        assertEquals(movedTo, moved.startMillis)
        assertEquals(local(shanghai, "2026-03-15 11:00"), moved.endMillis)
        // March 31 is the natural slot for this month and is not part of the March 15 window.
        assertEquals(1, occurrences.size)
    }

    // ---------------------------------------------------------------- moved out of the window

    @Test
    fun anInstanceMovedOutOfTheWindowIsNotLeaked() {
        val record = daily(
            "2026-10-20 07:30",
            overrides = mapOf(
                key("2026-10-20 07:30") to OccurrenceOverride(local(shanghai, "2026-12-24 09:00"), null)
            )
        )
        val day = window(shanghai, "2026-10-20")

        val occurrences = RecurrenceEngine.occurrences(record, day.first, day.last + 1)

        assertEquals(0, occurrences.size)
    }

    // ---------------------------------------------------------------- DST

    @Test
    fun movingAnInstanceAcrossTheSpringForwardGapKeepsTheEffectiveInstant() {
        val movedToLocal = LocalDateTime.parse("2026-03-08T02:30") // does not exist in New York
        val movedTo = movedToLocal.atZone(newYork).toInstant().toEpochMilli()
        val record = daily(
            "2026-02-01 01:30",
            zone = newYork,
            overrides = mapOf(key("2026-03-01 01:30") to OccurrenceOverride(movedTo, null))
        )
        val day = window(newYork, "2026-03-08")

        val occurrences = RecurrenceEngine.occurrences(record, day.first, day.last + 1)

        // The moved instance is one of the day's occurrences (the series also has a natural slot).
        val occurrence = occurrences.single { it.key == "2026-03-01T01:30" }
        // Gap policy resolves forward to 03:30 EDT; the original key is preserved.
        assertEquals(movedTo, occurrence.startMillis)
        assertEquals(LocalTime.of(3, 30), Instant.ofEpochMilli(occurrence.startMillis).atZone(newYork).toLocalTime())
    }

    // ---------------------------------------------------------------- repeated edits

    @Test
    fun changingAnOverrideTwiceKeepsOneInstanceUnderTheOriginalKey() {
        val firstMove = local(shanghai, "2026-10-20 09:00")
        val secondMove = local(shanghai, "2026-10-20 11:15")
        val originalKey = key("2026-08-21 07:30")
        val record = daily(
            "2026-01-01 07:30",
            // A map only ever holds the latest value for a key; this mirrors updating the card.
            overrides = mapOf(originalKey to OccurrenceOverride(secondMove, null))
        )
        val day = window(shanghai, "2026-10-20")

        val occurrences = RecurrenceEngine.occurrences(record, day.first, day.last + 1)

        val movedInstance = occurrences.single { it.key == originalKey }
        assertEquals(secondMove, movedInstance.startMillis)
        assertTrue(movedInstance.moved)
        // There is exactly one entry for the original slot, never a stale earlier move.
        assertEquals(1, occurrences.count { it.key == originalKey })
        assertTrue(firstMove != secondMove)
    }

    // ---------------------------------------------------------------- next occurrence

    @Test
    fun nextOccurrenceFindsAnInstanceMovedHereFromLongAgo() {
        val base = daily("2026-01-01 07:30")
        val now = local(shanghai, "2026-10-20 12:00")
        val movedTo = now + 2 * 3_600_000L
        val record = base.copy(
            recurrence = base.recurrence.copy(
                overrides = mapOf(key("2026-04-02 07:30") to OccurrenceOverride(movedTo, null))
            )
        )

        val next = RecurrenceEngine.nextOccurrence(record, now)

        assertNotNull("a far-moved instance must still be the next occurrence", next)
        assertEquals(movedTo, next!!.startMillis)
        assertEquals("2026-04-02T07:30", next.key)
    }

    @Test
    fun nextOccurrenceIgnoresAnInstanceMovedBeyondTheHorizonWhenAnEarlierOneExists() {
        val base = daily("2026-10-20 07:30")
        val now = local(shanghai, "2026-10-20 06:00")
        val movedFar = local(shanghai, "2028-06-01 09:00")
        val record = base.copy(
            recurrence = base.recurrence.copy(
                overrides = mapOf(key("2026-10-20 07:30") to OccurrenceOverride(movedFar, null))
            )
        )

        val next = RecurrenceEngine.nextOccurrence(record, now)

        assertNotNull(next)
        // The natural occurrence on 2026-10-21 is earlier than the far-moved one.
        assertEquals(LocalDate.of(2026, 10, 21), Instant.ofEpochMilli(next!!.startMillis).atZone(shanghai).toLocalDate())
    }

    // ---------------------------------------------------------------- regressions

    @Test
    fun aNaturalInstanceStillAppearsExactlyOnce() {
        val record = daily("2026-10-20 07:30")
        val day = window(shanghai, "2026-10-20")
        val occurrences = RecurrenceEngine.occurrences(record, day.first, day.last + 1)
        assertEquals(1, occurrences.size)
        assertEquals(local(shanghai, "2026-10-20 07:30"), occurrences.single().startMillis)
    }

    @Test
    fun activitiesStartingBeforeTheWindowAreIncludedWhileTheyStillOverlap() {
        val record = TemporalRecord(
            eventSeq = 2,
            revision = 1,
            kind = TemporalKind.ACTIVITY,
            title = "跨天",
            startMillis = local(shanghai, "2026-10-19 20:00"),
            endMillis = local(shanghai, "2026-10-20 04:00"),
            zoneId = shanghai.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY)
        )
        val day = window(shanghai, "2026-10-20")

        val occurrences = RecurrenceEngine.occurrences(record, day.first, day.last + 1)

        // A daily 20:00–04:00 activity overlaps a calendar day twice: the tail of yesterday's
        // instance and the start of today's. Both are legitimately visible.
        assertEquals(2, occurrences.size)
        assertTrue(occurrences.any { it.startMillis == local(shanghai, "2026-10-19 20:00") })
        assertTrue(occurrences.any { it.startMillis == local(shanghai, "2026-10-20 20:00") })
    }
}
