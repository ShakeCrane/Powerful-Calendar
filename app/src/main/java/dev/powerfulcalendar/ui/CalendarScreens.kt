package dev.powerfulcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.Priority
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("M月d日 · EEEE")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val palette = mapOf(
    Priority.URGENT to Color(0xFFBB4148),
    Priority.HIGH to Color(0xFFD18639),
    Priority.NORMAL to Color(0xFF4779B2),
    Priority.LOW to Color(0xFF6E8F7A)
)

@Composable
fun MonthPanel(
    month: YearMonth,
    selected: LocalDate,
    events: List<CalendarEvent>,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(month.year.toString() + "年 " + month.monthValue + "月",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹ 上月") }
                TextButton(onClick = { onMonth(month.plusMonths(1)) }) { Text("下月 ›") }
            }
            Row {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val firstDayOffset = month.atDay(1).dayOfWeek.value - 1
            val cellCount = ((firstDayOffset + month.lengthOfMonth() + 6) / 7) * 7
            val cells = (0 until cellCount).map {
                val number = it - firstDayOffset + 1
                if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        Column(
                            modifier = Modifier.weight(1f).height(55.dp)
                                .then(if (date != null) Modifier.clickable { onSelect(date) } else Modifier)
                                .background(
                                    if (date == selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                ).padding(top = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (date != null) {
                                Text(date.dayOfMonth.toString(),
                                    fontWeight = if (date == LocalDate.now() || date == selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                                val categories = events.filter { it.occursOn(date) }.map { it.priority }.distinct().take(3)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    categories.forEach { priority ->
                                        Box(Modifier.padding(top = 4.dp).size(5.dp).background(palette.getValue(priority), RoundedCornerShape(4.dp)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayPanel(
    day: LocalDate,
    events: List<CalendarEvent>,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (CalendarEvent) -> Unit,
    onExport: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayEvents = events.filter { it.occursOn(day) }.sortedWith(
        compareBy<CalendarEvent> { it.startMillis }.thenBy { it.priority.ordinal }
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day.format(dateFmt), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(dayEvents.size.toString() + " 项", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dayEvents.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("这一天还没有安排", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("点击右下角的 +，创建第一条日程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            dayEvents.forEach { event ->
                EventCard(event, onEdit = { onEdit(event) }, onDelete = { onDelete(event) },
                    onExport = { onExport(event) })
            }
        }
    }
}

@Composable
fun EventCard(
    event: CalendarEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zone).format(timeFmt)
    val end = Instant.ofEpochMilli(event.endMillis).atZone(zone).format(timeFmt)
    val priorityColor = palette.getValue(event.priority)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(priorityColor))
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (event.allDay) "全天" else start + " — " + end,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(5.dp), color = priorityColor.copy(alpha = 0.12f)) {
                        Text(event.priority.label, color = priorityColor,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (event.location.isNotBlank()) Text("地点 · " + event.location, style = MaterialTheme.typography.bodySmall)
                if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                val origin = when(event.source) {
                    "local" -> "本地"
                    "ics" -> "ICS 导入"
                    "android-calendar" -> "系统日历"
                    else -> event.source
                }
                Text(origin + if (event.reminderMinutes >= 0) "  ·  提前 " + event.reminderMinutes + " 分钟提醒" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑事件", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "保存到系统日历", modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除事件", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
