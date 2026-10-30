package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable

enum class NativeDeckKind(val label: String) {
    ALARM("闹钟"), ACTIVITY("活动"), DEADLINE("截止"), MEMO("备忘")
}

enum class NativeDeckOperation { CREATE, REPLACE, CONVERT, DELETE }
enum class NativeDeckStatus { PENDING, CONFLICT, DISCARDED, COMMITTED, INVALID }
enum class NativeDeckReminderMethod { MESSAGE, ALARM }
enum class NativeDeckReminderTiming { OFFSET, DAY_START }
enum class NativeDeckRecurrenceFreq { NONE, DAILY, WEEKLY, MONTHLY }

data class NativeDeckReminder(
    val method: NativeDeckReminderMethod,
    val timing: NativeDeckReminderTiming,
    val minutes: Int
)

/** Display projection only. Host-side updates are mapped onto the current Core form. */
data class NativeDeckForm(
    val kind: NativeDeckKind,
    val title: String,
    val startText: String,
    val endText: String,
    val notes: String,
    val location: String,
    val reminder: NativeDeckReminder,
    val enabled: Boolean,
    val trackingEnabled: Boolean,
    val sourceLabel: String,
    val zoneLabel: String,
    val recurrenceFreq: NativeDeckRecurrenceFreq,
    val recurrenceFromDate: String?,
    val recurrenceUntilDate: String?,
    val missingFields: List<String>,
    val publishable: Boolean,
    val validStart: Boolean,
    val validEnd: Boolean,
    val deadlineDateOnly: Boolean,
    val activityEndBeforeStart: Boolean,
    val recoveryScoped: Boolean,
    val occurrenceScoped: Boolean,
    val restrictOccurrenceFields: Boolean = false,
    val repeating: Boolean,
    val startPickerMillis: Long,
    val endPickerMillis: Long,
    val deadlinePrecisionPickerMillis: Long
)

data class NativeDeckCard(
    val cardKey: String,
    val sequenceLabel: String,
    val operation: NativeDeckOperation,
    val operationLabel: String,
    val status: NativeDeckStatus,
    val draftVersion: Long,
    val form: NativeDeckForm
)

data class NativeDeckSearchHit(
    val rowKey: String,
    val sequenceLabel: String,
    val title: String,
    val formal: Boolean
)

data class NativeDeckAiSession(val authorizedCount: Int, val remainingCount: Int, val lockEpoch: Long)
data class NativeDeckCompletion(val published: Int, val discarded: Int, val conflicts: Int)
data class NativeDeckDiffRow(val label: String, val before: String, val after: String, val changed: Boolean)
data class NativeDeckConflict(
    val sequenceLabel: String,
    val delete: Boolean,
    val currentRevision: Long?,
    val rows: List<NativeDeckDiffRow>
)
data class NativeDeckRestore(val sequenceLabel: String, val message: String)

data class NativeDeckState(
    val cards: List<NativeDeckCard>,
    val currentKey: String?,
    val currentForm: NativeDeckForm?,
    val position: Int,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val canUndoDiscard: Boolean,
    val busy: Boolean,
    val dirty: Boolean,
    val aiLocked: Boolean,
    val aiSession: NativeDeckAiSession?,
    val notice: String?,
    val error: String?,
    val conversionNote: String?,
    val completion: NativeDeckCompletion?,
    val conflict: NativeDeckConflict?,
    val restore: NativeDeckRestore?
)

data class NativeDeckDialog(
    val onDismissRequest: () -> Unit,
    val title: @Composable () -> Unit,
    val text: @Composable () -> Unit,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: (@Composable () -> Unit)? = null
)

typealias NativeDeckWindowSlot = @Composable (
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) -> Unit
typealias NativeDeckDialogSlot = @Composable (NativeDeckDialog) -> Unit
typealias NativeDeckBackdropSlot = @Composable () -> Unit
typealias NativeDeckImageSlot = @Composable () -> Unit

/** Compose values and Host-owned slots needed to preserve the first-party Native appearance. */
data class NativeDeckAppearance(
    val window: NativeDeckWindowSlot,
    val showDialog: NativeDeckDialogSlot,
    val backdrop: NativeDeckBackdropSlot,
    val completionEmblem: NativeDeckImageSlot,
    val hasCompletionEmblem: Boolean,
    val kindColors: Map<NativeDeckKind, Int>,
    val cardCornerRadiusDp: Float,
    val barCornerRadiusDp: Float,
    val chipCornerRadiusDp: Float,
    val blur: Boolean,
    val reduceMotion: Boolean
)

data class NativeDeckActions(
    val onDismiss: () -> Unit,
    val onCancelAiBatch: () -> Unit,
    val onOpenFormalEvent: (String) -> Unit,
    val onSelect: (String) -> Unit,
    val onBrowse: (Int) -> Unit,
    val onMove: (Int) -> Unit,
    val onSearch: (String) -> List<NativeDeckSearchHit>,
    val onFormChange: (NativeDeckForm) -> Unit,
    val onConvert: (NativeDeckKind) -> Unit,
    val onCommit: () -> Unit,
    val onDiscard: () -> Unit,
    val onRetrySave: () -> Unit,
    val onUndoDiscard: () -> Unit,
    val onConfirmConflict: () -> Unit,
    val onKeepConflict: () -> Unit,
    val onConfirmRestore: () -> Unit,
    val onDeclineRestore: () -> Unit,
    val onPickStartDate: (Long) -> Unit,
    val onPickStartTime: (Long) -> Unit,
    val onPickEndDate: (Long) -> Unit,
    val onPickEndTime: (Long) -> Unit,
    val onDeadlineDateToInstant: () -> Unit
)

data class NativeDraftDeckContext(
    val state: NativeDeckState,
    val actions: NativeDeckActions,
    val appearance: NativeDeckAppearance,
    val initialEditorOpen: Boolean = false,
    val transientNewEvent: Boolean = false,
    val alreadyPersisted: Boolean = false
)

data class NativeDeckFormActions(
    val onChange: (NativeDeckForm) -> Unit,
    val onPickStartDate: (Long) -> Unit,
    val onPickStartTime: (Long) -> Unit,
    val onPickEndDate: (Long) -> Unit,
    val onPickEndTime: (Long) -> Unit,
    val onDeadlineDateToInstant: () -> Unit
)

data class NativeDeckFormContext(
    val form: NativeDeckForm,
    val card: NativeDeckCard,
    val aiLocked: Boolean,
    val appearance: NativeDeckAppearance,
    val actions: NativeDeckFormActions
)

enum class NativeDirectEditScope { SINGLE_OCCURRENCE, THIS_AND_FUTURE, WHOLE_SERIES }

data class NativeDirectEditScopeOption(
    val scope: NativeDirectEditScope,
    val selected: Boolean,
    val enabled: Boolean
)

/** Presentation state and Host callbacks for the Native formal-event editor shell. */
data class NativeDirectEventEditorContext(
    val editorKey: String,
    val editorRevision: Int,
    val form: NativeDeckFormContext,
    val error: String?,
    val hasChanges: Boolean,
    val busy: Boolean,
    val canChangeKind: Boolean,
    val requiresRecurrenceScope: Boolean,
    val selectedRecurrenceScope: NativeDirectEditScope?,
    val recurrenceScopes: List<NativeDirectEditScopeOption>,
    val onChangeKind: (NativeDeckKind) -> Unit,
    val onChangeRecurrenceScope: (NativeDirectEditScope) -> Unit,
    val onSave: () -> Unit,
    val onDiscardChanges: () -> Unit,
    val onExit: () -> Unit,
    val onDelete: () -> Unit
)
