package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Demonstrates root chrome and notice re-theming without editing PowerCalApp. */
internal class StudioExampleChrome : ChromeVisualRenderer {
    override val id = "studio-example/chrome"
    @Composable override fun Render(context: ChromeVisualContext) {
        when (context.type) {
            ChromeType.ROOT -> {
                val color = MaterialTheme.colorScheme
                context.Body(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(
                            listOf(color.background, color.surfaceContainerHigh, color.background)
                        ))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                )
            }
            ChromeType.NOTICE -> Surface(
                modifier = context.modifier.semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp
            ) {
                Text(
                    context.message.orEmpty(),
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
