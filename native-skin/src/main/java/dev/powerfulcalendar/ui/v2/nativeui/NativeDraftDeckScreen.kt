package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeDeckActions
import dev.powerfulcalendar.ui.v2.NativeDeckAppearance
import dev.powerfulcalendar.ui.v2.NativeDeckCard
import dev.powerfulcalendar.ui.v2.NativeDeckCompletion
import dev.powerfulcalendar.ui.v2.NativeDeckConflict
import dev.powerfulcalendar.ui.v2.NativeDeckForm
import dev.powerfulcalendar.ui.v2.NativeDeckFormContext
import dev.powerfulcalendar.ui.v2.NativeDeckKind
import dev.powerfulcalendar.ui.v2.NativeDeckOperation
import dev.powerfulcalendar.ui.v2.NativeDeckRecurrenceFreq
import dev.powerfulcalendar.ui.v2.NativeDeckReminderMethod
import dev.powerfulcalendar.ui.v2.NativeDeckReminderTiming
import dev.powerfulcalendar.ui.v2.NativeDeckSearchHit
import dev.powerfulcalendar.ui.v2.NativeDeckState
import dev.powerfulcalendar.ui.v2.NativeDraftDeckContext

private val LocalDeckInputs = staticCompositionLocalOf<MutableMap<Any, Rect>> { mutableMapOf() }
private val LocalNativeDeckContext = staticCompositionLocalOf<NativeDraftDeckContext?> { null }

@Composable
fun NativeDraftDeckScreen(context: NativeDraftDeckContext) {
    CompositionLocalProvider(LocalNativeDeckContext provides context) {
        NativeDraftDeckContent()
    }
}

