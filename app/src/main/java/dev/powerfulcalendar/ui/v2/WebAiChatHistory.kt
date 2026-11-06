package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiChatMessage
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, lossless UI pages over the Core-owned canonical conversation. */
internal object WebAiChatHistory {
    const val PROJECTION_LIMIT = 40
    const val PAGE_JSON_CHAR_BUDGET = 128 * 1024

    internal data class Page(
        val messages: JSONArray,
        val start: Int,
        val endExclusive: Int,
        val hasOlder: Boolean
    )

    fun project(
        messages: List<AiChatMessage>,
        endExclusive: Int = messages.size
    ): Page {
        val end = endExclusive.coerceIn(0, messages.size)
        val firstAllowed = (end - PROJECTION_LIMIT).coerceAtLeast(0)
        val newestFirst = mutableListOf<JSONObject>()
        var serializedChars = 2 // []

        for (index in end - 1 downTo firstAllowed) {
            val item = projectMessage(messages[index])
            val addedChars = item.toString().length + if (newestFirst.isEmpty()) 0 else 1
            if (newestFirst.isNotEmpty() && serializedChars + addedChars > PAGE_JSON_CHAR_BUDGET) {
                break
            }
            newestFirst += item
            serializedChars += addedChars
        }

        val start = end - newestFirst.size
        val projected = JSONArray()
        newestFirst.asReversed().forEach(projected::put)
        return Page(
            messages = projected,
            start = start,
            endExclusive = end,
            hasOlder = start > 0
        )
    }

    private fun projectMessage(message: AiChatMessage): JSONObject = JSONObject()
        .put("messageId", message.messageId)
        .put("role", message.role)
        .put("content", message.content)
        .put("proposalAvailable", !message.proposalSource.isNullOrBlank())
}
