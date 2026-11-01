package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
}
