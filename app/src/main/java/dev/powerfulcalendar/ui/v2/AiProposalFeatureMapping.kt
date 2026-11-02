package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiConversationTaskSnapshot
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiTaskAssistantSuggestion
import dev.powerfulcalendar.ai.AiTaskEvidence
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.api.v1.AiBatchCandidateV1
import dev.powerfulcalendar.core.api.v1.AiProposalReviewV1
import dev.powerfulcalendar.core.api.v1.AiConversationMessageV1
import dev.powerfulcalendar.core.api.v1.AiConversationRoleV1
import dev.powerfulcalendar.core.api.v1.AiConversationTaskV1
import dev.powerfulcalendar.core.api.v1.DraftReviewCardV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm

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

internal fun DraftReviewCardV1.toAiBatchCandidateV1() = AiBatchCandidateV1(
    draftId = draftId,
    draftVersion = draftVersion,
    operation = operation,
    kind = form.kind,
    title = form.title
)
