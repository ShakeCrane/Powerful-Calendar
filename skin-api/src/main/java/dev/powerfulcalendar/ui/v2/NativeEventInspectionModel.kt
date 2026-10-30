package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

/** Display-only event text used while an AI batch makes formal events read-only. */
data class NativeEventInspectionModel(
    val title: String,
    val summary: String,
    val notes: String
)

data class NativeEventInspectionDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit
)

typealias NativeEventInspectionDialogSlot = @Composable (NativeEventInspectionDialog) -> Unit
