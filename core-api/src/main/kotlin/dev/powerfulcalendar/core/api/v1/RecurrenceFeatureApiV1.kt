package dev.powerfulcalendar.core.api.v1

import java.time.ZoneId

/** Platform-neutral request to compute the next occurrence from a detached event snapshot. */
data class RecurrenceNextOccurrenceQueryV1(
    val event: EventRecordV1,
    val afterMillis: Long,
    val localZoneId: String
) {
    init {
        require(localZoneId.isNotBlank()) { "localZoneId must not be blank" }
        ZoneId.of(localZoneId)
    }
}

/** One Core-computed occurrence. `key` is the original recurrence slot identity. */
data class RecurrenceOccurrenceV1(
    val key: String,
    val startMillis: Long,
    val endMillis: Long?,
    val skipped: Boolean,
    val moved: Boolean
)

/** Echoes the snapshot identity even when the series has no occurrence at or after the query. */
data class RecurrenceNextOccurrenceV1(
    val eventSeq: Long,
    val eventRevision: Long,
    val occurrence: RecurrenceOccurrenceV1?
)

/** Versioned recurrence calculations over detached Core event values. */
interface RecurrenceFeatureApiV1 {
    val apiVersion: Int

    /**
     * Returns the next occurrence whose start is at or after [query.afterMillis], or a null
     * occurrence when the series has ended. The result is a projection of the supplied snapshot;
     * it does not authorize a write or guarantee that the event is still current.
     */
    fun queryNextOccurrence(query: RecurrenceNextOccurrenceQueryV1): RecurrenceNextOccurrenceV1
}
