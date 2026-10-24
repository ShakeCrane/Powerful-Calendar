package dev.powerfulcalendar.core

import java.time.DayOfWeek
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * PowerCal temporal protocol v0.3.
 *
 * Three user-facing shapes over one controlled inheritance:
 *  - [TemporalKind.ALARM]    has a START point only      -> left of the central axis
 *  - [TemporalKind.ACTIVITY] has START and END            -> right of the central axis
 *  - [TemporalKind.DEADLINE] has a deadline point or date -> left of the central axis
 *
 * `eventSeq` is the permanent identity. Nothing in this file may derive identity, order or
 * ringing behaviour from nullable time fields: kind, trigger, schedule and execution state are
 * intentionally separate concepts.
 */

enum class TemporalKind(val label: String) {
    ALARM("闹钟"),
    ACTIVITY("活动"),
    DEADLINE("截止"),
    MEMO("备忘");

    val side: AxisSide get() = if (this == ALARM || this == DEADLINE) AxisSide.LEFT else AxisSide.RIGHT
}

enum class AxisSide { LEFT, RIGHT }

/** Date-only deadlines stay date-only; they are never silently widened to 23:59. */
enum class TimePrecision { INSTANT, DATE }

enum class RecurrenceFreq { NONE, DAILY, WEEKLY, MONTHLY }

enum class DraftOperation(val label: String, val requiresExisting: Boolean) {
    CREATE("新建", false),
    REPLACE("修改", true),
    CONVERT("类型转换", true),
    DELETE("删除", true)
}

enum class DraftStatus { PENDING, CONFLICT, DISCARDED, COMMITTED, INVALID }

/** The field a card is still missing before it may legally publish. */
enum class DraftField(val label: String) {
    TITLE("标题"),
    START("开始时间"),
    END("结束时间"),
    DEADLINE("截止日期或时刻")
}

/**
 * A committed formal snapshot. Exactly one of these exists per eventSeq, at the highest
 * committed [revision]. Field replacement is whole-object; callers must not merge field-wise.
 */
data class TemporalRecord(
    val eventSeq: Long,
    val revision: Long,
    val kind: TemporalKind,
    val title: String,
    val notes: String = "",
    val location: String = "",
    /** ALARM start point / ACTIVITY start. */
    val startMillis: Long? = null,
    /** ACTIVITY end. */
    val endMillis: Long? = null,
    /** DEADLINE expressed as an instant. */
    val deadlineMillis: Long? = null,
    /** DEADLINE expressed as a calendar date only. */
    val deadlineDate: String? = null,
    /** MEMO is a date-only todo; either boundary may be absent for undated tasks. */
    val memoStartDate: String? = null,
    val memoEndDate: String? = null,
    /** Legacy events do not automatically become unfinished tasks. */
    val trackingEnabled: Boolean = kind == TemporalKind.MEMO,
    /** Stable original recurrence occurrence keys; "single" for non-repeating events. */
    val completedKeys: Set<String> = emptySet(),
    val recurrence: Recurrence = Recurrence.NONE,
    /** Reminder offset in minutes before the anchor; negative means no reminder. */
    val reminderMinutes: Int = -1,
    val reminderMethod: ReminderMethod = ReminderMethod.MESSAGE,
    val reminderTiming: ReminderTiming = ReminderTiming.OFFSET,
    val enabled: Boolean = true,
    /** Provenance of the record itself; replaced snapshots keep this unless the user changes it. */
    val source: String = "local",
    val zoneId: String = ZoneId.systemDefault().id,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val precision: TimePrecision get() = if (kind == TemporalKind.DEADLINE && deadlineDate != null) {
        TimePrecision.DATE
    } else {
        TimePrecision.INSTANT
    }

    val zone: ZoneId get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

    /** Point used for "where does this sit on the axis". Date-only uses the start of its day band. */
    fun anchorMillis(inZone: ZoneId = zone): Long? = when (kind) {
        TemporalKind.ALARM, TemporalKind.ACTIVITY -> startMillis
        TemporalKind.DEADLINE -> deadlineMillis
            ?: deadlineDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.atStartOfDay(inZone)?.toInstant()?.toEpochMilli()
        TemporalKind.MEMO -> memoStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(inZone)?.toInstant()?.toEpochMilli()
    }

    /** Instant-based end used for lane overlap and drag ghosts; never used to fake precision. */
    fun spanEndMillis(inZone: ZoneId = zone): Long? = when (kind) {
        TemporalKind.ACTIVITY -> endMillis ?: startMillis
        TemporalKind.ALARM -> startMillis
        TemporalKind.DEADLINE -> anchorMillis(inZone)
        TemporalKind.MEMO -> memoEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.plusDays(1)?.atStartOfDay(inZone)?.toInstant()?.toEpochMilli() ?: anchorMillis(inZone)
    }

    fun isDateOnlyDeadline(): Boolean = precision == TimePrecision.DATE

    /** Presentation anchor only; stored deadline precision/value remain unchanged. */
    fun visualAxisMillis(inZone: ZoneId = zone): Long? =
        if (isDateOnlyDeadline()) deadlineDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.plusDays(1)?.atStartOfDay(inZone)?.toInstant()?.toEpochMilli()?.minus(1)
        else anchorMillis(inZone)

    /** Validation of the record as a whole. Returns a user-facing reason, or null when legal. */
    fun validate(): String? {
        if (title.isBlank()) return "请输入事件标题"
        if (recurrence.interval < 1) return "重复间隔必须至少为 1"
        if (recurrence.activeFromDate != null &&
            runCatching { LocalDate.parse(recurrence.activeFromDate) }.isFailure)
            return "重复开始日期无效"
        if (recurrence.activeUntilDate != null &&
            runCatching { LocalDate.parse(recurrence.activeUntilDate) }.isFailure)
            return "重复结束日期无效"
        if (recurrence.activeFromDate != null && recurrence.activeUntilDate != null &&
            recurrence.activeUntilDate < recurrence.activeFromDate) return "重复日期范围顺序无效"
        if (recurrence.activeFromDate != null &&
            runCatching { LocalDate.parse(recurrence.activeFromDate) }.isFailure)
            return "重复开始日期无效"
        if (recurrence.activeUntilDate != null &&
            runCatching { LocalDate.parse(recurrence.activeUntilDate) }.isFailure)
            return "重复结束日期无效"
        if (recurrence.activeFromDate != null && recurrence.activeUntilDate != null &&
            recurrence.activeUntilDate < recurrence.activeFromDate) return "重复日期范围顺序无效"
        if (reminderMinutes !in -1..10080) return "提醒提前量必须在 0～10080 分钟，或选择关闭"
        return when (kind) {
            TemporalKind.ALARM -> when {
                startMillis == null -> "闹钟需要开始时刻"
                endMillis != null -> "闹钟不能有结束时刻"
                deadlineMillis != null || deadlineDate != null -> "闹钟不能带截止时间"
                else -> null
            }
            TemporalKind.ACTIVITY -> when {
                startMillis == null || endMillis == null -> "活动需要开始和结束时刻"
                endMillis <= startMillis -> "结束时间必须晚于开始时间"
                deadlineMillis != null || deadlineDate != null -> "活动不能带截止时间"
                else -> null
            }
            TemporalKind.DEADLINE -> when {
                deadlineMillis != null && deadlineDate != null -> "截止时间不能同时是日期和时刻"
                deadlineMillis == null && deadlineDate == null -> "截止事件需要日期或时刻"
                deadlineDate != null &&
                    runCatching { LocalDate.parse(deadlineDate) }.isFailure -> "截止日期格式无效"
                startMillis != null || endMillis != null -> "截止事件不能带开始/结束区间"
                else -> null
            }
            TemporalKind.MEMO -> when {
                startMillis != null || endMillis != null || deadlineMillis != null ||
                    deadlineDate != null -> "备忘不能使用精确时间或截止字段"
                memoStartDate != null && runCatching { LocalDate.parse(memoStartDate) }.isFailure ->
                    "备忘开始日期无效"
                memoEndDate != null && runCatching { LocalDate.parse(memoEndDate) }.isFailure ->
                    "备忘结束日期无效"
                memoEndDate != null && memoStartDate == null -> "备忘结束日期需要开始日期"
                memoEndDate != null && memoEndDate < memoStartDate!! -> "备忘结束日期不能早于开始日期"
                recurrence.isRepeating && memoStartDate == null -> "重复备忘需要日期作为周期起点"
                reminderMinutes >= 0 && memoStartDate == null -> "无日期备忘不能凭空生成提醒时刻"
                !trackingEnabled -> "备忘必须启用完成跟踪"
                else -> null
            }
        }
    }

    fun withSeq(seq: Long): TemporalRecord = copy(eventSeq = seq)

    fun displaySeq(): String = formatSeq(eventSeq)

    companion object {
        fun formatSeq(seq: Long): String = "#" + seq.toString().padStart(4, '0')
    }
}

