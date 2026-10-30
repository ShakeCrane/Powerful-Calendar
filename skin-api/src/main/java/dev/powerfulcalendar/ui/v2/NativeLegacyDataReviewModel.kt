package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

enum class NativeLegacyDataReviewKind { MIGRATE_DATABASE, RESTORE_FLAT_BACKUP }

/** Copy and callbacks for legacy-data confirmation; source data and restore authority stay in Host. */
data class NativeLegacyDataReviewDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable () -> Unit
)

typealias NativeLegacyDataReviewDialogSlot = @Composable (NativeLegacyDataReviewDialog) -> Unit
