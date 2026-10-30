package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.ui.v2.OverlayType
import dev.powerfulcalendar.ui.v2.OverlayVisualContext
import dev.powerfulcalendar.ui.v2.OverlayVisualRenderer

/** First-party Native modal renderer. All content and action closures remain Host supplied. */
object NativeOverlaySkin : OverlayVisualRenderer {
    override val id: String = "native/overlay"

    @Composable
    override fun Render(context: OverlayVisualContext) {
        when (context.type) {
            OverlayType.ALERT -> Alert(
                onDismissRequest = context.onDismissRequest,
                confirmButton = requireNotNull(context.confirmButton),
                modifier = context.modifier,
                dismissButton = context.dismissButton,
                icon = context.icon,
                title = context.title,
                text = context.text,
                properties = context.properties
            )
            OverlayType.WINDOW -> Window(
                onDismissRequest = context.onDismissRequest,
                properties = context.properties,
                content = requireNotNull(context.content)
            )
        }
    }

    @Composable
    fun Alert(
        onDismissRequest: () -> Unit,
        confirmButton: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        dismissButton: (@Composable () -> Unit)? = null,
        icon: (@Composable () -> Unit)? = null,
        title: (@Composable () -> Unit)? = null,
        text: (@Composable () -> Unit)? = null,
        properties: DialogProperties = DialogProperties()
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            properties = properties
        )
    }

    @Composable
    fun Window(
        onDismissRequest: () -> Unit,
        properties: DialogProperties = DialogProperties(),
        content: @Composable () -> Unit
    ) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
    }
}