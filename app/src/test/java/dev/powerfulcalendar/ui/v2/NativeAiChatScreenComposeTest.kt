package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.powerfulcalendar.ui.v2.nativeui.NativeAiChatScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeAiChatScreenComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun composerAndBatchMenuUseExplicitHostCallbacks() {
        val sent = mutableListOf<String>()
        val modes = mutableListOf<NativeAiBatchMode>()
        ui.setContent {
            MaterialTheme {
                NativeAiChatScreen(
                    messages = emptyList(),
                    busy = false,
                    batchActionsBusy = false,
                    pendingCount = 2,
                    onSend = sent::add,
                    onPropose = {},
                    onClear = {},
                    onClearCache = {},
                    onSettings = {},
                    onOpenDeck = {},
                    onPickImage = {},
                    onPickFile = {},
                    onPrepareBatch = modes::add,
                    onRewind = {},
                    onCopyMessage = {}
                )
            }
        }

        ui.onNodeWithText("发送消息…").performTextInput("  下周一  ")
        ui.onNodeWithText("发送").performClick()
        ui.onNodeWithText("待处理 2").performClick()
        ui.onNodeWithText("AI 批量发布 · 先选择范围").performClick()
        ui.onNodeWithText("待处理 2").performClick()
        ui.onNodeWithText("AI 批量丢弃 · 先选择范围").performClick()

        assertEquals(listOf("下周一"), sent)
        assertEquals(listOf(NativeAiBatchMode.PUBLISH, NativeAiBatchMode.DISCARD), modes)
    }

    @Test fun messageProposalCopyRewindAndHostSlotsRemainAvailable() {
        val proposed = mutableListOf<String>()
        val copied = mutableListOf<String>()
        val rewound = mutableListOf<Int>()
        ui.setContent {
            MaterialTheme {
                NativeAiChatScreen(
                    messages = listOf(NativeAiMessage(false, "识别结果消息", null)),
                    busy = false,
                    batchActionsBusy = false,
                    pendingCount = 0,
                    onSend = {},
                    onPropose = proposed::add,
                    onClear = {},
                    onClearCache = {},
                    onSettings = {},
                    onOpenDeck = {},
                    onPickImage = {},
                    onPickFile = {},
                    onPrepareBatch = {},
                    onRewind = rewound::add,
                    onCopyMessage = copied::add,
                    proposalPending = true,
                    proposalReviewContent = { disabled ->
                        androidx.compose.material3.Text("Review slot disabled=$disabled")
                    },
                    assistantAvatarContent = {
                        androidx.compose.material3.Text("Injected avatar slot")
                    }
                )
            }
        }

        ui.onNodeWithText("Injected avatar slot", useUnmergedTree = true).assertExists()
        ui.onNodeWithText("Review slot disabled=false").assertExists()
        ui.onNodeWithContentDescription("添加图片或文件").assertIsNotEnabled()
        ui.onNodeWithText("识别结果消息").performTouchInput { longClick() }
        ui.onNodeWithText("从此消息创建事件草稿").performClick()
        ui.onNodeWithText("识别结果消息").performTouchInput { longClick() }
        ui.onNodeWithText("复制文字").performClick()
        ui.onNodeWithText("识别结果消息").performTouchInput { longClick() }
        ui.onNodeWithText("回退到此消息").performClick()

        assertEquals(listOf("识别结果消息"), proposed)
        assertEquals(listOf("识别结果消息"), copied)
        assertEquals(listOf(0), rewound)
    }
}
