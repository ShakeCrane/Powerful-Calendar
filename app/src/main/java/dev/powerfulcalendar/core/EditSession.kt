package dev.powerfulcalendar.core

import java.time.Clock
import java.time.ZoneId
import java.util.UUID

/**
 * In-memory formal-event editor. Typing and undo never touch SQLite. The caller explicitly invokes
 * [checkpoint], [save], or [delete] at the corresponding lifecycle/user boundary.
 */
class EditSession private constructor(
    private val store: PowerStore,
    val eventSeq: Long,
    private var expectedRevision: Long,
    private val lease: PowerStore.HumanEditLease,
    private var baseline: DraftForm,
    initial: DraftForm
) : AutoCloseable {
    var form: DraftForm = initial
        private set
    var lastError: String? = null
        private set

    val meaningfulDiff: Boolean get() = form != baseline
    val baselineForm: DraftForm get() = baseline

    fun edit(next: DraftForm) {
        form = next
        lastError = null
        store.touchHumanEdit(lease.token)
    }

    fun discardEditingChanges() {
        form = baseline
        lastError = null
    }

    fun heartbeat(): Boolean = store.touchHumanEdit(lease.token)

    /** One durable checkpoint for an explicit exit/switch/ON_STOP boundary. */
    fun checkpoint(): DraftCard? = try {
        store.checkpointFormalEdit(
            eventSeq, expectedRevision, baseline, form, lease.token
        )
    } catch (error: Exception) {
        lastError = error.message ?: error.javaClass.simpleName
        null
    }

    /** Save remains on this session/card. A no-op save clears an old checkpoint without revision. */
    fun save(): PowerStore.DirectEditOutcome {
        val outcome = store.saveFormalEdit(eventSeq, expectedRevision, form, lease.token)
        when (outcome) {
            is PowerStore.DirectEditOutcome.Saved -> {
                expectedRevision = outcome.revision
                baseline = form
                lastError = null
            }
            is PowerStore.DirectEditOutcome.Invalid -> lastError = outcome.reason
            is PowerStore.DirectEditOutcome.Conflict -> lastError = "Event changed while editing"
            PowerStore.DirectEditOutcome.Locked -> lastError = "Editor is no longer writable"
        }
        return outcome
    }

    fun delete(): PowerStore.DirectEditOutcome =
        store.deleteFormalEvent(eventSeq, expectedRevision, lease.token).also { outcome ->
            if (outcome is PowerStore.DirectEditOutcome.Invalid) lastError = outcome.reason
        }

    override fun close() {
        store.endHumanEdit(lease.token)
    }

    companion object {
        fun open(store: PowerStore, record: TemporalRecord): EditSession {
            val lease = store.beginHumanEdit(record.eventSeq)
            val formal = DraftForm.from(record)
            // An existing checkpoint resumes automatically, but the formal snapshot remains the
            // semantic baseline for undo/no-change detection and optimistic revision checks.
            val resumed = store.pendingDraftsFor(record.eventSeq).firstOrNull()?.form ?: formal
            return EditSession(store, record.eventSeq, record.revision, lease, formal, resumed)
        }
    }
}

/**
 * An ordinary new-event editor starts as memory only. It gets a permanent event sequence and a
 * queue position only when a meaningful edit is checkpointed, or when a complete event is
 * explicitly published. Unlike [EditSession], it has no formal-event lease or revision to guard.
 */
