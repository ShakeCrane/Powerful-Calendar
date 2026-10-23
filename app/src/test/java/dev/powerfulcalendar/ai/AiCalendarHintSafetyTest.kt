package dev.powerfulcalendar.ai

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiCalendarHintSafetyTest {
    @Test fun systemPromptPlaceholderIsNeverUsedAsStructuredDraftInput() {
        val raw = """识别到日程。
[[POWERCAL_DRAFT]]
{"type":"draft","source":"保持原始事件要素与用户明确确认过的上下文，不编造"}
[[/POWERCAL_DRAFT]]"""
        val hint = AiChatActionParser.parse(raw)
        assertNotNull(hint.proposalSource)
        val typed = "Create an Activity on 2026-10-27 from 10:00 to 11:00 called PC_QA_AI_04."
        assertEquals(typed, AiChatActionParser.authoritativeCurrentUserSource(hint, typed))
        assertNotEquals(hint.proposalSource,
            AiChatActionParser.authoritativeCurrentUserSource(hint, typed))
    }

    @Test fun ordinaryChatCannotBecomeCalendarProposalWithoutHint() {
        val noHint = AiChatActionParser.parse("普通对话，不涉及任何时间或事件")
        assertNull(AiChatActionParser.authoritativeCurrentUserSource(
            noHint, "这段文字不应触发写入"))
    }

    @Test fun emptyStructuredEventsHaveClearWarningRatherThanBlankError() {
        val parsed = AiResponseParser.parse(
            """{"events":[]}""", emptyList(), ZoneId.of("Asia/Shanghai"))
        assertTrue(parsed.proposals.isEmpty())
        assertTrue(parsed.warnings.first().contains("可操作"))
    }

    @Test fun malformedStructuredItemsNeverProduceAnEmptyFailureReason() {
        val parsed = AiResponseParser.parse(
            """{"events":[null]}""", emptyList(), ZoneId.of("Asia/Shanghai"))
        assertTrue(parsed.proposals.isEmpty())
        assertTrue(parsed.warnings.isNotEmpty())
        assertTrue(parsed.warnings.first().isNotBlank())
    }
}
