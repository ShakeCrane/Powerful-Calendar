package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.EditorDefaults
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventValidationResultV1
import dev.powerfulcalendar.core.api.v1.EventValidationStatusV1
import dev.powerfulcalendar.core.api.v1.EventSearchHitV1
import dev.powerfulcalendar.core.api.v1.DraftReviewFeatureApiV1
import dev.powerfulcalendar.core.api.v1.DraftReviewSnapshotV1
import dev.powerfulcalendar.core.api.v1.DraftReviewCardV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReceiptV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReceiptStatusV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReadinessV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReadinessStatusV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toCoreTemporalRecord
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import dev.powerfulcalendar.core.api.v1.AiBatchSessionV1
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.ui.v2.LocalGlassEffects
import dev.powerfulcalendar.ui.v2.LocalSkinVisual
import dev.powerfulcalendar.ui.v2.NativeDeckActions
import dev.powerfulcalendar.ui.v2.NativeDeckAiSession
import dev.powerfulcalendar.ui.v2.NativeDeckAppearance
import dev.powerfulcalendar.ui.v2.NativeDeckCard
import dev.powerfulcalendar.ui.v2.NativeDeckCompletion
import dev.powerfulcalendar.ui.v2.NativeDeckConflict
import dev.powerfulcalendar.ui.v2.NativeDeckDialog
import dev.powerfulcalendar.ui.v2.NativeDeckDiffRow
import dev.powerfulcalendar.ui.v2.NativeDeckForm
import dev.powerfulcalendar.ui.v2.NativeDeckKind
import dev.powerfulcalendar.ui.v2.NativeDeckOperation
import dev.powerfulcalendar.ui.v2.NativeDeckRecurrenceFreq
import dev.powerfulcalendar.ui.v2.NativeDeckReminder
import dev.powerfulcalendar.ui.v2.NativeDeckReminderMethod
import dev.powerfulcalendar.ui.v2.NativeDeckReminderTiming
import dev.powerfulcalendar.ui.v2.NativeDeckRestore
import dev.powerfulcalendar.ui.v2.NativeDeckSearchHit
import dev.powerfulcalendar.ui.v2.NativeDeckState
import dev.powerfulcalendar.ui.v2.NativeDeckStatus
import dev.powerfulcalendar.ui.v2.SkinAlertDialog
import dev.powerfulcalendar.ui.v2.SkinImage
import dev.powerfulcalendar.ui.v2.SkinPickers
import dev.powerfulcalendar.ui.v2.SkinSlot
import dev.powerfulcalendar.ui.v2.SkinWindowDialog
import dev.powerfulcalendar.ui.v2.kindColor
import dev.powerfulcalendar.ui.v2.skinBackdrop
import dev.powerfulcalendar.ui.v2.skinPainter
import dev.powerfulcalendar.ui.v2.snapshotRows
import dev.powerfulcalendar.ui.v2.nativeui.NativeDraftDeckScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Android/Core Host for the Native Draft Deck. The renderer receives only display projections and
 * callbacks; controller observation, serial commands, validation, persistence and picker results
 * stay in this module.
 */
