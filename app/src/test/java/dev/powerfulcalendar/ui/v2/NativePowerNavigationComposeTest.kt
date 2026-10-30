package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.powerfulcalendar.ui.v2.nativeui.NativePowerNavigation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativePowerNavigationComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun selectionAndRoutesRemainHostOwnedAcrossModuleBoundary() {
        val selectedTabs = mutableListOf<VisualTab>()
        ui.setContent {
            PowerCalTheme {
                NativePowerNavigation(
                    selected = VisualTab.MONTH,
                    onSelect = selectedTabs::add,
                    settingsIcon = { androidx.compose.material3.Text("Host settings glyph") }
                )
            }
        }

        ui.onNodeWithText("日历").assertIsSelected()
        ui.onNodeWithText("时间轴").assertIsNotSelected()
        ui.onNodeWithText("Host settings glyph", useUnmergedTree = true).assertExists()

        ui.onNodeWithText("AI 助手").performClick()
        ui.onNodeWithText("设置").performClick()

        assertEquals(listOf(VisualTab.AI, VisualTab.TOOLS), selectedTabs)
    }
}
