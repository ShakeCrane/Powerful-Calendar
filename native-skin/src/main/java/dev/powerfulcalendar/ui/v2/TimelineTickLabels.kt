package dev.powerfulcalendar.ui.v2

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TimelineTickLabel(val instantMillis: Long, val text: String)

private val timelineClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Build the labels used by the time ruler and by current-time badge collision avoidance. */
fun timelineTickLabels(
    fromMillis: Long,
    toMillis: Long,
    tickMinutes: Long,
    zone: ZoneId
): List<TimelineTickLabel> {
    require(fromMillis <= toMillis) { "Timeline tick range must be ascending" }
    require(tickMinutes > 0) { "Timeline tick interval must be positive" }

    var tick = Instant.ofEpochMilli(fromMillis).atZone(zone)
        .withSecond(0).withNano(0)
    val minuteOfDay = tick.hour * 60L + tick.minute
    tick = tick.minusMinutes(minuteOfDay % tickMinutes)

    val result = ArrayList<TimelineTickLabel>()
    var count = 0
    while (count < 400) {
        val instantMillis = tick.toInstant().toEpochMilli()
        if (instantMillis > toMillis) break

        val ambiguous = zone.rules.getValidOffsets(tick.toLocalDateTime()).size > 1
        result += TimelineTickLabel(
            instantMillis = instantMillis,
            text = tick.format(timelineClockFormat) + if (ambiguous) "\n${tick.offset}" else ""
        )
        tick = tick.plusMinutes(tickMinutes)
        count++
    }
    return result
}
