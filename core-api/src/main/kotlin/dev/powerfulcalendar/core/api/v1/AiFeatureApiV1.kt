package dev.powerfulcalendar.core.api.v1

/** The AI API returns proposals and may execute only the exact batch approved by the Host. */
enum class AiProposalGenerationStatusV1 {
    READY,
    NO_PROPOSALS,
    INVALID_REQUEST,
    MODEL_UNAVAILABLE
}

enum class AiConversationRoleV1 { USER, ASSISTANT }

/** Detached transcript evidence; proposal markers are display metadata, never authorization. */
data class AiConversationMessageV1(
    val role: AiConversationRoleV1,
    val content: String,
    val proposalSource: String? = null,
    val messageId: String,
    val createdAtMillis: Long? = null,
    val zoneId: String? = null
)

data class AiConversationSnapshotV1(
    val revision: Long,
    val messages: List<AiConversationMessageV1>
)

enum class AiConversationMutationStatusV1 {
    APPLIED,
    NO_CHANGE,
    STALE,
    MISSING_MESSAGE,
    AMBIGUOUS_MESSAGE,
    EDIT_REQUIRES_USER_MESSAGE,
    INVALID
}

enum class AiConversationRewindModeV1 { RETRACT, EDIT_AND_RESEND }

data class AiConversationMutationReceiptV1(
    val status: AiConversationMutationStatusV1,
    val snapshot: AiConversationSnapshotV1,
    /** Populated only for EDIT_AND_RESEND on a user message. */
    val prefillText: String? = null
)

enum class AiConversationPersistenceStatusV1 { SAVED, STALE, UNAVAILABLE }

/** Host-owned persistence adapter. The Core API keeps the canonical in-memory state. */
interface AiConversationHistoryPortV1 {
    fun load(): List<AiConversationMessageV1>
    suspend fun save(messages: List<AiConversationMessageV1>)
}

enum class AiChatProposalIntentV1 { EXPLICIT, SUGGESTED }

data class AiChatRequestV1(
    val requestId: String,
    val userMessageId: String,
    val expectedConversationRevision: Long,
    val zoneId: String,
    /** Host opt-in; the Android provider adapter rechecks the persisted sharing preference. */
    val shareCalendarContext: Boolean = false
)

/** Provider-only input assembled from the canonical Core conversation snapshot. */
data class AiChatModelRequestV1(
    val requestId: String,
    val history: List<AiConversationMessageV1>,
    val zoneId: String,
    val shareCalendarContext: Boolean = false
)

enum class AiChatStatusV1 {
    COMPLETED,
    INVALID_REQUEST,
    MODEL_UNAVAILABLE,
    LOCKED,
    DUPLICATE_REQUEST_ID,
    STALE_CONVERSATION
}

/** Provider result. Task and intent are untrusted model-derived evidence, not write authority. */
data class AiChatModelOutputV1(
    val reply: String = "",
    val error: String? = null,
    val proposalSource: String? = null,
    val automaticProposalTask: AiConversationTaskV1? = null,
    val proposalIntent: AiChatProposalIntentV1? = null
)

data class AiChatReceiptV1(
    val requestId: String,
    val status: AiChatStatusV1,
    val reply: String = "",
    val proposalSource: String? = null,
    val proposalIntent: AiChatProposalIntentV1? = null,
    val automaticProposalTask: AiConversationTaskV1? = null,
    val automaticProposal: AiProposalGenerationReceiptV1? = null,
    val clarification: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
    /** True only when this process returns a previously completed receipt for the same request. */
    val replayed: Boolean = false
)

data class AiTaskEvidenceV1(
    val messageId: String,
    val text: String,
    val createdAtMillis: Long,
    val zoneId: String
)

data class AiAssistantSuggestionV1(
    val messageId: String,
    val text: String
)

