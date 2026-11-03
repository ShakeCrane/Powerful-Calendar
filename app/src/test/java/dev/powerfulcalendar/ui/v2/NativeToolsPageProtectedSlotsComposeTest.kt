package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.api.v1.CoreRecurrenceFeatureApiV1
import dev.powerfulcalendar.core.api.v1.RecurrenceNextOccurrenceQueryV1
import dev.powerfulcalendar.core.api.v1.toEventRecordV1
import dev.powerfulcalendar.core.api.v1.AndroidSettingsDataFeatureApiV1
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
        PresentationSkinRegistry.reset()
        ui.setContent {
            PowerCalTheme {
                ToolsPage(
                    diagnostics = UiDiagnostics(),
                    exactAlarmAllowed = false,
                    notificationsAllowed = false,
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
                    onReleaseStuck = {},
                    settingsDataApi = AndroidSettingsDataFeatureApiV1(
                        ApplicationProvider.getApplicationContext<Context>()
                    )
                )
            }
        }

        ui.onNodeWithText("个人信息").assertExists()
        ui.onNodeWithText("单一 AI").assertExists()
        ui.onNodeWithText("配置端点、模型与密钥").assertExists()
        ui.onNodeWithText("皮肤", useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithText("导入皮肤包").assertDoesNotExist()
        ui.onNodeWithText("导出 Clear 候选皮肤").assertDoesNotExist()
        ui.onNodeWithText("从 ICS 文件导入").assertExists()
        ui.onNodeWithText("从系统日历一次性导入").assertExists()
        ui.onNodeWithText("导出原生备份（Envelope v1）").assertExists()
        ui.onNodeWithText("从备份恢复").assertExists()
        ui.onNodeWithText("精确闹钟设置").assertExists()
        ui.onNode(hasText("通知权限") and hasClickAction()).assertExists()
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
            diagnostics = UiDiagnostics(
                nextSequence = 42,
                identities = 17,
                databaseVersion = 9,
                databaseName = "fixture-settings.db"
            ),
            records = emptyList(),
            pendingCount = 3,
            aiLocked = true,
            aiSession = null,
            canReleaseStuckBatch = false,
            exactAlarmAllowed = true,
            notificationsAllowed = false
        )

        assertEquals(3, display.pendingCount)
        assertEquals(0, display.formalEventCount)
        assertEquals("#0042", display.nextSequenceLabel)
        assertEquals(17L, display.identityCount)
        assertEquals(9, display.databaseVersion)
        assertEquals("fixture-settings.db", display.databaseName)
        assertTrue(display.exactAlarmAllowed)
        assertFalse(display.notificationsAllowed)
        assertTrue(display.aiLocked)
        assertFalse(display.aiBatchActive)
        assertFalse(display.aiBatchCancelRequested)
        assertTrue(display.reminders.isEmpty())
    }

    @Test fun settingsProjectionKeepsExactCountAndRecurrenceFromLaterStorePages() {
        val laterRecurrence = TemporalRecord(
            eventSeq = 301,
            revision = 1,
            kind = TemporalKind.ALARM,
            title = "later recurring event",
            startMillis = 1_000L,
            recurrence = Recurrence(RecurrenceFreq.DAILY),
            zoneId = "UTC"
        )
        val display = projectSettingsDisplaySnapshot(
            diagnostics = UiDiagnostics(),
            records = emptyList(),
            pendingCount = 0,
            aiLocked = false,
            aiSession = null,
            canReleaseStuckBatch = false,
            exactAlarmAllowed = false,
            notificationsAllowed = false,
            formalEventCount = 301,
            recurrencePreviews = listOf(
                CoreRecurrenceFeatureApiV1().queryNextOccurrence(
                    RecurrenceNextOccurrenceQueryV1(
                        event = laterRecurrence.toEventRecordV1(),
                        afterMillis = 0L,
                        localZoneId = "UTC"
                    )
                )
            )
        )

        assertEquals(301, display.formalEventCount)
        assertEquals("重复 #301", display.recurrence.single().label)
    }
}
