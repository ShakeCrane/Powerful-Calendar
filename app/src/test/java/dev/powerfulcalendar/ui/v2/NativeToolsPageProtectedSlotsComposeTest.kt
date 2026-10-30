package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeToolsPageProtectedSlotsComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun nativeSettingsRendersHostOwnedProtectedProfileAndCredentialSlots() {
        ui.setContent {
            PowerCalTheme {
                ToolsPage(
                    diagnostics = UiDiagnostics(),
                    exactAlarmAllowed = false,
                    notificationsAllowed = false,
                    nowMillis = 0L,
                    records = emptyList(),
                    pendingCount = 0,
                    aiLocked = false,
                    aiSession = null,
                    canReleaseStuckBatch = false,
                    effects = GlassEffects(),
                    skinChoices = emptyList(),
                    selectedSkin = "native",
                    onSelectSkin = {},
                    onImportSkin = {},
                    onExportOriginalSkin = {},
                    onExportInstalledSkin = {},
                    onDeleteSkin = {},
                    onBack = {},
                    onOpenDeck = {},
                    onImportIcs = {},
                    onExportIcs = {},
                    onImportSystem = {},
                    onMigrateLegacy = {},
                    onExportBackup = {},
                    onRestoreBackup = {},
                    onRequestNotificationPermission = {},
                    onOpenExactAlarmSettings = {},
                    onCancelBatch = {},
                    onReconcile = {},
                    onReleaseStuck = {}
                )
            }
        }

        ui.onNodeWithText("个人信息").assertExists()
        ui.onNodeWithText("单一 AI").assertExists()
        ui.onNodeWithText("配置端点、模型与密钥").assertExists()
    }

    @Test fun settingsRendererApiDoesNotRetainHostModelsOrNativeImplementations() {
        val apiTypes = listOf(
            SettingsVisualContext::class.java,
            SettingsDisplaySnapshot::class.java,
            SettingsSkinChoice::class.java,
            SettingsVisualEffects::class.java,
            NativeProtectedProfileDisplay::class.java,
            NativeProtectedProfileActions::class.java
        )
        val exposedTypes = apiTypes.flatMap { type ->
            type.declaredFields.map { it.genericType.typeName } +
                type.declaredConstructors.flatMap { constructor ->
                    constructor.genericParameterTypes.map { it.typeName }
                } +
                type.declaredMethods.flatMap { method ->
                    listOf(method.genericReturnType.typeName) + method.genericParameterTypes.map { it.typeName }
                }
        }

        assertFalse(exposedTypes.any { it.contains("dev.powerfulcalendar.core.") })
        assertFalse(exposedTypes.any { it.contains("dev.powerfulcalendar.ai.AiBatchRunner") })
        assertFalse(exposedTypes.any { it.contains("InstalledSkin") })
        assertFalse(exposedTypes.any { it.contains("GlassEffects") })
        assertFalse(exposedTypes.any { it.contains("NativeToolsPage") })
        assertFalse(exposedTypes.any { it.contains("SharedPreferences") })
        assertFalse(exposedTypes.any { it.contains("android.content.Context") })
        assertFalse(exposedTypes.any { it.contains("AiSettingsStore") })
    }

    @Test fun hostProjectsSettingsInputsIntoValueOnlyDisplayState() {
        val display = projectSettingsDisplaySnapshot(
            diagnostics = UiDiagnostics(nextSequence = 42, identities = 17),
            records = emptyList(),
            pendingCount = 3,
            aiLocked = true,
            aiSession = null,
            canReleaseStuckBatch = false,
            exactAlarmAllowed = true,
            notificationsAllowed = false,
            nowMillis = 0L
        )

        assertEquals(3, display.pendingCount)
        assertEquals(0, display.formalEventCount)
        assertEquals("#0042", display.nextSequenceLabel)
        assertEquals(17L, display.identityCount)
        assertTrue(display.exactAlarmAllowed)
        assertFalse(display.notificationsAllowed)
        assertTrue(display.aiLocked)
        assertFalse(display.aiBatchActive)
        assertFalse(display.aiBatchCancelRequested)
        assertTrue(display.reminders.isEmpty())
    }
}
