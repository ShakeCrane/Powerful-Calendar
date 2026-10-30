package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiProposalReview
import java.util.UUID

/** Host projection adapter; AI parsing objects and validation stay outside Native rendering. */
@Composable
internal fun AiProposalReview(
    interpretation: AiInterpretation,
    busy: Boolean,
    onApply: (Set<Int>, Boolean) -> Unit,
    onDiscard: (Set<Int>) -> Unit,
    onSupplement: () -> Unit
) {
    val model = remember(interpretation) { projectNativeAiProposalReview(interpretation) }
    val selectionKey = remember(interpretation) { UUID.randomUUID().toString() }
    NativeAiProposalReview(model, selectionKey, busy, onApply, onDiscard, onSupplement)
}
