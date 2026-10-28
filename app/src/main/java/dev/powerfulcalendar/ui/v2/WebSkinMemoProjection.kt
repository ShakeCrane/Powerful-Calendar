package dev.powerfulcalendar.ui.v2

import org.json.JSONArray
import org.json.JSONObject

/** Host-owned Memo view state; the toggle key and editable recurrence slot have distinct meanings. */
internal object WebSkinMemoProjection {
    fun build(entries: List<TodoEntry>): JSONArray = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject()
                .put("eventSeq", entry.owner.eventSeq)
                .put("revision", entry.owner.revision)
                .put("occurrenceKey", entry.occurrenceKey)
                .put("originalOccurrenceKey", if (entry.owner.recurrence.isRepeating) {
                    entry.occurrenceKey
                } else {
                    JSONObject.NULL
                })
                .put("title", entry.title.take(240))
                .put("completed", entry.completed)
                .put("startMillis", entry.startMillis))
        }
    }
}
