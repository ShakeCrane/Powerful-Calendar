package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

/** Value-only settings state passed across the Host/renderer boundary. */
data class SettingsDisplaySnapshot(
    val pendingCount: Int = 0,
    val formalEventCount: Int = 0,
    val nextSequenceLabel: String = "#0001",
    val identityCount: Long = 0,
    val databaseVersion: Int = 0,
    val databaseName: String = "",
    val exactAlarmAllowed: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val aiLocked: Boolean = false,
    val canReleaseStuckBatch: Boolean = false,
    val aiBatchActive: Boolean = false,
    val aiBatchCancelRequested: Boolean = false,
    val reminders: List<SettingsReminderDiagnostic> = emptyList(),
    val recurrence: List<SettingsRecurrencePreview> = emptyList()
)

data class SettingsReminderDiagnostic(
    val eventLabel: String,
    val title: String,
    val scheduleLabel: String,
    val blocked: Boolean,
    val androidHint: String?,
    val execution: String?
)

data class SettingsRecurrencePreview(val label: String, val next: String)

data class SettingsSkinChoice(val id: String, val name: String, val builtIn: Boolean)

data class SettingsVisualEffects(
    val blur: Boolean = true,
    val animationScale: Float = 1f,
    val reduceMotion: Boolean = false
)

/** Profile display value supplied to the built-in Native settings renderer. */
data class NativeProtectedProfileDisplay(val displayName: String)

/** Host-owned profile edits; persistence remains in the app process. */
data class NativeProtectedProfileActions(
    val onDisplayNameChange: (String) -> Unit,
    val onSave: () -> Unit
)

/** Display-only state for the built-in protected AI settings renderer. The API key is never part
 * of this model; the Host renders its password field through a protected content slot. */
data class NativeProtectedAiSettingsDisplay(
    val configured: Boolean,
    val model: String,
    val endpoint: String,
    val enabled: Boolean,
    val shareContext: Boolean,
    val hasUnsavedChanges: Boolean,
    val hasFileAutomationConsent: Boolean
)

/** AI settings edits and persistence stay Host-owned. */
data class NativeProtectedAiSettingsActions(
    val onEndpointChange: (String) -> Unit,
    val onModelChange: (String) -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onShareContextChange: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onSaveAndEnable: () -> Unit,
    val onRevokeFileAutomationConsent: () -> Unit
)

/**
 * Skin-facing Settings API. Host state is reduced to values and actions; Native and protected
 * content arrive as composable slots so this API does not depend on either implementation.
 */
class SettingsVisualContext(
    val display: SettingsDisplaySnapshot,
    val effects: SettingsVisualEffects,
    val skinChoices: List<SettingsSkinChoice>,
    val selectedSkin: String,
    val onSelectSkin: (String) -> Unit,
    val onImportSkin: () -> Unit,
    val onExportOriginalSkin: () -> Unit,
    val onExportInstalledSkin: (String) -> Unit,
    val onDeleteSkin: (String) -> Unit,
    val onBack: () -> Unit,
    val onOpenDeck: () -> Unit,
    val onImportIcs: () -> Unit,
    val onExportIcs: () -> Unit,
    val onImportSystem: () -> Unit,
    val onMigrateLegacy: () -> Unit,
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onOpenExactAlarmSettings: () -> Unit,
    val onCancelBatch: () -> Unit,
    val onReconcile: () -> Unit,
    val onReleaseStuck: () -> Unit,
    private val nativeFallback: @Composable () -> Unit,
    private val protectedProfileContent: @Composable () -> Unit,
    private val protectedAiCredentialsContent: @Composable () -> Unit
) {
    val pendingCount: Int get() = display.pendingCount
    val aiLocked: Boolean get() = display.aiLocked
    val canReleaseStuckBatch: Boolean get() = display.canReleaseStuckBatch

    @Composable fun ProtectedProfile() = protectedProfileContent()
    @Composable fun ProtectedAiCredentials() = protectedAiCredentialsContent()
    @Composable fun Native() = nativeFallback()
}

enum class VisualTab(val title: String) {
    TIMELINE("时间轴"), MONTH("月历"), AI("AI 助手"), TOOLS("设置")
}

class NavigationVisualContext(
    val selected: VisualTab?,
    val onSelect: (VisualTab) -> Unit,
    private val nativeFallback: @Composable () -> Unit
) {
    @Composable fun Native() = nativeFallback()
}
