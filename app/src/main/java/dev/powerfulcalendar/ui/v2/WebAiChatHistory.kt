package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiChatMessage
import org.json.JSONArray
import org.json.JSONObject

/** Bounded UI projection of the Core-owned canonical conversation. */
internal object WebAiChatHistory {
    const val PROJECTION_LIMIT = 40
    private const val PROJECTED_CONTENT_LIMIT = 1_600

    fun project(messages: List<AiChatMessage>): JSONArray = JSONArray(
        messages.takeLast(PROJECTION_LIMIT).map { message ->
            JSONObject()
                .put("messageId", message.messageId)
                .put("role", message.role)
                .put("content", message.content.take(PROJECTED_CONTENT_LIMIT))
                .put("proposalAvailable", !message.proposalSource.isNullOrBlank())
        }
    )

}
