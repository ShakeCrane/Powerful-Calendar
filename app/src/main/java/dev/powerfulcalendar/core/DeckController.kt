package dev.powerfulcalendar.core

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
 * Every mutating entry point is synchronized: the UI edits on the main thread while durable saves
 * and commits run on Dispatchers.IO, and a read-modify-write on [state] must not interleave.
 *
 * The controller never talks to SQLite directly: [DeckPersistence] is the seam tests replace.
 */
interface DeckPersistence {
    fun pending(): List<DraftCard>
    fun card(draftId: Long): DraftCard?
    fun stage(draftId: Long, form: DraftForm, expectedVersion: Long): PowerStore.StageResult
    fun commit(draftId: Long, approveStale: Boolean): PowerStore.CommitOutcome
    fun discard(draftId: Long): Boolean
    fun undo(draftId: Long): Boolean
    fun pendingCount(): Int
    fun stats(sinceMillis: Long): PowerStore.DeckStats
    fun isAiLocked(): Boolean
    fun restore(eventSeq: Long, form: DraftForm): PowerStore.CommitOutcome
}

class DeckController(
    private val persistence: DeckPersistence,
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class ConflictPrompt(
        val draftId: Long,
        val eventSeq: Long,
        val current: TemporalRecord?,
        val changes: List<FieldChange>,
        val card: DraftCard
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
        val completion: Completion? = null
    ) {
        val position: Int get() = cards.indexOfFirst { it.draftId == currentId }.coerceAtLeast(0)
        val canGoPrevious: Boolean get() = position > 0
        val canGoNext: Boolean get() = position < cards.lastIndex
        val isEmpty: Boolean get() = cards.isEmpty()
    }

    @Volatile
    var state: DeckState = DeckState()
        private set

    private var listener: ((DeckState) -> Unit)? = null
    private val openedAt: Long = clock()
    private var undoDraftId: Long? = null

    fun observe(block: (DeckState) -> Unit) {
        listener = block
    }

    private fun publish(next: DeckState) {
        state = next
        listener?.invoke(next)
    }

    @Synchronized
    fun open(preferDraftId: Long? = null) {
        undoDraftId = null
        refreshInternal(preferDraftId)
    }

    /**
     * Reloads the queue without stealing focus: a new AI card appended at the tail must not move
     * the card the user is currently editing (I-14).
     */
    @Synchronized
    fun refresh(preferDraftId: Long? = null) {
        refreshInternal(preferDraftId)
    }

    private fun refreshInternal(preferDraftId: Long?) {
        val cards = persistence.pending()
        val aiLocked = persistence.isAiLocked()
        val targetId = preferDraftId
            ?: state.currentId?.takeIf { id -> cards.any { it.draftId == id } }
            ?: cards.firstOrNull()?.draftId
        val card = cards.firstOrNull { it.draftId == targetId }
        val keepDirtyForm = state.currentId == targetId && state.dirty && card != null
        publish(
            state.copy(
                cards = cards,
                currentId = card?.draftId,
                current = card,
                form = when {
                    card == null -> null
                    keepDirtyForm -> state.form
                    else -> card.form
                },
                version = when {
                    card == null -> 0L
                    keepDirtyForm -> state.version
                    else -> card.draftVersion
                },
                dirty = keepDirtyForm,
                aiLocked = aiLocked,
                completion = if (state.completion != null &&
                    (cards.isNotEmpty() || aiLocked || persistence.pendingCount() > 0)
                ) {
                    null
                } else {
                    state.completion
                }
            )
        )
    }

    @Synchronized
    fun edit(form: DraftForm) {
        if (state.aiLocked) {
            publish(state.copy(error = "AI 批量处理期间不能编辑卡片"))
            return
        }
        publish(state.copy(form = form, dirty = true, error = null))
    }

    /** Durably stages the latest text. Returns false when the caller must not navigate away. */
    @Synchronized
    fun flush(): Boolean {
        val id = state.currentId ?: return true
        val form = state.form ?: return true
        if (!state.dirty) return true
        if (state.aiLocked) {
            publish(state.copy(error = "AI 正独占卡组，稍后可继续编辑"))
            return false
        }
        return stageWithRetry(id, form)
    }

    private fun stageWithRetry(draftId: Long, formToSave: DraftForm): Boolean {
        var version = state.version
        repeat(2) { attempt ->
            when (val result = persistence.stage(draftId, formToSave, version)) {
                is PowerStore.StageResult.Saved -> {
                    // If the user kept typing while the write was in flight, the newest text is
                    // still dirty and is staged by the next debounce tick.
                    val stillDirty = state.form != formToSave
                    publish(
                        state.copy(
                            version = result.draftVersion,
                            dirty = stillDirty,
                            saving = false,
                            error = null
                        )
                    )
                    return true
                }
                is PowerStore.StageResult.VersionMismatch -> {
                    // Another writer advanced this card. Keep the text the user can see and land it
                    // on top of the newer row once; a second mismatch is surfaced, never looped.
                    version = result.current.draftVersion
                    publish(
                        state.copy(
                            version = version,
                            saving = false,
                            error = if (attempt == 0) null else "草稿已被其他操作更新，请检查后重试"
                        )
                    )
                }
                PowerStore.StageResult.Locked -> {
                    publish(state.copy(saving = false, error = "AI 正独占卡组，修改尚未保存"))
                    return false
                }
                PowerStore.StageResult.Missing -> {
                    publish(state.copy(saving = false, error = "草稿已不存在，请刷新"))
                    return false
                }
                PowerStore.StageResult.AlreadyFinal -> {
                    publish(state.copy(saving = false, dirty = false))
                    return false
                }
                PowerStore.StageResult.NotAuthorized -> {
                    publish(state.copy(saving = false, error = "AI 批次未获授权，修改未保存"))
                    return false
                }
            }
        }
        publish(state.copy(saving = false, error = "草稿保存失败，请重试"))
        return false
    }

    @Synchronized
    fun select(draftId: Long): Boolean {
        if (draftId == state.currentId) return true
        if (!flush()) return false
        val card = persistence.card(draftId) ?: run {
            refreshInternal(null)
            return false
        }
        if (!card.isActionable) {
            refreshInternal(null)
            return false
        }
        publish(
            state.copy(
                currentId = card.draftId,
                current = card,
                form = card.form,
                version = card.draftVersion,
                dirty = false,
                error = null,
                notice = null
            )
        )
        return true
    }

    /** Exactly one card per inside swipe. Right/previous is -1, left/next is +1. */
    @Synchronized
    fun move(delta: Int): Boolean {
        if (delta == 0) return true
        if (!flush()) return false
        val cards = state.cards
        if (cards.isEmpty()) return false
        val index = cards.indexOfFirst { it.draftId == state.currentId }
        if (index < 0) {
            refreshInternal(null)
            return false
        }
        val target = (index + delta).coerceIn(0, cards.lastIndex)
        if (target == index) return true
        val card = cards[target]
        publish(
            state.copy(
                currentId = card.draftId,
                current = card,
                form = card.form,
                version = card.draftVersion,
                dirty = false,
                error = null,
                notice = null
            )
        )
        return true
    }

    /** Fling/outside drag: still flush once, then jump (possibly several cards) in one gesture. */
    @Synchronized
    fun jump(steps: Int): Boolean {
        if (steps == 0) return true
        if (!flush()) return false
        val cards = state.cards
        if (cards.isEmpty()) return false
        val index = cards.indexOfFirst { it.draftId == state.currentId }
        if (index < 0) return false
        val target = (index + steps).coerceIn(0, cards.lastIndex)
        val card = cards[target]
        publish(
            state.copy(
                currentId = card.draftId,
                current = card,
                form = card.form,
                version = card.draftVersion,
                dirty = false,
                error = null
            )
        )
        return true
    }

    @Synchronized
    fun commit(approveStale: Boolean = false): Boolean {
        val id = state.currentId ?: return false
        if (state.aiLocked) {
            publish(state.copy(error = "AI 批量处理期间不能发布卡片"))
            return false
        }
        if (!flush()) return false
        val order = state.current?.queueOrder ?: Long.MAX_VALUE
        return when (val outcome = persistence.commit(id, approveStale)) {
            is PowerStore.CommitOutcome.Committed -> {
                publish(state.copy(conflict = null, restore = null, dirty = false))
                afterRemoval(order)
                true
            }
            is PowerStore.CommitOutcome.Conflict -> {
                val prompt = ConflictPrompt(
                    draftId = id,
                    eventSeq = state.current?.eventSeq ?: 0L,
                    current = outcome.current,
                    changes = DraftDiff.between(
                        outcome.current,
                        state.form ?: DraftForm(TemporalKind.ACTIVITY),
                        state.current?.eventSeq ?: 0L
                    ),
                    card = state.current ?: return false
                )
                publish(state.copy(conflict = prompt, error = null))
                false
            }
            is PowerStore.CommitOutcome.NeedsRestore -> {
                val card = state.current
                if (card == null) false else {
                    publish(
                        state.copy(
                            restore = RestorePrompt(
                                eventSeq = outcome.retiredSeq,
                                card = card,
                                message = "事件 #${outcome.retiredSeq} 已被删除。恢复会复用同一序号并生成新版本，不会静默复活。"
                            )
                        )
                    )
                    false
                }
            }
            is PowerStore.CommitOutcome.Invalid -> {
                publish(state.copy(error = outcome.reason))
                false
            }
            PowerStore.CommitOutcome.Locked -> {
                publish(state.copy(error = "AI 正独占卡组，发布被拒绝"))
                false
            }
            PowerStore.CommitOutcome.NotAuthorized -> {
                publish(state.copy(error = "未获授权的 AI 写入被拒绝"))
                false
            }
        }
    }

    /** User explicitly approved replacing the now-current revision with this card's snapshot. */
    @Synchronized
    fun confirmConflictReplacement(): Boolean {
        val prompt = state.conflict ?: return false
        if (prompt.draftId != state.currentId) {
            publish(state.copy(conflict = null, error = "冲突卡片已切换，请重新检查"))
            return false
        }
        val result = commit(approveStale = true)
        publish(state.copy(conflict = null))
        return result
    }

    @Synchronized
    fun dismissConflict() {
        publish(state.copy(conflict = null))
    }

    @Synchronized
    fun requestRestore(): Boolean {
        val prompt = state.restore ?: return false
        val form = state.form ?: return false
        return when (val outcome = persistence.restore(prompt.eventSeq, form)) {
            is PowerStore.CommitOutcome.Committed -> {
                publish(
                    state.copy(
                        restore = null,
                        notice = "已恢复事件 #${prompt.eventSeq} 并生成 v${outcome.revision}"
                    )
                )
                true
            }
            is PowerStore.CommitOutcome.Invalid -> {
                publish(state.copy(restore = null, error = outcome.reason))
                false
            }
            else -> {
                publish(state.copy(restore = null, error = "恢复失败，草稿已保留"))
                false
            }
        }
    }

    @Synchronized
    fun dismissRestore() {
        publish(state.copy(restore = null))
    }

    /** X: discard the proposal. Retention keeps the row so undo can restore the same queue slot. */
    @Synchronized
    fun discard(): Boolean {
        val id = state.currentId ?: return false
        if (state.aiLocked) {
            publish(state.copy(error = "AI 批量处理期间不能丢弃卡片"))
            return false
        }
        if (state.dirty && !flush()) return false
        val order = state.current?.queueOrder ?: Long.MAX_VALUE
        if (!persistence.discard(id)) {
            publish(state.copy(error = "丢弃失败，草稿已保留"))
            return false
        }
        undoDraftId = id
        afterRemoval(order)
        return true
    }

    @Synchronized
    fun undoDiscard(): Boolean {
        val id = undoDraftId ?: return false
        if (!persistence.undo(id)) return false
        undoDraftId = null
        // Undoing the last action cancels a completion card that claimed the queue was finished.
        publish(state.copy(completion = null, notice = "已撤销丢弃"))
        refreshInternal(id)
        return true
    }

    private fun afterRemoval(removedOrder: Long) {
        val remaining = persistence.pending()
        val next = remaining.firstOrNull { it.queueOrder > removedOrder } ?: remaining.firstOrNull()
        val stats = persistence.stats(openedAt)
        val aiLocked = persistence.isAiLocked()
        val finished = remaining.isEmpty() && !aiLocked && persistence.pendingCount() == 0
        publish(
            state.copy(
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
                completion = when {
                    finished -> Completion(stats.published, stats.discarded, stats.conflicts)
                    else -> null
                }
            )
        )
    }

    @Synchronized
    fun dismissCompletion() {
        publish(state.copy(completion = null))
    }

    @Synchronized
    fun clearError() {
        publish(state.copy(error = null, notice = null))
    }

    @Synchronized
    fun setAiLocked(locked: Boolean) {
        publish(state.copy(aiLocked = locked))
    }

    fun updateAiLock(locked: Boolean) = setAiLocked(locked)
}
