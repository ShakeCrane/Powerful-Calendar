package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.powerfulcalendar.ui.v2.NativeLegacyDataReviewDialog
import dev.powerfulcalendar.ui.v2.NativeLegacyDataReviewDialogSlot
import dev.powerfulcalendar.ui.v2.NativeLegacyDataReviewKind

@Composable
fun NativeLegacyDataReview(
    kind: NativeLegacyDataReviewKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showDialog: NativeLegacyDataReviewDialogSlot
) {
    val title = when (kind) {
        NativeLegacyDataReviewKind.MIGRATE_DATABASE -> "迁移旧版事件？"
        NativeLegacyDataReviewKind.RESTORE_FLAT_BACKUP -> "恢复早期格式备份？"
    }
    val message = when (kind) {
        NativeLegacyDataReviewKind.MIGRATE_DATABASE ->
            "旧版数据库只会被只读打开，不会被删除或修改。每条旧事件会获得新的永久序号，" +
                "并标记来源为旧版迁移；重复执行不会产生重复记录。旧闹钟可能仍在原时钟中运行，" +
                "迁入后请先检查并关闭旧闹钟，再启用 PowerCal 提醒，避免重复响铃。"
        NativeLegacyDataReviewKind.RESTORE_FLAT_BACKUP ->
            "该文件是早期 flat backup v2，没有 SHA-256 或 lineageId，无法验证是否被修改。" +
                "仅在你能确认文件来源和完整性时继续。恢复后会被视为新的数据库 lineage，" +
                "并重建提醒调度；AI 授权不会恢复。"
    }
    val confirmLabel = when (kind) {
        NativeLegacyDataReviewKind.MIGRATE_DATABASE -> "开始迁移"
        NativeLegacyDataReviewKind.RESTORE_FLAT_BACKUP -> "仍要恢复"
    }
    showDialog(
        NativeLegacyDataReviewDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    )
}
