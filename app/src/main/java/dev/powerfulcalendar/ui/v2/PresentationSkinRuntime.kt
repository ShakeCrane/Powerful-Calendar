package dev.powerfulcalendar.ui.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalRecord
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
    val records: List<TemporalRecord>,
    val initialDate: LocalDate,
    val zone: ZoneId,
    val aiLocked: Boolean,
    val onBack: () -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onOpen: (TemporalRecord, String?) -> Unit,
    val onCreate: (LocalDate) -> Unit
) {
    @Composable fun Native() {
        NativeMonthScreen(records, initialDate, zone, aiLocked, onBack, onDate, onOpen, onCreate)
    }
}

@Composable
internal fun MonthScreen(
    records: List<TemporalRecord>, initialDate: LocalDate, zone: ZoneId, aiLocked: Boolean,
    onBack: () -> Unit, onDate: (LocalDate) -> Unit,
    onOpen: (TemporalRecord, String?) -> Unit,
    onCreate: (LocalDate) -> Unit
) {
    val context = MonthVisualContext(records, initialDate, zone, aiLocked, onBack, onDate, onOpen, onCreate)
    val renderer = PresentationSkinRegistry.month
    if (RuntimeSkinSurfaces.enabled("month")) RuntimeSkinSurfaces.month(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}

internal class MemoVisualContext internal constructor(
    val items: List<TodoEntry>,
    val zone: ZoneId,
    val busy: Boolean,
    val onBack: () -> Unit,
    val onCreateMemo: () -> Unit,
    val onToggle: (TodoEntry) -> Unit,
    val onOpen: (TemporalRecord, String?) -> Unit
) {
    @Composable fun Native() {
        val rows = remember(items) { projectNativeMemoRows(items) }
        val entriesByKey = remember(items) { items.associateBy { it.id } }
        NativeMemoCenterScreen(
            items = rows,
            busy = busy,
            onBack = onBack,
            onCreateMemo = onCreateMemo,
            onToggle = { rowKey -> entriesByKey[rowKey]?.let(onToggle) },
            onOpen = { rowKey ->
                entriesByKey[rowKey]?.let { entry ->
                    onOpen(entry.owner, entry.occurrenceKey.takeIf {
                        entry.owner.recurrence.isRepeating
                    })
                }
            }
        )
    }
}

@Composable
internal fun MemoCenterScreen(
    items: List<TodoEntry>, zone: ZoneId, busy: Boolean,
    onBack: () -> Unit, onCreateMemo: () -> Unit,
    onToggle: (TodoEntry) -> Unit, onOpen: (TemporalRecord, String?) -> Unit
) {
    val context = MemoVisualContext(items, zone, busy, onBack, onCreateMemo, onToggle, onOpen)
    val renderer = PresentationSkinRegistry.memo
    if (RuntimeSkinSurfaces.enabled("memo")) RuntimeSkinSurfaces.memo(context)
    else if (renderer == null) context.Native() else renderer.Render(context)
}

internal class AiVisualContext internal constructor(
    val messages: List<AiChatMessage>,
    val busy: Boolean,
    val batchActionsBusy: Boolean,
    val pendingCount: Int,
    val onSend: (String) -> Unit,
    val onPropose: (String) -> Unit,
    val onClear: () -> Unit,
    val onClearCache: () -> Unit,
    val proposalReview: AiInterpretation?,
    val onApplyProposals: (Set<Int>, Boolean) -> Unit,
    val onDiscardProposals: (Set<Int>) -> Unit,
    val onSupplementProposals: () -> Unit,
    val onSettings: () -> Unit,
    val onOpenDeck: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickFile: () -> Unit,
    val onPrepareBatch: (AiBatchRunner.Mode) -> Unit,
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
        val review = proposalReview?.takeIf { it.proposals.isNotEmpty() }
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
            onPrepareBatch = { onPrepareBatch(hostAiBatchMode(it)) },
            onRewind = onRewind,
            onCopyMessage = { content ->
                clipboard.setPrimaryClip(ClipData.newPlainText("PowerCal 消息", content))
            },
            proposalPending = review != null,
            batchSelectionActive = batchSelectionActive,
            onCancelBatchSelection = onCancelBatchSelection,
            proposalReviewContent = { actionsDisabled ->
                review?.let {
                    AiProposalReview(it, actionsDisabled, onApplyProposals, onDiscardProposals,
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
    messages: List<AiChatMessage>, busy: Boolean, batchActionsBusy: Boolean = false, pendingCount: Int,
    onSend: (String) -> Unit, onPropose: (String) -> Unit,
    onClear: () -> Unit, onClearCache: () -> Unit,
    proposalReview: AiInterpretation? = null,
    onApplyProposals: (Set<Int>, Boolean) -> Unit = { _, _ -> },
    onDiscardProposals: (Set<Int>) -> Unit = {},
    onSupplementProposals: () -> Unit = {},
    onSettings: () -> Unit, onOpenDeck: () -> Unit,
    onPickImage: () -> Unit, onPickFile: () -> Unit,
    onPrepareBatch: (AiBatchRunner.Mode) -> Unit, onRewind: (Int) -> Unit,
    batchSelectionActive: Boolean = false,
    onCancelBatchSelection: () -> Unit = {}
) {
    val context = AiVisualContext(messages, busy, batchActionsBusy, pendingCount, onSend, onPropose,
        onClear, onClearCache, proposalReview, onApplyProposals, onDiscardProposals,
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
    aiSession: PowerStore.AiSession?,
    canReleaseStuckBatch: Boolean,
    exactAlarmAllowed: Boolean,
    notificationsAllowed: Boolean,
    nowMillis: Long
): SettingsDisplaySnapshot = SettingsDisplaySnapshot(
    pendingCount = pendingCount,
    formalEventCount = records.size,
    nextSequenceLabel = TemporalRecord.formatSeq(diagnostics.nextSequence),
    identityCount = diagnostics.identities,
    databaseVersion = PowerStore.VERSION,
    databaseName = PowerStore.DATABASE_NAME,
    exactAlarmAllowed = exactAlarmAllowed,
    notificationsAllowed = notificationsAllowed,
    aiLocked = aiLocked,
    canReleaseStuckBatch = canReleaseStuckBatch,
    aiBatchActive = aiSession?.isActive == true,
    aiBatchCancelRequested = aiSession?.state == PowerStore.AiState.CANCEL_REQUESTED,
    reminders = diagnostics.reminders.map { row ->
        val state = row.intent?.desired
        SettingsReminderDiagnostic(
            eventLabel = row.record.displaySeq(),
            title = row.record.title,
            scheduleLabel = when (state) {
                PowerStore.DesiredSchedule.PENDING -> "等待调度"
                PowerStore.DesiredSchedule.SCHEDULED -> "已交给系统"
                PowerStore.DesiredSchedule.BLOCKED -> "受阻，请检查权限"
                PowerStore.DesiredSchedule.CANCELLED -> "已取消"
                PowerStore.DesiredSchedule.FIRED -> "已触发"
                null -> "无待执行调度"
            },
            blocked = state == PowerStore.DesiredSchedule.BLOCKED,
            androidHint = row.intent?.androidHint,
            execution = row.execution
        )
    },
    recurrence = records.filter { it.recurrence.isRepeating }.take(3).map { record ->
        val next = RecurrenceEngine.nextOccurrence(record, nowMillis)
        SettingsRecurrencePreview(
            label = "重复 #${record.eventSeq}",
            next = next?.let { "下一次 " + DraftForm.formatInstant(it.startMillis) } ?: "没有后续实例"
        )
    }
)

@Composable
internal fun ToolsPage(
    diagnostics: UiDiagnostics,
    exactAlarmAllowed: Boolean,
    notificationsAllowed: Boolean,
    nowMillis: Long,
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
    onReleaseStuck: () -> Unit
) {
    val settingsDisplay = remember(
        diagnostics, records, pendingCount, aiLocked, aiSession, canReleaseStuckBatch,
        exactAlarmAllowed, notificationsAllowed, nowMillis
    ) {
        projectSettingsDisplaySnapshot(
            diagnostics, records, pendingCount, aiLocked, aiSession, canReleaseStuckBatch,
            exactAlarmAllowed, notificationsAllowed, nowMillis
        )
    }
    val settingsEffects = SettingsVisualEffects(effects.blur, effects.animationScale, effects.reduceMotion)
    val skinOptions = remember(skinChoices) {
        skinChoices.map { SettingsSkinChoice(it.id, it.name, it.builtIn) }
    }
    val protectedProfile: @Composable () -> Unit = { HostProtectedProfileSection() }
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
