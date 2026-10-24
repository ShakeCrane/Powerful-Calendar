package dev.powerfulcalendar.core

/** Search formal event snapshots while keeping their existing display order. */
object FormalEventSearch {
    fun filter(records: List<TemporalRecord>, query: String): List<TemporalRecord> {
        if (query.isBlank()) return emptyList()
        return records.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.eventSeq.toString().contains(query) ||
                TemporalRecord.formatSeq(it.eventSeq).contains(query)
        }.take(8)
    }
}
