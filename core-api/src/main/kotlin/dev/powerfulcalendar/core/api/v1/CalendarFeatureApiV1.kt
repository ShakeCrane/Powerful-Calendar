package dev.powerfulcalendar.core.api.v1

import java.time.ZoneId

/** Half-open calendar window. Date-only records are interpreted in [zoneId]'s local dates. */
data class CalendarWindowQueryV1(
    val fromMillis: Long,
    val toMillis: Long,
    val zoneId: String
) {
    init {
        require(fromMillis < toMillis) { "Calendar window must be non-empty" }
        require(zoneId.isNotBlank()) { "zoneId must not be blank" }
        ZoneId.of(zoneId)
    }
}

/**
 * A bounded time-window query over an immutable Event API snapshot.
 * Each matching formal owner is returned once, even if its recurrence has several visible slots.
 */
data class CalendarWindowSnapshotV1(
    val featureRevision: Long,
    val query: CalendarWindowQueryV1,
    val events: List<EventRecordV1>
)

/** Versioned calendar queries for timeline and month views. */
interface CalendarFeatureApiV1 {
    val apiVersion: Int

    /**
     * Returns every owner with at least one effective occurrence in [query]. The source is an
     * immutable snapshot from this Core API's `events` service; its revision is preserved.
     */
    fun queryWindow(
        query: CalendarWindowQueryV1,
        source: EventQuerySnapshotV1
    ): CalendarWindowSnapshotV1
}
