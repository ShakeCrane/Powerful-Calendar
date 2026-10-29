package dev.powerfulcalendar.interop

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dev.powerfulcalendar.data.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId

object SystemCalendar {
    fun readUpcoming(context: Context): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(365).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY
        )
        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, columns, null, null,
            CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(0)
                val begin = cursor.getLong(1)
                val end = cursor.getLong(2)
                if (end <= begin) continue
                events.add(CalendarEvent(
                    title = cursor.getString(3) ?: "未命名系统日程",
                    startMillis = begin,
                    endMillis = end,
                    notes = cursor.getString(4) ?: "",
                    location = cursor.getString(5) ?: "",
                    reminderMinutes = 0,
                    allDay = cursor.getInt(6) == 1,
                    source = "android-calendar",
                    externalId = eventId.toString() + ":" + begin.toString()
                ))
            }
        }
        return events
    }

    fun createEventIntent(event: CalendarEvent): Intent =
        Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, event.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)

    fun setSystemAlarm(timeHour: Int, timeMinute: Int, title: String): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, timeHour)
            .putExtra(AlarmClock.EXTRA_MINUTES, timeMinute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, title)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)

    fun showSystemAlarms(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
}