@Composable
fun NativeDraftDeckDialog(
    eventFeatureApi: EventFeatureApiV1,
    draftReviewApi: DraftReviewFeatureApiV1,
    aiSession: AiBatchSessionV1?,
    onDismissRequest: () -> Unit,
    onOpenFormalEvent: (EventSearchHitV1) -> Unit,
    onCancelAiBatch: () -> Unit,
    onDeckChanged: () -> Unit,
    formalSearch: suspend (String) -> List<EventSearchHitV1>,
    initialEditorOpen: Boolean = false
) {
    var state by remember(draftReviewApi) { mutableStateOf(draftReviewApi.snapshot) }
    var busy by remember(draftReviewApi) { mutableStateOf(false) }
    var conversionNote by remember(draftReviewApi) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val pickerNowMillis = remember(draftReviewApi) { System.currentTimeMillis() }
    val formalRows = remember(draftReviewApi) { mutableMapOf<String, EventSearchHitV1>() }

    DisposableEffect(draftReviewApi) {
        val subscription = draftReviewApi.observe { next -> scope.launch { state = next } }
        state = draftReviewApi.snapshot
        onDispose { subscription.close() }
    }
    LaunchedEffect(state.currentId) { conversionNote = null }

    fun editCoreForm(updated: DraftForm) {
        if (busy || state.aiLocked) return
        val current = draftReviewApi.snapshot
        val card = current.current ?: return
        val receipt = draftReviewApi.editCurrentDraft(
            card.draftId, card.draftVersion, current.stateRevision, updated.toEventFormV1()
        )
        if (!receipt.succeeded) state = current.copy(error = receipt.message ?: receipt.errorCode)
    }

    fun command(changed: Boolean = false, block: suspend () -> DraftReviewReceiptV1) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) { block() }
                state = draftReviewApi.snapshot
                if (changed && receipt.succeeded) onDeckChanged()
                if (!receipt.succeeded) {
                    state = state.copy(error = receipt.message ?: receipt.errorCode ?: "操作未完成，请重试")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = draftReviewApi.snapshot.copy(error = error.message ?: "操作未完成，草稿已保留，请重试")
            } finally {
                busy = false
            }
        }
    }
    fun leave(afterSave: () -> Unit = onDismissRequest) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) {
                    draftReviewApi.flush("native-deck-leave-${java.util.UUID.randomUUID()}")
                }
                if (receipt.succeeded) afterSave()
                state = draftReviewApi.snapshot
                if (!receipt.succeeded) state = state.copy(
                    error = receipt.message ?: receipt.errorCode ?: "暂存失败，请重试"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = draftReviewApi.snapshot.copy(error = "暂存失败，请重试：${error.message.orEmpty()}")
            } finally {
                busy = false
            }
        }
    }

    val currentForm = state.form?.toCoreDraftForm()
    fun editViewForm(view: NativeDeckForm) {
        if (busy || state.aiLocked) return
        val current = draftReviewApi.snapshot
        val base = current.form?.toCoreDraftForm() ?: return
        val card = current.current ?: return
        draftReviewApi.editCurrentDraft(
            card.draftId, card.draftVersion, current.stateRevision,
            view.toCoreForm(base).toEventFormV1()
        )
    }
    val projected = state.toNativeDeckState(pickerNowMillis, busy, conversionNote, aiSession)
    val pickerActions = currentForm?.let { form ->
        createPickerActions(androidContext, form, pickerNowMillis, ::editCoreForm)
    }
    val actions = NativeDeckActions(
        onDismiss = { leave() },
        onCancelAiBatch = onCancelAiBatch,
        onOpenFormalEvent = { key -> formalRows[key]?.let { hit -> leave { onOpenFormalEvent(hit) } } },
        onSelect = { key -> key.removePrefix("draft:").toLongOrNull()?.let { id ->
            command { draftReviewApi.select(id, java.util.UUID.randomUUID().toString()) }
        } },
        onBrowse = { steps -> command {
            draftReviewApi.jump(steps, java.util.UUID.randomUUID().toString())
        } },
        onMove = { steps -> command {
            draftReviewApi.move(steps, java.util.UUID.randomUUID().toString())
        } },
        onSearch = { query ->
            formalRows.clear()
            val drafts = draftReviewApi.searchCards(query, state.cards).take(4).map { hit ->
                NativeDeckSearchHit("draft:${hit.draftId}", TemporalRecord.formatSeq(hit.eventSeq),
                    hit.form.title, formal = false)
            }
            val formal = formalSearch(query).take(4).map { record ->
                val key = "formal:${record.eventSeq}:${record.revision}"
                formalRows[key] = record
                NativeDeckSearchHit(key, TemporalRecord.formatSeq(record.eventSeq), record.title, formal = true)
            }
            drafts + formal
        },
        onFormChange = ::editViewForm,
        onConvert = { kind ->
            if (!busy && !state.aiLocked) state.form?.let { form ->
                val result = eventFeatureApi.convertFormKind(form, kind.toEventKind())
                val current = draftReviewApi.snapshot
                current.current?.let { card ->
                    draftReviewApi.editCurrentDraft(
                        card.draftId, card.draftVersion, current.stateRevision,
                        result.form
                    )
                }
                conversionNote = result.note.ifBlank { null }
            }
        },
        onCommit = { draftReviewApi.snapshot.let { snapshot -> snapshot.current?.let { card ->
            command(changed = true) { draftReviewApi.commit(
                card.draftId, card.draftVersion, snapshot.stateRevision,
                java.util.UUID.randomUUID().toString()
            ) }
        } } },
        onDiscard = { draftReviewApi.snapshot.current?.let { card ->
            command(changed = true) { draftReviewApi.discard(
                card.draftId, card.draftVersion, java.util.UUID.randomUUID().toString()
            ) }
        } },
        onRetrySave = { command {
            draftReviewApi.flush(java.util.UUID.randomUUID().toString())
        } },
        onUndoDiscard = { draftReviewApi.snapshot.lastDiscardId?.let { draftId ->
            command(changed = true) { draftReviewApi.undoDiscard(
                draftId, java.util.UUID.randomUUID().toString()
            ) }
        } },
        onConfirmConflict = { draftReviewApi.snapshot.let { snapshot -> snapshot.conflict?.let { prompt ->
            prompt.currentRevision?.let { expectedRevision -> command(changed = true) {
                draftReviewApi.confirmConflictReplacement(
                    prompt.draftId, prompt.card.draftVersion, snapshot.stateRevision, expectedRevision,
                    java.util.UUID.randomUUID().toString()
                )
            } }
        } } },
        onKeepConflict = { draftReviewApi.dismissConflict() },
        onConfirmRestore = { draftReviewApi.snapshot.let { snapshot -> snapshot.restore?.let { prompt ->
            prompt.card.baseRevision?.let { revision -> command(changed = true) {
                draftReviewApi.requestRestore(
                    prompt.card.draftId, prompt.card.draftVersion, snapshot.stateRevision, revision,
                    java.util.UUID.randomUUID().toString()
                )
            } }
        } } },
        onDeclineRestore = { draftReviewApi.dismissRestore() },
        onPickStartDate = { base -> pickerActions?.startDate?.invoke(base) },
        onPickStartTime = { base -> pickerActions?.startTime?.invoke(base) },
        onPickEndDate = { base -> pickerActions?.endDate?.invoke(base) },
        onPickEndTime = { base -> pickerActions?.endTime?.invoke(base) },
        onDeadlineDateToInstant = { pickerActions?.deadlineDateToInstant?.invoke() }
    )
    NativeDraftDeckScreen(
        context = dev.powerfulcalendar.ui.v2.NativeDraftDeckContext(
            state = projected,
            actions = actions,
            appearance = nativeDeckAppearance(),
            initialEditorOpen = initialEditorOpen
        )
    )

}

