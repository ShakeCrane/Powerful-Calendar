package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalText
import java.time.LocalDate

/** Typed host bridge for non-executable layout trees. All actions reuse existing callbacks. */
internal object RuntimeSkinSurfaces {
    fun enabled(screen: String): Boolean =
        PowerCalSkinRegistry.active.runtime?.screens?.containsKey(screen) == true

    @Composable fun timeline(context: TimelineSkinContext) {
        RuntimeSkinCompose.scene("timeline", RuntimeSkinBindings(
            native = { context.Native() },
            actions = mapOf(
                "today" to context.onToday, "memo" to context.onOpenMemo,
                "month" to context.onOpenMonth, "deck" to context.onOpenDeck,
                "settings" to context.onOpenTools,
                "new-alarm" to { context.onCreate(TemporalKind.ALARM, context.nowMillis) },
                "new-activity" to { context.onCreate(TemporalKind.ACTIVITY, context.nowMillis) },
                "new-deadline" to { context.onCreate(TemporalKind.DEADLINE, context.nowMillis) }
            ),
            values = mapOf("pending" to context.pendingCount.toString(),
                "memo" to context.memoCount.toString()),
            widget = { kind ->
                if (kind == "events") EventRows(context.records, context.zone, context.onOpenEvent)
            }
        ), context.modifier.fillMaxSize())
    }

    @Composable fun month(context: MonthVisualContext) {
        RuntimeSkinCompose.scene("month", RuntimeSkinBindings(
            native = { context.Native() },
            actions = mapOf("back" to context.onBack,
                "new" to { context.onCreate(context.initialDate) }),
            values = mapOf("date" to context.initialDate.toString()),
            widget = { kind ->
                if (kind == "events") EventRows(context.records, context.zone, context.onOpen)
            }
        ), Modifier.fillMaxSize())
    }

