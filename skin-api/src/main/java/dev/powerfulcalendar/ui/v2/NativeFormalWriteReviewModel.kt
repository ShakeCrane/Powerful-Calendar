package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

/** Display-only snapshot for a Host-owned formal write confirmation. */
data class NativeFormalWriteReviewRow(
    val label: String,
    val before: String,
    val after: String
)

data class NativeFormalWriteReviewModel(
    val title: String,
    val targetSummary: String?,
    val scopeSummary: String?,
    val occurrenceSummary: String?,
    val explanation: String,
    val rows: List<NativeFormalWriteReviewRow>,
    val confirmLabel: String,
    val resolving: Boolean
)

/** Host-owned dialog shell and action slots; approval state and write authority stay in Host. */
data class NativeFormalWriteReviewDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable () -> Unit
)

typealias NativeFormalWriteReviewDialogSlot = @Composable (NativeFormalWriteReviewDialog) -> Unit
