package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

/** Chat text needed by the Native renderer; provider and persistence metadata stay in the Host. */
data class NativeAiMessage(
    val mine: Boolean,
    val content: String,
    val proposalSource: String? = null
)

enum class NativeAiBatchMode {
    PUBLISH,
    DISCARD
}

data class NativeAiBatchOperationCount(
    val operationLabel: String,
    val count: Int
)

data class NativeAiBatchReviewRow(
    val sequenceLabel: String,
    val operationLabel: String,
    val title: String
)

/** Display-only frozen batch review. Draft IDs, versions, and Core models stay in the Host. */
data class NativeAiBatchAuthorizationModel(
    val mode: NativeAiBatchMode,
    val count: Int,
    val operationCounts: List<NativeAiBatchOperationCount>,
    val containsFormalDeletion: Boolean,
    val rows: List<NativeAiBatchReviewRow>
)

data class NativeAiBatchAuthorizationDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable () -> Unit
)

typealias NativeAiBatchAuthorizationDialogSlot = @Composable (NativeAiBatchAuthorizationDialog) -> Unit

data class NativeAiInputReviewDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable () -> Unit
)

typealias NativeAiInputReviewDialogSlot = @Composable (NativeAiInputReviewDialog) -> Unit

enum class NativeAiProposalGroup { CREATE, EXISTING }

/** Display-only fields for one AI proposal; its original position keys Host callbacks. */
data class NativeAiProposalRow(
    val position: Int,
    val group: NativeAiProposalGroup,
    val title: String,
    val caption: String,
    val startText: String,
    val endText: String,
    val blockedReason: String?,
    val publishable: Boolean
)

/** No parser reply, AI object, DraftForm, or persistence identity crosses into Native rendering. */
data class NativeAiProposalReviewModel(
    val rows: List<NativeAiProposalRow>,
    val warnings: List<String>
)
