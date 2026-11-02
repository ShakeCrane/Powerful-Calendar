package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.api.v1.CoreRecurrenceFeatureApiV1
import dev.powerfulcalendar.core.api.v1.RecurrenceFeatureApiV1
import dev.powerfulcalendar.core.api.v1.RecurrenceOccurrenceWindowQueryV1
import dev.powerfulcalendar.ui.v2.isDateDeadlineVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

class NativeTimelineProjectionTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val recurrenceFeatures = CoreRecurrenceFeatureApiV1()

    private class RecordingRecurrenceFeatureApiV1(
        private val delegate: RecurrenceFeatureApiV1
    ) : RecurrenceFeatureApiV1 by delegate {
        val windowQueries = mutableListOf<RecurrenceOccurrenceWindowQueryV1>()

        override fun queryOccurrencesInWindow(
            query: RecurrenceOccurrenceWindowQueryV1
        ) = delegate.queryOccurrencesInWindow(query).also { windowQueries += query }
    }

    @Test
    fun recurringRowsKeepDisplayOverridesAndResolveToOriginalOwnerSlot() {
        val owner = TemporalRecord(
            eventSeq = 71,
            revision = 4,
            kind = TemporalKind.ALARM,
            title = "每日提醒",
            startMillis = LocalDateTime.of(2026, 10, 20, 7, 30)
                .atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id,
            recurrence = Recurrence(
                freq = RecurrenceFreq.DAILY,
                count = 3,
                overrides = mapOf("2026-10-21T07:30" to
                    dev.powerfulcalendar.core.OccurrenceOverride(
                        LocalDateTime.of(2026, 10, 21, 8, 0).atZone(zone)
                            .toInstant().toEpochMilli(),
                        null,
                        "周二改名"
                    ))
            )
        )
        val camera = TimelineCoordinate.Camera(
            LocalDateTime.of(2026, 10, 21, 12, 0).atZone(zone).toInstant().toEpochMilli(),
            24.0
        )

        val projection = projectNativeTimeline(
            listOf(owner), camera, zone, CoreRecurrenceFeatureApiV1()
        )
        val row = projection.events.single { it.title == "周二改名" }
        val target = projection.openTargets.getValue(row.rowKey)

        assertEquals(71L, row.eventSeq)
        assertEquals(4L, row.revision)
        assertEquals(TemporalKind.ALARM.label, row.kindLabel)
        assertEquals("2026-10-21T07:30", target.occurrenceKey)
        assertEquals(owner, target.owner)
        assertEquals(NativeTimelineSide.LEFT, row.side)
        assertEquals(8, java.time.Instant.ofEpochMilli(row.anchorMillis).atZone(zone).hour)
    }

    @Test
    fun laneProjectionRetainsTrueIntervalsAndDateOnlyDeadlinePrecision() {
        fun activity(seq: Long, startHour: Int, endHour: Int) = TemporalRecord(
            eventSeq = seq,
            revision = 1,
            kind = TemporalKind.ACTIVITY,
            title = "活动 $seq",
            startMillis = LocalDateTime.of(2026, 10, 21, startHour, 0)
                .atZone(zone).toInstant().toEpochMilli(),
            endMillis = LocalDateTime.of(2026, 10, 21, endHour, 0)
                .atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id
        )
        val deadline = TemporalRecord(
            eventSeq = 73,
            revision = 2,
            kind = TemporalKind.DEADLINE,
            title = "日期截止",
            deadlineDate = "2026-10-21",
            zoneId = zone.id
        )
        val camera = TimelineCoordinate.Camera(
            LocalDateTime.of(2026, 10, 21, 12, 0).atZone(zone).toInstant().toEpochMilli(),
            24.0
        )

        val projection = projectNativeTimeline(
            listOf(activity(71, 9, 11), activity(72, 10, 12), deadline), camera, zone,
            recurrenceFeatures
        )
        val first = projection.events.single { it.eventSeq == 71L }
        val second = projection.events.single { it.eventSeq == 72L }
        val dateRow = projection.events.single { it.eventSeq == 73L }

        assertEquals(NativeTimelineSide.RIGHT, first.side)
        assertEquals(NativeTimelineSide.RIGHT, second.side)
        assertTrue(first.lane != second.lane)
        assertEquals(2L * 60 * 60_000L, first.endMillis!! - first.anchorMillis)
        assertTrue(dateRow.dateOnlyDeadline)
        assertEquals("2026-10-21", dateRow.deadlineDate)
        assertEquals(NativeTimelineSide.LEFT, dateRow.side)
        assertTrue(isDateDeadlineVisible(dateRow, camera.toNative(), CoreNativeTimelineGeometry))
        assertNull(projection.openTargets.getValue(dateRow.rowKey).occurrenceKey)
    }

    @Test
    fun memoIsNotGivenMinuteScaleLaneAndEmptyStateReflectsFormalRecords() {
        val memo = TemporalRecord(
            eventSeq = 81,
            revision = 1,
            kind = TemporalKind.MEMO,
            title = "无日期 Memo",
            zoneId = zone.id
        )
        val camera = TimelineCoordinate.Camera(
            LocalDateTime.of(2026, 10, 21, 12, 0).atZone(zone).toInstant().toEpochMilli(),
            24.0
        )

        val projection = projectNativeTimeline(listOf(memo), camera, zone, recurrenceFeatures)

        assertTrue(projection.events.isEmpty())
        assertTrue(projection.hasRecords)
        assertFalse(projectNativeTimeline(emptyList(), camera, zone, recurrenceFeatures).hasRecords)
    }

    @Test
    fun dateOnlyDeadlineWindowUsesTheEventZoneWhenTheCameraZoneDiffers() {
        val deadlineZone = ZoneId.of("Asia/Shanghai")
        val deadline = TemporalRecord(
            eventSeq = 90,
            revision = 1,
            kind = TemporalKind.DEADLINE,
            title = "上海日期截止",
            deadlineDate = "2026-10-21",
            zoneId = deadlineZone.id
        )
        val camera = TimelineCoordinate.Camera(
            Instant.parse("2026-10-20T17:00:00Z").toEpochMilli(),
            4.0
        )
        val projection = projectNativeTimeline(
            listOf(deadline), camera, ZoneId.of("America/Los_Angeles"), CoreRecurrenceFeatureApiV1()
        )
        val row = projection.events.single()

        assertEquals(deadlineZone.id, row.zoneId)
        assertTrue(isDateDeadlineVisible(
            row,
            NativeTimelineCamera(camera.anchorMillis, camera.hours),
            CoreNativeTimelineGeometry
        ))
    }

    @Test
    fun nativeTimelineRoutesAllVisibleOwnersThroughTheBoundedCoreQuery() {
        val start = LocalDateTime.of(2026, 10, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val recurring = TemporalRecord(
            eventSeq = 91,
            revision = 5,
            kind = TemporalKind.ALARM,
            title = "Recurring",
            startMillis = start,
            zoneId = zone.id,
            recurrence = Recurrence(RecurrenceFreq.DAILY, count = 5)
        )
        val single = TemporalRecord(
            eventSeq = 92,
            revision = 2,
            kind = TemporalKind.ACTIVITY,
            title = "Single",
            startMillis = start,
            endMillis = start + 60 * 60_000,
            zoneId = zone.id
        )
        val camera = TimelineCoordinate.Camera(start + 3 * 60 * 60_000, 24.0)
        val records = listOf(recurring, single)
        val expected = projectNativeTimeline(records, camera, zone, CoreRecurrenceFeatureApiV1())
        val recurrenceApi = RecordingRecurrenceFeatureApiV1(CoreRecurrenceFeatureApiV1())

        val actual = projectNativeTimeline(records, camera, zone, recurrenceApi)

        assertEquals(expected.events, actual.events)
        assertEquals(expected.openTargets, actual.openTargets)
        assertEquals(setOf(91L, 92L), recurrenceApi.windowQueries.map { it.event.eventSeq }.toSet())
        val localDay = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(camera.anchorMillis), zone)
            .toLocalDate()
        assertTrue(recurrenceApi.windowQueries.all { query ->
            query.fromMillis == localDay.minusDays(8).atStartOfDay(zone).toInstant().toEpochMilli() &&
                query.toMillis == localDay.plusDays(9).atStartOfDay(zone).toInstant().toEpochMilli() &&
                query.maxOccurrences == RecurrenceOccurrenceWindowQueryV1.MAX_OCCURRENCES
        })
    }

    private fun TimelineCoordinate.Camera.toNative() =
        NativeTimelineCamera(anchorMillis, hours)
}
