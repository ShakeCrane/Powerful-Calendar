package dev.powerfulcalendar.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/** Runs in the product process after AlarmManager delivers the isolated probe broadcast. */
private class ReminderAlarmDeliveryProbeReceiver(
    private val isolatedContext: Context,
    private val targetContext: Context,
    private val resultFile: File
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val deliveryIntent = Intent(intent).apply { action = AlarmContract.ACTION_FIRE }
                ReminderReceiver().handle(isolatedContext, deliveryIntent)
                val eventSeq = deliveryIntent.getLongExtra(AlarmContract.EXTRA_EVENT_SEQ, -1L)
                val store = PowerStore(isolatedContext)
                val execution = try {
                    store.lastExecution(eventSeq) ?: "NO_EXECUTION"
                } finally {
                    store.close()
                }
                val notificationId = NotificationHelper.notificationId(eventSeq)
                val notificationPosted = targetContext.getSystemService(NotificationManager::class.java)
                    ?.activeNotifications
                    ?.any { it.id == notificationId } == true
                resultFile.writeText("execution=$execution;notification=$notificationPosted")
            } catch (failure: Throwable) {
                runCatching {
                    resultFile.writeText("error=${failure.javaClass.simpleName}:${failure.message.orEmpty()}")
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

@RunWith(AndroidJUnit4::class)
class ReminderAlarmManagerDeliveryInstrumentedTest {
    @Test
    fun api35AlarmManagerDeliveryReachesReceiverAndRecordsFiredInIsolatedStore() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val alarmManager = targetContext.getSystemService(AlarmManager::class.java)!!
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            targetContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        )

        val databaseName = "pc-issue-170-${UUID.randomUUID()}.db"
        val resultName = "pc-issue-170-${UUID.randomUUID()}.result"
        val directoryName = "pc-issue-170-${UUID.randomUUID()}"
        val runDirectory = File(targetContext.cacheDir, directoryName)
        assertTrue(
            "Could not create isolated probe directory $runDirectory",
            runDirectory.mkdirs() || runDirectory.isDirectory
        )
        val resultFile = File(runDirectory, resultName)
        val notificationManager = targetContext.getSystemService(NotificationManager::class.java)!!
        val occupiedNotificationIds = notificationManager.activeNotifications.map { it.id }.toSet()
        val isolated = IsolatedReminderDatabaseContext(
            targetContext,
            targetContext,
            databaseName,
            directoryName
        )
        val probeAction = "dev.powerfulcalendar.pc_issue_170.${UUID.randomUUID()}"
        val probeReceiver = ReminderAlarmDeliveryProbeReceiver(isolated, targetContext, resultFile)
        var receiverRegistered = false
        var store: PowerStore? = null
        var pendingIntent: PendingIntent? = null
        var eventSeq = 0L
        var passed = false

        try {
            val activeStore = PowerStore(isolated)
            store = activeStore
            ContextCompat.registerReceiver(
                targetContext,
                probeReceiver,
                IntentFilter(probeAction),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            var candidate = Random.nextLong(1_000_000L, 999_000_000L)
            while (NotificationHelper.notificationId(candidate) in occupiedNotificationIds) {
                candidate = Random.nextLong(1_000_000L, 999_000_000L)
            }
            activeStore.writableDatabase.execSQL(
                "UPDATE meta SET next_seq=? WHERE id=1",
                arrayOf(candidate)
            )

            val zone = ZoneId.systemDefault()
            val nextMinute = (System.currentTimeMillis() / 60_000L + 1L) * 60_000L
            val draft = activeStore.newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "PC-ISSUE-170 synthetic alarm ${UUID.randomUUID()}",
                    startText = DraftForm.formatInstant(nextMinute, zone),
                    reminderMinutes = 0,
                    reminderMethod = ReminderMethod.MESSAGE,
                    trackingEnabled = true,
                    zoneId = zone.id
                )
            )
            eventSeq = draft.eventSeq
            assertEquals(candidate, eventSeq)
            assertTrue(
                activeStore.commit(draft.draftId, actor = "instrumentation") is
                    PowerStore.CommitOutcome.Committed
            )

            val record = activeStore.get(eventSeq)!!
            val intent = activeStore.intentFor(eventSeq)!!
            assertTrue(
                activeStore.markIntent(
                    eventSeq,
                    intent.triggerId,
                    PowerStore.DesiredSchedule.SCHEDULED,
                    null,
                    intent.revision,
                    intent.occurrenceKey
                )
            )
            val alarmIntent = Intent(probeAction).setPackage(targetContext.packageName).apply {
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, eventSeq)
                putExtra(AlarmContract.EXTRA_REVISION, record.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, intent.occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, intent.triggerId)
                putExtra(AlarmContract.EXTRA_INSTANT, intent.instantMillis)
                putExtra(AlarmContract.EXTRA_SNOOZE, false)
            }
            val requestCode = (eventSeq % Int.MAX_VALUE).toInt()
            pendingIntent = PendingIntent.getBroadcast(
                targetContext,
                requestCode,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            resultFile.delete()
            activeStore.close()

            val scheduledAt = System.currentTimeMillis()
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                intent.instantMillis,
                pendingIntent
            )

            val timeoutAt = SystemClock.elapsedRealtime() + 180_000L
            while (!resultFile.exists() && SystemClock.elapsedRealtime() < timeoutAt) {
                SystemClock.sleep(100L)
            }
            assertTrue("No AlarmManager receiver result before timeout", resultFile.exists())
            val receiverResult = resultFile.readText()
            assertEquals("execution=FIRED;notification=true", receiverResult)

            val reopened = PowerStore(isolated)
            try {
                assertEquals("FIRED", reopened.lastExecution(eventSeq))
                assertTrue(reopened.get(eventSeq)!!.completedKeys.isEmpty())
                assertTrue(
                    notificationManager.activeNotifications.any {
                        it.id == NotificationHelper.notificationId(eventSeq)
                    }
                )
            } finally {
                reopened.close()
            }
            println(
                "ALARM_MANAGER_DELIVERY api=${android.os.Build.VERSION.SDK_INT} eventSeq=$eventSeq " +
                    "scheduledAt=$scheduledAt triggerAt=${intent.instantMillis} " +
                    "receivedAt=${System.currentTimeMillis()} result=$receiverResult database=$databaseName"
            )
            passed = true
        } finally {
            if (receiverRegistered) targetContext.unregisterReceiver(probeReceiver)
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            if (eventSeq > 0L) {
                notificationManager.cancel(NotificationHelper.notificationId(eventSeq))
            }
            resultFile.delete()
            store?.close()
            isolated.deleteDatabase(PowerStore.DATABASE_NAME)
            runDirectory.delete()
            if (!passed) {
                if (resultFile.exists()) resultFile.delete()
            }
        }
    }
}

private class IsolatedReminderDatabaseContext(
    private val productContext: Context,
    private val databaseContext: Context,
    private val databaseName: String,
    private val directoryName: String
) : ContextWrapper(productContext) {
    private val isolatedDatabaseFile: File
        get() = File(File(databaseContext.cacheDir, directoryName), databaseName)

    override fun getApplicationContext(): Context = this

    override fun getDatabasePath(name: String): File = isolatedDatabaseFile

    override fun deleteDatabase(name: String): Boolean {
        val database = isolatedDatabaseFile
        val sidecars = listOf("-journal", "-shm", "-wal").map { suffix ->
            File(database.absolutePath + suffix)
        }
        return (sidecars + database).fold(true) { deleted, file ->
            (!file.exists() || file.delete()) && deleted
        }
    }

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?
    ): SQLiteDatabase = SQLiteDatabase.openDatabase(
        isolatedDatabaseFile.absolutePath,
        factory,
        SQLiteDatabase.CREATE_IF_NECESSARY
    )

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase = SQLiteDatabase.openDatabase(
        isolatedDatabaseFile.absolutePath,
        factory,
        SQLiteDatabase.CREATE_IF_NECESSARY,
        errorHandler
    )
}
