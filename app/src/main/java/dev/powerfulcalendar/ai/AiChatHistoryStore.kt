package dev.powerfulcalendar.ai

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** App-private, atomic, bounded chat persistence. Never stores endpoint credentials. */
object AiChatHistoryStore {
    private const val FILENAME = "powercal-chat-history.json"
    private const val MAX_BYTES = 1_500_000

    @Synchronized
    fun load(context: Context): List<AiChatMessage> = runCatching {
        val file = File(context.filesDir, FILENAME)
        if (!file.exists() || file.length() > MAX_BYTES) return@runCatching emptyList<AiChatMessage>()
        val data = AtomicFile(file).openRead().use { it.readBytes() }
        val array = JSONArray(data.toString(Charsets.UTF_8))
        (0 until array.length()).toList().takeLast(200).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val role = item.optString("role")
            val content = item.optString("content")
            if (role != "user" && role != "assistant") return@mapNotNull null
            AiChatMessage(
                role = role,
                content = content,
                proposalSource = item.optString("proposalSource").ifEmpty { null },
                messageId = item.optString("messageId").ifBlank { UUID.randomUUID().toString() },
                createdAtMillis = if (item.has("createdAtMillis") &&
                    !item.isNull("createdAtMillis")) item.optLong("createdAtMillis") else null,
                zoneId = item.optString("zoneId").ifBlank { null }
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(context: Context, messages: List<AiChatMessage>) {
        val array = JSONArray()
        messages.takeLast(200).forEach { message ->
            array.put(JSONObject().put("role", message.role).put("content", message.content)
                .put("proposalSource", message.proposalSource ?: "")
                .put("messageId", message.messageId)
                .put("createdAtMillis", message.createdAtMillis ?: JSONObject.NULL)
                .put("zoneId", message.zoneId ?: ""))
        }
        val bytes = array.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "聊天历史达到私有存储上限" }
        val atomic = AtomicFile(File(context.filesDir, FILENAME))
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    /** Explicitly scoped to app-owned temporary AI evidence. Does not touch events or credentials. */
    fun clearTemporaryCache(context: Context): Int {
        var deleted = 0
        context.cacheDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("powercal-ai-")
        }?.forEach { file -> if (file.delete()) deleted++ }
        return deleted
    }
}
