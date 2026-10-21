package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.*
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/** Read-only occurrence projection. Keep the formal owner separate from its displayed slot. */
internal data class DisplayOccurrence(val owner: TemporalRecord, val record: TemporalRecord, val key: String)

private fun occurrenceDisplayRecord(owner: TemporalRecord, slot: Occurrence): TemporalRecord {
    val projected = when (owner.kind) {
        TemporalKind.ALARM -> owner.copy(startMillis = slot.startMillis)
        TemporalKind.ACTIVITY -> owner.copy(startMillis = slot.startMillis, endMillis = slot.endMillis)
        TemporalKind.MEMO -> owner.copy(memoStartDate =
            Instant.ofEpochMilli(slot.startMillis).atZone(owner.zone).toLocalDate().toString())
        TemporalKind.DEADLINE -> if (owner.isDateOnlyDeadline()) {
            owner.copy(deadlineDate = Instant.ofEpochMilli(slot.startMillis).atZone(owner.zone).toLocalDate().toString())
        } else {
            owner.copy(deadlineMillis = slot.startMillis)
        }
    }
    return projected.copy(title = owner.recurrence.overrides[slot.key]?.title ?: owner.title)
}

internal fun singleDisplayOccurrence(owner: TemporalRecord): DisplayOccurrence? {
    val anchor = owner.anchorMillis() ?: return null
    val originalKey = RecurrenceEngine.keyFor(Instant.ofEpochMilli(anchor).atZone(owner.zone).toLocalDateTime())
    val slot = RecurrenceEngine.occurrenceForKey(owner, originalKey) ?: return null
    return DisplayOccurrence(
        owner,
        occurrenceDisplayRecord(owner, slot),
        "${owner.eventSeq}:${slot.key}"
    )
}

/** Half-open interval intersection; point events belong to exactly one day. */
internal fun overlapsDayWindow(record: TemporalRecord, begin: Long, end: Long): Boolean {
    val start = record.anchorMillis() ?: return false
    val finish = record.spanEndMillis() ?: start
    return if (finish > start) start < end && finish > begin else start >= begin && start < end
}

/** A drag only fills an uncommitted form, preserving the formal owner's wall-clock zone. */
internal fun movedDraftForm(record: TemporalRecord, target: Long): DraftForm {
    val base = DraftForm.from(record)
    return when (record.kind) {
        TemporalKind.ALARM -> base.copy(startText = DraftForm.formatInstant(target, record.zone))
        TemporalKind.ACTIVITY -> {
            val duration = (record.endMillis ?: target) - (record.startMillis ?: target)
            base.copy(startText = DraftForm.formatInstant(target, record.zone), endText = DraftForm.formatInstant(target + duration, record.zone))
        }
        TemporalKind.MEMO -> base.copy(startText =
            Instant.ofEpochMilli(target).atZone(record.zone).toLocalDate().toString())
        TemporalKind.DEADLINE -> base.copy(endText = if (record.isDateOnlyDeadline()) {
            Instant.ofEpochMilli(target).atZone(record.zone).toLocalDate().toString()
        } else DraftForm.formatInstant(target, record.zone))
    }
}

internal fun displayOccurrences(
    records: List<TemporalRecord>, from: Long, to: Long
): List<DisplayOccurrence> = records.flatMap { owner ->
    if (!owner.recurrence.isRepeating) {
        val occurrence = singleDisplayOccurrence(owner) ?: return@flatMap emptyList()
        val display = occurrence.record
        val start = display.anchorMillis() ?: return@flatMap emptyList()
        val end = if (display.isDateOnlyDeadline()) {
            LocalDate.parse(display.deadlineDate).plusDays(1).atStartOfDay(owner.zone).toInstant().toEpochMilli()
        } else display.spanEndMillis() ?: start
        if (start < to && (if (end > start) end > from else start >= from)) {
            listOf(occurrence)
        } else emptyList()
    } else {
        val queryFrom = if (owner.isDateOnlyDeadline()) Instant.ofEpochMilli(from).atZone(owner.zone).toLocalDate().atStartOfDay(owner.zone).toInstant().toEpochMilli() else from
        RecurrenceEngine.occurrences(
            owner, queryFrom, to, limit = 2048, prioritizeWindowStarts = true
        ).map { slot ->
            val display = occurrenceDisplayRecord(owner, slot)
            DisplayOccurrence(owner, display, "${owner.eventSeq}:${slot.key}")
        }
    }
}

