package dev.powerfulcalendar.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.util.ReflectionHelpers
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootReceiverRecoveryTest {

    @Test
    fun bootBroadcastReconcilesDesiredAlarmAndFinishesAsyncPendingResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        var store: PowerStore? = PowerStore(context)
        val databaseName = requireNotNull(store).databaseName

        try {
            assertTrue(requireNotNull(store).all().isEmpty())
            val zone = ZoneId.systemDefault()
            val start = System.currentTimeMillis() + 60 * 60_000L
            val draft = requireNotNull(store).newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "Boot recovery",
                    startText = DraftForm.formatInstant(start, zone),
                    reminderMinutes = 0,
                    zoneId = zone.id
                )
            )
            val committed = requireNotNull(store).commit(draft.draftId)
            assertTrue(committed is PowerStore.CommitOutcome.Committed)
            val eventSeq = draft.eventSeq
            assertEquals(PowerStore.DesiredSchedule.PENDING, requireNotNull(store).intentFor(eventSeq)!!.desired)
            assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())

            requireNotNull(store).close()
            store = null

            val receiver = BootReceiver()
            val pendingResult = ReflectionHelpers.callConstructor(BroadcastReceiver.PendingResult::class.java)
            ReflectionHelpers.setField(receiver, "mPendingResult", pendingResult)
            val shadowReceiver = Shadows.shadowOf(receiver)
            shadowReceiver.onReceive(
                context,
                Intent(Intent.ACTION_BOOT_COMPLETED),
                AtomicBoolean(false)
            )

            assertTrue(shadowReceiver.wentAsync())
            Shadows.shadowOf(pendingResult).getFuture().get(10, TimeUnit.SECONDS)

            val reopened = PowerStore(context)
            try {
                val scheduled = reopened.intentFor(eventSeq)
                assertNotNull(scheduled)
                assertEquals(PowerStore.DesiredSchedule.SCHEDULED, scheduled!!.desired)
                assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
                assertFalse(reopened.isLocked())
            } finally {
                reopened.close()
            }
        } finally {
            store?.close()
            context.deleteDatabase(databaseName)
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
    }

    @Test
    fun recoveryBroadcastsReconcileDesiredAlarmAndFinishAsyncWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        var store: PowerStore? = PowerStore(context)
        val databaseName = requireNotNull(store).databaseName

        try {
            val zone = ZoneId.systemDefault()
            val start = System.currentTimeMillis() + 60 * 60_000L
            val draft = requireNotNull(store).newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "Recovery broadcast",
                    startText = DraftForm.formatInstant(start, zone),
                    reminderMinutes = 0,
                    zoneId = zone.id
                )
            )
            val committed = requireNotNull(store).commit(draft.draftId)
            assertTrue(committed is PowerStore.CommitOutcome.Committed)
            val eventSeq = draft.eventSeq
            assertEquals(PowerStore.DesiredSchedule.PENDING, requireNotNull(store).intentFor(eventSeq)!!.desired)
            requireNotNull(store).close()
            store = null

            val recoveryActions = listOf(
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
                AlarmContract.ACTION_RECONCILE
            )
            recoveryActions.forEachIndexed { index, action ->
                if (index > 0) {
                    shadowAlarmManager.scheduledAlarms.forEach { scheduled ->
                        scheduled.operation?.let(alarmManager::cancel)
                    }
                    assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
                }
                dispatchAndAwait(context, action)
                assertEquals("$action should re-arm the durable intent", 1, shadowAlarmManager.scheduledAlarms.size)
            }

            val reopened = PowerStore(context)
            try {
                val scheduled = reopened.intentFor(eventSeq)
                assertNotNull(scheduled)
                assertEquals(PowerStore.DesiredSchedule.SCHEDULED, scheduled!!.desired)
                assertEquals("Every supported recovery action should be idempotent", 1, shadowAlarmManager.scheduledAlarms.size)
                assertFalse(reopened.isLocked())
            } finally {
                reopened.close()
            }
        } finally {
            store?.close()
            context.deleteDatabase(databaseName)
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
    }

    @Test
    fun exactAlarmPermissionBroadcastRetriesBlockedExactReminder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val alarmManager = context.getSystemService(AlarmManager::class.java)!!
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        var store: PowerStore? = PowerStore(context)
        val databaseName = requireNotNull(store).databaseName

        try {
            assertTrue(requireNotNull(store).all().isEmpty())
            val zone = ZoneId.systemDefault()
            val draft = requireNotNull(store).newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "Exact alarm permission recovery",
                    startText = DraftForm.formatInstant(System.currentTimeMillis() + 60 * 60_000L, zone),
                    reminderMinutes = 0,
                    reminderMethod = ReminderMethod.ALARM,
                    zoneId = zone.id
                )
            )
            assertTrue(requireNotNull(store).commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
            val eventSeq = draft.eventSeq

            ReminderScheduler.reconcile(context, requireNotNull(store))
            assertEquals(PowerStore.DesiredSchedule.BLOCKED, requireNotNull(store).intentFor(eventSeq)!!.desired)
            assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
            requireNotNull(store).close()
            store = null

            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            dispatchAndAwait(
                context,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
            )

            val reopened = PowerStore(context)
            try {
                val recovered = reopened.intentFor(eventSeq)
                assertNotNull(recovered)
                assertEquals(PowerStore.DesiredSchedule.SCHEDULED, recovered!!.desired)
                assertEquals(null, recovered.androidHint)
                assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
            } finally {
                reopened.close()
            }
        } finally {
            store?.close()
            context.deleteDatabase(databaseName)
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
    }

    private fun dispatchAndAwait(context: Context, action: String) {
        val receiver = BootReceiver()
        val pendingResult = ReflectionHelpers.callConstructor(BroadcastReceiver.PendingResult::class.java)
        ReflectionHelpers.setField(receiver, "mPendingResult", pendingResult)
        val shadowReceiver = Shadows.shadowOf(receiver)

        shadowReceiver.onReceive(context, Intent(action), AtomicBoolean(false))

        assertTrue("$action should enter goAsync()", shadowReceiver.wentAsync())
        Shadows.shadowOf(pendingResult).getFuture().get(10, TimeUnit.SECONDS)
    }
}
