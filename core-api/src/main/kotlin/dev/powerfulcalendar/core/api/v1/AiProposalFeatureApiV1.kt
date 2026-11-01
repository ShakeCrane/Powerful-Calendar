package dev.powerfulcalendar.core.api.v1

/** Stable decision vocabulary for AI generated proposal cards. */
enum class AiProposalDecisionV1 { STAGE, PUBLISH }

enum class AiProposalApplyStatusV1 {
    APPLIED,
    PARTIAL,
    CONFLICT,
    INVALID,
    EXPIRED,
    LOCKED,
    NOT_AUTHORIZED,
    FAILED,
    DUPLICATE_REQUEST_ID
}

enum class AiProposalItemStatusV1 {
    STAGED,
    PUBLISHED,
    ALREADY_RESOLVED,
    CONFLICT,
    BLOCKED,
    FAILED
}

enum class AiProposalItemReceiptDurabilityV1 { DURABLE_ITEM, PROCESS_LOCAL }

/** Minimal task identity needed to retain PowerStore's durable task/item proposal receipts. */
data class AiProposalTaskContextV1(
    val taskId: String,
    val revision: Int,
    val anchorMillis: Long,
    val latestEvidenceMillis: Long
)

/** Detached candidate shown by the trusted Host review surface. Positions address this list. */
data class AiProposalItemV1(
    val sourceIndex: Int,
    val operation: DraftOperationV1,
    val eventSeq: Long?,
    val expectedRevision: Long?,
    val form: EventFormV1,
    val rationale: String = "",
    val blockedReason: String? = null
)

data class AiProposalReviewV1(
    val reviewId: String,
    val items: List<AiProposalItemV1>,
    val warnings: List<String> = emptyList(),
    val task: AiProposalTaskContextV1? = null
)

data class AiProposalApplyRequestV1(
    val requestId: String,
    val review: AiProposalReviewV1,
    val selectedPositions: Set<Int>,
    val decision: AiProposalDecisionV1
)

/** Marker approval. Concrete instances are issued by internal Host code and tracked by identity. */
interface AiProposalPublishApprovalV1

data class AiProposalItemReceiptV1(
    val position: Int,
    val sourceIndex: Int,
    val operation: DraftOperationV1,
    val status: AiProposalItemStatusV1,
    val eventSeq: Long? = null,
    val draftId: Long? = null,
    val draftVersion: Long? = null,
    val revision: Long? = null,
    val commitId: String? = null,
    val changed: Boolean = false,
    val message: String? = null
)

data class AiProposalApplyReceiptV1(
    val requestId: String,
    val status: AiProposalApplyStatusV1,
    val handledPositions: Set<Int> = emptySet(),
    val stagedCount: Int = 0,
    val publishedCount: Int = 0,
    val alreadyResolvedCount: Int = 0,
    val itemReceipts: List<AiProposalItemReceiptV1> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errorCode: String? = null,
    val message: String? = null,
    val featureRevision: Long = 0L,
    /** Persistence of per-item retry state; the outer request cache is always process-local. */
    val itemReceiptDurability: AiProposalItemReceiptDurabilityV1 =
        AiProposalItemReceiptDurabilityV1.PROCESS_LOCAL,
    val duplicate: Boolean = false,
    val refreshRequired: Boolean = false
)

/** AI proposal writes remain separate from AI batch selection, locking, cancellation, and recovery. */
interface AiProposalFeatureApiV1 {
    val apiVersion: Int

    /** Create draft cards after an explicit Host action; no formal event is published. */
    fun createDrafts(review: AiProposalReviewV1, requestId: String): AiProposalApplyReceiptV1

    /** Apply only selected, Host-reviewed positions. PUBLISH requires a Host-issued approval. */
    fun applyReviewedProposals(
        request: AiProposalApplyRequestV1,
        approval: AiProposalPublishApprovalV1? = null
    ): AiProposalApplyReceiptV1
}
