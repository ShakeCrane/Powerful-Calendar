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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.EditSession

/** Host-owned confirmation surface; programmable skins cannot replace these persistence controls. */
@Composable
internal fun DirectEventEditorDialog(
    session: EditSession,
    onChange: (DraftForm) -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDelete: () -> Unit
) {
    val form = session.form
    SkinWindowDialog(onDismissRequest = onExit) {
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
                session.lastError?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave) { Text("Save") }
                    OutlinedButton(onClick = onDiscardChanges, enabled = session.meaningfulDiff) {
                        Text("Discard editing changes")
                    }
                    TextButton(onClick = onExit) { Text("Exit") }
                }
                TextButton(onClick = onDelete) { Text("Delete event") }
            }
        }
    }
}
