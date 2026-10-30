package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

/** Host dialog slots for the Native natural-language draft prompt. */
data class NativeTextDraftDialogContent(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable () -> Unit
)

typealias NativeTextDraftDialogSlot = @Composable (NativeTextDraftDialogContent) -> Unit