/** Conversation facts are input evidence; assistant suggestions remain explicitly untrusted. */
data class AiConversationTaskV1(
    val taskId: String,
    val revision: Int,
    val anchorMillis: Long,
    val zoneId: String,
    val userEvidence: List<AiTaskEvidenceV1>,
    val assistantSuggestions: List<AiAssistantSuggestionV1>
)

enum class AiCalendarContextPolicyV1 {
    FIRST_60_FORMAL_EVENTS,
    EVENTS_THROUGH_14_DAYS_AFTER_NOW
}

data class AiProposalGenerationRequestV1(
    val requestId: String,
    val userText: String? = null,
    val task: AiConversationTaskV1? = null,
    val zoneId: String,
    /** Host-controlled opt-in; the provider adapter also enforces the persisted sharing setting. */
    val shareCalendarContext: Boolean = false,
    val contextPolicy: AiCalendarContextPolicyV1 = AiCalendarContextPolicyV1.FIRST_60_FORMAL_EVENTS
)

data class AiProposalGenerationReceiptV1(
    val requestId: String,
    val status: AiProposalGenerationStatusV1,
    val review: AiProposalReviewV1? = null,
    val sharedEventCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val errorCode: String? = null,
    val message: String? = null
)

/** Trusted provider-adapter metadata; never copied from model-generated content. */
data class AiProposalModelOutputV1(
    val review: AiProposalReviewV1,
    val contextUsed: Boolean
)

enum class AiBatchModeV1 { PUBLISH, DISCARD }
enum class AiBatchStateV1 {
    REQUESTED,
    RUNNING,
    CANCEL_REQUESTED,
    COMPLETED,
    CANCELLED,
    FAILED,
    INTERRUPTED
}

data class AiBatchCandidateV1(
    val draftId: Long,
    val draftVersion: Long,
    val operation: DraftOperationV1,
    val kind: EventKindV1,
    val title: String
)

data class AiBatchSelectionRequestV1(
    val userRequest: String,
    val candidates: List<AiBatchCandidateV1>,
    /** Set only by the Host for its exact-match “all” actions; this bypasses model selection. */
    val selectAll: Boolean = false
)

enum class AiBatchSelectionStatusV1 { READY, NEEDS_CLARIFICATION, INVALID, MODEL_UNAVAILABLE }

data class AiBatchSelectionReceiptV1(
    val status: AiBatchSelectionStatusV1,
    /** Exact candidate snapshots, including versions and operation types, for Host review. */
    val selected: List<AiBatchCandidateV1> = emptyList(),
    val reply: String = "",
    val question: String? = null,
    val message: String? = null
)

/** The model may return IDs only. The feature API resolves them back to this local snapshot. */
data class AiBatchModelSelectionV1(
    val selectedDraftIds: List<Long> = emptyList(),
    val reply: String = "",
    val question: String? = null,
    val error: String? = null
)

data class AiBatchReviewV1(
    val mode: AiBatchModeV1,
    val items: List<AiBatchCandidateV1>
)

/** Sanitized persisted outcome; exception text and private draft identities stay in the Host. */
data class AiBatchTerminalReportV1(
    val mode: AiBatchModeV1? = null,
    val outcomeKnown: Boolean,
    val published: Int? = null,
    val discarded: Int? = null,
    val kept: Int? = null,
    val cancelled: Boolean? = null,
    val hasFailures: Boolean = false,
    val releasedByUser: Boolean = false,
    val finishedAtMillis: Long? = null
)

/** Marker instances are issued internally by trusted Host code after the review UI confirms. */
interface AiBatchApprovalV1
interface AiBatchStuckReleaseApprovalV1

data class AiBatchSessionV1(
    val sessionId: String,
    val lockEpoch: Long,
    val scope: Set<Long>,
    val reviewedVersions: Map<Long, Long> = emptyMap(),
    val allowedOperations: Set<DraftOperationV1>,
    val state: AiBatchStateV1,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val terminalReport: AiBatchTerminalReportV1? = null,
    val canReleaseStuck: Boolean = false
) {
    val isActive: Boolean
        get() = state == AiBatchStateV1.RUNNING || state == AiBatchStateV1.CANCEL_REQUESTED
}

