package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiChatMessage
import org.json.JSONArray
import org.json.JSONObject

internal enum class WebChatRewindMode { RETRACT, EDIT_AND_RESEND }

internal sealed interface WebChatHistoryMutation {
    data class Applied(
        val messages: List<AiChatMessage>,
        /** Composer changes only for the explicit user-message edit-and-resend action. */
        val updateComposer: Boolean = false,
        val prefillText: String? = null
    ) : WebChatHistoryMutation

    data object Stale : WebChatHistoryMutation
    data object NoChange : WebChatHistoryMutation
    data object MissingMessage : WebChatHistoryMutation
    data object AmbiguousMessage : WebChatHistoryMutation
    data object EditRequiresUserMessage : WebChatHistoryMutation
}

/** Pure, deterministic operations for the Host-owned local chat history. */
internal object WebAiChatHistory {
    const val PROJECTION_LIMIT = 40
    private const val PROJECTED_CONTENT_LIMIT = 1_600

    fun project(messages: List<AiChatMessage>): JSONArray = JSONArray(
        messages.takeLast(PROJECTION_LIMIT).map { message ->
            JSONObject()
                .put("messageId", message.messageId)
                .put("role", message.role)
                .put("content", message.content.take(PROJECTED_CONTENT_LIMIT))
        }
    )

    fun clear(
        messages: List<AiChatMessage>,
        expectedRevision: Long,
        currentRevision: Long
    ): WebChatHistoryMutation {
        if (expectedRevision != currentRevision) return WebChatHistoryMutation.Stale
        if (messages.isEmpty()) return WebChatHistoryMutation.NoChange
        return WebChatHistoryMutation.Applied(emptyList())
    }

    fun rewind(
        messages: List<AiChatMessage>,
        messageId: String,
        mode: WebChatRewindMode,
        expectedRevision: Long,
        currentRevision: Long
    ): WebChatHistoryMutation {
        if (expectedRevision != currentRevision) return WebChatHistoryMutation.Stale
        val matches = messages.withIndex().filter { it.value.messageId == messageId }
        if (matches.isEmpty()) return WebChatHistoryMutation.MissingMessage
        if (matches.size != 1) return WebChatHistoryMutation.AmbiguousMessage
        val (index, selected) = matches.single()
        if (mode == WebChatRewindMode.EDIT_AND_RESEND && selected.role != "user") {
            return WebChatHistoryMutation.EditRequiresUserMessage
        }
        val retainedCount = if (selected.role == "user") index else index + 1
        val result = WebChatHistoryMutation.Applied(
            messages = messages.take(retainedCount),
            updateComposer = mode == WebChatRewindMode.EDIT_AND_RESEND,
            prefillText = selected.content.takeIf { mode == WebChatRewindMode.EDIT_AND_RESEND }
        )
        return if (result.messages == messages && !result.updateComposer) {
            WebChatHistoryMutation.NoChange
        } else result
    }
}
