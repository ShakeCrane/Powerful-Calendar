package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimePrecision
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventQuerySnapshotV1
import dev.powerfulcalendar.core.api.v1.EventMutationStatusV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.toEventRecordV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodoProjectionTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 10, 24).atTime(12, 0).atZone(zone)
        .toInstant().toEpochMilli()

    private fun at(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun memoApiKeepsUnboundedRecurrenceInsideTheExistingLookbackAndAheadWindow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "memo-window-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            val api = PowerCalCoreFeatureApiV1(
                store, reminderContext = context, settingsContext = context
            ).memos
            val recurring = TemporalRecord(
                eventSeq = 1, revision = 1, kind = TemporalKind.ALARM,
                title = "Unbounded daily task",
                startMillis = now - 400L * 86_400_000L,
                trackingEnabled = true, zoneId = zone.id,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY)
            )
            val projected = api.queryEntries(
                EventQuerySnapshotV1(11L, listOf(recurring.toEventRecordV1())), now
            ).entries
            val earliest = now - 365L * 86_400_000L
            val latest = now + 7L * 86_400_000L

            assertTrue(projected.size in 350..1200)
            assertTrue(projected.all { it.startMillis in earliest..latest })
            assertEquals(projected.size, projected.map { it.occurrenceKey }.distinct().size)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun memoApiProjectsTrackedIncompleteItemsAcrossKindsWithoutFlatteningDateOrUndatedSemantics() {
        val alarm = TemporalRecord(
            eventSeq = 1, revision = 1, kind = TemporalKind.ALARM, title = "Alarm",
            startMillis = at("2026-10-24", "08:00"), trackingEnabled = true, zoneId = zone.id
        )
        val activity = TemporalRecord(
            eventSeq = 2, revision = 1, kind = TemporalKind.ACTIVITY, title = "Activity",
            startMillis = at("2026-10-24", "09:00"), endMillis = at("2026-10-24", "10:00"),
            trackingEnabled = true, zoneId = zone.id
        )
        val dateDeadline = TemporalRecord(
            eventSeq = 3, revision = 1, kind = TemporalKind.DEADLINE, title = "Date deadline",
            deadlineDate = "2026-10-24", trackingEnabled = true, zoneId = zone.id
        )
        val datedMemo = TemporalRecord(
            eventSeq = 4, revision = 1, kind = TemporalKind.MEMO, title = "Dated memo",
            memoStartDate = "2026-10-25", memoEndDate = "2026-10-27", zoneId = zone.id
        )
        val undatedMemo = TemporalRecord(
            eventSeq = 5, revision = 1, kind = TemporalKind.MEMO, title = "Undated memo",
            zoneId = zone.id
        )
        val completedActivity = activity.copy(
            eventSeq = 6, title = "Completed activity", completedKeys = setOf("single")
        )
        val disabled = alarm.copy(eventSeq = 7, title = "Disabled alarm", enabled = false)
        val notTracked = activity.copy(eventSeq = 8, title = "Untracked activity", trackingEnabled = false)
        val repeatedActivity = TemporalRecord(
            eventSeq = 9, revision = 2, kind = TemporalKind.ACTIVITY, title = "Repeated activity",
            startMillis = at("2026-10-22", "09:00"), endMillis = at("2026-10-22", "10:00"),
            trackingEnabled = true, zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 6),
            completedKeys = setOf("2026-10-23T09:00")
        )
        val records = listOf(
            alarm, activity, dateDeadline, datedMemo, undatedMemo,
            completedActivity, disabled, notTracked, repeatedActivity
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "memo-api-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            val api = PowerCalCoreFeatureApiV1(
                store, reminderContext = context, settingsContext = context
            ).memos
            val source = EventQuerySnapshotV1(7L, records.map(TemporalRecord::toEventRecordV1))

            val snapshot = api.queryEntries(source, now)
            val remaining = snapshot.entries

            assertEquals(7L, snapshot.featureRevision)
            assertEquals(listOf(9L, 3L, 1L, 2L, 9L, 4L, 9L, 9L, 9L, 5L),
                remaining.map { it.eventSeq })
            assertEquals(
                setOf(EventKindV1.ALARM, EventKindV1.ACTIVITY, EventKindV1.DEADLINE, EventKindV1.MEMO),
                remaining.map { it.kind }.toSet()
            )
            remaining.forEach { entry ->
                assertEquals(records.single { it.eventSeq == entry.eventSeq }.revision, entry.revision)
                assertFalse(entry.completed)
            }

            val alarmEntry = remaining.single { it.eventSeq == alarm.eventSeq }
            assertEquals(alarm.startMillis, alarmEntry.startMillis)
            val activityEntry = remaining.single { it.eventSeq == activity.eventSeq }
            assertEquals(activity.startMillis, activityEntry.startMillis)
            val deadlineEntry = remaining.single { it.eventSeq == dateDeadline.eventSeq }
            assertEquals(EventKindV1.DEADLINE, deadlineEntry.kind)
            assertEquals(TimePrecision.DATE, dateDeadline.precision)
            assertEquals("2026-10-24", dateDeadline.deadlineDate)
            assertNull(dateDeadline.deadlineMillis)
            assertEquals(at("2026-10-24", "00:00"), deadlineEntry.startMillis)
            val datedMemoEntry = remaining.single { it.eventSeq == datedMemo.eventSeq }
            assertEquals("2026-10-25", datedMemo.memoStartDate)
            assertEquals("2026-10-27", datedMemo.memoEndDate)
            assertEquals(at("2026-10-25", "00:00"), datedMemoEntry.startMillis)
            assertNull(remaining.single { it.eventSeq == undatedMemo.eventSeq }.startMillis)
            val repeatedEntries = remaining.filter { it.eventSeq == repeatedActivity.eventSeq }
            assertEquals(
                listOf("2026-10-22T09:00", "2026-10-24T09:00", "2026-10-25T09:00",
                    "2026-10-26T09:00", "2026-10-27T09:00"),
                repeatedEntries.map { it.occurrenceKey }
            )

            val includingCompleted = api.queryEntries(source, now, includeCompleted = true).entries
            val completedEntry = includingCompleted.single { it.eventSeq == completedActivity.eventSeq }
            assertTrue(completedEntry.completed)
            assertEquals("single", completedEntry.occurrenceKey)
            val completedRepeatedEntry = includingCompleted.single {
                it.eventSeq == repeatedActivity.eventSeq && it.occurrenceKey == "2026-10-23T09:00"
            }
            assertTrue(completedRepeatedEntry.completed)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun completionAndCurrentVisibilityQuerySurviveRestartAndRespectCompletionFilter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "todo-${UUID.randomUUID()}.db"
        var store = PowerStore(context, databaseName)
        try {
            val draft = store.newDraft(DraftForm(
                kind = TemporalKind.MEMO,
                title = "Synthetic completion projection",
                zoneId = zone.id
            ))
            assertTrue(store.commit(draft.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
            val original = requireNotNull(store.get(draft.eventSeq))
            var core = PowerCalCoreFeatureApiV1(
                store, reminderContext = context, settingsContext = context
            )
            assertEquals(1, core.memos.queryEntries(core.events.queryEvents(), now).entries.size)
            assertEquals("single", core.memos.queryCurrentEntry(
                original.eventSeq, "single", now
            )?.occurrenceKey)

            val completion = core.events.setOccurrenceCompletion(
                eventSeq = original.eventSeq,
                expectedRevision = original.revision,
                occurrenceKey = "single",
                expectedCompleted = false,
                completed = true,
                requestId = "memo-api-complete-01"
            )
            assertEquals(EventMutationStatusV1.UPDATED, completion.status)
            val completed = requireNotNull(store.get(original.eventSeq))
            assertEquals(TemporalKind.MEMO, completed.kind)
            assertNull(completed.anchorMillis())
            assertTrue(requireNotNull(core.memos.queryCurrentEntry(
                original.eventSeq, "single", now
            )).completed)
            assertNull(core.memos.queryCurrentEntry(
                original.eventSeq, "single", now, includeCompleted = false
            ))

            store.close()
            store = PowerStore(context, databaseName)
            core = PowerCalCoreFeatureApiV1(
                store, reminderContext = context, settingsContext = context
            )
            val reopened = requireNotNull(store.get(original.eventSeq))
            assertEquals(TemporalKind.MEMO, reopened.kind)
            assertTrue("single" in reopened.completedKeys)
            assertTrue(core.memos.queryEntries(core.events.queryEvents(), now).entries.isEmpty())
            val completedView = core.memos.queryEntries(
                core.events.queryEvents(), now, includeCompleted = true
            ).entries
            assertEquals(1, completedView.size)
            assertTrue(completedView.single().completed)
            assertEquals("single", completedView.single().occurrenceKey)
            assertEquals(reopened.revision, core.memos.queryCurrentEntry(
                reopened.eventSeq, "single", now, includeCompleted = true
            )?.revision)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