/** Ordinary new-event editor. The session and its memory-only baseline remain Host-owned. */
@Composable
internal fun NewEventEditorDialog(
    eventFeatureApi: EventFeatureApiV1,
    editor: EventEditorSnapshotV1,
    form: DraftForm,
    error: String?,
    busy: Boolean,
    onFormChange: (DraftForm) -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscard: () -> Unit
) {
    val androidContext = LocalContext.current
    val nowMillis = editor.defaultNowMillis ?: System.currentTimeMillis()
    val pickerActions = createPickerActions(androidContext, form, nowMillis, onFormChange)
    val validation = remember(eventFeatureApi, editor.handle, form) {
        eventFeatureApi.validateNewEvent(editor.handle)
    }
    val card = NativeDeckCard(
        cardKey = "new:${editor.handle.value}",
        sequenceLabel = "新建",
        operation = NativeDeckOperation.CREATE,
        operationLabel = "新建",
        status = NativeDeckStatus.PENDING,
        draftVersion = editor.draftVersion ?: 0,
        form = form.toNativeDeckForm(nowMillis, eventValidation = validation)
    )
    val state = NativeDeckState(
        cards = listOf(card), currentKey = card.cardKey, currentForm = card.form, position = 0,
        canGoPrevious = false, canGoNext = false, canUndoDiscard = false,
        busy = busy, dirty = editor.meaningfulChanges, aiLocked = false, aiSession = null,
        notice = null, error = error, conversionNote = null, completion = null, conflict = null, restore = null
    )
    val actions = NativeDeckActions(
        onDismiss = onExit,
        onCancelAiBatch = {},
        onOpenFormalEvent = {},
        onSelect = {},
        onBrowse = {},
        onMove = {},
        onSearch = { emptyList() },
        onFormChange = { onFormChange(it.toCoreForm(form)) },
        onConvert = { target ->
            val converted = eventFeatureApi.convertFormKind(
                form.toEventFormV1(), EventKindV1.valueOf(target.name)
            )
            onFormChange(converted.form.toCoreDraftForm())
        },
        onCommit = onSave,
        onDiscard = onDiscard,
        onRetrySave = onSave,
        onUndoDiscard = {},
        onConfirmConflict = {},
        onKeepConflict = {},
        onConfirmRestore = {},
        onDeclineRestore = {},
        onPickStartDate = pickerActions.startDate,
        onPickStartTime = pickerActions.startTime,
        onPickEndDate = pickerActions.endDate,
        onPickEndTime = pickerActions.endTime,
        onDeadlineDateToInstant = pickerActions.deadlineDateToInstant
    )
    NativeDraftDeckScreen(dev.powerfulcalendar.ui.v2.NativeDraftDeckContext(
        state = state, actions = actions, appearance = nativeDeckAppearance(),
        initialEditorOpen = true, transientNewEvent = true,
        alreadyPersisted = editor.hasPersistedDraft
    ))
}

