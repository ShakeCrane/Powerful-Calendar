package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.ChromeType
import dev.powerfulcalendar.ui.v2.ChromeVisualContext
import dev.powerfulcalendar.ui.v2.ChromeVisualRenderer

/** First-party Native implementation for the app shell and transient status notice. */
object NativeChromeSkin : ChromeVisualRenderer {
    override val id: String = "native/chrome"

    @Composable
    override fun Render(context: ChromeVisualContext) {
        when (context.type) {
            ChromeType.ROOT -> context.Body(rootModifier())
            ChromeType.NOTICE -> Notice(context.message.orEmpty(), context.modifier)
        }
    }

    @Composable
    fun Root(content: @Composable BoxScope.() -> Unit) {
        Box(rootModifier(), content = content)
    }

    @Composable
    fun Notice(message: String, modifier: Modifier = Modifier) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 6.dp
        ) {
            Text(
                message,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
        }
    }

    @Composable
    private fun rootModifier() = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding()
}
