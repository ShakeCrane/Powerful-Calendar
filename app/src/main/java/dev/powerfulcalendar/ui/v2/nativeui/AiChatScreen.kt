package dev.powerfulcalendar.ui.v2.nativeui

import dev.powerfulcalendar.ui.v2.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.powerfulcalendar.ai.AiBatchRunner
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiInterpretation

/**
 * Compact messenger-style shell. Text is ordinary chat; no ordinary message authorizes writes.
 * Explicit long-press on one message is the current scoped calendar-proposal entry.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NativeAiChatScreen(
    messages: List<AiChatMessage>,
    busy: Boolean,
    batchActionsBusy: Boolean = false,
    pendingCount: Int,
    onSend: (String) -> Unit,
    onPropose: (String) -> Unit,
    onClear: () -> Unit,
    onClearCache: () -> Unit,
    proposalReview: AiInterpretation? = null,
    onApplyProposals: (Set<Int>, Boolean) -> Unit = { _, _ -> },
    onDiscardProposals: (Set<Int>) -> Unit = {},
    onSupplementProposals: () -> Unit = {},
    onSettings: () -> Unit,
    onOpenDeck: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPrepareBatch: (AiBatchRunner.Mode) -> Unit,
    onRewind: (Int) -> Unit,
    batchSelectionActive: Boolean = false,
    onCancelBatchSelection: () -> Unit = {}
) {
    val actionsDisabled = busy || batchActionsBusy
    val proposalPending = proposalReview?.proposals?.isNotEmpty() == true
    val attachmentsDisabled = actionsDisabled || proposalPending
    var composer by rememberSaveable { mutableStateOf("") }
    var attachmentMenu by remember { mutableStateOf(false) }
    var pendingMenu by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisible < 0 || lastVisible >= messages.lastIndex - 2) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI 助手", style = MaterialTheme.typography.titleLarge)
                Text("聊天 · 文件理解 · 日程 Skills",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSettings) { Text("配置") }
            if (batchSelectionActive) {
                TextButton(onClick = onCancelBatchSelection, enabled = !busy) {
                    Text("取消选择")
                }
            }
            Box {
                TextButton(onClick = { pendingMenu = true }) {
                    Text("待处理 " + pendingCount)
                    Icon(Icons.Default.MoreVert, null)
                }
                DropdownMenu(expanded = pendingMenu, onDismissRequest = { pendingMenu = false }) {
                    DropdownMenuItem(text = { Text("处理事件") }, onClick = {
                        pendingMenu = false
                        onOpenDeck()
                    })
                    DropdownMenuItem(text = { Text("AI 批量发布 · 先选择范围") }, onClick = {
                        pendingMenu = false
                        onPrepareBatch(AiBatchRunner.Mode.PUBLISH)
                    }, enabled = !actionsDisabled && !proposalPending && pendingCount > 0)
                    DropdownMenuItem(text = { Text("AI 批量丢弃 · 先选择范围") }, onClick = {
                        pendingMenu = false
                        onPrepareBatch(AiBatchRunner.Mode.DISCARD)
                    }, enabled = !actionsDisabled && !proposalPending && pendingCount > 0)
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("清理 AI 临时缓存（保留聊天与事件）") }, onClick = {
                        pendingMenu = false
                        onClearCache()
                    }, enabled = !actionsDisabled)
                    DropdownMenuItem(text = { Text("清空聊天（不改变事件）") }, onClick = {
                        pendingMenu = false
                        onClear()
                    }, enabled = !actionsDisabled && messages.isNotEmpty())
                }
            }
        }

        if (messages.isEmpty()) {
            Text(
                "在这里直接聊天，或点 + 上传图片、ICS、文档和表格。" +
                    " 通常 AI 会主动提供结构化提案；长按消息创建仅用于识别失败后的手动补救。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { index, msg ->
                val mine = msg.role == "user"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Box(Modifier.fillMaxWidth(0.9f)) {
                        Surface(
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { selectedIndex = index }
                            ),
                            color = if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomEnd = if (mine) 4.dp else 16.dp,
                                bottomStart = if (mine) 16.dp else 4.dp
                            )
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (!mine && skinPainter(SkinSlot.AI_AVATAR) != null) {
                                        SkinImage(SkinSlot.AI_AVATAR, Modifier.size(19.dp))
                                    }
                                    Text(if (mine) "我" else "PowerCal AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 75,
                                    overflow = TextOverflow.Ellipsis)
                                if (!mine && msg.proposalSource != null) {
                                    TextButton(onClick = {
                                        onPropose(msg.proposalSource)
                                    }, enabled = !actionsDisabled) {
                                        Text("从此消息创建事件草稿")
                                    }
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = selectedIndex == index,
                            onDismissRequest = { selectedIndex = -1 }
                        ) {
                            DropdownMenuItem(
                                text = { Text("从此消息创建事件草稿") },
                                onClick = {
                                    selectedIndex = -1
                                    onPropose(msg.content)
                                }, enabled = !actionsDisabled
                            )
                            if (mine) DropdownMenuItem(
                                text = { Text("编辑并重新发送") },
                                onClick = {
                                    selectedIndex = -1
                                    composer = msg.content
                                    onRewind(index - 1)
                                }, enabled = !actionsDisabled
                            )
                            DropdownMenuItem(
                                text = { Text(if (mine) "撤回此消息及后续对话" else "回退到此消息") },
                                onClick = {
                                    selectedIndex = -1
                                    onRewind(if (mine) index - 1 else index)
                                }, enabled = !actionsDisabled
                            )
                            DropdownMenuItem(text = { Text("复制文字") }, onClick = {
                                selectedIndex = -1
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PowerCal 消息", msg.content))
                            })
                        }
                    }
                }
            }
        }

        proposalReview?.takeIf { it.proposals.isNotEmpty() }?.let { preview ->
            AiProposalReview(preview, actionsDisabled, onApplyProposals, onDiscardProposals,
                onSupplementProposals)
        }

        if (busy) Text("正在处理…", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box {
                IconButton(onClick = { attachmentMenu = true }, enabled = !attachmentsDisabled) {
                    Icon(Icons.Default.Add, "添加图片或文件")
                }
                DropdownMenu(expanded = attachmentMenu, onDismissRequest = { attachmentMenu = false }) {
                    DropdownMenuItem(text = { Text("添加图片 · 智能识别") }, onClick = {
                        attachmentMenu = false
                        onPickImage()
                    }, enabled = !attachmentsDisabled)
                    DropdownMenuItem(text = { Text("添加 ICS / 文档 / 表格") }, onClick = {
                        attachmentMenu = false
                        onPickFile()
                    }, enabled = !attachmentsDisabled)
                }
            }
            OutlinedTextField(
                value = composer,
                onValueChange = { if (it.length <= 6000) composer = it },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                maxLines = 4,
                placeholder = { Text("发送消息…") },
                enabled = !busy,
                shape = RoundedCornerShape(19.dp)
            )
            Button(
                onClick = {
                    if (composer.isNotBlank()) {
                        onSend(composer.trim())
                        composer = ""
                    }
                },
                enabled = !busy && composer.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = 52.dp)
            ) { Text("发送") }
        }
    }
}
