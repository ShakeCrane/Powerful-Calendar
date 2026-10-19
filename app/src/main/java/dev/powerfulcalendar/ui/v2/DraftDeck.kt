package dev.powerfulcalendar.ui.v2

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftDiff
import dev.powerfulcalendar.core.DraftField
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 待处理: one centred glass popup that is both the pending inbox and every event editor.
 *
 * Gesture rules implemented here:
 *  - one swipe INSIDE the card moves exactly one queue card, and only after the text is durably
 *    staged; a failed save keeps the card and the text (handled by [DeckController.flush]);
 *  - a swipe OUTSIDE the card may fling several cards, still staging first;
 *  - vertical drag inside the expanded body scrolls; text selection wins because the gesture
 *    reader never consumes pointer changes itself.
 */
@Composable
fun DraftDeckDialog(
    controller: DeckController,
    aiSession: PowerStore.AiSession?,
    onDismissRequest: () -> Unit,
    onOpenFormalEvent: (TemporalRecord) -> Unit,
    onCancelAiBatch: () -> Unit,
    onDeckChanged: () -> Unit,
    formalSearch: (String) -> List<TemporalRecord>
) {
    var state by remember { mutableStateOf(controller.state) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var conversionNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val effects = LocalGlassEffects.current

    DisposableEffect(controller) {
        // Marshal controller publications (which may happen on an IO thread) onto the UI thread.
        controller.observe { next -> scope.launch { state = next } }
        onDispose { controller.observe { } }
    }

    // Debounced durable staging. Nothing here blocks per-keystroke typing.
    LaunchedEffect(state.currentId, state.form, state.dirty, state.aiLocked) {
        if (!state.dirty || state.aiLocked) return@LaunchedEffect
        delay(300)
        withContext(Dispatchers.IO) { controller.flush() }
    }

    // Keep local UI flags tied to card identity, never to list position.
    LaunchedEffect(state.currentId) {
        expanded = false
        typeMenu = false
        conversionNote = null
    }

    val card = state.current
    val form = state.form

    Dialog(
        onDismissRequest = {
            scope.launch {
                withContext(Dispatchers.IO) { controller.flush() }
                onDismissRequest()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Dimmed backdrop: taps close (after flush), horizontal drags browse several cards.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (effects.blur) 0.38f else 0.5f))
                    .outsideGesture(
                        onTap = {
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) { controller.flush() }
                                if (saved) onDismissRequest()
                            }
                        },
                        onBrowse = { steps ->
                            scope.launch { withContext(Dispatchers.IO) { controller.jump(steps) } }
                        }
                    )
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.98f)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    // Absorb taps inside the popup so they never reach the dismiss backdrop.
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DeckHeader(
                    total = state.cards.size,
                    aiLocked = state.aiLocked,
                    session = aiSession,
                    onClose = {
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { controller.flush() }
                            if (saved) onDismissRequest()
                        }
                    },
                    onCancelAi = onCancelAiBatch
                )

                if (aiSession?.isActive == true) {
                    AiProgressBanner(aiSession, state.cards.size, onCancelAiBatch)
                }

                DeckSearch(
                    query = query,
                    onChange = { query = it },
                    drafts = state.cards,
                    onPickDraft = { id ->
                        scope.launch { withContext(Dispatchers.IO) { controller.select(id) } }
                        query = ""
                    },
                    formalResults = remember(query) { if (query.isBlank()) emptyList() else formalSearch(query) },
                    onPickFormal = { record ->
                        query = ""
                        onOpenFormalEvent(record)
                    }
                )

                Spacer(Modifier.height(10.dp))

                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.completion != null -> CompletionCard(
                            completion = state.completion!!,
                            animated = !effects.reduceMotion,
                            onUndo = {
                                scope.launch {
                                    if (withContext(Dispatchers.IO) { controller.undoDiscard() }) {
                                        onDeckChanged()
                                    }
                                }
                            },
                            onDone = onDismissRequest
                        )

                        state.aiLocked && card == null -> AiHoldCard(
                            session = aiSession,
                            onCancel = onCancelAiBatch,
                            onClose = onDismissRequest
                        )

                        card == null || form == null -> EmptyDeckCard(onClose = onDismissRequest)

                        else -> {
                            FanStack(
                                count = state.cards.size,
                                position = state.position,
                                expanded = expanded,
                                onNext = {
                                    scope.launch { withContext(Dispatchers.IO) { controller.move(1) } }
                                },
                                onPrevious = {
                                    scope.launch { withContext(Dispatchers.IO) { controller.move(-1) } }
                                }
                            ) {
                                DeckCardBody(
                                    card = card,
                                    form = form,
                                    position = state.position,
                                    total = state.cards.size,
                                    expanded = expanded,
                                    aiLocked = state.aiLocked,
                                    error = state.error,
                                    conversionNote = conversionNote,
                                    dirty = state.dirty,
                                    onExpandToggle = { expanded = !expanded },
                                    onFormChange = { controller.edit(it) },
                                    onTypeChange = { target ->
                                        val result = TemporalConversion.convert(form, target)
                                        controller.edit(result.form)
                                        conversionNote = result.note.ifBlank { null }
                                        typeMenu = false
                                    },
                                    typeMenuExpanded = typeMenu,
                                    onTypeMenuChange = { typeMenu = it },
                                    onPrevious = {
                                        scope.launch { withContext(Dispatchers.IO) { controller.move(-1) } }
                                    },
                                    onNext = {
                                        scope.launch { withContext(Dispatchers.IO) { controller.move(1) } }
                                    },
                                    canGoPrevious = state.canGoPrevious,
                                    canGoNext = state.canGoNext,
                                    onCommit = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { controller.commit() }
                                            onDeckChanged()
                                        }
                                    },
                                    onDiscard = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { controller.discard() }
                                            onDeckChanged()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    "卡内左右滑动逐张 · 卡外快速滑动浏览 · 点外部保存并退出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    state.conflict?.let { prompt ->
        ConflictDialog(
            prompt = prompt,
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { controller.confirmConflictReplacement() }
                    onDeckChanged()
                }
            },
            onKeep = { controller.dismissConflict() }
        )
    }

    state.restore?.let { prompt ->
        AlertDialog(
            onDismissRequest = { controller.dismissRestore() },
            title = { Text("恢复已删除的事件 #${prompt.eventSeq}？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prompt.message)
                    Text(
                        "恢复后会生成新的正式版本，旧记录保留为历史。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { controller.requestRestore() }
                        onDeckChanged()
                    }
                }) { Text("用同一序号恢复") }
            },
            dismissButton = { TextButton(onClick = { controller.dismissRestore() }) { Text("保留草稿") } }
        )
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
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("待处理", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(8.dp))
        Text(
            if (total == 0) "已清空" else "$total 张",
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
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "保存并退出待处理")
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
                    "AI 正在处理 ${session.scope.size} 张授权草稿 · 剩余 $remaining",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("停止") }
            }
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
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
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text("查找草稿标题或事件序号") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focused = false })
    )
    if (query.isNotBlank()) {
        val draftHits = drafts.filter {
            it.form.title.contains(query, ignoreCase = true) ||
                it.eventSeq.toString().contains(query) ||
                TemporalRecord.formatSeq(it.eventSeq).contains(query)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = GlassShapes.chip,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
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
                            .clickable { onPickDraft(hit.draftId) }
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
                            .clickable { onPickFormal(hit) }
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Background fan: decoration only. It never owns identity or hit targets.
        for (layer in 2 downTo 1) {
            val rotation = if (layer % 2 == 0) 4.2f else -3.4f
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(if (expanded) 0.9f else 0.66f)
                    .scale(1f - layer * 0.045f)
                    .rotate(rotation)
                    .alpha(0.55f - layer * 0.12f),
                shape = GlassShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) { Box(Modifier.fillMaxSize()) }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (expanded) 0.96f else 0.74f)
                .cardSwipe(onNext = onNext, onPrevious = onPrevious),
            shape = GlassShapes.card,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            content()
        }
        if (count > 1) {
            Text(
                "${position + 1} / $count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp)
            )
        }
    }
}

