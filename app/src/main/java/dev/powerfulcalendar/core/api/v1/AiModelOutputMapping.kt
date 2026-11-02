package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.ai.AiConversationTaskSnapshot
import dev.powerfulcalendar.ai.AiInterpretation

/** Maps untrusted provider/parser output into detached Feature API review DTOs. */
internal fun AiInterpretation.toAiProposalReviewV1(
    reviewId: String,
    expectedTargetRevisions: Map<Long, Long>
): AiProposalReviewV1 = AiProposalReviewV1(
    reviewId = reviewId,
    items = proposals.map { proposal ->
        AiProposalItemV1(
            sourceIndex = proposal.sourceIndex,
            operation = DraftOperationV1.valueOf(proposal.operation.name),
            eventSeq = proposal.eventSeq,
            expectedRevision = proposal.eventSeq?.let(expectedTargetRevisions::get),
            form = proposal.form.toEventFormV1(),
            rationale = proposal.rationale,
            blockedReason = proposal.blocked
        )
    },
    warnings = warnings.toList(),
    task = taskSnapshot?.let { task ->
        AiProposalTaskContextV1(
            taskId = task.taskId,
            revision = task.revision,
            anchorMillis = task.anchorMillis,
            latestEvidenceMillis = task.userEvidence.maxOfOrNull { it.createdAtMillis }
                ?: task.anchorMillis
        )
    }
)

/** Converts trusted task context; target-event revisions are bound later by the Store adapter. */
internal fun AiConversationTaskSnapshot.toAiConversationTaskV1() = AiConversationTaskV1(
    taskId = taskId,
    revision = revision,
    anchorMillis = anchorMillis,
    zoneId = zoneId,
    userEvidence = userEvidence.map { evidence ->
        AiTaskEvidenceV1(
            messageId = evidence.messageId,
            text = evidence.text,
            createdAtMillis = evidence.createdAtMillis,
            zoneId = evidence.zoneId
        )
    },
    assistantSuggestions = assistantSuggestions.map { suggestion ->
        AiAssistantSuggestionV1(suggestion.messageId, suggestion.text)
    }
)
