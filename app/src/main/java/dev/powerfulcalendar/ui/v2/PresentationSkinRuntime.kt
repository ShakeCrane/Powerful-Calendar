package dev.powerfulcalendar.ui.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import dev.powerfulcalendar.core.api.v1.SettingsDataFeatureApiV1
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.api.v1.AiBatchModeV1
import dev.powerfulcalendar.core.api.v1.AiBatchSessionV1
import dev.powerfulcalendar.core.api.v1.AiBatchStateV1
import dev.powerfulcalendar.core.api.v1.AiConversationMessageV1
import dev.powerfulcalendar.core.api.v1.AiProposalReviewReadinessV1
import dev.powerfulcalendar.core.api.v1.AiProposalReviewV1
import dev.powerfulcalendar.core.api.v1.EventRecordV1
import dev.powerfulcalendar.core.api.v1.RecurrenceFeatureApiV1
import dev.powerfulcalendar.core.api.v1.RecurrenceFeatureApiV1PreviewDefault
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.api.v1.MemoEntryV1
import dev.powerfulcalendar.core.api.v1.ReminderScheduleStateV1
import dev.powerfulcalendar.core.api.v1.RecurrenceNextOccurrenceV1
import dev.powerfulcalendar.core.api.v1.toEventRecordV1
import dev.powerfulcalendar.ui.v2.nativeui.*
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stable source-compiled screen replacement interface (v1). All user-facing screens have a
 * full-page renderer entry. The Android runtime does NOT execute skin ZIP code.
 *
 * Context data is a snapshot and actions are existing host-owned callbacks; a skin never gets
 * PowerStore, persistence, credentials or any data-mutation service. Native() remains reachable.
 */
internal interface MonthVisualRenderer {
    val id: String
    @Composable fun Render(context: MonthVisualContext)
}
internal interface MemoVisualRenderer {
    val id: String
    @Composable fun Render(context: MemoVisualContext)
}
internal interface AiVisualRenderer {
    val id: String
    @Composable fun Render(context: AiVisualContext)
}
internal interface SettingsVisualRenderer {
    val id: String
    @Composable fun Render(context: SettingsVisualContext)
}
internal interface NavigationVisualRenderer {
    val id: String
    @Composable fun Render(context: NavigationVisualContext)
}

/** Registry changes are observable by Compose, and null always restores first-party rendering. */
internal object PresentationSkinRegistry {
    var month by mutableStateOf<MonthVisualRenderer?>(null)
        private set
    var memo by mutableStateOf<MemoVisualRenderer?>(null)
        private set
    var ai by mutableStateOf<AiVisualRenderer?>(null)
        private set
    var settings by mutableStateOf<SettingsVisualRenderer?>(null)
        private set
    var navigation by mutableStateOf<NavigationVisualRenderer?>(null)
        private set
    internal var overlay by mutableStateOf<OverlayVisualRenderer?>(null)
        private set
    internal var picker by mutableStateOf<PickerVisualRenderer?>(null)
        private set
    internal var chrome by mutableStateOf<ChromeVisualRenderer?>(null)
        private set

    fun useMonth(renderer: MonthVisualRenderer?) { requireId(renderer?.id); month = renderer }
    fun useMemo(renderer: MemoVisualRenderer?) { requireId(renderer?.id); memo = renderer }
    fun useAi(renderer: AiVisualRenderer?) { requireId(renderer?.id); ai = renderer }
    fun useSettings(renderer: SettingsVisualRenderer?) { requireId(renderer?.id); settings = renderer }
    fun useNavigation(renderer: NavigationVisualRenderer?) { requireId(renderer?.id); navigation = renderer }
    internal fun useOverlay(renderer: OverlayVisualRenderer?) { requireId(renderer?.id); overlay = renderer }
    internal fun usePicker(renderer: PickerVisualRenderer?) { requireId(renderer?.id); picker = renderer }
    internal fun useChrome(renderer: ChromeVisualRenderer?) { requireId(renderer?.id); chrome = renderer }
    fun reset() {
        month = null; memo = null; ai = null; settings = null; navigation = null; overlay = null; picker = null; chrome = null
        CompiledSkinRuntime.restoreNativeTimeline()
        DeckPresentationSkinRegistry.reset()
    }
    private fun requireId(id: String?) {
        require(id == null || id.isNotBlank()) { "Compiled renderer ID must not be blank" }
    }
}

