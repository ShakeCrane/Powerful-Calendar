package dev.powerfulcalendar.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiConversationTaskSnapshotTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun confirmationSnapshotRetainsEveryUserCorrectionAndOriginalAnchor() {
        val history = mutableListOf<AiChatMessage>()
        history += user("request", "今天 01:45–02:45 创建多个相互重叠的事件，最初误说重复事件。", 25, 0, 30)
        history += assistant("你说的是重复日程吗？")
        history += user("recurrence-correction", "我的意思是就创建多个就行了，不用重复。", 25, 0, 31)
        history += assistant("五个事项都使用同一个标题吗？")
        history += user("title-value", "14514", 25, 0, 32)
        history += assistant("五个事项都叫 14514，对吗？")
        history += user("title-confirmation", "就叫这个。", 25, 0, 33)
        history += assistant("我会按单次事项准备五个候选，确认吗？")
        history += user("final-confirmation", "确认。", 25, 0, 34)

        val snapshot = requireNotNull(AiConversationTaskSnapshot.from(
            history, millis(26, 0, 2), zone.id))

        assertEquals("request", snapshot.taskId)
        assertEquals(4, snapshot.revision)
        assertEquals(listOf(
            "今天 01:45–02:45 创建多个相互重叠的事件，最初误说重复事件。",
            "我的意思是就创建多个就行了，不用重复。",
            "14514",
            "就叫这个。",
            "确认。"
        ), snapshot.userEvidence.map { it.text })
        assertEquals("2026-10-25T00:30", snapshot.anchorLocalDateTime)
        assertEquals(zone.id, snapshot.zoneId)
        assertTrue(snapshot.assistantSuggestions.any { it.text.contains("五个事项") })
        assertFalse(snapshot.userEvidence.any { it.text.contains("五个事项都使用") })
    }

    @Test
    fun requestAnchorSurvivesMidnightAndTimezoneChangeAtConfirmation() {
        val history = listOf(
            user("start", "今天创建一个截止事项，今晚处理。", 25, 23, 59),
            assistant("需要我按这个请求生成待确认卡吗？"),
            user("confirm", "确认", 26, 0, 2, ZoneId.of("America/Los_Angeles"))
        )

        val now = LocalDateTime.of(2026, 10, 26, 0, 4)
            .atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()
        val snapshot = requireNotNull(AiConversationTaskSnapshot.from(history, now, zone.id))

        assertEquals("2026-10-25T23:59", snapshot.anchorLocalDateTime)
        assertEquals("Asia/Shanghai", snapshot.zoneId)
        assertEquals("America/Los_Angeles", snapshot.userEvidence.last().zoneId)
    }

    @Test
    fun repeatedConfirmationTurnsDoNotAdvanceTheContentRevision() {
        val history = listOf(
            user("start", "今天创建一项活动 12:00 到 13:00。", 25, 10, 0),
            assistant("标题是什么？"),
            user("title", "午餐", 25, 10, 1),
            assistant("确认创建午餐活动吗？"),
            user("confirm-1", "确认。", 25, 10, 2),
            assistant("仍保留相同的待处理任务。"),
            user("confirm-2", "好的", 25, 10, 3)
        )

        val snapshot = requireNotNull(AiConversationTaskSnapshot.from(
            history, millis(25, 10, 4), zone.id))

        assertEquals("start", snapshot.taskId)
        assertEquals(2, snapshot.revision)
        assertEquals(listOf("start", "title", "confirm-1", "confirm-2"),
            snapshot.userEvidence.map { it.messageId })
    }

    @Test
    fun correctionReturningToAnEarlierValueGetsANewRevisionButConfirmationDoesNot() {
        val prefix = listOf(
            user("start", "今天创建一项活动 12:00 到 13:00。", 25, 10, 0),
            assistant("标题是什么？"),
            user("title-lunch", "午餐", 25, 10, 1),
            assistant("已暂定标题午餐。"),
            user("title-dinner", "标题改为晚餐", 25, 10, 2)
        )
        val dinner = requireNotNull(AiConversationTaskSnapshot.from(
            prefix, millis(25, 10, 3), zone.id))
        val returned = requireNotNull(AiConversationTaskSnapshot.from(
            prefix + listOf(
                assistant("标题要改回什么？"),
                user("title-back-to-lunch", "午餐", 25, 10, 4)
            ), millis(25, 10, 5), zone.id))
        val confirmed = requireNotNull(AiConversationTaskSnapshot.from(
            prefix + listOf(
                assistant("标题要改回什么？"),
                user("title-back-to-lunch", "午餐", 25, 10, 4),
                assistant("按午餐生成候选吗？"),
                user("confirmation", "确认", 25, 10, 5)
            ), millis(25, 10, 6), zone.id))

        assertEquals(3, dinner.revision)
        assertEquals(4, returned.revision)
        assertTrue(dinner.revision != returned.revision)
        assertEquals(returned.revision, confirmed.revision)
    }

    @Test
    fun unresolvedIntentExpiresAfterTwentyFourHoursAndRejectsFutureEvidence() {
        val request = user("expiry-start", "今天创建一项活动 12:00 到 13:00。", 25, 10, 0)
        val history = listOf(request)
        val anchor = requireNotNull(request.createdAtMillis)

        assertTrue(AiConversationTaskSnapshot.from(
            history, anchor + AiConversationTaskSnapshot.MAX_TASK_AGE_MILLIS, zone.id) != null)
        assertNull(AiConversationTaskSnapshot.from(
            history, anchor + AiConversationTaskSnapshot.MAX_TASK_AGE_MILLIS + 1, zone.id))
        assertNull(AiConversationTaskSnapshot.from(history, anchor - 1, zone.id))
    }

    @Test
    fun latestExplicitRequestStartsASeparateTaskAndOldConversationIsNotMixedIn() {
        val history = listOf(
            user("old", "明天创建一项旧活动 09:00 到 10:00。", 25, 10, 0),
            assistant("旧活动已经理解。"),
            user("old-confirm", "确认", 25, 10, 1),
            assistant("旧任务结束。"),
            user("new", "下周一创建新的会议 14:00 到 15:00。", 25, 10, 2)
        )

        val snapshot = requireNotNull(AiConversationTaskSnapshot.from(
            history, millis(25, 10, 3), zone.id))

        assertEquals("new", snapshot.taskId)
        assertEquals(1, snapshot.revision)
        assertEquals("下周一创建新的会议 14:00 到 15:00。", snapshot.userEvidence.single().text)
    }

    @Test
    fun unrelatedLatestTurnAndUnstampedLegacyMultiturnHistoryCannotAuthorizeHandoff() {
        val unrelated = listOf(
            user("request", "今天创建一个活动 12:00 到 13:00。", 25, 10, 0),
            assistant("我理解了。"),
            user("topic-change", "请解释一下海豚怎么睡觉。", 25, 10, 1)
        )
        val topicChangeAfterClarification = listOf(
            user("request-2", "今天创建一个活动 12:00 到 13:00。", 25, 10, 0),
            assistant("标题叫什么？"),
            user("topic-change-2", "请问海豚怎么睡", 25, 10, 1)
        )
        val unstampedLegacy = listOf(
            AiChatMessage("user", "今天创建一个活动 12:00 到 13:00。"),
            AiChatMessage("assistant", "标题是什么？"),
            AiChatMessage("user", "确认")
        )

        assertNull(AiConversationTaskSnapshot.from(unrelated, millis(25, 10, 2), zone.id))
        assertNull(AiConversationTaskSnapshot.from(
            topicChangeAfterClarification, millis(25, 10, 2), zone.id))
        assertNull(AiConversationTaskSnapshot.from(unstampedLegacy, millis(25, 10, 2), zone.id))
        assertNull(AiConversationTaskSnapshot.from(
            listOf(AiChatMessage("user", "确认")), millis(25, 10, 2), zone.id))
    }

    @Test
    fun bareNegativeRejectsCreationButCanCorrectARecurrenceQuestion() {
        val declinedCreation = listOf(
            user("create", "今天创建一项活动 12:00 到 13:00。", 25, 10, 0),
            assistant("要现在创建这项活动吗？"),
            user("decline", "不用", 25, 10, 1)
        )
        val declinedRecurrence = listOf(
            user("create-single", "今天创建一项活动 12:00 到 13:00。", 25, 10, 0),
            assistant("需要把这项活动设置为重复日程吗？"),
            user("no-repeat", "不用", 25, 10, 1)
        )

        assertNull(AiConversationTaskSnapshot.from(
            declinedCreation, millis(25, 10, 2), zone.id))
        val corrected = requireNotNull(AiConversationTaskSnapshot.from(
            declinedRecurrence, millis(25, 10, 2), zone.id))
        assertEquals("create-single", corrected.taskId)
        assertEquals(listOf("今天创建一项活动 12:00 到 13:00。", "不用"),
            corrected.userEvidence.map { it.text })
        assertEquals(2, corrected.revision)
    }

    @Test
    fun persistedChatRetainsEvidenceIdsTimesAndReconstructsSameTaskRevision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = AiChatHistoryStore.load(context)
        val history = listOf(
            user("persisted-start", "今天创建一项活动 12:00 到 13:00。", 25, 10, 0),
            assistant("标题是什么？"),
            user("persisted-title", "午餐", 25, 10, 1),
            assistant("确认创建午餐活动吗？"),
            user("persisted-confirm", "确认", 25, 10, 2)
        )
        val now = millis(25, 10, 3)
        try {
            AiChatHistoryStore.save(context, history)
            val restored = AiChatHistoryStore.load(context)
            val before = requireNotNull(AiConversationTaskSnapshot.from(history, now, zone.id))
            val after = requireNotNull(AiConversationTaskSnapshot.from(restored, now, zone.id))

            assertEquals(before.taskId, after.taskId)
            assertEquals(before.revision, after.revision)
            assertEquals(before.anchorMillis, after.anchorMillis)
            assertEquals(before.userEvidence, after.userEvidence)
        } finally {
            AiChatHistoryStore.save(context, previous)
        }
    }

    private fun user(
        id: String,
        text: String,
        day: Int,
        hour: Int,
        minute: Int,
        messageZone: ZoneId = zone
    ) = AiChatMessage(
        role = "user",
        content = text,
        messageId = id,
        createdAtMillis = LocalDateTime.of(2026, 10, day, hour, minute)
            .atZone(messageZone).toInstant().toEpochMilli(),
        zoneId = messageZone.id
    )

    private fun assistant(text: String) = AiChatMessage("assistant", text)

    private fun millis(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
