package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeDeckDialog
import dev.powerfulcalendar.ui.v2.NativeDirectEditScope
import dev.powerfulcalendar.ui.v2.NativeDirectEventEditorContext

/** Native presentation for formal event edits; persistence and all business actions stay Host-owned. */
@Composable
fun NativeDirectEventEditor(context: NativeDirectEventEditorContext) {
    var confirmingDelete by remember(context.editorKey) { mutableStateOf(false) }
    var choosingType by remember(context.editorKey) { mutableStateOf(false) }

    key(context.editorKey, context.editorRevision) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                Modifier.padding(16.dp).heightIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("编辑事件 ${context.form.card.sequenceLabel}")
                    Box {
                        AssistChip(
                            onClick = { choosingType = true },
                            enabled = context.canChangeKind,
                            label = { Text(context.form.form.kind.label + " ▾") }
                        )
                        DropdownMenu(
                            expanded = choosingType,
                            onDismissRequest = { choosingType = false }
                        ) {
                            dev.powerfulcalendar.ui.v2.NativeDeckKind.entries.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(kind.label) },
                                    onClick = {
                                        choosingType = false
                                        context.onChangeKind(kind)
                                    }
                                )
                            }
                        }
                    }
                }
                if (context.requiresRecurrenceScope) {
                    Text(
                        "重复事件编辑范围（必须明确选择）",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        context.recurrenceScopes.forEach { option ->
                            FilterChip(
                                selected = option.selected,
                                onClick = { context.onChangeRecurrenceScope(option.scope) },
                                enabled = option.enabled && !context.hasChanges,
                                label = { Text(option.scope.label()) }
                            )
                        }
                    }
                    if (context.selectedRecurrenceScope == NativeDirectEditScope.SINGLE_OCCURRENCE) {
                        Text(
                            "仅本次可修改标题和时间；地点、备注、提醒与重复规则保持不变。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Keep the footer visible while fields scroll or the soft keyboard is open.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (!context.requiresRecurrenceScope || context.selectedRecurrenceScope != null) {
                        NativeDeckEventForm(context.form)
                    } else {
                        Text("请先选择编辑范围，再修改字段或保存。")
                    }
                    context.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = context.onSave,
                        enabled = !context.busy &&
                            (!context.requiresRecurrenceScope || context.selectedRecurrenceScope != null)
                    ) { Text("保存修改") }
                    OutlinedButton(
                        onClick = context.onDiscardChanges,
                        enabled = context.hasChanges && !context.busy
                    ) { Text("放弃本次修改") }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { confirmingDelete = true }, enabled = !context.busy) {
                        Text("删除事件")
                    }
                    TextButton(onClick = context.onExit, enabled = !context.busy) {
                        Text("存档并退出")
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        context.form.appearance.showDialog(
            NativeDeckDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text("确认删除事件？") },
                text = { Text("删除后不可撤销；请确认事件序号与内容。") },
                confirmButton = {
                    TextButton(onClick = { confirmingDelete = false; context.onDelete() }) {
                        Text("确认删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingDelete = false }) { Text("取消") }
                }
            )
        )
    }
}

private fun NativeDirectEditScope.label(): String = when (this) {
    NativeDirectEditScope.SINGLE_OCCURRENCE -> "仅本次"
    NativeDirectEditScope.THIS_AND_FUTURE -> "本次及以后"
    NativeDirectEditScope.WHOLE_SERIES -> "整个系列"
}
