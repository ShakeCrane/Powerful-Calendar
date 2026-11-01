package dev.powerfulcalendar.core.api.v1

import android.content.Context
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android persistence adapter for the existing app-private, version-1 transcript file. */
internal class AndroidAiConversationHistoryPortV1(context: Context) : AiConversationHistoryPortV1 {
    private val appContext = context.applicationContext

    override fun load(): List<AiConversationMessageV1> =
        AiChatHistoryStore.load(appContext).map { message -> message.toApiMessage() }

    override suspend fun save(messages: List<AiConversationMessageV1>) {
        withContext(Dispatchers.IO) {
            AiChatHistoryStore.save(appContext, messages.map { it.toHostMessage() })
        }
    }

    private fun AiChatMessage.toApiMessage() = AiConversationMessageV1(
        role = if (role == "user") AiConversationRoleV1.USER else AiConversationRoleV1.ASSISTANT,
        content = content,
        proposalSource = proposalSource,
        messageId = messageId,
        createdAtMillis = createdAtMillis,
        zoneId = zoneId
    )

    private fun AiConversationMessageV1.toHostMessage() = AiChatMessage(
        role = role.name.lowercase(),
        content = content,
        proposalSource = proposalSource,
        messageId = messageId,
        createdAtMillis = createdAtMillis,
        zoneId = zoneId
    )
}
