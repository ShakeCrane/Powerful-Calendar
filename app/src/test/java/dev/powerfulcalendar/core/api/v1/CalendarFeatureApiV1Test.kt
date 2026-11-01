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

    @Test
    fun boundedEventQueryUsesInclusiveViewerDatesAndReturnsVersionedMinimalHits() {
        val matching = commit(
            TemporalKind.ACTIVITY,
            "Weekly Sync",
            start = "2026-10-26 16:30",
            end = "2026-10-26 17:30",
            zoneId = "UTC",
            recurrence = Recurrence(freq = RecurrenceFreq.WEEKLY, count = 3)
        )
        commit(
            TemporalKind.ACTIVITY,
            "Weekly Sync outside viewer date",
            start = "2026-10-27 18:00",
            end = "2026-10-27 19:00",
            zoneId = "UTC"
        )
        commit(
            TemporalKind.DEADLINE,
            "Weekly Sync deadline",
            end = "2026-10-27",
            zoneId = "Asia/Shanghai"
        )
        val source = core.events.queryEvents()

        val result = core.calendar.queryEvents(
            CalendarEventQueryV1(
                zoneId = "Asia/Shanghai",
                fromDate = "2026-10-27",
                throughDate = "2026-10-27",
                titleQuery = "weekly sync",
                kind = EventKindV1.ACTIVITY,
                maxResults = 10
            ),
            source
        )

        val hit = result.results.single()
        assertEquals(source.featureRevision, result.featureRevision)
        assertEquals(1, result.totalMatches)
        assertFalse(result.truncated)
        assertEquals(matching, hit.eventSeq)
        assertEquals(source.events.single { it.eventSeq == matching }.revision, hit.revision)
        assertEquals(EventKindV1.ACTIVITY, hit.kind)
        assertEquals("Weekly Sync", hit.title)
        assertEquals(CalendarEventPrecisionV1.INTERVAL, hit.precision)
        assertEquals("UTC", hit.zoneId)
        assertEquals("local", hit.source)
        assertEquals(EventRecurrenceFrequencyV1.WEEKLY, hit.recurrence.frequency)
        assertEquals(instant("2026-10-26T16:30:00Z"), hit.startMillis)
        assertEquals(instant("2026-10-26T17:30:00Z"), hit.endMillis)
    }

    @Test
    fun boundedEventQueryCapsRowsAndDetachesRecurrenceCollections() {
        val template = commit(
            TemporalKind.MEMO,
            "Record template",
            zoneId = "UTC"
        )
        val record = core.events.queryEvents().events.single { it.eventSeq == template }
        val mutableExceptions = linkedSetOf("2026-11-03")
        val events = (1L..105L).map { seq ->
            record.copy(
                eventSeq = seq,
                revision = seq + 4L,
                title = "Record $seq",
                recurrence = record.recurrence.copy(exceptions = mutableExceptions)
            )
        }
        val result = core.calendar.queryEvents(
            CalendarEventQueryV1(
                zoneId = "UTC",
                titleQuery = "record",
                maxResults = 100
            ),
            EventQuerySnapshotV1(featureRevision = 77L, events = events)
        )

        mutableExceptions += "2026-11-04"
        assertEquals(77L, result.featureRevision)
        assertEquals(105, result.totalMatches)
        assertEquals(100, result.results.size)
        assertTrue(result.truncated)
        assertEquals(1L, result.results.first().eventSeq)
        assertEquals(100L, result.results.last().eventSeq)
        assertEquals(setOf("2026-11-03"), result.results.first().recurrence.exceptions)
    }

    @Test
    fun eventQueryRejectsMissingOrUnboundedSelectors() {
        assertThrows(IllegalArgumentException::class.java) {
            CalendarEventQueryV1(zoneId = "UTC")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarEventQueryV1(
                zoneId = "UTC", fromDate = "2026-01-01", titleQuery = "meeting"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarEventQueryV1(
                zoneId = "UTC", fromDate = "2026-01-01", throughDate = "2027-01-02"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarEventQueryV1(zoneId = "UTC", titleQuery = "x".repeat(121))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarEventQueryV1(zoneId = "UTC", titleQuery = "meeting", maxResults = 101)
        }
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
