package dev.powerfulcalendar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.powerfulcalendar.core.PowerStore

/**
 * Recovery entry point: boot, app upgrade, timezone change, wall-clock change and a granted
 * exact-alarm permission all mean the Android schedule may no longer match what the user wants.
 * Every path rebuilds the desired schedule from the durable store instead of trusting old handles.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            AlarmContract.ACTION_RECONCILE -> Unit
            else -> return
        }
        val result = goAsync()
        Thread {
            try {
                val store = PowerStore(context)
                try {
                    // Remaining PendingIntents for events that no longer exist are cancelled by
                    // reconcile; any obsolete broadcast that still arrives is rejected on arrival.
                    store.rebuildLocalDailyAlarmSchedules()
                    ReminderScheduler.reconcile(context, store)
                } finally {
                    store.close()
                }
            } catch (t: Throwable) {
                Log.e("PowerCalBoot", "reconcile failed", t)
            } finally {
                result.finish()
            }
        }.start()
    }
}
