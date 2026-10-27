package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.TimelineLayout
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * Host-controlled, bounded timeline projection for v3 Web skins.
 *
 * Skins may draw a timeline anywhere, but must not reinterpret persisted recurrence,
 * time-zone, precision or event identity. Every projected occurrence references its
 * existing formal owner and revision. No free-form notes or privileged objects escape.
 */
internal object WebSkinTimelineProjection {
    const val MAX_OWNERS = 400
    const val MAX_ITEMS = 300

    fun build(
        records: List<TemporalRecord>,
        camera: TimelineCoordinate.Camera,
        nowMillis: Long,
        zone: ZoneId
    ): JSONObject {
        val hours = TimelineCoordinate.clampHours(camera.hours)
        val halfSpan = (TimelineCoordinate.spanMillis(hours) / 2).toLong()
        val visibleStart = camera.anchorMillis - halfSpan
        val visibleEnd = camera.anchorMillis + halfSpan
        val day = Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate()
        val from = day.minusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(9).atStartOfDay(zone).toInstant().toEpochMilli()

        val owners = records.filter { it.kind != TemporalKind.MEMO }.take(MAX_OWNERS)
        val projected = owners.filter { !it.recurrence.isRepeating }
            .mapNotNull(::singleDisplayOccurrence) +
            displayOccurrences(owners.filter { it.recurrence.isRepeating }, from, to)
        val overlapping = projected.filter { slot ->
            val record = slot.record
            if (record.isDateOnlyDeadline()) {
                val date = runCatching { LocalDate.parse(record.deadlineDate) }.getOrNull()
                    ?: return@filter false
                val start = date.atStartOfDay(record.zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(record.zone).toInstant().toEpochMilli()
                start < visibleEnd && end > visibleStart
            } else {
                val start = record.anchorMillis() ?: return@filter false
                val end = record.spanEndMillis() ?: start
                if (end > start) start < visibleEnd && end > visibleStart
                else start >= visibleStart && start < visibleEnd
            }
        }
        // A bounded subset is explicit; do not imply that clipped occurrences are absent.
        val selected = overlapping.sortedWith(compareBy<DisplayOccurrence>(
            { abs((it.record.visualAxisMillis() ?: camera.anchorMillis).toDouble() -
                camera.anchorMillis.toDouble()) },
            { it.owner.eventSeq },
            { it.key }
        )).take(MAX_ITEMS).sortedWith(compareBy(
            { it.record.visualAxisMillis() ?: Long.MAX_VALUE },
            { it.owner.eventSeq }, { it.key }
        ))
        val layout = TimelineLayout.layout(selected.map { it.record })
        val lanes = IdentityHashMap<TemporalRecord, Int>()
        layout.items.forEach { lanes[it.record] = it.lane }

        val entries = JSONArray()
        selected.forEach { slot ->
            val record = slot.record
            entries.put(JSONObject()
                .put("eventSeq", slot.owner.eventSeq)
                .put("revision", slot.owner.revision)
                .put("occurrenceKey", slot.key.take(120))
                .put("originalOccurrenceKey", if (slot.owner.recurrence.isRepeating)
                    slot.occurrenceKey else JSONObject.NULL)
                .put("kind", record.kind.name)
                .put("title", record.title.take(300))
                .put("side", record.kind.side.name)
                .put("lane", lanes[record] ?: 0)
                .put("startMillis", record.visualAxisMillis())
                .put("endMillis", if (record.isDateOnlyDeadline()) JSONObject.NULL
                    else record.spanEndMillis())
                .put("precision", record.precision.name)
                .put("deadlineDate", record.deadlineDate ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("zoneId", zone.id)
            .put("anchorMillis", camera.anchorMillis)
            .put("hours", hours)
            .put("visibleStartMillis", visibleStart)
            .put("visibleEndMillis", visibleEnd)
            .put("nowMillis", nowMillis)
            .put("items", entries)
            .put("itemLimit", MAX_ITEMS)
            .put("truncated", records.count { it.kind != TemporalKind.MEMO } > MAX_OWNERS ||
                overlapping.size > MAX_ITEMS)
    }
}
