package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.AiBatchSessionV1
import dev.powerfulcalendar.core.api.v1.DraftReviewSnapshotV1
import dev.powerfulcalendar.core.api.v1.DraftReviewFeatureApiV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReceiptV1
import dev.powerfulcalendar.core.api.v1.DraftReviewCardV1
import dev.powerfulcalendar.core.api.v1.DraftReviewConflictV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventSearchHitV1
import dev.powerfulcalendar.core.api.v1.DraftReviewRestoreV1
import dev.powerfulcalendar.core.api.v1.EventFormV1
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DeckVisualRenderer {
    val id: String
    @Composable fun Render(context: DeckVisualContext)
}

object DeckPresentationSkinRegistry {
    var active by mutableStateOf<DeckVisualRenderer?>(null)
        private set
    fun use(renderer: DeckVisualRenderer?) {
        require(renderer == null || renderer.id.isNotBlank()) { "Invalid deck skin ID" }
        active = renderer
    }
    fun reset() { active = null }
}

private data class PendingDeckCommitApproval(
    val card: DraftReviewCardV1,
    val form: EventFormV1?,
    val stateRevision: Long
)

private data class PendingDeckConflictApproval(
    val prompt: DraftReviewConflictV1,
    val stateRevision: Long
)

private data class PendingDeckRestoreApproval(
    val prompt: DraftReviewRestoreV1,
    val form: EventFormV1,
    val stateRevision: Long
)

/**
 * Renderer sees the versioned Draft/Review API snapshot, but NOT Core state or persistence.
 * Terminal actions go through the same durable controller state machine as the native deck.
 * Every callback is host-mediated; no renderer can call PowerStore.commit.
 */
class DeckVisualContext internal constructor(
    val state: DraftReviewSnapshotV1,
    val busy: Boolean,
    val canCommit: Boolean,
    val aiSession: AiBatchSessionV1?,
    val onDismiss: () -> Unit,
    val onOpenFormalEvent: (EventSearchHitV1) -> Unit,
    val onCancelAiBatch: () -> Unit,
    val formalSearch: (String) -> List<EventSearchHitV1>,
    val onSelect: (Long) -> Unit,
    val onBrowse: (Int) -> Unit,
    val onEdit: (EventFormV1) -> Unit,
    val onConvert: (EventKindV1) -> Unit,
    val onCommit: () -> Unit,
    val onDiscard: () -> Unit,
    val onUndoDiscard: () -> Unit,
    val onRetrySave: () -> Unit,
    val onConfirmConflict: () -> Unit,
    val onKeepConflict: () -> Unit,
    val onConfirmRestore: () -> Unit,
    val onDeclineRestore: () -> Unit,
    private val native: @Composable () -> Unit
) {
    @Composable fun Native() = native()
}

