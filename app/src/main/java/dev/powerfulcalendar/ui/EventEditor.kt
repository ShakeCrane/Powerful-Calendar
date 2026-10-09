package dev.powerfulcalendar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.Priority
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val editorTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun EventEditor(
    initial: CalendarEvent,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit
) {
    val zone = ZoneId.systemDefault()
    fun format(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).format(editorTimeFormat)
    var title by remember(initial) { mutableStateOf(initial.title) }
    var startText by remember(initial) { mutableStateOf(format(initial.startMillis)) }
    var endText by remember(initial) { mutableStateOf(format(initial.endMillis)) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }
    var location by remember(initial) { mutableStateOf(initial.location) }
    var priority by remember(initial) { mutableStateOf(initial.priority) }
    var reminder by remember(initial) { mutableIntStateOf(initial.reminderMinutes) }
    var allDay by remember(initial) { mutableStateOf(initial.allDay) }
    var error by remember(initial) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "新增事件" else "编辑事件") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(startText, { startText = it }, label = { Text("开始时间") },
                    supportingText = { Text("yyyy-MM-dd HH:mm") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true)
                OutlinedTextField(endText, { endText = it }, label = { Text("结束时间") },
                    supportingText = { Text("yyyy-MM-dd HH:mm") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("全天事件", modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                OutlinedTextField(location, { location = it }, label = { Text("地点（可选）") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("优先级", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Priority.entries.forEach { level ->
                        FilterChip(selected = priority == level, onClick = { priority = level },
                            label = { Text(level.label) })
                    }
                }
                Text("提醒", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(-1, 0, 5, 10, 30, 60, 1440).forEach { minutes ->
                        val label = when(minutes) {
                            -1 -> "不提醒"
                            0 -> "准时"
                            60 -> "1小时"
                            1440 -> "1天"
                            else -> minutes.toString() + "分钟"
                        }
                        FilterChip(selected = reminder == minutes, onClick = { reminder = minutes },
                            label = { Text(label) })
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    error = "请输入标题"
                    return@TextButton
                }
                try {
                    val start = LocalDateTime.parse(startText, editorTimeFormat)
                    val end = LocalDateTime.parse(endText, editorTimeFormat)
                    val first = if (allDay) start.toLocalDate().atStartOfDay() else start
                    val last = if (allDay) end.toLocalDate().plusDays(1).atStartOfDay() else end
                    if (!last.isAfter(first)) {
                        error = "结束时间必须晚于开始时间"
                        return@TextButton
                    }
                    onSave(initial.copy(
                        title = title.trim(),
                        startMillis = first.atZone(zone).toInstant().toEpochMilli(),
                        endMillis = last.atZone(zone).toInstant().toEpochMilli(),
                        notes = notes.trim(),
                        location = location.trim(),
                        priority = priority,
                        reminderMinutes = reminder,
                        allDay = allDay
                    ))
                } catch (_: Exception) {
                    error = "日期格式不正确，请使用 yyyy-MM-dd HH:mm"
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
