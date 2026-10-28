package dev.powerfulcalendar.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.ReminderPolicy
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reconciles durable [PowerStore.ScheduleIntent] rows to Android alarms.
 *
 * Guarantees:
 *  - a committed revision owns the schedule; an obsolete revision is cancelled, never rewritten;
 *  - exact alarms are only claimed when the OS actually grants exactness; otherwise the intent is
 *    marked BLOCKED with visible guidance instead of silently downgrading a required alarm;
 *  - a pending broadcast carries revision + occurrence + trigger so a stale alarm can be rejected
 *    when it fires (Android will still deliver it; we choose not to ring).
 */
object ReminderScheduler {
    private const val TAG = "PowerCalScheduler"

    fun exactAlarmAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Idempotent: safe to call after boot, timezone change, permission change or a commit. */
    fun reconcile(context: Context, store: PowerStore) {
        val now = System.currentTimeMillis()
        // Revisit blocked rows as well: a permission or OS state change can make them schedulable
        // without a new event revision. `markIntent` remains revision-guarded against concurrent
        // commits.
        store.reconciliationIntents(limit = 400).forEach { intent ->
            runCatching { apply(context, store, intent, now) }
                .onFailure { Log.w(TAG, "reconcile #${intent.eventSeq} failed", it) }
        }
        // Cancelled intents may still have an armed PendingIntent (delete/disable). Disarm it once.
        store.intentsInState(PowerStore.DesiredSchedule.CANCELLED, limit = 100).forEach { intent ->
            cancel(context, intent.eventSeq)
            store.deleteIntent(intent.eventSeq, intent.triggerId)
        }
    }

