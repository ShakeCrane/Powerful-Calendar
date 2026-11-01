package dev.powerfulcalendar.core.api.v1

/** Stable vocabulary for persisted proposal cards. */
enum class DraftOperationV1 {
    CREATE, REPLACE, CONVERT, DELETE;
    val requiresExisting: Boolean get() = this != CREATE
}
enum class DraftStatusV1 { PENDING, CONFLICT, DISCARDED, COMMITTED, INVALID }

data class DraftReviewCardV1(
    val draftId: Long,
    val eventSeq: Long,
    val queueOrder: Long,
    val operation: DraftOperationV1,
    val form: EventFormV1,
    val status: DraftStatusV1,
    val baseRevision: Long?,
    val draftVersion: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class DraftReviewFieldChangeV1(
    val label: String,
    val before: String,
    val after: String
)

data class DraftReviewConflictV1(
    val draftId: Long,
    val eventSeq: Long,
    val current: EventRecordV1?,
    val changes: List<DraftReviewFieldChangeV1>,
    val card: DraftReviewCardV1,
    val currentRevision: Long?,
    /** Exact candidate shown for approval; any change requires a new review. */
    val approvedForm: EventFormV1
)

data class DraftReviewRestoreV1(
    val eventSeq: Long,
    val card: DraftReviewCardV1,
    val message: String
)

data class DraftReviewCompletionV1(
    val published: Int,
    val discarded: Int,
    val conflicts: Int
)

/** Immutable state projection. It contains no Core, database, Android, or presentation types. */
data class DraftReviewSnapshotV1(
    val cards: List<DraftReviewCardV1> = emptyList(),
    val currentId: Long? = null,
    val current: DraftReviewCardV1? = null,
    val form: EventFormV1? = null,
    val version: Long = 0L,
    /** Monotonic editor-state revision; unlike card.draftVersion it advances for unsaved edits. */
    val stateRevision: Long = 0L,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val aiLocked: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
    val conflict: DraftReviewConflictV1? = null,
    val restore: DraftReviewRestoreV1? = null,
    val completion: DraftReviewCompletionV1? = null,
    val lastDiscardId: Long? = null,
    val busy: Boolean = false
) {
    val position: Int get() = cards.indexOfFirst { it.draftId == currentId }.coerceAtLeast(0)
    val canGoPrevious: Boolean get() = position > 0
    val canGoNext: Boolean get() = position < cards.lastIndex
    val canUndoDiscard: Boolean get() = lastDiscardId != null
}

enum class DraftReviewReceiptStatusV1 {
    APPLIED,
    NO_CHANGE,
    REVIEW_REQUIRED,
    RESTORE_REQUIRED,
    CONFLICT,
    LOCKED,
    NOT_FOUND,
    INVALID,
    FAILED,
    DUPLICATE_REQUEST_ID
}

data class DraftReviewReceiptV1(
    val requestId: String,
    val status: DraftReviewReceiptStatusV1,
    val draftId: Long? = null,
    val eventSeq: Long? = null,
    val revision: Long? = null,
    val draftVersion: Long? = null,
    val featureRevision: Long = 0L,
    val duplicate: Boolean = false,
    val errorCode: String? = null,
    val message: String? = null
) {
    val succeeded: Boolean get() = status == DraftReviewReceiptStatusV1.APPLIED ||
        status == DraftReviewReceiptStatusV1.NO_CHANGE
}

/** Shared versioned command surface for Native and Web draft review. */
interface DraftReviewFeatureApiV1 {
    val apiVersion: Int
    val snapshot: DraftReviewSnapshotV1

    fun observe(listener: (DraftReviewSnapshotV1) -> Unit): AutoCloseable
    fun editCurrentDraft(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        form: EventFormV1
    ): DraftReviewReceiptV1

    fun dismissConflict()
    fun dismissRestore()
    fun dismissCompletion()
    fun clearError()
    fun setAiLocked(locked: Boolean)

    suspend fun open(preferDraftId: Long? = null): DraftReviewSnapshotV1
    suspend fun refresh(preferDraftId: Long? = null): DraftReviewSnapshotV1
    /** Persist one explicit user-owned draft without publishing a formal event. */
    suspend fun createDraft(form: EventFormV1, requestId: String): DraftReviewReceiptV1
    suspend fun beginFormalReplacement(
        eventSeq: Long,
        expectedRevision: Long,
        requestId: String
    ): DraftReviewReceiptV1
    suspend fun beginRestore(
        eventSeq: Long,
        expectedRevision: Long,
        requestId: String
    ): DraftReviewReceiptV1
    suspend fun flush(requestId: String, lockEdits: Boolean = true): DraftReviewReceiptV1
    suspend fun select(draftId: Long, requestId: String): DraftReviewReceiptV1
    suspend fun move(delta: Int, requestId: String): DraftReviewReceiptV1
    suspend fun jump(steps: Int, requestId: String): DraftReviewReceiptV1
    suspend fun commit(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        requestId: String
    ): DraftReviewReceiptV1
    suspend fun confirmConflictReplacement(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        expectedCurrentRevision: Long,
        requestId: String
    ): DraftReviewReceiptV1
    suspend fun requestRestore(
        draftId: Long,
        expectedDraftVersion: Long,
        expectedStateRevision: Long,
        expectedTombstoneRevision: Long,
        requestId: String
    ): DraftReviewReceiptV1
    suspend fun discard(
        draftId: Long,
        expectedDraftVersion: Long,
        requestId: String
    ): DraftReviewReceiptV1
    suspend fun undoDiscard(draftId: Long, requestId: String): DraftReviewReceiptV1
}
