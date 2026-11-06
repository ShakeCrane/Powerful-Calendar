package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAiChatHistoryTest {
    @Test
    fun latestPageIsBoundedAndIncludesStableCoreMessageIds() {
        val messages = (0..41).map { index ->
            AiChatMessage("user", "m$index", messageId = "id-$index")
        }

        val page = WebAiChatHistory.project(messages)

        assertEquals(40, page.messages.length())
        assertEquals(2, page.start)
        assertEquals(42, page.endExclusive)
        assertTrue(page.hasOlder)
        assertEquals("id-2", page.messages.getJSONObject(0).getString("messageId"))
        assertEquals("id-41", page.messages.getJSONObject(39).getString("messageId"))
    }

    @Test
    fun previousPageIsLosslessAndEndsExactlyWhereLatestPageBegins() {
        val messages = (0..41).map { index ->
            AiChatMessage("user", "message $index", messageId = "id-$index")
        }

        val latest = WebAiChatHistory.project(messages)
        val older = WebAiChatHistory.project(messages, latest.start)

        assertEquals(0, older.start)
        assertEquals(latest.start, older.endExclusive)
        assertFalse(older.hasOlder)
        assertEquals(2, older.messages.length())
        assertEquals("id-0", older.messages.getJSONObject(0).getString("messageId"))
        assertEquals("id-1", older.messages.getJSONObject(1).getString("messageId"))
    }

    @Test
    fun completeMessageContentsArePreservedInsteadOfTruncated() {
        val longContent = "prefix" + "x".repeat(1_930) + "LONG_HISTORY_END_SENTINEL"
        val page = WebAiChatHistory.project(listOf(
            AiChatMessage("assistant", longContent, messageId = "long")
        ))

        assertEquals(longContent, page.messages.getJSONObject(0).getString("content"))
        assertTrue(page.messages.getJSONObject(0).getString("content").endsWith("LONG_HISTORY_END_SENTINEL"))
    }

    @Test
    fun escapedWorstCaseContentFitsThePageBudgetWithoutLosingCharacters() {
        val content = "\u0000".repeat(6_000)
        val page = WebAiChatHistory.project((0..7).map { index ->
            AiChatMessage("assistant", content, messageId = "id-$index")
        })

        assertTrue(page.messages.length() in 1..3)
        assertTrue(page.messages.toString().length <= WebAiChatHistory.PAGE_JSON_CHAR_BUDGET)
        assertEquals(content, page.messages.getJSONObject(0).getString("content"))
        assertTrue(page.hasOlder)
    }

    @Test
    fun emptyAndOutOfRangePagesAreSafe() {
        val empty = WebAiChatHistory.project(emptyList())
        assertEquals(0, empty.messages.length())
        assertEquals(0, empty.start)
        assertEquals(0, empty.endExclusive)
        assertFalse(empty.hasOlder)

        val messages = listOf(AiChatMessage("user", "body", messageId = "id"))
        assertEquals(1, WebAiChatHistory.project(messages, Int.MAX_VALUE).messages.length())
        assertEquals(0, WebAiChatHistory.project(messages, Int.MIN_VALUE).messages.length())
    }

    @Test
    fun proposalProjectionExposesOnlyWhetherNativeManualAffordanceExists() {
        val page = WebAiChatHistory.project(listOf(
            AiChatMessage("assistant", "reply", messageId = "plain"),
            AiChatMessage("assistant", "reply with source", proposalSource = "untrusted proposal input",
                messageId = "proposal")
        ))

        assertEquals(false, page.messages.getJSONObject(0).getBoolean("proposalAvailable"))
        assertEquals(true, page.messages.getJSONObject(1).getBoolean("proposalAvailable"))
        assertEquals(false, page.messages.getJSONObject(1).has("proposalSource"))
    }
}