@Composable
internal fun nativeDeckAppearance(): NativeDeckAppearance {
    val effects = LocalGlassEffects.current
    val visual = LocalSkinVisual.current
    val emblemAvailable = skinPainter(SkinSlot.COMPLETION_EMBLEM) != null
    return NativeDeckAppearance(
        window = { onDismiss, content ->
            SkinWindowDialog(onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false,
                    decorFitsSystemWindows = false), content = content)
        },
        showDialog = { dialog ->
            SkinAlertDialog(onDismissRequest = dialog.onDismissRequest,
                title = dialog.title, text = dialog.text,
                confirmButton = dialog.confirmButton, dismissButton = dialog.dismissButton)
        },
        backdrop = {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.fillMaxSize().skinBackdrop(SkinSlot.DECK_BACKDROP)
            )
        },
        completionEmblem = { SkinImage(SkinSlot.COMPLETION_EMBLEM, androidx.compose.ui.Modifier.size(46.dp)) },
        hasCompletionEmblem = emblemAvailable,
        kindColors = NativeDeckKind.entries.associateWith { kind ->
            kindColor(kind.toCoreKind()).toArgb()
        },
        cardCornerRadiusDp = visual.cardRadiusDp,
        barCornerRadiusDp = visual.barRadiusDp,
        chipCornerRadiusDp = 14f,
        blur = effects.blur,
        reduceMotion = effects.reduceMotion
    )
}

internal data class HostPickerActions(
    val startDate: (Long) -> Unit,
    val startTime: (Long) -> Unit,
    val endDate: (Long) -> Unit,
    val endTime: (Long) -> Unit,
    val deadlineDateToInstant: () -> Unit
)

