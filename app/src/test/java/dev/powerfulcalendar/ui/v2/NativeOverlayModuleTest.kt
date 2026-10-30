package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.DialogProperties
import dev.powerfulcalendar.ui.v2.nativeui.NativeOverlaySkin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NativeOverlayModuleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun nativeAlertRendersHostContentAndPreservesBothActionClosures() {
        val confirmed = AtomicInteger()
        val dismissed = AtomicInteger()
        val fallbackCalls = AtomicInteger()
        val context = OverlayVisualContext(
            type = OverlayType.ALERT,
            onDismissRequest = { dismissed.incrementAndGet() },
            modifier = Modifier,
            properties = DialogProperties(),
            title = { Text("Host 确认") },
            text = { Text("内容由 Host 提供") },
            confirmButton = {
                TextButton(onClick = { confirmed.incrementAndGet() }) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { dismissed.incrementAndGet() }) { Text("返回") }
            },
            icon = null,
            content = null,
            firstParty = { fallbackCalls.incrementAndGet() }
        )

        compose.setContent { MaterialTheme { NativeOverlaySkin.Render(context) } }

        compose.onNodeWithText("Host 确认").assertIsDisplayed()
        compose.onNodeWithText("内容由 Host 提供").assertIsDisplayed()
        compose.onNodeWithText("继续").performClick()
        compose.onNodeWithText("返回").performClick()

        assertEquals(1, confirmed.get())
        assertEquals(1, dismissed.get())
        assertEquals(0, fallbackCalls.get())
    }
}