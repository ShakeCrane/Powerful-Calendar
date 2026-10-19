package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.TemporalRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Minimal OpenAI-compatible chat client. It performs exactly one job: turn text into a list of
 * typed proposals. Interpretation is always a draft — nothing here can publish an event.
 */
class AiClient(
    private val settings: AiSettings,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    suspend fun interpret(userText: String, context: List<TemporalRecord>): AiInterpretation =
        withContext(Dispatchers.IO) {
            if (!settings.configured) {
                return@withContext AiInterpretation(
                    warnings = listOf("尚未启用 AI：请在工具页填写端点、模型与密钥")
                )
            }
            val url = runCatching { URL(AiSettings.normalizeEndpoint(settings.endpoint)) }.getOrNull()
                ?: return@withContext AiInterpretation(warnings = listOf("AI 端点地址无效"))
            val connection = runCatching { url.openConnection() as HttpURLConnection }.getOrNull()
                ?: return@withContext AiInterpretation(warnings = listOf("无法建立网络连接"))
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20_000
                connection.readTimeout = 45_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                if (settings.apiKey.isNotBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer " + settings.apiKey)
                }
                val payload = JSONObject().apply {
                    put("model", settings.model)
                    put("temperature", 0.15)
                    put("stream", false)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        put(
                            JSONObject().put("role", "user").put(
                                "content", buildUserPrompt(userText, context)
                            )
                        )
                    })
                }.toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    return@withContext AiInterpretation(
                        warnings = listOf("AI 接口返回 HTTP $code：" + body.take(240))
                    )
                }
                val content = runCatching {
                    JSONObject(body).getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }.getOrNull()
                    ?: return@withContext AiInterpretation(
                        warnings = listOf("AI 返回内容不是预期的 chat completion 结构")
                    )
                AiResponseParser.parse(content, context, zone)
            } catch (error: Exception) {
                AiInterpretation(warnings = listOf("AI 请求失败：" + (error.message ?: error.javaClass.simpleName)))
            } finally {
                connection.disconnect()
            }
        }

    private fun buildUserPrompt(userText: String, context: List<TemporalRecord>): String {
        val now = Instant.now()
        val contextJson = JSONArray().apply {
            context.take(120).forEach { record ->
                put(
                    JSONObject().apply {
                        put("eventSeq", record.eventSeq)
                        put("kind", record.kind.name)
                        put("title", record.title)
                        put("revision", record.revision)
                        record.startMillis?.let {
                            put("start", Instant.ofEpochMilli(it).atZone(zone).format(stamp))
                        }
                        record.endMillis?.let {
                            put("end", Instant.ofEpochMilli(it).atZone(zone).format(stamp))
                        }
                        record.deadlineDate?.let { put("deadlineDate", it) }
                        record.deadlineMillis?.let {
                            put("deadlineTime", Instant.ofEpochMilli(it).atZone(zone).format(stamp))
                        }
                        if (record.recurrence.isRepeating) {
                            put("recurrence", record.recurrence.freq.name)
                        }
                    }
                )
            }
        }
        return buildString {
            append("当前时间：")
            append(Instant.ofEpochMilli(now.toEpochMilli()).atZone(zone).format(stamp))
            append("（时区 ").append(zone.id).append("）\n")
            append("用户已有的相关事件（仅用于解析「修改/删除/这个」这类指代）：\n")
            append(contextJson.toString())
            append("\n\n用户输入：\n")
            append(userText)
            append("\n\n只输出 JSON，不要解释。")
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
            你是 PowerCal 的单一日程解析器。把用户输入转换成结构化事件提案。
            规则：
            1) 三种类型：ALARM（只有开始时刻）、ACTIVITY（开始和结束）、DEADLINE（截止时刻或仅日期）。
            2) 绝不编造缺失的关键时间。若信息不足，把对应字段留空字符串，并在 missing 里列出字段名。
            3) 只有 DATE 精度的截止用 deadlineDate（yyyy-MM-dd）；不要补 23:59。
            4) 修改/删除/类型转换必须引用上下文里真实存在的 eventSeq；没有匹配就不要猜测。
            5) 时间统一用 yyyy-MM-dd HH:mm（本地墙钟时间）。
            6) 只输出一个 JSON 对象，结构如下：
            {"events":[{"operation":"CREATE|REPLACE|CONVERT|DELETE","eventSeq":null,
            "kind":"ALARM|ACTIVITY|DEADLINE","title":"","start":"","end":"",
            "deadlineDate":"","deadlineTime":"","notes":"","location":"",
            "reminderMinutes":-1,"enabled":true,
            "recurrence":{"freq":"NONE|DAILY|WEEKLY|MONTHLY","interval":1},
            "missing":["START"],"rationale":"一句话说明"}]}
        """.trimIndent()
    }
}
