package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventRecurrenceScopeV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import dev.powerfulcalendar.ui.v2.nativeui.NativeDirectEventEditor
import java.util.UUID

/** Host-owned window and callbacks surrounding the Native formal editor presentation. */
@Composable
internal fun DirectEventEditorDialog(
    eventFeatureApi: EventFeatureApiV1,
    editor: EventEditorSnapshotV1,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    busy: Boolean = false,
    editorRevision: Int = 0,
    onChange: (DraftForm) -> Unit,
    onScopeChange: (EventRecurrenceScopeV1) -> Unit = {},
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    SkinWindowDialog(onDismissRequest = onExit) {
        DirectEventEditorContent(
            eventFeatureApi, editor, form, error, hasChanges, busy, editorRevision, onChange,
            onScopeChange, onSave, onExit, onDiscardChanges, onDelete
        )
    }
}

/** Builds a display-only request; Core editor handles and all mutations remain Host-owned. */
@Composable
internal fun DirectEventEditorContent(
    eventFeatureApi: EventFeatureApiV1,
    editor: EventEditorSnapshotV1,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    busy: Boolean = false,
    editorRevision: Int = 0,
    onChange: (DraftForm) -> Unit,
    onScopeChange: (EventRecurrenceScopeV1) -> Unit = {},
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    val androidContext = LocalContext.current
    val eventSeq = requireNotNull(editor.eventSeq)
    val recurrenceScope = editor.form.recurrenceScope
    val pickerNowMillis = remember(editor.handle) { System.currentTimeMillis() }
    val editorKey = remember(editor.handle) { editor.handle.value }
    val singleOccurrence = recurrenceScope == EventRecurrenceScopeV1.SINGLE_OCCURRENCE
    val nativeForm = form.toNativeDeckForm(pickerNowMillis).copy(
        occurrenceScoped = singleOccurrence,
        restrictOccurrenceFields = singleOccurrence
    )
    val nativeCard = NativeDeckCard(
        cardKey = "formal:$eventSeq",
        sequenceLabel = TemporalRecord.formatSeq(eventSeq),
        operation = NativeDeckOperation.REPLACE,
        operationLabel = "修改",
        status = NativeDeckStatus.PENDING,
        draftVersion = 0L,
        form = nativeForm
    )
    val pickerActions = createPickerActions(androidContext, form, pickerNowMillis, onChange)
    val scopeOptions = EventRecurrenceScopeV1.entries.map { scope ->
        NativeDirectEditScopeOption(
            scope = NativeDirectEditScope.valueOf(scope.name),
            selected = recurrenceScope == scope,
            enabled = scope == EventRecurrenceScopeV1.WHOLE_SERIES ||
                editor.selectedOccurrenceKey != null
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
        requiresRecurrenceScope = editor.recurrenceScopeRequired,
        selectedRecurrenceScope = recurrenceScope?.let { NativeDirectEditScope.valueOf(it.name) },
        recurrenceScopes = scopeOptions,
        onChangeKind = { selected ->
            val converted = eventFeatureApi.convertFormKind(
                form.toEventFormV1(), EventKindV1.valueOf(selected.name)
            )
            onChange(converted.form.toCoreDraftForm())
        },
        onChangeRecurrenceScope = { selected ->
            onScopeChange(EventRecurrenceScopeV1.valueOf(selected.name))
        },
        onSave = onSave,
        onDiscardChanges = onDiscardChanges,
        onExit = onExit,
        onDelete = onDelete
    )
    NativeDirectEventEditor(context)
}
