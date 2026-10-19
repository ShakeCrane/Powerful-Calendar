package dev.powerfulcalendar.ui.v2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiClient
import dev.powerfulcalendar.ai.AiDraftService
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.EventStore
import dev.powerfulcalendar.data.LegacyImporter
import dev.powerfulcalendar.data.Priority
import dev.powerfulcalendar.interop.IcsCodec
import dev.powerfulcalendar.interop.SystemCalendar
import dev.powerfulcalendar.interop.TextParser
import dev.powerfulcalendar.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Process-wide store: alarms, boot receivers and the UI must agree on one database and one token. */
object PowerCalStores {
    @Volatile
    private var instance: PowerStore? = null

    fun get(context: Context): PowerStore = instance ?: synchronized(this) {
        instance ?: PowerStore(context.applicationContext).also { instance = it }
    }
}

private enum class Page { TIMELINE, TOOLS }

private data class AppData(
    val records: List<TemporalRecord>,
    val pending: Int,
    val locked: Boolean,
    val session: PowerStore.AiSession?
)

@Composable
fun PowerCalApp(store: PowerStore) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val zone = remember { ZoneId.systemDefault() }
    val scope = rememberCoroutineScope()
    val controller = remember(store) { DeckController(StoreDeckPersistence(store)) }
    val effects = LocalGlassEffects.current

    var page by rememberSaveable { mutableStateOf(Page.TIMELINE.name) }
    var records by remember { mutableStateOf<List<TemporalRecord>>(emptyList()) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var aiLocked by remember { mutableStateOf(false) }
    var aiSession by remember { mutableStateOf<PowerStore.AiSession?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }
    var deckOpen by rememberSaveable { mutableStateOf(false) }
    var deckInitialId by rememberSaveable { mutableStateOf<Long?>(null) }
    var textDialogOpen by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var confirmBatch by remember { mutableStateOf<AiBatchRunner.Mode?>(null) }
    var confirmingLegacy by remember { mutableStateOf(false) }

    val initialCamera = remember { TimelineCoordinate.initialCamera(System.currentTimeMillis(), zone) }
    var anchorMillis by rememberSaveable { mutableLongStateOf(initialCamera.anchorMillis) }
    var hours by rememberSaveable { mutableDoubleStateOf(initialCamera.hours) }

    val camera = TimelineCoordinate.Camera(anchorMillis, TimelineCoordinate.clampHours(hours))

    suspend fun reload() {
        val data = withContext(Dispatchers.IO) {
            AppData(
                records = store.all(),
                pending = store.pendingCount(),
                locked = store.isLocked(),
                session = store.currentAiSession()
            )
        }
        records = data.records
        pendingCount = data.pending
        aiLocked = data.locked
        aiSession = data.session
    }

    suspend fun reconcile() {
        withContext(Dispatchers.IO) { ReminderScheduler.reconcile(appContext, store) }
    }

    suspend fun openDeckAt(draftId: Long?) {
        withContext(Dispatchers.IO) { controller.open(draftId) }
        deckInitialId = draftId
        deckOpen = true
    }

    LaunchedEffect(Unit) {
        reload()
        reconcile()
        // A process that died mid-batch is fenced and unlocked here; never leave a permanent lock.
        if (store.isLocked()) {
            withContext(Dispatchers.IO) { store.recoverInterruptedAi() }
            reload()
        }
    }

    LaunchedEffect(deckOpen, deckInitialId) {
        if (deckOpen) {
            withContext(Dispatchers.IO) { controller.open(deckInitialId) }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(20_000)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(5000)
            message = null
        }
    }

    suspend fun createDraftFromText(text: String, source: String) {
        val parsed = TextParser.parse(text, java.time.LocalDateTime.now(zone))
        val event = parsed.event
        val form = DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = event.title,
            startText = DraftForm.formatInstant(event.startMillis, zone),
            endText = DraftForm.formatInstant(event.endMillis, zone),
            notes = ("原始文本：" + text.trim()).take(800),
            source = source,
            zoneId = zone.id
        )
        val card = withContext(Dispatchers.IO) {
            store.newDraft(form, actor = "user")
        }
        reload()
        openDeckAt(card.draftId)
        message = parsed.note
    }

    // ------------------------------------------------------------------ import launchers

    suspend fun importIcs(uri: Uri) {
        try {
            val text = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            } ?: error("无法读取所选文件")
            val parsed = IcsCodec.parse(text)
            if (parsed.events.isEmpty()) {
                message = "文件里没有可导入的事件"
                return
            }
            val batchId = withContext(Dispatchers.IO) { store.beginMigration("ics") }
            var imported = 0
            var skipped = 0
            withContext(Dispatchers.IO) {
                parsed.events.forEach { event ->
                    val record = event.toRecord()
                    val external = event.externalId ?: UUID.randomUUID().toString()
                    when (store.importStructured(record, external, batchId, "ics")) {
                        is PowerStore.ImportOutcome.Imported -> imported++
                        else -> skipped++
                    }
                }
                store.finishMigration(batchId, parsed.warnings.take(30))
            }
            reload()
            reconcile()
            message = "ICS 导入 $imported 条" +
                (if (skipped > 0) "，跳过 $skipped 条重复或无效记录" else "") +
                (if (parsed.warnings.isEmpty()) "" else "；${parsed.warnings.first()}")
        } catch (error: Exception) {
            message = "ICS 导入失败：" + (error.message ?: "未知错误")
        }
    }

    suspend fun importSystemCalendar() {
        try {
            val incoming = withContext(Dispatchers.IO) { SystemCalendar.readUpcoming(appContext) }
            if (incoming.isEmpty()) {
                message = "系统日历没有可导入的事件"
                return
            }
            val batchId = withContext(Dispatchers.IO) { store.beginMigration("android-calendar") }
            var imported = 0
            withContext(Dispatchers.IO) {
                incoming.forEach { event ->
                    val external = event.externalId ?: ("cal:" + event.startMillis + ":" + event.title)
                    if (store.importStructured(
                            event.toRecord(),
                            external,
                            batchId,
                            "android-calendar"
                        ) is PowerStore.ImportOutcome.Imported
                    ) imported++
                }
                store.finishMigration(batchId)
            }
            reload()
            reconcile()
            message = "已从系统日历导入 $imported 条（一次性快照，不是双向同步）"
        } catch (error: Exception) {
            message = "读取系统日历失败：" + (error.message ?: "未知错误")
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scope.launch { importSystemCalendar() }
        else message = "未授权读取系统日历"
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) message = "没有通知权限时，提醒会被记录为 BLOCKED，而不会假装已提醒"
    }
    val openIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) scope.launch { importIcs(uri) } }
    val exportIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) scope.launch {
            val body = withContext(Dispatchers.IO) {
                IcsCodec.export(
                    records.mapNotNull { record ->
                        if (record.kind != TemporalKind.ACTIVITY) return@mapNotNull null
                        CalendarEvent(
                            id = record.eventSeq,
                            title = record.title,
                            startMillis = record.startMillis ?: return@mapNotNull null,
                            endMillis = record.endMillis ?: return@mapNotNull null,
                            notes = record.notes,
                            location = record.location,
                            reminderMinutes = record.reminderMinutes,
                            source = "powercal",
                            externalId = "powercal-" + record.eventSeq + "@local"
                        )
                    }
                )
            }
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                    it.write(body)
                }
            }.onSuccess {
                message = "已导出活动类事件（闹钟与截止不进入 ICS，因为该格式会丢失它们的语义）"
            }.onFailure { message = "导出失败：" + it.message }
        }
    }
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { store.exportBackup().toString(2) }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                        it.write(json)
                    } ?: error("无法写入目标文件")
                }
            }.onSuccess { message = "原生备份已导出（不含密钥与 Android 调度句柄）" }
                .onFailure { message = "备份失败：" + it.message }
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } ?: error("无法读取备份文件")
                val report = withContext(Dispatchers.IO) {
                    store.restoreBackup(JSONObject(text))
                }
                reload()
                reconcile()
                message = "已恢复 ${report.objects} 个事件、${report.drafts} 张草稿；调度已重建，旧授权未恢复"
            }.onFailure { message = "恢复失败：" + it.message }
        }
    }
    val ocrPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (image == null) {
                message = "无法读取图片"
                recognizer.close()
            } else {
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (result.text.isBlank()) {
                            message = "没有识别到文字，可以手动输入"
                        } else {
                            scope.launch { createDraftFromText(result.text, "ocr") }
                        }
                    }
                    .addOnFailureListener { message = "OCR 失败：" + it.message }
                    .addOnCompleteListener { recognizer.close() }
            }
        }
    }

    val tools = page == Page.TOOLS.name

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (tools) {
            ToolsPage(
                store = store,
                records = records,
                pendingCount = pendingCount,
                aiLocked = aiLocked,
                aiSession = aiSession,
                effects = effects,
                onBack = {
                    page = Page.TIMELINE.name
                    scope.launch { reload() }
                },
                onOpenDeck = { scope.launch { openDeckAt(null) } },
                onImportIcs = {
                    openIcs.launch(arrayOf("text/calendar", "text/*", "application/octet-stream"))
                },
                onExportIcs = {
                    exportIcs.launch("PowerfulCalendar-activities.ics")
                },
                onImportSystem = {
                    if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        scope.launch { importSystemCalendar() }
                    } else {
                        calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    }
                },
                onMigrateLegacy = { confirmingLegacy = true },
                onPickImage = { ocrPicker.launch("image/*") },
                onTextDraft = { textDialogOpen = true },
                onExportBackup = { createBackup.launch("powercal-backup.json") },
                onRestoreBackup = { restoreBackup.launch(arrayOf("application/json", "*/*")) },
                onRequestNotificationPermission = {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenExactAlarmSettings = {
                    ReminderScheduler.openExactAlarmSettings(appContext)
                },
                onRunBatch = { mode -> confirmBatch = mode },
                onCancelBatch = {
                    scope.launch {
                        val session = aiSession
                        if (session != null) {
                            withContext(Dispatchers.IO) { store.requestAiCancel(session.sessionId) }
                            reload()
                            message = "已请求停止；已完成的结果保留，剩余卡片继续等待你"
                        }
                    }
                },
                onReconcile = {
                    scope.launch {
                        reconcile()
                        reload()
                        message = "已按当前数据重建提醒调度"
                    }
                },
                onAiDraft = { text ->
                    aiBusy = true
                    scope.launch {
                        try {
                            val settings = AiSettingsStore.load(appContext)
                            if (!settings.configured) {
                                message = "请先配置 AI 端点、模型与密钥"
                                return@launch
                            }
                            val result = AiClient(settings, zone).interpret(text, records)
                            val created = withContext(Dispatchers.IO) {
                                AiDraftService(store).createDrafts(result)
                            }
                            reload()
                            if (created.draftIds.isEmpty()) {
                                message = result.warnings.firstOrNull() ?: "AI 没有返回可用的结构化草稿"
                            } else {
                                openDeckAt(created.draftIds.first())
                                message = "AI 生成了 ${created.draftIds.size} 张待确认草稿"
                            }
                        } finally {
                            aiBusy = false
                        }
                    }
                },
                aiBusy = aiBusy
            )
        } else {
            TimelineScreen(
                records = records,
                camera = camera,
                nowMillis = nowMillis,
                zone = zone,
                pendingCount = pendingCount,
                aiLocked = aiLocked,
                onCameraChange = { updated ->
                    anchorMillis = updated.anchorMillis
                    hours = updated.hours
                },
                onOpenEvent = { record ->
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                store.pendingDraftsFor(record.eventSeq).firstOrNull {
                                    it.operation == DraftOperation.REPLACE ||
                                        it.operation == DraftOperation.CONVERT
                                } ?: store.proposeReplace(record.eventSeq)
                            }
                        }.onSuccess { card ->
                            reload()
                            openDeckAt(card.draftId)
                        }.onFailure {
                            message = it.message ?: "无法打开事件编辑"
                        }
                    }
                },
                onMoveEvent = { record, target ->
                    scope.launch {
                        val moved = withContext(Dispatchers.IO) {
                            val base = DraftForm.from(record)
                            val form = when (record.kind) {
                                TemporalKind.ALARM -> base.copy(
                                    startText = DraftForm.formatInstant(target, zone)
                                )
                                TemporalKind.ACTIVITY -> {
                                    val duration = (record.endMillis ?: target) -
                                        (record.startMillis ?: target)
                                    base.copy(
                                        startText = DraftForm.formatInstant(target, zone),
                                        endText = DraftForm.formatInstant(target + duration, zone)
                                    )
                                }
                                TemporalKind.DEADLINE -> if (record.isDateOnlyDeadline()) {
                                    base.copy(
                                        endText = Instant.ofEpochMilli(target)
                                            .atZone(zone).toLocalDate().toString()
                                    )
                                } else {
                                    base.copy(
                                        endText = DraftForm.formatInstant(target, zone)
                                    )
                                }
                            }
                            store.newDraft(form, record.eventSeq, DraftOperation.REPLACE, actor = "user")
                        }
                        reload()
                        openDeckAt(moved.draftId)
                    }
                },
                onCreate = { kind, millis ->
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                store.newDraft(
                                    DraftForm.atInstant(kind, millis, zone),
                                    actor = "user"
                                )
                            }
                        }.onSuccess { card ->
                            reload()
                            openDeckAt(card.draftId)
                        }.onFailure {
                            message = it.message ?: "无法创建草稿"
                        }
                    }
                },
                onToday = {
                    nowMillis = System.currentTimeMillis()
                    anchorMillis = nowMillis
                },
                onOpenDeck = { scope.launch { openDeckAt(null) } },
                onOpenTools = {
                    page = Page.TOOLS.name
                    scope.launch { reload() }
                }
            )
        }

        message?.let { text ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                shape = GlassShapes.chip,
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }

    if (deckOpen) {
        DraftDeckDialog(
            controller = controller,
            aiSession = aiSession,
            onDismissRequest = {
                deckOpen = false
                scope.launch {
                    reload()
                    reconcile()
                }
            },
            onOpenFormalEvent = { record ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            store.pendingDraftsFor(record.eventSeq).firstOrNull()
                                ?: store.proposeReplace(record.eventSeq)
                        }
                    }.onSuccess { card ->
                        deckOpen = false
                        reload()
                        openDeckAt(card.draftId)
                    }.onFailure {
                        message = it.message ?: "无法打开事件编辑"
                    }
                }
            },
            onCancelAiBatch = {
                scope.launch {
                    val session = aiSession
                    if (session != null) {
                        withContext(Dispatchers.IO) { store.requestAiCancel(session.sessionId) }
                        reload()
                        message = "已请求停止 AI 批次"
                    }
                }
            },
            onDeckChanged = {
                scope.launch {
                    reload()
                    reconcile()
                }
            },
            formalSearch = { query ->
                if (query.isBlank()) emptyList() else records.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.eventSeq.toString().contains(query) ||
                        TemporalRecord.formatSeq(it.eventSeq).contains(query)
                }.take(8)
            }
        )
    }

    if (textDialogOpen) {
        TextDraftDialog(
            onDismiss = { textDialogOpen = false },
            onConfirm = { text ->
                textDialogOpen = false
                scope.launch { createDraftFromText(text, "text") }
            }
        )
    }

    confirmBatch?.let { mode ->
        val label = if (mode == AiBatchRunner.Mode.PUBLISH) "发布" else "丢弃"
        AlertDialog(
            onDismissRequest = { confirmBatch = null },
            title = { Text("授权 AI 批量$label？") },
            text = {
                Text(
                    "将处理当前 $pendingCount 张待处理卡片。期间整个卡组对你是只读的，" +
                        "时间轴仍可查看；你可以随时停止。已成功的提交会保留，" +
                        "有版本冲突的卡片会留给你确认，不会被 AI 静默覆盖。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBatch = null
                    scope.launch {
                        val pending = withContext(Dispatchers.IO) { store.pendingDrafts() }
                        if (pending.isEmpty()) {
                            message = "没有待处理卡片"
                            return@launch
                        }
                        val session = runCatching {
                            withContext(Dispatchers.IO) {
                                store.beginAiBatch(
                                    pending.map { it.draftId }.toSet(),
                                    DraftOperation.entries.toSet()
                                )
                            }
                        }.getOrElse {
                            message = "无法开始批次：" + (it.message ?: "")
                            return@launch
                        }
                        reload()
                        val report = AiBatchRunner(store).run(session, mode)
                        reload()
                        withContext(Dispatchers.IO) { controller.refresh() }
                        message = buildString {
                            append("AI 批次结束：")
                            if (mode == AiBatchRunner.Mode.PUBLISH) {
                                append("发布 ${report.published} 张")
                            } else {
                                append("丢弃 ${report.discarded} 张")
                            }
                            append("，保留 ${report.kept} 张")
                            if (report.cancelled) append("（已停止）")
                            if (report.failures.isNotEmpty()) append("；").append(report.failures.first())
                        }
                        reconcile()
                    }
                }) { Text("授权并开始") }
            },
            dismissButton = { TextButton(onClick = { confirmBatch = null }) { Text("取消") } }
        )
    }

    if (confirmingLegacy) {
        AlertDialog(
            onDismissRequest = { confirmingLegacy = false },
            title = { Text("迁移旧版事件？") },
            text = {
                Text(
                    "旧版数据库只会被只读打开，不会被删除或修改。每条旧事件会获得新的永久序号，" +
                        "并标记来源为旧版迁移；重复执行不会产生重复记录。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingLegacy = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            LegacyImporter.migrate(appContext, store)
                        }
                        reload()
                        message = if (!result.available) {
                            result.warnings.firstOrNull() ?: "没有可迁移的旧数据"
                        } else {
                            "旧版迁移完成：导入 ${result.imported} 条，跳过 ${result.skipped} 条"
                        }
                    }
                }) { Text("开始迁移") }
            },
            dismissButton = { TextButton(onClick = { confirmingLegacy = false }) { Text("取消") } }
        )
    }
}