    @Composable fun memo(context: MemoVisualContext) {
        RuntimeSkinCompose.scene("memo", RuntimeSkinBindings(
            native = { context.Native() },
            actions = mapOf("back" to context.onBack,
                "new" to { if (!context.busy) context.onCreateMemo() }),
            values = mapOf("count" to context.items.count { !it.completed }.toString()),
            widget = { kind ->
                if (kind == "todos") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        context.items.take(100).forEach { entry ->
                            Surface(shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = entry.completed,
                                        onCheckedChange = { next ->
                                            if (!context.busy && next != entry.completed) context.onToggle(entry)
                                        },
                                        enabled = !context.busy
                                    )
                                    Text(entry.title, Modifier.weight(1f)
                                        .clickable { context.onOpen(entry.owner) },
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        ), Modifier.fillMaxSize())
    }

    @Composable fun ai(context: AiVisualContext) {
        RuntimeSkinCompose.scene("ai", RuntimeSkinBindings(
            native = { context.Native() },
            actions = mapOf(
                "settings" to context.onSettings, "deck" to context.onOpenDeck,
                "pick-image" to context.onPickImage, "pick-file" to context.onPickFile,
                "clear-cache" to context.onClearCache
            ),
            values = mapOf("pending" to context.pendingCount.toString()),
            widget = { kind ->
                when(kind) {
                    "chats" -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        context.messages.takeLast(60).forEach { message ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (message.role == "user")
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(if (message.role == "user") "我" else "AI",
                                        style = MaterialTheme.typography.labelSmall)
                                    Text(message.content.take(1800), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    "chat-input" -> {
                        var draft by rememberSaveable { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draft, onValueChange = { if (it.length <= 6000) draft = it },
                                label = { Text("发送消息") }, modifier = Modifier.weight(1f),
                                maxLines = 4
                            )
                            Button(enabled = draft.isNotBlank() && !context.busy,
                                onClick = { context.onSend(draft); draft = "" }) { Text("发送") }
                        }
                        if (context.proposalReview != null) {
                            // All interpretation/review controls remain in the first-party screen.
                            Text("有待审核的 AI 提案，请通过「完整 AI 界面」确认。",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        ), Modifier.fillMaxSize())
    }

    @Composable fun settings(context: SettingsVisualContext) {
        RuntimeSkinCompose.scene("settings", RuntimeSkinBindings(
            native = { context.Native() },
            actions = mapOf(
                "back" to context.onBack, "deck" to context.onOpenDeck,
                "__restore_original" to { context.onSelectSkin("powercal-default") },
                "import-skin" to context.onImportSkin,
                "export-skin" to context.onExportOriginalSkin,
                "import-ics" to context.onImportIcs,
                "export-ics" to context.onExportIcs,
                "export-backup" to context.onExportBackup,
                "restore-backup" to context.onRestoreBackup,
                "notification-permission" to context.onRequestNotificationPermission,
                "exact-alarm" to context.onOpenExactAlarmSettings
            ),
            values = mapOf("pending" to context.pendingCount.toString()),
            widget = { kind ->
                when(kind) {
                    "profile" -> context.ProtectedProfile()
                    "credentials" -> context.ProtectedAiCredentials()
                    "skins" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        context.skinChoices.filterNot { it.builtIn }.forEach { skin ->
                            FilledTonalButton(
                                onClick = { context.onSelectSkin(skin.id) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = skin.id != context.selectedSkin
                            ) { Text(skin.name + if (skin.id == context.selectedSkin) " ✓" else "") }
                        }
                        OutlinedButton(onClick = context.onImportSkin) { Text("导入 .pcskin") }
                    }
                }
            }
        ), Modifier.fillMaxSize())
    }

    @Composable fun navigation(context: NavigationVisualContext) {
        RuntimeSkinCompose.scene("navigation", RuntimeSkinBindings(
            native = { context.Native() },
            actions = mapOf(
                "timeline" to { context.onSelect(VisualTab.TIMELINE) },
                "month" to { context.onSelect(VisualTab.MONTH) },
                "ai" to { context.onSelect(VisualTab.AI) },
                "settings" to { context.onSelect(VisualTab.TOOLS) }
            ),
            widget = { kind ->
                if (kind == "nav-items") {
                    NavigationBar(modifier = Modifier.fillMaxWidth()) {
                        VisualTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = context.selected == tab,
                                onClick = { context.onSelect(tab) },
                                icon = { Text(when(tab) {
                                    VisualTab.TIMELINE -> "◷"
                                    VisualTab.MONTH -> "▦"
                                    VisualTab.AI -> "✦"
                                    VisualTab.TOOLS -> "⚙"
                                }) },
                                label = { Text(tab.title) }
                            )
                        }
                    }
                }
            }
        ))
    }

    @Composable fun deck(context: DeckVisualContext) {
        // Do not allow an imported deck layout to swallow conflict or restore decisions.
        if (context.state.conflict != null || context.state.restore != null) {
            context.Native()
            return
        }
        var expanded by rememberSaveable { mutableStateOf(false) }
        if (expanded) {
            context.Native()
            return
        }
        Dialog(
            onDismissRequest = context.onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false,
                dismissOnClickOutside = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                Modifier.fillMaxWidth().heightIn(max = 760.dp).padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                    RuntimeSkinCompose.scene("deck", RuntimeSkinBindings(
                        native = { Text("草稿编辑器") },
                        actions = mapOf(
                            "back" to context.onDismiss,
                            "commit" to { if (!context.busy) context.onCommit() },
                            "discard" to { if (!context.busy) context.onDiscard() },
                            "undo" to { if (!context.busy) context.onUndoDiscard() },
                            "save" to { if (!context.busy) context.onRetrySave() },
                            "native" to { expanded = true }
                        ),
                        values = mapOf("title" to (context.state.form?.title ?: "无待处理草稿"),
                            "count" to context.state.cards.size.toString()),
                        widget = { kind ->
                            if (kind == "deck-actions") {
                                Text(context.state.form?.title ?: "没有待处理草稿")
                                context.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                OutlinedButton(onClick = { expanded = true }) { Text("完整原生编辑器") }
                                Row {
                                    Button(onClick = context.onCommit,
                                        enabled = !context.busy && !context.state.isEmpty) { Text("提交") }
                                    OutlinedButton(onClick = context.onDismiss) { Text("关闭") }
                                }
                            }
                        }
                    ), Modifier.fillMaxWidth(), manageRecovery = false)
                    // Mandatory escape hatch for the full editor and all original deck operations.
                    TextButton(onClick = { expanded = true }) { Text("进入完整草稿编辑器") }
                }
            }
        }
    }

    @Composable fun overlay(context: OverlayVisualContext) {
        val node = PowerCalSkinRegistry.active.runtime?.screens?.get("overlay")
        if (node == null || containsType(node, "native")) { context.Native(); return }
        Dialog(onDismissRequest = context.onDismissRequest, properties = context.properties) {
            Surface(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                RuntimeSkinCompose.scene("overlay", RuntimeSkinBindings(
                    native = { },
                    widget = { kind ->
                        when (kind) {
                            "overlay-title" -> context.title?.invoke()
                            "overlay-body" -> context.text?.invoke()
                            "overlay-confirm" -> context.confirmButton?.invoke()
                            "overlay-dismiss" -> context.dismissButton?.invoke()
                            "window-content" -> context.content?.invoke()
                        }
                    }
                ), Modifier.fillMaxWidth(), manageRecovery = false)
            }
        }
    }

    @Composable fun chrome(context: ChromeVisualContext) {
        if (context.type != ChromeType.ROOT) { context.Native(); return }
        RuntimeSkinCompose.scene("chrome", RuntimeSkinBindings(
            native = {
                context.Body(
                    Modifier.fillMaxSize().statusBarsPadding()
                        .navigationBarsPadding().imePadding()
                )
            }
        ), Modifier.fillMaxSize(), manageRecovery = false)
    }

    private fun containsType(node: RuntimeSkinNode, kind: String): Boolean =
        node.type == kind || node.children.any { containsType(it, kind) }

    @Composable private fun EventRows(
        records: List<dev.powerfulcalendar.core.TemporalRecord>,
        zone: java.time.ZoneId,
        open: (dev.powerfulcalendar.core.TemporalRecord) -> Unit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            records.sortedBy { it.anchorMillis(zone) ?: Long.MAX_VALUE }.take(70).forEach { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { open(record) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text(record.title, style = MaterialTheme.typography.titleSmall)
                        Text(TemporalText.summarize(record),
                            style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
        }
    }
}
