package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.api.v1.AiBatchSessionV1
import dev.powerfulcalendar.core.api.v1.AiBatchStateV1
import dev.powerfulcalendar.core.api.v1.AiBatchTerminalReportV1
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Process-local progress only; identity and authorization data stay in the Host. */
internal data class WebAiBatchProgress(
    val sessionId: String,
    val mode: AiBatchRunner.Mode,
    val done: Int,
    val total: Int
)

/** Process-lifetime, session-fenced progress shared by the runner and recreated UI compositions. */
internal class WebAiBatchProgressTracker {
    private val mutableProgress = MutableStateFlow<WebAiBatchProgress?>(null)
    val progress: StateFlow<WebAiBatchProgress?> = mutableProgress.asStateFlow()

    @Synchronized
    fun begin(sessionId: String, mode: AiBatchRunner.Mode, total: Int) {
        mutableProgress.value = if (total > 0) {
            WebAiBatchProgress(sessionId, mode, done = 0, total = total)
        } else null
    }

    @Synchronized
    fun report(sessionId: String, mode: AiBatchRunner.Mode, done: Int, total: Int) {
        val current = mutableProgress.value ?: return
        if (current.sessionId != sessionId || current.mode != mode || current.total != total) return
        val boundedDone = done.coerceIn(0, total)
        if (boundedDone >= current.done) {
            mutableProgress.value = current.copy(done = boundedDone)
        }
    }

    @Synchronized
    fun finish(sessionId: String) {
        if (mutableProgress.value?.sessionId == sessionId) mutableProgress.value = null
    }
}

/** Safe status projection for skins. It deliberately omits session IDs, epochs and draft IDs. */
internal object WebAiBatchProjection {
    fun project(
        session: AiBatchSessionV1?,
        preparationMode: AiBatchRunner.Mode?,
        reviewMode: AiBatchRunner.Mode?,
        reviewCards: List<DraftCard>?,
        progress: WebAiBatchProgress?
    ): JSONObject {
        val active = session?.isActive == true
        val persistedReport = session?.terminalReport
        val reportMode = persistedReport?.mode?.name
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
            session?.state == AiBatchStateV1.CANCEL_REQUESTED -> "CANCEL_REQUESTED"
            session?.state == AiBatchStateV1.RUNNING -> "RUNNING"
            reviewCards != null -> "AWAITING_HOST_REVIEW"
            preparationMode != null -> "SELECTING"
            session?.state == AiBatchStateV1.INTERRUPTED -> "INTERRUPTED"
            session?.state == AiBatchStateV1.COMPLETED -> "COMPLETED"
            session?.state == AiBatchStateV1.CANCELLED -> "CANCELLED"
            session?.state == AiBatchStateV1.FAILED -> "FAILED"
            else -> "IDLE"
        }
        return JSONObject()
            .put("phase", phase)
            .put("state", session?.state?.name ?: JSONObject.NULL)
            .put("active", active)
            .put("canCancel", session?.state == AiBatchStateV1.RUNNING)
            .put("canRelease", session?.canReleaseStuck == true)
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

    private fun projectReport(report: AiBatchTerminalReportV1?): JSONObject? {
        if (report == null) return null
        val projected = JSONObject()
        report.mode?.let { projected.put("mode", it.name) }
        if (report.cancelled != null) projected.put("cancelled", report.cancelled)
        report.finishedAtMillis?.let { projected.put("finishedAt", it) }
        if (report.hasFailures) {
            // Stored failure details include draft IDs and exception text; keep them Host-only.
            projected.put("hasFailures", true)
            projected.put("failureSummary", "有批次操作未完成，草稿仍可单独审核。")
        }
        projected.put("outcomeKnown", report.outcomeKnown)
        if (report.outcomeKnown) {
            report.published?.let { projected.put("published", it) }
            report.discarded?.let { projected.put("discarded", it) }
            report.kept?.let { projected.put("kept", it) }
        } else {
            listOf("published", "discarded", "kept").forEach(projected::remove)
        }
        if (report.releasedByUser) projected.put("released", true)
        return projected
    }
}