/** Recurrence is a semantic rule; future occurrences are derived, never stored as truth. */
data class Recurrence(
    val freq: RecurrenceFreq = RecurrenceFreq.NONE,
    val interval: Int = 1,
    /** Total number of occurrences including the first, or null for unbounded. */
    val count: Int? = null,
    val untilMillis: Long? = null,
    /** Inclusive local-date effective range; independent from stable recurrence anchor. */
    val activeFromDate: String? = null,
    val activeUntilDate: String? = null,
    /** Weekly filter; empty means "same weekday as the first occurrence". */
    val byDay: Set<DayOfWeek> = emptySet(),
    /**
     * Stable occurrence keys (original local slot, ISO `yyyy-MM-ddTHH:mm`) that are skipped.
     * Skipping an instance never mutates the series.
     */
    val exceptions: Set<String> = emptySet(),
    /** Moved instances keyed by original slot. */
    val overrides: Map<String, OccurrenceOverride> = emptyMap()
) {
    val isRepeating: Boolean get() = freq != RecurrenceFreq.NONE

    companion object {
        val NONE = Recurrence()
    }
}

/** Explicit scope chosen when a user edits one projected occurrence of a repeating event. */
enum class RecurrenceEditScope {
    SINGLE_OCCURRENCE,
    THIS_AND_FUTURE,
    WHOLE_SERIES
}

data class OccurrenceOverride(val startMillis: Long, val endMillis: Long?, val title: String? = null)

/** One materialized instance of an event. Identity = eventSeq + original slot key. */
data class Occurrence(
    val key: String,
    val startMillis: Long,
    val endMillis: Long?,
    val skipped: Boolean = false,
    val moved: Boolean = false
)

object RecurrenceEngine {
    /** Compare against ORIGINAL slot, not an overridden display instant. */
    fun activeOnOriginalKey(rule: Recurrence, key: String): Boolean {
        val localDate = key.take(10)
        return (rule.activeFromDate == null || localDate >= rule.activeFromDate) &&
            (rule.activeUntilDate == null || localDate <= rule.activeUntilDate)
    }

    private val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun keyFor(local: LocalDateTime): String = local.format(KEY_FORMAT)

    private fun projectionZone(record: TemporalRecord, localZone: ZoneId): ZoneId =
        if (record.kind == TemporalKind.ALARM && record.recurrence.freq == RecurrenceFreq.DAILY) {
            localZone
        } else {
            record.zone
        }

    /**
     * Materializes the instances whose **effective** time intersects `[fromMillis, toMillis)`.
     *
     * Slots are computed by index in **wall-clock** time using the record's own zone, so a daily
     * 07:30 occurrence stays 07:30 there across DST. A slot inside a DST gap has no occurrence but
     * still consumes its generated index/count; a fold uses the earlier offset (first instant).
     * `count` includes generated slots such as skipped exceptions and DST-gap slots, so skipping
     * one never shifts the rest of the series.
     *
     * Effective time is what matters for a window query:
     *  - an override may move an instance from months outside the window *into* it, and such an
     *    instance is still identified by its original slot key;
     *  - an instance moved out of the window must not be returned;
     *  - the caller never has to guess an "original slot" search range.
     *
     * [prioritizeWindowStarts] is for bounded renderers: when overlap count exceeds [limit],
     * occurrences starting inside the window take priority over older carry-in intervals.
     */
    fun occurrences(
        record: TemporalRecord,
        fromMillis: Long,
        toMillis: Long,
        limit: Int = 512,
        prioritizeWindowStarts: Boolean = false
    ): List<Occurrence> {
        if (toMillis <= fromMillis || limit <= 0) return emptyList()
        val anchor = record.anchorMillis() ?: return emptyList()
        val zone = record.zone
        val duration = ((record.spanEndMillis() ?: anchor) - anchor).coerceAtLeast(0L)
        val rule = record.recurrence

        if (!rule.isRepeating) {
            val key = keyFor(Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime())
            if (key in rule.exceptions) return emptyList()
            val moved = rule.overrides[key]
            val start = moved?.startMillis ?: anchor
            val end = moved?.endMillis
                ?: if (record.kind == TemporalKind.ACTIVITY) start + duration else null
            return if (overlapsWindow(start, end, fromMillis, toMillis)) {
                listOf(Occurrence(key, start, end, moved = moved != null))
            } else {
                emptyList()
            }
        }

        if (prioritizeWindowStarts) {
            return occurrencesPrioritizingWindowStarts(
                record, fromMillis, toMillis, limit, anchor, duration, rule
            ).filter { activeOnOriginalKey(rule, it.key) }.filter { activeOnOriginalKey(rule, it.key) }
        }

        val results = ArrayList<Occurrence>()
        // (A) Overrides are searched by EFFECTIVE time, so a far-away slot moved into this window
        // is found regardless of where its original slot sits in the series. The original key must
        // still identify a real slot within count/until; an override cannot extend the series.
        rule.overrides.forEach { (key, override) ->
            if (key in rule.exceptions || !activeOnOriginalKey(rule, key) ||
                seriesSlotIndex(record, key) == null) return@forEach
            val occurrence = overrideOccurrence(record, key, override, duration)
            if (overlapsWindow(occurrence.startMillis, occurrence.endMillis, fromMillis, toMillis)) {
                results.add(occurrence)
            }
        }

        // (B) Natural slots near the window. The scan starts one activity length early so an
        // activity that began before the window but still overlaps it is included.
        val firstLocal = Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime()
        val countLimit = rule.count ?: Int.MAX_VALUE
        val scanFrom = fromMillis - duration - 1
        var index = startIndexNear(firstLocal, rule, scanFrom, zone, countLimit)
        var emitted = 0
        var iterations = 0L
        // Budget for filtered generated slots (exceptions, overrides, inactive keys, and DST gaps)
        // so a dense edited section cannot silently hide later results.
        val iterationCap = limit.toLong() + rule.exceptions.size + rule.overrides.size + 2L
        while (index < countLimit && emitted < limit && iterations < iterationCap) {
            iterations++
            val slotLocal = runCatching { slotAt(firstLocal, rule, index) }.getOrNull() ?: break
            index++
            val slotPosition = slotOrderMillis(slotLocal, zone) ?: break
            if (rule.untilMillis != null && slotPosition > rule.untilMillis) break
            if (slotPosition >= toMillis) break
            val slotStart = slotMillis(slotLocal, zone) ?: continue
            val slotKey = keyFor(slotLocal)
            // An overridden slot already contributed (or was correctly excluded) in step (A).
            if (slotKey in rule.overrides || slotKey in rule.exceptions ||
                !activeOnOriginalKey(rule, slotKey)) continue
            val end = if (record.kind == TemporalKind.ACTIVITY) slotStart + duration else null
            if (!overlapsWindow(slotStart, end, fromMillis, toMillis)) continue
            results.add(Occurrence(slotKey, slotStart, end))
            emitted++
        }
        return results.sortedWith(compareBy({ it.startMillis }, { it.key })).take(limit)
    }

