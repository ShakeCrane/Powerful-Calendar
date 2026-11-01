package dev.powerfulcalendar.core

/** Thin adapter so [DeckController] never depends on SQLite directly. */
class StoreDeckPersistence(private val store: PowerStore) : DeckPersistence {
    override fun pending(): List<DraftCard> = store.pendingDrafts()

    override fun card(draftId: Long): DraftCard? = store.draft(draftId)

    override fun stage(draftId: Long, form: DraftForm, expectedVersion: Long): PowerStore.StageResult =
        store.stageDraft(draftId, form, expectedVersion)

    override fun commit(
        draftId: Long,
        approveStale: Boolean,
        expectedCurrentRevision: Long?
    ): PowerStore.CommitOutcome = store.commit(
        draftId,
        approveStale = approveStale,
        actor = "user",
        expectedCurrentRevision = expectedCurrentRevision
    )

    override fun commit(
        draftId: Long,
        approveStale: Boolean,
        expectedCurrentRevision: Long?,
        expectedDraftVersion: Long?
    ): PowerStore.CommitOutcome = store.commit(
        draftId,
        approveStale = approveStale,
        actor = "user",
        expectedCurrentRevision = expectedCurrentRevision,
        expectedDraftVersion = expectedDraftVersion
    )

    override fun discard(draftId: Long): Boolean = store.discard(draftId)

    override fun undo(draftId: Long): Boolean = store.undoDiscard(draftId)

    override fun pendingCount(): Int = store.pendingCount()

    override fun stats(sinceMillis: Long): PowerStore.DeckStats = store.stats(sinceMillis)

    override fun isAiLocked(): Boolean = store.isLocked()

    override fun restoreDraft(draftId: Long, form: DraftForm): PowerStore.CommitOutcome =
        store.restoreDraft(draftId, form, actor = "user")

    override fun restoreDraft(
        draftId: Long,
        form: DraftForm,
        expectedTombstoneRevision: Long?
    ): PowerStore.CommitOutcome = store.restoreDraft(
        draftId, form, actor = "user", expectedTombstoneRevision = expectedTombstoneRevision
    )

    override fun restoreDraft(
        draftId: Long,
        form: DraftForm,
        expectedTombstoneRevision: Long?,
        expectedDraftVersion: Long?
    ): PowerStore.CommitOutcome = store.restoreDraft(
        draftId, form, actor = "user", expectedTombstoneRevision = expectedTombstoneRevision,
        expectedDraftVersion = expectedDraftVersion
    )

    override fun proposeRestore(eventSeq: Long): DraftCard =
        store.proposeRestoreDraft(eventSeq, actor = "user")

    override fun record(eventSeq: Long): TemporalRecord? = store.get(eventSeq)

    override fun proposeFormalReplacement(
        eventSeq: Long,
        expectedRevision: Long
    ): DeckProposalResult = synchronized(store) {
        if (store.isLocked()) return@synchronized DeckProposalResult.Locked
        val current = store.get(eventSeq) ?: return@synchronized DeckProposalResult.Missing
        if (current.revision != expectedRevision) {
            return@synchronized DeckProposalResult.Stale(current.revision)
        }
        val existing = store.pendingDraftsFor(eventSeq).firstOrNull {
            it.operation == DraftOperation.REPLACE || it.operation == DraftOperation.CONVERT
        }
        val card = existing ?: runCatching { store.proposeReplace(eventSeq) }.getOrElse { error ->
            return@synchronized if (store.isLocked()) DeckProposalResult.Locked
            else DeckProposalResult.Failed(error.message ?: "无法建立正式替换提案")
        }
        if (card.operation == DraftOperation.REPLACE || card.operation == DraftOperation.CONVERT) {
            DeckProposalResult.Proposed(card)
        } else {
            DeckProposalResult.Conflict
        }
    }

    override fun proposeRestoreAtRevision(
        eventSeq: Long,
        expectedRevision: Long
    ): DeckProposalResult = synchronized(store) {
        if (store.isLocked()) return@synchronized DeckProposalResult.Locked
        val retired = store.getIncludingRetired(eventSeq)
            ?: return@synchronized DeckProposalResult.Missing
        if (retired.revision != expectedRevision || !store.isRetired(eventSeq)) {
            return@synchronized DeckProposalResult.Stale(retired.revision)
        }
        val card = runCatching { store.proposeRestoreDraft(eventSeq) }.getOrElse { error ->
            return@synchronized if (store.isLocked()) DeckProposalResult.Locked
            else DeckProposalResult.Failed(error.message ?: "无法建立恢复提案")
        }
        DeckProposalResult.Proposed(card)
    }
}
