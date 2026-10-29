package dev.powerfulcalendar.ui.v2

import org.json.JSONArray
import org.json.JSONObject

/** Read-only, Settings-only mirror of the Native Tools reminder diagnostics rows. */
internal object WebSkinReminderDiagnosticsProjection {
    const val MAX_ITEMS = 40

    fun build(rows: List<ReminderDisplay>): JSONArray = JSONArray().apply {
        rows.take(MAX_ITEMS).forEach { row ->
            val intent = row.intent?.let {
                JSONObject()
                    .put("triggerId", it.triggerId.take(120))
                    .put("revision", it.revision)
                    .put("occurrenceKey", it.occurrenceKey.take(200))
                    .put("instantMillis", it.instantMillis)
                    .put("requirement", it.requirement.name)
                    .put("desiredState", it.desired.name)
                    .put("androidHint", it.androidHint?.take(300) ?: JSONObject.NULL)
            } ?: JSONObject.NULL

            put(
                JSONObject()
                    .put("eventSeq", row.record.eventSeq)
                    .put("displaySeq", row.record.displaySeq())
                    .put("eventRevision", row.record.revision)
                    .put("title", row.record.title.take(300))
                    .put("intent", intent)
                    .put("lastExecutionResult", row.execution ?: JSONObject.NULL)
            )
        }
    }
}
