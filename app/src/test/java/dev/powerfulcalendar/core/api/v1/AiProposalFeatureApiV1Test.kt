package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiDraftService
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.TemporalKind
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiProposalFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1
    private val clock = MutableClock(Instant.parse("2026-11-01T00:00:00Z"))
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "ai-proposal-api-${UUID.randomUUID()}.db")
        core = PowerCalCoreFeatureApiV1(store, clock)
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test fun explicitDraftCreationUsesApiReceiptAndOneSharedChange() {
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = core.events.subscribe(changes::add)
        try {
            val review = review(createItem("explicit draft"))
            val receipt = core.aiProposals.createDrafts(review, "explicit-create-1")
            assertEquals(AiProposalApplyStatusV1.APPLIED, receipt.status)
            assertEquals(AiProposalItemReceiptDurabilityV1.PROCESS_LOCAL,
                receipt.itemReceiptDurability)
            assertEquals(1, receipt.stagedCount)
            val draftId = requireNotNull(receipt.itemReceipts.single().draftId)
            assertEquals("explicit draft", store.draft(draftId)?.form?.title)
            assertNull(store.get(requireNotNull(receipt.itemReceipts.single().eventSeq)))
            assertEquals(listOf(EventFeatureChangeTypeV1.DRAFTS_CHANGED), changes.map { it.type })

            val replay = core.aiProposals.createDrafts(review, "explicit-create-1")
            assertTrue(replay.duplicate)
            assertEquals(draftId, replay.itemReceipts.single().draftId)
            assertFalse(replay.itemReceipts.single().changed)
            assertEquals(1, changes.size)
            val collision = core.aiProposals.createDrafts(
                review("review-two", createItem("different")), "explicit-create-1")
            assertEquals(AiProposalApplyStatusV1.DUPLICATE_REQUEST_ID, collision.status)
            assertEquals(1, store.pendingDrafts().size)
        } finally {
            subscription.close()
        }
    }

    @Test fun taskBackedStageReplaySurvivesApiRecreationAndPublishIsIdempotent() {
        val task = AiProposalTaskContextV1(
            taskId = "chat-task-1",
            revision = 1,
            anchorMillis = clock.millis() - 500L,
            latestEvidenceMillis = clock.millis() - 500L
        )
        val review = review(createItem("durable task proposal"), task = task)
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = core.events.subscribe(changes::add)
        try {
            val staged = core.aiProposals.applyReviewedProposals(request(
                "task-stage-1", review, AiProposalDecisionV1.STAGE))
            assertEquals(AiProposalApplyStatusV1.APPLIED, staged.status)
            assertEquals(AiProposalItemReceiptDurabilityV1.DURABLE_ITEM,
                staged.itemReceiptDurability)
            val draftId = requireNotNull(staged.itemReceipts.single().draftId)
            assertTrue(staged.itemReceipts.single().changed)
            assertEquals(1, store.pendingDrafts().size)
            assertEquals(listOf(EventFeatureChangeTypeV1.DRAFTS_CHANGED), changes.map { it.type })

            val recreatedCore = PowerCalCoreFeatureApiV1(store, clock)
            val replayed = recreatedCore.aiProposals.applyReviewedProposals(request(
                "task-stage-after-recreate", review, AiProposalDecisionV1.STAGE))
            assertEquals(AiProposalApplyStatusV1.APPLIED, replayed.status)
            assertEquals(draftId, replayed.itemReceipts.single().draftId)
            assertEquals(AiProposalItemStatusV1.STAGED, replayed.itemReceipts.single().status)
            assertFalse(replayed.itemReceipts.single().changed)
            assertEquals(1, store.pendingDrafts().size)
            assertEquals(1, store.identityCount())
            assertEquals(1, changes.size)

            val publishRequest = request("task-publish-1", review, AiProposalDecisionV1.PUBLISH)
            val approval = requireNotNull(recreatedCore.issueAiProposalPublishApproval(publishRequest))
            val recreatedChanges = mutableListOf<EventFeatureChangeV1>()
            val recreatedSubscription = recreatedCore.events.subscribe(recreatedChanges::add)
            try {
                val published = recreatedCore.aiProposals.applyReviewedProposals(publishRequest, approval)
                assertEquals(AiProposalApplyStatusV1.APPLIED, published.status)
                assertEquals(1, published.publishedCount)
                assertTrue(published.itemReceipts.single().changed)
                assertEquals("durable task proposal", store.all().single().title)
                assertEquals(1L, store.commitCount())
                assertEquals(listOf(EventFeatureChangeTypeV1.FORMAL_CREATED,
                    EventFeatureChangeTypeV1.DRAFTS_CHANGED), recreatedChanges.map { it.type })

                val retryRequest = request("task-publish-after-recreate", review,
                    AiProposalDecisionV1.PUBLISH)
                val retryApproval = requireNotNull(
                    recreatedCore.issueAiProposalPublishApproval(retryRequest))
                val retry = recreatedCore.aiProposals.applyReviewedProposals(retryRequest, retryApproval)
                assertEquals(AiProposalApplyStatusV1.APPLIED, retry.status)
                assertEquals(1, retry.alreadyResolvedCount)
                assertEquals(AiProposalItemStatusV1.ALREADY_RESOLVED, retry.itemReceipts.single().status)
                assertFalse(retry.itemReceipts.single().changed)
                assertEquals(1, store.all().size)
                assertEquals(1L, store.commitCount())
                assertEquals(2, recreatedChanges.size)
            } finally {
                recreatedSubscription.close()
            }
        } finally {
            subscription.close()
        }
    }

    @Test fun expiredTaskReplaysOnlyItsExactExistingPendingReceipt() {
        val task = AiProposalTaskContextV1(
            taskId = "expired-pending-task",
            revision = 1,
            anchorMillis = clock.millis() - 500L,
            latestEvidenceMillis = clock.millis() - 500L
        )
        val review = review(createItem("pending before expiry"), task = task)
        val first = core.aiProposals.applyReviewedProposals(request(
            "pending-before-expiry", review, AiProposalDecisionV1.STAGE))
        assertEquals(AiProposalApplyStatusV1.APPLIED, first.status)
        val draftId = requireNotNull(first.itemReceipts.single().draftId)
        val before = listOf(store.draftCount(), store.identityCount(), store.commitCount())

        clock.advanceMillis(25L * 60 * 60 * 1000 + 1_000L)
        val recreatedCore = PowerCalCoreFeatureApiV1(store, clock)
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = recreatedCore.events.subscribe(changes::add)
        try {
            val replay = recreatedCore.aiProposals.applyReviewedProposals(request(
                "pending-after-expiry", review, AiProposalDecisionV1.STAGE))
            assertEquals(AiProposalApplyStatusV1.APPLIED, replay.status)
            assertEquals(AiProposalItemStatusV1.STAGED, replay.itemReceipts.single().status)
            assertEquals(draftId, replay.itemReceipts.single().draftId)
            assertFalse(replay.itemReceipts.single().changed)

            val changedReview = review(createItem("changed after expiry"), task = task)
            val changed = recreatedCore.aiProposals.applyReviewedProposals(request(
                "changed-after-expiry", changedReview, AiProposalDecisionV1.STAGE))
            assertEquals(AiProposalApplyStatusV1.EXPIRED, changed.status)

            val publish = request("publish-pending-after-expiry", review,
                AiProposalDecisionV1.PUBLISH)
            assertNull(recreatedCore.issueAiProposalPublishApproval(publish))
            assertEquals(AiProposalApplyStatusV1.EXPIRED,
                recreatedCore.aiProposals.applyReviewedProposals(publish).status)

            assertEquals(before, listOf(store.draftCount(), store.identityCount(), store.commitCount()))
            assertEquals(draftId, store.pendingDrafts().single().draftId)
            assertTrue(changes.isEmpty())
        } finally {
            subscription.close()
        }
    }

    @Test fun expiredTaskReplaysItsExactTerminalPublishReceipt() {
        val task = AiProposalTaskContextV1(
            taskId = "expired-terminal-task",
            revision = 1,
            anchorMillis = clock.millis() - 500L,
            latestEvidenceMillis = clock.millis() - 500L
        )
        val review = review(createItem("published before expiry"), task = task)
        val firstRequest = request("terminal-before-expiry", review, AiProposalDecisionV1.PUBLISH)
        val firstApproval = requireNotNull(core.issueAiProposalPublishApproval(firstRequest))
        val first = core.aiProposals.applyReviewedProposals(firstRequest, firstApproval)
        assertEquals(AiProposalApplyStatusV1.APPLIED, first.status)
        val event = store.all().single()
        val before = listOf(store.draftCount(), store.identityCount(), store.commitCount())

        clock.advanceMillis(25L * 60 * 60 * 1000 + 1_000L)
        val recreatedCore = PowerCalCoreFeatureApiV1(store, clock)
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = recreatedCore.events.subscribe(changes::add)
        try {
            val retry = request("terminal-after-expiry", review, AiProposalDecisionV1.PUBLISH)
            val retryApproval = requireNotNull(recreatedCore.issueAiProposalPublishApproval(retry))
            val replay = recreatedCore.aiProposals.applyReviewedProposals(retry, retryApproval)

            assertEquals(AiProposalApplyStatusV1.APPLIED, replay.status)
            assertEquals(1, replay.alreadyResolvedCount)
            assertEquals(AiProposalItemStatusV1.ALREADY_RESOLVED,
                replay.itemReceipts.single().status)
            assertFalse(replay.itemReceipts.single().changed)
            assertEquals(event, store.all().single())
            assertEquals(before, listOf(store.draftCount(), store.identityCount(), store.commitCount()))
            assertTrue(changes.isEmpty())
        } finally {
            subscription.close()
        }
    }

    @Test fun tasklessPublishReplayAfterApiRecreationUsesDurableItemReceipt() {
        val review = review(createItem("taskless durable publish"))
        val original = request("taskless-durable-request", review, AiProposalDecisionV1.PUBLISH)
        val approval = requireNotNull(core.issueAiProposalPublishApproval(original))
        val first = core.aiProposals.applyReviewedProposals(original, approval)

        assertEquals(AiProposalApplyStatusV1.APPLIED, first.status)
        assertEquals(AiProposalItemReceiptDurabilityV1.DURABLE_ITEM,
            first.itemReceiptDurability)
        assertEquals(1, first.publishedCount)
        val event = store.all().single()
        val before = listOf(store.draftCount(), store.identityCount(), store.commitCount())

        val recreatedCore = PowerCalCoreFeatureApiV1(store, clock)
        val retry = request("taskless-durable-request", review, AiProposalDecisionV1.PUBLISH)
        val retryApproval = requireNotNull(recreatedCore.issueAiProposalPublishApproval(retry))
        val replay = recreatedCore.aiProposals.applyReviewedProposals(retry, retryApproval)

        assertEquals(AiProposalApplyStatusV1.APPLIED, replay.status)
        assertEquals(1, replay.alreadyResolvedCount)
        assertEquals(AiProposalItemStatusV1.ALREADY_RESOLVED,
            replay.itemReceipts.single().status)
        assertFalse(replay.itemReceipts.single().changed)
        assertEquals(event, store.all().single())
        assertEquals(before, listOf(store.draftCount(), store.identityCount(), store.commitCount()))

        val changedReview = review(createItem("changed payload under same request id"))
        val changedCore = PowerCalCoreFeatureApiV1(store, clock)
        val changedRequest = request("taskless-durable-request", changedReview,
            AiProposalDecisionV1.PUBLISH)
        val changedApproval = requireNotNull(changedCore.issueAiProposalPublishApproval(changedRequest))
        val changed = changedCore.aiProposals.applyReviewedProposals(changedRequest, changedApproval)

        assertEquals(AiProposalApplyStatusV1.FAILED, changed.status)
        assertEquals(AiProposalItemStatusV1.FAILED, changed.itemReceipts.single().status)
        assertEquals(event, store.all().single())
        assertEquals(before, listOf(store.draftCount(), store.identityCount(), store.commitCount()))
    }

    @Test fun tasklessReplaceAndDeleteReplayAfterApiRecreationBypassStaleTargets() {
        val replaceTarget = createFormal("taskless replace before")
        val deleteTarget = createFormal("taskless delete before")
        val replace = AiProposalItemV1(0, DraftOperationV1.REPLACE,
            replaceTarget.eventSeq, replaceTarget.revision, form("taskless replace after"))
        val delete = AiProposalItemV1(1, DraftOperationV1.DELETE,
            deleteTarget.eventSeq, deleteTarget.revision, form("taskless delete candidate"))
        val review = review("taskless-terminal-replay", replace, delete)
        val original = request("taskless-terminal-request", review, AiProposalDecisionV1.PUBLISH)
        val approval = requireNotNull(core.issueAiProposalPublishApproval(original))
        val first = core.aiProposals.applyReviewedProposals(original, approval)
        assertEquals(AiProposalApplyStatusV1.APPLIED, first.status)
        assertEquals(2, first.publishedCount)
        assertEquals("taskless replace after", store.get(replaceTarget.eventSeq)?.title)
        assertNull(store.get(deleteTarget.eventSeq))
        val before = listOf(store.draftCount(), store.identityCount(), store.commitCount())

        val recreatedCore = PowerCalCoreFeatureApiV1(store, clock)
        val retry = request("taskless-terminal-request", review, AiProposalDecisionV1.PUBLISH)
        val retryApproval = requireNotNull(recreatedCore.issueAiProposalPublishApproval(retry))
        val replay = recreatedCore.aiProposals.applyReviewedProposals(retry, retryApproval)

        assertEquals(AiProposalApplyStatusV1.APPLIED, replay.status)
        assertEquals(2, replay.alreadyResolvedCount)
        assertTrue(replay.itemReceipts.all {
            it.status == AiProposalItemStatusV1.ALREADY_RESOLVED && !it.changed
        })
        assertEquals("taskless replace after", store.get(replaceTarget.eventSeq)?.title)
        assertNull(store.get(deleteTarget.eventSeq))
        assertEquals(before, listOf(store.draftCount(), store.identityCount(), store.commitCount()))
    }

    @Test fun proposalChangeListenersRunAfterProposalMutexIsReleased() {
        val callbackStarted = AtomicBoolean(false)
        val reentryCompletedInsideCallback = AtomicBoolean(false)
        val worker = AtomicReference<Thread?>()
        val completed = CountDownLatch(1)
        val subscription = core.events.subscribe {
            if (callbackStarted.compareAndSet(false, true)) {
                worker.set(Thread {
                    core.aiProposals.createDrafts(review(createItem("listener reentry")),
                        "listener-reentry")
                    completed.countDown()
                }.also(Thread::start))
                reentryCompletedInsideCallback.set(completed.await(250, TimeUnit.MILLISECONDS))
            }
        }
        try {
            val receipt = core.aiProposals.createDrafts(
                review(createItem("outer listener action")), "outer-listener-action")
            assertEquals(AiProposalApplyStatusV1.APPLIED, receipt.status)
            worker.get()?.join(2_000)
            assertTrue(callbackStarted.get())
            assertTrue("A listener reentry must finish while its callback is active",
                reentryCompletedInsideCallback.get())
            assertTrue(worker.get()?.isAlive == false)
            assertEquals(setOf("outer listener action", "listener reentry"),
                store.pendingDrafts().map { it.form.title }.toSet())
        } finally {
            subscription.close()
            worker.get()?.join(2_000)
        }
    }

    @Test fun publishRequiresHostApprovalAndRejectsStaleReviewedTargetsAtomically() {
        val target = createFormal("target before review")
        val replace = AiProposalItemV1(
            sourceIndex = 0,
            operation = DraftOperationV1.REPLACE,
            eventSeq = target.eventSeq,
            expectedRevision = target.revision,
            form = form("reviewed replacement")
        )
        val create = createItem("independent create").copy(sourceIndex = 1)
        val review = review(replace, create)
        val unauthorized = request("stale-no-approval", review, AiProposalDecisionV1.PUBLISH)

        assertEquals(AiProposalApplyStatusV1.NOT_AUTHORIZED,
            core.aiProposals.applyReviewedProposals(unauthorized).status)
        val forged = object : AiProposalPublishApprovalV1 {}
        assertEquals(AiProposalApplyStatusV1.NOT_AUTHORIZED,
            core.aiProposals.applyReviewedProposals(
                request("stale-forged-token", review, AiProposalDecisionV1.PUBLISH), forged
            ).status)

        val request = request("stale-batch", review, AiProposalDecisionV1.PUBLISH)
        val approval = requireNotNull(core.issueAiProposalPublishApproval(request))
        val concurrent = store.newDraft(form("concurrent target update").toCoreDraftForm(), target.eventSeq,
            DraftOperation.REPLACE, expectedRevision = target.revision)
        assertTrue(store.commit(concurrent.draftId) is PowerStore.CommitOutcome.Committed)
        val result = core.aiProposals.applyReviewedProposals(request, approval)
        assertEquals(AiProposalApplyStatusV1.CONFLICT, result.status)
        assertTrue(result.handledPositions.isEmpty())
        assertEquals("concurrent target update", store.get(target.eventSeq)?.title)
        assertEquals(1, store.all().size)
        assertEquals(0, store.pendingDrafts().size)
        assertEquals(2L, store.commitCount())
    }

    @Test fun publishApprovalIsBoundToExactRequestAndExpires() {
        val review = review(createItem("approved once"))
        val first = request("publish-request-1", review, AiProposalDecisionV1.PUBLISH)
        val approval = requireNotNull(core.issueAiProposalPublishApproval(first))
        val changedRequest = request("publish-request-2", review, AiProposalDecisionV1.PUBLISH)
        assertEquals(AiProposalApplyStatusV1.NOT_AUTHORIZED,
            core.aiProposals.applyReviewedProposals(changedRequest, approval).status)
        assertTrue(store.all().isEmpty())

        val expiredRequest = request("publish-expired", review, AiProposalDecisionV1.PUBLISH)
        val expiredApproval = requireNotNull(core.issueAiProposalPublishApproval(expiredRequest))
        clock.advanceMillis(2 * 60 * 1000L + 1L)
        assertEquals(AiProposalApplyStatusV1.NOT_AUTHORIZED,
            core.aiProposals.applyReviewedProposals(expiredRequest, expiredApproval).status)
        assertTrue(store.all().isEmpty())

        val allowedRequest = request("publish-request-3", review, AiProposalDecisionV1.PUBLISH)
        val allowedApproval = requireNotNull(core.issueAiProposalPublishApproval(allowedRequest))
        val firstApply = core.aiProposals.applyReviewedProposals(allowedRequest, allowedApproval)
        assertEquals(AiProposalApplyStatusV1.APPLIED, firstApply.status)
        assertEquals(1L, store.commitCount())
        val replay = core.aiProposals.applyReviewedProposals(allowedRequest)
        assertTrue(replay.duplicate)
        assertEquals(1L, store.commitCount())
    }

    @Test fun taskReplaceAndDeleteReplayReturnStoredReceiptsAfterTargetChanges() {
        val task = AiProposalTaskContextV1(
            taskId = "terminal-task",
            revision = 1,
            anchorMillis = clock.millis() - 500L,
            latestEvidenceMillis = clock.millis() - 500L
        )
        val replaceTarget = createFormal("replace before")
        val deleteTarget = createFormal("delete before")
        val replaceItem = AiProposalItemV1(0, DraftOperationV1.REPLACE,
            replaceTarget.eventSeq, replaceTarget.revision, form("replace approved"))
        val deleteItem = AiProposalItemV1(1, DraftOperationV1.DELETE,
            deleteTarget.eventSeq, deleteTarget.revision, form("delete approved"))

        val replaceReview = review("replace-terminal", replaceItem, task = task)
        val replaceRequest = request("replace-terminal-publish", replaceReview,
            AiProposalDecisionV1.PUBLISH)
        val replaceApproval = requireNotNull(core.issueAiProposalPublishApproval(replaceRequest))
        val replaceReceipt = core.aiProposals.applyReviewedProposals(replaceRequest, replaceApproval)
        assertEquals(AiProposalApplyStatusV1.APPLIED, replaceReceipt.status)
        assertEquals("replace approved", store.get(replaceTarget.eventSeq)?.title)

        val deleteReview = review("delete-terminal", deleteItem, task = task)
        val deleteRequest = request("delete-terminal-publish", deleteReview,
            AiProposalDecisionV1.PUBLISH)
        val deleteApproval = requireNotNull(core.issueAiProposalPublishApproval(deleteRequest))
        val deleteReceipt = core.aiProposals.applyReviewedProposals(deleteRequest, deleteApproval)
        assertEquals(AiProposalApplyStatusV1.APPLIED, deleteReceipt.status)
        assertNull(store.get(deleteTarget.eventSeq))
        assertEquals(4L, store.commitCount())

        val replayCore = PowerCalCoreFeatureApiV1(store, clock)
        listOf(replaceRequest, deleteRequest).forEach { original ->
            val retry = original.copy(requestId = "retry-${original.requestId}")
            val retryApproval = requireNotNull(replayCore.issueAiProposalPublishApproval(retry))
            val receipt = replayCore.aiProposals.applyReviewedProposals(retry, retryApproval)
            assertEquals(AiProposalApplyStatusV1.APPLIED, receipt.status)
            assertEquals(1, receipt.alreadyResolvedCount)
            assertEquals(AiProposalItemStatusV1.ALREADY_RESOLVED,
                receipt.itemReceipts.single().status)
            assertFalse(receipt.itemReceipts.single().changed)
        }
        assertEquals(4L, store.commitCount())
        assertEquals("replace approved", store.get(replaceTarget.eventSeq)?.title)
        assertNull(store.get(deleteTarget.eventSeq))
    }

    @Test fun taskPublishRejectsDraftEditedAfterReview() {
        val task = AiProposalTaskContextV1(
            taskId = "edited-pending-task",
            revision = 1,
            anchorMillis = clock.millis() - 500L,
            latestEvidenceMillis = clock.millis() - 500L
        )
        val review = review(createItem("reviewed task title"), task = task)
        val staged = core.aiProposals.applyReviewedProposals(request(
            "edited-task-stage", review, AiProposalDecisionV1.STAGE))
        assertEquals(AiProposalApplyStatusV1.APPLIED, staged.status)
        val draft = requireNotNull(store.draft(requireNotNull(staged.itemReceipts.single().draftId)))
        assertTrue(store.stageDraft(draft.draftId, form("edited after review").toCoreDraftForm(),
            draft.draftVersion) is PowerStore.StageResult.Saved)

        val publishRequest = request("edited-task-publish", review, AiProposalDecisionV1.PUBLISH)
        val approval = requireNotNull(core.issueAiProposalPublishApproval(publishRequest))
        val receipt = core.aiProposals.applyReviewedProposals(publishRequest, approval)

        assertEquals(AiProposalApplyStatusV1.FAILED, receipt.status)
        assertEquals(AiProposalItemStatusV1.FAILED, receipt.itemReceipts.single().status)
        assertEquals("edited after review", store.pendingDrafts().single().form.title)
        assertTrue(store.all().isEmpty())
        assertEquals(0L, store.commitCount())
    }

    @Test fun selectedProposalsCannotTargetTheSameExistingEvent() {
        val target = createFormal("single reviewed target")
        val first = AiProposalItemV1(0, DraftOperationV1.REPLACE,
            target.eventSeq, target.revision, form("first replacement"))
        val second = AiProposalItemV1(1, DraftOperationV1.DELETE,
            target.eventSeq, target.revision, form("delete same target"))
        val request = request("duplicate-targets", review(first, second),
            AiProposalDecisionV1.STAGE)

        val receipt = core.aiProposals.applyReviewedProposals(request)

        assertEquals(AiProposalApplyStatusV1.INVALID, receipt.status)
        assertTrue(store.pendingDrafts().isEmpty())
        assertEquals("single reviewed target", store.get(target.eventSeq)?.title)
        assertEquals(1L, store.commitCount())
    }

    @Test fun staleCreateDraftTargetOnlyBlocksThatIndependentItem() {
        val target = createFormal("before stale proposal")
        val staleReplacement = AiProposalItemV1(
            sourceIndex = 0,
            operation = DraftOperationV1.REPLACE,
            eventSeq = target.eventSeq,
            expectedRevision = target.revision,
            form = form("must not stage")
        )
        val independentCreate = createItem("independent item").copy(sourceIndex = 1)
        val review = review(staleReplacement, independentCreate)

        val concurrent = store.newDraft(form("concurrent change").toCoreDraftForm(),
            target.eventSeq, DraftOperation.REPLACE, expectedRevision = target.revision)
        assertTrue(store.commit(concurrent.draftId) is PowerStore.CommitOutcome.Committed)
        val receipt = core.aiProposals.createDrafts(review, "create-independent-with-stale")

        assertEquals(AiProposalApplyStatusV1.PARTIAL, receipt.status)
        assertEquals(1, receipt.stagedCount)
        assertEquals(AiProposalItemStatusV1.BLOCKED, receipt.itemReceipts[0].status)
        assertEquals(AiProposalItemStatusV1.STAGED, receipt.itemReceipts[1].status)
        assertEquals("concurrent change", store.get(target.eventSeq)?.title)
        assertEquals(listOf("independent item"), store.pendingDrafts().map { it.form.title })
    }

    @Test fun createDraftsBlocksMixedStaleAndCurrentDuplicateTargets() {
        val target = createFormal("before duplicate proposals")
        val staleReplacement = AiProposalItemV1(
            sourceIndex = 0,
            operation = DraftOperationV1.REPLACE,
            eventSeq = target.eventSeq,
            expectedRevision = target.revision,
            form = form("stale replacement")
        )
        val concurrent = store.newDraft(form("current formal event").toCoreDraftForm(),
            target.eventSeq, DraftOperation.REPLACE, expectedRevision = target.revision)
        assertTrue(store.commit(concurrent.draftId) is PowerStore.CommitOutcome.Committed)
        val currentTarget = requireNotNull(store.get(target.eventSeq))
        val currentDelete = AiProposalItemV1(
            sourceIndex = 1,
            operation = DraftOperationV1.DELETE,
            eventSeq = target.eventSeq,
            expectedRevision = currentTarget.revision,
            form = form("current delete")
        )
        val independentCreate = createItem("independent create").copy(sourceIndex = 2)

        val receipt = core.aiProposals.createDrafts(
            review(staleReplacement, currentDelete, independentCreate),
            "mixed-stale-current-duplicate-targets"
        )

        val targetAfter = requireNotNull(store.get(target.eventSeq))
        assertEquals(AiProposalApplyStatusV1.PARTIAL, receipt.status)
        assertEquals(1, receipt.stagedCount)
        assertEquals(AiProposalItemStatusV1.BLOCKED, receipt.itemReceipts[0].status)
        assertEquals(AiProposalItemStatusV1.BLOCKED, receipt.itemReceipts[1].status)
        assertEquals(AiProposalItemStatusV1.STAGED, receipt.itemReceipts[2].status)
        assertEquals(currentTarget.title, targetAfter.title)
        assertEquals(currentTarget.revision, targetAfter.revision)
        assertEquals(listOf("independent create"), store.pendingDrafts().map { it.form.title })
    }

    @Test fun createDraftsRejectsDuplicateTargetsAndPreservesExistingHumanDraft() {
        val target = createFormal("duplicate target")
        val humanDraft = store.newDraft(form("human draft stays intact").toCoreDraftForm(),
            target.eventSeq, DraftOperation.REPLACE, expectedRevision = target.revision)
        val first = AiProposalItemV1(0, DraftOperationV1.REPLACE,
            target.eventSeq, target.revision, form("first AI replacement"))
        val second = AiProposalItemV1(1, DraftOperationV1.DELETE,
            target.eventSeq, target.revision, form("second AI operation"))
        val independent = createItem("independent create").copy(sourceIndex = 2)

        val receipt = core.aiProposals.createDrafts(
            review(first, second, independent), "duplicate-target-create-drafts")

        assertEquals(AiProposalApplyStatusV1.PARTIAL, receipt.status)
        assertEquals(1, receipt.stagedCount)
        assertEquals(AiProposalItemStatusV1.BLOCKED, receipt.itemReceipts[0].status)
        assertEquals(AiProposalItemStatusV1.BLOCKED, receipt.itemReceipts[1].status)
        assertEquals(AiProposalItemStatusV1.STAGED, receipt.itemReceipts[2].status)
        assertEquals("human draft stays intact", store.draft(humanDraft.draftId)?.form?.title)
        assertEquals(setOf("human draft stays intact", "independent create"),
            store.pendingDrafts().map { it.form.title }.toSet())
        assertEquals("duplicate target", store.get(target.eventSeq)?.title)
    }

    @Test fun expiredTaskAndAiBatchLockRejectProposalWithoutWrites() {
        val expired = AiProposalTaskContextV1(
            taskId = "expired-task",
            revision = 1,
            anchorMillis = clock.millis() - 25L * 60 * 60 * 1000,
            latestEvidenceMillis = clock.millis() - 25L * 60 * 60 * 1000
        )
        val expiredReceipt = core.aiProposals.applyReviewedProposals(request(
            "expired-task-request", review(createItem("expired"), task = expired),
            AiProposalDecisionV1.STAGE))
        assertEquals(AiProposalApplyStatusV1.EXPIRED, expiredReceipt.status)
        assertTrue(store.pendingDrafts().isEmpty())

        val existing = store.newDraft(form("batch owned").toCoreDraftForm())
        val session = store.beginAiBatch(mapOf(existing.draftId to existing.draftVersion),
            setOf(DraftOperation.CREATE))
        try {
            val lockedReview = review(createItem("must wait"), task = AiProposalTaskContextV1(
                "active-task", 1, clock.millis(), clock.millis()))
            val locked = core.aiProposals.applyReviewedProposals(request(
                "locked-task-request", lockedReview, AiProposalDecisionV1.STAGE))
            assertEquals(AiProposalApplyStatusV1.LOCKED, locked.status)
            assertEquals(1, store.pendingDrafts().size)
            assertEquals(setOf("batch owned"), store.pendingDrafts().map { it.form.title }.toSet())
        } finally {
            store.finishAiBatch(session.sessionId, PowerStore.AiState.CANCELLED)
        }
    }

    @Test fun invalidSelectionAndRecurrenceScopeCannotWrite() {
        val blocked = createItem("blocked").copy(blockedReason = "ambiguous target")
        val review = review(blocked)
        val blockedResult = core.aiProposals.applyReviewedProposals(request(
            "blocked-selection", review, AiProposalDecisionV1.STAGE))
        assertEquals(AiProposalApplyStatusV1.INVALID, blockedResult.status)
        assertTrue(store.pendingDrafts().isEmpty())

        val scopedReview = review(createItem("bad scope").copy(
            form = createItem("bad scope").form.copy(
                recurrenceScope = EventRecurrenceScopeV1.WHOLE_SERIES,
                editOccurrenceKey = "2026-11-02T10:00"
            )
        ))
        val scopedResult = core.aiProposals.applyReviewedProposals(request(
            "scope-selection", scopedReview, AiProposalDecisionV1.STAGE))
        assertEquals(AiProposalApplyStatusV1.INVALID, scopedResult.status)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun reviewReadinessUsesFullCoreBuildAndKeepsDeleteFormOptional() {
        val target = createFormal("delete readiness target")
        val malformedRecurrence = createItem("bad recurrence").copy(
            form = form("bad recurrence").copy(recurrence = EventRecurrenceV1(interval = 0))
        )
        val malformedMemo = AiProposalItemV1(
            sourceIndex = 3,
            operation = DraftOperationV1.CREATE,
            eventSeq = null,
            expectedRevision = null,
            form = DraftForm(kind = TemporalKind.MEMO, title = "Bad memo",
                endText = "2026-12-02", zoneId = zone.id).toEventFormV1()
        )
        val invalidDelete = AiProposalItemV1(
            sourceIndex = 5,
            operation = DraftOperationV1.DELETE,
            eventSeq = target.eventSeq,
            expectedRevision = target.revision,
            form = malformedRecurrence.form
        )
        val scopedDelete = invalidDelete.copy(
            sourceIndex = 6,
            form = invalidDelete.form.copy(
                recurrenceScope = EventRecurrenceScopeV1.SINGLE_OCCURRENCE,
                editOccurrenceKey = "2026-11-02T10:00"
            )
        )
        val review = review(
            createItem("ready"),
            createItem("incomplete").copy(form = form("incomplete").copy(endText = "")),
            malformedRecurrence,
            malformedMemo,
            createItem("blocked").copy(blockedReason = "目标不明确"),
            invalidDelete,
            scopedDelete
        )
        val commitsBefore = store.commitCount()

        val readiness = core.aiProposals.assessReview(review)

        assertEquals(review.reviewId, readiness.reviewId)
        assertEquals(listOf(
            AiProposalReadinessStatusV1.READY,
            AiProposalReadinessStatusV1.INCOMPLETE,
            AiProposalReadinessStatusV1.INVALID,
            AiProposalReadinessStatusV1.INVALID,
            AiProposalReadinessStatusV1.BLOCKED,
            AiProposalReadinessStatusV1.NOT_REQUIRED,
            AiProposalReadinessStatusV1.INVALID
        ), readiness.items.map { it.status })
        assertEquals(listOf(true, false, false, false, false, true, false),
            readiness.items.map { it.canPublish })
        assertEquals("重复间隔必须至少为 1", readiness.items[2].message)
        assertEquals("备忘结束日期需要开始日期", readiness.items[3].message)
        assertEquals("AI_PROPOSAL_SCOPE_INVALID", readiness.items[6].errorCode)
        assertTrue(malformedRecurrence.form.toCoreDraftForm().isPublishable())
        assertTrue(malformedMemo.form.toCoreDraftForm().isPublishable())
        assertTrue(store.pendingDrafts().isEmpty())
        assertEquals(commitsBefore, store.commitCount())
        assertEquals(target, store.get(target.eventSeq))
    }

    @Test fun buildInvalidProposalCannotPublishOrBeCountedAsStaged() {
        val malformed = DraftForm(kind = TemporalKind.ACTIVITY, title = "Invalid recurrence",
            startText = "2026-11-02 10:00", endText = "2026-11-02 11:00",
            recurrence = Recurrence(interval = 0), zoneId = zone.id)
        assertTrue(malformed.isPublishable())
        assertTrue(malformed.build(1L, 1L, clock.millis()) is
            dev.powerfulcalendar.core.FormBuild.Invalid)
        val review = review(createItem("invalid").copy(form = malformed.toEventFormV1()))
        val request = request("invalid-build-publish", review, AiProposalDecisionV1.PUBLISH)
        val commitsBefore = store.commitCount()

        assertNull(core.issueAiProposalPublishApproval(request))
        val receipt = core.aiProposals.applyReviewedProposals(request)
        assertEquals(AiProposalApplyStatusV1.INVALID, receipt.status)
        assertEquals(0, receipt.stagedCount)
        assertEquals(0, receipt.publishedCount)
        assertTrue(store.pendingDrafts().isEmpty())
        assertEquals(commitsBefore, store.commitCount())

        val serviceResult = AiDraftService(store, clock).applyReviewedProposals(
            interpretation = AiInterpretation(listOf(AiProposal(
                DraftOperation.CREATE, null, malformed
            ))),
            selectedPositions = setOf(0),
            publish = true
        )
        assertEquals(0, serviceResult.stagedCount)
        assertEquals(AiDraftService.ItemState.FAILED, serviceResult.itemResults.single().state)
        assertTrue(serviceResult.itemResults.single().message.orEmpty().contains("重复间隔必须至少为 1"))
        assertTrue(store.pendingDrafts().isEmpty())
        assertEquals(commitsBefore, store.commitCount())
    }

    private fun createFormal(title: String): dev.powerfulcalendar.core.TemporalRecord {
        val draft = store.newDraft(form(title).toCoreDraftForm())
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        return requireNotNull(store.get(draft.eventSeq))
    }

    private fun form(title: String) = DraftForm(
        kind = TemporalKind.ACTIVITY,
        title = title,
        startText = "2026-11-02 10:00",
        endText = "2026-11-02 11:00",
        zoneId = zone.id
    ).toEventFormV1()

    private fun createItem(title: String) = AiProposalItemV1(
        sourceIndex = 0,
        operation = DraftOperationV1.CREATE,
        eventSeq = null,
        expectedRevision = null,
        form = form(title)
    )

    private fun review(
        vararg items: AiProposalItemV1,
        task: AiProposalTaskContextV1? = null
    ) = review("review-${UUID.randomUUID()}", *items, task = task)

    private fun review(
        reviewId: String,
        vararg items: AiProposalItemV1,
        task: AiProposalTaskContextV1? = null
    ) = AiProposalReviewV1(reviewId, items.toList(), task = task)

    private fun request(
        requestId: String,
        review: AiProposalReviewV1,
        decision: AiProposalDecisionV1
    ) = AiProposalApplyRequestV1(requestId, review, review.items.indices.toSet(), decision)

    private class MutableClock(private var current: Instant) : Clock() {
        fun advanceMillis(millis: Long) { current = current.plusMillis(millis) }
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }
}
