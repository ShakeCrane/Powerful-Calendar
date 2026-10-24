package dev.powerfulcalendar.core

/**
 * Search the visible pending-card snapshot without changing its queue order.
 * The input comes from [PowerStore.pendingDrafts], whose order is oldest first.
 */
object DraftQueueSearch {
    fun filter(drafts: List<DraftCard>, query: String): List<DraftCard> = drafts.filter {
        it.form.title.contains(query, ignoreCase = true) ||
            it.eventSeq.toString().contains(query) ||
            TemporalRecord.formatSeq(it.eventSeq).contains(query)
    }
}
