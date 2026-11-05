package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import dev.powerfulcalendar.core.api.v1.RecurrenceFeatureApiV1
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

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
        zone: ZoneId,
        recurrenceFeatures: RecurrenceFeatureApiV1
    ): JSONObject {
        require(view.selectedDate.year in MIN_YEAR..MAX_YEAR &&
            YearMonth.from(view.selectedDate) == view.month)
        val monthFirst = view.month.atDay(1)
        val gridFirst = monthFirst.minusDays((monthFirst.dayOfWeek.value - 1).toLong())
        val gridEnd = gridFirst.plusDays(42)
        val monthDays = (1..view.month.lengthOfMonth()).map { monthFirst.plusDays((it - 1).toLong()) }
        val from = gridFirst.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = gridEnd.atStartOfDay(zone).toInstant().toEpochMilli()
        val eligible = records
        val owners = eligible.take(MAX_OWNERS)
        val occurrences = displayOccurrences(owners, from, to, recurrenceFeatures)
            .sortedWith(compareBy({ it.record.anchorMillis() ?: Long.MAX_VALUE },
                { it.owner.eventSeq }, { it.key }))
        val items = JSONArray()
        occurrences.take(MAX_ITEMS).forEach { occurrence ->
            val display = occurrence.record
            val visibleDates = monthDays.filter { day ->
                if (display.isDateOnlyDeadline()) display.deadlineDate == day.toString()
                else {
                    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    overlapsDayWindow(display, dayStart, dayEnd)
                }
            }
            if (visibleDates.isEmpty()) return@forEach
            val start = display.anchorMillis() ?: return@forEach
            val displayDate = if (display.isDateOnlyDeadline()) {
                display.deadlineDate ?: return@forEach
            } else Instant.ofEpochMilli(start).atZone(zone).toLocalDate().toString()
            val visibleDateKeys = JSONArray().apply {
                visibleDates.forEach { put(it.toString()) }
            }
            items.put(JSONObject()
                .put("eventSeq", occurrence.owner.eventSeq)
                .put("revision", occurrence.owner.revision)
                .put("occurrenceKey", occurrence.key.take(120))
                .put("originalOccurrenceKey", if (occurrence.owner.recurrence.isRepeating)
                    occurrence.occurrenceKey else JSONObject.NULL)
                .put("title", display.title.take(200))
                .put("kind", display.kind.name)
                .put("kindLabel", display.kind.label)
                .put("summary", TemporalText.summarize(display).take(240))
                .put("sequenceLabel", occurrence.owner.displaySeq())
                .put("date", displayDate)
                .put("visibleDateKeys", visibleDateKeys)
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
