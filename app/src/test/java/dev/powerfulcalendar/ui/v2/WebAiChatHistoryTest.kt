package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebAiChatHistoryTest {
    @Test
    fun projectionIsBoundedAndIncludesStableCoreMessageIds() {
        val messages = (0..41).map { index ->
            AiChatMessage("user", "m$index", messageId = "id-$index")
        }

        val projected = WebAiChatHistory.project(messages)

        assertEquals(WebAiChatHistory.PROJECTION_LIMIT, projected.length())
        assertEquals("id-2", projected.getJSONObject(0).getString("messageId"))
        assertEquals("id-41", projected.getJSONObject(39).getString("messageId"))
    }
}
