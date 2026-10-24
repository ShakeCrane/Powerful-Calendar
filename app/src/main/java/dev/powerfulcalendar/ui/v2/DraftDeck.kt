package dev.powerfulcalendar.ui.v2

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.snap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftQueueSearch
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val LocalDeckInputs = staticCompositionLocalOf<MutableMap<Any, Rect>> { mutableMapOf() }

/** Explicit field hit regions keep paging away from selection, caret and picker gestures. */
@Composable
private fun Modifier.deckInput(): Modifier {
    val regions = LocalDeckInputs.current
    val token = remember { Any() }
    DisposableEffect(regions) { onDispose { regions.remove(token) } }
    return onGloballyPositioned { regions[token] = it.boundsInRoot() }
}

/**
 * 待处理: one centred glass popup that is both the pending inbox and every event editor.
 *
 * Gesture rules implemented here:
 *  - one swipe INSIDE the card moves exactly one queue card, and only after the text is durably
 *    staged; a failed save keeps the card and the text (handled by [DeckController.flush]);
 *  - a swipe OUTSIDE the card may fling several cards, still staging first;
 *  - vertical drag inside the expanded body scrolls; registered input hit regions keep text
 *    selection and caret gestures out of paging.
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
    var state by remember { mutableStateOf(controller.state) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(initialEditorOpen) }
    var typeMenu by remember { mutableStateOf(false) }
    var conversionNote by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val effects = LocalGlassEffects.current
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density) > 0
    val popupFocus = remember { FocusRequester() }
    var focusPlaced by remember { mutableStateOf(false) }

    // Claim the command on the main thread before launching IO: rapid gestures cannot queue
    // terminal actions for a card which has already been promoted by another command. The
    // controller serializes internally as well; this flag only keeps the UI honest.
    fun command(changed: Boolean = false, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                state = controller.state
                if (changed) onDeckChanged()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                state = controller.state.copy(error = error.message ?: "操作未完成，草稿已保留，请重试")
            } finally { busy = false }
        }
    }
    fun leave(afterSave: () -> Unit = onDismissRequest) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (withContext(Dispatchers.IO) { controller.flush() }) afterSave()
                state = controller.state
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                state = controller.state.copy(error = "暂存失败，请重试：${error.message.orEmpty()}")
            } finally { busy = false }
        }
    }

    DisposableEffect(controller) {
        controller.observe { next -> scope.launch { state = next } }
        state = controller.state
        onDispose { controller.observe { } }
    }
    // Persist only at exit/navigation/lifecycle checkpoints, not every keystroke.
    LaunchedEffect(state.currentId, initialEditorOpen) {
        // An explicit New/Edit entry opens editable fields immediately; an
        // ordinary visit to the pending inbox keeps the card preview mode.
        expanded = initialEditorOpen
        typeMenu = false
        conversionNote = null
    }
    val card = state.current
    val form = state.form

    SkinWindowDialog(
        onDismissRequest = { leave() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().testTag("deck-backdrop")
                .background(Color.Black.copy(alpha = 0.42f))
                .skinBackdrop(SkinSlot.DECK_BACKDROP)
                .outsideGesture(onTap = { leave() }, onBrowse = { steps -> command { controller.jump(steps) } }))
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val short = maxHeight < 500.dp || keyboard
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight().focusRequester(popupFocus).focusable()
                    .onGloballyPositioned {
                        // Dialog content has a separate owner: request only after its focus node
                        // is attached and placed, rather than racing the dialog composition.
                        if (!focusPlaced) { focusPlaced = true; popupFocus.requestFocus() }
                    }
                    // Do not install a full-screen no-op tap detector here. It steals hits
                    // from the actual backdrop and makes outside-tap-to-save impossible.
                    , horizontalAlignment = Alignment.CenterHorizontally) {
                    DeckHeader(state.cards.size, state.aiLocked, aiSession, { leave() }, onCancelAiBatch)
                    if (state.aiLocked && !short && aiSession != null) AiProgressBanner(aiSession, state.cards.count { it.draftId in aiSession.scope }, onCancelAiBatch)
                    if (!short && state.completion == null) {
                        DeckSearch(query, { query = it }, state.cards, { id ->
                            command { controller.select(id) }; query = ""
                        }, if (query.isBlank()) emptyList() else formalSearch(query), { record ->
                            leave { query = ""; onOpenFormalEvent(record) }
                        })
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = if (short) 0.dp else 12.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.completion != null -> CompletionCard(state.completion!!, !effects.reduceMotion, state.canUndoDiscard,
                                { command(true) { controller.undoDiscard() } }, { leave() })
                            card == null && state.aiLocked -> AiHoldCard(aiSession, onCancelAiBatch, { leave() })
                            card == null || form == null -> EmptyDeckCard { leave() }
                            else -> FanStack(state.cards.size, state.position, expanded || short,
                                { command { controller.move(1) } }, { command { controller.move(-1) } }) {
                                key(card.draftId) {
                                    DeckCardBody(card, form, state.position, state.cards.size, expanded,
                                        state.aiLocked || busy, state.error, conversionNote, state.dirty,
                                        { expanded = !expanded },
                                        { if (!busy && !state.aiLocked) controller.edit(it) },
                                        { target ->
                                            if (!busy && !state.aiLocked) {
                                                val result = TemporalConversion.convert(form, target)
                                                controller.edit(result.form)
                                                conversionNote = result.note.ifBlank { null }
                                                typeMenu = false
                                            }
                                        }, typeMenu, { typeMenu = it },
                                        { command { controller.move(-1) } }, { command { controller.move(1) } },
                                        state.canGoPrevious, state.canGoNext,
                                        { command(true) { controller.commit() } }, { command(true) { controller.discard() } }, { command { controller.flush() } })
                                }
                            }
                        }
                    }
                    if (state.canUndoDiscard && state.completion == null && !short) {
                        TextButton(onClick = { command(true) { controller.undoDiscard() } }, enabled = !state.aiLocked && !busy) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("撤销上一次丢弃")
                        }
                    }
                    if (!short) Surface(shape = GlassShapes.chip, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                        Text("卡内逐张 · 卡外快速浏览 · 点外部保存退出", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
    state.conflict?.let { prompt ->
        ConflictDialog(prompt, { command(true) { controller.confirmConflictReplacement() } }, { controller.dismissConflict() })
    }
    state.restore?.let { prompt ->
        SkinAlertDialog(onDismissRequest = { controller.dismissRestore() },
            title = { Text("恢复 ${TemporalRecord.formatSeq(prompt.eventSeq)}？") },
            text = { Text(prompt.message) },
            confirmButton = { TextButton(onClick = { command(true) { controller.requestRestore() } }, enabled = !busy) { Text("恢复同一事件") } },
            dismissButton = { TextButton(onClick = { controller.dismissRestore() }) { Text("保留草稿") } })
    }
}

@Composable
private fun DeckHeader(
    total: Int,
    aiLocked: Boolean,
    session: PowerStore.AiSession?,
    onClose: () -> Unit,
    onCancelAi: () -> Unit
) {
    Surface(shape = skinBarShape(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("待处理", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.width(8.dp))
        Text(
            if (aiLocked) "只读" else if (total == 0) "0 张" else "$total 张",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (aiLocked && session != null) {
            AssistChip(
                onClick = onCancelAi,
                label = { Text("AI 进行中 · 停止") }
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, "保存并退出待处理")
        }
    }
    }
}

@Composable
private fun AiProgressBanner(
    session: PowerStore.AiSession,
    remaining: Int,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = GlassShapes.chip,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "本次授权 ${session.scope.size} 张 · 仍待处理 $remaining 张",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("停止") }
            }
            if (!LocalGlassEffects.current.reduceMotion) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            Text(
                "期间卡组为只读；已提交的结果会保留，未处理的卡片继续等待你。",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DeckSearch(
    query: String,
    onChange: (String) -> Unit,
    drafts: List<DraftCard>,
    onPickDraft: (Long) -> Unit,
    formalResults: List<TemporalRecord>,
    onPickFormal: (TemporalRecord) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text("查找草稿标题或事件序号") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focused = false })
    )
    if (query.isNotBlank()) {
        val draftHits = DraftQueueSearch.filter(drafts, query)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = GlassShapes.chip,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        ) {
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                if (draftHits.isEmpty() && formalResults.isEmpty()) {
                    Text(
                        "没有匹配的草稿或正式事件",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                draftHits.take(4).forEach { hit ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onPickDraft(hit.draftId) }.heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            TemporalRecord.formatSeq(hit.eventSeq),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            hit.form.title.ifBlank { "未命名草稿" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text("草稿", style = MaterialTheme.typography.labelSmall)
                    }
                }
                formalResults.take(4).forEach { hit ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onPickFormal(hit) }.heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            TemporalRecord.formatSeq(hit.eventSeq),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            hit.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text("编辑", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FanStack(
    count: Int,
    position: Int,
    expanded: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    content: @Composable () -> Unit
) {
    val regions = remember { mutableMapOf<Any, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val displacement by animateFloatAsState(drag, if (dragging) snap() else glassMotion(), label = "deckSwipe")
    val heightFraction by animateFloatAsState(if (expanded) 1f else 0.90f, glassMotion(), label = "deckExpand")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Background fan: decoration only. It never owns identity or hit targets.
        for (layer in (if (LocalGlassEffects.current.blur) minOf(2, count - 1) else 0) downTo 1) {
            val rotation = if (layer % 2 == 0) 4.2f else -3.4f
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = if (expanded) Dp.Infinity else 460.dp)
                    .fillMaxHeight(if (expanded) 0.94f else 0.90f)
                    .offset(y = (layer * 8).dp)
                    .scale(1f - layer * 0.045f)
                    .rotate(rotation)
                    .alpha(0.55f - layer * 0.12f),
                shape = skinCardShape(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) { Box(Modifier.fillMaxSize()) }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (expanded) Dp.Infinity else 460.dp)
                .fillMaxHeight(heightFraction)
                .testTag("draft-card")
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer { translationX = displacement }
                .cardSwipe(regions, { origin }, { x, active -> drag = x.coerceIn(-24f, 24f); dragging = active }, onNext, onPrevious),
            shape = skinCardShape(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 0.dp,
            shadowElevation = if (LocalGlassEffects.current.blur) 3.dp else 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            CompositionLocalProvider(LocalDeckInputs provides regions) { content() }
        }

    }
}

/**
 * Claim only horizontal travel outside registered input fields. Text selection and vertical
 * scrolling retain ownership; one claimed physical swipe can finish at most one page.
 */
