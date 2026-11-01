package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.core.api.v1.AiProposalItemV1
import dev.powerfulcalendar.core.api.v1.AiProposalReviewV1
import dev.powerfulcalendar.core.api.v1.AiProposalTaskContextV1
import dev.powerfulcalendar.core.api.v1.DraftOperationV1
import dev.powerfulcalendar.core.api.v1.toEventFormV1

/** Host conversion from parser-owned proposals to the stable Core Feature API DTOs. */
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
