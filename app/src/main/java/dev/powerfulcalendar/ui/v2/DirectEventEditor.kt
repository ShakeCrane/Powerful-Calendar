package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

/** Host-owned confirmation surface; programmable skins cannot replace these persistence controls. */
@Composable
internal fun DirectEventEditorDialog(
    session: EditSession,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    onChange: (DraftForm) -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    SkinWindowDialog(onDismissRequest = onExit) {
        DirectEventEditorContent(session, form, error, hasChanges, onChange, onSave, onExit,
            onDiscardChanges, onDelete)
    }
}

@Composable
internal fun DirectEventEditorContent(
    session: EditSession,
    form: DraftForm,
    error: String?,
    hasChanges: Boolean,
    onChange: (DraftForm) -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmingDelete by remember(session) { mutableStateOf(false) }
    androidx.compose.material3.Surface(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(
                Modifier.padding(16.dp).heightIn(max = 760.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Edit ${dev.powerfulcalendar.core.TemporalRecord.formatSeq(session.eventSeq)}")
                OutlinedTextField(form.title, { onChange(form.copy(title = it)) },
                    Modifier.fillMaxWidth(), label = { Text("Title") })
                OutlinedTextField(form.startText, { onChange(form.copy(startText = it)) },
                    Modifier.fillMaxWidth(), label = { Text("Start / date") })
                if (form.kind.name == "ACTIVITY" || form.kind.name == "MEMO") {
                    OutlinedTextField(form.endText, { onChange(form.copy(endText = it)) },
                        Modifier.fillMaxWidth(), label = { Text("End") })
                }
                OutlinedTextField(form.location, { onChange(form.copy(location = it)) },
                    Modifier.fillMaxWidth(), label = { Text("Location") })
                OutlinedTextField(form.notes, { onChange(form.copy(notes = it)) },
                    Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 3)
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave) { Text("Save") }
                    OutlinedButton(onClick = onDiscardChanges, enabled = hasChanges) {
                        Text("Discard editing changes")
                    }
                    TextButton(onClick = onExit) { Text("Exit") }
                }
                TextButton(onClick = { confirmingDelete = true }) { Text("Delete event") }
            }
    }
    if (confirmingDelete) {
        SkinAlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete event?") },
            text = { Text("This permanently deletes the event and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text("Confirm delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        )
    }
}
