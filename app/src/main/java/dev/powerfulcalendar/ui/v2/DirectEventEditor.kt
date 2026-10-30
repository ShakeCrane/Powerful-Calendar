package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.ui.v2.nativeui.NativeDirectEventEditor
import dev.powerfulcalendar.ui.v2.nativeui.createPickerActions
import dev.powerfulcalendar.ui.v2.nativeui.nativeDeckAppearance
import dev.powerfulcalendar.ui.v2.nativeui.toCoreForm
import dev.powerfulcalendar.ui.v2.nativeui.toNativeDeckForm
import java.util.UUID

/** Host-owned window and callbacks surrounding the Native formal editor presentation. */
@Composable
internal fun DirectEventEditorDialog(
    session: EditSession,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    busy: Boolean = false,
    editorRevision: Int = 0,
    onChange: (DraftForm) -> Unit,
    onScopeChange: (RecurrenceEditScope) -> Unit = {},
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    SkinWindowDialog(onDismissRequest = onExit) {
        DirectEventEditorContent(
            session, form, error, hasChanges, busy, editorRevision, onChange,
            onScopeChange, onSave, onExit, onDiscardChanges, onDelete
        )
    }
}

/** Builds a display-only request; EditSession, conversion and every mutation remain Host-owned. */
@Composable
internal fun DirectEventEditorContent(
    session: EditSession,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    busy: Boolean = false,
    editorRevision: Int = 0,
    onChange: (DraftForm) -> Unit,
    onScopeChange: (RecurrenceEditScope) -> Unit = {},
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    val androidContext = LocalContext.current
    val pickerNowMillis = remember(session) { System.currentTimeMillis() }
    val editorKey = remember(session) { "${session.eventSeq}:${UUID.randomUUID()}" }
    val singleOccurrence = session.recurrenceScope == RecurrenceEditScope.SINGLE_OCCURRENCE
    val nativeForm = form.toNativeDeckForm(pickerNowMillis).copy(
        occurrenceScoped = singleOccurrence,
        restrictOccurrenceFields = singleOccurrence
    )
    val nativeCard = NativeDeckCard(
        cardKey = "formal:${session.eventSeq}",
        sequenceLabel = TemporalRecord.formatSeq(session.eventSeq),
        operation = NativeDeckOperation.REPLACE,
        operationLabel = "修改",
        status = NativeDeckStatus.PENDING,
        draftVersion = 0L,
        form = nativeForm
    )
    val pickerActions = createPickerActions(androidContext, form, pickerNowMillis, onChange)
    val scopeOptions = RecurrenceEditScope.entries.map { scope ->
        NativeDirectEditScopeOption(
            scope = scope.toNativeScope(),
            selected = session.recurrenceScope == scope,
            enabled = scope == RecurrenceEditScope.WHOLE_SERIES || session.canEditSelectedOccurrence
        )
    }
    val context = NativeDirectEventEditorContext(
        editorKey = editorKey,
        editorRevision = editorRevision,
        form = NativeDeckFormContext(
            form = nativeForm,
            card = nativeCard,
            aiLocked = false,
            appearance = nativeDeckAppearance(),
            actions = NativeDeckFormActions(
                onChange = { onChange(it.toCoreForm(form)) },
                onPickStartDate = pickerActions.startDate,
                onPickStartTime = pickerActions.startTime,
                onPickEndDate = pickerActions.endDate,
                onPickEndTime = pickerActions.endTime,
                onDeadlineDateToInstant = pickerActions.deadlineDateToInstant
            )
        ),
        error = error,
        hasChanges = hasChanges,
        busy = busy,
        canChangeKind = !singleOccurrence,
        requiresRecurrenceScope = session.requiresRecurrenceScope,
        selectedRecurrenceScope = session.recurrenceScope?.toNativeScope(),
        recurrenceScopes = scopeOptions,
        onChangeKind = { selected -> onChange(TemporalConversion.convert(form, selected.toCoreKind()).form) },
        onChangeRecurrenceScope = { selected -> onScopeChange(selected.toCoreScope()) },
        onSave = onSave,
        onDiscardChanges = onDiscardChanges,
        onExit = onExit,
        onDelete = onDelete
    )
    NativeDirectEventEditor(context)
}

private fun RecurrenceEditScope.toNativeScope() = when (this) {
    RecurrenceEditScope.SINGLE_OCCURRENCE -> NativeDirectEditScope.SINGLE_OCCURRENCE
    RecurrenceEditScope.THIS_AND_FUTURE -> NativeDirectEditScope.THIS_AND_FUTURE
    RecurrenceEditScope.WHOLE_SERIES -> NativeDirectEditScope.WHOLE_SERIES
}

private fun NativeDirectEditScope.toCoreScope() = when (this) {
    NativeDirectEditScope.SINGLE_OCCURRENCE -> RecurrenceEditScope.SINGLE_OCCURRENCE
    NativeDirectEditScope.THIS_AND_FUTURE -> RecurrenceEditScope.THIS_AND_FUTURE
    NativeDirectEditScope.WHOLE_SERIES -> RecurrenceEditScope.WHOLE_SERIES
}

private fun NativeDeckKind.toCoreKind() = when (this) {
    NativeDeckKind.ALARM -> TemporalKind.ALARM
    NativeDeckKind.ACTIVITY -> TemporalKind.ACTIVITY
    NativeDeckKind.DEADLINE -> TemporalKind.DEADLINE
    NativeDeckKind.MEMO -> TemporalKind.MEMO
}
