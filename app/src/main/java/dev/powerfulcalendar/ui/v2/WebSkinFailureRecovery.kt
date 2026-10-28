package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Native Host recovery surface for an editor retained after a skin checkpoint failure. */
@Composable
internal fun WebSkinFailureRecoveryDialog(
    recovery: WebSkinEditRecoverySnapshot,
    busy: Boolean,
    onRetry: () -> Unit
) {
    SkinAlertDialog(
        onDismissRequest = {},
        title = { Text("编辑暂存失败") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            ) {
                Text("皮肤已切换到原生安全界面。Host 仍保留这份编辑内容，但尚未成功写入；请勿强制结束应用。")
                Text(recovery.description)
                recovery.form?.let { form ->
                    Text("类型：${form.kind.label}")
                    UnifiedEventFormFields(
                        form = form,
                        expanded = true,
                        aiLocked = true,
                        instanceKey = recovery.description,
                        onFormChange = {}
                    )
                }
                recovery.error?.takeIf(String::isNotBlank)?.let {
                    Text("最近错误：${it.take(240)}")
                }
                Text("存储恢复后重试安全暂存。成功后，已修改内容会出现在待处理列表中；此操作不会发布正式事件。")
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry, enabled = !busy) {
                Text(if (busy) "正在重试…" else "重试安全暂存")
            }
        }
    )
}
