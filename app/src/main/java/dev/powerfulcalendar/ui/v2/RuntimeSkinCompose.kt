package dev.powerfulcalendar.ui.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A fixed set of host actions/widgets. The JSON document can never create new Kotlin actions. */
internal class RuntimeSkinBindings(
    val native: @Composable () -> Unit,
    val actions: Map<String, () -> Unit> = emptyMap(),
    val values: Map<String, String> = emptyMap(),
    val widget: @Composable (String) -> Unit = {}
)

internal object RuntimeSkinCompose {
    @Composable fun scene(
        screen: String,
        bindings: RuntimeSkinBindings,
        modifier: Modifier = Modifier,
        manageRecovery: Boolean = true
    ) {
        val node = PowerCalSkinRegistry.active.runtime?.screens?.get(screen)
        if (node == null) bindings.native()
        else if (!manageRecovery) {
            node(node, bindings, modifier)
        } else {
            // An always-accessible recovery route even when imported UI lacks a Native node.
            var recovery by rememberSaveable(screen, PowerCalSkinRegistry.active.key) {
                mutableStateOf(false)
            }
            if (recovery) {
                Box(modifier.fillMaxSize()) {
                    bindings.native()
                    TextButton(
                        onClick = { recovery = false },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) { Text("返回皮肤") }
                }
            } else {
                val mapped = RuntimeSkinBindings(
                    native = bindings.native,
                    actions = bindings.actions + ("native" to { recovery = true }),
                    values = bindings.values,
                    widget = bindings.widget
                )
                if (screen == "settings") {
                    Box(modifier.fillMaxSize()) {
                        node(node, mapped, Modifier.fillMaxSize())
                        TextButton(
                            onClick = { recovery = true },
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) { Text("Native settings / restore skin") }
                    }
                } else node(node, mapped, modifier)
            }
        }
    }

    @Composable
    private fun node(n: RuntimeSkinNode, bindings: RuntimeSkinBindings, modifier: Modifier) {
        val effects = LocalGlassEffects.current
        if (effects.reduceMotion || n.motion == "none" || n.durationMs == 0) {
            draw(n, bindings, modifier)
            return
        }
        var appeared by remember(n) { mutableStateOf(false) }
        LaunchedEffect(n) { appeared = true }
        val enter = when(n.motion) {
            "fade" -> fadeIn(tween(n.durationMs))
            "slide-up" -> fadeIn(tween(n.durationMs)) +
                slideInVertically(tween(n.durationMs), initialOffsetY = { it / 6 })
            "slide-left" -> fadeIn(tween(n.durationMs)) +
                slideInHorizontally(tween(n.durationMs), initialOffsetX = { it / 5 })
            "scale" -> fadeIn(tween(n.durationMs)) + scaleIn(tween(n.durationMs), initialScale = .94f)
            else -> EnterTransition.None
        }
        AnimatedVisibility(appeared, enter = enter, exit = ExitTransition.None) {
            draw(n, bindings, modifier)
        }
    }

    @Composable
    private fun color(name: String): Color = when (name) {
        "primary" -> MaterialTheme.colorScheme.primary
        "primaryContainer" -> MaterialTheme.colorScheme.primaryContainer
        "surface" -> MaterialTheme.colorScheme.surface
        "surfaceContainer" -> MaterialTheme.colorScheme.surfaceContainer
        "surfaceContainerHigh" -> MaterialTheme.colorScheme.surfaceContainerHigh
        "surfaceContainerLow" -> MaterialTheme.colorScheme.surfaceContainerLow
        "background" -> MaterialTheme.colorScheme.background
        else -> Color.Transparent
    }

    @Composable
    private fun draw(n: RuntimeSkinNode, bindings: RuntimeSkinBindings, modifier: Modifier) {
        val padded = modifier.padding(n.padding.dp)
        when (n.type) {
            "column" -> Column(padded, verticalArrangement = Arrangement.spacedBy(n.gap.dp)) {
                n.children.forEach { child ->
                    node(child, bindings, if (child.grow) Modifier.weight(1f) else Modifier.fillMaxWidth())
                }
            }
            "row" -> Row(padded, horizontalArrangement = Arrangement.spacedBy(n.gap.dp),
                verticalAlignment = Alignment.CenterVertically) {
                n.children.forEach { child ->
                    node(child, bindings, if (child.grow) Modifier.weight(1f) else Modifier)
                }
            }
            "box" -> Box(padded) {
                n.children.forEach { child -> node(child, bindings, Modifier.fillMaxSize()) }
            }
            "card" -> Surface(
                modifier = padded.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                color = if (n.color.isNotEmpty()) color(n.color)
                else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(n.gap.dp)) {
                    n.children.forEach { child -> node(child, bindings, Modifier.fillMaxWidth()) }
                }
            }
            "scroll" -> Column(
                padded.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(n.gap.dp)
            ) {
                n.children.forEach { child -> node(child, bindings, Modifier.fillMaxWidth()) }
            }
            "text" -> Text(
                n.text.replaceTokens(bindings.values),
                modifier = padded, style = MaterialTheme.typography.titleMedium,
                maxLines = 6, overflow = TextOverflow.Ellipsis
            )
            "button" -> {
                val onClick = bindings.actions[n.action] ?: return
                FilledTonalButton(
                    modifier = padded, onClick = onClick,
                    shape = RoundedCornerShape(14.dp)
                ) { Text(n.text.replaceTokens(bindings.values), maxLines = 2) }
            }
            "spacer" -> Spacer(padded.height((n.gap + 4).dp))
            "native" -> bindings.native()
            else -> bindings.widget(n.type)
        }
    }

    /** Strict string substitution, not an expression or scripting engine. */
    private fun String.replaceTokens(values: Map<String, String>): String =
        values.entries.fold(this) { result, (key, value) ->
            result.replace("{$key}", value.take(80))
        }
}

/** Real page-to-page transition at the host route boundary (not only entering-page decoration). */
@Composable
internal fun SkinPageTransition(
    page: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit
) {
    val spec = PowerCalSkinRegistry.active.runtime
    if (spec == null || spec.transition == "none" ||
        spec.durationMs == 0 || LocalGlassEffects.current.reduceMotion) {
        Box(modifier) { content(page) }
        return
    }
    val duration = spec.durationMs
    AnimatedContent(
        targetState = page, modifier = modifier,
        transitionSpec = {
            val enter = when(spec.transition) {
                "slide-left" -> slideInHorizontally(tween(duration)) { it / 4 } +
                    fadeIn(tween(duration))
                "slide-up" -> slideInVertically(tween(duration)) { it / 5 } +
                    fadeIn(tween(duration))
                "scale" -> scaleIn(tween(duration), initialScale = .96f) + fadeIn(tween(duration))
                else -> fadeIn(tween(duration))
            }
            val exit = when(spec.transition) {
                "slide-left" -> slideOutHorizontally(tween(duration)) { -it / 4 } +
                    fadeOut(tween(duration))
                "scale" -> scaleOut(tween(duration), targetScale = 1.03f) + fadeOut(tween(duration))
                else -> fadeOut(tween(duration))
            }
            enter togetherWith exit
        },
        label = "PowerCal v2 skin page transition"
    ) { displayedPage ->
        content(displayedPage)
    }
}
