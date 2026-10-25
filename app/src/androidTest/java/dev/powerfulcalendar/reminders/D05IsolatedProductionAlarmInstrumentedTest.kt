package dev.powerfulcalendar.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.util.UUID

/**
 * Opt-in production-path AVD test. The dedicated d05qa application id gives it a separate
 * package, private database, alarm namespace, notifications and runtime permissions.
 */
@RunWith(AndroidJUnit4::class)
class D05IsolatedProductionAlarmInstrumentedTest {
    @Test
    fun armProductionExactAlarmForExternalProcessKillAndDeliveryCheck() {
        val context = qaContext()
        val marker = File(context.filesDir, ARMED_MARKER)
        assertTrue("Unexpected stale QA marker", !marker.exists())

        val store = PowerStore(context)
        var eventSeq = 0L
        var emptyDatabaseConfirmed = false
        var armed = false
        try {
            assertTrue("QA package starts without formal events", store.all().isEmpty())
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(0L, store.identityCount())
            assertEquals(0L, store.commitCount())
            assertEquals(1L, store.nextSequencePreview())
            assertEquals(1L, store.nextQueueOrderPreview())
            emptyDatabaseConfirmed = true

            assertEquals(
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            )
            val alarmManager = context.getSystemService(AlarmManager::class.java)!!
            assertTrue("QA package needs exact-alarm access", alarmManager.canScheduleExactAlarms())
            ReminderScheduler.ensureChannels(context)

            val zone = ZoneId.systemDefault()
            val triggerAt = (System.currentTimeMillis() / 60_000L + 3L) * 60_000L
            val title = "PC-D05-QA-${UUID.randomUUID()}"
            val draft = store.newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = title,
                    startText = DraftForm.formatInstant(triggerAt, zone),
                    reminderMinutes = 0,
                    reminderMethod = ReminderMethod.ALARM,
                    trackingEnabled = true,
                    zoneId = zone.id
                )
            )
            eventSeq = draft.eventSeq
            val committed = store.commit(draft.draftId, actor = "d05-qa")
            assertTrue("Synthetic alarm must commit in the isolated QA store: $committed",
                committed is PowerStore.CommitOutcome.Committed)

            ReminderScheduler.reconcile(context, store)
            val scheduled = requireNotNull(store.intentFor(eventSeq))
            assertEquals(PowerStore.DesiredSchedule.SCHEDULED, scheduled.desired)
            assertEquals(PowerStore.TriggerRequirement.EXACT, scheduled.requirement)
            assertEquals(triggerAt, scheduled.instantMillis)
            assertTrue(scheduled.instantMillis > System.currentTimeMillis() + 60_000L)

            marker.writeText(
                "eventSeq=$eventSeq\nrevision=${scheduled.revision}\n" +
                    "triggerAt=${scheduled.instantMillis}\ntitle=$title\n"
            )
            println(
                "D05_QA_ARMED package=${context.packageName} eventSeq=$eventSeq " +
                    "revision=${scheduled.revision} triggerAt=${scheduled.instantMillis} " +
                    "desired=${scheduled.desired} requirement=${scheduled.requirement}"
            )
            armed = true
        } finally {
            store.close()
            if (!armed && emptyDatabaseConfirmed) {
                if (eventSeq > 0L) {
                    ReminderScheduler.cancel(context, eventSeq)
                    NotificationHelper.cancelReminder(context, eventSeq)
                }
                marker.delete()
                context.deleteDatabase(PowerStore.DATABASE_NAME)
            }
        }
    }

    @Test
    fun productionManifestReceiverFiresAndPostsAfterHostKillsAppProcess() {
        val context = qaContext()
        val marker = File(context.filesDir, ARMED_MARKER)
        assertTrue("Arm phase marker must survive app-process death", marker.isFile)
        val values = marker.readLines().mapNotNull { line ->
            val split = line.indexOf('=')
            if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
        }.toMap()
        val eventSeq = requireNotNull(values["eventSeq"]?.toLongOrNull())
        val triggerAt = requireNotNull(values["triggerAt"]?.toLongOrNull())
        val title = requireNotNull(values["title"])

        val store = PowerStore(context)
        var passed = false
        try {
            val record = requireNotNull(store.get(eventSeq))
            assertEquals(title, record.title)
            assertEquals(TemporalKind.ALARM, record.kind)
            assertTrue("Host must wait until after the scheduled instant",
                System.currentTimeMillis() >= triggerAt)
            assertEquals("FIRED", store.lastExecution(eventSeq))
            assertTrue("Reminder does not imply task completion", record.completedKeys.isEmpty())

            val notifications = context.getSystemService(NotificationManager::class.java)!!
                .activeNotifications
            val posted = notifications.firstOrNull {
                it.id == NotificationHelper.notificationId(eventSeq)
            }
            assertNotNull("Production receiver should post the reminder notification", posted)
            assertEquals(AlarmContract.CHANNEL_ALARMS, posted!!.notification.channelId)
            println(
                "D05_QA_DELIVERED package=${context.packageName} eventSeq=$eventSeq " +
                    "triggerAt=$triggerAt execution=${store.lastExecution(eventSeq)} " +
                    "notificationId=${posted.id} channel=${posted.notification.channelId} " +
                    "completedKeys=${record.completedKeys.size}"
            )
            passed = true
        } finally {
            store.close()
            if (passed) {
                ReminderScheduler.cancel(context, eventSeq)
                NotificationHelper.cancelReminder(context, eventSeq)
                marker.delete()
                assertTrue("Delete only this unique QA package database",
                    context.deleteDatabase(PowerStore.DATABASE_NAME))
                assertTrue(!context.getDatabasePath(PowerStore.DATABASE_NAME).exists())
            }
        }
    }

    private fun qaContext(): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "Requires the isolated package built with -Ppowercal.d05QaSuffix",
            context.packageName == QA_APPLICATION_ID
        )
        return context
    }

    private companion object {
        const val QA_APPLICATION_ID = "dev.powerfulcalendar.d05qa572cf10"
        const val ARMED_MARKER = "pc-d05-production-qa-armed.txt"
    }
}
