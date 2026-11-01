package dev.powerfulcalendar.core.api.v1

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
    val events: List<EventRecordV1>,
    val stale: Boolean = false
)

/** Bounded read-only search over a date window, title, or both. Dates are inclusive. */
data class CalendarEventQueryV1(
    val zoneId: String,
    val fromDate: String? = null,
    val throughDate: String? = null,
    val titleQuery: String? = null,
    val kind: EventKindV1? = null,
    val maxResults: Int = DEFAULT_MAX_RESULTS
) {
    companion object {
        const val MAX_TITLE_CHARS = 120
        const val DEFAULT_MAX_RESULTS = 20
        const val MAX_RESULTS = 100
        /** Inclusive ranges may span up to this many day boundaries (366 calendar dates). */
        const val MAX_DATE_RANGE_DAY_DIFFERENCE = 365L
    }

    init {
        val zone = ZoneId.of(zoneId)
        require((fromDate == null) == (throughDate == null)) {
            "fromDate and throughDate must be supplied together"
        }
        require(fromDate != null || !titleQuery.isNullOrBlank()) {
            "A date range or non-empty title query is required"
        }
        require(titleQuery == null ||
            (titleQuery.length <= MAX_TITLE_CHARS &&
                titleQuery.none(Char::isISOControl) && titleQuery.isNotBlank())
        ) { "titleQuery is invalid or too long" }
        require(maxResults in 1..MAX_RESULTS) {
            "maxResults must be between 1 and $MAX_RESULTS"
        }
        if (fromDate != null && throughDate != null) {
            val first = LocalDate.parse(fromDate)
            val last = LocalDate.parse(throughDate)
            require(!last.isBefore(first)) { "throughDate must not be before fromDate" }
            require(ChronoUnit.DAYS.between(first, last) in 0L..MAX_DATE_RANGE_DAY_DIFFERENCE) {
                "Calendar query date range exceeds $MAX_DATE_RANGE_DAY_DIFFERENCE day boundaries"
            }
            require(runCatching {
                first.atStartOfDay(zone).toInstant().toEpochMilli()
                last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }.isSuccess) { "Calendar query date range is outside the supported epoch" }
        }
    }
}

enum class CalendarEventPrecisionV1 {
    INSTANT, INTERVAL, DATE_ONLY, DATE_RANGE, UNDATED, INCOMPLETE
}

/** Minimal formal-event result for read-only queries; notes, location, and reminder data are omitted. */
data class CalendarEventQueryHitV1(
    val eventSeq: Long,
    val revision: Long,
    val kind: EventKindV1,
    val title: String,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val deadlineMillis: Long? = null,
    val deadlineDate: String? = null,
    val memoStartDate: String? = null,
    val memoEndDate: String? = null,
    val precision: CalendarEventPrecisionV1,
    val zoneId: String,
    val recurrence: EventRecurrenceV1,
    val source: String
)

data class CalendarEventQueryResultV1(
    val featureRevision: Long,
    val query: CalendarEventQueryV1,
    val results: List<CalendarEventQueryHitV1>,
    val totalMatches: Int,
    val truncated: Boolean,
    val stale: Boolean = false
)

/** Versioned calendar queries for timeline and month views. */
interface CalendarFeatureApiV1 {
    val apiVersion: Int

    /**
     * Queries the authoritative Store in bounded pages and returns every matching owner once.
     * Results retain the Store's axis-anchor ordering and the captured Event feature revision.
     */
    fun queryWindow(query: CalendarWindowQueryV1): CalendarWindowSnapshotV1

    /**
     * Returns every owner with at least one effective occurrence in [query]. The source is an
     * immutable snapshot from this Core API's `events` service; its revision is preserved.
     */
    fun queryWindow(
        query: CalendarWindowQueryV1,
        source: EventQuerySnapshotV1
    ): CalendarWindowSnapshotV1

    /** Searches an immutable Event snapshot; results contain minimal, versioned display values. */
    fun queryEvents(
        query: CalendarEventQueryV1,
        source: EventQuerySnapshotV1
    ): CalendarEventQueryResultV1

    /** Store-backed equivalent of [queryEvents], with bounded Store reads and capped results. */
    fun queryEvents(query: CalendarEventQueryV1): CalendarEventQueryResultV1
}
