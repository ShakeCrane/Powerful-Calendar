package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.NativeAiChatScreen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiChatSuggestionAffordanceInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun casualScheduleMentionOffersButtonOnlyUntilUserTapsIt() {
        val casualSource = "下周也许找时间开个会。"
        val actionLabel = "从此消息创建事件草稿"
        var proposedSource: String? = null
        val messages = listOf(
            NativeAiMessage(true, casualSource),
            NativeAiMessage(
                mine = false,
                content = "如果需要，可以从此消息创建事件草稿。",
                proposalSource = casualSource
            )
        )

        compose.setContent {
            PowerCalTheme {
                SkinAppShell(forceNative = true) {
                    NativeAiChatScreen(
                        messages = messages,
                        busy = false,
                        batchActionsBusy = false,
                        pendingCount = 0,
                        onSend = {},
                        onPropose = { proposedSource = it },
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

        compose.waitForIdle()
        val button = compose.onNodeWithText(actionLabel)
            .assertExists()
            .assertHasClickAction()
        compose.onNodeWithText("识别到新增 · 是否创建草稿？").assertDoesNotExist()
        compose.runOnIdle { assertNull("rendering a suggestion must not invoke proposal handling", proposedSource) }

        button.performClick()
        compose.runOnIdle { assertEquals(casualSource, proposedSource) }
    }
}
