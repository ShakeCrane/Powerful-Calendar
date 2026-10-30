package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.NativeAiChatScreen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiChatConversationAnchoringInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun initialHistory() = mutableStateListOf<NativeAiMessage>().apply {
        repeat(32) { index ->
            add(NativeAiMessage(true, "CHAT_HISTORY_${index.toString().padStart(2, '0')}"))
        }
    }

    private fun showChat(messages: List<NativeAiMessage>) {
        compose.setContent {
            PowerCalTheme {
                SkinAppShell(forceNative = true) {
                    NativeAiChatScreen(
                        messages = messages,
                        busy = false,
                        batchActionsBusy = false,
                        pendingCount = 0,
                        onSend = {},
                        onPropose = {},
                        onClear = {},
                        onClearCache = {},
                        onSettings = {},
                        onOpenDeck = {},
                        onPickImage = {},
                        onPickFile = {},
                        onPrepareBatch = {},
                        onRewind = {},
                        onCopyMessage = {}
                    )
                }
            }
        }
    }

    @Test
    fun newMessageWhileBottomAnchoredFollowsLatestMessage() {
        val messages = initialHistory()
        showChat(messages)
        compose.waitForIdle()

        compose.onNodeWithText("CHAT_HISTORY_31").assertIsDisplayed()
        compose.runOnIdle {
            messages.add(NativeAiMessage(false, "CHAT_LATEST_WHILE_AT_BOTTOM"))
        }
        compose.waitForIdle()

        val latest = compose.onNodeWithText("CHAT_LATEST_WHILE_AT_BOTTOM")
        val composer = compose.onNodeWithText("发送消息…")
        latest.assertIsDisplayed()
        val latestBounds = latest.fetchSemanticsNode().boundsInRoot
        val composerBounds = composer.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "latest message should remain above composer: latest=$latestBounds composer=$composerBounds",
            latestBounds.bottom <= composerBounds.top
        )
        println("AI_CHAT_BOTTOM_ANCHOR latest=$latestBounds composer=$composerBounds")
    }

    @Test
    fun newMessageWhileReadingHistoryDoesNotJumpToLatest() {
        val messages = initialHistory()
        showChat(messages)
        compose.waitForIdle()

        val list = compose.onNode(hasScrollAction())
        list.performScrollToIndex(0)
        compose.waitForIdle()
        compose.onNodeWithText("CHAT_HISTORY_00").assertIsDisplayed()

        compose.runOnIdle {
            messages.add(NativeAiMessage(false, "CHAT_LATEST_WHILE_READING"))
        }
        compose.waitForIdle()

        compose.onNodeWithText("CHAT_HISTORY_00").assertIsDisplayed()
        compose.onNodeWithText("CHAT_LATEST_WHILE_READING").assertDoesNotExist()
        println("AI_CHAT_HISTORY_ANCHOR firstMessage=visible latestMessage=offscreen")
    }
}
