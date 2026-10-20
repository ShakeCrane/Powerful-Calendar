package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.DraftCard
import org.json.JSONObject

/**
 * Selection is READ-ONLY. It never grants permission or commits a batch.
 * The user must review a frozen list of exact draftIds and draftVersions in the app.
 */
data class AiBatchSelection(
    val selectedIds: List<Long> = emptyList(),
    val reply: String = "",
    val question: String? = null,
    val error: String? = null
) {
    val ready: Boolean get() = error == null && question == null && selectedIds.isNotEmpty()
}

object AiBatchSelectionParser {
    private const val MAX_RAW = 32_000
    fun parse(raw: String, candidates: List<DraftCard>): AiBatchSelection {
        if (raw.length > MAX_RAW) return failure("模型选择结果超过大小上限")
        val fence = 96.toChar().toString().repeat(3)
        val trimmed = raw.trim().removePrefix(fence + "json").removePrefix(fence)
            .removeSuffix(fence).trim()
        val data = runCatching { JSONObject(trimmed) }.getOrNull()
            ?: return failure("无法验证 AI 给出的批量目标")
        val mode = data.optString("mode")
        val reply = data.optString("reply").take(500)
        if (mode == "CLARIFY") {
            return AiBatchSelection(reply = reply,
                question = data.optString("question").take(300).ifBlank { "请说明要处理哪些草稿。" })
        }
        if (mode != "SELECT") return failure("AI 返回了未知的选择模式")
        val arr = data.optJSONArray("draftIds") ?: return failure("缺少具体草稿 ID")
        if (arr.length() !in 1..100) return failure("选择的草稿数量无效或超过 100")
        val allowed = candidates.filter { it.isActionable }.associateBy { it.draftId }
        val ids = mutableListOf<Long>()
        for (i in 0 until arr.length()) {
            val rawId = arr.opt(i)?.toString().orEmpty()
            if (!rawId.matches(Regex("[1-9][0-9]{0,17}"))) return failure("AI 给出了非法草稿 ID")
            val id = rawId.toLongOrNull() ?: return failure("草稿 ID 超出合法范围")
            if (!allowed.containsKey(id)) return failure("AI 选择了范围外或不可处理的草稿")
            if (id in ids) return failure("AI 给出了重复的草稿 ID")
            ids += id
        }
        return AiBatchSelection(selectedIds = ids, reply = reply.ifBlank { "已找到候选草稿" })
    }

    private fun failure(message: String) = AiBatchSelection(error = message)
}