internal fun createPickerActions(
    context: Context,
    form: DraftForm,
    nowMillis: Long,
    onChange: (DraftForm) -> Unit
): HostPickerActions {
    val defaults = EditorDefaults.capture(Clock.fixed(Instant.ofEpochMilli(nowMillis), form.zone), form.zone)
    return HostPickerActions(
        startDate = { baseMillis ->
            SkinPickers.pickDate(context, baseMillis, form.zone) { date ->
                val time = form.parseStart()?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalTime() }
                    ?: defaults.now.atZone(form.zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                onChange(form.copy(startText = DraftForm.formatInstant(
                    date.atTime(time).atZone(form.zone).toInstant().toEpochMilli(), form.zone)))
            }
        },
        startTime = { baseMillis ->
            SkinPickers.pickTime(context, baseMillis, form.zone) { hour, minute ->
                val date = form.parseStart()?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalDate() }
                    ?: defaults.today
                onChange(form.copy(startText = DraftForm.formatInstant(
                    date.atTime(hour, minute).atZone(form.zone).toInstant().toEpochMilli(), form.zone)))
            }
        },
        endDate = { baseMillis ->
            SkinPickers.pickDate(context, baseMillis, form.zone) { date ->
                if (form.kind == TemporalKind.DEADLINE && form.endText.trim().length == 10) {
                    onChange(form.copy(endText = date.toString()))
                } else {
                    val time = form.parseEnd()?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalTime() }
                        ?: form.parseStart()?.let { Instant.ofEpochMilli(it + 3_600_000L).atZone(form.zone).toLocalTime() }
                        ?: defaults.now.atZone(form.zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                    onChange(form.copy(endText = DraftForm.formatInstant(
                        date.atTime(time).atZone(form.zone).toInstant().toEpochMilli(), form.zone)))
                }
            }
        },
        endTime = { baseMillis ->
            SkinPickers.pickTime(context, baseMillis, form.zone) { hour, minute ->
                val date = form.parseEnd()?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalDate() }
                    ?: DraftForm.parseDateOnly(form.endText)
                    ?: form.parseStart()?.let { Instant.ofEpochMilli(it + 3_600_000L).atZone(form.zone).toLocalDate() }
                    ?: defaults.today
                onChange(form.copy(endText = DraftForm.formatInstant(
                    date.atTime(hour, minute).atZone(form.zone).toInstant().toEpochMilli(), form.zone)))
            }
        },
        deadlineDateToInstant = {
            val date = DraftForm.parseDateOnly(form.endText) ?: defaults.today
            val base = form.parseEnd() ?: defaults.pickerInstant(date)
            SkinPickers.pickTime(context, base, form.zone) { hour, minute ->
                onChange(form.copy(endText = DraftForm.formatInstant(
                    date.atTime(hour, minute).atZone(form.zone).toInstant().toEpochMilli(), form.zone)))
            }
        }
    )
}

internal fun DraftForm.toNativeDeckForm(
    pickerNowMillis: Long,
    readiness: DraftReviewReadinessV1? = null,
    eventValidation: EventValidationResultV1? = null
): NativeDeckForm {
    val defaults = EditorDefaults.capture(Clock.fixed(Instant.ofEpochMilli(pickerNowMillis), zone), zone)
    val start = if (readiness != null) readiness.startMillis else parseStart()
    val end = if (readiness != null) readiness.endMillis else parseEnd()
    val dateOnly = if (readiness != null) {
        readiness.deadlineDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    } else DraftForm.parseDateOnly(endText)
    val nextHour = start?.plus(3_600_000L)
    val activityEndBeforeStart = kind == TemporalKind.ACTIVITY &&
        start != null && end != null && end <= start
    return NativeDeckForm(
        kind = kind.toNativeKind(), title = title, startText = startText, endText = endText,
        notes = notes, location = location,
        reminder = NativeDeckReminder(
            method = if (reminderMethod == ReminderMethod.MESSAGE) NativeDeckReminderMethod.MESSAGE else NativeDeckReminderMethod.ALARM,
            timing = if (reminderTiming == ReminderTiming.OFFSET) NativeDeckReminderTiming.OFFSET else NativeDeckReminderTiming.DAY_START,
            minutes = reminderMinutes
        ),
        enabled = enabled, trackingEnabled = trackingEnabled,
        sourceLabel = TemporalText.sourceLabel(source), zoneLabel = zoneId,
        recurrenceFreq = recurrence.freq.toNativeFreq(),
        recurrenceFromDate = recurrence.activeFromDate, recurrenceUntilDate = recurrence.activeUntilDate,
        missingFields = readiness?.missingFields?.map(::draftFieldLabel)
            ?: eventValidation?.missingFields?.map(::draftFieldLabel)
            ?: missingFields().map { it.label },
        publishable = readiness?.canCommit
            ?: eventValidation?.let { it.status == EventValidationStatusV1.READY }
            ?: isPublishable(),
        validStart = readiness?.validStart ?: when (kind) {
            TemporalKind.ALARM, TemporalKind.ACTIVITY -> start != null
            TemporalKind.MEMO -> startText.isBlank() || DraftForm.parseDateOnly(startText) != null
            TemporalKind.DEADLINE -> true
        },
        validEnd = readiness?.validEnd ?: when (kind) {
            TemporalKind.ACTIVITY -> end != null
            TemporalKind.DEADLINE -> parseDeadline() != null
            TemporalKind.MEMO -> endText.isBlank() || DraftForm.parseDateOnly(endText) != null
            TemporalKind.ALARM -> true
        },
        deadlineDateOnly = readiness?.deadlineDateOnly ?: (kind == TemporalKind.DEADLINE && dateOnly != null),
        activityEndBeforeStart = readiness?.activityEndBeforeStart ?: activityEndBeforeStart,
        recoveryScoped = recurrenceEditScope != null,
        occurrenceScoped = editOccurrenceKey != null,
        repeating = recurrence.isRepeating,
        startPickerMillis = start ?: defaults.pickerInstant(),
        endPickerMillis = end ?: dateOnly?.let(defaults::pickerInstant) ?: nextHour ?: defaults.pickerInstant(),
        deadlinePrecisionPickerMillis = end ?: dateOnly?.let(defaults::pickerInstant) ?: defaults.pickerInstant(),
        readinessMessage = readiness?.message?.takeIf {
            readiness.status == DraftReviewReadinessStatusV1.INVALID || readiness.activityEndBeforeStart
        } ?: eventValidation?.takeIf {
            it.status == EventValidationStatusV1.INVALID || activityEndBeforeStart
        }?.message
    )
}

