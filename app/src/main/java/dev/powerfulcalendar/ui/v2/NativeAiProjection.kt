package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatMessage

internal fun projectNativeAiMessages(messages: List<AiChatMessage>): List<NativeAiMessage> =
    messages.map { message ->
        NativeAiMessage(
            mine = message.role == "user",
            content = message.content,
            proposalSource = message.proposalSource
        )
    }

internal fun hostAiBatchMode(mode: NativeAiBatchMode): AiBatchRunner.Mode = when (mode) {
    NativeAiBatchMode.PUBLISH -> AiBatchRunner.Mode.PUBLISH
    NativeAiBatchMode.DISCARD -> AiBatchRunner.Mode.DISCARD
}
