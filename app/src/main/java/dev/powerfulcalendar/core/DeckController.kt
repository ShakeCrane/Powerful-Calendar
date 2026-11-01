package dev.powerfulcalendar.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.util.concurrent.CopyOnWriteArraySet

private const val REVIEWED_DRAFT_VERSION_CONFLICT_MESSAGE =
    "草稿已被其他操作更新；本次提交未写入，请重新检查"

/**
 * Interaction core of 待处理. Deliberately free of Compose and Android so the rules that protect
 * user text can be tested without a device:
 *
 *  - identity is the draftId, never a list index (I-08);
 *  - a page transition is only allowed after the latest edit is durably staged (I-09);
 *  - one swipe moves exactly one queue card; outside drags may jump further but still flush first;
 *  - a failed save keeps the card and the in-memory text;
 *  - completion exists only when the queue is really empty, the AI batch is finished and unlocked,
 *    and every save landed (I-15);
 *  - undoing the last discard cancels completion.
 *
 * ## Concurrency contract (DSH-07)
 *
 * Storage is slow; typing is not. The controller therefore separates two concerns:
 *
 *  - **State** is mutated only through [update], which holds [stateLock] for a few instructions and
 *    never performs IO. [edit] takes that short path only, so the main thread can never wait for a
 *    database write.
 *  - **Commands** (open/refresh/flush/navigation/commit/discard/restore) are `suspend` functions
 *    serialized by [commands]. Each one runs the durable work on [ioDispatcher].
 *
 * Cancellation is cooperative: a command can be cancelled while queued or before it is dispatched
 * to [ioDispatcher]. Once a synchronous SQLite operation has started, Kotlin cannot pre-empt that
 * call; the command records its version/dirty result before returning. Accepted edits remain in
 * memory while dirty, but an OS process kill before a successful flush can still lose unsaved text.
 * There is exactly one controller and one command queue; no second lifecycle manager, and the UI
 * keeps one serialization point for gestures.
 *
 * The controller never talks to SQLite directly: [DeckPersistence] is the seam tests replace.
 */
interface DeckPersistence {
    fun pending(): List<DraftCard>
    fun card(draftId: Long): DraftCard?
    fun stage(draftId: Long, form: DraftForm, expectedVersion: Long): PowerStore.StageResult
    fun commit(
        draftId: Long,
        approveStale: Boolean,
        expectedCurrentRevision: Long? = null
    ): PowerStore.CommitOutcome
    /** Transactional draft-version fence for a proposal that was explicitly reviewed. */
    fun commit(
        draftId: Long,
        approveStale: Boolean,
        expectedCurrentRevision: Long?,
        expectedDraftVersion: Long?
    ): PowerStore.CommitOutcome = commit(draftId, approveStale, expectedCurrentRevision)

    fun discard(draftId: Long): Boolean
    fun undo(draftId: Long): Boolean
    fun pendingCount(): Int
    fun stats(sinceMillis: Long): PowerStore.DeckStats
    fun isAiLocked(): Boolean

    /** Restoration is driven by the proposing card so it cannot survive as a stale duplicate. */
    fun restoreDraft(draftId: Long, form: DraftForm): PowerStore.CommitOutcome

    /** Restore also binds the tombstone revision that the user reviewed. */
    fun restoreDraft(
        draftId: Long,
        form: DraftForm,
        expectedTombstoneRevision: Long?
    ): PowerStore.CommitOutcome = restoreDraft(draftId, form)

    fun restoreDraft(
        draftId: Long,
        form: DraftForm,
        expectedTombstoneRevision: Long?,
        expectedDraftVersion: Long?
    ): PowerStore.CommitOutcome = restoreDraft(draftId, form, expectedTombstoneRevision)

    /** Creates or resumes a proposal for a tombstone; confirmation remains a separate action. */
    fun proposeRestore(eventSeq: Long): DraftCard

    /** Current formal snapshot, used to re-verify a conflict prompt before overwriting. */
    fun record(eventSeq: Long): TemporalRecord?

    /** Revision-checked proposal creation used by the shared Draft/Review API. */
    fun proposeFormalReplacement(eventSeq: Long, expectedRevision: Long): DeckProposalResult =
        DeckProposalResult.Failed("正式替换提案不可用")

    /** Revision-checked restore proposal creation used by the shared Draft/Review API. */
    fun proposeRestoreAtRevision(eventSeq: Long, expectedRevision: Long): DeckProposalResult =
        DeckProposalResult.Failed("恢复提案不可用")
}

