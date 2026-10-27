package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalKind

/**
 * Bounded, strictly incomplete REVIEW fallback when an explicitly requested structured Skill
 * response has no items. Never fabricate an instant, eventSeq or permanent calendar object.
 */
internal object AiPartialProposalFallback {
    fun recover(task: AiConversationTaskSnapshot, original: AiInterpretation): AiInterpretation {
        if (original.proposals.isNotEmpty()) return original
        val userText = task.userEvidence.joinToString("\n") { it.text }.take(2_000)
        val kind = inferredKind(userText) ?: return original
        val count = explicitCount(userText).coerceIn(1, 20)
        val forms = (0 until count).map { index ->
            AiProposal(
                operation = DraftOperation.CREATE,
                eventSeq = null,
                form = DraftForm(
                    kind = kind, title = "",
                    notes = ("待补全 · 用户原始请求：\n" + userText).take(2_000),
                    reminderMinutes = if (kind == TemporalKind.DEADLINE) 60
                        else if (kind == TemporalKind.MEMO) -1 else 0,
                    zoneId = task.zoneId,
                    source = "ai"
                ),
                rationale = "仅供审核，时间、标题及其他未确认内容留空",
                sourceIndex = index
            )
        }
        return original.copy(
            proposals = forms,
            warnings = original.warnings +
                "结构化模型未返回可用事件：已依据用户明确的事件类型提供待补全卡片。" +
                " 日期时间未猜测；补全后才能发布。"
        )
    }

    private fun inferredKind(text: String): TemporalKind? {
        // An unresolved modification/deletion must NEVER become a new CREATE.
        if (Regex("修改|删除|移除|替换|改成|改为|编辑|撤销").containsMatchIn(text)) return null
        val kinds = mutableSetOf<TemporalKind>()
        if (Regex("闹钟|定时提醒|提醒我|alarm", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)) kinds += TemporalKind.ALARM
        if (Regex("截止|deadline|ddl|最迟|到期", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)) kinds += TemporalKind.DEADLINE
        if (Regex("备忘|待办|memo|to.?do", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)) kinds += TemporalKind.MEMO
        if (kinds.isEmpty() && Regex("活动|会议|开会|课程|复习|日程|事件|event|activity", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)) kinds += TemporalKind.ACTIVITY
        return kinds.singleOrNull()
    }

    private fun explicitCount(text: String): Int {
        val count = Regex("""(?:创建|新建|添加|安排|生成).{0,25}?(\d{1,2})\s*(?:个|条|项)""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return count ?: 1
    }
}
