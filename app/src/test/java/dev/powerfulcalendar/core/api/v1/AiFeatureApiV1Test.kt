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
    private lateinit var conversationPort: FakeAiConversationHistoryPort
    private val clock = MutableClock(Instant.parse("2026-11-01T00:00:00Z"))
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "ai-feature-api-${UUID.randomUUID()}.db")
        model = FakeAiModelPort()
        conversationPort = FakeAiConversationHistoryPort()
        core = PowerCalCoreFeatureApiV1(
            store, clock, aiModelPort = model, aiConversationHistoryPort = conversationPort
        )
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test fun ordinaryChatUsesBoundedContextAndReplaysTheCompletedReceipt() = runBlocking {
        val target = createFormal("chat context")
        model.chatOutput = AiChatModelOutputV1(reply = "你好")
        val request = chatRequest("chat-replay", share = true)

        val first = core.ai.chat(request)
        val replay = core.ai.chat(request)
        val conflicting = core.ai.chat(request.copy(shareCalendarContext = false))

        assertEquals(AiChatStatusV1.COMPLETED, first.status)
        assertEquals("你好", first.reply)
        assertEquals(listOf(target.eventSeq), model.lastChatContext.map { it.eventSeq })
        assertEquals(1, model.chatCalls)
        assertEquals(listOf("你好"), model.lastChatRequest?.history?.map { it.content })
        assertTrue(replay.replayed)
        assertEquals("你好", replay.reply)
        assertEquals(AiChatStatusV1.DUPLICATE_REQUEST_ID, conflicting.status)
        assertEquals(1, model.chatCalls)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun ordinaryChatDoesNotReadCalendarWhenSharingIsOff() = runBlocking {
        createFormal("private chat context")
        model.chatOutput = AiChatModelOutputV1(reply = "已收到")

        val receipt = core.ai.chat(chatRequest("chat-private", share = false))

        assertEquals(AiChatStatusV1.COMPLETED, receipt.status)
        assertTrue(model.lastChatContext.isEmpty())
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun explicitChatIntentUsesProposalApiAndNeverStagesByItself() = runBlocking {
        model.chatOutput = AiChatModelOutputV1(
            reply = "我先生成一份可审核提案。",
            automaticProposalTask = task(),
            proposalIntent = AiChatProposalIntentV1.EXPLICIT
        )
        model.proposalOutput = AiProposalModelOutputV1(AiProposalReviewV1(
            reviewId = "model-review",
            items = listOf(AiProposalItemV1(
                sourceIndex = 0,
                operation = DraftOperationV1.CREATE,
                eventSeq = null,
                expectedRevision = null,
                form = form("chat proposal")
            ))
        ), contextUsed = false)

        val receipt = core.ai.chat(chatRequest("chat-explicit"))

        assertEquals(AiChatStatusV1.COMPLETED, receipt.status)
        assertEquals(AiProposalGenerationStatusV1.READY, receipt.automaticProposal?.status)
        assertEquals(1, model.proposalCalls)
        assertTrue(receipt.automaticProposal?.review?.reviewId?.startsWith("chat-proposal-") == true)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun explicitChatWithoutAValidatedTaskReturnsClarification() = runBlocking {
        model.chatOutput = AiChatModelOutputV1(
            reply = "我可以帮你安排。",
            proposalIntent = AiChatProposalIntentV1.EXPLICIT
        )

        val receipt = core.ai.chat(chatRequest("chat-no-task"))

        assertEquals(AiChatStatusV1.COMPLETED, receipt.status)
        assertTrue(receipt.clarification.orEmpty().contains("无法安全关联"))
        assertEquals(0, model.proposalCalls)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun invalidConversationIsRejectedBeforeProviderCall() = runBlocking {
        val request = AiChatRequestV1(
            requestId = "chat-invalid",
            userMessageId = "missing-user-message",
            expectedConversationRevision = core.ai.queryConversation().revision,
            zoneId = zone.id
        )

        val receipt = core.ai.chat(request)

        assertEquals(AiChatStatusV1.INVALID_REQUEST, receipt.status)
        assertEquals(0, model.chatCalls)
    }

    @Test fun chatBindsTheExactUserMessageAndConversationRevision() = runBlocking {
        val initial = core.ai.queryConversation()
        val appended = core.ai.appendConversationMessage(AiConversationMessageV1(
            role = AiConversationRoleV1.USER,
            content = "本轮输入",
            messageId = "bound-user"
        ), initial.revision)
        assertEquals(AiConversationMutationStatusV1.APPLIED, appended.status)

        val mismatchedMessage = core.ai.chat(AiChatRequestV1(
            requestId = "chat-message-mismatch",
            userMessageId = "other-user",
            expectedConversationRevision = appended.snapshot.revision,
            zoneId = zone.id
        ))
        assertEquals(AiChatStatusV1.INVALID_REQUEST, mismatchedMessage.status)
        assertEquals("CHAT_MESSAGE_MISMATCH", mismatchedMessage.errorCode)

        val changed = core.ai.appendConversationMessage(AiConversationMessageV1(
            role = AiConversationRoleV1.ASSISTANT,
            content = "追加的状态",
            messageId = "after-user"
        ), appended.snapshot.revision)
        assertEquals(AiConversationMutationStatusV1.APPLIED, changed.status)
        val stale = core.ai.chat(AiChatRequestV1(
            requestId = "chat-stale-revision",
            userMessageId = "bound-user",
            expectedConversationRevision = appended.snapshot.revision,
            zoneId = zone.id
        ))
        assertEquals(AiChatStatusV1.STALE_CONVERSATION, stale.status)
        assertEquals(0, model.chatCalls)
    }

    @Test fun conversationCommandsOwnRevisionFencedMutationsAndPersistence() = runBlocking {
        val ids = listOf("u1", "a1", "u2", "a2")
        val contents = listOf("第一条请求", "第一条回复", "第二条请求", "第二条回复")
        var revision = core.ai.queryConversation().revision
        ids.indices.forEach { index ->
            val result = core.ai.appendConversationMessage(AiConversationMessageV1(
                role = if (index % 2 == 0) AiConversationRoleV1.USER else AiConversationRoleV1.ASSISTANT,
                content = contents[index],
                messageId = ids[index]
            ), revision)
            assertEquals(AiConversationMutationStatusV1.APPLIED, result.status)
            revision = result.snapshot.revision
        }

        assertEquals(AiConversationMutationStatusV1.STALE,
            core.ai.clearConversation(revision - 1).status)
        val edited = core.ai.rewindConversation(
            "u2", AiConversationRewindModeV1.EDIT_AND_RESEND, revision
        )
        assertEquals(AiConversationMutationStatusV1.APPLIED, edited.status)
        assertEquals("第二条请求", edited.prefillText)
        assertEquals(listOf("u1", "a1"), edited.snapshot.messages.map { it.messageId })
        assertEquals(AiConversationPersistenceStatusV1.STALE,
            core.ai.persistConversation(revision))
        assertEquals(AiConversationPersistenceStatusV1.SAVED,
            core.ai.persistConversation(edited.snapshot.revision))
        assertEquals(edited.snapshot.messages, conversationPort.savedMessages)

        val clear = core.ai.clearConversation(edited.snapshot.revision)
        assertEquals(AiConversationMutationStatusV1.APPLIED, clear.status)
        assertEquals(AiConversationMutationStatusV1.NO_CHANGE,
            core.ai.clearConversation(clear.snapshot.revision).status)
    }

    @Test fun conversationRewindRejectsMissingAmbiguousAndNonUserEditTargets() {
        val duplicates = listOf(
            AiConversationMessageV1(AiConversationRoleV1.USER, "一", messageId = "same"),
            AiConversationMessageV1(AiConversationRoleV1.ASSISTANT, "二", messageId = "same"),
            AiConversationMessageV1(AiConversationRoleV1.ASSISTANT, "回复", messageId = "assistant")
        )
        val duplicatePort = FakeAiConversationHistoryPort(duplicates)
        val duplicateApi = PowerCalCoreFeatureApiV1(
            store, clock, aiConversationHistoryPort = duplicatePort
        ).ai
        val revision = duplicateApi.queryConversation().revision

        assertEquals(AiConversationMutationStatusV1.AMBIGUOUS_MESSAGE,
            duplicateApi.rewindConversation("same", AiConversationRewindModeV1.RETRACT, revision).status)
        assertEquals(AiConversationMutationStatusV1.MISSING_MESSAGE,
            duplicateApi.rewindConversation("absent", AiConversationRewindModeV1.RETRACT, revision).status)
        assertEquals(AiConversationMutationStatusV1.EDIT_REQUIRES_USER_MESSAGE,
            duplicateApi.rewindConversation(
                "assistant", AiConversationRewindModeV1.EDIT_AND_RESEND, revision
            ).status)
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

    @Test fun reviewedTextEvidenceUsesCanonicalConversationAndRejectsStaleOrOversizedInput() = runBlocking {
        val conversation = chatRequest("reviewed-evidence")
        model.evidenceOutput = AiEvidenceModelOutputV1(
            reply = "发现两项日期需要核对。",
            proposalSource = "模型候选来源，仅供单独审核"
        )

        val evidenceRequest = AiReviewedTextEvidenceRequestV1(
            requestId = "reviewed-evidence-analysis",
            source = AiTextEvidenceSourceV1.REVIEWED_OCR_TEXT,
            filename = "reviewed.png",
            text = "周一 10 点课程",
            truncated = true,
            preflightHint = "OCR 文本已由用户确认",
            expectedConversationRevision = conversation.expectedConversationRevision,
            zoneId = zone.id
        )
        val receipt = core.ai.analyzeReviewedTextEvidence(evidenceRequest)

        assertEquals(AiEvidenceAnalysisStatusV1.COMPLETED, receipt.status)
        assertEquals("发现两项日期需要核对。", receipt.reply)
        assertEquals("模型候选来源，仅供单独审核", receipt.proposalSource)
        assertEquals(AiTextEvidenceSourceV1.REVIEWED_OCR_TEXT, model.lastTextEvidence?.source)
        assertEquals("周一 10 点课程", model.lastTextEvidence?.text)
        assertTrue(model.lastTextEvidence?.truncated == true)
        assertEquals("OCR 文本已由用户确认", model.lastTextEvidence?.preflightHint)
        assertEquals("user-reviewed-evidence", model.lastTextEvidence?.history?.last()?.messageId)
        assertEquals(0, model.proposalCalls)
        assertTrue(store.pendingDrafts().isEmpty())

        val replay = core.ai.analyzeReviewedTextEvidence(evidenceRequest)
        assertEquals(AiEvidenceAnalysisStatusV1.COMPLETED, replay.status)
        assertTrue(replay.replayed)
        val reusedId = core.ai.analyzeReviewedTextEvidence(evidenceRequest.copy(text = "改过的内容"))
        assertEquals(AiEvidenceAnalysisStatusV1.DUPLICATE_REQUEST_ID, reusedId.status)

        val stale = core.ai.analyzeReviewedTextEvidence(AiReviewedTextEvidenceRequestV1(
            requestId = "stale-evidence",
            source = AiTextEvidenceSourceV1.REVIEWED_FILE_TEXT,
            filename = "schedule.txt",
            text = "明天 10 点开会",
            truncated = false,
            expectedConversationRevision = conversation.expectedConversationRevision - 1,
            zoneId = zone.id
        ))
        assertEquals(AiEvidenceAnalysisStatusV1.STALE_CONVERSATION, stale.status)

        val oversized = core.ai.analyzeReviewedTextEvidence(AiReviewedTextEvidenceRequestV1(
            requestId = "oversized-evidence",
            source = AiTextEvidenceSourceV1.REVIEWED_FILE_TEXT,
            filename = "schedule.txt",
            text = "x".repeat(24_001),
            truncated = false,
            expectedConversationRevision = core.ai.queryConversation().revision,
            zoneId = zone.id
        ))
        assertEquals(AiEvidenceAnalysisStatusV1.INVALID_REQUEST, oversized.status)
        assertEquals(1, model.textEvidenceCalls)
    }

    @Test fun evidenceResultIsDiscardedIfConversationChangesDuringModelCall() = runBlocking {
        val conversation = chatRequest("late-evidence")
        model.beforeEvidenceReturn = {
            val current = core.ai.queryConversation()
            core.ai.appendConversationMessage(AiConversationMessageV1(
                role = AiConversationRoleV1.USER,
                content = "另一条输入",
                messageId = "later-user",
                createdAtMillis = clock.millis(),
                zoneId = zone.id
            ), current.revision)
        }

        val receipt = core.ai.analyzeReviewedTextEvidence(AiReviewedTextEvidenceRequestV1(
            requestId = "late-evidence-analysis",
            source = AiTextEvidenceSourceV1.REVIEWED_FILE_TEXT,
            filename = "schedule.txt",
            text = "明天十点开会",
            truncated = false,
            expectedConversationRevision = conversation.expectedConversationRevision,
            zoneId = zone.id
        ))

        assertEquals(AiEvidenceAnalysisStatusV1.STALE_CONVERSATION, receipt.status)
        assertEquals(1, model.textEvidenceCalls)
    }

    @Test fun unresolvedIcsEvidenceNeverReturnsAProposalSource() = runBlocking {
        val conversation = chatRequest("ics-evidence")
        model.evidenceOutput = AiEvidenceModelOutputV1(
            reply = "该日历包含当前不支持的修改语义。",
            proposalSource = "模型不应把修改例外当作新事件"
        )

        val receipt = core.ai.analyzeReviewedTextEvidence(AiReviewedTextEvidenceRequestV1(
            requestId = "ics-explanation",
            source = AiTextEvidenceSourceV1.ICS_UNRESOLVED,
            filename = "unresolved.ics",
            text = "BEGIN:VEVENT\nRECURRENCE-ID...",
            truncated = false,
            expectedConversationRevision = conversation.expectedConversationRevision,
            zoneId = zone.id
        ))

        assertEquals(AiEvidenceAnalysisStatusV1.COMPLETED, receipt.status)
        assertEquals("该日历包含当前不支持的修改语义。", receipt.reply)
        assertNull(receipt.proposalSource)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun evidenceModelOutputExceedingLimitsIsNotReturned() = runBlocking {
        val conversation = chatRequest("large-evidence-output")
        model.evidenceOutput = AiEvidenceModelOutputV1(reply = "x".repeat(12_001))
        val oversizedReply = core.ai.analyzeReviewedTextEvidence(AiReviewedTextEvidenceRequestV1(
            requestId = "large-evidence-reply",
            source = AiTextEvidenceSourceV1.REVIEWED_FILE_TEXT,
            filename = "schedule.txt",
            text = "明天十点开会",
            truncated = false,
            expectedConversationRevision = conversation.expectedConversationRevision,
            zoneId = zone.id
        ))
        assertEquals(AiEvidenceAnalysisStatusV1.MODEL_UNAVAILABLE, oversizedReply.status)
        assertEquals("OUTPUT_LIMIT_EXCEEDED", oversizedReply.errorCode)
        assertTrue(oversizedReply.reply.isEmpty())

        model.evidenceOutput = AiEvidenceModelOutputV1(proposalSource = "x".repeat(6_001))
        val oversizedProposalSource = core.ai.analyzeReviewedTextEvidence(AiReviewedTextEvidenceRequestV1(
            requestId = "large-evidence-proposal-source",
            source = AiTextEvidenceSourceV1.REVIEWED_FILE_TEXT,
            filename = "schedule.txt",
            text = "明天十点开会",
            truncated = false,
            expectedConversationRevision = conversation.expectedConversationRevision,
            zoneId = zone.id
        ))
        assertEquals(AiEvidenceAnalysisStatusV1.MODEL_UNAVAILABLE, oversizedProposalSource.status)
        assertEquals("OUTPUT_LIMIT_EXCEEDED", oversizedProposalSource.errorCode)
        assertTrue(oversizedProposalSource.proposalSource == null)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test fun consentedImageEvidenceIsBoundedAndPassedOnlyToTheModelPort() = runBlocking {
        val conversation = chatRequest("image-evidence")
        val bytes = byteArrayOf(1, 2, 3, 4)
        model.evidenceOutput = AiEvidenceModelOutputV1(reply = "图片内容已识别。")

        val imageRequest = AiConsentedImageEvidenceRequestV1(
            requestId = "image-analysis",
            filename = "calendar.png",
            mimeType = "image/png",
            imageBytes = bytes,
            expectedConversationRevision = conversation.expectedConversationRevision,
            zoneId = zone.id
        )
        val receipt = core.ai.analyzeConsentedImageEvidence(imageRequest)

        assertEquals(AiEvidenceAnalysisStatusV1.COMPLETED, receipt.status)
        assertEquals("图片内容已识别。", receipt.reply)
        assertEquals("image/png", model.lastImageEvidence?.mimeType)
        assertTrue(bytes.contentEquals(model.lastImageEvidence?.imageBytes))
        assertEquals("user-image-evidence", model.lastImageEvidence?.history?.last()?.messageId)
        assertEquals(0, model.proposalCalls)
        assertTrue(store.pendingDrafts().isEmpty())

        assertTrue(core.ai.analyzeConsentedImageEvidence(imageRequest).replayed)
        val changedImage = core.ai.analyzeConsentedImageEvidence(
            imageRequest.copy(imageBytes = byteArrayOf(9, 8, 7))
        )
        assertEquals(AiEvidenceAnalysisStatusV1.DUPLICATE_REQUEST_ID, changedImage.status)

        val invalid = core.ai.analyzeConsentedImageEvidence(AiConsentedImageEvidenceRequestV1(
            requestId = "oversized-image",
            filename = "calendar.png",
            mimeType = "image/png",
            imageBytes = ByteArray(6_000_001),
            expectedConversationRevision = core.ai.queryConversation().revision,
            zoneId = zone.id
        ))
        assertEquals(AiEvidenceAnalysisStatusV1.INVALID_REQUEST, invalid.status)
        val invalidMime = core.ai.analyzeConsentedImageEvidence(
            imageRequest.copy(requestId = "invalid-image-mime", mimeType = "text/plain")
        )
        assertEquals(AiEvidenceAnalysisStatusV1.INVALID_REQUEST, invalidMime.status)
        assertEquals(1, model.imageEvidenceCalls)
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

    private fun chatRequest(requestId: String, share: Boolean = false): AiChatRequestV1 {
        val snapshot = core.ai.queryConversation()
        val userMessageId = "user-$requestId"
        val appended = core.ai.appendConversationMessage(AiConversationMessageV1(
            role = AiConversationRoleV1.USER,
            content = "你好",
            messageId = userMessageId,
            createdAtMillis = clock.millis(),
            zoneId = zone.id
        ), snapshot.revision)
        assertEquals(AiConversationMutationStatusV1.APPLIED, appended.status)
        return AiChatRequestV1(
            requestId = requestId,
            userMessageId = userMessageId,
            expectedConversationRevision = appended.snapshot.revision,
            zoneId = zone.id,
            shareCalendarContext = share
        )
    }

    private class FakeAiModelPort : AiModelPortV1 {
        var proposalOutput = AiProposalModelOutputV1(AiProposalReviewV1("unused", emptyList()), false)
        var evidenceOutput = AiEvidenceModelOutputV1(error = "未配置假响应")
        var selection = AiBatchModelSelectionV1(error = "未配置假响应")
        var chatOutput = AiChatModelOutputV1(error = "未配置假响应")
        var chatCalls = 0
        var lastChatRequest: AiChatModelRequestV1? = null
        var proposalCalls = 0
        var textEvidenceCalls = 0
        var imageEvidenceCalls = 0
        var beforeEvidenceReturn: (() -> Unit)? = null
        var lastTextEvidence: AiTextEvidenceModelRequestV1? = null
        var lastImageEvidence: AiImageEvidenceModelRequestV1? = null
        var lastChatContext: List<EventRecordV1> = emptyList()
        var lastContext: List<EventRecordV1> = emptyList()

        override suspend fun chat(
            request: AiChatModelRequestV1,
            calendarContext: List<EventRecordV1>
        ): AiChatModelOutputV1 {
            chatCalls++
            lastChatRequest = request.copy(history = request.history.map { it.copy() })
            lastChatContext = calendarContext.toList()
            return chatOutput
        }

        override suspend fun generateProposal(
            request: AiProposalGenerationRequestV1,
            calendarContext: List<EventRecordV1>
        ): AiProposalModelOutputV1 {
            proposalCalls++
            lastContext = calendarContext.toList()
            return proposalOutput
        }

        override suspend fun analyzeReviewedTextEvidence(
            request: AiTextEvidenceModelRequestV1
        ): AiEvidenceModelOutputV1 {
            textEvidenceCalls++
            lastTextEvidence = request.copy(history = request.history.map { it.copy() })
            beforeEvidenceReturn?.invoke()
            return evidenceOutput
        }

        override suspend fun analyzeConsentedImageEvidence(
            request: AiImageEvidenceModelRequestV1
        ): AiEvidenceModelOutputV1 {
            imageEvidenceCalls++
            lastImageEvidence = request.copy(
                imageBytes = request.imageBytes.copyOf(),
                history = request.history.map { it.copy() }
            )
            beforeEvidenceReturn?.invoke()
            return evidenceOutput
        }

        override suspend fun selectBatchDrafts(
            userRequest: String,
            candidates: List<AiBatchCandidateV1>
        ) = selection
    }

    private class FakeAiConversationHistoryPort(
        initialMessages: List<AiConversationMessageV1> = emptyList()
    ) : AiConversationHistoryPortV1 {
        private val loadedMessages = initialMessages.map { it.copy() }
        var savedMessages: List<AiConversationMessageV1> = emptyList()
            private set

        override fun load() = loadedMessages.map { it.copy() }

        override suspend fun save(messages: List<AiConversationMessageV1>) {
            savedMessages = messages.map { it.copy() }
        }
    }

    private class MutableClock(private var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
        fun set(value: Instant) { instant = value }
    }
}
