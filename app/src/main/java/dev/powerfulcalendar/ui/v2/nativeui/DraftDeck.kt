package dev.powerfulcalendar.ui.v2.nativeui

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
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftQueueSearch
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.EditorDefaults
import dev.powerfulcalendar.core.NewEventEditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
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
    controller: DeckController,
    aiSession: PowerStore.AiSession?,
    onDismissRequest: () -> Unit,
    onOpenFormalEvent: (TemporalRecord) -> Unit,
    onCancelAiBatch: () -> Unit,
    onDeckChanged: () -> Unit,
    formalSearch: (String) -> List<TemporalRecord>,
    initialEditorOpen: Boolean = false
) {
    var state by remember(controller) { mutableStateOf(controller.state) }
    var busy by remember(controller) { mutableStateOf(false) }
    var conversionNote by remember(controller) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val pickerNowMillis = remember(controller) { System.currentTimeMillis() }
    val formalRows = remember(controller) { mutableMapOf<String, TemporalRecord>() }

    DisposableEffect(controller) {
        controller.observe { next -> scope.launch { state = next } }
        state = controller.state
        onDispose { controller.observe { } }
    }
    LaunchedEffect(state.currentId) { conversionNote = null }

    fun editCoreForm(updated: DraftForm) {
        if (busy || state.aiLocked) return
        controller.edit(updated)
    }

    fun command(changed: Boolean = false, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                state = controller.state
                if (changed) onDeckChanged()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = controller.state.copy(error = error.message ?: "操作未完成，草稿已保留，请重试")
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
                if (withContext(Dispatchers.IO) { controller.flush() }) afterSave()
                state = controller.state
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = controller.state.copy(error = "暂存失败，请重试：${error.message.orEmpty()}")
            } finally {
                busy = false
            }
        }
    }

    val currentForm = state.form
    fun editViewForm(view: NativeDeckForm) {
        if (busy || state.aiLocked) return
        val base = controller.state.form ?: return
        controller.edit(view.toCoreForm(base))
    }
    val projected = state.toNativeDeckState(pickerNowMillis, busy, conversionNote, aiSession)
    val pickerActions = currentForm?.let { form ->
        createPickerActions(androidContext, form, pickerNowMillis, ::editCoreForm)
    }
    val actions = NativeDeckActions(
        onDismiss = { leave() },
        onCancelAiBatch = onCancelAiBatch,
        onOpenFormalEvent = { key -> formalRows[key]?.let { record -> leave { onOpenFormalEvent(record) } } },
        onSelect = { key -> key.removePrefix("draft:").toLongOrNull()?.let { id -> command { controller.select(id) } } },
        onBrowse = { steps -> command { controller.jump(steps) } },
        onMove = { steps -> command { controller.move(steps) } },
        onSearch = { query ->
            formalRows.clear()
            val drafts = DraftQueueSearch.filter(state.cards, query).take(4).map { hit ->
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
            if (!busy && !state.aiLocked) controller.state.form?.let { form ->
                val result = TemporalConversion.convert(form, kind.toCoreKind())
                controller.edit(result.form)
                conversionNote = result.note.ifBlank { null }
            }
        },
        onCommit = { command(changed = true) { controller.commit() } },
        onDiscard = { command(changed = true) { controller.discard() } },
        onRetrySave = { command { controller.flush() } },
        onUndoDiscard = { command(changed = true) { controller.undoDiscard() } },
        onConfirmConflict = { command(changed = true) { controller.confirmConflictReplacement() } },
        onKeepConflict = { controller.dismissConflict() },
        onConfirmRestore = { command(changed = true) { controller.requestRestore() } },
        onDeclineRestore = { controller.dismissRestore() },
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
    session: NewEventEditSession,
    form: DraftForm,
    error: String?,
    busy: Boolean,
    onFormChange: (DraftForm) -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscard: () -> Unit
) {
    val androidContext = LocalContext.current
    val pickerActions = createPickerActions(androidContext, form, session.defaults.nowMillis, onFormChange)
    val card = NativeDeckCard(
        cardKey = "new:${session.idempotencyKey}",
        sequenceLabel = "新建",
        operation = NativeDeckOperation.CREATE,
        operationLabel = "新建",
        status = NativeDeckStatus.PENDING,
        draftVersion = session.persistedCard?.draftVersion ?: 0,
        form = form.toNativeDeckForm(session.defaults.nowMillis)
    )
    val state = NativeDeckState(
        cards = listOf(card), currentKey = card.cardKey, currentForm = card.form, position = 0,
        canGoPrevious = false, canGoNext = false, canUndoDiscard = false,
        busy = busy, dirty = session.meaningfulDiff, aiLocked = false, aiSession = null,
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
        onConvert = { target -> onFormChange(TemporalConversion.convert(form, target.toCoreKind()).form) },
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
        alreadyPersisted = session.persistedCard != null
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

internal fun DraftForm.toNativeDeckForm(pickerNowMillis: Long): NativeDeckForm {
    val defaults = EditorDefaults.capture(Clock.fixed(Instant.ofEpochMilli(pickerNowMillis), zone), zone)
    val start = parseStart()
    val end = parseEnd()
    val dateOnly = DraftForm.parseDateOnly(endText)
    val nextHour = start?.plus(3_600_000L)
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
        missingFields = missingFields().map { it.label }, publishable = isPublishable(),
        validStart = when (kind) {
            TemporalKind.ALARM, TemporalKind.ACTIVITY -> start != null
            TemporalKind.MEMO -> startText.isBlank() || DraftForm.parseDateOnly(startText) != null
            TemporalKind.DEADLINE -> true
        },
        validEnd = when (kind) {
            TemporalKind.ACTIVITY -> end != null
            TemporalKind.DEADLINE -> parseDeadline() != null
            TemporalKind.MEMO -> endText.isBlank() || DraftForm.parseDateOnly(endText) != null
            TemporalKind.ALARM -> true
        },
        deadlineDateOnly = kind == TemporalKind.DEADLINE && dateOnly != null,
        activityEndBeforeStart = kind == TemporalKind.ACTIVITY && start != null && end != null && end <= start,
        recoveryScoped = recurrenceEditScope != null,
        occurrenceScoped = editOccurrenceKey != null,
        repeating = recurrence.isRepeating,
        startPickerMillis = start ?: defaults.pickerInstant(),
        endPickerMillis = end ?: dateOnly?.let(defaults::pickerInstant) ?: nextHour ?: defaults.pickerInstant(),
        deadlinePrecisionPickerMillis = end ?: dateOnly?.let(defaults::pickerInstant) ?: defaults.pickerInstant()
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

private fun DeckController.DeckState.toNativeDeckState(
    pickerNowMillis: Long,
    commandBusy: Boolean,
    conversionNote: String?,
    aiSession: PowerStore.AiSession?
): NativeDeckState {
    val drafts = cards.map { it.toNativeDeckCard(pickerNowMillis) }
    val currentForm = form?.toNativeDeckForm(pickerNowMillis)
    val conflict = conflict?.let { prompt ->
        NativeDeckConflict(
            sequenceLabel = TemporalRecord.formatSeq(prompt.eventSeq),
            delete = prompt.card.operation == DraftOperation.DELETE,
            currentRevision = prompt.currentRevision,
            rows = snapshotRows(prompt.current, prompt.approvedForm).map {
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
            NativeDeckAiSession(session.scope.size, cards.count { it.draftId in session.scope }, session.lockEpoch)
        },
        notice = notice,
        error = error,
        conversionNote = conversionNote,
        completion = completion?.let { NativeDeckCompletion(it.published, it.discarded, it.conflicts) },
        conflict = conflict,
        restore = restore?.let { NativeDeckRestore(TemporalRecord.formatSeq(it.eventSeq), it.message) }
    )
}

private fun DraftCard.toNativeDeckCard(pickerNowMillis: Long) = NativeDeckCard(
    cardKey = "draft:$draftId",
    sequenceLabel = TemporalRecord.formatSeq(eventSeq),
    operation = operation.toNativeOperation(),
    operationLabel = operation.label,
    status = status.toNativeStatus(),
    draftVersion = draftVersion,
    form = form.toNativeDeckForm(pickerNowMillis)
)

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
