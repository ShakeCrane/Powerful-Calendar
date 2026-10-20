package dev.powerfulcalendar.ai

/**
 * Budgeted multi-turn context, instead of an arbitrary fixed 12-message cutoff.
 * This is not long-term memory; it is a bounded conversation slice.
 */
object AiConversationWindow {
    private const val MAX_MESSAGES = 40
    private const val MAX_CHARS = 28_000

    fun select(history: List<AiChatMessage>): List<AiChatMessage> {
        val result = ArrayList<AiChatMessage>()
        var size = 0
        for (message in history.asReversed()) {
            if (result.size >= MAX_MESSAGES || size + message.content.length > MAX_CHARS) break
            result += message
            size += message.content.length
        }
        return result.asReversed()
    }
}
