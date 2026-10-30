package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.powerfulcalendar.ui.v2.NativeAiStuckBatchReleaseDialog
import dev.powerfulcalendar.ui.v2.NativeAiStuckBatchReleaseDialogSlot

@Composable
fun NativeAiStuckBatchReleaseReview(
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeAiStuckBatchReleaseDialogSlot
) {
    showDialog(
        NativeAiStuckBatchReleaseDialog(
            onDismissRequest = onDismiss,
            title = { Text("强制解除 AI 批次锁？") },
            text = {
                Text(
                    "仅在请求停止超过 10 分钟仍无响应时继续。当前执行结果数量可能无法确定；" +
                        "已提交的事件操作保留，未完成草稿保留待处理。旧批次授权会被作废，不能自动续跑。"
                )
            },
            confirmButton = {
                TextButton(enabled = canConfirm, onClick = onConfirm) {
                    Text("解除并作废旧授权")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("返回") }
            }
        )
    )
}