internal class MonthVisualContext internal constructor(
    private val nativeRecords: List<TemporalRecord>,
    private val nativeRecordsForMonth: (java.time.YearMonth) -> List<TemporalRecord>,
    val initialDate: LocalDate,
    val zone: ZoneId,
    val aiLocked: Boolean,
    val onBack: () -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onOpen: (Long, Long, String?) -> Unit,
    val onCreate: (LocalDate) -> Unit,
    val onRequestMonth: (java.time.YearMonth) -> Unit,
    private val recurrenceFeatures: RecurrenceFeatureApiV1
) {
    val records: List<EventRecordV1> = nativeRecords.map(TemporalRecord::toEventRecordV1)
    val recordsForMonth: (java.time.YearMonth) -> List<EventRecordV1> = { month ->
        nativeRecordsForMonth(month).map(TemporalRecord::toEventRecordV1)
    }

    @Composable fun Native() {
        val projection = remember(nativeRecordsForMonth, zone, recurrenceFeatures) {
            NativeMonthProjectionSession(nativeRecordsForMonth, zone, recurrenceFeatures)
        }
        val rowsForMonth = remember(projection) { projection::rowsFor }
        val appearance = NativeMonthAppearance(
            eventRowCornerRadiusDp = LocalSkinVisual.current.barRadiusDp,
            panel = { content ->
                NativeGlassPanel(
                    contentPadding = PaddingValues(0.dp),
                    cornerRadiusDp = LocalSkinVisual.current.cardRadiusDp,
                    blur = LocalGlassEffects.current.blur,
                    glassFill = LocalAxisPalette.current.glassFill,
                    surfaceColor = MaterialTheme.colorScheme.surface,
                    outlineVariant = MaterialTheme.colorScheme.outlineVariant
                ) { content() }
            },
            showDialog = { dialog ->
                SkinAlertDialog(
                    onDismissRequest = dialog.onDismissRequest,
                    title = dialog.title,
                    text = dialog.text,
                    confirmButton = dialog.confirmButton,
                    dismissButton = dialog.dismissButton
                )
            },
            kindColors = NativeMonthKind.values().associateWith { kind ->
                kindColor(when (kind) {
                    NativeMonthKind.ALARM -> TemporalKind.ALARM
                    NativeMonthKind.ACTIVITY -> TemporalKind.ACTIVITY
                    NativeMonthKind.DEADLINE -> TemporalKind.DEADLINE
                    NativeMonthKind.MEMO -> TemporalKind.MEMO
                }).toArgb()
            }
        )
        Box(Modifier.fillMaxSize().skinBackdrop(SkinSlot.MONTH_BACKGROUND)) {
            NativeMonthScreen(
                initialDate = initialDate,
                today = LocalDate.now(zone),
                aiLocked = aiLocked,
                rowsForMonth = rowsForMonth,
                onMonthRequest = onRequestMonth,
                onBack = onBack,
                onDate = onDate,
                onOpen = { rowKey ->
                    projection.openTarget(rowKey)?.let { target ->
                        onOpen(target.owner.eventSeq, target.owner.revision, target.occurrenceKey)
                    }
                },
                onCreate = onCreate,
                appearance = appearance
            )
        }
    }
}

