package dev.powerfulcalendar.core

import java.time.Instant
import java.time.ZoneId

/**
 * Alert delivery is independent of event KIND.
 * MESSAGE = ordinary notification; ALARM = high-priority exact delivery when the OS permits.
 * DAY_START = start of the occurrence's local calendar day, not "N x 24h before".
 *
 * The same deterministic policy MUST be used to build intents and validate broadcasts.
 */
enum class ReminderMethod { MESSAGE, ALARM }
enum class ReminderTiming { OFFSET, DAY_START }

object ReminderPolicy {
    fun instant(record: TemporalRecord, occurrenceStart: Long): Long? {
        if (!record.enabled) return null
        if (record.reminderMinutes < 0) return null
        return when (record.reminderTiming) {
            ReminderTiming.OFFSET -> occurrenceStart -
                record.reminderMinutes.coerceAtLeast(0).toLong() * 60_000L
            ReminderTiming.DAY_START -> Instant.ofEpochMilli(occurrenceStart)
                .atZone(record.zone).toLocalDate().atStartOfDay(record.zone)
                .toInstant().toEpochMilli()
        }
    }

    /** Absolute 00:00 must respect the event's calendar zone across DST. */
    fun dayStart(occurrenceStart: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(occurrenceStart).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
}
