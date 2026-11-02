package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftSourceRevisionMismatchException
import dev.powerfulcalendar.core.DraftTargetOccupiedException
import dev.powerfulcalendar.core.PowerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Clock

/** Turns a validated interpretation into real draft cards. */
class AiDraftService(
    private val store: PowerStore,
    private val clock: Clock = Clock.systemUTC()
) {
    data class Created(
        val draftIds: List<Long>,
        val warnings: List<String>,
        val itemResults: List<AiDraftItemResult> = emptyList()
    )

    data class ReviewedApply(
        val handledIndices: Set<Int>,
        val stagedCount: Int,
        val publishedCount: Int,
        val alreadyResolvedCount: Int,
        val warnings: List<String>,
        val itemResults: List<AiDraftItemResult> = emptyList()
    )

    enum class ItemState { STAGED, PUBLISHED, ALREADY_RESOLVED, CONFLICT, BLOCKED, FAILED }

    data class AiDraftItemResult(
        val position: Int,
        val sourceIndex: Int,
        val operation: DraftOperation,
        val state: ItemState,
        val eventSeq: Long? = null,
        val draftId: Long? = null,
        val draftVersion: Long? = null,
        val revision: Long? = null,
        val commitId: String? = null,
        val changed: Boolean = false,
        val message: String? = null
    )

    fun createDrafts(
        interpretation: AiInterpretation,
        rejectExistingTargetDrafts: Boolean = false
    ): Created {
        val ids = mutableListOf<Long>()
        val warnings = interpretation.warnings.toMutableList()
        val itemResults = mutableListOf<AiDraftItemResult>()
        val duplicateTargets = if (rejectExistingTargetDrafts) {
            interpretation.proposals.asSequence()
                .filter { it.blocked == null && it.operation != DraftOperation.CREATE }
                .mapNotNull { it.eventSeq }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        } else emptySet()
        if (interpretation.taskSnapshot != null) {
            val warning = "自动任务候选必须先经审核卡明确选择；未写入草稿"
            warnings.add(warning)
            interpretation.proposals.forEachIndexed { index, proposal ->
                itemResults += AiDraftItemResult(index, proposal.sourceIndex, proposal.operation,
                    ItemState.FAILED, eventSeq = proposal.eventSeq, message = warning)
            }
            return Created(ids, warnings, itemResults)
        }
        interpretation.proposals.forEachIndexed { index, proposal ->
            if (proposal.blocked != null) {
                warnings.add(proposal.blocked)
                itemResults += AiDraftItemResult(index, proposal.sourceIndex, proposal.operation,
                    ItemState.BLOCKED, eventSeq = proposal.eventSeq, message = proposal.blocked)
                return@forEachIndexed
            }
            if (proposal.operation != DraftOperation.CREATE && proposal.eventSeq != null &&
                proposal.eventSeq in duplicateTargets
            ) {
                val warning = "同一审核批次包含多个针对同一事件的提案；这些项目均未暂存"
                warnings.add(warning)
                itemResults += AiDraftItemResult(index, proposal.sourceIndex, proposal.operation,
                    ItemState.BLOCKED, eventSeq = proposal.eventSeq, message = warning)
                return@forEachIndexed
            }
            val operation = proposal.operation
            val target = proposal.eventSeq
            val targetRecord = target?.let(store::get)
            if (rejectExistingTargetDrafts && operation != DraftOperation.CREATE &&
                (targetRecord == null || targetRecord.revision != proposal.expectedRevision)
            ) {
                val warning = "第 ${index + 1} 项目标事件已变化；未写入该草稿"
                warnings.add(warning)
                itemResults += AiDraftItemResult(index, proposal.sourceIndex, operation,
                    ItemState.BLOCKED, eventSeq = target, message = warning)
                return@forEachIndexed
            }
            // `source` is already correct: CREATE is AI-authored, REPLACE keeps the real provenance.
            val form = proposal.form
            if (operation != DraftOperation.CREATE && (target == null || targetRecord == null)) {
                val warning = "第 ${index + 1} 项目标事件不存在，未生成草稿"
                warnings.add(warning)
                itemResults += AiDraftItemResult(index, proposal.sourceIndex, operation,
                    ItemState.FAILED, eventSeq = target, message = warning)
                return@forEachIndexed
            }
            runCatching {
                val card = if (operation == DraftOperation.CREATE) {
                    store.newDraft(form, null, DraftOperation.CREATE, actor = "ai")
                } else {
                    store.newDraft(form, target, operation, actor = "ai",
                        expectedRevision = proposal.expectedRevision,
                        rejectExistingDraft = rejectExistingTargetDrafts)
                }
                ids.add(card.draftId)
                itemResults += AiDraftItemResult(index, proposal.sourceIndex, operation,
                    ItemState.STAGED, card.eventSeq, card.draftId, card.draftVersion,
                    changed = true)
            }.onFailure {
                val warning = "第 ${index + 1} 项未能生成草稿：" + (it.message ?: "未知错误")
                warnings.add(warning)
                itemResults += AiDraftItemResult(index, proposal.sourceIndex, operation,
                    if (it is DraftSourceRevisionMismatchException ||
                        it is DraftTargetOccupiedException) ItemState.CONFLICT else ItemState.FAILED,
                    eventSeq = target, message = warning)
            }
        }
        return Created(ids, warnings, itemResults)
    }

    /**
     * Applies only the candidate positions explicitly selected in the review UI. Automatic task
     * previews use durable task receipts; older/manual proposal paths retain their existing draft
     * behavior. A retry can therefore neither allocate a second identity nor publish twice.
     */
    internal fun applyReviewedProposals(
        interpretation: AiInterpretation,
        selectedPositions: Set<Int>,
        publish: Boolean,
        rejectExistingTargetDrafts: Boolean = false,
        allowExpiredReceiptReplay: Boolean = false,
        taskContext: ReviewedAiTaskContext? = interpretation.taskSnapshot?.let {
            ReviewedAiTaskContext(
                taskId = it.taskId,
                revision = it.revision,
                anchorMillis = it.anchorMillis,
                latestEvidenceMillis = it.userEvidence.maxOfOrNull { evidence ->
                    evidence.createdAtMillis
                } ?: it.anchorMillis
            )
        }
    ): ReviewedApply {
        val task = taskContext
        val expiredTask = task?.takeIf { !it.isValidAt(clock.millis()) }
        val taskExpired = expiredTask != null
        val expiredReceiptsMatch = expiredTask != null && allowExpiredReceiptReplay &&
            selectedPositions.all { position ->
                val proposal = interpretation.proposals.getOrNull(position) ?: return@all false
                runCatching {
                    store.hasAiTaskProposalReceipt(
                        taskId = expiredTask.taskId,
                        taskRevision = expiredTask.revision,
                        itemIndex = proposal.sourceIndex,
                        form = proposal.form,
                        operation = proposal.operation,
                        eventSeq = proposal.eventSeq,
                        expectedTargetRevision = proposal.expectedRevision,
                        terminalOnly = publish
                    )
                }.getOrDefault(false)
            }
        if (taskExpired && !expiredReceiptsMatch) {
            return ReviewedApply(
                handledIndices = emptySet(),
                stagedCount = 0,
                publishedCount = 0,
                alreadyResolvedCount = 0,
                warnings = listOf("待处理 AI 任务已过期，请重新说明任务后再生成候选"),
                itemResults = selectedPositions.sorted().mapNotNull { position ->
                    interpretation.proposals.getOrNull(position)?.let { proposal ->
                        AiDraftItemResult(position, proposal.sourceIndex, proposal.operation,
                            ItemState.FAILED, eventSeq = proposal.eventSeq,
                            message = "待处理 AI 任务已过期，请重新说明任务后再生成候选")
                    }
                }
            )
        }
        val handled = mutableSetOf<Int>()
        val warnings = mutableListOf<String>()
        val itemResults = mutableListOf<AiDraftItemResult>()
        var staged = 0
        var published = 0
        var alreadyResolved = 0

        selectedPositions.sorted().forEach { position ->
            val proposal = interpretation.proposals.getOrNull(position) ?: return@forEach
            if (proposal.blocked != null) {
                warnings.add(proposal.blocked)
                itemResults += AiDraftItemResult(position, proposal.sourceIndex, proposal.operation,
                    ItemState.BLOCKED, eventSeq = proposal.eventSeq, message = proposal.blocked)
                return@forEach
            }
            val publishValidationError = if (publish && proposal.operation != DraftOperation.DELETE) {
                when (val built = proposal.form.build(seq = 1L, revision = 1L,
                    nowMillis = clock.millis())) {
                    is dev.powerfulcalendar.core.FormBuild.Ready -> null
                    is dev.powerfulcalendar.core.FormBuild.Incomplete -> built.message
                    is dev.powerfulcalendar.core.FormBuild.Invalid -> built.reason
                }
            } else null
            if (publishValidationError != null) {
                val warning = "第 ${position + 1} 项未补全或无效，禁止直接发布：$publishValidationError；请先写入草稿并修正"
                warnings.add(warning)
                itemResults += AiDraftItemResult(position, proposal.sourceIndex, proposal.operation,
                    ItemState.FAILED, eventSeq = proposal.eventSeq, message = warning)
                return@forEach
            }
            try {
                if (task == null) {
                    val target = proposal.eventSeq
                    if (proposal.operation != DraftOperation.CREATE &&
                        (target == null || store.get(target) == null)
                    ) {
                        val warning = "第 ${position + 1} 项目标事件不存在，未生成草稿"
                        warnings.add(warning)
                        itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                            proposal.operation, ItemState.FAILED, eventSeq = target,
                            message = warning)
                        return@forEach
                    }
                    val draft = if (proposal.operation == DraftOperation.CREATE) {
                        store.newDraft(proposal.form, null, DraftOperation.CREATE, actor = "ai")
                    } else {
                        store.newDraft(proposal.form, proposal.eventSeq, proposal.operation,
                            actor = "ai", expectedRevision = proposal.expectedRevision,
                            rejectExistingDraft = rejectExistingTargetDrafts)
                    }
                    if (publish) {
                        when (val result = store.commit(draft.draftId, actor = "user",
                            expectedCurrentRevision = draft.baseRevision,
                            expectedDraftVersion = draft.draftVersion)) {
                            is PowerStore.CommitOutcome.Committed -> {
                                handled.add(position)
                                published++
                                itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                    proposal.operation, ItemState.PUBLISHED, draft.eventSeq,
                                    draft.draftId, draft.draftVersion, result.revision,
                                    result.commitId, changed = !result.duplicate)
                            }
                            is PowerStore.CommitOutcome.Conflict,
                            is PowerStore.CommitOutcome.DraftVersionMismatch -> {
                                val warning = "序号 ${draft.eventSeq} 的审核版本已变化；正式事件未发布"
                                warnings.add(warning)
                                itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                    proposal.operation, ItemState.CONFLICT, draft.eventSeq,
                                    draft.draftId, draft.draftVersion, message = warning)
                            }
                            is PowerStore.CommitOutcome.Invalid -> {
                                val invalidDraft = store.draft(draft.draftId) ?: draft
                                val warning = "序号 ${draft.eventSeq} 未发布：${result.reason}；草稿仍可在草稿队列中修正"
                                warnings.add(warning)
                                handled.add(position)
                                itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                    proposal.operation, ItemState.FAILED, invalidDraft.eventSeq,
                                    invalidDraft.draftId, invalidDraft.draftVersion,
                                    changed = true, message = warning)
                            }
                            else -> {
                                handled.add(position)
                                staged++
                                val warning = "序号 ${draft.eventSeq} 未发布；草稿仍可编辑"
                                warnings.add(warning)
                                itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                    proposal.operation, ItemState.STAGED, draft.eventSeq,
                                    draft.draftId, draft.draftVersion, changed = true,
                                    message = warning)
                            }
                        }
                    } else {
                        handled.add(position)
                        staged++
                        itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                            proposal.operation, ItemState.STAGED, draft.eventSeq,
                            draft.draftId, draft.draftVersion, changed = true)
                    }
                    return@forEach
                }

                when (val result = store.stageAiTaskProposal(
                    taskId = task.taskId,
                    taskRevision = task.revision,
                    itemIndex = proposal.sourceIndex,
                    form = proposal.form,
                    operation = proposal.operation,
                    eventSeq = proposal.eventSeq,
                    expectedTargetRevision = proposal.expectedRevision,
                    requireExistingReceipt = taskExpired,
                    requireTerminalReceipt = taskExpired && publish
                )) {
                    is PowerStore.AiTaskProposalStage.Pending -> {
                        if (publish) {
                            if (result.draft.form != proposal.form ||
                                result.draft.operation != proposal.operation
                            ) {
                                val warning = "已审核的 AI 任务草稿在审核后发生变化；请重新审核当前草稿"
                                warnings.add(warning)
                                itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                    proposal.operation, ItemState.FAILED, result.draft.eventSeq,
                                    result.draft.draftId, result.draft.draftVersion,
                                    message = warning)
                                return@forEach
                            }
                            when (val outcome = store.commit(
                                result.draft.draftId,
                                actor = "user",
                                idempotencyKey = result.idempotencyKey,
                                expectedCurrentRevision = result.draft.baseRevision,
                                expectedDraftVersion = result.draft.draftVersion
                            )) {
                                is PowerStore.CommitOutcome.Committed -> {
                                    handled.add(position)
                                    if (outcome.duplicate) alreadyResolved++ else published++
                                    itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                        proposal.operation,
                                        if (outcome.duplicate) ItemState.ALREADY_RESOLVED
                                        else ItemState.PUBLISHED,
                                        result.draft.eventSeq, result.draft.draftId,
                                        result.draft.draftVersion, outcome.revision,
                                        outcome.commitId, changed = !outcome.duplicate)
                                }
                                is PowerStore.CommitOutcome.Conflict,
                                is PowerStore.CommitOutcome.DraftVersionMismatch -> {
                                    val warning = "序号 ${result.draft.eventSeq} 的审核版本已变化；正式事件未发布"
                                    warnings.add(warning)
                                    itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                        proposal.operation, ItemState.CONFLICT,
                                        result.draft.eventSeq, result.draft.draftId,
                                        result.draft.draftVersion, message = warning)
                                }
                                is PowerStore.CommitOutcome.Invalid -> {
                                    val invalidDraft = store.draft(result.draft.draftId) ?: result.draft
                                    val warning = "序号 ${result.draft.eventSeq} 未发布：${outcome.reason}；草稿仍可在草稿队列中修正"
                                    warnings.add(warning)
                                    handled.add(position)
                                    itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                        proposal.operation, ItemState.FAILED,
                                        invalidDraft.eventSeq, invalidDraft.draftId,
                                        invalidDraft.draftVersion, changed = !result.reused,
                                        message = warning)
                                }
                                else -> {
                                    handled.add(position)
                                    staged++
                                    val warning = "序号 ${result.draft.eventSeq} 未发布；草稿仍可编辑"
                                    warnings.add(warning)
                                    itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                        proposal.operation, ItemState.STAGED,
                                        result.draft.eventSeq, result.draft.draftId,
                                        result.draft.draftVersion, changed = !result.reused,
                                        message = warning)
                                }
                            }
                        } else {
                            handled.add(position)
                            staged++
                            itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                                proposal.operation, ItemState.STAGED, result.draft.eventSeq,
                                result.draft.draftId, result.draft.draftVersion,
                                changed = !result.reused)
                        }
                    }
                    is PowerStore.AiTaskProposalStage.Committed -> {
                        handled.add(position)
                        alreadyResolved++
                        itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                            proposal.operation, ItemState.ALREADY_RESOLVED,
                            result.eventSeq, revision = result.revision,
                            commitId = result.commitId)
                    }
                    PowerStore.AiTaskProposalStage.Discarded -> {
                        handled.add(position)
                        alreadyResolved++
                        itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                            proposal.operation, ItemState.ALREADY_RESOLVED,
                            proposal.eventSeq, message = "提案已由此前操作处理")
                    }
                    PowerStore.AiTaskProposalStage.Expired -> {
                        val warning = "待处理 AI 任务已过期，且没有可重放的对应项目收据"
                        warnings.add(warning)
                        itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                            proposal.operation, ItemState.FAILED, proposal.eventSeq,
                            message = warning)
                    }
                    is PowerStore.AiTaskProposalStage.Conflict -> {
                        val warning = "目标事件 revision 已变化；本项没有暂存或发布"
                        warnings.add(warning)
                        itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                            proposal.operation, ItemState.CONFLICT, proposal.eventSeq,
                            revision = result.currentRevision, message = warning)
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val warning = "第 ${position + 1} 项未能写入：" +
                    (error.message ?: error.javaClass.simpleName)
                warnings.add(warning)
                itemResults += AiDraftItemResult(position, proposal.sourceIndex,
                    proposal.operation,
                    if (error is DraftSourceRevisionMismatchException ||
                        error is DraftTargetOccupiedException) ItemState.CONFLICT else ItemState.FAILED,
                    eventSeq = proposal.eventSeq,
                    message = warning)
            }
        }
        return ReviewedApply(handled, staged, published, alreadyResolved, warnings, itemResults)
    }
}

