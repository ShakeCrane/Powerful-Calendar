package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.ui.v2.NativeProtectedAiSettingsActions
import dev.powerfulcalendar.ui.v2.NativeProtectedAiSettingsDisplay
import dev.powerfulcalendar.ui.v2.nativeui.NativeProtectedAiSettingsSection
import dev.powerfulcalendar.ui.v2.nativeui.NativeProtectedProfileSection

/** Host-owned profile editor. The visual context never receives private preferences. */
@Composable
internal fun HostProtectedProfileSection() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember(appContext) {
        appContext.getSharedPreferences("powercal-profile", Context.MODE_PRIVATE)
    }
    var displayName by remember(preferences) {
        mutableStateOf(preferences.getString("displayName", "").orEmpty())
    }
    NativeProtectedProfileSection(
        display = NativeProtectedProfileDisplay(displayName),
        actions = NativeProtectedProfileActions(
            onDisplayNameChange = { displayName = it },
            onSave = { preferences.edit().putString("displayName", displayName).apply() }
        ),
        cardShape = skinCardShape()
    )
}

/** Host-owned AI settings control; renderer code cannot read or persist the key. */
@Composable
internal fun HostProtectedAiSettingsSection() {
    val appContext = LocalContext.current.applicationContext
    var settings by remember { mutableStateOf(AiSettingsStore.load(appContext)) }
    var dirty by remember { mutableStateOf(false) }
    var consentRevision by remember { mutableIntStateOf(0) }
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
            onEndpointChange = { settings = settings.copy(endpoint = it); dirty = true },
            onModelChange = { settings = settings.copy(model = it); dirty = true },
            onEnabledChange = { settings = settings.copy(enabled = it); dirty = true },
            onShareContextChange = { settings = settings.copy(shareContext = it); dirty = true },
            onSave = {
                AiSettingsStore.save(appContext, settings)
                dirty = false
            },
            onSaveAndEnable = {
                AiSettingsStore.save(appContext, settings.copy(enabled = true))
                settings = settings.copy(enabled = true)
                dirty = false
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
