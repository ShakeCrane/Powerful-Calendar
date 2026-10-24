package dev.powerfulcalendar.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormalEventSearchTest {
    private fun record(eventSeq: Long, title: String) = TemporalRecord(
        eventSeq = eventSeq,
        revision = 1,
        kind = TemporalKind.ACTIVITY,
        title = title
    )

    @Test
    fun titleAndSequenceSearchKeepRecordOrderAndCapResults() {
        val records = listOf(
            record(90, "Research Alpha"),
            record(3, "Other"),
            record(12, "ALPHA follow-up"),
            record(19, "Alpha notes"),
            record(24, "Alpha 4"),
            record(31, "Alpha 5"),
            record(42, "Alpha 6"),
            record(53, "Alpha 7"),
            record(64, "Alpha 8"),
            record(75, "Alpha 9")
        )

        assertEquals(listOf(90L, 12L, 19L, 24L, 31L, 42L, 53L, 64L),
            FormalEventSearch.filter(records, "alpha").map { it.eventSeq })
        assertEquals(listOf(12L, 19L, 31L),
            FormalEventSearch.filter(records, "1").map { it.eventSeq })
    }

    @Test
    fun blankQueryDoesNotReturnFormalEvents() {
        assertEquals(emptyList<TemporalRecord>(), FormalEventSearch.filter(
            listOf(record(1, "Meeting")), " \t"
        ))
    }
}
