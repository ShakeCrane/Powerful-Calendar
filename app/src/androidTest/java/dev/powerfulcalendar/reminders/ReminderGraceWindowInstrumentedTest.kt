package dev.powerfulcalendar.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.ReminderPolicy
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderGraceWindowInstrumentedTest {
    @Test
    fun api35ReceiverFiresWithinTenMinutesAndMissesAfterward() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val databaseName = "pc-issue-105-${UUID.randomUUID()}.db"
        val context = IsolatedDatabaseContext(application, databaseName)
        val store = PowerStore(context)
        val notifications = context.getSystemService(NotificationManager::class.java)!!
        var passed = false

        try {
            assertEquals(
                "Host setup must grant POST_NOTIFICATIONS before instrumentation starts",
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            )
            val withinWindow = createAlarm(store, lateByMinutes = 9)
            dispatchFire(context, store, withinWindow)

            // The receiver reconciles all overdue intents after each delivery. Create the second
            // overdue case only after the first delivery so it cannot be swept as MISSED first.
            val outsideWindow = createAlarm(store, lateByMinutes = 11)
            dispatchFire(context, store, outsideWindow)

            assertEquals("FIRED", store.lastExecution(withinWindow))
            assertEquals("MISSED", store.lastExecution(outsideWindow))
            assertTrue(
                notifications.activeNotifications.any {
                    it.id == NotificationHelper.notificationId(withinWindow)
                }
            )
            assertTrue(
                notifications.activeNotifications.none {
                    it.id == NotificationHelper.notificationId(outsideWindow)
                }
            )
            println(
                "REMINDER_GRACE api=${android.os.Build.VERSION.SDK_INT} " +
                    "within=FIRED lateMinutes=9 outside=MISSED lateMinutes=11 " +
                    "notificationWithin=true notificationOutside=false isolatedDb=$databaseName"
            )
            passed = true
        } finally {
            store.close()
            if (passed) context.deleteDatabase(PowerStore.DATABASE_NAME)
        }
    }

    private fun createAlarm(store: PowerStore, lateByMinutes: Long): Long {
        val zone = ZoneId.systemDefault()
        val nowAtMinute = Math.floorDiv(System.currentTimeMillis(), 60_000L) * 60_000L
        val start = nowAtMinute - lateByMinutes * 60_000L
        val draft = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "PC-ISSUE-105 synthetic $lateByMinutes minute alarm",
                startText = DraftForm.formatInstant(start, zone),
                reminderMinutes = 0,
                trackingEnabled = true,
                zoneId = zone.id
            )
        )
        assertTrue(store.commit(draft.draftId, actor = "instrumentation") is PowerStore.CommitOutcome.Committed)

        val record = store.get(draft.eventSeq)!!
        val occurrenceKey = RecurrenceEngine.keyFor(
            Instant.ofEpochMilli(start).atZone(record.zone).toLocalDateTime()
        )
        val triggerInstant = ReminderPolicy.instant(record, start)!!
        store.writableDatabase.execSQL(
            """INSERT INTO schedule_intents(
                event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,
                desired_state,android_hint,updated_ms
            ) VALUES(?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>(
                draft.eventSeq,
                ReminderPolicy.PRIMARY_TRIGGER_ID,
                record.revision,
                occurrenceKey,
                triggerInstant,
                PowerStore.TriggerRequirement.INEXACT.name,
                PowerStore.DesiredSchedule.PENDING.name,
                null,
                System.currentTimeMillis()
            )
        )
        val storedIntent = store.intentFor(draft.eventSeq)
        assertNotNull(
            "Pending intent missing after insert: event=${draft.eventSeq}, " +
                "trigger=${ReminderPolicy.PRIMARY_TRIGGER_ID}, db=${store.readableDatabase.path}",
            storedIntent
        )
        return draft.eventSeq
    }

    private fun dispatchFire(context: Context, store: PowerStore, eventSeq: Long) {
        val intent = store.intentFor(eventSeq)!!
        ReminderReceiver().handle(
            context,
            Intent(context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_FIRE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, eventSeq)
                putExtra(AlarmContract.EXTRA_REVISION, intent.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, intent.occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, intent.triggerId)
                putExtra(AlarmContract.EXTRA_INSTANT, intent.instantMillis)
            }
        )
    }

    /** Routes the receiver's default PowerStore name to a unique disposable database. */
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
