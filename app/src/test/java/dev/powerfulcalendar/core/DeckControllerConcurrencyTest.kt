package dev.powerfulcalendar.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * DSH-07 — the controller's durable commands are serialized, cancellable while queued, and never
 * let accepted keystrokes disappear behind slow storage. `edit()` is deliberately not part of the
 * command queue: it only mutates in-memory state.
 */
class DeckControllerConcurrencyTest {

    /** Holds a dispatched command before its body begins, so cancellation-before-storage is testable. */
    private class GateDispatcher : CoroutineDispatcher() {
        @Volatile var gated = false
        val entered = CountDownLatch(1)
        private val queued = ArrayBlockingQueue<Runnable>(4)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!gated) {
                block.run()
            } else {
                queued.put(block)
                entered.countDown()
            }
        }

        fun runQueued() {
            val task = queued.poll(5, TimeUnit.SECONDS) ?: error("no dispatcher task queued")
            task.run()
        }
    }

    private class Fake : DeckPersistence {
        val cards = LinkedHashMap<Long, DraftCard>()
        private var nextId = 1L
        private var nextOrder = 1L

        val stageEntered = CountDownLatch(1)
        val releaseStage = CountDownLatch(1)
        @Volatile var blockStage = false
        @Volatile var blockOnlyFirstStage = false
        @Volatile var stageFailure: Exception? = null
        val stageCalls = AtomicInteger()
        val pendingCalls = AtomicInteger()

        fun add(title: String): DraftCard {
            val card = DraftCard(
                draftId = nextId,
                eventSeq = 100L + nextId,
                queueOrder = nextOrder++,
                operation = DraftOperation.CREATE,
                form = DraftForm(TemporalKind.ACTIVITY, title, "2026-10-20 09:00", "2026-10-20 10:00"),
                status = DraftStatus.PENDING,
                baseRevision = null,
                draftVersion = 0
            )
            nextId++
            cards[card.draftId] = card
            return card
        }

        override fun pending(): List<DraftCard> {
            pendingCalls.incrementAndGet()
            return cards.values.sortedBy { it.queueOrder }
        }

        override fun card(draftId: Long): DraftCard? = cards[draftId]

        override fun stage(draftId: Long, form: DraftForm, expectedVersion: Long): PowerStore.StageResult {
            val call = stageCalls.incrementAndGet()
            stageFailure?.let { throw it }
            if (blockStage || (blockOnlyFirstStage && call == 1)) {
                stageEntered.countDown()
                releaseStage.await(10, TimeUnit.SECONDS)
            }
            val card = cards[draftId] ?: return PowerStore.StageResult.Missing
            if (card.draftVersion != expectedVersion) {
                return PowerStore.StageResult.VersionMismatch(card)
            }
            val updated = card.copy(form = form, draftVersion = card.draftVersion + 1)
            cards[draftId] = updated
            return PowerStore.StageResult.Saved(updated.draftVersion)
        }

        override fun commit(
            draftId: Long,
            approveStale: Boolean,
            expectedCurrentRevision: Long?
        ): PowerStore.CommitOutcome {
            val card = cards.remove(draftId) ?: return PowerStore.CommitOutcome.Invalid("缺失")
            return PowerStore.CommitOutcome.Committed(card.eventSeq, 1, "c-$draftId")
        }

        override fun discard(draftId: Long): Boolean = cards.remove(draftId) != null
        override fun undo(draftId: Long): Boolean = false
        override fun pendingCount(): Int = cards.size
        override fun stats(sinceMillis: Long): PowerStore.DeckStats = PowerStore.DeckStats(0, 0, 0)
        override fun isAiLocked(): Boolean = false
        override fun restoreDraft(draftId: Long, form: DraftForm): PowerStore.CommitOutcome =
            PowerStore.CommitOutcome.Committed(draftId, 1, "r")
        override fun proposeRestore(eventSeq: Long): DraftCard =
            add("恢复提案").copy(eventSeq = eventSeq, operation = DraftOperation.REPLACE)
                .also { cards[it.draftId] = it }

        override fun record(eventSeq: Long): TemporalRecord? = null
    }

    private fun controller(fake: Fake) = DeckController(fake, ioDispatcher = Dispatchers.IO)

    @Test
    fun editingNeverWaitsForBackgroundAutosaveIo() = runBlocking {
        val fake = Fake()
        fake.add("初始")
        fake.blockStage = true
        val controller = controller(fake)
        controller.open()
        controller.edit(controller.state.form!!.copy(title = "第一版"))

        // Debounced autosave must not lock fields; command serialization still protects navigation.
        val flush = launch(Dispatchers.IO) { controller.flush(lockEdits = false) }
        assertTrue("flush should reach storage", fake.stageEntered.await(5, TimeUnit.SECONDS))

        val elapsed = CompletableDeferred<Long>()
        launch(Dispatchers.Default) {
            val startedAt = System.nanoTime()
            controller.edit(controller.state.form!!.copy(title = "第二版"))
            elapsed.complete((System.nanoTime() - startedAt) / 1_000_000)
        }
        val waitedMs = withTimeout(2_000) { elapsed.await() }
        assertTrue("edit() waited ${waitedMs}ms behind storage IO", waitedMs < 200)
        assertEquals(1L, controller.state.currentId)
        assertEquals("第二版", controller.state.form!!.title)
        assertTrue("accepted input stays dirty until its latest version is staged", controller.state.dirty)

        fake.releaseStage.countDown()
        flush.join()
        assertTrue("newer text remains dirty for the next debounce save", controller.state.dirty)
        assertTrue(controller.flush(lockEdits = false))
        assertEquals("第二版", fake.cards[1L]!!.form.title)
    }

    @Test
    fun lifecycleFlushQueuedBehindAutosavePersistsTheLatestAcceptedEdit() = runBlocking {
        val fake = Fake()
        val first = fake.add("初始")
        fake.blockOnlyFirstStage = true
        val controller = controller(fake)
        controller.open()
        controller.edit(controller.state.form!!.copy(title = "自动暂存快照"))

        val autosave = launch(Dispatchers.IO) { controller.flush(lockEdits = false) }
        assertTrue("autosave should reach storage", fake.stageEntered.await(5, TimeUnit.SECONDS))
        controller.edit(controller.state.form!!.copy(title = "后台前最后输入"))
        val onStop = launch(Dispatchers.IO) { controller.flush() }
        delay(20) // let the lifecycle command reach the held command mutex before releasing storage
        assertEquals(1, fake.stageCalls.get())

        fake.releaseStage.countDown()
        autosave.join()
        onStop.join()

        assertEquals("后台前最后输入", fake.cards[first.draftId]!!.form.title)
        assertFalse(controller.state.dirty)
    }

    @Test
    fun cancellationAfterMutexButBeforeStorageDispatchDoesNotRunTheCommand() = runBlocking {
        val fake = Fake()
        fake.add("唯一")
        val dispatcher = GateDispatcher()
        val controller = DeckController(fake, ioDispatcher = dispatcher)
        controller.open() // gate is open; initialize the state synchronously
        controller.edit(controller.state.form!!.copy(title = "待暂存"))

        dispatcher.gated = true
        val job = launch(Dispatchers.Default) { controller.flush(lockEdits = false) }
        assertTrue("command should reach the IO dispatch gate", dispatcher.entered.await(5, TimeUnit.SECONDS))
        job.cancel()
        dispatcher.runQueued() // the cancelled continuation must not invoke persistence.stage
        withTimeout(2_000) { job.join() }

        assertEquals("storage was never entered", 0, fake.stageCalls.get())
        assertTrue("accepted input remains dirty for a later command", controller.state.dirty)
        assertFalse(controller.state.busy)
    }

    @Test
    fun storageFailureKeepsAcceptedInputDirtyAndPublishesRetryState() = runBlocking {
        val fake = Fake()
        fake.add("唯一")
        val controller = controller(fake)
        controller.open()
        controller.edit(controller.state.form!!.copy(title = "仍保留的输入"))
        fake.stageFailure = IllegalStateException("disk unavailable")

        assertFalse(controller.flush())

        assertEquals("仍保留的输入", controller.state.form!!.title)
        assertTrue(controller.state.dirty)
        assertTrue(controller.state.error!!.contains("disk unavailable"))
        assertFalse(controller.state.busy)

        fake.stageFailure = null
        assertTrue(controller.flush())

        assertEquals("仍保留的输入", fake.cards[1L]!!.form.title)
        assertFalse(controller.state.dirty)
        assertEquals(null, controller.state.error)
        assertFalse(controller.state.busy)
    }

    @Test
    fun concurrentMovesSerializeInsteadOfBothLandingOnTheSameIndex() = runBlocking {
        val fake = Fake()
        val first = fake.add("A")
        fake.add("B")
        fake.add("C")
        val controller = controller(fake)
        controller.open()

        val one = async(Dispatchers.Default) { controller.move(1) }
        val two = async(Dispatchers.Default) { controller.move(1) }
        assertTrue(one.await())
        assertTrue(two.await())

        // If the two commands had read the same starting index they would both stop on card B.
        assertEquals(3L, controller.state.currentId)
        assertTrue(first.draftId != controller.state.currentId)
    }

    @Test
    fun aQueuedCommandCanBeCancelledBeforeItTouchesStorage() = runBlocking {
        val fake = Fake()
        fake.add("唯一")
        fake.blockStage = true
        val controller = controller(fake)
        controller.open()
        controller.edit(controller.state.form!!.copy(title = "待保存"))

        val flush = launch(Dispatchers.IO) { controller.flush() }
        assertTrue(fake.stageEntered.await(5, TimeUnit.SECONDS))
        val pendingBefore = fake.pendingCalls.get()

        val queued = launch(Dispatchers.IO) { controller.refresh() }
        delay(150) // let it reach the mutex
        queued.cancel()
        fake.releaseStage.countDown()
        flush.join()
        queued.join()

        assertEquals("a cancelled queued command must not run", pendingBefore, fake.pendingCalls.get())
    }

    @Test
    fun cancellingACommandMidWriteStillRecordsTheAcceptedInput() = runBlocking {
        val fake = Fake()
        fake.add("初始")
        fake.blockStage = true
        val controller = controller(fake)
        controller.open()
        controller.edit(controller.state.form!!.copy(title = "最后一个字"))

        val flush = launch(Dispatchers.IO) { controller.flush() }
        assertTrue(fake.stageEntered.await(5, TimeUnit.SECONDS))
        flush.cancel()
        fake.releaseStage.countDown()
        flush.join()

        // The atomic write finished under NonCancellable and its version/dirty result was recorded.
        assertEquals("最后一个字", fake.cards[1L]!!.form.title)
        assertEquals(1L, controller.state.version)
        assertFalse(controller.state.dirty)
    }

    @Test
    fun navigationSavesTheLastAcceptedCharacterAndRejectsEditsDuringTheTransition() = runBlocking {
        val fake = Fake()
        val first = fake.add("第一张")
        fake.add("第二张")
        fake.blockOnlyFirstStage = true
        val controller = controller(fake)
        controller.open()
        // This last character was accepted before the navigation command began.
        controller.edit(controller.state.form!!.copy(title = "最后一字"))

        val move = launch(Dispatchers.IO) { controller.move(1) }
        assertTrue("first stage should be slow", fake.stageEntered.await(5, TimeUnit.SECONDS))
        // A stale IME event arriving after the command has claimed the card is explicitly refused,
        // not accepted and then lost when the next card is promoted.
        controller.edit(controller.state.form!!.copy(title = "不应覆盖"))
        assertEquals("最后一字", controller.state.form!!.title)
        assertNotNull(controller.state.error)
        fake.releaseStage.countDown()
        move.join()

        assertEquals("最后一字", fake.cards[first.draftId]!!.form.title)
        assertEquals("第二张", controller.state.current?.form?.title)
        assertTrue("the second card must be reached", controller.state.currentId != first.draftId)
    }
}
