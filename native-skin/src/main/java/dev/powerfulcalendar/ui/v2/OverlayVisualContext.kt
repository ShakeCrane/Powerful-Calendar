package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/** Presentation-only slots for standard AlertDialog and full-window modal surfaces. */
enum class OverlayType { ALERT, WINDOW }

class OverlayVisualContext(
    val type: OverlayType,
    val onDismissRequest: () -> Unit,
    val modifier: Modifier,
    val properties: DialogProperties,
    val title: (@Composable () -> Unit)?,
    val text: (@Composable () -> Unit)?,
    val confirmButton: (@Composable () -> Unit)?,
    val dismissButton: (@Composable () -> Unit)?,
    val icon: (@Composable () -> Unit)?,
    val content: (@Composable () -> Unit)?,
    private val firstParty: @Composable () -> Unit
) {
    /** Exact Native renderer, retaining the Host-provided action closures. */
    @Composable fun Native() = firstParty()
}

interface OverlayVisualRenderer {
    val id: String
    @Composable fun Render(context: OverlayVisualContext)
}