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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
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
import dev.powerfulcalendar.ai.ChatProposalIntent
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiDraftService
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.interop.SafBackupReader
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.FormalEventSearch
import dev.powerfulcalendar.core.LegacyBackupConsentRequired
import dev.powerfulcalendar.core.NewEventEditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.LegacyImporter
import dev.powerfulcalendar.interop.IcsCodec
import dev.powerfulcalendar.interop.IcsImportPlanner
import dev.powerfulcalendar.interop.ImportCheckpoint
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.UUID

internal suspend fun beginSystemCalendarBatchTracked(
    store: PowerStore,
    onCreated: (String) -> Unit
): String = withContext(Dispatchers.IO) {
    store.beginMigration("android-calendar").also(onCreated)
}

internal suspend fun finishSystemCalendarBatch(
    store: PowerStore,
    batchId: String,
    warnings: List<String>
) {
    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
        store.finishMigration(batchId, warnings)
    }
}

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
    internal val aiBatchProgress = WebAiBatchProgressTracker()

    fun get(context: Context): PowerStore = instance ?: synchronized(this) {
        instance ?: PowerStore(context.applicationContext).also { instance = it }
    }
}

private enum class Page { TIMELINE, MONTH, MEMO, AI, SETTINGS, TOOLS }

private data class BatchReview(val mode: AiBatchRunner.Mode, val cards: List<dev.powerfulcalendar.core.DraftCard>)

internal fun nativeRecoveryMessage(persisted: Boolean): String = if (persisted) {
    "已切换至原生设置页，日历数据及已安装皮肤均保留。"
} else {
    "当前已切换至原生设置页，但未能确认回退设置已保存；请在皮肤设置中再次选择原生皮肤。"
}