/**
 * One swipe inside the card = exactly one queue card. The detector never consumes changes, so a
 * text field keeps the gesture for selection/caret; a two-finger or vertically dominant gesture is
 * left to the scroller.
 */
private fun Modifier.cardSwipe(onNext: () -> Unit, onPrevious: () -> Unit): Modifier =
    pointerInput(onNext, onPrevious) {
        val threshold = 84.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var total = 0f
            var lastX = down.position.x
            var blocked = false
            var maxPointers = 1
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (event.changes.any { it.isConsumed }) blocked = true
                if (pressed.isEmpty()) break
                maxPointers = maxOf(maxPointers, pressed.size)
                val x = pressed.map { it.position.x }.average().toFloat()
                total += x - lastX
                lastX = x
            }
            if (!blocked && maxPointers == 1 && abs(total) > threshold) {
                if (total < 0) onNext() else onPrevious()
            }
        }
    }

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
    onDiscard: () -> Unit
) {
    val context = LocalContext.current
    val missing = form.missingFields()
    val publishable = missing.isEmpty()
    var showDeleteConfirm by remember(card.draftId) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                TemporalRecord.formatSeq(card.eventSeq),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
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
            Spacer(Modifier.width(6.dp))
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
            Spacer(Modifier.weight(1f))
            if (dirty) {
                Text(
                    "暂存中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onExpandToggle) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "收起详细编辑" else "展开完整编辑"
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { onFormChange(form.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !aiLocked,
                label = { Text("标题") },
                isError = form.title.isBlank()
            )

            if (form.kind != TemporalKind.DEADLINE) {
                TimeRow(
                    label = "开始",
                    value = form.startText,
                    enabled = !aiLocked,
                    onChange = { onFormChange(form.copy(startText = it)) },
                    onPickDate = { base ->
                        pickDate(context, base) { date ->
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
                        pickTime(context, base) { hour, minute ->
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

            if (form.kind != TemporalKind.ALARM) {
                TimeRow(
                    label = if (form.kind == TemporalKind.DEADLINE) "截止" else "结束",
                    value = form.endText,
                    enabled = !aiLocked,
                    hint = if (form.kind == TemporalKind.DEADLINE) {
                        "日期 yyyy-MM-dd 或时刻 yyyy-MM-dd HH:mm"
                    } else {
                        "yyyy-MM-dd HH:mm"
                    },
                    onChange = { onFormChange(form.copy(endText = it)) },
                    onPickDate = { base ->
                        pickDate(context, base) { date ->
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
                        pickTime(context, base) { hour, minute ->
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
                                val date = DraftForm.parseDateOnly(form.endText) ?: LocalDate.now()
                                onFormChange(
                                    form.copy(
                                        endText = DraftForm.formatInstant(
                                            date.atTime(18, 0).atZone(form.zone)
                                                .toInstant().toEpochMilli(),
                                            form.zone
                                        )
                                    )
                                )
                            },
                            label = { Text("改为精确时刻") }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring()) + fadeIn(),
                exit = shrinkVertically(spring()) + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.location,
                        onValueChange = { onFormChange(form.copy(location = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !aiLocked,
                        label = { Text("地点（可选）") }
                    )
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = { onFormChange(form.copy(notes = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !aiLocked,
                        label = { Text("备注") }
                    )
                    if (form.kind != TemporalKind.DEADLINE) {
                        Text("提醒", style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-1, 0, 5, 10, 30, 60).forEach { minutes ->
                                FilterChip(
                                    selected = form.reminderMinutes == minutes,
                                    onClick = { onFormChange(form.copy(reminderMinutes = minutes)) },
                                    enabled = !aiLocked,
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
                    }
                    Text("重复", style = MaterialTheme.typography.labelLarge)
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
                            checked = form.enabled,
                            onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                            enabled = !aiLocked
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "来源 · " + TemporalText.sourceLabel(form.source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "draft v${card.draftVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (card.status == DraftStatus.CONFLICT) {
                        Text(
                            "此卡基于旧版本，发布前会要求你确认整份覆盖。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

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
            if (missing.isNotEmpty()) {
                Text(
                    "还需要：" + missing.joinToString("、") { it.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { showDeleteConfirm = true },
                enabled = !aiLocked,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Close, "丢弃这张草稿")
                Spacer(Modifier.width(4.dp))
                Text("丢弃")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = canGoPrevious && !aiLocked) {
                    Icon(Icons.Default.KeyboardArrowLeft, "上一张草稿")
                }
                Text(
                    "$position / ${total.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onNext, enabled = canGoNext && !aiLocked) {
                    Icon(Icons.Default.KeyboardArrowRight, "下一张草稿")
                }
            }
            Button(
                onClick = {
                    if (card.operation == DraftOperation.DELETE) showDeleteConfirm = true else onCommit()
                },
                enabled = !aiLocked && (publishable || card.operation == DraftOperation.DELETE),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(4.dp))
                Text(if (card.operation == DraftOperation.DELETE) "确认删除" else "发布")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
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
                        "删除后事件 #${card.eventSeq} 的序号将永久退役，不会复用。可以在完成页撤销。"
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
    onChange: (String) -> Unit,
    onPickDate: (Long) -> Unit,
    onPickTime: (Long) -> Unit
) {
    val parsed = DraftForm.parseInstant(value)
    Row(verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            label = { Text("$label · $hint") },
            isError = value.isNotBlank() && parsed == null && DraftForm.parseDateOnly(value) == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        IconButton(
            onClick = { onPickDate(parsed ?: System.currentTimeMillis()) },
            enabled = enabled,
            modifier = Modifier.size(46.dp)
        ) { Icon(Icons.Default.CalendarToday, "选择 $label 日期", modifier = Modifier.size(18.dp)) }
        IconButton(
            onClick = { onPickTime(parsed ?: System.currentTimeMillis()) },
            enabled = enabled,
            modifier = Modifier.size(46.dp)
        ) { Icon(Icons.Default.Schedule, "选择 $label 时间", modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun EmptyDeckCard(onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassShapes.card,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(
            Modifier.padding(28.dp),
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
        shape = GlassShapes.card,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(
            Modifier.padding(28.dp),
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
    onUndo: () -> Unit,
    onDone: () -> Unit
) {
    var entered by remember { mutableStateOf(!animated) }
    var showActions by remember { mutableStateOf(!animated) }
    LaunchedEffect(animated) {
        if (animated) {
            entered = true
            delay(420)
        }
        showActions = true
    }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 340f),
        label = "completionScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "completionAlpha"
    )
    Surface(
        modifier = Modifier.fillMaxWidth()
            .scale(if (animated) scale else 1f)
            .alpha(if (animated) alpha else 1f),
        shape = GlassShapes.card,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 10.dp,
        shadowElevation = 18.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
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
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text("待处理已清空", style = MaterialTheme.typography.headlineSmall)
            Text(
                "已提交 ${completion.published} 项 · 已丢弃 ${completion.discarded} 项" +
                    if (completion.conflicts > 0) " · 仍有 ${completion.conflicts} 张冲突卡" else "",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "每个数字都来自已落库的提交记录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(visible = showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (completion.discarded > 0) {
                        TextButton(onClick = onUndo) {
                            Icon(Icons.Default.Undo, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("撤销丢弃")
                        }
                    }
                    Button(onClick = onDone) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("返回时间轴")
                    }
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
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("这张卡基于旧版本 #${prompt.eventSeq}") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "确认后会用这张卡的全部字段替换当前正式对象，不自动合并、不保留中间字段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                HorizontalDivider()
                Text("完整差异", style = MaterialTheme.typography.labelLarge)
                prompt.changes.forEach { change ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(change.label, style = MaterialTheme.typography.labelMedium)
                        Text(
                            "现在：" + change.before,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "本卡：" + change.after,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认整份覆盖") }
        },
        dismissButton = { TextButton(onClick = onKeep) { Text("保留待处理") } }
    )
}

/** Tap outside closes (after flush); horizontal drag outside browses several cards at once. */
private fun Modifier.outsideGesture(onTap: () -> Unit, onBrowse: (Int) -> Unit): Modifier =
    pointerInput(onTap, onBrowse) {
        val pageWidth = 110.dp.toPx()
        val slop = 24.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var total = 0f
            var lastX = down.position.x
            var pointerCount = 1
            while (true) {
                val event: PointerEvent = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                pointerCount = maxOf(pointerCount, pressed.size)
                val x = pressed.map { it.position.x }.average().toFloat()
                total += x - lastX
                lastX = x
            }
            if (pointerCount == 1) {
                when {
                    abs(total) > 72.dp.toPx() -> {
                        // Inertial browse: one card per ~110dp of travel, at most three.
                        val steps = (abs(total) / pageWidth).toInt().coerceIn(1, 3)
                        onBrowse(if (total < 0) steps else -steps)
                    }
                    abs(total) < slop -> onTap()
                }
            }
        }
    }

private fun kindHint(kind: TemporalKind): String = when (kind) {
    TemporalKind.ALARM -> "只有开始时刻"
    TemporalKind.ACTIVITY -> "开始到结束"
    TemporalKind.DEADLINE -> "时刻或日期"
}

private fun pickDate(context: android.content.Context, baseMillis: Long, onPick: (LocalDate) -> Unit) {
    val zone = ZoneId.systemDefault()
    val base = Instant.ofEpochMilli(baseMillis).atZone(zone)
    DatePickerDialog(
        context,
        { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
        base.year, base.monthValue - 1, base.dayOfMonth
    ).show()
}

private fun pickTime(context: android.content.Context, baseMillis: Long, onPick: (Int, Int) -> Unit) {
    val zone = ZoneId.systemDefault()
    val base = Instant.ofEpochMilli(baseMillis).atZone(zone)
    TimePickerDialog(
        context,
        { _, hour, minute -> onPick(hour, minute) },
        base.hour, base.minute, true
    ).show()
}

private fun abs(value: Float): Float = kotlin.math.abs(value)
