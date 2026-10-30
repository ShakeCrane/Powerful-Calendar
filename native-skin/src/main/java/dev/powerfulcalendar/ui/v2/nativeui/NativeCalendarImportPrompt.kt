package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.powerfulcalendar.ui.v2.NativeCalendarImportPromptDialog
import dev.powerfulcalendar.ui.v2.NativeCalendarImportPromptDialogSlot

/** Native presentation for the explicit one-time system-calendar import choice. */
@Composable
fun NativeCalendarImportPrompt(
    onImportOnce: () -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeCalendarImportPromptDialogSlot
) {
    showDialog(
        NativeCalendarImportPromptDialog(
            onDismissRequest = onDismiss,
            title = { Text("导入系统日历？") },
            text = {
                Text(
                    "可以读取设备当前的日历日程作为一次性快照。" +
                        "以后不会监测或同步原日历；也可以稍后在设置中手动导入。"
                )
            },
            confirmButton = {
                TextButton(onClick = onImportOnce) { Text("导入一次") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("暂不导入") }
            }
        )
    )
}
