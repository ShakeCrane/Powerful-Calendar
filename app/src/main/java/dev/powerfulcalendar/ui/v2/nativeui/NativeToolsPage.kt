package dev.powerfulcalendar.ui.v2.nativeui

import dev.powerfulcalendar.ui.v2.*

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.reminders.ReminderScheduler

/** First-party Native settings and recovery UI; host actions remain supplied by SettingsVisualContext. */
@Composable
internal fun NativeToolsPage(
    diagnostics: UiDiagnostics,
    records: List<TemporalRecord>,
    pendingCount: Int,
    aiLocked: Boolean,
    aiSession: PowerStore.AiSession?,
    canReleaseStuckBatch: Boolean,
    effects: GlassEffects,
    skinChoices: List<InstalledSkin>,
    selectedSkin: String,
    onSelectSkin: (String) -> Unit,
    onImportSkin: () -> Unit,
    onExportOriginalSkin: () -> Unit,
    onExportInstalledSkin: (InstalledSkin) -> Unit,
    onDeleteSkin: (String) -> Unit,
    onBack: () -> Unit,
    onOpenDeck: () -> Unit,
    onImportIcs: () -> Unit,
    onExportIcs: () -> Unit,
    onImportSystem: () -> Unit,
    onMigrateLegacy: () -> Unit,
    onPickImage: () -> Unit,
    onTextDraft: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRunBatch: (AiBatchRunner.Mode) -> Unit,
    onCancelBatch: () -> Unit,
    onReconcile: () -> Unit,
    onAiDraft: (String, Boolean) -> Unit,
    aiBusy: Boolean,
    onReleaseStuck: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var aiText by rememberSaveable { mutableStateOf("") }
    var shareForRequest by rememberSaveable { mutableStateOf(false) }
    // Diagnostics are read once per data change instead of on every recomposition.
    val nextSequence = diagnostics.nextSequence
    val identityCount = diagnostics.identities

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回时间轴") }
            Text("工具与设置", style = MaterialTheme.typography.headlineSmall)
        }

        ProtectedProfileSection()
        ProtectedAiSettingsSection()

        SectionCard("皮肤", "支持独立 .pcskin 包、即时切换、重启保留；原生主题始终可用") {
            skinChoices.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedSkin == item.id,
                        onClick = { onSelectSkin(item.id) },
                        label = { Text(item.name, maxLines = 1) }
                    )
                    Spacer(Modifier.weight(1f))
                    if (!item.builtIn) {
                        TextButton(
                            onClick = { onExportInstalledSkin(item) },
                            modifier = Modifier.semantics {
                                contentDescription = "导出 ${item.name} 皮肤包"
                            }
                        ) { Text("导出") }
                        TextButton(onClick = { onDeleteSkin(item.id) }) { Text("删除") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportSkin) { Text("导入皮肤包") }
                TextButton(onClick = onExportOriginalSkin) { Text("导出 Clear 候选皮肤") }
            }
            Text("导入后不会自动启用。第三方皮肤可导出原包，在 Skin Studio 导入并继续编辑；" +
                "v3 皮肤可使用受限 HTML/CSS/JavaScript；原生设置随时可用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("导入", "仅保留系统级来源，其他文件请从 AI 助手 ＋ 添加") {
            ToolRow("从系统日历一次性导入", "读取过去 30 天与未来一年；不是双向同步", onImportSystem, enabled = !aiLocked)
            ToolRow("从旧版 PowerCal 迁移", "只读旧数据库，保留原文件", onMigrateLegacy, enabled = !aiLocked)
        }

        SectionCard("备份与导出", "原生备份优先于有损格式") {
            ToolRow(
                "导出原生备份（Envelope v1）",
                "单一致性快照 + SHA-256；含序号水位、草稿、提交与迁移记录；不含密钥。文件为明文敏感数据，请自行保管",
                onExportBackup
            )
            ToolRow(
                "从备份恢复",
                "校验版本、哈希完整性与引用；哈希不证明来源可信；只允许恢复到空数据库；" +
                    "恢复后可查看提醒权限状态；旧 flat v2 需显式确认",
                onRestoreBackup,
                enabled = !aiLocked
            )
            ToolRow("导出 ICS", "仅导出活动且不含活动启用/停用状态与活动提醒；闹钟、截止、备忘与完成状态不会由 ICS 保留",
                onExportIcs)
        }

        SectionCard("提醒可靠性", "保存 ≠ 已调度 ≠ 已触发") {
            val exact = ReminderScheduler.exactAlarmAllowed(appContext)
            val notifications = ReminderScheduler.notificationsAllowed(appContext)
            StatusLine("精确闹钟权限", if (exact) "已允许" else "未允许 · 需要的提醒会显示为受阻", !exact)
            StatusLine("通知权限", if (notifications) "已允许" else "未允许 · 触发会被记录为 BLOCKED", !notifications)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenExactAlarmSettings) { Text("精确闹钟设置") }
                OutlinedButton(onClick = onRequestNotificationPermission) { Text("通知权限") }
            }
            Text(
                "厂商对后台与精确闹钟的限制各不相同，PowerCal 不会承诺所有设备 100% 精确响铃；" +
                    "错过宽限窗口的提醒会记录为 MISSED 而不是补响。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onReconcile, modifier = Modifier.fillMaxWidth()) {
                Text("按当前数据重建调度")
            }
        }

        SectionCard("执行状态", "最近的期望调度与执行记录，最多 40 项") {
            if (aiSession?.isActive == true) {
                val activeState = aiSession.state
                StatusLine("AI 批次", if (activeState == PowerStore.AiState.CANCEL_REQUESTED)
                    "正在停止；已完成结果保留" else "正在运行；未完成草稿会继续等待", false)
                ToolRow(
                    "请求停止 AI 批次",
                    "已完成的操作保留，剩余草稿不执行",
                    onCancelBatch,
                    enabled = activeState == PowerStore.AiState.RUNNING
                )
                if (activeState == PowerStore.AiState.CANCEL_REQUESTED) {
                    ToolRow(
                        "强制解除卡住的 AI 批次",
                        "停止请求超过 10 分钟无响应后可用；当前结果数量可能无法确定",
                        onReleaseStuck,
                        enabled = aiLocked && canReleaseStuckBatch
                    )
                }
            }
            if (diagnostics.reminders.isEmpty()) Text("还没有启用的提醒。", style = MaterialTheme.typography.bodyMedium)
            diagnostics.reminders.forEach { row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${row.record.displaySeq()} · ${row.record.title}", style = MaterialTheme.typography.titleSmall)
                    val state = row.intent?.desired
                    Text("调度：" + when (state) {
                        PowerStore.DesiredSchedule.PENDING -> "等待调度"
                        PowerStore.DesiredSchedule.SCHEDULED -> "已交给系统"
                        PowerStore.DesiredSchedule.BLOCKED -> "受阻，请检查权限"
                        PowerStore.DesiredSchedule.CANCELLED -> "已取消"
                        PowerStore.DesiredSchedule.FIRED -> "已触发"
                        null -> "无待执行调度"
                    }, style = MaterialTheme.typography.bodyMedium, color = if (state == PowerStore.DesiredSchedule.BLOCKED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    row.intent?.androidHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    row.execution?.let { Text("最近执行：$it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        SectionCard("诊断", "只读") {
            StatusLine("正式事件", "${records.size} 条", false)
            StatusLine("下一个事件序号", TemporalRecord.formatSeq(nextSequence), false)
            StatusLine("已分配序号", "$identityCount 个（含退役）", false)
            StatusLine("数据库版本", "v${PowerStore.VERSION} · ${PowerStore.DATABASE_NAME}", false)
            StatusLine("视觉效果", if (effects.blur) "分层玻璃" else "简化实色界面", false)
            StatusLine("减少动画", if (effects.reduceMotion) "已遵循系统设置" else "未启用", false)
            records.filter { it.recurrence.isRepeating }.take(3).forEach { record ->
                val next = RecurrenceEngine.nextOccurrence(record, System.currentTimeMillis())
                StatusLine(
                    "重复 #${record.eventSeq}",
                    next?.let { "下一次 " + DraftForm.formatInstant(it.startMillis) } ?: "没有后续实例",
                    false
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Placement-safe profile editor. The visual context never receives raw private preferences. */
@Composable
internal fun ProtectedProfileSection() {
    val appContext = LocalContext.current.applicationContext
    SectionCard("个人信息", "本机资料 · 不上传给 AI") {
        var displayName by remember {
            mutableStateOf(appContext.getSharedPreferences("powercal-profile", Context.MODE_PRIVATE)
                .getString("displayName", "").orEmpty())
        }
        Text("头像与云端资料将在皮肤系统完成后支持；个人资料不是日历业务数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = displayName,
            onValueChange = { if (it.length <= 80) displayName = it },
            label = { Text("你的名字（可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedButton(onClick = {
            appContext.getSharedPreferences("powercal-profile", Context.MODE_PRIVATE).edit()
                .putString("displayName", displayName).apply()
        }) { Text("保存个人信息") }
    }


}

/** Credentials are rendered by this host-owned control; skins cannot read the value. */
@Composable
internal fun ProtectedAiSettingsSection() {
    val appContext = LocalContext.current.applicationContext
    var settings by remember { mutableStateOf(AiSettingsStore.load(appContext)) }
    var dirty by remember { mutableStateOf(false) }
    var consentRevision by remember { mutableIntStateOf(0) }
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(
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
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = skinCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun ToolRow(title: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = GlassShapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, warn: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(if (warn) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null, Modifier.size(20.dp),
            tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
