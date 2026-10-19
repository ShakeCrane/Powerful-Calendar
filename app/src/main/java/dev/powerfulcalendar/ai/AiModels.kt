package dev.powerfulcalendar.ai

import android.content.Context
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * One AI, one user-supplied endpoint. There is deliberately no provider routing, no second
 * assistant and no agent-to-agent ownership anywhere in this package.
 */
data class AiSettings(
    val endpoint: String = "",
    val model: String = "",
    val apiKey: String = "",
    val enabled: Boolean = false,
    /**
     * Context sharing is an explicit opt-in, not a side effect of configuring a key. When false the
     * model only sees the text the user typed; when true it sees a bounded slice of events and the
     * UI reports the count.
     */
    val shareContext: Boolean = false
) {
    val configured: Boolean
        get() = enabled && endpoint.isNotBlank() && model.isNotBlank()

    companion object {
        val EMPTY = AiSettings()

        /** Accepts both `https://host/v1` and a full `/chat/completions` URL. */
        fun normalizeEndpoint(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }
    }
}

/**
 * The key lives in app-private SharedPreferences and is excluded from platform auto-backup and
 * from PowerCal's native export. It is never logged.
 */
object AiSettingsStore {
    private const val PREFS = "powercal-ai"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_MODEL = "model"
    private const val KEY_KEY = "api_key"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SHARE_CONTEXT = "share_context"

    fun load(context: Context): AiSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AiSettings(
            endpoint = prefs.getString(KEY_ENDPOINT, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, "").orEmpty(),
            apiKey = prefs.getString(KEY_KEY, "").orEmpty(),
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            shareContext = prefs.getBoolean(KEY_SHARE_CONTEXT, false)
        )
    }

    fun save(context: Context, settings: AiSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_ENDPOINT, settings.endpoint)
            putString(KEY_MODEL, settings.model)
            putString(KEY_KEY, settings.apiKey)
            putBoolean(KEY_ENABLED, settings.enabled)
            putBoolean(KEY_SHARE_CONTEXT, settings.shareContext)
        }.apply()
    }
}

data class AiProposal(
    val operation: DraftOperation,
    val eventSeq: Long?,
    val form: DraftForm,
    val rationale: String = "",
    /** Non-null when the proposal cannot be turned into a card (e.g. unknown target event). */
    val blocked: String? = null
)

data class AiInterpretation(
    val proposals: List<AiProposal> = emptyList(),
    val warnings: List<String> = emptyList(),
    val rawReply: String = ""
) {
    val usable: Boolean get() = proposals.isNotEmpty()
}

/**
 * Parses the model's structured reply. Nothing is trusted: an unknown kind, a REPLACE without a
 * real eventSeq, or an impossible field is dropped with a warning instead of being coerced.
 */
