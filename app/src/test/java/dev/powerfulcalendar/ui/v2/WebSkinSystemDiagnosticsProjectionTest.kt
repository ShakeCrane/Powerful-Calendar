package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinSystemDiagnosticsProjectionTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()

    private fun alarm(
        eventSeq: Long,
        anchor: String,
        recurrence: Recurrence = Recurrence(RecurrenceFreq.DAILY)
    ) = TemporalRecord(
        eventSeq = eventSeq,
        revision = 1,
        kind = TemporalKind.ALARM,
        title = "private recurrence title",
        startMillis = ZonedDateTime.parse(anchor).toInstant().toEpochMilli(),
        recurrence = recurrence,
        zoneId = zone.id
    )

    @Test
    fun projectionMirrorsNativeCountsWatermarksEffectsAndThreeNextOccurrences() {
        val records = listOf(
            alarm(1, "2026-10-28T09:00:00Z"),
            alarm(2, "2026-10-28T10:00:00Z"),
            alarm(3, "2026-10-28T11:00:00Z"),
            alarm(4, "2026-10-28T12:00:00Z"),
            alarm(5, "2026-10-30T13:00:00Z", Recurrence.NONE)
        )

        val projected = WebSkinSystemDiagnosticsProjection.build(
            records = records,
            nextSequence = 42,
            identityCount = 987_654_321_987_654_321L,
            effects = GlassEffects(blur = false, animationScale = 0f, reduceMotion = true),
            nowMillis = now,
            zone = zone
        )

        assertEquals(records.size, projected.getInt("formalCount"))
        assertEquals("#0042", projected.getString("nextSequence"))
        assertEquals("987654321987654321", projected.getString("identityCount"))
        assertEquals(PowerStore.VERSION, projected.getInt("databaseVersion"))
        assertEquals(PowerStore.DATABASE_NAME, projected.getString("databaseName"))
        assertFalse(projected.getBoolean("blurEnabled"))
        assertTrue(projected.getBoolean("reduceMotion"))
        assertFalse(projected.toString().contains("private recurrence title"))

        val repeats = projected.getJSONArray("recurrences")
        assertEquals(3, repeats.length())
        assertEquals("重复 #1", repeats.getJSONObject(0).getString("label"))
        assertEquals("下一次 2026-10-31 09:00", repeats.getJSONObject(0).getString("value"))
        assertEquals("重复 #3", repeats.getJSONObject(2).getString("label"))
    }

    @Test
    fun endedRecurrenceKeepsNativeNoFutureOccurrenceDiagnostic() {
        val ended = alarm(
            eventSeq = 7,
            anchor = "2026-10-28T09:00:00Z",
            recurrence = Recurrence(RecurrenceFreq.DAILY, count = 1)
        )

        val projected = WebSkinSystemDiagnosticsProjection.build(
            records = listOf(ended),
            nextSequence = 8,
            identityCount = 7,
            effects = GlassEffects(),
            nowMillis = now,
            zone = zone
        )

        val recurrence = projected.getJSONArray("recurrences").getJSONObject(0)
        assertEquals("重复 #7", recurrence.getString("label"))
        assertEquals("没有后续实例", recurrence.getString("value"))
    }

    @Test
    fun dailyAlarmDiagnosticUsesCurrentLocalZoneAcrossDstFallback() {
        val localZone = ZoneId.of("America/New_York")
        val anchor = LocalDate.of(2026, 10, 31).atTime(LocalTime.of(9, 0))
            .atZone(localZone).toInstant().toEpochMilli()
        val record = TemporalRecord(
            eventSeq = 9,
            revision = 1,
            kind = TemporalKind.ALARM,
            title = "private DST recurrence title",
            startMillis = anchor,
            recurrence = Recurrence(RecurrenceFreq.DAILY),
            zoneId = localZone.id
        )

        val projected = WebSkinSystemDiagnosticsProjection.build(
            records = listOf(record),
            nextSequence = 10,
            identityCount = 9,
            effects = GlassEffects(),
            nowMillis = Instant.parse("2026-11-01T06:00:00Z").toEpochMilli(),
            zone = localZone
        )

        val recurrence = projected.getJSONArray("recurrences").getJSONObject(0)
        assertEquals("下一次 2026-11-01 09:00", recurrence.getString("value"))
        assertFalse(recurrence.toString().contains("private DST recurrence title"))
    }
}
