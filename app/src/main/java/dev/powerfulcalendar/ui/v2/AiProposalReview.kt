package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalText

/** This is a preview, not a database mutation; only explicit button callbacks may write. */
@Composable
internal fun AiProposalReview(
    interpretation: AiInterpretation,
    busy: Boolean,
    onApply: (Set<Int>, Boolean) -> Unit,
    onDiscard: (Set<Int>) -> Unit,
    onSupplement: () -> Unit
) {
    var selected by remember(interpretation) {
        mutableStateOf(interpretation.proposals.indices
            .filter { interpretation.proposals[it].blocked == null }.toSet())
    }
    val entries = interpretation.proposals
    Column(
        Modifier.fillMaxWidth().heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            true to "识别到新增 · 是否创建草稿？",
            false to "识别到修改 / 删除 · 是否创建修改提案？"
        ).forEach { (newItem, label) ->
            val indexes = entries.indices.filter {
                (entries[it].operation == DraftOperation.CREATE) == newItem
            }
            if (indexes.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        indexes.forEach { index ->
                            val item = entries[index]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = index in selected, enabled = !busy && item.blocked == null,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + index else selected - index
                                    })
                                Column(Modifier.weight(1f)) {
                                    val caption = if (newItem) item.form.kind.label
                                        else item.operation.label +
                                            (item.eventSeq?.let { " #" + it } ?: "")
                                    Text((index + 1).toString() + ". " + item.form.title.ifBlank { "未命名" } +
                                        " · " + caption, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2)
                                    Text(item.form.startText + " " + item.form.endText +
                                        if (item.blocked == null) "" else " · " + item.blocked,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.blocked != null) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                        }
                        val chosen = selected.intersect(indexes.toSet())
                        val publishable = chosen.isNotEmpty() && chosen.all {
                            val proposal = entries[it]
                            proposal.blocked == null &&
                                (proposal.operation == DraftOperation.DELETE ||
                                    proposal.form.isPublishable())
                        }
                        if (!publishable && chosen.isNotEmpty()) {
                            Text("部分信息待补全：可先写入草稿，完善后再发布。",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { onApply(chosen, true) }, enabled = publishable && !busy,
                                modifier = Modifier.weight(1f)) { Text("批量同意", maxLines = 1) }
                            OutlinedButton(onClick = { onApply(chosen, false) },
                                enabled = chosen.isNotEmpty() && !busy, modifier = Modifier.weight(1f)) {
                                Text("写入草稿", maxLines = 1)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                onDiscard(chosen); selected = selected - chosen
                            }, enabled = chosen.isNotEmpty() && !busy,
                                modifier = Modifier.weight(1f)) { Text("批量拒绝") }
                            TextButton(onClick = onSupplement, enabled = !busy,
                                modifier = Modifier.weight(1f)) { Text("补充细节") }
                        }
                    }
                }
            }
        }
        interpretation.warnings.take(2).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
