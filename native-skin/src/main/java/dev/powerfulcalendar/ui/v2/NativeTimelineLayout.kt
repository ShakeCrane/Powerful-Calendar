package dev.powerfulcalendar.ui.v2

import java.time.LocalDate
import java.time.ZoneId

/** Date-only deadline window check used by the Native timeline ribbon. */
fun isDateDeadlineVisible(
    event: NativeTimelineEvent,
    camera: NativeTimelineCamera,
    geometry: NativeTimelineGeometry
): Boolean {
    if (!event.dateOnlyDeadline || event.deadlineDate == null) return false
    val zone = ZoneId.of(event.zoneId)
    val startDate = LocalDate.parse(event.deadlineDate)
    val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = startDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val halfSpan = (geometry.spanMillis(camera.hours) / 2).toLong()
    return start < camera.anchorMillis + halfSpan && end > camera.anchorMillis - halfSpan
}
