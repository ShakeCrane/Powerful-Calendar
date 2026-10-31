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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.ui.v2.nativeui.NativeSkinAxisPalette
import dev.powerfulcalendar.ui.v2.nativeui.NativeSkinThemeDefaults

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
    NativeSkinThemeDefaults.unboundAxisPalette.toAxisPalette()
}

private fun NativeSkinAxisPalette.toAxisPalette() = AxisPalette(
    alarm = alarm,
    activity = activity,
    deadline = deadline,
    nowLine = nowLine,
    gridLine = gridLine,
    glassFill = glassFill,
    glassBorder = glassBorder,
    glassHighlight = glassHighlight
)

/** One theme entry point so runtime light/dark switching never rebuilds the whole app shell. */
@Composable
fun PowerCalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    effects: GlassEffects = GlassEffects(),
    skin: PowerCalSkinPack = PowerCalSkinRegistry.active,
    content: @Composable () -> Unit
) {
    val baseScheme = NativeSkinThemeDefaults.scheme(darkTheme)
    val scheme = (if (darkTheme) skin.darkColors else skin.lightColors)
        ?: skin.visual?.scheme(baseScheme, darkTheme) ?: baseScheme
    val basePalette = NativeSkinThemeDefaults.axisPalette(darkTheme).toAxisPalette()
    val palette = (if (darkTheme) skin.darkAxis else skin.lightAxis)
        ?: skin.visual?.axis(basePalette, darkTheme) ?: basePalette
    val tokens = skin.visual ?: SkinVisualTokens.default
    val baseTypography = skin.typography ?: NativeSkinThemeDefaults.typography
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

/** A uniform timeline backdrop; decorative vertical washes can read as false horizontal rules. */
@Composable
fun axisBackground(): Brush {
    val scheme = MaterialTheme.colorScheme
    return SolidColor(scheme.background)
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
