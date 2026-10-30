package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiSettingsStore
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
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
    HostProtectedSectionCard(
        "单一 AI",
        if (settings.configured) "已配置 · 模型 ${settings.model}" else "未配置"
    ) {
        Text(
            "描述计划，先生成待确认草稿；批量发布或丢弃需要一次性授权。" +
                "密钥保存在应用私有存储，不进入备份，也不会写入日志。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { modelExpanded = !modelExpanded }) {
            Icon(if (modelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            Text(if (modelExpanded) "收起模型配置" else "配置端点、模型与密钥")
        }
        if (modelExpanded) {
            OutlinedTextField(
                value = settings.endpoint,
                onValueChange = { settings = settings.copy(endpoint = it); dirty = true },
                label = { Text("端点 · https://api.example.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = settings.model,
                onValueChange = { settings = settings.copy(model = it); dirty = true },
                label = { Text("模型名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { settings = settings.copy(apiKey = it); dirty = true },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用 AI", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { settings = settings.copy(enabled = it); dirty = true }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许按次共享事件上下文", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "开启后，每次请求仍需单独选择是否共享，最多 60 条事件。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.shareContext,
                    onCheckedChange = { settings = settings.copy(shareContext = it); dirty = true }
                )
            }
            if (consentRevision >= 0 && AiFileFallbackConsent.allowed(appContext, settings)) {
                TextButton(onClick = {
                    AiFileFallbackConsent.revoke(appContext)
                    consentRevision++
                }) { Text("撤销文件 AI 自动处理授权") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        AiSettingsStore.save(appContext, settings)
                        dirty = false
                    },
                    enabled = dirty
                ) { Text("保存") }
                OutlinedButton(
                    onClick = {
                        AiSettingsStore.save(appContext, settings.copy(enabled = true))
                        settings = settings.copy(enabled = true)
                        dirty = false
                    }
                ) { Text("保存并启用") }
            }
        }
        Text("聊天、OCR、ICS 和文档都从底部 AI 助手进入。日历变更在待处理确认，设置页只管理模型连接与隐私权限。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HostProtectedSectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = skinCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
