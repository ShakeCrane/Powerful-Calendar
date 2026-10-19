package dev.powerfulcalendar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The interaction contract of 待处理, tested without UI or SQLite. These are the rules that decide
 * whether a fast swipe can lose text, whether a click can publish twice, and whether the completion
 * card is ever allowed to lie about an empty queue.
 */
class DeckControllerTest {

    private class Fake : DeckPersistence {
        val cards = LinkedHashMap<Long, DraftCard>()
        var nextDraftId = 1L
        var nextOrder = 1L
        var locked = false
        var failStage = false
        var conflictOnCommit = false
        var published = 0
        var discarded = 0
        var stageCalls = 0
        var commitCalls = 0

        fun add(title: String, seq: Long? = null, operation: DraftOperation = DraftOperation.CREATE): DraftCard {
            val card = DraftCard(
                draftId = nextDraftId++,
                eventSeq = seq ?: draftIdSafe(),
                queueOrder = nextOrder++,
                operation = operation,
                form = DraftForm(TemporalKind.ACTIVITY, title, "2026-10-20 09:00", "2026-10-20 10:00"),
                status = DraftStatus.PENDING,
                baseRevision = null,
                draftVersion = 0
            )
            cards[card.draftId] = card
            return card
        }

        private fun draftIdSafe(): Long = 100L + cards.size

        override fun pending(): List<DraftCard> = cards.values.sortedBy { it.queueOrder }
        override fun card(draftId: Long): DraftCard? = cards[draftId]

        override fun stage(draftId: Long, form: DraftForm, expectedVersion: Long): PowerStore.StageResult {
            stageCalls++
            if (locked) return PowerStore.StageResult.Locked
            if (failStage) return PowerStore.StageResult.Locked
            val card = cards[draftId] ?: return PowerStore.StageResult.Missing
            if (card.draftVersion != expectedVersion) {
                return PowerStore.StageResult.VersionMismatch(card)
            }
            val updated = card.copy(
                form = form,
                draftVersion = card.draftVersion + 1
            )
            cards[draftId] = updated
            return PowerStore.StageResult.Saved(updated.draftVersion)
        }

        override fun commit(draftId: Long, approveStale: Boolean): PowerStore.CommitOutcome {
            commitCalls++
            if (locked) return PowerStore.CommitOutcome.Locked
            if (conflictOnCommit && !approveStale) {
                return PowerStore.CommitOutcome.Conflict(null)
            }
            val card = cards.remove(draftId) ?: return PowerStore.CommitOutcome.Invalid("不存在")
            published++
            return PowerStore.CommitOutcome.Committed(card.eventSeq, 2, "commit-$draftId")
        }

        override fun discard(draftId: Long): Boolean {
            if (locked) return false
            cards.remove(draftId) ?: return false
            discarded++
            return true
        }

        override fun undo(draftId: Long): Boolean {
            val restored = DraftCard(
                draftId = draftId,
                eventSeq = 100L + draftId,
                queueOrder = draftId,
                operation = DraftOperation.CREATE,
                form = DraftForm(TemporalKind.ACTIVITY, "撤销"),
                status = DraftStatus.PENDING,
                baseRevision = null,
                draftVersion = 1
            )
            cards[draftId] = restored
            discarded = (discarded - 1).coerceAtLeast(0)
            return true
        }

        override fun pendingCount(): Int = cards.size
        override fun stats(sinceMillis: Long): PowerStore.DeckStats =
            PowerStore.DeckStats(published, discarded, 0)

        override fun isAiLocked(): Boolean = locked

        override fun restore(eventSeq: Long, form: DraftForm): PowerStore.CommitOutcome =
            PowerStore.CommitOutcome.Committed(eventSeq, 3, "restore")
    }

    @Test
    fun movingFlushesTheLatestTextFirst() {
        val fake = Fake()
        val first = fake.add("第一张")
        fake.add("第二张")
        val controller = DeckController(fake)
        controller.open()

        controller.edit(controller.state.form!!.copy(title = "第一张改过"))
        assertTrue(controller.move(1))

        assertEquals(1, fake.stageCalls)
        assertEquals("第一张改过", fake.cards[first.draftId]!!.form.title)
        assertEquals(2L, controller.state.currentId)
        assertFalse(controller.state.dirty)
    }

    @Test
    fun failedSaveBlocksTheTransitionAndKeepsText() {
        val fake = Fake()
        fake.add("第一张")
        fake.add("第二张")
        val controller = DeckController(fake)
        controller.open()

        controller.edit(controller.state.form!!.copy(title = "不能丢的字"))
        fake.failStage = true
        assertFalse(controller.move(1))

        assertEquals(1L, controller.state.currentId)
        assertEquals("不能丢的字", controller.state.form!!.title)
        assertTrue(controller.state.dirty)
        assertNotNull(controller.state.error)
    }

