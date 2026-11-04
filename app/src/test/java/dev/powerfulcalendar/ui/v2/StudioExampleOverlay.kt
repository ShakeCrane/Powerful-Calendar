package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Demonstrates that confirmation, file review, restore and other Compose modal dialogs can be
 * completely rearranged by a compiled skin, without access to their business implementations.
 * Native window dialogs are kept for the full-screen gesture-intensive deck by this example;
 * custom renderers are free to render context.content instead, using context.properties.
 */
internal class StudioExampleOverlay : OverlayVisualRenderer {
    override val id: String = "studio-example/overlay"

    @Composable override fun Render(context: OverlayVisualContext) {
        if (context.type == OverlayType.WINDOW) {
            context.Native()
            return
        }
        Dialog(
            onDismissRequest = context.onDismissRequest,
            properties = context.properties
        ) {
            Surface(
                modifier = context.modifier.fillMaxWidth().padding(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(30.dp),
                tonalElevation = 10.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    context.icon?.invoke()
                    context.title?.invoke()
                    Column(
                        Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { context.text?.invoke() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        context.dismissButton?.invoke()
                        context.confirmButton?.invoke()
                    }
                }
            }
        }
    }
}
