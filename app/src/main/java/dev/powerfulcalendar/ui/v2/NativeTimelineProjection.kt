package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.AxisSide
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.TimelineLayout
import java.time.Instant
import java.time.ZoneId
import java.util.IdentityHashMap

internal data class NativeTimelineOpenTarget(
    val owner: TemporalRecord,
    val occurrenceKey: String?
)

internal data class NativeTimelineHostProjection(
    val events: List<NativeTimelineEvent>,
    val openTargets: Map<String, NativeTimelineOpenTarget>,
    val hasRecords: Boolean,
    val hasDateOnlyDeadlines: Boolean
)

/** Core-backed projection. Only display values and opaque row keys cross into :native-skin. */
internal fun projectNativeTimeline(
    records: List<TemporalRecord>,
    camera: TimelineCoordinate.Camera,
    zone: ZoneId
): NativeTimelineHostProjection {
    val day = Instant.ofEpochMilli(camera.anchorMillis).atZone(zone).toLocalDate()
    val from = day.minusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = day.plusDays(9).atStartOfDay(zone).toInstant().toEpochMilli()
    val occurrences = records.filterNot { it.recurrence.isRepeating }
        .mapNotNull(::singleDisplayOccurrence) +
        displayOccurrences(records.filter { it.recurrence.isRepeating }, from, to)

    val occurrenceByRecord = IdentityHashMap<TemporalRecord, DisplayOccurrence>()
    occurrences.forEach { occurrenceByRecord[it.record] = it }

    val layout = TimelineLayout.layout(
        occurrences.map { it.record }.filterNot { it.kind == TemporalKind.MEMO }
    )
    val rows = layout.items.mapNotNull { item ->
        val occurrence = occurrenceByRecord[item.record] ?: return@mapNotNull null
        val record = item.record
        val kind = when (record.kind) {
            TemporalKind.ALARM -> NativeTimelineKind.ALARM
            TemporalKind.ACTIVITY -> NativeTimelineKind.ACTIVITY
            TemporalKind.DEADLINE -> NativeTimelineKind.DEADLINE
            TemporalKind.MEMO -> NativeTimelineKind.MEMO
        }
        NativeTimelineEvent(
            rowKey = occurrence.key,
            eventSeq = occurrence.owner.eventSeq,
            revision = occurrence.owner.revision,
            kind = kind,
            kindLabel = record.kind.label,
            title = record.title,
            notes = record.notes,
            summary = TemporalText.summarize(record),
            sequenceLabel = occurrence.owner.displaySeq(),
            anchorMillis = item.anchorMillis,
            endMillis = record.spanEndMillis(),
            deadlineDate = record.deadlineDate,
            zoneId = record.zone.id,
            dateOnlyDeadline = record.isDateOnlyDeadline(),
            lane = item.lane,
            side = when (item.side) {
                AxisSide.LEFT -> NativeTimelineSide.LEFT
                AxisSide.RIGHT -> NativeTimelineSide.RIGHT
            }
        )
    }
    val targets = occurrences.associate { occurrence ->
        occurrence.key to NativeTimelineOpenTarget(
            owner = occurrence.owner,
            occurrenceKey = occurrence.occurrenceKey.takeIf {
                occurrence.owner.recurrence.isRepeating
            }
        )
    }
    return NativeTimelineHostProjection(
        events = rows,
        openTargets = targets,
        hasRecords = records.isNotEmpty(),
        hasDateOnlyDeadlines = records.any { it.isDateOnlyDeadline() }
    )
}
internal object CoreNativeTimelineGeometry : NativeTimelineGeometry {
    override val minimumHours: Double get() = TimelineCoordinate.MIN_HOURS
    override val maximumHours: Double get() = TimelineCoordinate.MAX_HOURS
    override fun spanMillis(hours: Double): Double = TimelineCoordinate.spanMillis(hours)
    override fun clampHours(hours: Double): Double = TimelineCoordinate.clampHours(hours)
    override fun yOf(
        instantMillis: Long,
        anchorMillis: Long,
        heightPx: Float,
        hours: Double
    ): Float = TimelineCoordinate.yOf(instantMillis, anchorMillis, heightPx, hours)

    override fun snap(instantMillis: Long, hours: Double, zone: ZoneId): Long =
        TimelineCoordinate.snap(instantMillis, hours, zone)

    override fun transformedCamera(
        camera: NativeTimelineCamera,
        oldY: Float,
        newY: Float,
        zoom: Float,
        heightPx: Float
    ): NativeTimelineCamera {
        val transformed = dev.powerfulcalendar.ui.v2.transformedCamera(
            TimelineCoordinate.Camera(camera.anchorMillis, camera.hours),
            oldY,
            newY,
            zoom,
            heightPx
        )
        return NativeTimelineCamera(transformed.anchorMillis, transformed.hours)
    }
}
