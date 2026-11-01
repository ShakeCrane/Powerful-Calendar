package dev.powerfulcalendar.core.api.v1

import android.content.Context
import android.os.Build
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.reminders.ReminderScheduler

/** Android adapter for the shared reminder contract; PowerStore and ReminderScheduler stay authoritative. */
internal class AndroidReminderFeatureApiV1(
    private val store: PowerStore,
    context: Context?
) : ReminderFeatureApiV1 {
    private val appContext = context?.applicationContext

    override val apiVersion: Int = 1

    override fun queryPermissions(): ReminderPermissionSnapshotV1 {
        val context = appContext ?: return ReminderPermissionSnapshotV1(
            exactAlarm = ReminderPermissionV1(required = null, allowed = null),
            notifications = ReminderPermissionV1(required = null, allowed = null)
        )
        val sdk = Build.VERSION.SDK_INT
        return ReminderPermissionSnapshotV1(
            exactAlarm = ReminderPermissionV1(
                required = sdk >= Build.VERSION_CODES.S,
                allowed = ReminderScheduler.exactAlarmAllowed(context)
            ),
            notifications = ReminderPermissionV1(
                required = sdk >= Build.VERSION_CODES.TIRAMISU,
                allowed = ReminderScheduler.notificationsAllowed(context)
            )
        )
    }

    override fun queryEvent(eventSeq: Long): ReminderEventSnapshotV1 {
        require(eventSeq > 0L) { "eventSeq must be positive" }
        val record = store.get(eventSeq)
        val intent = store.intentFor(eventSeq)
        return ReminderEventSnapshotV1(
            eventSeq = eventSeq,
            currentRevision = record?.revision,
            schedule = intent?.let {
                ReminderScheduleV1(
                    eventSeq = it.eventSeq,
                    triggerId = it.triggerId,
                    revision = it.revision,
                    occurrenceKey = it.occurrenceKey,
                    instantMillis = it.instantMillis,
                    requirement = ReminderTriggerRequirementV1.valueOf(it.requirement.name),
                    state = ReminderScheduleStateV1.valueOf(it.desired.name),
                    blockedHint = it.androidHint
                )
            },
            lastExecutionResult = store.lastExecution(eventSeq)
        )
    }

    override fun reconcile(mode: ReminderReconcileModeV1): ReminderReconcileReceiptV1 {
        val context = appContext
        if (context == null) {
            return receipt(
                mode = mode,
                status = ReminderReconcileStatusV1.UNAVAILABLE,
                attempted = 0,
                failed = 0
            )
        }

        val report = when (mode) {
            ReminderReconcileModeV1.PENDING -> ReminderScheduler.reconcile(context, store)
            ReminderReconcileModeV1.EXHAUSTIVE -> ReminderScheduler.reconcileAll(context, store)
        }
        val remainingPending = store.intentsInState(PowerStore.DesiredSchedule.PENDING, limit = 401)
        val blocked = store.intentsInState(PowerStore.DesiredSchedule.BLOCKED, limit = 401)
        val status = when {
            report.failed > 0 || remainingPending.isNotEmpty() -> ReminderReconcileStatusV1.INCOMPLETE
            blocked.isNotEmpty() -> ReminderReconcileStatusV1.BLOCKED
            else -> ReminderReconcileStatusV1.COMPLETE
        }
        return ReminderReconcileReceiptV1(
            mode = mode,
            status = status,
            attempted = report.attempted,
            failed = report.failed,
            pendingCountCapped = remainingPending.size,
            blockedCountCapped = blocked.size,
            firstBlockedHint = blocked.firstNotNullOfOrNull { it.androidHint?.takeIf(String::isNotBlank) }
        )
    }

    private fun receipt(
        mode: ReminderReconcileModeV1,
        status: ReminderReconcileStatusV1,
        attempted: Int,
        failed: Int
    ): ReminderReconcileReceiptV1 {
        val pending = store.intentsInState(PowerStore.DesiredSchedule.PENDING, limit = 401)
        val blocked = store.intentsInState(PowerStore.DesiredSchedule.BLOCKED, limit = 401)
        return ReminderReconcileReceiptV1(
            mode = mode,
            status = status,
            attempted = attempted,
            failed = failed,
            pendingCountCapped = pending.size,
            blockedCountCapped = blocked.size,
            firstBlockedHint = blocked.firstNotNullOfOrNull { it.androidHint?.takeIf(String::isNotBlank) }
        )
    }
}
