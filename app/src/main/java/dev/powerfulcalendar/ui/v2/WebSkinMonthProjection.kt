package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Host-owned month navigation state; selection is independent from the timeline camera. */
internal data class WebSkinMonthViewState(
    val month: YearMonth,
    val selectedDate: LocalDate
)

/** Read-only, recurrence-aware, 42-day calendar view. No calendar writes here. */
internal object WebSkinMonthProjection {
    const val MAX_OWNERS = 400
    const val MAX_ITEMS = 350
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2100

    fun initial(anchorMillis: Long, zone: ZoneId): WebSkinMonthViewState {
        val selected = Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()
        return WebSkinMonthViewState(YearMonth.from(selected), selected)
    }

    fun selectDate(
        state: WebSkinMonthViewState,
        selected: LocalDate
    ): WebSkinMonthViewState? = selected.takeIf {
        it.year in MIN_YEAR..MAX_YEAR && YearMonth.from(it) == state.month
    }?.let { state.copy(selectedDate = it) }

    fun selectMonth(month: YearMonth): WebSkinMonthViewState? = month.takeIf {
        it.year in MIN_YEAR..MAX_YEAR
    }?.let { WebSkinMonthViewState(it, it.atDay(1)) }

    fun browseMonth(
        state: WebSkinMonthViewState,
        deltaMonths: Int
    ): WebSkinMonthViewState? {
        if (deltaMonths !in -12..12 || deltaMonths == 0) return null
        return runCatching { selectMonth(state.month.plusMonths(deltaMonths.toLong())) }
            .getOrNull()
    }

    fun today(today: LocalDate): WebSkinMonthViewState? =
        today.takeIf { it.year in MIN_YEAR..MAX_YEAR }
            ?.let { WebSkinMonthViewState(YearMonth.from(it), it) }

    fun build(
        records: List<TemporalRecord>,
        view: WebSkinMonthViewState,
        zone: ZoneId
    ): JSONObject {
        require(view.selectedDate.year in MIN_YEAR..MAX_YEAR &&
            YearMonth.from(view.selectedDate) == view.month)
        val monthFirst = view.month.atDay(1)
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
                .put("originalOccurrenceKey", if (item.owner.recurrence.isRepeating)
                    item.occurrenceKey else JSONObject.NULL)
                .put("title", item.record.title.take(200))
                .put("kind", item.record.kind.name)
                .put("date", day.toString())
                .put("startMillis", start))
        }
        return JSONObject()
            .put("zoneId", zone.id)
            .put("month", monthFirst.toString().take(7))
            .put("selectedDate", view.selectedDate.toString())
            .put("gridFrom", gridFirst.toString())
            .put("itemLimit", MAX_ITEMS)
            .put("truncated", eligible.size > MAX_OWNERS ||
                occurrences.size > MAX_ITEMS)
            .put("items", items)
    }
}
