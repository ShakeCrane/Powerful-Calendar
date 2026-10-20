package dev.powerfulcalendar.interop

import dev.powerfulcalendar.data.CalendarEvent
import dev.powerfulcalendar.data.Priority
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class InteropTest {
    @Test fun parsesChineseDateTime() {
        val parsed = TextParser.parse("明天下午3点开组会", LocalDateTime.of(2026, 10, 9, 10, 0))
        val local = java.time.Instant.ofEpochMilli(parsed.event.startMillis)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 10, 10, 15, 0), local)
        assertEquals("开组会", parsed.event.title)
    }

    @Test fun timeWithoutDateRollsForward() {
        val parsed = TextParser.parse("晚上8点复习", LocalDateTime.of(2026, 10, 9, 22, 0))
        val local = java.time.Instant.ofEpochMilli(parsed.event.startMillis)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 10, 10, 20, 0), local)
    }

    @Test fun missingDateOrTimeIsNotValidatedForDraftCreation() {
        val now = LocalDateTime.of(2026, 10, 9, 10, 0)
        val noTime = TextParser.parse("明天开会", now)
        assertTrue(noTime.hasExplicitDate)
        assertFalse(noTime.hasUnambiguousTime)
        val noDate = TextParser.parse("下午三点开会", now)
        assertFalse(noDate.hasExplicitDate)
        assertFalse(noDate.hasUnambiguousTime)
        val vague = TextParser.parse("明天3点开会", now)
        assertTrue(vague.hasExplicitDate)
        assertFalse(vague.hasUnambiguousTime)
        val certain = TextParser.parse("明天下午3点开会", now)
        assertTrue(certain.hasExplicitDate)
        assertTrue(certain.hasUnambiguousTime)
    }

    @Test fun icsRoundTripWithEscapes() {
        val start = LocalDateTime.of(2026, 10, 10, 13, 45)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val event = CalendarEvent(title = "组会, 第一阶段", startMillis = start,
            endMillis = start + 3_600_000L, notes = "第一行\n第二行",
            priority = Priority.HIGH)
        val encoded = IcsCodec.export(listOf(event))
        val decoded = IcsCodec.parse(encoded)
        assertTrue(decoded.warnings.isEmpty())
        assertEquals(1, decoded.events.size)
        assertEquals(event.title, decoded.events.single().title)
        assertEquals(event.notes, decoded.events.single().notes)
        assertEquals(event.startMillis, decoded.events.single().startMillis)
    }

    @Test fun parsesFoldedIcs() {
        val content = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\n" +
            "UID:example-1\r\nDTSTART:20261009T153000Z\r\nDTEND:20261009T163000Z\r\n" +
            "SUMMARY:Weekly\r\n Meeting\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val events = IcsCodec.parse(content).events
        assertEquals(1, events.size)
        assertEquals("WeeklyMeeting", events.single().title)
    }

    @Test fun rejectsIcsUnknownTimezoneInsteadOfFallingBackToDeviceZone() {
        val content = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:badzone\n" +
            "DTSTART;TZID=Fake/Zone:20261009T153000\nDTEND;TZID=Fake/Zone:20261009T163000\n" +
            "SUMMARY:错误时区\nEND:VEVENT\nEND:VCALENDAR"
        val parsed = IcsCodec.parse(content)
        assertTrue(parsed.events.isEmpty())
        assertTrue(parsed.warnings.any { it.contains("TZID") })
    }

    @Test fun rejectsIcsMissingEndInsteadOfInventingAnHour() {
        val content = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:noend\n" +
            "DTSTART:20261009T153000Z\nSUMMARY:缺少结束\nEND:VEVENT\nEND:VCALENDAR"
        val parsed = IcsCodec.parse(content)
        assertTrue(parsed.events.isEmpty())
        assertTrue(parsed.warnings.any { it.contains("DTEND") })
    }

    @Test fun warnsWhenRecurrenceIsNotExpanded() {
        val content = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:1\n" +
            "DTSTART:20261009T153000Z\nDTEND:20261009T163000Z\n" +
            "RRULE:FREQ=WEEKLY;COUNT=8\nSUMMARY:例会\nEND:VEVENT\nEND:VCALENDAR"
        val result = IcsCodec.parse(content)
        assertTrue(result.events.isEmpty())
        assertTrue(result.warnings.any { it.contains("RRULE") })
    }
}