    /**
     * Bounded UI projection. When an unusually long activity creates more overlapping occurrences
     * than the renderer can display, keep all starts inside the requested window first, then fill
     * remaining capacity with the nearest carry-in occurrences.
     */
    private fun occurrencesPrioritizingWindowStarts(
        record: TemporalRecord,
        fromMillis: Long,
        toMillis: Long,
        limit: Int,
        anchor: Long,
        duration: Long,
        rule: Recurrence
    ): List<Occurrence> {
        val inside = ArrayList<Occurrence>()
        val carryIn = ArrayList<Occurrence>()
        val zone = record.zone

        rule.overrides.forEach { (key, override) ->
            if (key in rule.exceptions || !activeOnOriginalKey(rule, key) ||
                seriesSlotIndex(record, key) == null) return@forEach
            val occurrence = overrideOccurrence(record, key, override, duration)
            if (!overlapsWindow(occurrence.startMillis, occurrence.endMillis, fromMillis, toMillis)) {
                return@forEach
            }
            if (occurrence.startMillis >= fromMillis) inside.add(occurrence) else carryIn.add(occurrence)
        }

        val firstLocal = Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime()
        val countLimit = rule.count ?: Int.MAX_VALUE
        var index = startIndexNear(firstLocal, rule, fromMillis, zone, countLimit)
        var emitted = 0
        var iterations = 0L
        val iterationCap = limit.toLong() + rule.exceptions.size + rule.overrides.size + 2L
        while (index < countLimit && emitted < limit && iterations < iterationCap) {
            iterations++
            val slotLocal = runCatching { slotAt(firstLocal, rule, index) }.getOrNull() ?: break
            index++
            val slotPosition = slotOrderMillis(slotLocal, zone) ?: break
            if (rule.untilMillis != null && slotPosition > rule.untilMillis) break
            if (slotPosition >= toMillis) break
            val slotStart = slotMillis(slotLocal, zone) ?: continue
            if (slotStart < fromMillis) continue
            val key = keyFor(slotLocal)
            if (key in rule.exceptions || key in rule.overrides) continue
            val end = if (record.kind == TemporalKind.ACTIVITY) slotStart + duration else null
            inside.add(Occurrence(key, slotStart, end))
            emitted++
        }

        val selectedInside = inside
            .sortedWith(compareBy({ it.startMillis }, { it.key }))
            .take(limit)
        val remaining = limit - selectedInside.size
        if (remaining > 0 && duration > 0) {
            var carryIndex = lastSlotIndexBefore(firstLocal, rule, fromMillis, zone, countLimit) ?: -1
            if (rule.untilMillis != null && rule.untilMillis < Long.MAX_VALUE) {
                val lastWithinUntil = lastSlotIndexBefore(
                    firstLocal, rule, rule.untilMillis + 1, zone, countLimit
                ) ?: -1
                carryIndex = minOf(carryIndex, lastWithinUntil)
            }
            var scans = 0L
            val scanCap = remaining.toLong() + rule.exceptions.size + rule.overrides.size + 1L
            while (carryIndex >= 0 && carryIn.size < remaining && scans < scanCap) {
                scans++
                val slotLocal = runCatching { slotAt(firstLocal, rule, carryIndex) }.getOrNull() ?: break
                val slotPosition = slotOrderMillis(slotLocal, zone) ?: break
                if (rule.untilMillis != null && slotPosition > rule.untilMillis) {
                    carryIndex--
                    continue
                }
                val slotStart = slotMillis(slotLocal, zone)
                if (slotStart == null) {
                    carryIndex--
                    continue
                }
                val slotEnd = runCatching { Math.addExact(slotStart, duration) }.getOrDefault(Long.MAX_VALUE)
                if (slotEnd <= fromMillis) break
                val key = keyFor(slotLocal)
                if (key !in rule.exceptions && key !in rule.overrides) {
                    carryIn.add(Occurrence(key, slotStart, slotEnd))
                }
                carryIndex--
            }
        }

        val selectedCarry = carryIn
            .sortedWith(compareByDescending<Occurrence> { it.startMillis }.thenBy { it.key })
            .take(remaining)
        return (selectedInside + selectedCarry)
            .sortedWith(compareBy({ it.startMillis }, { it.key }))
    }

    /**
     * Intersection test used for both point and interval occurrences. A zero-length point is inside
     * the window when `from <= start < to`; an interval may start earlier or end later as long as
     * they overlap.
     */
    private fun overlapsWindow(start: Long, end: Long?, from: Long, to: Long): Boolean {
        val effectiveEnd = end ?: start
        return if (effectiveEnd > start) {
            start < to && effectiveEnd > from
        } else {
            start >= from && start < to
        }
    }

    /** One canonical effective-time projection for a moved original slot. */
    private fun overrideOccurrence(
        record: TemporalRecord,
        originalKey: String,
        override: OccurrenceOverride,
        duration: Long
    ): Occurrence {
        val end = override.endMillis
            ?: if (record.kind == TemporalKind.ACTIVITY) override.startMillis + duration else null
        return Occurrence(originalKey, override.startMillis, end, moved = true)
    }

    /**
     * Returns whether an override key is a generated slot in this series, including its count and
     * inclusive-until bounds. Overrides are allowed to move a slot anywhere in effective time, but
     * they cannot manufacture a slot that the recurrence rule never generated.
     */
    private fun seriesSlotIndex(record: TemporalRecord, key: String): Int? {
        val rule = record.recurrence
        if (!rule.isRepeating) return null
        val firstLocal = record.anchorMillis()?.let {
            Instant.ofEpochMilli(it).atZone(record.zone).toLocalDateTime()
        } ?: return null
        val local = runCatching { LocalDateTime.parse(key) }.getOrNull() ?: return null
        if (keyFor(local) != key ||
            local.hour != firstLocal.hour || local.minute != firstLocal.minute
        ) return null

        val interval = rule.interval.coerceAtLeast(1).toLong()
        val index = when (rule.freq) {
            RecurrenceFreq.NONE -> return null
            RecurrenceFreq.DAILY -> {
                val days = ChronoUnit.DAYS.between(firstLocal.toLocalDate(), local.toLocalDate())
                if (days < 0 || days % interval != 0L) return null
                days / interval
            }
            RecurrenceFreq.WEEKLY -> {
                val days = weeklyDays(rule, firstLocal)
                val firstWeekExtra = days.filter { it > firstLocal.dayOfWeek.value }
                val firstMonday = firstLocal.toLocalDate()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val slotMonday = local.toLocalDate()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekDistance = ChronoUnit.WEEKS.between(firstMonday, slotMonday)
                if (weekDistance < 0) return null
                if (weekDistance == 0L) {
                    if (local.toLocalDate() == firstLocal.toLocalDate()) {
                        0L
                    } else {
                        val extraIndex = firstWeekExtra.indexOf(local.dayOfWeek.value)
                        if (extraIndex < 0) return null
                        extraIndex + 1L
                    }
                } else {
                    if (weekDistance % interval != 0L) return null
                    val dayIndex = days.indexOf(local.dayOfWeek.value)
                    if (dayIndex < 0) return null
                    firstWeekExtra.size + 1L +
                        (weekDistance / interval - 1L) * days.size + dayIndex
                }
            }
            RecurrenceFreq.MONTHLY -> {
                val firstMonth = java.time.YearMonth.from(firstLocal)
                val slotMonth = java.time.YearMonth.from(local)
                val months = ChronoUnit.MONTHS.between(firstMonth, slotMonth)
                if (months < 0 || months % interval != 0L) return null
                val expectedDay = minOf(firstLocal.dayOfMonth, slotMonth.lengthOfMonth())
                if (local.dayOfMonth != expectedDay) return null
                months / interval
            }
        }
        if (index < 0 || index > Int.MAX_VALUE) return null
        if (rule.count != null && index >= rule.count.toLong()) return null

        // Keep this check tied to slotAt() so the validator and recurrence projection cannot drift
        // apart if a frequency's sequence rules change later.
        val generated = runCatching { slotAt(firstLocal, rule, index.toInt()) }.getOrNull() ?: return null
        if (keyFor(generated) != key) return null
        val generatedMillis = slotOrderMillis(generated, record.zone) ?: return null
        if (rule.untilMillis != null && generatedMillis > rule.untilMillis) return null
        return index.toInt()
    }

    /** Materializes one original slot; a daily Alarm's effective instant follows [localZone]. */
    fun occurrenceForKey(
        record: TemporalRecord,
        key: String,
        localZone: ZoneId = ZoneId.systemDefault()
    ): Occurrence? {
        val anchor = record.anchorMillis() ?: return null
        val rule = record.recurrence
        if (key in rule.exceptions) return null
        val duration = ((record.spanEndMillis() ?: anchor) - anchor).coerceAtLeast(0L)
        val moved = rule.overrides[key]
        if (!rule.isRepeating) {
            val naturalKey = keyFor(Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime())
            if (key != naturalKey) return null
            return if (moved != null) {
                overrideOccurrence(record, key, moved, duration)
            } else {
                val end = if (record.kind == TemporalKind.ACTIVITY) anchor + duration else null
                Occurrence(key, anchor, end)
            }
        }

        val index = seriesSlotIndex(record, key) ?: return null
        if (moved != null) return overrideOccurrence(record, key, moved, duration)
        val firstLocal = Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime()
        val slotLocal = runCatching { slotAt(firstLocal, rule, index) }.getOrNull() ?: return null
        val start = slotMillis(slotLocal, projectionZone(record, localZone)) ?: return null
        val end = if (record.kind == TemporalKind.ACTIVITY) start + duration else null
        return Occurrence(key, start, end)
    }

    /** Generated zero-based slot index for a stable original occurrence key. */
    fun slotIndexForKey(record: TemporalRecord, key: String): Int? =
        seriesSlotIndex(record, key)

