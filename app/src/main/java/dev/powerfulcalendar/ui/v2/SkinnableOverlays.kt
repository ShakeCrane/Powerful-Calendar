package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Presentation-only adapter for ALL Compose dialog windows.
 *
 * Composable slots are supplied by the host: the skin may reposition or decorate them, but
 * never sees an AI key, a database controller or authority to skip the supplied confirmation.
 * Dialog body lifetime and its closures remain owned by the host.
 *
 * This is a source-compiled renderer API, not a dynamically loaded script.
 */
internal enum class OverlayType { ALERT, WINDOW }

internal class OverlayVisualContext internal constructor(
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
    /** Exact native fallback including its supplied business closures. */
    @Composable fun Native() = firstParty()
}

/**
 * Renderer is responsible for mounting a genuine modal window and providing usable action
 * affordances. Hide neither dismiss nor confirmation content; preserve DialogProperties.
 */
internal interface OverlayVisualRenderer {
    val id: String
    @Composable fun Render(context: OverlayVisualContext)
}

@Composable
internal fun SkinAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val native: @Composable () -> Unit = {
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
    val renderer = PresentationSkinRegistry.overlay
    if (renderer == null && !RuntimeSkinSurfaces.enabled("overlay")) native()
    else {
        val context = OverlayVisualContext(
        type = OverlayType.ALERT,
        onDismissRequest = onDismissRequest, modifier = modifier,
        properties = properties, title = title, text = text, confirmButton = confirmButton,
        dismissButton = dismissButton, icon = icon, content = null, firstParty = native
        )
        if (RuntimeSkinSurfaces.enabled("overlay")) RuntimeSkinSurfaces.overlay(context)
        else renderer!!.Render(context)
    }
}

@Composable
internal fun SkinWindowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val native: @Composable () -> Unit = {
        Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
    }
    val renderer = PresentationSkinRegistry.overlay
    if (renderer == null && !RuntimeSkinSurfaces.enabled("overlay")) native()
    else {
        val context = OverlayVisualContext(
        type = OverlayType.WINDOW, onDismissRequest = onDismissRequest,
        modifier = Modifier, properties = properties, title = null, text = null,
        confirmButton = null, dismissButton = null, icon = null, content = content,
        firstParty = native
        )
        if (RuntimeSkinSurfaces.enabled("overlay")) RuntimeSkinSurfaces.overlay(context)
        else renderer!!.Render(context)
    }
}