class NewEventEditSession private constructor(
    val idempotencyKey: String,
    val clientDraftId: Long,
    val defaults: EditorDefaultsSnapshot,
    val baseline: DraftForm,
    initialForm: DraftForm
) {
    var form: DraftForm = initialForm
        private set
    var lastError: String? = null
        private set
    private var persisted: DraftCard? = null
    private var closed = false
    private var lastClosedCard: DraftCard? = null
    private var savedOutcome: PowerStore.CommitOutcome? = null

    val meaningfulDiff: Boolean get() = form.hasMeaningfulDiffFrom(baseline)
    val persistedCard: DraftCard? get() = persisted
    val isClosed: Boolean get() = closed
    val version: Long get() = persisted?.draftVersion ?: 0L

    /** A stable, session-local projection for Web skins; it is never inserted into PowerStore. */
    fun previewCard(): DraftCard = DraftCard(
        draftId = clientDraftId,
        eventSeq = persisted?.eventSeq ?: 0L,
        queueOrder = persisted?.queueOrder ?: 0L,
        operation = DraftOperation.CREATE,
        form = form,
        status = DraftStatus.PENDING,
        baseRevision = null,
        draftVersion = version
    )

    @Synchronized
    fun edit(next: DraftForm) {
        check(!closed) { "New-event editor is already closed" }
        form = next
        lastError = null
    }

    /**
     * Durable recovery checkpoint. Repeated callbacks with unchanged content return the same card
     * without incrementing its version or queue order. A clean session is strictly read-only.
     */
    @Synchronized
    fun checkpoint(store: PowerStore): DraftCard? {
        if (closed) return lastClosedCard
        try {
            if (!meaningfulDiff) {
                clearPreviouslyCheckpointedNoOp(store)
                return null
            }
            val old = persisted
            if (old == null) {
                val created = store.newDraft(form, actor = "user")
                persisted = created
                lastError = null
                return created
            }
            if (!form.hasMeaningfulDiffFrom(old.form)) return old
            return when (val result = store.stageDraft(old.draftId, form, old.draftVersion)) {
                is PowerStore.StageResult.Saved -> store.draft(old.draftId)?.also {
                    persisted = it
                    lastError = null
                } ?: fail("草稿暂存后无法重新读取")
                is PowerStore.StageResult.VersionMismatch -> fail("草稿已被其他操作更新，请重新打开后继续")
                PowerStore.StageResult.Locked -> fail("待处理卡组正由 AI 独占，修改仍保留在编辑器中")
                PowerStore.StageResult.Missing -> fail("已暂存草稿不存在，修改仍保留在编辑器中")
                PowerStore.StageResult.AlreadyFinal -> fail("草稿已处理，修改仍保留在编辑器中")
                PowerStore.StageResult.NotAuthorized -> fail("当前编辑会话无权暂存此草稿")
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            lastError = error.message ?: error.javaClass.simpleName
            throw error
        }
    }

    /** Explicit save: publish complete content directly; keep incomplete content as one draft. */
    @Synchronized
    fun save(store: PowerStore): SaveOutcome {
        savedOutcome?.let { return SaveOutcome.Published(it) }
        check(!closed) { "New-event editor is already closed" }
        if (!meaningfulDiff) {
            checkpoint(store)
            return SaveOutcome.NoChanges
        }
        when (val built = form.build(1L, 1L, defaults.nowMillis)) {
            is FormBuild.Incomplete -> return stageForContinuation(store)
            is FormBuild.Invalid -> return stageForContinuation(store)
            is FormBuild.Ready -> Unit
        }

        val outcome = if (persisted == null) {
            store.createFormalEvent(form, idempotencyKey)
        } else {
            val card = checkpoint(store) ?: return SaveOutcome.NoChanges
            store.commit(card.draftId, actor = "user", idempotencyKey = idempotencyKey)
        }
        lastError = when (outcome) {
            is PowerStore.CommitOutcome.Committed -> null
            is PowerStore.CommitOutcome.Invalid -> outcome.reason
            is PowerStore.CommitOutcome.Conflict -> "新建事件发生冲突，请重新检查内容"
            is PowerStore.CommitOutcome.NeedsRestore -> "新建事件需要恢复确认"
            PowerStore.CommitOutcome.Locked -> "待处理卡组正由 AI 独占，修改仍保留在编辑器中"
            PowerStore.CommitOutcome.NotAuthorized -> "当前编辑会话无权发布此事件"
        }
        if (outcome is PowerStore.CommitOutcome.Committed) {
            savedOutcome = outcome
            closed = true
        }
        return SaveOutcome.Published(outcome)
    }

    /** × persists one meaningful draft but never publishes it. Re-entry is idempotent. */
    @Synchronized
    fun close(store: PowerStore): DraftCard? {
        if (closed) return lastClosedCard
        val card = checkpoint(store)
        closed = true
        lastClosedCard = card
        return card
    }

    /** Explicit discard remains separate from ×; unsaved memory is simply forgotten. */
    @Synchronized
    fun discard(store: PowerStore): Boolean {
        if (closed) return true
        val card = persisted
        val discarded = card == null || store.discard(card.draftId)
        if (!discarded) {
            lastError = "草稿暂时无法丢弃，请重试"
            return false
        }
        closed = true
        lastClosedCard = null
        return true
    }

    private fun stageForContinuation(store: PowerStore): SaveOutcome =
        try {
            val card = checkpoint(store)
            if (card == null) SaveOutcome.NoChanges else SaveOutcome.Staged(card)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            lastError = error.message ?: error.javaClass.simpleName
            SaveOutcome.Failed(lastError.orEmpty())
        }

    private fun clearPreviouslyCheckpointedNoOp(store: PowerStore) {
        val card = persisted ?: return
        check(store.discard(card.draftId)) { "已检查点的草稿暂时无法清除" }
        persisted = null
    }

    private fun fail(message: String): Nothing {
        lastError = message
        throw IllegalStateException(message)
    }

    sealed interface SaveOutcome {
        data class Published(val outcome: PowerStore.CommitOutcome) : SaveOutcome
        data class Staged(val card: DraftCard) : SaveOutcome
        data class Failed(val message: String) : SaveOutcome
        data object NoChanges : SaveOutcome
    }

    companion object {
        fun open(
            kind: TemporalKind,
            clock: Clock = Clock.systemUTC(),
            zone: ZoneId = ZoneId.systemDefault(),
            selection: EditorSelection? = null
        ): NewEventEditSession {
            val defaults = EditorDefaults.capture(clock, zone)
            val initial = defaults.form(kind, selection)
            val clientId = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
                .coerceAtLeast(1L)
            return NewEventEditSession(
                idempotencyKey = UUID.randomUUID().toString(),
                clientDraftId = clientId,
                defaults = defaults,
                baseline = initial,
                initialForm = initial
            )
        }
    }
}
