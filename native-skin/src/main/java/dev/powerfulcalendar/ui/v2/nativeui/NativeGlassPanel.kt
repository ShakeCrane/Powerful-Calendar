package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Native month panel renderer. Host supplies active skin tokens and platform effect state. */
@Composable
fun NativeGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    cornerRadiusDp: Float,
    blur: Boolean,
    glassFill: Color,
    surfaceColor: Color,
    outlineVariant: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadiusDp.dp),
        color = if (blur) glassFill else surfaceColor,
        border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = if (blur) 2.dp else 0.dp
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}
