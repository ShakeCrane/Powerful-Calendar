package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalRecord

/** A read-only, frozen preview. The only write authorization is the explicit confirm callback. */
@Composable
internal fun AiBatchAuthorizationReview(
    mode: AiBatchRunner.Mode,
    cards: List<DraftCard>,
    busy: Boolean,
    onAuthorize: (List<DraftCard>) -> Unit,
    onDismiss: () -> Unit
) {
    val snapshot = cards.toList()
    val label = if (mode == AiBatchRunner.Mode.PUBLISH) "发布" else "丢弃"
    SkinAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("一次性授权 AI $label") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "范围固定为下列 ${snapshot.size} 张草稿。卡组在处理期间只读，时间轴可以查看。你可随时停止，已完成结果会保留。"
                )
                Text(
                    if (mode == AiBatchRunner.Mode.DISCARD) "丢弃只是不执行这些提案，不会删除正式事件。"
                    else "发布会执行每张提案的完整操作；冲突会保留给你确认。",
                    style = MaterialTheme.typography.bodyMedium
                )
                snapshot.groupingBy { it.operation }.eachCount().forEach { (operation, count) ->
                    Text("${operation.label}提案 · $count 张", style = MaterialTheme.typography.labelLarge)
                }
                if (mode == AiBatchRunner.Mode.PUBLISH && snapshot.any { it.operation == DraftOperation.DELETE }) {
                    Text(
                        "包含正式删除操作：事件序号会退役，当前没有发布撤销功能。",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider()
                snapshot.forEachIndexed { index, card ->
                    Text(
                        "${index + 1}. ${TemporalRecord.formatSeq(card.eventSeq)} · ${card.operation.label} · ${card.form.title.ifBlank { "未命名" }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = snapshot.isNotEmpty() && !busy, onClick = {
                onAuthorize(snapshot)
            }) { Text("授权并$label ${snapshot.size} 张") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
