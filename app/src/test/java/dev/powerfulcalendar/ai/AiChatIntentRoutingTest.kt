package dev.powerfulcalendar.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AiChatIntentRoutingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val anchor = LocalDateTime.of(2026, 10, 25, 0, 30).atZone(zone)
        .toInstant().toEpochMilli()
    private fun user(content: String, min: Long = 0) = AiChatMessage(
        role = "user", content = content, createdAtMillis = anchor + min * 60_000,
        zoneId = zone.id
    )
    private fun assistant(content: String) = AiChatMessage(role = "assistant", content = content)
    private fun route(
        history: List<AiChatMessage>,
        intent: ChatProposalIntent? = null
    ) = AiChatIntentRouting.resolve(
        ChatPresentation("OK", proposalIntent = intent), history,
        anchor + 60L * 60_000, zone.id
    )

    @Test fun explicitCommandWorksWhenProviderOmitsPrivateMarker() {
        val result = route(listOf(user(
            "\u5E2E\u6211\u5728\u4ECA\u5929\u7684 1:45~2:45 \u4E4B\u95F4\u521B\u5EFA\u591A\u4E2A\u4E8B\u4EF6"
        )))
        assertEquals(ChatProposalIntent.EXPLICIT, result.intent)
        assertNotNull(result.task)
        assertEquals(1, result.task!!.revision)
    }

    @Test fun modelExplicitTimeRangeSentenceWithoutCreateVerbCanEnterReview() {
        val result = route(listOf(user(
            "\u4ECA\u665A 18:00-22:00 \u590D\u4E60\u5316\u5B66"
        )), ChatProposalIntent.EXPLICIT)
        assertNotNull(result.task)
        assertEquals(ChatProposalIntent.EXPLICIT, result.intent)
    }

    @Test fun modelSuggestionCannotAutoConvertCasualMention() {
        val result = route(listOf(user(
            "\u6211\u6700\u8FD1\u53EF\u80FD\u60F3\u7EA6\u4E00\u4E2A\u805A\u9910"
        )), ChatProposalIntent.SUGGESTED)
        assertEquals(ChatProposalIntent.SUGGESTED, result.intent)
        assertNull(result.task)
    }

    @Test fun confirmationAfterExplicitTaskIsRoutedWithoutSecondMagicMarker() {
        val history = listOf(
            user("\u4ECA\u5929 01:45-02:45 \u521B\u5EFA 5 \u4E2A\u4E8B\u4EF6"),
            assistant("\u8981\u521B\u5EFA\u4E94\u4E2A\u5417\uFF1F"),
            user("\u786E\u8BA4", 1)
        )
        val result = route(history)
        assertEquals(ChatProposalIntent.EXPLICIT, result.intent)
        assertEquals(2, result.task!!.userEvidence.size)
    }

    @Test fun arbitraryConfirmationDoesNotCreateStandaloneProposal() {
        val result = route(listOf(
            user("\u4F60\u559C\u6B22\u4EC0\u4E48\u4E66"),
            assistant("\u786E\u8BA4\u6211\u7684\u610F\u601D\u5417\uFF1F"),
            user("\u786E\u8BA4", 1)
        ))
        assertNull(result.task)
        assertNull(result.intent)
    }

    @Test fun providerFailureCannotBeMisreportedAsCalendarAuthorization() {
        val result = route(listOf(user("\u4F60\u597D")))
        assertNull(result.intent)
        assertNull(result.task)
    }
}
