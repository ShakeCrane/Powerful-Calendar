package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Read-only, recurrence-aware, 42-day calendar view. No calendar writes here. */
internal object WebSkinMonthProjection {
    const val MAX_OWNERS = 400
    const val MAX_ITEMS = 350
    fun build(records: List<TemporalRecord>, anchorMillis: Long, zone: ZoneId): JSONObject {
        val selected = Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()
        val monthFirst = selected.withDayOfMonth(1)
        val gridFirst = monthFirst.minusDays((monthFirst.dayOfWeek.value - 1).toLong())
        val gridEnd = gridFirst.plusDays(42)
        val from = gridFirst.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = gridEnd.atStartOfDay(zone).toInstant().toEpochMilli()
        val eligible = records.filter { it.kind != TemporalKind.MEMO }
        val owners = eligible.take(MAX_OWNERS)
        val occurrences = displayOccurrences(owners, from, to)
            .sortedWith(compareBy({ it.record.anchorMillis() ?: Long.MAX_VALUE },
                { it.owner.eventSeq }, { it.key }))
        val items = JSONArray()
        occurrences.take(MAX_ITEMS).forEach { item ->
            val start = item.record.anchorMillis() ?: return@forEach
            val day = Instant.ofEpochMilli(start).atZone(item.record.zone).toLocalDate()
            items.put(JSONObject()
                .put("eventSeq", item.owner.eventSeq)
                .put("revision", item.owner.revision)
                .put("occurrenceKey", item.key.take(120))
                .put("title", item.record.title.take(200))
                .put("kind", item.record.kind.name)
                .put("date", day.toString())
                .put("startMillis", start))
        }
        return JSONObject()
            .put("zoneId", zone.id)
            .put("month", monthFirst.toString().take(7))
            .put("selectedDate", selected.toString())
            .put("gridFrom", gridFirst.toString())
            .put("itemLimit", MAX_ITEMS)
            .put("truncated", eligible.size > MAX_OWNERS ||
                occurrences.size > MAX_ITEMS)
            .put("items", items)
    }
}
