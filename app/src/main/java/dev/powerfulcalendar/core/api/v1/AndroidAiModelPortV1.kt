package dev.powerfulcalendar.core.api.v1

import android.content.Context
import dev.powerfulcalendar.ai.AiBatchSelection
import dev.powerfulcalendar.ai.AiClient
import dev.powerfulcalendar.ai.AiConversationTaskSnapshot
import dev.powerfulcalendar.ai.AiTaskAssistantSuggestion
import dev.powerfulcalendar.ai.AiTaskEvidence
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.ui.v2.toAiProposalReviewV1
import java.time.ZoneId

/** Android provider adapter; credentials and persisted AI settings stay inside the Host. */
internal class AndroidAiModelPortV1(context: Context) : AiModelPortV1 {
    private val appContext = context.applicationContext

    override suspend fun generateProposal(
        request: AiProposalGenerationRequestV1,
        calendarContext: List<EventRecordV1>
    ): AiProposalModelOutputV1 {
        val settings = AiSettingsStore.load(appContext)
        val zone = ZoneId.of(request.zoneId)
        val contextRecords = calendarContext.map(EventRecordV1::toCoreTemporalRecord)
        val client = AiClient(settings, zone)
        val interpretation = request.task?.let { task ->
            client.interpret(task.toConversationTaskSnapshot(), contextRecords)
        } ?: client.interpret(request.userText.orEmpty(), contextRecords)
        val expectedRevisions = if (settings.shareContext && request.shareCalendarContext) {
            calendarContext.associate { it.eventSeq to it.revision }
        } else emptyMap()
        return AiProposalModelOutputV1(
            review = interpretation.toAiProposalReviewV1(request.requestId, expectedRevisions),
            contextUsed = settings.shareContext && request.shareCalendarContext &&
                calendarContext.isNotEmpty()
        )
    }

    override suspend fun selectBatchDrafts(
        userRequest: String,
        candidates: List<AiBatchCandidateV1>
    ): AiBatchModelSelectionV1 {
        val settings = AiSettingsStore.load(appContext)
        val selection: AiBatchSelection = AiClient(settings, ZoneId.systemDefault())
            .planBatchSelection(userRequest, candidates)
        return AiBatchModelSelectionV1(
            selectedDraftIds = selection.selectedIds.toList(),
            reply = selection.reply,
            question = selection.question,
            error = selection.error
        )
    }

    private fun AiConversationTaskV1.toConversationTaskSnapshot() = AiConversationTaskSnapshot(
        taskId = taskId,
        revision = revision,
        anchorMillis = anchorMillis,
        zoneId = zoneId,
        userEvidence = userEvidence.map { evidence ->
            AiTaskEvidence(
                messageId = evidence.messageId,
                text = evidence.text,
                createdAtMillis = evidence.createdAtMillis,
                zoneId = evidence.zoneId
            )
        },
        assistantSuggestions = assistantSuggestions.map { suggestion ->
            AiTaskAssistantSuggestion(suggestion.messageId, suggestion.text)
        }
    )
}
