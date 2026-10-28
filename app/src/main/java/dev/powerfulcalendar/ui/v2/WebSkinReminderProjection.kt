package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.reminders.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A private Host-side input. None of its database identity fields are serialized. */
internal data class ReminderDiagnosticInput(
    val record: TemporalRecord,
    val intent: PowerStore.ScheduleIntent?,
    val lastExecution: String?
)

/** Versioned, bounded allowlist projection for the skin-owned Settings page. */
internal object WebSkinReminderProjection {
    const val ITEM_LIMIT = 40
    const val TITLE_LIMIT = 120
    const val HINT_LIMIT = 160
    const val EXECUTION_LIMIT = 32

    private val safeHints = setOf("事件已更新或删除", "缺少精确闹钟权限", "系统拒绝精确闹钟")
    private val safeExecutions = setOf(
        "FIRED", "MISSED", "BLOCKED", "SNOOZED", "SNOOZE_INEXACT",
        "IGNORED_STALE", "IGNORED_SNOOZE_STALE"
    )

    fun toJson(entries: List<ReminderDiagnosticInput>): JSONObject {
        val ordered = entries.sortedWith(
            compareBy<ReminderDiagnosticInput>(
                { it.intent?.instantMillis ?: it.record.anchorMillis() ?: Long.MAX_VALUE },
                { it.record.kind.name },
                { it.record.title.lowercase(Locale.ROOT) },
                { it.record.title },
                // Private tie-breaker only; it is never copied into the projection.
                { it.record.eventSeq }
            )
        )
        val items = JSONArray()
        ordered.take(ITEM_LIMIT).forEachIndexed { index, entry ->
            val title = entry.record.title
            val safeHint = entry.intent?.androidHint?.takeIf { it in safeHints }?.take(HINT_LIMIT)
            val execution = entry.lastExecution?.takeIf { it in safeExecutions }?.take(EXECUTION_LIMIT)
            val desiredState = entry.intent?.desired?.name
                ?: if (execution == "FIRED") "FIRED" else "NO_INTENT"

            items.put(JSONObject()
                .put("displayId", "reminder-${(index + 1).toString().padStart(2, '0')}")
                .put("title", title.take(TITLE_LIMIT))
                .put("titleTruncated", title.length > TITLE_LIMIT)
                .put("kind", entry.record.kind.name)
                .put("desiredState", desiredState)
                .put("androidHint", safeHint ?: JSONObject.NULL)
                .put("lastExecution", execution ?: JSONObject.NULL))
        }

        return JSONObject()
            .put("itemLimit", ITEM_LIMIT)
            .put("truncated", ordered.size > ITEM_LIMIT)
            .put("items", items)
    }
}

/** Host operation state. Counts contain no event-specific information. */
internal data class ReminderReconcileSnapshot(
    val status: String = "IDLE",
    val checked: Int = 0,
    val scheduled: Int = 0,
    val blocked: Int = 0,
    val pending: Int = 0,
    val failed: Int = 0,
    val truncated: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", status)
        .put("checked", checked)
        .put("scheduled", scheduled)
        .put("blocked", blocked)
        .put("pending", pending)
        .put("failed", failed)
        .put("truncated", truncated)

    companion object {
        fun running() = ReminderReconcileSnapshot(status = "RUNNING")

        fun completed(report: ReminderScheduler.ReconcileReport) = ReminderReconcileSnapshot(
            status = if (report.failed == 0 && report.pending == 0 && !report.truncated) "SUCCEEDED" else "FAILED",
            checked = report.checked,
            scheduled = report.scheduled,
            blocked = report.blocked,
            pending = report.pending,
            failed = report.failed,
            truncated = report.truncated
        )

        fun failed() = ReminderReconcileSnapshot(status = "FAILED", failed = 1)
    }
}