object AiResponseParser {
    fun parse(raw: String, known: List<TemporalRecord>, zone: ZoneId): AiInterpretation {
        val warnings = mutableListOf<String>()
        val clean = extractJson(raw)
        if (clean == null) {
            return AiInterpretation(warnings = listOf("AI 回复里没有可解析的 JSON"), rawReply = raw)
        }
        val root = runCatching { org.json.JSONObject(clean) }.getOrElse {
            return AiInterpretation(warnings = listOf("AI 回复的 JSON 结构无法解析"), rawReply = raw)
        }
        val array = root.optJSONArray("events") ?: root.optJSONArray("items")
            ?: return AiInterpretation(warnings = listOf("AI 回复缺少 events 数组"), rawReply = raw)
        val knownBySeq = known.associateBy { it.eventSeq }
        val proposals = mutableListOf<AiProposal>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val kindText = item.optString("kind").uppercase()
            val kind = runCatching { TemporalKind.valueOf(kindText) }.getOrNull()
            if (kind == null) {
                warnings.add("第 ${index + 1} 项类型未知，已忽略")
                continue
            }
            val operationText = item.optString("operation", "CREATE").uppercase()
            val operation = runCatching { DraftOperation.valueOf(operationText) }.getOrNull()
            if (operation == null) {
                warnings.add("第 ${index + 1} 项操作未知，已按新建处理")
            }
            var op = operation ?: DraftOperation.CREATE

            var eventSeq: Long? = if (item.isNull("eventSeq")) null else item.optLong("eventSeq").takeIf { it > 0 }
            // A REPLACE/CONVERT/DELETE that names an event outside the shared context must never
            // be turned into a CREATE: that would allocate a second permanent number for the same
            // real-world event. It becomes a blocked proposal the UI reports instead.
            var target: TemporalRecord? = null
            var blocked: String? = null
            if (op != DraftOperation.CREATE) {
                target = eventSeq?.let { knownBySeq[it] }
                if (target == null) {
                    blocked = "第 ${index + 1} 项引用的 #$eventSeq 不在本次上下文里，未生成草稿"
                    warnings.add(blocked)
                }
            }
            val title = item.optString("title").trim()
            if (title.isEmpty() && op != DraftOperation.DELETE) {
                warnings.add("第 ${index + 1} 项缺少标题，已保留为待补全草稿")
            }
            val rawStart = item.optString("start").trim().ifBlank { null }
            val rawEnd = item.optString("end").trim().ifBlank { null }
            val deadlineDate = item.optString("deadlineDate").trim().ifBlank { null }
            val rawDeadlineTime = item.optString("deadlineTime").trim().ifBlank { null }

            // The model answers in the device's wall clock. If the event lives in another zone,
            // translate the instant so the same moment is written, not the same clock reading.
            val answerZone = zone
            val recordZone = target?.zone ?: zone
            fun retarget(text: String?): String? {
                if (text == null) return null
                val local = DraftForm.parseLocalDateTime(text) ?: return null
                if (recordZone == answerZone) return text
                return local.atZone(answerZone).withZoneSameInstant(recordZone)
                    .toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }
            val start = rawStart?.let { retarget(it) ?: it }
            val end = rawEnd?.let { retarget(it) ?: it }
            val deadlineTime = rawDeadlineTime?.let { retarget(it) ?: it }

            val startText = when {
                start == null -> ""
                DraftForm.parseLocalDateTime(start) != null -> start
                else -> {
                    warnings.add("第 ${index + 1} 项开始时间格式不可用，已留空待你确认")
                    ""
                }
            }
            val endText = when (kind) {
                TemporalKind.DEADLINE -> when {
                    deadlineDate != null && DraftForm.parseDateOnly(deadlineDate) != null -> deadlineDate
                    deadlineTime != null && DraftForm.parseLocalDateTime(deadlineTime) != null -> deadlineTime
                    end != null && DraftForm.parseLocalDateTime(end) != null -> end
                    else -> ""
                }
                else -> when {
                    end == null -> ""
                    DraftForm.parseLocalDateTime(end) != null -> end
                    else -> {
                        warnings.add("第 ${index + 1} 项结束时间格式不可用，已留空待你确认")
                        ""
                    }
                }
            }
            val reminder = if (item.has("reminderMinutes")) item.optInt("reminderMinutes", -1) else -1
            val recurrenceJson = item.optJSONObject("recurrence")
            val recurrence = if (recurrenceJson == null) {
                Recurrence.NONE
            } else {
                val freq = runCatching {
                    RecurrenceFreq.valueOf(recurrenceJson.optString("freq", "NONE").uppercase())
                }.getOrDefault(RecurrenceFreq.NONE)
                Recurrence(freq = freq, interval = recurrenceJson.optInt("interval", 1).coerceAtLeast(1))
            }
            val form = DraftForm(
                kind = kind,
                title = title,
                startText = startText,
                endText = endText,
                notes = item.optString("notes"),
                location = item.optString("location"),
                reminderMinutes = reminder,
                enabled = item.optBoolean("enabled", true),
                // Provenance is never rewritten by the AI: a REPLACE keeps the original source, and
                // only a brand-new event is marked as AI-authored.
                source = if (op == DraftOperation.CREATE) "ai" else (target?.source ?: "ai"),
                zoneId = recordZone.id,
                recurrence = recurrence
            )
            if (op != DraftOperation.DELETE && blocked == null && form.missingFields().isNotEmpty()) {
                warnings.add(
                    "第 ${index + 1} 项还缺 " +
                        form.missingFields().joinToString("、") { it.label } + "，已保留为草稿"
                )
            }
            proposals.add(
                AiProposal(
                    operation = op,
                    eventSeq = eventSeq,
                    form = form,
                    rationale = item.optString("rationale"),
                    blocked = blocked
                )
            )
        }
        return AiInterpretation(proposals, warnings, raw)
    }

    /** Tolerates ```json fences and leading prose without accepting arbitrary text as JSON. */
    private fun extractJson(raw: String): String? {
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```").find(raw)?.groupValues?.get(1)
        if (fenced != null) return fenced
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    /** Converts a validated proposal into a draft form the card editor can open. */
    fun asDraftForm(proposal: AiProposal, zone: ZoneId): DraftForm = proposal.form.copy(zoneId = zone.id)

    fun todayText(zone: ZoneId): String = LocalDate.now(zone).toString()
}
