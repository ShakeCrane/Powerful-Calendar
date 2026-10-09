package dev.powerfulcalendar

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.EventStore
import dev.powerfulcalendar.data.Priority
import dev.powerfulcalendar.interop.IcsCodec
import dev.powerfulcalendar.interop.SystemCalendar
import dev.powerfulcalendar.interop.TextParser
import dev.powerfulcalendar.reminders.ReminderScheduler
import dev.powerfulcalendar.ui.DayPanel
import dev.powerfulcalendar.ui.EventEditor
import dev.powerfulcalendar.ui.MonthPanel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class Page(val title: String) {
    TODAY("今日"), CALENDAR("日历"), ALARMS("闹钟"), MORE("工具")
}

class MainActivity : ComponentActivity() {
    private val store by lazy { EventStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
                    darkColorScheme() else lightColorScheme()
            ) { PowerfulCalendarApp() }
        }
    }

    private fun tip(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun safeStart(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { tip("设备未安装可以处理该操作的应用") }
        catch (e: Exception) { tip("操作失败：" + e.message) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) store.close()
    }

    @Composable
    private fun PowerfulCalendarApp() {
        var page by remember { mutableStateOf(Page.TODAY) }
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var shownMonth by remember { mutableStateOf(YearMonth.now()) }
        var events by remember { mutableStateOf(store.all()) }
        var editor by remember { mutableStateOf<CalendarEvent?>(null) }
        var quickDialog by remember { mutableStateOf(false) }
        var quickText by remember { mutableStateOf("") }
        var parseNote by remember { mutableStateOf("") }
        var deleteCandidate by remember { mutableStateOf<CalendarEvent?>(null) }

        fun reload() { events = store.all() }
        fun save(event: CalendarEvent) {
            val id = store.upsert(event)
            ReminderScheduler.schedule(this, event.copy(id = id))
            reload()
            tip("日程已保存")
        }
        fun importEvents(imported: List<CalendarEvent>): Int {
            imported.forEach { event ->
                val id = store.upsert(event)
                ReminderScheduler.schedule(this, event.copy(id = id))
            }
            reload()
            return imported.size
        }
        fun importSystem() {
            try {
                val incoming = SystemCalendar.readUpcoming(this)
                tip("已读取并保存 " + importEvents(incoming) + " 条系统日历事件")
            } catch (e: Exception) { tip("读取系统日历失败：" + e.message) }
        }

        val notifications = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { allowed ->
            if (!allowed) tip("通知权限未授予，提醒可能无法显示")
        }
        val calendarPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { allowed ->
            if (allowed) importSystem() else tip("未授权读取系统日历")
        }
        val openIcs = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("无法读取文件")
                val parsed = IcsCodec.parse(text)
                val count = importEvents(parsed.events)
                tip("已导入 " + count + " 条事件，提示 " + parsed.warnings.size + " 条" +
                    if (parsed.warnings.isEmpty()) "" else "：" + parsed.warnings.take(2).joinToString("；"))
            } catch (e: Exception) { tip("导入失败：" + e.message) }
        }
        val exportIcs = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/calendar")
        ) { uri ->
            if (uri != null) try {
                contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                    it.write(IcsCodec.export(events))
                } ?: error("无法写入目标位置")
                tip("已导出 " + events.size + " 条事件")
            } catch (e: Exception) { tip("导出失败：" + e.message) }
        }
        val pickImage = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    val image = InputImage.fromFilePath(this, uri)
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            quickText = result.text
                            quickDialog = true
                            if (result.text.isBlank()) tip("未识别到文字，可手动输入")
                        }
                        .addOnFailureListener { error -> tip("OCR 失败：" + error.message) }
                        .addOnCompleteListener { recognizer.close() }
                } catch (e: Exception) { tip("图片读取失败：" + e.message) }
            }
        }
        fun newDraft(day: LocalDate): CalendarEvent {
            val start = day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return CalendarEvent(title = "", startMillis = start, endMillis = start + 3_600_000L)
        }

        Scaffold(
            topBar = {
                Surface(shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("Powerful Calendar", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Text(page.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
            floatingActionButton = {
                if (page == Page.TODAY || page == Page.CALENDAR) {
                    FloatingActionButton(onClick = {
                        editor = newDraft(if (page == Page.TODAY) LocalDate.now() else selectedDate)
                    }) { Icon(Icons.Default.Add, contentDescription = "新增事件") }
                }
            },
            bottomBar = {
                NavigationBar {
                    Page.entries.forEach { item ->
                        val icon = when (item) {
                            Page.TODAY -> Icons.Default.Today
                            Page.CALENDAR -> Icons.Default.CalendarMonth
                            Page.ALARMS -> Icons.Default.Alarm
                            Page.MORE -> Icons.Default.MoreHoriz
                        }
                        NavigationBarItem(
                            selected = page == item,
                            onClick = {
                                page = item
                                if (item == Page.TODAY) selectedDate = LocalDate.now()
                            },
                            icon = { Icon(icon, contentDescription = item.title) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (page) {
                    Page.TODAY -> {
                        val today = LocalDate.now()
                        val todays = events.filter { it.occursOn(today) }
                        Text("今天，专注重要的事", style = MaterialTheme.typography.headlineSmall)
                        Text(todays.size.toString() + " 项安排 · " +
                            todays.count { it.priority == Priority.URGENT || it.priority == Priority.HIGH } + " 项重点",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DayPanel(today, events, onEdit = { editor = it },
                            onDelete = { deleteCandidate = it },
                            onExport = { safeStart(SystemCalendar.createEventIntent(it)) })
                        HorizontalDivider()
                        OutlinedButton(onClick = { quickText = ""; quickDialog = true },
                            modifier = Modifier.fillMaxWidth()) { Text("智能文本创建") }
                    }
                    Page.CALENDAR -> {
                        MonthPanel(shownMonth, selectedDate, events,
                            onMonth = { shownMonth = it },
                            onSelect = { selectedDate = it })
                        DayPanel(selectedDate, events, onEdit = { editor = it },
                            onDelete = { deleteCandidate = it },
                            onExport = { safeStart(SystemCalendar.createEventIntent(it)) })
                    }
                    Page.ALARMS -> AlarmPage(
                        onCreate = { hour, minute, title ->
                            safeStart(SystemCalendar.setSystemAlarm(hour, minute, title))
                        },
                        onShow = { safeStart(SystemCalendar.showSystemAlarms()) }
                    )
                    Page.MORE -> {
                        Text("导入 · 智能创建 · 同步", style = MaterialTheme.typography.headlineSmall)
                        ToolButton("从系统日历读取", "读取未来一年与过去 30 天的系统事件") {
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CALENDAR)
                                == PackageManager.PERMISSION_GRANTED) importSystem()
                            else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        }
                        ToolButton("导入 ICS 日历文件", "适用于 Outlook、Google、Apple 导出的 .ics 文件") {
                            openIcs.launch(arrayOf("text/calendar", "text/*", "application/octet-stream"))
                        }
                        ToolButton("导出 ICS 日历文件", "备份并迁移本地事件") {
                            exportIcs.launch("PowerfulCalendar.ics")
                        }
                        ToolButton("从截图识别事件", "本地中文 OCR，识别后可校对") { pickImage.launch("image/*") }
                        ToolButton("从文字识别事件", "输入“明天下午3点开组会”等语句") {
                            quickText = ""; quickDialog = true
                        }
                        Text("说明：系统日历为手动导入快照，不是实时双向同步。重复规则及复杂邀请尚未支持。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (quickDialog) {
            AlertDialog(
                onDismissRequest = { quickDialog = false },
                title = { Text("自然语言 / 截图文本") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("识别结果须由你确认，不会自动写入日历。")
                        OutlinedTextField(quickText, { quickText = it },
                            modifier = Modifier.fillMaxWidth(), minLines = 3,
                            label = { Text("事件描述") })
                        if (parseNote.isNotBlank()) Text(parseNote)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (quickText.isBlank()) { parseNote = "请填写事件描述"; return@TextButton }
                        val parsed = TextParser.parse(quickText)
                        editor = parsed.event.copy(notes = "原始文本：" + quickText)
                        parseNote = parsed.note
                        quickDialog = false
                    }) { Text("解析并编辑") }
                },
                dismissButton = { TextButton(onClick = { quickDialog = false }) { Text("取消") } }
            )
        }
        editor?.let { draft ->
            EventEditor(draft, onDismiss = { editor = null }, onSave = {
                save(it)
                if (Build.VERSION.SDK_INT >= 33 && it.reminderMinutes >= 0 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                editor = null
            })
        }
        deleteCandidate?.let { candidate ->
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                title = { Text("删除事件？") },
                text = { Text("将从 Powerful Calendar 本地删除「" + candidate.title + "」，不会修改外部日历。") },
                confirmButton = {
                    TextButton(onClick = {
                        ReminderScheduler.cancel(this, candidate.id)
                        store.delete(candidate.id)
                        reload()
                        deleteCandidate = null
                    }) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("取消") } }
            )
        }
    }
}

@Composable
private fun ToolButton(title: String, description: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlarmPage(onCreate: (Int, Int, String) -> Unit, onShow: () -> Unit) {
    var time by remember { mutableStateOf("07:30") }
    var title by remember { mutableStateOf("起床") }
    var error by remember { mutableStateOf("") }
    Text("系统闹钟", style = MaterialTheme.typography.headlineSmall)
    Text("通过 Android 标准闹钟接口交给系统时钟应用创建，避免依赖后台持续运行。",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(time, { time = it }, label = { Text("闹钟时间 HH:mm") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(title, { title = it }, label = { Text("闹钟名称") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(time.trim())
                val hour = match?.groupValues?.get(1)?.toIntOrNull()
                val minute = match?.groupValues?.get(2)?.toIntOrNull()
                if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                    error = "请输入合法时间，例如 07:30"
                } else {
                    error = ""
                    onCreate(hour, minute, title)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("交给系统创建闹钟") }
            OutlinedButton(onClick = onShow, modifier = Modifier.fillMaxWidth()) { Text("打开系统闹钟列表") }
        }
    }
    Text("Android 未提供读取所有厂商闹钟列表的通用公开接口。App 自带的日程提醒位于“今日”和“日历”事件中。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
