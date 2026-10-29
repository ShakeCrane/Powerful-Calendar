package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAiChatHistoryTest {
    private val conversation = listOf(
        AiChatMessage("user", "第一条请求", messageId = "u1"),
        AiChatMessage("assistant", "第一条回复", messageId = "a1"),
        AiChatMessage("user", "第二条请求", messageId = "u2"),
        AiChatMessage("assistant", "第二条回复", messageId = "a2")
    )

    @Test
    fun projectionIsBoundedAndIncludesStableHostMessageIds() {
        val messages = (0..41).map { index ->
            AiChatMessage("user", "m$index", messageId = "id-$index")
        }

        val projected = WebAiChatHistory.project(messages)

        assertEquals(WebAiChatHistory.PROJECTION_LIMIT, projected.length())
        assertEquals("id-2", projected.getJSONObject(0).getString("messageId"))
        assertEquals("id-41", projected.getJSONObject(39).getString("messageId"))
    }

    @Test
    fun editingAUserMessageRewindsItAndLaterTurnsAndReturnsTrustedPrefill() {
        val result = WebAiChatHistory.rewind(
            conversation, "u2", WebChatRewindMode.EDIT_AND_RESEND,
            expectedRevision = 9, currentRevision = 9
        )

        assertEquals(WebChatHistoryMutation.Applied(
            conversation.take(2), updateComposer = true, prefillText = "第二条请求"
        ), result)
    }

    @Test
    fun retractingAUserMessageRemovesThatTurnAndEverythingAfterIt() {
        val result = WebAiChatHistory.rewind(
            conversation, "u2", WebChatRewindMode.RETRACT,
            expectedRevision = 2, currentRevision = 2
        )

        assertEquals(WebChatHistoryMutation.Applied(conversation.take(2)), result)
    }

    @Test
    fun rewindingToAnAssistantRetainsItAndRemovesOnlyLaterTurns() {
        val result = WebAiChatHistory.rewind(
            conversation, "a1", WebChatRewindMode.RETRACT,
            expectedRevision = 0, currentRevision = 0
        )

        assertEquals(WebChatHistoryMutation.Applied(conversation.take(2)), result)
    }

    @Test
    fun staleMissingAmbiguousAndInvalidEditRequestsAreRejected() {
        assertEquals(WebChatHistoryMutation.Stale,
            WebAiChatHistory.rewind(conversation, "u2", WebChatRewindMode.RETRACT, 1, 2))
        assertEquals(WebChatHistoryMutation.MissingMessage,
            WebAiChatHistory.rewind(conversation, "not-present", WebChatRewindMode.RETRACT, 2, 2))
        assertEquals(WebChatHistoryMutation.EditRequiresUserMessage,
            WebAiChatHistory.rewind(conversation, "a1", WebChatRewindMode.EDIT_AND_RESEND, 2, 2))
        val duplicated = conversation + conversation.first()
        assertEquals(WebChatHistoryMutation.AmbiguousMessage,
            WebAiChatHistory.rewind(duplicated, "u1", WebChatRewindMode.RETRACT, 2, 2))
    }

    @Test
    fun clearRequiresCurrentHistoryRevisionAndOnlyClearsChatMessages() {
        assertEquals(WebChatHistoryMutation.Stale,
            WebAiChatHistory.clear(conversation, expectedRevision = 3, currentRevision = 4))
        val cleared = WebAiChatHistory.clear(conversation, expectedRevision = 4, currentRevision = 4)
        assertEquals(WebChatHistoryMutation.Applied(emptyList()), cleared)
        assertTrue((cleared as WebChatHistoryMutation.Applied).messages.isEmpty())
        assertEquals(WebChatHistoryMutation.NoChange,
            WebAiChatHistory.clear(emptyList(), expectedRevision = 4, currentRevision = 4))
    }

    @Test
    fun rewindingToTheLastAssistantReplyDoesNotCreateAHistoryMutation() {
        assertEquals(WebChatHistoryMutation.NoChange,
            WebAiChatHistory.rewind(conversation, "a2", WebChatRewindMode.RETRACT, 6, 6))
    }
}
