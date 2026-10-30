package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
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
import dev.powerfulcalendar.ui.v2.NativeTextDraftDialogContent
import dev.powerfulcalendar.ui.v2.NativeTextDraftDialogSlot

/** Native prompt presentation; parsing and draft creation remain Host actions. */
@Composable
fun NativeTextDraftDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    showDialog: NativeTextDraftDialogSlot
) {
    var text by remember { mutableStateOf("") }
    showDialog(
        NativeTextDraftDialogContent(
            onDismissRequest = onDismiss,
            title = { Text("用文字创建草稿") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "本地解析器先给出一个可编辑草稿；不会直接写入时间轴。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                        label = { Text("例如：明天下午 3 点开组会") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (text.isNotBlank()) onConfirm(text) },
                    enabled = text.isNotBlank()
                ) { Text("生成草稿") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    )
}
