package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeAiInputReviewDialog
import dev.powerfulcalendar.ui.v2.NativeAiInputReviewDialogSlot

@Composable
fun NativeAiImageConsentReview(
    busy: Boolean,
    onSendOriginal: () -> Unit,
    onLocalOcr: () -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeAiInputReviewDialogSlot
) {
    showDialog(
        NativeAiInputReviewDialog(
            onDismissRequest = onDismiss,
            title = { Text("图片识别方式") },
            text = {
                Text(
                    "如果当前 AI 服务支持视觉，可以直接上传图片由模型读图。图片可能含个人信息；" +
                        "只有点击「发送原图给 AI」才会向配置的端点上传。也可以只在本地 OCR，之后核对文字。"
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = onSendOriginal) {
                    Text("发送原图给 AI")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onLocalOcr) { Text("仅本地 OCR") }
                }
            }
        )
    )
}

@Composable
fun NativeAiOcrTextReview(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeAiInputReviewDialogSlot
) {
    showDialog(
        NativeAiInputReviewDialog(
            onDismissRequest = onDismiss,
            title = { Text("核对 OCR 识别原文") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "先修正 OCR 错字，然后发送给 AI 理解；缺少日期、时刻或结束时间时 AI 应先追问。不会自动创建或发布事件。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
                        label = { Text("识别原文（可编辑）") }
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { onSubmit(text) }) {
                    Text("发送给 AI 理解")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    )
}