@Composable
fun DraftDeckDialog(
    controller: DeckController,
    draftReviewApi: DraftReviewFeatureApiV1,
    aiSession: AiBatchSessionV1?,
    onDismissRequest: () -> Unit,
    onOpenFormalEvent: (EventSearchHitV1) -> Unit,
    onCancelAiBatch: () -> Unit,
    onDeckChanged: () -> Unit,
    formalSearch: (String) -> List<EventSearchHitV1>,
    initialEditorOpen: Boolean = false
) {
    val renderer = DeckPresentationSkinRegistry.active
    val runtime = RuntimeSkinSurfaces.enabled("deck")
    if ((renderer == null || NativeSkinHostRegistration.isDeckRenderer(renderer)) && !runtime) {
        NativeDraftDeckDialog(draftReviewApi, aiSession, onDismissRequest,
            onOpenFormalEvent, onCancelAiBatch, onDeckChanged, formalSearch,
            initialEditorOpen = initialEditorOpen)
        return
    }

    val scope = rememberCoroutineScope()
    var state by remember(controller) { mutableStateOf(controller.state) }
    var executing by remember(controller) { mutableStateOf(false) }
    var pendingCommitApproval by remember(draftReviewApi) {
        mutableStateOf<PendingDeckCommitApproval?>(null)
    }
    var pendingConflictApproval by remember(draftReviewApi) {
        mutableStateOf<PendingDeckConflictApproval?>(null)
    }
    var pendingRestoreApproval by remember(draftReviewApi) {
        mutableStateOf<PendingDeckRestoreApproval?>(null)
    }
    // Keep the presentation listener separate from the API's subscription.
    DisposableEffect(controller) {
        val subscription = controller.observeAdditional { next -> scope.launch { state = next } }
        state = controller.state
        onDispose { subscription.close() }
    }
    // Persist only at exit/navigation/lifecycle checkpoints, not every keystroke.
    fun command(changed: Boolean = false, block: suspend () -> DraftReviewReceiptV1) {
        if (executing || state.busy) return
        executing = true  // acquire synchronously, before coroutine dispatch
        scope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) { block() }
                state = controller.state
                if (changed && receipt.succeeded) onDeckChanged()
                if (!receipt.succeeded) {
                    state = state.copy(error = receipt.message ?: receipt.errorCode ?: "操作未完成，草稿仍被保留")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = controller.state.copy(error = error.message ?: "操作未完成，草稿仍被保留")
            } finally {
                executing = false
            }
        }
    }
    fun leave(afterSave: () -> Unit = onDismissRequest) =
        command {
            val receipt = draftReviewApi.flush("deck-leave-${java.util.UUID.randomUUID()}")
            if (receipt.succeeded) {
                withContext(Dispatchers.Main) { afterSave() }
                receipt
            } else receipt
        }
    val locked = state.aiLocked || executing || state.busy
    val context = DeckVisualContext(
        state = draftReviewApi.snapshot.copy(error = state.error),
        busy = locked,
        canCommit = state.form?.isPublishable() == true,
        aiSession = aiSession,
        onDismiss = { leave() },
        onOpenFormalEvent = { hit -> leave { onOpenFormalEvent(hit) } },
        onCancelAiBatch = onCancelAiBatch,
        formalSearch = formalSearch,
        onSelect = { id -> if (!locked) command {
            draftReviewApi.select(id, java.util.UUID.randomUUID().toString())
        } },
        onBrowse = { steps -> if (!locked) command {
            draftReviewApi.jump(steps, java.util.UUID.randomUUID().toString())
        } },
        onEdit = { form -> if (!locked) draftReviewApi.snapshot.current?.let { card ->
            val receipt = draftReviewApi.editCurrentDraft(
                card.draftId, card.draftVersion, draftReviewApi.snapshot.stateRevision,
                form
            )
            if (!receipt.succeeded) state = controller.state.copy(error = receipt.message ?: receipt.errorCode)
        } },
        onConvert = { kind ->
            if (!locked) state.form?.let { form ->
                val current = draftReviewApi.snapshot.current ?: return@let
                val receipt = draftReviewApi.editCurrentDraft(
                    current.draftId, current.draftVersion, draftReviewApi.snapshot.stateRevision,
                    TemporalConversion.convert(form, kind.toCoreTemporalKind()).form.toEventFormV1()
                )
                if (!receipt.succeeded) state = controller.state.copy(
                    error = receipt.message ?: receipt.errorCode
                )
            }
        },
        onCommit = { if (!locked) draftReviewApi.snapshot.let { snapshot ->
            snapshot.current?.let { card ->
                pendingCommitApproval = PendingDeckCommitApproval(
                    card, snapshot.form, snapshot.stateRevision
                )
            }
        } },
        onDiscard = { if (!locked) draftReviewApi.snapshot.current?.let { card ->
            command(changed = true) { draftReviewApi.discard(
                card.draftId, card.draftVersion, java.util.UUID.randomUUID().toString()
            ) }
        } },
        onUndoDiscard = { if (!locked) controller.state.lastDiscardId?.let { draftId ->
            command(changed = true) { draftReviewApi.undoDiscard(
                draftId, java.util.UUID.randomUUID().toString()
            ) }
        } },
        onRetrySave = { if (!locked) command {
            draftReviewApi.flush(java.util.UUID.randomUUID().toString())
        } },
        onConfirmConflict = { if (!locked) draftReviewApi.snapshot.let { snapshot ->
            snapshot.conflict?.let { prompt ->
                pendingConflictApproval = PendingDeckConflictApproval(prompt, snapshot.stateRevision)
            }
        } },
        onKeepConflict = { draftReviewApi.dismissConflict() },
        onConfirmRestore = { if (!locked) draftReviewApi.snapshot.let { snapshot ->
            val form = snapshot.form
            snapshot.restore?.let { prompt -> if (form != null) {
                pendingRestoreApproval = PendingDeckRestoreApproval(prompt, form, snapshot.stateRevision)
            }
            }
        } },
        onDeclineRestore = { draftReviewApi.dismissRestore() },
        native = {
            NativeDraftDeckDialog(draftReviewApi, aiSession, onDismissRequest,
                onOpenFormalEvent, onCancelAiBatch, onDeckChanged, formalSearch,
                initialEditorOpen = initialEditorOpen)
        }
    )
    if (runtime) RuntimeSkinSurfaces.deck(context) else renderer!!.Render(context)

    pendingCommitApproval?.let { approval ->
        val action = when (approval.card.operation) {
            dev.powerfulcalendar.core.api.v1.DraftOperationV1.CREATE -> "创建"
            dev.powerfulcalendar.core.api.v1.DraftOperationV1.DELETE -> "删除"
            dev.powerfulcalendar.core.api.v1.DraftOperationV1.REPLACE,
            dev.powerfulcalendar.core.api.v1.DraftOperationV1.CONVERT -> "修改"
        }
        AlertDialog(
            onDismissRequest = { pendingCommitApproval = null },
            title = { Text("确认${action}正式事件") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("事件 #${approval.card.eventSeq}")
                    Text("草稿版本 ${approval.card.draftVersion}；来源版本 ${approval.card.baseRevision ?: "新建"}")
                    approval.form?.hostReviewLines()?.forEach { line -> Text(line) }
                    Spacer(Modifier.height(8.dp))
                    Text("请核对这张草稿。确认后才会写入正式日历。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val latest = draftReviewApi.snapshot
                    val current = latest.current
                    if (current?.draftId != approval.card.draftId ||
                        current.draftVersion != approval.card.draftVersion ||
                        latest.stateRevision != approval.stateRevision || latest.form != approval.form
                    ) {
                        pendingCommitApproval = null
                            state = controller.state.copy(error = "草稿已变化，请重新核对后提交")
                    } else {
                        pendingCommitApproval = null
                        command(changed = true) {
                            draftReviewApi.commit(current.draftId, current.draftVersion,
                                approval.stateRevision,
                                java.util.UUID.randomUUID().toString())
                        }
                    }
                }) { Text("确认提交") }
            },
            dismissButton = { TextButton(onClick = { pendingCommitApproval = null }) { Text("取消") } }
        )
    }

    pendingConflictApproval?.let { approval ->
        val prompt = approval.prompt
        AlertDialog(
            onDismissRequest = { pendingConflictApproval = null },
            title = { Text("确认覆盖已变化的事件") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("事件 #${prompt.eventSeq} · 当前版本 ${prompt.currentRevision ?: "未知"}")
                    prompt.changes.forEach { change -> Text("${change.label}：${change.before} → ${change.after}") }
                    prompt.approvedForm.hostReviewLines().forEach { line -> Text(line) }
                    Spacer(Modifier.height(8.dp))
                    Text("只有在核对差异后确认，才会覆盖当前版本。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val latest = draftReviewApi.snapshot
                    val current = latest.conflict
                    val revision = prompt.currentRevision
                    if (current != prompt || revision == null ||
                        latest.stateRevision != approval.stateRevision
                    ) {
                        pendingConflictApproval = null
                            state = controller.state.copy(error = "冲突内容已变化，请重新核对")
                    } else {
                        pendingConflictApproval = null
                        command(changed = true) {
                            draftReviewApi.confirmConflictReplacement(
                                prompt.draftId, prompt.card.draftVersion, approval.stateRevision, revision,
                                java.util.UUID.randomUUID().toString()
                            )
                        }
                    }
                }) { Text("确认覆盖") }
            },
            dismissButton = { TextButton(onClick = { pendingConflictApproval = null }) { Text("取消") } }
        )
    }

    pendingRestoreApproval?.let { approval ->
        val prompt = approval.prompt
        AlertDialog(
            onDismissRequest = { pendingRestoreApproval = null },
            title = { Text("确认恢复已删除事件") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("事件 #${prompt.eventSeq} · 已审核来源版本 ${prompt.card.baseRevision ?: "未知"}")
                    approval.form.hostReviewLines().forEach { line -> Text(line) }
                    Spacer(Modifier.height(8.dp))
                    Text("恢复会保留原事件序号，并创建新版本。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val latest = draftReviewApi.snapshot
                    val current = latest.restore
                    val revision = prompt.card.baseRevision
                    if (current != prompt || revision == null ||
                        latest.stateRevision != approval.stateRevision || latest.form != approval.form
                    ) {
                        pendingRestoreApproval = null
                            state = controller.state.copy(error = "恢复内容已变化，请重新核对")
                    } else {
                        pendingRestoreApproval = null
                        command(changed = true) {
                            draftReviewApi.requestRestore(
                                prompt.card.draftId, prompt.card.draftVersion, approval.stateRevision, revision,
                                java.util.UUID.randomUUID().toString()
                            )
                        }
                    }
                }) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreApproval = null }) { Text("取消") } }
        )
    }
}

