package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.powerfulcalendar.ui.v2.nativeui.NativeChromeSkin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeChromeModuleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun nativeRendererComposesHostRootContentWithoutInvokingTheFallbackSlot() {
        val fallbackCalls = AtomicInteger()
        val context = ChromeVisualContext(
            type = ChromeType.ROOT,
            message = null,
            modifier = Modifier,
            body = { Text("Host 根内容") },
            native = { fallbackCalls.incrementAndGet() }
        )

        compose.setContent { MaterialTheme { NativeChromeSkin.Render(context) } }

        compose.onNodeWithText("Host 根内容").assertIsDisplayed()
        assertEquals(0, fallbackCalls.get())
    }

    @Test
    fun nativeNoticePreservesPoliteLiveRegionSemantics() {
        val context = ChromeVisualContext(
            type = ChromeType.NOTICE,
            message = "已保存",
            modifier = Modifier,
            body = null,
            native = {}
        )

        compose.setContent { MaterialTheme { NativeChromeSkin.Render(context) } }

        val node = compose.onNodeWithText("已保存").assertIsDisplayed().fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config[SemanticsProperties.LiveRegion])
    }
}