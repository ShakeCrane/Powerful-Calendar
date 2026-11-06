package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.api.v1.CoreRecurrenceFeatureApiV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebSkinMonthProjectionTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val recurrence = CoreRecurrenceFeatureApiV1()

    @Test
    fun webMonthProjectionRetainsEverySingleEventAboveFormerItemLimit() {
        val month = YearMonth.of(2026, 10)
        assertWebMatchesNative(singleEvents(month, 351), month, expectedOccurrences = 351)
    }

    @Test
    fun webMonthProjectionRetainsOwnersAboveFormerOwnerLimit() {
        val month = YearMonth.of(2026, 10)
        assertWebMatchesNative(singleEvents(month, 401), month, expectedOccurrences = 401)
    }

    @Test
    fun webMonthProjectionRetainsDenseDailyRecurringInstances() {
        val month = YearMonth.of(2026, 10)
        val recurrenceStart = LocalDateTime.of(2026, 9, 20, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val records = (1L..12L).map { eventSeq ->
            TemporalRecord(
                eventSeq = eventSeq,
                revision = 1,
                kind = TemporalKind.ACTIVITY,
                title = "每日活动 $eventSeq",
                startMillis = recurrenceStart,
                endMillis = recurrenceStart + 3_600_000L,
                zoneId = zone.id,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 60)
            )
        }
        assertWebMatchesNative(records, month, expectedOccurrences = 12 * month.lengthOfMonth())
    }

    private fun singleEvents(month: YearMonth, count: Int): List<TemporalRecord> =
        (1L..count.toLong()).map { eventSeq ->
            val day = month.atDay(((eventSeq - 1) % month.lengthOfMonth() + 1).toInt())
            val start = LocalDateTime.of(day, java.time.LocalTime.of(9, 0))
                .atZone(zone).toInstant().toEpochMilli()
            TemporalRecord(
                eventSeq = eventSeq,
                revision = 1,
                kind = TemporalKind.ACTIVITY,
                title = "活动 $eventSeq",
                startMillis = start,
                endMillis = start + 3_600_000L,
                zoneId = zone.id
            )
        }

    private fun assertWebMatchesNative(
        records: List<TemporalRecord>,
        month: YearMonth,
        expectedOccurrences: Int
    ) {
        val nativeRows = projectNativeMonth(records, month, zone, recurrence).rows
        val webMonth = WebSkinMonthProjection.build(
            records,
            WebSkinMonthProjection.selectMonth(month)!!,
            zone,
            recurrence
        )
        val webItems = webMonth.getJSONArray("items")
        val webRowKeys = (0 until webItems.length()).map { index ->
            webItems.getJSONObject(index).getString("occurrenceKey")
        }

        assertEquals(expectedOccurrences, nativeRows.size)
        assertEquals("Web must project every occurrence available to Native",
            expectedOccurrences, webItems.length())
        assertEquals(nativeRows.map { it.rowKey }, webRowKeys)
        assertFalse(webMonth.optBoolean("truncated", false))
    }
}
