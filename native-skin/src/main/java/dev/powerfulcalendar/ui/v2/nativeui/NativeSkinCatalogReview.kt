package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.powerfulcalendar.ui.v2.NativeSkinCatalogDialog
import dev.powerfulcalendar.ui.v2.NativeSkinCatalogDialogSlot
import dev.powerfulcalendar.ui.v2.NativeSkinCatalogReviewKind

@Composable
fun NativeSkinCatalogReview(
    kind: NativeSkinCatalogReviewKind,
    skinName: String,
    recoveryGesture: String = "",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeSkinCatalogDialogSlot
) {
    val title = when (kind) {
        NativeSkinCatalogReviewKind.ACTIVATE -> "切换皮肤前请确认"
        NativeSkinCatalogReviewKind.DELETE -> "删除皮肤？"
    }
    val message = when (kind) {
        NativeSkinCatalogReviewKind.ACTIVATE -> """即将启用“$skinName”。第三方皮肤可能遮挡设置入口，甚至导致界面无法操作。

$recoveryGesture

回退不会删除日历事件、草稿或已安装皮肤。导入新皮肤本身不会自动启用。""".trimIndent()
        NativeSkinCatalogReviewKind.DELETE ->
            "确认删除“$skinName”？此操作仅删除皮肤素材，日历事件不会改变。"
    }
    val confirmLabel = when (kind) {
        NativeSkinCatalogReviewKind.ACTIVATE -> "确认切换"
        NativeSkinCatalogReviewKind.DELETE -> "确认删除"
    }
    showDialog(
        NativeSkinCatalogDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    )
}
