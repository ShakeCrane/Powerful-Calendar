package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import java.time.Clock
import java.time.ZoneId
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Store-backed AI domain adapter. Provider configuration is supplied only through [modelPort]. */
internal class StoreAiFeatureApiV1(
    private val store: PowerStore,
    private val events: StoreEventFeatureApiV1,
    private val modelPort: AiModelPortV1?,
    private val clock: Clock
) : AiFeatureApiV1 {
    override val apiVersion: Int = 1

    private val approvalLock = Any()
    private val batchApprovals = IdentityHashMap<AiBatchApprovalV1, AiBatchReviewV1>()
    private val stuckReleaseApprovals =
        IdentityHashMap<AiBatchStuckReleaseApprovalV1, Pair<String, Long>>()

    override suspend fun generateProposal(
        request: AiProposalGenerationRequestV1
    ): AiProposalGenerationReceiptV1 {
        validateGenerationRequest(request)?.let { (code, message) ->
            return AiProposalGenerationReceiptV1(
                requestId = request.requestId,
                status = AiProposalGenerationStatusV1.INVALID_REQUEST,
                errorCode = code,
                message = message
            )
        }
        val port = modelPort ?: return AiProposalGenerationReceiptV1(
            requestId = request.requestId,
            status = AiProposalGenerationStatusV1.MODEL_UNAVAILABLE,
            errorCode = "MODEL_UNAVAILABLE",
            message = "AI 提案服务暂不可用"
        )

        val context = if (request.shareCalendarContext) {
            val snapshot = events.queryEvents().events
            when (request.contextPolicy) {
                AiCalendarContextPolicyV1.FIRST_60_FORMAL_EVENTS ->
                    snapshot.take(MAX_CALENDAR_CONTEXT_EVENTS)
                AiCalendarContextPolicyV1.EVENTS_THROUGH_14_DAYS_AFTER_NOW -> {
                    val horizon = clock.millis() + CALENDAR_CONTEXT_HORIZON_MILLIS
                    snapshot.asSequence()
                        .filter { record ->
                            val temporal = record.toCoreTemporalRecord()
                            (temporal.anchorMillis() ?: 0L) <= horizon
                        }
                        .take(MAX_CALENDAR_CONTEXT_EVENTS)
                        .toList()
                }
            }
        } else emptyList()

        val modelOutput = try {
            port.generateProposal(request.detached(), context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return AiProposalGenerationReceiptV1(
                requestId = request.requestId,
                status = AiProposalGenerationStatusV1.MODEL_UNAVAILABLE,
                sharedEventCount = 0,
                errorCode = "MODEL_UNAVAILABLE",
                message = error.message?.take(300) ?: "AI 提案阶段未完成"
            )
        }
        if (modelOutput.review.items.size > MAX_AI_PROPOSALS) {
            return AiProposalGenerationReceiptV1(
                requestId = request.requestId,
                status = AiProposalGenerationStatusV1.MODEL_UNAVAILABLE,
                sharedEventCount = if (modelOutput.contextUsed) context.size else 0,
                errorCode = "OUTPUT_LIMIT_EXCEEDED",
                message = "AI 返回的候选超过 100 项；没有创建草稿"
            )
        }
        val effectiveContext = if (modelOutput.contextUsed) context else emptyList()
        val expectedRevisions = effectiveContext.associate { it.eventSeq to it.revision }
        val trustedTaskContext = request.task?.let { task ->
            AiProposalTaskContextV1(
                taskId = task.taskId,
                revision = task.revision,
                anchorMillis = task.anchorMillis,
                latestEvidenceMillis = task.userEvidence.maxOfOrNull { it.createdAtMillis }
                    ?: task.anchorMillis
            )
        }
        val review = modelOutput.review.copy(
            reviewId = request.requestId,
            items = modelOutput.review.items.map { item ->
                item.copy(expectedRevision = item.eventSeq?.let(expectedRevisions::get))
            },
            warnings = modelOutput.review.warnings.toList(),
            task = trustedTaskContext
        ).detached()
        return AiProposalGenerationReceiptV1(
            requestId = request.requestId,
            status = if (review.items.isEmpty()) AiProposalGenerationStatusV1.NO_PROPOSALS
                else AiProposalGenerationStatusV1.READY,
            review = review,
            sharedEventCount = effectiveContext.size,
            warnings = review.warnings
        )
    }

    override suspend fun selectBatchDrafts(
        request: AiBatchSelectionRequestV1
    ): AiBatchSelectionReceiptV1 {
        val candidates = request.candidates.map { it.copy() }
        validateCandidates(candidates)?.let { return selectionInvalid(it) }
        if (candidates.isEmpty()) return AiBatchSelectionReceiptV1(
            status = AiBatchSelectionStatusV1.NEEDS_CLARIFICATION,
            question = "没有可处理的待办草稿"
        )
        if (request.selectAll) return AiBatchSelectionReceiptV1(
            status = AiBatchSelectionStatusV1.READY,
            selected = candidates,
            reply = "按用户明确的“全部”选择全部待处理草稿"
        )
        if (request.userRequest.isBlank()) return AiBatchSelectionReceiptV1(
            status = AiBatchSelectionStatusV1.NEEDS_CLARIFICATION,
            question = "请说明要处理哪些草稿，或明确选择全部。"
        )
        if (candidates.size > MAX_BATCH_ITEMS) return selectionInvalid(
            "待处理超过 100 张，请先缩小范围"
        )
        val port = modelPort ?: return AiBatchSelectionReceiptV1(
            status = AiBatchSelectionStatusV1.MODEL_UNAVAILABLE,
            message = "请先配置 AI 服务"
        )
        val modelSelection = try {
            port.selectBatchDrafts(request.userRequest.take(MAX_BATCH_REQUEST_CHARS), candidates)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return AiBatchSelectionReceiptV1(
                status = AiBatchSelectionStatusV1.MODEL_UNAVAILABLE,
                message = error.message?.take(300) ?: "AI 暂时不能安全选择目标"
            )
        }
        modelSelection.error?.let { modelError -> return AiBatchSelectionReceiptV1(
            status = AiBatchSelectionStatusV1.MODEL_UNAVAILABLE,
            message = modelError.take(300)
        ) }
        modelSelection.question?.let { question -> return AiBatchSelectionReceiptV1(
            status = AiBatchSelectionStatusV1.NEEDS_CLARIFICATION,
            reply = modelSelection.reply.take(500),
            question = question.take(300)
        ) }
        val ids = modelSelection.selectedDraftIds
        if (ids.isEmpty() || ids.size > MAX_BATCH_ITEMS || ids.distinct().size != ids.size) {
            return selectionInvalid("AI 返回的批量草稿 ID 为空、重复或数量超限")
        }
        val byId = candidates.associateBy { it.draftId }
        val selected = ids.map { id -> byId[id] ?: return selectionInvalid(
            "AI 选择了范围外的草稿；没有执行任何操作"
        ) }
        return AiBatchSelectionReceiptV1(
            status = AiBatchSelectionStatusV1.READY,
            selected = selected,
            reply = modelSelection.reply.take(500).ifBlank { "已找到候选草稿" }
        )
    }

    override fun queryBatchState(): AiBatchStateSnapshotV1 {
        val session = store.currentAiSession()
        return AiBatchStateSnapshotV1(
            locked = store.isLocked(),
            session = session?.toApiSession(clock.millis())
        )
    }

    override fun requestBatchCancellation(sessionId: String): AiBatchCommandReceiptV1 {
        val current = store.currentAiSession() ?: return AiBatchCommandReceiptV1(
            AiBatchCommandStatusV1.NO_ACTIVE_SESSION
        )
        if (current.sessionId != sessionId) return AiBatchCommandReceiptV1(
            AiBatchCommandStatusV1.STALE, sessionId, "批次已变化"
        )
        if (current.state == PowerStore.AiState.CANCEL_REQUESTED) return AiBatchCommandReceiptV1(
            AiBatchCommandStatusV1.ALREADY_CANCEL_REQUESTED, sessionId
        )
        if (current.state != PowerStore.AiState.RUNNING) return AiBatchCommandReceiptV1(
            AiBatchCommandStatusV1.NO_ACTIVE_SESSION, sessionId
        )
        return if (store.requestAiCancel(sessionId)) AiBatchCommandReceiptV1(
            AiBatchCommandStatusV1.CANCEL_REQUESTED, sessionId
        ) else AiBatchCommandReceiptV1(AiBatchCommandStatusV1.STALE, sessionId, "批次状态已变化")
    }

    override fun recoverInterruptedBatch(): Boolean = store.recoverInterruptedAi()

    override suspend fun runReviewedBatch(
        review: AiBatchReviewV1,
        approval: AiBatchApprovalV1,
        onProgress: (AiBatchProgressV1) -> Unit
    ): AiBatchExecutionReceiptV1 {
        val snapshot = review.detached()
        val approved = synchronized(approvalLock) {
            val claims = batchApprovals.remove(approval)
            claims == snapshot
        }
        if (!approved) return rejectedBatch("批次批准无效或已使用")
        validateReview(snapshot)?.let { return rejectedBatch(it) }

        val session = try {
            withContext(Dispatchers.IO) {
                store.beginAiBatch(
                    reviewed = snapshot.items.associate { it.draftId to it.draftVersion },
                    allowedOperations = snapshot.items.mapTo(linkedSetOf()) {
                        DraftOperation.valueOf(it.operation.name)
                    }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return AiBatchExecutionReceiptV1(
                status = AiBatchExecutionStatusV1.START_REJECTED,
                message = error.message?.take(400) ?: "无法开始批次"
            )
        }

        val mode = when (snapshot.mode) {
            AiBatchModeV1.PUBLISH -> AiBatchRunner.Mode.PUBLISH
            AiBatchModeV1.DISCARD -> AiBatchRunner.Mode.DISCARD
        }
        fun publish(done: Int, total: Int) {
            runCatching {
                onProgress(AiBatchProgressV1(
                    sessionId = session.sessionId,
                    lockEpoch = session.lockEpoch,
                    mode = snapshot.mode,
                    completed = done.coerceIn(0, total),
                    total = total
                ))
            }
        }
        publish(0, session.scope.size)
        val report = AiBatchRunner(store).run(session, mode) { done, total -> publish(done, total) }
        return AiBatchExecutionReceiptV1(
            status = when {
                report.cancelled -> AiBatchExecutionStatusV1.CANCELLED
                report.failures.isNotEmpty() -> AiBatchExecutionStatusV1.FAILED
                else -> AiBatchExecutionStatusV1.COMPLETED
            },
            sessionId = session.sessionId,
            lockEpoch = session.lockEpoch,
            published = report.published,
            discarded = report.discarded,
            kept = report.kept,
            failures = report.failures.toList()
        )
    }

    override fun releaseStuckBatch(
        sessionId: String,
        lockEpoch: Long,
        approval: AiBatchStuckReleaseApprovalV1
    ): AiBatchCommandReceiptV1 {
        val approved = synchronized(approvalLock) {
            stuckReleaseApprovals.remove(approval) == (sessionId to lockEpoch)
        }
        if (!approved) return AiBatchCommandReceiptV1(
            AiBatchCommandStatusV1.NOT_AUTHORIZED, sessionId, "恢复批准无效或已使用"
        )
        val current = store.currentAiSession()
        if (current?.sessionId != sessionId || current.lockEpoch != lockEpoch) {
            return AiBatchCommandReceiptV1(AiBatchCommandStatusV1.STALE, sessionId)
        }
        if (!PowerStore.canReleaseStuckAiBatch(current, clock.millis())) {
            return AiBatchCommandReceiptV1(AiBatchCommandStatusV1.NOT_ELIGIBLE, sessionId)
        }
        return if (store.releaseStuckAiBatch(sessionId, lockEpoch, clock.millis())) {
            AiBatchCommandReceiptV1(AiBatchCommandStatusV1.RELEASED, sessionId)
        } else AiBatchCommandReceiptV1(AiBatchCommandStatusV1.STALE, sessionId)
    }

    internal fun issueHostBatchApproval(review: AiBatchReviewV1): AiBatchApprovalV1? {
        val snapshot = review.detached()
        if (validateReview(snapshot) != null) return null
        val approval = HostBatchApproval()
        synchronized(approvalLock) { batchApprovals[approval] = snapshot }
        return approval
    }

    internal fun issueHostStuckReleaseApproval(
        sessionId: String,
        lockEpoch: Long
    ): AiBatchStuckReleaseApprovalV1? {
        val current = store.currentAiSession() ?: return null
        if (current.sessionId != sessionId || current.lockEpoch != lockEpoch ||
            !PowerStore.canReleaseStuckAiBatch(current, clock.millis())) return null
        val approval = HostStuckReleaseApproval()
        synchronized(approvalLock) { stuckReleaseApprovals[approval] = sessionId to lockEpoch }
        return approval
    }

    private fun validateGenerationRequest(
        request: AiProposalGenerationRequestV1
    ): Pair<String, String>? {
        if (request.requestId.isBlank() || request.requestId.length > MAX_REQUEST_ID_CHARS) {
            return "REQUEST_ID_INVALID" to "AI 请求标识无效"
        }
        if ((request.userText.isNullOrBlank()) == (request.task == null)) {
            return "INPUT_INVALID" to "需要且只能提供一项文本或多轮任务"
        }
        if (request.task != null && request.userText != null) {
            return "INPUT_INVALID" to "文本与多轮任务不能同时提交"
        }
        if (runCatching { ZoneId.of(request.zoneId) }.isFailure) {
            return "ZONE_INVALID" to "AI 请求时区无效"
        }
        val task = request.task ?: return null
        if (task.taskId.isBlank() || task.revision <= 0 || task.userEvidence.isEmpty() ||
            task.userEvidence.size > MAX_TASK_EVIDENCE_COUNT ||
            task.userEvidence.sumOf { it.text.length } > MAX_TASK_EVIDENCE_CHARS ||
            task.userEvidence.first().messageId != task.taskId ||
            task.userEvidence.first().createdAtMillis != task.anchorMillis ||
            task.userEvidence.any { it.messageId.isBlank() || it.text.isBlank() ||
                runCatching { ZoneId.of(it.zoneId) }.isFailure } ||
            task.assistantSuggestions.size > MAX_TASK_SUGGESTIONS ||
            task.assistantSuggestions.any { it.messageId.isBlank() }
        ) return "TASK_INVALID" to "多轮任务证据无效"
        if (task.zoneId != request.zoneId ||
            runCatching { ZoneId.of(task.zoneId) }.isFailure ||
            clock.millis() < task.anchorMillis ||
            clock.millis() - task.anchorMillis > MAX_TASK_AGE_MILLIS ||
            task.userEvidence.any { it.createdAtMillis > clock.millis() }
        ) return "TASK_EXPIRED" to "多轮任务已过期或时区不一致，请重新说明日程"
        return null
    }

    private fun validateCandidates(items: List<AiBatchCandidateV1>): String? {
        if (items.isEmpty()) return null
        if (items.size > MAX_BATCH_ITEMS) return "待处理超过 100 张，请先缩小范围"
        if (items.any { it.draftId <= 0L || it.draftVersion < 0L }) {
            return "待处理草稿快照无效"
        }
        if (items.map { it.draftId }.distinct().size != items.size) return "待处理草稿 ID 重复"
        return null
    }

    private fun validateReview(review: AiBatchReviewV1): String? {
        if (review.items.isEmpty() || review.items.size > MAX_BATCH_ITEMS) return "批次范围无效"
        return validateCandidates(review.items)
    }

    private fun selectionInvalid(message: String) = AiBatchSelectionReceiptV1(
        status = AiBatchSelectionStatusV1.INVALID,
        message = message.take(300)
    )

    private fun rejectedBatch(message: String) = AiBatchExecutionReceiptV1(
        status = AiBatchExecutionStatusV1.START_REJECTED,
        message = message.take(400)
    )

    private fun PowerStore.AiSession.toApiSession(nowMillis: Long) = AiBatchSessionV1(
        sessionId = sessionId,
        lockEpoch = lockEpoch,
        scope = scope.toSet(),
        reviewedVersions = reviewedVersions.toMap(),
        allowedOperations = allowedOperations.mapTo(linkedSetOf()) {
            DraftOperationV1.valueOf(it.name)
        },
        state = AiBatchStateV1.valueOf(state.name),
        startedAtMillis = startedAtMillis,
        updatedAtMillis = updatedAtMillis,
        terminalReport = report.toApiTerminalReport(),
        canReleaseStuck = PowerStore.canReleaseStuckAiBatch(this, nowMillis)
    )

    private fun String?.toApiTerminalReport(): AiBatchTerminalReportV1? {
        val json = this?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val mode = json.optString("mode").takeIf {
            it == AiBatchModeV1.PUBLISH.name || it == AiBatchModeV1.DISCARD.name
        }?.let(AiBatchModeV1::valueOf)
        val known = json.optBoolean("outcomeKnown",
            json.has("published") && json.has("discarded") && json.has("kept"))
        return AiBatchTerminalReportV1(
            mode = mode,
            outcomeKnown = known,
            published = if (known && json.has("published")) json.optInt("published").coerceAtLeast(0)
                else null,
            discarded = if (known && json.has("discarded")) json.optInt("discarded").coerceAtLeast(0)
                else null,
            kept = if (known && json.has("kept")) json.optInt("kept").coerceAtLeast(0) else null,
            cancelled = json.optBoolean("cancelled").takeIf { json.has("cancelled") },
            hasFailures = json.optString("failures").isNotBlank(),
            releasedByUser = json.optString("released") == "user",
            finishedAtMillis = json.optLong("finishedAt").takeIf { json.has("finishedAt") }
        )
    }

    private fun AiProposalGenerationRequestV1.detached() = copy(
        task = task?.let { taskSnapshot ->
            taskSnapshot.copy(
                userEvidence = taskSnapshot.userEvidence.map { it.copy() },
                assistantSuggestions = taskSnapshot.assistantSuggestions.map { it.copy() }
            )
        }
    )

    private fun AiBatchReviewV1.detached() = copy(items = items.map { it.copy() })

    private fun AiProposalReviewV1.detached() = copy(
        items = items.map { item -> item.copy(form = item.form.detached()) },
        warnings = warnings.toList(),
        task = task?.copy()
    )

    private fun EventFormV1.detached() = copy(
        recurrence = recurrence.copy(
            byDay = recurrence.byDay.toSet(),
            exceptions = recurrence.exceptions.toSet(),
            overrides = recurrence.overrides.mapValues { (_, value) -> value.copy() }
        ),
        completedKeys = completedKeys.toSet()
    )

    private class HostBatchApproval : AiBatchApprovalV1
    private class HostStuckReleaseApproval : AiBatchStuckReleaseApprovalV1

    private companion object {
        const val MAX_REQUEST_ID_CHARS = 160
        const val MAX_BATCH_ITEMS = 100
        const val MAX_BATCH_REQUEST_CHARS = 2_500
        const val MAX_AI_PROPOSALS = 100
        const val MAX_CALENDAR_CONTEXT_EVENTS = 60
        const val MAX_TASK_EVIDENCE_COUNT = 40
        const val MAX_TASK_EVIDENCE_CHARS = 20_000
        const val MAX_TASK_SUGGESTIONS = 40
        const val MAX_TASK_AGE_MILLIS = 24L * 60 * 60 * 1000
        const val CALENDAR_CONTEXT_HORIZON_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
