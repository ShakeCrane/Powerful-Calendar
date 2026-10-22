package dev.powerfulcalendar.core

/**
 * In-memory formal-event editor. Typing and undo never touch SQLite. The caller explicitly invokes
 * [checkpoint], [save], or [delete] at the corresponding lifecycle/user boundary.
 */
class EditSession private constructor(
    private val store: PowerStore,
    val eventSeq: Long,
    private var expectedRevision: Long,
    private val lease: PowerStore.HumanEditLease,
    private var baseline: DraftForm,
    initial: DraftForm
) : AutoCloseable {
    var form: DraftForm = initial
        private set
    var lastError: String? = null
        private set

    val meaningfulDiff: Boolean get() = form != baseline
    val baselineForm: DraftForm get() = baseline

    fun edit(next: DraftForm) {
        form = next
        lastError = null
        store.touchHumanEdit(lease.token)
    }

    fun discardEditingChanges() {
        form = baseline
        lastError = null
    }

    fun heartbeat(): Boolean = store.touchHumanEdit(lease.token)

    /** One durable checkpoint for an explicit exit/switch/ON_STOP boundary. */
    fun checkpoint(): DraftCard? = try {
        store.checkpointFormalEdit(
            eventSeq, expectedRevision, baseline, form, lease.token
        )
    } catch (error: Exception) {
        lastError = error.message ?: error.javaClass.simpleName
        null
    }

    /** Save remains on this session/card. A no-op save clears an old checkpoint without revision. */
    fun save(): PowerStore.DirectEditOutcome {
        val outcome = store.saveFormalEdit(eventSeq, expectedRevision, form, lease.token)
        when (outcome) {
            is PowerStore.DirectEditOutcome.Saved -> {
                expectedRevision = outcome.revision
                baseline = form
                lastError = null
            }
            is PowerStore.DirectEditOutcome.Invalid -> lastError = outcome.reason
            is PowerStore.DirectEditOutcome.Conflict -> lastError = "Event changed while editing"
            PowerStore.DirectEditOutcome.Locked -> lastError = "Editor is no longer writable"
        }
        return outcome
    }

    fun delete(): PowerStore.DirectEditOutcome =
        store.deleteFormalEvent(eventSeq, expectedRevision, lease.token).also { outcome ->
            if (outcome is PowerStore.DirectEditOutcome.Invalid) lastError = outcome.reason
        }

    override fun close() {
        store.endHumanEdit(lease.token)
    }

    companion object {
        fun open(store: PowerStore, record: TemporalRecord): EditSession {
            val lease = store.beginHumanEdit(record.eventSeq)
            val formal = DraftForm.from(record)
            // An existing checkpoint resumes automatically, but the formal snapshot remains the
            // semantic baseline for undo/no-change detection and optimistic revision checks.
            val resumed = store.pendingDraftsFor(record.eventSeq).firstOrNull()?.form ?: formal
            return EditSession(store, record.eventSeq, record.revision, lease, formal, resumed)
        }
    }
}
