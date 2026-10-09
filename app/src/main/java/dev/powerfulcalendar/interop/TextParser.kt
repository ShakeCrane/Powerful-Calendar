package dev.powerfulcalendar.interop

import dev.powerfulcalendar.data.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class ParsedText(val event: CalendarEvent, val note: String)

object TextParser {
    // Deliberately small, deterministic Chinese parser: the user always reviews its output.
    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ParsedText {
        val raw = input.trim()
        var date = now.toLocalDate()
        var dateMatched = false
        val absolute = Regex("""(20\d{2})[-/年.](\d{1,2})[-/月.](\d{1,2})日?""")
        val monthDay = Regex("""(\d{1,2})月(\d{1,2})日?""")
        val abs = absolute.find(raw)
        val md = monthDay.find(raw)
        try {
            when {
                abs != null -> {
                    date = LocalDate.of(abs.groupValues[1].toInt(),
                        abs.groupValues[2].toInt(), abs.groupValues[3].toInt())
                    dateMatched = true
                }
                md != null -> {
                    date = LocalDate.of(now.year, md.groupValues[1].toInt(),
                        md.groupValues[2].toInt())
                    if (date.isBefore(now.toLocalDate().minusDays(10))) date = date.plusYears(1)
                    dateMatched = true
                }
                raw.contains("后天") -> { date = date.plusDays(2); dateMatched = true }
                raw.contains("明天") -> { date = date.plusDays(1); dateMatched = true }
                raw.contains("今天") -> dateMatched = true
            }
        } catch (_: Exception) { date = now.toLocalDate(); dateMatched = false }

        val timePattern = Regex("""(凌晨|早上|上午|中午|下午|傍晚|晚上|晚间)?\s*(\d{1,2})\s*(?::|：|点|时)\s*(\d{1,2}|半|一刻|三刻)?\s*分?""")
        val time = timePattern.find(raw)
        val hour = time?.groupValues?.get(2)?.toIntOrNull()
        val minute = when (time?.groupValues?.get(3)) {
            "半" -> 30
            "一刻" -> 15
            "三刻" -> 45
            else -> time?.groupValues?.get(3)?.toIntOrNull() ?: 0
        }
        val period = time?.groupValues?.get(1) ?: ""
        val correctedHour = when {
            hour == null -> now.plusHours(1).hour
            hour in 1..11 && (period == "下午" || period == "傍晚" || period == "晚上" || period == "晚间") -> hour + 12
            hour == 12 && period == "凌晨" -> 0
            else -> hour
        }
        val validTime = hour != null && correctedHour in 0..23 && minute in 0..59
        val inferred = if (validTime) date.atTime(correctedHour, minute)
            else if (dateMatched) date.atTime(9, 0) else now.plusHours(1).withMinute(0).withSecond(0)
        val startDateTime = if (validTime && !dateMatched && inferred.isBefore(now)) inferred.plusDays(1) else inferred
        val title = raw
            .replace(absolute, "").replace(monthDay, "")
            .replace("今天", "").replace("明天", "").replace("后天", "")
            .replace(timePattern, "")
            .replace(Regex("""^(提醒我|帮我|请|安排|创建|添加|日程|闹钟)\s*"""), "")
            .trim(' ', '，', '。', '：', ':', '\n')
            .ifBlank { "新日程" }
        val start = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val event = CalendarEvent(title = title, startMillis = start,
            endMillis = start + 3_600_000L)
        val note = when {
            !dateMatched && !validTime -> "没有识别出日期或时间，请检查默认时间"
            !validTime -> "未识别出具体时间，默认 09:00"
            !dateMatched -> "未识别出日期，已选择今天或明天最近的对应时间"
            else -> "已提取日期和时间，请确认后保存"
        }
        return ParsedText(event, note)
    }
}
