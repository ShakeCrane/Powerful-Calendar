package dev.powerfulcalendar.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftQueueSearchTest {
    private fun card(
        draftId: Long,
        eventSeq: Long,
        queueOrder: Long,
        title: String
    ) = DraftCard(
        draftId = draftId,
        eventSeq = eventSeq,
        queueOrder = queueOrder,
        operation = DraftOperation.CREATE,
        form = DraftForm(TemporalKind.ACTIVITY, title),
        status = DraftStatus.PENDING,
        baseRevision = null,
        draftVersion = 1
    )

    @Test
    fun titleSearchKeepsTheQueueSnapshotOrder() {
        val queue = listOf(
            card(draftId = 21, eventSeq = 900, queueOrder = 1, title = "Alpha first"),
            card(draftId = 22, eventSeq = 100, queueOrder = 2, title = "Between"),
            card(draftId = 23, eventSeq = 700, queueOrder = 3, title = "ALPHA last")
        )
        val originalOrder = queue.map { it.draftId to it.queueOrder }

        val hits = DraftQueueSearch.filter(queue, "alpha")

        assertEquals(listOf(21L, 23L), hits.map { it.draftId })
        assertEquals(listOf(1L, 3L), hits.map { it.queueOrder })
        assertEquals(originalOrder, queue.map { it.draftId to it.queueOrder })
    }

    @Test
    fun eventSequenceSearchKeepsRelativeQueueOrder() {
        val queue = listOf(
            card(draftId = 31, eventSeq = 900, queueOrder = 4, title = "One"),
            card(draftId = 32, eventSeq = 42, queueOrder = 5, title = "Two"),
            card(draftId = 33, eventSeq = 142, queueOrder = 6, title = "Three")
        )

        val hits = DraftQueueSearch.filter(queue, "42")

        assertEquals(listOf(32L, 33L), hits.map { it.draftId })
        assertEquals(listOf(5L, 6L), hits.map { it.queueOrder })
    }
}
