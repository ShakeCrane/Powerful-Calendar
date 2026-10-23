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
import dev.powerfulcalendar.core.PowerStore
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
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderCompletionIsolationTest {
    private val zone = ZoneId.of("Asia/Shanghai")

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

            ReminderReceiver().onReceive(fixture.context, fire)
            await(timeoutMillis = 3_000) {
                store.lastExecution(seq) == "FIRED" && store.intentFor(seq) == null
            }

            assertEquals("FIRED", store.lastExecution(seq))
            assertTrue(record.completedKeys.isEmpty())
            assertTrue(store.get(seq)!!.completedKeys.isEmpty())
            assertEquals(record.revision, store.get(seq)!!.revision)
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

    private fun await(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(10L)
        }
        assertTrue("timed out waiting for reminder receiver", condition())
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
