package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