private fun CalendarEvent.toRecord(): TemporalRecord = TemporalRecord(
    eventSeq = 0L,
    revision = 0L,
    kind = TemporalKind.ACTIVITY,
    title = title,
    notes = notes,
    location = location,
    startMillis = startMillis,
    endMillis = endMillis.coerceAtLeast(startMillis + 60_000L),
    reminderMinutes = reminderMinutes,
    enabled = reminderMinutes >= 0,
    source = source
)

@Composable
private fun TextDraftDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用文字创建草稿") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "本地解析器先给出一个可编辑草稿；不会直接写入时间轴。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    label = { Text("例如：明天下午 3 点开组会") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("生成草稿")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ToolsPage(
    store: PowerStore,
    records: List<TemporalRecord>,
    pendingCount: Int,
    aiLocked: Boolean,
    aiSession: PowerStore.AiSession?,
    effects: GlassEffects,
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
    onAiDraft: (String) -> Unit,
    aiBusy: Boolean
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var settings by remember { mutableStateOf(AiSettingsStore.load(appContext)) }
    var aiText by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回时间轴") }
            Text("工具与设置", style = MaterialTheme.typography.headlineSmall)
        }

        SectionCard("待处理", "$pendingCount 张草稿") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDeck, modifier = Modifier.weight(1f)) {
                    Text("打开待处理")
                }
                OutlinedButton(
                    onClick = { onRunBatch(AiBatchRunner.Mode.PUBLISH) },
                    modifier = Modifier.weight(1f),
                    enabled = pendingCount > 0 && !aiLocked
                ) { Text("AI 批量发布") }
                OutlinedButton(
                    onClick = { onRunBatch(AiBatchRunner.Mode.DISCARD) },
                    modifier = Modifier.weight(1f),
                    enabled = pendingCount > 0 && !aiLocked
                ) { Text("AI 批量丢弃") }
            }
            if (aiLocked) {
                Text(
                    "AI 批次正在运行：卡组只读。可以停止，已提交结果会保留。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                TextButton(onClick = onCancelBatch) { Text("停止当前批次") }
            }
            aiSession?.report?.let { report ->
                HorizontalDivider()
                Text("上一次批次报告", style = MaterialTheme.typography.labelMedium)
                Text(report, style = MaterialTheme.typography.labelSmall)
            }
        }

        SectionCard(
            "单一 AI",
            if (settings.configured) "已配置 · 模型 ${settings.model}" else "未配置"
        ) {
            Text(
                "PowerCal 只有一个 AI。它只生成待确认草稿，或在一次显式授权的批次内发布/丢弃选中的卡片。" +
                    "密钥保存在应用私有存储，不进入备份，也不会写入日志。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            HorizontalDivider()
            OutlinedTextField(
                value = aiText,
                onValueChange = { aiText = it },
                label = { Text("用自然语言描述，例如：把周五的组会改到 16:00") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
            )
            Button(
                onClick = { onAiDraft(aiText) },
                enabled = !aiBusy && aiText.isNotBlank() && settings.configured
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (aiBusy) "解析中…" else "生成待确认草稿")
            }
        }

        SectionCard("导入", "结构化数据严格校验后可直接发布") {
            ToolRow("从系统日历一次性导入", "读取未来一年与过去 30 天，不是双向同步", onImportSystem)
            ToolRow("导入 ICS 文件", "支持折叠行、TZID 与转义；不展开 RRULE", onImportIcs)
            ToolRow("从旧版 PowerCal 迁移", "只读打开旧数据库，保留原文件", onMigrateLegacy)
            ToolRow("从截图识别", "本地中文 OCR，识别结果为待确认草稿", onPickImage)
            ToolRow("用文字创建草稿", "离线解析器，不需要联网", onTextDraft)
        }

        SectionCard("备份与导出", "原生备份优先于有损格式") {
            ToolRow("导出原生备份", "包含序号水位、草稿顺序与提交历史；不含密钥", onExportBackup)
            ToolRow("从备份恢复", "只允许恢复到空数据库，避免覆盖现有数据", onRestoreBackup)
            ToolRow("导出 ICS", "仅活动类事件；闹钟与截止的语义会被该格式丢失", onExportIcs)
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

        SectionCard("诊断", "只读") {
            StatusLine("正式事件", "${records.size} 条", false)
            StatusLine("下一个事件序号", TemporalRecord.formatSeq(store.nextSequencePreview()), false)
            StatusLine("已分配序号", "${store.identityCount()} 个（含退役）", false)
            StatusLine("数据库版本", "v${PowerStore.VERSION} · ${PowerStore.DATABASE_NAME}", false)
            StatusLine("视觉效果", if (effects.blur) "玻璃效果开启" else "低端设备降级", false)
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

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = GlassShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
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
private fun ToolRow(title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = GlassShapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (warn) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            null,
            modifier = Modifier.size(15.dp),
            tint = if (warn) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
