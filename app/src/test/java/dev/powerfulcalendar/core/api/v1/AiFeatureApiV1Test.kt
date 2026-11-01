package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1
    private lateinit var model: FakeAiModelPort
    private val clock = MutableClock(Instant.parse("2026-11-01T00:00:00Z"))
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "ai-feature-api-${UUID.randomUUID()}.db")
        model = FakeAiModelPort()
        core = PowerCalCoreFeatureApiV1(store, clock, aiModelPort = model)
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test fun proposalGenerationUsesBoundedApiContextAndReturnsReviewOnly() = runBlocking {
        val target = createFormal("context target")
        val task = task()
        model.proposalOutput = AiProposalModelOutputV1(
            review = AiProposalReviewV1(
                reviewId = "model-controlled-id",
                items = listOf(AiProposalItemV1(
                    sourceIndex = 0,
                    operation = DraftOperationV1.REPLACE,
                    eventSeq = target.eventSeq,
                    expectedRevision = 999L,
                    form = form("proposed replacement"),
                    rationale = "candidate only"
                )),
                task = AiProposalTaskContextV1("model-forged-task", 99, 1, 1)
            ),
            contextUsed = true
        )

        val receipt = core.ai.generateProposal(AiProposalGenerationRequestV1(
            requestId = "generation-1",
            task = task,
            zoneId = zone.id,
            shareCalendarContext = true
        ))

        assertEquals(AiProposalGenerationStatusV1.READY, receipt.status)
        assertEquals(1, receipt.sharedEventCount)
        assertEquals(listOf(target.eventSeq), model.lastContext.map { it.eventSeq })
        assertEquals(target.revision, receipt.review?.items?.single()?.expectedRevision)
        assertEquals("generation-1", receipt.review?.reviewId)
        assertEquals("task-1", receipt.review?.task?.taskId)
        assertEquals("context target", store.get(target.eventSeq)?.title)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun generationWithoutContextCannotAttachTargetRevision() = runBlocking {
        val target = createFormal("private context")
        model.proposalOutput = AiProposalModelOutputV1(
            review = AiProposalReviewV1(
                reviewId = "untrusted",
                items = listOf(AiProposalItemV1(
                    sourceIndex = 0,
                    operation = DraftOperationV1.REPLACE,
                    eventSeq = target.eventSeq,
                    expectedRevision = target.revision,
                    form = form("must remain unbound")
                ))
            ),
            contextUsed = true
        )

        val receipt = core.ai.generateProposal(AiProposalGenerationRequestV1(
            requestId = "generation-private",
            userText = "提议修改一个事项",
            zoneId = zone.id,
            shareCalendarContext = false
        ))

        assertTrue(model.lastContext.isEmpty())
        assertEquals(0, receipt.sharedEventCount)
        assertNull(receipt.review?.items?.single()?.expectedRevision)
        assertEquals("private context", store.get(target.eventSeq)?.title)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun batchSelectionResolvesModelIdsAgainstTheExactCandidateSnapshot() = runBlocking {
        val first = candidate(1, 2, "first")
        val second = candidate(2, 5, "second", DraftOperationV1.DELETE)
        model.selection = AiBatchModelSelectionV1(selectedDraftIds = listOf(2), reply = "选中第二项")

        val selected = core.ai.selectBatchDrafts(AiBatchSelectionRequestV1(
            userRequest = "只处理第二项",
            candidates = listOf(first, second)
        ))

        assertEquals(AiBatchSelectionStatusV1.READY, selected.status)
        assertEquals(listOf(second), selected.selected)
        assertTrue(store.pendingDrafts().isEmpty())

        model.selection = AiBatchModelSelectionV1(selectedDraftIds = listOf(987654))
        val outOfScope = core.ai.selectBatchDrafts(AiBatchSelectionRequestV1(
            userRequest = "选一个",
            candidates = listOf(first, second)
        ))
        assertEquals(AiBatchSelectionStatusV1.INVALID, outOfScope.status)
        assertTrue(outOfScope.selected.isEmpty())
    }

    @Test fun batchApprovalBindsExactReviewedVersionAndModeAndCanBeUsedOnce() = runBlocking {
        val draft = store.newDraft(form("approved batch").toCoreDraftForm())
        val review = AiBatchReviewV1(
            mode = AiBatchModeV1.PUBLISH,
            items = listOf(candidate(
                draftId = draft.draftId,
                version = draft.draftVersion,
                title = draft.form.title,
                operation = DraftOperationV1.CREATE
            ))
        )
        val approval = requireNotNull(core.issueAiBatchApproval(review))
        val progress = mutableListOf<AiBatchProgressV1>()

        val receipt = core.ai.runReviewedBatch(review, approval, progress::add)

        assertEquals(AiBatchExecutionStatusV1.COMPLETED, receipt.status)
        assertEquals(1, receipt.published)
        assertEquals(0, receipt.discarded)
        assertEquals(listOf(0, 1), progress.map { it.completed })
        assertTrue(store.pendingDrafts().isEmpty())
        assertEquals("approved batch", store.all().single().title)
        assertEquals(AiBatchStateV1.COMPLETED, core.ai.queryBatchState().session?.state)

        val replay = core.ai.runReviewedBatch(review, approval)
        assertEquals(AiBatchExecutionStatusV1.START_REJECTED, replay.status)
        assertEquals(1, store.all().size)
    }

    @Test fun approvalCannotBeReusedForChangedReviewAndStaleVersionIsRejectedByStore() = runBlocking {
        val draft = store.newDraft(form("before review").toCoreDraftForm())
        val review = AiBatchReviewV1(
            mode = AiBatchModeV1.PUBLISH,
            items = listOf(candidate(draft.draftId, draft.draftVersion, "before review"))
        )
        val approval = requireNotNull(core.issueAiBatchApproval(review))
        val altered = review.copy(mode = AiBatchModeV1.DISCARD)

        val mismatched = core.ai.runReviewedBatch(altered, approval)
        assertEquals(AiBatchExecutionStatusV1.START_REJECTED, mismatched.status)
        val consumed = core.ai.runReviewedBatch(review, approval)
        assertEquals(AiBatchExecutionStatusV1.START_REJECTED, consumed.status)
        assertEquals(1, store.pendingDrafts().size)
        assertTrue(store.all().isEmpty())

        val staleApproval = requireNotNull(core.issueAiBatchApproval(review))
        assertTrue(store.stageDraft(
            draft.draftId,
            form("changed after review").toCoreDraftForm(),
            draft.draftVersion
        ) is PowerStore.StageResult.Saved)
        val stale = core.ai.runReviewedBatch(review, staleApproval)
        assertEquals(AiBatchExecutionStatusV1.START_REJECTED, stale.status)
        assertEquals("changed after review", store.pendingDrafts().single().form.title)
        assertTrue(store.all().isEmpty())
    }

    @Test fun stuckBatchReleaseRequiresOneUseHostApprovalAndReturnsSanitizedUnknownOutcome() {
        val draft = store.newDraft(form("stuck batch").toCoreDraftForm())
        val session = store.beginAiBatch(
            mapOf(draft.draftId to draft.draftVersion),
            setOf(DraftOperation.CREATE)
        )
        assertEquals(
            AiBatchCommandStatusV1.CANCEL_REQUESTED,
            core.ai.requestBatchCancellation(session.sessionId).status
        )
        val cancellation = requireNotNull(store.currentAiSession())
        val fakeApproval = object : AiBatchStuckReleaseApprovalV1 {}
        assertEquals(
            AiBatchCommandStatusV1.NOT_AUTHORIZED,
            core.ai.releaseStuckBatch(session.sessionId, session.lockEpoch, fakeApproval).status
        )
        assertNull(core.issueAiBatchStuckReleaseApproval(session.sessionId, session.lockEpoch))

        clock.set(Instant.ofEpochMilli(
            cancellation.updatedAtMillis + PowerStore.DEFAULT_STALE_MS
        ))
        val approval = requireNotNull(
            core.issueAiBatchStuckReleaseApproval(session.sessionId, session.lockEpoch)
        )
        assertEquals(
            AiBatchCommandStatusV1.RELEASED,
            core.ai.releaseStuckBatch(session.sessionId, session.lockEpoch, approval).status
        )
        assertEquals(
            AiBatchCommandStatusV1.NOT_AUTHORIZED,
            core.ai.releaseStuckBatch(session.sessionId, session.lockEpoch, approval).status
        )

        val terminal = requireNotNull(core.ai.queryBatchState().session?.terminalReport)
        assertFalse(terminal.outcomeKnown)
        assertTrue(terminal.releasedByUser)
        assertNull(terminal.published)
        assertFalse(core.ai.queryBatchState().locked)
    }

    private fun createFormal(title: String) = store.newDraft(form(title).toCoreDraftForm()).let { draft ->
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        requireNotNull(store.get(draft.eventSeq))
    }

    private fun form(title: String) = DraftForm(
        kind = TemporalKind.ACTIVITY,
        title = title,
        startText = "2026-11-02 10:00",
        endText = "2026-11-02 11:00",
        zoneId = zone.id
    ).toEventFormV1()

    private fun candidate(
        draftId: Long,
        version: Long,
        title: String,
        operation: DraftOperationV1 = DraftOperationV1.CREATE
    ) = AiBatchCandidateV1(
        draftId = draftId,
        draftVersion = version,
        operation = operation,
        kind = EventKindV1.ACTIVITY,
        title = title
    )

    private fun task() = AiConversationTaskV1(
        taskId = "task-1",
        revision = 1,
        anchorMillis = clock.millis() - 1_000,
        zoneId = zone.id,
        userEvidence = listOf(AiTaskEvidenceV1(
            messageId = "task-1",
            text = "明天十点开会",
            createdAtMillis = clock.millis() - 1_000,
            zoneId = zone.id
        )),
        assistantSuggestions = emptyList()
    )

    private class FakeAiModelPort : AiModelPortV1 {
        var proposalOutput = AiProposalModelOutputV1(AiProposalReviewV1("unused", emptyList()), false)
        var selection = AiBatchModelSelectionV1(error = "未配置假响应")
        var lastContext: List<EventRecordV1> = emptyList()

        override suspend fun generateProposal(
            request: AiProposalGenerationRequestV1,
            calendarContext: List<EventRecordV1>
        ): AiProposalModelOutputV1 {
            lastContext = calendarContext.toList()
            return proposalOutput
        }

        override suspend fun selectBatchDrafts(
            userRequest: String,
            candidates: List<AiBatchCandidateV1>
        ) = selection
    }

    private class MutableClock(private var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
        fun set(value: Instant) { instant = value }
    }
}