internal class LifecycleCheckpointGate {
    private var checkpointedSession: Any? = null
    fun onStart() { checkpointedSession = null }
    fun shouldCheckpoint(session: Any): Boolean {
        if (checkpointedSession === session) return false
        checkpointedSession = session
        return true
    }
}

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
fun PowerCalApp(
    store: PowerStore,
    nativeRecoverySignal: Int = 0,
    nativeRecoveryPersisted: Boolean = true
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val webAiBatchProgress by PowerCalStores.aiBatchProgress.progress.collectAsState()
    // Re-read on composition so sessions opened after a timezone change capture the current zone.
    val zone = ZoneId.systemDefault()
    val scope = rememberCoroutineScope()
    val controller = remember(store) { DeckController(StoreDeckPersistence(store)) }
    val effects = LocalGlassEffects.current
    val skins = remember(appContext) { SkinPackageManager(appContext) }
    var skinChoices by remember { mutableStateOf(skins.installed()) }
    var selectedSkin by remember { mutableStateOf(skins.currentId()) }
    var skinToDelete by remember { mutableStateOf<InstalledSkin?>(null) }
    var skinToActivate by remember { mutableStateOf<InstalledSkin?>(null) }
    var pendingSkinExportId by rememberSaveable { mutableStateOf<String?>(null) }

    var page by rememberSaveable { mutableStateOf(Page.TIMELINE.name) }
    var records by remember { mutableStateOf<List<TemporalRecord>>(emptyList()) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var aiLocked by remember { mutableStateOf(false) }
    var aiSession by remember { mutableStateOf<PowerStore.AiSession?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }

    // The Activity attempts to persist native selection independently of the Web layer.
    LaunchedEffect(nativeRecoverySignal, nativeRecoveryPersisted) {
        if (nativeRecoverySignal > 0) {
            selectedSkin = if (nativeRecoveryPersisted) skins.currentId() else "powercal-default"
            skinToActivate = null
            page = Page.TOOLS.name
            message = nativeRecoveryMessage(nativeRecoveryPersisted)
        }
    }
    var deckOpen by rememberSaveable { mutableStateOf(false) }
    var deckInitialId by rememberSaveable { mutableStateOf<Long?>(null) }
    var textDialogOpen by remember { mutableStateOf(false) }
    var ocrReviewText by remember { mutableStateOf<String?>(null) }
    var pendingImageReview by remember { mutableStateOf<Uri?>(null) }
    var pendingFileReview by remember { mutableStateOf<Pair<Uri, AiFileEvidence>?>(null) }
    var aiAttachmentPickerBusy by remember { mutableStateOf(false) }
    var firstCalendarImportPrompt by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var chatBusy by remember { mutableStateOf(false) }
    var webAiProposalReviewSession by remember { mutableStateOf<WebAiProposalReviewSession?>(null) }
    val proposalReview = webAiProposalReviewSession?.interpretation
    var pendingWebAiProposalPublish by remember {
        mutableStateOf<WebAiProposalPublishReview?>(null)
    }
    fun replaceAiProposalReview(interpretation: AiInterpretation?, knownRecords: List<TemporalRecord> = emptyList()) {
        webAiProposalReviewSession = interpretation?.takeIf { it.proposals.isNotEmpty() }
            ?.let { WebAiProposalReviewSession.create(it, knownRecords) }
        WebSkinBridgeRuntime.stateRevision++
    }
    var chatHistory by remember { mutableStateOf(AiChatHistoryStore.load(appContext)) }
    var chatHistoryRevision by remember { mutableLongStateOf(0L) }
    var webChatPrefillText by remember { mutableStateOf("") }
    var webChatPrefillRevision by remember { mutableLongStateOf(0L) }
    LaunchedEffect(chatHistory) {
        withContext(Dispatchers.IO) {
            AiChatHistoryStore.save(appContext, chatHistory)
        }
    }
    var confirmBatch by remember { mutableStateOf<BatchReview?>(null) }
    var confirmStuckBatchRelease by remember { mutableStateOf<PowerStore.AiSession?>(null) }
    var stuckBatchReleaseRequestPending by remember { mutableStateOf(false) }
    // Choosing a batch mode only starts a conversation; it is never the commit authorization.
    var pendingBatchMode by remember { mutableStateOf<AiBatchRunner.Mode?>(null) }
    var pendingCards by remember { mutableStateOf<List<dev.powerfulcalendar.core.DraftCard>>(emptyList()) }
    var diagnostics by remember { mutableStateOf(UiDiagnostics()) }
    var monthInspection by remember { mutableStateOf<TemporalRecord?>(null) }
    var editSession by remember { mutableStateOf<EditSession?>(null) }
    var editForm by remember { mutableStateOf<DraftForm?>(null) }
    var editBaseline by remember { mutableStateOf<DraftForm?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    var editBusy by remember { mutableStateOf(false) }
    var editUiRevision by remember { mutableIntStateOf(0) }
    var newEventSession by remember { mutableStateOf<NewEventEditSession?>(null) }
    var newEventForm by remember { mutableStateOf<DraftForm?>(null) }
    var newEventError by remember { mutableStateOf<String?>(null) }
    var newEventBusy by remember { mutableStateOf(false) }
    // One Web editing coordinator per host; reloading or replacing a skin
    // must never create a new DeckController, editor lease or command queue.
    val nativeEditingNow = androidx.compose.runtime.rememberUpdatedState(
        editSession != null || deckOpen || newEventSession != null)
    val webEditing = remember(store, controller) {
        WebSkinEditService(store, controller) { nativeEditingNow.value }
    }
    DisposableEffect(webEditing) {
        onDispose {
            // UI teardown never drops a live editing lease without first trying
            // the same durable checkpoint used for an explicit skin change.
            PowerCalStores.appScope.launch { webEditing.prepareSkinChange() }
        }
    }
    var confirmingLegacy by remember { mutableStateOf(false) }
    // Holds an early flat v2 file until the user explicitly accepts its unverified nature.
    var legacyRestoreText by remember { mutableStateOf<String?>(null) }

    val initialCamera = remember { TimelineCoordinate.initialCamera(System.currentTimeMillis(), zone) }
    var anchorMillis by rememberSaveable { mutableLongStateOf(initialCamera.anchorMillis) }
    var hours by rememberSaveable { mutableDoubleStateOf(initialCamera.hours) }
    var monthText by rememberSaveable { mutableStateOf("") }
    var monthSelectedDateText by rememberSaveable { mutableStateOf("") }
    fun monthViewState(): WebSkinMonthViewState? = runCatching {
        WebSkinMonthViewState(YearMonth.parse(monthText), LocalDate.parse(monthSelectedDateText))
            .takeIf { YearMonth.from(it.selectedDate) == it.month }
    }.getOrNull()
    fun setMonthViewState(view: WebSkinMonthViewState) {
        monthText = view.month.toString()
        monthSelectedDateText = view.selectedDate.toString()
    }
    fun openMonthPage() {
        setMonthViewState(WebSkinMonthProjection.initial(anchorMillis, zone))
        page = Page.MONTH.name
    }
    val camera = TimelineCoordinate.Camera(anchorMillis, TimelineCoordinate.clampHours(hours))
    val todayKey = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val todoEntries = remember(records, todayKey) {
        TodoProjection.entries(records, nowMillis, includeCompleted = true)
    }
    val todoCount = todoEntries.count { !it.completed }
    var memoVisibleLimit by rememberSaveable { mutableIntStateOf(MemoProjectionPaging.PAGE_SIZE) }
    val memoVisibleCount = MemoProjectionPaging.visibleCount(todoEntries.size, memoVisibleLimit)
    val memoRemainingCount = (todoEntries.size - memoVisibleCount).coerceAtLeast(0)
    val preferences = remember(appContext) {
        appContext.getSharedPreferences("powercal-ui", Context.MODE_PRIVATE)
    }
    // Title placement is always outside; old user preference intentionally ignored.

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

    suspend fun openFormalEditor(record: TemporalRecord, occurrenceKey: String? = null) {
        val opened = runCatching {
            withContext(Dispatchers.IO) { EditSession.open(store, record, occurrenceKey) }
        }
        opened.onSuccess {
            editSession = it
            editForm = it.form
            editBaseline = it.baselineForm
            editError = it.lastError
            editBusy = false
            editUiRevision = 0
        }.onFailure {
            message = it.message ?: "Unable to open editor"
        }
    }

    fun openNewEvent(kind: TemporalKind, selection: EditorSelection? = null) {
        if (aiLocked || editSession != null || deckOpen || newEventSession != null) return
        val opened = NewEventEditSession.open(kind, zone = zone, selection = selection)
        newEventSession = opened
        newEventForm = opened.form
        newEventError = null
        newEventBusy = false
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
        SafBackupReader.readText(appContext.contentResolver, uri)
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
        withContext(Dispatchers.IO) { store.recoverAbandonedHumanEdit() }
        reconcile()
        reload()
        if (deckOpen) withContext(Dispatchers.IO) { controller.open(deckInitialId) }
        val onboarding = appContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        if (!onboarding.getBoolean("system-calendar-import-asked", false)) {
            firstCalendarImportPrompt = true
        }
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
    val activeEditSession by rememberUpdatedState(editSession)
    val activeNewEventSession by rememberUpdatedState(newEventSession)
    val checkpointGate = remember { LifecycleCheckpointGate() }
    DisposableEffect(hostLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) checkpointGate.onStart()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                editError = activeEditSession?.lastError
                newEventError = activeNewEventSession?.lastError
                scope.launch { reconcile(); reload() }
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP &&
                webEditing.hasLiveEditor()) {
                PowerCalStores.appScope.launch {
                    // Keep the session/dirty input live on failure; no silent discard.
                    webEditing.checkpoint()
                }
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && deckVisible) {
                // Best effort: the controller serializes this after any autosave, and a failed
                // stage keeps the draft dirty with a retryable error for the next visible session.
                PowerCalStores.appScope.launch { controller.flush() }
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                activeEditSession?.takeIf { checkpointGate.shouldCheckpoint(it) }?.let { session ->
                    // Keep the lease and live memory on failure; a successful checkpoint is the
                    // one durable write for this lifecycle boundary.
                    PowerCalStores.appScope.launch { session.checkpoint() }
                }
                activeNewEventSession?.takeIf { checkpointGate.shouldCheckpoint(it) }?.let { session ->
                    PowerCalStores.appScope.launch {
                        runCatching { session.checkpoint(store) }
                    }
                }
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

    fun replaceChatHistory(messages: List<AiChatMessage>) {
        val bounded = messages.takeLast(200)
        if (bounded == chatHistory) return
        chatHistory = bounded
        chatHistoryRevision++
    }

    fun updateWebChatPrefill(text: String?) {
        webChatPrefillText = text.orEmpty()
        webChatPrefillRevision++
    }

    fun canEditChatHistory(): Boolean = page == Page.AI.name && !aiLocked &&
        !chatBusy && !aiBusy && pendingBatchMode == null && confirmBatch == null &&
        confirmStuckBatchRelease == null && editSession == null && newEventSession == null && !deckOpen &&
        pendingWebAiProposalPublish == null && !aiAttachmentPickerBusy &&
        pendingFileReview == null && pendingImageReview == null

    fun appendChat(role: String, text: String, proposalSource: String? = null) {
        replaceChatHistory(chatHistory + AiChatMessage(
            role = role,
            content = text,
            proposalSource = proposalSource,
            createdAtMillis = if (role == "user") System.currentTimeMillis() else null,
            zoneId = if (role == "user") zone.id else null
        ))
    }

    fun beginBatchConversation(mode: AiBatchRunner.Mode) {
        if (chatBusy || aiBusy || aiLocked || pendingBatchMode != null || confirmBatch != null ||
            confirmStuckBatchRelease != null || webAiProposalReviewSession != null) return
        pendingBatchMode = mode
        page = Page.AI.name
        appendChat("assistant",
            if (mode == AiBatchRunner.Mode.PUBLISH) {
                "准备批量发布。请告诉我要处理哪些草稿：可以说“全部”，也可以按标题、类型或草稿编号筛选。接下来我会列出具体清单，只有你在审核弹窗确认后才会执行。"
            } else {
                "准备批量丢弃草稿（不会删除已经发布的正式事件）。你希望丢弃哪些？可以说“全部”，或描述要筛选的草稿。随后仍需单独审核确认。"
            })
        WebSkinBridgeRuntime.stateRevision++
    }

    fun cancelAiBatchFromSurface() {
        if (pendingBatchMode != null && !chatBusy && !aiBusy && confirmBatch == null) {
            pendingBatchMode = null
            message = "已取消批量范围选择；草稿未修改"
            WebSkinBridgeRuntime.stateRevision++
            return
        }
        scope.launch {
            val current = withContext(Dispatchers.IO) { store.currentAiSession() }
            val requested = current?.takeIf {
                it.state == PowerStore.AiState.RUNNING
            }?.let { session ->
                withContext(Dispatchers.IO) { store.requestAiCancel(session.sessionId) }
            } ?: false
            reload()
            message = when {
                requested -> "已请求停止；已完成结果保留，未完成草稿继续等待审核"
                current?.state == PowerStore.AiState.CANCEL_REQUESTED -> "停止请求已提交，正在等待当前操作结束"
                confirmBatch != null -> "请在当前 Host 审核窗口取消尚未授权的批次"
                else -> "当前没有正在运行的 AI 批次"
            }
            WebSkinBridgeRuntime.stateRevision++
        }
    }

    fun requestStuckBatchRelease() {
        if (stuckBatchReleaseRequestPending || confirmStuckBatchRelease != null) return
        stuckBatchReleaseRequestPending = true
        scope.launch {
            val current = withContext(Dispatchers.IO) { store.currentAiSession() }
            val now = System.currentTimeMillis()
            if (aiLocked && PowerStore.canReleaseStuckAiBatch(current, now)) {
                confirmStuckBatchRelease = current
            } else {
                message = when (current?.state) {
                    PowerStore.AiState.RUNNING -> "请先请求停止 AI 批次；只有停止请求 10 分钟无响应后才能恢复"
                    PowerStore.AiState.CANCEL_REQUESTED -> "停止请求尚未超过 10 分钟无响应窗口，请继续等待"
                    else -> "当前批次已结束或没有待恢复的批次"
                }
            }
            stuckBatchReleaseRequestPending = false
            reload()
            WebSkinBridgeRuntime.stateRevision++
        }
    }

    fun submitAiChatInput(input: String) {
        val text = input.trim()
        if (text.isEmpty() || chatBusy || aiBusy) return
        updateWebChatPrefill("")
        appendChat("user", text)
        val currentHistory = chatHistory.takeLast(80)
        val mode = pendingBatchMode
        chatBusy = true
        scope.launch {
            try {
                if (mode == null) {
                    val settingsForChat = AiSettingsStore.load(appContext)
                    val sharedForChat = if (settingsForChat.shareContext)
                        records.take(60) else emptyList()
                    val response = AiClient(settingsForChat, zone).chat(
                        currentHistory, sharedForChat
                    )
                    appendChat("assistant", if (response.succeeded) response.reply
                        else "AI 请求未完成：" + response.error.orEmpty(),
                        proposalSource = if (response.succeeded) response.proposalSource else null)
                    // Only an explicit-intent marker starts an automatic structured preview.
                    // A casual suggestion remains a button until the user taps it; neither path
                    // writes to PowerStore without proposal review and authorization.
                    val task = if (response.succeeded) response.automaticProposalTask else null
                    if (response.succeeded && response.proposalIntent == ChatProposalIntent.EXPLICIT &&
                        task == null) {
                        appendChat("assistant", "无法安全关联到一项完整的待处理日程请求。" +
                            "为避免把“确认”误当成事件内容，请重新说明要创建或修改的事项、日期和时间。")
                    } else if (task != null) {
                        val settings = AiSettingsStore.load(appContext)
                        val share = if (settings.shareContext) records.take(60) else emptyList()
                        val result = AiClient(settings, zone).interpret(task, share)
                        if (result.proposals.isNotEmpty()) replaceAiProposalReview(result, share)
                        else appendChat("assistant", "无法生成结构化提案：" +
                            (result.warnings.firstOrNull()?.takeIf { it.isNotBlank() }
                                ?: "模型回复未形成可审查提案，请补充或核对日程信息后重试。"))
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
                val client = AiClient(AiSettingsStore.load(appContext), zone)
                if (evidence.format == "ics-problem") {
                    // Known RFC constructs unsupported by our domain are not candidates for
                    // invented appointments. AI may explain and ask, never bypass the codec.
                    val reply = client.analyzeEvidence(evidence.filename,
                        "只解释尚未解决的日历语义，优先询问必要的一个问题。" +
                            "不要把改单次实例当成独立新日程，不得声称已导入。\n" +
                            evidence.text, emptyList())
                    appendChat("assistant", if (reply.succeeded) reply.reply
                        else "部分日历规则尚无法安全处理，已保存其他草稿。" +
                            "AI 当前不可用，可稍后重新选择文件。")
                    return@launch
                }
                // A document is a source of untrusted facts, never an execution instruction.
                // Try the existing typed proposal parser before falling back to clarification.
                val interpretation = client.interpret(
                    "从以下文件提取真实日程，仅生成候选项；缺少时间不可猜测。文件：" +
                        evidence.filename + "\n" + evidence.text, emptyList())
                if (interpretation.proposals.isNotEmpty()) {
                    replaceAiProposalReview(interpretation)
                    appendChat("assistant", "已从文件识别 ${interpretation.proposals.size} 项候选日程，" +
                        "请核对后选择生成草稿；尚未正式发布。" +
                        if (evidence.truncated) " 文件内容不完整，请核对遗漏。" else "")
                } else {
                    val reply = client.analyzeEvidence(evidence.filename,
                        (if (hint.isBlank()) "" else "文件预检信息：$hint\n") + evidence.text,
                        prior)
                    appendChat("assistant", if (reply.succeeded) reply.reply
                        else "文件暂时无法完成分析，已保留现有日历数据。可以稍后重试。" )
                }
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

    fun finishWebAiProposalRequest(
        session: WebAiProposalReviewSession,
        handledIndices: Set<Int>
    ) {
        if (handledIndices.isEmpty() ||
            webAiProposalReviewSession?.reviewId != session.reviewId) return
        val remaining = session.interpretation.copy(proposals =
            session.interpretation.proposals.filterIndexed { index, _ -> index !in handledIndices })
        webAiProposalReviewSession = if (remaining.proposals.isEmpty()) null
            else session.replace(remaining)
    }

    fun captureAiProposalTargets(
        session: WebAiProposalReviewSession,
        positions: List<Int>
    ): Map<Long, TemporalRecord>? = synchronized(store) {
        val targets = linkedMapOf<Long, TemporalRecord>()
        for (position in positions) {
            val proposal = session.interpretation.proposals.getOrNull(position)
                ?: return@synchronized null
            if (proposal.operation == dev.powerfulcalendar.core.DraftOperation.CREATE) {
                if (proposal.eventSeq != null) return@synchronized null
                continue
            }
            val seq = proposal.eventSeq ?: return@synchronized null
            val expectedRevision = session.expectedTargetRevisions[seq]
                ?: return@synchronized null
            val current = store.get(seq) ?: return@synchronized null
            if (current.revision != expectedRevision) return@synchronized null
            targets[seq] = current
        }
        targets
    }

    fun resolveWebAiProposalAction(action: WebSkinAction.ResolveAiProposal) {
        val session = webAiProposalReviewSession
        if (page != Page.AI.name || aiLocked || chatBusy || aiBusy || editSession != null ||
            deckOpen || newEventSession != null || pendingBatchMode != null || confirmBatch != null ||
            pendingWebAiProposalPublish != null || pendingFileReview != null ||
            pendingImageReview != null) return
        if (session == null || session.reviewId != action.reviewId) {
            appendChat("assistant", "这组 AI 提案已更新或过期，请按当前审核卡重新选择。")
            WebSkinBridgeRuntime.stateRevision++
            return
        }
        val selected = if (action.decision == AiProposalDecision.SUPPLEMENT) emptyList()
            else WebAiProposalProjection.validSelection(session, action.indices)
                ?: run {
                    appendChat("assistant", "所选提案已变化或不可操作；没有执行任何写入。")
                    WebSkinBridgeRuntime.stateRevision++
                    return
                }
        when (action.decision) {
            AiProposalDecision.SUPPLEMENT -> {
                appendChat("assistant", "请在下方聊天框补充或更正提案内容；现有候选不会因此自动修改或发布。")
                WebSkinBridgeRuntime.stateRevision++
            }
            AiProposalDecision.REJECT -> {
                finishWebAiProposalRequest(session, selected.toSet())
                appendChat("assistant", "已拒绝所选 ${selected.size} 项 AI 提案；没有创建草稿或修改正式事件。")
                WebSkinBridgeRuntime.stateRevision++
            }
            AiProposalDecision.STAGE -> {
                aiBusy = true
                PowerCalStores.appScope.launch {
                    try {
                        val applied = withContext(Dispatchers.IO) {
                            synchronized(store) {
                                if (captureAiProposalTargets(session, selected) == null) null
                                else AiDraftService(store).applyReviewedProposals(
                                    session.interpretation, selected.toSet(), publish = false)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (applied == null) {
                                appendChat("assistant", "目标事件已变化或无法核对；没有写入草稿，请重新生成提案。")
                            } else {
                                finishWebAiProposalRequest(session, applied.handledIndices)
                                val refreshed = runCatching { reload() }.isSuccess
                                appendChat("assistant", "已写入 ${applied.stagedCount} 张待处理草稿；" +
                                    (if (applied.alreadyResolvedCount > 0)
                                        "已有收据确认处理 ${applied.alreadyResolvedCount} 项；" else "") +
                                    applied.warnings.take(2).joinToString("；") +
                                    (if (!refreshed) " · 草稿已写入，但界面刷新失败" else ""))
                            }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        withContext(Dispatchers.Main) {
                            appendChat("assistant", "写入草稿未完成：" +
                                (error.message ?: error.javaClass.simpleName) + "；请核对后重试。")
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            aiBusy = false
                            WebSkinBridgeRuntime.stateRevision++
                        }
                    }
                }
            }
            AiProposalDecision.PUBLISH -> {
                val proposals = selected.map { session.interpretation.proposals[it] }
                if (proposals.any { !WebAiProposalProjection.isPublishable(it) }) {
                    appendChat("assistant", "所选项目包含未补全或不可发布的提案；请先写入草稿或补充细节。")
                    WebSkinBridgeRuntime.stateRevision++
                    return
                }
                aiBusy = true
                PowerCalStores.appScope.launch {
                    try {
                        val targets = withContext(Dispatchers.IO) {
                            captureAiProposalTargets(session, selected)
                        }
                        withContext(Dispatchers.Main) {
                            if (webAiProposalReviewSession?.reviewId != session.reviewId) {
                                appendChat("assistant", "提案在审核期间已更新；没有执行正式操作。")
                            } else if (targets == null) {
                                appendChat("assistant", "目标事件已变化或不可用；请重新读取后生成提案。没有执行正式操作。")
                            } else {
                                pendingWebAiProposalPublish = WebAiProposalPublishReview(
                                    reviewId = session.reviewId,
                                    interpretation = session.interpretation,
                                    selectedPositions = selected,
                                    targetSnapshots = targets
                                )
                            }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        withContext(Dispatchers.Main) {
                            appendChat("assistant", "无法准备 Host 审核：" +
                                (error.message ?: error.javaClass.simpleName))
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            aiBusy = false
                            WebSkinBridgeRuntime.stateRevision++
                        }
                    }
                }
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
            // Group by RFC UID before importing: a changed recurrence instance and its master
            // form one atomic semantic unit. Problems in independent units cannot block good ones.
            val plan = withContext(Dispatchers.Default) { IcsImportPlanner.prepare(text, zone) }
            if (plan.units == 0) {
                message = "文件里没有可导入的日历事件"
                return
            }
            val result = withContext(Dispatchers.IO) {
                // Exact title/start/end comparison is only an import guard, not a promise
                // of cross-device identity or a substitute for permanent invocation receipts.
                fun identity(form: DraftForm): List<Any?> = listOf(
                    form.kind, form.title.trim(), form.parseStart(), form.parseEnd(),
                    form.parseDeadline(), form.recurrence, form.zoneId,
                    form.location.trim(), form.notes.trim(), form.reminderMinutes
                )
                val known = (
                    store.all().map { DraftForm.from(it) } +
                        store.pendingDrafts().filter { it.operation == DraftOperation.CREATE }
                            .map { it.form }
                    ).map(::identity).toMutableSet()
                var duplicated = 0
                val created = mutableListOf<Long>()
                val failed = mutableListOf<String>()
                val receipt = ImportCheckpoint.recent(appContext)
                    .firstOrNull { it.fingerprint == plan.fingerprint }
                // A receipt is a recovery hint, not permission to skip missing/discarded cards.
                receipt?.draftIds?.filter { store.draft(it) != null }?.forEach { created += it }
                plan.candidates.forEach { candidate ->
                    val form = candidate.form
                    if (!known.add(identity(form))) {
                        duplicated++
                        return@forEach
                    }
                    runCatching { store.newDraft(form, actor = "user") }
                        .onSuccess { card ->
                            created += card.draftId
                            ImportCheckpoint.record(appContext, ImportCheckpoint.Snapshot(
                                plan.fingerprint, created.distinct(), plan.problems.size))
                        }.onFailure { failed += candidate.sourceId.take(80) }
                }
                ImportCheckpoint.record(appContext, ImportCheckpoint.Snapshot(
                    plan.fingerprint, created.distinct(), plan.problems.size))
                Triple(created.distinct(), duplicated, failed)
            }
            reload()
            result.first.firstOrNull()?.let { openDeckAt(it) }
            message = "ICS 处理完成：${result.first.size} 张已保存草稿，${result.second} 项重复，" +
                "${plan.problems.size + result.third.size} 项待处理。尚未正式发布。" +
                (plan.problems.firstOrNull()?.reason?.let { " 首个问题：${it.take(130)}" } ?: "")
            // Do not send the whole calendar to AI. Only explicitly unresolved components
            // may be explained or clarified, subject to separate provider-bound consent.
            if (plan.problems.isNotEmpty()) {
                val settings = AiSettingsStore.load(appContext)
                if (settings.configured) {
                    val focused = plan.problems.take(5).mapIndexed { index, item ->
                        "问题 ${index + 1}：${item.reason}\n" + item.evidence.take(2400)
                    }.joinToString("\n\n").take(20_000)
                    val partial = plan.problems.size > 5
                    val evidence = AiFileEvidence(
                        filename = "ICS 未解决项目",
                        format = "ics-problem",
                        text = focused,
                        truncated = partial,
                        note = "仅包含本地未解决的前 ${minOf(5, plan.problems.size)} 项；" +
                            "已有可靠草稿不会发送给 AI。"
                    )
                    if (!partial && AiFileFallbackConsent.allowed(appContext, settings)) {
                        submitEvidence(evidence)
                    } else pendingFileReview = uri to evidence
                }
            }
        } catch (error: Exception) {
            message = "ICS 预览失败：" + (error.message ?: "未知错误") + "；请检查待处理是否有已生成草稿"
        }
    }

    suspend fun importSystemCalendar() {
        var imported = 0
        var batchId: String? = null
        try {
            val incoming = withContext(Dispatchers.IO) { SystemCalendar.readUpcoming(appContext) }
            if (incoming.isEmpty()) {
                message = "系统日历没有可导入的事件"
                return
            }
            val warnings = mutableListOf<String>()
            var importFailure: Exception? = null
            var rejected = 0
            try {
                // Publish the committed ID before withContext returns, so finally can close it
                // even if cancellation wins at the dispatcher-return boundary.
                beginSystemCalendarBatchTracked(store) { batchId = it }
                val activeBatchId = checkNotNull(batchId)
                withContext(Dispatchers.IO) {
                    incoming.forEachIndexed { index, event ->
                        val external = event.externalId ?: ("cal:" + event.startMillis + ":" + event.title)
                        when (val result = store.importStructured(
                            event.toRecord(), external, activeBatchId, "android-calendar"
                        )) {
                            is PowerStore.ImportOutcome.Imported -> imported++
                            is PowerStore.ImportOutcome.AlreadyImported -> Unit
                            is PowerStore.ImportOutcome.Rejected -> {
                                rejected++
                                warnings += "第 ${index + 1} 条系统日历事件未导入：${result.reason}"
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                importFailure = error
                warnings += "系统日历导入中断；已提交记录可通过重试自动去重"
            } finally {
                batchId?.let { createdBatchId ->
                    try {
                        finishSystemCalendarBatch(store, createdBatchId, warnings.toList())
                    } catch (finishError: Exception) {
                        if (importFailure == null) importFailure = finishError
                        else importFailure?.addSuppressed(finishError)
                    }
                }
            }
            importFailure?.let { throw it }
            reload()
            reconcile()
            message = if (rejected == 0) {
                "已从系统日历导入 $imported 条（一次性快照，不是双向同步）"
            } else {
                "已从系统日历导入 $imported 条；有 $rejected 条未导入，可重试（一次性快照，不是双向同步）"
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (batchId != null) {
                if (imported > 0) {
                    runCatching { reload(); reconcile() }.onFailure(error::addSuppressed)
                }
                message = "系统日历导入中断，已导入 $imported 条；可重试，已写入记录会自动去重：" +
                    (error.message ?: "未知错误")
            } else {
                message = "读取系统日历失败：" + (error.message ?: "未知错误")
            }
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
        WebSkinBridgeRuntime.stateRevision++
    }
    val openIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) scope.launch { importIcs(uri) } }
    val openAiDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) aiAttachmentPickerBusy = false
        else scope.launch {
            try {
                // Every attachment entry must route a calendar file to the full local parser,
                // before any bounded AI text extraction or network permission check.
                val isCalendar = withContext(Dispatchers.IO) {
                    val mime = appContext.contentResolver.getType(uri)
                    val name = runCatching {
                        appContext.contentResolver.query(uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else ""
                        }
                    }.getOrNull().orEmpty()
                    val magic = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        val buffer = ByteArray(1024)
                        val n = stream.read(buffer)
                        if (n > 0) String(buffer, 0, n, Charsets.UTF_8)
                            .trimStart('\uFEFF', ' ', '\r', '\n', '\t')
                            .startsWith("BEGIN:VCALENDAR", ignoreCase = true)
                        else false
                    } ?: false
                    mime == "text/calendar" || name.endsWith(".ics", ignoreCase = true) || magic
                }
                if (isCalendar) importIcs(uri)
                else {
                    val evidence = withContext(Dispatchers.IO) {
                        AiFileEvidenceReader.read(appContext, uri)
                    }
                    val settings = AiSettingsStore.load(appContext)
                    if (!evidence.truncated && AiFileFallbackConsent.allowed(appContext, settings)) {
                        submitEvidence(evidence)
                    } else pendingFileReview = uri to evidence
                }
            } catch (error: Exception) {
                message = "无法安全提取文件：" + (error.message ?: error.javaClass.simpleName) +
                    "；没有上传给 AI，也没有创建事件。"
            } finally {
                aiAttachmentPickerBusy = false
            }
        }
    }
    val importSkin = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { skins.install(uri) } }
                .onSuccess { result ->
                    // Importing must never execute an unreviewed skin automatically.
                    skinChoices = skins.installed()
                    selectedSkin = skins.currentId()
                    message = "皮肤已导入，但未启用：" + result.name +
                        "；请在设置中手动选择。"
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
            runCatching { withContext(Dispatchers.IO) { skins.exportBundledClear(uri) } }
                .onSuccess { message = "已导出完整 Clear v3 候选皮肤；正式替代原生版前仍需完成功能覆盖验收" }
                .onFailure { error -> message = "导出皮肤模板失败：" + error.message }
        }
    }
    val exportInstalledSkin = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val id = pendingSkinExportId
        pendingSkinExportId = null
        if (uri != null && id != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { skins.exportInstalled(id, uri) }
            }.onSuccess {
                message = "已导出皮肤包 $id；可在 Skin Studio 导入后继续编辑"
            }.onFailure { error ->
                message = "导出已安装皮肤失败：" + (error.message ?: "未知错误")
            }
        }
    }

    val exportIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = IcsCodec.exportActivities(
                        records.filter { it.kind == TemporalKind.ACTIVITY }
                    )
                    appContext.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                        it.write(body)
                    } ?: error("无法写入所选文件")
                }
            }.onSuccess {
                message = "已导出 Activity ICS；请使用原生备份保留闹钟、截止、备忘与完成状态"
            }.onFailure { error ->
                message = "ICS 导出受阻：" + (error.message ?: error.javaClass.simpleName)
            }
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
                    else -> message = "恢复失败：" + RestoreFailureExplanation.explain(error)
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
    ) { uri ->
        pendingImageReview = uri
        aiAttachmentPickerBusy = false
    }

    fun canLaunchWebAiAttachmentPicker(): Boolean =
        page == Page.AI.name && !aiLocked && !chatBusy && !aiBusy &&
            !aiAttachmentPickerBusy && editSession == null && !deckOpen &&
            newEventSession == null && pendingBatchMode == null && confirmBatch == null &&
            pendingWebAiProposalPublish == null && pendingFileReview == null &&
            pendingImageReview == null && webAiProposalReviewSession == null

    fun launchWebAiAttachmentPicker(image: Boolean) {
        if (!canLaunchWebAiAttachmentPicker()) return
        aiAttachmentPickerBusy = true
        runCatching {
            if (image) ocrPicker.launch("image/*")
            else openAiDocument.launch(arrayOf("*/*"))
        }.onFailure { error ->
            aiAttachmentPickerBusy = false
            message = "无法打开附件选择器：" + (error.message ?: error.javaClass.simpleName)
        }
    }

    val tools = page == Page.TOOLS.name
    // Tools/Settings is a protected native recovery page, never rendered by Web skins.
    val fullCanvasWeb = PowerCalSkinRegistry.active.web?.fullCanvas == true && !tools
    val webBridgeOwner = remember { Any() }
    androidx.compose.runtime.SideEffect {
        WebSkinBridgeRuntime.owner = webBridgeOwner
        WebSkinBridgeRuntime.onEdit = { command ->
            val selection = if (command is WebEditCommand.CreateDeck)
                newEventSelectionForPage(page, camera, zone, monthViewState()?.selectedDate) else null
            val moveContext = if (page == Page.TIMELINE.name &&
                command is WebEditCommand.MoveTimelineEvent) {
                val projectedItems = WebSkinTimelineProjection.build(
                    records, camera, nowMillis, zone
                ).getJSONArray("items")
                val visibleRevisions = linkedMapOf<Long, Long>()
                for (index in 0 until projectedItems.length()) {
                    val item = projectedItems.optJSONObject(index) ?: continue
                    visibleRevisions[item.getLong("eventSeq")] = item.getLong("revision")
                }
                TimelineMoveContext(camera, zone, visibleRevisions)
            } else null
            val response = webEditing.execute(
                command, newEventZone = zone, newEventSelection = selection,
                timelineMoveContext = moveContext
            )
            val status = response.optString("status")
            if(status in setOf("saved","staged","committed","discarded","restored")) {
                // A completed host transaction must refresh the app-wide projections;
                // an unsuccessful UI refresh must not turn success into a retryable write.
                runCatching { reload() }
                    .onFailure { message = "操作已完成，但界面同步失败；请刷新查看" }
            }
            if(status in setOf("saved","committed","restored")) {
                runCatching { reconcile() }
                    .onFailure { message = "操作已保存，提醒同步尚未完成" }
            }
            // Web editor state lives in the host, not the WebView. Publish on
            // every completed command, including a conflict or failed save.
            WebSkinBridgeRuntime.stateRevision++
            response
        }
        WebSkinBridgeRuntime.onAction = { action ->
            when (action) {
                is WebSkinAction.Routed -> when (action.name) {
                    "timeline" -> page = Page.TIMELINE.name
                    "month" -> openMonthPage()
                    "memo" -> {
                        page = Page.MEMO.name
                        scope.launch { reload() }
                    }
                    "ai" -> page = Page.AI.name
                    "settings" -> page = Page.SETTINGS.name
                    "nativeSettings" -> page = Page.TOOLS.name
                    "openDeck" -> scope.launch { openDeckAt(null) }
                    "today" -> {
                        nowMillis = System.currentTimeMillis()
                        if (page == Page.MONTH.name) {
                            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                            WebSkinMonthProjection.today(today)?.let(::setMonthViewState)
                        } else {
                            val today = TimelineCoordinate.todayCamera(nowMillis, zone)
                            anchorMillis = today.anchorMillis; hours = today.hours
                        }
                    }
                    else -> Unit
                }
                WebSkinAction.RequestNotificationPermission -> {
                    when (notificationPermissionLaunch(page, android.os.Build.VERSION.SDK_INT)) {
                        NotificationPermissionLaunch.UNAVAILABLE -> Unit
                        NotificationPermissionLaunch.REQUEST_RUNTIME_PERMISSION -> {
                            if (android.os.Build.VERSION.SDK_INT >=
                                android.os.Build.VERSION_CODES.TIRAMISU
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        NotificationPermissionLaunch.OPEN_APP_NOTIFICATION_SETTINGS ->
                            context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, appContext.packageName))
                    }
                    WebSkinBridgeRuntime.stateRevision++
                }
                is WebSkinAction.InvokeOption -> when {
                    aiLocked || editSession != null -> Unit
                    action.id == "calendar.import.file" ->
                        openAiDocument.launch(arrayOf("*/*"))
                    action.id == "calendar.import.system" -> {
                        if (ContextCompat.checkSelfPermission(appContext,
                                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                        ) scope.launch { importSystemCalendar() }
                        else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    }
                    action.id == "data.legacy.migrate" -> confirmingLegacy = true
                    else -> Unit
                }
                is WebSkinAction.PanTimeline -> {
                    if (page == Page.TIMELINE.name) {
                        val next = runCatching { Math.addExact(anchorMillis,
                            action.deltaMinutes.toLong() * 60_000L) }.getOrNull()
                        if (next != null) {
                            val year = java.time.Instant.ofEpochMilli(next).atZone(zone).year
                            if (year in 1900..2200) anchorMillis = next
                        }
                    }
                }
                is WebSkinAction.ZoomTimeline -> {
                    if (page == Page.TIMELINE.name) {
                        val multiplier = when (action.direction) {
                            TimelineZoomDirection.IN -> 1.0 / 1.5
                            TimelineZoomDirection.OUT -> 1.5
                        }
                        hours = TimelineCoordinate.clampHours(hours * multiplier)
                    }
                }
                WebSkinAction.ResetTimelineZoom -> {
                    if (page == Page.TIMELINE.name) hours = 24.0
                }
                is WebSkinAction.JumpTimelineDay -> {
                    if (page == Page.TIMELINE.name || page == Page.MONTH.name) {
                        val date = java.time.LocalDate.parse(action.date)
                        anchorMillis = date.atTime(12, 0).atZone(zone)
                            .toInstant().toEpochMilli()
                        page = Page.TIMELINE.name
                    }
                }
                is WebSkinAction.BrowseMonth -> {
                    if (page == Page.MONTH.name) {
                        val current = monthViewState()
                            ?: WebSkinMonthProjection.initial(anchorMillis, zone)
                        WebSkinMonthProjection.browseMonth(current, action.deltaMonths)
                            ?.let(::setMonthViewState)
                    }
                }
                is WebSkinAction.SelectMonthDate -> if (page == Page.MONTH.name) {
                    val current = monthViewState()
                        ?: WebSkinMonthProjection.initial(anchorMillis, zone)
                    WebSkinMonthProjection.selectDate(current, action.date)
                        ?.let(::setMonthViewState)
                }
                is WebSkinAction.SelectMonth -> if (page == Page.MONTH.name) {
                    WebSkinMonthProjection.selectMonth(action.month)
                        ?.let(::setMonthViewState)
                }
                is WebSkinAction.CreateDraft -> if (!aiLocked && editSession == null) {
                    val kind = TemporalKind.valueOf(action.kind)
                    val day = if (page == Page.MONTH.name)
                        monthViewState()?.selectedDate
                            ?: Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()
                    else Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()
                    openNewEvent(kind, EditorSelection.OnDate(day))
                }
                WebSkinAction.CreateMemo -> if (!aiLocked && editSession == null) {
                    openNewEvent(TemporalKind.MEMO)
                }
                WebSkinAction.LoadMoreMemo -> if (page == Page.MEMO.name) {
                    val next = MemoProjectionPaging.nextLimit(memoVisibleLimit, todoEntries.size)
                    if (next != memoVisibleLimit) {
                        memoVisibleLimit = next
                        WebSkinBridgeRuntime.stateRevision++
                    }
                }
                is WebSkinAction.ToggleMemo -> if (!aiLocked && editSession == null) {
                    scope.launch {
                        val current = withContext(Dispatchers.IO) { store.get(action.eventSeq) }
                        val visible = current?.let {
                            TodoProjection.entries(listOf(it), System.currentTimeMillis(),
                                includeCompleted = true)
                        }?.firstOrNull { it.occurrenceKey == action.occurrenceKey }
                        if (current == null || current.revision != action.expectedRevision ||
                            visible == null || visible.completed != action.completed) {
                            message = "Memo 已变化，请刷新"
                        } else {
                            val result = withContext(Dispatchers.IO) {
                                store.setCompleted(action.eventSeq, action.occurrenceKey,
                                    !action.completed, action.expectedRevision)
                            }
                            if (!result) message = "Memo 状态已变化，请刷新"
                        }
                        reload()
                    }
                }
                is WebSkinAction.SendChat -> if (!aiLocked && !chatBusy && !aiBusy &&
                    editSession == null && confirmBatch == null && pendingWebAiProposalPublish == null &&
                    !aiAttachmentPickerBusy && pendingFileReview == null &&
                    pendingImageReview == null) {
                    submitAiChatInput(action.text)
                }
                is WebSkinAction.ClearChat -> if (canEditChatHistory()) {
                    when (val result = WebAiChatHistory.clear(
                        chatHistory, action.historyRevision, chatHistoryRevision
                    )) {
                        is WebChatHistoryMutation.Applied -> {
                            replaceChatHistory(result.messages)
                            pendingBatchMode = null
                            webAiProposalReviewSession = null
                            message = "聊天已清空；事件和待处理草稿未更改"
                        }
                        WebChatHistoryMutation.NoChange -> message = "聊天记录已经为空"
                        else -> message = "聊天已变化，请刷新后重试清理"
                    }
                }
                is WebSkinAction.RewindChat -> if (canEditChatHistory()) {
                    val mode = if (action.editAndResend) WebChatRewindMode.EDIT_AND_RESEND
                        else WebChatRewindMode.RETRACT
                    when (val result = WebAiChatHistory.rewind(
                        chatHistory, action.messageId, mode,
                        action.historyRevision, chatHistoryRevision
                    )) {
                        is WebChatHistoryMutation.Applied -> {
                            replaceChatHistory(result.messages)
                            pendingBatchMode = null
                            if (result.updateComposer) updateWebChatPrefill(result.prefillText)
                            message = if (result.prefillText != null)
                                "已回退后续对话；原消息已放入输入框，可修改后重新发送"
                            else "已回退这条消息及后续对话；日历和草稿未更改"
                        }
                        WebChatHistoryMutation.NoChange -> message = "这条回复后没有更多对话可回退"
                        else -> message = "聊天已变化或目标已不存在，请刷新后重试"
                    }
                }
                is WebSkinAction.PrepareAiBatch -> if (page == Page.AI.name && !aiLocked &&
                    !chatBusy && !aiBusy && pendingBatchMode == null && editSession == null && confirmBatch == null &&
                    webAiProposalReviewSession == null && pendingWebAiProposalPublish == null && !aiAttachmentPickerBusy &&
                    pendingFileReview == null && pendingImageReview == null) {
                    beginBatchConversation(action.mode)
                }
                WebSkinAction.CancelAiBatch -> if (page == Page.AI.name &&
                    pendingWebAiProposalPublish == null && pendingFileReview == null &&
                    pendingImageReview == null) {
                    cancelAiBatchFromSurface()
                }
                WebSkinAction.ReleaseStuckAiBatch -> if (page == Page.AI.name) {
                    requestStuckBatchRelease()
                }
                WebSkinAction.PickAiFile -> launchWebAiAttachmentPicker(image = false)
                WebSkinAction.PickAiImage -> launchWebAiAttachmentPicker(image = true)
                is WebSkinAction.ResolveAiProposal -> resolveWebAiProposalAction(action)
                is WebSkinAction.OpenEvent -> scope.launch {
                    val current = withContext(Dispatchers.IO) { store.get(action.eventSeq) }
                    val validated = validateOpenEvent(action, current, !aiLocked && editSession == null)
                    if (current == null || current.revision != action.expectedRevision) {
                        message = "Event changed or is no longer available"
                    } else if (validated == null) {
                        message = "Calendar editor is currently unavailable"
                    } else {
                        openFormalEditor(validated, action.occurrenceKey)
                    }
                }
            }
        }
        WebSkinBridgeRuntime.state = {
            val notificationStatus = notificationPermissionStatus(
                android.os.Build.VERSION.SDK_INT,
                ReminderScheduler.notificationsAllowed(appContext)
            )
            JSONObject().put("bridgeVersion", WebSkinBridgeRuntime.VERSION).put("page", page)
                .put("aiLocked", aiLocked).put("pendingCount", pendingCount)
                .put("memoCount", todoCount)
                .put("memoEntries", WebSkinMemoProjection.build(
                    if (page == Page.MEMO.name) todoEntries.take(memoVisibleCount) else emptyList()
                ))
                .put("memoTruncated", page == Page.MEMO.name && memoRemainingCount > 0)
                .put("memoRemainingCount", if (page == Page.MEMO.name) memoRemainingCount else 0)
                .put("chatBusy", chatBusy || aiBusy || pendingWebAiProposalPublish != null)
                .put("chatHistoryRevision", chatHistoryRevision)
                .put("chatHistoryActionsAvailable", canEditChatHistory())
                .put("chatPrefillText", webChatPrefillText)
                .put("chatPrefillRevision", webChatPrefillRevision)
                .put("aiBatch", WebAiBatchProjection.project(
                    session = aiSession,
                    preparationMode = pendingBatchMode,
                    reviewMode = confirmBatch?.mode,
                    reviewCards = confirmBatch?.cards,
                    progress = webAiBatchProgress,
                    nowMillis = nowMillis
                ))
                .put("chatAttachmentAvailable", canLaunchWebAiAttachmentPicker())
                .put("chatAttachmentBusy", aiAttachmentPickerBusy ||
                    pendingFileReview != null || pendingImageReview != null)
                .put("chatMessages", WebAiChatHistory.project(
                    if (page == Page.AI.name) chatHistory else emptyList()
                ))
                .put("aiProposalReview", if (page == Page.AI.name) {
                    webAiProposalReviewSession?.let(WebAiProposalProjection::project) ?: JSONObject.NULL
                } else JSONObject.NULL)
                .put("reducedMotion", effects.reduceMotion)
                .put("notificationPermission", if (page == Page.SETTINGS.name) {
                    JSONObject().put("required", notificationStatus.required)
                        .put("allowed", notificationStatus.allowed)
                } else JSONObject())
                .put("editing", webEditing.snapshot())
                .put("optionGroups", SemanticOptions.toJson(
                    enabled = !aiLocked && editSession == null))
                .put("timeline", WebSkinTimelineProjection.build(
                    records, camera, nowMillis, zone))
                .put("month", if (page == Page.MONTH.name)
                    WebSkinMonthProjection.build(
                        records,
                        monthViewState() ?: WebSkinMonthProjection.initial(anchorMillis, zone),
                        zone
                    )
                    else JSONObject())
                .put("events", org.json.JSONArray(records.take(300).map { record ->
                    JSONObject().put("eventSeq", record.eventSeq).put("revision", record.revision)
                        .put("kind", record.kind.name).put("title", record.title.take(300))
                        .put("anchorMillis", record.anchorMillis()).put("endMillis", record.spanEndMillis())
                }))
        }
        WebSkinBridgeRuntime.stateRevision++
    }
    DisposableEffect(webBridgeOwner) {
        onDispose {
            if (WebSkinBridgeRuntime.owner === webBridgeOwner) {
                WebSkinBridgeRuntime.owner = null
                WebSkinBridgeRuntime.onAction = {}
                WebSkinBridgeRuntime.onEdit = { cmd ->
                    JSONObject().put("requestId",cmd.requestId).put("status","unavailable")
                }
                WebSkinBridgeRuntime.state = {
                    JSONObject().put("bridgeVersion", WebSkinBridgeRuntime.VERSION)
                }
            }
        }
    }

    // System back on the tools page returns to the timeline instead of leaving the app.
    androidx.activity.compose.BackHandler(enabled = page != Page.TIMELINE.name && !deckOpen) {
        page = Page.TIMELINE.name
        scope.launch { reload() }
    }

    // The hardware Back key is a secondary recovery route for an unusable Web skin.
    androidx.activity.compose.BackHandler(
        enabled = fullCanvasWeb && page == Page.TIMELINE.name &&
            !deckOpen && editSession == null && !webEditing.hasLiveEditor()
    ) { page = Page.TOOLS.name }

    androidx.activity.compose.BackHandler(
        enabled = fullCanvasWeb && page == Page.SETTINGS.name
    ) { page = Page.TIMELINE.name }

    // An active Web editor consumes Back before the native page navigation.
    // It performs the same durable checkpoint as a skin change, without
    // silently discarding any dirty form content.
    androidx.activity.compose.BackHandler(
        enabled = fullCanvasWeb && webEditing.hasLiveEditor()
    ) {
        scope.launch {
            if (webEditing.prepareSkinChange()) WebSkinBridgeRuntime.stateRevision++
            else message = "编辑内容仍未安全保存，请检查后再退出"
        }
    }

    SkinAppShell(
        forceNative = tools,
        onWebSkinFailure = {
            scope.launch {
                val safe = webEditing.prepareSkinChange()
                val persisted = skins.selectNativeForEmergencyRecovery()
                selectedSkin = if (persisted) skins.currentId() else "powercal-default"
                page = Page.TOOLS.name
                message = when {
                    !safe && !persisted -> "皮肤已失效，编辑检查点失败且原生回退设置未能确认保存；当前已进入安全界面，请勿强制结束应用。"
                    !safe -> "皮肤已失效，编辑检查点失败；已进入安全界面，切勿强制结束应用。"
                    persisted -> "皮肤加载失败，编辑已存档并恢复安全界面。"
                    else -> "编辑已存档并切换至原生界面，但未能确认回退设置已保存；请在皮肤设置中再次选择原生皮肤。"
                }
            }
        }
    ) {
        val keyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        SkinPageTransition(
            page, Modifier.fillMaxSize().padding(bottom =
                if (keyboardVisible || fullCanvasWeb) 0.dp else 80.dp)
        ) { presentedPage ->
        if (presentedPage == Page.TOOLS.name) {
            ToolsPage(
                diagnostics = diagnostics,
                records = records,
                pendingCount = pendingCount,
                aiLocked = aiLocked,
                aiSession = aiSession,
                canReleaseStuckBatch = PowerStore.canReleaseStuckAiBatch(aiSession, nowMillis),
                effects = effects,
                skinChoices = skinChoices,
                selectedSkin = selectedSkin,
                onSelectSkin = { id ->
                    if (id != selectedSkin) {
                        skinToActivate = skinChoices.firstOrNull { it.id == id }
                    }
                },
                onImportSkin = {
                    importSkin.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                onExportOriginalSkin = { exportOriginalSkin.launch("PowerCal-Clear-Preview.pcskin") },
                onExportInstalledSkin = { item ->
                    pendingSkinExportId = item.id
                    message = "请选择保存位置以导出 ${item.name}"
                    exportInstalledSkin.launch(item.id + ".pcskin")
                },
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
                    cancelAiBatchFromSurface()
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
                    requestStuckBatchRelease()
                }
            )
        } else if (presentedPage == Page.AI.name) {
            AiChatScreen(
                messages = chatHistory,
                busy = chatBusy || aiBusy || aiLocked || confirmBatch != null ||
                    pendingWebAiProposalPublish != null,
                batchActionsBusy = pendingBatchMode != null || confirmBatch != null || aiSession?.isActive == true,
                batchSelectionActive = pendingBatchMode != null,
                onCancelBatchSelection = { cancelAiBatchFromSurface() },
                pendingCount = pendingCount,
                proposalReview = proposalReview,
                onApplyProposals = { selected, publish ->
                    val session = webAiProposalReviewSession
                    val snapshot = session?.interpretation
                    if (session != null && snapshot != null && selected.isNotEmpty() &&
                        !aiBusy && !chatBusy && pendingWebAiProposalPublish == null) {
                        aiBusy = true
                        scope.launch {
                            try {
                                val applied = withContext(Dispatchers.IO) {
                                    AiDraftService(store).applyReviewedProposals(
                                        snapshot, selected.toSet(), publish)
                                }
                                if (webAiProposalReviewSession?.reviewId == session.reviewId) {
                                    val remaining = snapshot.copy(proposals = snapshot.proposals
                                        .filterIndexed { i, _ -> i !in applied.handledIndices })
                                    webAiProposalReviewSession = if (remaining.proposals.isEmpty()) null
                                        else session.replace(remaining)
                                }
                                reload()
                                if (publish && applied.publishedCount > 0) reconcile()
                                appendChat("assistant", "结构化操作结果：写入或复用待处理草稿 " +
                                    applied.stagedCount + "，正式发布 " + applied.publishedCount +
                                    (if (applied.alreadyResolvedCount > 0)
                                        "，已有收据确认已处理 " + applied.alreadyResolvedCount else "") +
                                    "。" + applied.warnings.take(2).joinToString("；"))
                                WebSkinBridgeRuntime.stateRevision++
                            } finally {
                                aiBusy = false
                            }
                        }
                    }
                },
                onDiscardProposals = { selected ->
                    webAiProposalReviewSession?.let { session ->
                        val remaining = session.interpretation.copy(proposals =
                            session.interpretation.proposals.filterIndexed { i, _ -> i !in selected })
                        webAiProposalReviewSession = if (remaining.proposals.isEmpty()) null
                            else session.replace(remaining)
                        WebSkinBridgeRuntime.stateRevision++
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
                    replaceChatHistory(emptyList())
                    pendingBatchMode = null
                    webAiProposalReviewSession = null
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
                    replaceChatHistory(chatHistory.take((lastIndex + 1).coerceAtLeast(0)))
                    pendingBatchMode = null
                }
            )
        } else if (presentedPage == Page.MEMO.name) {
            MemoCenterScreen(
                items = todoEntries, zone = zone, busy = aiLocked,
                onBack = { page = Page.TIMELINE.name },
                onCreateMemo = {
                    openNewEvent(TemporalKind.MEMO)
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
                onOpen = { record, occurrenceKey ->
                    if (aiLocked) monthInspection = record else scope.launch {
                        openFormalEditor(record, occurrenceKey)
                    }
                }
            )
        } else if (presentedPage == Page.MONTH.name) {
            MonthScreen(records, Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate(), zone, aiLocked,
                onBack = { page = Page.TIMELINE.name },
                onDate = { date -> anchorMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(); page = Page.TIMELINE.name },
                onOpen = { record, occurrenceKey ->
                    if (aiLocked) monthInspection = record else scope.launch {
                        openFormalEditor(record, occurrenceKey)
                    }
                },
                onCreate = { date ->
                    openNewEvent(TemporalKind.ACTIVITY, EditorSelection.OnDate(date))
                })
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
                titlesOutside = true,
                onTitlesOutsideChange = {},
                onOpenEvent = { record ->
                    if (aiLocked) monthInspection = record else scope.launch {
                        openFormalEditor(record)
                    }
                },
                onOpenOccurrence = { record, occurrenceKey ->
                    if (aiLocked) monthInspection = record else scope.launch {
                        openFormalEditor(record, occurrenceKey)
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
                                    actor = "user",
                                    expectedRevision = record.revision
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
                    openNewEvent(kind, EditorSelection.AtInstant(millis))
                },
                onToday = {
                    nowMillis = System.currentTimeMillis()
                    val today = TimelineCoordinate.todayCamera(nowMillis, zone)
                    anchorMillis = today.anchorMillis
                    hours = today.hours
                },
                onOpenDeck = { scope.launch { openDeckAt(null) } },
                onOpenTools = { page = Page.TOOLS.name },
                blockedReminders = diagnostics.reminders.count { it.intent?.desired == PowerStore.DesiredSchedule.BLOCKED }
            )
        }
        } // page content above the persistent bottom navigation
        if (!keyboardVisible && !fullCanvasWeb) {
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

    pendingWebAiProposalPublish?.let { review ->
        var resolving by remember(review.reviewId) { mutableStateOf(false) }
        WebAiProposalPublishAuthorizationReview(
            review = review,
            busy = resolving,
            onDismiss = {
                if (!resolving) {
                    pendingWebAiProposalPublish = null
                    WebSkinBridgeRuntime.stateRevision++
                }
            },
            onConfirm = {
                if (!resolving && webAiProposalReviewSession?.reviewId == review.reviewId) {
                    resolving = true
                    aiBusy = true
                    PowerCalStores.appScope.launch {
                        try {
                            val applied = withContext(Dispatchers.IO) {
                                synchronized(store) {
                                    val unchanged = review.targetSnapshots.all { (seq, snapshot) ->
                                        store.get(seq) == snapshot
                                    }
                                    if (!unchanged) null else AiDraftService(store)
                                        .applyReviewedProposals(
                                            review.interpretation,
                                            review.selectedPositions.toSet(),
                                            publish = true
                                        )
                                }
                            }
                            withContext(Dispatchers.Main) {
                                pendingWebAiProposalPublish = null
                                if (applied == null) {
                                    appendChat("assistant", "确认期间目标事件已变化；没有提交本批 AI 操作，请重新审核。")
                                } else {
                                    webAiProposalReviewSession?.takeIf {
                                        it.reviewId == review.reviewId
                                    }?.let { finishWebAiProposalRequest(it, applied.handledIndices) }
                                    val refreshed = runCatching { reload() }.isSuccess
                                    val reconciled = if (applied.publishedCount > 0)
                                        runCatching { reconcile() }.isSuccess else true
                                    appendChat("assistant", "Host 审核结果：写入草稿 " +
                                        applied.stagedCount + "，正式发布 " + applied.publishedCount +
                                        (if (applied.alreadyResolvedCount > 0)
                                            "，已有收据确认处理 " + applied.alreadyResolvedCount else "") +
                                        " 项。" + applied.warnings.take(3).joinToString("；") +
                                        (if (!refreshed) " · 操作已完成，但界面刷新失败" else "") +
                                        (if (!reconciled) " · 提醒调度同步失败" else ""))
                                }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            withContext(Dispatchers.Main) {
                                pendingWebAiProposalPublish = null
                                appendChat("assistant", "AI 正式操作没有完成：" +
                                    (error.message ?: error.javaClass.simpleName) + "；请重新审核。")
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                aiBusy = false
                                WebSkinBridgeRuntime.stateRevision++
                            }
                        }
                    }
                } else if (!resolving) {
                    pendingWebAiProposalPublish = null
                    appendChat("assistant", "提案审核已过期；没有执行正式操作。")
                    WebSkinBridgeRuntime.stateRevision++
                }
            }
        )
    }

    webEditing.pendingWriteReview?.let { review ->
        var resolving by remember(review.token) { mutableStateOf(false) }
        val resolveReview: (Boolean) -> Unit = { approved ->
            if (!resolving) {
                resolving = true
                // A confirmed write follows the existing Web command lifetime:
                // composition teardown must not cancel an in-flight transaction.
                PowerCalStores.appScope.launch(Dispatchers.Main.immediate) {
                    try {
                        val reply = webEditing.resolveFormalWriteReview(review.token, approved)
                        when (reply.optString("status")) {
                            "saved", "committed", "replaced", "restored" -> {
                                val refreshed = runCatching { reload() }.isSuccess
                                val reconciled = runCatching { reconcile() }.isSuccess
                                message = when {
                                    !refreshed -> "修改已保存，但界面同步失败；请刷新查看"
                                    !reconciled -> "修改已保存，提醒同步尚未完成"
                                    else -> when {
                                        review.command is WebEditCommand.RequestRestoreReviewDeck ->
                                            "已恢复同一事件序号"
                                        review.command is WebEditCommand.RequestConflictReviewDeck &&
                                            review.operation != DraftOperation.DELETE ->
                                            "已按审阅差异整份覆盖正式事件"
                                        review.operation == DraftOperation.DELETE -> "正式事件已删除"
                                        review.command is WebEditCommand.CommitDeck ||
                                            reply.optString("status") == "committed" -> "日程已正式发布"
                                        else -> "正式事件修改已保存"
                                    }
                                }
                            }
                            "cancelled" -> message = when (review.command) {
                                is WebEditCommand.RequestConflictReviewDeck ->
                                    "已取消整份覆盖；冲突草稿与正式事件均未改变"
                                is WebEditCommand.RequestRestoreReviewDeck ->
                                    "已取消恢复；恢复草稿仍保留，事件仍处于已删除状态"
                                else -> "已取消保存，修改仍保留在编辑器中"
                            }
                            "kept" -> message = "已保留草稿；正式事件没有变化"
                            else -> message = reply.optString("detail").ifBlank { "保存未完成，请重新检查" }
                        }
                        WebSkinBridgeRuntime.stateRevision++
                    } finally { resolving = false }
                }
            }
        }
        val reviewTitle = when {
            review.command is WebEditCommand.RequestConflictReviewDeck ->
                if (review.operation == DraftOperation.DELETE) "确认删除冲突事件" else "确认整份覆盖"
            review.command is WebEditCommand.RequestRestoreReviewDeck -> "确认恢复同一事件"
            review.operation == DraftOperation.DELETE -> "确认删除正式事件"
            review.command is WebEditCommand.CommitDeck -> "确认发布正式事件"
            else -> "确认保存正式事件修改"
        }
        SkinAlertDialog(
            onDismissRequest = { resolveReview(false) },
            title = { Text(reviewTitle) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    review.current?.let {
                        Text("事件 #${it.eventSeq} · 版本 ${it.revision}")
                    } ?: review.deckCard?.let {
                        Text("草稿 #${it.draftId} · ${review.operation.label}")
                    }
                    review.scope?.let { scope ->
                        Text("范围：" + when(scope) {
                            RecurrenceEditScope.SINGLE_OCCURRENCE -> "仅本次"
                            RecurrenceEditScope.THIS_AND_FUTURE -> "本次及以后（拆分为新系列）"
                            RecurrenceEditScope.WHOLE_SERIES -> "整个系列"
                        })
                    }
                    review.occurrenceKey?.let { Text("原始实例：$it") }
                    Text(when {
                        review.command is WebEditCommand.RequestRestoreReviewDeck ->
                            "该事件已删除。确认后会恢复同一事件序号并创建新版本；请核对墓碑快照与恢复内容。"
                        review.command is WebEditCommand.RequestConflictReviewDeck &&
                            review.operation != DraftOperation.DELETE ->
                            "此草稿基于旧版本。确认后会按下方完整内容整份覆盖当前正式事件。"
                        review.operation == DraftOperation.DELETE -> {
                        if (review.current?.recurrence?.freq?.name?.let { it != "NONE" } == true)
                            "确认后将删除整个正式重复事件及其所有实例。请核对事件内容后确认。"
                        else "确认后将删除该正式事件。请核对事件内容后确认。"
                        }
                        else -> "请核对完整内容；只有在此宿主对话框确认后才会写入正式事件。"
                    })
                    when {
                        review.operation == DraftOperation.DELETE &&
                            review.command is WebEditCommand.RequestConflictReviewDeck ->
                            review.before?.let { before -> snapshotRows(before, DraftForm.from(before)) }
                                ?.forEach { row -> Text("${row.label}：${row.before} → （删除）") }
                        review.proposed != null -> snapshotRows(review.before, review.proposed)
                            .forEach { row -> Text("${row.label}：${row.before} → ${row.after}") }
                        review.before != null -> snapshotRows(review.before,
                            DraftForm.from(review.before)).forEach { row ->
                            Text("${row.label}：${row.before} → （删除）")
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !resolving, onClick = { resolveReview(true) }) {
                Text(if (review.operation == DraftOperation.DELETE) "确认删除" else "确认")
            } },
            dismissButton = { TextButton(enabled = !resolving, onClick = { resolveReview(false) }) {
                Text("取消")
            } }
        )
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
                FormalEventSearch.filter(records, query)
            },
            initialEditorOpen = deckInitialId != null
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

    skinToActivate?.let { item ->
        SkinAlertDialog(
            onDismissRequest = { skinToActivate = null },
            title = { Text("切换皮肤前请确认") },
            text = {
                Text(
                    """即将启用“${item.name}”。第三方皮肤可能遮挡设置入口，甚至导致界面无法操作。

紧急回退：在屏幕上相隔的两处位置，用双指轮流快速轻点，共连续敲击 8 下（每指 4 下），即可强制恢复原生皮肤。

回退不会删除日历事件、草稿或已安装皮肤。导入新皮肤本身不会自动启用。""".trimIndent()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    skinToActivate = null
                    scope.launch {
                        if(!webEditing.prepareSkinChange()) {
                            message = "编辑内容尚未安全保存，已阻止切换皮肤"
                            return@launch
                        }
                        runCatching { skins.select(item.id) }
                            .onSuccess {
                                selectedSkin = skins.currentId()
                                message = "已切换皮肤：" + item.name
                            }
                            .onFailure { error ->
                                message = "无法切换皮肤：" + error.message
                            }
                    }
                }) { Text("确认切换") }
            },
            dismissButton = {
                TextButton(onClick = { skinToActivate = null }) { Text("取消") }
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

    newEventSession?.let { session ->
        val visibleForm = newEventForm ?: session.form
        NewEventEditorDialog(
            session = session,
            form = visibleForm,
            error = newEventError,
            busy = newEventBusy,
            onFormChange = { next ->
                if (!newEventBusy) {
                    session.edit(next)
                    newEventForm = next
                    newEventError = null
                }
            },
            onSave = {
                if (!newEventBusy) {
                    newEventBusy = true
                    scope.launch {
                        try {
                            when (val result = withContext(Dispatchers.IO) { session.save(store) }) {
                                is NewEventEditSession.SaveOutcome.Staged -> {
                                    newEventError = null
                                    val refreshFailed = runCatching { reload() }.isFailure
                                    message = if (refreshFailed) {
                                        "草稿已保存，但界面刷新失败；请重新打开待处理列表。"
                                    } else "内容尚未完整，已保存为待处理草稿，可继续编辑。"
                                }
                                is NewEventEditSession.SaveOutcome.Failed ->
                                    newEventError = result.message
                                NewEventEditSession.SaveOutcome.NoChanges ->
                                    newEventError = "内容没有有效修改，未创建事件或草稿。"
                                is NewEventEditSession.SaveOutcome.Published -> when (val outcome = result.outcome) {
                                    is PowerStore.CommitOutcome.Committed -> {
                                        newEventSession = null
                                        newEventForm = null
                                        newEventError = null
                                        message = "事件已发布"
                                        runCatching { reload() }
                                            .onFailure { message = "事件已发布，但界面刷新失败；请重新打开查看。" }
                                        runCatching { reconcile() }
                                            .onFailure { message = "事件已发布，提醒同步尚未完成。" }
                                    }
                                    is PowerStore.CommitOutcome.Invalid -> newEventError = outcome.reason
                                    is PowerStore.CommitOutcome.Conflict -> newEventError = "新建事件发生冲突，请重试。"
                                    is PowerStore.CommitOutcome.NeedsRestore -> newEventError = "新建事件需要恢复确认。"
                                    PowerStore.CommitOutcome.Locked -> newEventError = "待处理卡组正由 AI 独占，修改仍保留在编辑器中。"
                                    PowerStore.CommitOutcome.NotAuthorized -> newEventError = "当前编辑会话无权发布此事件。"
                                }
                            }
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            newEventError = error.message ?: "保存失败；编辑内容仍保留。"
                        } finally {
                            newEventBusy = false
                        }
                    }
                }
            },
            onExit = {
                if (!newEventBusy) {
                    newEventBusy = true
                    scope.launch {
                        try {
                            val card = withContext(Dispatchers.IO) { session.close(store) }
                            newEventSession = null
                            newEventForm = null
                            newEventError = null
                            if (card != null) message = "修改已保存为一张待处理草稿"
                            runCatching { reload() }
                                .onFailure { message = "修改已暂存，但界面刷新失败；请重新打开待处理列表。" }
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            newEventError = error.message ?: "暂存失败；编辑内容仍保留。"
                        } finally {
                            newEventBusy = false
                        }
                    }
                }
            },
            onDiscard = {
                if (!newEventBusy) {
                    newEventBusy = true
                    scope.launch {
                        try {
                            val discarded = withContext(Dispatchers.IO) { session.discard(store) }
                            if (discarded) {
                                newEventSession = null
                                newEventForm = null
                                newEventError = null
                                runCatching { reload() }
                                    .onFailure { message = "内容已丢弃，但界面刷新失败；请重新打开查看。" }
                            } else newEventError = session.lastError ?: "丢弃失败，请重试。"
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            newEventError = error.message ?: "丢弃失败；编辑内容仍保留。"
                        } finally {
                            newEventBusy = false
                        }
                    }
                }
            }
        )
    }

    editSession?.let { session ->
        fun exitWithCheckpoint() {
            if (editBusy) return
            editBusy = true
            scope.launch {
                val checkpoint = withContext(Dispatchers.IO) { session.checkpoint() }
                if (session.lastError != null) {
                    message = "Checkpoint failed; edits remain in memory: " + session.lastError
                    editBusy = false
                } else {
                    withContext(Dispatchers.IO) { session.close() }
                    editSession = null
                    editForm = null
                    editBaseline = null
                    editError = null
                    reload()
                    if (checkpoint != null) message = "Changes saved as one pending draft"
                }
            }
        }
        val visibleForm = editForm ?: session.form
        DirectEventEditorDialog(
            session = session,
            form = visibleForm,
            error = editError,
            hasChanges = session.meaningfulDiff,
            busy = editBusy,
            editorRevision = editUiRevision,
            onChange = { next ->
                editForm = next
                editError = null
                session.edit(next)
            },
            onScopeChange = { selectedScope ->
                runCatching { session.selectRecurrenceScope(selectedScope) }
                    .onSuccess {
                        editForm = session.form
                        editError = null
                    }
                    .onFailure { editError = it.message ?: "无法切换重复编辑范围" }
            },
            onSave = {
                if (!editBusy) {
                    editBusy = true
                    scope.launch {
                        try {
                            if (session.recurrenceScope == RecurrenceEditScope.THIS_AND_FUTURE) {
                                if (!session.meaningfulDiff) {
                                    editError = "内容没有有效修改，未拆分重复序列。"
                                } else when (val outcome = withContext(Dispatchers.IO) {
                                    session.saveThisAndFuture()
                                }) {
                                    is PowerStore.RecurrenceSplitOutcome.Applied -> {
                                        withContext(Dispatchers.IO) { session.close() }
                                        editSession = null; editForm = null; editBaseline = null
                                        editError = null
                                        reload(); reconcile()
                                        message = "本次及以后已拆分保存为新的重复序列"
                                    }
                                    is PowerStore.RecurrenceSplitOutcome.Conflict -> {
                                        editError = session.lastError
                                        message = "重复事件已变化，请重新打开确认"
                                    }
                                    is PowerStore.RecurrenceSplitOutcome.Invalid ->
                                        editError = outcome.reason
                                    PowerStore.RecurrenceSplitOutcome.Locked ->
                                        editError = "编辑器当前不可写"
                                }
                            } else when (val outcome = withContext(Dispatchers.IO) { session.save() }) {
                                is PowerStore.DirectEditOutcome.Saved -> {
                                    editForm = session.form
                                    editBaseline = session.baselineForm
                                    editUiRevision++
                                    editError = null
                                    reload(); reconcile()
                                    message = if (outcome.changed) "Event saved" else "No changes; pending draft cleared"
                                }
                                is PowerStore.DirectEditOutcome.Conflict -> {
                                    editError = session.lastError; message = "Event changed; reopen to review"
                                }
                                is PowerStore.DirectEditOutcome.Invalid -> {
                                    editError = session.lastError; message = outcome.reason
                                }
                                PowerStore.DirectEditOutcome.Locked -> {
                                    editError = session.lastError; message = "Editor is locked"
                                }
                            }
                        } finally {
                            editBusy = false
                        }
                    }
                }
            },
            onExit = { exitWithCheckpoint() },
            onDiscardChanges = {
                session.discardEditingChanges()
                editForm = session.form
                editBaseline = session.baselineForm
                editError = null
            },
            onDelete = {
                if (!editBusy) scope.launch {
                    when (withContext(Dispatchers.IO) { session.delete() }) {
                        is PowerStore.DirectEditOutcome.Saved -> {
                            withContext(Dispatchers.IO) { session.close() }
                            editSession = null; editForm = null; editBaseline = null; editError = null
                            reload(); reconcile(); message = "Event deleted"
                        }
                        else -> {
                            editError = session.lastError
                            message = session.lastError ?: "Delete failed"
                        }
                    }
                }
            }
        )
    }
    LaunchedEffect(editSession) {
        while (editSession != null) {
            delay(30_000)
            withContext(Dispatchers.IO) { editSession?.heartbeat() }
        }
    }

    if (firstCalendarImportPrompt) {
        SkinAlertDialog(
            onDismissRequest = {
                appContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                    .edit().putBoolean("system-calendar-import-asked", true).apply()
                firstCalendarImportPrompt = false
            },
            title = { Text("导入系统日历？") },
            text = { Text("可以读取设备当前的日历日程作为一次性快照。" +
                "以后不会监测或同步原日历；也可以稍后在设置中手动导入。") },
            confirmButton = {
                TextButton(onClick = {
                    appContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                        .edit().putBoolean("system-calendar-import-asked", true).apply()
                    firstCalendarImportPrompt = false
                    if (ContextCompat.checkSelfPermission(appContext,
                            Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    ) scope.launch { importSystemCalendar() }
                    else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }) { Text("导入一次") }
            },
            dismissButton = {
                TextButton(onClick = {
                    appContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                        .edit().putBoolean("system-calendar-import-asked", true).apply()
                    firstCalendarImportPrompt = false
                }) { Text("暂不导入") }
            }
        )
    }

    pendingFileReview?.let { (uri, evidence) ->
        var localText by remember(uri) { mutableStateOf(evidence.text) }
        var rememberFileConsent by remember(uri) { mutableStateOf(false) }
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
                    if (!ics && !evidence.truncated && AiSettingsStore.load(appContext).configured) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rememberFileConsent,
                                onCheckedChange = { rememberFileConsent = it })
                            Text("以后同一 AI 服务可自动分析必要的文件文本；不共享已有日历、不上传原始图片或文件。",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
                            if (rememberFileConsent && !evidence.truncated) {
                                AiFileFallbackConsent.grant(appContext,
                                    AiSettingsStore.load(appContext))
                            }
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
                                    if (result.proposals.isNotEmpty()) {
                                        replaceAiProposalReview(result, contextForAi)
                                    }
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
        AiBatchAuthorizationReview(
            mode = review.mode,
            cards = review.cards,
            busy = aiLocked,
            onDismiss = { confirmBatch = null },
            onAuthorize = { reviewedCards ->
                confirmBatch = null
                PowerCalStores.appScope.launch {
                    // Authorize the exact reviewed snapshots in ONE transaction: the domain layer
                    // re-checks every draftVersion, so a change after this dialog cannot slip in.
                    val reviewed = reviewedCards.associate { it.draftId to it.draftVersion }
                    val session = runCatching {
                        store.beginAiBatch(reviewed, reviewedCards.map { it.operation }.toSet())
                    }.getOrElse {
                        withContext(Dispatchers.Main) {
                            reload()
                            message = "无法开始批次：${it.message.orEmpty()}"
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) { reload() }
                    PowerCalStores.aiBatchProgress.begin(
                        session.sessionId, review.mode, session.scope.size
                    )
                    val report = try {
                        AiBatchRunner(store).run(session, review.mode) { done, total ->
                            PowerCalStores.aiBatchProgress.report(
                                session.sessionId, review.mode, done, total
                            )
                        }
                    } finally {
                        PowerCalStores.aiBatchProgress.finish(session.sessionId)
                    }
                    controller.refresh()
                    withContext(Dispatchers.Main) {
                        reload()
                        message = "AI 批次结束：发布 ${report.published} 张，丢弃 ${report.discarded} 张，保留 ${report.kept} 张" +
                            (if (report.cancelled) "（已停止）" else "") +
                            (if (report.failures.isNotEmpty()) "；部分草稿未完成，可单独重新审核" else "")
                    }
                    ReminderScheduler.reconcile(appContext, store)
                    withContext(Dispatchers.Main) { reload() }
                }
            }
        )
    }

    if (confirmStuckBatchRelease != null) {
        SkinAlertDialog(
            onDismissRequest = { confirmStuckBatchRelease = null },
            title = { Text("强制解除 AI 批次锁？") },
            text = {
                Text(
                    "仅在请求停止超过 10 分钟仍无响应时继续。当前执行结果数量可能无法确定；" +
                        "已提交的事件操作保留，未完成草稿保留待处理。旧批次授权会被作废，不能自动续跑。"
                )
            },
            confirmButton = {
                val reviewedSession = confirmStuckBatchRelease
                TextButton(
                    enabled = reviewedSession != null && aiLocked && aiSession?.isActive == true &&
                        aiSession?.sessionId == reviewedSession.sessionId &&
                        aiSession?.lockEpoch == reviewedSession.lockEpoch &&
                        PowerStore.canReleaseStuckAiBatch(aiSession, nowMillis),
                    onClick = {
                        val expected = confirmStuckBatchRelease
                        confirmStuckBatchRelease = null
                        if (expected != null) scope.launch {
                            val released = withContext(Dispatchers.IO) {
                                store.releaseStuckAiBatch(expected.sessionId, expected.lockEpoch)
                            }
                            reload()
                            message = if (released) {
                                "AI 批次锁已解除；请检查正式事件和待处理草稿，当前批次结果数量无法确定"
                            } else {
                                "批次已变化、结束或已恢复，无需再次解除"
                            }
                            WebSkinBridgeRuntime.stateRevision++
                        }
                    }
                ) { Text("解除并作废旧授权") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStuckBatchRelease = null }) { Text("返回") }
            }
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
                            message = "恢复失败：" + RestoreFailureExplanation.explain(error)
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
    canReleaseStuckBatch: Boolean,
    effects: GlassEffects,
    skinChoices: List<InstalledSkin>,
    selectedSkin: String,
    onSelectSkin: (String) -> Unit,
    onImportSkin: () -> Unit,
    onExportOriginalSkin: () -> Unit,
    onExportInstalledSkin: (InstalledSkin) -> Unit,
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
                        TextButton(
                            onClick = { onExportInstalledSkin(item) },
                            modifier = Modifier.semantics {
                                contentDescription = "导出 ${item.name} 皮肤包"
                            }
                        ) { Text("导出") }
                        TextButton(onClick = { onDeleteSkin(item.id) }) { Text("删除") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportSkin) { Text("导入皮肤包") }
                TextButton(onClick = onExportOriginalSkin) { Text("导出 Clear 候选皮肤") }
            }
            Text("导入后不会自动启用。第三方皮肤可导出原包，在 Skin Studio 导入并继续编辑；" +
                "v3 皮肤可使用受限 HTML/CSS/JavaScript；原生设置随时可用。",
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
            if (aiSession?.isActive == true) {
                val activeState = aiSession.state
                StatusLine("AI 批次", if (activeState == PowerStore.AiState.CANCEL_REQUESTED)
                    "正在停止；已完成结果保留" else "正在运行；未完成草稿会继续等待", false)
                ToolRow(
                    "请求停止 AI 批次",
                    "已完成的操作保留，剩余草稿不执行",
                    onCancelBatch,
                    enabled = activeState == PowerStore.AiState.RUNNING
                )
                if (activeState == PowerStore.AiState.CANCEL_REQUESTED) {
                    ToolRow(
                        "强制解除卡住的 AI 批次",
                        "停止请求超过 10 分钟无响应后可用；当前结果数量可能无法确定",
                        onReleaseStuck,
                        enabled = aiLocked && canReleaseStuckBatch
                    )
                }
            }
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
    var consentRevision by remember { mutableIntStateOf(0) }
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
        if (consentRevision >= 0 && AiFileFallbackConsent.allowed(appContext, settings)) {
            TextButton(onClick = {
                AiFileFallbackConsent.revoke(appContext)
                consentRevision++
            }) { Text("撤销文件 AI 自动处理授权") }
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
