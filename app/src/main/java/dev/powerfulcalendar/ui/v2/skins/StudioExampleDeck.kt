package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.core.TemporalKind

/**
 * Functional alternate Draft Deck. The renderer NEVER receives DeckController.
 * Its buttons are constrained by DeckVisualContext actions and all durability rules
 * remain host-mediated. A switch to the original advanced editor is always available.
 */
internal class StudioExampleDeck : DeckVisualRenderer {
    override val id = "studio-example/deck"
    @Composable override fun Render(context: DeckVisualContext) {
        var native by rememberSaveable { mutableStateOf(false) }
        if (native) {
            context.Native()
            return
        }
        Dialog(
            onDismissRequest = context.onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
        ) {
            Surface(Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().heightIn(max = 700.dp)
                    .verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Draft Atelier", style = MaterialTheme.typography.headlineSmall)
                    Text("草稿事务安全由宿主管理", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = context.onDismiss) { Text("暂存并关闭") }
                        TextButton(onClick = { native = true }) { Text("完整原生编辑器") }
                    }
                    context.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (context.state.aiLocked) Text("AI 批次执行中：草稿只读")
                    if (context.state.completion != null) {
                        Text("处理完成")
                        Button(onClick = context.onDismiss) { Text("返回") }
                    } else {
                        val form = context.state.form
                        val card = context.state.current
                        if (card != null && form != null) {
                            Text("草稿 " + card.draftId + "  " +
                                (context.state.position + 1) + "/" + context.state.cards.size)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { context.onBrowse(-1) },
                                    enabled = !context.busy && context.state.canGoPrevious) { Text("上一张") }
                                OutlinedButton(onClick = { context.onBrowse(1) },
                                    enabled = !context.busy && context.state.canGoNext) { Text("下一张") }
                            }
                            Text("类型：" + form.kind.label)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TemporalKind.entries.forEach { kind ->
                                    TextButton(onClick = { context.onConvert(kind) },
                                        enabled = !context.busy) { Text(kind.label) }
                                }
                            }
                            OutlinedTextField(value = form.title,
                                onValueChange = { context.onEdit(form.copy(title = it)) },
                                label = { Text("标题") }, modifier = Modifier.fillMaxWidth(),
                                enabled = !context.busy)
                            if (form.kind == TemporalKind.ALARM || form.kind == TemporalKind.ACTIVITY) {
                                OutlinedTextField(value = form.startText,
                                    onValueChange = { context.onEdit(form.copy(startText = it)) },
                                    label = { Text("开始时间 yyyy-MM-dd HH:mm") },
                                    modifier = Modifier.fillMaxWidth(), enabled = !context.busy)
                            }
                            if (form.kind == TemporalKind.ACTIVITY || form.kind == TemporalKind.DEADLINE) {
                                OutlinedTextField(value = form.endText,
                                    onValueChange = { context.onEdit(form.copy(endText = it)) },
                                    label = { Text(if (form.kind == TemporalKind.DEADLINE) "截止" else "结束") },
                                    modifier = Modifier.fillMaxWidth(), enabled = !context.busy)
                            }
                            OutlinedTextField(value = form.notes,
                                onValueChange = { context.onEdit(form.copy(notes = it)) },
                                label = { Text("备注") }, modifier = Modifier.fillMaxWidth(),
                                enabled = !context.busy)
                            Text("发布前请核对重复规则、提醒方式及时间精度。",
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = context.onCommit,
                                    enabled = !context.busy && form.isPublishable()) { Text("正式发布") }
                                OutlinedButton(onClick = context.onDiscard,
                                    enabled = !context.busy) { Text("丢弃草稿") }
                            }
                            if (context.state.dirty) {
                                TextButton(onClick = context.onRetrySave) { Text("重新暂存") }
                            }
                        } else {
                            Text("暂无草稿")
                            if (context.state.aiLocked) {
                                OutlinedButton(onClick = context.onCancelAiBatch) {
                                    Text("请求停止 AI 批次")
                                }
                            }
                        }
                        if (context.state.canUndoDiscard) {
                            TextButton(onClick = context.onUndoDiscard) { Text("撤销最近一次丢弃") }
                        }
                        if (context.state.conflict != null) {
                            Text("草稿存在版本冲突，请使用差异比较",
                                color = MaterialTheme.colorScheme.error)
                            Button(onClick = { native = true }) { Text("查看原生冲突详情") }
                            TextButton(onClick = context.onKeepConflict) { Text("保留现有版本") }
                        }
                        if (context.state.restore != null) {
                            Button(onClick = context.onConfirmRestore) { Text("确认恢复") }
                            TextButton(onClick = context.onDeclineRestore) { Text("取消恢复") }
                        }
                    }
                }
            }
        }
    }
}
