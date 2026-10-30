package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

/** Value-only settings state passed across the Host/renderer boundary. */
internal data class SettingsDisplaySnapshot(
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

internal data class SettingsReminderDiagnostic(
    val eventLabel: String,
    val title: String,
    val scheduleLabel: String,
    val blocked: Boolean,
    val androidHint: String?,
    val execution: String?
)

internal data class SettingsRecurrencePreview(val label: String, val next: String)

internal data class SettingsSkinChoice(val id: String, val name: String, val builtIn: Boolean)

internal data class SettingsVisualEffects(
    val blur: Boolean = true,
    val animationScale: Float = 1f,
    val reduceMotion: Boolean = false
)

/**
 * Skin-facing Settings API. Host state is reduced to values and actions; Native and protected
 * content arrive as composable slots so this API does not depend on either implementation.
 */
internal class SettingsVisualContext internal constructor(
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
