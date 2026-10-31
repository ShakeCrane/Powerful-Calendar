package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** First-party Native design defaults. Host may layer an installed skin over this baseline. */
object NativeSkinThemeDefaults {
    val unboundAxisPalette = NativeSkinAxisPalette(
        alarm = Color(0xFF5B7CFA),
        activity = Color(0xFF2E9E83),
        deadline = Color(0xFFC2622C),
        nowLine = Color(0xFF2F6BFF),
        gridLine = Color(0x1A000000),
        glassFill = Color(0xCCFFFFFF),
        glassBorder = Color(0x33000000),
        glassHighlight = Color(0x66FFFFFF)
    )

    val lightScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF2457D6),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE5FF),
        onPrimaryContainer = Color(0xFF0B2A66),
        secondary = Color(0xFF2E7D6B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3EFE7),
        onSecondaryContainer = Color(0xFF0B3A30),
        tertiary = Color(0xFF9A4A1F),
        tertiaryContainer = Color(0xFFFFE4CE),
        onTertiaryContainer = Color(0xFF5E2D0D),
        background = Color(0xFFF3F5FA),
        onBackground = Color(0xFF11151C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF11151C),
        surfaceVariant = Color(0xFFE4E8F1),
        onSurfaceVariant = Color(0xFF4A5261),
        surfaceContainerLow = Color(0xFFF6F8FC),
        surfaceContainer = Color(0xFFEFF2F8),
        surfaceContainerHigh = Color(0xFFE7EBF4),
        surfaceContainerHighest = Color(0xFFDFE4EF),
        outline = Color(0xFF6B7485),
        outlineVariant = Color(0xFFC3CAD8),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF)
    )

    val darkScheme: ColorScheme = darkColorScheme(
        primary = Color(0xFF9DB8FF),
        onPrimary = Color(0xFF0B2A66),
        primaryContainer = Color(0xFF1B3A7A),
        onPrimaryContainer = Color(0xFFDCE5FF),
        secondary = Color(0xFF7FD6C2),
        onSecondary = Color(0xFF07281F),
        secondaryContainer = Color(0xFF173B33),
        onSecondaryContainer = Color(0xFFD3EFE7),
        tertiary = Color(0xFFFFB68C),
        tertiaryContainer = Color(0xFF4A2A18),
        onTertiaryContainer = Color(0xFFFFDCC1),
        background = Color(0xFF0A0D13),
        onBackground = Color(0xFFE6EAF2),
        surface = Color(0xFF12161E),
        onSurface = Color(0xFFE6EAF2),
        surfaceVariant = Color(0xFF2A303B),
        onSurfaceVariant = Color(0xFFB4BCCB),
        surfaceContainerLow = Color(0xFF141922),
        surfaceContainer = Color(0xFF171D27),
        surfaceContainerHigh = Color(0xFF1C2330),
        surfaceContainerHighest = Color(0xFF222A38),
        outline = Color(0xFF8A93A3),
        outlineVariant = Color(0xFF39414F),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005)
    )

    val typography: Typography = Typography().let { base ->
        base.copy(
            displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp)
        )
    }

    fun scheme(dark: Boolean): ColorScheme = if (dark) darkScheme else lightScheme

    fun axisPalette(dark: Boolean): NativeSkinAxisPalette = if (dark) {
        NativeSkinAxisPalette(
            alarm = Color(0xFF9DB8FF),
            activity = Color(0xFF7FD6C2),
            deadline = Color(0xFFFFB68C),
            nowLine = Color(0xFF9DB8FF),
            gridLine = Color(0x22FFFFFF),
            glassFill = Color(0xB31A2130),
            glassBorder = Color(0x33FFFFFF),
            glassHighlight = Color(0x1AFFFFFF)
        )
    } else {
        NativeSkinAxisPalette(
            alarm = Color(0xFF3D5FD8),
            activity = Color(0xFF177A63),
            deadline = Color(0xFFA8501C),
            nowLine = Color(0xFF2457D6),
            gridLine = Color(0x1A0B1220),
            glassFill = Color(0xCCFFFFFF),
            glassBorder = Color(0x2E0B1220),
            glassHighlight = Color(0x80FFFFFF)
        )
    }
}

@Immutable
data class NativeSkinAxisPalette(
    val alarm: Color,
    val activity: Color,
    val deadline: Color,
    val nowLine: Color,
    val gridLine: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color
)
