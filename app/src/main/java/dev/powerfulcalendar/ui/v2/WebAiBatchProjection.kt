package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONObject

/** Process-local progress only; identity and authorization data stay in the Host. */
internal data class WebAiBatchProgress(
    val sessionId: String,
    val mode: AiBatchRunner.Mode,
    val done: Int,
    val total: Int
)

/** Safe status projection for skins. It deliberately omits session IDs, epochs and draft IDs. */
internal object WebAiBatchProjection {
    fun project(
        session: PowerStore.AiSession?,
        preparationMode: AiBatchRunner.Mode?,
        reviewMode: AiBatchRunner.Mode?,
        reviewCards: List<DraftCard>?,
        progress: WebAiBatchProgress?,
        nowMillis: Long = System.currentTimeMillis()
    ): JSONObject {
        val active = session?.isActive == true
        val persistedReport = session?.report?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        val reportMode = persistedReport?.optString("mode")?.takeIf {
            it == AiBatchRunner.Mode.PUBLISH.name || it == AiBatchRunner.Mode.DISCARD.name
        }
        val currentProgress = progress?.takeIf {
            active && it.sessionId == session?.sessionId
        }
        val mode = when {
            currentProgress != null -> currentProgress.mode
            reviewCards != null -> reviewMode
            preparationMode != null -> preparationMode
            reportMode != null -> runCatching { AiBatchRunner.Mode.valueOf(reportMode) }.getOrNull()
            else -> null
        }
        val phase = when {
            session?.state == PowerStore.AiState.CANCEL_REQUESTED -> "CANCEL_REQUESTED"
            session?.state == PowerStore.AiState.RUNNING -> "RUNNING"
            reviewCards != null -> "AWAITING_HOST_REVIEW"
            preparationMode != null -> "SELECTING"
            session?.state == PowerStore.AiState.INTERRUPTED -> "INTERRUPTED"
            session?.state == PowerStore.AiState.COMPLETED -> "COMPLETED"
            session?.state == PowerStore.AiState.CANCELLED -> "CANCELLED"
            session?.state == PowerStore.AiState.FAILED -> "FAILED"
            else -> "IDLE"
        }
        return JSONObject()
            .put("phase", phase)
            .put("state", session?.state?.name ?: JSONObject.NULL)
            .put("active", active)
            .put("canCancel", session?.state == PowerStore.AiState.RUNNING)
            .put("canRelease", PowerStore.canReleaseStuckAiBatch(session, nowMillis))
            .put("mode", mode?.name ?: JSONObject.NULL)
            .put("scopeCount", session?.scope?.size ?: 0)
            .put("selectedCount", reviewCards?.size ?: 0)
            .put("operationCounts", reviewCards?.let(::operationCounts) ?: JSONObject())
            .put("containsDelete", reviewCards?.any {
                it.operation == dev.powerfulcalendar.core.DraftOperation.DELETE
            } ?: false)
            .put("progress", progress?.takeIf {
                active && it.sessionId == session?.sessionId && it.total > 0
            }?.let {
                JSONObject().put("done", it.done.coerceIn(0, it.total))
                    .put("total", it.total)
            } ?: JSONObject.NULL)
            .put("report", projectReport(persistedReport) ?: JSONObject.NULL)
    }

    private fun operationCounts(cards: List<DraftCard>): JSONObject = JSONObject().apply {
        cards.groupingBy { it.operation.name }.eachCount().toSortedMap().forEach { (operation, count) ->
            put(operation, count)
        }
    }

    private fun projectReport(report: JSONObject?): JSONObject? {
        if (report == null) return null
        val projected = JSONObject()
        report.optString("mode").takeIf {
            it == AiBatchRunner.Mode.PUBLISH.name || it == AiBatchRunner.Mode.DISCARD.name
        }?.let { projected.put("mode", it) }
        listOf("published", "discarded", "kept").forEach { key ->
            if (report.has(key)) projected.put(key, report.optInt(key).coerceAtLeast(0))
        }
        if (report.has("cancelled")) projected.put("cancelled", report.optBoolean("cancelled"))
        if (report.has("finishedAt")) projected.put("finishedAt", report.optLong("finishedAt"))
        if (report.optString("failures").isNotBlank()) {
            // Stored failure details include draft IDs and exception text; keep them Host-only.
            projected.put("hasFailures", true)
            projected.put("failureSummary", "有批次操作未完成，草稿仍可单独审核。")
        }
        val outcomeKnown = report.optBoolean("outcomeKnown",
            report.has("published") && report.has("discarded") && report.has("kept"))
        projected.put("outcomeKnown", outcomeKnown)
        if (outcomeKnown) {
            listOf("published", "discarded", "kept").forEach { key ->
                if (report.has(key)) projected.put(key, report.optInt(key).coerceAtLeast(0))
            }
        } else {
            listOf("published", "discarded", "kept").forEach { projected.remove(it) }
        }
        if (report.optString("released") == "user") projected.put("released", true)
        return projected
    }
}
