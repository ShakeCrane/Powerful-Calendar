package dev.powerfulcalendar.core

/** Thin adapter so [DeckController] never depends on SQLite directly. */
class StoreDeckPersistence(private val store: PowerStore) : DeckPersistence {
    override fun pending(): List<DraftCard> = store.pendingDrafts()

    override fun card(draftId: Long): DraftCard? = store.draft(draftId)

    override fun stage(draftId: Long, form: DraftForm, expectedVersion: Long): PowerStore.StageResult =
        store.stageDraft(draftId, form, expectedVersion)

    override fun commit(draftId: Long, approveStale: Boolean): PowerStore.CommitOutcome =
        store.commit(draftId, approveStale = approveStale, actor = "user")

    override fun discard(draftId: Long): Boolean = store.discard(draftId)

    override fun undo(draftId: Long): Boolean = store.undoDiscard(draftId)

    override fun pendingCount(): Int = store.pendingCount()

    override fun stats(sinceMillis: Long): PowerStore.DeckStats = store.stats(sinceMillis)

    override fun isAiLocked(): Boolean = store.isLocked()

    override fun restoreDraft(draftId: Long, form: DraftForm): PowerStore.CommitOutcome =
        store.restoreDraft(draftId, form, actor = "user")

    override fun proposeRestore(eventSeq: Long): DraftCard =
        store.proposeRestoreDraft(eventSeq, actor = "user")

    override fun record(eventSeq: Long): TemporalRecord? = store.get(eventSeq)
}
