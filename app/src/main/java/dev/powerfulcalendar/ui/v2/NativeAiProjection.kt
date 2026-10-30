package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.core.DraftOperation

internal fun projectNativeAiMessages(messages: List<AiChatMessage>): List<NativeAiMessage> =
    messages.map { message ->
        NativeAiMessage(
            mine = message.role == "user",
            content = message.content,
            proposalSource = message.proposalSource
        )
    }

internal fun projectNativeAiProposalReview(
    interpretation: AiInterpretation
): NativeAiProposalReviewModel = NativeAiProposalReviewModel(
    rows = interpretation.proposals.mapIndexed { position, proposal ->
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
            publishable = proposal.blocked == null &&
                (proposal.operation == DraftOperation.DELETE || proposal.form.isPublishable())
        )
    },
    warnings = interpretation.warnings.take(2)
)

internal fun hostAiBatchMode(mode: NativeAiBatchMode): AiBatchRunner.Mode = when (mode) {
    NativeAiBatchMode.PUBLISH -> AiBatchRunner.Mode.PUBLISH
    NativeAiBatchMode.DISCARD -> AiBatchRunner.Mode.DISCARD
}
