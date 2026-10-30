package dev.powerfulcalendar.ui.v2.nativeui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeProtectedAiSettingsActions
import dev.powerfulcalendar.ui.v2.NativeProtectedAiSettingsDisplay

/** Native-only AI settings presentation. The Host supplies the password field and owns all data. */
@Composable
fun NativeProtectedAiSettingsSection(
    display: NativeProtectedAiSettingsDisplay,
    actions: NativeProtectedAiSettingsActions,
    protectedApiKeyField: @Composable () -> Unit,
    cardShape: Shape
) {
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
    val subtitle = if (display.configured) "已配置 · 模型 ${display.model}" else "未配置"

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("单一 AI", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                    value = display.endpoint,
                    onValueChange = actions.onEndpointChange,
                    label = { Text("端点 · https://api.example.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = display.model,
                    onValueChange = actions.onModelChange,
                    label = { Text("模型名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                protectedApiKeyField()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用 AI", modifier = Modifier.weight(1f))
                    Switch(
                        checked = display.enabled,
                        onCheckedChange = actions.onEnabledChange
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
                        checked = display.shareContext,
                        onCheckedChange = actions.onShareContextChange
                    )
                }
                if (display.hasFileAutomationConsent) {
                    TextButton(onClick = actions.onRevokeFileAutomationConsent) {
                        Text("撤销文件 AI 自动处理授权")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = actions.onSave,
                        enabled = display.hasUnsavedChanges
                    ) { Text("保存") }
                    OutlinedButton(onClick = actions.onSaveAndEnable) {
                        Text("保存并启用")
                    }
                }
            }
            Text(
                "聊天、OCR、ICS 和文档都从底部 AI 助手进入。日历变更在待处理确认，设置页只管理模型连接与隐私权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
