package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceFreq
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
class WebAiProposalReviewTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun hostPublishReviewShowsFullSnapshotAndRequiresExplicitConfirmation() {
        val zone = "Asia/Shanghai"
        val current = TemporalRecord(
            eventSeq = 71,
            revision = 4,
            kind = TemporalKind.ACTIVITY,
            title = "原标题",
            startMillis = 1_798_000_000_000,
            endMillis = 1_798_003_600_000,
            notes = "原备注",
            location = "原地点",
            zoneId = zone,
            reminderMinutes = 10,
            reminderMethod = dev.powerfulcalendar.core.ReminderMethod.ALARM,
            reminderTiming = dev.powerfulcalendar.core.ReminderTiming.DAY_START,
            trackingEnabled = true,
            recurrence = Recurrence(
                freq = RecurrenceFreq.WEEKLY,
                interval = 2,
                count = 4,
                activeFromDate = "2026-10-01",
                byDay = setOf(java.time.DayOfWeek.MONDAY),
                exceptions = setOf("2026-10-12T09:00"),
                overrides = mapOf("2026-10-19T09:00" to OccurrenceOverride(
                    1_798_000_000_000, 1_798_003_600_000, "原覆盖标题"))
            )
        )
        val proposed = DraftForm.from(current).copy(
            title = "新标题",
            notes = "新备注",
            location = "新地点",
            reminderMinutes = 20,
            reminderMethod = dev.powerfulcalendar.core.ReminderMethod.MESSAGE,
            reminderTiming = dev.powerfulcalendar.core.ReminderTiming.OFFSET,
            trackingEnabled = false,
            recurrence = current.recurrence.copy(
                freq = RecurrenceFreq.DAILY,
                interval = 3,
                activeUntilDate = "2026-12-31",
                exceptions = emptySet(),
                overrides = emptyMap()
            )
        )
        val removed = TemporalRecord(
            eventSeq = 72,
            revision = 2,
            kind = TemporalKind.ALARM,
            title = "待删除提醒",
            startMillis = 1_798_000_000_000,
            zoneId = zone
        )
        val review = WebAiProposalPublishReview(
            reviewId = "review-token",
            interpretation = AiInterpretation(proposals = listOf(
                AiProposal(DraftOperation.REPLACE, 71, proposed),
                AiProposal(DraftOperation.DELETE, 72, DraftForm.from(removed))
            )),
            selectedPositions = listOf(0, 1),
            targetSnapshots = mapOf(71L to current, 72L to removed)
        )
        var confirmed = 0
        var dismissed = 0

