package dev.powerfulcalendar.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimelineTickLabelsTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun springForwardSkipsTheMissingLocalHourOnTheElapsedTimeRuler() {
        val date = LocalDate.of(2026, 3, 8)
        val from = date.atStartOfDay(newYork).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(newYork).toInstant().toEpochMilli() - 1

        val ticks = timelineTickLabels(from, to, tickMinutes = 60, zone = newYork)

        assertEquals(listOf("00:00", "01:00", "03:00", "04:00"), ticks.take(4).map { it.text })
        assertTrue(ticks.none { it.text.startsWith("02:00") })
        assertEquals(60 * 60_000L, ticks[2].instantMillis - ticks[1].instantMillis)
    }

    @Test
    fun fallBackLabelsBothRepeatedHoursWithTheirUtcOffsets() {
        val date = LocalDate.of(2026, 11, 1)
        val from = date.atStartOfDay(newYork).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(newYork).toInstant().toEpochMilli() - 1

        val ticks = timelineTickLabels(from, to, tickMinutes = 60, zone = newYork)

        assertEquals(
            listOf("00:00", "01:00\n-04:00", "01:00\n-05:00", "02:00", "03:00"),
            ticks.take(5).map { it.text }
        )
        assertEquals(60 * 60_000L, ticks[2].instantMillis - ticks[1].instantMillis)
    }
}