internal fun NativeDeckForm.toCoreForm(base: DraftForm): DraftForm {
    val recurrenceFreq = recurrenceFreq.toCoreFreq()
    return base.copy(
        kind = kind.toCoreKind(), title = title, startText = startText, endText = endText,
        notes = notes, location = location,
        reminderMethod = if (reminder.method == NativeDeckReminderMethod.MESSAGE) ReminderMethod.MESSAGE else ReminderMethod.ALARM,
        reminderTiming = if (reminder.timing == NativeDeckReminderTiming.OFFSET) ReminderTiming.OFFSET else ReminderTiming.DAY_START,
        reminderMinutes = reminder.minutes,
        enabled = enabled, trackingEnabled = trackingEnabled,
        recurrence = if (recurrenceFreq == RecurrenceFreq.NONE) Recurrence.NONE else base.recurrence.copy(
            freq = recurrenceFreq,
            activeFromDate = recurrenceFromDate,
            activeUntilDate = recurrenceUntilDate
        )
    )
}

private fun DraftReviewSnapshotV1.toNativeDeckState(
    pickerNowMillis: Long,
    commandBusy: Boolean,
    conversionNote: String?,
    aiSession: AiBatchSessionV1?
): NativeDeckState {
    val drafts = cards.map { it.toNativeDeckCard(pickerNowMillis) }
    val currentForm = form?.toCoreDraftForm()?.toNativeDeckForm(pickerNowMillis, currentReadiness)
    val conflict = conflict?.let { prompt ->
        val current = prompt.current?.toCoreTemporalRecord()
        val approvedForm = prompt.approvedForm.toCoreDraftForm()
        NativeDeckConflict(
            sequenceLabel = TemporalRecord.formatSeq(prompt.eventSeq),
            delete = prompt.card.operation == dev.powerfulcalendar.core.api.v1.DraftOperationV1.DELETE,
            currentRevision = prompt.currentRevision,
            rows = snapshotRows(current, approvedForm).map {
                NativeDeckDiffRow(it.label, it.before, it.after, it.changed)
            }
        )
    }
    return NativeDeckState(
        cards = drafts,
        currentKey = currentId?.let { "draft:$it" },
        currentForm = currentForm,
        position = position,
        canGoPrevious = canGoPrevious,
        canGoNext = canGoNext,
        canUndoDiscard = canUndoDiscard,
        busy = commandBusy || busy,
        dirty = dirty,
        aiLocked = aiLocked,
        aiSession = aiSession?.let { session ->
            NativeDeckAiSession(session.scope.size, cards.count { it.draftId in session.scope })
        },
        notice = notice,
        error = error,
        conversionNote = conversionNote,
        completion = completion?.let { NativeDeckCompletion(it.published, it.discarded, it.conflicts) },
        conflict = conflict,
        restore = restore?.let { NativeDeckRestore(TemporalRecord.formatSeq(it.eventSeq), it.message) }
    )
}

