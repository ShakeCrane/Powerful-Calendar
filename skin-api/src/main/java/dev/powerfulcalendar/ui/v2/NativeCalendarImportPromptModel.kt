package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

/** Dialog slots for the first-run system-calendar import prompt. */
data class NativeCalendarImportPromptDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable () -> Unit
)

typealias NativeCalendarImportPromptDialogSlot = @Composable (NativeCalendarImportPromptDialog) -> Unit
