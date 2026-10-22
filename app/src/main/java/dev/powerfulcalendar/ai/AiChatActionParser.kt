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
        // Never truncate a natural-language answer mid-sentence. Malformed internal markers
        // are hidden rather than printed verbatim as if they were user-facing prose.
        if (begin < 0 && end < 0) {
            val visible = raw.trim()
            // A provider may ignore the conversational mode and return an internal
            // proposal/tool payload. Never print it as a chat answer.
            val internal = if (visible.startsWith("{")) runCatching {
                val objectBody = JSONObject(visible)
                objectBody.has("events") || objectBody.has("ops") ||
                    objectBody.has("tool_calls") || objectBody.has("draftIds")
            }.getOrDefault(false) else false
            return ChatPresentation(if (internal)
                "收到结构化日程内容，但尚未完成本地核对，没有修改日历。"
                else visible)
        }
        if (begin < 0 || end <= begin || raw.substring(end + CLOSE.length).isNotBlank()) {
            return ChatPresentation(raw.substringBefore(OPEN).substringBefore(CLOSE)
                .trim().ifBlank { "本次回复未形成可展示的完整结果，请重试。" })
        }
        val jsonText = raw.substring(begin + OPEN.length, end).trim()
        if (jsonText.length !in 2..4200) return ChatPresentation(
            raw.take(begin).trim().ifBlank { "日程提案格式有误，请重试。" })
        val hint = runCatching { JSONObject(jsonText) }.getOrNull()
        val source = if (hint?.optString("type") == "draft") {
            hint.optString("source").trim().takeIf { it.isNotBlank() && it.length <= 4000 }
        } else null
        return ChatPresentation(
            text = raw.take(begin).trim().ifBlank { "我识别到日程信息，可以先生成待确认草稿。" },
            proposalSource = source
        )
    }
}
