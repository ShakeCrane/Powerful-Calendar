package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiConversationTaskSnapshot
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiTaskAssistantSuggestion
import dev.powerfulcalendar.ai.AiTaskEvidence
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.api.v1.AiBatchCandidateV1
import dev.powerfulcalendar.core.api.v1.AiBatchModeV1
import dev.powerfulcalendar.core.api.v1.AiProposalItemV1
import dev.powerfulcalendar.core.api.v1.AiProposalReviewV1
import dev.powerfulcalendar.core.api.v1.AiProposalTaskContextV1
import dev.powerfulcalendar.core.api.v1.AiAssistantSuggestionV1
import dev.powerfulcalendar.core.api.v1.AiConversationMessageV1
import dev.powerfulcalendar.core.api.v1.AiConversationRoleV1
import dev.powerfulcalendar.core.api.v1.AiConversationTaskV1
import dev.powerfulcalendar.core.api.v1.DraftOperationV1
import dev.powerfulcalendar.core.api.v1.DraftReviewCardV1
import dev.powerfulcalendar.core.api.v1.AiTaskEvidenceV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
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

/** Host presentation adapter; the stable API DTO remains the source for target revisions. */
internal fun AiProposalReviewV1.toAiInterpretation(
    taskSnapshot: AiConversationTaskSnapshot? = null
): AiInterpretation = AiInterpretation(
    proposals = items.map { item ->
        AiProposal(
            operation = DraftOperation.valueOf(item.operation.name),
            eventSeq = item.eventSeq,
            form = item.form.toCoreDraftForm(),
            rationale = item.rationale,
            blocked = item.blockedReason,
            sourceIndex = item.sourceIndex,
            expectedRevision = item.expectedRevision
        )
    },
    warnings = warnings.toList(),
    taskSnapshot = taskSnapshot
)

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

internal fun AiConversationTaskV1.toAiConversationTaskSnapshot() = AiConversationTaskSnapshot(
    taskId = taskId,
    revision = revision,
    anchorMillis = anchorMillis,
    zoneId = zoneId,
    userEvidence = userEvidence.map { evidence ->
        AiTaskEvidence(
            messageId = evidence.messageId,
            text = evidence.text,
            createdAtMillis = evidence.createdAtMillis,
            zoneId = evidence.zoneId
        )
    },
    assistantSuggestions = assistantSuggestions.map { suggestion ->
        AiTaskAssistantSuggestion(suggestion.messageId, suggestion.text)
    }
)

internal fun AiChatMessage.toAiConversationMessageV1() = AiConversationMessageV1(
    role = if (role == "user") AiConversationRoleV1.USER else AiConversationRoleV1.ASSISTANT,
    content = content,
    proposalSource = proposalSource,
    messageId = messageId,
    createdAtMillis = createdAtMillis,
    zoneId = zoneId
)

internal fun AiConversationMessageV1.toAiChatMessage() = AiChatMessage(
    role = if (role == AiConversationRoleV1.USER) "user" else "assistant",
    content = content,
    proposalSource = proposalSource,
    messageId = messageId,
    createdAtMillis = createdAtMillis,
    zoneId = zoneId
)

internal fun DraftCard.toAiBatchCandidateV1() = AiBatchCandidateV1(
    draftId = draftId,
    draftVersion = draftVersion,
    operation = DraftOperationV1.valueOf(operation.name),
    kind = dev.powerfulcalendar.core.api.v1.EventKindV1.valueOf(form.kind.name),
    title = form.title
)

/** Host-only projection used by the existing trusted batch review surface. */
internal fun DraftReviewCardV1.toHostDraftCard() = DraftCard(
    draftId = draftId,
    eventSeq = eventSeq,
    queueOrder = queueOrder,
    operation = DraftOperation.valueOf(operation.name),
    form = form.toCoreDraftForm(),
    status = DraftStatus.valueOf(status.name),
    baseRevision = baseRevision,
    draftVersion = draftVersion,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

internal fun AiBatchRunner.Mode.toAiBatchModeV1() = AiBatchModeV1.valueOf(name)

internal fun AiBatchModeV1.toRunnerMode() = AiBatchRunner.Mode.valueOf(name)