@Composable
private fun Modifier.cardSwipe(
    regions: Map<Any, Rect>, origin: () -> Offset, onDrag: (Float, Boolean) -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit
): Modifier {
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val drag by rememberUpdatedState(onDrag)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (regions.values.any { it.contains(down.position + origin()) }) return@awaitEachGesture
            var last = down.position
            var travel = Offset.Zero
            var blocked = false
            var claimed = false
            var pointers = 1
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    pointers = maxOf(pointers, event.changes.count { it.pressed })
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    travel += change.position - last
                    last = change.position
                    if (pointers > 1 || (!claimed && change.isConsumed && change.pressed)) blocked = true
                    if (!claimed && abs(travel.y) > viewConfiguration.touchSlop && abs(travel.y) > abs(travel.x)) blocked = true
                    if (!blocked && !claimed && abs(travel.x) > viewConfiguration.touchSlop * 1.4f && abs(travel.x) > abs(travel.y) * 1.4f) claimed = true
                    if (claimed && !blocked) { change.consume(); drag(travel.x * 0.14f, true) }
                    if (!change.pressed) break
                }
                if (claimed && !blocked) when (horizontalPageSteps(travel.x, travel.y, 72.dp.toPx(), size.width.toFloat(), false)) {
                    1 -> next()
                    -1 -> previous()
                }
            } finally { drag(0f, false) }
        }
    }
}

