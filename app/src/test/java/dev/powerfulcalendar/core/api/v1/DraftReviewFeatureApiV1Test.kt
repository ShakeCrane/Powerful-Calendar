package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DeckPersistence
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftReviewFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "draft-review-api-${UUID.randomUUID()}.db")
        core = PowerCalCoreFeatureApiV1(store, clock)
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test fun createDraftUsesReceiptReplayAndPublishesOneDraftChange() = runBlocking {
        val api = core.draftReview
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = core.events.subscribe(changes::add)
        val form = newActivityDraft("Created through Draft API").toEventFormV1().copy(
            completedKeys = linkedSetOf("2026-11-03T09:00", "2026-11-02T09:00"),
            recurrence = EventRecurrenceV1(
                frequency = EventRecurrenceFrequencyV1.WEEKLY,
                byDay = linkedSetOf(3, 1),
                exceptions = linkedSetOf("2026-11-03T09:00", "2026-11-02T09:00"),
                overrides = linkedMapOf(
                    "2026-11-03T09:00" to EventOccurrenceOverrideV1(1L, null),
                    "2026-11-02T09:00" to EventOccurrenceOverrideV1(2L, null)
                )
            )
        )
        val reorderedForm = form.copy(
            completedKeys = form.completedKeys.reversed().toSet(),
            recurrence = form.recurrence.copy(
                byDay = form.recurrence.byDay.reversed().toSet(),
                exceptions = form.recurrence.exceptions.reversed().toSet(),
                overrides = form.recurrence.overrides.entries.reversed()
                    .associate { it.key to it.value }
            )
        )
        val beforeRevision = core.events.featureRevision
        try {
            val created = api.createDraft(form, "draft-create-api-1")
            assertEquals(DraftReviewReceiptStatusV1.APPLIED, created.status)
            val draftId = requireNotNull(created.draftId)
            val eventSeq = requireNotNull(created.eventSeq)
            assertEquals(beforeRevision + 1, created.featureRevision)
            assertEquals("Created through Draft API", store.draft(draftId)?.form?.title)
            assertTrue(store.get(eventSeq) == null)
            assertEquals(1, store.pendingDrafts().size)
            assertEquals(listOf(EventFeatureChangeTypeV1.DRAFTS_CHANGED), changes.map { it.type })

            val replay = api.createDraft(reorderedForm, "draft-create-api-1")
            assertTrue(replay.duplicate)
            assertEquals(draftId, replay.draftId)
            assertEquals(beforeRevision + 1, core.events.featureRevision)
            assertEquals(1, store.pendingDrafts().size)
            assertEquals(1, changes.size)

            val reused = api.createDraft(form.copy(title = "Different draft"), "draft-create-api-1")
            assertEquals(DraftReviewReceiptStatusV1.DUPLICATE_REQUEST_ID, reused.status)
            assertEquals(1, store.pendingDrafts().size)

            val invalidScope = api.createDraft(
                form.copy(recurrenceScope = EventRecurrenceScopeV1.WHOLE_SERIES),
                "draft-create-invalid-scope"
            )
            assertEquals(DraftReviewReceiptStatusV1.INVALID, invalidScope.status)
            assertEquals(1, store.pendingDrafts().size)
            assertEquals(1, changes.size)

            val opened = api.open(draftId)
            assertEquals(draftId, opened.currentId)
            assertEquals(1, changes.size)
            assertEquals(beforeRevision + 1, core.events.featureRevision)
        } finally {
            subscription.close()
        }
    }

    @Test fun createDraftRespectsTheExistingAiBatchLock() = runBlocking {
        val existing = store.newDraft(newActivityDraft("Reviewed by AI"))
        val session = store.beginAiBatch(
            mapOf(existing.draftId to existing.draftVersion), setOf(DraftOperation.CREATE)
        )
        try {
            val result = core.draftReview.createDraft(
                newActivityDraft("Must stay out of the authorized batch").toEventFormV1(),
                "draft-create-during-ai"
            )
            assertEquals(DraftReviewReceiptStatusV1.LOCKED, result.status)
            assertEquals(1, store.pendingDrafts().size)
        } finally {
            if (store.isLocked()) assertTrue(store.finishAiBatch(session.sessionId))
        }
    }

    @Test fun queryPendingDraftsPreservesStoreOrderAndLeavesDirtyEditorUntouched() = runBlocking {
        val oldest = store.newDraft(newActivityDraft("Oldest persisted"))
        val edited = store.newDraft(newActivityDraft("Persisted before edit"))
        val discarded = store.newDraft(newActivityDraft("Discarded"))
        assertTrue(store.discard(discarded.draftId))

        val api = core.draftReview
        val opened = api.open(edited.draftId)
        val current = requireNotNull(opened.current)
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            edited.draftId,
            current.draftVersion,
            opened.stateRevision,
            current.form.copy(title = "Unsaved editor value")
        ).status)
        val editorBeforeQuery = api.snapshot
        val revisionBeforeQuery = core.events.featureRevision
        val draftCountBeforeQuery = store.draftCount()
        val commitCountBeforeQuery = store.commitCount()
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = core.events.subscribe(changes::add)

        try {
            val queue = api.queryPendingDrafts()

            assertEquals(listOf(oldest.draftId, edited.draftId), queue.map { it.draftId })
            assertEquals(listOf(oldest.queueOrder, edited.queueOrder), queue.map { it.queueOrder })
            assertEquals(
                listOf(DraftStatusV1.PENDING, DraftStatusV1.PENDING),
                queue.map { it.status }
            )
            assertEquals("Persisted before edit", queue.last().form.title)
            assertEquals("Unsaved editor value", api.snapshot.form?.title)
            assertEquals(editorBeforeQuery, api.snapshot)
            assertEquals("Persisted before edit", store.draft(edited.draftId)?.form?.title)
            assertEquals(draftCountBeforeQuery, store.draftCount())
            assertEquals(commitCountBeforeQuery, store.commitCount())
            assertEquals(revisionBeforeQuery, core.events.featureRevision)
            assertTrue(changes.isEmpty())
        } finally {
            subscription.close()
        }
    }

    @Test fun createDraftFingerprintSeparatesDelimiterContainingFieldValues() = runBlocking {
        val api = core.draftReview
        val base = newActivityDraft("Base").toEventFormV1()
        val firstForm = base.copy(title = "x, startText=y", startText = "")
        val changedForm = base.copy(title = "x", startText = "y, startText=")
        assertEquals(firstForm.toString(), changedForm.toString())

        val created = api.createDraft(firstForm, "draft-create-collision")
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, created.status)
        val reused = api.createDraft(changedForm, "draft-create-collision")
        assertEquals(DraftReviewReceiptStatusV1.DUPLICATE_REQUEST_ID, reused.status)
        assertEquals(1, store.pendingDrafts().size)
    }

    @Test fun editsRequireTheCurrentDraftVersionAndFlushPublishesOneChange() = runBlocking {
        val card = store.newDraft(newActivityDraft("Before API edit"))
        val api = core.draftReview
        api.open(card.draftId)
        val initial = api.snapshot
        val current = requireNotNull(initial.current)
        val beforeRevision = core.events.featureRevision
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = core.events.subscribe(changes::add)
        try {
            val stale = api.editCurrentDraft(
                card.draftId, current.draftVersion + 1, initial.stateRevision,
                current.form.copy(title = "Stale edit")
            )
            assertEquals(DraftReviewReceiptStatusV1.CONFLICT, stale.status)
            assertEquals("Before API edit", store.draft(card.draftId)?.form?.title)

            val edited = api.editCurrentDraft(
                card.draftId, current.draftVersion, initial.stateRevision,
                current.form.copy(title = "Through Draft API")
            )
            assertEquals(DraftReviewReceiptStatusV1.APPLIED, edited.status)
            assertEquals("Before API edit", store.draft(card.draftId)?.form?.title)
            val delayed = api.editCurrentDraft(
                card.draftId, current.draftVersion, initial.stateRevision,
                current.form.copy(title = "Delayed older edit")
            )
            assertEquals(DraftReviewReceiptStatusV1.CONFLICT, delayed.status)
            assertEquals("Through Draft API", api.snapshot.form?.title)

            val flushed = api.flush("draft-flush-1")
            assertEquals(DraftReviewReceiptStatusV1.APPLIED, flushed.status)
            assertEquals("Through Draft API", store.draft(card.draftId)?.form?.title)
            assertEquals(current.draftVersion + 1, api.snapshot.current?.draftVersion)
            assertEquals(current.draftVersion + 1, api.snapshot.cards.single().draftVersion)
            assertEquals(beforeRevision + 1, core.events.featureRevision)
            assertEquals(listOf(EventFeatureChangeTypeV1.DRAFTS_CHANGED), changes.map { it.type })

            val staleAfterFlush = api.editCurrentDraft(
                card.draftId, current.draftVersion, initial.stateRevision,
                current.form.copy(title = "Old version")
            )
            assertEquals(DraftReviewReceiptStatusV1.CONFLICT, staleAfterFlush.status)
        } finally {
            subscription.close()
        }
    }

    @Test fun openingAnotherCardFlushesDirtyInputBeforeChangingSelection() = runBlocking {
        val first = store.newDraft(newActivityDraft("First card"))
        val second = store.newDraft(newActivityDraft("Second card"))
        val api = core.draftReview
        api.open(first.draftId)
        val beforeEdit = api.snapshot
        val firstCard = requireNotNull(beforeEdit.current)
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            first.draftId, firstCard.draftVersion, beforeEdit.stateRevision,
            firstCard.form.copy(title = "Saved before navigation")
        ).status)

        val opened = api.open(second.draftId)
        assertEquals(second.draftId, opened.currentId)
        assertEquals("Saved before navigation", store.draft(first.draftId)?.form?.title)
    }

    @Test fun replacementProposalChecksFormalRevisionAndCommitReplayIsIdempotent() = runBlocking {
        val original = createFormalEvent("Formal original")
        val api = core.draftReview

        val stale = api.beginFormalReplacement(original.eventSeq, original.revision + 1, "replace-stale")
        assertEquals(DraftReviewReceiptStatusV1.CONFLICT, stale.status)
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())

        val proposal = api.beginFormalReplacement(original.eventSeq, original.revision, "replace-open")
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, proposal.status)
        val draftId = requireNotNull(proposal.draftId)
        val stateRevision = api.snapshot.stateRevision
        val proposed = requireNotNull(api.snapshot.current)
        assertEquals(original.revision, proposed.baseRevision)

        assertEquals(DraftReviewReceiptStatusV1.APPLIED,
            api.editCurrentDraft(draftId, proposed.draftVersion, stateRevision,
                proposed.form.copy(title = "Formal replacement" )).status)
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.flush("replace-flush" ).status)
        assertEquals("Formal original", store.get(original.eventSeq)?.title)

        val version = requireNotNull(api.snapshot.current).draftVersion
        val reviewedStateRevision = api.snapshot.stateRevision
        val committed = api.commit(draftId, version, reviewedStateRevision, "replace-commit")
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, committed.status)
        assertEquals(original.revision + 1, committed.revision)
        assertEquals("Formal replacement", store.get(original.eventSeq)?.title)
        val commitCount = store.commitCount()

        val replay = api.commit(draftId, version, reviewedStateRevision, "replace-commit")
        assertTrue(replay.duplicate)
        assertEquals(commitCount, store.commitCount())
        val reusedId = api.commit(draftId, version + 1, reviewedStateRevision, "replace-commit")
        assertEquals(DraftReviewReceiptStatusV1.DUPLICATE_REQUEST_ID, reusedId.status)
        assertEquals(commitCount, store.commitCount())
    }

    @Test fun staleFormalRevisionRequiresSeparateConflictApproval() = runBlocking {
        val original = createFormalEvent("Original")
        val draft = store.newDraft(
            newActivityDraft("Original"), original.eventSeq, DraftOperation.REPLACE,
            expectedRevision = original.revision
        )
        val simulatedCurrent = original.copy(
            revision = original.revision + 1, title = "Concurrent update"
        )
        var rejectedAsStale = false
        var approvedAtRevision: Long? = null
        val persistence = object : DeckPersistence by StoreDeckPersistence(store) {
            override fun record(eventSeq: Long): TemporalRecord? = simulatedCurrent

            override fun commit(
                draftId: Long,
                approveStale: Boolean,
                expectedCurrentRevision: Long?
            ): PowerStore.CommitOutcome {
                if (!approveStale) {
                    rejectedAsStale = true
                    return PowerStore.CommitOutcome.Conflict(simulatedCurrent)
                }
                approvedAtRevision = expectedCurrentRevision
                return PowerStore.CommitOutcome.Committed(
                    original.eventSeq, (expectedCurrentRevision ?: 0L) + 1, "approved-conflict"
                )
            }

            override fun commit(
                draftId: Long,
                approveStale: Boolean,
                expectedCurrentRevision: Long?,
                expectedDraftVersion: Long?
            ): PowerStore.CommitOutcome = commit(draftId, approveStale, expectedCurrentRevision)
        }
        val conflictCore = PowerCalCoreFeatureApiV1(
            store, clock, DeckController(persistence,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val api = conflictCore.draftReview
        api.open(draft.draftId)
        val stateRevision = api.snapshot.stateRevision
        val card = requireNotNull(api.snapshot.current)
        val draftId = card.draftId
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            draftId, card.draftVersion, stateRevision, card.form.copy(title = "Reviewed candidate")
        ).status)
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.flush("conflict-flush").status)

        val currentCard = requireNotNull(api.snapshot.current)
        val rejected = api.commit(draftId, currentCard.draftVersion,
            api.snapshot.stateRevision, "conflict-first")
        assertEquals(DraftReviewReceiptStatusV1.REVIEW_REQUIRED, rejected.status)
        assertTrue(rejectedAsStale)
        assertEquals("Original", store.get(original.eventSeq)?.title)
        val prompt = requireNotNull(api.snapshot.conflict)
        assertEquals(simulatedCurrent.revision, prompt.currentRevision)
        assertEquals("Reviewed candidate", prompt.approvedForm.title)
        val conflictStateRevision = api.snapshot.stateRevision

        val approved = api.confirmConflictReplacement(
            draftId, prompt.card.draftVersion, conflictStateRevision,
            simulatedCurrent.revision, "conflict-approved"
        )
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, approved.status)
        assertEquals(simulatedCurrent.revision, approvedAtRevision)
        val commitCount = store.commitCount()
        assertTrue(api.confirmConflictReplacement(
            draftId, prompt.card.draftVersion, conflictStateRevision,
            simulatedCurrent.revision, "conflict-approved"
        ).duplicate)
        assertEquals(commitCount, store.commitCount())
    }

    @Test fun commitRejectsPersistedDraftVersionChangedAfterReview() = runBlocking {
        val card = store.newDraft(newActivityDraft("Before concurrent checkpoint"))
        val api = core.draftReview
        api.open(card.draftId)
        val reviewed = api.snapshot
        val reviewedCard = requireNotNull(reviewed.current)

        val concurrent = store.stageDraft(
            card.draftId,
            reviewedCard.form.copy(title = "Concurrent checkpoint" ).toCoreDraftForm(),
            reviewedCard.draftVersion
        )
        assertTrue(concurrent is PowerStore.StageResult.Saved)

        val receipt = api.commit(
            card.draftId, reviewedCard.draftVersion, reviewed.stateRevision, "stale-reviewed-commit"
        )
        assertEquals(DraftReviewReceiptStatusV1.CONFLICT, receipt.status)
        assertEquals("DRAFT_REVIEW_STALE", receipt.errorCode)
        assertEquals("Concurrent checkpoint", store.draft(card.draftId)?.form?.title)
        assertEquals(0, store.commitCount())
        assertEquals(1, store.pendingDrafts().size)
    }

    @Test fun dirtyReviewedCommitDoesNotOverwriteConcurrentDraftCheckpoint() = runBlocking {
        val card = store.newDraft(newActivityDraft("Initial draft"))
        val api = core.draftReview
        api.open(card.draftId)
        val initial = api.snapshot
        val current = requireNotNull(initial.current)
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            card.draftId, current.draftVersion, initial.stateRevision,
            current.form.copy(title = "Locally reviewed form")
        ).status)

        val concurrent = store.stageDraft(
            card.draftId,
            current.form.copy(title = "Concurrent writer form").toCoreDraftForm(),
            current.draftVersion
        )
        assertTrue(concurrent is PowerStore.StageResult.Saved)
        val reviewed = api.snapshot
        val receipt = api.commit(
            card.draftId, current.draftVersion, reviewed.stateRevision, "dirty-stale-reviewed-commit"
        )

        assertEquals(DraftReviewReceiptStatusV1.CONFLICT, receipt.status)
        assertEquals("DRAFT_REVIEW_STALE", receipt.errorCode)
        assertEquals("Concurrent writer form", store.draft(card.draftId)?.form?.title)
        assertEquals("Locally reviewed form", api.snapshot.form?.title)
        assertTrue(api.snapshot.dirty)
        assertEquals(0, store.commitCount())
    }

    @Test fun dirtyReviewedCommitPersistsAndPublishesItsExactCandidate() = runBlocking {
        val original = createFormalEvent("Original")
        val proposal = core.draftReview.beginFormalReplacement(
            original.eventSeq, original.revision, "dirty-replacement-proposal"
        )
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, proposal.status)
        val api = core.draftReview
        val beforeEdit = api.snapshot
        val card = requireNotNull(beforeEdit.current)
        val candidate = card.form.copy(title = "Dirty but reviewed replacement")
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            card.draftId, card.draftVersion, beforeEdit.stateRevision, candidate
        ).status)

        val reviewed = api.snapshot
        val receipt = api.commit(
            card.draftId, card.draftVersion, reviewed.stateRevision, "dirty-reviewed-commit"
        )

        assertEquals(DraftReviewReceiptStatusV1.APPLIED, receipt.status)
        assertEquals("Dirty but reviewed replacement", store.get(original.eventSeq)?.title)
        assertEquals("Dirty but reviewed replacement", store.draft(card.draftId)?.form?.title)
        assertFalse(api.snapshot.dirty)
    }

    @Test fun dirtyRestoreApprovalDoesNotOverwriteConcurrentDraftCheckpoint() = runBlocking {
        val original = createFormalEvent("Restore target")
        val deleteCard = store.newDraft(
            DraftForm.from(original), original.eventSeq, DraftOperation.DELETE,
            expectedRevision = original.revision
        )
        val api = core.draftReview
        api.open(deleteCard.draftId)
        assertEquals(DraftReviewReceiptStatusV1.APPLIED,
            api.commit(deleteCard.draftId, deleteCard.draftVersion,
                api.snapshot.stateRevision, "restore-race-delete").status)
        val tombstone = requireNotNull(store.getIncludingRetired(original.eventSeq))
        val proposal = api.beginRestore(original.eventSeq, tombstone.revision, "restore-race-proposal")
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, proposal.status)
        val restoreCard = requireNotNull(api.snapshot.current)
        assertEquals(DraftReviewReceiptStatusV1.RESTORE_REQUIRED,
            api.commit(restoreCard.draftId, restoreCard.draftVersion,
                api.snapshot.stateRevision, "restore-race-review").status)

        val beforeEdit = api.snapshot
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            restoreCard.draftId, restoreCard.draftVersion, beforeEdit.stateRevision,
            restoreCard.form.copy(title = "Locally reviewed restore")
        ).status)
        val concurrent = store.stageDraft(
            restoreCard.draftId,
            restoreCard.form.copy(title = "Concurrent restore checkpoint").toCoreDraftForm(),
            restoreCard.draftVersion
        )
        assertTrue(concurrent is PowerStore.StageResult.Saved)
        val reviewed = api.snapshot
        val commitCount = store.commitCount()

        val receipt = api.requestRestore(
            restoreCard.draftId, restoreCard.draftVersion, reviewed.stateRevision,
            tombstone.revision, "dirty-stale-restore"
        )

        assertEquals(DraftReviewReceiptStatusV1.CONFLICT, receipt.status)
        assertEquals("DRAFT_REVIEW_STALE", receipt.errorCode)
        assertEquals("Concurrent restore checkpoint", store.draft(restoreCard.draftId)?.form?.title)
        assertTrue(store.isRetired(original.eventSeq))
        assertEquals(commitCount, store.commitCount())
    }

    @Test fun commitRejectsInMemoryDraftStateChangedAfterReview() = runBlocking {
        val card = store.newDraft(newActivityDraft("Reviewed title"))
        val api = core.draftReview
        api.open(card.draftId)
        val reviewed = api.snapshot
        val reviewedCard = requireNotNull(reviewed.current)

        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            card.draftId, reviewedCard.draftVersion, reviewed.stateRevision,
            reviewedCard.form.copy(title = "New unsaved title")
        ).status)
        val staleApproval = api.commit(
            card.draftId, reviewedCard.draftVersion, reviewed.stateRevision, "stale-state-commit"
        )

        assertEquals(DraftReviewReceiptStatusV1.CONFLICT, staleApproval.status)
        assertEquals("DRAFT_STATE_STALE", staleApproval.errorCode)
        assertEquals("Reviewed title", store.draft(card.draftId)?.form?.title)
        assertEquals("New unsaved title", api.snapshot.form?.title)
        assertEquals(0, store.commitCount())
    }

    @Test fun deleteThenRestoreApproval() = runBlocking {
        val original = createFormalEvent("Delete then restore")
        val deleteCard = store.newDraft(
            DraftForm.from(original), original.eventSeq, DraftOperation.DELETE,
            expectedRevision = original.revision
        )
        val api = core.draftReview
        api.open(deleteCard.draftId)
        val deleteStateRevision = api.snapshot.stateRevision
        assertEquals(DraftReviewReceiptStatusV1.APPLIED,
            api.commit(deleteCard.draftId, deleteCard.draftVersion, deleteStateRevision, "delete-card").status)
        assertTrue(store.isRetired(original.eventSeq))
        val tombstone = requireNotNull(store.getIncludingRetired(original.eventSeq))
        assertEquals(original.revision + 1, tombstone.revision)

        val restoreProposal = api.beginRestore(original.eventSeq, tombstone.revision, "restore-open")
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, restoreProposal.status)
        val restoreCard = requireNotNull(api.snapshot.current)
        assertEquals(tombstone.revision, restoreCard.baseRevision)
        assertEquals(DraftReviewReceiptStatusV1.RESTORE_REQUIRED,
            api.commit(restoreCard.draftId, restoreCard.draftVersion,
                api.snapshot.stateRevision, "restore-review").status)
        assertTrue(store.isRetired(original.eventSeq))

        val beforeRestoreEdit = api.snapshot
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            restoreCard.draftId, restoreCard.draftVersion, beforeRestoreEdit.stateRevision,
            restoreCard.form.copy(title = "Reviewed restore form")
        ).status)
        val restoreStateRevision = api.snapshot.stateRevision
        val restored = api.requestRestore(
            restoreCard.draftId, restoreCard.draftVersion, restoreStateRevision,
            tombstone.revision, "restore-approved"
        )
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, restored.status)
        assertFalse(store.isRetired(original.eventSeq))
        assertEquals(tombstone.revision + 1, store.get(original.eventSeq)?.revision)
        assertEquals("Reviewed restore form", store.get(original.eventSeq)?.title)
        val commitCount = store.commitCount()
        assertTrue(api.requestRestore(
            restoreCard.draftId, restoreCard.draftVersion, restoreStateRevision,
            tombstone.revision, "restore-approved"
        ).duplicate)
        assertEquals(commitCount, store.commitCount())
    }

    private fun newActivityDraft(title: String) = DraftForm.fresh(
        TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 26), zone
    ).copy(title = title, startText = "2026-10-26 09:00", endText = "2026-10-26 10:00")

    private fun createFormalEvent(title: String) = run {
        val opened = core.events.openNewEvent(EventKindV1.ACTIVITY, zone.id)
        val editor = (opened as? EventEditorOpenResultV1.Opened)?.editor
            ?: error("Expected event editor, got $opened")
        val receipt = core.events.saveNewEvent(
            editor.handle,
            editor.form.copy(
                title = title, startText = "2026-10-26 09:00", endText = "2026-10-26 10:00"
            ),
            "create-${UUID.randomUUID()}"
        )
        assertEquals(EventMutationStatusV1.CREATED, receipt.status)
        requireNotNull(store.get(requireNotNull(receipt.eventSeq)))
    }

    private fun updateFormalEvent(record: dev.powerfulcalendar.core.TemporalRecord, title: String) = run {
        val opened = core.events.openFormalEvent(record.eventSeq, record.revision)
        val editor = (opened as? EventEditorOpenResultV1.Opened)?.editor
            ?: error("Expected formal editor, got $opened")
        val updatedForm = editor.form.copy(title = title)
        assertEquals(EventEditorUpdateStatusV1.UPDATED,
            core.events.updateFormalEvent(editor.handle, updatedForm).status)
        val receipt = core.events.saveFormalEvent(
            editor.handle, updatedForm, "update-${UUID.randomUUID()}"
        )
        assertEquals(EventMutationStatusV1.UPDATED, receipt.status)
        core.events.closeFormalEvent(editor.handle)
        requireNotNull(store.get(record.eventSeq))
    }
}
