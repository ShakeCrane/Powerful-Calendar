package dev.powerfulcalendar.core.api.v1

import android.content.Context
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DeckEditResult
import dev.powerfulcalendar.core.DeckReviewCommandResult
import dev.powerfulcalendar.core.DeckProposalResult
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
import dev.powerfulcalendar.core.StoreDeckPersistence
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_EVENT_QUERY_PAGE_SIZE = 500
private const val MAX_EVENT_SEARCH_RESULTS = 8

/** Android/Core implementation adapter. All exposed values come from the pure :core-api module. */
class PowerCalCoreFeatureApiV1(
    private val store: PowerStore,
    clock: Clock = Clock.systemUTC(),
    deckController: DeckController = DeckController(StoreDeckPersistence(store)),
    reminderContext: Context? = null,
    settingsContext: Context? = reminderContext,
    aiModelPort: AiModelPortV1? = null,
    aiConversationHistoryPort: AiConversationHistoryPortV1? = null
) : CoreFeatureApiV1 {
    override val apiVersion: Int = 1
    private val eventService = StoreEventFeatureApiV1(store, clock)
    private val calendarService = StoreCalendarFeatureApiV1(store) { eventService.featureRevision }
    private val draftReviewService = StoreDraftReviewFeatureApiV1(store, deckController, eventService)
    private val aiService = StoreAiFeatureApiV1(
        store, eventService, calendarService, aiModelPort, clock, aiConversationHistoryPort
    )
    private val aiProposalService = StoreAiProposalFeatureApiV1(store, eventService, clock)
    private val reminderService = AndroidReminderFeatureApiV1(store, reminderContext)
    private val importExportService = StoreImportExportFeatureApiV1(
        store, draftReviewService, eventService
    )
    private val diagnosticsService = StoreCoreDiagnosticsFeatureApiV1(store)
    private val settingsDataService = AndroidSettingsDataFeatureApiV1(settingsContext)
    private val memoService = StoreMemoFeatureApiV1(store) { eventService.featureRevision }
    override val calendar: CalendarFeatureApiV1 get() = calendarService
    override val events: EventFeatureApiV1 get() = eventService
    override val draftReview: DraftReviewFeatureApiV1 get() = draftReviewService
    override val ai: AiFeatureApiV1 get() = aiService
    override val aiProposals: AiProposalFeatureApiV1 get() = aiProposalService
    override val reminders: ReminderFeatureApiV1 get() = reminderService
    override val importExport: ImportExportFeatureApiV1 get() = importExportService
    override val settingsData: SettingsDataFeatureApiV1 get() = settingsDataService
    override val diagnostics: CoreDiagnosticsFeatureApiV1 get() = diagnosticsService
    override val memos: MemoFeatureApiV1 get() = memoService

    /** Test/legacy Host seam for an explicitly supplied controller; production uses [draftReview]. */
    internal fun adaptDeckController(controller: DeckController): DraftReviewFeatureApiV1 =
        StoreDraftReviewFeatureApiV1(store, controller, eventService)

    /** Called only by trusted Host code after a user-confirmation surface has accepted deletion. */
    internal fun issueFormalDeleteApproval(handle: EventEditorHandleV1): EventDeleteApprovalV1? =
        eventService.issueFormalDeleteApproval(handle)

    /** Called only by trusted Host code after the exact AI proposal set received user approval. */
    internal fun issueAiProposalPublishApproval(
        request: AiProposalApplyRequestV1
    ): AiProposalPublishApprovalV1? = aiProposalService.issueHostApproval(request)

    /** Called only after the Host review surface confirms this exact batch and operation mode. */
    internal fun issueAiBatchApproval(review: AiBatchReviewV1): AiBatchApprovalV1? =
        aiService.issueHostBatchApproval(review)

    /** Called only after the Host stuck-session review has been confirmed. */
    internal fun issueAiBatchStuckReleaseApproval(
        sessionId: String,
        lockEpoch: Long
    ): AiBatchStuckReleaseApprovalV1? =
        aiService.issueHostStuckReleaseApproval(sessionId, lockEpoch)
}