@Composable
internal fun MonthScreen(
    records: List<TemporalRecord>, initialDate: LocalDate, zone: ZoneId, aiLocked: Boolean,
    onBack: () -> Unit, onDate: (LocalDate) -> Unit,
    onOpen: (Long, Long, String?) -> Unit,
    onCreate: (LocalDate) -> Unit,
    recordsForMonth: (java.time.YearMonth) -> List<TemporalRecord> = { records },
    onRequestMonth: (java.time.YearMonth) -> Unit = {},
    recurrenceFeatures: RecurrenceFeatureApiV1 = RecurrenceFeatureApiV1PreviewDefault.recurrence
) {
    val context = MonthVisualContext(
        nativeRecords = records,
        nativeRecordsForMonth = recordsForMonth,
        initialDate = initialDate,
        zone = zone,
        aiLocked = aiLocked,
        onBack = onBack,
        onDate = onDate,
        onOpen = onOpen,
        onCreate = onCreate,
        onRequestMonth = onRequestMonth,
        recurrenceFeatures = recurrenceFeatures
    )
    val renderer = PresentationSkinRegistry.month
    if (RuntimeSkinSurfaces.enabled("month")) RuntimeSkinSurfaces.month(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}

internal class MemoVisualContext internal constructor(
    val items: List<MemoEntryV1>,
    val zone: ZoneId,
    val busy: Boolean,
    val hasMore: Boolean,
    val loadingMore: Boolean,
    val onBack: () -> Unit,
    val onCreateMemo: () -> Unit,
    val onToggle: (String) -> Unit,
    val onOpen: (String) -> Unit,
    val onLoadMore: () -> Unit
) {
    @Composable fun Native() {
        val rows = remember(items) { projectNativeMemoRows(items) }
        val entriesByKey = remember(items) { items.associateBy { it.rowKey } }
        NativeMemoCenterScreen(
            items = rows,
            busy = busy,
            hasMore = hasMore,
            loadingMore = loadingMore,
            onBack = onBack,
            onCreateMemo = onCreateMemo,
            onLoadMore = onLoadMore,
            onToggle = { rowKey -> if (rowKey in entriesByKey) onToggle(rowKey) },
            onOpen = { rowKey ->
                    entriesByKey[rowKey]?.let { onOpen(it.rowKey) }
            }
        )
    }
}

@Composable
internal fun MemoCenterScreen(
    items: List<MemoEntryV1>, zone: ZoneId, busy: Boolean,
    onBack: () -> Unit, onCreateMemo: () -> Unit,
    onToggle: (String) -> Unit, onOpen: (String) -> Unit,
    hasMore: Boolean = false, loadingMore: Boolean = false, onLoadMore: () -> Unit = {}
) {
    val context = MemoVisualContext(
        items, zone, busy, hasMore, loadingMore, onBack, onCreateMemo, onToggle, onOpen, onLoadMore
    )
    val renderer = PresentationSkinRegistry.memo
    if (RuntimeSkinSurfaces.enabled("memo")) RuntimeSkinSurfaces.memo(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}

internal class AiVisualContext internal constructor(
    val messages: List<AiConversationMessageV1>,
    val busy: Boolean,
    val batchActionsBusy: Boolean,
    val pendingCount: Int,
    val onSend: (String) -> Unit,
    val onPropose: (String) -> Unit,
    val onClear: () -> Unit,
    val onClearCache: () -> Unit,
    val proposalReview: AiProposalReviewV1?,
    val proposalReadiness: AiProposalReviewReadinessV1?,
    val onApplyProposals: (Set<Int>, Boolean) -> Unit,
    val onDiscardProposals: (Set<Int>) -> Unit,
    val onSupplementProposals: () -> Unit,
    val onSettings: () -> Unit,
    val onOpenDeck: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickFile: () -> Unit,
    val onPrepareBatch: (AiBatchModeV1) -> Unit,
    val onRewind: (Int) -> Unit,
    val batchSelectionActive: Boolean,
    val onCancelBatchSelection: () -> Unit
) {
    @Composable fun Native() {
        val platformContext = LocalContext.current
        val clipboard = remember(platformContext) {
            platformContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
        val nativeMessages = remember(messages) { projectNativeAiMessages(messages) }
        val review = proposalReview?.takeIf { it.items.isNotEmpty() }?.toAiInterpretation()
        val readiness = proposalReadiness?.takeIf { it.reviewId == proposalReview?.reviewId }
        NativeAiChatScreen(
            messages = nativeMessages,
            busy = busy,
            batchActionsBusy = batchActionsBusy,
            pendingCount = pendingCount,
            onSend = onSend,
            onPropose = onPropose,
            onClear = onClear,
            onClearCache = onClearCache,
            onSettings = onSettings,
            onOpenDeck = onOpenDeck,
            onPickImage = onPickImage,
            onPickFile = onPickFile,
            onPrepareBatch = { onPrepareBatch(hostAiBatchMode(it).toAiBatchModeV1()) },
            onRewind = onRewind,
            onCopyMessage = { content ->
                clipboard.setPrimaryClip(ClipData.newPlainText("PowerCal 消息", content))
            },
            proposalPending = review != null,
            batchSelectionActive = batchSelectionActive,
            onCancelBatchSelection = onCancelBatchSelection,
            proposalReviewContent = { actionsDisabled ->
                review?.let {
                    AiProposalReview(it, readiness, actionsDisabled, onApplyProposals, onDiscardProposals,
                        onSupplementProposals)
                }
            },
            assistantAvatarContent = {
                if (skinPainter(SkinSlot.AI_AVATAR) != null) {
                    SkinImage(SkinSlot.AI_AVATAR, Modifier.size(19.dp))
                }
            }
        )
    }
}

@Composable
fun AiChatScreen(
    messages: List<AiConversationMessageV1>, busy: Boolean, batchActionsBusy: Boolean = false, pendingCount: Int,
    onSend: (String) -> Unit, onPropose: (String) -> Unit,
    onClear: () -> Unit, onClearCache: () -> Unit,
    proposalReview: AiProposalReviewV1? = null,
    proposalReadiness: AiProposalReviewReadinessV1? = null,
    onApplyProposals: (Set<Int>, Boolean) -> Unit = { _, _ -> },
    onDiscardProposals: (Set<Int>) -> Unit = {},
    onSupplementProposals: () -> Unit = {},
    onSettings: () -> Unit, onOpenDeck: () -> Unit,
    onPickImage: () -> Unit, onPickFile: () -> Unit,
    onPrepareBatch: (AiBatchModeV1) -> Unit, onRewind: (Int) -> Unit,
    batchSelectionActive: Boolean = false,
    onCancelBatchSelection: () -> Unit = {}
) {
    val context = AiVisualContext(messages, busy, batchActionsBusy, pendingCount, onSend, onPropose,
        onClear, onClearCache, proposalReview, proposalReadiness, onApplyProposals, onDiscardProposals,
        onSupplementProposals, onSettings, onOpenDeck, onPickImage, onPickFile,
        onPrepareBatch, onRewind, batchSelectionActive, onCancelBatchSelection)
    val renderer = PresentationSkinRegistry.ai
    if (RuntimeSkinSurfaces.enabled("ai")) RuntimeSkinSurfaces.ai(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}

internal fun projectSettingsDisplaySnapshot(
    diagnostics: UiDiagnostics,
    records: List<TemporalRecord>,
    pendingCount: Int,
    aiLocked: Boolean,
    aiSession: AiBatchSessionV1?,
    canReleaseStuckBatch: Boolean,
    exactAlarmAllowed: Boolean,
    notificationsAllowed: Boolean,
    formalEventCount: Int = records.size,
    recurrencePreviews: List<RecurrenceNextOccurrenceV1> = emptyList()
): SettingsDisplaySnapshot = SettingsDisplaySnapshot(
    pendingCount = pendingCount,
    formalEventCount = formalEventCount,
    nextSequenceLabel = TemporalRecord.formatSeq(diagnostics.nextSequence),
    identityCount = diagnostics.identities,
    databaseVersion = PowerStore.VERSION,
    databaseName = PowerStore.DATABASE_NAME,
    exactAlarmAllowed = exactAlarmAllowed,
    notificationsAllowed = notificationsAllowed,
    aiLocked = aiLocked,
    canReleaseStuckBatch = canReleaseStuckBatch,
    aiBatchActive = aiSession?.isActive == true,
    aiBatchCancelRequested = aiSession?.state == AiBatchStateV1.CANCEL_REQUESTED,
    reminders = diagnostics.reminders.map { row ->
        val state = row.status.schedule?.state
        SettingsReminderDiagnostic(
            eventLabel = row.record.displaySeq(),
            title = row.record.title,
            scheduleLabel = when (state) {
                ReminderScheduleStateV1.PENDING -> "等待调度"
                ReminderScheduleStateV1.SCHEDULED -> "已交给系统"
                ReminderScheduleStateV1.BLOCKED -> "受阻，请检查权限"
                ReminderScheduleStateV1.CANCELLED -> "已取消"
                ReminderScheduleStateV1.FIRED -> "已触发"
                null -> "无待执行调度"
            },
            blocked = state == ReminderScheduleStateV1.BLOCKED,
            androidHint = row.status.schedule?.blockedHint,
            execution = row.status.lastExecutionResult
        )
    },
    recurrence = recurrencePreviews.take(3).map { preview ->
        val next = preview.occurrence
        SettingsRecurrencePreview(
            label = "重复 #${preview.eventSeq}",
            next = next?.let { "下一次 " + DraftForm.formatInstant(it.startMillis) } ?: "没有后续实例"
        )
    }
)

@Composable
internal fun ToolsPage(
    diagnostics: UiDiagnostics,
    exactAlarmAllowed: Boolean,
    notificationsAllowed: Boolean,
    records: List<TemporalRecord>,
    pendingCount: Int,
    aiLocked: Boolean,
    aiSession: AiBatchSessionV1?,
    canReleaseStuckBatch: Boolean,
    effects: GlassEffects,
    skinChoices: List<InstalledSkin>,
    selectedSkin: String,
    onSelectSkin: (String) -> Unit,
    onImportSkin: () -> Unit,
    onExportOriginalSkin: () -> Unit,
    onExportInstalledSkin: (String) -> Unit,
    onDeleteSkin: (String) -> Unit,
    onBack: () -> Unit,
    onOpenDeck: () -> Unit,
    onImportIcs: () -> Unit,
    onExportIcs: () -> Unit,
    onImportSystem: () -> Unit,
    onMigrateLegacy: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onCancelBatch: () -> Unit,
    onReconcile: () -> Unit,
    onReleaseStuck: () -> Unit,
    settingsDataApi: SettingsDataFeatureApiV1,
    formalEventCount: Int = records.size,
    recurrencePreviews: List<RecurrenceNextOccurrenceV1> = emptyList()
) {
    val settingsDisplay = remember(
        diagnostics, records, pendingCount, aiLocked, aiSession, canReleaseStuckBatch,
        exactAlarmAllowed, notificationsAllowed, formalEventCount, recurrencePreviews
    ) {
        projectSettingsDisplaySnapshot(
            diagnostics, records, pendingCount, aiLocked, aiSession, canReleaseStuckBatch,
            exactAlarmAllowed, notificationsAllowed, formalEventCount, recurrencePreviews
        )
    }
    val settingsEffects = SettingsVisualEffects(effects.blur, effects.animationScale, effects.reduceMotion)
    val skinOptions = remember(skinChoices) {
        skinChoices.map { SettingsSkinChoice(it.id, it.name, it.builtIn) }
    }
    val protectedProfile: @Composable () -> Unit = { HostProtectedProfileSection(settingsDataApi) }
    val protectedAiCredentials: @Composable () -> Unit = { HostProtectedAiSettingsSection() }
    val nativeFallback: @Composable () -> Unit = {
        NativeToolsPage(
            display = settingsDisplay,
            effects = settingsEffects,
            cardShape = skinCardShape(),
            rowShape = GlassShapes.chip,
            skinChoices = skinOptions,
            selectedSkin = selectedSkin,
            onSelectSkin = onSelectSkin,
            onImportSkin = onImportSkin,
            onExportOriginalSkin = onExportOriginalSkin,
            onExportInstalledSkin = onExportInstalledSkin,
            onDeleteSkin = onDeleteSkin,
            onBack = onBack,
            onOpenDeck = onOpenDeck,
            onImportIcs = onImportIcs,
            onExportIcs = onExportIcs,
            onImportSystem = onImportSystem,
            onMigrateLegacy = onMigrateLegacy,
            onExportBackup = onExportBackup,
            onRestoreBackup = onRestoreBackup,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onCancelBatch = onCancelBatch,
            onReconcile = onReconcile,
            onReleaseStuck = onReleaseStuck,
            protectedProfile = protectedProfile,
            protectedAiCredentials = protectedAiCredentials
        )
    }
    val context = SettingsVisualContext(
        display = settingsDisplay,
        effects = settingsEffects,
        skinChoices = skinOptions,
        selectedSkin = selectedSkin,
        onSelectSkin = onSelectSkin,
        onImportSkin = onImportSkin,
        onExportOriginalSkin = onExportOriginalSkin,
        onExportInstalledSkin = onExportInstalledSkin,
        onDeleteSkin = onDeleteSkin,
        onBack = onBack,
        onOpenDeck = onOpenDeck,
        onImportIcs = onImportIcs,
        onExportIcs = onExportIcs,
        onImportSystem = onImportSystem,
        onMigrateLegacy = onMigrateLegacy,
        onExportBackup = onExportBackup,
        onRestoreBackup = onRestoreBackup,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onCancelBatch = onCancelBatch,
        onReconcile = onReconcile,
        onReleaseStuck = onReleaseStuck,
        nativeFallback = nativeFallback,
        protectedProfileContent = protectedProfile,
        protectedAiCredentialsContent = protectedAiCredentials
    )
    val renderer = PresentationSkinRegistry.settings
    if (RuntimeSkinSurfaces.enabled("settings")) RuntimeSkinSurfaces.settings(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}

@Composable
internal fun PowerCalNavigation(selected: VisualTab?, onSelect: (VisualTab) -> Unit) {
    val context = NavigationVisualContext(selected, onSelect) {
        NativePowerNavigation(selected, onSelect) {
            if (skinPainter(SkinSlot.SETTINGS_ICON) != null) {
                SkinImage(SkinSlot.SETTINGS_ICON, Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
        }
    }
    val renderer = PresentationSkinRegistry.navigation
    if (RuntimeSkinSurfaces.enabled("navigation")) RuntimeSkinSurfaces.navigation(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}
