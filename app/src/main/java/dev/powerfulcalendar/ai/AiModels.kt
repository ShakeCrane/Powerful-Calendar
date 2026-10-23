package dev.powerfulcalendar.ai

import android.content.Context
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderTiming
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
        if (array.length() == 0) {
            warnings.add("模型未返回可操作的日程项；请明确要创建的标题、日期和时间")
        }
        val knownBySeq = known.associateBy { it.eventSeq }
        val proposals = mutableListOf<AiProposal>()
        if (array.length() > 100) warnings.add("模型一次返回超过 100 项，后续条目未处理")
        for (index in 0 until minOf(array.length(), 100)) {
            val item = array.optJSONObject(index) ?: continue
            val kindText = item.optString("kind").uppercase()
            val operationText = item.optString("operation", "CREATE").uppercase()
            val operation = runCatching { DraftOperation.valueOf(operationText) }.getOrNull()
            if (operation == null) {
                warnings.add("第 ${index + 1} 项操作未知，已按新建处理")
            }
            val op = operation ?: DraftOperation.CREATE

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
            // Event kind is inferred from semantic evidence when the model omits its label.
            // Never invent an activity end: an unknown start-only item stays incomplete
            // unless the wording explicitly describes a point alarm/reminder.
            val titleHint = item.optString("title") + " " + item.optString("notes")
            val rescuedTime = if (op == DraftOperation.CREATE) AiTimeEvidence.recover(item.optString("notes")) else null
            val kind = runCatching { TemporalKind.valueOf(kindText) }.getOrNull()
                ?: target?.kind ?: when {
                    item.optString("deadlineDate").isNotBlank() ||
                        item.optString("deadlineTime").isNotBlank() ||
                        item.optString("dueDate").isNotBlank() -> TemporalKind.DEADLINE
                    item.optString("end").isNotBlank() ||
                        item.optString("endTime").isNotBlank() -> TemporalKind.ACTIVITY
                    item.optString("start").isNotBlank() || rescuedTime != null -> {
                        if (Regex("截止|ddl|due", RegexOption.IGNORE_CASE).containsMatchIn(titleHint))
                            TemporalKind.DEADLINE
                        else if (Regex("闹钟|定时提醒|提醒我|alarm", RegexOption.IGNORE_CASE)
                                .containsMatchIn(titleHint)) TemporalKind.ALARM
                        else TemporalKind.ACTIVITY
                    }
                    Regex("备忘|待办|清单|记事|memo|to.?do", RegexOption.IGNORE_CASE)
                        .containsMatchIn(titleHint) -> TemporalKind.MEMO
                    else -> null
                }
            if (kind == null) {
                warnings.add(
                    "第 ${index + 1} 项类型未知且没有可参照的事件，未生成草稿；请说明它属于闹钟、活动、截止还是备忘"
                )
                continue
            }
            val existing = target?.let(DraftForm::from)
            val title = if (item.has("title")) item.optString("title").trim()
                else existing?.title.orEmpty()
            if (title.isEmpty() && op != DraftOperation.DELETE) {
                warnings.add("第 ${index + 1} 项缺少标题，已保留为待补全草稿")
            }
            val startInput = if (item.has("start")) item.optString("start")
                else if (item.has("startTime")) item.optString("startTime")
                else existing?.startText.orEmpty()
            val endInput = if (item.has("end")) item.optString("end")
                else if (item.has("endTime")) item.optString("endTime")
                else existing?.endText.orEmpty()
            val rawStart = startInput.trim().ifBlank {
                if (kind != TemporalKind.DEADLINE) rescuedTime?.start else null
            }
            val rawEnd = endInput.trim().ifBlank { rescuedTime?.end }
            val deadlineDate = item.optString("deadlineDate",
                item.optString("dueDate")).trim().ifBlank { null }
            val rawDeadlineTime = item.optString("deadlineTime").trim().ifBlank {
                if (kind == TemporalKind.DEADLINE) rescuedTime?.start else null
            }
            if (rescuedTime != null && startInput.isBlank() && endInput.isBlank()) {
                warnings.add("第 ${index + 1} 项从备注中恢复了完整日期和时刻，请核对原始材料")
            }

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
                kind == TemporalKind.MEMO -> {
                    val date = item.optString("memoStartDate", startInput).trim()
                    if (date.isBlank() || DraftForm.parseDateOnly(date) != null) date else ""
                }
                start == null -> ""
                DraftForm.parseLocalDateTime(start) != null -> start
                else -> {
                    warnings.add("第 ${index + 1} 项开始时间格式不可用，已留空待你确认")
                    ""
                }
            }
            val endText = when (kind) {
                TemporalKind.MEMO -> {
                    val date = item.optString("memoEndDate", endInput).trim()
                    if (date.isBlank() || DraftForm.parseDateOnly(date) != null) date else ""
                }
                TemporalKind.DEADLINE -> when {
                    deadlineDate != null && DraftForm.parseDateOnly(deadlineDate) != null -> deadlineDate
                    deadlineTime != null && DraftForm.parseLocalDateTime(deadlineTime) != null -> deadlineTime
                    end != null && DraftForm.parseDateOnly(end) != null -> end
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
            val reminder = if (item.has("reminderMinutes")) item.optInt("reminderMinutes", -1)
                else existing?.reminderMinutes ?: if (kind == TemporalKind.MEMO) -1 else 0
            if (reminder !in -1..10080) {
                blocked = "提醒提前量超出 0～10080 分钟范围"
                warnings.add("第 ${index + 1} 项提醒提前量不合法，禁止执行")
            }
            val method = runCatching {
                ReminderMethod.valueOf(item.optString("reminderMethod", existing?.reminderMethod?.name ?: "MESSAGE").uppercase())
            }.getOrNull()
            val defaultTiming = if (kind == TemporalKind.DEADLINE &&
                DraftForm.parseDateOnly(endText) != null) ReminderTiming.DAY_START
                else existing?.reminderTiming ?: ReminderTiming.OFFSET
            val timing = runCatching {
                ReminderTiming.valueOf(item.optString("reminderTiming", defaultTiming.name).uppercase())
            }.getOrNull()
            if (method == null || timing == null) {
                blocked = "提醒方式或提醒时机不支持"
                warnings.add("第 ${index + 1} 项提醒策略不合法")
            }
            val recurrenceJson = item.optJSONObject("recurrence")
            val recurrence = if (recurrenceJson == null) existing?.recurrence ?: Recurrence.NONE
                else parseRecurrence(recurrenceJson, recordZone)
            if (recurrence == null) {
                blocked = "重复规则缺少有效参数或超出支持范围"
                warnings.add("第 ${index + 1} 项重复规则无法安全转换，需要澄清")
            }
            val participants = item.optJSONArray("participants")
                ?.let { a -> (0 until minOf(a.length(), 20)).map { a.optString(it).trim() }.filter { it.isNotBlank() } }
                .orEmpty()
            val notes = if (item.has("notes")) item.optString("notes") else existing?.notes.orEmpty()
            val enrichedNotes = if (participants.isEmpty()) notes
                else (notes + "\n参与人：" + participants.joinToString("、")).trim().take(4000)
            val form = DraftForm(
                kind = kind,
                title = title,
                startText = startText,
                endText = endText,
                notes = enrichedNotes,
                location = if (item.has("location")) item.optString("location") else existing?.location.orEmpty(),
                reminderMinutes = reminder.coerceIn(-1, 10080),
                reminderMethod = method ?: ReminderMethod.MESSAGE,
                reminderTiming = timing ?: defaultTiming,
                enabled = if (item.has("enabled")) item.optBoolean("enabled") else existing?.enabled ?: true,
                // Provenance is never rewritten by the AI: a REPLACE keeps the original source, and
                // only a brand-new event is marked as AI-authored.
                source = if (op == DraftOperation.CREATE) "ai" else (target?.source ?: "ai"),
                zoneId = recordZone.id,
                recurrence = recurrence ?: Recurrence.NONE,
                trackingEnabled = if (kind == TemporalKind.MEMO) true
                    else if (item.has("trackingEnabled")) item.optBoolean("trackingEnabled")
                    else existing?.trackingEnabled ?: false,
                completedKeys = existing?.completedKeys ?: emptySet()
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
        if (proposals.isEmpty() && warnings.isEmpty()) {
            warnings.add("模型回复中没有可用的日程项；请核对类型、标题和日期时间后重试")
        }
        return AiInterpretation(proposals, warnings, raw)
    }

    /**
     * Strict declarative recurrence semantics. Never turn an unknown rule into NONE:
     * that would silently turn a recurring course into a single occurrence.
     */
    private fun parseRecurrence(json: org.json.JSONObject, zone: ZoneId): Recurrence? {
        return runCatching {
            val freq = RecurrenceFreq.valueOf(json.getString("freq").uppercase())
            val interval = json.optInt("interval", 1)
            require(interval in 1..1000)
            val count = if (json.has("count") && !json.isNull("count")) {
                json.getInt("count").also { require(it in 1..1_000_000) }
            } else null
            val untilText = if (!json.has("until") || json.isNull("until")) "" else json.optString("until").trim()
            val until = if (untilText.isEmpty()) null else {
                if (DraftForm.parseDateOnly(untilText) != null) {
                    DraftForm.parseDateOnly(untilText)!!.plusDays(1)
                        .atStartOfDay(zone).toInstant().toEpochMilli() - 1
                } else DraftForm.parseInstantIn(untilText, zone)
                    ?: error("until date format invalid")
            }
            require(count == null || until == null)
            val byDays = mutableSetOf<DayOfWeek>()
            val dayArray = json.optJSONArray("byDay")
            if (dayArray != null) {
                require(dayArray.length() <= 7)
                for (index in 0 until dayArray.length()) {
                    val label = dayArray.getString(index).uppercase()
                    byDays += when (label) {
                        "MO", "MONDAY" -> DayOfWeek.MONDAY
                        "TU", "TUESDAY" -> DayOfWeek.TUESDAY
                        "WE", "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                        "TH", "THURSDAY" -> DayOfWeek.THURSDAY
                        "FR", "FRIDAY" -> DayOfWeek.FRIDAY
                        "SA", "SATURDAY" -> DayOfWeek.SATURDAY
                        "SU", "SUNDAY" -> DayOfWeek.SUNDAY
                        else -> error("unknown BYDAY: " + label.take(20))
                    }
                }
            }
            require(freq == RecurrenceFreq.WEEKLY || byDays.isEmpty())
            val allowed = setOf("freq", "interval", "count", "until", "byDay",
                "activeFromDate", "activeUntilDate")
            val keys = json.keys()
            while (keys.hasNext()) require(keys.next() in allowed) {
                "unknown recurrence constraint"
            }
            val fromDate = json.optString("activeFromDate").takeUnless { it.isBlank() || it == "null" }
            val untilDate = json.optString("activeUntilDate").takeUnless { it.isBlank() || it == "null" }
            require(fromDate == null || DraftForm.parseDateOnly(fromDate) != null)
            require(untilDate == null || DraftForm.parseDateOnly(untilDate) != null)
            require(fromDate == null || untilDate == null || fromDate <= untilDate)
            Recurrence(freq = freq, interval = interval, count = count, untilMillis = until,
                activeFromDate = fromDate, activeUntilDate = untilDate, byDay = byDays)
        }.getOrNull()
    }

    /**
     * Extract the first complete JSON object without a platform regex.
     * Android's ICU regex compiler rejects the old unescaped closing brace pattern.
     * Balanced scanning also handles nested event objects and braces inside quoted strings.
     */
    private fun extractJson(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until raw.length) {
            val char = raw[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, index + 1)
                        if (depth < 0) return null
                    }
                }
            }
        }
        return null
    }

    /** Converts a validated proposal into a draft form the card editor can open. */
    fun asDraftForm(proposal: AiProposal, zone: ZoneId): DraftForm = proposal.form.copy(zoneId = zone.id)

    fun todayText(zone: ZoneId): String = LocalDate.now(zone).toString()
}