internal class StoreEventFeatureApiV1(
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
    private val pageServiceInstanceId = UUID.randomUUID().toString()
    private val eventAxisOrder = compareBy<EventRecordV1>(
        { it.toCoreTemporalRecord().anchorMillis() == null },
        { it.toCoreTemporalRecord().anchorMillis() ?: Long.MAX_VALUE },
        EventRecordV1::eventSeq
    )

    override val featureRevision: Long get() = revision.get()

    @Synchronized
    override fun recoverAbandonedHumanEdit(staleAfterMillis: Long): Boolean =
        store.recoverAbandonedHumanEdit(staleAfterMillis)

    @Synchronized
    override fun queryEvents(): EventQuerySnapshotV1 {
        val snapshotRevision = featureRevision
        val events = buildList {
            store.forEachActiveEventPage { page ->
                addAll(page.map(TemporalRecord::toEventRecordV1))
                true
            }
        }
        return EventQuerySnapshotV1(snapshotRevision, events)
    }

    @Synchronized
    override fun queryEventsPage(
        cursor: EventQueryCursorV1?,
        limit: Int
    ): EventQueryPageV1 {
        require(limit in 1..MAX_EVENT_QUERY_PAGE_SIZE) {
            "事件查询页大小必须介于 1 和 $MAX_EVENT_QUERY_PAGE_SIZE"
        }
        val snapshotRevision = featureRevision
        if (cursor != null &&
            (cursor.serviceInstanceId != pageServiceInstanceId ||
                cursor.featureRevision != snapshotRevision)
        ) {
            return EventQueryPageV1(
                featureRevision = snapshotRevision,
                events = emptyList(),
                totalEvents = store.activeEventPage(limit = limit).total,
                stale = true
            )
        }
        val position = cursor?.let {
            PowerStore.ActiveEventPosition(it.afterAnchorMillis, it.afterEventSeq)
        }
        val page = store.activeEventPage(position, limit)
        val finalRevision = featureRevision
        if (snapshotRevision != finalRevision) return EventQueryPageV1(
            featureRevision = finalRevision,
            events = emptyList(),
            totalEvents = page.total,
            stale = true
        )
        return EventQueryPageV1(
            featureRevision = snapshotRevision,
            events = page.records.map(TemporalRecord::toEventRecordV1),
            totalEvents = page.total,
            nextCursor = page.next?.let {
                EventQueryCursorV1(
                    featureRevision = snapshotRevision,
                    afterAnchorMillis = it.anchorMillis,
                    afterEventSeq = it.eventSeq,
                    serviceInstanceId = pageServiceInstanceId
                )
            }
        )
    }

    @Synchronized
    override fun queryEvent(eventSeq: Long): EventRecordV1? =
        if (eventSeq <= 0L) null else store.get(eventSeq)?.toEventRecordV1()

    override fun searchEvents(
        query: String,
        snapshot: EventQuerySnapshotV1
    ): EventSearchSnapshotV1 {
        val hits = if (query.isBlank()) emptyList() else snapshot.events.asSequence()
            .filter { event ->
                event.title.contains(query, ignoreCase = true) ||
                    event.eventSeq.toString().contains(query) ||
                    TemporalRecord.formatSeq(event.eventSeq).contains(query)
            }
            .take(8)
            .map { event ->
                EventSearchHitV1(event.eventSeq, event.revision, event.kind, event.title)
            }
            .toList()
        return EventSearchSnapshotV1(snapshot.featureRevision, hits)
    }

    override suspend fun searchEvents(query: String): EventSearchSnapshotV1 = synchronized(this) {
        val snapshotRevision = featureRevision
        if (query.isBlank()) return@synchronized EventSearchSnapshotV1(snapshotRevision, emptyList())
        val firstHits = java.util.PriorityQueue(
            MAX_EVENT_SEARCH_RESULTS,
            eventAxisOrder.reversed()
        )
        store.forEachActiveEventPage { page ->
            page.asSequence()
                .map(TemporalRecord::toEventRecordV1)
                .filter { event ->
                    event.title.contains(query, ignoreCase = true) ||
                        event.eventSeq.toString().contains(query) ||
                        TemporalRecord.formatSeq(event.eventSeq).contains(query)
                }
                .forEach { event ->
                    if (firstHits.size < MAX_EVENT_SEARCH_RESULTS) firstHits.add(event)
                    else if (eventAxisOrder.compare(event, firstHits.peek()) < 0) {
                        firstHits.poll()
                        firstHits.add(event)
                    }
                }
            true
        }
        val finalRevision = featureRevision
        val hits = if (finalRevision == snapshotRevision) {
            firstHits.toList().sortedWith(eventAxisOrder).map {
                EventSearchHitV1(it.eventSeq, it.revision, it.kind, it.title)
            }
        } else emptyList()
        EventSearchSnapshotV1(finalRevision, hits)
    }

    @Synchronized
    override fun setOccurrenceCompletion(
        eventSeq: Long,
        expectedRevision: Long,
        occurrenceKey: String,
        expectedCompleted: Boolean,
        completed: Boolean,
        requestId: String
    ): EventMutationReceiptV1 {
        val fingerprint = "completion:$eventSeq:$expectedRevision:${occurrenceKey.length}:" +
            "$occurrenceKey:$expectedCompleted:$completed"
        return withReceipt(requestId, fingerprint) {
            val result = synchronized(store) {
                if (eventSeq <= 0L || expectedRevision <= 0L || occurrenceKey.isBlank()) {
                    return@synchronized receipt(
                        requestId, EventMutationStatusV1.INVALID,
                        errorCode = "COMPLETION_IDENTITY_INVALID", message = "完成项身份或版本无效"
                    )
                }
                if (store.isLocked()) return@synchronized receipt(
                    requestId, EventMutationStatusV1.LOCKED, eventSeq = eventSeq,
                    errorCode = "CORE_LOCKED", message = "事件正在批量处理中"
                )
                val current = store.get(eventSeq) ?: return@synchronized receipt(
                    requestId, EventMutationStatusV1.INVALID, eventSeq = eventSeq,
                    errorCode = "EVENT_NOT_FOUND", message = "事件不存在"
                )
                if (current.revision != expectedRevision) return@synchronized receipt(
                    requestId, EventMutationStatusV1.CONFLICT, eventSeq = eventSeq,
                    revision = current.revision, errorCode = "EVENT_REVISION_STALE",
                    message = "事件已变化，请刷新后重试", refreshRequired = true
                )
                if (!current.trackingEnabled || !current.enabled) return@synchronized receipt(
                    requestId, EventMutationStatusV1.INVALID, eventSeq = eventSeq,
                    revision = current.revision, errorCode = "COMPLETION_UNAVAILABLE",
                    message = "该事件当前未开放完成跟踪"
                )
                val occurrenceExists = if (current.recurrence.isRepeating) {
                    RecurrenceEngine.occurrenceForKey(current, occurrenceKey) != null
                } else occurrenceKey == "single"
                if (!occurrenceExists) return@synchronized receipt(
                    requestId, EventMutationStatusV1.INVALID, eventSeq = eventSeq,
                    revision = current.revision, errorCode = "OCCURRENCE_NOT_FOUND",
                    message = "完成项实例不存在或已失效"
                )
                val wasCompleted = occurrenceKey in current.completedKeys
                if (wasCompleted != expectedCompleted) return@synchronized receipt(
                    requestId, EventMutationStatusV1.CONFLICT, eventSeq = eventSeq,
                    revision = current.revision, errorCode = "COMPLETION_STATE_STALE",
                    message = "完成状态已变化，请刷新后重试", refreshRequired = true
                )
                if (wasCompleted == completed) return@synchronized receipt(
                    requestId, EventMutationStatusV1.NO_CHANGE, eventSeq = eventSeq,
                    revision = current.revision
                )
                if (!store.setCompleted(eventSeq, occurrenceKey, completed, expectedRevision)) {
                    val latest = store.get(eventSeq)
                    return@synchronized if (store.isLocked()) receipt(
                        requestId, EventMutationStatusV1.LOCKED, eventSeq = eventSeq,
                        errorCode = "CORE_LOCKED", message = "事件正在批量处理中"
                    ) else receipt(
                        requestId, EventMutationStatusV1.CONFLICT, eventSeq = eventSeq,
                        revision = latest?.revision, errorCode = "COMPLETION_WRITE_REJECTED",
                        message = "完成状态未保存，请刷新后重试", refreshRequired = true
                    )
                }
                val updated = store.get(eventSeq)
                if (updated == null || updated.revision != expectedRevision + 1L ||
                    (occurrenceKey in updated.completedKeys) != completed
                ) return@synchronized receipt(
                    requestId, EventMutationStatusV1.FAILED, eventSeq = eventSeq,
                    revision = updated?.revision, errorCode = "COMPLETION_RESULT_UNVERIFIED",
                    message = "完成状态已写入，但当前版本无法确认", refreshRequired = true
                )
                receipt(
                    requestId, EventMutationStatusV1.UPDATED, eventSeq = eventSeq,
                    revision = updated.revision
                )
            }
            if (result.status == EventMutationStatusV1.UPDATED) {
                publish(EventFeatureChangeTypeV1.FORMAL_UPDATED, eventSeq, result.revision)
            }
            result
        }
    }

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
                        is PowerStore.CommitOutcome.DraftVersionMismatch -> receipt(
                            requestId, EventMutationStatusV1.CONFLICT,
                            eventSeq = outcome.current.eventSeq,
                            draftId = outcome.current.draftId,
                            draftVersion = outcome.current.draftVersion,
                            errorCode = "DRAFT_VERSION_STALE", refreshRequired = true
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
        dispatchPreparedChange(prepareChange(type, eventSeq, eventRevision))
    }

    private fun prepareChange(
        type: EventFeatureChangeTypeV1,
        eventSeq: Long? = null,
        eventRevision: Long? = null
    ) = EventFeatureChangeV1(revision.incrementAndGet(), type, eventSeq, eventRevision)

    private fun dispatchPreparedChange(change: EventFeatureChangeV1) {
        listeners.forEach { listener -> runCatching { listener(change) } }
    }

    internal fun dispatchPreparedChanges(changes: List<EventFeatureChangeV1>) {
        changes.forEach(::dispatchPreparedChange)
    }

    internal fun publishDeckFormalChange(type: EventFeatureChangeTypeV1, eventSeq: Long) {
        val revision = store.getIncludingRetired(eventSeq)?.revision
        publish(type, eventSeq, revision)
    }

    internal fun publishStoreRestored() {
        publish(EventFeatureChangeTypeV1.STORE_RESTORED)
    }

    internal fun prepareDeckFormalChange(
        type: EventFeatureChangeTypeV1,
        eventSeq: Long
    ): EventFeatureChangeV1 {
        val eventRevision = store.getIncludingRetired(eventSeq)?.revision
        return prepareChange(type, eventSeq, eventRevision)
    }

    internal fun publishDeckDraftChange() {
        publish(EventFeatureChangeTypeV1.DRAFTS_CHANGED)
    }

    internal fun prepareDeckDraftChange(): EventFeatureChangeV1 =
        prepareChange(EventFeatureChangeTypeV1.DRAFTS_CHANGED)

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

/** App-side adapter for the platform-neutral Draft/Review command contract. */
private class StoreDraftReviewFeatureApiV1(
    private val store: PowerStore,
    private val controller: DeckController,
    private val events: StoreEventFeatureApiV1
) : DraftReviewFeatureApiV1 {
    override val apiVersion = 1
    private val listeners = CopyOnWriteArraySet<(DraftReviewSnapshotV1) -> Unit>()
    private val mutationMutex = Mutex()
    private val receipts = LinkedHashMap<String, Pair<String, DraftReviewReceiptV1>>()
    private val initialControllerState = controller.stateSnapshot()
    private val persistedFingerprintLock = Any()
    private val stalePersistedFingerprints = LinkedHashSet<String>()

    @Volatile private var latestSnapshot = initialControllerState.first.toDraftReviewSnapshotV1().copy(
        stateRevision = initialControllerState.second
    )
    private var persistedFingerprint = persistedDeckFingerprint(initialControllerState.first)

    init {
        controller.observeAdditionalVersioned { state, stateRevision ->
            val next = state.toDraftReviewSnapshotV1().copy(
                stateRevision = stateRevision
            )
            latestSnapshot = next
            listeners.forEach { runCatching { it(next) } }
            observePersistedDraftState(state)
        }
    }

    override val snapshot get() = latestSnapshot

    @Synchronized
    override fun queryPendingDrafts(): List<DraftReviewCardV1> =
        store.pendingDrafts().map { it.toDraftReviewCardV1() }

    @Synchronized
    override fun queryDraft(draftId: Long): DraftReviewCardV1? =
        if (draftId <= 0L) null else store.draft(draftId)?.toDraftReviewCardV1()

    @Synchronized
    override fun queryRetiredEventsPage(offset: Int, limit: Int): DraftReviewRetiredPageV1 {
        require(offset in 0..DraftReviewFeatureApiV1.MAX_RETIRED_PAGE_OFFSET) {
            "墓碑页偏移无效"
        }
        require(limit in 1..DraftReviewFeatureApiV1.MAX_RETIRED_PAGE_SIZE) {
            "墓碑页大小无效"
        }
        val page = store.retiredPage(offset, limit)
        return DraftReviewRetiredPageV1(
            offset = offset,
            limit = limit,
            total = page.total,
            events = page.records.map { DraftReviewRetiredEventV1(it.toEventRecordV1()) }
        )
    }

    @Synchronized
    override fun queryRetiredEvent(eventSeq: Long): DraftReviewRetiredEventV1? {
        if (eventSeq <= 0L) return null
        return synchronized(store) {
            val record = store.getIncludingRetired(eventSeq) ?: return@synchronized null
            if (!store.isRetired(eventSeq)) return@synchronized null
            DraftReviewRetiredEventV1(record.toEventRecordV1())
        }
    }

    override fun observe(listener: (DraftReviewSnapshotV1) -> Unit): AutoCloseable {
        listeners += listener
        runCatching { listener(snapshot) }
        return AutoCloseable { listeners -= listener }
    }

    @Synchronized
    override fun editCurrentDraft(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        form: EventFormV1
    ): DraftReviewReceiptV1 {
        val (state, stateRevision) = controller.stateSnapshot()
        val card = state.current
        if (state.aiLocked || state.busy) return receipt("", DraftReviewReceiptStatusV1.LOCKED,
            draftId = draftId, eventSeq = card?.eventSeq, errorCode = "DRAFT_REVIEW_LOCKED")
        if (card == null || state.currentId != draftId || state.version != expectedDraftVersion ||
            stateRevision != expectedStateRevision
        ) {
            return receipt("", DraftReviewReceiptStatusV1.CONFLICT, draftId = draftId,
                eventSeq = card?.eventSeq, draftVersion = state.version,
                errorCode = if (stateRevision != expectedStateRevision)
                    "DRAFT_STATE_STALE" else "DRAFT_VERSION_STALE",
                message = "草稿已变化，请重新读取后再编辑")
        }
        val updatedForm = form.toCoreDraftForm()
        return when (controller.editIfCurrent(updatedForm, expectedStateRevision)) {
            DeckEditResult.APPLIED -> receipt("", DraftReviewReceiptStatusV1.APPLIED,
                draftId = draftId, eventSeq = card.eventSeq, draftVersion = card.draftVersion)
            DeckEditResult.STALE -> receipt("", DraftReviewReceiptStatusV1.CONFLICT,
                draftId = draftId, eventSeq = card.eventSeq, draftVersion = controller.state.version,
                errorCode = "DRAFT_STATE_STALE", message = "草稿已变化，请重新读取后再编辑")
            DeckEditResult.LOCKED -> receipt("", DraftReviewReceiptStatusV1.LOCKED,
                draftId = draftId, eventSeq = card.eventSeq, errorCode = "DRAFT_REVIEW_LOCKED",
                message = "当前草稿暂不可编辑")
        }
    }

    override fun dismissConflict() = controller.dismissConflict()
    override fun dismissRestore() = controller.dismissRestore()
    override fun dismissCompletion() = controller.dismissCompletion()
    override fun clearError() = controller.clearError()
    override fun setAiLocked(locked: Boolean) = controller.setAiLocked(locked)

    override suspend fun open(preferDraftId: Long?): DraftReviewSnapshotV1 {
        if (!flushBeforeReopening()) return snapshot
        controller.open(preferDraftId)
        return snapshot
    }

    override suspend fun refresh(preferDraftId: Long?): DraftReviewSnapshotV1 {
        if (!flushBeforeReopening()) return snapshot
        controller.refresh(preferDraftId)
        return snapshot
    }

    override suspend fun createDraft(
        form: EventFormV1,
        requestId: String
    ): DraftReviewReceiptV1 {
        val formSnapshot = form.detachedDraftSnapshot()
        val fingerprint = canonicalDraftFormFingerprint(formSnapshot)
        return execute(requestId, "CREATE_DRAFT:$fingerprint") {
            if (formSnapshot.recurrenceScope != null || formSnapshot.editOccurrenceKey != null) {
                return@execute receipt(requestId, DraftReviewReceiptStatusV1.INVALID,
                    errorCode = "DRAFT_SCOPE_INVALID", message = "新草稿不能携带既有重复实例编辑范围")
            }
            val controllerFingerprintBeforeWrite =
                persistedDeckFingerprint(controller.stateSnapshot().first)
            val card = try {
                store.newDraft(formSnapshot.toCoreDraftForm(), actor = "user")
            } catch (error: IllegalArgumentException) {
                return@execute receipt(requestId, DraftReviewReceiptStatusV1.INVALID,
                    errorCode = "DRAFT_FORM_INVALID", message = error.message)
            } catch (error: IllegalStateException) {
                val locked = runCatching { store.isLocked() }.getOrDefault(false)
                return@execute if (locked) receipt(requestId, DraftReviewReceiptStatusV1.LOCKED,
                    errorCode = "DRAFT_CREATE_LOCKED", message = error.message)
                else receipt(requestId, DraftReviewReceiptStatusV1.FAILED,
                    errorCode = "DRAFT_CREATE_FAILED", message = error.message)
            } catch (error: Exception) {
                return@execute receipt(requestId, DraftReviewReceiptStatusV1.FAILED,
                    errorCode = "DRAFT_CREATE_FAILED", message = error.message)
            }
            val publicationConfirmed = publishDraftChangeIfNeeded(controllerFingerprintBeforeWrite)
            receipt(requestId, DraftReviewReceiptStatusV1.APPLIED,
                draftId = card.draftId, eventSeq = card.eventSeq, draftVersion = card.draftVersion,
                message = if (publicationConfirmed) null
                    else "草稿已保存；变更通知未能确认，请重新打开卡组刷新")
        }
    }

    private fun publishDraftChangeIfNeeded(staleControllerFingerprint: String): Boolean = try {
        synchronized(persistedFingerprintLock) {
            rememberStalePersistedFingerprint(staleControllerFingerprint)
            val fingerprint = persistedDraftFingerprint(store.pendingDrafts())
            if (fingerprint != persistedFingerprint) {
                events.publishDeckDraftChange()
                persistedFingerprint = fingerprint
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun observePersistedDraftState(state: DeckController.DeckState) {
        val observedFingerprint = persistedDeckFingerprint(state)
        synchronized(persistedFingerprintLock) {
            when {
                observedFingerprint == persistedFingerprint -> stalePersistedFingerprints.clear()
                observedFingerprint in stalePersistedFingerprints -> Unit
                else -> {
                    val currentFingerprint = runCatching {
                        persistedDraftFingerprint(store.pendingDrafts())
                    }.getOrNull() ?: return
                    if (currentFingerprint != persistedFingerprint) {
                        events.publishDeckDraftChange()
                        persistedFingerprint = currentFingerprint
                    }
                    if (observedFingerprint != persistedFingerprint) {
                        rememberStalePersistedFingerprint(observedFingerprint)
                    }
                }
            }
        }
    }

    private fun rememberStalePersistedFingerprint(fingerprint: String) {
        stalePersistedFingerprints.remove(fingerprint)
        stalePersistedFingerprints.add(fingerprint)
        while (stalePersistedFingerprints.size > 32) {
            stalePersistedFingerprints.remove(stalePersistedFingerprints.first())
        }
    }

    private suspend fun flushBeforeReopening(): Boolean {
        repeat(3) {
            if (!controller.state.dirty) return true
            if (!controller.flush()) return false
        }
        return !controller.state.dirty
    }

    override suspend fun beginFormalReplacement(
        eventSeq: Long, expectedRevision: Long, requestId: String
    ) = execute(requestId, "REPLACE:$eventSeq:$expectedRevision") {
        proposalReceipt(requestId, controller.beginFormalReplacement(eventSeq, expectedRevision), eventSeq)
    }

    override suspend fun beginRestore(
        eventSeq: Long, expectedRevision: Long, requestId: String
    ) = execute(requestId, "RESTORE_PROPOSAL:$eventSeq:$expectedRevision") {
        proposalReceipt(requestId, controller.beginRestore(eventSeq, expectedRevision), eventSeq)
    }

    private fun proposalReceipt(
        requestId: String,
        result: DeckProposalResult,
        requestedEventSeq: Long
    ) = when (result) {
        is DeckProposalResult.Proposed -> receipt(requestId, DraftReviewReceiptStatusV1.APPLIED,
            draftId = result.card.draftId, eventSeq = result.card.eventSeq,
            revision = result.card.baseRevision, draftVersion = result.card.draftVersion)
        is DeckProposalResult.Stale -> receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT,
            eventSeq = requestedEventSeq, revision = result.currentRevision,
            errorCode = "SOURCE_REVISION_STALE")
        DeckProposalResult.Missing -> receipt(requestId, DraftReviewReceiptStatusV1.NOT_FOUND,
            eventSeq = requestedEventSeq, errorCode = "SOURCE_NOT_FOUND")
        DeckProposalResult.Locked -> receipt(requestId, DraftReviewReceiptStatusV1.LOCKED,
            eventSeq = requestedEventSeq, errorCode = "CORE_LOCKED")
        DeckProposalResult.Conflict -> receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT,
            eventSeq = requestedEventSeq, errorCode = "DRAFT_OPERATION_CONFLICT")
        is DeckProposalResult.Failed -> receipt(requestId, DraftReviewReceiptStatusV1.FAILED,
            eventSeq = requestedEventSeq, errorCode = "DRAFT_PROPOSAL_FAILED", message = result.message)
    }

    override suspend fun flush(requestId: String, lockEdits: Boolean) =
        execute(requestId, "FLUSH:$lockEdits") {
            val before = controller.state
            val saved = controller.flush(lockEdits)
            if (saved) receipt(requestId,
                if (before.dirty) DraftReviewReceiptStatusV1.APPLIED else DraftReviewReceiptStatusV1.NO_CHANGE,
                draftId = before.currentId, eventSeq = before.current?.eventSeq,
                draftVersion = controller.state.version)
            else failureReceipt(requestId, "FLUSH_FAILED", before.currentId, before.current?.eventSeq)
        }

    override suspend fun select(draftId: Long, requestId: String) =
        execute(requestId, "SELECT:$draftId") {
            if (controller.state.cards.none { it.draftId == draftId }) receipt(
                requestId, DraftReviewReceiptStatusV1.NOT_FOUND, draftId = draftId)
            else if (controller.select(draftId)) appliedCurrent(requestId)
            else failureReceipt(requestId, "DRAFT_SELECT_FAILED", draftId)
        }

    override suspend fun move(delta: Int, requestId: String) =
        execute(requestId, "MOVE:$delta") {
            if (controller.move(delta)) appliedCurrent(requestId)
            else failureReceipt(requestId, "DRAFT_MOVE_FAILED")
        }

    override suspend fun jump(steps: Int, requestId: String) =
        execute(requestId, "JUMP:$steps") {
            if (controller.jump(steps)) appliedCurrent(requestId)
            else failureReceipt(requestId, "DRAFT_JUMP_FAILED")
        }

    override suspend fun commit(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        requestId: String
    ) = execute(requestId, "COMMIT:$draftId:$expectedDraftVersion:$expectedStateRevision") {
            val (before, stateRevision) = controller.stateSnapshot()
            val card = before.current
            if (stateRevision != expectedStateRevision || card == null ||
                before.currentId != draftId || before.version != expectedDraftVersion
            ) {
                receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT, draftId = draftId,
                    eventSeq = card?.eventSeq, draftVersion = before.version,
                    errorCode = if (stateRevision != expectedStateRevision)
                        "DRAFT_STATE_STALE" else "DRAFT_VERSION_STALE")
            } else when (controller.commitReviewed(draftId, expectedDraftVersion, expectedStateRevision)) {
                DeckReviewCommandResult.APPLIED -> {
                    val change = when (card.operation) {
                        DraftOperation.CREATE -> EventFeatureChangeTypeV1.FORMAL_CREATED
                        DraftOperation.DELETE -> EventFeatureChangeTypeV1.FORMAL_DELETED
                        DraftOperation.REPLACE, DraftOperation.CONVERT -> EventFeatureChangeTypeV1.FORMAL_UPDATED
                    }
                    events.publishDeckFormalChange(change, card.eventSeq)
                    receipt(requestId, DraftReviewReceiptStatusV1.APPLIED, draftId = draftId,
                        eventSeq = card.eventSeq, revision = store.getIncludingRetired(card.eventSeq)?.revision)
                }
                DeckReviewCommandResult.STALE -> receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT,
                    draftId = draftId, eventSeq = card.eventSeq,
                    draftVersion = controller.state.current?.draftVersion,
                    errorCode = "DRAFT_REVIEW_STALE", message = "草稿已变化，请重新检查")
                DeckReviewCommandResult.LOCKED -> receipt(requestId, DraftReviewReceiptStatusV1.LOCKED,
                    draftId = draftId, eventSeq = card.eventSeq, errorCode = "DRAFT_REVIEW_LOCKED")
                DeckReviewCommandResult.NOT_APPLIED -> when {
                    controller.state.conflict != null -> receipt(requestId,
                        DraftReviewReceiptStatusV1.REVIEW_REQUIRED, draftId = draftId,
                        eventSeq = card.eventSeq, errorCode = "FORMAL_CONFLICT_REVIEW_REQUIRED")
                    controller.state.restore != null -> receipt(requestId,
                        DraftReviewReceiptStatusV1.RESTORE_REQUIRED, draftId = draftId,
                        eventSeq = card.eventSeq, errorCode = "FORMAL_RESTORE_REVIEW_REQUIRED")
                    else -> failureReceipt(requestId, "DRAFT_COMMIT_NOT_APPLIED", draftId, card.eventSeq)
                }
            }
        }

    override suspend fun confirmConflictReplacement(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        expectedCurrentRevision: Long,
        requestId: String
    ) = execute(requestId,
        "CONFIRM_REPLACE:$draftId:$expectedDraftVersion:$expectedStateRevision:$expectedCurrentRevision") {
        val (state, stateRevision) = controller.stateSnapshot()
        val prompt = state.conflict
        if (prompt == null || prompt.draftId != draftId ||
            state.version != expectedDraftVersion || stateRevision != expectedStateRevision ||
            prompt.currentRevision != expectedCurrentRevision
        ) receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT, draftId = draftId,
            eventSeq = prompt?.eventSeq, revision = prompt?.currentRevision,
            errorCode = if (stateRevision != expectedStateRevision)
                "DRAFT_STATE_STALE" else "REVIEW_SNAPSHOT_STALE",
            message = "审阅内容已变化，请重新检查")
        else when (controller.confirmConflictReplacementReviewed(
            draftId, expectedDraftVersion, expectedCurrentRevision, expectedStateRevision
        )) {
            DeckReviewCommandResult.APPLIED -> {
                events.publishDeckFormalChange(EventFeatureChangeTypeV1.FORMAL_UPDATED, prompt.eventSeq)
                receipt(requestId, DraftReviewReceiptStatusV1.APPLIED, draftId = draftId,
                    eventSeq = prompt.eventSeq, revision = store.getIncludingRetired(prompt.eventSeq)?.revision)
            }
            DeckReviewCommandResult.STALE -> receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT,
                draftId = draftId, eventSeq = prompt.eventSeq,
                draftVersion = controller.state.current?.draftVersion,
                errorCode = "DRAFT_REVIEW_STALE", message = "草稿已变化，请重新检查")
            DeckReviewCommandResult.LOCKED -> receipt(requestId, DraftReviewReceiptStatusV1.LOCKED,
                draftId = draftId, eventSeq = prompt.eventSeq, errorCode = "DRAFT_REVIEW_LOCKED")
            DeckReviewCommandResult.NOT_APPLIED -> if (controller.state.conflict != null)
                receipt(requestId, DraftReviewReceiptStatusV1.REVIEW_REQUIRED, draftId = draftId,
                    eventSeq = prompt.eventSeq, errorCode = "FORMAL_CONFLICT_REVIEW_REQUIRED")
            else failureReceipt(requestId, "CONFLICT_APPROVAL_NOT_APPLIED", draftId, prompt.eventSeq)
        }
    }

    override suspend fun requestRestore(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        expectedTombstoneRevision: Long,
        requestId: String
    ) = execute(requestId,
        "RESTORE:$draftId:$expectedDraftVersion:$expectedStateRevision:$expectedTombstoneRevision") {
            val (state, stateRevision) = controller.stateSnapshot()
            val prompt = state.restore
            if (prompt == null || prompt.card.draftId != draftId ||
                state.version != expectedDraftVersion || stateRevision != expectedStateRevision ||
                prompt.card.baseRevision != expectedTombstoneRevision
            ) receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT, draftId = draftId,
                eventSeq = prompt?.eventSeq, revision = prompt?.card?.baseRevision,
                errorCode = if (stateRevision != expectedStateRevision)
                    "DRAFT_STATE_STALE" else "RESTORE_REVIEW_STALE")
            else when (controller.requestRestoreReviewed(
                draftId, expectedDraftVersion, expectedTombstoneRevision, expectedStateRevision
            )) {
                DeckReviewCommandResult.APPLIED -> {
                    events.publishDeckFormalChange(EventFeatureChangeTypeV1.FORMAL_UPDATED, prompt.eventSeq)
                    receipt(requestId, DraftReviewReceiptStatusV1.APPLIED, draftId = draftId,
                        eventSeq = prompt.eventSeq, revision = store.getIncludingRetired(prompt.eventSeq)?.revision)
                }
                DeckReviewCommandResult.STALE -> receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT,
                    draftId = draftId, eventSeq = prompt.eventSeq,
                    draftVersion = controller.state.current?.draftVersion,
                    errorCode = "DRAFT_REVIEW_STALE", message = "恢复草稿已变化，请重新检查")
                DeckReviewCommandResult.LOCKED -> receipt(requestId, DraftReviewReceiptStatusV1.LOCKED,
                    draftId = draftId, eventSeq = prompt.eventSeq, errorCode = "DRAFT_REVIEW_LOCKED")
                DeckReviewCommandResult.NOT_APPLIED -> if (controller.state.restore != null)
                    receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT, draftId = draftId,
                        eventSeq = prompt.eventSeq, errorCode = "RESTORE_REVIEW_STALE",
                        message = controller.state.error ?: "恢复内容已变化，请重新检查")
                else failureReceipt(requestId, "RESTORE_NOT_APPLIED", draftId, prompt.eventSeq)
            }
        }

    override suspend fun discard(draftId: Long, expectedDraftVersion: Long, requestId: String) =
        execute(requestId, "DISCARD:$draftId:$expectedDraftVersion") {
            val state = controller.state
            if (state.currentId != draftId || state.version != expectedDraftVersion) {
                receipt(requestId, DraftReviewReceiptStatusV1.CONFLICT, draftId = draftId,
                    eventSeq = state.current?.eventSeq, draftVersion = state.version,
                    errorCode = "DRAFT_VERSION_STALE")
            } else if (controller.discard()) receipt(requestId, DraftReviewReceiptStatusV1.APPLIED,
                draftId = draftId, eventSeq = state.current?.eventSeq, draftVersion = expectedDraftVersion)
            else failureReceipt(requestId, "DRAFT_DISCARD_FAILED", draftId, state.current?.eventSeq)
        }

    override suspend fun undoDiscard(draftId: Long, requestId: String) =
        execute(requestId, "UNDO_DISCARD:$draftId") {
            if (controller.state.lastDiscardId != draftId) receipt(
                requestId, DraftReviewReceiptStatusV1.NOT_FOUND, draftId = draftId)
            else if (controller.undoDiscard()) appliedCurrent(requestId, draftId)
            else failureReceipt(requestId, "DRAFT_UNDO_FAILED", draftId)
        }

    private suspend fun execute(
        requestId: String,
        fingerprint: String,
        operation: suspend () -> DraftReviewReceiptV1
    ): DraftReviewReceiptV1 {
        invalidRequest(requestId)?.let {
            return receipt(requestId, DraftReviewReceiptStatusV1.INVALID,
                errorCode = "REQUEST_ID_INVALID", message = it)
        }
        return mutationMutex.withLock {
            receipts[requestId]?.let { cached ->
                if (cached.first == fingerprint) return@withLock cached.second.copy(duplicate = true)
                return@withLock receipt(requestId, DraftReviewReceiptStatusV1.DUPLICATE_REQUEST_ID,
                    errorCode = "REQUEST_ID_REUSED", message = "请求标识已用于其他草稿操作")
            }
            val result = operation()
            receipts[requestId] = fingerprint to result
            while (receipts.size > 128) receipts.remove(receipts.keys.first())
            result
        }
    }

    private fun appliedCurrent(requestId: String, fallbackDraftId: Long? = null) = receipt(
        requestId, DraftReviewReceiptStatusV1.APPLIED,
        draftId = controller.state.currentId ?: fallbackDraftId,
        eventSeq = controller.state.current?.eventSeq,
        draftVersion = controller.state.version
    )

    private fun failureReceipt(
        requestId: String, code: String, draftId: Long? = null, eventSeq: Long? = null
    ): DraftReviewReceiptV1 {
        val state = controller.state
        val status = when {
            state.conflict != null -> DraftReviewReceiptStatusV1.REVIEW_REQUIRED
            state.restore != null -> DraftReviewReceiptStatusV1.RESTORE_REQUIRED
            state.aiLocked -> DraftReviewReceiptStatusV1.LOCKED
            else -> DraftReviewReceiptStatusV1.FAILED
        }
        return receipt(requestId, status, draftId, eventSeq,
            draftVersion = state.version, errorCode = code, message = state.error)
    }

    private fun receipt(
        requestId: String,
        status: DraftReviewReceiptStatusV1,
        draftId: Long? = null,
        eventSeq: Long? = null,
        revision: Long? = null,
        draftVersion: Long? = null,
        duplicate: Boolean = false,
        errorCode: String? = null,
        message: String? = null
    ) = DraftReviewReceiptV1(requestId, status, draftId, eventSeq, revision, draftVersion,
        events.featureRevision, duplicate, errorCode, message)

    private fun invalidRequest(id: String) = when {
        id.isBlank() || id.length > 128 -> "请求标识无效"
        id.any(Char::isISOControl) -> "请求标识包含控制字符"
        else -> null
    }
}

