package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.AiProposalItemReadinessV1
import dev.powerfulcalendar.core.api.v1.AiProposalReadinessStatusV1
import dev.powerfulcalendar.core.api.v1.AiProposalReviewReadinessV1
import dev.powerfulcalendar.core.api.v1.AiConversationMessageV1
import dev.powerfulcalendar.core.api.v1.AiConversationRoleV1
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAiProjectionTest {
    @Test fun projectsOnlyChatDisplayValues() {
        val projected = projectNativeAiMessages(
            listOf(
                AiConversationMessageV1(
                    role = AiConversationRoleV1.USER,
                    content = "你好",
                    proposalSource = "ignored",
                    messageId = "user-1"
                ),
                AiConversationMessageV1(
                    role = AiConversationRoleV1.ASSISTANT,
                    content = "结果",
                    proposalSource = "可选提案",
                    messageId = "assistant-1"
                )
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
        val interpretation = AiInterpretation(
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
        val projected = projectNativeAiProposalReview(
            interpretation,
            AiProposalReviewReadinessV1("native-review", listOf(
                AiProposalItemReadinessV1(0, 0, AiProposalReadinessStatusV1.BLOCKED,
                    errorCode = "AI_PROPOSAL_BLOCKED", message = "缺少时间"),
                AiProposalItemReadinessV1(1, 0, AiProposalReadinessStatusV1.NOT_REQUIRED)
            ))
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
                    publishable = false,
                    readinessMessage = "缺少时间"
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

    @Test fun nativePublishabilityUsesCoreInvalidReadinessEvenWhenShallowFormCheckPasses() {
        val form = DraftForm(kind = TemporalKind.ACTIVITY, title = "Invalid recurrence",
            startText = "2026-11-02 10:00", endText = "2026-11-02 11:00",
            recurrence = Recurrence(interval = 0))
        assertEquals(true, form.isPublishable())
        val projected = projectNativeAiProposalReview(
            AiInterpretation(proposals = listOf(AiProposal(
                DraftOperation.CREATE, null, form, sourceIndex = 9
            ))),
            AiProposalReviewReadinessV1("invalid-form-review", listOf(
                AiProposalItemReadinessV1(0, 9, AiProposalReadinessStatusV1.INVALID,
                    errorCode = "AI_PROPOSAL_INVALID", message = "重复间隔必须至少为 1")
            ))
        )

        assertEquals(false, projected.rows.single().publishable)
        assertEquals("重复间隔必须至少为 1", projected.rows.single().readinessMessage)
    }

    @Test fun projectsFrozenBatchReviewWithoutDraftIdsOrVersions() {
        val cards = listOf(
            batchCard(301, 101, DraftOperation.CREATE, "Create one", 4),
            batchCard(302, 202, DraftOperation.REPLACE, "Replace two", 8),
            batchCard(303, 303, DraftOperation.DELETE, "Delete three", 5)
        )

        val publish = projectNativeAiBatchAuthorization(AiBatchRunner.Mode.PUBLISH, cards)
        assertEquals(NativeAiBatchMode.PUBLISH, publish.mode)
        assertEquals(3, publish.count)
        assertEquals(
            listOf(
                NativeAiBatchOperationCount("新建", 1),
                NativeAiBatchOperationCount("修改", 1),
                NativeAiBatchOperationCount("删除", 1)
            ),
            publish.operationCounts
        )
        assertEquals(true, publish.containsFormalDeletion)
        assertEquals(
            listOf(
                NativeAiBatchReviewRow("#0101", "新建", "Create one"),
                NativeAiBatchReviewRow("#0202", "修改", "Replace two"),
                NativeAiBatchReviewRow("#0303", "删除", "Delete three")
            ),
            publish.rows
        )

        val discard = projectNativeAiBatchAuthorization(AiBatchRunner.Mode.DISCARD, cards)
        assertEquals(NativeAiBatchMode.DISCARD, discard.mode)
        assertEquals(false, discard.containsFormalDeletion)
    }

    private fun batchCard(
        draftId: Long,
        eventSeq: Long,
        operation: DraftOperation,
        title: String,
        version: Long
    ) = DraftCard(
        draftId = draftId,
        eventSeq = eventSeq,
        queueOrder = draftId,
        operation = operation,
        form = DraftForm(kind = TemporalKind.ACTIVITY, title = title),
        status = DraftStatus.PENDING,
        baseRevision = null,
        draftVersion = version
    )
}
