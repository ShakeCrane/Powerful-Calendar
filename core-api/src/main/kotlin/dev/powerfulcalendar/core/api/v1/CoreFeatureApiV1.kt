package dev.powerfulcalendar.core.api.v1

/** Stable event vocabulary. Keep these values independent from Core and presentation enums. */
enum class EventKindV1 { ALARM, ACTIVITY, DEADLINE, MEMO }
enum class EventRecurrenceFrequencyV1 { NONE, DAILY, WEEKLY, MONTHLY }
enum class EventReminderMethodV1 { MESSAGE, ALARM }
enum class EventReminderTimingV1 { OFFSET, DAY_START }
enum class EventRecurrenceScopeV1 { SINGLE_OCCURRENCE, THIS_AND_FUTURE, WHOLE_SERIES }
enum class EventCapabilityV1 { CREATE, EDIT, DELETE, RECURRENCE_EDIT, MOVE_TO_DRAFT }

data class EventOccurrenceOverrideV1(
    val startMillis: Long,
    val endMillis: Long?,
    val title: String? = null
)

/** ISO weekday numbers (Monday=1 through Sunday=7) keep the wire contract platform-neutral. */
data class EventRecurrenceV1(
    val frequency: EventRecurrenceFrequencyV1 = EventRecurrenceFrequencyV1.NONE,
    val interval: Int = 1,
    val count: Int? = null,
    val untilMillis: Long? = null,
    val activeFromDate: String? = null,
    val activeUntilDate: String? = null,
    val byDay: Set<Int> = emptySet(),
    val exceptions: Set<String> = emptySet(),
    val overrides: Map<String, EventOccurrenceOverrideV1> = emptyMap()
)

/** Complete editable event value. Immutable/Core-owned fields are validated by Core on update. */
data class EventFormV1(
    val kind: EventKindV1,
    val title: String = "",
    val startText: String = "",
    val endText: String = "",
    val notes: String = "",
    val location: String = "",
    val reminderMinutes: Int = -1,
    val reminderMethod: EventReminderMethodV1 = EventReminderMethodV1.MESSAGE,
    val reminderTiming: EventReminderTimingV1 = EventReminderTimingV1.OFFSET,
    val enabled: Boolean = true,
    val source: String = "local",
    val zoneId: String,
    val recurrence: EventRecurrenceV1 = EventRecurrenceV1(),
    val trackingEnabled: Boolean = kind == EventKindV1.MEMO,
    val completedKeys: Set<String> = emptySet(),
    val recurrenceScope: EventRecurrenceScopeV1? = null,
    val editOccurrenceKey: String? = null
)

