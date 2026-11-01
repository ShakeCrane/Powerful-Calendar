package dev.powerfulcalendar.core.api.v1

enum class ReminderTriggerRequirementV1 { EXACT, INEXACT }

enum class ReminderScheduleStateV1 { PENDING, SCHEDULED, BLOCKED, CANCELLED, FIRED }

enum class ReminderReconcileModeV1 { PENDING, EXHAUSTIVE }

enum class ReminderReconcileStatusV1 { COMPLETE, BLOCKED, INCOMPLETE, UNAVAILABLE }

data class ReminderPermissionV1(
    /** Null means this API instance has no Android Host context. */
    val required: Boolean?,
    /** Null means this API instance has no Android Host context. */
    val allowed: Boolean?
)

data class ReminderPermissionSnapshotV1(
    val exactAlarm: ReminderPermissionV1,
    val notifications: ReminderPermissionV1
)

data class ReminderScheduleV1(
    val eventSeq: Long,
    val triggerId: String,
    val revision: Long,
    val occurrenceKey: String,
    val instantMillis: Long,
    val requirement: ReminderTriggerRequirementV1,
    val state: ReminderScheduleStateV1,
    val blockedHint: String? = null
)

data class ReminderEventSnapshotV1(
    val eventSeq: Long,
    val currentRevision: Long?,
    val schedule: ReminderScheduleV1?,
    val lastExecutionResult: String?
)

data class ReminderReconcileReceiptV1(
    val mode: ReminderReconcileModeV1,
    val status: ReminderReconcileStatusV1,
    val attempted: Int = 0,
    val failed: Int = 0,
    /** Capped at 401; 401 means 401 or more remain. */
    val pendingCountCapped: Int = 0,
    /** Capped at 401; 401 means 401 or more remain. */
    val blockedCountCapped: Int = 0,
    val firstBlockedHint: String? = null
)

/** Host-facing reminder state and recovery contract. It contains no Android or Store types. */
interface ReminderFeatureApiV1 {
    val apiVersion: Int

    fun queryPermissions(): ReminderPermissionSnapshotV1

    /** Reads one event's durable schedule intent and latest execution marker. */
    fun queryEvent(eventSeq: Long): ReminderEventSnapshotV1

    /** Reconciles durable desired state to the Host OS scheduler; repeated calls are safe. */
    fun reconcile(mode: ReminderReconcileModeV1 = ReminderReconcileModeV1.PENDING):
        ReminderReconcileReceiptV1
}
