package dev.powerfulcalendar.reminders

/**
 * Extras carried by every alarm PendingIntent. They are the fence: a broadcast that does not
 * match the current event revision and occurrence is ignored instead of ringing.
 */
object AlarmContract {
    const val ACTION_FIRE = "dev.powerfulcalendar.action.FIRE_OCCURRENCE"
    const val ACTION_SNOOZE = "dev.powerfulcalendar.action.SNOOZE_OCCURRENCE"
    const val ACTION_RECONCILE = "dev.powerfulcalendar.action.RECONCILE"

    const val EXTRA_EVENT_SEQ = "event_seq"
    const val EXTRA_REVISION = "event_revision"
    const val EXTRA_OCCURRENCE_KEY = "occurrence_key"
    const val EXTRA_TRIGGER_ID = "trigger_id"
    const val EXTRA_INSTANT = "trigger_instant"
    const val EXTRA_SNOOZE = "is_snooze"

    const val CHANNEL_ALARMS = "powercal-alarms"
    const val CHANNEL_STATUS = "powercal-status"
    const val NOTIFICATION_STATUS_ID = 4100

    /** A late broadcast may ring only inside this window; otherwise it is recorded as MISSED. */
    const val GRACE_MILLIS = 10 * 60_000L
    const val SNOOZE_MILLIS = 10 * 60_000L
}