private fun EventFormV1.hostReviewLines(): List<String> {
    val repeat = recurrence
    val repeatSummary = if (repeat.frequency == dev.powerfulcalendar.core.api.v1.EventRecurrenceFrequencyV1.NONE) {
        "不重复"
    } else {
        buildString {
            append(repeat.frequency.name)
            append(" · 间隔 ${repeat.interval}")
            repeat.count?.let { append(" · 次数 $it") }
            repeat.untilMillis?.let { append(" · 截止毫秒 $it") }
            repeat.activeFromDate?.let { append(" · 起始 $it") }
            repeat.activeUntilDate?.let { append(" · 结束 $it") }
            if (repeat.byDay.isNotEmpty()) append(" · 星期 ${repeat.byDay.sorted().joinToString()}")
            if (repeat.exceptions.isNotEmpty()) append(" · 例外 ${repeat.exceptions.sorted().joinToString()}")
            if (repeat.overrides.isNotEmpty()) append(" · 覆盖 ${repeat.overrides.toSortedMap()}")
        }
    }
    return listOf(
        "类型：${kind.name}",
        "标题：${title.ifBlank { "（无标题）" }}",
        "开始/日期：${startText.ifBlank { "—" }}",
        "结束/截止：${endText.ifBlank { "—" }}",
        "时区：$zoneId",
        "地点：${location.ifBlank { "—" }}",
        "备注：${notes.ifBlank { "—" }}",
        "重复：$repeatSummary",
        "编辑范围：${recurrenceScope?.name ?: "普通事件"} · 实例：${editOccurrenceKey ?: "—"}",
        "提醒：${if (reminderMinutes < 0) "关闭" else "$reminderMinutes 分钟"} · ${reminderMethod.name} · ${reminderTiming.name}",
        "启用：$enabled · 完成跟踪：$trackingEnabled",
        "已完成实例：${completedKeys.sorted().joinToString().ifBlank { "—" }}"
    )
}

private fun EventKindV1.toCoreTemporalKind(): TemporalKind = when (this) {
    EventKindV1.ALARM -> TemporalKind.ALARM
    EventKindV1.ACTIVITY -> TemporalKind.ACTIVITY
    EventKindV1.DEADLINE -> TemporalKind.DEADLINE
    EventKindV1.MEMO -> TemporalKind.MEMO
}
