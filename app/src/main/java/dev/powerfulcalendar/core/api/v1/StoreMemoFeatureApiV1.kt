package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalRecord

/** Memo-specific query adapter. It reuses the existing event snapshot and recurrence engine. */
internal class StoreMemoFeatureApiV1(
    private val store: PowerStore
) : MemoFeatureApiV1 {
    override val apiVersion: Int = 1

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
        const val LOOKBACK_DAYS = 365L
        const val AHEAD_DAYS = 7L
        const val DAY_MILLIS = 86_400_000L
    }
}
