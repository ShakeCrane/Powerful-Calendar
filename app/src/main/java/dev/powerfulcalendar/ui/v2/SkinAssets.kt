package dev.powerfulcalendar.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.res.painterResource

/**
 * PowerCal visual skin API: resource-backed artwork only.
 *
 * Claude can supply packaged PNG/WebP/XML vector resources, then register their R.drawable IDs
 * through a SkinPack. A missing slot ALWAYS falls back to the current Compose presentation.
 * This is not a downloaded skin loader; packaged resources require a new APK build.
 *
 * No domain, database, AI or alarm code may depend on this file.
 */
enum class SkinSlot {
    TIMELINE_BACKGROUND,
    DECK_BACKDROP,
    MONTH_BACKGROUND,
    COMPLETION_EMBLEM,
    BRAND_MARK
}

@Immutable
data class SkinArt(
    @field:DrawableRes val drawableId: Int,
    val alpha: Float = 1f
) {
    init {
        require(drawableId > 0) { "Skin artwork must reference a valid compiled drawable ID" }
        require(alpha in 0f..1f) { "Skin artwork alpha must be within [0,1]" }
    }
}

@Immutable
data class SkinArtwork(
    val timelineBackground: SkinArt? = null,
    val deckBackdrop: SkinArt? = null,
    val monthBackground: SkinArt? = null,
    val completionEmblem: SkinArt? = null,
    val brandMark: SkinArt? = null
) {
    fun find(slot: SkinSlot): SkinArt? = when (slot) {
        SkinSlot.TIMELINE_BACKGROUND -> timelineBackground
        SkinSlot.DECK_BACKDROP -> deckBackdrop
        SkinSlot.MONTH_BACKGROUND -> monthBackground
        SkinSlot.COMPLETION_EMBLEM -> completionEmblem
        SkinSlot.BRAND_MARK -> brandMark
    }
}

/**
 * A skin pack owns only presentation. Light/dark slots can differ independently and fall back
 * to default Material/axis colors. Keep semantic event colors distinguishable in both themes.
 */
@Immutable
data class PowerCalSkinPack(
    val key: String = "powercal-default",
    val lightArtwork: SkinArtwork = SkinArtwork(),
    val darkArtwork: SkinArtwork = SkinArtwork(),
    val lightColors: ColorScheme? = null,
    val darkColors: ColorScheme? = null,
    val lightAxis: AxisPalette? = null,
    val darkAxis: AxisPalette? = null,
    val typography: Typography? = null
) {
    init { require(key.isNotBlank()) { "Skin pack key must not be empty" } }

    fun artworkFor(darkTheme: Boolean): SkinArtwork = if (darkTheme) darkArtwork else lightArtwork
}

/**
 * Simple in-process selection point. The default pack is visually identical to PowerCal Glass.
 * Call activate() from a future settings UI, or set a packaged pack in MainActivity.
 * There is intentionally no filesystem/network loader or permission to run skin-pack code.
 */
object PowerCalSkinRegistry {
    val default: PowerCalSkinPack = PowerCalSkinPack()
    var active by mutableStateOf(default)
        private set

    fun activate(pack: PowerCalSkinPack) { active = pack }
    fun reset() { active = default }
}

val LocalSkinArtwork = staticCompositionLocalOf { SkinArtwork() }

@Composable
fun skinArt(slot: SkinSlot): SkinArt? = LocalSkinArtwork.current.find(slot)

@Composable
fun skinPainter(slot: SkinSlot): Painter? = skinArt(slot)?.let { painterResource(it.drawableId) }

/** Transparent overlay for a named wallpaper/deck backdrop; no-op for an empty slot. */
@Composable
fun Modifier.skinBackdrop(slot: SkinSlot): Modifier {
    // Background decoration is optional on low-RAM and reduced-effects devices.
    if (!LocalGlassEffects.current.blur) return this
    val resource = skinArt(slot) ?: return this
    return paint(
        painter = painterResource(resource.drawableId),
        sizeToIntrinsics = false, // Never let artwork dimensions change the layout geometry.
        contentScale = ContentScale.Crop,
        alpha = resource.alpha
    )
}

/** Use for replaceable, purely decorative noninteractive illustrations. */
@Composable
fun SkinImage(slot: SkinSlot, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val resource = skinArt(slot) ?: return
    Image(
        painter = painterResource(resource.drawableId),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        alpha = resource.alpha
    )
}
