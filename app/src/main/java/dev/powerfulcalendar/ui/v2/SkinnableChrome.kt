package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.powerfulcalendar.ui.v2.nativeui.NativeChromeSkin

/**
 * The root Android Compose viewport + transient notice are presentation surfaces.
 * Root content is still composed under BoxScope, preserving the host's existing .align()
 * semantics in the native fallback; skins can wrap it with their own theme, backgrounds,
 * window insets, and motion. The host retains ownership of notice lifecycle.
 */
@Composable
internal fun SkinAppShell(
    forceNative: Boolean = false,
    onWebSkinFailure: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val web = PowerCalSkinRegistry.active.web
    val native: @Composable () -> Unit = { NativeChromeSkin.Root(content) }
    val renderer = PresentationSkinRegistry.chrome
    Box(Modifier.fillMaxSize()) {
        if (forceNative) native()
        else if (web != null) {
            WebSkinRoot(web, Modifier.fillMaxSize(), content,
                onSkinFailure = onWebSkinFailure)
        } else if (renderer == null && !RuntimeSkinSurfaces.enabled("chrome")) native()
        else {
            val context = ChromeVisualContext(
                type = ChromeType.ROOT,
                message = null,
                modifier = Modifier,
                body = content,
                native = native
            )
            if (RuntimeSkinSurfaces.enabled("chrome")) RuntimeSkinSurfaces.chrome(context)
            else renderer!!.Render(context)
        }
        RuntimeSkinPickerHost()
    }
}

@Composable
internal fun SkinNotice(message: String, modifier: Modifier = Modifier) {
    val native: @Composable () -> Unit = { NativeChromeSkin.Notice(message, modifier) }
    val renderer = PresentationSkinRegistry.chrome
    if (renderer == null && !RuntimeSkinSurfaces.enabled("chrome")) native()
    else {
        val context = ChromeVisualContext(
            type = ChromeType.NOTICE,
            message = message,
            modifier = modifier,
            body = null,
            native = native
        )
        if (RuntimeSkinSurfaces.enabled("chrome")) RuntimeSkinSurfaces.chrome(context)
        else renderer!!.Render(context)
    }
}
