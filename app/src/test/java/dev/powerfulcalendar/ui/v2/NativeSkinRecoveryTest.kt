package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A skin must never obscure its Settings recovery page, even when fullCanvas=true.
 * Does not construct a WebView, so failure is independent of third-party script behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeSkinRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @After fun reset() { PowerCalSkinRegistry.reset() }

    @Test fun nativeSettingsAlwaysRenderAboveAnInstalledFullCanvasWebSkin() {
        PowerCalSkinRegistry.activate(PowerCalSkinPack(
            key = "faulty-web-skin",
            web = WebSkinBundle(
                entrypoint = "web/index.html",
                files = emptyMap(),
                capabilities = emptySet(),
                fullCanvas = true
            )
        ))
        compose.setContent {
            PowerCalTheme {
                SkinAppShell(forceNative = true) {
                    Text("Native Settings Recovery")
                }
            }
        }
        compose.onNodeWithText("Native Settings Recovery").assertIsDisplayed()
        compose.runOnIdle { assertEquals("faulty-web-skin", PowerCalSkinRegistry.active.key) }
    }
}
