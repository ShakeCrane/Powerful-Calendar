package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Filters a versioned event snapshot using the existing Core recurrence engine. */
internal class SnapshotCalendarFeatureApiV1 : CalendarFeatureApiV1 {
    override val apiVersion: Int = 1

    override fun queryWindow(
        query: CalendarWindowQueryV1,
        source: EventQuerySnapshotV1
    ): CalendarWindowSnapshotV1 {
        val viewerZone = ZoneId.of(query.zoneId)
        val firstVisibleDate = Instant.ofEpochMilli(query.fromMillis).atZone(viewerZone).toLocalDate()
        val lastVisibleDate = Instant.ofEpochMilli(query.toMillis - 1L)
            .atZone(viewerZone).toLocalDate()
        val dateWindowEndExclusive = lastVisibleDate.plusDays(1)

        val matchingOwners = source.events.filter { ownerDto ->
            val owner = ownerDto.toCoreTemporalRecord()
            if (owner.kind == TemporalKind.MEMO && !owner.recurrence.isRepeating) {
                val memoStart = owner.memoStartDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@filter false
                val memoEnd = owner.memoEndDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: memoStart
                return@filter memoStart < dateWindowEndExclusive && memoEnd >= firstVisibleDate
            }

            val dateBased = owner.kind == TemporalKind.MEMO || owner.isDateOnlyDeadline()
            val (from, to) = if (dateBased) {
                firstVisibleDate.atStartOfDay(owner.zone).toInstant().toEpochMilli() to
                    dateWindowEndExclusive.atStartOfDay(owner.zone).toInstant().toEpochMilli()
            } else {
                query.fromMillis to query.toMillis
            }
            RecurrenceEngine.occurrences(owner, from, to, limit = 1).isNotEmpty()
        }

        return CalendarWindowSnapshotV1(
            featureRevision = source.featureRevision,
            query = query,
            events = matchingOwners
        )
    }

    override fun queryEvents(
        query: CalendarEventQueryV1,
        source: EventQuerySnapshotV1
    ): CalendarEventQueryResultV1 {
        val candidates = if (query.fromDate != null && query.throughDate != null) {
            val zone = ZoneId.of(query.zoneId)
            val first = LocalDate.parse(query.fromDate)
            val endExclusive = LocalDate.parse(query.throughDate).plusDays(1)
            queryWindow(
                CalendarWindowQueryV1(
                    fromMillis = first.atStartOfDay(zone).toInstant().toEpochMilli(),
                    toMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
                    zoneId = query.zoneId
                ),
                source
            ).events
        } else source.events

        val titleNeedle = query.titleQuery?.lowercase(Locale.ROOT)
        val matches = candidates.asSequence()
            .filter { query.kind == null || it.kind == query.kind }
            .filter { titleNeedle == null || it.title.lowercase(Locale.ROOT).contains(titleNeedle) }
            .sortedBy(EventRecordV1::eventSeq)
            .toList()
        val results = matches.take(query.maxResults).map { event ->
            CalendarEventQueryHitV1(
                eventSeq = event.eventSeq,
                revision = event.revision,
                kind = event.kind,
                title = event.title,
                startMillis = event.startMillis,
                endMillis = event.endMillis,
                deadlineMillis = event.deadlineMillis,
                deadlineDate = event.deadlineDate,
                memoStartDate = event.memoStartDate,
                memoEndDate = event.memoEndDate,
                precision = event.queryPrecision(),
                zoneId = event.zoneId,
                recurrence = event.recurrence.detachedForQuery(),
                source = event.source
            )
        }
        return CalendarEventQueryResultV1(
            featureRevision = source.featureRevision,
            query = query.copy(),
            results = results,
            totalMatches = matches.size,
            truncated = matches.size > results.size
        )
    }

    private fun EventRecordV1.queryPrecision(): CalendarEventPrecisionV1 = when (kind) {
        EventKindV1.ALARM -> if (startMillis != null) {
            CalendarEventPrecisionV1.INSTANT
        } else CalendarEventPrecisionV1.INCOMPLETE
        EventKindV1.ACTIVITY -> if (startMillis != null && endMillis != null) {
            CalendarEventPrecisionV1.INTERVAL
        } else CalendarEventPrecisionV1.INCOMPLETE
        EventKindV1.DEADLINE -> when {
            deadlineMillis != null -> CalendarEventPrecisionV1.INSTANT
            deadlineDate != null -> CalendarEventPrecisionV1.DATE_ONLY
            else -> CalendarEventPrecisionV1.INCOMPLETE
        }
        EventKindV1.MEMO -> if (memoStartDate == null && memoEndDate == null) {
            CalendarEventPrecisionV1.UNDATED
        } else CalendarEventPrecisionV1.DATE_RANGE
    }

    private fun EventRecurrenceV1.detachedForQuery() = copy(
        byDay = byDay.toSet(),
        exceptions = exceptions.toSet(),
        overrides = overrides.mapValues { (_, value) -> value.copy() }
    )
}
