package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        val settings = SettingsVisualContext(
            diagnostics = UiDiagnostics(),
            records = emptyList(),
            pendingCount = 0,
            aiLocked = false,
            aiSession = null,
            canReleaseStuckBatch = false,
            effects = GlassEffects(),
            skinChoices = emptyList(),
            selectedSkin = "native",
            onSelectSkin = { _ -> },
            onImportSkin = {},
            onExportOriginalSkin = {},
            onExportInstalledSkin = { _ -> },
            onDeleteSkin = { _ -> },
            onBack = {},
            onOpenDeck = {},
            onImportIcs = {},
            onExportIcs = {},
            onImportSystem = {},
            onMigrateLegacy = {},
            onPickImage = {},
            onTextDraft = {},
            onExportBackup = {},
            onRestoreBackup = {},
            onRequestNotificationPermission = {},
            onOpenExactAlarmSettings = {},
            onRunBatch = { _ -> },
            onCancelBatch = {},
            onReconcile = {},
            onAiDraft = { _, _ -> },
            aiBusy = false,
            onReleaseStuck = {}
        )
        ui.setContent { PowerCalTheme { settings.Native() } }

        ui.onNodeWithText("个人信息").assertExists()
        ui.onNodeWithText("单一 AI").assertExists()
        ui.onNodeWithText("配置端点、模型与密钥").assertExists()
    }
}
