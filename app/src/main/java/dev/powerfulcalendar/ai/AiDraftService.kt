package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Clock

/** Turns a validated interpretation into real draft cards. Multiple cards may share one eventSeq. */
class AiDraftService(
    private val store: PowerStore,
    private val clock: Clock = Clock.systemUTC()
) {
    data class Created(val draftIds: List<Long>, val warnings: List<String>)

    data class ReviewedApply(
        val handledIndices: Set<Int>,
        val stagedCount: Int,
        val publishedCount: Int,
        val alreadyResolvedCount: Int,
        val warnings: List<String>
    )

    fun createDrafts(interpretation: AiInterpretation): Created {
        val ids = mutableListOf<Long>()
        val warnings = interpretation.warnings.toMutableList()
        if (interpretation.taskSnapshot != null) {
            warnings.add("自动任务候选必须先经审核卡明确选择；未写入草稿")
            return Created(ids, warnings)
        }
        interpretation.proposals.forEachIndexed { index, proposal ->
            if (proposal.blocked != null) {
                warnings.add(proposal.blocked)
                return@forEachIndexed
            }
            val operation = proposal.operation
            val target = proposal.eventSeq
            // `source` is already correct: CREATE is AI-authored, REPLACE keeps the real provenance.
            val form = proposal.form
            if (operation != DraftOperation.CREATE && (target == null || store.get(target) == null)) {
                warnings.add("第 ${index + 1} 项目标事件不存在，未生成草稿")
                return@forEachIndexed
            }
            runCatching {
                val card = if (operation == DraftOperation.CREATE) {
                    store.newDraft(form, null, DraftOperation.CREATE, actor = "ai")
                } else {
                    store.newDraft(form, target, operation, actor = "ai")
                }
                ids.add(card.draftId)
            }.onFailure {
                warnings.add("第 ${index + 1} 项未能生成草稿：" + (it.message ?: "未知错误"))
            }
        }
        return Created(ids, warnings)
    }

    /**
     * Applies only the candidate positions explicitly selected in the review UI. Automatic task
     * previews use durable task receipts; older/manual proposal paths retain their existing draft
     * behavior. A retry can therefore neither allocate a second identity nor publish twice.
     */
    fun applyReviewedProposals(
        interpretation: AiInterpretation,
        selectedPositions: Set<Int>,
        publish: Boolean
    ): ReviewedApply {
        val task = interpretation.taskSnapshot
        if (task != null && !task.isValidAt(clock.millis())) {
            return ReviewedApply(
                handledIndices = emptySet(),
                stagedCount = 0,
                publishedCount = 0,
                alreadyResolvedCount = 0,
                warnings = listOf("待处理 AI 任务已过期，请重新说明任务后再生成候选")
            )
        }
        val handled = mutableSetOf<Int>()
        val warnings = mutableListOf<String>()
        var staged = 0
        var published = 0
        var alreadyResolved = 0

        selectedPositions.sorted().forEach { position ->
            val proposal = interpretation.proposals.getOrNull(position) ?: return@forEach
            if (proposal.blocked != null) {
                warnings.add(proposal.blocked)
                return@forEach
            }
            try {
                if (task == null) {
                    val target = proposal.eventSeq
                    if (proposal.operation != DraftOperation.CREATE &&
                        (target == null || store.get(target) == null)
                    ) {
                        warnings.add("第 ${position + 1} 项目标事件不存在，未生成草稿")
                        return@forEach
                    }
                    val draft = if (proposal.operation == DraftOperation.CREATE) {
                        store.newDraft(proposal.form, null, DraftOperation.CREATE, actor = "ai")
                    } else {
                        store.newDraft(proposal.form, proposal.eventSeq, proposal.operation, actor = "ai")
                    }
                    handled.add(position)
                    if (publish) {
                        when (val result = store.commit(draft.draftId, actor = "user")) {
                            is PowerStore.CommitOutcome.Committed -> published++
                            else -> {
                                staged++
                                warnings.add("序号 ${draft.eventSeq} 未发布；草稿仍可编辑")
                            }
                        }
                    } else staged++
                    return@forEach
                }

                when (val result = store.stageAiTaskProposal(
                    taskId = task.taskId,
                    taskRevision = task.revision,
                    itemIndex = proposal.sourceIndex,
                    form = proposal.form,
                    operation = proposal.operation,
                    eventSeq = proposal.eventSeq
                )) {
                    is PowerStore.AiTaskProposalStage.Pending -> {
                        handled.add(position)
                        if (publish) {
                            when (val outcome = store.commit(
                                result.draft.draftId,
                                actor = "user",
                                idempotencyKey = result.idempotencyKey
                            )) {
                                is PowerStore.CommitOutcome.Committed ->
                                    if (outcome.duplicate) alreadyResolved++ else published++
                                else -> {
                                    staged++
                                    warnings.add(
                                        "序号 ${result.draft.eventSeq} 未发布；草稿仍可编辑")
                                }
                            }
                        } else staged++
                    }
                    is PowerStore.AiTaskProposalStage.Committed -> {
                        handled.add(position)
                        alreadyResolved++
                    }
                    PowerStore.AiTaskProposalStage.Discarded -> {
                        handled.add(position)
                        alreadyResolved++
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                warnings.add("第 ${position + 1} 项未能写入：" +
                    (error.message ?: error.javaClass.simpleName))
            }
        }
        return ReviewedApply(handled, staged, published, alreadyResolved, warnings)
    }
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
