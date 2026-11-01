package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventRecordV1
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class RuntimeSkinEventRow(
    val event: EventRecordV1,
    val summary: String
)

/** Display-only rows for the runtime Skin bridge; no Core entity is exposed to the renderer. */
internal fun projectRuntimeSkinEventRows(
    records: List<EventRecordV1>,
    zone: ZoneId,
    maxRows: Int = MAX_RUNTIME_SKIN_EVENT_ROWS
): List<RuntimeSkinEventRow> = records
    .sortedBy { it.anchorMillis(zone) ?: Long.MAX_VALUE }
    .take(maxRows.coerceAtLeast(0))
    .map { RuntimeSkinEventRow(it, it.runtimeSummary()) }

internal fun EventKindV1.runtimeSkinLabel(): String = when (this) {
    EventKindV1.ALARM -> "闹钟"
    EventKindV1.ACTIVITY -> "活动"
    EventKindV1.DEADLINE -> "截止"
    EventKindV1.MEMO -> "备忘"
}

private fun EventRecordV1.anchorMillis(inZone: ZoneId): Long? = when (kind) {
    EventKindV1.ALARM, EventKindV1.ACTIVITY -> startMillis
    EventKindV1.DEADLINE -> deadlineMillis ?: deadlineDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.atStartOfDay(inZone)?.toInstant()?.toEpochMilli()
    EventKindV1.MEMO -> memoStartDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.atStartOfDay(inZone)?.toInstant()?.toEpochMilli()
}

private fun EventRecordV1.runtimeSummary(): String {
    val recordZone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    return when (kind) {
        EventKindV1.ALARM -> startMillis?.let {
            Instant.ofEpochMilli(it).atZone(recordZone).format(TIME_FORMAT)
        } ?: "时刻待补全"
        EventKindV1.ACTIVITY -> {
            val start = startMillis
            val end = endMillis
            if (start == null || end == null) "区间待补全" else {
                val from = Instant.ofEpochMilli(start).atZone(recordZone)
                val to = Instant.ofEpochMilli(end).atZone(recordZone)
                if (from.toLocalDate() == to.toLocalDate()) {
                    from.format(TIME_FORMAT) + " – " + to.format(TIME_FORMAT)
                } else {
                    from.format(DATE_FORMAT) + " " + from.format(TIME_FORMAT) + " – " +
                        to.format(DATE_FORMAT) + " " + to.format(TIME_FORMAT)
                }
            }
        }
        EventKindV1.MEMO -> when {
            memoStartDate == null -> "无日期 · 待办"
            memoEndDate == null -> "$memoStartDate · 待办"
            else -> "$memoStartDate ～ $memoEndDate · 待办"
        }
        EventKindV1.DEADLINE -> deadlineDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { "截止 ${it.format(DATE_WEEKDAY_FORMAT)}（日期）" }
            ?: deadlineMillis?.let {
                "截止 " + Instant.ofEpochMilli(it).atZone(recordZone)
                    .format(DATE_FORMAT) + " " +
                    Instant.ofEpochMilli(it).atZone(recordZone).format(TIME_FORMAT)
            }
            ?: "截止待补全"
    }
}

private const val MAX_RUNTIME_SKIN_EVENT_ROWS = 70
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日")
private val DATE_WEEKDAY_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEE")
