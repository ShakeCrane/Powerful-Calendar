package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalText

/**
 * Single editable skin activation point. Edit only files in this skins folder, not PowerCalApp.
 * "native" is the undeletable recovery path. Example is a separately compiled visual overhaul.
 */
object ActiveVisualSkin {
    const val PROFILE = "native"
    fun install() {
        PresentationSkinRegistry.reset()
        when (PROFILE) {
            "native" -> Unit
            "studio-example" -> installExample()
            else -> PresentationSkinRegistry.reset() // invalid config always falls back
        }
    }

    /** Public within app source, so instrumentation tests and local preview can enable the example. */
    fun installExample() {
        PresentationSkinRegistry.reset()
        CompiledSkinRuntime.registerTimeline(StudioExampleTimeline())
        PresentationSkinRegistry.useMonth(StudioExampleMonth())
        PresentationSkinRegistry.useMemo(StudioExampleMemo())
        PresentationSkinRegistry.useAi(StudioExampleAi())
        PresentationSkinRegistry.useSettings(StudioExampleSettings())
        PresentationSkinRegistry.useNavigation(StudioExampleNavigation())
        DeckPresentationSkinRegistry.use(StudioExampleDeck())
        PresentationSkinRegistry.useOverlay(StudioExampleOverlay())
        PresentationSkinRegistry.usePicker(StudioExamplePicker())
        PresentationSkinRegistry.useChrome(StudioExampleChrome())
    }
}

@Composable private fun DemoHeading(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private class StudioExampleNavigation : NavigationVisualRenderer {
    override val id = "studio-example/navigation"
    @Composable override fun Render(context: NavigationVisualContext) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            VisualTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = context.selected == tab,
                    onClick = { context.onSelect(tab) },
                    icon = { Text(when (tab) {
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

private class StudioExampleTimeline : CompiledTimelineSkin {
    override val rendererId = "studio-example/timeline"
    @Composable override fun Render(context: TimelineSkinContext) {
        var native by rememberSaveable { mutableStateOf(false) }
        if (native) {
            Box(Modifier.fillMaxSize()) {
                context.Native()
                FilledTonalButton(onClick = { native = false },
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text("日程概览")
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoHeading("Nova Timeline", "独立布局和组件 · 操作由宿主验证")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = context.onToday) { Text("今日") }
                    OutlinedButton(onClick = context.onOpenMemo) { Text("Memo " + context.memoCount) }
                    OutlinedButton(onClick = context.onOpenDeck) { Text("草稿 " + context.pendingCount) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !context.aiLocked,
                        onClick = { context.onCreate(TemporalKind.ACTIVITY, context.nowMillis) }) { Text("新建") }
                    TextButton(onClick = { native = true }) { Text("精确时间轴及手势") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(context.records.sortedBy { it.anchorMillis(context.zone) ?: Long.MAX_VALUE },
                        key = { it.eventSeq }) { record ->
                        Surface(onClick = { context.onOpenEvent(record, null) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(record.kind.label + " · " + record.title)
                                Text(TemporalText.summarize(record),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class StudioExampleMonth : MonthVisualRenderer {
    override val id = "studio-example/month"
    @Composable override fun Render(context: MonthVisualContext) {
        Column(Modifier.fillMaxSize()) {
            DemoHeading("Lunar Grid", "日期与重复发生实例保持原有规则")
            Box(Modifier.weight(1f)) { context.Native() }
        }
    }
}

private class StudioExampleMemo : MemoVisualRenderer {
    override val id = "studio-example/memo"
    @Composable override fun Render(context: MemoVisualContext) {
        var completed by rememberSaveable { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DemoHeading("Memo Board", "原始 occurrenceKey / revision 不变")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = context.onBack) { Text("返回") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { completed = !completed }) { Text(if (completed) "未完成" else "已完成") }
                Button(enabled = !context.busy, onClick = context.onCreateMemo) { Text("新建") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(context.items.filter { it.completed == completed }, key = { it.id }) { entry ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = entry.completed, enabled = !context.busy,
                                onCheckedChange = { if (it != entry.completed) context.onToggle(entry) })
                            Column(Modifier.weight(1f).clickable {
                                context.onOpen(entry.owner, entry.occurrenceKey.takeIf {
                                    entry.owner.recurrence.isRepeating
                                })
                            }) {
                                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(entry.owner.displaySeq(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class StudioExampleAi : AiVisualRenderer {
    override val id = "studio-example/ai"
    @Composable override fun Render(context: AiVisualContext) {
        Column(Modifier.fillMaxSize()) {
            DemoHeading("Neural Chat", "审批和附件权限继续由原生入口执行")
            Box(Modifier.weight(1f)) { context.Native() }
        }
    }
}

private class StudioExampleSettings : SettingsVisualRenderer {
    override val id = "studio-example/settings"
    @Composable override fun Render(context: SettingsVisualContext) {
        var advanced by rememberSaveable { mutableStateOf(false) }
        if (advanced) {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { advanced = false }) { Text("← 返回快速设置") }
                Box(Modifier.weight(1f)) { context.Native() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoHeading("Studio Settings", "独立布局，功能回调沿用宿主")
                TextButton(onClick = context.onBack) { Text("← 日历") }
                FilledTonalButton(onClick = context.onImportSkin, modifier = Modifier.fillMaxWidth()) { Text("导入数据皮肤") }
                FilledTonalButton(onClick = context.onExportOriginalSkin, modifier = Modifier.fillMaxWidth()) { Text("导出 Clear 候选皮肤") }
                FilledTonalButton(onClick = context.onOpenDeck, modifier = Modifier.fillMaxWidth()) { Text("草稿卡组") }
                FilledTonalButton(onClick = context.onExportBackup, modifier = Modifier.fillMaxWidth()) { Text("导出备份") }
                Button(onClick = { advanced = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("全部设置 / AI 凭证 / 诊断")
                }
            }
        }
    }
}
