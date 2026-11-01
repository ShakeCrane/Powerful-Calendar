package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.MemoEntryV1
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
        val occurrenceKey = "2025-12-31T16:30"

        val row = projectNativeMemoRows(
            listOf(MemoEntryV1(
                eventSeq = 91, revision = 3, kind = EventKindV1.ALARM,
                occurrenceKey = occurrenceKey, startMillis = millis, title = "重复待办",
                completed = true, zoneId = zone.id, repeating = true
            ))
        ).single()

        assertEquals("91:$occurrenceKey", row.rowKey)
        assertEquals("重复待办", row.title)
        assertEquals("2025-12-31", row.dateLabel)
        assertEquals("闹钟", row.kindLabel)
        assertTrue(row.completed)
        assertTrue(row.repeating)
        assertTrue(row.sequenceLabel.isNotBlank())
    }

    @Test fun projectsUndatedMemoWithoutInventingADateOrRepeat() {
        val row = projectNativeMemoRows(
            listOf(MemoEntryV1(
                eventSeq = 92, revision = 1, kind = EventKindV1.MEMO,
                occurrenceKey = "single", startMillis = null, title = "无日期备忘",
                completed = false, zoneId = "Asia/Shanghai", repeating = false
            ))
        ).single()

        assertEquals("92:single", row.rowKey)
        assertEquals("无日期", row.dateLabel)
        assertFalse(row.completed)
        assertFalse(row.repeating)
    }
}
