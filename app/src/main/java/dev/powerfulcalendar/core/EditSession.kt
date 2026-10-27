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
    private var formalRecord: TemporalRecord,
    private var baseline: DraftForm,
    private var scopeBaseline: DraftForm,
    initial: DraftForm,
    val selectedOccurrenceKey: String?
) : AutoCloseable {
    var form: DraftForm = initial
        private set
    var lastError: String? = null
        private set

    val meaningfulDiff: Boolean
        get() = form.hasMeaningfulDiffFrom(scopeBaseline)
    val baselineForm: DraftForm get() = baseline
    val recurrenceScope: RecurrenceEditScope? get() = form.recurrenceEditScope
    val requiresRecurrenceScope: Boolean get() = formalRecord.recurrence.isRepeating
    val canEditSelectedOccurrence: Boolean get() = selectedOccurrenceKey != null

    fun edit(next: DraftForm) {
        form = next
        lastError = null
        store.touchHumanEdit(lease.token)
    }

    fun discardEditingChanges() {
        form = scopeBaseline.withoutEditTarget().copy(
            recurrenceEditScope = recurrenceScope,
            editOccurrenceKey = if (recurrenceScope == RecurrenceEditScope.WHOLE_SERIES) null
                else selectedOccurrenceKey
        )
        lastError = null
    }

    /** The scope is chosen before fields are exposed; changing it never implies a default save. */
    fun selectRecurrenceScope(scope: RecurrenceEditScope) {
        check(requiresRecurrenceScope) { "This event does not repeat" }
        check(!meaningfulDiff) { "先放弃当前字段修改，再切换重复编辑范围" }
        if (scope != RecurrenceEditScope.WHOLE_SERIES) {
            check(selectedOccurrenceKey != null) { "打开此重复实例后才能选择单次或后续范围" }
        }
        val seed = if (scope == RecurrenceEditScope.WHOLE_SERIES) {
            baseline
        } else {
            occurrenceForm(formalRecord, requireNotNull(selectedOccurrenceKey))
        }
        scopeBaseline = seed.withoutEditTarget()
        form = seed.withoutEditTarget().copy(
            recurrenceEditScope = scope,
            editOccurrenceKey = if (scope == RecurrenceEditScope.WHOLE_SERIES) null
                else selectedOccurrenceKey
        )
        lastError = null
    }

    fun heartbeat(): Boolean = store.touchHumanEdit(lease.token)

    /** One durable checkpoint for an explicit exit/switch/ON_STOP boundary. */
    fun checkpoint(): DraftCard? = try {
        if (requiresRecurrenceScope && recurrenceScope == null && meaningfulDiff) {
            error("请先选择重复事件的编辑范围")
        }
        // A scope choice alone is not a durable edit. Passing the formal baseline here also lets
        // the store clear a previous checkpoint when the user reverted all business fields.
        val checkpointForm = if (meaningfulDiff) form else baseline
        store.checkpointFormalEdit(
            eventSeq, expectedRevision, baseline, checkpointForm, lease.token
        )
    } catch (error: Exception) {
        lastError = error.message ?: error.javaClass.simpleName
        null
    }

    /** Save remains on this session/card. A no-op save clears an old checkpoint without revision. */
    fun save(): PowerStore.DirectEditOutcome {
        if (requiresRecurrenceScope && recurrenceScope == null) {
            return invalid("请先选择重复事件的编辑范围")
        }
        if (recurrenceScope == RecurrenceEditScope.THIS_AND_FUTURE) {
            return invalid("此范围请使用重复序列拆分保存")
        }
        val candidate = if (recurrenceScope == RecurrenceEditScope.SINGLE_OCCURRENCE) {
            singleOccurrenceCandidate() ?: return PowerStore.DirectEditOutcome.Invalid(
                lastError ?: "单次实例修改无效"
            )
        } else {
            form.withoutEditTarget()
        }
        val outcome = store.saveFormalEdit(eventSeq, expectedRevision, candidate, lease.token)
        when (outcome) {
            is PowerStore.DirectEditOutcome.Saved -> {
                expectedRevision = outcome.revision
                formalRecord = store.get(eventSeq) ?: formalRecord
                baseline = DraftForm.from(formalRecord)
                val scope = recurrenceScope
                scopeBaseline = if (scope == RecurrenceEditScope.SINGLE_OCCURRENCE) {
                    occurrenceForm(formalRecord, requireNotNull(selectedOccurrenceKey))
                } else baseline
                form = scopeBaseline.withoutEditTarget().copy(
                    recurrenceEditScope = scope,
                    editOccurrenceKey = if (scope == RecurrenceEditScope.WHOLE_SERIES) null
                        else selectedOccurrenceKey
                )
                lastError = null
            }
            is PowerStore.DirectEditOutcome.Invalid -> lastError = outcome.reason
            is PowerStore.DirectEditOutcome.Conflict -> lastError = "Event changed while editing"
            PowerStore.DirectEditOutcome.Locked -> lastError = "Editor is no longer writable"
        }
        return outcome
    }

    /** Apply an explicitly selected this-and-future edit through the store's atomic split. */
    fun saveThisAndFuture(): PowerStore.RecurrenceSplitOutcome {
        if (recurrenceScope != RecurrenceEditScope.THIS_AND_FUTURE) {
            return PowerStore.RecurrenceSplitOutcome.Invalid("请先选择“本次及以后”范围")
        }
        if (!meaningfulDiff) {
            return PowerStore.RecurrenceSplitOutcome.Invalid("内容没有有效修改，未拆分重复序列")
        }
        val key = selectedOccurrenceKey
            ?: return PowerStore.RecurrenceSplitOutcome.Invalid("缺少所选实例的原始时间槽键")
        val outcome = store.saveThisAndFutureRecurringEdit(
            eventSeq, expectedRevision, key, form.withoutEditTarget(), lease.token
        )
        lastError = when (outcome) {
            is PowerStore.RecurrenceSplitOutcome.Applied -> null
            is PowerStore.RecurrenceSplitOutcome.Conflict -> "重复事件已被其他操作更新，请重新打开后确认"
            is PowerStore.RecurrenceSplitOutcome.Invalid -> outcome.reason
            PowerStore.RecurrenceSplitOutcome.Locked -> "编辑器当前不可写"
        }
        return outcome
    }

    private fun singleOccurrenceCandidate(): DraftForm? {
        val key = selectedOccurrenceKey ?: return failCandidate("缺少所选实例的原始时间槽键")
        val current = form.withoutEditTarget()
        val scopedBase = scopeBaseline.withoutEditTarget()
        val unsupportedChanges = current.copy(
            title = scopedBase.title,
            startText = scopedBase.startText,
            endText = scopedBase.endText
        )
        if (unsupportedChanges.hasMeaningfulDiffFrom(scopedBase)) {
            return failCandidate("仅本次目前支持修改标题和时间；地点、备注、提醒及重复规则需选择整组或本次及以后")
        }
        val edited = when (val built = current.build(eventSeq, expectedRevision + 1L)) {
            is FormBuild.Incomplete -> return failCandidate(built.message)
            is FormBuild.Invalid -> return failCandidate(built.reason)
            is FormBuild.Ready -> built.record
        }
        val start = edited.anchorMillis()
            ?: return failCandidate("单次实例缺少有效日期或开始时刻")
        val end = if (edited.kind == TemporalKind.ACTIVITY) edited.endMillis else null
        val titleOverride = edited.title.takeIf { it != formalRecord.title }
        val naturalRecord = formalRecord.copy(
            recurrence = formalRecord.recurrence.copy(
                overrides = formalRecord.recurrence.overrides - key
            )
        )
        val natural = RecurrenceEngine.occurrenceForKey(naturalRecord, key)
            ?: return failCandidate("所选实例已跳过或不属于当前重复规则")
        val overrides = formalRecord.recurrence.overrides.toMutableMap()
        if (start == natural.startMillis && end == natural.endMillis && titleOverride == null) {
            overrides.remove(key)
        } else {
            overrides[key] = OccurrenceOverride(start, end, titleOverride)
        }
        return baseline.copy(
            recurrence = formalRecord.recurrence.copy(overrides = overrides),
            recurrenceEditScope = null,
            editOccurrenceKey = null
        )
    }

    private fun failCandidate(message: String): DraftForm? {
        lastError = message
        return null
    }

    private fun invalid(message: String): PowerStore.DirectEditOutcome.Invalid {
        lastError = message
        return PowerStore.DirectEditOutcome.Invalid(message)
    }

    fun delete(): PowerStore.DirectEditOutcome =
        store.deleteFormalEvent(eventSeq, expectedRevision, lease.token).also { outcome ->
            if (outcome is PowerStore.DirectEditOutcome.Invalid) lastError = outcome.reason
        }

    override fun close() {
        store.endHumanEdit(lease.token)
    }

    companion object {
        private fun DraftForm.withoutEditTarget(): DraftForm = copy(
            recurrenceEditScope = null,
            editOccurrenceKey = null
        )

        private fun occurrenceForm(record: TemporalRecord, key: String): DraftForm {
            val slot = requireNotNull(RecurrenceEngine.occurrenceForKey(record, key)) {
                "所选实例已跳过或不属于当前重复规则"
            }
            val overrideTitle = record.recurrence.overrides[key]?.title
            val base = DraftForm.from(record).copy(title = overrideTitle ?: record.title)
            return when (record.kind) {
                TemporalKind.ALARM -> base.copy(
                    startText = DraftForm.formatInstant(slot.startMillis, record.zone)
                )
                TemporalKind.ACTIVITY -> base.copy(
                    startText = DraftForm.formatInstant(slot.startMillis, record.zone),
                    endText = DraftForm.formatInstant(slot.endMillis, record.zone)
                )
                TemporalKind.DEADLINE -> base.copy(
                    endText = if (record.isDateOnlyDeadline()) {
                        java.time.Instant.ofEpochMilli(slot.startMillis).atZone(record.zone)
                            .toLocalDate().toString()
                    } else DraftForm.formatInstant(slot.startMillis, record.zone)
                )
                TemporalKind.MEMO -> base.copy(
                    startText = java.time.Instant.ofEpochMilli(slot.startMillis).atZone(record.zone)
                        .toLocalDate().toString()
                )
            }
        }

        fun open(
            store: PowerStore,
            record: TemporalRecord,
            selectedOccurrenceKey: String? = null
        ): EditSession {
            val lease = store.beginHumanEdit(record.eventSeq)
            val formal = DraftForm.from(record)
            try {
                // An existing checkpoint resumes automatically, while the formal snapshot remains
                // the optimistic baseline. Scope/key metadata is part of the durable draft payload.
                val pending = store.pendingDraftsFor(record.eventSeq).firstOrNull()
                val stored = pending?.form
                val storedScope = stored?.recurrenceEditScope
                val targetKey = stored?.editOccurrenceKey ?: selectedOccurrenceKey
                val scope = storedScope ?: if (pending != null && record.recurrence.isRepeating) {
                    // Older checkpoints were whole-series edits; preserve that established meaning.
                    RecurrenceEditScope.WHOLE_SERIES
                } else null
                val seed = if (scope == RecurrenceEditScope.SINGLE_OCCURRENCE ||
                    scope == RecurrenceEditScope.THIS_AND_FUTURE
                ) {
                    requireNotNull(targetKey) { "重复范围草稿缺少原始实例键" }
                    occurrenceForm(record, targetKey)
                } else formal
                val resumed = (stored ?: seed).copy(
                    recurrenceEditScope = scope,
                    editOccurrenceKey = if (scope == RecurrenceEditScope.WHOLE_SERIES) null
                        else targetKey
                )
                val initialScopeBaseline = if (scope == RecurrenceEditScope.SINGLE_OCCURRENCE ||
                    scope == RecurrenceEditScope.THIS_AND_FUTURE
                ) seed else formal
                return EditSession(
                    store, record.eventSeq, record.revision, lease, record, formal,
                    initialScopeBaseline, resumed, targetKey
                )
            } catch (error: Exception) {
                store.endHumanEdit(lease.token)
                throw error
            }
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
            // This session-local ID crosses the WebSkin JSON bridge as a JavaScript Number.
            // Keep it within Number.MAX_SAFE_INTEGER so a patch round-trip cannot change it.
            val clientId = (UUID.randomUUID().mostSignificantBits and MAX_SAFE_WEB_INTEGER)
                .coerceAtLeast(1L)
            return NewEventEditSession(
                idempotencyKey = UUID.randomUUID().toString(),
                clientDraftId = clientId,
                defaults = defaults,
                baseline = initial,
                initialForm = initial
            )
        }

        private const val MAX_SAFE_WEB_INTEGER = 9_007_199_254_740_991L
    }
}
