package dev.powerfulcalendar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.powerfulcalendar.core.PowerStore

/**
 * Every delivered alarm is re-validated against the authoritative store before any audible side
 * effect. Android may deliver a PendingIntent that belonged to an older revision; that broadcast
 * still arrives, and the correct behaviour is to log it as ignored rather than ring the wrong time.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != AlarmContract.ACTION_FIRE && action != AlarmContract.ACTION_SNOOZE) return
        val result = goAsync()
        Thread {
            try {
                handle(context, intent)
            } catch (t: Throwable) {
                Log.e("PowerCalAlarm", "delivery failed", t)
            } finally {
                result.finish()
            }
        }.start()
    }

    /** Runs the delivery contract separately from Android's asynchronous broadcast wrapper. */
    internal fun handle(context: Context, intent: Intent) {
        val eventSeq = intent.getLongExtra(AlarmContract.EXTRA_EVENT_SEQ, -1L)
        if (eventSeq <= 0L) return
        val revision = intent.getLongExtra(AlarmContract.EXTRA_REVISION, -1L)
        val occurrenceKey = intent.getStringExtra(AlarmContract.EXTRA_OCCURRENCE_KEY) ?: return
        val triggerId = intent.getStringExtra(AlarmContract.EXTRA_TRIGGER_ID).orEmpty()
        val requestedInstant = intent.getLongExtra(AlarmContract.EXTRA_INSTANT, 0L)
        val isSnoozeRequest = intent.action == AlarmContract.ACTION_SNOOZE
        // ACTION_SNOOZE is the notification button; ACTION_FIRE + EXTRA_SNOOZE is its delayed alarm.
        val isScheduledSnooze = intent.action == AlarmContract.ACTION_FIRE &&
            intent.getBooleanExtra(AlarmContract.EXTRA_SNOOZE, false)

        val store = PowerStore(context)
        try {
            if (isSnoozeRequest) {
                when (val validation = ReminderScheduler.validate(
                    store, eventSeq, revision, occurrenceKey, requestedInstant,
                    snooze = true, triggerId = triggerId
                )) {
                    is ReminderScheduler.Validation.Rejected ->
                        store.recordExecution(
                            eventSeq, revision, occurrenceKey, triggerId,
                            "IGNORED_SNOOZE_STALE", validation.reason
                        )
                    is ReminderScheduler.Validation.Valid -> {
                        store.recordExecution(
                            eventSeq, revision, occurrenceKey, triggerId,
                            "SNOOZED", "用户选择 ${AlarmContract.SNOOZE_MILLIS / 60_000} 分钟后提醒"
                        )
                        NotificationHelper.cancelReminder(context, eventSeq)
                        val exact = ReminderScheduler.scheduleSnooze(
                            context, validation.record, occurrenceKey, triggerId
                        )
                        if (!exact) {
                            store.recordExecution(
                                eventSeq, revision, occurrenceKey, triggerId,
                                "SNOOZE_INEXACT", "精确闹钟不可用，稍后提醒按非精确方式安排"
                            )
                        }
                    }
                }
                return
            }

            when (val validation = ReminderScheduler.validate(
                store, eventSeq, revision, occurrenceKey, requestedInstant,
                snooze = isScheduledSnooze, triggerId = triggerId
            )) {
                is ReminderScheduler.Validation.Rejected -> {
                    store.recordExecution(
                        eventSeq, revision, occurrenceKey, triggerId,
                        "IGNORED_STALE", validation.reason
                    )
                    ReminderScheduler.cancel(context, eventSeq)
                }
                is ReminderScheduler.Validation.Valid -> {
                    val now = System.currentTimeMillis()
                    val lateBy = now - requestedInstant
                    val record = validation.record
                    if (AlarmContract.isOutsideGraceWindow(requestedInstant, now)) {
                        store.recordExecution(
                            eventSeq, revision, occurrenceKey, triggerId,
                            "MISSED", "迟于宽限窗口 ${lateBy / 60_000} 分钟"
                        )
                        store.advanceIntentAfterFiring(eventSeq, occurrenceKey)
                    } else {
                        if (ReminderScheduler.notificationsAllowed(context)) {
                            NotificationHelper.postReminder(
                                context, record, occurrenceKey, triggerId, snoozed = isScheduledSnooze
                            )
                            store.recordExecution(
                                eventSeq, revision, occurrenceKey, triggerId, "FIRED", ""
                            )
                        } else {
                            store.recordExecution(
                                eventSeq, revision, occurrenceKey, triggerId,
                                "BLOCKED", "缺少通知权限，未能展示提醒"
                            )
                        }
                        store.advanceIntentAfterFiring(eventSeq, occurrenceKey)
                    }
                }
            }
            // Immediately schedule the next occurrence so a repeating series never depends on the
            // app being opened again.
            ReminderScheduler.reconcile(context, store)
        } finally {
            store.close()
        }
    }
}
