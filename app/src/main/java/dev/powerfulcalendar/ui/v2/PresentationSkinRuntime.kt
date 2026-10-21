package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalRecord
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
    val onOpen: (TemporalRecord) -> Unit,
    val onCreate: (LocalDate) -> Unit
) {
    @Composable fun Native() {
        NativeMonthScreen(records, initialDate, zone, aiLocked, onBack, onDate, onOpen, onCreate)
    }
}

@Composable
internal fun MonthScreen(
    records: List<TemporalRecord>, initialDate: LocalDate, zone: ZoneId, aiLocked: Boolean,
    onBack: () -> Unit, onDate: (LocalDate) -> Unit, onOpen: (TemporalRecord) -> Unit,
    onCreate: (LocalDate) -> Unit
) {
    val context = MonthVisualContext(records, initialDate, zone, aiLocked, onBack, onDate, onOpen, onCreate)
    val renderer = PresentationSkinRegistry.month
    if (renderer == null) context.Native() else renderer.Render(context)
}

internal class MemoVisualContext internal constructor(
    val items: List<TodoEntry>,
    val zone: ZoneId,
    val busy: Boolean,
    val onBack: () -> Unit,
    val onCreateMemo: () -> Unit,
    val onToggle: (TodoEntry) -> Unit,
    val onOpen: (TemporalRecord) -> Unit
) {
    @Composable fun Native() {
        NativeMemoCenterScreen(items, zone, busy, onBack, onCreateMemo, onToggle, onOpen)
    }
}

@Composable
internal fun MemoCenterScreen(
    items: List<TodoEntry>, zone: ZoneId, busy: Boolean,
    onBack: () -> Unit, onCreateMemo: () -> Unit,
    onToggle: (TodoEntry) -> Unit, onOpen: (TemporalRecord) -> Unit
) {
    val context = MemoVisualContext(items, zone, busy, onBack, onCreateMemo, onToggle, onOpen)
    val renderer = PresentationSkinRegistry.memo
    if (renderer == null) context.Native() else renderer.Render(context)
}

internal class AiVisualContext internal constructor(
    val messages: List<AiChatMessage>,
    val busy: Boolean,
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
    val onRewind: (Int) -> Unit
) {
    @Composable fun Native() {
        NativeAiChatScreen(messages, busy, pendingCount, onSend, onPropose,
            onClear, onClearCache, proposalReview, onApplyProposals,
            onDiscardProposals, onSupplementProposals, onSettings, onOpenDeck,
            onPickImage, onPickFile, onPrepareBatch, onRewind)
    }
}

@Composable
fun AiChatScreen(
    messages: List<AiChatMessage>, busy: Boolean, pendingCount: Int,
    onSend: (String) -> Unit, onPropose: (String) -> Unit,
    onClear: () -> Unit, onClearCache: () -> Unit,
    proposalReview: AiInterpretation? = null,
    onApplyProposals: (Set<Int>, Boolean) -> Unit = { _, _ -> },
    onDiscardProposals: (Set<Int>) -> Unit = {},
    onSupplementProposals: () -> Unit = {},
    onSettings: () -> Unit, onOpenDeck: () -> Unit,
    onPickImage: () -> Unit, onPickFile: () -> Unit,
    onPrepareBatch: (AiBatchRunner.Mode) -> Unit, onRewind: (Int) -> Unit
) {
    val context = AiVisualContext(messages, busy, pendingCount, onSend, onPropose,
        onClear, onClearCache, proposalReview, onApplyProposals, onDiscardProposals,
        onSupplementProposals, onSettings, onOpenDeck, onPickImage, onPickFile,
        onPrepareBatch, onRewind)
    val renderer = PresentationSkinRegistry.ai
    if (renderer == null) context.Native() else renderer.Render(context)
}

/**
 * Exposing only callbacks for settings actions prevents custom screens from needing access to
 * secret API keys. Native() hosts the existing secure credential form until a separate scoped
 * credential editor action is introduced. No credentials are handed into this context.
 */
internal class SettingsVisualContext internal constructor(
    val diagnostics: UiDiagnostics,
    val records: List<TemporalRecord>,
    val pendingCount: Int,
    val aiLocked: Boolean,
    val aiSession: PowerStore.AiSession?,
    val effects: GlassEffects,
    val skinChoices: List<InstalledSkin>,
    val selectedSkin: String,
    val onSelectSkin: (String) -> Unit,
    val onImportSkin: () -> Unit,
    val onExportOriginalSkin: () -> Unit,
    val onDeleteSkin: (String) -> Unit,
    val onBack: () -> Unit,
    val onOpenDeck: () -> Unit,
    val onImportIcs: () -> Unit,
    val onExportIcs: () -> Unit,
    val onImportSystem: () -> Unit,
    val onMigrateLegacy: () -> Unit,
    val onPickImage: () -> Unit,
    val onTextDraft: () -> Unit,
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onOpenExactAlarmSettings: () -> Unit,
    val onRunBatch: (AiBatchRunner.Mode) -> Unit,
    val onCancelBatch: () -> Unit,
    val onReconcile: () -> Unit,
    val onAiDraft: (String, Boolean) -> Unit,
    val aiBusy: Boolean,
    val onReleaseStuck: () -> Unit
) {
    @Composable fun ProtectedProfile() = ProtectedProfileSection()
    @Composable fun ProtectedAiCredentials() = ProtectedAiSettingsSection()
    @Composable fun Native() {
        NativeToolsPage(
            diagnostics, records, pendingCount, aiLocked, aiSession, effects, skinChoices,
            selectedSkin, onSelectSkin, onImportSkin, onExportOriginalSkin, onDeleteSkin,
            onBack, onOpenDeck, onImportIcs, onExportIcs, onImportSystem, onMigrateLegacy,
            onPickImage, onTextDraft, onExportBackup, onRestoreBackup,
            onRequestNotificationPermission, onOpenExactAlarmSettings, onRunBatch,
            onCancelBatch, onReconcile, onAiDraft, aiBusy, onReleaseStuck
        )
    }
}

@Composable
internal fun ToolsPage(
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
    val context = SettingsVisualContext(
        diagnostics, records, pendingCount, aiLocked, aiSession, effects, skinChoices,
        selectedSkin, onSelectSkin, onImportSkin, onExportOriginalSkin, onDeleteSkin,
        onBack, onOpenDeck, onImportIcs, onExportIcs, onImportSystem, onMigrateLegacy,
        onPickImage, onTextDraft, onExportBackup, onRestoreBackup,
        onRequestNotificationPermission, onOpenExactAlarmSettings, onRunBatch,
        onCancelBatch, onReconcile, onAiDraft, aiBusy, onReleaseStuck
    )
    val renderer = PresentationSkinRegistry.settings
    if (renderer == null) context.Native() else renderer.Render(context)
}

internal enum class VisualTab(val title: String) {
    TIMELINE("时间轴"), MONTH("月历"), AI("AI 助手"), TOOLS("设置")
}

internal class NavigationVisualContext internal constructor(
    val selected: VisualTab?,
    val onSelect: (VisualTab) -> Unit
) {
    @Composable fun Native() = NativePowerNavigation(selected, onSelect)
}

@Composable
internal fun PowerCalNavigation(selected: VisualTab?, onSelect: (VisualTab) -> Unit) {
    val context = NavigationVisualContext(selected, onSelect)
    val renderer = PresentationSkinRegistry.navigation
    if (renderer == null) context.Native() else renderer.Render(context)
}
