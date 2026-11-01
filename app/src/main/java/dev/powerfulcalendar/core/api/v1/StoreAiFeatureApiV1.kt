package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.IdentityHashMap
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest

/** Store-backed AI domain adapter. Provider configuration is supplied only through [modelPort]. */
internal class StoreAiFeatureApiV1(
    private val store: PowerStore,
    private val events: StoreEventFeatureApiV1,
    private val calendar: CalendarFeatureApiV1,
    private val modelPort: AiModelPortV1?,
    private val clock: Clock,
    private val conversationHistoryPort: AiConversationHistoryPortV1?
) : AiFeatureApiV1 {
    override val apiVersion: Int = 1

    private val chatMutex = Mutex()
    private val evidenceMutex = Mutex()
    private val conversationPersistMutex = Mutex()
    private val chatReceipts = LinkedHashMap<String, CachedChatReceipt>()
    private val evidenceReceipts = LinkedHashMap<String, CachedEvidenceReceipt>()
    private var conversationRevision = 0L
    private var conversationMessages = runCatching {
        conversationHistoryPort?.load().orEmpty().takeLast(MAX_STORED_CHAT_MESSAGES)
            .map { it.copy() }
    }.getOrDefault(emptyList())

    private val approvalLock = Any()
    private val batchApprovals = IdentityHashMap<AiBatchApprovalV1, AiBatchReviewV1>()
    private val stuckReleaseApprovals =
        IdentityHashMap<AiBatchStuckReleaseApprovalV1, Pair<String, Long>>()

    @Synchronized
    override fun queryConversation(): AiConversationSnapshotV1 = conversationSnapshot()

    @Synchronized
    override fun appendConversationMessage(
        message: AiConversationMessageV1,
        expectedRevision: Long
    ): AiConversationMutationReceiptV1 {
        if (expectedRevision != conversationRevision) return conversationMutation(
            AiConversationMutationStatusV1.STALE
        )
        if (message.messageId.isBlank() || message.messageId.length > MAX_CHAT_MESSAGE_ID_CHARS ||
            message.content.length > MAX_CHAT_MESSAGE_CHARS ||
            message.proposalSource?.length?.let { it > MAX_CHAT_MESSAGE_CHARS } == true ||
            message.createdAtMillis?.let { it < 0L } == true ||
            (message.zoneId != null && runCatching { ZoneId.of(message.zoneId) }.isFailure) ||
            (message.role == AiConversationRoleV1.USER && message.content.isBlank()) ||
            conversationMessages.any { it.messageId == message.messageId }
        ) return conversationMutation(AiConversationMutationStatusV1.INVALID)
        conversationMessages = (conversationMessages + message.copy())
            .takeLast(MAX_STORED_CHAT_MESSAGES)
        conversationRevision++
        return conversationMutation(AiConversationMutationStatusV1.APPLIED)
    }

    @Synchronized
    override fun clearConversation(expectedRevision: Long): AiConversationMutationReceiptV1 {
        if (expectedRevision != conversationRevision) return conversationMutation(
            AiConversationMutationStatusV1.STALE
        )
        if (conversationMessages.isEmpty()) return conversationMutation(
            AiConversationMutationStatusV1.NO_CHANGE
        )
        conversationMessages = emptyList()
        conversationRevision++
        return conversationMutation(AiConversationMutationStatusV1.APPLIED)
    }

    @Synchronized
    override fun rewindConversation(
        messageId: String,
        mode: AiConversationRewindModeV1,
        expectedRevision: Long
    ): AiConversationMutationReceiptV1 {
        if (expectedRevision != conversationRevision) return conversationMutation(
            AiConversationMutationStatusV1.STALE
        )
        val matches = conversationMessages.withIndex().filter { it.value.messageId == messageId }
        if (matches.isEmpty()) return conversationMutation(
            AiConversationMutationStatusV1.MISSING_MESSAGE
        )
        if (matches.size != 1) return conversationMutation(
            AiConversationMutationStatusV1.AMBIGUOUS_MESSAGE
        )
        val (index, selected) = matches.single()
        if (mode == AiConversationRewindModeV1.EDIT_AND_RESEND &&
            selected.role != AiConversationRoleV1.USER
        ) return conversationMutation(AiConversationMutationStatusV1.EDIT_REQUIRES_USER_MESSAGE)
        val retainedCount = if (selected.role == AiConversationRoleV1.USER) index else index + 1
        val next = conversationMessages.take(retainedCount)
        if (next == conversationMessages && mode == AiConversationRewindModeV1.RETRACT) {
            return conversationMutation(AiConversationMutationStatusV1.NO_CHANGE)
        }
        conversationMessages = next
        conversationRevision++
        return conversationMutation(
            AiConversationMutationStatusV1.APPLIED,
            prefillText = selected.content.takeIf {
                mode == AiConversationRewindModeV1.EDIT_AND_RESEND
            }
        )
    }

    override suspend fun persistConversation(
        expectedRevision: Long
    ): AiConversationPersistenceStatusV1 = conversationPersistMutex.withLock {
        val port = conversationHistoryPort
            ?: return@withLock AiConversationPersistenceStatusV1.UNAVAILABLE
        val snapshot = synchronized(this) {
            if (expectedRevision != conversationRevision) null else conversationSnapshot()
        } ?: return@withLock AiConversationPersistenceStatusV1.STALE
        port.save(snapshot.messages)
        AiConversationPersistenceStatusV1.SAVED
    }

    override suspend fun chat(request: AiChatRequestV1): AiChatReceiptV1 {
        validateChatEnvelope(request)?.let { (code, message) ->
            return AiChatReceiptV1(
                requestId = request.requestId.take(MAX_REQUEST_ID_CHARS),
                status = AiChatStatusV1.INVALID_REQUEST,
                errorCode = code,
                message = message
            )
        }
        val fingerprint = request.fingerprint()
        return chatMutex.withLock {
            chatReceipts[request.requestId]?.let { cached ->
                return@withLock if (cached.fingerprint == fingerprint) {
                    cached.receipt.detached().copy(replayed = true)
                } else AiChatReceiptV1(
                    requestId = request.requestId,
                    status = AiChatStatusV1.DUPLICATE_REQUEST_ID,
                    errorCode = "DUPLICATE_REQUEST_ID",
                    message = "AI 请求标识已用于不同的对话内容"
                )
            }
            if (store.isLocked()) return@withLock AiChatReceiptV1(
                requestId = request.requestId,
                status = AiChatStatusV1.LOCKED,
                errorCode = "CORE_LOCKED",
                message = "AI 批次正在处理，请稍后重试"
            )
            val conversation = queryConversation()
            if (request.expectedConversationRevision != conversation.revision) {
                return@withLock AiChatReceiptV1(
                    requestId = request.requestId,
                    status = AiChatStatusV1.STALE_CONVERSATION,
                    errorCode = "STALE_CONVERSATION",
                    message = "聊天已变化，请刷新后重试"
                )
            }
            val history = conversation.messages.takeLast(MAX_CHAT_HISTORY_MESSAGES)
            validateChatHistory(history, request.userMessageId)?.let { (code, message) ->
                return@withLock AiChatReceiptV1(
                    requestId = request.requestId,
                    status = AiChatStatusV1.INVALID_REQUEST,
                    errorCode = code,
                    message = message
                )
            }
            val port = modelPort ?: return@withLock AiChatReceiptV1(
                requestId = request.requestId,
                status = AiChatStatusV1.MODEL_UNAVAILABLE,
                errorCode = "MODEL_UNAVAILABLE",
                message = "AI 聊天服务暂不可用"
            )
            val context = if (request.shareCalendarContext) {
                withContext(Dispatchers.IO) {
                    calendarContextEvents(AiCalendarContextPolicyV1.FIRST_60_FORMAL_EVENTS)
                }
            } else emptyList()
            val modelOutput = try {
                port.chat(AiChatModelRequestV1(
                    requestId = request.requestId,
                    history = history.map { it.copy() },
                    zoneId = request.zoneId,
                    shareCalendarContext = request.shareCalendarContext,
                    availableTools = AiChatToolRegistryV1.tools.filter { definition ->
                        !definition.requiresCalendarContext || request.shareCalendarContext
                    }
                ), context.map { it.copy() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val receipt = AiChatReceiptV1(
                    requestId = request.requestId,
                    status = AiChatStatusV1.MODEL_UNAVAILABLE,
                    errorCode = "MODEL_UNAVAILABLE",
                    message = "AI 聊天暂时未完成，请检查配置后重试"
                )
                rememberChatReceipt(request.requestId, fingerprint, receipt)
                return@withLock receipt
            }
            val modelError = modelOutput.error
            val receipt = if (modelError != null) {
                AiChatReceiptV1(
                    requestId = request.requestId,
                    status = AiChatStatusV1.MODEL_UNAVAILABLE,
                    errorCode = "MODEL_UNAVAILABLE",
                    message = modelError.take(300)
                )
            } else {
                val task = modelOutput.automaticProposalTask?.detached()
                if (modelOutput.toolCalls.isNotEmpty() || modelOutput.toolCallsTruncated) {
                    executeChatToolCall(request, modelOutput, task)
                } else {
                    val automaticProposal = if (
                        modelOutput.proposalIntent == AiChatProposalIntentV1.EXPLICIT && task != null
                    ) generateProposal(AiProposalGenerationRequestV1(
                        requestId = request.proposalRequestId(),
                        task = task,
                        zoneId = request.zoneId,
                        shareCalendarContext = request.shareCalendarContext,
                        contextPolicy = AiCalendarContextPolicyV1.FIRST_60_FORMAL_EVENTS
                    )) else null
                    AiChatReceiptV1(
                        requestId = request.requestId,
                        status = AiChatStatusV1.COMPLETED,
                        reply = modelOutput.reply.take(MAX_CHAT_MESSAGE_CHARS),
                        proposalSource = modelOutput.proposalSource?.take(MAX_CHAT_MESSAGE_CHARS),
                        proposalIntent = modelOutput.proposalIntent,
                        automaticProposalTask = task?.takeIf {
                            automaticProposal?.review?.items?.isNotEmpty() == true
                        },
                        automaticProposal = automaticProposal,
                        clarification = if (
                            modelOutput.proposalIntent == AiChatProposalIntentV1.EXPLICIT && task == null
                        ) MISSING_EXPLICIT_TASK_MESSAGE else null
                    )
                }
            }
            rememberChatReceipt(request.requestId, fingerprint, receipt)
            receipt
        }
    }

    private suspend fun executeChatToolCall(
        request: AiChatRequestV1,
        modelOutput: AiChatModelOutputV1,
        task: AiConversationTaskV1?
    ): AiChatReceiptV1 {
        if (synchronized(this) { request.expectedConversationRevision != conversationRevision }) {
            return AiChatReceiptV1(
                requestId = request.requestId,
                status = AiChatStatusV1.STALE_CONVERSATION,
                errorCode = "STALE_CONVERSATION",
                message = "聊天已变化；日程工具没有执行"
            )
        }

        val calls = modelOutput.toolCalls
        val call = calls.singleOrNull()
        val definition = call?.let { invocation ->
            AiChatToolRegistryV1.tools.singleOrNull {
                it.providerName == invocation.providerName
            }
        }
        var calendarQuery: CalendarEventQueryV1? = null
        val rejection = when {
            modelOutput.toolCallsTruncated -> "工具调用超出长度或数量限制"
            call == null -> "本轮只允许一个日程工具调用"
            definition == null -> "工具不在 Core 白名单中"
            !call.callId.matches(TOOL_CALL_ID_PATTERN) -> "工具调用标识无效"
            call.argumentsJson.length > definition.inputMaxBytes ||
                call.argumentsJson.toByteArray(Charsets.UTF_8).size > definition.inputMaxBytes ->
                "工具参数超出大小限制"
            definition.requiresCalendarContext && !request.shareCalendarContext ->
                "当前请求未允许共享日历上下文"
            definition.name == AiChatToolRegistryV1.CALENDAR_PROPOSE -> when {
                modelOutput.proposalIntent != AiChatProposalIntentV1.EXPLICIT || task == null ->
                    "无法将工具调用绑定到当前用户的日程请求"
                else -> {
                    val args = runCatching { JSONObject(call.argumentsJson) }.getOrNull()
                    if (args == null || args.length() != 0) "工具参数格式无效" else null
                }
            }
            definition.name == AiChatToolRegistryV1.CALENDAR_QUERY -> {
                calendarQuery = runCatching {
                    parseCalendarQueryArguments(call.argumentsJson, request.zoneId)
                }.getOrNull()
                if (calendarQuery == null) "工具参数格式无效" else null
            }
            else -> "工具不在 Core 白名单中"
        }
        if (rejection != null) {
            val rejected = calls.take(MAX_PROVIDER_TOOL_CALLS).map { item ->
                AiChatToolExecutionReceiptV1(
                    callId = item.callId.take(MAX_TOOL_CALL_ID_CHARS),
                    toolName = item.providerName.take(MAX_PROVIDER_TOOL_NAME_CHARS),
                    status = AiChatToolExecutionStatusV1.REJECTED,
                    message = rejection
                )
            }.ifEmpty {
                listOf(AiChatToolExecutionReceiptV1(
                    callId = "",
                    toolName = definition?.name ?: "unknown",
                    status = AiChatToolExecutionStatusV1.REJECTED,
                    message = rejection
                ))
            }
            return AiChatReceiptV1(
                requestId = request.requestId,
                status = AiChatStatusV1.COMPLETED,
                reply = "工具请求未通过 Core 校验；未执行日历操作。",
                toolExecutions = rejected,
                errorCode = "INVALID_TOOL_CALL",
                message = rejection
            )
        }

        val validCall = requireNotNull(call)
        val validDefinition = requireNotNull(definition)
        if (validDefinition.name == AiChatToolRegistryV1.CALENDAR_QUERY) {
            val query = requireNotNull(calendarQuery)
            val result = try {
                withContext(Dispatchers.IO) { calendar.queryEvents(query) }
                    .boundedForTool(validDefinition.outputMaxBytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val execution = AiChatToolExecutionReceiptV1(
                    callId = validCall.callId,
                    toolName = validDefinition.name,
                    status = AiChatToolExecutionStatusV1.FAILED,
                    message = "日历查询未能完成"
                )
                return AiChatReceiptV1(
                    requestId = request.requestId,
                    status = AiChatStatusV1.COMPLETED,
                    reply = "日历查询暂未完成；日历数据没有更改。",
                    toolExecutions = listOf(execution)
                )
            }
            val reply = result.toChatSummary().take(MAX_CHAT_MESSAGE_CHARS)
            val execution = AiChatToolExecutionReceiptV1(
                callId = validCall.callId,
                toolName = validDefinition.name,
                status = AiChatToolExecutionStatusV1.EXECUTED,
                message = reply.take(MAX_TOOL_RECEIPT_MESSAGE_CHARS),
                calendarQuery = result
            )
            return AiChatReceiptV1(
                requestId = request.requestId,
                status = AiChatStatusV1.COMPLETED,
                reply = reply,
                toolExecutions = listOf(execution)
            )
        }

        val proposalRequestId = request.proposalRequestId()
        val generated = generateProposal(AiProposalGenerationRequestV1(
            requestId = proposalRequestId,
            task = requireNotNull(task),
            zoneId = request.zoneId,
            shareCalendarContext = request.shareCalendarContext,
            contextPolicy = AiCalendarContextPolicyV1.FIRST_60_FORMAL_EVENTS
        ))
        val ready = generated.status == AiProposalGenerationStatusV1.READY &&
            generated.review?.items?.isNotEmpty() == true
        val execution = AiChatToolExecutionReceiptV1(
            callId = validCall.callId,
            toolName = validDefinition.name,
            status = if (generated.status == AiProposalGenerationStatusV1.MODEL_UNAVAILABLE ||
                generated.status == AiProposalGenerationStatusV1.INVALID_REQUEST
            ) AiChatToolExecutionStatusV1.FAILED else AiChatToolExecutionStatusV1.EXECUTED,
            message = generated.message?.take(MAX_TOOL_RECEIPT_MESSAGE_CHARS),
            proposalRequestId = proposalRequestId
        )
        return AiChatReceiptV1(
            requestId = request.requestId,
            status = AiChatStatusV1.COMPLETED,
            reply = if (ready) "已生成待审核提案；正式日历尚未修改。"
                else "本次没有形成可审核提案；正式日历尚未修改。",
            proposalIntent = AiChatProposalIntentV1.EXPLICIT,
            automaticProposalTask = task.takeIf { ready },
            automaticProposal = generated,
            toolExecutions = listOf(execution),
            clarification = generated.message.takeIf { !ready }
        )
    }

    private fun parseCalendarQueryArguments(
        json: String,
        zoneId: String
    ): CalendarEventQueryV1 {
        val arguments = JSONObject(json)
        val allowed = setOf("fromDate", "throughDate", "title", "kind", "maxResults")
        val keys = arguments.keys()
        while (keys.hasNext()) require(keys.next() in allowed) { "Unknown query argument" }

        fun optionalString(key: String): String? {
            if (!arguments.has(key)) return null
            return arguments.get(key) as? String ?: throw IllegalArgumentException("Invalid $key")
        }

        val kind = optionalString("kind")?.let(EventKindV1::valueOf)
        val maxResults = if (!arguments.has("maxResults")) {
            CalendarEventQueryV1.DEFAULT_MAX_RESULTS
        } else {
            val raw = arguments.get("maxResults") as? Number
                ?: throw IllegalArgumentException("Invalid maxResults")
            val numeric = raw.toDouble()
            require(numeric.isFinite() && numeric % 1.0 == 0.0 &&
                numeric in 1.0..CalendarEventQueryV1.MAX_RESULTS.toDouble()) {
                "Invalid maxResults"
            }
            numeric.toInt()
        }
        return CalendarEventQueryV1(
            zoneId = zoneId,
            fromDate = optionalString("fromDate"),
            throughDate = optionalString("throughDate"),
            titleQuery = optionalString("title"),
            kind = kind,
            maxResults = maxResults
        )
    }

    private fun CalendarEventQueryResultV1.boundedForTool(maxBytes: Int): CalendarEventQueryResultV1 {
        var bytes = 256L + (query.titleQuery?.toByteArray(Charsets.UTF_8)?.size ?: 0)
        val bounded = ArrayList<CalendarEventQueryHitV1>(results.size)
        for (hit in results) {
            val hitBytes = hit.estimatedUtf8Bytes()
            if (bytes + hitBytes > maxBytes) break
            bounded += hit
            bytes += hitBytes
        }
        return copy(
            results = bounded,
            truncated = truncated || bounded.size < results.size
        )
    }

    private fun CalendarEventQueryHitV1.estimatedUtf8Bytes(): Long {
        fun String?.jsonSize(): Long = this?.let {
            JSONObject.quote(it).toByteArray(Charsets.UTF_8).size.toLong()
        } ?: 4L
        var size = 384L + title.jsonSize() + zoneId.jsonSize() + source.jsonSize()
        size += deadlineDate.jsonSize() + memoStartDate.jsonSize() + memoEndDate.jsonSize()
        size += recurrence.activeFromDate.jsonSize() + recurrence.activeUntilDate.jsonSize()
        size += recurrence.byDay.size * 8L
        size += recurrence.exceptions.sumOf { it.jsonSize() + 8L }
        size += recurrence.overrides.entries.sumOf { (key, value) ->
            key.jsonSize() + value.title.jsonSize() + 48L
        }
        return size
    }

    private fun CalendarEventQueryResultV1.toChatSummary(): String {
        if (totalMatches == 0) return "日历中没有符合条件的事项。"
        if (results.isEmpty()) {
            return "找到 $totalMatches 项符合条件的日程，但记录详情超出返回容量；请缩小日期范围或按标题筛选。"
        }
        val shown = results.take(MAX_QUERY_DISPLAY_RESULTS)
        return buildString {
            append("找到 $totalMatches 项符合条件的日程")
            if (truncated || shown.size < totalMatches) append("，以下列出 ${shown.size} 项：")
            else append("：")
            shown.forEachIndexed { index, hit ->
                append('\n').append(index + 1).append(". ")
                    .append(hit.title.toDisplayTitle())
                    .append("（").append(hit.kind.toDisplayName())
                    .append("；").append(hit.displayTime()).append('）')
            }
            if (shown.size < totalMatches) {
                append("\n其余 ").append(totalMatches - shown.size).append(" 项未在回复中展开。")
            }
        }
    }

    private fun String.toDisplayTitle(): String = map { char ->
        if (char.isISOControl()) ' ' else char
    }.joinToString("").replace(Regex("\\s+"), " ").trim().take(120)

    private fun EventKindV1.toDisplayName(): String = when (this) {
        EventKindV1.ALARM -> "闹钟"
        EventKindV1.ACTIVITY -> "活动"
        EventKindV1.DEADLINE -> "截止事项"
        EventKindV1.MEMO -> "备忘"
    }

    private fun CalendarEventQueryHitV1.displayTime(): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))
        fun Long.displayDateTime() = Instant.ofEpochMilli(this).atZone(zone)
            .format(QUERY_RESULT_DATE_TIME_FORMATTER)
        return when (precision) {
            CalendarEventPrecisionV1.INSTANT ->
                (startMillis ?: deadlineMillis)?.displayDateTime() ?: "时间未填写"
            CalendarEventPrecisionV1.INTERVAL -> {
                val start = startMillis?.displayDateTime() ?: return "时间未填写"
                val end = endMillis?.displayDateTime() ?: return start
                "$start–$end"
            }
            CalendarEventPrecisionV1.DATE_ONLY -> deadlineDate ?: "日期未填写"
            CalendarEventPrecisionV1.DATE_RANGE -> when {
                memoStartDate != null && memoEndDate != null -> "$memoStartDate 至 $memoEndDate"
                memoStartDate != null -> "自 $memoStartDate 起"
                memoEndDate != null -> "至 $memoEndDate"
                else -> "日期未填写"
            }
            CalendarEventPrecisionV1.UNDATED -> "未注明日期"
            CalendarEventPrecisionV1.INCOMPLETE -> "时间未填写"
        }
    }

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
            withContext(Dispatchers.IO) { calendarContextEvents(request.contextPolicy) }
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

    override suspend fun analyzeReviewedTextEvidence(
        request: AiReviewedTextEvidenceRequestV1
    ): AiEvidenceAnalysisReceiptV1 {
        validateTextEvidenceRequest(request)?.let { (code, message) ->
            return AiEvidenceAnalysisReceiptV1(
                requestId = request.requestId.take(MAX_REQUEST_ID_CHARS),
                status = AiEvidenceAnalysisStatusV1.INVALID_REQUEST,
                errorCode = code,
                message = message
            )
        }
        return runEvidenceCommand(
            requestId = request.requestId,
            fingerprint = request.fingerprint(),
            expectedConversationRevision = request.expectedConversationRevision,
            unavailableMessage = "AI 证据分析暂不可用",
            allowProposalSource = request.source != AiTextEvidenceSourceV1.ICS_UNRESOLVED
        ) { port, history ->
            port.analyzeReviewedTextEvidence(AiTextEvidenceModelRequestV1(
                requestId = request.requestId,
                source = request.source,
                filename = request.filename,
                text = request.text,
                truncated = request.truncated,
                preflightHint = request.preflightHint,
                history = history,
                zoneId = request.zoneId
            ))
        }
    }

    override suspend fun analyzeConsentedImageEvidence(
        request: AiConsentedImageEvidenceRequestV1
    ): AiEvidenceAnalysisReceiptV1 {
        validateImageEvidenceRequest(request)?.let { (code, message) ->
            return AiEvidenceAnalysisReceiptV1(
                requestId = request.requestId.take(MAX_REQUEST_ID_CHARS),
                status = AiEvidenceAnalysisStatusV1.INVALID_REQUEST,
                errorCode = code,
                message = message
            )
        }
        return runEvidenceCommand(
            requestId = request.requestId,
            fingerprint = request.fingerprint(),
            expectedConversationRevision = request.expectedConversationRevision,
            unavailableMessage = "AI 图片分析暂不可用"
        ) { port, history ->
            port.analyzeConsentedImageEvidence(AiImageEvidenceModelRequestV1(
                requestId = request.requestId,
                filename = request.filename,
                mimeType = request.mimeType,
                imageBytes = request.imageBytes.copyOf(),
                history = history,
                zoneId = request.zoneId
            ))
        }
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

    private fun validateTextEvidenceRequest(
        request: AiReviewedTextEvidenceRequestV1
    ): Pair<String, String>? {
        validateEvidenceEnvelope(
            request.requestId,
            request.filename,
            request.expectedConversationRevision,
            request.zoneId
        )?.let { return it }
        if (request.text.isBlank() || request.text.length > MAX_AI_EVIDENCE_TEXT_CHARS ||
            '\u0000' in request.text || request.preflightHint.length > MAX_AI_EVIDENCE_HINT_CHARS
        ) return "EVIDENCE_INVALID" to "已审阅文本为空或超出安全限制"
        return null
    }

    private fun validateImageEvidenceRequest(
        request: AiConsentedImageEvidenceRequestV1
    ): Pair<String, String>? {
        validateEvidenceEnvelope(
            request.requestId,
            request.filename,
            request.expectedConversationRevision,
            request.zoneId
        )?.let { return it }
        if (!IMAGE_MIME_TYPE.matches(request.mimeType) || request.imageBytes.isEmpty() ||
            request.imageBytes.size > MAX_AI_IMAGE_BYTES
        ) return "IMAGE_INVALID" to "图片类型或大小超出安全限制"
        return null
    }

    private fun validateEvidenceEnvelope(
        requestId: String,
        filename: String,
        expectedConversationRevision: Long,
        zoneId: String
    ): Pair<String, String>? {
        if (requestId.isBlank() || requestId.length > MAX_REQUEST_ID_CHARS) {
            return "REQUEST_ID_INVALID" to "AI 请求标识无效"
        }
        if (filename.isBlank() || filename.length > MAX_AI_EVIDENCE_FILENAME_CHARS) {
            return "FILENAME_INVALID" to "证据来源名称无效"
        }
        if (expectedConversationRevision < 0L) {
            return "CONVERSATION_REVISION_INVALID" to "聊天版本无效"
        }
        if (runCatching { ZoneId.of(zoneId) }.isFailure) {
            return "ZONE_INVALID" to "AI 请求时区无效"
        }
        return null
    }

    private fun evidenceHistoryAt(expectedRevision: Long): List<AiConversationMessageV1>? =
        synchronized(this) {
            if (conversationRevision != expectedRevision) return@synchronized null
            conversationMessages.takeLast(MAX_AI_EVIDENCE_HISTORY_MESSAGES)
                .map { it.copy() }
        }

    private fun conversationRevisionIsCurrent(expectedRevision: Long): Boolean =
        synchronized(this) { conversationRevision == expectedRevision }

    private suspend fun runEvidenceCommand(
        requestId: String,
        fingerprint: String,
        expectedConversationRevision: Long,
        unavailableMessage: String,
        allowProposalSource: Boolean = true,
        invokeModel: suspend (
            AiModelPortV1,
            List<AiConversationMessageV1>
        ) -> AiEvidenceModelOutputV1
    ): AiEvidenceAnalysisReceiptV1 = evidenceMutex.withLock {
        evidenceReceipts[requestId]?.let { cached ->
            return@withLock if (cached.fingerprint == fingerprint) {
                cached.receipt.copy(replayed = true)
            } else AiEvidenceAnalysisReceiptV1(
                requestId = requestId,
                status = AiEvidenceAnalysisStatusV1.DUPLICATE_REQUEST_ID,
                errorCode = "DUPLICATE_REQUEST_ID",
                message = "AI 请求标识已用于不同证据"
            )
        }
        val history = evidenceHistoryAt(expectedConversationRevision)
            ?: return@withLock staleEvidence(requestId)
        val port = modelPort ?: return@withLock unavailableEvidence(
            requestId, "MODEL_UNAVAILABLE", unavailableMessage
        )
        val output = try {
            invokeModel(port, history)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@withLock unavailableEvidence(requestId, "MODEL_UNAVAILABLE",
                error.message?.take(MAX_MODEL_ERROR_CHARS) ?: unavailableMessage)
        }
        if (!conversationRevisionIsCurrent(expectedConversationRevision)) {
            return@withLock staleEvidence(requestId)
        }
        val receipt = evidenceReceipt(
            requestId,
            if (allowProposalSource) output else output.copy(proposalSource = null)
        )
        if (receipt.status == AiEvidenceAnalysisStatusV1.COMPLETED) {
            evidenceReceipts[requestId] = CachedEvidenceReceipt(fingerprint, receipt)
            while (evidenceReceipts.size > MAX_CHAT_RECEIPTS) {
                evidenceReceipts.remove(evidenceReceipts.entries.first().key)
            }
        }
        receipt
    }

    private fun evidenceReceipt(
        requestId: String,
        output: AiEvidenceModelOutputV1
    ): AiEvidenceAnalysisReceiptV1 {
        output.error?.let { return unavailableEvidence(requestId, "MODEL_UNAVAILABLE", it) }
        if (output.reply.length > MAX_AI_EVIDENCE_REPLY_CHARS ||
            output.proposalSource?.length?.let { it > MAX_CHAT_MESSAGE_CHARS } == true
        ) return unavailableEvidence(requestId, "OUTPUT_LIMIT_EXCEEDED",
            "AI 返回内容超过安全限制；没有创建草稿")
        return AiEvidenceAnalysisReceiptV1(
            requestId = requestId,
            status = AiEvidenceAnalysisStatusV1.COMPLETED,
            reply = output.reply,
            proposalSource = output.proposalSource
        )
    }

    private fun staleEvidence(requestId: String) = AiEvidenceAnalysisReceiptV1(
        requestId = requestId.take(MAX_REQUEST_ID_CHARS),
        status = AiEvidenceAnalysisStatusV1.STALE_CONVERSATION,
        errorCode = "STALE_CONVERSATION",
        message = "对话已变化，请重新提交证据分析"
    )

    private fun unavailableEvidence(
        requestId: String,
        errorCode: String,
        message: String
    ) = AiEvidenceAnalysisReceiptV1(
        requestId = requestId.take(MAX_REQUEST_ID_CHARS),
        status = AiEvidenceAnalysisStatusV1.MODEL_UNAVAILABLE,
        errorCode = errorCode,
        message = message.take(MAX_MODEL_ERROR_CHARS)
    )

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

    private fun validateChatEnvelope(request: AiChatRequestV1): Pair<String, String>? {
        if (request.requestId.isBlank() || request.requestId.length > MAX_REQUEST_ID_CHARS) {
            return "REQUEST_ID_INVALID" to "AI 请求标识无效"
        }
        if (request.userMessageId.isBlank() ||
            request.userMessageId.length > MAX_CHAT_MESSAGE_ID_CHARS
        ) return "USER_MESSAGE_ID_INVALID" to "聊天输入标识无效"
        if (request.expectedConversationRevision < 0L) {
            return "CONVERSATION_REVISION_INVALID" to "聊天版本无效"
        }
        if (runCatching { ZoneId.of(request.zoneId) }.isFailure) {
            return "ZONE_INVALID" to "AI 请求时区无效"
        }
        return null
    }

    private fun validateChatHistory(
        history: List<AiConversationMessageV1>,
        expectedUserMessageId: String
    ): Pair<String, String>? {
        if (history.isEmpty() || history.size > MAX_CHAT_HISTORY_MESSAGES ||
            history.last().role != AiConversationRoleV1.USER ||
            history.last().content.isBlank()
        ) return "HISTORY_INVALID" to "请先输入一条有效消息"
        if (history.last().messageId != expectedUserMessageId) {
            return "CHAT_MESSAGE_MISMATCH" to "聊天输入已变化，请刷新后重试"
        }
        if (history.any { message ->
                message.messageId.isBlank() || message.messageId.length > MAX_CHAT_MESSAGE_ID_CHARS ||
                    message.content.length > MAX_CHAT_MESSAGE_CHARS ||
                    message.proposalSource?.length?.let { it > MAX_CHAT_MESSAGE_CHARS } == true ||
                    (message.createdAtMillis?.let { it < 0L } == true) ||
                    (message.zoneId != null && runCatching { ZoneId.of(message.zoneId) }.isFailure)
            } || history.map { it.messageId }.distinct().size != history.size
        ) return "HISTORY_INVALID" to "对话记录格式无效"
        if (history.sumOf { it.content.length } > MAX_CHAT_HISTORY_CHARS) {
            return "HISTORY_TOO_LARGE" to "对话上下文超出限制，请清理较早消息后重试"
        }
        return null
    }

    @Synchronized
    private fun conversationSnapshot() = AiConversationSnapshotV1(
        revision = conversationRevision,
        messages = conversationMessages.map { it.copy() }
    )

    private fun conversationMutation(
        status: AiConversationMutationStatusV1,
        prefillText: String? = null
    ) = AiConversationMutationReceiptV1(
        status = status,
        snapshot = conversationSnapshot(),
        prefillText = prefillText
    )

    private fun rememberChatReceipt(
        requestId: String,
        fingerprint: String,
        receipt: AiChatReceiptV1
    ) {
        chatReceipts[requestId] = CachedChatReceipt(fingerprint, receipt.detached())
        while (chatReceipts.size > MAX_CHAT_RECEIPTS) {
            chatReceipts.remove(chatReceipts.entries.first().key)
        }
    }

    private fun AiConversationTaskV1.detached() = copy(
        userEvidence = userEvidence.map { it.copy() },
        assistantSuggestions = assistantSuggestions.map { it.copy() }
    )

    private fun AiChatReceiptV1.detached(): AiChatReceiptV1 {
        val proposal = automaticProposal
        return copy(
            automaticProposalTask = automaticProposalTask?.detached(),
            automaticProposal = proposal?.copy(
                review = proposal.review?.detached(),
                warnings = proposal.warnings.toList()
            ),
            toolExecutions = toolExecutions.map { execution ->
                execution.copy(calendarQuery = execution.calendarQuery?.detached())
            }
        )
    }

    private fun CalendarEventQueryResultV1.detached() = copy(
        query = query.copy(),
        results = results.map { hit ->
            hit.copy(recurrence = hit.recurrence.copy(
                byDay = hit.recurrence.byDay.toSet(),
                exceptions = hit.recurrence.exceptions.toSet(),
                overrides = hit.recurrence.overrides.mapValues { (_, value) -> value.copy() }
            ))
        }
    )

    private fun AiChatRequestV1.fingerprint(): String {
        val canonical = buildString {
            appendPart(userMessageId)
            appendPart(expectedConversationRevision.toString())
            appendPart(zoneId)
            appendPart(shareCalendarContext.toString())
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun AiReviewedTextEvidenceRequestV1.fingerprint(): String {
        val canonical = buildString {
            appendPart(source.name)
            appendPart(filename)
            appendPart(text)
            appendPart(truncated.toString())
            appendPart(preflightHint)
            appendPart(expectedConversationRevision.toString())
            appendPart(zoneId)
        }
        return canonical.sha256()
    }

    private fun AiConsentedImageEvidenceRequestV1.fingerprint(): String {
        val imageDigest = MessageDigest.getInstance("SHA-256")
            .digest(imageBytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val canonical = buildString {
            appendPart(filename)
            appendPart(mimeType)
            appendPart(imageDigest)
            appendPart(expectedConversationRevision.toString())
            appendPart(zoneId)
        }
        return canonical.sha256()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun StringBuilder.appendPart(value: String?) {
        if (value == null) append("-1:") else append(value.length).append(':').append(value)
    }

    private fun AiChatRequestV1.proposalRequestId(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(requestId.toByteArray(Charsets.UTF_8))
            .take(20)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "chat-proposal-$digest"
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

    private fun calendarContextEvents(
        policy: AiCalendarContextPolicyV1
    ): List<EventRecordV1> {
        val horizon = clock.millis() + CALENDAR_CONTEXT_HORIZON_MILLIS
        val selected = ArrayList<EventRecordV1>(MAX_CALENDAR_CONTEXT_EVENTS)
        var cursor: EventQueryCursorV1? = null
        var snapshotRevision: Long? = null
        while (selected.size < MAX_CALENDAR_CONTEXT_EVENTS) {
            val page = events.queryEventsPage(cursor, limit = 100)
            if (page.stale || (snapshotRevision != null && page.featureRevision != snapshotRevision)) {
                return emptyList()
            }
            snapshotRevision = page.featureRevision
            page.events.forEach { event ->
                val matches = when (policy) {
                    AiCalendarContextPolicyV1.FIRST_60_FORMAL_EVENTS -> true
                    AiCalendarContextPolicyV1.EVENTS_THROUGH_14_DAYS_AFTER_NOW ->
                        (event.toCoreTemporalRecord().anchorMillis() ?: 0L) <= horizon
                }
                if (matches && selected.size < MAX_CALENDAR_CONTEXT_EVENTS) selected.add(event)
            }
            cursor = page.nextCursor ?: break
        }
        return if (snapshotRevision == events.featureRevision) selected else emptyList()
    }

    private class HostBatchApproval : AiBatchApprovalV1
    private class HostStuckReleaseApproval : AiBatchStuckReleaseApprovalV1

    private data class CachedChatReceipt(
        val fingerprint: String,
        val receipt: AiChatReceiptV1
    )

    private data class CachedEvidenceReceipt(
        val fingerprint: String,
        val receipt: AiEvidenceAnalysisReceiptV1
    )

    private companion object {
        const val MAX_REQUEST_ID_CHARS = 160
        const val MAX_BATCH_ITEMS = 100
        const val MAX_BATCH_REQUEST_CHARS = 2_500
        const val MAX_AI_PROPOSALS = 100
        const val MAX_CALENDAR_CONTEXT_EVENTS = 60
        const val MAX_CHAT_HISTORY_MESSAGES = 80
        const val MAX_STORED_CHAT_MESSAGES = 200
        const val MAX_CHAT_HISTORY_CHARS = 480_000
        const val MAX_CHAT_MESSAGE_CHARS = 6_000
        const val MAX_PROVIDER_TOOL_CALLS = 8
        const val MAX_TOOL_CALL_ID_CHARS = 128
        const val MAX_PROVIDER_TOOL_NAME_CHARS = 120
        const val MAX_TOOL_RECEIPT_MESSAGE_CHARS = 300
        const val MAX_QUERY_DISPLAY_RESULTS = 20
        val TOOL_CALL_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
        val QUERY_RESULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "uuuu-MM-dd HH:mm", Locale.ROOT
        )
        const val MAX_CHAT_MESSAGE_ID_CHARS = 100
        const val MAX_CHAT_RECEIPTS = 128
        const val MAX_AI_EVIDENCE_FILENAME_CHARS = 180
        const val MAX_AI_EVIDENCE_TEXT_CHARS = 24_000
        const val MAX_AI_EVIDENCE_HINT_CHARS = 2_000
        const val MAX_AI_EVIDENCE_HISTORY_MESSAGES = 8
        const val MAX_AI_EVIDENCE_REPLY_CHARS = 12_000
        const val MAX_AI_IMAGE_BYTES = 6_000_000
        const val MAX_MODEL_ERROR_CHARS = 300
        const val MAX_TASK_EVIDENCE_COUNT = 40
        const val MAX_TASK_EVIDENCE_CHARS = 20_000
        const val MAX_TASK_SUGGESTIONS = 40
        const val MAX_TASK_AGE_MILLIS = 24L * 60 * 60 * 1000
        const val CALENDAR_CONTEXT_HORIZON_MILLIS = 14L * 24 * 60 * 60 * 1000
        const val MISSING_EXPLICIT_TASK_MESSAGE = "无法安全关联到一项完整的待处理日程请求。" +
            "为避免把“确认”误当成事件内容，请重新说明要创建或修改的事项、日期和时间。"
        val IMAGE_MIME_TYPE = Regex("image/[A-Za-z0-9.+-]{1,64}")
    }
}
