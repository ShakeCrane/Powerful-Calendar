package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.RecurrenceEngine
import java.time.ZoneId

/** Core adapter for recurrence calculations over the immutable v1 event contract. */
internal class CoreRecurrenceFeatureApiV1 : RecurrenceFeatureApiV1 {
    override val apiVersion: Int = 1

    override fun queryNextOccurrence(
        query: RecurrenceNextOccurrenceQueryV1
    ): RecurrenceNextOccurrenceV1 {
        val occurrence = RecurrenceEngine.nextOccurrence(
            query.event.toCoreTemporalRecord(),
            query.afterMillis,
            ZoneId.of(query.localZoneId)
        )
        return RecurrenceNextOccurrenceV1(
            eventSeq = query.event.eventSeq,
            eventRevision = query.event.revision,
            occurrence = occurrence?.let {
                RecurrenceOccurrenceV1(
                    key = it.key,
                    startMillis = it.startMillis,
                    endMillis = it.endMillis,
                    skipped = it.skipped,
                    moved = it.moved
                )
            }
        )
    }

    override fun queryOccurrenceByKey(
        query: RecurrenceOccurrenceByKeyQueryV1
    ): RecurrenceOccurrenceByKeyV1 {
        val event = query.event.toCoreTemporalRecord()
        val occurrence = RecurrenceEngine.occurrenceForKey(
            event,
            query.occurrenceKey,
            ZoneId.of(query.localZoneId)
        )
        return RecurrenceOccurrenceByKeyV1(
            eventSeq = query.event.eventSeq,
            eventRevision = query.event.revision,
            occurrence = occurrence?.let {
                RecurrenceOccurrenceV1(
                    key = it.key,
                    startMillis = it.startMillis,
                    endMillis = it.endMillis,
                    skipped = it.skipped,
                    moved = it.moved
                )
            }
        )
    }
}
