package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.nativeui.NativeProtectedAiSettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeProtectedAiSettingsSectionComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun nativeAiSettingsForwardsEditsAndExplicitSaveActionsWhileKeepingKeyInHostSlot() {
        var display by mutableStateOf(
            NativeProtectedAiSettingsDisplay(
                configured = false,
                model = "model-a",
                endpoint = "https://api.example.com/v1",
                enabled = false,
                shareContext = false,
                hasUnsavedChanges = false,
                hasFileAutomationConsent = false
            )
        )
        var apiKey by mutableStateOf("")
        var savedEndpoint: String? = null
        var saveAndEnableCalls = 0
        val cardShape: Shape = RoundedCornerShape(28.dp)

        ui.setContent {
            PowerCalTheme {
                NativeProtectedAiSettingsSection(
                    display = display,
                    actions = NativeProtectedAiSettingsActions(
                        onEndpointChange = {
                            display = display.copy(endpoint = it, hasUnsavedChanges = true)
                        },
                        onModelChange = {
                            display = display.copy(model = it, hasUnsavedChanges = true)
                        },
                        onEnabledChange = {
                            display = display.copy(enabled = it, hasUnsavedChanges = true)
                        },
                        onShareContextChange = {
                            display = display.copy(shareContext = it, hasUnsavedChanges = true)
                        },
                        onSave = {
                            savedEndpoint = display.endpoint
                            display = display.copy(hasUnsavedChanges = false)
                        },
                        onSaveAndEnable = { saveAndEnableCalls++ },
                        onRevokeFileAutomationConsent = {}
                    ),
                    protectedApiKeyField = {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    cardShape = cardShape
                )
            }
        }

        ui.onNodeWithText("单一 AI").assertIsDisplayed()
        ui.onNodeWithText("API Key").assertDoesNotExist()
        ui.onNodeWithText("保存").assertDoesNotExist()
        ui.onNodeWithText("配置端点、模型与密钥").performClick()
        ui.onNodeWithText("API Key").assertIsDisplayed()
        ui.onNodeWithText("保存").assertIsNotEnabled()

        ui.onAllNodes(hasSetTextAction())[0].performTextReplacement("https://custom.example/v1")
        ui.waitForIdle()
        assertEquals("https://custom.example/v1", display.endpoint)
        ui.onNodeWithText("保存").assertIsEnabled()
        ui.onNodeWithText("保存").performClick()
        assertEquals("https://custom.example/v1", savedEndpoint)

        ui.onNodeWithText("保存并启用").performClick()
        assertEquals(1, saveAndEnableCalls)
    }

    @Test
    fun aiSettingsDisplayContractDoesNotContainTheApiKey() {
        val fieldNames = NativeProtectedAiSettingsDisplay::class.java.declaredFields
            .map { it.name.lowercase() }

        assertFalse(fieldNames.any { "key" in it || "secret" in it || "credential" in it })
        assertTrue(fieldNames.contains("endpoint"))
    }
}
