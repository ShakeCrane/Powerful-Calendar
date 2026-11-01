package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftSourceRevisionMismatchException
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.NewEventEditSession
import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/** Android/Core implementation adapter. All exposed values come from the pure :core-api module. */
class PowerCalCoreFeatureApiV1(
    store: PowerStore,
    clock: Clock = Clock.systemUTC()
) : CoreFeatureApiV1 {
    override val apiVersion: Int = 1
    private val eventService = StoreEventFeatureApiV1(store, clock)
    override val events: EventFeatureApiV1 get() = eventService

    /** Called only by trusted Host code after a user-confirmation surface has accepted deletion. */
    internal fun issueFormalDeleteApproval(handle: EventEditorHandleV1): EventDeleteApprovalV1? =
        eventService.issueFormalDeleteApproval(handle)
}

private class StoreEventFeatureApiV1(
    private val store: PowerStore,
    private val clock: Clock
) : EventFeatureApiV1 {
    override val apiVersion: Int = 1

    private val revision = AtomicLong(0L)
    private val listeners = CopyOnWriteArraySet<(EventFeatureChangeV1) -> Unit>()
    private val formalSessions = LinkedHashMap<String, EditSession>()
    private val newSessions = LinkedHashMap<String, NewEventEditSession>()
    private val approvals = IdentityHashMap<EventDeleteApprovalV1, ApprovalClaims>()
    private val receipts = LinkedHashMap<String, Pair<String, EventMutationReceiptV1>>()
    private val checkpointReceipts = LinkedHashMap<String, Pair<String, EventCheckpointReceiptV1>>()

    override val featureRevision: Long get() = revision.get()

    @Synchronized
    override fun queryEvents(): EventQuerySnapshotV1 = EventQuerySnapshotV1(
        featureRevision = featureRevision,
        events = store.all().map(TemporalRecord::toEventRecordV1)
    )

    @Synchronized
    override fun openNewEvent(
        kind: EventKindV1,
        zoneId: String,
        selection: EventSelectionV1?
    ): EventEditorOpenResultV1 {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrElse {
            return EventEditorOpenResultV1.Invalid("ZONE_INVALID", "时区标识无效")
        }
        val instantMillis = selection?.instantMillis
        val localDate = selection?.localDate
        val editorSelection = when {
            selection == null -> null
            instantMillis != null -> EditorSelection.AtInstant(instantMillis)
            localDate != null -> runCatching {
                EditorSelection.OnDate(LocalDate.parse(localDate))
            }.getOrElse {
                return EventEditorOpenResultV1.Invalid("DATE_INVALID", "所选日期无效")
            }
            else -> null
        }
        return runCatching {
            val session = NewEventEditSession.open(kind.toCoreKind(), clock, zone, editorSelection)
            val handle = EventEditorHandleV1(UUID.randomUUID().toString())
            newSessions[handle.value] = session
            EventEditorOpenResultV1.Opened(
                EventEditorSnapshotV1(
                    handle = handle,
                    eventSeq = null,
                    expectedRevision = null,
                    clientDraftId = session.clientDraftId,
                    draftVersion = session.version,
                    form = session.form.toEventFormV1(),
                    baselineForm = session.form.toEventFormV1(),
                    recurrenceScopeRequired = false,
                    selectedOccurrenceKey = null,
                    defaultNowMillis = session.defaults.nowMillis,
                    meaningfulChanges = session.meaningfulDiff,
                    hasPersistedDraft = session.persistedCard != null,
                    lastError = session.lastError,
                    capabilities = setOf(
                        EventCapabilityV1.CREATE,
                        EventCapabilityV1.MOVE_TO_DRAFT
                    )
                )
            )
        }.getOrElse {
            EventEditorOpenResultV1.Invalid(
                "EDITOR_OPEN_FAILED", it.message ?: "无法打开新建事件编辑器"
            )
        }
    }

    @Synchronized
    override fun openFormalEvent(
        eventSeq: Long,
        expectedRevision: Long,
        occurrenceKey: String?
    ): EventEditorOpenResultV1 {
        if (eventSeq <= 0L || expectedRevision <= 0L) {
            return EventEditorOpenResultV1.Invalid("INVALID_IDENTITY", "事件身份或版本无效")
        }
        if (store.isLocked()) return EventEditorOpenResultV1.Locked
        val current = store.get(eventSeq) ?: return EventEditorOpenResultV1.Missing
        if (current.revision != expectedRevision) {
            return EventEditorOpenResultV1.Stale(current.revision)
        }
        if (occurrenceKey != null && (!current.recurrence.isRepeating ||
                RecurrenceEngine.occurrenceForKey(current, occurrenceKey) == null)
        ) {
            return EventEditorOpenResultV1.Invalid(
                "INVALID_OCCURRENCE", "所选重复实例不存在或已失效"
            )
        }
        return runCatching {
            val session = EditSession.open(store, current, occurrenceKey)
            val handle = EventEditorHandleV1(UUID.randomUUID().toString())
            formalSessions[handle.value] = session
            EventEditorOpenResultV1.Opened(
                EventEditorSnapshotV1(
                    handle = handle,
                    eventSeq = current.eventSeq,
                    expectedRevision = current.revision,
                    clientDraftId = null,
                    draftVersion = null,
                    form = session.form.toEventFormV1(),
                    baselineForm = session.baselineForm.toEventFormV1(),
                    recurrenceScopeRequired = session.requiresRecurrenceScope,
                    selectedOccurrenceKey = session.selectedOccurrenceKey,
                    defaultNowMillis = null,
                    meaningfulChanges = session.meaningfulDiff,
                    hasPersistedDraft = false,
                    lastError = session.lastError,
                    capabilities = buildSet {
                        add(EventCapabilityV1.EDIT)
                        add(EventCapabilityV1.DELETE)
                        add(EventCapabilityV1.MOVE_TO_DRAFT)
                        if (session.requiresRecurrenceScope) add(EventCapabilityV1.RECURRENCE_EDIT)
                    }
                )
            )
        }.getOrElse { error ->
            if (store.isLocked()) EventEditorOpenResultV1.Locked
            else EventEditorOpenResultV1.Invalid(
                "EDITOR_OPEN_FAILED", error.message ?: "无法打开事件编辑器"
            )
        }
    }

    @Synchronized
    override fun editorSnapshot(handle: EventEditorHandleV1): EventEditorSnapshotV1? =
        newSessions[handle.value]?.let { session ->
            EventEditorSnapshotV1(
                handle = handle,
                eventSeq = session.persistedCard?.eventSeq,
                expectedRevision = null,
                clientDraftId = session.clientDraftId,
                draftVersion = session.version,
                form = session.form.toEventFormV1(),
                baselineForm = session.baseline.toEventFormV1(),
                recurrenceScopeRequired = false,
                selectedOccurrenceKey = null,
                defaultNowMillis = session.defaults.nowMillis,
                meaningfulChanges = session.meaningfulDiff,
                hasPersistedDraft = session.persistedCard != null,
                lastError = session.lastError,
                capabilities = setOf(
                    EventCapabilityV1.CREATE,
                    EventCapabilityV1.MOVE_TO_DRAFT
                )
            )
        } ?: formalSessions[handle.value]?.let { session ->
            val currentRevision = store.get(session.eventSeq)?.revision
            EventEditorSnapshotV1(
                handle = handle,
                eventSeq = session.eventSeq,
                expectedRevision = currentRevision,
                clientDraftId = null,
                draftVersion = null,
                form = session.form.toEventFormV1(),
                baselineForm = session.baselineForm.toEventFormV1(),
                recurrenceScopeRequired = session.requiresRecurrenceScope,
                selectedOccurrenceKey = session.selectedOccurrenceKey,
                defaultNowMillis = null,
                meaningfulChanges = session.meaningfulDiff,
                hasPersistedDraft = false,
                lastError = session.lastError,
                capabilities = buildSet {
                    add(EventCapabilityV1.EDIT)
                    add(EventCapabilityV1.DELETE)
                    add(EventCapabilityV1.MOVE_TO_DRAFT)
                    if (session.requiresRecurrenceScope) add(EventCapabilityV1.RECURRENCE_EDIT)
                }
            )
        }

    @Synchronized
    override fun validateNewEvent(handle: EventEditorHandleV1): EventValidationResultV1 {
        val session = newSessions[handle.value]
            ?: return EventValidationResultV1(
                EventValidationStatusV1.NOT_FOUND,
                errorCode = "EDITOR_NOT_FOUND",
                message = "编辑器已关闭"
            )
        return when (val result = session.form.build(1L, 1L, session.defaults.nowMillis)) {
            is dev.powerfulcalendar.core.FormBuild.Ready ->
                EventValidationResultV1(EventValidationStatusV1.READY)
            is dev.powerfulcalendar.core.FormBuild.Incomplete -> EventValidationResultV1(
                EventValidationStatusV1.INCOMPLETE,
                missingFields = result.missing.map { it.name },
                errorCode = "EVENT_INCOMPLETE",
                message = result.message
            )
            is dev.powerfulcalendar.core.FormBuild.Invalid -> EventValidationResultV1(
                EventValidationStatusV1.INVALID,
                errorCode = "EVENT_INVALID",
                message = result.reason
            )
        }
    }

    @Synchronized
    override fun updateNewEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1
    ): EventEditorUpdateReceiptV1 {
        val session = newSessions[handle.value]
            ?: return updateError(EventEditorUpdateStatusV1.NOT_FOUND, "EDITOR_NOT_FOUND")
        return runCatching {
            session.edit(keepCoreOwnedFields(session.form, form.toCoreDraftForm()))
            EventEditorUpdateReceiptV1(EventEditorUpdateStatusV1.UPDATED)
        }.getOrElse {
            updateError(EventEditorUpdateStatusV1.INVALID, "EDITOR_UPDATE_FAILED", it.message)
        }
    }

    @Synchronized
    override fun updateFormalEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1
    ): EventEditorUpdateReceiptV1 {
        val session = formalSessions[handle.value]
            ?: return updateError(EventEditorUpdateStatusV1.NOT_FOUND, "EDITOR_NOT_FOUND")
        if (store.isLocked()) return updateError(EventEditorUpdateStatusV1.LOCKED, "CORE_LOCKED")
        return runCatching {
            session.edit(keepCoreOwnedFields(session.form, form.toCoreDraftForm()))
            EventEditorUpdateReceiptV1(EventEditorUpdateStatusV1.UPDATED)
        }.getOrElse {
            updateError(EventEditorUpdateStatusV1.INVALID, "EDITOR_UPDATE_FAILED", it.message)
        }
    }

    @Synchronized
    override fun stageFormalReplacement(
        eventSeq: Long,
        expectedRevision: Long,
        form: EventFormV1,
        requestId: String
    ): EventMutationReceiptV1 = withReceipt(
        requestId, "stage-replace:$eventSeq:$expectedRevision:$form"
    ) {
        try {
            val current = store.get(eventSeq)
                ?: return@withReceipt receipt(
                    requestId, EventMutationStatusV1.CONFLICT,
                    eventSeq = eventSeq, errorCode = "EVENT_MISSING", refreshRequired = true
                )
            if (current.revision != expectedRevision) {
                return@withReceipt receipt(
                    requestId, EventMutationStatusV1.CONFLICT,
                    eventSeq = eventSeq, revision = current.revision,
                    errorCode = "EVENT_CONFLICT", refreshRequired = true
                )
            }
            val safeForm = keepCoreOwnedFields(DraftForm.from(current), form.toCoreDraftForm())
            val card = store.newDraft(
                safeForm, eventSeq, DraftOperation.REPLACE,
                actor = "user", expectedRevision = expectedRevision
            )
            publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED, eventSeq, expectedRevision)
            receipt(
                requestId, EventMutationStatusV1.STAGED, eventSeq, expectedRevision,
                draftId = card.draftId, draftVersion = card.draftVersion
            )
        } catch (mismatch: DraftSourceRevisionMismatchException) {
            receipt(
                requestId, EventMutationStatusV1.CONFLICT,
                mismatch.eventSeq, mismatch.actualRevision,
                errorCode = "EVENT_CONFLICT", refreshRequired = true
            )
        } catch (error: IllegalStateException) {
            if (store.isLocked()) receipt(
                requestId, EventMutationStatusV1.LOCKED,
                eventSeq = eventSeq, errorCode = "CORE_LOCKED"
            ) else receipt(
                requestId, EventMutationStatusV1.INVALID,
                eventSeq = eventSeq, errorCode = "EVENT_INVALID", message = error.message
            )
        } catch (error: IllegalArgumentException) {
            receipt(
                requestId, EventMutationStatusV1.INVALID,
                eventSeq = eventSeq, errorCode = "EVENT_INVALID", message = error.message
            )
        } catch (error: Exception) {
            receipt(
                requestId, EventMutationStatusV1.FAILED,
                eventSeq = eventSeq, errorCode = "STAGE_FAILED", message = error.message
            )
        }
    }

    @Synchronized
    override fun selectFormalRecurrenceScope(
        handle: EventEditorHandleV1,
        scope: EventRecurrenceScopeV1
    ): EventEditorUpdateReceiptV1 {
        val session = formalSessions[handle.value]
            ?: return updateError(EventEditorUpdateStatusV1.NOT_FOUND, "EDITOR_NOT_FOUND")
        return runCatching {
            session.selectRecurrenceScope(scope.toCoreScope())
            EventEditorUpdateReceiptV1(EventEditorUpdateStatusV1.UPDATED)
        }.getOrElse {
            updateError(EventEditorUpdateStatusV1.INVALID, "RECURRENCE_SCOPE_INVALID", it.message)
        }
    }

    @Synchronized
    override fun discardFormalChanges(
        handle: EventEditorHandleV1
    ): EventEditorUpdateReceiptV1 {
        val session = formalSessions[handle.value]
            ?: return updateError(EventEditorUpdateStatusV1.NOT_FOUND, "EDITOR_NOT_FOUND")
        session.discardEditingChanges()
        return EventEditorUpdateReceiptV1(EventEditorUpdateStatusV1.UPDATED)
    }

    @Synchronized
    override fun checkpointNewEvent(
        handle: EventEditorHandleV1,
        requestId: String
    ): EventCheckpointReceiptV1 {
        val session = newSessions[handle.value] ?: return EventCheckpointReceiptV1(
            EventMutationStatusV1.INVALID, message = "编辑器已关闭"
        )
        val fingerprint = "checkpoint-new:${handle.value}:${session.form}"
        return withCheckpointReceipt(requestId, fingerprint) {
            runCatching { session.checkpoint(store) }
                .fold(
                    onSuccess = { card ->
                        if (card == null) EventCheckpointReceiptV1(EventMutationStatusV1.NO_CHANGE)
                        else {
                            publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED, card.eventSeq)
                            EventCheckpointReceiptV1(
                                EventMutationStatusV1.STAGED,
                                card.eventSeq,
                                card.draftId,
                                card.draftVersion
                            )
                        }
                    },
                    onFailure = { EventCheckpointReceiptV1(
                        EventMutationStatusV1.FAILED,
                        eventSeq = session.persistedCard?.eventSeq,
                        draftId = session.persistedCard?.draftId,
                        draftVersion = session.persistedCard?.draftVersion,
                        message = it.message ?: "无法暂存事件修改"
                    ) }
                )
        }
    }

    @Synchronized
    override fun checkpointFormalEvent(
        handle: EventEditorHandleV1,
        requestId: String
    ): EventCheckpointReceiptV1 {
        val session = formalSessions[handle.value] ?: return EventCheckpointReceiptV1(
            EventMutationStatusV1.INVALID, message = "编辑器已关闭"
        )
        val fingerprint = "checkpoint-formal:${handle.value}:${session.form}"
        return withCheckpointReceipt(requestId, fingerprint) {
            invalidRequest(requestId)?.let {
                return@withCheckpointReceipt EventCheckpointReceiptV1(
                    EventMutationStatusV1.INVALID, eventSeq = session.eventSeq, message = it
                )
            }
            val card = session.checkpoint()
            if (card == null && session.lastError != null) {
                val stale = !session.formalVersionIsCurrent()
                EventCheckpointReceiptV1(
                    if (stale) EventMutationStatusV1.CONFLICT else EventMutationStatusV1.FAILED,
                    eventSeq = session.eventSeq,
                    message = session.lastError
                )
            } else if (card == null) {
                EventCheckpointReceiptV1(EventMutationStatusV1.NO_CHANGE, eventSeq = session.eventSeq)
            } else {
                publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED, session.eventSeq)
                EventCheckpointReceiptV1(
                    EventMutationStatusV1.STAGED,
                    session.eventSeq,
                    card.draftId,
                    card.draftVersion
                )
            }
        }
    }

    @Synchronized
    override fun saveNewEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1,
        requestId: String
    ): EventMutationReceiptV1 = withReceipt(
        requestId, "create:${handle.value}:$form"
    ) {
        val session = newSessions[handle.value]
            ?: return@withReceipt receipt(
                requestId, EventMutationStatusV1.INVALID, errorCode = "EDITOR_NOT_FOUND"
            )
            updateNewEvent(handle, form).takeIf {
                it.status == EventEditorUpdateStatusV1.UPDATED
            }?.let {
                when (val saved = session.save(store)) {
                    is NewEventEditSession.SaveOutcome.Published -> when (val outcome = saved.outcome) {
                        is PowerStore.CommitOutcome.Committed -> {
                            newSessions.remove(handle.value)
                            if (!outcome.duplicate) publish(
                                EventFeatureChangeTypeV1.FORMAL_CREATED,
                                outcome.eventSeq,
                                outcome.revision
                            )
                            receipt(
                                requestId, EventMutationStatusV1.CREATED, outcome.eventSeq,
                                outcome.revision, duplicate = outcome.duplicate
                            )
                        }
                        is PowerStore.CommitOutcome.Conflict -> receipt(
                            requestId, EventMutationStatusV1.CONFLICT,
                            eventSeq = outcome.current?.eventSeq,
                            revision = outcome.current?.revision,
                            errorCode = "EVENT_CONFLICT", refreshRequired = true
                        )
                        is PowerStore.CommitOutcome.NeedsRestore -> receipt(
                            requestId, EventMutationStatusV1.NEEDS_RESTORE,
                            eventSeq = outcome.retiredSeq, errorCode = "RESTORE_REQUIRED"
                        )
                        is PowerStore.CommitOutcome.Invalid -> receipt(
                            requestId, EventMutationStatusV1.INVALID,
                            errorCode = "EVENT_INVALID", message = outcome.reason
                        )
                        PowerStore.CommitOutcome.Locked -> receipt(
                            requestId, EventMutationStatusV1.LOCKED, errorCode = "CORE_LOCKED"
                        )
                        PowerStore.CommitOutcome.NotAuthorized -> receipt(
                            requestId, EventMutationStatusV1.NOT_AUTHORIZED,
                            errorCode = "NOT_AUTHORIZED"
                        )
                    }
                    is NewEventEditSession.SaveOutcome.Staged -> {
                        publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED, saved.card.eventSeq)
                        receipt(
                            requestId, EventMutationStatusV1.STAGED, saved.card.eventSeq,
                            draftId = saved.card.draftId, draftVersion = saved.card.draftVersion
                        )
                    }
                    is NewEventEditSession.SaveOutcome.Failed -> receipt(
                        requestId, EventMutationStatusV1.FAILED,
                        errorCode = "SAVE_FAILED", message = saved.message
                    )
                    NewEventEditSession.SaveOutcome.NoChanges -> receipt(
                        requestId, EventMutationStatusV1.NO_CHANGE
                    )
                }
            } ?: receipt(
                requestId, EventMutationStatusV1.FAILED,
                errorCode = "EDITOR_UPDATE_FAILED", message = "无法更新新建事件"
            )
        }

    @Synchronized
    override fun saveFormalEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1,
        requestId: String
    ): EventMutationReceiptV1 {
        return withReceipt(requestId, "edit:${handle.value}:${form}") {
            val session = formalSessions[handle.value]
                ?: return@withReceipt receipt(
                    requestId, EventMutationStatusV1.INVALID, errorCode = "EDITOR_NOT_FOUND"
                )
            val update = updateFormalEvent(handle, form)
            if (update.status != EventEditorUpdateStatusV1.UPDATED) {
                return@withReceipt receipt(
                    requestId,
                    if (update.status == EventEditorUpdateStatusV1.LOCKED) EventMutationStatusV1.LOCKED
                    else EventMutationStatusV1.INVALID,
                    eventSeq = session.eventSeq,
                    errorCode = update.errorCode,
                    message = update.message
                )
            }
            if (session.recurrenceScope == RecurrenceEditScope.THIS_AND_FUTURE) {
                when (val outcome = session.saveThisAndFuture()) {
                    is PowerStore.RecurrenceSplitOutcome.Applied -> {
                        publish(
                            EventFeatureChangeTypeV1.FORMAL_UPDATED,
                            outcome.oldEventSeq,
                            outcome.oldRevision
                        )
                        publish(
                            EventFeatureChangeTypeV1.FORMAL_CREATED,
                            outcome.newEventSeq,
                            outcome.newRevision
                        )
                        receipt(
                            requestId, EventMutationStatusV1.UPDATED,
                            outcome.oldEventSeq, outcome.oldRevision,
                            relatedEventSeq = outcome.newEventSeq
                        )
                    }
                    is PowerStore.RecurrenceSplitOutcome.Conflict -> receipt(
                        requestId, EventMutationStatusV1.CONFLICT,
                        eventSeq = outcome.current?.eventSeq,
                        revision = outcome.current?.revision,
                        errorCode = "EVENT_CONFLICT", refreshRequired = true
                    )
                    is PowerStore.RecurrenceSplitOutcome.Invalid -> receipt(
                        requestId, EventMutationStatusV1.INVALID,
                        eventSeq = session.eventSeq,
                        errorCode = "EVENT_INVALID",
                        message = outcome.reason
                    )
                    PowerStore.RecurrenceSplitOutcome.Locked -> receipt(
                        requestId, EventMutationStatusV1.LOCKED,
                        eventSeq = session.eventSeq, errorCode = "CORE_LOCKED"
                    )
                }
            } else {
                when (val outcome = session.save()) {
                    is PowerStore.DirectEditOutcome.Saved -> {
                        if (outcome.changed) publish(
                            EventFeatureChangeTypeV1.FORMAL_UPDATED,
                            outcome.eventSeq,
                            outcome.revision
                        )
                        // A no-change save can still clear an earlier recovery checkpoint.
                        publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED, outcome.eventSeq)
                        receipt(
                            requestId,
                            if (outcome.changed) EventMutationStatusV1.UPDATED
                            else EventMutationStatusV1.NO_CHANGE,
                            outcome.eventSeq,
                            outcome.revision
                        )
                    }
                    is PowerStore.DirectEditOutcome.Conflict -> receipt(
                        requestId, EventMutationStatusV1.CONFLICT,
                        eventSeq = outcome.current?.eventSeq,
                        revision = outcome.current?.revision,
                        errorCode = "EVENT_CONFLICT", refreshRequired = true
                    )
                    is PowerStore.DirectEditOutcome.Invalid -> receipt(
                        requestId, EventMutationStatusV1.INVALID,
                        eventSeq = session.eventSeq,
                        errorCode = "EVENT_INVALID", message = outcome.reason
                    )
                    PowerStore.DirectEditOutcome.Locked -> receipt(
                        requestId, EventMutationStatusV1.LOCKED,
                        eventSeq = session.eventSeq, errorCode = "CORE_LOCKED"
                    )
                }
            }
        }
    }

    @Synchronized
    internal fun issueFormalDeleteApproval(handle: EventEditorHandleV1): EventDeleteApprovalV1? {
        val session = formalSessions[handle.value] ?: return null
        if (!session.formalVersionIsCurrent()) return null
        val current = store.get(session.eventSeq) ?: return null
        val approval = StoreEventDeleteApprovalV1(current.eventSeq, current.revision)
        approvals[approval] = ApprovalClaims(handle.value, current.eventSeq, current.revision)
        return approval
    }

    @Synchronized
    override fun deleteFormalEvent(
        handle: EventEditorHandleV1,
        approval: EventDeleteApprovalV1,
        requestId: String
    ): EventMutationReceiptV1 {
        return withReceipt(
            requestId,
            "delete:${handle.value}:${approval.eventSeq}:${approval.expectedRevision}:" +
                "${System.identityHashCode(approval)}"
        ) {
            val session = formalSessions[handle.value]
                ?: return@withReceipt receipt(
                    requestId, EventMutationStatusV1.INVALID, errorCode = "EDITOR_NOT_FOUND"
                )
            val claims = approvals[approval]
            val issued = approvals.remove(approval)
            if (issued == null || claims != issued || issued.handle != handle.value ||
                approval.eventSeq != session.eventSeq || approval.expectedRevision <= 0L
            ) {
                return@withReceipt receipt(
                    requestId, EventMutationStatusV1.NOT_AUTHORIZED,
                    eventSeq = session.eventSeq, errorCode = "DELETE_APPROVAL_INVALID"
                )
            }
            val currentRevision = store.get(session.eventSeq)?.revision
            if (currentRevision != approval.expectedRevision) {
                return@withReceipt receipt(
                    requestId, EventMutationStatusV1.CONFLICT,
                    eventSeq = session.eventSeq, revision = currentRevision,
                    errorCode = "EVENT_CONFLICT", refreshRequired = true
                )
            }
            when (val outcome = session.delete()) {
                is PowerStore.DirectEditOutcome.Saved -> {
                    if (outcome.changed) publish(
                        EventFeatureChangeTypeV1.FORMAL_DELETED,
                        outcome.eventSeq,
                        outcome.revision
                    )
                    publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED, outcome.eventSeq)
                    receipt(
                        requestId,
                        if (outcome.changed) EventMutationStatusV1.DELETED
                        else EventMutationStatusV1.NO_CHANGE,
                        outcome.eventSeq,
                        outcome.revision
                    )
                }
                is PowerStore.DirectEditOutcome.Conflict -> receipt(
                    requestId, EventMutationStatusV1.CONFLICT,
                    eventSeq = outcome.current?.eventSeq,
                    revision = outcome.current?.revision,
                    errorCode = "EVENT_CONFLICT", refreshRequired = true
                )
                is PowerStore.DirectEditOutcome.Invalid -> receipt(
                    requestId, EventMutationStatusV1.INVALID,
                    eventSeq = session.eventSeq,
                    errorCode = "EVENT_INVALID", message = outcome.reason
                )
                PowerStore.DirectEditOutcome.Locked -> receipt(
                    requestId, EventMutationStatusV1.LOCKED,
                    eventSeq = session.eventSeq, errorCode = "CORE_LOCKED"
                )
            }
        }
    }

    @Synchronized
    override fun closeNewEvent(handle: EventEditorHandleV1): EventCheckpointReceiptV1 {
        val session = newSessions[handle.value] ?: return EventCheckpointReceiptV1(
            EventMutationStatusV1.NO_CHANGE
        )
        val result = runCatching { session.close(store) }.fold(
            onSuccess = { card ->
                if (card == null) EventCheckpointReceiptV1(EventMutationStatusV1.NO_CHANGE)
                else {
                    publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED, card.eventSeq)
                    EventCheckpointReceiptV1(
                        EventMutationStatusV1.STAGED, card.eventSeq, card.draftId, card.draftVersion
                    )
                }
            },
            onFailure = {
                EventCheckpointReceiptV1(
                    EventMutationStatusV1.FAILED,
                    session.persistedCard?.eventSeq,
                    session.persistedCard?.draftId,
                    session.persistedCard?.draftVersion,
                    it.message ?: "无法保存事件恢复检查点"
                )
            }
        )
        if (result.status !in setOf(
                EventMutationStatusV1.FAILED,
                EventMutationStatusV1.CONFLICT,
                EventMutationStatusV1.INVALID
            )) newSessions.remove(handle.value)
        return result
    }

    @Synchronized
    override fun discardNewEvent(handle: EventEditorHandleV1): Boolean {
        val session = newSessions[handle.value] ?: return false
        val hadDraft = session.persistedCard != null
        val discarded = session.discard(store)
        if (discarded) newSessions.remove(handle.value)
        if (discarded && hadDraft) publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED)
        return discarded
    }

    @Synchronized
    override fun closeFormalEvent(handle: EventEditorHandleV1) {
        formalSessions.remove(handle.value)?.close()
        approvals.entries.removeIf { it.value.handle == handle.value }
    }

    @Synchronized
    override fun heartbeatFormalEvent(handle: EventEditorHandleV1): Boolean =
        formalSessions[handle.value]?.heartbeat() ?: false

    @Synchronized
    override fun isFormalEventCurrent(handle: EventEditorHandleV1): Boolean =
        formalSessions[handle.value]?.formalVersionIsCurrent() ?: false

    override fun subscribe(listener: (EventFeatureChangeV1) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    private fun keepCoreOwnedFields(current: DraftForm, proposed: DraftForm): DraftForm {
        val recurrence = proposed.recurrence.copy(
            count = current.recurrence.count,
            untilMillis = current.recurrence.untilMillis,
            byDay = current.recurrence.byDay,
            exceptions = current.recurrence.exceptions,
            overrides = current.recurrence.overrides
        )
        return proposed.copy(
            source = current.source,
            zoneId = current.zoneId,
            completedKeys = current.completedKeys,
            recurrence = recurrence,
            recurrenceEditScope = current.recurrenceEditScope,
            editOccurrenceKey = current.editOccurrenceKey
        )
    }

    private fun publish(
        type: EventFeatureChangeTypeV1,
        eventSeq: Long? = null,
        eventRevision: Long? = null
    ) {
        val change = EventFeatureChangeV1(revision.incrementAndGet(), type, eventSeq, eventRevision)
        listeners.forEach { listener -> runCatching { listener(change) } }
    }

    private fun withCheckpointReceipt(
        requestId: String,
        fingerprint: String,
        operation: () -> EventCheckpointReceiptV1
    ): EventCheckpointReceiptV1 {
        invalidRequest(requestId)?.let {
            return EventCheckpointReceiptV1(EventMutationStatusV1.INVALID, message = it)
        }
        checkpointReceipts[requestId]?.let { cached ->
            return if (cached.first == fingerprint) cached.second
            else EventCheckpointReceiptV1(
                EventMutationStatusV1.INVALID,
                message = "请求标识已用于其他检查点操作"
            )
        }
        val result = operation()
        checkpointReceipts[requestId] = fingerprint to result
        while (checkpointReceipts.size > 128) checkpointReceipts.remove(checkpointReceipts.keys.first())
        return result
    }

    private fun withReceipt(
        requestId: String,
        fingerprint: String,
        operation: () -> EventMutationReceiptV1
    ): EventMutationReceiptV1 {
        invalidRequest(requestId)?.let {
            return receipt(
                requestId, EventMutationStatusV1.INVALID,
                errorCode = "REQUEST_ID_INVALID", message = it
            )
        }
        receipts[requestId]?.let { cached ->
            return if (cached.first == fingerprint) cached.second.copy(duplicate = true)
            else receipt(
                requestId, EventMutationStatusV1.DUPLICATE_REQUEST_ID,
                errorCode = "REQUEST_ID_REUSED", message = "请求标识已用于其他事件操作"
            )
        }
        val result = operation()
        receipts[requestId] = fingerprint to result
        while (receipts.size > 128) receipts.remove(receipts.keys.first())
        return result
    }

    private fun invalidRequest(requestId: String): String? = when {
        requestId.isBlank() || requestId.length > 128 -> "请求标识无效"
        requestId.any(Char::isISOControl) -> "请求标识包含控制字符"
        else -> null
    }

    private fun updateError(
        status: EventEditorUpdateStatusV1,
        code: String,
        message: String? = null
    ) = EventEditorUpdateReceiptV1(status, code, message)

    private fun receipt(
        requestId: String,
        status: EventMutationStatusV1,
        eventSeq: Long? = null,
        revision: Long? = null,
        draftId: Long? = null,
        draftVersion: Long? = null,
        relatedEventSeq: Long? = null,
        duplicate: Boolean = false,
        errorCode: String? = null,
        message: String? = null,
        refreshRequired: Boolean = false
    ) = EventMutationReceiptV1(
        requestId, status, eventSeq, revision, draftId, draftVersion,
        relatedEventSeq, duplicate, errorCode, message, refreshRequired
    )

    private data class ApprovalClaims(
        val handle: String,
        val eventSeq: Long,
        val expectedRevision: Long
    )

    private data class StoreEventDeleteApprovalV1(
        override val eventSeq: Long,
        override val expectedRevision: Long
    ) : EventDeleteApprovalV1
}