    /** Whether any non-excepted occurrence before [key] remains inside the active date range. */
    fun hasEarlierActiveOccurrence(record: TemporalRecord, key: String): Boolean {
        val selectedIndex = seriesSlotIndex(record, key) ?: return false
        if (selectedIndex == 0) return false
        val firstLocal = record.anchorMillis()?.let {
            Instant.ofEpochMilli(it).atZone(record.zone).toLocalDateTime()
        } ?: return false
        val rule = record.recurrence
        var index = selectedIndex - 1
        while (index >= 0) {
            val candidate = runCatching { slotAt(firstLocal, rule, index) }.getOrNull() ?: return false
            val candidateKey = keyFor(candidate)
            if (rule.activeFromDate != null && candidateKey.substringBefore('T') < rule.activeFromDate) {
                break
            }
            if (activeOnOriginalKey(rule, candidateKey) && candidateKey !in rule.exceptions) {
                return true
            }
            index--
        }
        return false
    }

    /** Largest index whose slot is at or before [fromMillis], found by binary search. */
    private fun startIndexNear(
        firstLocal: LocalDateTime,
        rule: Recurrence,
        fromMillis: Long,
        zone: ZoneId,
        countLimit: Int
    ): Int {
        if (fromMillis <= firstLocal.atZone(zone).toInstant().toEpochMilli()) return 0
        if (countLimit <= 0) return 0
        var low = 0
        var high = countLimit - 1
        while (low < high) {
            val mid = low + ((high - low + 1) ushr 1)
            val slot = runCatching { slotOrderMillis(slotAt(firstLocal, rule, mid), zone) }.getOrNull()
            if (slot != null && slot <= fromMillis) low = mid else high = mid - 1
        }
        return (low - 1).coerceAtLeast(0)
    }

