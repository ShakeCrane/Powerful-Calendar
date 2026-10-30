package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeAiProposalGroup
import dev.powerfulcalendar.ui.v2.NativeAiProposalReviewModel

/** Native AI proposal UI. All approval and draft-writing effects are Host callbacks. */
@Composable
fun NativeAiProposalReview(
    model: NativeAiProposalReviewModel,
    selectionKey: String,
    busy: Boolean,
    onApply: (Set<Int>, Boolean) -> Unit,
    onDiscard: (Set<Int>) -> Unit,
    onSupplement: () -> Unit
) {
    var selected by remember(selectionKey) {
        mutableStateOf(model.rows.filter { it.blockedReason == null }.map { it.position }.toSet())
    }
    val rowsByPosition = remember(model.rows) { model.rows.associateBy { it.position } }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NativeAiProposalGroup.entries.forEach { group ->
            val rows = model.rows.filter { it.group == group }
            if (rows.isNotEmpty()) {
                val label = if (group == NativeAiProposalGroup.CREATE) {
                    "识别到新增 · 是否创建草稿？"
                } else {
                    "识别到修改 / 删除 · 是否创建修改提案？"
                }
                val positions = rows.map { it.position }.toSet()
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        rows.forEach { row ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = row.position in selected,
                                    enabled = !busy && row.blockedReason == null,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + row.position
                                            else selected - row.position
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${row.position + 1}. ${row.title.ifBlank { "未命名" }} · ${row.caption}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                    Text(
                                        row.startText + " " + row.endText +
                                            (row.blockedReason?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (row.blockedReason != null) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        val chosen = selected.intersect(positions)
                        val canPublish = chosen.isNotEmpty() && chosen.all {
                            rowsByPosition[it]?.publishable == true
                        }
                        if (!canPublish && chosen.isNotEmpty()) {
                            Text("部分信息待补全：可先写入草稿，完善后再发布。",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onApply(chosen, true) },
                                enabled = canPublish && !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text("批量同意", maxLines = 1) }
                            OutlinedButton(
                                onClick = { onApply(chosen, false) },
                                enabled = chosen.isNotEmpty() && !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text("写入草稿", maxLines = 1) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    onDiscard(chosen)
                                    selected = selected - chosen
                                },
                                enabled = chosen.isNotEmpty() && !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text("批量拒绝") }
                            TextButton(
                                onClick = onSupplement,
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text("补充细节") }
                        }
                    }
                }
            }
        }
        model.warnings.take(2).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
