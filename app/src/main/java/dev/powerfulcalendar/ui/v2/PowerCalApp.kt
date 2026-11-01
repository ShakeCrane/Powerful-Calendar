package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.NativeAiFileReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiImageConsentReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiOcrTextReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiStuckBatchReleaseReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeCalendarImportPrompt
import dev.powerfulcalendar.ui.v2.nativeui.NativeEventInspection
import dev.powerfulcalendar.ui.v2.nativeui.NativeFormalWriteReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeLegacyDataReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeSkinCatalogReview
import dev.powerfulcalendar.ui.v2.nativeui.NativeTextDraftDialog

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiFileFallbackConsent
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.LegacyBackupConsentRequired
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.api.v1.EventEditorHandleV1
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventQueryCursorV1
import dev.powerfulcalendar.core.api.v1.EventMutationStatusV1
import dev.powerfulcalendar.core.api.v1.MemoEntryV1
import dev.powerfulcalendar.core.api.v1.MemoQueryCursorV1
import dev.powerfulcalendar.core.api.v1.MemoQueryPageV1
import dev.powerfulcalendar.core.api.v1.EventSelectionV1
import dev.powerfulcalendar.core.api.v1.AiProposalApplyRequestV1
import dev.powerfulcalendar.core.api.v1.AiEvidenceAnalysisStatusV1
import dev.powerfulcalendar.core.api.v1.AiReviewedTextEvidenceRequestV1
import dev.powerfulcalendar.core.api.v1.AiTextEvidenceSourceV1
import dev.powerfulcalendar.core.api.v1.AiConsentedImageEvidenceRequestV1
import dev.powerfulcalendar.core.api.v1.AiChatRequestV1
import dev.powerfulcalendar.core.api.v1.AiChatStatusV1
import dev.powerfulcalendar.core.api.v1.AiConversationMutationStatusV1
import dev.powerfulcalendar.core.api.v1.AiConversationRewindModeV1
import dev.powerfulcalendar.core.api.v1.AiProposalDecisionV1
import dev.powerfulcalendar.core.api.v1.CalendarWindowQueryV1
import dev.powerfulcalendar.core.api.v1.CalendarWindowSnapshotV1
import dev.powerfulcalendar.core.api.v1.ImportExportFeatureApiV1
import dev.powerfulcalendar.core.api.v1.IcsImportCheckpointV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.AndroidAiModelPortV1
import dev.powerfulcalendar.core.api.v1.AndroidAiConversationHistoryPortV1
import dev.powerfulcalendar.core.api.v1.StructuredCalendarEventV1
import dev.powerfulcalendar.core.api.v1.StructuredImportBatchHandleV1
import dev.powerfulcalendar.core.api.v1.StructuredImportStatusV1
import dev.powerfulcalendar.core.api.v1.SettingsDataMutationStatusV1
import dev.powerfulcalendar.core.api.v1.BackupRestoreReceiptV1
import dev.powerfulcalendar.core.api.v1.newImportRequestId
import dev.powerfulcalendar.core.api.v1.ReminderEventSnapshotV1
import dev.powerfulcalendar.core.api.v1.ReminderReconcileModeV1
import dev.powerfulcalendar.core.api.v1.ReminderReconcileStatusV1
import dev.powerfulcalendar.core.api.v1.ReminderScheduleStateV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toCoreTemporalRecord
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import dev.powerfulcalendar.data.LegacyImporter
import dev.powerfulcalendar.interop.AiFileEvidence
import dev.powerfulcalendar.interop.AiFileEvidenceReader
import dev.powerfulcalendar.interop.IcsCodec
import dev.powerfulcalendar.interop.SafBackupReader
import dev.powerfulcalendar.interop.SystemCalendar
import dev.powerfulcalendar.interop.TextParser
import dev.powerfulcalendar.reminders.ReminderScheduler
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val POWER_CAL_SKIN_PACKAGE_MIME = "application/vnd.powercal.skin+zip"
private const val HOST_EVENT_PAGE_SIZE = 300
private const val MAX_CACHED_CALENDAR_WINDOWS = 12
private const val MAX_SNAPSHOT_LOAD_ATTEMPTS = 2

internal suspend fun beginSystemCalendarBatchTracked(
    api: ImportExportFeatureApiV1,
    onCreated: (StructuredImportBatchHandleV1) -> Unit
): StructuredImportBatchHandleV1 = withContext(Dispatchers.IO) {
    api.beginStructuredImport("android-calendar").also(onCreated)
}

