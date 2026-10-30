package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Root-shell and transient-notice surfaces replaceable by an audited source skin. */
enum class ChromeType { ROOT, NOTICE }

class ChromeVisualContext(
    val type: ChromeType,
    val message: String?,
    val modifier: Modifier,
    private val body: (@Composable BoxScope.() -> Unit)?,
    private val native: @Composable () -> Unit
) {
    /** First-party fallback supplied by the Host. */
    @Composable fun Native() = native()

    /** Place the Host children into a skin-defined Compose box. Only valid for ROOT. */
    @Composable fun Body(modifier: Modifier) {
        check(type == ChromeType.ROOT)
        Box(modifier, content = requireNotNull(body))
    }
}

interface ChromeVisualRenderer {
    val id: String
    @Composable fun Render(context: ChromeVisualContext)
}
