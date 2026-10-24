package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun remainingProjectionIncludesTrackedIncompleteItemsAcrossKindsAndPreservesTimeSemantics() {
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
        val records = listOf(
            alarm, activity, dateDeadline, datedMemo, undatedMemo,
            completedActivity, disabled, notTracked
        )

        val remaining = TodoProjection.entries(records, now)

        assertEquals(listOf(3L, 1L, 2L, 4L, 5L), remaining.map { it.owner.eventSeq })
        assertEquals(
            setOf(TemporalKind.ALARM, TemporalKind.ACTIVITY, TemporalKind.DEADLINE, TemporalKind.MEMO),
            remaining.map { it.owner.kind }.toSet()
        )
        remaining.forEach { entry ->
            assertEquals(records.single { it.eventSeq == entry.owner.eventSeq }, entry.owner)
            assertFalse(entry.completed)
        }

        val alarmEntry = remaining.single { it.owner.eventSeq == alarm.eventSeq }
        assertEquals(alarm.startMillis, alarmEntry.startMillis)
        val activityEntry = remaining.single { it.owner.eventSeq == activity.eventSeq }
        assertEquals(activity.startMillis, activityEntry.startMillis)
        assertEquals(activity.endMillis, activityEntry.owner.endMillis)
        val deadlineEntry = remaining.single { it.owner.eventSeq == dateDeadline.eventSeq }
        assertEquals(TimePrecision.DATE, deadlineEntry.owner.precision)
        assertEquals("2026-10-24", deadlineEntry.owner.deadlineDate)
        assertNull(deadlineEntry.owner.deadlineMillis)
        assertEquals(at("2026-10-24", "00:00"), deadlineEntry.startMillis)
        val datedMemoEntry = remaining.single { it.owner.eventSeq == datedMemo.eventSeq }
        assertEquals("2026-10-25", datedMemoEntry.owner.memoStartDate)
        assertEquals("2026-10-27", datedMemoEntry.owner.memoEndDate)
        assertEquals(at("2026-10-25", "00:00"), datedMemoEntry.startMillis)
        assertNull(remaining.single { it.owner.eventSeq == undatedMemo.eventSeq }.startMillis)

        val includingCompleted = TodoProjection.entries(records, now, includeCompleted = true)
        val completedEntry = includingCompleted.single { it.owner.eventSeq == completedActivity.eventSeq }
        assertTrue(completedEntry.completed)
        assertEquals(completedActivity, completedEntry.owner)
    }

    @Test
    fun completionToggleUpdatesRemainingProjectionAcrossRestart() {
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
            assertEquals(TemporalKind.MEMO, original.kind)
            assertEquals(1, TodoProjection.entries(store.all(), now).size)

            assertTrue(store.setCompleted(
                original.eventSeq, "single", completed = true, expectedRevision = original.revision
            ))
            val completed = requireNotNull(store.get(original.eventSeq))
            assertEquals(TemporalKind.MEMO, completed.kind)
            assertNull(completed.anchorMillis())

            store.close()
            store = PowerStore(context, databaseName)
            val reopened = requireNotNull(store.get(original.eventSeq))
            assertEquals(TemporalKind.MEMO, reopened.kind)
            assertTrue("single" in reopened.completedKeys)
            assertTrue(TodoProjection.entries(store.all(), now).isEmpty())
            val completedView = TodoProjection.entries(store.all(), now, includeCompleted = true)
            assertEquals(1, completedView.size)
            assertTrue(completedView.single().completed)
            assertEquals("single", completedView.single().occurrenceKey)

            assertTrue(store.setCompleted(
                reopened.eventSeq, "single", completed = false, expectedRevision = reopened.revision
            ))
            assertEquals(1, TodoProjection.entries(store.all(), now).size)
            assertNotNull(store.get(original.eventSeq))
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

}
