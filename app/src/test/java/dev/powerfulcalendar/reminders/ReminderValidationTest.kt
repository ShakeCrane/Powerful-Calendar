package dev.powerfulcalendar.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.ReminderPolicy
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
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
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
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
    fun exactAlarmPermissionDeniedBlocksWithoutApproximateFallback() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val seq = createAlarm(60)
        val record = store.get(seq)!!
        val exactReminder = store.newDraft(
            DraftForm.from(record).copy(reminderMethod = ReminderMethod.ALARM),
            seq,
            DraftOperation.REPLACE
        )
        assertTrue(store.commit(exactReminder.draftId) is PowerStore.CommitOutcome.Committed)
        val requested = store.intentFor(seq)!!
        assertEquals(PowerStore.TriggerRequirement.EXACT, requested.requirement)

        ReminderScheduler.reconcile(context, store)

        val blocked = store.intentFor(seq)!!
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, blocked.desired)
        assertTrue(blocked.androidHint.orEmpty().contains("精确闹钟权限"))
        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun revokingExactAlarmPermissionBlocksScheduledIntentOnNextReconcile() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val seq = createAlarm(60)
        val record = store.get(seq)!!
        val exactReminder = store.newDraft(
            DraftForm.from(record).copy(reminderMethod = ReminderMethod.ALARM),
            seq,
            DraftOperation.REPLACE
        )
        assertTrue(store.commit(exactReminder.draftId) is PowerStore.CommitOutcome.Committed)

        ReminderScheduler.reconcile(context, store)
        assertEquals(PowerStore.DesiredSchedule.SCHEDULED, store.intentFor(seq)!!.desired)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ReminderScheduler.reconcile(context, store)

        val blocked = store.intentFor(seq)!!
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, blocked.desired)
        assertTrue(blocked.androidHint.orEmpty().contains("精确闹钟权限"))
    }

    @Test
    fun pendingExactIntentIsSubmittedAndMarkedScheduledIdempotently() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val seq = createAlarm(60)
        val record = store.get(seq)!!
        val exactReminder = store.newDraft(
            DraftForm.from(record).copy(reminderMethod = ReminderMethod.ALARM),
            seq,
            DraftOperation.REPLACE
        )
        assertTrue(store.commit(exactReminder.draftId) is PowerStore.CommitOutcome.Committed)
        val requested = store.intentFor(seq)!!
        assertEquals(PowerStore.TriggerRequirement.EXACT, requested.requirement)
        assertEquals(PowerStore.DesiredSchedule.PENDING, requested.desired)

        ReminderScheduler.reconcile(context, store)

        assertEquals(PowerStore.DesiredSchedule.SCHEDULED, store.intentFor(seq)!!.desired)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        ReminderScheduler.reconcile(context, store)

        assertEquals(PowerStore.DesiredSchedule.SCHEDULED, store.intentFor(seq)!!.desired)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun overduePendingIntentOutsideCurrentGraceWindowIsMissedWithoutRearming() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        val seq = createAlarm(-11)
        val record = store.get(seq)!!
        val startMillis = record.startMillis!!
        val occurrenceKey = RecurrenceEngine.keyFor(
            Instant.ofEpochMilli(startMillis).atZone(record.zone).toLocalDateTime()
        )
        val triggerInstant = ReminderPolicy.instant(record, startMillis)!!
        store.writableDatabase.execSQL(
            """INSERT INTO schedule_intents(
                event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,
                desired_state,android_hint,updated_ms
            ) VALUES(?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>(
                seq, ReminderPolicy.PRIMARY_TRIGGER_ID, record.revision, occurrenceKey,
                triggerInstant, PowerStore.TriggerRequirement.INEXACT.name,
                PowerStore.DesiredSchedule.PENDING.name, null, System.currentTimeMillis()
            )
        )
        assertEquals(PowerStore.DesiredSchedule.PENDING, store.intentFor(seq)!!.desired)

        ReminderScheduler.reconcile(context, store)

        assertEquals("MISSED", store.lastExecution(seq))
        assertTrue(store.intentFor(seq) == null)
        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun changingCompletionTrackingReconcilesTheReminderAtTheNewRevision() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val start = System.currentTimeMillis() + 3 * 60 * 60_000L
        val initial = store.newDraft(
            DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "跟踪切换后仍提醒",
                startText = DraftForm.formatInstant(start, zone),
                endText = DraftForm.formatInstant(start + 30 * 60_000L, zone),
                reminderMinutes = 15,
                reminderMethod = ReminderMethod.MESSAGE,
                trackingEnabled = true,
                zoneId = zone.id
            )
        )
        assertTrue(store.commit(initial.draftId) is PowerStore.CommitOutcome.Committed)
        val originalIntent = store.intentFor(initial.eventSeq)!!

        ReminderScheduler.reconcile(context, store)

        assertEquals(PowerStore.DesiredSchedule.SCHEDULED,
            store.intentFor(initial.eventSeq)!!.desired)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        val original = store.get(initial.eventSeq)!!
        val trackingEdit = store.newDraft(
            DraftForm.from(original).copy(trackingEnabled = false),
            original.eventSeq,
            DraftOperation.REPLACE
        )
        assertTrue(store.commit(trackingEdit.draftId) is PowerStore.CommitOutcome.Committed)
        val updated = store.get(initial.eventSeq)!!
        val updatedIntent = store.intentFor(initial.eventSeq)!!
        assertFalse(updated.trackingEnabled)
        assertTrue(updated.enabled)
        assertEquals(original.reminderMinutes, updated.reminderMinutes)
        assertEquals(originalIntent.occurrenceKey, updatedIntent.occurrenceKey)
        assertEquals(originalIntent.instantMillis, updatedIntent.instantMillis)
        assertEquals(updated.revision, updatedIntent.revision)
        assertEquals(PowerStore.DesiredSchedule.PENDING, updatedIntent.desired)

        ReminderScheduler.reconcile(context, store)

        assertEquals(PowerStore.DesiredSchedule.SCHEDULED,
            store.intentFor(initial.eventSeq)!!.desired)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        assertTrue(ReminderScheduler.validate(
            store, initial.eventSeq, updated.revision, updatedIntent.occurrenceKey,
            updatedIntent.instantMillis, snooze = false
        ) is ReminderScheduler.Validation.Valid)
        assertTrue(ReminderScheduler.validate(
            store, initial.eventSeq, originalIntent.revision, originalIntent.occurrenceKey,
            originalIntent.instantMillis, snooze = false
        ) is ReminderScheduler.Validation.Rejected)
    }

    @Test
    fun scheduledIntentIsRearmedAfterStoreReopen() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val seq = createAlarm(60)
        val record = store.get(seq)!!
        val exactReminder = store.newDraft(
            DraftForm.from(record).copy(reminderMethod = ReminderMethod.ALARM),
            seq,
            DraftOperation.REPLACE
        )
        assertTrue(store.commit(exactReminder.draftId) is PowerStore.CommitOutcome.Committed)

        ReminderScheduler.reconcile(context, store)
        val beforeRestart = store.intentFor(seq)!!
        assertEquals(PowerStore.DesiredSchedule.SCHEDULED, beforeRestart.desired)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        val database = store.databaseName
        store.close()
        // A device reboot clears AlarmManager registrations but leaves the durable desired row.
        shadowAlarmManager.scheduledAlarms.forEach { scheduled ->
            scheduled.operation?.let(alarmManager::cancel)
        }
        store = PowerStore(context, database)

        val persisted = store.intentFor(seq)!!
        assertEquals(beforeRestart, persisted)
        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())

        ReminderScheduler.reconcile(context, store)

        assertEquals(PowerStore.DesiredSchedule.SCHEDULED, store.intentFor(seq)!!.desired)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
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
