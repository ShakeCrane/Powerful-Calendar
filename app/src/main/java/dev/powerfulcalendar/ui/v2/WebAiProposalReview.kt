package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalText
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Host-owned snapshot for one proposal review; its token changes whenever its contents change. */
internal data class WebAiProposalReviewSession(
    val reviewId: String,
    val interpretation: AiInterpretation,
    val expectedTargetRevisions: Map<Long, Long>
) {
    fun replace(interpretation: AiInterpretation): WebAiProposalReviewSession = copy(
        reviewId = UUID.randomUUID().toString(),
        interpretation = interpretation
    )

    companion object {
        fun create(
            interpretation: AiInterpretation,
            knownRecords: List<TemporalRecord>
        ): WebAiProposalReviewSession {
            val known = knownRecords.associateBy { it.eventSeq }
            val revisions = interpretation.proposals.mapNotNull { proposal ->
                val target = proposal.eventSeq ?: return@mapNotNull null
                known[target]?.let { target to it.revision }
            }.toMap()
            return WebAiProposalReviewSession(UUID.randomUUID().toString(), interpretation, revisions)
        }
    }
}

/** Frozen proposal and target records awaiting a trusted Host confirmation. */
internal data class WebAiProposalPublishReview(
    val reviewId: String,
    val interpretation: AiInterpretation,
    val selectedPositions: List<Int>,
    val targetSnapshots: Map<Long, TemporalRecord>
)

internal object WebAiProposalProjection {
    fun project(session: WebAiProposalReviewSession): JSONObject {
        val proposals = JSONArray()
        session.interpretation.proposals.take(100).forEachIndexed { index, proposal ->
            val form = proposal.form
            val recurrence = form.recurrence
            val exceptions = recurrence.exceptions.sorted()
            val overrides = recurrence.overrides.toSortedMap().entries.toList()
            val completedKeys = form.completedKeys.sorted()
            val recurrenceJson = JSONObject()
                .put("frequency", recurrence.freq.name)
                .put("interval", recurrence.interval)
                .put("byDay", JSONArray(recurrence.byDay.sortedBy { it.value }.map { it.name }))
                .put("count", recurrence.count ?: JSONObject.NULL)
                .put("untilMillis", recurrence.untilMillis ?: JSONObject.NULL)
                .put("activeFromDate", recurrence.activeFromDate ?: JSONObject.NULL)
                .put("activeUntilDate", recurrence.activeUntilDate ?: JSONObject.NULL)
                .put("exceptions", JSONArray(exceptions.take(100).map { it.take(100) }))
                .put("exceptionsTruncated", exceptions.size > 100 || exceptions.any { it.length > 100 })
                .put("overrides", JSONArray(overrides.take(100).map { (key, value) ->
                        JSONObject()
                            .put("key", key.take(100))
                            .put("startMillis", value.startMillis)
                            .put("endMillis", value.endMillis)
                            .put("title", value.title?.take(240) ?: JSONObject.NULL)
                            .put("titleTruncated", value.title?.length?.let { it > 240 } ?: false)
                    }))
                .put("overridesTruncated", overrides.size > 100 || overrides.any {
                    it.key.length > 100 || (it.value.title?.length ?: 0) > 240
                })
            proposals.put(JSONObject()
                .put("index", index)
                .put("operation", proposal.operation.name)
                .put("eventSeq", proposal.eventSeq ?: JSONObject.NULL)
                .put("targetRevision", proposal.eventSeq?.let(session.expectedTargetRevisions::get)
                    ?: JSONObject.NULL)
                .put("kind", form.kind.name)
                .put("title", form.title.take(240))
                .put("titleTruncated", form.title.length > 240)
                .put("startText", form.startText.take(80))
                .put("endText", form.endText.take(80))
                .put("zoneId", form.zoneId.take(100))
                .put("location", form.location.take(240))
                .put("locationTruncated", form.location.length > 240)
                .put("notes", form.notes.take(1600))
                .put("notesTruncated", form.notes.length > 1600)
                .put("completedKeys", JSONArray(completedKeys.take(100).map { it.take(100) }))
                .put("completedKeysTruncated", completedKeys.size > 100 ||
                    completedKeys.any { it.length > 100 })
                .put("source", form.source.take(120))
                .put("sourceTruncated", form.source.length > 120)
                .put("recurrenceEditScope", form.recurrenceEditScope?.name ?: JSONObject.NULL)
                .put("editOccurrenceKey", form.editOccurrenceKey ?: JSONObject.NULL)
                .put("reminderMinutes", form.reminderMinutes)
                .put("reminderMethod", form.reminderMethod.name)
                .put("reminderTiming", form.reminderTiming.name)
                .put("enabled", form.enabled)
                .put("trackingEnabled", form.trackingEnabled)
                .put("recurrence", recurrenceJson)
                .put("rationale", proposal.rationale.take(400))
                .put("rationaleTruncated", proposal.rationale.length > 400)
                .put("blocked", proposal.blocked?.take(500) ?: JSONObject.NULL)
                .put("publishable", isPublishable(proposal)))
        }
        return JSONObject()
            .put("reviewId", session.reviewId)
            .put("proposals", proposals)
            .put("warnings", JSONArray(session.interpretation.warnings.take(20)
                .map { it.take(500) } + if (session.interpretation.proposals.size > 100)
                    listOf("候选超过 100 项；请缩小任务后重新生成，未展示的候选不能由皮肤选择。")
                else emptyList()))
    }

    fun validSelection(session: WebAiProposalReviewSession, positions: List<Int>): List<Int>? {
        if (positions.isEmpty() || positions.size > 100 || positions.distinct().size != positions.size)
            return null
        val proposals = session.interpretation.proposals
        if (positions.any { it !in proposals.indices || proposals[it].blocked != null }) return null
        return positions.sorted()
    }

    fun isPublishable(proposal: AiProposal): Boolean = proposal.blocked == null &&
        (proposal.operation == DraftOperation.DELETE || proposal.form.isPublishable())
}

@Composable
internal fun WebAiProposalPublishAuthorizationReview(
    review: WebAiProposalPublishReview,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val selected = review.selectedPositions.mapNotNull(review.interpretation.proposals::getOrNull)
    SkinAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认 AI 批量正式操作") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())
                    .testTag("web-ai-publish-review-scroll"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("以下 ${selected.size} 项由 PowerCal Host 再次核对。只有点按本对话框的确认按钮才会提交。")
                selected.forEachIndexed { position, proposal ->
                    val target = proposal.eventSeq?.let(review.targetSnapshots::get)
                    Text(
                        "${position + 1}. ${proposal.operation.label} · " +
                            "${proposal.form.title.ifBlank { "未命名" }}" +
                            (target?.let { " · #${it.eventSeq} · 版本 ${it.revision}" } ?: ""),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (proposal.operation == DraftOperation.DELETE) {
                        Text("此操作会删除正式事件，且当前不能撤销。")
                        target?.let {
                            Text(TemporalText.summarize(it), style = MaterialTheme.typography.bodySmall)
                            snapshotRows(it, DraftForm.from(it)).forEach { row ->
                                Text("${row.label}：${row.before}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        val rows = snapshotRows(target, proposal.form)
                        rows.forEach { row -> Text("${row.label}：${row.before} → ${row.after}",
                            style = MaterialTheme.typography.bodySmall) }
                    }
                    if (proposal.rationale.isNotBlank()) {
                        Text("AI 说明：${proposal.rationale.take(1000)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty() && !busy, onClick = onConfirm) {
                Text("确认并提交 ${selected.size} 项")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } }
    )
}