    /** Greatest valid recurrence index whose effective natural start is strictly before [before]. */
    private fun lastSlotIndexBefore(
        firstLocal: LocalDateTime,
        rule: Recurrence,
        before: Long,
        zone: ZoneId,
        countLimit: Int
    ): Int? {
        if (countLimit <= 0) return null
        var low = 0
        var high = countLimit - 1
        var match = -1
        while (low <= high) {
            val mid = low + ((high - low) ushr 1)
            val local = runCatching { slotAt(firstLocal, rule, mid) }.getOrNull()
            val start = local?.let { slotOrderMillis(it, zone) }
            if (start != null && start < before) {
                match = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return match.takeIf { it >= 0 }
    }

    private data class SlotResolution(val positionMillis: Long, val occurrenceMillis: Long?)

    private fun resolveSlot(local: LocalDateTime, zone: ZoneId): SlotResolution? =
        runCatching {
            val offsets = zone.rules.getValidOffsets(local)
            if (offsets.isEmpty()) {
                val transition = zone.rules.getTransition(local) ?: return@runCatching null
                val boundary = transition.instant.toEpochMilli()
                SlotResolution(positionMillis = boundary, occurrenceMillis = null)
            } else {
                // ZoneRules returns the earlier offset first in an overlap. Select it explicitly
                // so the fold policy does not depend on LocalDateTime.atZone defaults.
                val instant = local.toInstant(offsets.first()).toEpochMilli()
                SlotResolution(positionMillis = instant, occurrenceMillis = instant)
            }
        }.getOrNull()

    /** Comparable position used for ordered scans and bounds, including nonexistent gap slots. */
    private fun slotOrderMillis(local: LocalDateTime, zone: ZoneId): Long? =
        resolveSlot(local, zone)?.positionMillis

    /** Effective instant for a real slot; local times in a DST gap intentionally have none. */
    private fun slotMillis(local: LocalDateTime, zone: ZoneId): Long? =
        resolveSlot(local, zone)?.occurrenceMillis

    /**
     * The nth slot in wall-clock time. `n = 0` is the first occurrence. Index-based stepping
     * avoids the "Jan 31 + 1 month = Feb 28, then Mar 28" drift of repeated addition.
     */
    fun slotAt(firstLocal: LocalDateTime, rule: Recurrence, n: Int): LocalDateTime {
        val interval = rule.interval.coerceAtLeast(1).toLong()
        if (n <= 0) return firstLocal
        return when (rule.freq) {
            RecurrenceFreq.NONE -> firstLocal
            RecurrenceFreq.DAILY -> firstLocal.plusDays(interval * n)
            RecurrenceFreq.WEEKLY -> {
                val days = weeklyDays(rule, firstLocal)
                val startDay = firstLocal.dayOfWeek.value
                // The first week contributes only the selected days AFTER the anchor day, so the
                // anchor itself is not emitted twice.
                val firstWeekExtra = days.filter { it > startDay }
                val monday = firstLocal.toLocalDate()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val time = firstLocal.toLocalTime()
                if (n <= firstWeekExtra.size) {
                    monday.plusDays((firstWeekExtra[n - 1] - 1).toLong()).atTime(time)
                } else {
                    val remainder = n - 1 - firstWeekExtra.size
                    val week = 1 + remainder / days.size
                    val day = days[remainder % days.size]
                    monday.plusWeeks(week.toLong() * interval)
                        .plusDays((day - 1).toLong())
                        .atTime(time)
                }
            }
            RecurrenceFreq.MONTHLY -> {
                val startMonth = java.time.YearMonth.from(firstLocal)
                val target = startMonth.plusMonths(interval * n)
                val day = minOf(firstLocal.dayOfMonth, target.lengthOfMonth())
                target.atDay(day).atTime(firstLocal.toLocalTime())
            }
        }
    }

    /**
     * Weekly weekday filter, coerced to include the first occurrence's weekday so the series
     * never starts with a slot earlier than the anchor the user actually created.
     */
    private fun weeklyDays(rule: Recurrence, firstLocal: LocalDateTime): List<Int> {
        val requested = rule.byDay.ifEmpty { setOf(firstLocal.dayOfWeek) }.map { it.value }
        val withFirst = (requested + firstLocal.dayOfWeek.value).distinct().sorted()
        return withFirst
    }

    /**
     * Next occurrence start at or after [afterMillis], or null when the series ended.
     *
     * A daily ALARM uses [localZone] for its effective wall-clock instant while its key remains
     * anchored to the record's original zone.
     *
     * An override can move an instance into the future from arbitrarily far back, so the answer is
     * the earliest of (a) every valid override whose effective start is at/after the instant and
     * (b) the first valid natural slot at/after it. The recurrence interval itself may exceed a
     * fixed lookahead window.
     */
    fun nextOccurrence(
        record: TemporalRecord,
        afterMillis: Long,
        localZone: ZoneId = ZoneId.systemDefault()
    ): Occurrence? {
        val anchor = record.anchorMillis() ?: return null
        val rule = record.recurrence
        val duration = ((record.spanEndMillis() ?: anchor) - anchor).coerceAtLeast(0L)

        if (!rule.isRepeating) {
            val key = keyFor(Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime())
            if (key in rule.exceptions) return null
            val moved = rule.overrides[key]
            val start = moved?.startMillis ?: anchor
            if (start < afterMillis) return null
            return if (moved != null) {
                overrideOccurrence(record, key, moved, duration)
            } else {
                val end = if (record.kind == TemporalKind.ACTIVITY) start + duration else null
                Occurrence(key, start, end)
            }
        }

        val zone = projectionZone(record, localZone)

        var best: Occurrence? = null
        rule.overrides.forEach { (key, moved) ->
            if (key in rule.exceptions || moved.startMillis < afterMillis ||
                !activeOnOriginalKey(rule, key) || seriesSlotIndex(record, key) == null) {
                return@forEach
            }
            val occurrence = overrideOccurrence(record, key, moved, duration)
            val current = best
            if (current == null || occurrence.startMillis < current.startMillis) {
                best = occurrence
            }
        }

        // This API asks for the next START, not an activity overlapping [afterMillis]. Starting
        // the search at afterMillis also prevents a long activity from consuming the occurrence
        // result limit with hundreds of historical starts before the actual next one.
        val firstLocal = Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime()
        val countLimit = rule.count ?: Int.MAX_VALUE
        val rangeStart = rule.activeFromDate?.let {
            LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli()
        } ?: afterMillis
        var index = startIndexNear(firstLocal, rule, maxOf(afterMillis, rangeStart), zone, countLimit)
        while (index < countLimit) {
            val slotLocal = runCatching { slotAt(firstLocal, rule, index) }.getOrNull() ?: break
            index++
            val slotPosition = slotOrderMillis(slotLocal, zone) ?: break
            if (rule.untilMillis != null && slotPosition > rule.untilMillis) break
            if (slotPosition < afterMillis) continue
            val slotKey = keyFor(slotLocal)
            if (rule.activeUntilDate != null &&
                slotLocal.toLocalDate().toString() > rule.activeUntilDate) break
            if (slotKey in rule.exceptions || slotKey in rule.overrides ||
                !activeOnOriginalKey(rule, slotKey)) continue
            val slotStart = slotMillis(slotLocal, zone) ?: continue
            val end = if (record.kind == TemporalKind.ACTIVITY) slotStart + duration else null
            val current = best
            if (current == null || slotStart < current.startMillis) {
                best = Occurrence(slotKey, slotStart, end)
            }
            // Natural slots are ordered by start, so no later natural slot can improve this one.
            break
        }
        return best
    }
}

/** One editable proposal. Identity is [draftId]; [eventSeq] may be shared by many cards. */
data class DraftCard(
    val draftId: Long,
    val eventSeq: Long,
    val queueOrder: Long,
    val operation: DraftOperation,
    val form: DraftForm,
    val status: DraftStatus,
    val baseRevision: Long?,
    val draftVersion: Long,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val isActionable: Boolean
        get() = status == DraftStatus.PENDING ||
            status == DraftStatus.CONFLICT ||
            status == DraftStatus.INVALID
}

/**
 * Editable projection of a proposal. Not a domain record: it may be incomplete, and it carries
 * provenance so a whole-object replace cannot silently rewrite where the event came from.
 */
data class DraftForm(
    val kind: TemporalKind,
    val title: String = "",
    val startText: String = "",
    val endText: String = "",
    val notes: String = "",
    val location: String = "",
    val reminderMinutes: Int = -1,
    val reminderMethod: ReminderMethod = ReminderMethod.MESSAGE,
    val reminderTiming: ReminderTiming = ReminderTiming.OFFSET,
    val enabled: Boolean = true,
    val source: String = "local",
    val zoneId: String = ZoneId.systemDefault().id,
    val recurrence: Recurrence = Recurrence.NONE,
    val trackingEnabled: Boolean = kind == TemporalKind.MEMO,
    val completedKeys: Set<String> = emptySet(),
    /** Recovery metadata for a human recurrence edit; ignored by [build]. */
    val recurrenceEditScope: RecurrenceEditScope? = null,
    /** Immutable original slot key selected in the occurrence projection. */
    val editOccurrenceKey: String? = null
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

    /**
     * Compares the business meaning that [build] would persist. Input formatting, whitespace,
     * unused fields for a kind, and transient UI state do not make a new-event session dirty.
     */
    fun hasMeaningfulDiffFrom(other: DraftForm): Boolean = meaningfulKey() != other.meaningfulKey()

    private fun meaningfulKey(): List<Any?> {
        fun instant(text: String): Any? = parseInstantIn(text, zone)?.let { listOf("instant", it) }
            ?: text.trim().takeIf(String::isNotEmpty)?.let { listOf("invalid", it) }
        fun date(text: String): Any? = parseDateOnly(text)?.let { listOf("date", it.toString()) }
            ?: text.trim().takeIf(String::isNotEmpty)?.let { listOf("invalid", it) }
        val start = when (kind) {
            TemporalKind.ALARM, TemporalKind.ACTIVITY -> instant(startText)
            TemporalKind.DEADLINE -> null
            TemporalKind.MEMO -> date(startText)
        }
        val end = when (kind) {
            TemporalKind.ALARM -> null
            TemporalKind.ACTIVITY -> instant(endText)
            TemporalKind.DEADLINE -> parseDeadline()?.let { due ->
                when (due) {
                    is DeadlineValue.Date -> listOf("date", due.date.toString())
                    is DeadlineValue.Instant -> listOf("instant", due.millis)
                }
            } ?: endText.trim().takeIf(String::isNotEmpty)?.let { listOf("invalid", it) }
            TemporalKind.MEMO -> date(endText)
        }
        return listOf(
            kind, title.trim(), start, end, notes.trim(), location.trim(),
            reminderMinutes, reminderMethod, reminderTiming, enabled,
            source.ifBlank { "local" }, zone.id, recurrence,
            if (kind == TemporalKind.MEMO) true else trackingEnabled,
            completedKeys.toSortedSet()
        )
    }

    /** Which required field is still unusable, in the order the UI should point at them. */
    fun missingFields(): List<DraftField> {
        val missing = ArrayList<DraftField>(3)
        if (title.isBlank()) missing.add(DraftField.TITLE)
        when (kind) {
            TemporalKind.ALARM -> if (parseStart() == null) missing.add(DraftField.START)
            TemporalKind.ACTIVITY -> {
                if (parseStart() == null) missing.add(DraftField.START)
                if (parseEnd() == null) missing.add(DraftField.END)
            }
            TemporalKind.DEADLINE -> if (parseDeadline() == null) missing.add(DraftField.DEADLINE)
            TemporalKind.MEMO -> { /* Undated Memo is valid. */ }
        }
        return missing
    }

    fun parseStart(): Long? = parseInstantIn(startText, zone)
    fun parseEnd(): Long? = parseInstantIn(endText, zone)

    /** Date-only is legal for DEADLINE and is never converted to an instant by this parser. */
    fun parseDeadline(): DeadlineValue? {
        val text = endText.trim()
        if (text.isEmpty()) return null
        parseDateOnly(text)?.let { return DeadlineValue.Date(it) }
        return parseInstantIn(text, zone)?.let { DeadlineValue.Instant(it) }
    }

    fun isPublishable(): Boolean {
        val missing = missingFields()
        if (missing.isNotEmpty()) return false
        if (kind == TemporalKind.ACTIVITY) {
            val start = parseStart() ?: return false
            val end = parseEnd() ?: return false
            if (end <= start) return false
        }
        return true
    }

    /** Builds the complete intended snapshot. Whole-object replacement, never a field patch. */
    fun build(seq: Long, revision: Long, nowMillis: Long = System.currentTimeMillis()): FormBuild {
        val missing = missingFields()
        if (missing.isNotEmpty()) return FormBuild.Incomplete(missing, missingHint(missing))
        val base = when (kind) {
            TemporalKind.ALARM -> TemporalRecord(
                eventSeq = seq, revision = revision, kind = kind, title = title.trim(),
                startMillis = parseStart()
            )
            TemporalKind.ACTIVITY -> {
                val start = parseStart()
                val end = parseEnd()
                if (start == null || end == null || end <= start) {
                    return FormBuild.Incomplete(listOf(DraftField.END), "结束时间必须晚于开始时间")
                }
                TemporalRecord(
                    eventSeq = seq, revision = revision, kind = kind, title = title.trim(),
                    startMillis = start, endMillis = end
                )
            }
            TemporalKind.DEADLINE -> when (val deadline = parseDeadline()) {
                null -> return FormBuild.Incomplete(listOf(DraftField.DEADLINE), "请输入截止日期或时刻")
                is DeadlineValue.Date -> TemporalRecord(
                    eventSeq = seq, revision = revision, kind = kind, title = title.trim(),
                    deadlineDate = deadline.date.toString()
                )
                is DeadlineValue.Instant -> TemporalRecord(
                    eventSeq = seq, revision = revision, kind = kind, title = title.trim(),
                    deadlineMillis = deadline.millis
                )
            }
            TemporalKind.MEMO -> TemporalRecord(
                eventSeq = seq, revision = revision, kind = kind, title = title.trim(),
                memoStartDate = startText.trim().ifEmpty { null },
                memoEndDate = endText.trim().ifEmpty { null },
                trackingEnabled = true
            )
        }
        val record = base.copy(
            notes = notes.trim(),
            location = location.trim(),
            recurrence = recurrence,
            trackingEnabled = if (kind == TemporalKind.MEMO) true else trackingEnabled,
            completedKeys = completedKeys,
            reminderMinutes = reminderMinutes,
            reminderMethod = reminderMethod,
            reminderTiming = reminderTiming,
            enabled = enabled,
            source = source.ifBlank { "local" },
            zoneId = zone.id,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        record.validate()?.let { return FormBuild.Invalid(it) }
        return FormBuild.Ready(record)
    }

    private fun missingHint(missing: List<DraftField>): String =
        "还需要：" + missing.joinToString("、") { it.label }

    companion object {
        private val DATE_TIME_PATTERNS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        )
        private val DATE_PATTERNS = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        )

        fun parseInstant(text: String): Long? = parseInstantIn(text, ZoneId.systemDefault())

        fun parseInstantIn(text: String, zone: ZoneId): Long? {
            val value = text.trim()
            if (value.isEmpty()) return null
            for (pattern in DATE_TIME_PATTERNS) {
                try {
                    return LocalDateTime.parse(value, pattern)
                        .atZone(zone).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) { /* try next */ }
            }
            return null
        }

        fun parseLocalDateTime(text: String): LocalDateTime? {
            val value = text.trim()
            for (pattern in DATE_TIME_PATTERNS) {
                try { return LocalDateTime.parse(value, pattern) } catch (_: DateTimeParseException) { }
            }
            return null
        }

        fun parseDateOnly(text: String): LocalDate? {
            val value = text.trim()
            for (pattern in DATE_PATTERNS) {
                try { return LocalDate.parse(value, pattern) } catch (_: DateTimeParseException) { }
            }
            return null
        }

        fun formatInstant(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
            millis?.let { Instant.ofEpochMilli(it).atZone(zone).format(DATE_TIME_PATTERNS[0]) } ?: ""

        fun from(record: TemporalRecord): DraftForm = DraftForm(
            kind = record.kind,
            title = record.title,
            startText = if (record.kind == TemporalKind.MEMO) record.memoStartDate.orEmpty()
                else formatInstant(record.startMillis, record.zone),
            endText = when (record.kind) {
                TemporalKind.DEADLINE -> record.deadlineDate
                    ?: formatInstant(record.deadlineMillis, record.zone)
                TemporalKind.MEMO -> record.memoEndDate.orEmpty()
                else -> formatInstant(record.endMillis, record.zone)
            },
            notes = record.notes,
            location = record.location,
            reminderMinutes = record.reminderMinutes,
            reminderMethod = record.reminderMethod,
            reminderTiming = record.reminderTiming,
            enabled = record.enabled,
            source = record.source,
            zoneId = record.zoneId,
            recurrence = record.recurrence,
            trackingEnabled = record.trackingEnabled,
            completedKeys = record.completedKeys
        )

        /** Fixed-time compatibility fixture; application entry points use NewEventEditSession. */
        fun fresh(
            kind: TemporalKind,
            day: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            at: LocalTime = LocalTime.of(9, 0)
        ): DraftForm {
            val fixed = Clock.fixed(day.atTime(at).atZone(zone).toInstant(), zone)
            val defaults = EditorDefaults.capture(fixed, zone)
            val selection = if (kind == TemporalKind.MEMO) null else EditorSelection.OnDate(day)
            val form = defaults.form(kind, selection)
            return if (kind == TemporalKind.DEADLINE) form.copy(reminderMinutes = 0) else form
        }

        /** Used by the timeline when the user long-presses "+ here". */
        fun atInstant(kind: TemporalKind, millis: Long, zone: ZoneId = ZoneId.systemDefault()): DraftForm {
            val clock = Clock.fixed(Instant.ofEpochMilli(millis), zone)
            return EditorDefaults.capture(clock, zone)
                .form(kind, EditorSelection.AtInstant(millis))
        }
    }
}

/** Explicit context selected before a new-event editor opens. */
sealed interface EditorSelection {
    data class AtInstant(val millis: Long) : EditorSelection
    data class OnDate(val date: LocalDate) : EditorSelection
}

/**
 * One captured clock/zone pair shared by every editor entry point. Current-time defaults use
 * ceiling-to-minute so a newly suggested ALARM does not start in the past, capped at the
 * captured local date so rounding never silently moves it to tomorrow. Explicit timeline
 * instants use floor-to-minute, matching the existing minute-resolution text fields.
 */
class EditorDefaultsSnapshot internal constructor(
    val now: Instant,
    val zone: ZoneId
) {
    val nowMillis: Long get() = now.toEpochMilli()
    val today: LocalDate get() = now.atZone(zone).toLocalDate()
    private val roundedNowMillis = ceilToMinuteWithinDate(now, zone)

    fun form(kind: TemporalKind, selection: EditorSelection? = null): DraftForm {
        val localNow = now.atZone(zone)
        val selectedInstant = (selection as? EditorSelection.AtInstant)?.millis?.let(::floorToMinute)
        val selectedDate = when (selection) {
            is EditorSelection.AtInstant -> Instant.ofEpochMilli(selection.millis).atZone(zone).toLocalDate()
            is EditorSelection.OnDate -> selection.date
            null -> null
        }
        val date = selectedDate ?: today
        val dateTimeDefault = selectedDate?.let {
            it.atTime(defaultLocalTime(it, localNow.toLocalTime())).atZone(zone)
                .toInstant().toEpochMilli()
        } ?: roundedNowMillis
        val point = selectedInstant ?: dateTimeDefault
        return when (kind) {
            TemporalKind.ALARM -> DraftForm(
                kind, startText = DraftForm.formatInstant(point, zone),
                reminderMinutes = 0, zoneId = zone.id
            )
            TemporalKind.ACTIVITY -> DraftForm(
                kind,
                startText = DraftForm.formatInstant(point, zone),
                endText = DraftForm.formatInstant(point + 3_600_000L, zone),
                reminderMinutes = 0, zoneId = zone.id
            )
            TemporalKind.DEADLINE -> {
                val exact = selectedInstant
                DraftForm(
                    kind,
                    endText = exact?.let { DraftForm.formatInstant(it, zone) } ?: date.toString(),
                    reminderMinutes = if (exact == null) 0 else -1,
                    reminderTiming = if (exact == null) ReminderTiming.DAY_START
                        else ReminderTiming.OFFSET,
                    zoneId = zone.id
                )
            }
            TemporalKind.MEMO -> DraftForm(
                kind,
                // A picker seed is not a business date: an ordinary new Memo stays undated.
                startText = selectedDate?.toString().orEmpty(),
                trackingEnabled = true,
                zoneId = zone.id
            )
        }
    }

    /** Date/time picker fallback for an existing incomplete draft in this editor instance. */
    fun pickerInstant(date: LocalDate = today): Long {
        val currentLocalTime = now.atZone(zone).toLocalTime()
        return date.atTime(defaultLocalTime(date, currentLocalTime)).atZone(zone).toInstant().toEpochMilli()
    }

    private fun defaultLocalTime(date: LocalDate, currentLocalTime: LocalTime): LocalTime =
        if (date == today) Instant.ofEpochMilli(roundedNowMillis).atZone(zone).toLocalTime()
        else currentLocalTime.truncatedTo(ChronoUnit.MINUTES)

    companion object {
        fun capture(clock: Clock, zone: ZoneId): EditorDefaultsSnapshot =
            EditorDefaultsSnapshot(clock.instant(), zone)

        private fun ceilToMinuteWithinDate(instant: Instant, zone: ZoneId): Long {
            val floor = instant.truncatedTo(ChronoUnit.MINUTES)
            val rounded = if (floor == instant) floor else floor.plus(1, ChronoUnit.MINUTES)
            val capturedDate = instant.atZone(zone).toLocalDate()
            return (if (rounded.atZone(zone).toLocalDate() == capturedDate) rounded else floor)
                .toEpochMilli()
        }

        private fun floorToMinute(millis: Long): Long =
            Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
    }
}

/** Factory kept separate so every create surface captures its defaults exactly once. */
object EditorDefaults {
    fun capture(clock: Clock = Clock.systemUTC(), zone: ZoneId = ZoneId.systemDefault()): EditorDefaultsSnapshot =
        EditorDefaultsSnapshot.capture(clock, zone)
}

sealed interface DeadlineValue {
    data class Instant(val millis: Long) : DeadlineValue
    data class Date(val date: LocalDate) : DeadlineValue
}

sealed interface FormBuild {
    data class Ready(val record: TemporalRecord) : FormBuild
    data class Incomplete(val missing: List<DraftField>, val message: String) : FormBuild
    data class Invalid(val reason: String) : FormBuild
}

/**
 * Type conversion keeps the event number and never invents a critical time.
 * Fields that the target shape needs are reported as required for review.
 */
object TemporalConversion {
    data class Result(
        val form: DraftForm,
        val required: List<DraftField>,
        val note: String
    )

    fun convert(form: DraftForm, target: TemporalKind): Result {
        if (form.kind == target) return Result(form, emptyList(), "")
        val start = form.startText.trim()
        val end = form.endText.trim()
        val next = when {
            // A point can serve as another point: start -> deadline, deadline -> start.
            form.kind == TemporalKind.ALARM && target == TemporalKind.DEADLINE ->
                form.copy(kind = target, startText = "", endText = start)
            form.kind == TemporalKind.DEADLINE && target == TemporalKind.ALARM ->
                if (DraftForm.parseDateOnly(end) != null) {
                    // A date-only deadline is not an instant; never promote it to a start time.
                    form.copy(kind = target, startText = "", endText = "")
                } else {
                    form.copy(kind = target, startText = end, endText = "")
                }
            // An interval collapses to the anchor that still means something.
            form.kind == TemporalKind.ACTIVITY && target == TemporalKind.DEADLINE ->
                form.copy(kind = target, startText = "", endText = end)
            form.kind == TemporalKind.DEADLINE && target == TemporalKind.ACTIVITY ->
                form.copy(kind = target, startText = "", endText = end)
            // Expanding a point into an interval requires the missing endpoint.
            form.kind == TemporalKind.ALARM && target == TemporalKind.ACTIVITY ->
                form.copy(kind = target, startText = start, endText = "")
            // Dropping the interval end: start stays as the semantic anchor.
            form.kind == TemporalKind.ACTIVITY && target == TemporalKind.ALARM ->
                form.copy(kind = target, startText = start, endText = "")
            else -> form.copy(kind = target)
        }
        val required = next.missingFields()
        val normalized = if (target == TemporalKind.DEADLINE &&
            DraftForm.parseDateOnly(next.endText) != null
        ) {
            // A date-precision deadline has no minute to trigger on; carrying a reminder over
            // would fabricate midnight.
            next.copy(reminderMinutes = -1)
        } else {
            next
        }
        val note = when {
            target == TemporalKind.ACTIVITY && DraftField.END in required ->
                "转换为活动需要补全结束时间；原有时刻已保留为开始。"
            target == TemporalKind.DEADLINE && DraftField.DEADLINE in required ->
                "转换为截止需要补全截止日期或时刻。"
            target == TemporalKind.ALARM && DraftField.START in required ->
                "转换为闹钟需要补全开始时刻；日期级截止不能当作精确时刻。"
            target == TemporalKind.ACTIVITY && form.kind == TemporalKind.DEADLINE ->
                "日期级截止已保留为结束日期，开始时刻需要你确认。"
            form.kind == TemporalKind.ACTIVITY && target == TemporalKind.ALARM ->
                "已保留开始时刻；结束时刻不再属于闹钟。"
            target == TemporalKind.DEADLINE && DraftForm.parseDateOnly(next.endText) != null ->
                "日期级截止没有具体分钟，提醒已随之关闭。"
            else -> "事件序号保持不变。"
        }
        return Result(normalized, required, note)
    }
}

/**
 * Pure commit predicate. `Last valid committed full snapshot wins`; a card whose base revision is
 * not the current revision must be re-approved by a human and may never auto-merge.
 */
object CommitRules {
    fun isStale(baseRevision: Long?, currentRevision: Long?): Boolean = baseRevision != currentRevision

    fun nextRevision(currentRevision: Long?): Long = (currentRevision ?: 0L) + 1L
}

/** Field-by-field readable diff used for stale-card review. Never applied automatically. */
data class FieldChange(val label: String, val before: String, val after: String)

object DraftDiff {
    fun between(current: TemporalRecord?, form: DraftForm, seq: Long): List<FieldChange> {
        val built = form.build(seq, CommitRules.nextRevision(current?.revision))
        val after = (built as? FormBuild.Ready)?.record
        val changes = mutableListOf<FieldChange>()
        fun add(label: String, before: String?, afterText: String?) {
            if ((before ?: "").trim() != (afterText ?: "").trim()) {
                changes.add(FieldChange(label, before.orEmpty().ifBlank { "（空）" },
                    afterText.orEmpty().ifBlank { "（空）" }))
            }
        }
        add("类型", current?.kind?.label, form.kind.label)
        add("标题", current?.title, after?.title ?: form.title)
        val beforeTimes = current?.let(::temporalFields).orEmpty()
        val afterTimes = after?.let(::temporalFields) ?: temporalFields(form)
        listOf("开始", "结束", "截止", "开始日期", "结束日期").forEach { label ->
            add(label, beforeTimes[label], afterTimes[label])
        }
        add("完成跟踪", current?.trackingEnabled?.toString(), form.trackingEnabled.toString())
        add("地点", current?.location, form.location)
        add("备注", current?.notes, form.notes)
        add("提醒", reminderLabel(current?.reminderMinutes), reminderLabel(form.reminderMinutes))
        add("提醒方式", current?.reminderMethod?.name, after?.reminderMethod?.name ?: form.reminderMethod.name)
        add("提醒时机", current?.reminderTiming?.name, after?.reminderTiming?.name ?: form.reminderTiming.name)
        add("启用", if (current?.enabled == false) "否" else if (current == null) null else "是",
            if (form.enabled) "是" else "否")
        add("来源", current?.source, after?.source ?: form.source.ifBlank { "local" })
        add("时区", current?.zoneId, after?.zoneId ?: form.zone.id)
        add(
            "重复规则",
            current?.recurrence?.let(::recurrenceSummary),
            after?.recurrence?.let(::recurrenceSummary) ?: recurrenceSummary(form.recurrence)
        )
        add(
            "完成槽位",
            current?.completedKeys?.let(::completedKeysSummary),
            after?.completedKeys?.let(::completedKeysSummary) ?: completedKeysSummary(form.completedKeys)
        )
        if (changes.isEmpty()) changes.add(FieldChange("内容", "与当前正式对象一致", "没有可显示的差异"))
        return changes
    }

    private fun temporalFields(record: TemporalRecord): Map<String, String?> = when (record.kind) {
        TemporalKind.ALARM -> mapOf(
            "开始" to record.startMillis?.let { DraftForm.formatInstant(it, record.zone) }
        )
        TemporalKind.ACTIVITY -> mapOf(
            "开始" to record.startMillis?.let { DraftForm.formatInstant(it, record.zone) },
            "结束" to record.endMillis?.let { DraftForm.formatInstant(it, record.zone) }
        )
        TemporalKind.DEADLINE -> mapOf(
            "截止" to (record.deadlineDate?.let { "日期 $it" }
                ?: record.deadlineMillis?.let { "时刻 ${DraftForm.formatInstant(it, record.zone)}" })
        )
        TemporalKind.MEMO -> mapOf(
            "开始日期" to record.memoStartDate,
            "结束日期" to record.memoEndDate
        )
    }

    private fun temporalFields(form: DraftForm): Map<String, String?> = when (form.kind) {
        TemporalKind.ALARM -> mapOf("开始" to form.startText.trim().ifEmpty { null })
        TemporalKind.ACTIVITY -> mapOf(
            "开始" to form.startText.trim().ifEmpty { null },
            "结束" to form.endText.trim().ifEmpty { null }
        )
        TemporalKind.DEADLINE -> mapOf(
            "截止" to when (val deadline = form.parseDeadline()) {
                is DeadlineValue.Date -> "日期 ${deadline.date}"
                is DeadlineValue.Instant -> "时刻 ${DraftForm.formatInstant(deadline.millis, form.zone)}"
                null -> form.endText.trim().ifEmpty { null }
            }
        )
        TemporalKind.MEMO -> mapOf(
            "开始日期" to form.startText.trim().ifEmpty { null },
            "结束日期" to form.endText.trim().ifEmpty { null }
        )
    }

    private fun recurrenceSummary(rule: Recurrence): String {
        val days = rule.byDay.sortedBy { it.value }.joinToString(",") { it.name }.ifEmpty { "none" }
        val exceptions = rule.exceptions.sorted().joinToString(",", "[", "]", transform = ::escapeDiffValue)
        val overrides = rule.overrides.toSortedMap().entries.joinToString(";", "[", "]") { (key, value) ->
            val start = Instant.ofEpochMilli(value.startMillis)
            val end = value.endMillis?.let { Instant.ofEpochMilli(it).toString() } ?: "none"
            val title = value.title?.let(::escapeDiffValue) ?: "none"
            "${escapeDiffValue(key)}=>$start/$end/$title"
        }
        return listOf(
            "freq=${rule.freq.name}",
            "interval=${rule.interval}",
            "count=${rule.count ?: "unbounded"}",
            "until=${rule.untilMillis?.let { Instant.ofEpochMilli(it).toString() } ?: "none"}",
            "activeFromDate=${rule.activeFromDate ?: "none"}",
            "activeUntilDate=${rule.activeUntilDate ?: "none"}",
            "byDay=$days",
            "exceptions=$exceptions",
            "overrides=$overrides"
        ).joinToString("; ")
    }

    private fun completedKeysSummary(keys: Set<String>): String =
        keys.sorted().joinToString(",", "[", "]", transform = ::escapeDiffValue)

    private fun escapeDiffValue(value: String): String = value
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("=", "\\=")
        .replace(">", "\\>")

    fun reminderLabel(minutes: Int?): String = when {
        minutes == null -> "（空）"
        minutes < 0 -> "关闭"
        minutes == 0 -> "准时"
        minutes % 1440 == 0 -> "${minutes / 1440} 天前"
        minutes % 60 == 0 -> "${minutes / 60} 小时前"
        else -> "$minutes 分钟前"
    }
}

/** Visual lane of one event inside a side of the axis. */
data class TimelineItem(
    val record: TemporalRecord,
    val anchorMillis: Long,
    val endMillis: Long,
    val lane: Int,
    val side: AxisSide,
    val dateOnly: Boolean,
    val clusterLanes: Int = 1
)

/**
 * Stable lane allocation: earlier-starting overlapping items sit nearer the central axis, later
 * ones expand outward, and freed lanes are reused. Allocation is computed over the full active
 * set (not the viewport), so small scrolls cannot reshuffle lanes.
 */
object TimelineLayout {
    // All overlapping events remain individually accessible. UI scrolls each wing horizontally.
    const val DEFAULT_MAX_LANES = Int.MAX_VALUE

    fun layout(
        records: List<TemporalRecord>,
        maxLanesPerSide: Int = DEFAULT_MAX_LANES,
        @Suppress("UNUSED_PARAMETER") minVisibleMillis: Long = 12 * 60_000L
    ): LayoutResult {
        val items = ArrayList<TimelineItem>(records.size)
        val overflow = HashMap<AxisSide, MutableList<TemporalRecord>>()
        AxisSide.entries.forEach { side ->
            val laneEnds = ArrayList<Long>()
            val onSide = records.filter { it.kind.side == side }
                .mapNotNull { record ->
                    val anchor = record.visualAxisMillis() ?: return@mapNotNull null
                    val end = record.spanEndMillis() ?: anchor
                    // Visual label/touch size MUST NOT influence semantic overlap.
                    // A duration interval ends exactly at END; point events collide
                    // only when they share an identical timestamp.
                    record to (anchor to maxOf(end, anchor + 1L))
                }
                .sortedWith(compareBy({ it.second.first }, { it.first.eventSeq }))
            onSide.forEach { (record, span) ->
                val (start, end) = span
                var lane = laneEnds.indexOfFirst { it <= start }
                if (lane < 0) {
                    lane = laneEnds.size
                    laneEnds.add(end)
                } else {
                    laneEnds[lane] = end
                }
                if (lane >= maxLanesPerSide) {
                    overflow.getOrPut(side) { mutableListOf() }.add(record)
                } else {
                    items.add(
                        TimelineItem(
                            record = record,
                            anchorMillis = start,
                            endMillis = end,
                            lane = lane,
                            side = side,
                            dateOnly = record.isDateOnlyDeadline()
                        )
                    )
                }
            }
        }
        val clustered = items.groupBy { it.side }.flatMap { (_, sideItems) ->
            val sorted = sideItems.sortedWith(compareBy({ it.anchorMillis }, { it.record.eventSeq }))
            val result = ArrayList<TimelineItem>(sorted.size)
            var group = ArrayList<TimelineItem>()
            var groupEnd = Long.MIN_VALUE
            fun flush() {
                if (group.isEmpty()) return
                val count = group.maxOf { it.lane } + 1
                result.addAll(group.map { it.copy(clusterLanes = count) })
                group = ArrayList()
            }
            for (item in sorted) {
                if (item.anchorMillis >= groupEnd) { flush(); groupEnd = Long.MIN_VALUE }
                group.add(item)
                groupEnd = maxOf(groupEnd, item.endMillis)
            }
            flush()
            result
        }
        return LayoutResult(clustered, overflow.mapValues { it.value.toList() })
    }

    data class LayoutResult(
        val items: List<TimelineItem>,
        val overflow: Map<AxisSide, List<TemporalRecord>>
    )
}

/**
 * Geometry of the central vertical axis. All conversion math is Double so a 2026 epoch
 * millisecond value never loses a hundred seconds to Float precision.
 */
object TimelineCoordinate {
    // Allow close inspection of short events without changing their true interval geometry.
    const val MIN_HOURS = 0.25
    const val MAX_HOURS = 48.0
    const val HOUR_MILLIS = 3_600_000.0

    fun clampHours(hours: Double): Double = hours.coerceIn(MIN_HOURS, MAX_HOURS)

    fun spanMillis(hours: Double): Double = hours * HOUR_MILLIS

    /** Y in pixels for an instant, with [anchorMillis] at the vertical center. */
    fun yOf(millis: Long, anchorMillis: Long, heightPx: Float, hours: Double): Float {
        if (heightPx <= 0f) return 0f
        val offset = (millis - anchorMillis) / spanMillis(hours) * heightPx.toDouble()
        return (heightPx / 2.0 + offset).toFloat()
    }

    /** Instant at a pixel row; inverse of [yOf]. */
    fun timeAt(yPx: Float, anchorMillis: Long, heightPx: Float, hours: Double): Long {
        if (heightPx <= 0f) return anchorMillis
        val offset = (yPx - heightPx / 2.0) / heightPx * spanMillis(hours)
        return anchorMillis + offset.roundToLong()
    }

    /** Moves the camera anchor so the instant under [focusYPx] survives a zoom change. */
    fun anchorPreservingFocus(
        anchorMillis: Long,
        oldHours: Double,
        newHours: Double,
        focusYPx: Float,
        heightPx: Float
    ): Long {
        if (heightPx <= 0f) return anchorMillis
        val ratio = (focusYPx - heightPx / 2.0) / heightPx
        return anchorMillis + (ratio * (spanMillis(oldHours) - spanMillis(newHours))).roundToLong()
    }

    /** Snap grid grows as the viewport shows more time, so dragging stays precise. */
    fun snapMinutesFor(hours: Double): Int = when {
        hours <= 6 -> 1
        hours <= 10 -> 5
        hours <= 16 -> 10
        hours <= 26 -> 15
        hours <= 36 -> 30
        else -> 60
    }

    /** Snaps a wall-clock instant to the zoom-dependent grid, so 09:07 becomes 09:05 etc. */
    fun snap(millis: Long, hours: Double, zone: ZoneId, nowMillis: Long? = null): Long {
        val minutes = snapMinutesFor(hours)
        val local = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
        val startOfDay = local.toLocalDate().atStartOfDay()
        val seconds = ChronoUnit.SECONDS.between(startOfDay, local)
        val step = minutes * 60L
        val snappedSeconds = ((seconds + step / 2) / step) * step
        return startOfDay.plusSeconds(snappedSeconds).atZone(zone).toInstant().toEpochMilli()
    }

    /** Camera state persisted across the Draft Deck round trip. */
    data class Camera(val anchorMillis: Long, val hours: Double)

    fun initialCamera(nowMillis: Long, zone: ZoneId): Camera {
        val noon = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return Camera(noon, 24.0)
    }

    /** Today recentres at the current instant without replacing the user's current zoom. */
    fun todayCamera(nowMillis: Long, currentHours: Double): Camera =
        Camera(nowMillis, clampHours(currentHours))

    fun dateBands(fromMillis: Long, toMillis: Long, zone: ZoneId): List<Pair<Long, Long>> {
        if (toMillis <= fromMillis) return emptyList()
        val startDay = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
        val bands = ArrayList<Pair<Long, Long>>()
        var day = startDay
        var guard = 0
        while (!day.isAfter(endDay) && guard < 400) {
            val begin = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val next = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            bands.add(begin to next)
            day = day.plusDays(1)
            guard++
        }
        return bands
    }
}

object TemporalText {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE = DateTimeFormatter.ofPattern("M月d日")
    private val DATE_WEEKDAY = DateTimeFormatter.ofPattern("M月d日 EEE")

    fun summarize(record: TemporalRecord): String = when (record.kind) {
        TemporalKind.ALARM -> {
            val start = record.startMillis
            if (start == null) "时刻待补全" else Instant.ofEpochMilli(start).atZone(record.zone).format(TIME)
        }
        TemporalKind.ACTIVITY -> {
            val start = record.startMillis
            val end = record.endMillis
            if (start == null || end == null) "区间待补全" else {
                val from = Instant.ofEpochMilli(start).atZone(record.zone)
                val to = Instant.ofEpochMilli(end).atZone(record.zone)
                if (from.toLocalDate() == to.toLocalDate()) {
                    from.format(TIME) + " – " + to.format(TIME)
                } else {
                    from.format(DATE) + " " + from.format(TIME) + " – " + to.format(DATE) + " " + to.format(TIME)
                }
            }
        }
        TemporalKind.MEMO -> when {
            record.memoStartDate == null -> "无日期 · 待办"
            record.memoEndDate == null -> record.memoStartDate + " · 待办"
            else -> record.memoStartDate + " ～ " + record.memoEndDate + " · 待办"
        }
        TemporalKind.DEADLINE ->
            record.deadlineDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { "截止 " + it.format(DATE_WEEKDAY) + "（日期）" }
                ?: record.deadlineMillis?.let {
                    "截止 " + Instant.ofEpochMilli(it).atZone(record.zone).format(DATE) + " " +
                        Instant.ofEpochMilli(it).atZone(record.zone).format(TIME)
                }
                ?: "截止待补全"
    }

    fun operationLabel(record: DraftOperation): String = record.label

    fun sourceLabel(source: String): String = when (source) {
        "local" -> "本地"
        "ics" -> "ICS 导入"
        "android-calendar" -> "系统日历"
        "ai" -> "AI 草稿"
        "ocr" -> "截图识别"
        "text" -> "文字识别"
        "legacy-v01" -> "旧版迁移"
        else -> source
    }
}
