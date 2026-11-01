package dev.powerfulcalendar.core.api.v1

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.OccurrenceOverride
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "cal-api-${UUID.randomUUID()}.db")
        core = PowerCalCoreFeatureApiV1(store)
    }

    @After
    fun tearDown() {
        val databaseName = store.databaseName
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun windowIncludesEffectiveOccurrences() {
        val utc = ZoneId.of("UTC")
        val recurring = commit(
            TemporalKind.ALARM,
            "Series anchored before window",
            start = "2026-10-01 09:00",
            zoneId = utc.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 40)
        )
        val moved = commit(
            TemporalKind.ALARM,
            "Moved original slot",
            start = "2026-09-01 09:00",
            zoneId = utc.id,
            recurrence = Recurrence(
                freq = RecurrenceFreq.DAILY,
                count = 2,
                overrides = mapOf(
                    "2026-09-02T09:00" to OccurrenceOverride(
                        startMillis = instant("2026-10-26T09:30:00Z"),
                        endMillis = null,
                        title = "Moved into window"
                    )
                )
            )
        )
        val overlapping = commit(
            TemporalKind.ACTIVITY,
            "Carry-in interval",
            start = "2026-10-26 08:45",
            end = "2026-10-26 09:15",
            zoneId = utc.id
        )
        commit(
            TemporalKind.ALARM,
            "At exclusive end",
            start = "2026-10-26 10:00",
            zoneId = utc.id
        )
        commit(
            TemporalKind.ALARM,
            "Outside recurrence count",
            start = "2026-10-01 09:00",
            zoneId = utc.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 2)
        )

        val source = core.events.queryEvents()
        val result = core.calendar.queryWindow(
            CalendarWindowQueryV1(
                fromMillis = instant("2026-10-26T09:00:00Z"),
                toMillis = instant("2026-10-26T10:00:00Z"),
                zoneId = utc.id
            ),
            source
        )

        assertEquals(1, core.calendar.apiVersion)
        assertEquals(source.featureRevision, result.featureRevision)
        assertEquals(setOf(recurring, moved, overlapping), result.events.map { it.eventSeq }.toSet())
        assertEquals(
            "2026-09-02T09:00",
            result.events.single { it.eventSeq == moved }.recurrence.overrides.keys.single()
        )
    }

    @Test
    fun dateOnlyDeadlinesAndDateRangeMemosUseTheVisibleCalendarDates() {
        val deadline = commit(
            TemporalKind.DEADLINE,
            "Visible date-only deadline",
            end = "2026-10-26",
            zoneId = "Pacific/Kiritimati"
        )
        commit(
            TemporalKind.DEADLINE,
            "Previous local date",
            end = "2026-10-25",
            zoneId = "Pacific/Kiritimati"
        )
        val memo = commit(
            TemporalKind.MEMO,
            "Memo spanning the window",
            start = "2026-10-25",
            end = "2026-10-27",
            zoneId = "UTC"
        )
        commit(TemporalKind.MEMO, "Undated memo", zoneId = "UTC")

        val source = core.events.queryEvents()
        val result = core.calendar.queryWindow(
            CalendarWindowQueryV1(
                fromMillis = instant("2026-10-26T00:00:00Z"),
                toMillis = instant("2026-10-27T00:00:00Z"),
                zoneId = "UTC"
            ),
            source
        )

        assertEquals(setOf(deadline, memo), result.events.map { it.eventSeq }.toSet())
        assertFalse(result.events.any { it.title == "Undated memo" })
    }

    @Test
    fun calendarWindowRejectsEmptyOrInvalidZoneRequests() {
        assertThrows(IllegalArgumentException::class.java) {
            CalendarWindowQueryV1(10L, 10L, "UTC")
        }
        assertThrows(Exception::class.java) {
            CalendarWindowQueryV1(10L, 11L, "not-a-time-zone")
        }
        assertTrue(core.calendar.queryWindow(
            CalendarWindowQueryV1(10L, 11L, "UTC"),
            EventQuerySnapshotV1(featureRevision = 4L, events = emptyList())
        ).events.isEmpty())
    }

    private fun commit(
        kind: TemporalKind,
        title: String,
        start: String = "",
        end: String = "",
        zoneId: String,
        recurrence: Recurrence = Recurrence.NONE
    ): Long {
        val card = store.newDraft(
            DraftForm(
                kind = kind,
                title = title,
                startText = start,
                endText = end,
                zoneId = zoneId,
                recurrence = recurrence
            )
        )
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        return card.eventSeq
    }

    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()
}
