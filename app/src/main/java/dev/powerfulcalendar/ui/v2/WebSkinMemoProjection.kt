package dev.powerfulcalendar.ui.v2

import org.json.JSONArray
import org.json.JSONObject
import dev.powerfulcalendar.core.api.v1.MemoEntryV1

/** Host-owned Memo view state; the toggle key and editable recurrence slot have distinct meanings. */
internal object WebSkinMemoProjection {
    fun build(entries: List<MemoEntryV1>): JSONArray = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject()
                .put("eventSeq", entry.eventSeq)
                .put("revision", entry.revision)
                .put("occurrenceKey", entry.occurrenceKey)
                .put("originalOccurrenceKey", if (entry.repeating) {
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

/** Host-controlled paging over the full, already sorted Memo API result. */
internal object MemoProjectionPaging {
    const val PAGE_SIZE = 250

    fun visibleCount(totalEntries: Int, visibleLimit: Int): Int =
        totalEntries.coerceAtLeast(0).coerceAtMost(visibleLimit.coerceAtLeast(0))

    /** Never decreases an expanded limit, and avoids integer overflow on repeated actions. */
    fun nextLimit(currentLimit: Int, totalEntries: Int): Int {
        val current = currentLimit.coerceAtLeast(0)
        val total = totalEntries.coerceAtLeast(0)
        if (current >= total) return current
        return minOf(total.toLong(), current.toLong() + PAGE_SIZE).toInt()
    }
}