/** Full immutable formal snapshot needed by feature queries; no Core entity escapes the adapter. */
data class EventRecordV1(
    val eventSeq: Long,
    val revision: Long,
    val kind: EventKindV1,
    val title: String,
    val notes: String,
    val location: String,
    val startMillis: Long?,
    val endMillis: Long?,
    val deadlineMillis: Long?,
    val deadlineDate: String?,
    val memoStartDate: String?,
    val memoEndDate: String?,
    val trackingEnabled: Boolean,
    val completedKeys: Set<String>,
    val recurrence: EventRecurrenceV1,
    val reminderMinutes: Int,
    val reminderMethod: EventReminderMethodV1,
    val reminderTiming: EventReminderTimingV1,
    val enabled: Boolean,
    val source: String,
    val zoneId: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class EventSelectionV1(
    val instantMillis: Long? = null,
    val localDate: String? = null
) {
    init {
        require(instantMillis == null || localDate == null) {
            "An event selection cannot contain both an instant and a local date"
        }
    }
}

/** Opaque in-process editor reference. Its random ID conveys no authority by itself. */
data class EventEditorHandleV1(val value: String)

data class EventEditorSnapshotV1(
    val handle: EventEditorHandleV1,
    val eventSeq: Long?,
    val expectedRevision: Long?,
    val clientDraftId: Long?,
    val draftVersion: Long?,
    val form: EventFormV1,
    val baselineForm: EventFormV1,
    val recurrenceScopeRequired: Boolean,
    val selectedOccurrenceKey: String?,
    val defaultNowMillis: Long?,
    val meaningfulChanges: Boolean,
    val hasPersistedDraft: Boolean,
    val lastError: String?,
    val capabilities: Set<EventCapabilityV1>
)

sealed interface EventEditorOpenResultV1 {
    data class Opened(val editor: EventEditorSnapshotV1) : EventEditorOpenResultV1
    data class Stale(val currentRevision: Long?) : EventEditorOpenResultV1
    data object Missing : EventEditorOpenResultV1
    data object Locked : EventEditorOpenResultV1
    data class Invalid(val code: String, val message: String) : EventEditorOpenResultV1
}

enum class EventEditorUpdateStatusV1 { UPDATED, NOT_FOUND, LOCKED, INVALID }

data class EventEditorUpdateReceiptV1(
    val status: EventEditorUpdateStatusV1,
    val errorCode: String? = null,
    val message: String? = null
)

enum class EventValidationStatusV1 { READY, INCOMPLETE, INVALID, NOT_FOUND }

data class EventValidationResultV1(
    val status: EventValidationStatusV1,
    val missingFields: List<String> = emptyList(),
    val errorCode: String? = null,
    val message: String? = null
)

enum class EventMutationStatusV1 {
    CREATED,
    STAGED,
    UPDATED,
    DELETED,
    NO_CHANGE,
    CONFLICT,
    INVALID,
    LOCKED,
    NEEDS_RESTORE,
    NOT_AUTHORIZED,
    FAILED,
    DUPLICATE_REQUEST_ID
}

data class EventMutationReceiptV1(
    val requestId: String,
    val status: EventMutationStatusV1,
    val eventSeq: Long? = null,
    val revision: Long? = null,
    val draftId: Long? = null,
    val draftVersion: Long? = null,
    val relatedEventSeq: Long? = null,
    val duplicate: Boolean = false,
    val errorCode: String? = null,
    val message: String? = null,
    val refreshRequired: Boolean = false
)

data class EventCheckpointReceiptV1(
    val status: EventMutationStatusV1,
    val eventSeq: Long? = null,
    val draftId: Long? = null,
    val draftVersion: Long? = null,
    val message: String? = null
)

/** Marker capability. Only the trusted Android Host issues concrete instances. */
interface EventDeleteApprovalV1 {
    val eventSeq: Long
    val expectedRevision: Long
}

data class EventQuerySnapshotV1(
    val featureRevision: Long,
    val events: List<EventRecordV1>
)

/** Opaque continuation within one Event API instance and feature revision. */
data class EventQueryCursorV1(
    val featureRevision: Long,
    val afterAnchorMillis: Long?,
    val afterEventSeq: Long,
    val serviceInstanceId: String
)

/** Bounded, axis-ordered page of active formal event DTOs. */
data class EventQueryPageV1(
    val featureRevision: Long,
    val events: List<EventRecordV1>,
    val totalEvents: Int,
    val nextCursor: EventQueryCursorV1? = null,
    val stale: Boolean = false
)

/** Minimal formal-event match projection; search consumers do not receive Core entities. */
data class EventSearchHitV1(
    val eventSeq: Long,
    val revision: Long,
    val kind: EventKindV1,
    val title: String
)

data class EventSearchSnapshotV1(
    val featureRevision: Long,
    val hits: List<EventSearchHitV1>
)

enum class EventFeatureChangeTypeV1 {
    FORMAL_CREATED, FORMAL_UPDATED, FORMAL_DELETED, DRAFTS_CHANGED, STORE_RESTORED
}

data class EventFeatureChangeV1(
    val featureRevision: Long,
    val type: EventFeatureChangeTypeV1,
    val eventSeq: Long? = null,
    val eventRevision: Long? = null
)

/** Versioned functional contract. It contains no Android, Compose, Store, Bridge, or Core types. */
interface EventFeatureApiV1 {
    val apiVersion: Int
    val featureRevision: Long

    /** Reclaims only another process's editor lease after its timeout; perform Store I/O off the UI thread. */
    fun recoverAbandonedHumanEdit(staleAfterMillis: Long = 5 * 60_000L): Boolean

    fun queryEvents(): EventQuerySnapshotV1
    /** Returns at most [limit] events in the same axis-anchor order as `queryEvents()`. */
    fun queryEventsPage(
        cursor: EventQueryCursorV1? = null,
        limit: Int = 100
    ): EventQueryPageV1
    /** Reads one current formal event as a detached DTO; missing and retired identities return null. */
    fun queryEvent(eventSeq: Long): EventRecordV1?
    /** Searches an API snapshot without doing synchronous Store I/O; results keep its revision. */
    fun searchEvents(query: String, snapshot: EventQuerySnapshotV1): EventSearchSnapshotV1
    /** Store-backed search; call from an I/O coroutine. Preserves the axis order and eight-hit cap. */
    suspend fun searchEvents(query: String): EventSearchSnapshotV1
    /** Explicitly set an occurrence's completion state against its observed revision and state. */
    fun setOccurrenceCompletion(
        eventSeq: Long,
        expectedRevision: Long,
        occurrenceKey: String,
        expectedCompleted: Boolean,
        completed: Boolean,
        requestId: String
    ): EventMutationReceiptV1
    fun openNewEvent(
        kind: EventKindV1,
        zoneId: String,
        selection: EventSelectionV1? = null
    ): EventEditorOpenResultV1
    fun openFormalEvent(
        eventSeq: Long,
        expectedRevision: Long,
        occurrenceKey: String? = null
    ): EventEditorOpenResultV1
    fun editorSnapshot(handle: EventEditorHandleV1): EventEditorSnapshotV1?
    fun validateNewEvent(handle: EventEditorHandleV1): EventValidationResultV1

    fun updateNewEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1
    ): EventEditorUpdateReceiptV1
    fun updateFormalEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1
    ): EventEditorUpdateReceiptV1
    fun stageFormalReplacement(
        eventSeq: Long,
        expectedRevision: Long,
        form: EventFormV1,
        requestId: String
    ): EventMutationReceiptV1
    fun selectFormalRecurrenceScope(
        handle: EventEditorHandleV1,
        scope: EventRecurrenceScopeV1
    ): EventEditorUpdateReceiptV1
    fun discardFormalChanges(handle: EventEditorHandleV1): EventEditorUpdateReceiptV1

    fun checkpointNewEvent(
        handle: EventEditorHandleV1,
        requestId: String
    ): EventCheckpointReceiptV1
    fun checkpointFormalEvent(
        handle: EventEditorHandleV1,
        requestId: String
    ): EventCheckpointReceiptV1
    fun saveNewEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1,
        requestId: String
    ): EventMutationReceiptV1
    fun saveFormalEvent(
        handle: EventEditorHandleV1,
        form: EventFormV1,
        requestId: String
    ): EventMutationReceiptV1
    fun deleteFormalEvent(
        handle: EventEditorHandleV1,
        approval: EventDeleteApprovalV1,
        requestId: String
    ): EventMutationReceiptV1

    fun closeNewEvent(handle: EventEditorHandleV1): EventCheckpointReceiptV1
    fun discardNewEvent(handle: EventEditorHandleV1): Boolean
    fun closeFormalEvent(handle: EventEditorHandleV1)
    fun heartbeatFormalEvent(handle: EventEditorHandleV1): Boolean
    fun isFormalEventCurrent(handle: EventEditorHandleV1): Boolean

    fun subscribe(listener: (EventFeatureChangeV1) -> Unit): AutoCloseable
}

interface CoreFeatureApiV1 {
    val apiVersion: Int
    val calendar: CalendarFeatureApiV1
    val events: EventFeatureApiV1
    val recurrence: RecurrenceFeatureApiV1
    val draftReview: DraftReviewFeatureApiV1
    val ai: AiFeatureApiV1
    val aiProposals: AiProposalFeatureApiV1
    val reminders: ReminderFeatureApiV1
    val importExport: ImportExportFeatureApiV1
    val settingsData: SettingsDataFeatureApiV1
    val diagnostics: CoreDiagnosticsFeatureApiV1
    val memos: MemoFeatureApiV1
}
