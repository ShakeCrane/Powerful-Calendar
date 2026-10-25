package dev.powerfulcalendar.ai

/**
 * Natural-language routing is a hint to show REVIEW candidates, never calendar write authority.
 * The user's explicit command must not depend on a provider emitting a private marker.
 */
internal object AiChatIntentRouting {
    data class Result(val intent: ChatProposalIntent?, val task: AiConversationTaskSnapshot?)

    private val assistantAsksForReview = Regex(
        """\u786E\u8BA4|\u751F\u6210|\u521B\u5EFA|\u63D0\u6848|\u662F\u5426"""
    )
    private val directCalendarCommand = Regex(
        """^(?:\u8BF7|\u5E2E\u6211|\u7ED9\u6211|\u66FF\u6211)?\s*""" +
            """(?:\u5728.{0,35})?""" +
            """(?:\u521B\u5EFA|\u65B0\u5EFA|\u6DFB\u52A0|\u5B89\u6392|""" +
            """\u751F\u6210|\u8BBE\u7F6E\u63D0\u9192|\u8BBE\u4E2A\u95F9\u949F|""" +
            """\u63D0\u9192\u6211|\u5220\u9664|\u4FEE\u6539|""" +
            """\u628A.{1,40}(?:\u6539\u6210|\u6539\u4E3A))"""
    )

    fun resolve(
        parsed: ChatPresentation,
        history: List<AiChatMessage>,
        nowMillis: Long,
        zoneId: String
    ): Result {
        val last = history.lastOrNull()?.takeIf { it.role == "user" }?.content.orEmpty().trim()
        val explicitCommand = isDirectCalendarCommand(last)
        val confirmation = isConfirmation(last) &&
            assistantAsksForReview.containsMatchIn(
                history.dropLast(1).lastOrNull { it.role == "assistant" }?.content.orEmpty()
            )
        val modelExplicit = parsed.proposalIntent == ChatProposalIntent.EXPLICIT
        if (!modelExplicit && !explicitCommand && !confirmation)
            return Result(parsed.proposalIntent, null)

        val task = AiConversationTaskSnapshot.from(
            history, nowMillis, zoneId,
            allowStandaloneScheduleStart = modelExplicit
        )
        return if (task != null) Result(ChatProposalIntent.EXPLICIT, task)
        else Result(
            if (modelExplicit || explicitCommand) ChatProposalIntent.EXPLICIT
            else parsed.proposalIntent,
            null
        )
    }

    private fun isDirectCalendarCommand(text: String): Boolean {
        if (text.isBlank() || text.contains('?') || text.contains('\uFF1F')) return false
        return directCalendarCommand.containsMatchIn(text)
    }

    private fun isConfirmation(text: String): Boolean {
        val normalized = text.trim().lowercase().trimEnd {
            it.isWhitespace() || it == '\u3002' || it == '\uFF0C' || it == '\uFF01' ||
                it == '!' || it == '.' || it == ',' || it == ':'
        }
        return normalized in setOf(
            "\u786E\u8BA4", "\u540C\u610F", "\u53EF\u4EE5",
            "\u597D\u7684", "\u5C31\u8FD9\u6837", "ok"
        )
    }
}
