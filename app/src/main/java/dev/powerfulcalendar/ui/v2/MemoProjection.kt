package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.MemoEntryV1
import java.time.Instant
import java.time.ZoneId

/** Converts stable Memo API entries into the display-only model consumed by :native-skin. */
internal fun projectNativeMemoRows(entries: List<MemoEntryV1>): List<NativeMemoRow> =
    entries.map { entry ->
        NativeMemoRow(
            rowKey = entry.rowKey,
            title = entry.title,
            completed = entry.completed,
            kindLabel = when (entry.kind) {
                EventKindV1.ALARM -> "闹钟"
                EventKindV1.ACTIVITY -> "活动"
                EventKindV1.DEADLINE -> "截止"
                EventKindV1.MEMO -> "备忘"
            },
            dateLabel = entry.startMillis?.let { millis ->
                Instant.ofEpochMilli(millis).atZone(ZoneId.of(entry.zoneId)).toLocalDate().toString()
            } ?: "无日期",
            repeating = entry.repeating,
            sequenceLabel = "#" + entry.eventSeq.toString().padStart(4, '0')
        )
    }
