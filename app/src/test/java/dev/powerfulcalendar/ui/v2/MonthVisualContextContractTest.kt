package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.api.v1.CoreRecurrenceFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventRecordV1
import dev.powerfulcalendar.core.api.v1.toEventRecordV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthVisualContextContractTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun skinReadsVersionedEventDtosAndOpenKeepsOwnerIdentity() {
        val record = TemporalRecord(
            eventSeq = 51,
            revision = 7,
            kind = TemporalKind.ACTIVITY,
            title = "跨夜值守",
            startMillis = LocalDateTime.of(2026, 10, 20, 23, 30)
                .atZone(zone).toInstant().toEpochMilli(),
            endMillis = LocalDateTime.of(2026, 10, 21, 1, 0)
                .atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id
        )
        val month = YearMonth.of(2026, 10)
        val requestedMonths = mutableListOf<YearMonth>()
        var opened: Triple<Long, Long, String?>? = null
        val context = MonthVisualContext(
            nativeRecords = listOf(record),
            nativeRecordsForMonth = { requested ->
                requestedMonths += requested
                listOf(record)
            },
            initialDate = LocalDate.of(2026, 10, 20),
            zone = zone,
            onRequestMonth = { requestedMonths += it },
            aiLocked = false,
            onBack = {},
            onDate = {},
            onOpen = { eventSeq, revision, occurrenceKey ->
                opened = Triple(eventSeq, revision, occurrenceKey)
            },
            onCreate = {},
            recurrenceFeatures = CoreRecurrenceFeatureApiV1()
        )

        val skinRecords: List<EventRecordV1> = context.records
        val skinRecordsForMonth: (YearMonth) -> List<EventRecordV1> = context.recordsForMonth
        val skinOpen: (Long, Long, String?) -> Unit = context.onOpen
        val projected = skinRecords.single()
        val occurrenceKey = "2026-10-20T23:30"
        skinOpen(projected.eventSeq, projected.revision, occurrenceKey)
        val projectedMonth = skinRecordsForMonth(month).single()

        assertEquals(record.toEventRecordV1(), projected)
        assertEquals(record.toEventRecordV1(), projectedMonth)
        assertEquals(listOf(month), requestedMonths)
        assertEquals(Triple(record.eventSeq, record.revision, occurrenceKey), opened)
    }

    @Test
    fun runtimeSkinRowsKeepSortSummaryAndExistingLimit() {
        val early = event(1, "较早", 9, 0).toEventRecordV1()
        val late = event(2, "较晚", 11, 0).toEventRecordV1()
        val deadline = TemporalRecord(
            eventSeq = 3,
            revision = 1,
            kind = TemporalKind.DEADLINE,
            title = "日期截止",
            deadlineDate = "2026-10-22",
            zoneId = zone.id
        ).toEventRecordV1()

        val rows = projectRuntimeSkinEventRows(listOf(late, deadline, early), zone)

        assertEquals(listOf("较早", "较晚", "日期截止"), rows.map { it.event.title })
        assertEquals("09:00 – 10:00", rows.first().summary)
        assertTrue(rows.last().summary.contains("10月22日"))
        assertTrue(rows.last().summary.contains("（日期）"))

        val many = (0 until 72).map { index ->
            TemporalRecord(
                eventSeq = index + 10L,
                revision = 1,
                kind = TemporalKind.ALARM,
                title = "event-$index",
                startMillis = index.toLong(),
                zoneId = zone.id
            ).toEventRecordV1()
        }
        assertEquals(70, projectRuntimeSkinEventRows(many, zone).size)
    }

    private fun event(seq: Long, title: String, hour: Int, minute: Int) = TemporalRecord(
        eventSeq = seq,
        revision = 1,
        kind = TemporalKind.ACTIVITY,
        title = title,
        startMillis = LocalDateTime.of(2026, 10, 20, hour, minute)
            .atZone(zone).toInstant().toEpochMilli(),
        endMillis = LocalDateTime.of(2026, 10, 20, hour + 1, minute)
            .atZone(zone).toInstant().toEpochMilli(),
        zoneId = zone.id
    )
}