internal suspend fun finishSystemCalendarBatch(
    api: ImportExportFeatureApiV1,
    batchId: StructuredImportBatchHandleV1,
    warnings: List<String>
) {
    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
        api.finishStructuredImport(batchId, warnings)
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

internal const val SKIN_RECOVERY_GESTURE_COPY = "紧急回退：在屏幕上相隔的两处位置交替快速轻点，共连续轻点 8 下，即可强制恢复原生皮肤。"

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

internal data class ReminderDisplay(val record: TemporalRecord, val status: ReminderEventSnapshotV1)
internal data class UiDiagnostics(
    val nextSequence: Long = 1,
    val identities: Long = 0,
    val reminders: List<ReminderDisplay> = emptyList(),
    val recurrenceRecords: List<TemporalRecord> = emptyList()
)

private data class AppData(
    val records: List<TemporalRecord>,
    val totalEventCount: Int,
    val eventFeatureRevision: Long,
    val memoPage: MemoQueryPageV1,
    val calendarWindows: Map<CalendarWindowQueryV1, CalendarWindowSnapshotV1>,
    val pending: Int,
    val locked: Boolean,
    val session: dev.powerfulcalendar.core.api.v1.AiBatchSessionV1?,
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
    val coreFeatureApi = remember(store, appContext) {
        PowerCalCoreFeatureApiV1(
            store,
            reminderContext = appContext,
            settingsContext = appContext,
            aiModelPort = AndroidAiModelPortV1(appContext),
            aiConversationHistoryPort = AndroidAiConversationHistoryPortV1(appContext),
            importCheckpointContext = appContext
        )
    }
    val calendarFeatureApi = coreFeatureApi.calendar
    val eventFeatureApi = coreFeatureApi.events
    val memoFeatureApi = coreFeatureApi.memos
    val draftReviewApi = coreFeatureApi.draftReview
    val aiFeatureApi = coreFeatureApi.ai
    val reminderFeatureApi = coreFeatureApi.reminders
    val importExportApi = coreFeatureApi.importExport
    val effects = LocalGlassEffects.current
    val skins = remember(appContext) { SkinPackageManager(appContext) }
    var skinChoices by remember { mutableStateOf(skins.installed()) }
    var selectedSkin by remember { mutableStateOf(skins.currentId()) }
    var skinToDelete by remember { mutableStateOf<InstalledSkin?>(null) }
    var skinToActivate by remember { mutableStateOf<InstalledSkin?>(null) }
    var pendingSkinExportId by rememberSaveable { mutableStateOf<String?>(null) }
    // Only project the Host-owned profile name while a user-opened skin profile editor is active.
    var webProfileEditorName by remember { mutableStateOf<String?>(null) }
    var webProfileEditorSkinId by remember { mutableStateOf<String?>(null) }
    var webProfileEditorSaved by remember { mutableStateOf(false) }
    var webProfileSaveRevision by remember { mutableIntStateOf(0) }

    var page by rememberSaveable { mutableStateOf(Page.TIMELINE.name) }
    var records by remember { mutableStateOf<List<TemporalRecord>>(emptyList()) }
    var eventDataGeneration by remember { mutableLongStateOf(0L) }
    var eventFeatureRevision by remember { mutableLongStateOf(0L) }
    val latestEventFeatureRevision by rememberUpdatedState(eventFeatureRevision)
    var hostDataReady by remember(store) { mutableStateOf(false) }
    var todoEntries by remember(store) { mutableStateOf<List<MemoEntryV1>>(emptyList()) }
    var memoTotalEntries by remember(store) { mutableIntStateOf(0) }
    var memoOpenEntries by remember(store) { mutableIntStateOf(0) }
    var memoQueryNowMillis by remember(store) { mutableLongStateOf(System.currentTimeMillis()) }
    var memoNextCursor by remember(store) { mutableStateOf<MemoQueryCursorV1?>(null) }
    var memoLoadingMore by remember { mutableStateOf(false) }
    var totalEventCount by remember(store) { mutableIntStateOf(0) }
    var calendarWindows by remember(store) {
        mutableStateOf<Map<CalendarWindowQueryV1, CalendarWindowSnapshotV1>>(emptyMap())
    }
    var pendingCalendarQueries by remember(store) {
        mutableStateOf<Set<CalendarWindowQueryV1>>(emptySet())
    }
    var pendingCount by remember { mutableIntStateOf(0) }
    var aiLocked by remember { mutableStateOf(false) }
    var aiSession by remember {
        mutableStateOf<dev.powerfulcalendar.core.api.v1.AiBatchSessionV1?>(null)
    }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }
    var manualReminderReconcileStatus by remember { mutableStateOf("IDLE") }
    var manualReminderReconcileMessage by remember {
        mutableStateOf("可以按当前已保存的数据手动重建提醒调度。")
    }
    var backupRestoreBusy by remember { mutableStateOf(false) }

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
    var deckOpening by remember { mutableStateOf(false) }
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
    val proposalReview = webAiProposalReviewSession?.let { session ->
        session.interpretation.toAiProposalReviewV1(session.reviewId, session.expectedTargetRevisions)
    }
    var pendingWebAiProposalPublish by remember {
        mutableStateOf<WebAiProposalPublishReview?>(null)
    }
    suspend fun replaceAiProposalReview(
        review: dev.powerfulcalendar.core.api.v1.AiProposalReviewV1?,
        knownRecords: List<TemporalRecord> = emptyList(),
        taskSnapshot: dev.powerfulcalendar.ai.AiConversationTaskSnapshot? = null
    ) {
        val expected = review?.items.orEmpty().mapNotNull { item ->
            val eventSeq = item.eventSeq ?: return@mapNotNull null
            val revision = item.expectedRevision ?: return@mapNotNull null
            eventSeq to revision
        }.toMap()
        val knownBySeq = knownRecords.associateBy { it.eventSeq }
        val missingTargets = expected.keys.filterNot(knownBySeq::containsKey)
        val currentTargets = withContext(Dispatchers.IO) {
            missingTargets.mapNotNull { seq -> eventFeatureApi.queryEvent(seq)?.toCoreTemporalRecord() }
        }
        val reviewRecords = (knownRecords + currentTargets)
            .filter { expected[it.eventSeq] == it.revision }
        val interpretation = review?.toAiInterpretation(taskSnapshot)
        webAiProposalReviewSession = interpretation?.takeIf { it.proposals.isNotEmpty() }
            ?.let { WebAiProposalReviewSession.create(it, reviewRecords) }
        WebSkinBridgeRuntime.stateRevision++
    }
    val initialChatConversation = remember(aiFeatureApi) { aiFeatureApi.queryConversation() }
    var chatConversation by remember(aiFeatureApi) { mutableStateOf(initialChatConversation) }
    var chatHistory by remember(aiFeatureApi) {
        mutableStateOf(initialChatConversation.messages.map { it.toAiChatMessage() })
    }
    var chatHistoryRevision by remember(aiFeatureApi) {
        mutableLongStateOf(initialChatConversation.revision)
    }
    var webChatPrefillText by remember { mutableStateOf("") }
    var webChatPrefillRevision by remember { mutableLongStateOf(0L) }
    fun applyChatConversation(snapshot: dev.powerfulcalendar.core.api.v1.AiConversationSnapshotV1) {
        chatConversation = snapshot
        chatHistory = snapshot.messages.map { it.toAiChatMessage() }
        chatHistoryRevision = snapshot.revision
    }

    LaunchedEffect(chatConversation.revision, aiFeatureApi) {
        try {
            aiFeatureApi.persistConversation(chatConversation.revision)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            message = "聊天历史保存失败：" + (error.message ?: "请重试")
        }
    }
    var confirmBatch by remember { mutableStateOf<BatchReview?>(null) }
    var confirmStuckBatchRelease by remember {
        mutableStateOf<dev.powerfulcalendar.core.api.v1.AiBatchSessionV1?>(null)
    }
    var stuckBatchReleaseRequestPending by remember { mutableStateOf(false) }
    // Choosing a batch mode only starts a conversation; it is never the commit authorization.
    var pendingBatchMode by remember { mutableStateOf<AiBatchRunner.Mode?>(null) }
    var pendingCards by remember { mutableStateOf<List<dev.powerfulcalendar.core.DraftCard>>(emptyList()) }
    var diagnostics by remember { mutableStateOf(UiDiagnostics()) }
    var monthInspection by remember { mutableStateOf<TemporalRecord?>(null) }
    var editSession by remember { mutableStateOf<EventEditorHandleV1?>(null) }
    var editEditor by remember { mutableStateOf<EventEditorSnapshotV1?>(null) }
    var editForm by remember { mutableStateOf<DraftForm?>(null) }
    var editBaseline by remember { mutableStateOf<DraftForm?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    var editBusy by remember { mutableStateOf(false) }
    var editUiRevision by remember { mutableIntStateOf(0) }
    var newEventSession by remember { mutableStateOf<EventEditorHandleV1?>(null) }
    var newEventEditor by remember { mutableStateOf<EventEditorSnapshotV1?>(null) }
    var newEventForm by remember { mutableStateOf<DraftForm?>(null) }
    var newEventError by remember { mutableStateOf<String?>(null) }
    var newEventBusy by remember { mutableStateOf(false) }
    // One Web editing coordinator per host; reloading or replacing a skin
    // must never create a new editor lease or command queue.
    val nativeEditingNow = androidx.compose.runtime.rememberUpdatedState(
        editSession != null || deckOpen || newEventSession != null)
    val webEditing = remember(coreFeatureApi) {
        WebSkinEditService(
            draftReviewApi, { nativeEditingNow.value }, coreFeatureApi
        )
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
    val backupRestoreAllowed = androidx.compose.runtime.rememberUpdatedState(
        !aiLocked && editSession == null && !deckOpen && !deckOpening && newEventSession == null &&
            !webEditing.hasLiveEditor() && !backupRestoreBusy && !confirmingLegacy
    )
    val semanticOptionsEnabled = !aiLocked && editSession == null && !deckOpen && !deckOpening &&
        newEventSession == null && !webEditing.hasLiveEditor() && !backupRestoreBusy && !confirmingLegacy

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
    fun timelineCalendarQueryFor(anchor: Long): CalendarWindowQueryV1 {
        val day = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate()
        return CalendarWindowQueryV1(
            fromMillis = day.minusDays(8).atStartOfDay(zone).toInstant().toEpochMilli(),
            toMillis = day.plusDays(9).atStartOfDay(zone).toInstant().toEpochMilli(),
            zoneId = zone.id
        )
    }
    fun monthCalendarQueryFor(month: YearMonth): CalendarWindowQueryV1 {
        val monthFirst = month.atDay(1)
        val gridFirst = monthFirst.minusDays((monthFirst.dayOfWeek.value - 1).toLong())
        val gridEnd = gridFirst.plusDays(42)
        return CalendarWindowQueryV1(
            fromMillis = gridFirst.atStartOfDay(zone).toInstant().toEpochMilli(),
            toMillis = gridEnd.atStartOfDay(zone).toInstant().toEpochMilli(),
            zoneId = zone.id
        )
    }
    val camera = TimelineCoordinate.Camera(anchorMillis, TimelineCoordinate.clampHours(hours))
    val timelineQueryDay = Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate()
    val timelineCalendarQuery = remember(timelineQueryDay, zone.id) {
        timelineCalendarQueryFor(camera.anchorMillis)
    }
    val timelineCalendarRecords = remember(calendarWindows, eventDataGeneration, timelineCalendarQuery) {
        calendarWindows[timelineCalendarQuery]?.events?.map { it.toCoreTemporalRecord() }
            ?: emptyList()
    }
    val monthQueryView = monthViewState() ?: WebSkinMonthProjection.initial(anchorMillis, zone)
    val monthCalendarQuery = remember(monthQueryView.month, zone.id) {
        monthCalendarQueryFor(monthQueryView.month)
    }
    val monthCalendarRecords = remember(calendarWindows, eventDataGeneration, monthCalendarQuery) {
        calendarWindows[monthCalendarQuery]?.events?.map { it.toCoreTemporalRecord() }
            ?: emptyList()
    }
    val nativeMonth = YearMonth.from(Instant.ofEpochMilli(anchorMillis).atZone(zone))
    val nativeMonthCalendarQuery = remember(nativeMonth, zone.id) {
        monthCalendarQueryFor(nativeMonth)
    }
    val nativeMonthCalendarRecords = remember(calendarWindows, eventDataGeneration, nativeMonthCalendarQuery) {
        calendarWindows[nativeMonthCalendarQuery]?.events?.map { it.toCoreTemporalRecord() }
            ?: emptyList()
    }
    val nativeMonthRecordsForMonth = remember(calendarWindows, eventDataGeneration, zone.id) {
        { requestedMonth: YearMonth ->
            val query = monthCalendarQueryFor(requestedMonth)
            calendarWindows[query]?.events?.map { it.toCoreTemporalRecord() }
                ?: emptyList()
        }
    }
    val todayKey = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val todoCount = memoOpenEntries
    val memoEntriesByKey = remember(todoEntries) { todoEntries.associateBy { it.rowKey } }
    var memoVisibleLimit by rememberSaveable { mutableIntStateOf(MemoProjectionPaging.PAGE_SIZE) }
    val memoVisibleCount = MemoProjectionPaging.visibleCount(todoEntries.size, memoVisibleLimit)
    val memoRemainingCount = (memoTotalEntries - memoVisibleCount).coerceAtLeast(0)
    fun requestCalendarWindow(query: CalendarWindowQueryV1) {
        if (calendarWindows[query]?.featureRevision == eventFeatureRevision ||
            query in pendingCalendarQueries
        ) return
        pendingCalendarQueries = pendingCalendarQueries + query
        scope.launch {
            val result = withContext(Dispatchers.IO) { calendarFeatureApi.queryWindow(query) }
            pendingCalendarQueries = pendingCalendarQueries - query
            if (!result.stale && result.featureRevision == latestEventFeatureRevision) {
                val updated = LinkedHashMap(calendarWindows)
                updated.remove(query)
                updated[query] = result
                while (updated.size > MAX_CACHED_CALENDAR_WINDOWS) {
                    updated.remove(updated.keys.first())
                }
                calendarWindows = updated
                WebSkinBridgeRuntime.stateRevision++
            }
        }
    }

    LaunchedEffect(
        hostDataReady,
        timelineCalendarQuery,
        monthCalendarQuery,
        nativeMonthCalendarQuery,
        eventFeatureRevision,
        eventDataGeneration,
        pendingCalendarQueries
    ) {
        if (!hostDataReady) return@LaunchedEffect
        listOf(
            timelineCalendarQuery, monthCalendarQuery, nativeMonthCalendarQuery
        ).distinct().filter { query ->
            calendarWindows[query]?.featureRevision != eventFeatureRevision &&
                query !in pendingCalendarQueries
        }.forEach(::requestCalendarWindow)
    }
    val preferences = remember(appContext) {
        appContext.getSharedPreferences("powercal-ui", Context.MODE_PRIVATE)
    }
    // Title placement is always outside; old user preference intentionally ignored.

    suspend fun reload() {
        val projectionNowMillis = nowMillis
        val data = withContext(Dispatchers.IO) {
            var loaded: AppData? = null
            repeat(MAX_SNAPSHOT_LOAD_ATTEMPTS) {
                if (loaded != null) return@repeat
                val pending = draftReviewApi.queryPendingDrafts().map { it.toHostDraftCard() }
                val eventPage = eventFeatureApi.queryEventsPage(limit = HOST_EVENT_PAGE_SIZE)
                val memoPage = memoFeatureApi.queryEntriesPage(
                    nowMillis = projectionNowMillis,
                    includeCompleted = true
                )
                val requestedWindows = listOf(
                    timelineCalendarQuery, monthCalendarQuery, nativeMonthCalendarQuery
                ).distinct()
                val windows = requestedWindows.associateWith(calendarFeatureApi::queryWindow)
                val revision = eventPage.featureRevision
                if (eventPage.stale || memoPage.stale || memoPage.featureRevision != revision ||
                    windows.values.any { it.stale || it.featureRevision != revision }
                ) return@repeat
                val formalPage = eventPage.events.map { it.toCoreTemporalRecord() }
                val reminderRows = ArrayList<ReminderDisplay>(40)
                val recurrenceRows = ArrayList<TemporalRecord>(3)
                fun collectSettingsRows(events: List<dev.powerfulcalendar.core.api.v1.EventRecordV1>) {
                    events.forEach { event ->
                        val record = event.toCoreTemporalRecord()
                        if (recurrenceRows.size < 3 && record.recurrence.isRepeating) {
                            recurrenceRows += record
                        }
                        if (reminderRows.size < 40 && record.enabled && record.reminderMinutes >= 0) {
                            reminderRows += ReminderDisplay(
                                record, reminderFeatureApi.queryEvent(record.eventSeq)
                            )
                        }
                    }
                }
                collectSettingsRows(eventPage.events)
                var diagnosticsCursor: EventQueryCursorV1? = eventPage.nextCursor
                var diagnosticsFresh = true
                while ((reminderRows.size < 40 || recurrenceRows.size < 3) && diagnosticsCursor != null) {
                    val diagnosticsPage = eventFeatureApi.queryEventsPage(
                        diagnosticsCursor, limit = HOST_EVENT_PAGE_SIZE
                    )
                    if (diagnosticsPage.stale || diagnosticsPage.featureRevision != revision) {
                        diagnosticsFresh = false
                        break
                    }
                    collectSettingsRows(diagnosticsPage.events)
                    diagnosticsCursor = diagnosticsPage.nextCursor
                }
                if (!diagnosticsFresh || eventFeatureApi.featureRevision != revision) return@repeat
                val batchState = aiFeatureApi.queryBatchState()
                val coreDiagnostics = coreFeatureApi.diagnostics.queryDiagnostics()
                loaded = AppData(
                    records = formalPage,
                    totalEventCount = eventPage.totalEvents,
                    eventFeatureRevision = revision,
                    memoPage = memoPage,
                    calendarWindows = windows,
                    pending = pending.size,
                    locked = batchState.locked,
                    session = batchState.session,
                    cards = pending,
                    diagnostics = UiDiagnostics(
                        nextSequence = coreDiagnostics.nextSequencePreview,
                        identities = coreDiagnostics.identityCount,
                        reminders = reminderRows,
                        recurrenceRecords = recurrenceRows
                    )
                )
            }
            requireNotNull(loaded) { "日历在刷新期间持续变化，请稍后重试" }
        }
        // withContext(IO) resumes on the caller's UI dispatcher; publish there directly instead
        // of queueing a second Main dispatch that can stall under Compose's test dispatcher.
        if (data.eventFeatureRevision != eventFeatureApi.featureRevision) return
        records = data.records
        totalEventCount = data.totalEventCount
        eventDataGeneration += 1L
        eventFeatureRevision = data.eventFeatureRevision
        todoEntries = data.memoPage.entries
        memoTotalEntries = data.memoPage.totalEntries
        memoOpenEntries = data.memoPage.openEntries
        memoQueryNowMillis = data.memoPage.nowMillis
        memoNextCursor = data.memoPage.nextCursor
        memoLoadingMore = false
        calendarWindows = data.calendarWindows
        hostDataReady = true
        pendingCount = data.pending
        aiLocked = data.locked
        aiSession = data.session
        pendingCards = data.cards
        diagnostics = data.diagnostics
        WebSkinBridgeRuntime.stateRevision++
    }

    fun loadMoreMemoEntries() {
        val cursor = memoNextCursor ?: return
        if (memoLoadingMore) return
        memoLoadingMore = true
        scope.launch {
            val nextPage = withContext(Dispatchers.IO) {
                memoFeatureApi.queryEntriesPage(
                    nowMillis = memoQueryNowMillis,
                    includeCompleted = true,
                    cursor = cursor
                )
            }
            if (nextPage.stale || nextPage.featureRevision != latestEventFeatureRevision) {
                memoLoadingMore = false
                runCatching { reload() }
                    .onFailure { message = "待办列表已变化，刷新失败；请重新打开查看" }
                return@launch
            }
            todoEntries = todoEntries + nextPage.entries
            memoTotalEntries = nextPage.totalEntries
            memoOpenEntries = nextPage.openEntries
            memoNextCursor = nextPage.nextCursor
            memoVisibleLimit = MemoProjectionPaging.nextLimit(
                memoVisibleLimit, nextPage.totalEntries
            )
            memoLoadingMore = false
            WebSkinBridgeRuntime.stateRevision++
        }
    }

    DisposableEffect(eventFeatureApi) {
        val subscription = eventFeatureApi.subscribe { change ->
            scope.launch {
                if (change.featureRevision > latestEventFeatureRevision) {
                    eventFeatureRevision = change.featureRevision
                    runCatching { reload() }
                        .onFailure { message = "事件已处理，但界面状态刷新失败；请重新打开查看" }
                    WebSkinBridgeRuntime.stateRevision++
                }
            }
        }
        onDispose { subscription.close() }
    }

    suspend fun openFormalEditorByIdentity(
        eventSeq: Long,
        expectedRevision: Long,
        occurrenceKey: String? = null
    ) {
        when (val result = withContext(Dispatchers.IO) {
            eventFeatureApi.openFormalEvent(eventSeq, expectedRevision, occurrenceKey)
        }) {
            is EventEditorOpenResultV1.Opened -> {
                val opened = result.editor
                editSession = opened.handle
                editEditor = opened
                editForm = opened.form.toCoreDraftForm()
                editBaseline = opened.baselineForm.toCoreDraftForm()
                editError = opened.lastError
                editBusy = false
                editUiRevision = 0
            }
            is EventEditorOpenResultV1.Stale -> message = "事件已变化，请刷新后重新打开"
            EventEditorOpenResultV1.Missing -> message = "事件已不存在或已删除"
            EventEditorOpenResultV1.Locked -> message = "当前业务操作暂时锁定了事件编辑"
            is EventEditorOpenResultV1.Invalid -> message = result.message
        }
    }

    suspend fun openFormalEditor(record: TemporalRecord, occurrenceKey: String? = null) {
        openFormalEditorByIdentity(record.eventSeq, record.revision, occurrenceKey)
    }

    fun openNewEvent(kind: TemporalKind, selection: EditorSelection? = null) {
        if (aiLocked || editSession != null || deckOpen || newEventSession != null) return
        val apiSelection = when (selection) {
            is EditorSelection.AtInstant -> EventSelectionV1(instantMillis = selection.millis)
            is EditorSelection.OnDate -> EventSelectionV1(localDate = selection.date.toString())
            null -> null
        }
        when (val opened = eventFeatureApi.openNewEvent(
            EventKindV1.valueOf(kind.name), zone.id, apiSelection
        )) {
            is EventEditorOpenResultV1.Opened -> {
                newEventEditor = opened.editor
                newEventSession = opened.editor.handle
                newEventForm = opened.editor.form.toCoreDraftForm()
                newEventError = null
                newEventBusy = false
            }
            is EventEditorOpenResultV1.Invalid -> message = opened.message
            EventEditorOpenResultV1.Locked -> message = "当前业务操作暂时锁定了事件编辑"
            EventEditorOpenResultV1.Missing, is EventEditorOpenResultV1.Stale ->
                message = "无法打开事件编辑器，请刷新后重试"
        }
    }

    suspend fun reconcile(
        mode: ReminderReconcileModeV1 = ReminderReconcileModeV1.PENDING
    ) = withContext(Dispatchers.IO) {
        reminderFeatureApi.reconcile(mode)
    }

    fun reconcileCount(size: Int): String = if (size > 400) "400+" else size.toString()

    fun requestManualReminderReconcile() {
        if (manualReminderReconcileStatus == "RUNNING") return
        manualReminderReconcileStatus = "RUNNING"
        manualReminderReconcileMessage = "正在按当前数据重建提醒调度…"
        WebSkinBridgeRuntime.stateRevision++
        scope.launch {
            try {
                val report = reconcile(ReminderReconcileModeV1.EXHAUSTIVE)
                reload()
                manualReminderReconcileStatus = when (report.status) {
                    ReminderReconcileStatusV1.INCOMPLETE,
                    ReminderReconcileStatusV1.UNAVAILABLE -> "ERROR"
                    ReminderReconcileStatusV1.BLOCKED -> "BLOCKED"
                    ReminderReconcileStatusV1.COMPLETE -> "SUCCESS"
                }
                manualReminderReconcileMessage = when (manualReminderReconcileStatus) {
                    "ERROR" -> "重建未完成：${report.failed} 项处理失败，${reconcileCount(report.pendingCountCapped)} 项仍待调度。请检查执行状态后重试。"
                    "BLOCKED" -> {
                        val hint = report.firstBlockedHint
                        "重建已执行，但 ${reconcileCount(report.blockedCountCapped)} 项提醒仍受阻" +
                            (hint?.let { "：$it" } ?: "。请检查提醒权限与执行状态。")
                    }
                    else -> if (report.attempted == 0) {
                        "当前没有待处理的提醒调度。"
                    } else {
                        "已按当前数据重建提醒调度。"
                    }
                }
                message = manualReminderReconcileMessage
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                manualReminderReconcileStatus = "ERROR"
                manualReminderReconcileMessage = "重建失败：无法读取或更新提醒调度，请检查应用存储状态后重试。"
                message = manualReminderReconcileMessage
            } finally {
                WebSkinBridgeRuntime.stateRevision++
            }
        }
    }

    fun openDeckAt(draftId: Long?) {
        if (PowerCalSkinRegistry.active.web?.fullCanvas == true && page != Page.TOOLS.name) {
            scope.launch {
                val response = webEditing.execute(
                    WebEditCommand.OpenDeck("host-open-deck-${System.nanoTime()}", draftId),
                    newEventZone = zone
                )
                if (response.optString("status") !in setOf("opened", "selected")) {
                    message = response.optString("detail")
                        .ifBlank { "无法打开待处理草稿；草稿仍保存在宿主" }
                }
                WebSkinBridgeRuntime.stateRevision++
            }
            return
        }
        if (deckOpening || deckOpen) return
        deckOpening = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { draftReviewApi.open(draftId) }
                deckInitialId = draftId
                deckOpen = true
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                message = error.message ?: "无法打开待处理列表"
            } finally {
                deckOpening = false
            }
        }
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
        backupJson: String,
        allowLegacy: Boolean
    ): Pair<BackupRestoreReceiptV1, List<String>> {
        val report = withContext(Dispatchers.IO) {
            importExportApi.restoreBackup(backupJson, allowLegacy)
        }
        val problems = mutableListOf<String>()
        runCatching { reconcile() }.onFailure { problems += "提醒调度重建失败，请稍后重试" }
        // Load diagnostics after reconciliation so the restored UI reflects the scheduler's
        // final durable state (SCHEDULED/BLOCKED), matching the application startup order.
        runCatching { reload() }.onFailure { problems += "界面刷新失败，请重启应用查看已恢复数据" }
        return report to problems
    }

    fun restoreMessage(report: BackupRestoreReceiptV1, prefix: String): String = buildString {
        append(prefix)
        append("${report.objects} 个事件、${report.drafts} 张草稿、${report.commits} 条提交")
        append("；提醒计划已重新核对，实际投递状态见“提醒可靠性”；旧授权与旧执行记录未恢复")
        report.warnings.firstOrNull()?.let { append("。").append(it) }
    }

    fun Throwable.readable(): String = message ?: javaClass.simpleName

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { eventFeatureApi.recoverAbandonedHumanEdit() }
        val restoreDeckOnStartup = deckOpen
        val restoreDeckIdOnStartup = deckInitialId
        if (restoreDeckOnStartup) {
            withContext(Dispatchers.IO) { draftReviewApi.open(restoreDeckIdOnStartup) }
        }
        // Calendar projections should not stay empty while reminder scheduling reconciles.
        reload()
        runCatching { reconcile() }.onFailure {
            message = "提醒同步暂不可用，日历数据仍可查看；可在设置中重试"
        }
        // Refresh diagnostics after reconciliation, while retaining the first loaded projection
        // if the scheduler store is unavailable.
        runCatching { reload() }.onFailure {
            if (message == null) message = "提醒状态刷新失败；日历数据仍可查看"
        }
        if (!coreFeatureApi.settingsData.querySettings().systemCalendarImportPromptShown) {
            firstCalendarImportPrompt = true
        }
        // A process that died mid-batch is fenced and unlocked here; never leave a permanent lock.
        val batchState = withContext(Dispatchers.IO) { aiFeatureApi.queryBatchState() }
        if (batchState.locked) {
            withContext(Dispatchers.IO) { aiFeatureApi.recoverInterruptedBatch() }
            reload()
        }
    }

    LaunchedEffect(aiLocked) {
        withContext(Dispatchers.IO) { draftReviewApi.refresh() }
        while (aiLocked) {
            delay(750)
            reload()
            withContext(Dispatchers.IO) { draftReviewApi.refresh() }
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
                editError = activeEditSession?.let(eventFeatureApi::editorSnapshot)?.lastError
                newEventError = activeNewEventSession?.let(eventFeatureApi::editorSnapshot)?.lastError
                scope.launch {
                    try {
                        reconcile()
                        reload()
                    } finally {
                        // Exact-alarm access is an OS property, not Compose state. Force the
                        // Web Host snapshot to publish after returning from system settings.
                        WebSkinBridgeRuntime.stateRevision++
                    }
                }
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
                PowerCalStores.appScope.launch {
                    draftReviewApi.flush("native-stop-${java.util.UUID.randomUUID()}")
                }
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                activeEditSession?.takeIf { checkpointGate.shouldCheckpoint(it) }?.let { session ->
                    // Keep the lease and live memory on failure; a successful checkpoint is the
                    // one durable write for this lifecycle boundary.
                    PowerCalStores.appScope.launch {
                        eventFeatureApi.checkpointFormalEvent(
                            session, java.util.UUID.randomUUID().toString()
                        )
                    }
                }
                activeNewEventSession?.takeIf { checkpointGate.shouldCheckpoint(it) }?.let { session ->
                    PowerCalStores.appScope.launch {
                        runCatching {
                            eventFeatureApi.checkpointNewEvent(session, java.util.UUID.randomUUID().toString())
                        }
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

    LaunchedEffect(todayKey, hostDataReady) {
        if (hostDataReady) {
            val refreshed = withContext(Dispatchers.IO) {
                memoFeatureApi.queryEntriesPage(nowMillis, includeCompleted = true)
            }
            if (!refreshed.stale && refreshed.featureRevision == eventFeatureRevision) {
                todoEntries = refreshed.entries
                memoTotalEntries = refreshed.totalEntries
                memoOpenEntries = refreshed.openEntries
                memoQueryNowMillis = refreshed.nowMillis
                memoNextCursor = refreshed.nextCursor
                memoLoadingMore = false
                WebSkinBridgeRuntime.stateRevision++
            }
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
            val receipt = withContext(Dispatchers.IO) {
                draftReviewApi.createDraft(form.toEventFormV1(), java.util.UUID.randomUUID().toString())
            }
            if (!receipt.succeeded) error(receipt.message ?: "暂时无法创建草稿")
            receipt.draftId ?: error("草稿保存结果缺少草稿身份")
        }.onSuccess { draftId ->
            reload()
            openDeckAt(draftId)
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
            val generation = aiFeatureApi.generateProposal(
                dev.powerfulcalendar.core.api.v1.AiProposalGenerationRequestV1(
                    requestId = java.util.UUID.randomUUID().toString(),
                    userText = text,
                    zoneId = zone.id,
                    shareCalendarContext = settings.shareContext && shareForRequest,
                    contextPolicy = dev.powerfulcalendar.core.api.v1.AiCalendarContextPolicyV1.EVENTS_THROUGH_14_DAYS_AFTER_NOW
                )
            )
            val review = generation.review
            if (review == null || review.items.isEmpty()) {
                message = generation.warnings.firstOrNull()
                    ?: generation.message
                    ?: "AI 没有返回可用的结构化草稿"
                return
            }
            val created = withContext(Dispatchers.IO) {
                coreFeatureApi.aiProposals.createDrafts(
                    review,
                    requestId = java.util.UUID.randomUUID().toString()
                )
            }
            reload()
            val sharedNote = if (generation.sharedEventCount == 0) "未共享事件上下文"
                else "已共享 ${generation.sharedEventCount} 条事件作为本次上下文"
            val firstDraft = created.itemReceipts.firstOrNull {
                it.status == dev.powerfulcalendar.core.api.v1.AiProposalItemStatusV1.STAGED
            }?.draftId
            if (firstDraft == null) {
                message = (created.warnings.firstOrNull()
                    ?: "AI 没有返回可用的结构化草稿") + " · " + sharedNote
            } else {
                openDeckAt(firstDraft)
                message = "AI 生成 ${created.stagedCount} 张待确认草稿 · " + sharedNote +
                    created.warnings.take(2).joinToString("；").let { if (it.isBlank()) "" else " · $it" }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            message = "AI 未完成：" + (error.message ?: "检查配置后重试；已有草稿保留")
        } finally {
            aiBusy = false
        }
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

    fun appendChat(role: String, text: String, proposalSource: String? = null): String? {
        val message = AiChatMessage(
            role = role,
            content = text,
            proposalSource = proposalSource,
            createdAtMillis = if (role == "user") System.currentTimeMillis() else null,
            zoneId = if (role == "user") zone.id else null
        )
        val receipt = aiFeatureApi.appendConversationMessage(
            message.toAiConversationMessageV1(), aiFeatureApi.queryConversation().revision
        )
        applyChatConversation(receipt.snapshot)
        return message.messageId.takeIf {
            receipt.status == AiConversationMutationStatusV1.APPLIED
        }
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
            val current = withContext(Dispatchers.IO) { aiFeatureApi.queryBatchState().session }
            val result = current?.let { session ->
                withContext(Dispatchers.IO) {
                    aiFeatureApi.requestBatchCancellation(session.sessionId)
                }
            }
            reload()
            message = when (result?.status) {
                dev.powerfulcalendar.core.api.v1.AiBatchCommandStatusV1.CANCEL_REQUESTED ->
                    "已请求停止；已完成结果保留，未完成草稿继续等待审核"
                dev.powerfulcalendar.core.api.v1.AiBatchCommandStatusV1.ALREADY_CANCEL_REQUESTED ->
                    "停止请求已提交，正在等待当前操作结束"
                else -> if (confirmBatch != null) {
                    "请在当前 Host 审核窗口取消尚未授权的批次"
                } else {
                    "当前没有正在运行的 AI 批次"
                }
            }
            WebSkinBridgeRuntime.stateRevision++
        }
    }

    fun requestStuckBatchRelease() {
        if (stuckBatchReleaseRequestPending || confirmStuckBatchRelease != null) return
        stuckBatchReleaseRequestPending = true
        scope.launch {
            val current = withContext(Dispatchers.IO) { aiFeatureApi.queryBatchState().session }
            if (aiLocked && current?.canReleaseStuck == true) {
                confirmStuckBatchRelease = current
            } else {
                message = when (current?.state) {
                    dev.powerfulcalendar.core.api.v1.AiBatchStateV1.RUNNING ->
                        "请先请求停止 AI 批次；只有停止请求 10 分钟无响应后才能恢复"
                    dev.powerfulcalendar.core.api.v1.AiBatchStateV1.CANCEL_REQUESTED ->
                        "停止请求尚未超过 10 分钟无响应窗口，请继续等待"
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
        val userMessageId = appendChat("user", text) ?: return
        updateWebChatPrefill("")
        val expectedConversationRevision = chatHistoryRevision
        val mode = pendingBatchMode
        chatBusy = true
        scope.launch {
            try {
                if (mode == null) {
                    val settingsForChat = AiSettingsStore.load(appContext)
                    val response = aiFeatureApi.chat(AiChatRequestV1(
                        requestId = java.util.UUID.randomUUID().toString(),
                        userMessageId = userMessageId,
                        expectedConversationRevision = expectedConversationRevision,
                        zoneId = zone.id,
                        shareCalendarContext = settingsForChat.shareContext
                    ))
                    if (response.status == AiChatStatusV1.COMPLETED) {
                        appendChat("assistant", response.reply, proposalSource = response.proposalSource)
                        response.clarification?.let { appendChat("assistant", it) }
                        val generated = response.automaticProposal
                        val review = generated?.review
                        val task = response.automaticProposalTask?.toAiConversationTaskSnapshot()
                        if (review != null && review.items.isNotEmpty() && task != null) {
                            replaceAiProposalReview(review, records, task)
                        } else if (response.proposalIntent == dev.powerfulcalendar.core.api.v1.AiChatProposalIntentV1.EXPLICIT &&
                            response.clarification == null) {
                            appendChat("assistant", "无法生成结构化提案：" +
                                (generated?.warnings?.firstOrNull()?.takeIf { it.isNotBlank() }
                                    ?: generated?.message?.takeIf { it.isNotBlank() }
                                    ?: "模型回复未形成可审查提案，请补充或核对日程信息后重试。"))
                        }
                    } else {
                        appendChat("assistant", "AI 请求未完成：" + response.message.orEmpty())
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
                        val candidates = snapshot.map { it.toAiBatchCandidateV1() }
                        val selection = aiFeatureApi.selectBatchDrafts(
                            dev.powerfulcalendar.core.api.v1.AiBatchSelectionRequestV1(
                                userRequest = text,
                                candidates = candidates,
                                selectAll = all
                            )
                        )
                        when (selection.status) {
                            dev.powerfulcalendar.core.api.v1.AiBatchSelectionStatusV1.READY -> {
                                val chosen = selection.selected.associateBy { it.draftId }
                                val selected = snapshot.filter { card ->
                                    chosen[card.draftId]?.draftVersion == card.draftVersion
                                }
                                if (selected.size != selection.selected.size) {
                                    appendChat("assistant", "部分目标已发生变化，请重新选择；没有执行任何操作。")
                                } else {
                                    pendingBatchMode = null
                                    appendChat("assistant", "已根据要求选择 " + selected.size +
                                        " 张草稿。下面会弹出包含编号和操作类型的最终审核；取消不会修改事件。")
                                    confirmBatch = BatchReview(mode, selected)
                                }
                            }
                            dev.powerfulcalendar.core.api.v1.AiBatchSelectionStatusV1.NEEDS_CLARIFICATION ->
                                appendChat("assistant", selection.question ?: "请补充要处理的草稿范围。")
                            dev.powerfulcalendar.core.api.v1.AiBatchSelectionStatusV1.INVALID,
                            dev.powerfulcalendar.core.api.v1.AiBatchSelectionStatusV1.MODEL_UNAVAILABLE ->
                                appendChat("assistant", "暂时不能安全选定目标：" +
                                    (selection.message ?: "没有明确的草稿") + "。请补充范围或回复“全部”。")
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
        if (appendChat("user", "上传并确认分析：" + evidence.filename +
            "（" + evidence.format + "）" +
            if (evidence.truncated) " · 内容已截断" else "") == null
        ) {
            chatBusy = false
            return
        }
        val expectedConversationRevision = chatHistoryRevision
        scope.launch {
            try {
                if (evidence.format == "ics-problem") {
                    // Known RFC constructs unsupported by our domain are not candidates for
                    // invented appointments. AI may explain and ask, never bypass the codec.
                    val result = aiFeatureApi.analyzeReviewedTextEvidence(
                        AiReviewedTextEvidenceRequestV1(
                            requestId = java.util.UUID.randomUUID().toString(),
                            source = AiTextEvidenceSourceV1.ICS_UNRESOLVED,
                            filename = evidence.filename,
                            text = evidence.text,
                            truncated = evidence.truncated,
                            preflightHint = hint,
                            expectedConversationRevision = expectedConversationRevision,
                            zoneId = zone.id
                        )
                    )
                    appendChat("assistant", if (result.status == AiEvidenceAnalysisStatusV1.COMPLETED) result.reply
                        else "部分日历规则尚无法安全处理，已保存其他草稿。" +
                            "AI 当前不可用，可稍后重新选择文件。")
                    return@launch
                }
                // A document is a source of untrusted facts, never an execution instruction.
                // Try the existing typed proposal parser before falling back to clarification.
                val generated = aiFeatureApi.generateProposal(
                    dev.powerfulcalendar.core.api.v1.AiProposalGenerationRequestV1(
                        requestId = java.util.UUID.randomUUID().toString(),
                        userText = "从以下文件提取真实日程，仅生成候选项；缺少时间不可猜测。文件：" +
                            evidence.filename + "\n" + evidence.text,
                        zoneId = zone.id,
                        shareCalendarContext = false
                    )
                )
                val review = generated.review
                if (review != null && review.items.isNotEmpty()) {
                    replaceAiProposalReview(review)
                    appendChat("assistant", "已从文件识别 ${review.items.size} 项候选日程，" +
                        "请核对后选择生成草稿；尚未正式发布。" +
                        if (evidence.truncated) " 文件内容不完整，请核对遗漏。" else "")
                } else {
                    val result = aiFeatureApi.analyzeReviewedTextEvidence(
                        AiReviewedTextEvidenceRequestV1(
                            requestId = java.util.UUID.randomUUID().toString(),
                            source = if (evidence.format == "ocr") {
                                AiTextEvidenceSourceV1.REVIEWED_OCR_TEXT
                            } else AiTextEvidenceSourceV1.REVIEWED_FILE_TEXT,
                            filename = evidence.filename,
                            text = evidence.text,
                            truncated = evidence.truncated,
                            preflightHint = hint,
                            expectedConversationRevision = expectedConversationRevision,
                            zoneId = zone.id
                        )
                    )
                    appendChat("assistant", if (result.status == AiEvidenceAnalysisStatusV1.COMPLETED) result.reply
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
    ): Map<Long, TemporalRecord>? {
        val targets = linkedMapOf<Long, TemporalRecord>()
        for (position in positions) {
            val proposal = session.interpretation.proposals.getOrNull(position)
                ?: return null
            if (proposal.operation == dev.powerfulcalendar.core.DraftOperation.CREATE) {
                if (proposal.eventSeq != null) return null
                continue
            }
            val seq = proposal.eventSeq ?: return null
            val expectedRevision = session.expectedTargetRevisions[seq]
                ?: return null
            val current = targets[seq] ?: eventFeatureApi.queryEvent(seq)
                ?.toCoreTemporalRecord()?.also { targets[seq] = it }
                ?: return null
            if (current.revision != expectedRevision) return null
            targets[seq] = current
        }
        return targets
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
                            val request = AiProposalApplyRequestV1(
                                requestId = java.util.UUID.randomUUID().toString(),
                                review = session.interpretation.toAiProposalReviewV1(
                                    session.reviewId, session.expectedTargetRevisions),
                                selectedPositions = selected.toSet(),
                                decision = AiProposalDecisionV1.STAGE
                            )
                            coreFeatureApi.aiProposals.applyReviewedProposals(request)
                        }
                        withContext(Dispatchers.Main) {
                            if (applied.status == dev.powerfulcalendar.core.api.v1.AiProposalApplyStatusV1.CONFLICT) {
                                appendChat("assistant", "目标事件已变化或无法核对；没有写入草稿，请重新生成提案。")
                            } else {
                                finishWebAiProposalRequest(session, applied.handledPositions)
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
                                    targetSnapshots = targets,
                                    expectedTargetRevisions = session.expectedTargetRevisions
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
            val plan = withContext(Dispatchers.Default) {
                importExportApi.prepareIcsImport(text, zone.id)
            }
            if (plan.units == 0) {
                message = "文件里没有可导入的日历事件"
                return
            }
            val result = withContext(Dispatchers.IO) {
                val duplicateSourceIds =
                    importExportApi.duplicateIcsCandidateSourceIds(plan.candidates)
                var duplicated = 0
                val created = mutableListOf<Long>()
                val failed = mutableListOf<String>()
                val receipt = importExportApi.queryIcsImportCheckpoint(plan.fingerprint)
                // A receipt is a recovery hint, not permission to skip missing/discarded cards.
                receipt?.draftIds?.let(importExportApi::existingDraftIds)?.forEach { created += it }
                plan.candidates.forEach { candidate ->
                    if (candidate.sourceId in duplicateSourceIds) {
                        duplicated++
                        return@forEach
                    }
                    runCatching {
                        importExportApi.createIcsDraft(candidate, newImportRequestId())
                    }.onSuccess { draftReceipt ->
                        val draftId = draftReceipt.draftId
                        if (draftReceipt.succeeded && draftId != null) {
                            created.add(draftId)
                            importExportApi.recordIcsImportCheckpoint(
                                IcsImportCheckpointV1(
                                    plan.fingerprint, created.distinct(), plan.problems.size
                                )
                            )
                        } else failed += candidate.sourceId.take(80)
                    }.onFailure { failed += candidate.sourceId.take(80) }
                }
                importExportApi.recordIcsImportCheckpoint(
                    IcsImportCheckpointV1(
                        plan.fingerprint, created.distinct(), plan.problems.size
                    )
                )
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
        var batchId: StructuredImportBatchHandleV1? = null
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
                beginSystemCalendarBatchTracked(importExportApi) { batchId = it }
                val activeBatchId = checkNotNull(batchId)
                withContext(Dispatchers.IO) {
                    incoming.forEachIndexed { index, event ->
                        val external = event.externalId ?: ("cal:" + event.startMillis + ":" + event.title)
                        val importEvent = StructuredCalendarEventV1(
                            title = event.title,
                            startMillis = event.startMillis,
                            endMillis = event.endMillis,
                            notes = event.notes,
                            location = event.location,
                            reminderMinutes = event.reminderMinutes,
                            source = event.source,
                            zoneId = ZoneId.systemDefault().id
                        )
                        val result = importExportApi.importStructuredEvent(
                            activeBatchId, "android-calendar", external, importEvent
                        )
                        when (result.status) {
                            StructuredImportStatusV1.IMPORTED -> imported++
                            StructuredImportStatusV1.ALREADY_IMPORTED -> Unit
                            StructuredImportStatusV1.REJECTED -> {
                                rejected++
                                warnings += "第 ${index + 1} 条系统日历事件未导入：${result.reason.orEmpty()}"
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
                        finishSystemCalendarBatch(importExportApi, createdBatchId, warnings.toList())
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
        ActivityResultContracts.CreateDocument(POWER_CAL_SKIN_PACKAGE_MIME)
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { skins.exportBundledClear(uri) } }
                .onSuccess { message = "已导出完整 Clear v3 候选皮肤；正式替代原生版前仍需完成功能覆盖验收" }
                .onFailure { error -> message = "导出皮肤模板失败：" + error.message }
        }
    }
    val exportInstalledSkin = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(POWER_CAL_SKIN_PACKAGE_MIME)
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
                    val body = importExportApi.exportActivitiesIcs().content
                    appContext.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                        it.write(body)
                    } ?: error("无法写入所选文件")
                }
            }.onSuccess {
                message = "已导出活动 ICS；未包含活动启用/停用状态、活动提醒、闹钟、截止、备忘与完成状态。" +
                    "请使用原生备份保留完整数据"
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
                val exported = withContext(Dispatchers.IO) { importExportApi.exportBackup() }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.writer(Charsets.UTF_8)?.use {
                        it.write(exported.json)
                    } ?: error("无法写入目标文件")
                }
                // Read back through the same bounded reader so a hostile provider cannot bypass the
                // size guard; only a hash-verified read-back may be reported as success.
                val reread = runCatching { readBackupText(uri) }.getOrNull()
                if (reread == null) {
                    message = "备份已写入，但该文件提供方不支持回读校验；请自行保留并验证文件"
                } else {
                    importExportApi.verifyBackupReadback(exported, reread)
                    message = "原生备份已导出并回读校验（SHA-256 匹配）；文件为明文敏感数据"
                }
            }.onFailure { message = "备份失败：" + it.readable() }
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            backupRestoreBusy = false
            WebSkinBridgeRuntime.stateRevision++
        } else scope.launch {
            var text: String? = null
            try {
                runCatching {
                    val loaded = readBackupText(uri)
                    text = loaded
                    val (report, problems) = restoreAndRefresh(loaded, allowLegacy = false)
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
            } finally {
                backupRestoreBusy = false
                WebSkinBridgeRuntime.stateRevision++
            }
        }
    }
    val launchRestoreBackup = {
        if (backupRestoreAllowed.value) {
            backupRestoreBusy = true
            runCatching { restoreBackup.launch(arrayOf("application/json", "*/*")) }
                .onFailure {
                    backupRestoreBusy = false
                    message = "无法打开备份选择器：" + it.readable()
                }
            WebSkinBridgeRuntime.stateRevision++
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

    fun canManageSkinFromSettings(): Boolean =
        page == Page.SETTINGS.name && !aiLocked && !deckOpen && editSession == null &&
            newEventSession == null && !webEditing.hasLiveEditor()

    LaunchedEffect(page) {
        if (page != Page.SETTINGS.name) {
            webProfileEditorName = null
            webProfileEditorSkinId = null
            webProfileEditorSaved = false
        }
    }
    LaunchedEffect(selectedSkin) {
        if (webProfileEditorSkinId != null && webProfileEditorSkinId != selectedSkin) {
            webProfileEditorName = null
            webProfileEditorSkinId = null
            webProfileEditorSaved = false
        }
    }

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
            val activeCamera = TimelineCoordinate.Camera(
                anchorMillis, TimelineCoordinate.clampHours(hours)
            )
            val selection = if (command is WebEditCommand.CreateDeck)
                newEventSelectionForPage(page, activeCamera, zone, monthViewState()?.selectedDate) else null
            val moveContext = if (page == Page.TIMELINE.name &&
                command is WebEditCommand.MoveTimelineEvent) {
                val projectedItems = WebSkinTimelineProjection.build(
                    timelineCalendarRecords, activeCamera, nowMillis, zone
                ).getJSONArray("items")
                val visibleRevisions = linkedMapOf<Long, Long>()
                for (index in 0 until projectedItems.length()) {
                    val item = projectedItems.optJSONObject(index) ?: continue
                    visibleRevisions[item.getLong("eventSeq")] = item.getLong("revision")
                }
                TimelineMoveContext(activeCamera, zone, visibleRevisions)
            } else null
            val response = webEditing.execute(
                command, newEventZone = zone, newEventSelection = selection,
                timelineMoveContext = moveContext
            )
            val status = response.optString("status")
            if(status in setOf("saved","staged","committed","discarded","restored") ||
                (status == "closed" && command is WebEditCommand.CloseEvent)) {
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
                    "timeline" -> {
                        webProfileEditorName = null; webProfileEditorSkinId = null
                        page = Page.TIMELINE.name
                    }
                    "month" -> {
                        webProfileEditorName = null; webProfileEditorSkinId = null
                        openMonthPage()
                    }
                    "memo" -> {
                        webProfileEditorName = null
                        webProfileEditorSkinId = null
                        page = Page.MEMO.name
                        scope.launch { reload() }
                    }
                    "ai" -> {
                        webProfileEditorName = null; webProfileEditorSkinId = null
                        page = Page.AI.name
                    }
                    "settings" -> page = Page.SETTINGS.name
                    "nativeSettings" -> {
                        webProfileEditorName = null; webProfileEditorSkinId = null
                        page = Page.TOOLS.name
                    }
                    "openDeck" -> openDeckAt(null)
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
                WebSkinAction.OpenProfileEditor -> if (fullCanvasWeb && canManageSkinFromSettings()) {
                    webProfileEditorSkinId = selectedSkin
                    webProfileEditorName = coreFeatureApi.settingsData.querySettings()
                        .profileDisplayName
                    webProfileEditorSaved = false
                }
                is WebSkinAction.SaveProfileEditor -> if (
                    fullCanvasWeb && canManageSkinFromSettings() &&
                    webProfileEditorSkinId == selectedSkin &&
                    webProfileEditorName != null && action.displayName.length <= 80 &&
                    action.displayName.none(Char::isISOControl)
                ) {
                    val receipt = coreFeatureApi.settingsData.saveProfileDisplayName(action.displayName)
                    if (receipt.status in setOf(
                            SettingsDataMutationStatusV1.UPDATED,
                            SettingsDataMutationStatusV1.NO_CHANGE
                        )
                    ) {
                        webProfileEditorName = action.displayName
                        webProfileEditorSaved = true
                        webProfileSaveRevision++
                    }
                }
                WebSkinAction.CloseProfileEditor -> if (
                    fullCanvasWeb && page == Page.SETTINGS.name
                ) {
                    webProfileEditorName = null
                    webProfileEditorSkinId = null
                    webProfileEditorSaved = false
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
                WebSkinAction.OpenExactAlarmSettings -> {
                    if (exactAlarmSettingsLaunch(page, android.os.Build.VERSION.SDK_INT) ==
                        ExactAlarmSettingsLaunch.OPEN_SETTINGS
                    ) ReminderScheduler.openExactAlarmSettings(appContext)
                    WebSkinBridgeRuntime.stateRevision++
                }
                WebSkinAction.ReconcileReminders -> {
                    if (manualReminderReconcileAllowed(page)) requestManualReminderReconcile()
                }
                is WebSkinAction.InvokeOption -> when {
                    aiLocked || editSession != null -> Unit
                    action.id == "calendar.import.file" ->
                        openAiDocument.launch(arrayOf("*/*"))
                    action.id == "calendar.export.ics" ->
                        exportIcs.launch("PowerfulCalendar-activities.ics")
                    action.id == "data.backup.export" ->
                        createBackup.launch("powercal-backup.json")
                    action.id == "data.backup.restore" ->
                        if (!deckOpening && !webEditing.hasLiveEditor()) launchRestoreBackup()
                    action.id == "calendar.import.system" -> {
                        if (ContextCompat.checkSelfPermission(appContext,
                                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                        ) scope.launch { importSystemCalendar() }
                        else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    }
                    action.id == "data.legacy.migrate" -> confirmingLegacy = true
                    else -> Unit
                }
                is WebSkinAction.SelectSkin -> {
                    if (page == Page.SETTINGS.name && action.id != selectedSkin) {
                        skinChoices.firstOrNull { it.id == action.id }?.let { skinToActivate = it }
                    }
                }
                WebSkinAction.ImportSkin -> if (canManageSkinFromSettings()) {
                    importSkin.launch(arrayOf(
                        POWER_CAL_SKIN_PACKAGE_MIME, "application/zip", "application/octet-stream", "*/*"
                    ))
                }
                WebSkinAction.ExportClearSkin -> if (canManageSkinFromSettings()) {
                    exportOriginalSkin.launch("PowerCal-Clear-Preview.pcskin")
                }
                is WebSkinAction.ExportInstalledSkin -> if (canManageSkinFromSettings()) {
                    skinChoices.firstOrNull { it.id == action.id && !it.builtIn }?.let { item ->
                        pendingSkinExportId = item.id
                        message = "请选择保存位置以导出 ${item.name}"
                        exportInstalledSkin.launch(item.id + ".pcskin")
                    }
                }
                is WebSkinAction.DeleteSkin -> if (canManageSkinFromSettings()) {
                    skinChoices.firstOrNull { it.id == action.id && !it.builtIn }?.let {
                        skinToDelete = it
                    }
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
                    loadMoreMemoEntries()
                }
                is WebSkinAction.ToggleMemo -> if (!aiLocked && editSession == null) {
                    scope.launch {
                        val visible = withContext(Dispatchers.IO) {
                            memoFeatureApi.queryCurrentEntry(
                                action.eventSeq,
                                action.occurrenceKey,
                                System.currentTimeMillis(),
                                includeCompleted = true
                            )
                        }
                        if (visible == null || visible.revision != action.expectedRevision ||
                            visible.completed != action.completed) {
                            message = "Memo 已变化，请刷新"
                        } else {
                            val result = withContext(Dispatchers.IO) {
                                eventFeatureApi.setOccurrenceCompletion(
                                    eventSeq = action.eventSeq,
                                    expectedRevision = action.expectedRevision,
                                    occurrenceKey = action.occurrenceKey,
                                    expectedCompleted = action.completed,
                                    completed = !action.completed,
                                    requestId = java.util.UUID.randomUUID().toString()
                                )
                            }
                            if (result.status !in setOf(
                                    EventMutationStatusV1.UPDATED,
                                    EventMutationStatusV1.NO_CHANGE
                                )
                            ) message = "Memo 状态已变化，请刷新"
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
                    val result = aiFeatureApi.clearConversation(action.historyRevision)
                    applyChatConversation(result.snapshot)
                    when (result.status) {
                        AiConversationMutationStatusV1.APPLIED -> {
                            pendingBatchMode = null
                            webAiProposalReviewSession = null
                            message = "聊天已清空；事件和待处理草稿未更改"
                        }
                        AiConversationMutationStatusV1.NO_CHANGE -> message = "聊天记录已经为空"
                        else -> message = "聊天已变化，请刷新后重试清理"
                    }
                }
                is WebSkinAction.RewindChat -> if (canEditChatHistory()) {
                    val mode = if (action.editAndResend) AiConversationRewindModeV1.EDIT_AND_RESEND
                        else AiConversationRewindModeV1.RETRACT
                    val result = aiFeatureApi.rewindConversation(
                        action.messageId, mode, action.historyRevision
                    )
                    applyChatConversation(result.snapshot)
                    when (result.status) {
                        AiConversationMutationStatusV1.APPLIED -> {
                            pendingBatchMode = null
                            if (result.prefillText != null) updateWebChatPrefill(result.prefillText)
                            message = if (result.prefillText != null)
                                "已回退后续对话；原消息已放入输入框，可修改后重新发送"
                            else "已回退这条消息及后续对话；日历和草稿未更改"
                        }
                        AiConversationMutationStatusV1.NO_CHANGE ->
                            message = "这条回复后没有更多对话可回退"
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
                    val current = withContext(Dispatchers.IO) {
                        eventFeatureApi.queryEvent(action.eventSeq)?.toCoreTemporalRecord()
                    }
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
            val stateCamera = TimelineCoordinate.Camera(
                anchorMillis,
                TimelineCoordinate.clampHours(hours)
            )
            val stateTimelineQuery = timelineCalendarQueryFor(stateCamera.anchorMillis)
            val stateTimelineRecords = if (stateTimelineQuery == timelineCalendarQuery) {
                timelineCalendarRecords
            } else {
                calendarWindows[stateTimelineQuery]?.events?.map { it.toCoreTemporalRecord() }
                    ?: timelineCalendarRecords
            }
            val stateMonthView = monthViewState() ?:
                WebSkinMonthProjection.initial(anchorMillis, zone)
            val stateMonthQuery = monthCalendarQueryFor(stateMonthView.month)
            val stateMonthRecords = if (stateMonthQuery == monthCalendarQuery) {
                monthCalendarRecords
            } else {
                calendarWindows[stateMonthQuery]?.events?.map { it.toCoreTemporalRecord() }
                    ?: monthCalendarRecords
            }
            val reminderPermissions = reminderFeatureApi.queryPermissions()
            val notificationStatus = notificationPermissionStatus(
                android.os.Build.VERSION.SDK_INT,
                reminderPermissions.notifications.allowed == true
            )
            val exactAlarmStatus = exactAlarmPermissionStatus(
                android.os.Build.VERSION.SDK_INT,
                reminderPermissions.exactAlarm.allowed == true
            )
            val stateTodoEntries = todoEntries
            val stateTodoCount = memoOpenEntries
            val stateMemoVisibleCount = MemoProjectionPaging.visibleCount(
                stateTodoEntries.size, memoVisibleLimit
            )
            val stateMemoRemainingCount = (memoTotalEntries - stateMemoVisibleCount).coerceAtLeast(0)
            JSONObject().put("bridgeVersion", WebSkinBridgeRuntime.VERSION).put("page", page)
                .put("hostNotice", message?.take(1_000) ?: JSONObject.NULL)
                .put("hostDataReady", hostDataReady)
                .put("eventFeatureRevision", eventFeatureRevision)
                .put("aiLocked", aiLocked).put("pendingCount", pendingCount)
                .put("memoCount", stateTodoCount)
                .put("memoEntries", WebSkinMemoProjection.build(
                    if (page == Page.MEMO.name) stateTodoEntries.take(stateMemoVisibleCount) else emptyList()
                ))
                .put("memoTruncated", page == Page.MEMO.name && stateMemoRemainingCount > 0)
                .put("memoRemainingCount", if (page == Page.MEMO.name) stateMemoRemainingCount else 0)
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
                    progress = webAiBatchProgress
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
                .put("exactAlarmPermission", if (page == Page.SETTINGS.name) {
                    JSONObject().put("required", exactAlarmStatus.required)
                        .put("allowed", exactAlarmStatus.allowed)
                } else JSONObject())
                .put("reminderReconcile", if (page == Page.SETTINGS.name) {
                    JSONObject().put("status", manualReminderReconcileStatus)
                        .put("message", manualReminderReconcileMessage)
                } else JSONObject())
                .put("reminderDiagnostics", if (page == Page.SETTINGS.name) {
                    WebSkinReminderDiagnosticsProjection.build(diagnostics.reminders)
                } else org.json.JSONArray())
                .put("systemDiagnostics", if (page == Page.SETTINGS.name) {
                    WebSkinSystemDiagnosticsProjection.build(
                        records, diagnostics.nextSequence, diagnostics.identities, effects, nowMillis, zone,
                        totalEventCount, diagnostics.recurrenceRecords
                    )
                } else JSONObject())
                .put("editing", webEditing.snapshot())
                .put("optionGroups", SemanticOptions.toJson(enabled = semanticOptionsEnabled))
                .put("skinChoices", org.json.JSONArray().apply {
                    if (page == Page.SETTINGS.name) {
                        skinChoices.forEach { item ->
                            put(JSONObject().put("id", item.id).put("name", item.name)
                                .put("builtIn", item.builtIn))
                        }
                    }
                })
                .put("selectedSkinId", if (page == Page.SETTINGS.name) selectedSkin else JSONObject.NULL)
                .put("profileEditor", if (fullCanvasWeb && page == Page.SETTINGS.name &&
                    webProfileEditorName != null && webProfileEditorSkinId == selectedSkin
                ) {
                    JSONObject().put("displayName", webProfileEditorName)
                        .put("saved", webProfileEditorSaved)
                        .put("saveRevision", webProfileSaveRevision)
                } else JSONObject.NULL)
                .put("timeline", WebSkinTimelineProjection.build(
                    stateTimelineRecords,
                    stateCamera,
                    nowMillis, zone))
                .put("month", if (page == Page.MONTH.name)
                    WebSkinMonthProjection.build(
                        stateMonthRecords,
                        stateMonthView,
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

    val dispatchSkinBack: ((() -> Unit) -> Unit) = { onUnhandled ->
        if (fullCanvasWeb) WebSkinBridgeRuntime.requestSkinBack(onUnhandled)
        else onUnhandled()
    }

    // One stable handler avoids stale BackHandler priority while a full-canvas skin changes pages.
    // A skin gets first refusal for its transient UI; the Host then navigates or recovers.
    androidx.activity.compose.BackHandler(
        enabled = !deckOpen && (page != Page.TIMELINE.name ||
            (fullCanvasWeb && editSession == null && !webEditing.hasLiveEditor()))
    ) {
        dispatchSkinBack {
            if (page == Page.TIMELINE.name) {
                page = Page.TOOLS.name
            } else {
                page = Page.TIMELINE.name
                scope.launch { reload() }
            }
        }
    }

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
            val reminderPermissions = reminderFeatureApi.queryPermissions()
            ToolsPage(
                diagnostics = diagnostics,
                exactAlarmAllowed = reminderPermissions.exactAlarm.allowed == true,
                notificationsAllowed = reminderPermissions.notifications.allowed == true,
                nowMillis = nowMillis,
                records = records,
                pendingCount = pendingCount,
                aiLocked = aiLocked,
                aiSession = aiSession,
                canReleaseStuckBatch = aiSession?.canReleaseStuck == true,
                effects = effects,
                skinChoices = skinChoices,
                selectedSkin = selectedSkin,
                onSelectSkin = { id ->
                    if (id != selectedSkin) {
                        skinToActivate = skinChoices.firstOrNull { it.id == id }
                    }
                },
                onImportSkin = {
                    importSkin.launch(arrayOf(
                        POWER_CAL_SKIN_PACKAGE_MIME, "application/zip", "application/octet-stream", "*/*"
                    ))
                },
                onExportOriginalSkin = { exportOriginalSkin.launch("PowerCal-Clear-Preview.pcskin") },
                onExportInstalledSkin = { id ->
                    skinChoices.firstOrNull { it.id == id }?.let { item ->
                        pendingSkinExportId = item.id
                        message = "请选择保存位置以导出 ${item.name}"
                        exportInstalledSkin.launch(item.id + ".pcskin")
                    }
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
                onExportBackup = { createBackup.launch("powercal-backup.json") },
                onRestoreBackup = launchRestoreBackup,
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
                onCancelBatch = {
                    cancelAiBatchFromSurface()
                },
                onReconcile = {
                    requestManualReminderReconcile()
                },
                onReleaseStuck = {
                    requestStuckBatchRelease()
                },
                settingsDataApi = coreFeatureApi.settingsData,
                formalEventCount = totalEventCount,
                recurrenceRecords = diagnostics.recurrenceRecords
            )
        } else if (presentedPage == Page.AI.name) {
            AiChatScreen(
                messages = chatHistory.map { it.toAiConversationMessageV1() },
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
                                    val request = AiProposalApplyRequestV1(
                                        requestId = java.util.UUID.randomUUID().toString(),
                                        review = snapshot.toAiProposalReviewV1(
                                            session.reviewId, session.expectedTargetRevisions),
                                        selectedPositions = selected.toSet(),
                                        decision = if (publish) AiProposalDecisionV1.PUBLISH
                                            else AiProposalDecisionV1.STAGE
                                    )
                                    val approval = if (publish)
                                        coreFeatureApi.issueAiProposalPublishApproval(request) else null
                                    if (publish && approval == null) null
                                    else coreFeatureApi.aiProposals.applyReviewedProposals(request, approval)
                                }
                                if (applied != null &&
                                    webAiProposalReviewSession?.reviewId == session.reviewId) {
                                    val remaining = snapshot.copy(proposals = snapshot.proposals
                                        .filterIndexed { i, _ -> i !in applied.handledPositions })
                                    webAiProposalReviewSession = if (remaining.proposals.isEmpty()) null
                                        else session.replace(remaining)
                                }
                                reload()
                                if (publish && (applied?.publishedCount ?: 0) > 0) reconcile()
                                appendChat("assistant", "结构化操作结果：写入或复用待处理草稿 " +
                                    (applied?.stagedCount ?: 0) + "，正式发布 " +
                                    (applied?.publishedCount ?: 0) +
                                    (if (applied != null && applied.alreadyResolvedCount > 0)
                                        "，已有收据确认已处理 " + applied.alreadyResolvedCount else "") +
                                    (if (applied == null)
                                        "。目标状态已变化或审批未能签发；没有执行正式操作。"
                                    else "。" + applied.warnings.take(2).joinToString("；")))
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
                    val result = aiFeatureApi.clearConversation(chatHistoryRevision)
                    applyChatConversation(result.snapshot)
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
                onPrepareBatch = { beginBatchConversation(it.toRunnerMode()) },
                onRewind = { lastIndex ->
                    val result = if (lastIndex < 0) {
                        aiFeatureApi.clearConversation(chatHistoryRevision)
                    } else {
                        chatHistory.getOrNull(lastIndex)?.let { target ->
                            aiFeatureApi.rewindConversation(
                                target.messageId,
                                AiConversationRewindModeV1.RETRACT,
                                chatHistoryRevision
                            )
                        }
                    }
                    if (result != null) applyChatConversation(result.snapshot)
                    pendingBatchMode = null
                }
            )
        } else if (presentedPage == Page.MEMO.name) {
            MemoCenterScreen(
                items = todoEntries.take(memoVisibleCount), zone = zone, busy = aiLocked,
                hasMore = memoRemainingCount > 0,
                loadingMore = memoLoadingMore,
                onLoadMore = ::loadMoreMemoEntries,
                onBack = { page = Page.TIMELINE.name },
                onCreateMemo = {
                    openNewEvent(TemporalKind.MEMO)
                },
                onToggle = { rowKey -> memoEntriesByKey[rowKey]?.let { entry ->
                    scope.launch {
                        val receipt = withContext(Dispatchers.IO) {
                            eventFeatureApi.setOccurrenceCompletion(
                                eventSeq = entry.eventSeq,
                                expectedRevision = entry.revision,
                                occurrenceKey = entry.occurrenceKey,
                                expectedCompleted = entry.completed,
                                completed = !entry.completed,
                                requestId = java.util.UUID.randomUUID().toString()
                            )
                        }
                        if (receipt.status !in setOf(
                                EventMutationStatusV1.UPDATED,
                                EventMutationStatusV1.NO_CHANGE
                            )
                        ) message = "状态已变化或正在批量处理，请刷新后重试"
                        reload()
                    }
                } },
                onOpen = { rowKey -> memoEntriesByKey[rowKey]?.let { entry ->
                    if (aiLocked) scope.launch {
                        val record = withContext(Dispatchers.IO) {
                            eventFeatureApi.queryEvent(entry.eventSeq)?.toCoreTemporalRecord()
                        }
                        if (record?.revision == entry.revision) monthInspection = record
                    } else scope.launch {
                        openFormalEditorByIdentity(
                            entry.eventSeq,
                            entry.revision,
                            entry.occurrenceKey.takeIf { entry.repeating }
                        )
                    }
                } }
            )
        } else if (presentedPage == Page.MONTH.name) {
            MonthScreen(nativeMonthCalendarRecords,
                Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate(), zone, aiLocked,
                onBack = { page = Page.TIMELINE.name },
                onDate = { date -> anchorMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(); page = Page.TIMELINE.name },
                onOpen = { eventSeq, expectedRevision, occurrenceKey ->
                    if (aiLocked) scope.launch {
                        val record = withContext(Dispatchers.IO) {
                            eventFeatureApi.queryEvent(eventSeq)?.toCoreTemporalRecord()
                        }
                        if (record?.revision == expectedRevision) monthInspection = record
                    } else scope.launch {
                        openFormalEditorByIdentity(eventSeq, expectedRevision, occurrenceKey)
                    }
                },
                onCreate = { date ->
                    openNewEvent(TemporalKind.ACTIVITY, EditorSelection.OnDate(date))
                },
                recordsForMonth = nativeMonthRecordsForMonth,
                onRequestMonth = { requested ->
                    requestCalendarWindow(monthCalendarQueryFor(requested))
                })
        } else {
            SkinnableTimelineScreen(
                records = timelineCalendarRecords,
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
                                eventFeatureApi.stageFormalReplacement(
                                    record.eventSeq,
                                    record.revision,
                                    form.toEventFormV1(),
                                    java.util.UUID.randomUUID().toString()
                                )
                            }
                        }.onSuccess { staged ->
                            if (staged.status == EventMutationStatusV1.STAGED && staged.draftId != null) {
                                reload()
                                openDeckAt(staged.draftId)
                            } else message = staged.message ?: "暂时无法创建修改草稿"
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
                blockedReminders = diagnostics.reminders.count {
                    it.status.schedule?.state == ReminderScheduleStateV1.BLOCKED
                }
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
                                val request = AiProposalApplyRequestV1(
                                    requestId = java.util.UUID.randomUUID().toString(),
                                    review = review.interpretation.toAiProposalReviewV1(
                                        review.reviewId, review.expectedTargetRevisions),
                                    selectedPositions = review.selectedPositions.toSet(),
                                    decision = AiProposalDecisionV1.PUBLISH
                                )
                                val approval = coreFeatureApi.issueAiProposalPublishApproval(request)
                                if (approval == null) null
                                else coreFeatureApi.aiProposals.applyReviewedProposals(request, approval)
                            }
                            withContext(Dispatchers.Main) {
                                pendingWebAiProposalPublish = null
                                if (applied == null) {
                                    appendChat("assistant", "确认期间目标事件已变化；没有提交本批 AI 操作，请重新审核。")
                                } else {
                                    webAiProposalReviewSession?.takeIf {
                                        it.reviewId == review.reviewId
                                    }?.let { finishWebAiProposalRequest(it, applied.handledPositions) }
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
        val reviewExplanation = when {
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
        }
        val reviewRows = when {
            review.operation == DraftOperation.DELETE &&
                review.command is WebEditCommand.RequestConflictReviewDeck ->
                review.before?.let { before -> snapshotRows(before, DraftForm.from(before)) }
                    .orEmpty().map { NativeFormalWriteReviewRow(it.label, it.before, "（删除）") }
            review.proposed != null -> snapshotRows(review.before, review.proposed)
                .map { NativeFormalWriteReviewRow(it.label, it.before, it.after) }
            review.before != null -> snapshotRows(review.before, DraftForm.from(review.before))
                .map { NativeFormalWriteReviewRow(it.label, it.before, "（删除）") }
            else -> emptyList()
        }
        NativeFormalWriteReview(
            model = NativeFormalWriteReviewModel(
                title = reviewTitle,
                targetSummary = review.current?.let {
                    "事件 #${it.eventSeq} · 版本 ${it.revision}"
                } ?: review.deckCard?.let { "草稿 #${it.draftId} · ${review.operation.label}" },
                scopeSummary = review.scope?.let { scope ->
                    "范围：" + when (scope) {
                        RecurrenceEditScope.SINGLE_OCCURRENCE -> "仅本次"
                        RecurrenceEditScope.THIS_AND_FUTURE -> "本次及以后（拆分为新系列）"
                        RecurrenceEditScope.WHOLE_SERIES -> "整个系列"
                    }
                },
                occurrenceSummary = review.occurrenceKey?.let { "原始实例：$it" },
                explanation = reviewExplanation,
                rows = reviewRows,
                confirmLabel = if (review.operation == DraftOperation.DELETE) "确认删除" else "确认",
                resolving = resolving
            ),
            onConfirm = { resolveReview(true) },
            onDismiss = { resolveReview(false) },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    if (deckOpen) {
        DraftDeckDialog(
            draftReviewApi = draftReviewApi,
            aiSession = aiSession,
            onDismissRequest = {
                deckOpen = false
                scope.launch {
                    reconcile()
                    reload()
                }
            },
            onOpenFormalEvent = { hit ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            draftReviewApi.beginFormalReplacement(
                                hit.eventSeq,
                                hit.revision,
                                java.util.UUID.randomUUID().toString()
                            )
                        }
                    }.onSuccess { proposal ->
                        check(proposal.succeeded) {
                            proposal.message ?: proposal.errorCode ?: "无法打开事件编辑"
                        }
                        deckOpen = false
                        reload()
                        openDeckAt(proposal.draftId)
                    }.onFailure {
                        message = it.message ?: "无法打开事件编辑"
                    }
                }
            },
            onCancelAiBatch = {
                scope.launch {
                    val session = aiSession
                    if (session != null) {
                        withContext(Dispatchers.IO) {
                            aiFeatureApi.requestBatchCancellation(session.sessionId)
                        }
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
                withContext(Dispatchers.IO) { eventFeatureApi.searchEvents(query).hits }
            },
            initialEditorOpen = deckInitialId != null
        )
    }

    if (textDialogOpen) {
        NativeTextDraftDialog(
            onDismiss = { textDialogOpen = false },
            onConfirm = { text ->
                textDialogOpen = false
                scope.launch { createDraftFromText(text, "text") }
            },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    skinToActivate?.let { item ->
        NativeSkinCatalogReview(
            kind = NativeSkinCatalogReviewKind.ACTIVATE,
            skinName = item.name,
            recoveryGesture = SKIN_RECOVERY_GESTURE_COPY,
            onConfirm = {
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
            },
            onDismiss = { skinToActivate = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    skinToDelete?.let { item ->
        NativeSkinCatalogReview(
            kind = NativeSkinCatalogReviewKind.DELETE,
            skinName = item.name,
            onConfirm = {
                    skinToDelete = null
                    runCatching { skins.delete(item.id) }
                        .onSuccess {
                            selectedSkin = skins.currentId()
                            skinChoices = skins.installed()
                            message = "已删除皮肤：" + item.name
                        }
                        .onFailure { error -> message = "删除皮肤失败：" + error.message }
            },
            onDismiss = { skinToDelete = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    newEventSession?.let { session ->
        val editor = newEventEditor ?: eventFeatureApi.editorSnapshot(session) ?: return@let
        val visibleForm = newEventForm ?: editor.form.toCoreDraftForm()
        NewEventEditorDialog(
            editor = editor,
            form = visibleForm,
            error = newEventError,
            busy = newEventBusy,
            onFormChange = { next ->
                if (!newEventBusy) {
                    val update = eventFeatureApi.updateNewEvent(session, next.toEventFormV1())
                    newEventForm = next
                    newEventEditor = eventFeatureApi.editorSnapshot(session)
                    newEventError = update.message
                }
            },
            onSave = {
                if (!newEventBusy) {
                    newEventBusy = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                eventFeatureApi.saveNewEvent(
                                    session,
                                    (newEventForm ?: eventFeatureApi.editorSnapshot(session)
                                        ?.form?.toCoreDraftForm() ?: visibleForm).toEventFormV1(),
                                    java.util.UUID.randomUUID().toString()
                                )
                            }
                            when (result.status) {
                                EventMutationStatusV1.STAGED -> {
                                    newEventEditor = eventFeatureApi.editorSnapshot(session)
                                    newEventError = null
                                    val refreshFailed = runCatching { reload() }.isFailure
                                    message = if (refreshFailed) {
                                        "草稿已保存，但界面刷新失败；请重新打开待处理列表。"
                                    } else "内容尚未完整，已保存为待处理草稿，可继续编辑。"
                                }
                                EventMutationStatusV1.FAILED ->
                                    newEventError = result.message ?: "保存失败；编辑内容仍保留。"
                                EventMutationStatusV1.NO_CHANGE ->
                                    newEventError = "内容没有有效修改，未创建事件或草稿。"
                                EventMutationStatusV1.CREATED -> {
                                    newEventSession = null
                                    newEventEditor = null
                                    newEventForm = null
                                    newEventError = null
                                    message = "事件已发布"
                                    runCatching { reload() }
                                        .onFailure { message = "事件已发布，但界面刷新失败；请重新打开查看。" }
                                    runCatching { reconcile() }
                                        .onFailure { message = "事件已发布，提醒同步尚未完成。" }
                                }
                                EventMutationStatusV1.INVALID -> newEventError = result.message
                                    ?: "事件信息无效"
                                EventMutationStatusV1.CONFLICT -> newEventError = "新建事件发生冲突，请重试。"
                                EventMutationStatusV1.NEEDS_RESTORE -> newEventError = "新建事件需要恢复确认。"
                                EventMutationStatusV1.LOCKED -> newEventError = "待处理卡组正由 AI 独占，修改仍保留在编辑器中。"
                                EventMutationStatusV1.NOT_AUTHORIZED -> newEventError = "当前编辑会话无权发布此事件。"
                                else -> newEventError = "事件保存失败，请检查后重试。"
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
                            val card = withContext(Dispatchers.IO) {
                                eventFeatureApi.closeNewEvent(session)
                            }
                            if (card.status in setOf(
                                    EventMutationStatusV1.FAILED,
                                    EventMutationStatusV1.CONFLICT,
                                    EventMutationStatusV1.INVALID
                                )) {
                                newEventError = card.message ?: "暂存失败；编辑内容仍保留。"
                                return@launch
                            }
                            newEventSession = null
                            newEventEditor = null
                            newEventForm = null
                            newEventError = null
                            if (card.status == EventMutationStatusV1.STAGED)
                                message = "修改已保存为一张待处理草稿"
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
                            val discarded = withContext(Dispatchers.IO) {
                                eventFeatureApi.discardNewEvent(session)
                            }
                            if (discarded) {
                                newEventSession = null
                                newEventEditor = null
                                newEventForm = null
                                newEventError = null
                                runCatching { reload() }
                                    .onFailure { message = "内容已丢弃，但界面刷新失败；请重新打开查看。" }
                            } else newEventError = eventFeatureApi.editorSnapshot(session)?.lastError
                                ?: "丢弃失败，请重试。"
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
        val editor = editEditor ?: eventFeatureApi.editorSnapshot(session) ?: return@let
        fun exitWithCheckpoint() {
            if (editBusy) return
            editBusy = true
            scope.launch {
                val checkpoint = withContext(Dispatchers.IO) {
                    eventFeatureApi.checkpointFormalEvent(
                        session, java.util.UUID.randomUUID().toString()
                    )
                }
                if (checkpoint.status == EventMutationStatusV1.FAILED) {
                    message = "Checkpoint failed; edits remain in memory: " +
                        (checkpoint.message ?: eventFeatureApi.editorSnapshot(session)?.lastError)
                    editBusy = false
                } else {
                    withContext(Dispatchers.IO) { eventFeatureApi.closeFormalEvent(session) }
                    editSession = null
                    editEditor = null
                    editForm = null
                    editBaseline = null
                    editError = null
                    reload()
                    if (checkpoint.status == EventMutationStatusV1.STAGED)
                        message = "Changes saved as one pending draft"
                }
            }
        }
        val visibleForm = editForm ?: editor.form.toCoreDraftForm()
        DirectEventEditorDialog(
            editor = editor,
            form = visibleForm,
            error = editError,
            hasChanges = eventFeatureApi.editorSnapshot(session)?.meaningfulChanges ?: false,
            busy = editBusy,
            editorRevision = editUiRevision,
            onChange = { next ->
                editForm = next
                editError = null
                val update = eventFeatureApi.updateFormalEvent(session, next.toEventFormV1())
                editEditor = eventFeatureApi.editorSnapshot(session)
                if (update.status != dev.powerfulcalendar.core.api.v1.EventEditorUpdateStatusV1.UPDATED)
                    editError = update.message ?: "事件编辑状态已失效"
            },
            onScopeChange = { selectedScope ->
                val update = eventFeatureApi.selectFormalRecurrenceScope(session, selectedScope)
                if (update.status == dev.powerfulcalendar.core.api.v1.EventEditorUpdateStatusV1.UPDATED) {
                    eventFeatureApi.editorSnapshot(session)?.let { updated ->
                        editEditor = updated
                        editForm = updated.form.toCoreDraftForm()
                        editError = null
                    }
                } else editError = update.message ?: "无法切换重复编辑范围"
            },
            onSave = {
                if (!editBusy) {
                    editBusy = true
                    scope.launch {
                        try {
                            val outcome = withContext(Dispatchers.IO) {
                                eventFeatureApi.saveFormalEvent(
                                    session,
                                    (editForm ?: visibleForm).toEventFormV1(),
                                    java.util.UUID.randomUUID().toString()
                                )
                            }
                            when (outcome.status) {
                                EventMutationStatusV1.UPDATED, EventMutationStatusV1.NO_CHANGE -> {
                                    if (outcome.relatedEventSeq != null) {
                                        withContext(Dispatchers.IO) {
                                            eventFeatureApi.closeFormalEvent(session)
                                        }
                                        editSession = null
                                        editEditor = null
                                        editForm = null
                                        editBaseline = null
                                        editError = null
                                        reload(); reconcile()
                                        message = "本次及以后已拆分保存为新的重复序列"
                                    } else {
                                    val updated = eventFeatureApi.editorSnapshot(session)
                                    editEditor = updated
                                    editForm = updated?.form?.toCoreDraftForm()
                                    editBaseline = updated?.baselineForm?.toCoreDraftForm()
                                    editUiRevision++
                                    editError = null
                                    reload(); reconcile()
                                    message = if (outcome.status == EventMutationStatusV1.UPDATED)
                                        "Event saved" else "No changes; pending draft cleared"
                                    }
                                }
                                EventMutationStatusV1.CONFLICT -> {
                                    editError = eventFeatureApi.editorSnapshot(session)?.lastError
                                    message = "Event changed; reopen to review"
                                }
                                EventMutationStatusV1.INVALID -> {
                                    editError = outcome.message ?:
                                        eventFeatureApi.editorSnapshot(session)?.lastError
                                }
                                EventMutationStatusV1.LOCKED -> {
                                    editError = outcome.message ?:
                                        eventFeatureApi.editorSnapshot(session)?.lastError
                                    message = "Editor is locked"
                                }
                                else -> editError = outcome.message ?: "Unable to save event"
                            }
                        } finally {
                            editBusy = false
                        }
                    }
                }
            },
            onExit = { exitWithCheckpoint() },
            onDiscardChanges = {
                val update = eventFeatureApi.discardFormalChanges(session)
                eventFeatureApi.editorSnapshot(session)?.let { updated ->
                    editEditor = updated
                    editForm = updated.form.toCoreDraftForm()
                    editBaseline = updated.baselineForm.toCoreDraftForm()
                }
                editError = update.message
            },
            onDelete = {
                if (!editBusy) scope.launch {
                    val approval = withContext(Dispatchers.IO) {
                        coreFeatureApi.issueFormalDeleteApproval(session)
                    }
                    val outcome = if (approval == null) null else withContext(Dispatchers.IO) {
                        eventFeatureApi.deleteFormalEvent(
                            session, approval, java.util.UUID.randomUUID().toString()
                        )
                    }
                    if (outcome?.status == EventMutationStatusV1.DELETED) {
                            withContext(Dispatchers.IO) { eventFeatureApi.closeFormalEvent(session) }
                            editSession = null; editEditor = null; editForm = null
                            editBaseline = null; editError = null
                            reload(); reconcile(); message = "Event deleted"
                    } else {
                        editError = outcome?.message ?:
                            eventFeatureApi.editorSnapshot(session)?.lastError
                        message = if (outcome?.status == EventMutationStatusV1.CONFLICT || approval == null)
                            "Event changed; reopen to review"
                        else eventFeatureApi.editorSnapshot(session)?.lastError ?: "Delete failed"
                    }
                }
            }
        )
    }
    LaunchedEffect(editSession) {
        while (editSession != null) {
            delay(30_000)
            withContext(Dispatchers.IO) { editSession?.let(eventFeatureApi::heartbeatFormalEvent) }
        }
    }

    if (firstCalendarImportPrompt) {
        val markCalendarImportPromptShown = {
            coreFeatureApi.settingsData.markSystemCalendarImportPromptShown()
            firstCalendarImportPrompt = false
        }
        NativeCalendarImportPrompt(
            onDismiss = markCalendarImportPromptShown,
            onImportOnce = {
                markCalendarImportPromptShown()
                if (ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.READ_CALENDAR
                    ) == PackageManager.PERMISSION_GRANTED
                ) scope.launch { importSystemCalendar() }
                else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
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
        val directIcsImport = ics && !evidence.truncated && localText == evidence.text &&
            parsed != null && parsed.warnings.isEmpty() &&
            (parsed.events.isNotEmpty() || parsed.series.isNotEmpty())
        val icsPreflight = if (ics) {
            "ICS 本地预检：单次 " + (parsed?.events?.size ?: 0) +
                " 条、重复系列 " + (parsed?.series?.size ?: 0) +
                " 个；需要澄清 " + (parsed?.warnings?.size ?: 0) + " 项。" +
                (parsed?.warnings?.firstOrNull()?.let { "\n例如：$it" } ?: "")
        } else null
        NativeAiFileReview(
            filename = evidence.filename,
            text = localText,
            note = evidence.note,
            truncated = evidence.truncated,
            icsPreflight = icsPreflight,
            rememberConsentAvailable = !ics && !evidence.truncated &&
                AiSettingsStore.load(appContext).configured,
            rememberConsent = rememberFileConsent,
            directIcsImport = directIcsImport,
            busy = chatBusy,
            onTextChange = { localText = it },
            onRememberConsentChange = { rememberFileConsent = it },
            onSubmit = { reviewedText ->
                pendingFileReview = null
                if (directIcsImport) {
                    // Standard formats are processed by the deterministic importer FIRST.
                    scope.launch { importIcs(uri) }
                } else {
                    if (rememberFileConsent && !evidence.truncated) {
                        AiFileFallbackConsent.grant(appContext, AiSettingsStore.load(appContext))
                    }
                    // Only unsupported, partial or edited evidence takes the AI route.
                    val report = if (ics) parsed?.warnings?.take(5)
                        ?.joinToString("; ").orEmpty() else ""
                    submitEvidence(evidence.copy(text = reviewedText), report)
                }
            },
            onDismiss = { pendingFileReview = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    pendingImageReview?.let { uri ->
        NativeAiImageConsentReview(
            busy = chatBusy,
            onSendOriginal = {
                    pendingImageReview = null
                    page = Page.AI.name
                    chatBusy = true
                    val expectedConversationRevision = chatHistoryRevision
                    scope.launch {
                        try {
                            val (mimeType, imageBytes) = withContext(Dispatchers.IO) {
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
                                mime to output.toByteArray()
                            }
                            val settings = AiSettingsStore.load(appContext)
                            val result = aiFeatureApi.analyzeConsentedImageEvidence(
                                AiConsentedImageEvidenceRequestV1(
                                    requestId = java.util.UUID.randomUUID().toString(),
                                    filename = "用户上传图片",
                                    mimeType = mimeType,
                                    imageBytes = imageBytes,
                                    expectedConversationRevision = expectedConversationRevision,
                                    zoneId = zone.id
                                )
                            )
                            if (result.status != AiEvidenceAnalysisStatusV1.COMPLETED) {
                                message = "视觉识别不可用：" + result.message.orEmpty() + "；已改用本地 OCR"
                                runLocalOcr(uri)
                            } else {
                                appendChat("user", "已授权 AI 读取图片（原始图片不写入聊天历史）")
                                appendChat("assistant", result.reply, result.proposalSource)
                                result.proposalSource?.let { source ->
                                    val generated = aiFeatureApi.generateProposal(
                                        dev.powerfulcalendar.core.api.v1.AiProposalGenerationRequestV1(
                                            requestId = java.util.UUID.randomUUID().toString(),
                                            userText = source,
                                            zoneId = zone.id,
                                            shareCalendarContext = settings.shareContext,
                                            contextPolicy = dev.powerfulcalendar.core.api.v1.AiCalendarContextPolicyV1.FIRST_60_FORMAL_EVENTS
                                        )
                                    )
                                    val review = generated.review
                                    if (review != null && review.items.isNotEmpty()) {
                                        replaceAiProposalReview(review, records)
                                    }
                                }
                            }
                        } catch (error: Exception) {
                            message = "图片处理失败：" + (error.message ?: "未知错误")
                        } finally { chatBusy = false }
                    }
            },
            onLocalOcr = { pendingImageReview = null; runLocalOcr(uri) },
            onDismiss = { pendingImageReview = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    ocrReviewText?.let { extracted ->
        NativeAiOcrTextReview(
            text = extracted,
            onTextChange = { ocrReviewText = it },
            onSubmit = { reviewedText ->
                ocrReviewText = null
                submitEvidence(AiFileEvidence(
                    filename = "OCR 图片文字", format = "ocr", text = reviewedText,
                    truncated = false, note = "OCR 文本由用户核对后发送"
                ))
            },
            onDismiss = { ocrReviewText = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    monthInspection?.let { record ->
        NativeEventInspection(
            model = NativeEventInspectionModel(
                title = record.title,
                summary = TemporalText.summarize(record),
                notes = record.notes
            ),
            onDismiss = { monthInspection = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton
                )
            }
        )
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
                    val apiReview = dev.powerfulcalendar.core.api.v1.AiBatchReviewV1(
                        mode = review.mode.toAiBatchModeV1(),
                        items = reviewedCards.map { it.toAiBatchCandidateV1() }
                    )
                    val approval = coreFeatureApi.issueAiBatchApproval(apiReview)
                    if (approval == null) {
                        withContext(Dispatchers.Main) {
                            reload()
                            message = "无法开始批次审核；没有执行任何操作"
                        }
                        return@launch
                    }
                    var progressStarted = false
                    var activeSessionId: String? = null
                    val report = try {
                        aiFeatureApi.runReviewedBatch(apiReview, approval) { progress ->
                            activeSessionId = progress.sessionId
                            val batchMode = progress.mode.toRunnerMode()
                            if (!progressStarted) {
                                progressStarted = true
                                PowerCalStores.aiBatchProgress.begin(
                                    progress.sessionId, batchMode, progress.total
                                )
                                PowerCalStores.appScope.launch(Dispatchers.Main) { reload() }
                            } else {
                                PowerCalStores.aiBatchProgress.report(
                                    progress.sessionId, batchMode,
                                    progress.completed, progress.total
                                )
                            }
                        }
                    } finally {
                        if (progressStarted) {
                            activeSessionId?.let(PowerCalStores.aiBatchProgress::finish)
                        }
                    }
                    if (report.status ==
                        dev.powerfulcalendar.core.api.v1.AiBatchExecutionStatusV1.START_REJECTED) {
                        withContext(Dispatchers.Main) {
                            reload()
                            message = "无法开始批次：${report.message.orEmpty()}"
                        }
                        return@launch
                    }
                    draftReviewApi.refresh()
                    withContext(Dispatchers.Main) {
                        reload()
                        message = "AI 批次结束：发布 ${report.published} 张，丢弃 ${report.discarded} 张，保留 ${report.kept} 张" +
                            (if (report.status ==
                                dev.powerfulcalendar.core.api.v1.AiBatchExecutionStatusV1.CANCELLED) "（已停止）" else "") +
                            (if (report.failures.isNotEmpty()) "；部分草稿未完成，可单独重新审核" else "")
                    }
                    reconcile()
                    withContext(Dispatchers.Main) { reload() }
                }
            }
        )
    }

    if (confirmStuckBatchRelease != null) {
        val reviewedSession = confirmStuckBatchRelease
        val canConfirmRelease = reviewedSession != null && aiLocked && aiSession?.isActive == true &&
            aiSession?.sessionId == reviewedSession.sessionId &&
            aiSession?.lockEpoch == reviewedSession.lockEpoch &&
            aiSession?.canReleaseStuck == true
        NativeAiStuckBatchReleaseReview(
            canConfirm = canConfirmRelease,
            onConfirm = {
                val expected = confirmStuckBatchRelease
                confirmStuckBatchRelease = null
                if (expected != null) scope.launch {
                    val released = withContext(Dispatchers.IO) {
                        val approval = coreFeatureApi.issueAiBatchStuckReleaseApproval(
                            expected.sessionId, expected.lockEpoch
                        ) ?: return@withContext false
                        aiFeatureApi.releaseStuckBatch(
                            expected.sessionId, expected.lockEpoch, approval
                        ).status == dev.powerfulcalendar.core.api.v1.AiBatchCommandStatusV1.RELEASED
                    }
                    reload()
                    message = if (released) {
                        "AI 批次锁已解除；请检查正式事件和待处理草稿，当前批次结果数量无法确定"
                    } else {
                        "批次已变化、结束或已恢复，无需再次解除"
                    }
                    WebSkinBridgeRuntime.stateRevision++
                }
            },
            onDismiss = { confirmStuckBatchRelease = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            }
        )
    }

    if (confirmingLegacy) {
        NativeLegacyDataReview(
            kind = NativeLegacyDataReviewKind.MIGRATE_DATABASE,
            onConfirm = {
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
            },
            onDismiss = { confirmingLegacy = false },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            },
        )
    }

    legacyRestoreText?.let { legacyText ->
        NativeLegacyDataReview(
            kind = NativeLegacyDataReviewKind.RESTORE_FLAT_BACKUP,
            onConfirm = {
                    legacyRestoreText = null
                    scope.launch {
                        runCatching {
                            val (report, problems) = restoreAndRefresh(legacyText, allowLegacy = true)
                            message = restoreMessage(report, "旧格式备份已恢复（未验证完整性）：") +
                                problems.joinToString("", "；", "") { it } +
                                "；建议立即导出新的 Envelope 备份"
                        }.onFailure { error ->
                            message = "恢复失败：" + RestoreFailureExplanation.explain(error)
                        }
                    }
            },
            onDismiss = { legacyRestoreText = null },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            },
        )
    }
}
