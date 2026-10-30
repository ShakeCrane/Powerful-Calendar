package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.nativeui.NativeProtectedProfileSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeProtectedProfileSectionComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun profilePresentationForwardsEditsAndOnlySavesAfterExplicitAction() {
        var displayName by mutableStateOf("")
        var savedName: String? = null
        ui.setContent {
            PowerCalTheme {
                NativeProtectedProfileSection(
                    display = NativeProtectedProfileDisplay(displayName),
                    actions = NativeProtectedProfileActions(
                        onDisplayNameChange = { displayName = it },
                        onSave = { savedName = displayName }
                    ),
                    cardShape = RoundedCornerShape(28.dp)
                )
            }
        }

        ui.onNodeWithText("个人信息").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).performTextInput("小明")
        ui.waitForIdle()

        assertEquals("小明", displayName)
        assertNull(savedName)
        ui.onNodeWithText("保存个人信息").performClick()
        assertEquals("小明", savedName)
    }

    @Test
    fun profileInputRetainsTheExistingEightyCharacterLimit() {
        val accepted = mutableListOf<String>()
        ui.setContent {
            PowerCalTheme {
                NativeProtectedProfileSection(
                    display = NativeProtectedProfileDisplay(""),
                    actions = NativeProtectedProfileActions(
                        onDisplayNameChange = accepted::add,
                        onSave = {}
                    ),
                    cardShape = RoundedCornerShape(28.dp)
                )
            }
        }

        ui.onNode(hasSetTextAction()).performTextInput("x".repeat(81))
        assertEquals(emptyList<String>(), accepted)
    }
}
