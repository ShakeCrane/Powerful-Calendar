package dev.powerfulcalendar.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderCompletionIsolationTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun oldRevisionBroadcastIsRejectedBeforeNotification() {
        val fixture = newFixture()
        val store = fixture.store
        var eventSeq = -1L
        try {
            eventSeq = createTrackedAlarm(store)
            val before = store.get(eventSeq)!!
            val oldIntent = store.intentFor(eventSeq)!!
            NotificationHelper.cancelReminder(fixture.context, eventSeq)

            val replacement = store.newDraft(
                DraftForm.from(before).copy(
                    startText = DraftForm.formatInstant(requireNotNull(before.startMillis) + 90 * 60_000L, zone)
                ),
                eventSeq,
                DraftOperation.REPLACE
            )
            assertTrue(store.commit(replacement.draftId) is PowerStore.CommitOutcome.Committed)
            val current = store.get(eventSeq)!!
            assertEquals(before.revision + 1, current.revision)

            val staleBroadcast = Intent(fixture.context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_FIRE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, eventSeq)
                putExtra(AlarmContract.EXTRA_REVISION, oldIntent.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, oldIntent.occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, oldIntent.triggerId)
                putExtra(AlarmContract.EXTRA_INSTANT, oldIntent.instantMillis)
            }
            ReminderReceiver().handle(fixture.context, staleBroadcast)

            assertEquals("IGNORED_STALE", store.lastExecution(eventSeq))
            val notificationManager = fixture.context.getSystemService(NotificationManager::class.java)!!
            assertTrue(
                notificationManager.activeNotifications.none {
                    it.id == NotificationHelper.notificationId(eventSeq)
                }
            )
            assertEquals(current.revision, store.get(eventSeq)!!.revision)
            assertEquals(current.title, store.get(eventSeq)!!.title)
        } finally {
            if (eventSeq > 0L) NotificationHelper.cancelReminder(fixture.context, eventSeq)
            closeFixture(fixture)
        }
    }

    @Test
    fun fireWithDifferentTriggerIdentityIsRejectedBeforeNotification() {
        val fixture = newFixture()
        val store = fixture.store
        try {
            val seq = createTrackedAlarm(store)
            val before = store.get(seq)!!
            val scheduled = store.intentFor(seq)!!
            val staleTrigger = Intent(fixture.context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_FIRE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, seq)
                putExtra(AlarmContract.EXTRA_REVISION, scheduled.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, scheduled.occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, "retired-trigger")
                putExtra(AlarmContract.EXTRA_INSTANT, scheduled.instantMillis)
            }

            ReminderReceiver().handle(fixture.context, staleTrigger)

            assertEquals("IGNORED_STALE", store.lastExecution(seq))
            assertTrue(
                fixture.context.getSystemService(NotificationManager::class.java)!!
                    .activeNotifications.none {
                        it.id == NotificationHelper.notificationId(seq)
                    }
            )
            assertEquals(before.revision, store.get(seq)!!.revision)
            assertEquals(before.title, store.get(seq)!!.title)
        } finally {
            closeFixture(fixture)
        }
    }

    @Test
    fun fireWithoutTriggerIdentityIsRejectedBeforeNotification() {
        val fixture = newFixture()
        val store = fixture.store
        try {
            val seq = createTrackedAlarm(store)
            val before = store.get(seq)!!
            val scheduled = store.intentFor(seq)!!
            val missingTrigger = Intent(fixture.context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_FIRE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, seq)
                putExtra(AlarmContract.EXTRA_REVISION, scheduled.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, scheduled.occurrenceKey)
                putExtra(AlarmContract.EXTRA_INSTANT, scheduled.instantMillis)
            }

            ReminderReceiver().handle(fixture.context, missingTrigger)

            assertEquals("IGNORED_STALE", store.lastExecution(seq))
            assertTrue(
                fixture.context.getSystemService(NotificationManager::class.java)!!
                    .activeNotifications.none {
                        it.id == NotificationHelper.notificationId(seq)
                    }
            )
            assertEquals(before.revision, store.get(seq)!!.revision)
            assertEquals(before.title, store.get(seq)!!.title)
        } finally {
            closeFixture(fixture)
        }
    }

    @Test
    fun firedAlarmRecordsExecutionWithoutCompletingTrackedEvent() {
        val fixture = newFixture()
        val store = fixture.store
        try {
            val seq = createTrackedAlarm(store)
            val record = store.get(seq)!!
            val scheduled = store.intentFor(seq)!!
            val fire = Intent(fixture.context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_FIRE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, seq)
                putExtra(AlarmContract.EXTRA_REVISION, scheduled.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, scheduled.occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, scheduled.triggerId)
                putExtra(AlarmContract.EXTRA_INSTANT, scheduled.instantMillis)
            }

            ReminderReceiver().handle(fixture.context, fire)

            assertEquals("FIRED", store.lastExecution(seq))
            assertNull(store.intentFor(seq))
            assertTrue(record.completedKeys.isEmpty())
            assertTrue(store.get(seq)!!.completedKeys.isEmpty())
            assertEquals(record.revision, store.get(seq)!!.revision)
        } finally {
            closeFixture(fixture)
        }
    }

    @Test
    fun snoozedRecurringOccurrenceFiresWithoutChangingItsSeries() {
        val fixture = newFixture()
        val store = fixture.store
        try {
            val start = (System.currentTimeMillis() / 60_000L) * 60_000L - 60_000L
            val card = store.newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "重复提醒稍后响",
                    startText = DraftForm.formatInstant(start, zone),
                    reminderMinutes = 0,
                    trackingEnabled = true,
                    zoneId = zone.id,
                    recurrence = Recurrence(freq = RecurrenceFreq.DAILY)
                )
            )
            assertTrue(store.commit(card.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
            val before = store.get(card.eventSeq)!!
            val occurrenceKey = RecurrenceEngine.keyFor(
                Instant.ofEpochMilli(start).atZone(zone).toLocalDateTime()
            )
            val windowStart = start - 60_000L
            val windowEnd = start + 3 * 24 * 60 * 60_000L
            val seriesBefore = RecurrenceEngine.occurrences(before, windowStart, windowEnd, limit = 10)
            assertTrue(seriesBefore.any { it.key == occurrenceKey })

            val snoozeAction = Intent(fixture.context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_SNOOZE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, card.eventSeq)
                putExtra(AlarmContract.EXTRA_REVISION, before.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, "primary")
                putExtra(AlarmContract.EXTRA_INSTANT, System.currentTimeMillis())
            }
            ReminderReceiver().handle(fixture.context, snoozeAction)

            // ReminderScheduler.scheduleSnooze emits FIRE + EXTRA_SNOOZE=true for the delayed alarm.
            val delayedSnooze = Intent(fixture.context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_FIRE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, card.eventSeq)
                putExtra(AlarmContract.EXTRA_REVISION, before.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, "primary")
                putExtra(AlarmContract.EXTRA_INSTANT, System.currentTimeMillis() - 1_000L)
                putExtra(AlarmContract.EXTRA_SNOOZE, true)
            }
            ReminderReceiver().handle(fixture.context, delayedSnooze)

            val after = store.get(card.eventSeq)!!
            assertEquals(before.revision, after.revision)
            assertEquals(before.recurrence, after.recurrence)
            assertEquals(before.completedKeys, after.completedKeys)
            assertEquals(
                seriesBefore,
                RecurrenceEngine.occurrences(after, windowStart, windowEnd, limit = 10)
            )
            assertEquals("FIRED", store.lastExecution(card.eventSeq))
            val posted = fixture.context.getSystemService(NotificationManager::class.java)!!
                .activeNotifications.single { it.id == NotificationHelper.notificationId(card.eventSeq) }
                .notification
            assertTrue(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("稍后提醒"))
        } finally {
            closeFixture(fixture)
        }
    }

    @Test
    fun autoCancelledReminderHasNoCompletionDeleteCallback() {
        val fixture = newFixture()
        val store = fixture.store
        try {
            val seq = createTrackedAlarm(store)
            val record = store.get(seq)!!
            NotificationHelper.postReminder(
                fixture.context,
                record,
                store.intentFor(seq)!!.occurrenceKey,
                "primary",
                snoozed = false
            )

            val notificationId = NotificationHelper.notificationId(seq)
            val notificationManager = fixture.context.getSystemService(NotificationManager::class.java)!!
            val posted = notificationManager.activeNotifications
                .single { it.id == notificationId }
                .notification

            assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
            assertNull(posted.deleteIntent)
            notificationManager.cancel(notificationId)
            assertTrue(notificationManager.activeNotifications.none { it.id == notificationId })
            assertTrue(store.get(seq)!!.completedKeys.isEmpty())
            assertEquals(record.revision, store.get(seq)!!.revision)
        } finally {
            closeFixture(fixture)
        }
    }

    private fun createTrackedAlarm(store: PowerStore): Long {
        val start = System.currentTimeMillis() + 60 * 60_000L
        val card = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "提醒状态隔离测试",
                startText = DraftForm.formatInstant(start, zone),
                reminderMinutes = 0,
                trackingEnabled = true,
                zoneId = zone.id
            )
        )
        assertTrue(store.commit(card.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
        return card.eventSeq
    }

    private fun newFixture(): Fixture {
        val application = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(application as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val testDatabase = "reminder-completion-${UUID.randomUUID()}.db"
        application.deleteDatabase(testDatabase)
        val context = IsolatedDatabaseContext(application, testDatabase)
        return Fixture(context, PowerStore(context))
    }

    private fun closeFixture(fixture: Fixture) {
        fixture.store.close()
        fixture.context.deleteDatabase(PowerStore.DATABASE_NAME)
    }

    private data class Fixture(val context: Context, val store: PowerStore)

    /** Redirects the receiver's default PowerStore database name to a disposable test database. */
    private class IsolatedDatabaseContext(
        private val delegate: Context,
        private val databaseName: String
    ) : ContextWrapper(delegate) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File = delegate.getDatabasePath(databaseName)

        override fun deleteDatabase(name: String): Boolean = delegate.deleteDatabase(databaseName)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase = delegate.openOrCreateDatabase(databaseName, mode, factory)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase = delegate.openOrCreateDatabase(databaseName, mode, factory, errorHandler)
    }
}