data class AiBatchStateSnapshotV1(
    val locked: Boolean,
    val session: AiBatchSessionV1?
)

data class AiBatchProgressV1(
    val sessionId: String,
    val lockEpoch: Long,
    val mode: AiBatchModeV1,
    val completed: Int,
    val total: Int
)

enum class AiBatchCommandStatusV1 {
    CANCEL_REQUESTED,
    ALREADY_CANCEL_REQUESTED,
    RELEASED,
    NO_ACTIVE_SESSION,
    STALE,
    NOT_AUTHORIZED,
    NOT_ELIGIBLE,
    FAILED
}

data class AiBatchCommandReceiptV1(
    val status: AiBatchCommandStatusV1,
    val sessionId: String? = null,
    val message: String? = null
)

enum class AiBatchExecutionStatusV1 { COMPLETED, CANCELLED, FAILED, START_REJECTED }

data class AiBatchExecutionReceiptV1(
    val status: AiBatchExecutionStatusV1,
    val sessionId: String? = null,
    val lockEpoch: Long? = null,
    val published: Int = 0,
    val discarded: Int = 0,
    val kept: Int = 0,
    val failures: List<String> = emptyList(),
    val message: String? = null
)

/** Host adapter for provider calls; implementations own configuration and credentials. */
interface AiModelPortV1 {
    suspend fun chat(
        request: AiChatModelRequestV1,
        calendarContext: List<EventRecordV1>
    ): AiChatModelOutputV1

    suspend fun generateProposal(
        request: AiProposalGenerationRequestV1,
        calendarContext: List<EventRecordV1>
    ): AiProposalModelOutputV1

    suspend fun selectBatchDrafts(
        userRequest: String,
        candidates: List<AiBatchCandidateV1>
    ): AiBatchModelSelectionV1
}

/** Versioned AI domain commands, state, and model-selection boundary. */
interface AiFeatureApiV1 {
    val apiVersion: Int

    /** Runs one bounded ordinary conversation turn and returns review-only proposal output. */
    suspend fun chat(request: AiChatRequestV1): AiChatReceiptV1

    fun queryConversation(): AiConversationSnapshotV1
    fun appendConversationMessage(
        message: AiConversationMessageV1,
        expectedRevision: Long
    ): AiConversationMutationReceiptV1
    fun clearConversation(expectedRevision: Long): AiConversationMutationReceiptV1
    fun rewindConversation(
        messageId: String,
        mode: AiConversationRewindModeV1,
        expectedRevision: Long
    ): AiConversationMutationReceiptV1
    suspend fun persistConversation(expectedRevision: Long): AiConversationPersistenceStatusV1

    /** Generates reviewable proposals only; it never stages or publishes a write. */
    suspend fun generateProposal(request: AiProposalGenerationRequestV1): AiProposalGenerationReceiptV1

    /** Read-only model selection. A successful result still requires exact Host review. */
    suspend fun selectBatchDrafts(request: AiBatchSelectionRequestV1): AiBatchSelectionReceiptV1

    fun queryBatchState(): AiBatchStateSnapshotV1
    fun requestBatchCancellation(sessionId: String): AiBatchCommandReceiptV1
    fun recoverInterruptedBatch(): Boolean

    /** Begins and runs one exact, Host-reviewed scope. The approval can be consumed only once. */
    suspend fun runReviewedBatch(
        review: AiBatchReviewV1,
        approval: AiBatchApprovalV1,
        onProgress: (AiBatchProgressV1) -> Unit = {}
    ): AiBatchExecutionReceiptV1

    fun releaseStuckBatch(
        sessionId: String,
        lockEpoch: Long,
        approval: AiBatchStuckReleaseApprovalV1
    ): AiBatchCommandReceiptV1
}
