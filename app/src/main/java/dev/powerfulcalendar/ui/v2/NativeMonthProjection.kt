package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal data class NativeMonthOpenTarget(
    val owner: TemporalRecord,
    val occurrenceKey: String?
)

internal data class NativeMonthHostProjection(
    val rows: List<NativeMonthRow>,
    val openTargets: Map<String, NativeMonthOpenTarget>
)

/** Core-backed month projection; only NativeMonthRow values cross into :native-skin. */
internal fun projectNativeMonth(
    records: List<TemporalRecord>,
    month: YearMonth,
    zone: ZoneId
): NativeMonthHostProjection {
    val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val days = (1..month.lengthOfMonth()).map(month::atDay)
    val projected = displayOccurrences(records, from, to).mapNotNull { occurrence ->
        val display = occurrence.record
        val visibleDates = days.filter { day ->
            if (display.isDateOnlyDeadline()) display.deadlineDate == day.toString()
            else {
                val begin = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                overlapsDayWindow(display, begin, end)
            }
        }
        if (visibleDates.isEmpty()) return@mapNotNull null
        val kind = when (display.kind) {
            TemporalKind.ALARM -> NativeMonthKind.ALARM
            TemporalKind.ACTIVITY -> NativeMonthKind.ACTIVITY
            TemporalKind.DEADLINE -> NativeMonthKind.DEADLINE
            TemporalKind.MEMO -> NativeMonthKind.MEMO
        }
        val row = NativeMonthRow(
            rowKey = occurrence.key,
            kind = kind,
            kindLabel = display.kind.label,
            title = display.title,
            summary = TemporalText.summarize(display),
            sequenceLabel = occurrence.owner.displaySeq(),
            visibleDateKeys = visibleDates.map { it.toString() }
        )
        val target = NativeMonthOpenTarget(
            owner = occurrence.owner,
            occurrenceKey = occurrence.occurrenceKey.takeIf {
                occurrence.owner.recurrence.isRepeating
            }
        )
        Triple(row, target, display.anchorMillis())
    }.sortedBy { it.third }

    return NativeMonthHostProjection(
        rows = projected.map { it.first },
        openTargets = projected.associate { (row, target) -> row.rowKey to target }
    )
}

/** Keeps only the currently projected month's action map for opaque Native row keys. */
internal class NativeMonthProjectionSession(
    private val records: List<TemporalRecord>,
    private val zone: ZoneId
) {
    private var openTargets: Map<String, NativeMonthOpenTarget> = emptyMap()

    fun rowsFor(month: YearMonth): List<NativeMonthRow> {
        val projection = projectNativeMonth(records, month, zone)
        openTargets = projection.openTargets
        return projection.rows
    }

    fun openTarget(rowKey: String): NativeMonthOpenTarget? = openTargets[rowKey]
}
