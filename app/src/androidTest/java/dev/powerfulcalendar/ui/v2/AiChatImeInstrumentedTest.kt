package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.NativeAiChatScreen

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiChatImeInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openImeKeepsComposerAndSendVisibleAndResizesConversation() {
        compose.activity.runOnUiThread {
            compose.activity.window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(
                compose.activity.window, false
            )
        }
        compose.setContent {
            PowerCalTheme {
                SkinAppShell(forceNative = true) {
                    NativeAiChatScreen(
                        messages = listOf(NativeAiMessage(false, "IME_SCROLL_SENTINEL")),
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

        val conversationList = compose.onNode(hasScrollAction())
        val listBoundsBeforeIme = conversationList.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("发送消息…").performClick()
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithText("发送消息…").performTextInput("IME probe")
        compose.waitForIdle()

        val imeInsets = requireNotNull(
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        )
        val imeBottomPx = imeInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val sendNode = compose.onNodeWithText("发送").fetchSemanticsNode()
        val composerNode = compose.onNodeWithText("IME probe").fetchSemanticsNode()
        val messageNode = compose.onNodeWithText("IME_SCROLL_SENTINEL").fetchSemanticsNode()
        val listBoundsWithIme = conversationList.fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val imeTopInRoot = rootBounds.bottom - imeBottomPx

        compose.onNodeWithText("发送").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("IME_SCROLL_SENTINEL").assertIsDisplayed()
        assertTrue("IME should have a positive bottom inset", imeBottomPx > 0)
        assertTrue(
            "conversation viewport must resize with the IME; " +
                "before=$listBoundsBeforeIme after=$listBoundsWithIme",
            listBoundsWithIme.height < listBoundsBeforeIme.height
        )
        assertTrue(
            "send control must remain above the IME; root=$rootBounds imeBottom=$imeBottomPx " +
                "send=${sendNode.boundsInRoot}",
            sendNode.boundsInRoot.bottom <= imeTopInRoot + 2f
        )
        assertTrue(
            "conversation message must remain above the composer; " +
                "message=${messageNode.boundsInRoot} composer=${composerNode.boundsInRoot}",
            messageNode.boundsInRoot.bottom <= composerNode.boundsInRoot.top
        )
        println(
            "AI_IME_GEOMETRY root=$rootBounds imeBottomPx=$imeBottomPx " +
                "imeTopInRoot=$imeTopInRoot listBefore=$listBoundsBeforeIme " +
                "listWithIme=$listBoundsWithIme message=${messageNode.boundsInRoot} " +
                "composer=${composerNode.boundsInRoot} send=${sendNode.boundsInRoot}"
        )
    }
}
