package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceFeatureApiV1Test {
    private val api = CoreRecurrenceFeatureApiV1()

    @Test
    fun dailyAlarmKeepsExistingWallClockBehaviorAcrossDstFallback() {
        val zone = ZoneId.of("America/New_York")
        val anchor = LocalDate.of(2026, 10, 31).atTime(LocalTime.of(9, 0))
            .atZone(zone).toInstant().toEpochMilli()
        val record = TemporalRecord(
            eventSeq = 9,
            revision = 7,
            kind = TemporalKind.ALARM,
            title = "DST preview",
            startMillis = anchor,
            recurrence = Recurrence(RecurrenceFreq.DAILY),
            zoneId = zone.id
        )
        val afterMillis = Instant.parse("2026-11-01T06:00:00Z").toEpochMilli()
        val expected = requireNotNull(RecurrenceEngine.nextOccurrence(record, afterMillis, zone))

        val actual = api.queryNextOccurrence(
            RecurrenceNextOccurrenceQueryV1(record.toEventRecordV1(), afterMillis, zone.id)
        )

        assertEquals(record.eventSeq, actual.eventSeq)
        assertEquals(record.revision, actual.eventRevision)
        assertNotNull(actual.occurrence)
        assertEquals(expected.key, actual.occurrence?.key)
        assertEquals(expected.startMillis, actual.occurrence?.startMillis)
        assertEquals(expected.endMillis, actual.occurrence?.endMillis)
        assertEquals(expected.skipped, actual.occurrence?.skipped)
        assertEquals(expected.moved, actual.occurrence?.moved)
        assertEquals(
            Instant.parse("2026-11-01T14:00:00Z").toEpochMilli(),
            actual.occurrence?.startMillis
        )
    }

    @Test
    fun endedRecurrenceReturnsNullOccurrenceAndPreservesSnapshotIdentity() {
        val record = TemporalRecord(
            eventSeq = 17,
            revision = 4,
            kind = TemporalKind.ALARM,
            title = "Ended series",
            startMillis = Instant.parse("2026-10-28T09:00:00Z").toEpochMilli(),
            recurrence = Recurrence(RecurrenceFreq.DAILY, count = 1),
            zoneId = "UTC"
        )
        val afterMillis = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()
        val expected = RecurrenceEngine.nextOccurrence(record, afterMillis, ZoneId.of("UTC"))

        val actual = api.queryNextOccurrence(
            RecurrenceNextOccurrenceQueryV1(record.toEventRecordV1(), afterMillis, "UTC")
        )

        assertNull(expected)
        assertEquals(17L, actual.eventSeq)
        assertEquals(4L, actual.eventRevision)
        assertNull(actual.occurrence)
    }

    @Test
    fun occurrenceByKeyPreservesOriginalIdentityAndMovedOverride() {
        val zone = ZoneId.of("UTC")
        val anchor = Instant.parse("2026-10-20T09:00:00Z").toEpochMilli()
        val movedStart = Instant.parse("2026-10-25T11:00:00Z").toEpochMilli()
        val key = "2026-10-22T09:00"
        val record = TemporalRecord(
            eventSeq = 31,
            revision = 12,
            kind = TemporalKind.ACTIVITY,
            title = "Moved recurrence",
            startMillis = anchor,
            endMillis = anchor + 60 * 60 * 1000,
            recurrence = Recurrence(
                RecurrenceFreq.DAILY,
                count = 4,
                overrides = mapOf(
                    key to OccurrenceOverride(movedStart, movedStart + 60 * 60 * 1000)
                )
            ),
            zoneId = zone.id
        )
        val expected = requireNotNull(RecurrenceEngine.occurrenceForKey(record, key, zone))

        val actual = api.queryOccurrenceByKey(
            RecurrenceOccurrenceByKeyQueryV1(record.toEventRecordV1(), key, zone.id)
        )

        assertEquals(record.eventSeq, actual.eventSeq)
        assertEquals(record.revision, actual.eventRevision)
        assertEquals(key, actual.occurrence?.key)
        assertEquals(expected.key, actual.occurrence?.key)
        assertEquals(expected.startMillis, actual.occurrence?.startMillis)
        assertEquals(expected.endMillis, actual.occurrence?.endMillis)
        assertTrue(actual.occurrence?.moved == true)

        val excepted = record.copy(
            recurrence = record.recurrence.copy(exceptions = setOf(key))
        )
        assertNull(api.queryOccurrenceByKey(
            RecurrenceOccurrenceByKeyQueryV1(excepted.toEventRecordV1(), key, zone.id)
        ).occurrence)
        assertNull(api.queryOccurrenceByKey(
            RecurrenceOccurrenceByKeyQueryV1(record.toEventRecordV1(), "2026-10-29T09:00", zone.id)
        ).occurrence)
        assertNull(api.queryOccurrenceByKey(
            RecurrenceOccurrenceByKeyQueryV1(
                record.copy(recurrence = Recurrence.NONE).toEventRecordV1(), key, zone.id
            )
        ).occurrence)
    }

    @Test
    fun occurrenceByKeyUsesTheExplicitLocalZoneAcrossDstFallback() {
        val zone = ZoneId.of("America/New_York")
        val anchor = LocalDate.of(2026, 10, 31).atTime(LocalTime.of(9, 0))
            .atZone(zone).toInstant().toEpochMilli()
        val key = "2026-11-01T09:00"
        val record = TemporalRecord(
            eventSeq = 32,
            revision = 3,
            kind = TemporalKind.ALARM,
            title = "DST occurrence",
            startMillis = anchor,
            recurrence = Recurrence(RecurrenceFreq.DAILY),
            zoneId = zone.id
        )

        val actual = api.queryOccurrenceByKey(
            RecurrenceOccurrenceByKeyQueryV1(record.toEventRecordV1(), key, zone.id)
        )

        assertEquals(key, actual.occurrence?.key)
        assertEquals(
            Instant.parse("2026-11-01T14:00:00Z").toEpochMilli(),
            actual.occurrence?.startMillis
        )
    }

    @Test
    fun occurrenceByKeySelectsTheEarlierOffsetForAnAmbiguousFallFoldSlot() {
        val zone = ZoneId.of("America/New_York")
        val anchor = LocalDateTime.parse("2026-10-31T01:30")
            .atZone(zone).toInstant().toEpochMilli()
        val key = "2026-11-01T01:30"
        val record = TemporalRecord(
            eventSeq = 33,
            revision = 5,
            kind = TemporalKind.ALARM,
            title = "Fall fold",
            startMillis = anchor,
            recurrence = Recurrence(RecurrenceFreq.DAILY, count = 3),
            zoneId = zone.id
        )

        val actual = api.queryOccurrenceByKey(
            RecurrenceOccurrenceByKeyQueryV1(record.toEventRecordV1(), key, zone.id)
        )

        assertEquals(record.eventSeq, actual.eventSeq)
        assertEquals(record.revision, actual.eventRevision)
        assertEquals(key, actual.occurrence?.key)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),
            actual.occurrence?.startMillis)
        assertEquals(-4 * 60 * 60, Instant.ofEpochMilli(
            requireNotNull(actual.occurrence).startMillis
        ).atZone(zone).offset.totalSeconds)
    }

    @Test
    fun dateOnlyDeadlineOccurrencesKeepTheirLocalDatesAndNoInstantDeadline() {
        val zone = ZoneId.of("America/New_York")
        val record = TemporalRecord(
            eventSeq = 34,
            revision = 6,
            kind = TemporalKind.DEADLINE,
            title = "Date-only recurrence",
            deadlineDate = "2026-10-31",
            recurrence = Recurrence(RecurrenceFreq.DAILY, count = 3),
            zoneId = zone.id
        )
        val window = api.queryOccurrencesInWindow(
            RecurrenceOccurrenceWindowQueryV1(
                record.toEventRecordV1(),
                LocalDate.of(2026, 10, 31).atStartOfDay(zone).toInstant().toEpochMilli(),
                LocalDate.of(2026, 11, 3).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        )

        assertEquals(record.eventSeq, window.eventSeq)
        assertEquals(record.revision, window.eventRevision)
        assertEquals(listOf("2026-10-31T00:00", "2026-11-01T00:00", "2026-11-02T00:00"),
            window.occurrences.map { it.key })
        assertEquals(listOf(31, 1, 2), window.occurrences.map {
            Instant.ofEpochMilli(it.startMillis).atZone(zone).dayOfMonth
        })
        assertEquals("2026-10-31", record.toEventRecordV1().deadlineDate)
        assertEquals(null, record.toEventRecordV1().deadlineMillis)
    }

    @Test
    fun occurrenceWindowPreservesMovedOriginalSlotAndReportsTheBound() {
        val zone = ZoneId.of("UTC")
        val anchor = Instant.parse("2026-10-01T09:00:00Z").toEpochMilli()
        val movedStart = Instant.parse("2026-10-23T14:00:00Z").toEpochMilli()
        val originalKey = "2026-10-21T09:00"
        val record = TemporalRecord(
            eventSeq = 41,
            revision = 8,
            kind = TemporalKind.ACTIVITY,
            title = "Long recurring activity",
            startMillis = anchor,
            endMillis = anchor + 4L * 365 * 24 * 60 * 60 * 1000,
            recurrence = Recurrence(
                RecurrenceFreq.DAILY,
                count = 1000,
                overrides = mapOf(
                    originalKey to OccurrenceOverride(movedStart, movedStart + 60 * 60 * 1000)
                )
            ),
            zoneId = zone.id
        )
        val from = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val to = Instant.parse("2026-10-26T00:00:00Z").toEpochMilli()

        val actual = api.queryOccurrencesInWindow(
            RecurrenceOccurrenceWindowQueryV1(
                event = record.toEventRecordV1(),
                fromMillis = from,
                toMillis = to,
                maxOccurrences = 2
            )
        )

        assertEquals(record.eventSeq, actual.eventSeq)
        assertEquals(record.revision, actual.eventRevision)
        assertTrue(actual.truncated)
        assertEquals(2, actual.occurrences.size)
        val moved = actual.occurrences.single { it.key == originalKey }
        assertEquals(movedStart, moved.startMillis)
        assertTrue(moved.moved)
    }

    @Test
    fun occurrenceWindowKeepsWallClockTimeAcrossDstFallback() {
        val zone = ZoneId.of("America/New_York")
        val anchor = LocalDate.of(2026, 10, 31).atTime(9, 0).atZone(zone)
            .toInstant().toEpochMilli()
        val record = TemporalRecord(
            eventSeq = 42,
            revision = 2,
            kind = TemporalKind.ALARM,
            title = "Daily alarm",
            startMillis = anchor,
            recurrence = Recurrence(RecurrenceFreq.DAILY),
            zoneId = zone.id
        )

        val actual = api.queryOccurrencesInWindow(
            RecurrenceOccurrenceWindowQueryV1(
                event = record.toEventRecordV1(),
                fromMillis = Instant.parse("2026-10-31T00:00:00Z").toEpochMilli(),
                toMillis = Instant.parse("2026-11-03T00:00:00Z").toEpochMilli()
            )
        )

        assertFalse(actual.truncated)
        assertEquals(3, actual.occurrences.size)
        val fallbackDay = actual.occurrences.single { it.key == "2026-11-01T09:00" }
        assertEquals(9, Instant.ofEpochMilli(fallbackDay.startMillis).atZone(zone).hour)
        assertEquals(
            Instant.parse("2026-11-01T14:00:00Z").toEpochMilli(),
            fallbackDay.startMillis
        )
    }

    @Test
    fun occurrenceWindowRejectsEmptyOversizedAndOverCapRequests() {
        val record = TemporalRecord(
            eventSeq = 43,
            revision = 1,
            kind = TemporalKind.ALARM,
            title = "Bound validation",
            startMillis = Instant.parse("2026-10-20T09:00:00Z").toEpochMilli()
        ).toEventRecordV1()
        val from = Instant.parse("2026-10-20T00:00:00Z").toEpochMilli()

        assertTrue(runCatching {
            RecurrenceOccurrenceWindowQueryV1(record, from, from)
        }.isFailure)
        assertTrue(runCatching {
            RecurrenceOccurrenceWindowQueryV1(
                record, from, from + RecurrenceOccurrenceWindowQueryV1.MAX_WINDOW_MILLIS + 1
            )
        }.isFailure)
        assertTrue(runCatching {
            RecurrenceOccurrenceWindowQueryV1(
                record, from, from + 1000, maxOccurrences =
                    RecurrenceOccurrenceWindowQueryV1.MAX_OCCURRENCES + 1
            )
        }.isFailure)
    }
}
