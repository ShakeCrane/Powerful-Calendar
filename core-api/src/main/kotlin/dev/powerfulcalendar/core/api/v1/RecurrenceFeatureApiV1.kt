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

/** Core-owned lookup of one original recurrence slot from a detached event snapshot. */
data class RecurrenceOccurrenceByKeyQueryV1(
    val event: EventRecordV1,
    val occurrenceKey: String,
    val localZoneId: String
) {
    init {
        require(localZoneId.isNotBlank()) { "localZoneId must not be blank" }
        ZoneId.of(localZoneId)
    }
}

/** Echoes snapshot identity alongside the occurrence with the requested original key, if present. */
data class RecurrenceOccurrenceByKeyV1(
    val eventSeq: Long,
    val eventRevision: Long,
    val occurrence: RecurrenceOccurrenceV1?
)

/** Bounded half-open materialization of one event's effective slots for a render window. */
data class RecurrenceOccurrenceWindowQueryV1(
    val event: EventRecordV1,
    val fromMillis: Long,
    val toMillis: Long,
    val maxOccurrences: Int = MAX_OCCURRENCES
) {
    companion object {
        const val MAX_WINDOW_DAYS = 43L
        const val MAX_OCCURRENCES = 2048
        const val MAX_WINDOW_MILLIS = MAX_WINDOW_DAYS * 24L * 60L * 60L * 1000L
    }

    init {
        val windowMillis = runCatching { Math.subtractExact(toMillis, fromMillis) }.getOrNull()
        require(windowMillis != null && windowMillis in 1..MAX_WINDOW_MILLIS) {
            "Occurrence window must be non-empty and no longer than $MAX_WINDOW_DAYS days"
        }
        require(maxOccurrences in 1..MAX_OCCURRENCES) {
            "maxOccurrences must be between 1 and $MAX_OCCURRENCES"
        }
    }
}

/** Echoes snapshot identity and reports when more effective slots exist than the requested cap. */
data class RecurrenceOccurrenceWindowV1(
    val eventSeq: Long,
    val eventRevision: Long,
    val occurrences: List<RecurrenceOccurrenceV1>,
    val truncated: Boolean
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

    /**
     * Returns the materialized slot with this original key, or `null` when the key is invalid,
     * excepted, or outside the event's series. The result is calculated from the supplied
     * snapshot; it does not establish event currentness.
     */
    fun queryOccurrenceByKey(query: RecurrenceOccurrenceByKeyQueryV1): RecurrenceOccurrenceByKeyV1

    /**
     * Returns effective occurrences intersecting the half-open time window. A bounded renderer
     * uses window-start-prioritized selection, then the nearest carry-in intervals. The result
     * reflects only the detached snapshot and does not establish event currentness.
     */
    fun queryOccurrencesInWindow(
        query: RecurrenceOccurrenceWindowQueryV1
    ): RecurrenceOccurrenceWindowV1
}
