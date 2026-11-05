package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.api.v1.SettingsDataFeatureApiV1
import dev.powerfulcalendar.ui.v2.NativeProtectedAiSettingsActions
import dev.powerfulcalendar.ui.v2.NativeProtectedAiSettingsDisplay
import dev.powerfulcalendar.ui.v2.nativeui.NativeProtectedAiSettingsSection
import dev.powerfulcalendar.ui.v2.nativeui.NativeProtectedProfileSection

/** Host-owned profile editor. The visual context never receives private preferences. */
@Composable
internal fun HostProtectedProfileSection(settingsDataApi: SettingsDataFeatureApiV1) {
    var displayName by remember(settingsDataApi) {
        mutableStateOf(settingsDataApi.querySettings().profileDisplayName)
    }
    NativeProtectedProfileSection(
        display = NativeProtectedProfileDisplay(displayName),
        actions = NativeProtectedProfileActions(
            onDisplayNameChange = { displayName = it },
            onSave = { settingsDataApi.saveProfileDisplayName(displayName) }
        ),
        cardShape = skinCardShape()
    )
}

/** Host-owned AI settings control; renderer code cannot read or persist the key. */
@Composable
internal fun HostProtectedAiSettingsSection(onDirtyChange: (Boolean) -> Unit = {}) {
    val appContext = LocalContext.current.applicationContext
    var settings by remember { mutableStateOf(AiSettingsStore.load(appContext)) }
    var dirty by remember { mutableStateOf(false) }
    var consentRevision by remember { mutableIntStateOf(0) }
    fun updateSettings(updated: AiSettings) {
        settings = updated
        dirty = true
        onDirtyChange(true)
    }
    NativeProtectedAiSettingsSection(
        display = NativeProtectedAiSettingsDisplay(
            configured = settings.configured,
            model = settings.model,
            endpoint = settings.endpoint,
            enabled = settings.enabled,
            shareContext = settings.shareContext,
            hasUnsavedChanges = dirty,
            hasFileAutomationConsent = consentRevision >= 0 &&
                AiFileFallbackConsent.allowed(appContext, settings)
        ),
        actions = NativeProtectedAiSettingsActions(
            onEndpointChange = { updateSettings(settings.copy(endpoint = it)) },
            onModelChange = { updateSettings(settings.copy(model = it)) },
            onEnabledChange = { updateSettings(settings.copy(enabled = it)) },
            onShareContextChange = { updateSettings(settings.copy(shareContext = it)) },
            onSave = {
                AiSettingsStore.save(appContext, settings)
                dirty = false
                onDirtyChange(false)
            },
            onSaveAndEnable = {
                AiSettingsStore.save(appContext, settings.copy(enabled = true))
                settings = settings.copy(enabled = true)
                dirty = false
                onDirtyChange(false)
            },
            onRevokeFileAutomationConsent = {
                AiFileFallbackConsent.revoke(appContext)
                consentRevision++
            }
        ),
        protectedApiKeyField = {
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { settings = settings.copy(apiKey = it); dirty = true },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        },
        cardShape = skinCardShape()
    )
}

/** A narrow Host-owned surface for Skin Settings; secrets never enter the Web bridge. */
@Composable
internal fun HostProtectedAiSettingsDialog(onDismiss: () -> Unit) {
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestDismiss = {
        if (hasUnsavedChanges) confirmDiscard = true else onDismiss()
    }
    Dialog(onDismissRequest = requestDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
                    .imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AI 配置与隐私",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = requestDismiss) { Text("关闭") }
                }
                HostProtectedAiSettingsSection(onDirtyChange = { hasUnsavedChanges = it })
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的 AI 配置？") },
            text = { Text("尚未保存的更改将被丢弃。") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("放弃更改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            }
        )
    }
}