@Composable
private fun NativeDraftDeckContent() {
    val context = LocalNativeDeckContext.current ?: return
    val state = context.state
    val actions = context.actions
    val appearance = context.appearance
    val aiSession = state.aiSession
    val completion = state.completion
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density) > 0
    val popupFocus = remember { FocusRequester() }
    var focusPlaced by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(context.initialEditorOpen) }
    var typeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentKey, context.initialEditorOpen) {
        expanded = context.initialEditorOpen
        typeMenu = false
    }

    appearance.window(actions.onDismiss) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().testTag(if (context.transientNewEvent) "new-event-backdrop" else "deck-backdrop")
                .background(Color.Black.copy(alpha = 0.42f))
                .then(Modifier.outsideGesture(actions.onDismiss, actions.onBrowse))) {
                appearance.backdrop()
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val short = maxHeight < 500.dp || keyboard
                Column(
                    Modifier.widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight()
                        .focusRequester(popupFocus).focusable()
                        .onGloballyPositioned {
                            if (!focusPlaced) {
                                focusPlaced = true
                                popupFocus.requestFocus()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (context.transientNewEvent) {
                        NewEventHeader(busy = state.busy, onExit = actions.onDismiss)
                    } else {
                        DeckHeader(state.cards.size, state.aiLocked, aiSession,
                            actions.onDismiss, actions.onCancelAiBatch)
                        state.notice?.let {
                            Text(it, Modifier.fillMaxWidth().padding(top = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (state.aiLocked && !short && aiSession != null) {
                            AiProgressBanner(aiSession, actions.onCancelAiBatch)
                        }
                        if (!short && state.completion == null) {
                            val results = remember(query, state.cards) {
                                if (query.isBlank()) emptyList() else actions.onSearch(query)
                            }
                            DeckSearch(query, { query = it }, results) { hit ->
                                if (hit.formal) {
                                    actions.onOpenFormalEvent(hit.rowKey)
                                    query = ""
                                } else {
                                    actions.onSelect(hit.rowKey)
                                    query = ""
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().weight(1f).padding(vertical = if (short) 0.dp else 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val card = state.cards.firstOrNull { it.cardKey == state.currentKey }
                        val form = state.currentForm
                        when {
                            !context.transientNewEvent && completion != null -> CompletionCard(
                                completion, !appearance.reduceMotion, state.canUndoDiscard,
                                actions.onUndoDiscard, actions.onDismiss
                            )
                            !context.transientNewEvent && card == null && state.aiLocked -> AiHoldCard(
                                state.aiSession, actions.onCancelAiBatch, actions.onDismiss
                            )
                            card == null || form == null -> EmptyDeckCard(actions.onDismiss)
                            else -> FanStack(
                                count = state.cards.size,
                                expanded = expanded || short,
                                appearance = appearance,
                                onNext = { actions.onMove(1) },
                                onPrevious = { actions.onMove(-1) }
                            ) {
                                key(card.cardKey) {
                                    DeckCardBody(
                                        card = card,
                                        form = form,
                                        position = state.position,
                                        total = state.cards.size,
                                        expanded = expanded,
                                        aiLocked = state.aiLocked || state.busy,
                                        error = state.error,
                                        conversionNote = state.conversionNote,
                                        dirty = state.dirty,
                                        transientNewEvent = context.transientNewEvent,
                                        alreadyPersisted = context.alreadyPersisted,
                                        canGoPrevious = !context.transientNewEvent && state.canGoPrevious,
                                        canGoNext = !context.transientNewEvent && state.canGoNext,
                                        onExpandToggle = { expanded = !expanded },
                                        onFormChange = actions.onFormChange,
                                        onTypeChange = actions.onConvert,
                                        typeMenuExpanded = typeMenu,
                                        onTypeMenuChange = { typeMenu = it },
                                        onPrevious = { actions.onMove(-1) },
                                        onNext = { actions.onMove(1) },
                                        onCommit = actions.onCommit,
                                        onDiscard = actions.onDiscard,
                                        onRetrySave = actions.onRetrySave,
                                        onPickStartDate = actions.onPickStartDate,
                                        onPickStartTime = actions.onPickStartTime,
                                        onPickEndDate = actions.onPickEndDate,
                                        onPickEndTime = actions.onPickEndTime,
                                        onDeadlineDateToInstant = actions.onDeadlineDateToInstant
                                    )
                                }
                            }
                        }
                    }
                    if (!context.transientNewEvent && state.canUndoDiscard && state.completion == null && !short) {
                        TextButton(onClick = actions.onUndoDiscard, enabled = !state.aiLocked && !state.busy) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("撤销上一次丢弃")
                        }
                    }
                    if (!context.transientNewEvent && !short) {
                        Surface(
                            shape = RoundedCornerShape(appearance.chipCornerRadiusDp.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ) {
                            Text("卡内逐张 · 卡外快速浏览 · 点外部保存退出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }

    if (!context.transientNewEvent) {
        state.conflict?.let { prompt ->
            ConflictDialog(prompt, actions.onConfirmConflict, actions.onKeepConflict, appearance)
        }
        state.restore?.let { prompt ->
            appearance.showDialog(
                dev.powerfulcalendar.ui.v2.NativeDeckDialog(
                    onDismissRequest = actions.onDeclineRestore,
                    title = { Text("恢复 ${prompt.sequenceLabel}？") },
                    text = { Text(prompt.message) },
                    confirmButton = {
                        TextButton(onClick = actions.onConfirmRestore, enabled = !state.busy) { Text("恢复同一事件") }
                    },
                    dismissButton = { TextButton(onClick = actions.onDeclineRestore) { Text("保留草稿") } }
                )
            )
        }
    }
}

@Composable
private fun NewEventHeader(busy: Boolean, onExit: () -> Unit) {
    val appearance = LocalNativeDeckContext.current!!.appearance
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(appearance.barCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("新建事件", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.width(8.dp))
            Text("只在修改后暂存", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onExit, enabled = !busy,
                modifier = Modifier.size(48.dp).testTag("new-event-close")) {
                Icon(Icons.Default.Close, "保存草稿并退出")
            }
        }
    }
}

@Composable
private fun DeckHeader(
    total: Int,
    aiLocked: Boolean,
    session: dev.powerfulcalendar.ui.v2.NativeDeckAiSession?,
    onClose: () -> Unit,
    onCancelAi: () -> Unit
) {
    val appearance = LocalNativeDeckContext.current!!.appearance
    Surface(shape = RoundedCornerShape(appearance.barCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("待处理", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.width(8.dp))
            Text(if (aiLocked) "只读" else if (total == 0) "0 张" else "$total 张",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (aiLocked && session != null) {
                AssistChip(onClick = onCancelAi, label = { Text("AI 进行中 · 停止") })
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, "保存并退出待处理")
            }
        }
    }
}

@Composable
private fun AiProgressBanner(session: dev.powerfulcalendar.ui.v2.NativeDeckAiSession, onCancel: () -> Unit) {
    val appearance = LocalNativeDeckContext.current!!.appearance
    Surface(Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(appearance.chipCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("本次授权 ${session.authorizedCount} 张 · 仍待处理 ${session.remainingCount} 张",
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("停止") }
            }
            if (!appearance.reduceMotion) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            Text("期间卡组为只读；已提交的结果会保留，未处理的卡片继续等待你。",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DeckSearch(
    query: String,
    onChange: (String) -> Unit,
    results: List<NativeDeckSearchHit>,
    onPick: (NativeDeckSearchHit) -> Unit
) {
    val appearance = LocalNativeDeckContext.current!!.appearance
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
        Surface(Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = RoundedCornerShape(appearance.chipCornerRadiusDp.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)) {
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                if (results.isEmpty()) {
                    Text("没有匹配的草稿或正式事件", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                results.take(8).forEach { hit ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(hit) }.heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(hit.sequenceLabel, style = MaterialTheme.typography.labelMedium,
                            color = if (hit.formal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(hit.title.ifBlank { if (hit.formal) "未命名事件" else "未命名草稿" },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(if (hit.formal) "编辑" else "草稿", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FanStack(
    count: Int,
    expanded: Boolean,
    appearance: NativeDeckAppearance,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    content: @Composable () -> Unit
) {
    val regions = remember { mutableMapOf<Any, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val motion = if (appearance.reduceMotion) snap<Float>() else spring<Float>(dampingRatio = 0.86f, stiffness = 380f)
    val displacement by animateFloatAsState(drag, if (dragging) snap() else motion, label = "deckSwipe")
    val heightFraction by animateFloatAsState(if (expanded) 1f else 0.90f, motion, label = "deckExpand")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        for (layer in (if (appearance.blur) minOf(2, count - 1) else 0) downTo 1) {
            val rotation = if (layer % 2 == 0) 4.2f else -3.4f
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f)
                    .heightIn(max = if (expanded) Dp.Infinity else 460.dp)
                    .fillMaxHeight(if (expanded) 0.94f else 0.90f)
                    .offset(y = (layer * 8).dp).scale(1f - layer * 0.045f).rotate(rotation)
                    .alpha(0.55f - layer * 0.12f),
                shape = RoundedCornerShape(appearance.cardCornerRadiusDp.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) { Box(Modifier.fillMaxSize()) }
        }
        Surface(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = if (expanded) Dp.Infinity else 460.dp)
                .fillMaxHeight(heightFraction).testTag("draft-card")
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer { translationX = displacement }
                .cardSwipe(regions, { origin }, { x, active -> drag = x.coerceIn(-24f, 24f); dragging = active }, onNext, onPrevious),
            shape = RoundedCornerShape(appearance.cardCornerRadiusDp.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 0.dp,
            shadowElevation = if (appearance.blur) 3.dp else 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            CompositionLocalProvider(LocalDeckInputs provides regions) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckCardBody(
    card: NativeDeckCard,
    form: NativeDeckForm,
    position: Int,
    total: Int,
    expanded: Boolean,
    aiLocked: Boolean,
    error: String?,
    conversionNote: String?,
    dirty: Boolean,
    transientNewEvent: Boolean,
    alreadyPersisted: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onExpandToggle: () -> Unit,
    onFormChange: (NativeDeckForm) -> Unit,
    onTypeChange: (NativeDeckKind) -> Unit,
    typeMenuExpanded: Boolean,
    onTypeMenuChange: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCommit: () -> Unit,
    onDiscard: () -> Unit,
    onRetrySave: () -> Unit,
    onPickStartDate: (Long) -> Unit,
    onPickStartTime: (Long) -> Unit,
    onPickEndDate: (Long) -> Unit,
    onPickEndTime: (Long) -> Unit,
    onDeadlineDateToInstant: () -> Unit
) {
    val context = LocalNativeDeckContext.current!!
    val appearance = context.appearance
    val missing = form.missingFields
    var showDeleteConfirm by remember(card.cardKey) { mutableStateOf(false) }
    var arrived by remember(card.cardKey) { mutableStateOf(false) }
    LaunchedEffect(card.cardKey) { arrived = true }
    val motion = if (appearance.reduceMotion) snap<Float>() else spring<Float>(dampingRatio = 0.86f, stiffness = 380f)
    val entrance by animateFloatAsState(if (arrived) 1f else 0.86f, motion, label = "deckArrival")

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = entrance }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (transientNewEvent) "新建" else card.sequenceLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Box {
                        AssistChip(onClick = { if (!aiLocked) onTypeMenuChange(true) },
                            label = { Text(form.kind.label + " ▾") })
                        DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { onTypeMenuChange(false) }) {
                            NativeDeckKind.entries.forEach { kind ->
                                DropdownMenuItem(text = { Text(kind.label + "  ·  " + kindHint(kind)) },
                                    onClick = { onTypeChange(kind) })
                            }
                        }
                    }
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)) {
                        Text(card.operationLabel, Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onExpandToggle, modifier = Modifier.size(48.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (expanded) "收起详细编辑" else "展开完整编辑")
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!expanded) {
                    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "展开完整编辑", onClick = onExpandToggle)
                        .padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(form.title.ifBlank { "给这段时间起个名字" }, style = MaterialTheme.typography.headlineSmall)
                        Text(when (form.kind) {
                            NativeDeckKind.ALARM -> form.startText
                            NativeDeckKind.ACTIVITY -> "${form.startText}\n${form.endText}"
                            NativeDeckKind.DEADLINE -> "${form.endText} · 截止"
                            NativeDeckKind.MEMO -> form.startText.ifBlank { "无日期备忘" }
                        }, style = MaterialTheme.typography.bodyLarge,
                            color = Color(appearance.kindColors.getValue(form.kind)))
                        if (form.location.isNotBlank()) Text(form.location, style = MaterialTheme.typography.bodyMedium)
                        Text(if (aiLocked) "卡组只读 · 可浏览详情" else "轻点卡片，展开完整编辑",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    UnifiedEventFormFields(form, expanded, aiLocked, card, appearance, onFormChange,
                        onPickStartDate, onPickStartTime, onPickEndDate, onPickEndTime,
                        onDeadlineDateToInstant)
                }
                if (!expanded && form.notes.isNotBlank()) Text(form.notes, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                conversionNote?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary) }
                if (form.recoveryScoped || form.occurrenceScoped) {
                    Text("这是带重复范围的恢复草稿。请从原事件继续编辑并保存，不能从草稿卡组按整组替换发布。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                } else if (form.repeating) {
                    Text("重复事件可在原事件编辑器中选择仅本次、本次及以后或整个系列。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (form.kind == NativeDeckKind.ACTIVITY && missing.isEmpty() && form.activityEndBeforeStart) {
                    Text("结束时刻需要晚于开始时刻", color = MaterialTheme.colorScheme.error)
                }
                if (missing.isNotEmpty() && card.operation != NativeDeckOperation.DELETE) {
                    Text("还需要：${missing.joinToString("、")}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = onRetrySave, enabled = !aiLocked) { Text("重试暂存") }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = canGoPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一张草稿")
                }
                val stateLabel = if (transientNewEvent) {
                    if (alreadyPersisted) "已暂存" else if (dirty) "待暂存" else "未修改"
                } else if (dirty) "待暂存" else "已暂存"
                Text("${position + 1} / ${total.coerceAtLeast(1)} · $stateLabel",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一张草稿")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onDiscard, enabled = !aiLocked,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (card.operation == NativeDeckOperation.DELETE) "不删除" else "丢弃")
                }
                Button(onClick = {
                    if (card.operation == NativeDeckOperation.DELETE) showDeleteConfirm = true else onCommit()
                }, enabled = !aiLocked && !form.recoveryScoped && !form.occurrenceScoped &&
                    (form.publishable || (transientNewEvent && dirty) || card.operation == NativeDeckOperation.DELETE),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(when {
                        form.recoveryScoped || form.occurrenceScoped -> "返回事件继续"
                        card.operation == NativeDeckOperation.DELETE -> "确认删除"
                        transientNewEvent && !form.publishable -> "保存草稿"
                        else -> "发布"
                    })
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val appearance = context.appearance
        appearance.showDialog(
            dev.powerfulcalendar.ui.v2.NativeDeckDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(if (card.operation == NativeDeckOperation.DELETE) "确认删除事件？" else "丢弃这张草稿？") },
                text = {
                    Text(if (card.operation == NativeDeckOperation.DELETE) {
                        "删除会将事件 ${card.sequenceLabel} 的序号永久退役。当前版本不支持撤销正式删除，请确认。"
                    } else if (transientNewEvent) {
                        "尚未发布的编辑内容将被放弃。"
                    } else {
                        "草稿会被丢弃但可以撤销；事件 ${card.sequenceLabel} 的正式内容不受影响。"
                    })
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        if (card.operation == NativeDeckOperation.DELETE) onCommit() else onDiscard()
                    }) { Text(if (card.operation == NativeDeckOperation.DELETE) "删除" else "丢弃") }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
            )
        )
    }
}

@Composable
fun NativeDeckEventForm(context: NativeDeckFormContext) {
    UnifiedEventFormFields(
        form = context.form,
        expanded = true,
        aiLocked = context.aiLocked,
        card = context.card,
        appearance = context.appearance,
        onFormChange = context.actions.onChange,
        onPickStartDate = context.actions.onPickStartDate,
        onPickStartTime = context.actions.onPickStartTime,
        onPickEndDate = context.actions.onPickEndDate,
        onPickEndTime = context.actions.onPickEndTime,
        onDeadlineDateToInstant = context.actions.onDeadlineDateToInstant
    )
}

@Composable
private fun UnifiedEventFormFields(
    form: NativeDeckForm,
    expanded: Boolean,
    aiLocked: Boolean,
    card: NativeDeckCard,
    appearance: NativeDeckAppearance,
    onFormChange: (NativeDeckForm) -> Unit,
    onPickStartDate: (Long) -> Unit,
    onPickStartTime: (Long) -> Unit,
    onPickEndDate: (Long) -> Unit,
    onPickEndTime: (Long) -> Unit,
    onDeadlineDateToInstant: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = form.title, onValueChange = { onFormChange(form.copy(title = it)) },
            modifier = Modifier.fillMaxWidth().deckInput(), singleLine = true, readOnly = aiLocked,
            label = { Text("标题") }, isError = form.title.isBlank())

        if (form.kind == NativeDeckKind.MEMO) {
            Text("备忘可完全不指定日期；也可以填连续的自然日区间。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(form.startText, { onFormChange(form.copy(startText = it)) },
                Modifier.fillMaxWidth().deckInput(), singleLine = true, label = { Text("开始日期（可选，yyyy-MM-dd）") }, readOnly = aiLocked)
            OutlinedTextField(form.endText, { onFormChange(form.copy(endText = it)) },
                Modifier.fillMaxWidth().deckInput(), singleLine = true, label = { Text("结束日期（可选，含当天）") }, readOnly = aiLocked)
        }
        if (form.kind == NativeDeckKind.ALARM || form.kind == NativeDeckKind.ACTIVITY) {
            TimeRow("开始", form.startText, !aiLocked, form.startPickerMillis, "yyyy-MM-dd HH:mm",
                form.validStart || form.startText.isBlank(), { onFormChange(form.copy(startText = it)) },
                onPickStartDate, onPickStartTime)
        }
        if (form.kind == NativeDeckKind.ACTIVITY || form.kind == NativeDeckKind.DEADLINE) {
            TimeRow(if (form.kind == NativeDeckKind.DEADLINE) "截止" else "结束",
                form.endText, !aiLocked, form.endPickerMillis,
                if (form.kind == NativeDeckKind.DEADLINE) "日期 yyyy-MM-dd 或时刻 yyyy-MM-dd HH:mm" else "yyyy-MM-dd HH:mm",
                form.validEnd || form.deadlineDateOnly || form.endText.isBlank(),
                { onFormChange(form.copy(endText = it)) }, onPickEndDate, onPickEndTime)
        }
        if (form.kind == NativeDeckKind.DEADLINE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("只填日期时保持日期精度，不会自动变成 23:59。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (form.deadlineDateOnly) AssistChip(onClick = onDeadlineDateToInstant,
                    enabled = !aiLocked, label = { Text("改为精确时刻") })
            }
        }

        if (expanded && !form.restrictOccurrenceFields) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.location, { onFormChange(form.copy(location = it)) },
                    Modifier.fillMaxWidth().deckInput(), singleLine = true, readOnly = aiLocked,
                    label = { Text("地点（可选）") })
                OutlinedTextField(form.notes, { onFormChange(form.copy(notes = it)) },
                    Modifier.fillMaxWidth().deckInput(), minLines = 2, readOnly = aiLocked,
                    label = { Text("备注") })
                Text("提醒方式与时间（与事件类型分别设置）", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NativeDeckReminderMethod.entries.forEach { method ->
                        FilterChip(selected = form.reminder.method == method,
                            onClick = { onFormChange(form.copy(reminder = form.reminder.copy(method = method))) },
                            enabled = !aiLocked,
                            label = { Text(if (method == NativeDeckReminderMethod.MESSAGE) "消息" else "闹钟（精确）") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NativeDeckReminderTiming.entries.forEach { timing ->
                        FilterChip(selected = form.reminder.timing == timing,
                            onClick = { onFormChange(form.copy(reminder = form.reminder.copy(
                                timing = timing, minutes = maxOf(form.reminder.minutes, 0)))) },
                            enabled = !aiLocked,
                            label = { Text(if (timing == NativeDeckReminderTiming.DAY_START) "当天 00:00" else "相对开始/截止") })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1, 0, 5, 10, 30, 60).forEach { minutes ->
                        FilterChip(selected = form.reminder.minutes == minutes,
                            onClick = { onFormChange(form.copy(reminder = form.reminder.copy(minutes = minutes))) },
                            enabled = !aiLocked && (form.reminder.timing == NativeDeckReminderTiming.OFFSET || minutes == -1),
                            label = { Text(when (minutes) { -1 -> "关闭"; 0 -> "准时"; 60 -> "1 小时"; else -> "$minutes 分钟" }) })
                    }
                }
                if (form.kind == NativeDeckKind.DEADLINE && form.deadlineDateOnly) {
                    Text("只有日期的截止仍保持日期精度；默认提醒在该日期 00:00，并非把截止时刻改成零点。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                var customOffset by remember(card.cardKey) { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(customOffset, { input ->
                        if (input.length <= 6 && input.all(Char::isDigit)) customOffset = input
                    }, label = { Text("自定义分钟") }, singleLine = true, modifier = Modifier.width(150.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !aiLocked && form.reminder.timing == NativeDeckReminderTiming.OFFSET)
                    val offset = customOffset.toIntOrNull()
                    TextButton(onClick = {
                        if (offset != null) onFormChange(form.copy(reminder = form.reminder.copy(minutes = offset)))
                    }, enabled = !aiLocked && form.reminder.timing == NativeDeckReminderTiming.OFFSET && offset != null && offset in 0..10080) {
                        Text("应用")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("加入待办（完成跟踪）", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = form.trackingEnabled || form.kind == NativeDeckKind.MEMO,
                        onCheckedChange = { onFormChange(form.copy(trackingEnabled = it)) },
                        enabled = !aiLocked && form.kind != NativeDeckKind.MEMO)
                }
                if (form.kind == NativeDeckKind.MEMO && form.startText.isBlank()) {
                    Text("无日期备忘不设置基于时刻的提醒或重复周期。", style = MaterialTheme.typography.labelSmall)
                }
                Text("重复", style = MaterialTheme.typography.labelLarge)
                if (form.repeating) {
                    OutlinedTextField(form.recurrenceFromDate.orEmpty(), {
                        onFormChange(form.copy(recurrenceFromDate = it.ifBlank { null }))
                    }, label = { Text("从该日期起有效（可留空 yyyy-MM-dd）") }, singleLine = true,
                        readOnly = aiLocked, modifier = Modifier.fillMaxWidth().deckInput())
                    OutlinedTextField(form.recurrenceUntilDate.orEmpty(), {
                        onFormChange(form.copy(recurrenceUntilDate = it.ifBlank { null }))
                    }, label = { Text("截至该日期有效（含当天，可留空）") }, singleLine = true,
                        readOnly = aiLocked, modifier = Modifier.fillMaxWidth().deckInput())
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NativeDeckRecurrenceFreq.entries.forEach { freq ->
                        FilterChip(selected = form.recurrenceFreq == freq,
                            onClick = { onFormChange(form.copy(
                                recurrenceFreq = freq,
                                recurrenceFromDate = if (freq == NativeDeckRecurrenceFreq.NONE) null else form.recurrenceFromDate,
                                recurrenceUntilDate = if (freq == NativeDeckRecurrenceFreq.NONE) null else form.recurrenceUntilDate)) },
                            enabled = !aiLocked,
                            label = { Text(when (freq) {
                                NativeDeckRecurrenceFreq.NONE -> "不重复"
                                NativeDeckRecurrenceFreq.DAILY -> "每天"
                                NativeDeckRecurrenceFreq.WEEKLY -> "每周"
                                NativeDeckRecurrenceFreq.MONTHLY -> "每月"
                            }) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用", style = MaterialTheme.typography.labelLarge)
                        Text(if (form.enabled) "会参与提醒调度" else "保留内容，但不调度提醒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = form.enabled,
                        onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                        modifier = Modifier.semantics { contentDescription = "启用事件提醒调度" },
                        enabled = !aiLocked
                    )
                }
                Text("时区 · ${form.zoneLabel}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("来源 · ${form.sourceLabel}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (card.draftVersion > 0) Text("草稿版本 ${card.draftVersion}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (card.status == dev.powerfulcalendar.ui.v2.NativeDeckStatus.CONFLICT) {
                    Text("此卡基于旧版本，发布前会要求你确认整份覆盖。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    value: String,
    enabled: Boolean,
    fallbackMillis: Long,
    hint: String,
    valid: Boolean,
    onChange: (String) -> Unit,
    onPickDate: (Long) -> Unit,
    onPickTime: (Long) -> Unit
) {
    Row(Modifier.deckInput(), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(value, onChange, Modifier.weight(1f), singleLine = true, readOnly = !enabled,
            label = { Text(label) }, placeholder = { Text(hint) },
            isError = value.isNotBlank() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
        IconButton(onClick = { onPickDate(fallbackMillis) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.CalendarToday, "选择 $label 日期", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { onPickTime(fallbackMillis) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Schedule, "选择 $label 时间", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyDeckCard(onClose: () -> Unit) {
    val appearance = LocalNativeDeckContext.current!!.appearance
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(appearance.cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 6.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("没有待处理卡片", style = MaterialTheme.typography.titleMedium)
            Text("所有草稿都已处理。新的自然语言、截图或 AI 建议会重新出现在这里。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClose) { Text("返回时间轴") }
        }
    }
}

@Composable
private fun AiHoldCard(session: dev.powerfulcalendar.ui.v2.NativeDeckAiSession?, onCancel: () -> Unit, onClose: () -> Unit) {
    val appearance = LocalNativeDeckContext.current!!.appearance
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(appearance.cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 6.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(28.dp))
            Text("AI 正在独占处理卡组", style = MaterialTheme.typography.titleMedium)
            Text("你可以查看时间轴，但不能同时编辑卡片。停止后会保留已完成的结果与剩余卡片。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            session?.let { Text("授权 ${it.authorizedCount} 张 · 批次 ${it.lockEpoch}",
                style = MaterialTheme.typography.labelSmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("停止批次") }
                Button(onClick = onClose) { Text("返回时间轴") }
            }
        }
    }
}

@Composable
private fun CompletionCard(
    completion: NativeDeckCompletion,
    animated: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDone: () -> Unit
) {
    val appearance = LocalNativeDeckContext.current!!.appearance
    var entered by remember { mutableStateOf(!animated) }
    LaunchedEffect(animated) { if (animated) entered = true }
    val motion = if (appearance.reduceMotion) snap<Float>() else spring<Float>(dampingRatio = 0.86f, stiffness = 380f)
    val scale by animateFloatAsState(if (entered) 1f else 0.86f, motion, label = "completionScale")
    val alpha by animateFloatAsState(if (entered) 1f else 0f, motion, label = "completionAlpha")
    Surface(Modifier.fillMaxWidth().scale(if (animated) scale else 1f).alpha(if (animated) alpha else 1f),
        shape = RoundedCornerShape(appearance.cardCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), tonalElevation = 10.dp,
        shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center) {
                if (appearance.hasCompletionEmblem) appearance.completionEmblem()
                else Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
            }
            Text("待处理已清空", style = MaterialTheme.typography.headlineSmall)
            Text("已提交 ${completion.published} 项 · 已丢弃 ${completion.discarded} 项" +
                if (completion.conflicts > 0) " · 本次记录 ${completion.conflicts} 项冲突" else "",
                style = MaterialTheme.typography.bodyMedium)
            Text("为接下来的时间，留一点从容。", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canUndo) TextButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("撤销丢弃")
                }
                Button(onClick = onDone) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("退出待处理")
                }
            }
        }
    }
}

@Composable
private fun ConflictDialog(
    prompt: NativeDeckConflict,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    appearance: NativeDeckAppearance
) {
    appearance.showDialog(
        dev.powerfulcalendar.ui.v2.NativeDeckDialog(
            onDismissRequest = onKeep,
            title = { Text("正式内容已变化 ${prompt.sequenceLabel}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (prompt.delete) "这是一张删除提案。确认会删除当前正式版本，序号退役；下方展示当前内容与提案原快照。"
                        else "确认后用本卡的完整快照替换当前正式对象。请检查全部差异，未展示过的新版本需要重新确认。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    HorizontalDivider()
                    Text("完整差异", style = MaterialTheme.typography.labelLarge)
                    Text("当前 v${prompt.currentRevision ?: 0} → 本卡完整快照", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            prompt.rows.forEach { row ->
                                Surface(shape = RoundedCornerShape(appearance.chipCornerRadiusDp.dp),
                                    color = if (row.changed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(row.label + if (row.changed) " · 将更改" else " · 保持",
                                            style = MaterialTheme.typography.labelLarge)
                                        Text("现在：${row.before}", style = MaterialTheme.typography.bodyMedium)
                                        Text("本卡：${row.after}", style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConfirm) {
                Text(if (prompt.delete) "确认删除当前版本" else "确认整份覆盖")
            } },
            dismissButton = { TextButton(onClick = onKeep) { Text("保留待处理") } }
        )
    )
}

@Composable
private fun Modifier.deckInput(): Modifier {
    val regions = LocalDeckInputs.current
    val token = remember { Any() }
    DisposableEffect(regions) { onDispose { regions.remove(token) } }
    return onGloballyPositioned { regions[token] = it.boundsInRoot() }
}

@Composable
private fun Modifier.cardSwipe(
    regions: Map<Any, Rect>, origin: () -> Offset,
    onDrag: (Float, Boolean) -> Unit, onNext: () -> Unit, onPrevious: () -> Unit
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
                    if (!claimed && kotlin.math.abs(travel.y) > viewConfiguration.touchSlop &&
                        kotlin.math.abs(travel.y) > kotlin.math.abs(travel.x)) blocked = true
                    if (!blocked && !claimed && kotlin.math.abs(travel.x) > viewConfiguration.touchSlop * 1.4f &&
                        kotlin.math.abs(travel.x) > kotlin.math.abs(travel.y) * 1.4f) claimed = true
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

@Composable
private fun Modifier.outsideGesture(onTap: () -> Unit, onBrowse: (Int) -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    val browse by rememberUpdatedState(onBrowse)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
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
                val steps = horizontalPageSteps(travel.x, travel.y, 60.dp.toPx(), 110.dp.toPx(), true,
                    tracker.calculateVelocity().x)
                if (steps != 0) browse(steps)
                else if (maxTravel < viewConfiguration.touchSlop) tap()
            }
        }
    }
}

private fun kindHint(kind: NativeDeckKind): String = when (kind) {
    NativeDeckKind.ALARM -> "只有开始时刻"
    NativeDeckKind.ACTIVITY -> "开始到结束"
    NativeDeckKind.DEADLINE -> "时刻或日期"
    NativeDeckKind.MEMO -> "按天待办或无日期"
}

private fun horizontalPageSteps(
    dx: Float,
    dy: Float,
    minimumDistance: Float,
    width: Float,
    outside: Boolean,
    velocityX: Float = 0f
): Int {
    val horizontal = kotlin.math.abs(dx)
    if (horizontal < minimumDistance || horizontal <= kotlin.math.abs(dy) * 1.25f) return 0
    val direction = if (dx < 0f) 1 else -1
    if (!outside) return direction
    val fling = kotlin.math.abs(velocityX) > 900f
    val count = if (fling) (horizontal / width).toInt().coerceAtLeast(1) else 1
    return direction * count.coerceAtMost(8)
}
