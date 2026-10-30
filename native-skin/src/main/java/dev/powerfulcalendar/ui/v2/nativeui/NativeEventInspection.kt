package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.powerfulcalendar.ui.v2.NativeEventInspectionDialog
import dev.powerfulcalendar.ui.v2.NativeEventInspectionDialogSlot
import dev.powerfulcalendar.ui.v2.NativeEventInspectionModel

/** Native read-only details shown while an AI batch locks formal event operations. */
@Composable
fun NativeEventInspection(
    model: NativeEventInspectionModel,
    onDismiss: () -> Unit,
    showDialog: NativeEventInspectionDialogSlot,
    readOnlyMessage: String = "AI 处理中，卡组只读。",
    dismissButtonLabel: String = "关闭"
) {
    showDialog(
        NativeEventInspectionDialog(
            onDismissRequest = onDismiss,
            title = { Text(model.title) },
            text = {
                Column {
                    Text(model.summary)
                    Text(model.notes)
                    Text(readOnlyMessage)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(dismissButtonLabel) }
            }
        )
    )
}
