package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.core.api.v1.AiProposalReviewReadinessV1
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiProposalReview
import java.util.UUID

/** Host projection adapter; AI parsing objects and validation stay outside Native rendering. */
@Composable
internal fun AiProposalReview(
    interpretation: AiInterpretation,
    readiness: AiProposalReviewReadinessV1? = null,
    busy: Boolean,
    onApply: (Set<Int>, Boolean) -> Unit,
    onDiscard: (Set<Int>) -> Unit,
    onSupplement: () -> Unit
) {
    val model = remember(interpretation, readiness) {
        projectNativeAiProposalReview(interpretation, readiness)
    }
    val selectionKey = remember(interpretation) { UUID.randomUUID().toString() }
    NativeAiProposalReview(model, selectionKey, busy, onApply, onDiscard, onSupplement)
}
