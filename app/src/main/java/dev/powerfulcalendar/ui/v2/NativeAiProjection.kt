package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.api.v1.AiConversationMessageV1
import dev.powerfulcalendar.core.api.v1.AiConversationRoleV1
import dev.powerfulcalendar.core.api.v1.AiProposalReviewReadinessV1

internal fun projectNativeAiMessages(messages: List<AiConversationMessageV1>): List<NativeAiMessage> =
    messages.map { message ->
        NativeAiMessage(
            mine = message.role == AiConversationRoleV1.USER,
            content = message.content,
            proposalSource = message.proposalSource
        )
    }

internal fun projectNativeAiProposalReview(
    interpretation: AiInterpretation,
    readiness: AiProposalReviewReadinessV1?
): NativeAiProposalReviewModel = NativeAiProposalReviewModel(
    rows = interpretation.proposals.mapIndexed { position, proposal ->
        val coreReadiness = readiness?.items?.getOrNull(position)
            ?.takeIf { it.sourceIndex == proposal.sourceIndex }
        NativeAiProposalRow(
            position = position,
            group = if (proposal.operation == DraftOperation.CREATE) {
                NativeAiProposalGroup.CREATE
            } else {
                NativeAiProposalGroup.EXISTING
            },
            title = proposal.form.title,
            caption = if (proposal.operation == DraftOperation.CREATE) proposal.form.kind.label else {
                proposal.operation.label + (proposal.eventSeq?.let { " #$it" } ?: "")
            },
            startText = proposal.form.startText,
            endText = proposal.form.endText,
            blockedReason = proposal.blocked,
            publishable = coreReadiness?.canPublish == true,
            readinessMessage = coreReadiness?.message
        )
    },
    warnings = interpretation.warnings.take(2)
)

internal fun projectNativeAiBatchAuthorization(
    mode: AiBatchRunner.Mode,
    cards: List<DraftCard>
): NativeAiBatchAuthorizationModel = NativeAiBatchAuthorizationModel(
    mode = if (mode == AiBatchRunner.Mode.PUBLISH) NativeAiBatchMode.PUBLISH else NativeAiBatchMode.DISCARD,
    count = cards.size,
    operationCounts = cards.groupingBy { it.operation }.eachCount().map { (operation, count) ->
        NativeAiBatchOperationCount(operation.label, count)
    },
    containsFormalDeletion = mode == AiBatchRunner.Mode.PUBLISH &&
        cards.any { it.operation == DraftOperation.DELETE },
    rows = cards.map { card ->
        NativeAiBatchReviewRow(
            sequenceLabel = TemporalRecord.formatSeq(card.eventSeq),
            operationLabel = card.operation.label,
            title = card.form.title
        )
    }
)

internal fun hostAiBatchMode(mode: NativeAiBatchMode): AiBatchRunner.Mode = when (mode) {
    NativeAiBatchMode.PUBLISH -> AiBatchRunner.Mode.PUBLISH
    NativeAiBatchMode.DISCARD -> AiBatchRunner.Mode.DISCARD
}
