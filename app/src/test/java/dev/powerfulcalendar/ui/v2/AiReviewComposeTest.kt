package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class AiReviewComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun proposalReviewShowsAllFourChoicesAndAppliesOnlyValidItemsInEachOperationGroup() {
        val interpretation = AiInterpretation(proposals = listOf(
            AiProposal(DraftOperation.CREATE, null,
                DraftForm(kind = TemporalKind.ACTIVITY, title = "Create A")),
            AiProposal(DraftOperation.CREATE, null,
                DraftForm(kind = TemporalKind.ACTIVITY, title = "Blocked create"),
                blocked = "缺少时间"),
            AiProposal(DraftOperation.DELETE, 42L,
                DraftForm(kind = TemporalKind.ACTIVITY, title = "Delete B"))
        ))
        val applied = mutableListOf<Pair<Set<Int>, Boolean>>()
        val discarded = mutableListOf<Set<Int>>()
        var supplemented = 0

        ui.setContent {
            PowerCalTheme {
                AiProposalReview(
                    interpretation = interpretation,
                    busy = false,
                    onApply = { indexes, approve -> applied += indexes to approve },
                    onDiscard = { discarded += it },
                    onSupplement = { supplemented++ }
                )
            }
        }

        listOf("批量同意", "写入草稿", "批量拒绝", "补充细节").forEach { label ->
            ui.onAllNodesWithText(label).assertCountEquals(2)
        }
        ui.onNodeWithText("1. Create A · 活动").assertExists()
        ui.onNodeWithText("2. Blocked create · 活动").assertExists()
        ui.onNodeWithText("3. Delete B · 删除 #42").assertExists()

        ui.onAllNodesWithText("批量同意")[0].performClick()
        ui.onAllNodesWithText("批量同意")[1].performScrollTo()
        ui.onAllNodesWithText("批量同意")[1].performClick()
        ui.onAllNodesWithText("写入草稿")[0].performScrollTo()
        ui.onAllNodesWithText("写入草稿")[0].performClick()
        ui.onAllNodesWithText("批量拒绝")[0].performScrollTo()
        ui.onAllNodesWithText("批量拒绝")[0].performClick()
        ui.onAllNodesWithText("补充细节")[0].performScrollTo()
        ui.onAllNodesWithText("补充细节")[0].performClick()

        assertEquals(listOf(setOf(0) to true, setOf(2) to true, setOf(0) to false), applied)
        assertEquals(listOf(setOf(0)), discarded)
        assertEquals(1, supplemented)
    }

    @Test
    fun proposalReviewDisablesEveryActionWhileBusy() {
        ui.setContent {
            PowerCalTheme {
                AiProposalReview(
                    interpretation = AiInterpretation(proposals = listOf(
                        AiProposal(DraftOperation.CREATE, null,
                            DraftForm(kind = TemporalKind.ACTIVITY, title = "Busy"))
                    )),
                    busy = true,
                    onApply = { _, _ -> error("busy review must not authorize") },
                    onDiscard = { error("busy review must not discard") },
                    onSupplement = { error("busy review must not supplement") }
                )
            }
        }

        listOf("批量同意", "写入草稿", "批量拒绝", "补充细节").forEach { label ->
            ui.onNodeWithText(label).assertIsNotEnabled()
        }
    }

    @Test
    fun batchReviewShowsExactOperationCountsAndFrozenDraftSnapshotsBeforeAuthorization() {
        val cards = listOf(
            card(301, 101, 1, DraftOperation.CREATE, "Create one", 4),
            card(302, 202, 2, DraftOperation.REPLACE, "Replace two", 8),
            card(303, 303, 3, DraftOperation.DELETE, "Delete three", 5)
        )
        var authorized: List<DraftCard>? = null
        var dismissed = false

        ui.setContent {
            PowerCalTheme {
                AiBatchAuthorizationReview(
                    mode = AiBatchRunner.Mode.PUBLISH,
                    cards = cards,
                    busy = false,
                    onAuthorize = { authorized = it },
                    onDismiss = { dismissed = true }
                )
            }
        }

        ui.onNodeWithText("范围固定为下列 3 张草稿。卡组在处理期间只读，时间轴可以查看。你可随时停止，已完成结果会保留。")
            .assertIsDisplayed()
        ui.onNodeWithText("新建提案 · 1 张").assertExists()
        ui.onNodeWithText("修改提案 · 1 张").assertExists()
        ui.onNodeWithText("删除提案 · 1 张").assertExists()
        ui.onNodeWithText("1. ${TemporalRecord.formatSeq(101)} · 新建 · Create one").assertExists()
        ui.onNodeWithText("2. ${TemporalRecord.formatSeq(202)} · 修改 · Replace two").assertExists()
        ui.onNodeWithText("3. ${TemporalRecord.formatSeq(303)} · 删除 · Delete three").assertExists()
        ui.onNodeWithText("包含正式删除操作：事件序号会退役，当前没有发布撤销功能。")
            .assertExists()
        ui.onNodeWithText("授权并发布 3 张").assertExists()
        assertNull("displaying a preview does not authorize it", authorized)
        assertFalse(dismissed)

        ui.onNodeWithText("授权并发布 3 张").performClick()

        assertEquals(cards, authorized)
        assertTrue(authorized!!.map { it.draftId to it.draftVersion }.toSet().containsAll(
            setOf(301L to 4L, 302L to 8L, 303L to 5L)
        ))
    }

    @Test
    fun dismissingBatchReviewDoesNotAuthorizeAndBusyReviewCannotConfirm() {
        val cards = listOf(card(401, 404, 1, DraftOperation.CREATE, "Cancel me", 2))
        var authorized = false
        var dismissed = false
        ui.setContent {
            PowerCalTheme {
                AiBatchAuthorizationReview(
                    mode = AiBatchRunner.Mode.DISCARD,
                    cards = cards,
                    busy = true,
                    onAuthorize = { authorized = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        ui.onNodeWithText("授权并丢弃 1 张").assertIsNotEnabled()
        ui.onNodeWithText("取消").performClick()

        assertFalse(authorized)
        assertTrue(dismissed)
    }

    private fun card(
        draftId: Long,
        eventSeq: Long,
        queueOrder: Long,
        operation: DraftOperation,
        title: String,
        version: Long
    ) = DraftCard(
        draftId = draftId,
        eventSeq = eventSeq,
        queueOrder = queueOrder,
        operation = operation,
        form = DraftForm(kind = TemporalKind.ACTIVITY, title = title),
        status = DraftStatus.PENDING,
        baseRevision = null,
        draftVersion = version
    )
}
