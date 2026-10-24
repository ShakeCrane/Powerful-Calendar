package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeGlassFallbackInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private data class ThemeSnapshot(
        val blur: Boolean,
        val surface: Color,
        val onSurface: Color,
        val onSurfaceVariant: Color,
        val glassFill: Color,
        val glassHighlight: Color
    )

    private fun contrast(foreground: Color, background: Color): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    @Test
    fun disabledGlassUsesReadableNativeSurfacesAndKeepsDraftEditable() {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-issue-161-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val draft = store.newDraft(
                DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 25), zone)
                    .copy(title = "GLASS_FALLBACK_INITIAL")
            )
            val controller = DeckController(
                StoreDeckPersistence(store), ioDispatcher = Dispatchers.Unconfined
            )
            runBlocking { controller.open(draft.draftId) }

            var darkTheme by mutableStateOf(false)
            var snapshot by mutableStateOf<ThemeSnapshot?>(null)
            compose.setContent {
                PowerCalTheme(darkTheme = darkTheme, effects = GlassEffects(blur = false)) {
                    val scheme = MaterialTheme.colorScheme
                    val axis = LocalAxisPalette.current
                    val effects = LocalGlassEffects.current
                    SideEffect {
                        snapshot = ThemeSnapshot(
                            blur = effects.blur,
                            surface = scheme.surface,
                            onSurface = scheme.onSurface,
                            onSurfaceVariant = scheme.onSurfaceVariant,
                            glassFill = axis.glassFill,
                            glassHighlight = axis.glassHighlight
                        )
                    }
                    Column {
                        GlassPanel {
                            Text("GLASS_FALLBACK_NATIVE_CONTENT")
                        }
                        NativeDraftDeckDialog(
                            controller = controller,
                            aiSession = null,
                            onDismissRequest = {},
                            onOpenFormalEvent = {},
                            onCancelAiBatch = {},
                            onDeckChanged = {},
                            formalSearch = { emptyList() },
                            initialEditorOpen = true
                        )
                    }
                }
            }
            compose.waitForIdle()

            val titleField = compose.onNode(hasSetTextAction() and hasText("标题"))
            titleField.performClick()
            titleField.assertIsFocused()
            titleField.performTextReplacement("GLASS_FALLBACK_EDITED")
            compose.waitForIdle()

            val originalId = controller.state.currentId
            assertEquals(draft.draftId, originalId)
            assertEquals("GLASS_FALLBACK_EDITED", controller.state.form?.title)
            compose.onNodeWithText("GLASS_FALLBACK_NATIVE_CONTENT").assertIsDisplayed()
            val light = compose.runOnIdle { snapshot!! }
            assertFalse(light.blur)
            assertEquals(light.surface, light.glassFill)
            assertEquals(Color.Transparent, light.glassHighlight)
            assertTrue("light onSurface contrast=${contrast(light.onSurface, light.surface)}",
                contrast(light.onSurface, light.surface) >= 4.5f)
            assertTrue("light onSurfaceVariant contrast=${contrast(light.onSurfaceVariant, light.surface)}",
                contrast(light.onSurfaceVariant, light.surface) >= 4.5f)

            compose.runOnIdle { darkTheme = true }
            compose.waitForIdle()

            val dark = compose.runOnIdle { snapshot!! }
            assertFalse(dark.blur)
            assertEquals(dark.surface, dark.glassFill)
            assertEquals(Color.Transparent, dark.glassHighlight)
            assertTrue("dark onSurface contrast=${contrast(dark.onSurface, dark.surface)}",
                contrast(dark.onSurface, dark.surface) >= 4.5f)
            assertTrue("dark onSurfaceVariant contrast=${contrast(dark.onSurfaceVariant, dark.surface)}",
                contrast(dark.onSurfaceVariant, dark.surface) >= 4.5f)
            assertEquals(originalId, controller.state.currentId)
            assertEquals("GLASS_FALLBACK_EDITED", controller.state.form?.title)
            compose.onNodeWithText("GLASS_FALLBACK_EDITED").assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("GLASS_FALLBACK_EDITED")).assertIsFocused()

            println(
                "NATIVE_GLASS_FALLBACK draftId=${controller.state.currentId} " +
                    "title=${controller.state.form?.title} " +
                    "light=${contrast(light.onSurface, light.surface)}/${contrast(light.onSurfaceVariant, light.surface)} " +
                    "dark=${contrast(dark.onSurface, dark.surface)}/${contrast(dark.onSurfaceVariant, dark.surface)} " +
                    "glassFill=surface highlight=transparent focused=true"
            )
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
