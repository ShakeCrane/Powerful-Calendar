package dev.powerfulcalendar.ai

import org.junit.Assert.*
import org.junit.Test

class AiCompletionPolicyTest {
    @Test fun freeModelsGetReasoningHeadroomBeforeFirstCall() {
        assertEquals(2048, AiCompletionPolicy.outputBudget("stealth/glyph-cluster:free", 540))
        assertEquals(2048, AiCompletionPolicy.outputBudget("reflection/beam:free", 900))
        assertEquals(3000, AiCompletionPolicy.outputBudget("reflection/beam:free", 3000))
    }

    @Test fun paidModelKeepsRequestedBudgetWithoutSwitchingOrSpendingExtra() {
        assertEquals(540, AiCompletionPolicy.outputBudget("deepseek/deepseek-v4.1-flash", 540))
    }

    @Test fun freeModelNeverBlindlyRetriesEmptyHttpSuccess() {
        assertFalse(AiCompletionPolicy.shouldRetryEmpty(
            "stealth/glyph-cluster:free", "length", 2048, true))
        assertFalse(AiCompletionPolicy.shouldRetryEmpty(
            "stealth/glyph-cluster:free", "stop", 2048, true))
    }

    @Test fun paidModelRetriesOnlyConfirmedTruncationWithinBudget() {
        assertTrue(AiCompletionPolicy.shouldRetryEmpty(
            "deepseek/deepseek-v4.1-flash", "length", 540, true))
        assertFalse(AiCompletionPolicy.shouldRetryEmpty(
            "deepseek/deepseek-v4.1-flash", "stop", 540, true))
        assertFalse(AiCompletionPolicy.shouldRetryEmpty(
            "deepseek/deepseek-v4.1-flash", "length", 2048, true))
        assertFalse(AiCompletionPolicy.shouldRetryEmpty(
            "deepseek/deepseek-v4.1-flash", "length", 540, false))
    }

    @Test fun otherPaidModelsNeverReceiveAnAutomaticRetry() {
        assertFalse(AiCompletionPolicy.shouldRetryEmpty(
            "another/provider-paid-model", "length", 540, true))
    }
}