    @Test
    fun insideSwipeMovesExactlyOneCardAndClampsAtTheEdges() {
        val fake = Fake()
        repeat(3) { fake.add("卡 $it") }
        val controller = DeckController(fake)
        controller.open()

        assertEquals(1L, controller.state.currentId)
        assertTrue(controller.move(1))
        assertEquals(2L, controller.state.currentId)
        assertTrue(controller.move(1))
        assertEquals(3L, controller.state.currentId)
        assertTrue(controller.move(1))
        assertEquals(3L, controller.state.currentId)
        assertTrue(controller.move(-1))
        assertEquals(2L, controller.state.currentId)
    }

    @Test
    fun outsideFlingCanJumpSeveralCardsButStillFlushesOnce() {
        val fake = Fake()
        repeat(5) { fake.add("卡 $it") }
        val controller = DeckController(fake)
        controller.open()
        controller.edit(controller.state.form!!.copy(title = "已编辑"))

        assertTrue(controller.jump(3))
        assertEquals(4L, controller.state.currentId)
        assertEquals(1, fake.stageCalls)
    }

    @Test
    fun completionAppearsOnlyForAnEmptyUnlockedQueueWithRealCounts() {
        val fake = Fake()
        fake.add("唯一一张")
        val controller = DeckController(fake)
        controller.open()

        assertTrue(controller.commit())
        val completion = controller.state.completion
        assertNotNull(completion)
        assertEquals(1, completion!!.published)
        assertEquals(0, completion.discarded)
        assertTrue(controller.state.cards.isEmpty())
    }

    @Test
    fun completionIsSuppressedWhileAnAiBatchIsLocked() {
        val fake = Fake()
        fake.add("唯一一张")
        val controller = DeckController(fake)
        controller.open()
        fake.locked = true

        assertFalse(controller.commit())
        assertNull(controller.state.completion)
        assertTrue(controller.state.cards.isNotEmpty())
    }

    @Test
    fun undoingTheLastDiscardCancelsCompletion() {
        val fake = Fake()
        val only = fake.add("唯一一张")
        val controller = DeckController(fake)
        controller.open()

        assertTrue(controller.discard())
        assertNotNull(controller.state.completion)
        assertTrue(controller.state.cards.isEmpty())

        assertTrue(controller.undoDiscard())
        assertNull(controller.state.completion)
        assertEquals(1, controller.state.cards.size)
        assertEquals(only.queueOrder, controller.state.cards.single().queueOrder)
        assertEquals(1L, controller.state.currentId)
    }

    @Test
    fun aiAppendingCardsDoesNotStealTheCurrentCard() {
        val fake = Fake()
        fake.add("第一张")
        fake.add("第二张")
        val controller = DeckController(fake)
        controller.open()
        assertTrue(controller.move(1))
        val current = controller.state.currentId

        fake.add("AI 追加")
        controller.refresh()

        assertEquals(current, controller.state.currentId)
        assertEquals("第二张", controller.state.form!!.title)
        assertEquals(3, controller.state.cards.size)
    }

    @Test
    fun aConflictKeepsTheCardAndAsksForExplicitReplacement() {
        val fake = Fake()
        fake.add("旧卡")
        fake.conflictOnCommit = true
        val controller = DeckController(fake)
        controller.open()

        assertFalse(controller.commit())
        assertNotNull(controller.state.conflict)
        assertEquals(1, controller.state.cards.size)
        assertEquals(DraftStatus.PENDING, controller.state.cards.single().status)

        fake.conflictOnCommit = false
        assertTrue(controller.confirmConflictReplacement())
        assertNull(controller.state.conflict)
        assertTrue(controller.state.cards.isEmpty())
    }

    @Test
    fun promoteAfterCommitUsesQueueOrderNotTheOldIndex() {
        val fake = Fake()
        fake.add("A")
        fake.add("B")
        fake.add("C")
        val controller = DeckController(fake)
        controller.open()

        // Move to B, commit it: the next card should be C (next in queue order).
        assertTrue(controller.move(1))
        assertTrue(controller.commit())
        assertEquals("C", controller.state.form!!.title)

        // Commit C: only A remains even though it is "older" than the card just handled.
        assertTrue(controller.commit())
        assertEquals("A", controller.state.form!!.title)
        assertTrue(controller.move(1))
        assertTrue(controller.commit())
        assertNotNull(controller.state.completion)
        assertEquals(3, controller.state.completion!!.published)
    }

    @Test
    fun repeatedClicksCannotPublishTwice() {
        val fake = Fake()
        fake.add("唯一一张")
        val controller = DeckController(fake)
        controller.open()

        assertTrue(controller.commit())
        assertFalse(controller.commit())
        assertEquals(1, fake.published)
        assertEquals(1, fake.commitCalls)
    }

    @Test
    fun editingDuringAnAiLockIsRejected() {
        val fake = Fake()
        fake.add("锁定卡")
        val controller = DeckController(fake)
        controller.open()
        fake.locked = true
        controller.setAiLocked(true)

        controller.edit(controller.state.form!!.copy(title = "不该写进去"))
        assertEquals("锁定卡", controller.state.form!!.title)
        assertNotNull(controller.state.error)
    }
}
