package dev.powerfulcalendar.ui.v2

import android.Manifest
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.*
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiClient
import dev.powerfulcalendar.ai.AiDraftService
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
import dev.powerfulcalendar.data.LegacyImporter
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

    /**
     * Batch work outlives the composition that started it. A rotation or a dismissed dialog must
     * not cancel a batch mid-flight and leave the deck locked behind a dead coroutine.
     */
    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    fun get(context: Context): PowerStore = instance ?: synchronized(this) {
        instance ?: PowerStore(context.applicationContext).also { instance = it }
    }
}

private enum class Page { TIMELINE, MONTH, TOOLS }

private data class BatchReview(val mode: AiBatchRunner.Mode, val cards: List<dev.powerfulcalendar.core.DraftCard>)

internal data class ReminderDisplay(val record: TemporalRecord, val intent: PowerStore.ScheduleIntent?, val execution: String?)
internal data class UiDiagnostics(val nextSequence: Long = 1, val identities: Long = 0, val reminders: List<ReminderDisplay> = emptyList())

private data class AppData(
    val records: List<TemporalRecord>,
    val pending: Int,
    val locked: Boolean,
    val session: PowerStore.AiSession?,
    val cards: List<dev.powerfulcalendar.core.DraftCard>,
    val diagnostics: UiDiagnostics
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
    var confirmBatch by remember { mutableStateOf<BatchReview?>(null) }
    var pendingCards by remember { mutableStateOf<List<dev.powerfulcalendar.core.DraftCard>>(emptyList()) }
    var diagnostics by remember { mutableStateOf(UiDiagnostics()) }
    var monthInspection by remember { mutableStateOf<TemporalRecord?>(null) }
    var confirmingLegacy by remember { mutableStateOf(false) }

    val initialCamera = remember { TimelineCoordinate.initialCamera(System.currentTimeMillis(), zone) }
    var anchorMillis by rememberSaveable { mutableLongStateOf(initialCamera.anchorMillis) }
    var hours by rememberSaveable { mutableDoubleStateOf(initialCamera.hours) }

    val camera = TimelineCoordinate.Camera(anchorMillis, TimelineCoordinate.clampHours(hours))

    suspend fun reload() {
        val data = withContext(Dispatchers.IO) {
            val pending = store.pendingDrafts()
            val formal = store.all()
            AppData(
                records = formal,
                pending = pending.size,
                locked = store.isLocked(),
                session = store.currentAiSession(),
                cards = pending,
                diagnostics = UiDiagnostics(store.nextSequencePreview(), store.identityCount(), formal.filter { it.enabled && it.reminderMinutes >= 0 }.take(40).map {
                    ReminderDisplay(it, store.intentFor(it.eventSeq), store.lastExecution(it.eventSeq))
                })
            )
        }
        withContext(Dispatchers.Main) {
            records = data.records
            pendingCount = data.pending
            aiLocked = data.locked
            aiSession = data.session
            pendingCards = data.cards
            diagnostics = data.diagnostics
        }
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
        reconcile()
        reload()
        if (deckOpen) withContext(Dispatchers.IO) { controller.open(deckInitialId) }
        // A process that died mid-batch is fenced and unlocked here; never leave a permanent lock.
        val locked = withContext(Dispatchers.IO) { store.isLocked() }
        if (locked) {
            withContext(Dispatchers.IO) { store.recoverInterruptedAi() }
            reload()
        }
    }

    LaunchedEffect(aiLocked) {
        withContext(Dispatchers.IO) { controller.refresh() }
        while (aiLocked) {
            delay(750)
            reload()
            withContext(Dispatchers.IO) { controller.refresh() }
        }
    }
    // Refresh permissions and desired scheduling after the user returns from Android settings.
    val hostLifecycle = (context as? androidx.activity.ComponentActivity)?.lifecycle
    val deckVisible by rememberUpdatedState(deckOpen)
    DisposableEffect(hostLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch { reconcile(); reload() }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && deckVisible) PowerCalStores.appScope.launch { controller.flush() }
        }
        hostLifecycle?.addObserver(observer)
        onDispose { hostLifecycle?.removeObserver(observer) }
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
        runCatching {
            withContext(Dispatchers.IO) { store.newDraft(form, actor = "user") }
        }.onSuccess { card ->
            reload()
            openDeckAt(card.draftId)
            message = parsed.note
        }.onFailure {
            message = it.message ?: "暂时无法创建草稿（AI 批次可能正在独占卡组）"
        }
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
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                        it.write(body)
                    } ?: error("无法写入所选文件")
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

    // System back on the tools page returns to the timeline instead of leaving the app.
    androidx.activity.compose.BackHandler(enabled = page != Page.TIMELINE.name && !deckOpen) {
        page = Page.TIMELINE.name
        scope.launch { reload() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (tools) {
            ToolsPage(
                diagnostics = diagnostics,
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
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, appContext.packageName))
                    }
                },
                onOpenExactAlarmSettings = {
                    ReminderScheduler.openExactAlarmSettings(appContext)
                },
                onRunBatch = { mode ->
                    // Show the review with the freshest versions: authorization itself is still
                    // atomic, but a stale dialog would only fail after the user read it.
                    scope.launch {
                        reload()
                        confirmBatch = BatchReview(mode, pendingCards.toList())
                    }
                },
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
                onAiDraft = { text, shareForRequest ->
                    aiBusy = true
                    scope.launch {
                        try {
                            val settings = AiSettingsStore.load(appContext)
                            if (!settings.configured) {
                                message = "请先配置 AI 端点、模型与密钥"
                                return@launch
                            }
                            // Context sharing is opt-in. When disabled the endpoint only receives
                            // the text the user typed.
                            val contextForAi = if (settings.shareContext && shareForRequest) {
                                val horizon = nowMillis + 14L * 24 * 3_600_000
                                records.filter { (it.anchorMillis() ?: 0L) <= horizon }.take(60)
                            } else {
                                emptyList()
                            }
                            val result = AiClient(settings, zone).interpret(text, contextForAi)
                            val created = withContext(Dispatchers.IO) {
                                AiDraftService(store).createDrafts(result)
                            }
                            reload()
                            val sharedNote = if (contextForAi.isEmpty()) {
                                "未共享事件上下文"
                            } else {
                                "已共享 ${contextForAi.size} 条事件作为本次上下文"
                            }
                            if (created.draftIds.isEmpty()) {
                                message = (result.warnings.firstOrNull()
                                    ?: "AI 没有返回可用的结构化草稿") + " · " + sharedNote
                            } else {
                                openDeckAt(created.draftIds.first())
                                message = "AI 生成了 ${created.draftIds.size} 张待确认草稿 · " + sharedNote
                            }
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            message = "AI 未完成：" + (error.message ?: "请检查配置后重试，已有草稿已保留")
                        } finally {
                            aiBusy = false
                        }
                    }
                },
                aiBusy = aiBusy,
                onReleaseStuck = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store.releaseStuckAiBatch() }
                        reload()
                        message = "已强制解除 AI 独占；旧批次的结果被作废，剩余卡片保留"
                    }
                }
            )
        } else if (page == Page.MONTH.name) {
            MonthScreen(records, Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate(), zone, aiLocked,
                onBack = { page = Page.TIMELINE.name },
                onDate = { date -> anchorMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(); page = Page.TIMELINE.name },
                onOpen = { record ->
                    if (aiLocked) monthInspection = record else scope.launch {
                        runCatching { withContext(Dispatchers.IO) { store.pendingDraftsFor(record.eventSeq).firstOrNull { it.operation == DraftOperation.REPLACE || it.operation == DraftOperation.CONVERT } ?: store.proposeReplace(record.eventSeq) } }
                            .onSuccess { reload(); openDeckAt(it.draftId) }.onFailure { message = it.message ?: "无法打开事件" }
                    }
                },
                onCreate = { date -> scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, date, zone)) } }
                        .onSuccess { reload(); openDeckAt(it.draftId) }.onFailure { message = it.message ?: "无法创建草稿" }
                } })
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
                    if (record.recurrence.isRepeating) {
                        // Moving one instance of a series needs an occurrence-specific proposal.
                        // Until that editor exists, refuse rather than silently moving the series.
                        message = "重复事件暂不支持逐实例拖动；打开卡片可以修改整条重复规则"
                    } else scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val form = movedDraftForm(record, target)
                                store.newDraft(
                                    form,
                                    record.eventSeq,
                                    DraftOperation.REPLACE,
                                    actor = "user"
                                )
                            }
                        }.onSuccess { card ->
                            reload()
                            openDeckAt(card.draftId)
                        }.onFailure {
                            message = it.message ?: "暂时无法创建修改草稿"
                        }
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
                },
                onOpenMonth = { page = Page.MONTH.name },
                blockedReminders = diagnostics.reminders.count { it.intent?.desired == PowerStore.DesiredSchedule.BLOCKED }
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp).semantics { liveRegion = LiveRegionMode.Polite },
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
                    reconcile()
                    reload()
                }
            },
            onOpenFormalEvent = { record ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            store.pendingDraftsFor(record.eventSeq).firstOrNull {
                                it.operation == DraftOperation.REPLACE ||
                                    it.operation == DraftOperation.CONVERT
                            } ?: store.proposeReplace(record.eventSeq)
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
                    reconcile()
                    reload()
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

    monthInspection?.let { record ->
        AlertDialog(onDismissRequest = { monthInspection = null }, title = { Text(record.title) },
            text = { Column { Text(TemporalText.summarize(record)); Text(record.notes); Text("AI 处理中，卡组只读。") } },
            confirmButton = { TextButton(onClick = { monthInspection = null }) { Text("关闭") } })
    }
    confirmBatch?.let { review ->
        val mode = review.mode
        val label = if (mode == AiBatchRunner.Mode.PUBLISH) "发布" else "丢弃"
        AlertDialog(
            onDismissRequest = { confirmBatch = null }, title = { Text("一次性授权 AI $label") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("范围固定为下列 ${review.cards.size} 张草稿。卡组在处理期间只读，时间轴可以查看。你可随时停止，已完成结果会保留。")
                    Text(if (mode == AiBatchRunner.Mode.DISCARD) "丢弃只是不执行这些提案，不会删除正式事件。" else "发布会执行每张提案的完整操作；冲突会保留给你确认。", style = MaterialTheme.typography.bodyMedium)
                    review.cards.groupingBy { it.operation }.eachCount().forEach { (operation, count) ->
                        Text("${operation.label}提案 · $count 张", style = MaterialTheme.typography.labelLarge)
                    }
                    if (mode == AiBatchRunner.Mode.PUBLISH && review.cards.any { it.operation == DraftOperation.DELETE }) {
                        Text("包含正式删除操作：事件序号会退役，当前没有发布撤销功能。", color = MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider()
                    review.cards.forEachIndexed { index, card ->
                        Text("${index + 1}. ${TemporalRecord.formatSeq(card.eventSeq)} · ${card.operation.label} · ${card.form.title.ifBlank { "未命名" }}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(enabled = review.cards.isNotEmpty() && !aiLocked, onClick = {
                confirmBatch = null
                PowerCalStores.appScope.launch {
                    // Authorize the exact reviewed snapshots in ONE transaction: the domain layer
                    // re-checks every draftVersion, so a change after this dialog cannot slip in.
                    val reviewed = review.cards.associate { it.draftId to it.draftVersion }
                    val session = runCatching {
                        store.beginAiBatch(reviewed, review.cards.map { it.operation }.toSet())
                    }.getOrElse {
                        withContext(Dispatchers.Main) {
                            reload()
                            message = "无法开始批次：${it.message.orEmpty()}"
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) { reload() }
                    val report = AiBatchRunner(store).run(session, mode)
                    controller.refresh()
                    withContext(Dispatchers.Main) {
                        reload()
                        message = "AI 批次结束：发布 ${report.published} 张，丢弃 ${report.discarded} 张，保留 ${report.kept} 张" +
                            (if (report.cancelled) "（已停止）" else "") + (report.failures.firstOrNull()?.let { "；$it" } ?: "")
                    }
                    ReminderScheduler.reconcile(appContext, store)
                    withContext(Dispatchers.Main) { reload() }
                }
            }) { Text("授权并$label ${review.cards.size} 张") } },
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
                        "并标记来源为旧版迁移；重复执行不会产生重复记录。旧闹钟可能仍在原时钟中运行，迁入后请先检查并关闭旧闹钟，再启用 PowerCal 提醒，避免重复响铃。"
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
    diagnostics: UiDiagnostics,
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
    onAiDraft: (String, Boolean) -> Unit,
    aiBusy: Boolean,
    onReleaseStuck: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var settings by remember { mutableStateOf(AiSettingsStore.load(appContext)) }
    var aiText by rememberSaveable { mutableStateOf("") }
    var shareForRequest by rememberSaveable { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    // Diagnostics are read once per data change instead of on every recomposition.
    val nextSequence = diagnostics.nextSequence
    val identityCount = diagnostics.identities
    var modelExpanded by rememberSaveable { mutableStateOf(false) }

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

        SectionCard("待处理", "$pendingCount 张草稿") {
            OutlinedButton(onClick = onOpenDeck, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("打开待处理 · $pendingCount 张") }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onRunBatch(AiBatchRunner.Mode.PUBLISH) }, modifier = Modifier.fillMaxWidth(), enabled = pendingCount > 0 && !aiLocked) { Text("一次性授权 AI 批量发布") }
                TextButton(onClick = { onRunBatch(AiBatchRunner.Mode.DISCARD) }, modifier = Modifier.fillMaxWidth(), enabled = pendingCount > 0 && !aiLocked) { Text("一次性授权 AI 批量丢弃") }
            }
            if (aiLocked) {
                Text(
                    "AI 批次正在运行：卡组只读。可以停止，已提交结果会保留。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancelBatch) { Text("停止当前批次") }
                    TextButton(onClick = onReleaseStuck) { Text("强制解除独占") }
                }
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
            HorizontalDivider()
            OutlinedTextField(
                value = aiText,
                onValueChange = { aiText = it },
                label = { Text("用自然语言描述，例如：把周五的组会改到 16:00") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
            )
            if (settings.shareContext) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("本次共享事件上下文", style = MaterialTheme.typography.bodyMedium)
                    Text("向配置的端点发送至多 60 条事件的标题与时间；默认只发送输入文字。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(shareForRequest, { shareForRequest = it }, enabled = !aiBusy && !aiLocked)
            }
            if (dirty) Text("先保存模型配置，再发送本次请求。", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { onAiDraft(aiText, shareForRequest); shareForRequest = false },
                enabled = !aiBusy && !aiLocked && !dirty && aiText.isNotBlank() && settings.configured
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (aiBusy) "解析中…" else "生成待确认草稿")
            }
        }

        SectionCard("导入", "结构化数据严格校验后可直接发布") {
            ToolRow("从系统日历一次性导入", "读取未来一年与过去 30 天，不是双向同步", onImportSystem, enabled = !aiLocked)
            ToolRow("导入 ICS 文件", "支持折叠行、TZID 与转义；不展开 RRULE", onImportIcs, enabled = !aiLocked)
            ToolRow("从旧版 PowerCal 迁移", "只读打开旧数据库，保留原文件", onMigrateLegacy, enabled = !aiLocked)
            ToolRow("从截图识别", "本地中文 OCR，识别结果为待确认草稿", onPickImage, enabled = !aiLocked)
            ToolRow("用文字创建草稿", "离线解析器，不需要联网", onTextDraft, enabled = !aiLocked)
        }

        SectionCard("备份与导出", "原生备份优先于有损格式") {
            ToolRow("导出原生备份", "包含序号水位、草稿顺序与提交历史；不含密钥", onExportBackup)
            ToolRow("从备份恢复", "只允许恢复到空数据库，避免覆盖现有数据", onRestoreBackup, enabled = !aiLocked)
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

        SectionCard("执行状态", "最近的期望调度与执行记录，最多 40 项") {
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
