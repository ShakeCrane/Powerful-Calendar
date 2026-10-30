package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeFormalWriteReviewDialog
import dev.powerfulcalendar.ui.v2.NativeFormalWriteReviewDialogSlot
import dev.powerfulcalendar.ui.v2.NativeFormalWriteReviewModel

/** Native review content; the Host supplies the trusted dialog shell and resolution callbacks. */
@Composable
fun NativeFormalWriteReview(
    model: NativeFormalWriteReviewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeFormalWriteReviewDialogSlot
) {
    showDialog(
        NativeFormalWriteReviewDialog(
            onDismissRequest = onDismiss,
            title = { Text(model.title) },
            text = {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    model.targetSummary?.let { Text(it) }
                    model.scopeSummary?.let { Text(it) }
                    model.occurrenceSummary?.let { Text(it) }
                    Text(model.explanation)
                    model.rows.forEach { row ->
                        Text("${row.label}：${row.before} → ${row.after}")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !model.resolving, onClick = onConfirm) {
                    Text(model.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(enabled = !model.resolving, onClick = onDismiss) {
                    Text("取消")
                }
            }
        )
    )
}
