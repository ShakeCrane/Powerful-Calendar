package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.ai.AiDraftService
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.ai.ReviewedAiTaskContext
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalRecord
import java.security.MessageDigest
import java.time.Clock
import java.util.IdentityHashMap

/** Store adapter for the platform-neutral, Host-owned AI proposal feature contract. */
internal class StoreAiProposalFeatureApiV1(
    private val store: PowerStore,
    private val events: StoreEventFeatureApiV1,
    private val clock: Clock
) : AiProposalFeatureApiV1 {
    override val apiVersion: Int = 1

    private val mutationLock = Any()
    private val receipts = LinkedHashMap<String, Pair<String, AiProposalApplyReceiptV1>>()
    private val approvals = IdentityHashMap<AiProposalPublishApprovalV1, ApprovalClaims>()

    override fun createDrafts(
        review: AiProposalReviewV1,
        requestId: String
    ): AiProposalApplyReceiptV1 = withPostLockNotifications { notifications ->
        if (!validRequestId(requestId)) return@withPostLockNotifications invalid(requestId,
            "REQUEST_ID_INVALID", "AI 草稿请求标识无效")
        val snapshot = review.detached()
        val fingerprint = fingerprint("CREATE_DRAFTS", snapshot)
        cached(requestId, fingerprint)?.let { return@withPostLockNotifications it }
        validateReview(snapshot)?.let {
            return@withPostLockNotifications remember(requestId, fingerprint,
                invalid(requestId, "AI_PROPOSAL_INVALID", it))
        }
        if (snapshot.task != null) return@withPostLockNotifications remember(requestId, fingerprint,
            receipt(requestId, AiProposalApplyStatusV1.INVALID,
                warnings = listOf("任务型提案必须经当前审核卡确认后写入")))

        val mutation = synchronized(store) {
            val current = captureStoreState()
            runCatching {
                AiDraftService(store, clock).createDrafts(
                    snapshot.toInterpretation(), rejectExistingTargetDrafts = true)
            }.fold(
                onSuccess = { result -> StoreMutation.Applied(result, changesSince(current)) },
                onFailure = { error -> StoreMutation.Rejected(receipt(requestId,
                    AiProposalApplyStatusV1.FAILED,
                    warnings = listOf(error.message ?: error.javaClass.simpleName))) }
            )
        }
        when (mutation) {
            is StoreMutation.Rejected -> remember(requestId, fingerprint, mutation.receipt)
            is StoreMutation.Applied -> {
                val refreshed = prepareChangesIfNeeded(mutation.changes, notifications)
                remember(requestId, fingerprint, mutation.result.toReceipt(requestId,
                    itemReceiptDurability = AiProposalItemReceiptDurabilityV1.PROCESS_LOCAL,
                    featureRevision = events.featureRevision,
                    refreshRequired = !refreshed))
            }
        }
    }

    override fun applyReviewedProposals(
        request: AiProposalApplyRequestV1,
        approval: AiProposalPublishApprovalV1?
    ): AiProposalApplyReceiptV1 = withPostLockNotifications { notifications ->
        val requestId = request.requestId
        if (!validRequestId(requestId)) return@withPostLockNotifications invalid(requestId,
            "REQUEST_ID_INVALID", "AI 提案请求标识无效")
        val snapshot = request.detached()
        val fingerprint = fingerprint(snapshot)
        cached(requestId, fingerprint)?.let { return@withPostLockNotifications it }
        validateRequest(snapshot)?.let {
            return@withPostLockNotifications remember(requestId, fingerprint,
                invalid(requestId, "AI_PROPOSAL_INVALID", it))
        }
        val itemDurability = AiProposalItemReceiptDurabilityV1.DURABLE_ITEM

        val taskExpired = snapshot.review.task?.isExpiredAt(clock.millis()) == true
        val exactReceiptReplayAvailable = snapshot.review.task != null && synchronized(store) {
            hasExactTaskReceipts(snapshot, terminalOnly =
                snapshot.decision == AiProposalDecisionV1.PUBLISH)
        }
        val expiredReplayAllowed = taskExpired && exactReceiptReplayAvailable
        if (taskExpired && !expiredReplayAllowed) return@withPostLockNotifications remember(
            requestId, fingerprint, receipt(requestId, AiProposalApplyStatusV1.EXPIRED,
                warnings = listOf("待处理 AI 任务已过期，请重新说明任务后再生成候选"),
                itemReceiptDurability = itemDurability))

        if (snapshot.decision == AiProposalDecisionV1.PUBLISH) {
            val claims = approval?.let(approvals::remove)
                ?: return@withPostLockNotifications remember(requestId, fingerprint,
                    receipt(requestId, AiProposalApplyStatusV1.NOT_AUTHORIZED,
                        warnings = listOf("正式发布缺少 Host 审核授权")))
            if (claims.fingerprint != fingerprint ||
                clock.millis() - claims.issuedAtMillis !in 0..APPROVAL_TTL_MILLIS
            ) return@withPostLockNotifications remember(requestId, fingerprint,
                receipt(requestId, AiProposalApplyStatusV1.NOT_AUTHORIZED,
                    warnings = listOf("Host 审核授权已失效或与当前提案不匹配")))
        } else if (approval != null) {
            return@withPostLockNotifications remember(requestId, fingerprint,
                receipt(requestId, AiProposalApplyStatusV1.NOT_AUTHORIZED,
                    warnings = listOf("草稿操作不能携带正式发布授权")))
        }

        val mutation = synchronized(store) {
            val staleTarget = validateTargets(snapshot)
            when {
                store.isLocked() && !expiredReplayAllowed -> StoreMutation.Rejected(receipt(requestId,
                    AiProposalApplyStatusV1.LOCKED,
                    warnings = listOf("AI 批次正在独占卡组；本次提案没有写入")))
                staleTarget != null -> StoreMutation.Rejected(receipt(requestId,
                    AiProposalApplyStatusV1.CONFLICT,
                    warnings = listOf(staleTarget),
                    itemReceiptDurability = itemDurability))
                else -> {
                    val before = captureStoreState()
                    runCatching {
                        AiDraftService(store, clock).applyReviewedProposals(
                            interpretation = snapshot.review.toInterpretation(),
                            selectedPositions = snapshot.selectedPositions,
                            publish = snapshot.decision == AiProposalDecisionV1.PUBLISH,
                            rejectExistingTargetDrafts = true,
                            taskContext = snapshot.toServiceContext(),
                            allowExpiredReceiptReplay = exactReceiptReplayAvailable
                        )
                    }.fold(
                        onSuccess = { result -> StoreMutation.Applied(result, changesSince(before)) },
                        onFailure = { error -> StoreMutation.Rejected(receipt(requestId,
                            AiProposalApplyStatusV1.FAILED,
                            warnings = listOf(error.message ?: error.javaClass.simpleName),
                            itemReceiptDurability = itemDurability)) }
                    )
                }
            }
        }
        when (mutation) {
            is StoreMutation.Rejected -> remember(requestId, fingerprint, mutation.receipt)
            is StoreMutation.Applied -> {
                val refreshed = prepareChangesIfNeeded(mutation.changes, notifications)
                remember(requestId, fingerprint, mutation.result.toReceipt(requestId,
                    itemReceiptDurability = itemDurability,
                    featureRevision = events.featureRevision,
                    refreshRequired = !refreshed))
            }
        }
    }

    /** Internal capability issuer; only the trusted Host invokes this after the user's publish action. */
    fun issueHostApproval(request: AiProposalApplyRequestV1): AiProposalPublishApprovalV1? =
        synchronized(mutationLock) {
            val snapshot = request.detached()
            val taskExpired = snapshot.review.task?.isExpiredAt(clock.millis()) == true
            if (snapshot.decision != AiProposalDecisionV1.PUBLISH ||
                !validRequestId(snapshot.requestId) || validateRequest(snapshot) != null
            ) return@synchronized null
            val expiredReplayAllowed = taskExpired && synchronized(store) {
                hasExactTaskReceipts(snapshot, terminalOnly = true)
            }
            if (taskExpired && !expiredReplayAllowed) return@synchronized null
            synchronized(store) {
                if ((store.isLocked() && !expiredReplayAllowed) ||
                    validateTargets(snapshot) != null) return@synchronized null
                val token = StoreAiProposalPublishApprovalV1()
                approvals[token] = ApprovalClaims(fingerprint(snapshot), clock.millis())
                while (approvals.size > MAX_APPROVALS) {
                    val oldest = approvals.keys.firstOrNull() ?: break
                    approvals.remove(oldest)
                }
                token
            }
        }

    private fun validateRequest(request: AiProposalApplyRequestV1): String? {
        validateReview(request.review)?.let { return it }
        if (request.selectedPositions.isEmpty() || request.selectedPositions.size > MAX_ITEMS)
            return "必须选择 1–$MAX_ITEMS 项 AI 提案"
        if (request.selectedPositions.any { it !in request.review.items.indices })
            return "AI 提案选择已过期或超出审核快照范围"
        val selected = request.selectedPositions.sorted().map(request.review.items::get)
        if (selected.any { it.blockedReason != null }) return "被阻止的 AI 提案不能写入"
        if (selected.map { it.sourceIndex }.distinct().size != selected.size)
            return "所选 AI 提案源索引重复"
        if (selected.filter { it.operation != DraftOperationV1.CREATE }
                .mapNotNull { it.eventSeq }.distinct().size !=
            selected.count { it.operation != DraftOperationV1.CREATE }
        ) return "同一审核批次不能包含多个针对同一事件的提案"
        if (selected.any { it.form.recurrenceScope != null || it.form.editOccurrenceKey != null })
            return "重复实例范围提案必须经过正式事件范围编辑流程"
        if (request.decision == AiProposalDecisionV1.PUBLISH && selected.any {
                it.operation != DraftOperationV1.DELETE && !it.form.isPublishableV1()
            }) return "所选提案包含未补全项目；请先写入草稿并补全"
        return null
    }

    private fun validateReview(review: AiProposalReviewV1): String? {
        if (!review.reviewId.matches(REVIEW_ID_PATTERN)) return "AI 审核标识无效"
        if (review.items.isEmpty() || review.items.size > MAX_ITEMS)
            return "AI 审核快照必须包含 1–$MAX_ITEMS 项"
        if (review.items.any { it.sourceIndex !in 0 until MAX_ITEMS }) return "AI 提案源索引无效"
        val task = review.task
        if (task != null && (!task.taskId.matches(TASK_ID_PATTERN) || task.revision <= 0 ||
                task.anchorMillis < 0L || task.latestEvidenceMillis < task.anchorMillis)) {
            return "AI 任务收据上下文无效"
        }
        review.items.forEach { item ->
            if (item.blockedReason != null) return@forEach
            if (item.operation == DraftOperationV1.CREATE) {
                if (item.eventSeq != null || item.expectedRevision != null)
                    return "新建提案不能绑定既有事件 revision"
            } else {
                val eventSeq = item.eventSeq
                val expectedRevision = item.expectedRevision
                if (eventSeq == null || eventSeq <= 0L || expectedRevision == null ||
                    expectedRevision <= 0L) return "修改或删除提案必须绑定目标事件与 revision"
            }
        }
        return null
    }

    private fun validateTargets(request: AiProposalApplyRequestV1): String? {
        val task = request.toServiceContext()
        for (position in request.selectedPositions.sorted()) {
            val item = request.review.items[position]
            if (item.operation == DraftOperationV1.CREATE) continue
            val current = item.eventSeq?.let(store::get)
            if (current?.revision == item.expectedRevision) continue
            val completedReplay = item.eventSeq != null && runCatching {
                store.hasAiTaskProposalReceipt(
                    taskId = task.taskId,
                    taskRevision = task.revision,
                    itemIndex = item.sourceIndex,
                    form = item.form.toCoreDraftForm(),
                    operation = DraftOperation.valueOf(item.operation.name),
                    eventSeq = item.eventSeq,
                    expectedTargetRevision = item.expectedRevision,
                    terminalOnly = false
                )
            }.getOrDefault(false)
            if (!completedReplay) return if (current == null)
                "目标事件已删除或不可用；没有执行本批提案"
            else "目标事件 revision 已变化；没有执行本批提案"
        }
        return null
    }

    private fun hasExactTaskReceipts(
        request: AiProposalApplyRequestV1,
        terminalOnly: Boolean
    ): Boolean {
        val task = request.review.task ?: return false
        return request.selectedPositions.sorted().all { position ->
            val item = request.review.items[position]
            runCatching {
                store.hasAiTaskProposalReceipt(
                    taskId = task.taskId,
                    taskRevision = task.revision,
                    itemIndex = item.sourceIndex,
                    form = item.form.toCoreDraftForm(),
                    operation = DraftOperation.valueOf(item.operation.name),
                    eventSeq = item.eventSeq,
                    expectedTargetRevision = item.expectedRevision,
                    terminalOnly = terminalOnly
                )
            }.getOrDefault(false)
        }
    }

    private fun captureStoreState(): StoreState = StoreState(
        formal = store.all().associateBy { it.eventSeq },
        drafts = store.pendingDrafts().associateBy { it.draftId }
    )

    /** Capture changes while Store is locked; invoke listeners only after releasing that lock. */
    private fun changesSince(before: StoreState): List<StoreChange>? = try {
        val after = captureStoreState()
        val changes = mutableListOf<StoreChange>()
        (before.formal.keys + after.formal.keys).toSortedSet().forEach { seq ->
            val old = before.formal[seq]
            val current = after.formal[seq]
            val type = when {
                old == null && current != null -> EventFeatureChangeTypeV1.FORMAL_CREATED
                old != null && current == null -> EventFeatureChangeTypeV1.FORMAL_DELETED
                old != null && current != null && old.revision != current.revision ->
                    EventFeatureChangeTypeV1.FORMAL_UPDATED
                else -> null
            }
            if (type != null) changes += StoreChange(type, seq)
        }
        if (before.drafts != after.drafts) changes += StoreChange(
            EventFeatureChangeTypeV1.DRAFTS_CHANGED, null)
        changes
    } catch (_: Exception) {
        null
    }

    private fun <T> withPostLockNotifications(
        operation: (MutableList<EventFeatureChangeV1>) -> T
    ): T {
        val notifications = mutableListOf<EventFeatureChangeV1>()
        val result = synchronized(mutationLock) { operation(notifications) }
        events.dispatchPreparedChanges(notifications)
        return result
    }

    private fun prepareChangesIfNeeded(
        changes: List<StoreChange>?,
        notifications: MutableList<EventFeatureChangeV1>
    ): Boolean {
        return try {
            if (changes == null) false
            else {
                changes.forEach { change ->
                    notifications += if (change.type == EventFeatureChangeTypeV1.DRAFTS_CHANGED)
                        events.prepareDeckDraftChange()
                    else events.prepareDeckFormalChange(change.type, requireNotNull(change.eventSeq))
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun cached(requestId: String, fingerprint: String): AiProposalApplyReceiptV1? {
        val previous = receipts[requestId] ?: return null
        return if (previous.first == fingerprint) previous.second.copy(
            itemReceipts = previous.second.itemReceipts.map { it.copy(changed = false) },
            duplicate = true,
            featureRevision = events.featureRevision
        ) else receipt(requestId, AiProposalApplyStatusV1.DUPLICATE_REQUEST_ID,
            warnings = listOf("同一请求标识已用于不同的 AI 提案内容"),
            errorCode = "DUPLICATE_REQUEST_ID",
            message = "请为修改后的提案生成新请求标识")
    }

    private fun remember(
        requestId: String,
        fingerprint: String,
        result: AiProposalApplyReceiptV1
    ): AiProposalApplyReceiptV1 {
        receipts[requestId] = fingerprint to result
        while (receipts.size > MAX_RECEIPTS) receipts.remove(receipts.keys.first())
        return result
    }

    private fun invalid(requestId: String, code: String, message: String) = receipt(
        requestId, AiProposalApplyStatusV1.INVALID,
        warnings = listOf(message), errorCode = code, message = message
    )

    private fun receipt(
        requestId: String,
        status: AiProposalApplyStatusV1,
        handled: Set<Int> = emptySet(),
        staged: Int = 0,
        published: Int = 0,
        resolved: Int = 0,
        items: List<AiProposalItemReceiptV1> = emptyList(),
        warnings: List<String> = emptyList(),
        errorCode: String? = null,
        message: String? = null,
        itemReceiptDurability: AiProposalItemReceiptDurabilityV1 =
            AiProposalItemReceiptDurabilityV1.PROCESS_LOCAL,
        refreshRequired: Boolean = false
    ) = AiProposalApplyReceiptV1(
        requestId = requestId,
        status = status,
        handledPositions = handled,
        stagedCount = staged,
        publishedCount = published,
        alreadyResolvedCount = resolved,
        itemReceipts = items,
        warnings = warnings,
        errorCode = errorCode ?: when (status) {
            AiProposalApplyStatusV1.CONFLICT -> "TARGET_REVISION_STALE"
            AiProposalApplyStatusV1.EXPIRED -> "TASK_EXPIRED"
            AiProposalApplyStatusV1.LOCKED -> "CORE_LOCKED"
            AiProposalApplyStatusV1.NOT_AUTHORIZED -> "NOT_AUTHORIZED"
            AiProposalApplyStatusV1.FAILED -> "AI_PROPOSAL_FAILED"
            AiProposalApplyStatusV1.INVALID -> "AI_PROPOSAL_INVALID"
            AiProposalApplyStatusV1.DUPLICATE_REQUEST_ID -> "DUPLICATE_REQUEST_ID"
            AiProposalApplyStatusV1.APPLIED, AiProposalApplyStatusV1.PARTIAL -> null
        },
        message = message ?: warnings.firstOrNull(),
        featureRevision = events.featureRevision,
        itemReceiptDurability = itemReceiptDurability,
        refreshRequired = refreshRequired
    )

    private fun AiDraftService.Created.toReceipt(
        requestId: String,
        itemReceiptDurability: AiProposalItemReceiptDurabilityV1,
        featureRevision: Long,
        refreshRequired: Boolean
    ): AiProposalApplyReceiptV1 {
        val items = itemResults.map { it.toApiReceipt() }
        val handled = items.filter { it.status == AiProposalItemStatusV1.STAGED }
            .map { it.position }.toSet()
        val hasFailure = warnings.isNotEmpty() || items.any {
            it.status == AiProposalItemStatusV1.FAILED ||
                it.status == AiProposalItemStatusV1.BLOCKED ||
                it.status == AiProposalItemStatusV1.CONFLICT
        }
        val hasConflict = items.any { it.status == AiProposalItemStatusV1.CONFLICT }
        val status = when {
            hasFailure && handled.isNotEmpty() -> AiProposalApplyStatusV1.PARTIAL
            hasConflict -> AiProposalApplyStatusV1.CONFLICT
            hasFailure -> AiProposalApplyStatusV1.FAILED
            else -> AiProposalApplyStatusV1.APPLIED
        }
        return AiProposalApplyReceiptV1(
            requestId = requestId,
            status = status,
            handledPositions = handled,
            stagedCount = draftIds.size,
            itemReceipts = items,
            warnings = warnings,
            errorCode = if (status == AiProposalApplyStatusV1.CONFLICT)
                "TARGET_REVISION_STALE" else null,
            message = warnings.firstOrNull(),
            featureRevision = featureRevision,
            itemReceiptDurability = itemReceiptDurability,
            refreshRequired = refreshRequired
        )
    }

    private fun AiDraftService.ReviewedApply.toReceipt(
        requestId: String,
        itemReceiptDurability: AiProposalItemReceiptDurabilityV1,
        featureRevision: Long,
        refreshRequired: Boolean
    ): AiProposalApplyReceiptV1 {
        val items = itemResults.map { it.toApiReceipt() }
        val hasFailure = warnings.isNotEmpty() || items.any {
            it.status == AiProposalItemStatusV1.FAILED ||
                it.status == AiProposalItemStatusV1.BLOCKED ||
                it.status == AiProposalItemStatusV1.CONFLICT
        }
        val hasConflict = items.any { it.status == AiProposalItemStatusV1.CONFLICT }
        val status = when {
            hasFailure && handledIndices.isNotEmpty() -> AiProposalApplyStatusV1.PARTIAL
            hasConflict -> AiProposalApplyStatusV1.CONFLICT
            hasFailure -> AiProposalApplyStatusV1.FAILED
            else -> AiProposalApplyStatusV1.APPLIED
        }
        return AiProposalApplyReceiptV1(
            requestId = requestId,
            status = status,
            handledPositions = handledIndices,
            stagedCount = stagedCount,
            publishedCount = publishedCount,
            alreadyResolvedCount = alreadyResolvedCount,
            itemReceipts = items,
            warnings = warnings,
            errorCode = if (status == AiProposalApplyStatusV1.CONFLICT)
                "TARGET_REVISION_STALE" else null,
            message = warnings.firstOrNull(),
            featureRevision = featureRevision,
            itemReceiptDurability = itemReceiptDurability,
            refreshRequired = refreshRequired
        )
    }

    private fun AiDraftService.AiDraftItemResult.toApiReceipt() = AiProposalItemReceiptV1(
        position = position,
        sourceIndex = sourceIndex,
        operation = DraftOperationV1.valueOf(operation.name),
        status = when (state) {
            AiDraftService.ItemState.STAGED -> AiProposalItemStatusV1.STAGED
            AiDraftService.ItemState.PUBLISHED -> AiProposalItemStatusV1.PUBLISHED
            AiDraftService.ItemState.ALREADY_RESOLVED -> AiProposalItemStatusV1.ALREADY_RESOLVED
            AiDraftService.ItemState.CONFLICT -> AiProposalItemStatusV1.CONFLICT
            AiDraftService.ItemState.BLOCKED -> AiProposalItemStatusV1.BLOCKED
            AiDraftService.ItemState.FAILED -> AiProposalItemStatusV1.FAILED
        },
        eventSeq = eventSeq,
        draftId = draftId,
        draftVersion = draftVersion,
        revision = revision,
        commitId = commitId,
        changed = changed,
        message = message
    )

    private fun AiProposalReviewV1.toInterpretation() = AiInterpretation(
        proposals = items.map { item ->
            AiProposal(
                operation = DraftOperation.valueOf(item.operation.name),
                eventSeq = item.eventSeq,
                form = item.form.toCoreDraftForm(),
                rationale = item.rationale,
                blocked = item.blockedReason,
                sourceIndex = item.sourceIndex,
                expectedRevision = item.expectedRevision
            )
        },
        warnings = warnings.toList()
    )

    private fun AiProposalTaskContextV1.toServiceContext() = ReviewedAiTaskContext(
        taskId = taskId,
        revision = revision,
        anchorMillis = anchorMillis,
        latestEvidenceMillis = latestEvidenceMillis
    )

    private fun AiProposalApplyRequestV1.toServiceContext(): ReviewedAiTaskContext {
        review.task?.let { return it.toServiceContext() }
        val now = clock.millis()
        return ReviewedAiTaskContext(
            taskId = "api-proposal-${digest(requestId)}",
            revision = 1,
            anchorMillis = now,
            latestEvidenceMillis = now
        )
    }

    private fun AiProposalTaskContextV1.isExpiredAt(nowMillis: Long): Boolean =
        nowMillis < anchorMillis ||
            nowMillis - anchorMillis > dev.powerfulcalendar.ai.AiConversationTaskSnapshot.MAX_TASK_AGE_MILLIS ||
            latestEvidenceMillis > nowMillis

    private fun AiProposalReviewV1.detached() = copy(
        items = items.map { item -> item.copy(form = item.form.detached()) },
        warnings = warnings.toList(),
        task = task?.copy()
    )

    private fun AiProposalApplyRequestV1.detached() = copy(
        review = review.detached(),
        selectedPositions = selectedPositions.toSet()
    )

    private fun EventFormV1.detached() = copy(
        completedKeys = completedKeys.toSet(),
        recurrence = recurrence.copy(
            byDay = recurrence.byDay.toSet(),
            exceptions = recurrence.exceptions.toSet(),
            overrides = recurrence.overrides.mapValues { (_, value) -> value.copy() }
        )
    )

    private fun fingerprint(action: String, review: AiProposalReviewV1): String = digest(buildString {
        fun field(name: String, value: String?) {
            append(name.length).append(':').append(name)
            if (value == null) append("-1:") else append(value.length).append(':').append(value)
        }
        field("action", action)
        appendReview(review, ::field)
    })

    private fun fingerprint(request: AiProposalApplyRequestV1): String = digest(buildString {
        fun field(name: String, value: String?) {
            append(name.length).append(':').append(name)
            if (value == null) append("-1:") else append(value.length).append(':').append(value)
        }
        field("action", "APPLY_REVIEWED")
        field("requestId", request.requestId)
        field("decision", request.decision.name)
        appendReview(request.review, ::field)
        field("selected.count", request.selectedPositions.size.toString())
        request.selectedPositions.sorted().forEach { field("selected", it.toString()) }
    })

    private fun StringBuilder.appendReview(
        review: AiProposalReviewV1,
        field: (String, String?) -> Unit
    ) {
        field("reviewId", review.reviewId)
        field("items.count", review.items.size.toString())
        review.items.forEachIndexed { position, item ->
            field("item.position", position.toString())
            field("item.sourceIndex", item.sourceIndex.toString())
            field("item.operation", item.operation.name)
            field("item.eventSeq", item.eventSeq?.toString())
            field("item.expectedRevision", item.expectedRevision?.toString())
            field("item.form", canonicalDraftFormFingerprint(item.form))
            field("item.rationale", item.rationale)
            field("item.blockedReason", item.blockedReason)
        }
        field("warnings.count", review.warnings.size.toString())
        review.warnings.forEach { field("warning", it) }
        val task = review.task
        field("task.id", task?.taskId)
        field("task.revision", task?.revision?.toString())
        field("task.anchorMillis", task?.anchorMillis?.toString())
        field("task.latestEvidenceMillis", task?.latestEvidenceMillis?.toString())
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class StoreState(
        val formal: Map<Long, TemporalRecord>,
        val drafts: Map<Long, DraftCard>
    )

    private data class StoreChange(val type: EventFeatureChangeTypeV1, val eventSeq: Long?)

    private sealed interface StoreMutation<out T> {
        data class Applied<T>(val result: T, val changes: List<StoreChange>?) : StoreMutation<T>
        data class Rejected(val receipt: AiProposalApplyReceiptV1) : StoreMutation<Nothing>
    }

    private data class ApprovalClaims(val fingerprint: String, val issuedAtMillis: Long)

    private class StoreAiProposalPublishApprovalV1 : AiProposalPublishApprovalV1

    companion object {
        private const val MAX_ITEMS = 100
        private const val MAX_RECEIPTS = 128
        private const val MAX_APPROVALS = 128
        private const val APPROVAL_TTL_MILLIS = 2 * 60 * 1000L
        private val REVIEW_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        private val TASK_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,100}")
        private val REQUEST_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,160}")
        private fun validRequestId(value: String) = REQUEST_ID_PATTERN.matches(value)
    }
}

private fun EventFormV1.isPublishableV1(): Boolean = try {
    this.toCoreDraftForm().isPublishable()
} catch (_: Exception) {
    false
}
