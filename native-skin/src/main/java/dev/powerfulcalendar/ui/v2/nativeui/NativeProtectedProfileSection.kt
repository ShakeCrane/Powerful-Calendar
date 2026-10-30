package dev.powerfulcalendar.ui.v2.nativeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ui.v2.NativeProtectedProfileActions
import dev.powerfulcalendar.ui.v2.NativeProtectedProfileDisplay

/** Native-only profile presentation. The Host supplies values and persists explicit saves. */
@Composable
fun NativeProtectedProfileSection(
    display: NativeProtectedProfileDisplay,
    actions: NativeProtectedProfileActions,
    cardShape: Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("个人信息", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Text("本机资料 · 不上传给 AI", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "头像与云端资料将在皮肤系统完成后支持；个人资料不是日历业务数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = display.displayName,
                onValueChange = { value -> if (value.length <= 80) actions.onDisplayNameChange(value) },
                label = { Text("你的名字（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedButton(
                onClick = actions.onSave,
                modifier = Modifier
            ) { Text("保存个人信息") }
        }
    }
}
