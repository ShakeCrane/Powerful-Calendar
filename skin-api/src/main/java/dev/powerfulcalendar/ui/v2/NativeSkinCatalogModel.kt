package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

enum class NativeSkinCatalogReviewKind { ACTIVATE, DELETE }

/** Display-only confirmation surface; skin identity, selection and deletion stay in the Host. */
data class NativeSkinCatalogDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable () -> Unit
)

typealias NativeSkinCatalogDialogSlot = @Composable (NativeSkinCatalogDialog) -> Unit
