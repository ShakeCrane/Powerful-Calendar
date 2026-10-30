package dev.powerfulcalendar.ui.v2.nativeui

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
import dev.powerfulcalendar.ui.v2.NativeAiBatchAuthorizationDialog
import dev.powerfulcalendar.ui.v2.NativeAiBatchAuthorizationDialogSlot
import dev.powerfulcalendar.ui.v2.NativeAiBatchAuthorizationModel
import dev.powerfulcalendar.ui.v2.NativeAiBatchMode

/** Native frozen-batch review. Only the explicit confirmation callback can authorize Host work. */
@Composable
fun NativeAiBatchAuthorizationReview(
    model: NativeAiBatchAuthorizationModel,
    busy: Boolean,
    onAuthorize: () -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeAiBatchAuthorizationDialogSlot
) {
    val label = if (model.mode == NativeAiBatchMode.PUBLISH) "发布" else "丢弃"
    showDialog(
        NativeAiBatchAuthorizationDialog(
            onDismissRequest = onDismiss,
            title = { Text("一次性授权 AI $label") },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "范围固定为下列 ${model.count} 张草稿。卡组在处理期间只读，时间轴可以查看。你可随时停止，已完成结果会保留。"
                    )
                    Text(
                        if (model.mode == NativeAiBatchMode.DISCARD) {
                            "丢弃只是不执行这些提案，不会删除正式事件。"
                        } else {
                            "发布会执行每张提案的完整操作；冲突会保留给你确认。"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    model.operationCounts.forEach { item ->
                        Text("${item.operationLabel}提案 · ${item.count} 张",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    if (model.containsFormalDeletion) {
                        Text(
                            "包含正式删除操作：事件序号会退役，当前没有发布撤销功能。",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider()
                    model.rows.forEachIndexed { index, row ->
                        Text(
                            "${index + 1}. ${row.sequenceLabel} · ${row.operationLabel} · ${row.title.ifBlank { "未命名" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = model.count > 0 && !busy, onClick = onAuthorize) {
                    Text("授权并$label ${model.count} 张")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    )
}
