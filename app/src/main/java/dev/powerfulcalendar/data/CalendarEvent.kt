package dev.powerfulcalendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Priority(val label: String) {
    URGENT("紧急"), HIGH("重要"), NORMAL("普通"), LOW("低优先级")
}

data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val notes: String = "",
    val location: String = "",
    val priority: Priority = Priority.NORMAL,
    val reminderMinutes: Int = 10,
    val allDay: Boolean = false,
    val source: String = "local",
    val externalId: String? = null
) {
    fun occursOn(date: LocalDate): Boolean {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli((endMillis - 1).coerceAtLeast(startMillis)).atZone(zone).toLocalDate()
        return !date.isBefore(start) && !date.isAfter(end)
    }
}
