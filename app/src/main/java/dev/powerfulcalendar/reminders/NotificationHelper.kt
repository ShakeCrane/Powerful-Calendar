package dev.powerfulcalendar.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.powerfulcalendar.MainActivity
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun notificationId(eventSeq: Long): Int = (eventSeq % 1_000_000L).toInt() + 1000

    fun postReminder(
        context: Context,
        record: TemporalRecord,
        occurrenceKey: String,
        triggerId: String,
        snoozed: Boolean
    ) {
        ReminderScheduler.ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context,
            (record.eventSeq % 1_000_000L).toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            (record.eventSeq % 1_000_000L).toInt() + 7,
            Intent(context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_SNOOZE
                putExtra(AlarmContract.EXTRA_EVENT_SEQ, record.eventSeq)
                putExtra(AlarmContract.EXTRA_REVISION, record.revision)
                putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, occurrenceKey)
                putExtra(AlarmContract.EXTRA_TRIGGER_ID, triggerId)
                putExtra(AlarmContract.EXTRA_INSTANT, System.currentTimeMillis())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ringing = record.reminderMethod == dev.powerfulcalendar.core.ReminderMethod.ALARM
        val builder = Notification.Builder(context,
            if (ringing) AlarmContract.CHANNEL_ALARMS else AlarmContract.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle((if (ringing) "闹钟 · " else "提醒 · ") + record.title)
            .setContentText(
                TemporalText.summarize(record) + if (snoozed) " · 稍后提醒" else ""
            )
            .setStyle(
                Notification.BigTextStyle().bigText(
                    buildString {
                        append(record.title)
                        append('\n')
                        append(TemporalText.summarize(record))
                        if (record.location.isNotBlank()) append("\n地点：").append(record.location)
                        if (record.notes.isNotBlank()) append('\n').append(record.notes)
                    }
                )
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(if (ringing) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(
                Notification.Action.Builder(
                    null, "10 分钟后提醒", snooze
                ).build()
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        runCatching { manager.notify(notificationId(record.eventSeq), builder.build()) }
    }

    fun postExactAlarmBlocked(context: Context, record: TemporalRecord) {
        ReminderScheduler.ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context,
            91,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, AlarmContract.CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("提醒未能按精确时间安排")
            .setContentText("「${record.title}」需要精确闹钟权限。打开应用可查看处理方式。")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "「${record.title}」已保存，但系统未授予精确闹钟权限，" +
                        "因此没有安排精确响铃。PowerCal 不会用近似闹钟冒充精确闹钟。"
                )
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(AlarmContract.NOTIFICATION_STATUS_ID, notification) }
    }

    fun cancelReminder(context: Context, eventSeq: Long) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(notificationId(eventSeq))
    }

    fun describeNow(record: TemporalRecord): String =
        Instant.now().atZone(ZoneId.systemDefault()).format(TIME) + " · " + record.title
}
