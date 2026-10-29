package dev.powerfulcalendar.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    /** Controllable clock so the terminal-action rate limit is deterministic. */
    private var now = 1_000L

    /**
     * Unconfined dispatcher so durable commands run inline in the calling `runBlocking`; the tests
     * assert ordering and cancellation without depending on a thread pool.
     */
    private fun controller(fake: Fake): DeckController {
        now = 1_000L
        return DeckController(fake, { now }, Dispatchers.Unconfined)
    }

    private fun tick() {
        now += 1_000L
    }

    private class Fake : DeckPersistence {
        val cards = LinkedHashMap<Long, DraftCard>()
        var nextDraftId = 1L
        var nextOrder = 1L
        var locked = false
        var failStage = false
        var conflictOnCommit = false
        var needsRestoreOnCommit = false
        var currentRevisionForRecord: Long? = null
        var advanceRevisionOnCommit = false
        var published = 0
        var discarded = 0
        var stageCalls = 0
        var commitCalls = 0
        var expectedCurrentRevisionOnCommit: Long? = null

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

        override fun commit(
            draftId: Long,
            approveStale: Boolean,
            expectedCurrentRevision: Long?
        ): PowerStore.CommitOutcome {
            commitCalls++
            expectedCurrentRevisionOnCommit = expectedCurrentRevision
            if (locked) return PowerStore.CommitOutcome.Locked
            if (advanceRevisionOnCommit)
                currentRevisionForRecord = currentRevisionForRecord?.plus(1)
            if (expectedCurrentRevision != null &&
                currentRevisionForRecord != expectedCurrentRevision)
                return PowerStore.CommitOutcome.Conflict(record(1L))
            if (conflictOnCommit && !approveStale) {
                return PowerStore.CommitOutcome.Conflict(record(1L))
            }
            val card = cards[draftId] ?: return PowerStore.CommitOutcome.Invalid("不存在")
            if (needsRestoreOnCommit) return PowerStore.CommitOutcome.NeedsRestore(card.eventSeq)
            cards.remove(draftId)
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

        override fun restoreDraft(draftId: Long, form: DraftForm): PowerStore.CommitOutcome {
            val card = cards.remove(draftId) ?: return PowerStore.CommitOutcome.Invalid("不存在")
            published++
            return PowerStore.CommitOutcome.Committed(card.eventSeq, 3, "restore-$draftId")
        }

        override fun proposeRestore(eventSeq: Long): DraftCard =
            cards.values.firstOrNull { it.eventSeq == eventSeq }
                ?: add("恢复提案", eventSeq, DraftOperation.REPLACE)

        override fun record(eventSeq: Long): TemporalRecord? =
            currentRevisionForRecord?.let { revision ->
                TemporalRecord(
                    eventSeq = eventSeq,
                    revision = revision,
                    kind = TemporalKind.ACTIVITY,
                    title = "磁盘上的当前内容",
                    startMillis = 1_000L,
                    endMillis = 2_000L
                )
            }
    }

    @Test
    fun explicitRestoreEntryOpensProposalAndRequiresSeparateConfirmation() = runBlocking {
        val fake = Fake().apply { needsRestoreOnCommit = true }
        val controller = controller(fake)

        assertTrue(controller.beginRestore(42L))
        val card = controller.state.current!!
        assertEquals(42L, card.eventSeq)
        assertEquals(DraftOperation.REPLACE, card.operation)

        assertFalse(controller.commit())
        assertEquals(42L, controller.state.restore!!.eventSeq)
        assertTrue(controller.requestRestore())
        assertNull(controller.state.restore)
        assertEquals(1, controller.state.completion!!.published)
        assertEquals(1, fake.published)
    }

    @Test
    fun movingFlushesTheLatestTextFirst() = runBlocking {
        val fake = Fake()
        val first = fake.add("第一张")
        fake.add("第二张")
        val controller = controller(fake)
        controller.open()

        controller.edit(controller.state.form!!.copy(title = "第一张改过"))
        assertTrue(controller.move(1))

        assertEquals(1, fake.stageCalls)
        assertEquals("第一张改过", fake.cards[first.draftId]!!.form.title)
        assertEquals(2L, controller.state.currentId)
        assertFalse(controller.state.dirty)
    }

    @Test
    fun failedSaveBlocksTheTransitionAndKeepsText() = runBlocking {
        val fake = Fake()
        fake.add("第一张")
        fake.add("第二张")
        val controller = controller(fake)
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
    fun insideSwipeMovesExactlyOneCardAndClampsAtTheEdges() = runBlocking {
        val fake = Fake()
        repeat(3) { fake.add("卡 $it") }
        val controller = controller(fake)
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
        assertTrue(controller.move(-1))
        assertEquals(1L, controller.state.currentId)
        assertTrue(controller.move(-1))
        assertEquals(1L, controller.state.currentId)
    }

    @Test
    fun outsideFlingCanJumpSeveralCardsButStillFlushesOnce() = runBlocking {
        val fake = Fake()
        repeat(5) { fake.add("卡 $it") }
        val controller = controller(fake)
        controller.open()
        controller.edit(controller.state.form!!.copy(title = "已编辑"))

        assertTrue(controller.jump(3))
        assertEquals(4L, controller.state.currentId)
        assertEquals(1, fake.stageCalls)
    }

    @Test
    fun completionAppearsOnlyForAnEmptyUnlockedQueueWithRealCounts() = runBlocking {
        val fake = Fake()
        fake.add("唯一一张")
        val controller = controller(fake)
        controller.open()

        assertTrue(controller.commit())
        val completion = controller.state.completion
        assertNotNull(completion)
        assertEquals(1, completion!!.published)
        assertEquals(0, completion.discarded)
        assertTrue(controller.state.cards.isEmpty())
    }

    @Test
    fun completionReportsPublishedAndDiscardedCountsForTheSession() = runBlocking {
        val fake = Fake()
        fake.add("提交这张")
        fake.add("丢弃最后一张")
        val controller = controller(fake)
        controller.open()

        assertTrue(controller.commit())
        tick()
        assertTrue(controller.discard())

        val completion = controller.state.completion
        assertNotNull(completion)
        assertEquals(1, completion!!.published)
        assertEquals(1, completion.discarded)
        assertTrue(controller.state.cards.isEmpty())
    }

    @Test
    fun completionIsSuppressedWhileAnAiBatchIsLocked() = runBlocking {
        val fake = Fake()
        fake.add("唯一一张")
        val controller = controller(fake)
        controller.open()
        fake.locked = true

        assertFalse(controller.commit())
        assertNull(controller.state.completion)
        assertTrue(controller.state.cards.isNotEmpty())
    }

    @Test
    fun undoingTheLastDiscardCancelsCompletion() = runBlocking {
        val fake = Fake()
        val only = fake.add("唯一一张")
        val controller = controller(fake)
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
    fun aiAppendingCardsDoesNotStealTheCurrentCard() = runBlocking {
        val fake = Fake()
        fake.add("第一张")
        fake.add("第二张")
        val controller = controller(fake)
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
    fun aConflictKeepsTheCardAndAsksForExplicitReplacement() = runBlocking {
        val fake = Fake()
        fake.add("旧卡")
        fake.conflictOnCommit = true
        fake.currentRevisionForRecord = 1L
        val controller = controller(fake)
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
    fun promoteAfterCommitUsesQueueOrderNotTheOldIndex() = runBlocking {
        val fake = Fake()
        fake.add("A")
        fake.add("B")
        fake.add("C")
        val controller = controller(fake)
        controller.open()

        // Move to B, commit it: the next card should be C (next in queue order).
        assertTrue(controller.move(1))
        assertTrue(controller.commit())
        assertEquals("C", controller.state.form!!.title)

        // Commit C: only A remains even though it is "older" than the card just handled.
        tick()
        assertTrue(controller.commit())
        assertEquals("A", controller.state.form!!.title)
        assertTrue(controller.move(1))
        tick()
        assertTrue(controller.commit())
        assertNotNull(controller.state.completion)
        assertEquals(3, controller.state.completion!!.published)
    }

    @Test
    fun twoRapidClicksPublishOnlyOneCard() = runBlocking {
        val fake = Fake()
        fake.add("A")
        fake.add("B")
        val controller = controller(fake)
        controller.open()

        assertTrue(controller.commit())
        // The second click lands before the UI can re-target the button; it must not fall through
        // to the card that just became current.
        assertFalse(controller.commit())
        assertEquals(1, fake.published)
        assertEquals("B", controller.state.form!!.title)
    }

    @Test
    fun aConflictIsRevalidatedBeforeOverwritingAnything() = runBlocking {
        val fake = Fake()
        fake.add("旧卡")
        fake.conflictOnCommit = true
        fake.currentRevisionForRecord = 2L
        val controller = controller(fake)
        controller.open()

        assertFalse(controller.commit())
        assertNotNull(controller.state.conflict)
        assertEquals(2L, controller.state.conflict!!.currentRevision)

        // Another writer commits while the dialog is open: the shown diff is now stale.
        fake.currentRevisionForRecord = 3L
        assertFalse(controller.confirmConflictReplacement())
        assertNotNull("the prompt must be recomputed, not applied", controller.state.conflict)
        assertEquals(3L, controller.state.conflict!!.currentRevision)
        assertEquals(0, fake.published)

        // After re-reading the new diff the user can confirm for real.
        assertTrue(controller.confirmConflictReplacement())
        assertEquals(1, fake.published)
        assertEquals(3L, fake.expectedCurrentRevisionOnCommit)
        assertNull(controller.state.conflict)
    }

    @Test
    fun transactionRevisionMismatchKeepsConflictPromptAfterReview() = runBlocking {
        val fake = Fake()
        fake.add("并发变化")
        fake.conflictOnCommit = true
        fake.currentRevisionForRecord = 4L
        val controller = controller(fake)
        controller.open()
        assertFalse(controller.commit())
        assertEquals(4L, controller.state.conflict!!.currentRevision)

        fake.conflictOnCommit = false
        fake.advanceRevisionOnCommit = true
        assertFalse(controller.confirmConflictReplacement(4L))
        assertEquals(5L, controller.state.conflict!!.currentRevision)
        assertEquals(0, fake.published)

        fake.advanceRevisionOnCommit = false
        assertTrue(controller.confirmConflictReplacement(5L))
        assertEquals(1, fake.published)
        assertNull(controller.state.conflict)
    }

    @Test
    fun editingWhileTheConflictDialogIsOpenAlsoForcesAReconfirmation() = runBlocking {
        val fake = Fake()
        fake.add("旧卡")
        fake.conflictOnCommit = true
        fake.currentRevisionForRecord = 2L
        val controller = controller(fake)
        controller.open()

        assertFalse(controller.commit())
        controller.edit(controller.state.form!!.copy(title = "对话框打开时又改了一次"))
        assertFalse(controller.confirmConflictReplacement())
        assertNotNull(controller.state.conflict)
        assertEquals(0, fake.published)
    }

    @Test
    fun repeatedClicksCannotPublishTwice() = runBlocking {
        val fake = Fake()
        fake.add("唯一一张")
        val controller = controller(fake)
        controller.open()

        assertTrue(controller.commit())
        assertFalse(controller.commit())
        assertEquals(1, fake.published)
        assertEquals(1, fake.commitCalls)
    }

    @Test
    fun editingDuringAnAiLockIsRejected() = runBlocking {
        val fake = Fake()
        fake.add("锁定卡")
        val controller = controller(fake)
        controller.open()
        fake.locked = true
        controller.setAiLocked(true)

        controller.edit(controller.state.form!!.copy(title = "不该写进去"))
        assertEquals("锁定卡", controller.state.form!!.title)
        assertNotNull(controller.state.error)
    }
}
