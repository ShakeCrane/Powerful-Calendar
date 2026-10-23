package dev.powerfulcalendar.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
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
}