private fun DraftReviewCardV1.toNativeDeckCard(pickerNowMillis: Long) = NativeDeckCard(
    cardKey = "draft:$draftId",
    sequenceLabel = TemporalRecord.formatSeq(eventSeq),
    operation = DraftOperation.valueOf(operation.name).toNativeOperation(),
    operationLabel = DraftOperation.valueOf(operation.name).label,
    status = DraftStatus.valueOf(status.name).toNativeStatus(),
    draftVersion = draftVersion,
    form = form.toCoreDraftForm().toNativeDeckForm(pickerNowMillis, readiness)
)

private fun draftFieldLabel(field: String): String = when (field) {
    "TITLE" -> "标题"
    "START" -> "开始时间"
    "END" -> "结束时间"
    "DEADLINE" -> "截止日期或时刻"
    else -> field
}

private fun TemporalKind.toNativeKind() = when (this) {
    TemporalKind.ALARM -> NativeDeckKind.ALARM
    TemporalKind.ACTIVITY -> NativeDeckKind.ACTIVITY
    TemporalKind.DEADLINE -> NativeDeckKind.DEADLINE
    TemporalKind.MEMO -> NativeDeckKind.MEMO
}

private fun NativeDeckKind.toCoreKind() = when (this) {
    NativeDeckKind.ALARM -> TemporalKind.ALARM
    NativeDeckKind.ACTIVITY -> TemporalKind.ACTIVITY
    NativeDeckKind.DEADLINE -> TemporalKind.DEADLINE
    NativeDeckKind.MEMO -> TemporalKind.MEMO
}

private fun NativeDeckKind.toEventKind() = EventKindV1.valueOf(name)

private fun RecurrenceFreq.toNativeFreq() = when (this) {
    RecurrenceFreq.NONE -> NativeDeckRecurrenceFreq.NONE
    RecurrenceFreq.DAILY -> NativeDeckRecurrenceFreq.DAILY
    RecurrenceFreq.WEEKLY -> NativeDeckRecurrenceFreq.WEEKLY
    RecurrenceFreq.MONTHLY -> NativeDeckRecurrenceFreq.MONTHLY
}

private fun NativeDeckRecurrenceFreq.toCoreFreq() = when (this) {
    NativeDeckRecurrenceFreq.NONE -> RecurrenceFreq.NONE
    NativeDeckRecurrenceFreq.DAILY -> RecurrenceFreq.DAILY
    NativeDeckRecurrenceFreq.WEEKLY -> RecurrenceFreq.WEEKLY
    NativeDeckRecurrenceFreq.MONTHLY -> RecurrenceFreq.MONTHLY
}

private fun DraftOperation.toNativeOperation() = when (this) {
    DraftOperation.CREATE -> NativeDeckOperation.CREATE
    DraftOperation.REPLACE -> NativeDeckOperation.REPLACE
    DraftOperation.CONVERT -> NativeDeckOperation.CONVERT
    DraftOperation.DELETE -> NativeDeckOperation.DELETE
}

private fun DraftStatus.toNativeStatus() = when (this) {
    DraftStatus.PENDING -> NativeDeckStatus.PENDING
    DraftStatus.CONFLICT -> NativeDeckStatus.CONFLICT
    DraftStatus.DISCARDED -> NativeDeckStatus.DISCARDED
    DraftStatus.COMMITTED -> NativeDeckStatus.COMMITTED
    DraftStatus.INVALID -> NativeDeckStatus.INVALID
}
