package dev.powerfulcalendar.interop

import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.Priority
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Exactly ONE draft for the whole recurring series, no matter how many slots it generates. */
data class IcsSeriesDraft(val uid: String?, val form: DraftForm)
data class IcsImport(
    val events: List<CalendarEvent>,
    val warnings: List<String>,
    val series: List<IcsSeriesDraft> = emptyList()
)

object IcsCodec {
    private val dateFormat = DateTimeFormatter.BASIC_ISO_DATE
    private val localFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun parse(text: String): IcsImport {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n').fold(mutableListOf<String>()) { acc, line ->
                if ((line.startsWith(" ") || line.startsWith("\t")) && acc.isNotEmpty()) {
                    acc[acc.lastIndex] = acc.last() + line.drop(1)
                } else acc.add(line)
                acc
            }
        val events = mutableListOf<CalendarEvent>()
        val series = mutableListOf<IcsSeriesDraft>()
        val warnings = mutableListOf<String>()
        var current: MutableMap<String, Pair<String, String>>? = null
        for (line in lines) {
            when (line.trim()) {
                "BEGIN:VEVENT", "BEGIN:VTODO" -> current = mutableMapOf()
                "END:VEVENT", "END:VTODO" -> {
                    current?.let { fields ->
                        try {
                            if ("__DUPLICATE_PROPERTY" in fields) {
                                error("发现重复且冲突的 ICS 字段，请先交给 AI 核对")
                            }
                            if ("RECURRENCE-ID" in fields || "RDATE" in fields) {
                                error("包含单次变更/附加日期，当前无法无损应用到重复系列；请先交给 AI 澄清")
                            }
                            if ("RRULE" in fields) {
                                series.add(parseSeries(fields))
                            } else {
                            val startField = fields["DTSTART"] ?: error("缺少 DTSTART")
                            val endField = fields["DTEND"]
                            val allDay = startField.first.contains("VALUE=DATE") ||
                                (startField.second.length == 8)
                            val start = parseDate(startField.first, startField.second)
                            // A missing end has no safe duration. Reject instead of inventing 1h.
                            val end = endField?.let { parseDate(it.first, it.second) }
                                ?: error("缺少 DTEND；不能猜测持续时间")
                            val uid = fields["UID"]?.second
                            val title = decode(fields["SUMMARY"]?.second ?: "未命名事件")
                            val priority = when (fields["PRIORITY"]?.second?.toIntOrNull()) {
                                in 1..3 -> Priority.HIGH
                                in 4..5 -> Priority.NORMAL
                                in 6..9 -> Priority.LOW
                                else -> Priority.NORMAL
                            }
                            if ("EXDATE" in fields) error("非重复事件不能忽略 EXDATE")
                            require(end > start) { "DTEND 不得早于或等于 DTSTART" }
                            events.add(CalendarEvent(
                                title = title,
                                startMillis = start,
                                endMillis = end,
                                notes = decode(fields["DESCRIPTION"]?.second ?: ""),
                                location = decode(fields["LOCATION"]?.second ?: ""),
                                priority = priority,
                                reminderMinutes = -1,
                                allDay = allDay,
                                source = "ics",
                                externalId = uid ?: UUID.randomUUID().toString()
                            ))
                            }
                        } catch (e: Exception) {
                            warnings.add("跳过无效 VEVENT：" + e.message)
                        }
                    }
                    current = null
                }
                else -> if (current != null) {
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon)
                        val name = key.substringBefore(';').uppercase()
                        val previous = current[name]
                        if (previous != null && name == "EXDATE" && previous.first == key) {
                            current[name] = key to (previous.second + "," + line.substring(colon + 1))
                        } else if (previous != null) {
                            current["__DUPLICATE_PROPERTY"] = name to "重复字段：" + name
                        } else {
                            current[name] = key to line.substring(colon + 1)
                        }
                    }
                }
            }
        }
        return IcsImport(events, warnings, series)
    }

    /**
     * Supported RFC 5545 recurrence subset maps to ONE semantic draft, never expanded rows.
     * Unknown constraints are reported for AI-assisted clarification, not silently discarded.
     */
    private fun parseSeries(fields: Map<String, Pair<String, String>>): IcsSeriesDraft {
        val startField = fields["DTSTART"] ?: fields["DUE"]
            ?: error("重复系列缺少 DTSTART / DUE，请确认首次发生时刻")
        val start = parseDate(startField.first, startField.second)
        val zoneName = startField.first.split(';').firstOrNull {
            it.startsWith("TZID=", ignoreCase = true)
        }?.substringAfter('=')?.trim('"')
        val zone = when {
            zoneName != null -> ZoneId.of(zoneName)
            startField.second.endsWith("Z") -> ZoneOffset.UTC
            else -> ZoneId.systemDefault()
        }
        val dateOnly = startField.first.contains("VALUE=DATE", ignoreCase = true) ||
            startField.second.length == 8
        val title = decode(fields["SUMMARY"]?.second.orEmpty()).ifBlank { "未命名重复事件" }
        val seenParts = mutableSetOf<String>()
        val parts = fields.getValue("RRULE").second.split(';').associate { part ->
            val eq = part.indexOf('=')
            require(eq > 0 && eq < part.lastIndex) { "RRULE 字段不完整：" + part.take(70) }
            val name = part.substring(0, eq).uppercase()
            require(seenParts.add(name)) { "RRULE 含重复约束：" + name }
            name to part.substring(eq + 1)
        }
        val supported = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "WKST")
        require(parts.keys.all { it in supported }) {
            "RRULE 含尚未实现的约束：" + (parts.keys - supported).joinToString()
        }
        require(parts["WKST"] == null || parts["WKST"] == "MO") {
            "当前仅支持周一为周起点；请确认 WKST"
        }
        val freq = when (parts["FREQ"]?.uppercase()) {
            "DAILY" -> RecurrenceFreq.DAILY
            "WEEKLY" -> RecurrenceFreq.WEEKLY
            "MONTHLY" -> RecurrenceFreq.MONTHLY
            else -> error("未支持的 FREQ：" + parts["FREQ"])
        }
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: if ("INTERVAL" in parts) {
            error("RRULE INTERVAL 无效")
        } else 1
        require(interval in 1..1000) { "重复间隔必须在 1～1000 内" }
        val count = parts["COUNT"]?.let { item ->
            item.toIntOrNull()?.takeIf { it > 0 }
                ?: error("RRULE COUNT 无效或超限")
        }
        require(count == null || "UNTIL" !in parts) { "COUNT 与 UNTIL 不能同时使用" }
        val byDay = parts["BYDAY"]?.split(',')?.map { day ->
            when (day.uppercase()) {
                "MO" -> DayOfWeek.MONDAY
                "TU" -> DayOfWeek.TUESDAY
                "WE" -> DayOfWeek.WEDNESDAY
                "TH" -> DayOfWeek.THURSDAY
                "FR" -> DayOfWeek.FRIDAY
                "SA" -> DayOfWeek.SATURDAY
                "SU" -> DayOfWeek.SUNDAY
                else -> error("复杂 BYDAY 尚不支持：" + day.take(20))
            }
        }?.toSet().orEmpty()
        require(freq == RecurrenceFreq.WEEKLY || byDay.isEmpty()) {
            "当前仅支持 WEEKLY BYDAY，其他过滤规则需要澄清"
        }
        val initialLocal = Instant.ofEpochMilli(start).atZone(zone).toLocalDateTime()
        require(byDay.isEmpty() || initialLocal.dayOfWeek in byDay) {
            "首次发生星期不在 BYDAY 中，请确认 DTSTART"
        }
        require(freq != RecurrenceFreq.MONTHLY || initialLocal.dayOfMonth <= 28) {
            "每月 29–31 日的 RFC 重复规则需要额外校验"
        }
        val until = parts["UNTIL"]?.let { input ->
            val value = if (input.length == 8) {
                LocalDate.parse(input, dateFormat).plusDays(1).atStartOfDay(zone)
                    .toInstant().toEpochMilli() - 1L
            } else parseDate(
                if (input.endsWith("Z")) "UNTIL" else "UNTIL;TZID=" + zone.id,
                input
            )
            require(value >= start) { "UNTIL 不得早于首次发生" }
            value
        }
        val exceptions = mutableSetOf<String>()
        fields["EXDATE"]?.let { (key, csv) ->
            val exceptionZoneName = key.split(';').firstOrNull {
                it.startsWith("TZID=", ignoreCase = true)
            }?.substringAfter('=')?.trim('"')
            if (exceptionZoneName != null) require(ZoneId.of(exceptionZoneName) == zone) {
                "EXDATE 时区与系列时区不一致"
            }
            csv.split(',').forEach { slot ->
                val millis = parseDate(key, slot)
                val local = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
                require(local.hour == initialLocal.hour && local.minute == initialLocal.minute) {
                    "EXDATE 的时间与系列原时刻不匹配"
                }
                exceptions += RecurrenceEngine.keyFor(local)
            }
            require(exceptions.size <= 512) { "EXDATE 超过每系列 512 个例外上限" }
        }
        val recurrence = Recurrence(
            freq = freq, interval = interval, count = count,
            untilMillis = until, byDay = byDay, exceptions = exceptions
        )
        val isDeadline = fields.containsKey("DUE") && !fields.containsKey("DTSTART")
        val end = fields["DTEND"]?.let { parseDate(it.first, it.second) }
            ?: fields["DURATION"]?.let { start + Duration.parse(it.second).toMillis() }
        val kind = when {
            isDeadline -> TemporalKind.DEADLINE
            end != null -> TemporalKind.ACTIVITY
            else -> TemporalKind.ALARM
        }
        if (kind == TemporalKind.ACTIVITY) require(end!! > start) {
            "重复活动必须有正的持续时间"
        }
        val form = DraftForm(
            kind = kind,
            title = title,
            notes = decode(fields["DESCRIPTION"]?.second.orEmpty()),
            location = decode(fields["LOCATION"]?.second.orEmpty()),
            startText = if (kind == TemporalKind.DEADLINE) "" else DraftForm.formatInstant(start, zone),
            endText = when (kind) {
                TemporalKind.ACTIVITY -> DraftForm.formatInstant(end, zone)
                TemporalKind.DEADLINE -> if (dateOnly) {
                    Instant.ofEpochMilli(start).atZone(zone).toLocalDate().toString()
                } else DraftForm.formatInstant(start, zone)
                TemporalKind.ALARM -> ""
            },
            reminderMinutes = when (kind) {
                TemporalKind.ALARM -> 0
                TemporalKind.ACTIVITY -> 10
                TemporalKind.DEADLINE -> if (dateOnly) -1 else 10
            },
            source = "ics", zoneId = zone.id, recurrence = recurrence
        )
        require(form.isPublishable()) { "重复系列仍有未填写的必填字段" }
        return IcsSeriesDraft(fields["UID"]?.second, form)
    }

    private fun parseDate(key: String, input: String): Long {
        val dateOnly = key.contains("VALUE=DATE") || input.length == 8
        val tzid = key.split(';').firstOrNull { it.startsWith("TZID=", true) }
            ?.substringAfter('=')?.trim('"')
        val zone = tzid?.let {
            try { ZoneId.of(it) } catch (_: Exception) { error("未知 TZID：$it") }
        } ?: ZoneId.systemDefault()
        if (dateOnly) return LocalDate.parse(input.take(8), dateFormat)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        if (input.endsWith("Z")) {
            return LocalDateTime.parse(input.dropLast(1), localFormat)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        return LocalDateTime.parse(input, localFormat).atZone(zone).toInstant().toEpochMilli()
    }

    private fun decode(input: String): String = input
        .replace("\\n", "\n").replace("\\N", "\n")
        .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun encode(input: String): String = input
        .replace("\\", "\\\\").replace("\n", "\\n")
        .replace(",", "\\,").replace(";", "\\;")

    fun export(events: List<CalendarEvent>): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Powerful Calendar//Android//ZH\r\n")
        for (e in events) {
            append("BEGIN:VEVENT\r\n")
            append("UID:" + (e.externalId ?: ("powerful-" + e.id + "@local")) + "\r\n")
            append("DTSTAMP:" + utcFormat.format(Instant.now()) + "\r\n")
            if (e.allDay) {
                val zone = ZoneId.systemDefault()
                val start = Instant.ofEpochMilli(e.startMillis).atZone(zone).toLocalDate()
                val end = Instant.ofEpochMilli(e.endMillis - 1).atZone(zone).toLocalDate().plusDays(1)
                append("DTSTART;VALUE=DATE:" + start.format(dateFormat) + "\r\n")
                append("DTEND;VALUE=DATE:" + end.format(dateFormat) + "\r\n")
            } else {
                append("DTSTART:" + utcFormat.format(Instant.ofEpochMilli(e.startMillis)) + "\r\n")
                append("DTEND:" + utcFormat.format(Instant.ofEpochMilli(e.endMillis)) + "\r\n")
            }
            append("SUMMARY:" + encode(e.title) + "\r\n")
            if (e.notes.isNotBlank()) append("DESCRIPTION:" + encode(e.notes) + "\r\n")
            if (e.location.isNotBlank()) append("LOCATION:" + encode(e.location) + "\r\n")
            append("PRIORITY:" + when (e.priority) {
                Priority.URGENT -> 1
                Priority.HIGH -> 3
                Priority.NORMAL -> 5
                Priority.LOW -> 9
            })
            append("\r\nEND:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }
}