/** App-internal projection that omits private conversation evidence from the stable API DTO. */
internal data class ReviewedAiTaskContext(
    val taskId: String,
    val revision: Int,
    val anchorMillis: Long,
    val latestEvidenceMillis: Long
) {
    fun isValidAt(nowMillis: Long): Boolean = nowMillis >= anchorMillis &&
        nowMillis - anchorMillis <= AiConversationTaskSnapshot.MAX_TASK_AGE_MILLIS &&
        latestEvidenceMillis <= nowMillis
}

/**
 * The single AI's exclusive batch. It does not call the network: the cards it acts on are the
 * proposals already produced and shown to the user, so every decision is inspectable beforehand.
 *
 * Fencing: each write goes through [PowerStore.commit]/[PowerStore.discard] with the session, so a
 * cancellation, a newer epoch or a restart rejects the remaining work. Cancellation between items
 * is deterministic because the persisted session state is re-read before every item.
 */
class AiBatchRunner(private val store: PowerStore) {

    enum class Mode { PUBLISH, DISCARD }

    data class Report(
        val mode: Mode,
        val published: Int,
        val discarded: Int,
        val kept: Int,
        val cancelled: Boolean,
        val failures: List<String>,
        val finishedAtMillis: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("mode", mode.name)
            put("published", published)
            put("discarded", discarded)
            put("kept", kept)
            put("cancelled", cancelled)
            put("failures", failures.joinToString(" | "))
            put("finishedAt", finishedAtMillis)
        }
    }

    suspend fun run(
        session: PowerStore.AiSession,
        mode: Mode,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Report = withContext(Dispatchers.IO) {
        val scope = session.scope
        var done = 0
        var published = 0
        var discarded = 0
        var kept = 0
        var cancelled = false
        val failures = mutableListOf<String>()
        var report: Report? = null
        try {
            for (draftId in scope) {
                val current = store.refreshSession(session.sessionId)
                if (current == null || !current.isActive || current.state != PowerStore.AiState.RUNNING) {
                    cancelled = true
                    break
                }
                store.heartbeat(current.sessionId)
                try {
                    when (mode) {
                        Mode.PUBLISH -> when (val outcome = store.commit(
                            draftId,
                            approveStale = false,
                            actor = "ai",
                            aiSession = current
                        )) {
                            is PowerStore.CommitOutcome.Committed -> published++
                            is PowerStore.CommitOutcome.Conflict,
                            is PowerStore.CommitOutcome.DraftVersionMismatch,
                            is PowerStore.CommitOutcome.NeedsRestore,
                            PowerStore.CommitOutcome.NotAuthorized -> kept++
                            is PowerStore.CommitOutcome.Invalid -> {
                                kept++
                                failures.add("#$draftId：${outcome.reason}")
                            }
                            PowerStore.CommitOutcome.Locked -> {
                                cancelled = true
                            }
                        }
                        Mode.DISCARD -> {
                            if (store.discard(draftId, current)) discarded++ else kept++
                        }
                    }
                } catch (cancelledByCaller: kotlinx.coroutines.CancellationException) {
                    throw cancelledByCaller
                } catch (error: Exception) {
                    kept++
                    failures.add("#$draftId：${error.message ?: error.javaClass.simpleName}")
                }
                done++
                onProgress(done, scope.size)
                delay(120)
            }
            // Build the report only after the loop finishes so `finally` can distinguish a clean
            // finish from an abort.
            report = Report(mode, published, discarded, kept, cancelled, failures)
        } catch (cancelledByCaller: kotlinx.coroutines.CancellationException) {
            cancelled = true
            throw cancelledByCaller
        } catch (error: Exception) {
            failures.add(error.message ?: error.javaClass.simpleName)
        } finally {
            // The lock must be released even when the coroutine was cancelled or threw: a stuck
            // epoch would make the whole deck permanently read-only (PROTOCOL §4).
            val finalReport = report ?: Report(
                mode = mode,
                published = published,
                discarded = discarded,
                kept = kept,
                cancelled = true,
                failures = failures
            )
            runCatching {
                withContext(kotlinx.coroutines.NonCancellable) {
                    store.finishAiBatch(
                        session.sessionId,
                        when {
                            finalReport.cancelled -> PowerStore.AiState.CANCELLED
                            finalReport.failures.isNotEmpty() -> PowerStore.AiState.FAILED
                            else -> PowerStore.AiState.COMPLETED
                        },
                        finalReport.toJson()
                    )
                }
            }
            report = finalReport
        }
        report ?: Report(mode, published, discarded, kept, cancelled = true, failures = failures)
    }
}
