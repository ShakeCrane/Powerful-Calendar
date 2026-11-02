package dev.powerfulcalendar.core

/**
 * Search the visible pending-card snapshot without changing its queue order.
 * The input comes from [PowerStore.pendingDrafts], whose order is oldest first.
 */
object DraftQueueSearch {
    fun matches(title: String, eventSeq: Long, query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            eventSeq.toString().contains(query) ||
            TemporalRecord.formatSeq(eventSeq).contains(query)

    fun filter(drafts: List<DraftCard>, query: String): List<DraftCard> =
        drafts.filter { matches(it.form.title, it.eventSeq, query) }
}