    private fun apply(context: Context, store: PowerStore, intent: PowerStore.ScheduleIntent, now: Long) {
        val record = store.get(intent.eventSeq)
        if (record == null || record.revision != intent.revision || !record.enabled) {
            // Only disarm when the row still belongs to this revision; a newer schedule must survive.
            val owned = store.markIntent(
                intent.eventSeq, intent.triggerId, PowerStore.DesiredSchedule.CANCELLED,
                "事件已更新或删除", intent.revision, intent.occurrenceKey
            )
            if (owned) cancel(context, intent.eventSeq)
            return
        }
        if (AlarmContract.isOutsideGraceWindow(intent.instantMillis, now)) {
            // The window has passed while we were not running. Ringing now would be a lie.
            store.recordExecution(
                intent.eventSeq, record.revision, intent.occurrenceKey, intent.triggerId,
                "MISSED", "错过宽限窗口，未补响"
            )
            store.advanceIntentAfterFiring(intent.eventSeq, intent.occurrenceKey)
            return
        }
        if (intent.requirement == PowerStore.TriggerRequirement.EXACT && !exactAlarmAllowed(context)) {
            val owned = store.markIntent(
                intent.eventSeq, intent.triggerId, PowerStore.DesiredSchedule.BLOCKED,
                "缺少精确闹钟权限", intent.revision, intent.occurrenceKey
            )
            if (owned) NotificationHelper.postExactAlarmBlocked(context, record)
            return
        }
        if (!notificationsAllowed(context)) {
            // The alarm can still be scheduled; delivery will be silent until permission returns.
            Log.w(TAG, "POST_NOTIFICATIONS missing; alarm #${intent.eventSeq} will be silent")
        }
        val pending = alarmPendingIntent(
            context, intent.eventSeq, record.revision, intent.occurrenceKey,
            intent.triggerId, intent.instantMillis, snooze = false
        )
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (intent.requirement == PowerStore.TriggerRequirement.EXACT) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, intent.instantMillis, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, intent.instantMillis, pending)
            }
            // Zero rows means the row was replaced; do not claim we scheduled the current revision.
            val markedScheduled = store.markIntent(
                intent.eventSeq, intent.triggerId, PowerStore.DesiredSchedule.SCHEDULED,
                null, intent.revision, intent.occurrenceKey
            )
            if (markedScheduled && store.intentsInState(PowerStore.DesiredSchedule.BLOCKED, limit = 1).isEmpty()) {
                NotificationHelper.cancelExactAlarmBlocked(context)
            }
        } catch (security: SecurityException) {
            val owned = store.markIntent(
                intent.eventSeq, intent.triggerId, PowerStore.DesiredSchedule.BLOCKED,
                "系统拒绝精确闹钟", intent.revision, intent.occurrenceKey
            )
            if (owned) NotificationHelper.postExactAlarmBlocked(context, record)
        }
    }

    fun cancel(context: Context, eventSeq: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(eventSeq),
            Intent(context, ReminderReceiver::class.java).apply {
                action = AlarmContract.ACTION_FIRE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
        pending.cancel()
    }

    /** Snooze is an occurrence execution change, never a recurrence-series change. */
    fun scheduleSnooze(
        context: Context,
        record: TemporalRecord,
        occurrenceKey: String,
        triggerId: String
    ): Boolean {
        val at = System.currentTimeMillis() + AlarmContract.SNOOZE_MILLIS
        val pending = alarmPendingIntent(
            context, record.eventSeq, record.revision, occurrenceKey, triggerId, at, snooze = true
        )
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return try {
            if (exactAlarmAllowed(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                true
            } else {
                // Never claim exactness: the caller records the downgrade as evidence.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                false
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            false
        }
    }

    private fun requestCode(eventSeq: Long): Int = (eventSeq % Int.MAX_VALUE).toInt()

    private fun alarmPendingIntent(
        context: Context,
        eventSeq: Long,
        revision: Long,
        occurrenceKey: String,
        triggerId: String,
        instant: Long,
        snooze: Boolean
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(eventSeq),
        Intent(context, ReminderReceiver::class.java).apply {
            action = AlarmContract.ACTION_FIRE
            putExtra(AlarmContract.EXTRA_EVENT_SEQ, eventSeq)
            putExtra(AlarmContract.EXTRA_REVISION, revision)
            putExtra(AlarmContract.EXTRA_OCCURRENCE_KEY, occurrenceKey)
            putExtra(AlarmContract.EXTRA_TRIGGER_ID, triggerId)
            putExtra(AlarmContract.EXTRA_INSTANT, instant)
            putExtra(AlarmContract.EXTRA_SNOOZE, snooze)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Legacy offset helper retained for existing diagnostic clients. */
    fun triggerOffsetMillis(record: TemporalRecord): Long? =
        if (record.reminderMinutes < 0) null
        else record.reminderMinutes.toLong() * 60_000L

    /**
     * Verifies a broadcast against the authoritative store. Returns the occurrence when the
     * alarm is still legitimate; `null` means it must not ring.
     */
    fun validate(
        store: PowerStore,
        eventSeq: Long,
        expectedRevision: Long,
        occurrenceKey: String,
        requestedInstant: Long,
        snooze: Boolean,
        triggerId: String = ReminderPolicy.PRIMARY_TRIGGER_ID
    ): ReminderScheduler.Validation {
        val record = store.get(eventSeq)
            ?: return Validation.Rejected("事件不存在或已删除")
        if (!record.enabled) return Validation.Rejected("事件已停用")
        if (record.revision != expectedRevision) {
            return Validation.Rejected("版本已更新至 v${record.revision}")
        }
        if (triggerId != ReminderPolicy.PRIMARY_TRIGGER_ID) {
            return Validation.Rejected("提醒触发器已失效")
        }
        if (record.reminderMinutes < 0) {
            return Validation.Rejected("该事件不再需要提醒")
        }
        val occurrence = RecurrenceEngine.occurrenceForKey(record, occurrenceKey)
            ?: return Validation.Rejected("发生实例已失效")
        if (!snooze) {
            val expected = dev.powerfulcalendar.core.ReminderPolicy.instant(record, occurrence.startMillis)
                ?: return Validation.Rejected("提醒策略已关闭")
            if (expected != requestedInstant) {
                return Validation.Rejected("触发时刻与当前版本不一致")
            }
        }
        return Validation.Valid(record, occurrence)
    }

    sealed interface Validation {
        data class Valid(
            val record: TemporalRecord,
            val occurrence: dev.powerfulcalendar.core.Occurrence
        ) : Validation

        data class Rejected(val reason: String) : Validation
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:" + context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /** Human-readable status for the timeline editor / diagnostics panel. */
    fun statusText(store: PowerStore, record: TemporalRecord): String {
        val intent = store.intentFor(record.eventSeq) ?: return "无待调度提醒"
        val time = Instant.ofEpochMilli(intent.instantMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        return when (intent.desired) {
            PowerStore.DesiredSchedule.SCHEDULED -> "已安排 · $time"
            PowerStore.DesiredSchedule.PENDING -> "等待调度 · $time"
            PowerStore.DesiredSchedule.BLOCKED -> "调度受阻 · $time（" + (intent.androidHint ?: "") + "）"
            PowerStore.DesiredSchedule.CANCELLED -> "已取消"
            PowerStore.DesiredSchedule.FIRED -> "已触发 · $time"
        }
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                AlarmContract.CHANNEL_ALARMS,
                "时间提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟、活动与截止的提醒"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AlarmContract.CHANNEL_MESSAGES,
                "日程消息提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "普通活动、截止和默认闹钟的消息通知"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AlarmContract.CHANNEL_STATUS,
                "调度状态",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "权限与调度受阻提示" }
        )
    }
}
