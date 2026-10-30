package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalKind
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

    @Test fun projectsAiReviewIntoMinimalDisplayRowsAndOriginalHostPositions() {
        val projected = projectNativeAiProposalReview(
            AiInterpretation(
                proposals = listOf(
                    AiProposal(
                        operation = DraftOperation.CREATE,
                        eventSeq = null,
                        form = DraftForm(kind = TemporalKind.ALARM, title = "待补全"),
                        blocked = "缺少时间"
                    ),
                    AiProposal(
                        operation = DraftOperation.DELETE,
                        eventSeq = 42,
                        form = DraftForm(kind = TemporalKind.ACTIVITY, title = "删除目标")
                    )
                ),
                warnings = listOf("提示一", "提示二", "不展示的后续提示")
            )
        )

        assertEquals(
            listOf(
                NativeAiProposalRow(
                    position = 0,
                    group = NativeAiProposalGroup.CREATE,
                    title = "待补全",
                    caption = "闹钟",
                    startText = "",
                    endText = "",
                    blockedReason = "缺少时间",
                    publishable = false
                ),
                NativeAiProposalRow(
                    position = 1,
                    group = NativeAiProposalGroup.EXISTING,
                    title = "删除目标",
                    caption = "删除 #42",
                    startText = "",
                    endText = "",
                    blockedReason = null,
                    publishable = true
                )
            ),
            projected.rows
        )
        assertEquals(listOf("提示一", "提示二"), projected.warnings)
    }
}
