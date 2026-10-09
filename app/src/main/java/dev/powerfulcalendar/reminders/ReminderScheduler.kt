package dev.powerfulcalendar.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.powerfulcalendar.data.CalendarEvent

object ReminderScheduler {
    fun schedule(context: Context, event: CalendarEvent) {
        cancel(context, event.id)
        if (event.reminderMinutes < 0) return
        val whenMillis = event.startMillis - event.reminderMinutes * 60_000L
        if (whenMillis <= System.currentTimeMillis()) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, event.id, event.title)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }
    }

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pendingIntent(context, id, ""))
    }

    private fun pendingIntent(context: Context, id: Long, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .putExtra("event_id", id)
                .putExtra("event_title", title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
