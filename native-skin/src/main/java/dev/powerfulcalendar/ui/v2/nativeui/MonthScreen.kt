package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeMonthAppearance
import dev.powerfulcalendar.ui.v2.NativeMonthDialog
import dev.powerfulcalendar.ui.v2.NativeMonthRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun NativeMonthScreen(
    initialDate: LocalDate,
    today: LocalDate,
    aiLocked: Boolean,
    rowsForMonth: (YearMonth) -> List<NativeMonthRow>,
    onBack: () -> Unit, onDate: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
    onCreate: (LocalDate) -> Unit,
    appearance: NativeMonthAppearance
) {
    var selectedText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(initialDate).toString()) }
    val selected = LocalDate.parse(selectedText)
    val month = YearMonth.parse(monthText)
    var yearPickerOpen by remember { mutableStateOf(false) }
    var monthPickerOpen by remember { mutableStateOf(false) }
    var yearInput by remember(monthText) { mutableStateOf(month.year.toString()) }
    fun showMonth(target: YearMonth) {
        require(target.year in 1900..2100)
        monthText = target.toString()
        selectedText = target.atDay(1).toString()
    }
    val entries = remember(month, rowsForMonth) { rowsForMonth(month) }
    val daily = remember(entries, month) {
        (1..month.lengthOfMonth()).associate { day ->
            val date = month.atDay(day)
            date to entries.filter { date.toString() in it.visibleDateKeys }
        }
    }
    var swipeDistance by remember { mutableFloatStateOf(0f) }
    LazyColumn(
        Modifier.fillMaxSize()
            .pointerInput(monthText) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeDistance = 0f },
                    onHorizontalDrag = { change, drag ->
                        swipeDistance += drag
                        change.consume()
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(swipeDistance) > 72.dp.toPx()) {
                            val delta = if (swipeDistance < 0f) 1L else -1L
                            val next = month.plusMonths(delta)
                            if (next.year in 1900..2100) showMonth(next)
                        }
                        swipeDistance = 0f
                    },
                    onDragCancel = { swipeDistance = 0f }
                )
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回时间轴，保留原视角") }
                Text("日历", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { selectedText = today.toString(); monthText = YearMonth.from(today).toString() }) { Text("今天") }
            }
        }
        item {
            appearance.panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString(); selectedText = month.minusMonths(1).atDay(1).toString() }) { Icon(Icons.Default.ChevronLeft, "上个月") }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { yearPickerOpen = true }) {
                            Text("${month.year} 年", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { monthPickerOpen = true }) {
                            Text("${month.monthValue} 月", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    IconButton(onClick = { monthText = month.plusMonths(1).toString(); selectedText = month.plusMonths(1).atDay(1).toString() }) { Icon(Icons.Default.ChevronRight, "下个月") }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                Column(Modifier.align(Alignment.Center).fillMaxWidth().widthIn(max = 336.dp)) {
                Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f).padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) } }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
                repeat(cells / 7) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { weekday ->
                            val number = week * 7 + weekday - offset + 1
                            val date = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                            if (date == null) Spacer(Modifier.weight(1f).height(52.dp)) else {
                                val count = daily[date].orEmpty().size
                                Surface(
                                    onClick = { selectedText = date.toString() },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).semantics {
                                        contentDescription = "${date}，$count 项${if (date == today) "，今天" else ""}"
                                        this.selected = date == selected
                                    },
                                    shape = RoundedCornerShape(appearance.gridCellCornerRadiusDp.dp),
                                    color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    border = if (date == today) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(number.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                                        Text(if (count > 0) "$count 项" else "", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                }
                // The day grid fits the viewport; swiping changes MONTH, never scrolls a cropped grid.
                }
            }
        }
        item {
            Column {
                Text(selected.format(DateTimeFormatter.ofPattern("M月d日 EEEE")), style = MaterialTheme.typography.titleMedium)
            }
        }
        val selectedEntries = daily[selected].orEmpty()
        if (selectedEntries.isEmpty()) item { Text("这一天还很从容。", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 16.dp)) }
        items(selectedEntries, key = { it.rowKey }) { entry ->
            Surface(onClick = {
                onOpen(entry.rowKey)
            }, shape = RoundedCornerShape(appearance.eventRowCornerRadiusDp.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp)) {
                    Text("${entry.kindLabel} · ${entry.title}", style = MaterialTheme.typography.titleMedium, color = Color(appearance.kindColors.getValue(entry.kind)))
                    Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                    Text(entry.sequenceLabel + if (aiLocked) " · AI 处理中，仅查看" else "", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { OutlinedButton(onClick = { onCreate(selected) }, enabled = !aiLocked, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Text("为这一天新建活动") } }
        item {
            TextButton(onClick = { onDate(selected) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("在时间轴查看这一天")
            }
        }
    }
    if (yearPickerOpen) {
        val year = yearInput.toIntOrNull()
        appearance.showDialog(NativeMonthDialog(
            onDismissRequest = { yearPickerOpen = false },
            title = { Text("选择年份") },
            text = {
                OutlinedTextField(
                    value = yearInput,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) yearInput = it },
                    label = { Text("1900～2100") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (year != null && year in 1900..2100) showMonth(month.withYear(year))
                        yearPickerOpen = false
                    },
                    enabled = year != null && year in 1900..2100
                ) { Text("跳转") }
            },
            dismissButton = { TextButton(onClick = { yearPickerOpen = false }) { Text("取消") } }
        ))
    }
    if (monthPickerOpen) {
        appearance.showDialog(NativeMonthDialog(
            onDismissRequest = { monthPickerOpen = false },
            title = { Text("选择月份 · ${month.year} 年") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            repeat(3) { column ->
                                val value = row * 3 + column + 1
                                TextButton(onClick = {
                                    showMonth(YearMonth.of(month.year, value))
                                    monthPickerOpen = false
                                }) { Text("${value} 月") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { monthPickerOpen = false }) { Text("关闭") } }
        ))
    }
}
