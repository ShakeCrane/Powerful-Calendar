package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAiProjectionTest {
    @Test fun projectsOnlyChatDisplayValues() {
        val projected = projectNativeAiMessages(
            listOf(
                AiChatMessage(role = "user", content = "你好", proposalSource = "ignored"),
                AiChatMessage(role = "assistant", content = "结果", proposalSource = "可选提案")
            )
        )

        assertEquals(
            listOf(
                NativeAiMessage(mine = true, content = "你好", proposalSource = "ignored"),
                NativeAiMessage(mine = false, content = "结果", proposalSource = "可选提案")
            ),
            projected
        )
    }

    @Test fun mapsNativeBatchActionsToExistingHostModes() {
        assertEquals(AiBatchRunner.Mode.PUBLISH, hostAiBatchMode(NativeAiBatchMode.PUBLISH))
        assertEquals(AiBatchRunner.Mode.DISCARD, hostAiBatchMode(NativeAiBatchMode.DISCARD))
    }
}