private fun DeckController.DeckState.toDraftReviewSnapshotV1(): DraftReviewSnapshotV1 {
    fun projected(card: DraftCard): DraftReviewCardV1 {
        val persistedCurrent = card.draftId == currentId && !dirty
        return card.copy(
            form = if (persistedCurrent) form ?: card.form else card.form,
            draftVersion = if (persistedCurrent) version else card.draftVersion
        ).toDraftReviewCardV1()
    }
    return DraftReviewSnapshotV1(
        cards = cards.map(::projected), currentId = currentId,
        current = current?.let(::projected), form = form?.toEventFormV1(),
        version = version, dirty = dirty, saving = saving, aiLocked = aiLocked,
        notice = notice, error = error,
        conflict = conflict?.let { prompt -> DraftReviewConflictV1(
            prompt.draftId, prompt.eventSeq, prompt.current?.toEventRecordV1(),
            prompt.changes.map { DraftReviewFieldChangeV1(it.label, it.before, it.after) },
            projected(prompt.card), prompt.currentRevision,
            prompt.approvedForm.toEventFormV1()
        ) },
        restore = restore?.let { DraftReviewRestoreV1(it.eventSeq, projected(it.card), it.message) },
        completion = completion?.let { DraftReviewCompletionV1(it.published, it.discarded, it.conflicts) },
        lastDiscardId = lastDiscardId, busy = busy
    )
}

