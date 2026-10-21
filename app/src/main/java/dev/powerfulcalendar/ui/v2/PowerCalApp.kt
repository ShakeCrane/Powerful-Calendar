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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.mutableFloatStateOf
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
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiDraftService
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.LegacyBackupConsentRequired
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
import dev.powerfulcalendar.interop.AiFileEvidence
import dev.powerfulcalendar.interop.AiFileEvidenceReader
import dev.powerfulcalendar.interop.SystemCalendar
import dev.powerfulcalendar.interop.TextParser
import dev.powerfulcalendar.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

private enum class Page { TIMELINE, MONTH, MEMO, AI, TOOLS }

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
    val skins = remember(appContext) { SkinPackageManager(appContext) }
    var skinChoices by remember { mutableStateOf(skins.installed()) }
    var selectedSkin by remember { mutableStateOf(skins.currentId()) }
    var skinToDelete by remember { mutableStateOf<InstalledSkin?>(null) }

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
    var ocrReviewText by remember { mutableStateOf<String?>(null) }
    var pendingImageReview by remember { mutableStateOf<Uri?>(null) }
    var pendingFileReview by remember { mutableStateOf<Pair<Uri, AiFileEvidence>?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var chatBusy by remember { mutableStateOf(false) }
    var proposalReview by remember { mutableStateOf<AiInterpretation?>(null) }
    var chatHistory by remember { mutableStateOf(AiChatHistoryStore.load(appContext)) }
    LaunchedEffect(chatHistory) {
        withContext(Dispatchers.IO) {
            AiChatHistoryStore.save(appContext, chatHistory)
        }
    }
    var confirmBatch by remember { mutableStateOf<BatchReview?>(null) }
    // Choosing a batch mode only starts a conversation; it is never the commit authorization.
    var pendingBatchMode by remember { mutableStateOf<AiBatchRunner.Mode?>(null) }
    var pendingCards by remember { mutableStateOf<List<dev.powerfulcalendar.core.DraftCard>>(emptyList()) }
    var diagnostics by remember { mutableStateOf(UiDiagnostics()) }
    var monthInspection by remember { mutableStateOf<TemporalRecord?>(null) }
    var selectedFormalEvent by remember { mutableStateOf<TemporalRecord?>(null) }
    var confirmingLegacy by remember { mutableStateOf(false) }
    // Holds an early flat v2 file until the user explicitly accepts its unverified nature.
    var legacyRestoreText by remember { mutableStateOf<String?>(null) }

    val initialCamera = remember { TimelineCoordinate.initialCamera(System.currentTimeMillis(), zone) }
    var anchorMillis by rememberSaveable { mutableLongStateOf(initialCamera.anchorMillis) }
    var hours by rememberSaveable { mutableDoubleStateOf(initialCamera.hours) }
    val camera = TimelineCoordinate.Camera(anchorMillis, TimelineCoordinate.clampHours(hours))
    val todayKey = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val todoEntries = remember(records, todayKey) {
        TodoProjection.entries(records, nowMillis, includeCompleted = true)
    }
    val todoCount = todoEntries.count { !it.completed }
    val preferences = remember(appContext) {
        appContext.getSharedPreferences("powercal-ui", Context.MODE_PRIVATE)
    }
    var titlesOutside by remember { mutableStateOf(preferences.getBoolean("titlesOutside", false)) }

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

    /** Hardware-safe read: a crafted or corrupt file cannot exhaust memory before verification. */
    suspend fun readBackupText(uri: Uri): String = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > BackupEnvelope.MAX_BYTES) {
                    throw IllegalArgumentException("备份文件超过 64MB 上限，已拒绝读取")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        } ?: error("无法读取备份文件")
    }

    /**
     * Restore is already committed when this returns. Refresh or scheduling problems are returned as
     * warnings instead of being reported as "restore failed", which would invite a destructive retry.
     */
    suspend fun restoreAndRefresh(
        parsed: JSONObject,
        allowLegacy: Boolean
    ): Pair<PowerStore.RestoreReport, List<String>> {
        val report = withContext(Dispatchers.IO) { store.restoreBackup(parsed, allowLegacy) }
        val problems = mutableListOf<String>()
        runCatching { reload() }.onFailure { problems += "界面刷新失败，请重启应用查看已恢复数据" }
        runCatching { reconcile() }.onFailure { problems += "提醒调度重建失败，请稍后重试" }
        return report to problems
    }

    fun restoreMessage(report: PowerStore.RestoreReport, prefix: String): String = buildString {
        append(prefix)
        append("${report.objects} 个事件、${report.drafts} 张草稿、${report.commits} 条提交")
        append("；调度已重建，旧授权与旧执行记录未恢复")
        report.warnings.firstOrNull()?.let { append("。").append(it) }
    }

    fun Throwable.readable(): String = message ?: javaClass.simpleName

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
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && deckVisible) {
                // Best effort: the controller serializes this after any autosave, and a failed
                // stage keeps the draft dirty with a retryable error for the next visible session.
                PowerCalStores.appScope.launch { controller.flush() }
            }
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
            startText = if (parsed.hasExplicitDate && parsed.hasUnambiguousTime) {
                DraftForm.formatInstant(event.startMillis, zone)
            } else "",
            // An arbitrary 1-hour default must not look like a confirmed appointment duration.
            endText = "",
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

    /** Explicit Skill entry only. It is never invoked from ordinary chat model text. */
    suspend fun generateAiDraft(text: String, shareForRequest: Boolean) {
        if (aiBusy || chatBusy) return
        aiBusy = true
        try {
            val settings = AiSettingsStore.load(appContext)
            if (!settings.configured) {
                message = "请先在设置页配置 AI 端点、模型与密钥"
                return
            }
            // Context sharing is opt-in and the AI Center defaults to zero context.
            val contextForAi = if (settings.shareContext && shareForRequest) {
                val horizon = nowMillis + 14L * 24 * 3_600_000
                records.filter { (it.anchorMillis() ?: 0L) <= horizon }.take(60)
            } else emptyList()
            val result = AiClient(settings, zone).interpret(text, contextForAi)
            val created = withContext(Dispatchers.IO) {
                AiDraftService(store).createDrafts(result)
            }
            reload()
            val sharedNote = if (contextForAi.isEmpty()) "未共享事件上下文"
                else "已共享 ${contextForAi.size} 条事件作为本次上下文"
            if (created.draftIds.isEmpty()) {
                message = (result.warnings.firstOrNull()
                    ?: "AI 没有返回可用的结构化草稿") + " · " + sharedNote
            } else {
                openDeckAt(created.draftIds.first())
                message = "AI 生成 ${created.draftIds.size} 张待确认草稿 · " + sharedNote
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            message = "AI 未完成：" + (error.message ?: "检查配置后重试；已有草稿保留")
        } finally {
            aiBusy = false
        }
    }

    fun appendChat(role: String, text: String, proposalSource: String? = null) {
        chatHistory = (chatHistory + AiChatMessage(role, text.take(6000), proposalSource)).takeLast(200)
    }

    fun beginBatchConversation(mode: AiBatchRunner.Mode) {
        if (chatBusy || aiBusy || aiLocked) return
        pendingBatchMode = mode
        page = Page.AI.name
        appendChat("assistant",
            if (mode == AiBatchRunner.Mode.PUBLISH) {
                "准备批量发布。请告诉我要处理哪些草稿：可以说“全部”，也可以按标题、类型或草稿编号筛选。接下来我会列出具体清单，只有你在审核弹窗确认后才会执行。"
            } else {
                "准备批量丢弃草稿（不会删除已经发布的正式事件）。你希望丢弃哪些？可以说“全部”，或描述要筛选的草稿。随后仍需单独审核确认。"
            })
    }

    fun submitAiChatInput(input: String) {
        val text = input.trim()
        if (text.isEmpty() || chatBusy || aiBusy) return
        appendChat("user", text)
        val currentHistory = chatHistory.takeLast(80)
        val mode = pendingBatchMode
        chatBusy = true
        scope.launch {
            try {
                if (mode == null) {
                    val response = AiClient(AiSettingsStore.load(appContext), zone).chat(currentHistory)
                    appendChat("assistant", if (response.succeeded) response.reply
                        else "AI 请求未完成：" + response.error.orEmpty(),
                        proposalSource = if (response.succeeded) response.proposalSource else null)
                    // Structured proposals are generated by the same validated calendar Skill
                    // used in the explicit AI Tools entry. Chat text alone never mutates PowerStore.
                    val source = if (response.succeeded) response.proposalSource else null
                    if (!source.isNullOrBlank()) {
                        val settings = AiSettingsStore.load(appContext)
                        val share = if (settings.shareContext) records.take(60) else emptyList()
                        val result = AiClient(settings, zone).interpret(source, share)
                        if (result.proposals.isNotEmpty()) proposalReview = result
                        else appendChat("assistant", "无法生成结构化提案：" +
                            result.warnings.firstOrNull().orEmpty())
                    }
                } else {
                    reload()
                    val snapshot = pendingCards.filter { it.isActionable }
                    if (snapshot.isEmpty()) {
                        pendingBatchMode = null
                        appendChat("assistant", "待处理已经为空，没有可授权的草稿。")
                    } else if (snapshot.size > 100) {
                        appendChat("assistant", "待处理超过 100 张，本次不会默认选择前 100 张。请先在待处理中缩小范围。")
                    } else {
                        val all = text in setOf("全部", "所有", "全部草稿", "全选", "所有待处理")
                        val selection = if (all) {
                            dev.powerfulcalendar.ai.AiBatchSelection(
                                selectedIds = snapshot.map { it.draftId },
                                reply = "按用户明确的“全部”选择全部待处理草稿"
                            )
                        } else {
                            AiClient(AiSettingsStore.load(appContext), zone)
                                .planBatchSelection(text, snapshot)
                        }
                        when {
                            selection.ready -> {
                                val ids = selection.selectedIds.toSet()
                                val selected = snapshot.filter { it.draftId in ids }
                                if (selected.size != ids.size) {
                                    appendChat("assistant", "部分目标已发生变化，请重新选择；没有执行任何操作。")
                                } else {
                                    pendingBatchMode = null
                                    appendChat("assistant", "已根据要求选择 " + selected.size +
                                        " 张草稿。下面会弹出包含编号和操作类型的最终审核；取消不会修改事件。")
                                    confirmBatch = BatchReview(mode, selected)
                                }
                            }
                            selection.question != null ->
                                appendChat("assistant", selection.question)
                            else -> appendChat("assistant",
                                "暂时不能安全选定目标：" + (selection.error ?: "没有明确的草稿") +
                                    "。请补充范围或回复“全部”。")
                        }
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                appendChat("assistant", "本次处理未完成：" + error.javaClass.simpleName +
                    "；没有批准任何批量操作。")
            } finally {
                chatBusy = false
            }
        }
    }

    /** AI receives only the text whose preview the user explicitly approved. */
    fun submitEvidence(evidence: AiFileEvidence, hint: String = "") {
        if (chatBusy || aiBusy) return
        page = Page.AI.name
        chatBusy = true
        appendChat("user", "上传并确认分析：" + evidence.filename +
            "（" + evidence.format + "）" +
            if (evidence.truncated) " · 内容已截断" else "")
        val prior = chatHistory.takeLast(7)
        scope.launch {
            try {
                val reply = AiClient(AiSettingsStore.load(appContext), zone)
                    .analyzeEvidence(evidence.filename,
                        (if (hint.isBlank()) "" else "文件预检信息：$hint\n") + evidence.text,
                        prior)
                appendChat("assistant", if (reply.succeeded) reply.reply
                    else "分析未完成：" + reply.error.orEmpty() +
                        "；源文件没有被导入，日历数据未修改。",
                    proposalSource = if (reply.succeeded) reply.proposalSource else null)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                appendChat("assistant", "无法完成文件分析：" + error.javaClass.simpleName +
                    "；原始数据没有发布。")
            } finally {
                chatBusy = false
            }
        }
    }

    // ------------------------------------------------------------------ import launchers

    suspend fun importIcs(uri: Uri) {
        try {
            // A file provider may stream arbitrary input. Bound memory before parsing.
            val text = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val output = StringBuilder()
                    val chunk = CharArray(8192)
                    while (true) {
                        val count = reader.read(chunk)
                        if (count < 0) break
                        require(output.length + count <= 2_000_000) { "ICS 文件超过 200 万字符上限" }
                        output.append(chunk, 0, count)
                    }
                    output.toString()
                }
            } ?: error("无法读取所选文件")
            val parsed = IcsCodec.parse(text)
            // A partial ICS import is worse than a clear refusal: RRULE, unsupported TZID,
            // missing DTEND and invalid VEVENTs must not silently lose calendar occurrences.
            if (parsed.warnings.isNotEmpty()) {
                message = "ICS 未导入：包含 ${parsed.warnings.size} 项无法安全解析的内容。首项：${parsed.warnings.first().take(180)}"
                return
            }
            if (parsed.events.isEmpty() && parsed.series.isEmpty()) {
                message = "文件里没有可导入的单次事件或重复系列"
                return
            }
            // The quota counts SERIES, never occurrences. An infinite RRULE is one card.
            require(parsed.events.size + parsed.series.size <= 300) {
                "一次最多预览 300 个单次事件/重复系列，请先拆分文件"
            }
            val result = withContext(Dispatchers.IO) {
                // Exact title/start/end comparison is only an import guard, not a promise
                // of cross-device identity or a substitute for permanent invocation receipts.
                fun identity(form: DraftForm): List<Any?> = listOf(
                    form.kind, form.title.trim(), form.parseStart(), form.parseEnd(),
                    form.parseDeadline(), form.recurrence, form.zoneId
                )
                val known = (
                    store.all().map { DraftForm.from(it) } +
                        store.pendingDrafts().filter { it.operation == DraftOperation.CREATE }
                            .map { it.form }
                    ).map(::identity).toMutableSet()
                var duplicated = 0
                val singletonForms = parsed.events.map { event ->
                    DraftForm(
                        kind = TemporalKind.ACTIVITY,
                        title = event.title,
                        startText = DraftForm.formatInstant(event.startMillis, zone),
                        endText = DraftForm.formatInstant(event.endMillis, zone),
                        notes = event.notes, location = event.location,
                        reminderMinutes = event.reminderMinutes,
                        source = "ics", zoneId = zone.id
                    )
                }
                // Each series is represented by ONE DraftForm -> ONE allocated eventSeq.
                val forms = (singletonForms + parsed.series.map { it.form }).filter { form ->
                    if (known.add(identity(form))) true
                    else { duplicated++; false }
                }
                val created = forms.map { form -> store.newDraft(form, actor = "user") }
                created to duplicated
            }
            reload()
            result.first.firstOrNull()?.let { openDeckAt(it.draftId) }
            message = "ICS 已生成 ${result.first.size} 张待确认草稿（其中 ${parsed.series.size} 个重复系列，每系列仅占一个序号）；跳过 ${result.second} 条疑似重复。尚未正式发布。"
        } catch (error: Exception) {
            message = "ICS 预览失败：" + (error.message ?: "未知错误") + "；请检查待处理是否有已生成草稿"
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
    val openAiDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val evidence = withContext(Dispatchers.IO) {
                    AiFileEvidenceReader.read(appContext, uri)
                }
                pendingFileReview = uri to evidence
            } catch (error: Exception) {
                message = "无法安全提取文件：" + (error.message ?: error.javaClass.simpleName) +
                    "；没有上传给 AI，也没有创建事件。"
            }
        }
    }
    val importSkin = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { skins.install(uri) } }
                .onSuccess { result ->
                    runCatching { skins.select(result.id) }
                        .onSuccess {
                            selectedSkin = result.id
                            skinChoices = skins.installed()
                            message = "皮肤已校验、导入并启用：" + result.name
                        }
                        .onFailure { error ->
                            message = "皮肤已导入但无法启用：" + error.message
                        }
                }
                .onFailure { error ->
                    message = "皮肤导入已拒绝：" + (error.message ?: "未知错误") +
                        "。原生主题保持不变。"
                }
        }
    }
    val exportOriginalSkin = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { skins.exportOriginal(uri) } }
                .onSuccess { message = "PowerCal 原生玻璃皮肤模板已导出，可交给 Claude 修改" }
                .onFailure { error -> message = "导出皮肤模板失败：" + error.message }
        }
    }

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
                val envelope = withContext(Dispatchers.IO) { store.exportBackup() }
                val json = envelope.toString(2)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                        it.write(json)
                    } ?: error("无法写入目标文件")
                }
                // Read back through the same bounded reader so a hostile provider cannot bypass the
                // size guard; only a hash-verified read-back may be reported as success.
                val reread = runCatching { readBackupText(uri) }.getOrNull()
                if (reread == null) {
                    message = "备份已写入，但该文件提供方不支持回读校验；请自行保留并验证文件"
                } else {
                    BackupEnvelope.verifyReadback(envelope, JSONObject(reread), PowerStore.VERSION)
                    message = "原生备份已导出并回读校验（SHA-256 匹配）；文件为明文敏感数据"
                }
            }.onFailure { message = "备份失败：" + it.readable() }
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            var text: String? = null
            runCatching {
                val loaded = readBackupText(uri)
                text = loaded
                val parsed = runCatching { JSONObject(loaded) }
                    .getOrElse { throw IllegalArgumentException("备份文件不是有效 JSON") }
                val (report, problems) = restoreAndRefresh(parsed, allowLegacy = false)
                message = restoreMessage(report, "已恢复 ") + problems.joinToString("", "；", "") { it }
            }.onFailure { error ->
                when (error) {
                    is LegacyBackupConsentRequired -> {
                        legacyRestoreText = text
                        message = "该备份是早期 flat v2 格式，缺少 SHA-256；需要你确认来源可信后才能恢复"
                    }
                    else -> message = "恢复失败：" + error.readable()
                }
            }
        }
    }
    fun runLocalOcr(uri: Uri) {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (image == null) {
            message = "无法读取图片"
            recognizer.close()
        } else {
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (result.text.isBlank()) message = "未识别到文字，可以手动输入"
                    else ocrReviewText = result.text
                }
                .addOnFailureListener { message = "OCR 失败：" + it.message }
                .addOnCompleteListener { recognizer.close() }
        }
    }
    val ocrPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) pendingImageReview = uri }

    val tools = page == Page.TOOLS.name

    // System back on the tools page returns to the timeline instead of leaving the app.
    androidx.activity.compose.BackHandler(enabled = page != Page.TIMELINE.name && !deckOpen) {
        page = Page.TIMELINE.name
        scope.launch { reload() }
    }

    SkinAppShell {
        val keyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        SkinPageTransition(
            page, Modifier.fillMaxSize().padding(bottom = if (keyboardVisible) 0.dp else 80.dp)
        ) { presentedPage ->
        if (presentedPage == Page.TOOLS.name) {
            ToolsPage(
                diagnostics = diagnostics,
                records = records,
                pendingCount = pendingCount,
                aiLocked = aiLocked,
                aiSession = aiSession,
                effects = effects,
                skinChoices = skinChoices,
                selectedSkin = selectedSkin,
                onSelectSkin = { id ->
                    runCatching { skins.select(id) }
                        .onSuccess {
                            selectedSkin = id
                            message = "已切换皮肤：" + skinChoices.firstOrNull { it.id == id }?.name
                        }
                        .onFailure { error -> message = "无法切换皮肤：" + error.message }
                },
                onImportSkin = {
                    importSkin.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                onExportOriginalSkin = { exportOriginalSkin.launch("PowerCal-Original.pcskin") },
                onDeleteSkin = { id ->
                    skinToDelete = skinChoices.firstOrNull { it.id == id && !it.builtIn }
                },
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
                    scope.launch { generateAiDraft(text, shareForRequest) }
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
        } else if (presentedPage == Page.AI.name) {
            AiChatScreen(
                messages = chatHistory,
                busy = chatBusy || aiBusy,
                pendingCount = pendingCount,
                proposalReview = proposalReview,
                onApplyProposals = { selected, publish ->
                    val snapshot = proposalReview
                    if (snapshot != null && selected.isNotEmpty() && !aiBusy && !chatBusy) {
                        aiBusy = true
                        scope.launch {
                            try {
                                var staged = 0
                                var published = 0
                                val handled = mutableSetOf<Int>()
                                val errors = mutableListOf<String>()
                                selected.sorted().forEach { index ->
                                    val proposal = snapshot.proposals.getOrNull(index)
                                        ?: return@forEach
                                    if (proposal.blocked != null) {
                                        errors.add(proposal.blocked)
                                        return@forEach
                                    }
                                    val card = runCatching {
                                        withContext(Dispatchers.IO) {
                                            val seq = proposal.eventSeq
                                            if (proposal.operation == DraftOperation.CREATE) {
                                                store.newDraft(proposal.form, actor = "ai")
                                            } else {
                                                require(seq != null) { "修改提案缺少目标序号" }
                                                store.newDraft(proposal.form, seq, proposal.operation, actor = "ai")
                                            }
                                        }
                                    }.onFailure {
                                        errors.add(it.message ?: "无法写入草稿")
                                    }.getOrNull() ?: return@forEach
                                    staged++
                                    handled.add(index)
                                    if (publish) {
                                        val result = withContext(Dispatchers.IO) {
                                            store.commit(card.draftId, actor = "user")
                                        }
                                        if (result is PowerStore.CommitOutcome.Committed) published++
                                        else errors.add("序号 " + card.eventSeq + " 未发布；草稿仍可编辑")
                                    }
                                }
                                proposalReview = snapshot.copy(proposals =
                                    snapshot.proposals.filterIndexed { i, _ -> i !in handled })
                                    .takeIf { it.proposals.isNotEmpty() }
                                reload()
                                if (publish && published > 0) reconcile()
                                appendChat("assistant", "结构化操作结果：写入草稿 " + staged +
                                    "，正式发布 " + published + "。"
                                    + errors.take(2).joinToString("；"))
                            } finally {
                                aiBusy = false
                            }
                        }
                    }
                },
                onDiscardProposals = { selected ->
                    proposalReview = proposalReview?.let { old ->
                        old.copy(proposals = old.proposals.filterIndexed { i, _ -> i !in selected })
                            .takeIf { it.proposals.isNotEmpty() }
                    }
                },
                onSupplementProposals = {
                    appendChat("assistant", "请直接补充要更正的提案字段或目标；不会在未同意时改动正式事件。")
                },
                onSend = { submitAiChatInput(it) },
                onPropose = { text ->
                    scope.launch { generateAiDraft(text, shareForRequest = false) }
                },
                onClear = {
                    chatHistory = emptyList()
                    pendingBatchMode = null
                    proposalReview = null
                },
                onClearCache = {
                    scope.launch {
                        val count = withContext(Dispatchers.IO) {
                            AiChatHistoryStore.clearTemporaryCache(appContext)
                        }
                        message = "已清理 " + count + " 个应用私有 AI 临时缓存文件；聊天和正式事件保留"
                    }
                },
                onSettings = { page = Page.TOOLS.name },
                onOpenDeck = { scope.launch { openDeckAt(null) } },
                onPickImage = { ocrPicker.launch("image/*") },
                onPickFile = { openAiDocument.launch(arrayOf("*/*")) },
                onPrepareBatch = { beginBatchConversation(it) },
                onRewind = { lastIndex ->
                    chatHistory = chatHistory.take((lastIndex + 1).coerceAtLeast(0))
                    pendingBatchMode = null
                }
            )
        } else if (presentedPage == Page.MEMO.name) {
            MemoCenterScreen(
                items = todoEntries, zone = zone, busy = aiLocked,
                onBack = { page = Page.TIMELINE.name },
                onCreateMemo = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                store.newDraft(DraftForm.fresh(TemporalKind.MEMO, zone = zone))
                            }
                        }.onSuccess {
                            reload(); openDeckAt(it.draftId)
                        }.onFailure { message = it.message ?: "无法新建 Memo" }
                    }
                },
                onToggle = { entry ->
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            store.setCompleted(entry.owner.eventSeq, entry.occurrenceKey,
                                !entry.completed, entry.owner.revision)
                        }
                        if (!success) message = "状态已变化或正在批量处理，请刷新后重试"
                        reload()
                    }
                },
                onOpen = { record ->
                    if (aiLocked) monthInspection = record else selectedFormalEvent = record
                }
            )
        } else if (presentedPage == Page.MONTH.name) {
            MonthScreen(records, Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate(), zone, aiLocked,
                onBack = { page = Page.TIMELINE.name },
                onDate = { date -> anchorMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(); page = Page.TIMELINE.name },
                onOpen = { record ->
                    if (aiLocked) monthInspection = record else selectedFormalEvent = record
                },
                onCreate = { date -> scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, date, zone)) } }
                        .onSuccess { reload(); openDeckAt(it.draftId) }.onFailure { message = it.message ?: "无法创建草稿" }
                } })
        } else {
            SkinnableTimelineScreen(
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
                onOpenMemo = { page = Page.MEMO.name },
                memoCount = todoCount,
                titlesOutside = titlesOutside,
                onTitlesOutsideChange = { outside ->
                    titlesOutside = outside
                    preferences.edit().putBoolean("titlesOutside", outside).apply()
                },
                onOpenEvent = { record ->
                    if (aiLocked) monthInspection = record else selectedFormalEvent = record
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
                    // Today means the entire local day, not a zoomed view around this minute.
                    val today = TimelineCoordinate.initialCamera(nowMillis, zone)
                    anchorMillis = today.anchorMillis
                    hours = today.hours
                },
                onOpenDeck = { scope.launch { openDeckAt(null) } },
                onOpenTools = { page = Page.TOOLS.name },
                blockedReminders = diagnostics.reminders.count { it.intent?.desired == PowerStore.DesiredSchedule.BLOCKED }
            )
        }
        } // page content above the persistent bottom navigation
        if (!keyboardVisible) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                PowerCalNavigation(
                    selected = when (page) {
                        Page.MONTH.name -> VisualTab.MONTH
                        Page.AI.name -> VisualTab.AI
                        Page.TOOLS.name -> VisualTab.TOOLS
                        Page.MEMO.name -> null
                        else -> VisualTab.TIMELINE
                    },
                    onSelect = { tab -> page = tab.name }
                )
            }
        }

        message?.let { text ->
            SkinNotice(
                text,
                Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 92.dp)
            )
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

    skinToDelete?.let { item ->
        SkinAlertDialog(
            onDismissRequest = { skinToDelete = null },
            title = { Text("删除皮肤？") },
            text = { Text("确认删除“" + item.name + "”？此操作仅删除皮肤素材，日历事件不会改变。") },
            confirmButton = {
                TextButton(onClick = {
                    skinToDelete = null
                    runCatching { skins.delete(item.id) }
                        .onSuccess {
                            selectedSkin = skins.currentId()
                            skinChoices = skins.installed()
                            message = "已删除皮肤：" + item.name
                        }
                        .onFailure { error -> message = "删除皮肤失败：" + error.message }
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { skinToDelete = null }) { Text("取消") } }
        )
    }

    selectedFormalEvent?.let { record ->
        fun choose(operation: DraftOperation) {
            selectedFormalEvent = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        store.pendingDraftsFor(record.eventSeq)
                            .firstOrNull { it.operation == operation && it.isActionable }
                            ?: store.proposeReplace(record.eventSeq, operation)
                    }
                }.onSuccess { card ->
                    reload()
                    openDeckAt(card.draftId)
                }.onFailure { error ->
                    message = error.message ?: "无法创建${operation.label}提案"
                }
            }
        }
        SkinAlertDialog(
            onDismissRequest = { selectedFormalEvent = null },
            title = { Text("正式事件 ${record.displaySeq()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium)
                    Text(TemporalText.summarize(record))
                    Text("编辑和删除都会先生成待确认草稿；取消草稿不会影响正式事件。",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { choose(DraftOperation.REPLACE) }) { Text("编辑") }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedFormalEvent = null }) { Text("返回") }
                    TextButton(onClick = { choose(DraftOperation.DELETE) }) {
                        Text("创建删除提案", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    pendingFileReview?.let { (uri, evidence) ->
        var localText by remember(uri) { mutableStateOf(evidence.text) }
        val ics = evidence.format == "ics"
        val parsed = remember(uri, evidence.text) {
            if (ics) runCatching { IcsCodec.parse(evidence.text) }.getOrNull() else null
        }
        SkinAlertDialog(
            onDismissRequest = { pendingFileReview = null },
            title = { Text("核对文件 · " + evidence.filename) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本地提取，不会自动发布。发送给 AI 前请确认文本没有敏感信息，文件中的指令不会被执行。",
                        style = MaterialTheme.typography.bodySmall)
                    if (ics) Text(
                        "ICS 本地预检：单次 " + (parsed?.events?.size ?: 0) +
                            " 条、重复系列 " + (parsed?.series?.size ?: 0) +
                            " 个；需要澄清 " + (parsed?.warnings?.size ?: 0) + " 项。" +
                            (parsed?.warnings?.firstOrNull()?.let { "\n例如：$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(evidence.note, style = MaterialTheme.typography.labelSmall,
                        color = if (evidence.truncated) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = localText,
                        onValueChange = { if (it.length <= 24_000) localText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 310.dp),
                        label = { Text("本次将发送的提取文本（可删除隐私内容）") },
                        maxLines = 16
                    )
                }
            },
            confirmButton = {
                val direct = ics && !evidence.truncated && localText == evidence.text &&
                    parsed != null && parsed.warnings.isEmpty() &&
                    (parsed.events.isNotEmpty() || parsed.series.isNotEmpty())
                TextButton(
                    enabled = localText.isNotBlank() && !chatBusy,
                    onClick = {
                        pendingFileReview = null
                        if (direct) {
                            // Standard formats are processed by the deterministic importer FIRST.
                            scope.launch { importIcs(uri) }
                        } else {
                            // Only unsupported, partial or edited evidence takes the AI route.
                            val report = if (ics) parsed?.warnings?.take(5)
                                ?.joinToString("; ").orEmpty() else ""
                            submitEvidence(evidence.copy(text = localText), report)
                        }
                    }
                ) { Text(if (direct) "按 ICS 规则生成草稿" else "交给 AI 分析并澄清") }
            },
            dismissButton = {
                TextButton(onClick = { pendingFileReview = null }) { Text("取消") }
            }
        )
    }

    pendingImageReview?.let { uri ->
        SkinAlertDialog(
            onDismissRequest = { pendingImageReview = null },
            title = { Text("图片识别方式") },
            text = {
                Text("如果当前 AI 服务支持视觉，可以直接上传图片由模型读图。图片可能含个人信息；" +
                    "只有点击「发送原图给 AI」才会向配置的端点上传。也可以只在本地 OCR，之后核对文字。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImageReview = null
                    page = Page.AI.name
                    chatBusy = true
                    scope.launch {
                        try {
                            val dataUri = withContext(Dispatchers.IO) {
                                val resolver = appContext.contentResolver
                                val mime = resolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
                                require(mime.startsWith("image/")) { "所选文件不是图片" }
                                val output = ByteArrayOutputStream()
                                resolver.openInputStream(uri)?.use { input ->
                                    val buffer = ByteArray(32_768)
                                    while (true) {
                                        val size = input.read(buffer)
                                        if (size < 0) break
                                        require(output.size() + size <= 6_000_000) {
                                            "图片超过 6MB，建议压缩或使用本地 OCR"
                                        }
                                        output.write(buffer, 0, size)
                                    }
                                } ?: error("无法读取图片")
                                "data:" + mime + ";base64," +
                                    android.util.Base64.encodeToString(output.toByteArray(),
                                        android.util.Base64.NO_WRAP)
                            }
                            val settings = AiSettingsStore.load(appContext)
                            val reply = AiClient(settings, zone).analyzeImage(
                                "用户上传图片", dataUri, chatHistory.takeLast(8))
                            if (!reply.succeeded) {
                                message = "视觉识别不可用：" + reply.error.orEmpty() + "；已改用本地 OCR"
                                runLocalOcr(uri)
                            } else {
                                appendChat("user", "已授权 AI 读取图片（原始图片不写入聊天历史）")
                                appendChat("assistant", reply.reply, reply.proposalSource)
                                reply.proposalSource?.let { source ->
                                    val contextForAi = if (settings.shareContext) records.take(60)
                                        else emptyList()
                                    val result = AiClient(settings, zone).interpret(source, contextForAi)
                                    if (result.proposals.isNotEmpty()) proposalReview = result
                                }
                            }
                        } catch (error: Exception) {
                            message = "图片处理失败：" + (error.message ?: "未知错误")
                        } finally { chatBusy = false }
                    }
                }, enabled = !chatBusy) { Text("发送原图给 AI") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingImageReview = null }) { Text("取消") }
                    TextButton(onClick = { pendingImageReview = null; runLocalOcr(uri) }) {
                        Text("仅本地 OCR")
                    }
                }
            }
        )
    }

    ocrReviewText?.let { extracted ->
        SkinAlertDialog(
            onDismissRequest = { ocrReviewText = null },
            title = { Text("核对 OCR 识别原文") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("先修正 OCR 错字，然后发送给 AI 理解；缺少日期、时刻或结束时间时 AI 应先追问。不会自动创建或发布事件。",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = extracted,
                        onValueChange = { ocrReviewText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
                        label = { Text("识别原文（可编辑）") }
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = extracted.isNotBlank(), onClick = {
                    ocrReviewText = null
                    submitEvidence(AiFileEvidence(
                        filename = "OCR 图片文字", format = "ocr", text = extracted,
                        truncated = false, note = "OCR 文本由用户核对后发送"
                    ))
                }) { Text("发送给 AI 理解") }
            },
            dismissButton = { TextButton(onClick = { ocrReviewText = null }) { Text("取消") } }
        )
    }

    monthInspection?.let { record ->
        SkinAlertDialog(onDismissRequest = { monthInspection = null }, title = { Text(record.title) },
            text = { Column { Text(TemporalText.summarize(record)); Text(record.notes); Text("AI 处理中，卡组只读。") } },
            confirmButton = { TextButton(onClick = { monthInspection = null }) { Text("关闭") } })
    }
    confirmBatch?.let { review ->
        val mode = review.mode
        val label = if (mode == AiBatchRunner.Mode.PUBLISH) "发布" else "丢弃"
        SkinAlertDialog(
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
        SkinAlertDialog(
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

    legacyRestoreText?.let { legacyText ->
        SkinAlertDialog(
            onDismissRequest = { legacyRestoreText = null },
            title = { Text("恢复早期格式备份？") },
            text = {
                Text(
                    "该文件是早期 flat backup v2，没有 SHA-256 或 lineageId，无法验证是否被修改。" +
                        "仅在你能确认文件来源和完整性时继续。恢复后会被视为新的数据库 lineage，" +
                        "并重建提醒调度；AI 授权不会恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    legacyRestoreText = null
                    scope.launch {
                        runCatching {
                            val parsed = runCatching { JSONObject(legacyText) }
                                .getOrElse { throw IllegalArgumentException("备份文件不是有效 JSON") }
                            val (report, problems) = restoreAndRefresh(parsed, allowLegacy = true)
                            message = restoreMessage(report, "旧格式备份已恢复（未验证完整性）：") +
                                problems.joinToString("", "；", "") { it } +
                                "；建议立即导出新的 Envelope 备份"
                        }.onFailure { error ->
                            message = "恢复失败：" + error.readable()
                        }
                    }
                }) { Text("仍要恢复") }
            },
            dismissButton = {
                TextButton(onClick = { legacyRestoreText = null }) { Text("取消") }
            }
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
    SkinAlertDialog(
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
internal fun NativeToolsPage(
    diagnostics: UiDiagnostics,
    records: List<TemporalRecord>,
    pendingCount: Int,
    aiLocked: Boolean,
    aiSession: PowerStore.AiSession?,
    effects: GlassEffects,
    skinChoices: List<InstalledSkin>,
    selectedSkin: String,
    onSelectSkin: (String) -> Unit,
    onImportSkin: () -> Unit,
    onExportOriginalSkin: () -> Unit,
    onDeleteSkin: (String) -> Unit,
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
    var aiText by rememberSaveable { mutableStateOf("") }
    var shareForRequest by rememberSaveable { mutableStateOf(false) }
    // Diagnostics are read once per data change instead of on every recomposition.
    val nextSequence = diagnostics.nextSequence
    val identityCount = diagnostics.identities

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

        ProtectedProfileSection()
        ProtectedAiSettingsSection()

        SectionCard("皮肤", "支持独立 .pcskin 包、即时切换、重启保留；原生主题始终可用") {
            skinChoices.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedSkin == item.id,
                        onClick = { onSelectSkin(item.id) },
                        label = { Text(item.name, maxLines = 1) }
                    )
                    Spacer(Modifier.weight(1f))
                    if (!item.builtIn) {
                        TextButton(onClick = { onDeleteSkin(item.id) }) { Text("删除") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportSkin) { Text("导入皮肤包") }
                TextButton(onClick = onExportOriginalSkin) { Text("导出原生皮肤") }
            }
            Text("仅加载受校验的 JSON 与 PNG/WebP。字体缩放、颜色、圆角、时间标记线宽和装饰素材可配置；不执行外部脚本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("导入", "仅保留系统级来源，其他文件请从 AI 助手 ＋ 添加") {
            ToolRow("从系统日历一次性导入", "读取过去 30 天与未来一年；不是双向同步", onImportSystem, enabled = !aiLocked)
            ToolRow("从旧版 PowerCal 迁移", "只读旧数据库，保留原文件", onMigrateLegacy, enabled = !aiLocked)
        }

        SectionCard("备份与导出", "原生备份优先于有损格式") {
            ToolRow(
                "导出原生备份（Envelope v1）",
                "单一致性快照 + SHA-256；含序号水位、草稿、提交与迁移记录；不含密钥。文件为明文敏感数据，请自行保管",
                onExportBackup
            )
            ToolRow(
                "从备份恢复",
                "校验版本/哈希/引用；只允许恢复到空数据库；旧 flat v2 需显式确认",
                onRestoreBackup,
                enabled = !aiLocked
            )
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

/** Placement-safe profile editor. The visual context never receives raw private preferences. */
@Composable
internal fun ProtectedProfileSection() {
    val appContext = LocalContext.current.applicationContext
    SectionCard("个人信息", "本机资料 · 不上传给 AI") {
        var displayName by remember {
            mutableStateOf(appContext.getSharedPreferences("powercal-profile", Context.MODE_PRIVATE)
                .getString("displayName", "").orEmpty())
        }
        Text("头像与云端资料将在皮肤系统完成后支持；个人资料不是日历业务数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = displayName,
            onValueChange = { if (it.length <= 80) displayName = it },
            label = { Text("你的名字（可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedButton(onClick = {
            appContext.getSharedPreferences("powercal-profile", Context.MODE_PRIVATE).edit()
                .putString("displayName", displayName).apply()
        }) { Text("保存个人信息") }
    }


}

/** Credentials are rendered by this host-owned control; skins cannot read the value. */
@Composable
internal fun ProtectedAiSettingsSection() {
    val appContext = LocalContext.current.applicationContext
    var settings by remember { mutableStateOf(AiSettingsStore.load(appContext)) }
    var dirty by remember { mutableStateOf(false) }
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
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
        Text("聊天、OCR、ICS 和文档都从底部 AI 助手进入。日历变更在待处理确认，设置页只管理模型连接与隐私权限。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }


}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = skinCardShape(),
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
