package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.PowerStore
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

/**
 * Renderer sees immutable controller state, but NOT the controller or persistence object.
 * Terminal actions go through the same durable controller state machine as the native deck.
 * Every callback is host-mediated; no renderer can call PowerStore.commit.
 */
class DeckVisualContext internal constructor(
    val state: DeckController.DeckState,
    val busy: Boolean,
    val aiSession: PowerStore.AiSession?,
    val onDismiss: () -> Unit,
    val onOpenFormalEvent: (TemporalRecord) -> Unit,
    val onCancelAiBatch: () -> Unit,
    val formalSearch: (String) -> List<TemporalRecord>,
    val onSelect: (Long) -> Unit,
    val onBrowse: (Int) -> Unit,
    val onEdit: (DraftForm) -> Unit,
    val onConvert: (TemporalKind) -> Unit,
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
    aiSession: PowerStore.AiSession?,
    onDismissRequest: () -> Unit,
    onOpenFormalEvent: (TemporalRecord) -> Unit,
    onCancelAiBatch: () -> Unit,
    onDeckChanged: () -> Unit,
    formalSearch: (String) -> List<TemporalRecord>,
    initialEditorOpen: Boolean = false
) {
    val renderer = DeckPresentationSkinRegistry.active
    val runtime = RuntimeSkinSurfaces.enabled("deck")
    if (renderer == null && !runtime) {
        NativeDraftDeckDialog(controller, aiSession, onDismissRequest,
            onOpenFormalEvent, onCancelAiBatch, onDeckChanged, formalSearch,
            initialEditorOpen = initialEditorOpen)
        return
    }

    val scope = rememberCoroutineScope()
    var state by remember(controller) { mutableStateOf(controller.state) }
    var executing by remember(controller) { mutableStateOf(false) }
    // Observe only in the custom path: native path has its own observer.
    DisposableEffect(controller) {
        controller.observe { next -> scope.launch { state = next } }
        state = controller.state
        onDispose { controller.observe { } }
    }
    // Persist only at exit/navigation/lifecycle checkpoints, not every keystroke.
    fun command(changed: Boolean = false, block: suspend () -> Unit) {
        if (executing || state.busy) return
        executing = true  // acquire synchronously, before coroutine dispatch
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                state = controller.state
                if (changed) onDeckChanged()
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
            val flushed = controller.flush()
            if (flushed) withContext(Dispatchers.Main) { afterSave() }
            else throw IllegalStateException("暂存未完成，请检查错误并重试")
        }
    val locked = state.aiLocked || executing || state.busy
    val context = DeckVisualContext(
        state = state,
        busy = locked,
        aiSession = aiSession,
        onDismiss = { leave() },
        onOpenFormalEvent = { record -> leave { onOpenFormalEvent(record) } },
        onCancelAiBatch = onCancelAiBatch,
        formalSearch = formalSearch,
        onSelect = { id -> if (!locked) command { controller.select(id) } },
        onBrowse = { steps -> if (!locked) command { controller.jump(steps) } },
        onEdit = { form -> if (!locked) controller.edit(form) },
        onConvert = { kind ->
            if (!locked) state.form?.let { form ->
                controller.edit(TemporalConversion.convert(form, kind).form)
            }
        },
        onCommit = { if (!locked) command(changed = true) { controller.commit() } },
        onDiscard = { if (!locked) command(changed = true) { controller.discard() } },
        onUndoDiscard = { if (!locked) command(changed = true) { controller.undoDiscard() } },
        onRetrySave = { if (!locked) command { controller.flush() } },
        onConfirmConflict = { if (!locked) command(changed = true) { controller.confirmConflictReplacement() } },
        onKeepConflict = { controller.dismissConflict() },
        onConfirmRestore = { if (!locked) command(changed = true) { controller.requestRestore() } },
        onDeclineRestore = { controller.dismissRestore() },
        native = {
            NativeDraftDeckDialog(controller, aiSession, onDismissRequest,
                onOpenFormalEvent, onCancelAiBatch, onDeckChanged, formalSearch,
                initialEditorOpen = initialEditorOpen)
        }
    )
    if (runtime) RuntimeSkinSurfaces.deck(context) else renderer!!.Render(context)
}