        ui.setContent {
            PowerCalTheme {
                WebAiProposalPublishAuthorizationReview(
                    review = review,
                    busy = false,
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ }
                )
            }
        }

        ui.onNodeWithText("确认 AI 批量正式操作").assertIsDisplayed()
        ui.onNodeWithText("标题：原标题 → 新标题").assertIsDisplayed()
        ui.onNodeWithText("地点：原地点 → 新地点").assertIsDisplayed()
        ui.onNodeWithText("备注：原备注 → 新备注").assertIsDisplayed()
        ui.onNodeWithText("提醒：10 分钟前 → 20 分钟前").assertIsDisplayed()
        ui.onNodeWithText("提醒方式：闹钟 → 通知").assertIsDisplayed()
        ui.onNodeWithText("提醒时机：当天开始 → 按提前量").assertIsDisplayed()
        ui.onNodeWithText("完成跟踪：启用 → 关闭").assertIsDisplayed()
        val reviewContent = ui.onNodeWithTag("web-ai-publish-review-scroll")
        reviewContent.performScrollToNode(hasText("重复频率：每周 → 每天"))
        ui.onNodeWithText("重复频率：每周 → 每天").assertIsDisplayed()
        reviewContent.performScrollToNode(hasText("重复星期：", substring = true))
        val weekdayTexts = ui.onAllNodesWithText("重复星期：", substring = true)
            .fetchSemanticsNodes().flatMap { node ->
                node.config[SemanticsProperties.Text].map { it.text }
            }
        assertTrue("weekday review must show the full before/after value: $weekdayTexts",
            "重复星期：周一 → 周一" in weekdayTexts)
        reviewContent.performScrollToNode(hasText("跳过的原始实例：2026-10-12T09:00 → （空）"))
        ui.onNodeWithText("跳过的原始实例：2026-10-12T09:00 → （空）").assertIsDisplayed()
        reviewContent.performScrollToNode(hasText("实例覆盖：2026-10-19T09:00", substring = true))
        val overrideText = ui.onNodeWithText("实例覆盖：2026-10-19T09:00", substring = true)
        overrideText.assertIsDisplayed()
        val overrideValue = overrideText.fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString { it.text }
        assertTrue("override review must show complete old and new values: $overrideValue",
            overrideValue.contains("原覆盖标题") && overrideValue.endsWith("→ （空）"))
        reviewContent.performScrollToNode(hasText("此操作会删除正式事件，且当前不能撤销。"))
        ui.onNodeWithText("此操作会删除正式事件，且当前不能撤销。")
            .assertIsDisplayed()
        assertEquals(0, confirmed)
        ui.onNodeWithText("确认并提交 2 项").performClick()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun hostPublishReviewDisablesCommitWhileResolving() {
        val review = WebAiProposalPublishReview(
            reviewId = "busy-review",
            interpretation = AiInterpretation(proposals = listOf(
                AiProposal(DraftOperation.CREATE, null,
                    DraftForm(kind = TemporalKind.MEMO, title = "新备忘"))
            )),
            selectedPositions = listOf(0),
            targetSnapshots = emptyMap()
        )
        ui.setContent {
            PowerCalTheme {
                WebAiProposalPublishAuthorizationReview(
                    review = review,
                    busy = true,
                    onConfirm = { error("resolving confirmation must be disabled") },
                    onDismiss = { error("resolving dismissal must be disabled") }
                )
            }
        }
        ui.onNodeWithText("确认并提交 1 项").assertIsNotEnabled()
        ui.onNodeWithText("取消").assertIsNotEnabled()
    }

    @Test
    fun projectionIncludesDeterministicRecurrenceAndCompletionDetails() {
        val interpretation = AiInterpretation(proposals = listOf(
            AiProposal(DraftOperation.CREATE, null, DraftForm(
                kind = TemporalKind.MEMO,
                title = "每周备忘",
                completedKeys = setOf("2026-10-19T09:00"),
                source = "ai",
                recurrenceEditScope = RecurrenceEditScope.SINGLE_OCCURRENCE,
                editOccurrenceKey = "2026-10-26T09:00",
                recurrence = Recurrence(
                    freq = RecurrenceFreq.WEEKLY,
                    byDay = setOf(java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.MONDAY),
                    exceptions = setOf("2026-10-14T09:00", "2026-10-07T09:00"),
                    overrides = mapOf("2026-10-21T09:00" to OccurrenceOverride(
                        1_798_000_000_000, null, "移动后的标题"))
                )
            ))
        ))
        val projection = WebAiProposalProjection.project(
            WebAiProposalReviewSession.create(interpretation, emptyList())
        ).getJSONArray("proposals").getJSONObject(0)
        val recurrence = projection.getJSONObject("recurrence")
        assertEquals(listOf("MONDAY", "WEDNESDAY"), (0 until recurrence
            .getJSONArray("byDay").length()).map { recurrence.getJSONArray("byDay").getString(it) })
        assertEquals("2026-10-07T09:00", recurrence.getJSONArray("exceptions").getString(0))
        assertEquals("2026-10-21T09:00", recurrence.getJSONArray("overrides")
            .getJSONObject(0).getString("key"))
        assertEquals("2026-10-19T09:00", projection.getJSONArray("completedKeys").getString(0))
        assertEquals("SINGLE_OCCURRENCE", projection.getString("recurrenceEditScope"))
        assertEquals("2026-10-26T09:00", projection.getString("editOccurrenceKey"))
        assertEquals("ai", projection.getString("source"))
    }

    @Test
    fun projectionBoundsNestedUntrustedStringsAndReportsTruncation() {
        val oversized = "x".repeat(500)
        val form = DraftForm(
            kind = TemporalKind.MEMO,
            title = "有界审核",
            source = oversized,
            completedKeys = setOf(oversized),
            recurrence = Recurrence(
                freq = RecurrenceFreq.WEEKLY,
                exceptions = setOf(oversized),
                overrides = mapOf(oversized to OccurrenceOverride(
                    1_798_000_000_000, null, oversized))
            )
        )
        val projection = WebAiProposalProjection.project(
            WebAiProposalReviewSession.create(AiInterpretation(proposals = listOf(
                AiProposal(DraftOperation.CREATE, null, form)
            )), emptyList())
        ).getJSONArray("proposals").getJSONObject(0)

        assertEquals(120, projection.getString("source").length)
        assertTrue(projection.getBoolean("sourceTruncated"))
        assertTrue(projection.getBoolean("completedKeysTruncated"))
        assertEquals(100, projection.getJSONArray("completedKeys").getString(0).length)
        assertTrue(projection.getJSONObject("recurrence").getBoolean("exceptionsTruncated"))
        assertEquals(100, projection.getJSONObject("recurrence").getJSONArray("exceptions")
            .getString(0).length)
        assertTrue(projection.getJSONObject("recurrence").getBoolean("overridesTruncated"))
        val override = projection.getJSONObject("recurrence").getJSONArray("overrides")
            .getJSONObject(0)
        assertEquals(100, override.getString("key").length)
        assertEquals(240, override.getString("title").length)
        assertTrue(override.getBoolean("titleTruncated"))
    }

    @Test
    fun selectionRejectsStaleAndBlockedItemsButKeepsIncompleteItemsStageable() {
        val session = WebAiProposalReviewSession.create(AiInterpretation(proposals = listOf(
            AiProposal(DraftOperation.CREATE, null,
                DraftForm(kind = TemporalKind.MEMO, title = "完整备忘")),
            AiProposal(DraftOperation.CREATE, null,
                DraftForm(kind = TemporalKind.ALARM, title = "不可用目标"), blocked = "目标不存在"),
            AiProposal(DraftOperation.CREATE, null,
                DraftForm(kind = TemporalKind.ALARM, title = "待补时刻"))
        )), emptyList())

        assertEquals(listOf(0, 2), WebAiProposalProjection.validSelection(session, listOf(2, 0)))
        assertNull(WebAiProposalProjection.validSelection(session, emptyList()))
        assertNull(WebAiProposalProjection.validSelection(session, listOf(0, 0)))
        assertNull(WebAiProposalProjection.validSelection(session, listOf(3)))
        assertNull(WebAiProposalProjection.validSelection(session, listOf(1)))
        assertTrue(WebAiProposalProjection.isPublishable(session.interpretation.proposals[0]))
        assertFalse(WebAiProposalProjection.isPublishable(session.interpretation.proposals[2]))
    }
}
