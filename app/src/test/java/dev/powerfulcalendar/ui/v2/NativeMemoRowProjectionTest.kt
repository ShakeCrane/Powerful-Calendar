package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMemoRowProjectionTest {
    @Test fun projectsOpaqueOccurrenceKeyAndFormatsDateInTheOwnerZone() {
        val zone = ZoneId.of("America/Los_Angeles")
        val millis = Instant.parse("2026-01-01T00:30:00Z").toEpochMilli()
        val owner = TemporalRecord(
            eventSeq = 91,
            revision = 3,
            kind = TemporalKind.ALARM,
            title = "重复待办",
            startMillis = millis,
            trackingEnabled = true,
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        )
        val occurrenceKey = "2025-12-31T16:30"

        val row = projectNativeMemoRows(
            listOf(TodoEntry(owner, occurrenceKey, millis, owner.title, completed = true))
        ).single()

        assertEquals("91:$occurrenceKey", row.rowKey)
        assertEquals("重复待办", row.title)
        assertEquals("2025-12-31", row.dateLabel)
        assertEquals(owner.kind.label, row.kindLabel)
        assertTrue(row.completed)
        assertTrue(row.repeating)
        assertTrue(row.sequenceLabel.isNotBlank())
    }

    @Test fun projectsUndatedMemoWithoutInventingADateOrRepeat() {
        val owner = TemporalRecord(
            eventSeq = 92,
            revision = 1,
            kind = TemporalKind.MEMO,
            title = "无日期备忘",
            zoneId = "Asia/Shanghai"
        )

        val row = projectNativeMemoRows(
            listOf(TodoEntry(owner, "single", null, owner.title))
        ).single()

        assertEquals("92:single", row.rowKey)
        assertEquals("无日期", row.dateLabel)
        assertFalse(row.completed)
        assertFalse(row.repeating)
    }
}
