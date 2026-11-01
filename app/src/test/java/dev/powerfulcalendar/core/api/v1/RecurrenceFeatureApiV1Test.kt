package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
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
}
