package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalRecord
import java.util.PriorityQueue

/** Store-backed Calendar adapter. It keeps only one bounded Store page plus matching output. */
internal class StoreCalendarFeatureApiV1(
    private val store: PowerStore,
    private val currentFeatureRevision: () -> Long
) : CalendarFeatureApiV1 {
    override val apiVersion: Int = 1
    private val snapshotQueries = SnapshotCalendarFeatureApiV1()
    private val windowCache = object : LinkedHashMap<WindowCacheKey, CalendarWindowSnapshotV1>(
        MAX_CACHED_WINDOWS, 0.75f, true
    ) {}

    override fun queryWindow(
        query: CalendarWindowQueryV1,
        source: EventQuerySnapshotV1
    ): CalendarWindowSnapshotV1 = snapshotQueries.queryWindow(query, source)

    @Synchronized
    override fun queryWindow(query: CalendarWindowQueryV1): CalendarWindowSnapshotV1 {
        repeat(MAX_STALE_RETRIES + 1) {
            val revision = currentFeatureRevision()
            windowCache[WindowCacheKey(revision, query)]?.let { return it }
            val matches = ArrayList<EventRecordV1>()
            store.forEachActiveEventPage { records ->
                val source = EventQuerySnapshotV1(
                    featureRevision = revision,
                    events = records.map(TemporalRecord::toEventRecordV1)
                )
                matches.addAll(snapshotQueries.queryWindow(query, source).events)
                true
            }
            val finalRevision = currentFeatureRevision()
            if (revision == finalRevision) {
                val result = CalendarWindowSnapshotV1(
                    featureRevision = revision,
                    query = query,
                    events = matches.sortedWith(EVENT_AXIS_ORDER)
                )
                windowCache[WindowCacheKey(revision, query)] = result
                while (windowCache.size > MAX_CACHED_WINDOWS) {
                    windowCache.remove(windowCache.keys.first())
                }
                return result
            }
        }
        return CalendarWindowSnapshotV1(
            featureRevision = currentFeatureRevision(),
            query = query,
            events = emptyList(),
            stale = true
        )
    }

    override fun queryEvents(
        query: CalendarEventQueryV1,
        source: EventQuerySnapshotV1
    ): CalendarEventQueryResultV1 = snapshotQueries.queryEvents(query, source)

    override fun queryEvents(query: CalendarEventQueryV1): CalendarEventQueryResultV1 {
        repeat(MAX_STALE_RETRIES + 1) {
            val revision = currentFeatureRevision()
            val resultOrder = compareBy<CalendarEventQueryHitV1>(CalendarEventQueryHitV1::eventSeq)
            val firstMatches = PriorityQueue(query.maxResults, resultOrder.reversed())
            var total = 0
            store.forEachActiveEventPage { records ->
                val source = EventQuerySnapshotV1(
                    featureRevision = revision,
                    events = records.map(TemporalRecord::toEventRecordV1)
                )
                val page = snapshotQueries.queryEvents(query, source)
                total += page.totalMatches
                page.results.forEach { hit ->
                    if (firstMatches.size < query.maxResults) firstMatches.add(hit)
                    else if (resultOrder.compare(hit, firstMatches.peek()) < 0) {
                        firstMatches.poll()
                        firstMatches.add(hit)
                    }
                }
                true
            }
            val finalRevision = currentFeatureRevision()
            if (revision == finalRevision) {
                val results = firstMatches.toList().sortedWith(resultOrder)
                return CalendarEventQueryResultV1(
                    featureRevision = revision,
                    query = query.copy(),
                    results = results,
                    totalMatches = total,
                    truncated = total > results.size
                )
            }
        }
        return CalendarEventQueryResultV1(
            featureRevision = currentFeatureRevision(),
            query = query.copy(),
            results = emptyList(),
            totalMatches = 0,
            truncated = false,
            stale = true
        )
    }

    private companion object {
        const val MAX_STALE_RETRIES = 1
        const val MAX_CACHED_WINDOWS = 12
        data class WindowCacheKey(
            val featureRevision: Long,
            val query: CalendarWindowQueryV1
        )
        val EVENT_AXIS_ORDER = compareBy<EventRecordV1>(
            { it.toCoreTemporalRecord().anchorMillis() == null },
            { it.toCoreTemporalRecord().anchorMillis() ?: Long.MAX_VALUE },
            EventRecordV1::eventSeq
        )
    }
}
