package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Verifies that the actual v2 ZIP is decoded and displayed in Compose without recompiling skin Kotlin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h760dp-xhdpi")
class RuntimeSkinComposeTest {
    @get:Rule val ui = createComposeRule()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 21)
        .atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Before fun importDemo() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val file = listOf(
            File(cwd, "tools/skin-studio/examples/Nebula-Flux-v2.pcskin"),
            File(cwd, "../tools/skin-studio/examples/Nebula-Flux-v2.pcskin")
        ).firstOrNull { it.isFile }
        assertNotNull("Missing checked-in v2 sample package", file)
        val manager = SkinPackageManager(ApplicationProvider.getApplicationContext())
        val decoded = manager.decode(file!!.readBytes(), false)
        assertEquals(10, decoded.pack.runtime?.screens?.size)
        PowerCalSkinRegistry.activate(decoded.pack)
    }

    @After fun recover() {
        RuntimePickerRequest.dismiss()
        PowerCalSkinRegistry.reset()
    }

    @Test fun v2TimelineReplacesTreeAndInvokesOnlyHostCallbacks() {
        var today = 0
        var memo = 0
        var created: TemporalKind? = null
        val record = TemporalRecord(101, 1, TemporalKind.ACTIVITY, "RUNTIME EVENT",
            startMillis = noon, endMillis = noon + 3600_000)
        ui.setContent {
            PowerCalTheme {
                SkinnableTimelineScreen(
                    records = listOf(record),
                    camera = TimelineCoordinate.Camera(noon, 24.0),
                    nowMillis = noon, zone = zone,
                    pendingCount = 3, aiLocked = false,
                    onCameraChange = {}, onOpenEvent = {}, onMoveEvent = { _, _ -> },
                    onCreate = { kind, _ -> created = kind },
                    onToday = { today++ }, onOpenDeck = {}, onOpenTools = {},
                    onOpenMemo = { memo++ }
                )
            }
        }
        ui.onNodeWithText("NEBULA / DAILY COMMAND").assertIsDisplayed()
        ui.onNodeWithText("RUNTIME EVENT").assertExists()
        ui.onNodeWithText("今天").performClick()
        ui.onNodeWithText("Memo").performClick()
        ui.onNodeWithText("新建活动").performClick()
        ui.runOnIdle {
            assertEquals(1, today)
            assertEquals(1, memo)
            assertEquals(TemporalKind.ACTIVITY, created)
        }
    }

    @Test fun v2CanReturnToNativeTimelineWithoutLosingActions() {
        ui.setContent { PowerCalTheme {
            SkinnableTimelineScreen(
                records = emptyList(), camera = TimelineCoordinate.Camera(noon, 24.0),
                nowMillis = noon, zone = zone, pendingCount = 0, aiLocked = false,
                onCameraChange = {}, onOpenEvent = {}, onMoveEvent = { _, _ -> },
                onCreate = { _, _ -> }, onToday = {}, onOpenDeck = {}, onOpenTools = {}
            )
        } }
        ui.onNodeWithText("查看完整原生页面").performClick()
        ui.onNodeWithContentDescription("今日 · 复位为全天").assertExists()
        ui.onNodeWithText("返回皮肤").performClick()
        ui.onNodeWithText("NEBULA / DAILY COMMAND").assertExists()
    }

    @Test fun v2DialogKeepsConfirmationAndDismissActions() {
        var authorized = false
        var dismissed = false
        ui.setContent { PowerCalTheme {
            SkinAlertDialog(
                title = { Text("授权确认") },
                text = { Text("确认之前不会执行操作") },
                onDismissRequest = { dismissed = true },
                confirmButton = { TextButton(onClick = { authorized = true }) { Text("确定执行") } },
                dismissButton = { TextButton(onClick = { dismissed = true }) { Text("取消") } }
            )
        } }
        ui.onNodeWithText("授权确认").assertIsDisplayed()
        ui.onNodeWithText("确认之前不会执行操作").assertIsDisplayed()
        ui.runOnIdle { assertFalse(authorized); assertFalse(dismissed) }
        ui.onNodeWithText("确定执行").performClick()
        ui.runOnIdle { assertTrue(authorized); assertFalse(dismissed) }
    }

    @Test fun v2PickerShowsCustomComposeDateAndRequiresConfirm() {
        var chosen: LocalDate? = null
        ui.setContent { PowerCalTheme {
            val context = LocalContext.current
            SkinAppShell {
                Button(onClick = {
                    SkinPickers.pickDate(context, noon, zone) { chosen = it }
                }) { Text("Open picker") }
            }
        } }
        ui.onNodeWithText("Open picker").performClick()
        ui.onNodeWithText("选择日期").assertIsDisplayed()
        ui.runOnIdle { assertNull(chosen) }
        ui.onNodeWithText("确定").performClick()
        ui.runOnIdle { assertEquals(LocalDate.of(2026, 10, 21), chosen) }
    }

    @Test fun chromeAlwaysPreservesHostBodyAndDynamicNavigation() {
        var selected: VisualTab? = VisualTab.TIMELINE
        ui.setContent { PowerCalTheme {
            SkinAppShell {
                Box(Modifier.fillMaxSize()) {
                    Text("HOST CONTENT ALIVE")
                    PowerCalNavigation(selected) { selected = it }
                }
            }
        } }
        ui.onNodeWithText("HOST CONTENT ALIVE").assertExists()
        ui.onNodeWithText("月历").performClick()
        ui.runOnIdle { assertEquals(VisualTab.MONTH, selected) }
    }
}
