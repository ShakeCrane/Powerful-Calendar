package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.ui.v2.nativeui.NativeOverlaySkin

/**
 * Host adapters for all Compose dialog windows. Skins may reposition or decorate supplied slots,
 * but never receive business controllers or authority to skip the Host's confirmation actions.
 */
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
        NativeOverlaySkin.Alert(
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
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            properties = properties,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            icon = icon,
            content = null,
            firstParty = native
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
        NativeOverlaySkin.Window(onDismissRequest = onDismissRequest, properties = properties, content = content)
    }
    val renderer = PresentationSkinRegistry.overlay
    if (renderer == null && !RuntimeSkinSurfaces.enabled("overlay")) native()
    else {
        val context = OverlayVisualContext(
            type = OverlayType.WINDOW,
            onDismissRequest = onDismissRequest,
            modifier = Modifier,
            properties = properties,
            title = null,
            text = null,
            confirmButton = null,
            dismissButton = null,
            icon = null,
            content = content,
            firstParty = native
        )
        if (RuntimeSkinSurfaces.enabled("overlay")) RuntimeSkinSurfaces.overlay(context)
        else renderer!!.Render(context)
    }
}
