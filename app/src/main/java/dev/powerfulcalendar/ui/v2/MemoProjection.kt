package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalRecord

/** Lightweight read projection. Nothing here creates extra linked event objects. */
internal data class TodoEntry(
    val owner: TemporalRecord,
    val occurrenceKey: String,
    val startMillis: Long?,
    val title: String,
    val completed: Boolean = false
) {
    val id: String get() = owner.eventSeq.toString() + ":" + occurrenceKey
}

/**
 * Repeat projection is deliberately windowed. Events before the lookback remain in PowerStore;
 * the UI discloses this bound instead of silently claiming this is all historical debt.
 * Unbounded future instances are never materialized.
 */
internal object TodoProjection {
    const val LOOKBACK_DAYS = 365L
    const val AHEAD_DAYS = 7L
    fun entries(records: List<TemporalRecord>, nowMillis: Long,
                includeCompleted: Boolean = false): List<TodoEntry> {
        val earliest = nowMillis - LOOKBACK_DAYS * 86_400_000L
        val latest = nowMillis + AHEAD_DAYS * 86_400_000L
        return records.filter { it.trackingEnabled && it.enabled }.flatMap { record ->
            if (!record.recurrence.isRepeating) {
                val done = "single" in record.completedKeys
                if (done && !includeCompleted) emptyList()
                else listOf(TodoEntry(record, "single", record.anchorMillis(), record.title, done))
            } else {
                val anchor = record.anchorMillis() ?: return@flatMap emptyList()
                RecurrenceEngine.occurrences(record, maxOf(earliest, anchor), latest, limit = 1200)
                    .filterNot { it.skipped || (!includeCompleted && it.key in record.completedKeys) }
                    .map { TodoEntry(record, it.key, it.startMillis, record.title,
                        it.key in record.completedKeys) }
            }
        }.sortedWith(compareBy<TodoEntry>({ it.startMillis == null }, { it.startMillis ?: Long.MAX_VALUE },
            { it.owner.eventSeq }, { it.occurrenceKey }))
    }
}
