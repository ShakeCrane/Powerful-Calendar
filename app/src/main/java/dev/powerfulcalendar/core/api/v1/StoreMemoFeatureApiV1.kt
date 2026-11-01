package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalRecord
import java.util.UUID

/** Memo-specific query adapter. It reuses the existing event snapshot and recurrence engine. */
internal class StoreMemoFeatureApiV1(
    private val store: PowerStore,
    private val currentFeatureRevision: () -> Long
) : MemoFeatureApiV1 {
    override val apiVersion: Int = 1
    private val pageServiceInstanceId = UUID.randomUUID().toString()

    override fun queryEntries(
        source: EventQuerySnapshotV1,
        nowMillis: Long,
        includeCompleted: Boolean
    ): MemoQuerySnapshotV1 = MemoQuerySnapshotV1(
        featureRevision = source.featureRevision,
        nowMillis = nowMillis,
        includesCompleted = includeCompleted,
        entries = project(source.events.map(EventRecordV1::toCoreTemporalRecord), nowMillis, includeCompleted)
    )

    override fun queryEntriesPage(
        nowMillis: Long,
        includeCompleted: Boolean,
        cursor: MemoQueryCursorV1?,
        limit: Int
    ): MemoQueryPageV1 {
        require(limit in 1..MemoFeatureApiV1.MAX_PAGE_SIZE) {
            "Memo 查询页大小必须介于 1 和 ${MemoFeatureApiV1.MAX_PAGE_SIZE}"
        }
        val revision = currentFeatureRevision()
        if (cursor != null && (
                cursor.featureRevision != revision ||
                    cursor.nowMillis != nowMillis ||
                    cursor.includesCompleted != includeCompleted ||
                    cursor.eventSeq <= 0L || cursor.occurrenceKey.isBlank() ||
                    cursor.undated != (cursor.startMillis == null) ||
                    cursor.serviceInstanceId != pageServiceInstanceId
                )
        ) return stalePage(revision, nowMillis, includeCompleted)

        val orderedPage = java.util.PriorityQueue(
            limit + 1,
            ENTRY_ORDER.reversed()
        )
        var totalEntries = 0
        var openEntries = 0
        store.forEachActiveEventPage { records ->
            records.forEach { record ->
                entriesFor(record, nowMillis, includeCompleted).forEach { entry ->
                    totalEntries = incrementBounded(totalEntries)
                    if (!entry.completed) openEntries = incrementBounded(openEntries)
                    if (cursor == null || compareEntryToCursor(entry, cursor) > 0) {
                        if (orderedPage.size < limit + 1) orderedPage.add(entry)
                        else if (ENTRY_ORDER.compare(entry, orderedPage.peek()) < 0) {
                            orderedPage.poll()
                            orderedPage.add(entry)
                        }
                    }
                }
            }
            true
        }
        val finalRevision = currentFeatureRevision()
        if (finalRevision != revision) return stalePage(finalRevision, nowMillis, includeCompleted)

        val ordered = orderedPage.toList().sortedWith(ENTRY_ORDER)
        val entries = ordered.take(limit)
        val nextCursor = if (ordered.size > limit) entries.lastOrNull()?.let { entry ->
            MemoQueryCursorV1(
                featureRevision = revision,
                nowMillis = nowMillis,
                includesCompleted = includeCompleted,
                undated = entry.startMillis == null,
                startMillis = entry.startMillis,
                eventSeq = entry.eventSeq,
                occurrenceKey = entry.occurrenceKey,
                serviceInstanceId = pageServiceInstanceId
            )
        } else null
        return MemoQueryPageV1(
            featureRevision = revision,
            nowMillis = nowMillis,
            includesCompleted = includeCompleted,
            entries = entries,
            totalEntries = totalEntries,
            openEntries = openEntries,
            nextCursor = nextCursor
        )
    }

    override fun queryCurrentEntry(
        eventSeq: Long,
        occurrenceKey: String,
        nowMillis: Long,
        includeCompleted: Boolean
    ): MemoEntryV1? {
        if (eventSeq <= 0L || occurrenceKey.isBlank()) return null
        val current = store.get(eventSeq) ?: return null
        return project(listOf(current), nowMillis, includeCompleted)
            .firstOrNull { it.occurrenceKey == occurrenceKey }
    }

    private fun project(
        records: List<TemporalRecord>,
        nowMillis: Long,
        includeCompleted: Boolean
    ): List<MemoEntryV1> {
        val earliest = nowMillis - LOOKBACK_DAYS * DAY_MILLIS
        val latest = nowMillis + AHEAD_DAYS * DAY_MILLIS
        return records.asSequence()
            .filter { it.trackingEnabled && it.enabled }
            .flatMap { record ->
                if (!record.recurrence.isRepeating) {
                    val completed = "single" in record.completedKeys
                    if (completed && !includeCompleted) emptySequence()
                    else sequenceOf(entry(record, "single", record.anchorMillis(), completed))
                } else {
                    val anchor = record.anchorMillis() ?: return@flatMap emptySequence()
                    RecurrenceEngine.occurrences(record, maxOf(earliest, anchor), latest, limit = 1200)
                        .asSequence()
                        .filterNot { it.skipped || (!includeCompleted && it.key in record.completedKeys) }
                        .map { occurrence ->
                            entry(
                                record,
                                occurrence.key,
                                occurrence.startMillis,
                                occurrence.key in record.completedKeys
                            )
                        }
                }
            }
            .sortedWith(compareBy<MemoEntryV1>(
                { it.startMillis == null },
                { it.startMillis ?: Long.MAX_VALUE },
                { it.eventSeq },
                { it.occurrenceKey }
            ))
            .toList()
    }

    private fun entriesFor(
        record: TemporalRecord,
        nowMillis: Long,
        includeCompleted: Boolean
    ): List<MemoEntryV1> {
        if (!record.trackingEnabled || !record.enabled) return emptyList()
        if (!record.recurrence.isRepeating) {
            val completed = "single" in record.completedKeys
            if (completed && !includeCompleted) return emptyList()
            return listOf(entry(record, "single", record.anchorMillis(), completed))
        }
        val anchor = record.anchorMillis() ?: return emptyList()
        val earliest = nowMillis - LOOKBACK_DAYS * DAY_MILLIS
        val latest = nowMillis + AHEAD_DAYS * DAY_MILLIS
        return RecurrenceEngine.occurrences(record, maxOf(earliest, anchor), latest, limit = 1200)
            .asSequence()
            .filterNot { it.skipped || (!includeCompleted && it.key in record.completedKeys) }
            .map { occurrence ->
                entry(
                    record,
                    occurrence.key,
                    occurrence.startMillis,
                    occurrence.key in record.completedKeys
                )
            }
            .toList()
    }

    private fun compareEntryToCursor(entry: MemoEntryV1, cursor: MemoQueryCursorV1): Int {
        val undatedOrder = (entry.startMillis == null).compareTo(cursor.undated)
        if (undatedOrder != 0) return undatedOrder
        val startOrder = compareValues(entry.startMillis ?: Long.MAX_VALUE,
            cursor.startMillis ?: Long.MAX_VALUE)
        if (startOrder != 0) return startOrder
        val sequenceOrder = compareValues(entry.eventSeq, cursor.eventSeq)
        if (sequenceOrder != 0) return sequenceOrder
        return entry.occurrenceKey.compareTo(cursor.occurrenceKey)
    }

    private fun stalePage(
        revision: Long,
        nowMillis: Long,
        includeCompleted: Boolean
    ) = MemoQueryPageV1(
        featureRevision = revision,
        nowMillis = nowMillis,
        includesCompleted = includeCompleted,
        entries = emptyList(),
        totalEntries = 0,
        openEntries = 0,
        stale = true
    )

    private fun entry(
        record: TemporalRecord,
        occurrenceKey: String,
        startMillis: Long?,
        completed: Boolean
    ) = MemoEntryV1(
        eventSeq = record.eventSeq,
        revision = record.revision,
        kind = EventKindV1.valueOf(record.kind.name),
        occurrenceKey = occurrenceKey,
        startMillis = startMillis,
        title = record.title,
        completed = completed,
        zoneId = record.zone.id,
        repeating = record.recurrence.isRepeating
    )

    private companion object {
        val ENTRY_ORDER = compareBy<MemoEntryV1>(
            { it.startMillis == null },
            { it.startMillis ?: Long.MAX_VALUE },
            { it.eventSeq },
            { it.occurrenceKey }
        )
        const val LOOKBACK_DAYS = 365L
        const val AHEAD_DAYS = 7L
        const val DAY_MILLIS = 86_400_000L

        fun incrementBounded(value: Int) = if (value == Int.MAX_VALUE) value else value + 1
    }
}
