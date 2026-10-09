package dev.powerfulcalendar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.powerfulcalendar.data.EventStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        Thread {
            try {
                EventStore(context).use { store ->
                    store.all().forEach { ReminderScheduler.schedule(context, it) }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
