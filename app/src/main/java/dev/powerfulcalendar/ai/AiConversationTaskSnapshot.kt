package dev.powerfulcalendar.ai

import java.time.Instant
import java.time.ZoneId

/** A user-authored message retained as dated evidence for one structured proposal attempt. */
data class AiTaskEvidence(
    val messageId: String,
    val text: String,
    val createdAtMillis: Long,
    val zoneId: String
)

/** Previous assistant text can help interpret answers, but is explicitly non-authoritative. */
data class AiTaskAssistantSuggestion(
    val messageId: String,
    val text: String
)

/**
 * Reconstructible, versioned task state derived from the locally persisted conversation.
 * The first explicit request owns the relative-date anchor; every later user correction is
 * retained as separate evidence. Assistant prose is carried separately as untrusted context.
 */
data class AiConversationTaskSnapshot(
    val taskId: String,
    val revision: Int,
    val anchorMillis: Long,
    val zoneId: String,
    val userEvidence: List<AiTaskEvidence>,
    val assistantSuggestions: List<AiTaskAssistantSuggestion>
) {
    init {
        require(taskId.isNotBlank())
        require(revision == evidenceRevision(userEvidence) && revision > 0)
        require(userEvidence.first().messageId == taskId)
        require(anchorMillis == userEvidence.first().createdAtMillis)
        require(ZoneId.of(zoneId).id == zoneId)
    }

    val anchorLocalDateTime: String
        get() = Instant.ofEpochMilli(anchorMillis).atZone(ZoneId.of(zoneId))
            .toLocalDateTime().toString()

    /** Pending intent remains actionable for at most one day from its trusted original request. */
    fun isValidAt(nowMillis: Long): Boolean = nowMillis >= anchorMillis &&
        nowMillis - anchorMillis <= MAX_TASK_AGE_MILLIS &&
        userEvidence.all { it.createdAtMillis <= nowMillis }

    companion object {
        /** Conservative expiry for unresolved natural-language intent; authorization remains separate. */
        const val MAX_TASK_AGE_MILLIS = 24L * 60 * 60 * 1000
        private const val MAX_EVIDENCE_CHARS = 20_000
        private val ACKNOWLEDGEMENTS = setOf(
            "确认", "确认吧", "同意", "是", "是的", "对", "没错", "好", "好的",
            "可以", "行", "就这样", "继续", "ok", "okay", "yes", "yep", "confirm"
        )
        private val NEGATIVE_ACKNOWLEDGEMENTS = setOf(
            "不", "不用", "不用了", "不需要", "不要", "否", "取消", "算了", "停止"
        )

        /** A confirmation turn does not revise the task's business facts. */
        private fun evidenceRevision(evidence: List<AiTaskEvidence>): Int = evidence.count {
            val normalized = normalizeEvidence(it.text)
            normalized.isNotEmpty() && normalized !in ACKNOWLEDGEMENTS
        }

        private fun normalizeEvidence(text: String): String = text.trim().lowercase()
            .replace(Regex("[\\p{P}\\p{Z}\\s]+"), "")

        /**
         * Returns null if the latest turn cannot be safely attached to one recent explicit task.
         * A legacy single-turn request may use this call's clock. Multi-turn legacy history with
         * missing timestamps is rejected so “today” is never silently re-anchored at confirmation.
         */
        fun from(
            history: List<AiChatMessage>,
            nowMillis: Long,
            fallbackZoneId: String,
            allowStandaloneScheduleStart: Boolean = false
        ): AiConversationTaskSnapshot? {
            if (history.isEmpty() || history.last().role != "user") return null
            val bounded = AiConversationWindow.select(history)
            val userIndices = bounded.indices.filter { bounded[it].role == "user" }
            val lastUserIndex = userIndices.lastOrNull() ?: return null
            if (lastUserIndex != bounded.lastIndex) return null

            val startIndex = userIndices.lastOrNull {
                isExplicitTaskStart(bounded[it].content) ||
                    (allowStandaloneScheduleStart && isStandaloneScheduleStatement(bounded[it].content))
            } ?: return null
            val start = bounded[startIndex]
            val trailingUserIndices = userIndices.filter { it >= startIndex }
            if (trailingUserIndices.any { index ->
                    index != startIndex && !isTaskContinuation(bounded, index)
                }) return null

            val zone = start.zoneId?.let { runCatching { ZoneId.of(it).id }.getOrNull() }
                ?: if (trailingUserIndices.size == 1) fallbackZoneId else null
                ?: return null
            val evidence = trailingUserIndices.map { index ->
                val message = bounded[index]
                val timestamp = message.createdAtMillis
                    ?: if (trailingUserIndices.size == 1) nowMillis else return null
                val messageZone = message.zoneId
                    ?.let { runCatching { ZoneId.of(it).id }.getOrNull() }
                    ?: if (trailingUserIndices.size == 1) zone else return null
                AiTaskEvidence(message.messageId, message.content, timestamp, messageZone)
            }
            if (evidence.sumOf { it.text.length } > MAX_EVIDENCE_CHARS) return null
            if (nowMillis < 0L || evidence.any { it.createdAtMillis > nowMillis } ||
                nowMillis - evidence.first().createdAtMillis > MAX_TASK_AGE_MILLIS
            ) return null

            val suggestions = bounded.subList(startIndex, lastUserIndex)
                .filter { it.role == "assistant" }
                .map { AiTaskAssistantSuggestion(it.messageId, it.content.take(4_000)) }
            return AiConversationTaskSnapshot(
                taskId = evidence.first().messageId,
                revision = evidenceRevision(evidence),
                anchorMillis = evidence.first().createdAtMillis,
                zoneId = zone,
                userEvidence = evidence,
                assistantSuggestions = suggestions
            )
        }

        private fun isStandaloneScheduleStatement(text: String): Boolean {
            val value = text.trim()
            if (value.isBlank() || isConfirmation(value) || isStrongCorrection(value) ||
                isTopicChangeOrQuestion(value)) return false
            // Used only when the conversational model explicitly classified the message as
            // an actionable request. Its suggestion tag alone is NOT enough.
            val hasTime = Regex(
                "(?:[0-2]?\\d[:\\uFF1A\\u70B9\\u65F6])|" +
                    "(?:\\u4ECA\\u5929|\\u4ECA\\u665A|\\u660E\\u5929|\\u5468[\\u4E00-\\u516D])"
            ).containsMatchIn(value)
            val hasSubject = Regex(
                "\\u4F1A\\u8BAE|\\u6D3B\\u52A8|\\u8BFE|\\u590D\\u4E60|" +
                    "\\u95F9\\u949F|\\u63D0\\u9192|\\u622A\\u6B62|\\u51FA\\u53D1|" +
                    "\\u8003\\u8BD5|\\u805A\\u9910|\\u65E5\\u7A0B"
            ).containsMatchIn(value)
            return hasTime && hasSubject
        }

        private fun isExplicitTaskStart(text: String): Boolean {
            val value = text.trim()
            if (value.isEmpty() || isStrongCorrection(value) || isConfirmation(value)) return false
            val hasCreateIntent = Regex(
                "创建|新增|新建|安排|添加|记下|记一下|录入|设置提醒|设提醒|提醒我|帮我记|帮我创建|帮我安排|create|add|schedule|remind|log",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(value)
            val hasCalendarSubject = Regex(
                "事件|事项|日程|活动|会议|课程|闹钟|提醒|截止|备忘|待办|重复|行程|event|activity|meeting|alarm|deadline|memo",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(value)
            val hasDateOrTime = Regex(
                "(?:20\\d{2}[-年/])|(?:今天|明天|后天|周[一二三四五六日天])|(?:[0-2]?\\d[:：点时])"
            ).containsMatchIn(value)
            return hasCreateIntent && (hasCalendarSubject || hasDateOrTime)
        }

        private fun isTaskContinuation(history: List<AiChatMessage>, index: Int): Boolean {
            val text = history[index].content.trim()
            if (isTopicChangeOrQuestion(text)) return false
            val previousAssistant = (index - 1 downTo 0)
                .firstOrNull { history[it].role == "assistant" }
                ?.let { history[it].content }
                .orEmpty()
            if (isStrongCorrection(text)) return true
            if (isNegativeAcknowledgement(text)) {
                // A bare “不用” answers a recurrence question as a correction, but rejects a
                // proposed create/save action. Never reinterpret that rejection as approval.
                return previousAssistantAskedAboutRecurrence(previousAssistant)
            }
            if (isStrongContinuation(text)) return true
            return previousAssistantAskedForDetails(previousAssistant) && text.length <= 240
        }

        private fun isTopicChangeOrQuestion(text: String): Boolean =
            text.contains('?') || text.contains('？') || Regex(
                "^(?:请问|请(?:解释|介绍|说明|告诉|查询|搜索)|你能(?:解释|介绍|说明|告诉|查询|搜索)|(?:解释|介绍|说明)一下|我(?:想)?(?:问|有个问题|了解|聊)|顺便(?:问|说)|另外|此外|换个话题|为什么|怎么(?:样|做|会)|什么是|帮我(?:查|搜|了解)|tell me|explain|why\\b|how\\b)",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(text.trim())

        private fun isStrongContinuation(text: String): Boolean {
            val normalized = text.trim().lowercase()
                .replace(Regex("[\\s，。！？!?、；;：:]+$"), "")
            if (isConfirmation(normalized) || isStrongCorrection(normalized)) return true
            return Regex(
                "标题|名称|改成|改为|补充|分别|重叠|时间|日期|开始|结束|几点|哪天|多几个|五个|多个事件|每个"
            ).containsMatchIn(normalized)
        }

        private fun isConfirmation(text: String): Boolean {
            val normalized = text.trim().lowercase()
                .replace(Regex("[\\s，。！？!?、；;：:]+$"), "")
            val confirmations = setOf(
                "确认", "确认吧", "同意", "是", "是的", "对", "没错", "好", "好的",
                "可以", "行", "就这样", "继续"
            )
            return normalized in confirmations
        }

        private fun isNegativeAcknowledgement(text: String): Boolean {
            val normalized = text.trim().lowercase()
                .replace(Regex("[\\s，。！？!?、；;：:]+$"), "")
            return normalized in NEGATIVE_ACKNOWLEDGEMENTS
        }

        private fun previousAssistantAskedAboutRecurrence(text: String): Boolean = Regex(
            "重复|周期|每周|每月|每年|rrule", RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        private fun isStrongCorrection(text: String): Boolean {
            val normalized = text.trim().lowercase()
                .replace(Regex("[\\s，。！？!?、；;：:]+$"), "")
            return Regex(
                "我的意思|不是.{0,10}(?:重复|周期)|不用重复|不需要重复|不要重复|不重复|单次|一次性|就叫|叫这个|更正|纠正|刚才|这个|那就"
            ).containsMatchIn(normalized)
        }

        private fun previousAssistantAskedForDetails(text: String): Boolean =
            text.contains('?') || text.contains('？') || Regex(
                "请(?:告诉|确认|补充)|还需要|需要.*吗|希望.*(?:标题|时间|日期)|标题(?:叫|是|是什么)|名称是什么|哪一个|哪一天|什么时候|几点|多少个|是否"
            ).containsMatchIn(text)
    }
}