internal fun EventFormV1.toCoreDraftForm(): DraftForm = DraftForm(
    kind = kind.toCoreKind(),
    title = title,
    startText = startText,
    endText = endText,
    notes = notes,
    location = location,
    reminderMinutes = reminderMinutes,
    reminderMethod = ReminderMethod.valueOf(reminderMethod.name),
    reminderTiming = ReminderTiming.valueOf(reminderTiming.name),
    enabled = enabled,
    source = source,
    zoneId = zoneId,
    recurrence = recurrence.toCoreRecurrence(),
    trackingEnabled = trackingEnabled,
    completedKeys = completedKeys.toSet(),
    recurrenceEditScope = recurrenceScope?.toCoreScope(),
    editOccurrenceKey = editOccurrenceKey
)

internal fun DraftForm.toEventFormV1(): EventFormV1 = EventFormV1(
    kind = kind.toApiKind(),
    title = title,
    startText = startText,
    endText = endText,
    notes = notes,
    location = location,
    reminderMinutes = reminderMinutes,
    reminderMethod = EventReminderMethodV1.valueOf(reminderMethod.name),
    reminderTiming = EventReminderTimingV1.valueOf(reminderTiming.name),
    enabled = enabled,
    source = source,
    zoneId = zoneId,
    recurrence = recurrence.toEventRecurrenceV1(),
    trackingEnabled = trackingEnabled,
    completedKeys = completedKeys.toSet(),
    recurrenceScope = recurrenceEditScope?.toApiScope(),
    editOccurrenceKey = editOccurrenceKey
)

