package dev.powerfulcalendar.interop

import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.Priority
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

data class IcsImport(val events: List<CalendarEvent>, val warnings: List<String>)

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
        val warnings = mutableListOf<String>()
        var current: MutableMap<String, Pair<String, String>>? = null
        for (line in lines) {
            when (line.trim()) {
                "BEGIN:VEVENT" -> current = mutableMapOf()
                "END:VEVENT" -> {
                    current?.let { fields ->
                        try {
                            val startField = fields["DTSTART"] ?: error("缺少 DTSTART")
                            val endField = fields["DTEND"]
                            val allDay = startField.first.contains("VALUE=DATE") ||
                                (startField.second.length == 8)
                            val start = parseDate(startField.first, startField.second)
                            val end = endField?.let { parseDate(it.first, it.second) }
                                ?: start + if (allDay) 86_400_000L else 3_600_000L
                            val uid = fields["UID"]?.second
                            val title = decode(fields["SUMMARY"]?.second ?: "未命名事件")
                            val priority = when (fields["PRIORITY"]?.second?.toIntOrNull()) {
                                in 1..3 -> Priority.HIGH
                                in 4..5 -> Priority.NORMAL
                                in 6..9 -> Priority.LOW
                                else -> Priority.NORMAL
                            }
                            if ("RRULE" in fields || "RECURRENCE-ID" in fields) {
                                warnings.add("重复日程「" + title + "」仅导入当前 VEVENT，尚不支持 RRULE 展开")
                            }
                            events.add(CalendarEvent(
                                title = title,
                                startMillis = start,
                                endMillis = end.coerceAtLeast(start + 60_000L),
                                notes = decode(fields["DESCRIPTION"]?.second ?: ""),
                                location = decode(fields["LOCATION"]?.second ?: ""),
                                priority = priority,
                                reminderMinutes = -1,
                                allDay = allDay,
                                source = "ics",
                                externalId = uid ?: UUID.randomUUID().toString()
                            ))
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
                        current[key.substringBefore(';').uppercase()] = key to line.substring(colon + 1)
                    }
                }
            }
        }
        return IcsImport(events, warnings)
    }

    private fun parseDate(key: String, input: String): Long {
        val dateOnly = key.contains("VALUE=DATE") || input.length == 8
        val tzid = key.split(';').firstOrNull { it.startsWith("TZID=", true) }
            ?.substringAfter('=')?.trim('"')
        val zone = tzid?.let {
            try { ZoneId.of(it) } catch (_: Exception) { ZoneId.systemDefault() }
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
