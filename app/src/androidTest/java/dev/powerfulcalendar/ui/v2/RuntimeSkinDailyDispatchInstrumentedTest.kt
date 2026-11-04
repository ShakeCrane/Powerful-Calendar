package dev.powerfulcalendar.ui.v2

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.PowerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RuntimeSkinDailyDispatchInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun parsedV2ScreenCannotReplaceNativeHostSurfaceOnDevice() {
        val context = compose.activity.applicationContext
        val databaseName = "v2-dispatch-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            RuntimeSkinSurfaces.resetV2CompatibilityForTests()
            ActiveVisualSkin.install()
            PowerCalSkinRegistry.activate(
                PowerCalSkinPack(
                    key = "legacy-v2-dispatch-fixture",
                    runtime = RuntimeSkinDocument(
                        screens = mapOf(
                            "timeline" to RuntimeSkinNode(
                                type = "column",
                                children = listOf(RuntimeSkinNode(type = "text", text = "LEGACY V2 DAILY ROUTE"))
                            )
                        ),
                        transition = "none",
                        durationMs = 0
                    )
                )
            )
            compose.activity.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天").assertExists()
            compose.onNodeWithText("LEGACY V2 DAILY ROUTE").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals("legacy-v2-dispatch-fixture", PowerCalSkinRegistry.active.key)
                assertTrue(store.all().isEmpty())
                assertEquals(0L, store.commitCount())
                assertEquals(0L, store.draftCount())
            }
        } finally {
            compose.activity.runOnUiThread {
                compose.activity.setContent { }
                RuntimeSkinSurfaces.resetV2CompatibilityForTests()
                PowerCalSkinRegistry.reset()
                ActiveVisualSkin.install()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
