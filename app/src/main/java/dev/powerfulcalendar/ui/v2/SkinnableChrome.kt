package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The root Android Compose viewport + transient notice are presentation surfaces.
 * Root content is still composed under BoxScope, preserving the host's existing .align()
 * semantics in the native fallback; skins can wrap it with their own theme, backgrounds,
 * window insets, and motion. The host retains ownership of notice lifecycle.
 */
internal enum class ChromeType { ROOT, NOTICE }

internal class ChromeVisualContext internal constructor(
    val type: ChromeType,
    val message: String?,
    val modifier: Modifier,
    private val body: (@Composable BoxScope.() -> Unit)?,
    private val native: @Composable () -> Unit
) {
    @Composable fun Native() = native()

    /** Place the host children into a skin-defined Compose box. Only valid for ROOT. */
    @Composable fun Body(modifier: Modifier) {
        check(type == ChromeType.ROOT)
        Box(modifier, content = requireNotNull(body))
    }
}

internal interface ChromeVisualRenderer {
    val id: String
    @Composable fun Render(context: ChromeVisualContext)
}

@Composable
internal fun SkinAppShell(
    forceNative: Boolean = false,
    onOpenNativeSettings: () -> Unit = {},
    onWebSkinFailure: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val web = PowerCalSkinRegistry.active.web
    val native: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            content = content
        )
    }
    val renderer = PresentationSkinRegistry.chrome
    Box(Modifier.fillMaxSize()) {
    if (forceNative) native()
    else if (web != null) {
        WebSkinRoot(web, Modifier.fillMaxSize(), content,
            onOpenNativeSettings = onOpenNativeSettings,
            onSkinFailure = onWebSkinFailure)
    } else if (renderer == null && !RuntimeSkinSurfaces.enabled("chrome")) native()
    else {
        val context = ChromeVisualContext(
        type = ChromeType.ROOT, message = null, modifier = Modifier,
        body = content, native = native
        )
        if (RuntimeSkinSurfaces.enabled("chrome")) RuntimeSkinSurfaces.chrome(context)
        else renderer!!.Render(context)
    }
    RuntimeSkinPickerHost()
    }
}

@Composable
internal fun SkinNotice(message: String, modifier: Modifier = Modifier) {
    val native: @Composable () -> Unit = {
        Surface(
            modifier = modifier,
            shape = GlassShapes.chip,
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
    val renderer = PresentationSkinRegistry.chrome
    if (renderer == null && !RuntimeSkinSurfaces.enabled("chrome")) native()
    else {
        val context = ChromeVisualContext(
        type = ChromeType.NOTICE, message = message,
        modifier = modifier, body = null, native = native
        )
        if (RuntimeSkinSurfaces.enabled("chrome")) RuntimeSkinSurfaces.chrome(context)
        else renderer!!.Render(context)
    }
}
