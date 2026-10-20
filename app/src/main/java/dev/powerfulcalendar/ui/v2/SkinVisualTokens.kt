package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Declarative visual ABI for externally imported skins.
 * No business state, schedule logic, permissions, tool calls, or executable content.
 */
@Immutable
data class SkinVisualTokens(
    val light: Map<String, Color> = emptyMap(),
    val dark: Map<String, Color> = emptyMap(),
    val cardRadiusDp: Float = 28f,
    val barRadiusDp: Float = 20f,
    val pointLineDp: Float = 2f,
    val fontScale: Float = 1f
) {
    init {
        require(cardRadiusDp in 12f..40f)
        require(barRadiusDp in 8f..32f)
        require(pointLineDp in 1f..5f)
        require(fontScale in 0.85f..1.20f)
    }

    fun palette(isDark: Boolean) = if (isDark) dark else light

    fun scheme(base: ColorScheme, isDark: Boolean): ColorScheme {
        val p = palette(isDark)
        fun color(name: String, fallback: Color) = p[name] ?: fallback
        return base.copy(
            primary = color("primary", base.primary),
            onPrimary = color("onPrimary", base.onPrimary),
            primaryContainer = color("primaryContainer", base.primaryContainer),
            onPrimaryContainer = color("onPrimaryContainer", base.onPrimaryContainer),
            secondary = color("secondary", base.secondary),
            onSecondary = color("onSecondary", base.onSecondary),
            tertiary = color("tertiary", base.tertiary),
            onTertiary = color("onTertiary", base.onTertiary),
            background = color("background", base.background),
            onBackground = color("onBackground", base.onBackground),
            surface = color("surface", base.surface),
            onSurface = color("onSurface", base.onSurface),
            surfaceContainerLow = color("surfaceContainerLow", base.surfaceContainerLow),
            surfaceContainer = color("surfaceContainer", base.surfaceContainer),
            surfaceContainerHigh = color("surfaceContainerHigh", base.surfaceContainerHigh),
            outline = color("outline", base.outline)
        )
    }

    fun axis(base: AxisPalette, isDark: Boolean): AxisPalette {
        val p = palette(isDark)
        fun color(name: String, fallback: Color) = p[name] ?: fallback
        return base.copy(
            alarm = color("alarm", base.alarm),
            activity = color("activity", base.activity),
            deadline = color("deadline", base.deadline),
            nowLine = color("nowLine", base.nowLine),
            gridLine = color("gridLine", base.gridLine),
            glassFill = color("glassFill", base.glassFill),
            glassBorder = color("glassBorder", base.glassBorder),
            glassHighlight = color("glassHighlight", base.glassHighlight)
        )
    }

    companion object {
        val colorNames = setOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
            "secondary", "onSecondary", "tertiary", "onTertiary",
            "background", "onBackground", "surface", "onSurface",
            "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh",
            "outline", "alarm", "activity", "deadline", "nowLine", "gridLine",
            "glassFill", "glassBorder", "glassHighlight"
        )
        val default = SkinVisualTokens()
    }
}

val LocalSkinVisual = staticCompositionLocalOf { SkinVisualTokens.default }