sealed interface DeckProposalResult {
    data class Proposed(val card: DraftCard) : DeckProposalResult
    data class Stale(val currentRevision: Long?) : DeckProposalResult
    data object Missing : DeckProposalResult
    data object Locked : DeckProposalResult
    data object Conflict : DeckProposalResult
    data class Failed(val message: String) : DeckProposalResult
}

enum class DeckEditResult { APPLIED, STALE, LOCKED }
enum class DeckReviewCommandResult { APPLIED, STALE, NOT_APPLIED, LOCKED }

class DeckController(
    private val persistence: DeckPersistence,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    data class ConflictPrompt(
        val draftId: Long,
        val eventSeq: Long,
        val current: TemporalRecord?,
        val changes: List<FieldChange>,
        val card: DraftCard,
        /** Revision the diff was computed against; re-verified before the overwrite. */
        val currentRevision: Long?,
        /** Exact snapshot the user was shown and approved. */
        val approvedForm: DraftForm
    )

    data class RestorePrompt(
        val eventSeq: Long,
        val card: DraftCard,
        val message: String
    )

    data class Completion(val published: Int, val discarded: Int, val conflicts: Int)

    data class DeckState(
        val cards: List<DraftCard> = emptyList(),
        val currentId: Long? = null,
        val current: DraftCard? = null,
        val form: DraftForm? = null,
        val version: Long = 0L,
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val aiLocked: Boolean = false,
        val notice: String? = null,
        val error: String? = null,
        val conflict: ConflictPrompt? = null,
        val restore: RestorePrompt? = null,
        val completion: Completion? = null,
        /** Set after a discard so the card's slot can be restored before the queue empties. */
        val lastDiscardId: Long? = null,
        /** True while a command is queued or executing; the UI may disable terminal actions. */
        val busy: Boolean = false
    ) {
        val position: Int get() = cards.indexOfFirst { it.draftId == currentId }.coerceAtLeast(0)
        val canGoPrevious: Boolean get() = position > 0
        val canGoNext: Boolean get() = position < cards.lastIndex
        val isEmpty: Boolean get() = cards.isEmpty()
        val canUndoDiscard: Boolean get() = lastDiscardId != null
    }

    @Volatile
    var state: DeckState = DeckState()
        private set

    private val stateLock = Any()
    private val listenerLock = Any()
    private val commands = Mutex()
    private var stateGeneration = 0L
    private var notifiedGeneration = 0L

    private var listener: ((DeckState) -> Unit)? = null
    private val additionalListeners = CopyOnWriteArraySet<(DeckState, Long) -> Unit>()
    private var sessionStartedAt: Long = clock()
    private var undoDraftId: Long? = null
    private var processedInSession = 0

    /**
     * Terminal actions are rate-limited. A double tap on 发布 would otherwise fall through to the
     * next card that just became current and publish two proposals from one intent.
     */
    private var lastTerminalActionAt = 0L
    private val terminalActionGuardMillis = 600L

    private fun terminalActionAllowed(now: Long): Boolean =
        now - lastTerminalActionAt >= terminalActionGuardMillis

    fun observe(block: (DeckState) -> Unit) {
        // Serialize the initial snapshot with later notifications. Otherwise a concurrent update
        // could notify v2 and then this initial callback could arrive last with stale v1.
        synchronized(listenerLock) {
            val snapshot = synchronized(stateLock) {
                listener = block
                notifiedGeneration = stateGeneration
                state
            }
            block(snapshot)
        }
    }

    /** Additional observers do not replace the single presentation listener. */
    fun observeAdditional(block: (DeckState) -> Unit): AutoCloseable {
        return observeAdditionalVersioned { snapshot, _ -> block(snapshot) }
    }

    /** Observer variant that keeps the state snapshot and its revision from the same transition. */
    fun observeAdditionalVersioned(block: (DeckState, Long) -> Unit): AutoCloseable {
        synchronized(listenerLock) {
            val (snapshot, revision) = synchronized(stateLock) { state to stateGeneration }
            additionalListeners += block
            block(snapshot, revision)
        }
        return AutoCloseable {
            synchronized(listenerLock) { additionalListeners -= block }
        }
    }

    fun stateSnapshot(): Pair<DeckState, Long> = synchronized(stateLock) { state to stateGeneration }

    /** Monotonic in-memory revision for rejecting delayed edits against a stale snapshot. */
    val stateRevision: Long
        get() = synchronized(stateLock) { stateGeneration }

    /**
     * Atomic state transition. Only memory work happens here: no storage call may ever be made
     * while holding [stateLock]. Listener callbacks are serialized, but invoked after releasing the
     * state lock; a stale callback is skipped rather than publishing over a newer edit.
     */
    private fun update(transform: (DeckState) -> DeckState): DeckState {
        val next: DeckState
        val generation: Long
        synchronized(stateLock) {
            next = transform(state)
            state = next
            stateGeneration++
            generation = stateGeneration
        }
        notifyListeners(next, generation)
        return next
    }

    /** Applies an edit only if the caller's view is still the current state revision. */
    fun editIfCurrent(form: DraftForm, expectedStateRevision: Long): DeckEditResult {
        val next: DeckState
        val generation: Long
        synchronized(stateLock) {
            if (stateGeneration != expectedStateRevision) return DeckEditResult.STALE
            if (state.aiLocked || state.busy) return DeckEditResult.LOCKED
            next = state.copy(
                form = form,
                dirty = form != state.current?.form,
                error = null
            )
            state = next
            stateGeneration++
            generation = stateGeneration
        }
        notifyListeners(next, generation)
        return DeckEditResult.APPLIED
    }

    private fun notifyListeners(next: DeckState, generation: Long) {
        synchronized(listenerLock) {
            val targets = synchronized(stateLock) {
                if (generation <= notifiedGeneration) emptyList()
                else buildList {
                    notifiedGeneration = generation
                    listener?.let { callback -> add { callback(next) } }
                    additionalListeners.forEach { callback -> add { callback(next, generation) } }
                }
            }
            targets.forEach { callback -> callback() }
        }
    }

    /**
     * Serializes one durable command. Cancellation is honoured while queued; once the write
     * starts it completes and records its result so the state and the storage never disagree.
     */
    private suspend fun <T> command(lockEdits: Boolean = true, block: () -> T): T = commands.withLock {
        coroutineContext.ensureActive()
        // Background autosave is serialized but must not make the editor read-only while the IME
        // is typing. Navigation and terminal commands use the default lockEdits=true.
        update { it.copy(busy = lockEdits) }
        try {
            // Dispatch remains cancellable: if the parent is cancelled after the mutex is acquired
            // but before this command reaches storage, the block never starts. The block itself is
            // synchronous; once an atomic SQLite call begins it cannot be pre-empted, and it records
            // its resulting state before returning to this cancellable boundary.
            withContext(ioDispatcher) {
                coroutineContext.ensureActive()
                block()
            }
        } finally {
            update { it.copy(busy = false) }
        }
    }

    /** Fences an approved action and marks the deck busy in the same state-lock transition. */
    private suspend fun reviewedCommand(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        block: () -> Boolean
    ): DeckReviewCommandResult = commands.withLock {
        coroutineContext.ensureActive()
        val started: Pair<DeckState, Long>
        synchronized(stateLock) {
            if (stateGeneration != expectedStateRevision || state.currentId != draftId ||
                state.version != expectedDraftVersion
            ) return@withLock DeckReviewCommandResult.STALE
            if (state.aiLocked || state.busy) return@withLock DeckReviewCommandResult.LOCKED
            val next = state.copy(busy = true)
            state = next
            stateGeneration++
            started = next to stateGeneration
        }
        notifyListeners(started.first, started.second)
        try {
            val applied = withContext(ioDispatcher) {
                coroutineContext.ensureActive()
                block()
            }
            if (applied) DeckReviewCommandResult.APPLIED
            else {
                val latest = stateSnapshot().first
                if (latest.currentId != draftId || latest.version != expectedDraftVersion ||
                    (latest.dirty && latest.error == REVIEWED_DRAFT_VERSION_CONFLICT_MESSAGE)
                )
                    DeckReviewCommandResult.STALE
                else DeckReviewCommandResult.NOT_APPLIED
            }
        } finally {
            update { it.copy(busy = false) }
        }
    }

    // ---------------------------------------------------------------- state-only operations

    fun edit(form: DraftForm) {
        update { current ->
            when {
                current.aiLocked -> current.copy(error = "AI 批量处理期间不能编辑卡片")
                current.busy -> current.copy(error = "卡片正在保存或切换，请稍候再输入")
                else -> current.copy(
                    form = form,
                    dirty = form != current.current?.form,
                    error = null
                )
            }
        }
    }

    fun dismissConflict() {
        update { it.copy(conflict = null) }
    }

    fun dismissRestore() {
        update { it.copy(restore = null) }
    }

    fun dismissCompletion() {
        update { it.copy(completion = null) }
    }

    fun clearError() {
        update { it.copy(error = null, notice = null) }
    }

    fun setAiLocked(locked: Boolean) {
        update { it.copy(aiLocked = locked) }
    }

    fun updateAiLock(locked: Boolean) = setAiLocked(locked)

    // ---------------------------------------------------------------- durable commands

    suspend fun open(preferDraftId: Long? = null) = command {
        resetSession()
        refreshInternal(preferDraftId)
    }

    /** Opens a tombstone restore proposal; it does not reactivate the formal event. */
    suspend fun beginRestore(eventSeq: Long): Boolean = command {
        val card = persistence.proposeRestore(eventSeq)
        resetSession()
        refreshInternal(card.draftId)
        true
    }

    suspend fun beginRestore(eventSeq: Long, expectedRevision: Long): DeckProposalResult = command {
        if (!flushInternal()) return@command DeckProposalResult.Failed(
            state.error ?: "当前草稿暂存失败，仍保留在待处理列表"
        )
        when (val result = persistence.proposeRestoreAtRevision(eventSeq, expectedRevision)) {
            is DeckProposalResult.Proposed -> {
                resetSession()
                refreshInternal(result.card.draftId)
                result
            }
            else -> result
        }
    }

    suspend fun beginFormalReplacement(
        eventSeq: Long,
        expectedRevision: Long
    ): DeckProposalResult = command {
        if (!flushInternal()) return@command DeckProposalResult.Failed(
            state.error ?: "当前草稿暂存失败，仍保留在待处理列表"
        )
        when (val result = persistence.proposeFormalReplacement(eventSeq, expectedRevision)) {
            is DeckProposalResult.Proposed -> {
                resetSession()
                refreshInternal(result.card.draftId)
                result
            }
            else -> result
        }
    }

    /**
     * Reloads the queue without stealing focus: a new AI card appended at the tail must not move
     * the card the user is currently editing (I-14).
     */
    suspend fun refresh(preferDraftId: Long? = null) = command { refreshInternal(preferDraftId) }

    /** Durably stages the latest text. Returns false when the caller must not navigate away. */
    /**
     * `lockEdits=false` is reserved for the debounce autosave: it is serialized with navigation,
     * but the editor remains writable so IME keystrokes do not lose their focus/selection.
     */
    suspend fun flush(lockEdits: Boolean = true): Boolean = command(lockEdits = lockEdits) { flushInternal() }

    suspend fun select(draftId: Long): Boolean = command { selectInternal(draftId) }

    /** Exactly one card per inside swipe. Right/previous is -1, left/next is +1. */
    suspend fun move(delta: Int): Boolean = command { moveInternal(delta) }

    /** Fling/outside drag: still flush once, then jump (possibly several cards) in one gesture. */
    suspend fun jump(steps: Int): Boolean = command { jumpInternal(steps) }

    suspend fun commit(approveStale: Boolean = false): Boolean =
        command { commitInternal(approveStale) }

    suspend fun commitReviewed(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long
    ): DeckReviewCommandResult = reviewedCommand(draftId, expectedDraftVersion, expectedStateRevision) {
        commitInternal(approveStale = false, expectedDraftVersion = expectedDraftVersion)
    }

    /** User explicitly approved replacing the now-current revision with this card's snapshot. */
    suspend fun confirmConflictReplacement(expectedCurrentRevision: Long? = null): Boolean =
        command { confirmConflictReplacementInternal(expectedCurrentRevision) }

    suspend fun confirmConflictReplacementReviewed(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedCurrentRevision: Long,
        expectedStateRevision: Long
    ): DeckReviewCommandResult = reviewedCommand(draftId, expectedDraftVersion, expectedStateRevision) {
        confirmConflictReplacementInternal(expectedCurrentRevision, expectedDraftVersion)
    }

    suspend fun requestRestore(expectedTombstoneRevision: Long? = null): Boolean =
        command { requestRestoreInternal(expectedTombstoneRevision) }

    suspend fun requestRestoreReviewed(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedTombstoneRevision: Long,
        expectedStateRevision: Long
    ): DeckReviewCommandResult = reviewedCommand(draftId, expectedDraftVersion, expectedStateRevision) {
        requestRestoreInternal(expectedTombstoneRevision, expectedDraftVersion)
    }

    /** X: discard the proposal. Retention keeps the row so undo can restore the same queue slot. */
    suspend fun discard(): Boolean = command { discardInternal() }

    suspend fun undoDiscard(): Boolean = command { undoDiscardInternal() }

    private fun resetSession() {
        undoDraftId = null
        lastTerminalActionAt = 0L
        processedInSession = 0
        sessionStartedAt = clock()
        update { it.copy(lastDiscardId = null, completion = null) }
    }

    // ---------------------------------------------------------------- internals

    private fun refreshInternal(preferDraftId: Long?) {
        val cards = persistence.pending()
        val aiLocked = persistence.isAiLocked()
        val snapshot = state
        val targetId = preferDraftId
            ?: snapshot.currentId?.takeIf { id -> cards.any { it.draftId == id } }
            ?: cards.firstOrNull()?.draftId
        val card = cards.firstOrNull { it.draftId == targetId }
        val keepDirtyForm = snapshot.currentId == targetId && snapshot.dirty && card != null
        val pendingInStore = persistence.pendingCount()
        // A batch that empties the queue must still produce a truthful completion card (G11-06),
        // and a batch that just ended must give the deck its editing rights back (M4).
        val justEmptied = snapshot.cards.isNotEmpty() && cards.isEmpty()
        val finished = cards.isEmpty() && !aiLocked && pendingInStore == 0 &&
            (processedInSession > 0 || justEmptied || snapshot.completion != null)
        val stats = if (finished) persistence.stats(sessionStartedAt) else null
        update { current ->
            current.copy(
                cards = cards,
                currentId = card?.draftId,
                current = card,
                form = when {
                    card == null -> null
                    keepDirtyForm -> current.form
                    else -> card.form
                },
                version = when {
                    card == null -> 0L
                    keepDirtyForm -> current.version
                    else -> card.draftVersion
                },
                dirty = keepDirtyForm,
                aiLocked = aiLocked,
                lastDiscardId = current.lastDiscardId,
                completion = when {
                    !finished -> null
                    current.completion != null -> current.completion
                    stats != null -> Completion(stats.published, stats.discarded, stats.conflicts)
                    else -> null
                }
            )
        }
    }

    private fun flushInternal(stopOnVersionMismatch: Boolean = false): Boolean {
        val snapshot = state
        val id = snapshot.currentId ?: return true
        val form = snapshot.form ?: return true
        if (!snapshot.dirty) return true
        if (snapshot.aiLocked) {
            update { it.copy(error = "AI 正独占卡组，稍后可继续编辑") }
            return false
        }
        return stageWithRetry(id, form, stopOnVersionMismatch)
    }

    /**
     * A navigation or terminal command must never leave accepted keystrokes behind. If an edit
     * arrives while the first stage is still in flight, the snapshot is dirty again and is staged
     * before the command is allowed to change cards. Bounded so a permanently failing store cannot
     * spin forever.
     */
    private fun flushFully(maxAttempts: Int = 3, stopOnVersionMismatch: Boolean = false): Boolean {
        repeat(maxAttempts) {
            if (!state.dirty) return true
            if (!flushInternal(stopOnVersionMismatch)) return false
            if (!state.dirty) return true
        }
        // Input kept arriving faster than it could be staged. Refuse the transition instead of
        // silently dropping the newest characters, and say why.
        update { it.copy(error = "最新输入尚未保存完成，请稍后再试") }
        return false
    }

    private fun stageWithRetry(
        draftId: Long,
        formToSave: DraftForm,
        stopOnVersionMismatch: Boolean = false
    ): Boolean {
        var version = state.version
        repeat(2) { attempt ->
            val result = try {
                persistence.stage(draftId, formToSave, version)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Lifecycle-triggered best-effort flushes have no synchronous caller waiting to
                // display a thrown SQLite error. Keep the input dirty and publish a retryable error.
                update {
                    it.copy(
                        saving = false,
                        error = "草稿暂存失败，输入仍保留，请重试：${error.message.orEmpty()}"
                    )
                }
                return false
            }
            when (result) {
                is PowerStore.StageResult.Saved -> {
                    // If the user kept typing while the write was in flight, the newest text is
                    // still dirty and is staged by the next debounce tick.
                    update { current ->
                        val stagedCurrent = current.current?.takeIf { it.draftId == draftId }
                            ?.copy(form = formToSave, draftVersion = result.draftVersion)
                        val stagedCards = current.cards.map { card ->
                            if (card.draftId == draftId) card.copy(
                                form = formToSave,
                                draftVersion = result.draftVersion
                            ) else card
                        }
                        current.copy(
                            cards = stagedCards,
                            current = stagedCurrent ?: current.current,
                            version = result.draftVersion,
                            dirty = current.form != formToSave,
                            saving = false,
                            error = null
                        )
                    }
                    return true
                }
                is PowerStore.StageResult.VersionMismatch -> {
                    if (stopOnVersionMismatch) {
                        // A reviewed write must never rebase stale form data over a newer draft.
                        // Keep the user's in-memory form dirty so it can be reviewed again.
                        update { it.copy(
                            saving = false,
                            error = REVIEWED_DRAFT_VERSION_CONFLICT_MESSAGE
                        ) }
                        return false
                    }
                    // Another writer advanced this card. Keep the text the user can see and land it
                    // on top of the newer row once; a second mismatch is surfaced, never looped.
                    version = result.current.draftVersion
                    update { current ->
                        current.copy(
                            version = version,
                            saving = false,
                            error = if (attempt == 0) null else "草稿已被其他操作更新，请检查后重试"
                        )
                    }
                }
                PowerStore.StageResult.Locked -> {
                    update { it.copy(saving = false, error = "AI 正独占卡组，修改尚未保存") }
                    return false
                }
                PowerStore.StageResult.Missing -> {
                    update { it.copy(saving = false, error = "草稿已不存在，请刷新") }
                    return false
                }
                PowerStore.StageResult.AlreadyFinal -> {
                    update { it.copy(saving = false, dirty = false) }
                    return false
                }
                PowerStore.StageResult.NotAuthorized -> {
                    update { it.copy(saving = false, error = "AI 批次未获授权，修改未保存") }
                    return false
                }
            }
        }
        update { it.copy(saving = false, error = "草稿保存失败，请重试") }
        return false
    }

    private fun selectInternal(draftId: Long): Boolean {
        if (draftId == state.currentId) return true
        if (!flushFully()) return false
        val card = persistence.card(draftId) ?: run {
            refreshInternal(null)
            return false
        }
        if (!card.isActionable) {
            refreshInternal(null)
            return false
        }
        update { current ->
            current.copy(
                currentId = card.draftId,
                current = card,
                form = card.form,
                version = card.draftVersion,
                dirty = false,
                error = null,
                notice = null
            )
        }
        return true
    }

    private fun moveInternal(delta: Int): Boolean {
        if (delta == 0) return true
        if (!flushFully()) return false
        val cards = state.cards
        if (cards.isEmpty()) return false
        val index = cards.indexOfFirst { it.draftId == state.currentId }
        if (index < 0) {
            refreshInternal(null)
            return false
        }
        val target = (index + delta).coerceIn(0, cards.lastIndex)
        if (target == index) return true
        promoteTo(cards[target])
        return true
    }

    private fun jumpInternal(steps: Int): Boolean {
        if (steps == 0) return true
        if (!flushFully()) return false
        val cards = state.cards
        if (cards.isEmpty()) return false
        val index = cards.indexOfFirst { it.draftId == state.currentId }
        if (index < 0) return false
        val target = (index + steps).coerceIn(0, cards.lastIndex)
        promoteTo(cards[target])
        return true
    }

    private fun promoteTo(card: DraftCard) {
        update { current ->
            current.copy(
                currentId = card.draftId,
                current = card,
                form = card.form,
                version = card.draftVersion,
                dirty = false,
                error = null,
                notice = null
            )
        }
    }

    private fun commitInternal(
        approveStale: Boolean,
        expectedCurrentRevision: Long? = null,
        expectedDraftVersion: Long? = null
    ): Boolean {
        val snapshot = state
        val id = snapshot.currentId ?: return false
        val now = clock()
        if (!terminalActionAllowed(now)) return false
        if (snapshot.aiLocked) {
            update { it.copy(error = "AI 批量处理期间不能发布卡片") }
            return false
        }
        val reviewedForm = snapshot.form
        if (expectedDraftVersion != null &&
            (snapshot.version != expectedDraftVersion || snapshot.currentId != id)
        ) {
            update { it.copy(error = "草稿在审阅后已变化，请重新检查后提交") }
            return false
        }
        if (!flushFully(stopOnVersionMismatch = expectedDraftVersion != null)) return false
        if (expectedDraftVersion != null && state.form != reviewedForm) {
            update { it.copy(error = "草稿内容在审阅后已变化，请重新检查后提交") }
            return false
        }
        val order = state.current?.queueOrder ?: Long.MAX_VALUE
        return when (val outcome = persistence.commit(
            id, approveStale, expectedCurrentRevision,
            expectedDraftVersion?.let { state.version }
        )) {
            is PowerStore.CommitOutcome.Committed -> {
                lastTerminalActionAt = clock()
                processedInSession++
                update { it.copy(conflict = null, restore = null, dirty = false) }
                afterRemoval(order)
                true
            }
            is PowerStore.CommitOutcome.Conflict -> {
                val form = state.form ?: DraftForm(TemporalKind.ACTIVITY)
                val card = state.current ?: return false
                val prompt = ConflictPrompt(
                    draftId = id,
                    eventSeq = card.eventSeq,
                    current = outcome.current,
                    changes = DraftDiff.between(outcome.current, form, card.eventSeq),
                    card = card,
                    currentRevision = outcome.current?.revision,
                    approvedForm = form
                )
                update { it.copy(conflict = prompt, error = null) }
                false
            }
            is PowerStore.CommitOutcome.DraftVersionMismatch -> {
                val latestCard = outcome.current
                update { current ->
                    val cards = current.cards.map { card ->
                        if (card.draftId == latestCard.draftId) latestCard else card
                    }
                    current.copy(
                        cards = cards,
                        current = latestCard.takeIf { current.currentId == latestCard.draftId },
                        form = latestCard.form.takeIf { current.currentId == latestCard.draftId },
                        version = if (current.currentId == latestCard.draftId)
                            latestCard.draftVersion else current.version,
                        dirty = false,
                        error = "草稿在审阅后已变化，请重新检查后提交"
                    )
                }
                false
            }
            is PowerStore.CommitOutcome.NeedsRestore -> {
                val card = state.current
                if (card == null) {
                    false
                } else {
                    update {
                        it.copy(
                            restore = RestorePrompt(
                                eventSeq = outcome.retiredSeq,
                                card = card,
                                message = "事件 #${outcome.retiredSeq} 已被删除。恢复会复用同一序号并生成新版本，不会静默复活。"
                            )
                        )
                    }
                    false
                }
            }
            is PowerStore.CommitOutcome.Invalid -> {
                update { it.copy(error = outcome.reason) }
                false
            }
            PowerStore.CommitOutcome.Locked -> {
                update { it.copy(error = "AI 正独占卡组，发布被拒绝") }
                false
            }
            PowerStore.CommitOutcome.NotAuthorized -> {
                update { it.copy(error = "未获授权的 AI 写入被拒绝") }
                false
            }
        }
    }

    private fun confirmConflictReplacementInternal(
        expectedCurrentRevision: Long?,
        expectedDraftVersion: Long? = null
    ): Boolean {
        val prompt = state.conflict ?: return false
        if (prompt.draftId != state.currentId) {
            update { it.copy(conflict = null, error = "冲突卡片已切换，请重新检查") }
            return false
        }
        // Re-verify what the user actually saw: another card may have committed, or the user may
        // have kept typing while the dialog was open. Never overwrite a diff that no longer holds.
        val reviewedRevision = expectedCurrentRevision ?: prompt.currentRevision
        if (reviewedRevision == null || prompt.currentRevision != reviewedRevision) {
            update { it.copy(error = "审阅版本已变化，请重新检查差异后再确认") }
            return false
        }
        val latest = persistence.record(prompt.eventSeq)
        val form = state.form ?: prompt.approvedForm
        if (latest?.revision != prompt.currentRevision || form != prompt.approvedForm) {
            update { current ->
                current.copy(
                    conflict = prompt.copy(
                        current = latest,
                        changes = DraftDiff.between(latest, form, prompt.eventSeq),
                        currentRevision = latest?.revision,
                        approvedForm = form
                    ),
                    error = "正式对象或本卡内容已变化，请重新检查差异后再确认"
                )
            }
            return false
        }
        val result = commitInternal(
            approveStale = true,
            expectedCurrentRevision = reviewedRevision,
            expectedDraftVersion = expectedDraftVersion
        )
        return result
    }

    private fun requestRestoreInternal(
        expectedTombstoneRevision: Long?,
        expectedDraftVersion: Long? = null
    ): Boolean {
        val prompt = state.restore ?: return false
        val reviewedForm = state.form
        if (expectedDraftVersion != null &&
            (state.version != expectedDraftVersion || state.currentId != prompt.card.draftId)
        ) {
            val latestCard = state.current ?: prompt.card
            update { it.copy(
                restore = prompt.copy(card = latestCard),
                error = "恢复草稿在审阅后已变化，请重新检查"
            ) }
            return false
        }
        if (!flushFully(stopOnVersionMismatch = expectedDraftVersion != null)) return false
        if (expectedDraftVersion != null && state.form != reviewedForm) {
            update { it.copy(error = "恢复内容在审阅后已变化，请重新检查") }
            return false
        }
        val form = state.form ?: return false
        val order = state.current?.queueOrder ?: Long.MAX_VALUE
        val reviewedRevision = expectedTombstoneRevision ?: prompt.card.baseRevision
        if (reviewedRevision == null || prompt.card.baseRevision != reviewedRevision) {
            update { it.copy(error = "已删除事件版本已变化，请重新审核后恢复") }
            return false
        }
        return when (val outcome = persistence.restoreDraft(
            prompt.card.draftId, form, reviewedRevision,
            expectedDraftVersion?.let { state.version }
        )) {
            is PowerStore.CommitOutcome.Committed -> {
                processedInSession++
                lastTerminalActionAt = clock()
                update {
                    it.copy(
                        restore = null,
                        notice = "已恢复事件 #${prompt.eventSeq} 并生成 v${outcome.revision}"
                    )
                }
                afterRemoval(order)
                true
            }
            is PowerStore.CommitOutcome.Invalid -> {
                update { it.copy(restore = null, error = outcome.reason) }
                false
            }
            is PowerStore.CommitOutcome.Conflict -> {
                update { it.copy(error = "已删除事件版本已变化，请重新审核后恢复") }
                false
            }
            is PowerStore.CommitOutcome.DraftVersionMismatch -> {
                val latestCard = outcome.current
                update { current ->
                    val cards = current.cards.map { card ->
                        if (card.draftId == latestCard.draftId) latestCard else card
                    }
                    current.copy(
                        cards = cards,
                        current = latestCard.takeIf { current.currentId == latestCard.draftId },
                        form = latestCard.form.takeIf { current.currentId == latestCard.draftId },
                        version = if (current.currentId == latestCard.draftId)
                            latestCard.draftVersion else current.version,
                        dirty = false,
                        restore = prompt.copy(card = latestCard),
                        error = "恢复草稿在审阅后已变化，请重新检查"
                    )
                }
                false
            }
            else -> {
                update { it.copy(restore = null, error = "恢复失败，草稿已保留") }
                false
            }
        }
    }

    private fun discardInternal(): Boolean {
        val snapshot = state
        val id = snapshot.currentId ?: return false
        val now = clock()
        if (!terminalActionAllowed(now)) return false
        if (snapshot.aiLocked) {
            update { it.copy(error = "AI 批量处理期间不能丢弃卡片") }
            return false
        }
        if (snapshot.dirty && !flushFully()) return false
        val order = state.current?.queueOrder ?: Long.MAX_VALUE
        if (!persistence.discard(id)) {
            update { it.copy(error = "丢弃失败，草稿已保留") }
            return false
        }
        undoDraftId = id
        lastTerminalActionAt = clock()
        processedInSession++
        afterRemoval(order, discardedId = id)
        return true
    }

    private fun undoDiscardInternal(): Boolean {
        val id = undoDraftId ?: return false
        if (!persistence.undo(id)) return false
        undoDraftId = null
        // Undoing the last action cancels a completion card that claimed the queue was finished.
        update { it.copy(completion = null, notice = "已撤销丢弃", lastDiscardId = null) }
        refreshInternal(id)
        return true
    }

    private fun afterRemoval(removedOrder: Long, discardedId: Long? = null) {
        val remaining = persistence.pending()
        val next = remaining.firstOrNull { it.queueOrder > removedOrder } ?: remaining.firstOrNull()
        val stats = persistence.stats(sessionStartedAt)
        val aiLocked = persistence.isAiLocked()
        val finished = remaining.isEmpty() && !aiLocked && persistence.pendingCount() == 0
        update { current ->
            current.copy(
                cards = remaining,
                currentId = next?.draftId,
                current = next,
                form = next?.form,
                version = next?.draftVersion ?: 0L,
                dirty = false,
                saving = false,
                error = null,
                notice = null,
                aiLocked = aiLocked,
                lastDiscardId = discardedId ?: current.lastDiscardId,
                completion = when {
                    finished -> Completion(stats.published, stats.discarded, stats.conflicts)
                    else -> null
                }
            )
        }
    }
}
