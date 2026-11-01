package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiChatMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAiConversationHistoryPortV1Test {
    @Test
    fun adapterRoundTripsEveryExistingJsonFieldWithoutChangingTheFileShape() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = AiChatHistoryStore.load(context)
        val apiMessages = listOf(
            AiConversationMessageV1(
                role = AiConversationRoleV1.USER,
                content = "下周二提醒我开会",
                messageId = "state-user-1",
                createdAtMillis = 1_798_185_600_000,
                zoneId = "Asia/Shanghai"
            ),
            AiConversationMessageV1(
                role = AiConversationRoleV1.ASSISTANT,
                content = "可以先核对时间。",
                proposalSource = "显式候选文本",
                messageId = "state-assistant-1"
            )
        )
        val expectedHostMessages = apiMessages.map { message ->
            AiChatMessage(
                role = if (message.role == AiConversationRoleV1.USER) "user" else "assistant",
                content = message.content,
                proposalSource = message.proposalSource,
                messageId = message.messageId,
                createdAtMillis = message.createdAtMillis,
                zoneId = message.zoneId
            )
        }
        val port = AndroidAiConversationHistoryPortV1(context)

        try {
            port.save(apiMessages)

            val json = JSONArray(File(context.filesDir, "powercal-chat-history.json")
                .readText(Charsets.UTF_8))
            val item = json.getJSONObject(0)
            assertEquals(setOf(
                "role", "content", "proposalSource", "messageId", "createdAtMillis", "zoneId"
            ), item.keys().asSequence().toSet())
            assertEquals(apiMessages, port.load())
            assertEquals(expectedHostMessages, AiChatHistoryStore.load(context))
            assertTrue(json.length() <= 200)
        } finally {
            AiChatHistoryStore.save(context, previous)
        }
    }
}
