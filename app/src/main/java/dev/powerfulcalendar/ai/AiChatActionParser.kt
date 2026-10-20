package dev.powerfulcalendar.ai

import org.json.JSONObject

/**
 * A model MAY attach a proposal affordance to a short conversational answer.
 * This is a HINT only: touching the button invokes the deterministic calendar-draft Skill,
 * which independently parses/validates the source; the hint itself never writes anything.
 */
data class ChatPresentation(val text: String, val proposalSource: String? = null)

object AiChatActionParser {
    const val OPEN = "[[POWERCAL_DRAFT]]"
    const val CLOSE = "[[/POWERCAL_DRAFT]]"

    fun parse(raw: String): ChatPresentation {
        val begin = raw.lastIndexOf(OPEN)
        val end = raw.lastIndexOf(CLOSE)
        if (begin < 0 || end <= begin || raw.substring(end + CLOSE.length).isNotBlank()) {
            return ChatPresentation(raw.trim().take(1000))
        }
        val jsonText = raw.substring(begin + OPEN.length, end).trim()
        if (jsonText.length !in 2..4200) return ChatPresentation(raw.take(begin).trim().take(1000))
        val hint = runCatching { JSONObject(jsonText) }.getOrNull()
        val source = if (hint?.optString("type") == "draft") {
            hint.optString("source").trim().takeIf { it.isNotBlank() && it.length <= 4000 }
        } else null
        return ChatPresentation(
            text = raw.take(begin).trim().take(1000).ifBlank { "我识别到日程信息，可以先生成待确认草稿。" },
            proposalSource = source
        )
    }
}