/**
 * One authoritative Compose field UI for both new-draft cards and direct editing.
 * Date/time pickers, reminder controls, recurrence and tracking fields share
 * exactly the same implementation; host persistence operations remain separate.
 */
@Composable
internal fun UnifiedEventFormFields(
    form: DraftForm,
    expanded: Boolean,
    aiLocked: Boolean,
    card: DraftCard? = null,
    instanceKey: Any? = card?.draftId,
    onFormChange: (DraftForm) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { onFormChange(form.copy(title = it)) },
                modifier = Modifier.fillMaxWidth().deckInput(),
                singleLine = true,
                readOnly = aiLocked,
                label = { Text("标题") },
                isError = form.title.isBlank()
            )

            if (form.kind == TemporalKind.MEMO) {
                Text("备忘可完全不指定日期；也可以填连续的自然日区间。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = form.startText,
                    onValueChange = { onFormChange(form.copy(startText = it)) },
                    modifier = Modifier.fillMaxWidth().deckInput(), singleLine = true,
                    label = { Text("开始日期（可选，yyyy-MM-dd）") }, readOnly = aiLocked)
                OutlinedTextField(value = form.endText,
                    onValueChange = { onFormChange(form.copy(endText = it)) },
                    modifier = Modifier.fillMaxWidth().deckInput(), singleLine = true,
                    label = { Text("结束日期（可选，含当天）") }, readOnly = aiLocked)
            }
            if (form.kind != TemporalKind.DEADLINE && form.kind != TemporalKind.MEMO) {
                TimeRow(
                    label = "开始",
                    zone = form.zone,
                    value = form.startText,
                    enabled = !aiLocked,
                    onChange = { onFormChange(form.copy(startText = it)) },
                    onPickDate = { base ->
                        pickDate(context, base, form.zone) { date ->
                            val time = form.parseStart()
                                ?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalTime() }
                                ?: LocalTime.of(9, 0)
                            onFormChange(
                                form.copy(
                                    startText = DraftForm.formatInstant(
                                        date.atTime(time).atZone(form.zone).toInstant().toEpochMilli(),
                                        form.zone
                                    )
                                )
                            )
                        }
                    },
                    onPickTime = { base ->
                        pickTime(context, base, form.zone) { hour, minute ->
                            val date = form.parseStart()
                                ?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalDate() }
                                ?: LocalDate.now()
                            onFormChange(
                                form.copy(
                                    startText = DraftForm.formatInstant(
                                        date.atTime(hour, minute).atZone(form.zone)
                                            .toInstant().toEpochMilli(),
                                        form.zone
                                    )
                                )
                            )
                        }
                    }
                )
            }

            if (form.kind != TemporalKind.ALARM && form.kind != TemporalKind.MEMO) {
                TimeRow(
                    label = if (form.kind == TemporalKind.DEADLINE) "截止" else "结束",
                    zone = form.zone,
                    value = form.endText,
                    enabled = !aiLocked,
                    hint = if (form.kind == TemporalKind.DEADLINE) {
                        "日期 yyyy-MM-dd 或时刻 yyyy-MM-dd HH:mm"
                    } else {
                        "yyyy-MM-dd HH:mm"
                    },
                    onChange = { onFormChange(form.copy(endText = it)) },
                    onPickDate = { base ->
                        pickDate(context, base, form.zone) { date ->
                            if (form.kind == TemporalKind.DEADLINE &&
                                form.endText.trim().length == 10
                            ) {
                                onFormChange(form.copy(endText = date.toString()))
                            } else {
                                val time = form.parseEnd()
                                    ?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalTime() }
                                    ?: LocalTime.of(10, 0)
                                onFormChange(
                                    form.copy(
                                        endText = DraftForm.formatInstant(
                                            date.atTime(time).atZone(form.zone)
                                                .toInstant().toEpochMilli(),
                                            form.zone
                                        )
                                    )
                                )
                            }
                        }
                    },
                    onPickTime = { base ->
                        pickTime(context, base, form.zone) { hour, minute ->
                            val date = form.parseEnd()
                                ?.let { Instant.ofEpochMilli(it).atZone(form.zone).toLocalDate() }
                                ?: LocalDate.now()
                            onFormChange(
                                form.copy(
                                    endText = DraftForm.formatInstant(
                                        date.atTime(hour, minute).atZone(form.zone)
                                            .toInstant().toEpochMilli(),
                                        form.zone
                                    )
                                )
                            )
                        }
                    }
                )
            }

            if (form.kind == TemporalKind.DEADLINE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "只填日期时保持日期精度，不会自动变成 23:59。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (DraftForm.parseDateOnly(form.endText) != null) {
                        AssistChip(
                            onClick = {
                                val date = DraftForm.parseDateOnly(form.endText) ?: LocalDate.now(form.zone)
                                pickTime(context, date.atStartOfDay(form.zone).toInstant().toEpochMilli(), form.zone) { hour, minute ->
                                    onFormChange(form.copy(endText = DraftForm.formatInstant(date.atTime(hour, minute).atZone(form.zone).toInstant().toEpochMilli(), form.zone)))
                                }
                            },
                            enabled = !aiLocked,
                            label = { Text("改为精确时刻") }
                        )
                    }
                }
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.location,
                        onValueChange = { onFormChange(form.copy(location = it)) },
                        modifier = Modifier.fillMaxWidth().deckInput(),
                        singleLine = true,
                        readOnly = aiLocked,
                        label = { Text("地点（可选）") }
                    )
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = { onFormChange(form.copy(notes = it)) },
                        modifier = Modifier.fillMaxWidth().deckInput(),
                        minLines = 2,
                        readOnly = aiLocked,
                        label = { Text("备注") }
                    )
                    run {
                        Text("提醒方式与时间（与事件类型分别设置）", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dev.powerfulcalendar.core.ReminderMethod.entries.forEach { mode ->
                                FilterChip(
                                    selected = form.reminderMethod == mode,
                                    onClick = { onFormChange(form.copy(reminderMethod = mode)) },
                                    enabled = !aiLocked,
                                    label = { Text(if (mode == dev.powerfulcalendar.core.ReminderMethod.MESSAGE) "消息" else "闹钟（精确）") }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dev.powerfulcalendar.core.ReminderTiming.entries.forEach { timing ->
                                FilterChip(
                                    selected = form.reminderTiming == timing,
                                    onClick = { onFormChange(form.copy(reminderTiming = timing, reminderMinutes = if (form.reminderMinutes < 0) 0 else form.reminderMinutes)) },
                                    enabled = !aiLocked,
                                    label = { Text(if (timing == dev.powerfulcalendar.core.ReminderTiming.DAY_START) "当天 00:00" else "相对开始/截止") }
                                )
                            }
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-1, 0, 5, 10, 30, 60).forEach { minutes ->
                                FilterChip(
                                    selected = form.reminderMinutes == minutes,
                                    onClick = { onFormChange(form.copy(reminderMinutes = minutes)) },
                                    enabled = !aiLocked &&
                                        (form.reminderTiming == dev.powerfulcalendar.core.ReminderTiming.OFFSET || minutes == -1),
                                    label = {
                                        Text(
                                            when (minutes) {
                                                -1 -> "关闭"
                                                0 -> "准时"
                                                60 -> "1 小时"
                                                else -> "$minutes 分钟"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                        if (form.kind == TemporalKind.DEADLINE && DraftForm.parseDateOnly(form.endText) != null) {
                            Text("只有日期的截止仍保持日期精度；默认提醒在该日期 00:00，并非把截止时刻改成零点。",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        var customOffset by remember(instanceKey) { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customOffset,
                                onValueChange = { input ->
                                    if (input.length <= 6 && input.all(Char::isDigit)) customOffset = input
                                },
                                label = { Text("自定义分钟") },
                                singleLine = true,
                                modifier = Modifier.width(150.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                enabled = !aiLocked &&
                                    form.reminderTiming == dev.powerfulcalendar.core.ReminderTiming.OFFSET
                            )
                            val offset = customOffset.toIntOrNull()
                            TextButton(onClick = {
                                if (offset != null) onFormChange(form.copy(reminderMinutes = offset))
                            }, enabled = !aiLocked &&
                                form.reminderTiming == dev.powerfulcalendar.core.ReminderTiming.OFFSET &&
                                offset != null && offset in 0..10080) {
                                Text("应用")
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("加入待办（完成跟踪）", style = MaterialTheme.typography.labelLarge)
                        Switch(checked = form.trackingEnabled || form.kind == TemporalKind.MEMO,
                            onCheckedChange = { onFormChange(form.copy(trackingEnabled = it)) },
                            enabled = !aiLocked && form.kind != TemporalKind.MEMO)
                    }
                    if (form.kind == TemporalKind.MEMO && form.startText.isBlank()) {
                        Text("无日期备忘不设置基于时刻的提醒或重复周期。",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("重复", style = MaterialTheme.typography.labelLarge)
                    if (form.recurrence.isRepeating) {
                        OutlinedTextField(
                            value = form.recurrence.activeFromDate.orEmpty(),
                            onValueChange = { onFormChange(form.copy(recurrence =
                                form.recurrence.copy(activeFromDate = it.ifBlank { null }))) },
                            label = { Text("从该日期起有效（可留空 yyyy-MM-dd）") },
                            singleLine = true, readOnly = aiLocked,
                            modifier = Modifier.fillMaxWidth().deckInput())
                        OutlinedTextField(
                            value = form.recurrence.activeUntilDate.orEmpty(),
                            onValueChange = { onFormChange(form.copy(recurrence =
                                form.recurrence.copy(activeUntilDate = it.ifBlank { null }))) },
                            label = { Text("截至该日期有效（含当天，可留空）") },
                            singleLine = true, readOnly = aiLocked,
                            modifier = Modifier.fillMaxWidth().deckInput())
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceFreq.entries.forEach { freq ->
                            FilterChip(
                                selected = form.recurrence.freq == freq,
                                onClick = {
                                    onFormChange(
                                        form.copy(
                                            recurrence = if (freq == RecurrenceFreq.NONE) {
                                                Recurrence.NONE
                                            } else {
                                                form.recurrence.copy(freq = freq)
                                            }
                                        )
                                    )
                                },
                                enabled = !aiLocked,
                                label = {
                                    Text(
                                        when (freq) {
                                            RecurrenceFreq.NONE -> "不重复"
                                            RecurrenceFreq.DAILY -> "每天"
                                            RecurrenceFreq.WEEKLY -> "每周"
                                            RecurrenceFreq.MONTHLY -> "每月"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启用", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (form.enabled) "会参与提醒调度" else "保留内容，但不调度提醒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            modifier = Modifier.semantics { contentDescription = "启用事件提醒调度" },
                            checked = form.enabled,
                            onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                            enabled = !aiLocked
                        )
                    }
                    Text("时区 · ${form.zoneId}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "来源 · " + TemporalText.sourceLabel(form.source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (card != null) Text(
                            "草稿版本 ${card.draftVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (card?.status == DraftStatus.CONFLICT) {
                        Text(
                            "此卡基于旧版本，发布前会要求你确认整份覆盖。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }


    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckCardBody(
    card: DraftCard,
    form: DraftForm,
    position: Int,
    total: Int,
    expanded: Boolean,
    aiLocked: Boolean,
    error: String?,
    conversionNote: String?,
    dirty: Boolean,
    onExpandToggle: () -> Unit,
    onFormChange: (DraftForm) -> Unit,
    onTypeChange: (TemporalKind) -> Unit,
    typeMenuExpanded: Boolean,
    onTypeMenuChange: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onCommit: () -> Unit,
    onDiscard: () -> Unit,
    onRetrySave: () -> Unit
) {
    val context = LocalContext.current
    val missing = form.missingFields()
    val publishable = form.isPublishable()
    var showDeleteConfirm by remember(card.draftId) { mutableStateOf(false) }
    var arrived by remember(card.draftId) { mutableStateOf(false) }
    LaunchedEffect(card.draftId) { arrived = true }
    val entrance by animateFloatAsState(if (arrived) 1f else 0.86f, glassMotion(), label = "deckArrival")

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = entrance }) {
    val tiny = maxHeight < 320.dp
    Column(Modifier.fillMaxSize().then(if (tiny) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                TemporalRecord.formatSeq(card.eventSeq),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Box {
                AssistChip(
                    onClick = { if (!aiLocked) onTypeMenuChange(true) },
                    label = { Text(form.kind.label + " ▾") }
                )
                DropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { onTypeMenuChange(false) }
                ) {
                    TemporalKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.label + "  ·  " + kindHint(kind)) },
                            onClick = { onTypeChange(kind) }
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    card.operation.label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            }
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "收起详细编辑" else "展开完整编辑"
                )
            }
        }

        Column(
            Modifier
                .then(if (tiny) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState())),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!expanded) {
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = "展开完整编辑", onClick = onExpandToggle).padding(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(form.title.ifBlank { "给这段时间起个名字" }, style = MaterialTheme.typography.headlineSmall)
                    Text(if (form.kind == TemporalKind.ALARM) form.startText else if (form.kind == TemporalKind.ACTIVITY) "${form.startText}\n${form.endText}" else "${form.endText} · 截止",
                        style = MaterialTheme.typography.bodyLarge, color = kindColor(form.kind))
                    if (form.location.isNotBlank()) Text(form.location, style = MaterialTheme.typography.bodyMedium)
                    Text(if (aiLocked) "卡组只读 · 可浏览详情" else "轻点卡片，展开完整编辑", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
            UnifiedEventFormFields(form = form, expanded = expanded, aiLocked = aiLocked,
                card = card, onFormChange = onFormChange)
            } // expanded editor
            if (!expanded && form.notes.isNotBlank()) {
                Text(
                    form.notes,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            conversionNote?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (form.recurrence.isRepeating) Text("当前编辑整条重复规则；本次 / 此后范围编辑尚未支持。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            if (form.kind == TemporalKind.ACTIVITY && missing.isEmpty() && !publishable) Text("结束时刻需要晚于开始时刻", color = MaterialTheme.colorScheme.error)
            if (missing.isNotEmpty() && card.operation != DraftOperation.DELETE) {
                Text(
                    "还需要：" + missing.joinToString("、") { it.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onRetrySave, enabled = !aiLocked) { Text("重试暂存") }
            }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = canGoPrevious, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一张草稿") }
            Text("${position + 1} / ${total.coerceAtLeast(1)} · " + if (dirty) "待暂存" else "已暂存",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一张草稿") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { onDiscard() }, enabled = !aiLocked, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                Text(if (card.operation == DraftOperation.DELETE) "不删除" else "丢弃")
            }
            Button(onClick = { if (card.operation == DraftOperation.DELETE) showDeleteConfirm = true else onCommit() },
                enabled = !aiLocked && (publishable || card.operation == DraftOperation.DELETE), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                Text(if (card.operation == DraftOperation.DELETE) "确认删除" else "发布")
            }
        }

    }

    } // short-viewport constraints
    if (showDeleteConfirm) {
        SkinAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    if (card.operation == DraftOperation.DELETE) "确认删除事件？"
                    else "丢弃这张草稿？"
                )
            },
            text = {
                Text(
                    if (card.operation == DraftOperation.DELETE) {
                        "删除会将事件 #${card.eventSeq} 的序号永久退役。当前版本不支持撤销正式删除，请确认。"
                    } else {
                        "草稿会被丢弃但可以撤销；事件 #${card.eventSeq} 的正式内容不受影响。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (card.operation == DraftOperation.DELETE) onCommit() else onDiscard()
                }) { Text(if (card.operation == DraftOperation.DELETE) "删除" else "丢弃") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun TimeRow(
    label: String,
    value: String,
    enabled: Boolean,
    hint: String = "yyyy-MM-dd HH:mm",
    zone: ZoneId = ZoneId.systemDefault(),
    onChange: (String) -> Unit,
    onPickDate: (Long) -> Unit,
    onPickTime: (Long) -> Unit
) {
    val parsed = DraftForm.parseInstantIn(value, zone)
    Row(Modifier.deckInput(), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            readOnly = !enabled,
            label = { Text(label) },
            placeholder = { Text(hint) },
            isError = value.isNotBlank() && parsed == null && !(label == "截止" && DraftForm.parseDateOnly(value) != null),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        IconButton(
            onClick = { onPickDate(parsed ?: System.currentTimeMillis()) },
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) { Icon(Icons.Default.CalendarToday, "选择 $label 日期", modifier = Modifier.size(18.dp)) }
        IconButton(
            onClick = { onPickTime(parsed ?: System.currentTimeMillis()) },
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) { Icon(Icons.Default.Schedule, "选择 $label 时间", modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun EmptyDeckCard(onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = skinCardShape(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("没有待处理卡片", style = MaterialTheme.typography.titleMedium)
            Text(
                "所有草稿都已处理。新的自然语言、截图或 AI 建议会重新出现在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onClose) { Text("返回时间轴") }
        }
    }
}

@Composable
private fun AiHoldCard(session: PowerStore.AiSession?, onCancel: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = skinCardShape(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(28.dp))
            Text("AI 正在独占处理卡组", style = MaterialTheme.typography.titleMedium)
            Text(
                "你可以查看时间轴，但不能同时编辑卡片。停止后会保留已完成的结果与剩余卡片。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session != null) {
                Text(
                    "授权 ${session.scope.size} 张 · 批次 ${session.lockEpoch}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("停止批次") }
                Button(onClick = onClose) { Text("返回时间轴") }
            }
        }
    }
}

@Composable
private fun CompletionCard(
    completion: DeckController.Completion,
    animated: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDone: () -> Unit
) {
    var entered by remember { mutableStateOf(!animated) }
    LaunchedEffect(animated) {
        if (animated) {
            entered = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = glassMotion(),
        label = "completionScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = glassMotion(),
        label = "completionAlpha"
    )
    Surface(
        modifier = Modifier.fillMaxWidth()
            .scale(if (animated) scale else 1f)
            .alpha(if (animated) alpha else 1f),
        shape = skinCardShape(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 10.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (skinPainter(SkinSlot.COMPLETION_EMBLEM) != null) {
                    SkinImage(SkinSlot.COMPLETION_EMBLEM, Modifier.size(46.dp))
                } else {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text("待处理已清空", style = MaterialTheme.typography.headlineSmall)
            Text(
                "已提交 ${completion.published} 项 · 已丢弃 ${completion.discarded} 项" +
                    if (completion.conflicts > 0) " · 本次记录 ${completion.conflicts} 项冲突" else "",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "为接下来的时间，留一点从容。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canUndo) {
                        TextButton(onClick = onUndo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("撤销丢弃")
                        }
                    }
                    Button(onClick = onDone) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("退出待处理")
                    }
            }
        }
    }
}

@Composable
private fun ConflictDialog(
    prompt: DeckController.ConflictPrompt,
    onConfirm: () -> Unit,
    onKeep: () -> Unit
) {
    SkinAlertDialog(
        onDismissRequest = onKeep,
        title = { Text("正式内容已变化 ${TemporalRecord.formatSeq(prompt.eventSeq)}") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (prompt.card.operation == DraftOperation.DELETE) "这是一张删除提案。确认会删除当前正式版本，序号退役；下方展示当前内容与提案原快照。"
                    else "确认后用本卡的完整快照替换当前正式对象。请检查全部差异，未展示过的新版本需要重新确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                HorizontalDivider()
                Text("完整差异", style = MaterialTheme.typography.labelLarge)
                Text("当前 v${prompt.currentRevision ?: 0} → 本卡完整快照", style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        snapshotRows(prompt.current, prompt.approvedForm).forEach { row ->
                            Surface(shape = GlassShapes.chip,
                                color = if (row.changed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(row.label + if (row.changed) " · 将更改" else " · 保持", style = MaterialTheme.typography.labelLarge)
                                    Text("现在：${row.before}", style = MaterialTheme.typography.bodyMedium)
                                    Text("本卡：${row.after}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (prompt.card.operation == DraftOperation.DELETE) "确认删除当前版本" else "确认整份覆盖") }
        },
        dismissButton = { TextButton(onClick = onKeep) { Text("保留待处理") } }
    )
}

/** Tap outside closes (after flush); horizontal drag outside browses several cards at once. */
@Composable
private fun Modifier.outsideGesture(onTap: () -> Unit, onBrowse: (Int) -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    val browse by rememberUpdatedState(onBrowse)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var travel = Offset.Zero
            var last = down.position
            var maxTravel = 0f
            var pointers = 1
            while (true) {
                val event = awaitPointerEvent()
                pointers = maxOf(pointers, event.changes.count { it.pressed })
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                travel += change.position - last
                last = change.position
                maxTravel = maxOf(maxTravel, travel.getDistance())
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) break
            }
            if (pointers == 1) {
                val steps = horizontalPageSteps(travel.x, travel.y, 60.dp.toPx(), 110.dp.toPx(), true, tracker.calculateVelocity().x)
                if (steps != 0) browse(steps)
                else if (maxTravel < viewConfiguration.touchSlop) tap()
            }
        }
    }
}

private fun kindHint(kind: TemporalKind): String = when (kind) {
    TemporalKind.ALARM -> "只有开始时刻"
    TemporalKind.ACTIVITY -> "开始到结束"
    TemporalKind.DEADLINE -> "时刻或日期"
    TemporalKind.MEMO -> "按天待办或无日期"
}

private fun pickDate(
    context: android.content.Context,
    baseMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    onPick: (LocalDate) -> Unit
) = SkinPickers.pickDate(context, baseMillis, zone, onPick)

private fun pickTime(
    context: android.content.Context,
    baseMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    onPick: (Int, Int) -> Unit
) = SkinPickers.pickTime(context, baseMillis, zone, onPick)

private fun abs(value: Float): Float = kotlin.math.abs(value)
