package dev.powerfulcalendar.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class AiTaskSnapshotInstrumentedTest {
    @Test
    fun androidRuntimeKeepsMultiTurnEvidenceAndThePreMidnightAnchor() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val losAngeles = ZoneId.of("America/Los_Angeles")
        fun message(id: String, role: String, text: String, time: LocalDateTime?, zone: ZoneId?) =
            AiChatMessage(
                role = role,
                content = text,
                messageId = id,
                createdAtMillis = time?.atZone(zone!!)?.toInstant()?.toEpochMilli(),
                zoneId = zone?.id
            )

        val history = listOf(
            message("req", "user", "今天创建五个重叠活动，23:30 到次日 00:30。",
                LocalDateTime.of(2026, 10, 25, 23, 30), shanghai),
            message("assistant-question", "assistant", "这些要重复吗？", null, null),
            message("fix", "user", "我的意思是五个单次活动，不用重复。",
                LocalDateTime.of(2026, 10, 25, 23, 40), shanghai),
            message("assistant-title", "assistant", "标题叫什么？", null, null),
            message("title", "user", "14514", LocalDateTime.of(2026, 10, 26, 0, 5), losAngeles),
            message("assistant-confirm", "assistant", "确认生成吗？", null, null),
            message("confirm", "user", "确认", LocalDateTime.of(2026, 10, 26, 0, 8), losAngeles)
        )

        val task = AiConversationTaskSnapshot.from(
            history,
            LocalDateTime.of(2026, 10, 26, 0, 9).atZone(shanghai).toInstant().toEpochMilli(),
            shanghai.id
        )

        assertNotNull(task)
        assertEquals("req", task!!.taskId)
        assertEquals(3, task.revision)
        assertEquals("2026-10-25T23:30", task.anchorLocalDateTime)
        assertEquals(shanghai.id, task.zoneId)
        assertEquals(losAngeles.id, task.userEvidence.last().zoneId)
        assertEquals(listOf("req", "fix", "title", "confirm"),
            task.userEvidence.map { it.messageId })
        assertTrue(task.assistantSuggestions.isNotEmpty())
        assertFalse(task.userEvidence.any { it.text.contains("标题叫什么") })
    }
}