/** Compare complete editable snapshots, including fields removed by a type conversion. */
internal data class SnapshotRow(val label: String, val before: String, val after: String) {
    val changed: Boolean get() = before != after
}

internal fun snapshotRows(current: TemporalRecord?, proposed: DraftForm): List<SnapshotRow> {
    fun fields(form: DraftForm?): List<Pair<String, String>> = listOf(
        "类型" to (form?.kind?.label ?: ""),
        "标题" to (form?.title ?: ""),
        "开始" to (form?.startText ?: ""),
        "结束 / 截止" to (form?.endText ?: ""),
        "地点" to (form?.location ?: ""),
        "备注" to (form?.notes ?: ""),
        "提醒" to DraftDiff.reminderLabel(form?.reminderMinutes),
        "启用" to (form?.let { if (it.enabled) "是" else "否" } ?: ""),
        "时区" to (form?.zoneId ?: ""),
        "来源" to (form?.source ?: ""),
        "重复频率" to (form?.recurrence?.freq?.name ?: ""),
        "重复间隔" to (form?.recurrence?.interval?.toString() ?: ""),
        "重复次数上限" to (form?.recurrence?.count?.toString() ?: "无"),
        "重复截至" to (form?.recurrence?.untilMillis?.let { DraftForm.formatInstant(it, form.zone) } ?: "无"),
        "重复星期" to (form?.recurrence?.byDay?.sortedBy { it.value }?.joinToString { it.name } ?: ""),
        "跳过的原始实例" to (form?.recurrence?.exceptions?.sorted()?.joinToString("\n") ?: ""),
        "实例覆盖" to (form?.recurrence?.overrides?.toSortedMap()?.entries?.joinToString("\n") { (key, value) ->
            "$key → ${DraftForm.formatInstant(value.startMillis, form.zone)} / ${DraftForm.formatInstant(value.endMillis, form.zone)} · ${value.title.orEmpty()}"
        } ?: "")
    )
    return fields(current?.let(DraftForm::from)).zip(fields(proposed)).map { (a, b) ->
        SnapshotRow(a.first, a.second.ifBlank { "（空）" }, b.second.ifBlank { "（空）" })
    }
}

/** Direction arbitration shared by the card and backdrop. No paging on a vertical drag. */
internal fun horizontalPageSteps(dx: Float, dy: Float, threshold: Float, pageWidth: Float, outside: Boolean, velocity: Float = 0f): Int {
    if (abs(dx) < threshold || abs(dx) < abs(dy) * 1.4f) return 0
    val pages = if (outside) ((abs(dx) + abs(velocity) * 0.12f) / pageWidth).toInt().coerceIn(1, 12) else 1
    return if (dx < 0) pages else -pages
}

/** Pinch: the old centroid's instant follows the new centroid, including simultaneous pan. */
internal fun transformedCamera(camera: TimelineCoordinate.Camera, oldY: Float, newY: Float, zoom: Float, height: Float): TimelineCoordinate.Camera {
    val hours = TimelineCoordinate.clampHours(camera.hours / zoom.coerceAtLeast(0.01f))
    val focus = TimelineCoordinate.timeAt(oldY, camera.anchorMillis, height, camera.hours)
    val offset = TimelineCoordinate.timeAt(newY, 0L, height, hours)
    return TimelineCoordinate.Camera(focus - offset, hours)
}

internal data class OverflowGroup(val side: AxisSide, val start: Long, val end: Long, val records: List<TemporalRecord>)

internal fun overflowGroups(overflow: Map<AxisSide, List<TemporalRecord>>, minimumSpan: Long): List<OverflowGroup> = buildList {
    overflow.forEach { (side, records) ->
        var group = mutableListOf<TemporalRecord>()
        var start = 0L
        var end = Long.MIN_VALUE
        records.sortedBy { it.anchorMillis() }.forEach { record ->
            val anchor = record.anchorMillis() ?: return@forEach
            if (anchor > end && group.isNotEmpty()) {
                add(OverflowGroup(side, start, end, group.toList()))
                group = mutableListOf()
            }
            if (group.isEmpty()) start = anchor
            end = maxOf(if (group.isEmpty()) anchor else end, record.spanEndMillis() ?: anchor, anchor + minimumSpan)
            group.add(record)
        }
        if (group.isNotEmpty()) add(OverflowGroup(side, start, end, group.toList()))
    }
}
