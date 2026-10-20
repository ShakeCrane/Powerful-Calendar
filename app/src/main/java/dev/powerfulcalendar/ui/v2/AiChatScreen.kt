package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiChatMessage

/**
 * Chat is a pure conversation view. Calendar proposal is an explicit, separate action.
 * No model text in this component is a PowerStore authorization or a tool invocation.
 */
@Composable
fun AiChatScreen(
    messages: List<AiChatMessage>,
    busy: Boolean,
    onSend: (String) -> Unit,
    onPropose: (String) -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    onOpenDeck: () -> Unit
) {
    var composer by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI 助手", style = MaterialTheme.typography.headlineMedium)
                Text("正常聊天不读取日历，日程操作需单独生成草稿",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSettings) { Text("配置") }
            TextButton(onClick = onOpenDeck) { Text("待处理") }
        }

        if (messages.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    "可以直接聊天、提问、讨论计划。需要修改日历时，在下方使用“生成草稿”，" +
                        "结果会进入待处理，不会自动发布。\n\n" +
                        "本阶段聊天暂存于当前 App 会话；不会自动备份聊天记录。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(messages) { index, msg ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.94f),
                        color = if (msg.role == "user")
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = GlassShapes.card
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (msg.role == "user") "我" else "AI",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = composer,
            onValueChange = { if (it.length <= 6000) composer = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp),
            maxLines = 4,
            label = { Text("消息或日程要求") },
            placeholder = { Text("例如：帮我分析一下这周的安排思路") },
            enabled = !busy
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                enabled = !busy && (composer.isNotBlank() || messages.any { it.role == "user" }),
                onClick = {
                    val selected = composer.ifBlank {
                        messages.lastOrNull { it.role == "user" }?.content.orEmpty()
                    }
                    if (selected.isNotBlank()) {
                        onPropose(selected)
                        composer = ""
                    }
                }
            ) { Text("生成草稿") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear, enabled = !busy && messages.isNotEmpty()) {
                Text("清空聊天")
            }
            Button(
                enabled = !busy && composer.isNotBlank(),
                onClick = {
                    onSend(composer)
                    composer = ""
                }
            ) { Text(if (busy) "请求中" else "发送") }
        }
        if (busy) Text("AI 正在回复…", style = MaterialTheme.typography.labelSmall)
    }
}
