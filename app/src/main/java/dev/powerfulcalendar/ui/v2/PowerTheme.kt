package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.powerfulcalendar.core.TemporalKind

/**
 * PowerCal Glass.
 *
 * Frosted translucency is built from layered translucent fills, a hairline highlight and a soft
 * shadow. Compose cannot sample the backdrop on all supported API levels (real backdrop blur is
 * only reachable through RenderEffect on 31+ and is not a portable primitive), so the glass is
 * *emulated* with layered alpha. It is deliberately restrained: content contrast wins over
 * decoration, and every surface remains readable without the effect.
 */
@Immutable
data class GlassEffects(
    /** Turned off automatically on low-RAM devices and whenever the user reduces motion. */
    val blur: Boolean = true,
    val animationScale: Float = 1f,
    val reduceMotion: Boolean = false
) {
    val motionScale: Float get() = if (reduceMotion) 0f else animationScale
}

val LocalGlassEffects = staticCompositionLocalOf { GlassEffects() }

/** Kind colours are semantic, not decorative: left/right identity must survive a theme switch. */
@Immutable
data class AxisPalette(
    val alarm: Color,
    val activity: Color,
    val deadline: Color,
    val nowLine: Color,
    val gridLine: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color
)

val LocalAxisPalette = staticCompositionLocalOf {
    AxisPalette(
        alarm = Color(0xFF5B7CFA),
        activity = Color(0xFF2E9E83),
        deadline = Color(0xFFC2622C),
        nowLine = Color(0xFF2F6BFF),
        gridLine = Color(0x1A000000),
        glassFill = Color(0xCCFFFFFF),
        glassBorder = Color(0x33000000),
        glassHighlight = Color(0x66FFFFFF)
    )
}

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

private val PowerTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp)
    )
}

/** One theme entry point so runtime light/dark switching never rebuilds the whole app shell. */
@Composable
fun PowerCalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    effects: GlassEffects = GlassEffects(),
    skin: PowerCalSkinPack = PowerCalSkinRegistry.active,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColors else LightColors
    val scheme = (if (darkTheme) skin.darkColors else skin.lightColors)
        ?: skin.visual?.scheme(baseScheme, darkTheme) ?: baseScheme
    val basePalette = if (darkTheme) {
        AxisPalette(
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
        AxisPalette(
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
    val palette = (if (darkTheme) skin.darkAxis else skin.lightAxis)
        ?: skin.visual?.axis(basePalette, darkTheme) ?: basePalette
    val tokens = skin.visual ?: SkinVisualTokens.default
    val baseTypography = skin.typography ?: PowerTypography
    val scaledTypography = if (tokens.fontScale == 1f) baseTypography else baseTypography.copy(
        bodySmall = baseTypography.bodySmall.copy(fontSize = baseTypography.bodySmall.fontSize * tokens.fontScale),
        bodyMedium = baseTypography.bodyMedium.copy(fontSize = baseTypography.bodyMedium.fontSize * tokens.fontScale),
        bodyLarge = baseTypography.bodyLarge.copy(fontSize = baseTypography.bodyLarge.fontSize * tokens.fontScale),
        titleMedium = baseTypography.titleMedium.copy(fontSize = baseTypography.titleMedium.fontSize * tokens.fontScale),
        titleLarge = baseTypography.titleLarge.copy(fontSize = baseTypography.titleLarge.fontSize * tokens.fontScale),
        labelSmall = baseTypography.labelSmall.copy(fontSize = baseTypography.labelSmall.fontSize * tokens.fontScale),
        labelMedium = baseTypography.labelMedium.copy(fontSize = baseTypography.labelMedium.fontSize * tokens.fontScale)
    )
    CompositionLocalProvider(
        LocalGlassEffects provides effects,
        LocalSkinArtwork provides skin.artworkFor(darkTheme),
        LocalSkinBitmaps provides skin.bitmapFor(darkTheme),
        LocalSkinVisual provides tokens,
        LocalAxisPalette provides if (effects.blur) palette else palette.copy(glassFill = scheme.surface, glassHighlight = Color.Transparent)
    ) {
        MaterialTheme(colorScheme = scheme, typography = scaledTypography) {
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

@Composable
fun kindColor(kind: TemporalKind): Color = when (kind) {
    TemporalKind.ALARM -> LocalAxisPalette.current.alarm
    TemporalKind.ACTIVITY -> LocalAxisPalette.current.activity
    TemporalKind.DEADLINE -> LocalAxisPalette.current.deadline
    TemporalKind.MEMO -> LocalAxisPalette.current.activity
}

/** Shared corner language: generous, but never so round that dense rows look like pills. */
@Composable
fun skinCardShape() = RoundedCornerShape(LocalSkinVisual.current.cardRadiusDp.dp)

@Composable
fun skinBarShape() = RoundedCornerShape(LocalSkinVisual.current.barRadiusDp.dp)

object GlassShapes {
    val card = RoundedCornerShape(28.dp)
    val cardCompact = RoundedCornerShape(24.dp)
    val chip = RoundedCornerShape(14.dp)
    val bar = RoundedCornerShape(20.dp)
}

/** A very soft vertical wash so the glass has something to sit on without hiding the ruler. */
@Composable
fun axisBackground(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        0f to scheme.background,
        0.42f to scheme.surfaceContainerLow,
        0.58f to scheme.surfaceContainerLow,
        1f to scheme.background
    )
}

object PowerMotion {
    const val PressScale = 0.972f
    const val CardLift = 1.012f
}

@Composable
internal fun glassMotion(): FiniteAnimationSpec<Float> =
    if (LocalGlassEffects.current.reduceMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 380f)

@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) {
    val effects = LocalGlassEffects.current
    Surface(
        modifier = modifier,
        shape = skinCardShape(),
        color = if (effects.blur) LocalAxisPalette.current.glassFill else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = if (effects.blur) 2.dp else 0.dp
    ) { Column(Modifier.padding(contentPadding), content = content) }
}

/** Body text minimum used in cards; kept here so accessibility review has one number to check. */
val BodyLineHeight = TextStyle(lineHeight = 22.sp)
