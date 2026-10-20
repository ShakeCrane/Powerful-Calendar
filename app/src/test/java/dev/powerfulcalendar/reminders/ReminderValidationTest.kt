package dev.powerfulcalendar.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.UUID

/**
 * I-11: a changed revision fences the old alarm before any ringtone is activated. These tests
 * exercise the exact predicate the receiver uses before it decides to notify.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderValidationTest {

    private lateinit var context: Context
    private lateinit var store: PowerStore
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "test-" + UUID.randomUUID() + ".db")
    }

    @After
    fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    private fun createAlarm(minutesAhead: Long): Long {
        val start = System.currentTimeMillis() + minutesAhead * 60_000L
        val card = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "起床",
                startText = DraftForm.formatInstant(start, zone),
                reminderMinutes = 0,
                zoneId = zone.id
            )
        )
        val outcome = store.commit(card.draftId) as PowerStore.CommitOutcome.Committed
        assertEquals(1L, outcome.revision)
        return card.eventSeq
    }

    @Test
    fun theScheduledOccurrenceOfTheCurrentRevisionIsValid() {
        val seq = createAlarm(60)
        val intent = store.intentFor(seq)!!
        val validation = ReminderScheduler.validate(
            store, seq, intent.revision, intent.occurrenceKey, intent.instantMillis, snooze = false
        )
        assertTrue(validation is ReminderScheduler.Validation.Valid)
    }

    @Test
    fun aNewerRevisionFencesTheOldBroadcast() {
        val seq = createAlarm(60)
        val oldIntent = store.intentFor(seq)!!

        val edit = store.newDraft(
            DraftForm.from(store.get(seq)!!).copy(
                startText = DraftForm.formatInstant(System.currentTimeMillis() + 90 * 60_000L, zone)
            ),
            seq,
            dev.powerfulcalendar.core.DraftOperation.REPLACE
        )
        assertTrue(store.commit(edit.draftId) is PowerStore.CommitOutcome.Committed)
        assertEquals(2L, store.get(seq)!!.revision)

        val stale = ReminderScheduler.validate(
            store, seq, oldIntent.revision, oldIntent.occurrenceKey, oldIntent.instantMillis, snooze = false
        )
        assertTrue(stale is ReminderScheduler.Validation.Rejected)
        assertTrue((stale as ReminderScheduler.Validation.Rejected).reason.contains("版本"))
    }

    @Test
    fun aWrongOccurrenceKeyIsRejected() {
        val seq = createAlarm(60)
        val intent = store.intentFor(seq)!!
        val wrong = ReminderScheduler.validate(
            store, seq, intent.revision, "1999-01-01T00:00", intent.instantMillis, snooze = false
        )
        assertTrue(wrong is ReminderScheduler.Validation.Rejected)
    }

    @Test
    fun longActivityReminderValidationFindsItsExactOccurrenceKey() {
        val now = System.currentTimeMillis()
        val start = now - 1_000L * 24 * 60 * 60 * 1_000
        val card = store.newDraft(
            DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "跨期活动",
                startText = DraftForm.formatInstant(start, zone),
                endText = DraftForm.formatInstant(start + 600L * 24 * 60 * 60 * 1_000, zone),
                reminderMinutes = 0,
                zoneId = zone.id,
                recurrence = dev.powerfulcalendar.core.Recurrence(
                    freq = dev.powerfulcalendar.core.RecurrenceFreq.DAILY
                )
            )
        )
        store.commit(card.draftId)
        val intent = store.intentFor(card.eventSeq)!!

        val validation = ReminderScheduler.validate(
            store, card.eventSeq, intent.revision, intent.occurrenceKey, intent.instantMillis, snooze = false
        )

        assertTrue(validation is ReminderScheduler.Validation.Valid)
    }

    @Test
    fun aChangedTriggerInstantIsRejected() {
        val seq = createAlarm(60)
        val intent = store.intentFor(seq)!!
        val wrong = ReminderScheduler.validate(
            store, seq, intent.revision, intent.occurrenceKey,
            intent.instantMillis + 3_600_000L, snooze = false
        )
        assertTrue(wrong is ReminderScheduler.Validation.Rejected)
        assertTrue((wrong as ReminderScheduler.Validation.Rejected).reason.contains("触发时刻"))
    }

    @Test
    fun snoozeValidatesIdentityButNotTheExactInstant() {
        val seq = createAlarm(60)
        val intent = store.intentFor(seq)!!
        val snoozed = ReminderScheduler.validate(
            store, seq, intent.revision, intent.occurrenceKey,
            System.currentTimeMillis(), snooze = true
        )
        assertTrue(snoozed is ReminderScheduler.Validation.Valid)

        // A stale revision still cannot snooze.
        val staleSnooze = ReminderScheduler.validate(
            store, seq, intent.revision - 1, intent.occurrenceKey,
            System.currentTimeMillis(), snooze = true
        )
        assertTrue(staleSnooze is ReminderScheduler.Validation.Rejected)
    }

    @Test
    fun aDeletedEventCannotRing() {
        val seq = createAlarm(60)
        val intent = store.intentFor(seq)!!
        val delete = store.newDraft(
            DraftForm.from(store.get(seq)!!), seq, dev.powerfulcalendar.core.DraftOperation.DELETE
        )
        store.commit(delete.draftId)
        val validation = ReminderScheduler.validate(
            store, seq, intent.revision, intent.occurrenceKey, intent.instantMillis, snooze = false
        )
        assertTrue(validation is ReminderScheduler.Validation.Rejected)
    }

    @Test
    fun aDisabledEventCannotRing() {
        val seq = createAlarm(60)
        val intent = store.intentFor(seq)!!
        val edit = store.newDraft(
            DraftForm.from(store.get(seq)!!).copy(enabled = false), seq,
            dev.powerfulcalendar.core.DraftOperation.REPLACE
        )
        store.commit(edit.draftId)
        val validation = ReminderScheduler.validate(
            store, seq, 2L, intent.occurrenceKey, intent.instantMillis, snooze = false
        )
        assertTrue(validation is ReminderScheduler.Validation.Rejected)
    }

    @Test
    fun triggerOffsetRespectsReminderMinutes() {
        val seq = createAlarm(60)
        val record = store.get(seq)!!
        assertEquals(0L, ReminderScheduler.triggerOffsetMillis(record))
        assertEquals(600_000L, ReminderScheduler.triggerOffsetMillis(record.copy(reminderMinutes = 10)))
    }

    @Test
    fun firedIntentAdvancesToTheNextOccurrenceWithoutTouchingTheSnapshot() {
        val start = System.currentTimeMillis() - 60_000L
        val card = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "每日",
                startText = DraftForm.formatInstant(start, zone),
                reminderMinutes = 0,
                zoneId = zone.id,
                recurrence = dev.powerfulcalendar.core.Recurrence(
                    freq = dev.powerfulcalendar.core.RecurrenceFreq.DAILY
                )
            )
        )
        val committed = store.commit(card.draftId) as PowerStore.CommitOutcome.Committed
        val record = store.get(card.eventSeq)!!
        // A recurring alarm whose first slot already passed should immediately offer the next one.
        val next = dev.powerfulcalendar.core.RecurrenceEngine.nextOccurrence(
            record, System.currentTimeMillis() + 1_000
        )
        assertTrue(next != null)
        store.advanceIntentAfterFiring(card.eventSeq, "nonexistent")
        val intent = store.intentFor(card.eventSeq)!!
        assertEquals(PowerStore.DesiredSchedule.PENDING, intent.desired)
        assertEquals(record.revision, store.get(card.eventSeq)!!.revision)
        assertEquals(committed.revision, intent.revision)
    }
}
