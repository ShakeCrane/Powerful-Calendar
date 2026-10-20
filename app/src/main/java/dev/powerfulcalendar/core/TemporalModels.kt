package dev.powerfulcalendar.core

import java.time.DayOfWeek
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
 *  - [TemporalKind.DEADLINE] has a deadline point or date -> right of the central axis
 *
 * `eventSeq` is the permanent identity. Nothing in this file may derive identity, order or
 * ringing behaviour from nullable time fields: kind, trigger, schedule and execution state are
 * intentionally separate concepts.
 */

enum class TemporalKind(val label: String) {
    ALARM("闹钟"),
    ACTIVITY("活动"),
    DEADLINE("截止");

    val side: AxisSide get() = if (this == ALARM) AxisSide.LEFT else AxisSide.RIGHT
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
    val recurrence: Recurrence = Recurrence.NONE,
    /** Reminder offset in minutes before the anchor; negative means no reminder. */
    val reminderMinutes: Int = -1,
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
    }

    /** Instant-based end used for lane overlap and drag ghosts; never used to fake precision. */
    fun spanEndMillis(inZone: ZoneId = zone): Long? = when (kind) {
        TemporalKind.ACTIVITY -> endMillis ?: startMillis
        TemporalKind.ALARM -> startMillis
        TemporalKind.DEADLINE -> anchorMillis(inZone)
    }

    fun isDateOnlyDeadline(): Boolean = precision == TimePrecision.DATE

    /** Validation of the record as a whole. Returns a user-facing reason, or null when legal. */
    fun validate(): String? {
        if (title.isBlank()) return "请输入事件标题"
        if (recurrence.interval < 1) return "重复间隔必须至少为 1"
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
    private val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun keyFor(local: LocalDateTime): String = local.format(KEY_FORMAT)

    /**
     * Materializes the instances whose **effective** time intersects `[fromMillis, toMillis)`.
     *
     * Slots are computed by index in **wall-clock** time using the record's own zone, so a daily
     * 07:30 alarm stays 07:30 locally across DST instead of drifting one hour. A DST gap resolves
     * forward (02:30 becomes 03:30) and a fold resolves to the earlier offset; both are documented
     * policy rather than accidental behaviour. `count` counts generated slots, including skipped
     * exceptions, which is why a skipped instance never shifts the rest of the series.
     *
     * Effective time is what matters for a window query:
     *  - an override may move an instance from months outside the window *into* it, and such an
     *    instance is still identified by its original slot key;
     *  - an instance moved out of the window must not be returned;
     *  - the caller never has to guess an "original slot" search range.
     */
    fun occurrences(
        record: TemporalRecord,
        fromMillis: Long,
        toMillis: Long,
        limit: Int = 512
    ): List<Occurrence> {
        if (toMillis <= fromMillis || limit <= 0) return emptyList()
        val anchor = record.anchorMillis() ?: return emptyList()
        val zone = record.zone
        val duration = ((record.spanEndMillis() ?: anchor) - anchor).coerceAtLeast(0L)
        val rule = record.recurrence

        if (!rule.isRepeating) {
            val key = keyFor(Instant.ofEpochMilli(anchor).atZone(zone).toLocalDateTime())
            if (key in rule.exceptions) return emptyList()
            val moved = rule.overrides[key]
            val start = moved?.startMillis ?: anchor
            val end = moved?.endMillis
                ?: if (record.kind == TemporalKind.ACTIVITY) anchor + duration else null
            return if (overlapsWindow(start, end, fromMillis, toMillis)) {
                listOf(Occurrence(key, start, end, moved = moved != null))
            } else {
                emptyList()
            }
        }

        val results = ArrayList<Occurrence>()
        // (A) Overrides are searched by EFFECTIVE time, so a far-away slot moved into this window
        // is found regardless of where its original slot sits in the series.
        rule.overrides.forEach { (key, override) ->
            if (key in rule.exceptions) return@forEach
            val start = override.startMillis
            val end = override.endMillis
                ?: if (record.kind == TemporalKind.ACTIVITY) start + duration else null
            if (overlapsWindow(start, end, fromMillis, toMillis)) {
                results.add(Occurrence(key, start, end, moved = true))
            }
        }

        // (B) Natural slots near the window. The scan starts one activity length early so an
        // activity that began before the window but still overlaps it is included.
        val firstLocal = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDateTime()
        val countLimit = rule.count ?: Int.MAX_VALUE
        val scanFrom = fromMillis - duration - 1
        var index = startIndexNear(firstLocal, rule, scanFrom, zone, countLimit)
        var emitted = 0
        var iterations = 0
        val iterationCap = limit * 4
        while (index < countLimit && emitted < limit && iterations < iterationCap) {
            iterations++
            val slotLocal = slotAt(firstLocal, rule, index)
            index++
            val slotStart = slotLocal.atZone(zone).toInstant().toEpochMilli()
            if (rule.untilMillis != null && slotStart > rule.untilMillis) break
            if (slotStart >= toMillis) break
            val slotKey = keyFor(slotLocal)
            // An overridden slot already contributed (or was correctly excluded) in step (A).
            if (slotKey in rule.overrides || slotKey in rule.exceptions) continue
            val end = if (record.kind == TemporalKind.ACTIVITY) slotStart + duration else null
            if (!overlapsWindow(slotStart, end, fromMillis, toMillis)) continue
            results.add(Occurrence(slotKey, slotStart, end))
            emitted++
        }
        return results.sortedWith(compareBy({ it.startMillis }, { it.key })).take(limit)
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

    /** Largest index whose slot is at or before [fromMillis], found by binary search. */
    private fun startIndexNear(
        firstLocal: LocalDateTime,
        rule: Recurrence,
        fromMillis: Long,
        zone: ZoneId,
        countLimit: Int
    ): Int {
        if (fromMillis <= firstLocal.atZone(zone).toInstant().toEpochMilli()) return 0
        var low = 0
        var high = minOf(countLimit - 1, 500_000).coerceAtLeast(0)
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            val slot = slotAt(firstLocal, rule, mid)
                .atZone(zone).toInstant().toEpochMilli()
            if (slot <= fromMillis) low = mid else high = mid - 1
        }
        return (low - 1).coerceAtLeast(0)
    }

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
     * An override can move an instance into the future from arbitrarily far back, so the answer is
     * the earliest of (a) every override whose effective start is at/after the instant and (b) the
     * natural slots inside a bounded horizon. Overrides are not limited by that horizon.
     */
    fun nextOccurrence(record: TemporalRecord, afterMillis: Long): Occurrence? {
        val anchor = record.anchorMillis() ?: return null
        val duration = ((record.spanEndMillis() ?: anchor) - anchor).coerceAtLeast(0L)
        val rule = record.recurrence

        if (!rule.isRepeating) {
            val key = keyFor(Instant.ofEpochMilli(anchor).atZone(record.zone).toLocalDateTime())
            if (key in rule.exceptions) return null
            val moved = rule.overrides[key]
            val start = moved?.startMillis ?: anchor
            if (start < afterMillis) return null
            val end = moved?.endMillis
                ?: if (record.kind == TemporalKind.ACTIVITY) anchor + duration else null
            return Occurrence(key, start, end, moved = moved != null)
        }

        val horizon = afterMillis + 400L * 24 * 3_600_000
        var best: Occurrence? = null
        rule.overrides.forEach { (key, moved) ->
            if (key in rule.exceptions || moved.startMillis < afterMillis) return@forEach
            val current = best
            if (current == null || moved.startMillis < current.startMillis) {
                val end = moved.endMillis
                    ?: if (record.kind == TemporalKind.ACTIVITY) moved.startMillis + duration else null
                best = Occurrence(key, moved.startMillis, end, moved = true)
            }
        }
        occurrences(record, afterMillis - duration - 1, horizon).forEach { occurrence ->
            if (occurrence.startMillis < afterMillis) return@forEach
            val current = best
            if (current == null || occurrence.startMillis < current.startMillis) {
                best = occurrence
            }
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
    val enabled: Boolean = true,
    val source: String = "local",
    val zoneId: String = ZoneId.systemDefault().id,
    val recurrence: Recurrence = Recurrence.NONE
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

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
        }
        val record = base.copy(
            notes = notes.trim(),
            location = location.trim(),
            recurrence = recurrence,
            reminderMinutes = reminderMinutes,
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
            startText = formatInstant(record.startMillis, record.zone),
            endText = when (record.kind) {
                TemporalKind.DEADLINE -> record.deadlineDate
                    ?: formatInstant(record.deadlineMillis, record.zone)
                else -> formatInstant(record.endMillis, record.zone)
            },
            notes = record.notes,
            location = record.location,
            reminderMinutes = record.reminderMinutes,
            enabled = record.enabled,
            source = record.source,
            zoneId = record.zoneId,
            recurrence = record.recurrence
        )

        fun fresh(
            kind: TemporalKind,
            day: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            at: LocalTime = LocalTime.of(9, 0)
        ): DraftForm = when (kind) {
            TemporalKind.ALARM -> DraftForm(
                kind, startText = formatInstant(day.atTime(at).atZone(zone).toInstant().toEpochMilli(), zone),
                zoneId = zone.id
            )
            TemporalKind.ACTIVITY -> DraftForm(
                kind,
                startText = formatInstant(day.atTime(at).atZone(zone).toInstant().toEpochMilli(), zone),
                endText = formatInstant(
                    day.atTime(at).plusHours(1).atZone(zone).toInstant().toEpochMilli(), zone
                ),
                zoneId = zone.id
            )
            TemporalKind.DEADLINE -> DraftForm(kind, endText = day.toString(), zoneId = zone.id)
        }

        /** Used by the timeline when the user long-presses "+ here". */
        fun atInstant(kind: TemporalKind, millis: Long, zone: ZoneId = ZoneId.systemDefault()): DraftForm {
            val local = Instant.ofEpochMilli(millis).atZone(zone)
            return when (kind) {
                TemporalKind.ALARM -> DraftForm(kind, startText = formatInstant(millis, zone), zoneId = zone.id)
                TemporalKind.ACTIVITY -> DraftForm(
                    kind, startText = formatInstant(millis, zone),
                    endText = formatInstant(millis + 3_600_000L, zone), zoneId = zone.id
                )
                TemporalKind.DEADLINE -> DraftForm(
                    kind, endText = local.toLocalDate().toString(), zoneId = zone.id
                )
            }
        }
    }
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
        val zone = form.zone
        when (form.kind) {
            TemporalKind.ALARM -> add("开始", current?.startMillis?.let { DraftForm.formatInstant(it, zone) },
                form.startText)
            TemporalKind.ACTIVITY -> {
                add("开始", current?.startMillis?.let { DraftForm.formatInstant(it, zone) }, form.startText)
                add("结束", current?.endMillis?.let { DraftForm.formatInstant(it, zone) }, form.endText)
            }
            TemporalKind.DEADLINE -> add(
                "截止",
                current?.deadlineDate ?: current?.deadlineMillis?.let { DraftForm.formatInstant(it, zone) },
                form.endText
            )
        }
        add("地点", current?.location, form.location)
        add("备注", current?.notes, form.notes)
        add("提醒", reminderLabel(current?.reminderMinutes), reminderLabel(form.reminderMinutes))
        add("启用", if (current?.enabled == false) "否" else if (current == null) null else "是",
            if (form.enabled) "是" else "否")
        if (changes.isEmpty()) changes.add(FieldChange("内容", "与当前正式对象一致", "没有可显示的差异"))
        return changes
    }

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
    val dateOnly: Boolean
)

/**
 * Stable lane allocation: earlier-starting overlapping items sit nearer the central axis, later
 * ones expand outward, and freed lanes are reused. Allocation is computed over the full active
 * set (not the viewport), so small scrolls cannot reshuffle lanes.
 */
object TimelineLayout {
    const val DEFAULT_MAX_LANES = 3

    fun layout(
        records: List<TemporalRecord>,
        maxLanesPerSide: Int = DEFAULT_MAX_LANES,
        minVisibleMillis: Long = 12 * 60_000L
    ): LayoutResult {
        val items = ArrayList<TimelineItem>(records.size)
        val overflow = HashMap<AxisSide, MutableList<TemporalRecord>>()
        AxisSide.entries.forEach { side ->
            val laneEnds = ArrayList<Long>()
            val onSide = records.filter { it.kind.side == side }
                .mapNotNull { record ->
                    val anchor = record.anchorMillis() ?: return@mapNotNull null
                    val end = record.spanEndMillis() ?: anchor
                    record to (anchor to maxOf(end, anchor + minVisibleMillis))
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
        return LayoutResult(items, overflow.mapValues { it.value.toList() })
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
    const val MIN_HOURS = 4.0
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
