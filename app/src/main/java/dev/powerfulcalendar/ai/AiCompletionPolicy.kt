package dev.powerfulcalendar.ai

/** Bounded model-specific output policy; no provider switching or autonomous paid fallback. */
internal object AiCompletionPolicy {
    private const val RETRYABLE_PAID_MODEL = "deepseek/deepseek-v4.1-flash"

    fun outputBudget(model: String, requested: Int): Int =
        if (model.endsWith(":free")) requested.coerceAtLeast(2048).coerceAtMost(4096)
        else requested

    fun shouldRetryEmpty(
        model: String, finishReason: String, requested: Int, allowRetry: Boolean
    ): Boolean = allowRetry &&
        model == RETRYABLE_PAID_MODEL &&
        finishReason.equals("length", ignoreCase = true) &&
        requested < 2048
}