private fun DraftCard.toDraftReviewCardV1() = DraftReviewCardV1(
    draftId, eventSeq, queueOrder, DraftOperationV1.valueOf(operation.name), form.toEventFormV1(),
    DraftStatusV1.valueOf(status.name), baseRevision, draftVersion, createdAtMillis, updatedAtMillis
)

private fun persistedDeckFingerprint(state: DeckController.DeckState): String =
    persistedDraftFingerprint(state.cards)

private fun persistedDraftFingerprint(cards: List<DraftCard>): String =
    cards.joinToString("|") { card ->
        "${card.draftId}:${card.eventSeq}:${card.queueOrder}:${card.draftVersion}:${card.status}"
    }

private fun EventFormV1.detachedDraftSnapshot(): EventFormV1 = copy(
    completedKeys = completedKeys.toSet(),
    recurrence = recurrence.copy(
        byDay = recurrence.byDay.toSet(),
        exceptions = recurrence.exceptions.toSet(),
        overrides = recurrence.overrides.mapValues { (_, value) -> value.copy() }
    )
)

internal fun canonicalDraftFormFingerprint(form: EventFormV1): String {
    val canonical = buildString {
        fun field(name: String, value: String?) {
            append(name.length).append(':').append(name)
            if (value == null) append("-1:")
            else append(value.length).append(':').append(value)
        }
        fun stringSet(name: String, values: Collection<String>) {
            field("$name.count", values.size.toString())
            values.sorted().forEach { field(name, it) }
        }

        field("kind", form.kind.name)
        field("title", form.title)
        field("startText", form.startText)
        field("endText", form.endText)
        field("notes", form.notes)
        field("location", form.location)
        field("reminderMinutes", form.reminderMinutes.toString())
        field("reminderMethod", form.reminderMethod.name)
        field("reminderTiming", form.reminderTiming.name)
        field("enabled", form.enabled.toString())
        field("source", form.source)
        field("zoneId", form.zoneId)
        field("recurrence.frequency", form.recurrence.frequency.name)
        field("recurrence.interval", form.recurrence.interval.toString())
        field("recurrence.count", form.recurrence.count?.toString())
        field("recurrence.untilMillis", form.recurrence.untilMillis?.toString())
        field("recurrence.activeFromDate", form.recurrence.activeFromDate)
        field("recurrence.activeUntilDate", form.recurrence.activeUntilDate)
        field("recurrence.byDay.count", form.recurrence.byDay.size.toString())
        form.recurrence.byDay.sorted().forEach { field("recurrence.byDay", it.toString()) }
        stringSet("recurrence.exceptions", form.recurrence.exceptions)
        val overrides = form.recurrence.overrides.toSortedMap()
        field("recurrence.overrides.count", overrides.size.toString())
        overrides.forEach { (key, value) ->
            field("recurrence.overrides.key", key)
            field("recurrence.overrides.startMillis", value.startMillis.toString())
            field("recurrence.overrides.endMillis", value.endMillis?.toString())
            field("recurrence.overrides.title", value.title)
        }
        field("trackingEnabled", form.trackingEnabled.toString())
        stringSet("completedKeys", form.completedKeys)
        field("recurrenceScope", form.recurrenceScope?.name)
        field("editOccurrenceKey", form.editOccurrenceKey)
    }
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
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

internal fun TemporalRecord.toEventRecordV1() = EventRecordV1(
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
