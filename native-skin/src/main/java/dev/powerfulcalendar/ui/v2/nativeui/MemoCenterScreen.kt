package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeMemoRow

@Composable
fun NativeMemoCenterScreen(
    items: List<NativeMemoRow>,
    busy: Boolean,
    onBack: () -> Unit,
    onCreateMemo: () -> Unit,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    var showCompleted by remember { mutableStateOf(false) }
    val visible = items.filter { it.completed == showCompleted }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    if (showCompleted) "Memo · 已完成" else "Memo · 未完成",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "统一待办中心 · " + visible.size + " 项（重复任务展示近一年及未来七日）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCreateMemo, enabled = !busy) {
                Icon(Icons.Default.Add, "新增无日期 Memo")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showCompleted = !showCompleted }) {
                Text(if (showCompleted) "查看未完成" else "查看已完成")
            }
            Spacer(Modifier.weight(1f))
            Text("勾选状态可撤销", style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("目前没有待处理事项", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visible, key = { it.rowKey }) { entry ->
                    Surface(
                        onClick = { onOpen(entry.rowKey) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = entry.completed,
                                enabled = !busy,
                                onCheckedChange = { checked ->
                                    if (checked != entry.completed) onToggle(entry.rowKey)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    entry.kindLabel + " · " + entry.dateLabel +
                                        if (entry.repeating) " · 重复" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(entry.sequenceLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