internal fun EventRecordV1.toCoreTemporalRecord(): TemporalRecord = TemporalRecord(
    eventSeq = eventSeq,
    revision = revision,
    kind = kind.toCoreKind(),
    title = title,
    notes = notes,
    location = location,
    startMillis = startMillis,
    endMillis = endMillis,
    deadlineMillis = deadlineMillis,
    deadlineDate = deadlineDate,
    memoStartDate = memoStartDate,
    memoEndDate = memoEndDate,
    trackingEnabled = trackingEnabled,
    completedKeys = completedKeys.toSet(),
    recurrence = recurrence.toCoreRecurrence(),
    reminderMinutes = reminderMinutes,
    reminderMethod = ReminderMethod.valueOf(reminderMethod.name),
    reminderTiming = ReminderTiming.valueOf(reminderTiming.name),
    enabled = enabled,
    source = source,
    zoneId = zoneId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

private fun TemporalRecord.toEventRecordV1() = EventRecordV1(
    eventSeq = eventSeq,
    revision = revision,
    kind = kind.toApiKind(),
    title = title,
    notes = notes,
    location = location,
    startMillis = startMillis,
    endMillis = endMillis,
    deadlineMillis = deadlineMillis,
    deadlineDate = deadlineDate,
    memoStartDate = memoStartDate,
    memoEndDate = memoEndDate,
    trackingEnabled = trackingEnabled,
    completedKeys = completedKeys.toSet(),
    recurrence = recurrence.toEventRecurrenceV1(),
    reminderMinutes = reminderMinutes,
    reminderMethod = EventReminderMethodV1.valueOf(reminderMethod.name),
    reminderTiming = EventReminderTimingV1.valueOf(reminderTiming.name),
    enabled = enabled,
    source = source,
    zoneId = zoneId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

private fun Recurrence.toEventRecurrenceV1() = EventRecurrenceV1(
    frequency = EventRecurrenceFrequencyV1.valueOf(freq.name),
    interval = interval,
    count = count,
    untilMillis = untilMillis,
    activeFromDate = activeFromDate,
    activeUntilDate = activeUntilDate,
    byDay = byDay.mapTo(linkedSetOf(), DayOfWeek::getValue),
    exceptions = exceptions.toSet(),
    overrides = overrides.mapValues { (_, value) ->
        EventOccurrenceOverrideV1(value.startMillis, value.endMillis, value.title)
    }
)

private fun EventRecurrenceV1.toCoreRecurrence() = Recurrence(
    freq = RecurrenceFreq.valueOf(frequency.name),
    interval = interval,
    count = count,
    untilMillis = untilMillis,
    activeFromDate = activeFromDate,
    activeUntilDate = activeUntilDate,
    byDay = byDay.mapTo(linkedSetOf(), DayOfWeek::of),
    exceptions = exceptions.toSet(),
    overrides = overrides.mapValues { (_, value) ->
        OccurrenceOverride(value.startMillis, value.endMillis, value.title)
    }
)

private fun TemporalKind.toApiKind() = EventKindV1.valueOf(name)
private fun EventKindV1.toCoreKind() = TemporalKind.valueOf(name)
private fun RecurrenceEditScope.toApiScope() = EventRecurrenceScopeV1.valueOf(name)
private fun EventRecurrenceScopeV1.toCoreScope() = RecurrenceEditScope.valueOf(name)
