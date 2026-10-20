package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiSkillContractTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun conciseReplyCanCarryAnOptInDraftButtonWithoutToolExecution() {
        val raw = """周一和周三安排了课，可以生成草稿。
[[POWERCAL_DRAFT]]
{"type":"draft","source":"2026-10-26 周一及周三 08:00～09:30 上课，地点 A101"}
[[/POWERCAL_DRAFT]]"""
        val result = AiChatActionParser.parse(raw)
        assertFalse(result.text.contains("POWERCAL_DRAFT"))
        assertTrue(result.proposalSource!!.contains("08:00"))
        assertNull(AiChatActionParser.parse("我们聊一聊").proposalSource)
        assertNull(AiChatActionParser.parse(
            """正常回复
[[POWERCAL_DRAFT]]
{"type":"publish","source":"直接提交"}
[[/POWERCAL_DRAFT]]"""
        ).proposalSource)
    }

    @Test fun conversationBudgetIncludesMoreThanTwelveShortTurnsAndIsBounded() {
        val messages = (1..65).map { AiChatMessage("user", "第 $it 轮") }
        val selected = AiConversationWindow.select(messages)
        assertEquals(40, selected.size)
        assertEquals("第 65 轮", selected.last().content)
        assertEquals("第 26 轮", selected.first().content)
    }

    @Test fun structuredWeekdayTimesAndPeopleBecomeTypedFields() {
        val raw = """{"events":[{"operation":"CREATE","kind":"ACTIVITY",
            "title":"实验课","start":"2026-10-26 08:00","end":"2026-10-26 09:30",
            "location":"A101","participants":["王同学"],
            "recurrence":{"freq":"WEEKLY","interval":1,"byDay":["MO","WE"],"count":16}
        }]}"""
        val result = AiResponseParser.parse(raw, emptyList(), zone)
        assertTrue(result.warnings.toString(), result.usable)
        val form = result.proposals.single().form
        assertEquals("2026-10-26 08:00", form.startText)
        assertEquals("2026-10-26 09:30", form.endText)
        assertEquals("A101", form.location)
        assertTrue(form.notes.contains("王同学"))
        assertEquals(16, form.recurrence.count)
        assertEquals(2, form.recurrence.byDay.size)
        assertEquals(0, form.reminderMinutes)
        assertEquals(ReminderMethod.MESSAGE, form.reminderMethod)
        assertTrue(form.isPublishable())
    }

    @Test fun incompleteWeeklyStartRemainsUnpublishableNotGuessed() {
        val raw = """{"events":[{"kind":"ACTIVITY","title":"周一实验课","start":"周一 8:00",
            "end":"09:30","recurrence":{"freq":"WEEKLY","byDay":["MO"]}}]}"""
        val result = AiResponseParser.parse(raw, emptyList(), zone)
        assertEquals("", result.proposals.single().form.startText)
        assertFalse(result.proposals.single().form.isPublishable())
    }

    @Test fun partialReminderUpdateInheritsExistingSnapshot() {
        val start = DraftForm.parseInstantIn("2026-10-26 08:00", zone)!!
        val existing = TemporalRecord(
            eventSeq = 77, revision = 3, kind = TemporalKind.ACTIVITY,
            title = "英语课", startMillis = start, endMillis = start + 5_400_000,
            location = "B201", notes = "同学小王",
            reminderMinutes = 0, zoneId = zone.id
        )
        val raw = """{"events":[{"operation":"REPLACE","eventSeq":77,
            "reminderMinutes":30,"reminderMethod":"ALARM"}]}"""
        val result = AiResponseParser.parse(raw, listOf(existing), zone)
        val proposal = result.proposals.single()
        assertNull(proposal.blocked)
        assertEquals("英语课", proposal.form.title)
        assertEquals("B201", proposal.form.location)
        assertEquals(30, proposal.form.reminderMinutes)
        assertEquals(ReminderMethod.ALARM, proposal.form.reminderMethod)
        assertTrue(proposal.form.isPublishable())
    }

    @Test fun unsupportedRecurrenceNeverDowngradesToOneOffWithoutWarning() {
        val raw = """{"events":[{"kind":"ACTIVITY","title":"课","start":"2026-10-26 08:00",
        "end":"2026-10-26 09:30","recurrence":{"freq":"YEARLY"}}]}"""
        val result = AiResponseParser.parse(raw, emptyList(), zone)
        assertNotNull(result.proposals.single().blocked)
    }
}
