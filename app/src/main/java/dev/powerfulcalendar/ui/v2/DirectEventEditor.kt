package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.UnifiedEventFormFields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.RecurrenceEditScope

/** Host-owned confirmation surface; programmable skins cannot replace these persistence controls. */
@Composable
internal fun DirectEventEditorDialog(
    session: EditSession,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    busy: Boolean = false,
    editorRevision: Int = 0,
    onChange: (DraftForm) -> Unit,
    onScopeChange: (RecurrenceEditScope) -> Unit = {},
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    SkinWindowDialog(onDismissRequest = onExit) {
        DirectEventEditorContent(session, form, error, hasChanges, busy, editorRevision, onChange,
            onScopeChange, onSave, onExit, onDiscardChanges, onDelete)
    }
}

@Composable
internal fun DirectEventEditorContent(
    session: EditSession,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    busy: Boolean = false,
    editorRevision: Int = 0,
    onChange: (DraftForm) -> Unit,
    onScopeChange: (RecurrenceEditScope) -> Unit = {},
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmingDelete by remember(session) { mutableStateOf(false) }
    var choosingType by remember(session) { mutableStateOf(false) }
    androidx.compose.runtime.key(session, editorRevision) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                Modifier.padding(16.dp).heightIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("编辑事件 ${dev.powerfulcalendar.core.TemporalRecord.formatSeq(session.eventSeq)}")
                    Box {
                        AssistChip(
                            onClick = { choosingType = true },
                            enabled = session.recurrenceScope != RecurrenceEditScope.SINGLE_OCCURRENCE,
                            label = { Text(form.kind.label + " ▾") }
                        )
                        DropdownMenu(
                            expanded = choosingType,
                            onDismissRequest = { choosingType = false }
                        ) {
                            TemporalKind.entries.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(kind.label) },
                                    onClick = {
                                        choosingType = false
                                        onChange(TemporalConversion.convert(form, kind).form)
                                    }
                                )
                            }
                        }
                    }
                }
                if (session.requiresRecurrenceScope) {
                    Text("重复事件编辑范围（必须明确选择）",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecurrenceEditScope.entries.forEach { scope ->
                            val scopeAvailable = scope == RecurrenceEditScope.WHOLE_SERIES ||
                                session.canEditSelectedOccurrence
                            FilterChip(
                                selected = session.recurrenceScope == scope,
                                onClick = { onScopeChange(scope) },
                                enabled = scopeAvailable && !hasChanges,
                                label = { Text(when (scope) {
                                    RecurrenceEditScope.SINGLE_OCCURRENCE -> "仅本次"
                                    RecurrenceEditScope.THIS_AND_FUTURE -> "本次及以后"
                                    RecurrenceEditScope.WHOLE_SERIES -> "整个系列"
                                }) }
                            )
                        }
                    }
                    if (session.recurrenceScope == RecurrenceEditScope.SINGLE_OCCURRENCE) {
                        Text("仅本次可修改标题和时间；地点、备注、提醒与重复规则保持不变。",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Field content scrolls; the action footer remains visible while
                // editing long reminder/recurrence forms or using the soft keyboard.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (!session.requiresRecurrenceScope || session.recurrenceScope != null) {
                        UnifiedEventFormFields(
                            form = form,
                            expanded = true,
                            aiLocked = false,
                            instanceKey = session.eventSeq,
                            occurrenceOnlyScope = session.recurrenceScope ==
                                RecurrenceEditScope.SINGLE_OCCURRENCE,
                            onFormChange = onChange
                        )
                    } else {
                        Text("请先选择编辑范围，再修改字段或保存。")
                    }
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onSave,
                        enabled = !busy && (!session.requiresRecurrenceScope ||
                            session.recurrenceScope != null)
                    ) { Text("保存修改") }
                    OutlinedButton(onClick = onDiscardChanges, enabled = hasChanges && !busy) {
                        Text("放弃本次修改")
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { confirmingDelete = true }, enabled = !busy) { Text("删除事件") }
                    TextButton(onClick = onExit, enabled = !busy) { Text("存档并退出") }
                }
            }
        }
    }
    if (confirmingDelete) {
        SkinAlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("确认删除事件？") },
            text = { Text("删除后不可撤销；请确认事件序号与内容。") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("取消") }
            }
        )
    }
}
