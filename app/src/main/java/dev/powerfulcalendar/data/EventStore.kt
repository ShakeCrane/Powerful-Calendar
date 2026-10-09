package dev.powerfulcalendar.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EventStore(context: Context) : SQLiteOpenHelper(context, "powerful-calendar.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE events (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              start_ms INTEGER NOT NULL,
              end_ms INTEGER NOT NULL,
              notes TEXT NOT NULL DEFAULT '',
              location TEXT NOT NULL DEFAULT '',
              priority TEXT NOT NULL DEFAULT 'NORMAL',
              reminder_minutes INTEGER NOT NULL DEFAULT 10,
              all_day INTEGER NOT NULL DEFAULT 0,
              source TEXT NOT NULL DEFAULT 'local',
              external_id TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX idx_external_event ON events(source, external_id)")
        db.execSQL("CREATE INDEX idx_event_start ON events(start_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun all(): List<CalendarEvent> {
        val rows = mutableListOf<CalendarEvent>()
        readableDatabase.query("events", null, null, null, null, null, "start_ms ASC").use { cursor ->
            while (cursor.moveToNext()) {
                fun string(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                fun number(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                rows.add(CalendarEvent(
                    id = number("id"),
                    title = string("title"),
                    startMillis = number("start_ms"),
                    endMillis = number("end_ms"),
                    notes = string("notes"),
                    location = string("location"),
                    priority = Priority.entries.firstOrNull { it.name == string("priority") } ?: Priority.NORMAL,
                    reminderMinutes = number("reminder_minutes").toInt(),
                    allDay = number("all_day") != 0L,
                    source = string("source"),
                    externalId = cursor.getString(cursor.getColumnIndexOrThrow("external_id"))
                ))
            }
        }
        return rows
    }

    @Synchronized fun upsert(event: CalendarEvent): Long {
        val cv = ContentValues().apply {
            put("title", event.title)
            put("start_ms", event.startMillis)
            put("end_ms", event.endMillis)
            put("notes", event.notes)
            put("location", event.location)
            put("priority", event.priority.name)
            put("reminder_minutes", event.reminderMinutes)
            put("all_day", if (event.allDay) 1 else 0)
            put("source", event.source)
            put("external_id", event.externalId)
        }
        val db = writableDatabase
        if (event.id > 0) {
            db.update("events", cv, "id = ?", arrayOf(event.id.toString()))
            return event.id
        }
        // Return the existing imported record rather than duplicating the same source entry.
        if (event.externalId != null) {
            db.query("events", arrayOf("id"), "source = ? AND external_id = ?",
                arrayOf(event.source, event.externalId), null, null, null).use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    db.update("events", cv, "id = ?", arrayOf(id.toString()))
                    return id
                }
            }
        }
        return db.insertOrThrow("events", null, cv)
    }

    @Synchronized fun delete(id: Long) {
        writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))
    }
}
