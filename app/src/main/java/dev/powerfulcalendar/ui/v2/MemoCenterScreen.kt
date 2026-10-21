package dev.powerfulcalendar.ui.v2

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
import dev.powerfulcalendar.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Lightweight read projection. Nothing here creates extra linked event objects. */
internal data class TodoEntry(
    val owner: TemporalRecord,
    val occurrenceKey: String,
    val startMillis: Long?,
    val title: String,
    val completed: Boolean = false
) {
    val id: String get() = owner.eventSeq.toString() + ":" + occurrenceKey
}

/**
 * Repeat projection is deliberately windowed. Events before the lookback remain in PowerStore;
 * the UI discloses this bound instead of silently claiming this is all historical debt.
 * Unbounded future instances are never materialized.
 */
internal object TodoProjection {
    const val LOOKBACK_DAYS = 365L
    const val AHEAD_DAYS = 7L
    fun entries(records: List<TemporalRecord>, nowMillis: Long,
                includeCompleted: Boolean = false): List<TodoEntry> {
        val earliest = nowMillis - LOOKBACK_DAYS * 86_400_000L
        val latest = nowMillis + AHEAD_DAYS * 86_400_000L
        return records.filter { it.trackingEnabled && it.enabled }.flatMap { record ->
            if (!record.recurrence.isRepeating) {
                val done = "single" in record.completedKeys
                if (done && !includeCompleted) emptyList()
                else listOf(TodoEntry(record, "single", record.anchorMillis(), record.title, done))
            } else {
                val anchor = record.anchorMillis() ?: return@flatMap emptyList()
                RecurrenceEngine.occurrences(record, maxOf(earliest, anchor), latest, limit = 1200)
                    .filterNot { it.skipped || (!includeCompleted && it.key in record.completedKeys) }
                    .map { TodoEntry(record, it.key, it.startMillis, record.title,
                        it.key in record.completedKeys) }
            }
        }.sortedWith(compareBy<TodoEntry>({ it.startMillis == null }, { it.startMillis ?: Long.MAX_VALUE },
            { it.owner.eventSeq }, { it.occurrenceKey }))
    }
}

@Composable
internal fun MemoCenterScreen(
    items: List<TodoEntry>,
    zone: ZoneId,
    busy: Boolean,
    onBack: () -> Unit,
    onCreateMemo: () -> Unit,
    onToggle: (TodoEntry) -> Unit,
    onOpen: (TemporalRecord) -> Unit
) {
    var showCompleted by remember { mutableStateOf(false) }
    val visible = items.filter { it.completed == showCompleted }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(if (showCompleted) "Memo · 已完成" else "Memo · 未完成",
                    style = MaterialTheme.typography.titleLarge)
                Text("统一待办中心 · " + visible.size + " 项（重复任务展示近一年及未来七日）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { entry ->
                    Surface(
                        onClick = { onOpen(entry.owner) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = entry.completed, enabled = !busy,
                                onCheckedChange = { checked ->
                                    if (checked != entry.completed) onToggle(entry)
                                })
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1)
                                val displayDate = entry.startMillis?.let {
                                    Instant.ofEpochMilli(it).atZone(entry.owner.zone).toLocalDate().toString()
                                } ?: "无日期"
                                Text(entry.owner.kind.label + " · " + displayDate +
                                    if (entry.owner.recurrence.isRepeating) " · 重复" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(entry.owner.displaySeq(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
