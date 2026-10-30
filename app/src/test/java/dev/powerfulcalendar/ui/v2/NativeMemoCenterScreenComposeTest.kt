package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import dev.powerfulcalendar.ui.v2.nativeui.NativeMemoCenterScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeMemoCenterScreenComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun filtersRowsAndReturnsOpaqueKeysForHostOwnedActions() {
        val pendingKey = "91:2026-10-21T07:30"
        val completedKey = "92:single"
        val toggled = mutableListOf<String>()
        val opened = mutableListOf<String>()
        ui.setContent {
            MaterialTheme {
                NativeMemoCenterScreen(
                    items = listOf(
                        NativeMemoRow(pendingKey, "待处理事项", false, "闹钟", "2026-10-21", true, "#0091"),
                        NativeMemoRow(completedKey, "已完成事项", true, "备忘", "无日期", false, "#0092")
                    ),
                    busy = false,
                    onBack = {},
                    onCreateMemo = {},
                    onToggle = toggled::add,
                    onOpen = opened::add
                )
            }
        }

        ui.onNodeWithText("待处理事项").assertExists().performClick()
        ui.onNode(isToggleable()).performClick()
        ui.onNodeWithText("查看已完成").performClick()
        ui.onNodeWithText("待处理事项").assertDoesNotExist()
        ui.onNodeWithText("已完成事项").assertExists().performClick()

        assertEquals(listOf(pendingKey), toggled)
        assertEquals(listOf(pendingKey, completedKey), opened)
    }

    @Test fun hostResolvesOpaqueActionKeyBackToTheOriginalTodoEntry() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val millis = java.time.LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val owner = dev.powerfulcalendar.core.TemporalRecord(
            eventSeq = 93,
            revision = 2,
            kind = dev.powerfulcalendar.core.TemporalKind.ALARM,
            title = "宿主映射测试",
            startMillis = millis,
            trackingEnabled = true,
            zoneId = zone.id,
            recurrence = dev.powerfulcalendar.core.Recurrence(
                freq = dev.powerfulcalendar.core.RecurrenceFreq.DAILY,
                count = 3
            )
        )
        val entry = TodoEntry(owner, "2026-10-21T07:30", millis, owner.title)
        var toggled: TodoEntry? = null
        val previousRenderer = PresentationSkinRegistry.memo
        PresentationSkinRegistry.useMemo(null)
        try {
            ui.setContent {
                PowerCalTheme {
                    MemoCenterScreen(
                        items = listOf(entry),
                        zone = zone,
                        busy = false,
                        onBack = {},
                        onCreateMemo = {},
                        onToggle = { toggled = it },
                        onOpen = { _, _ -> }
                    )
                }
            }
            ui.onNode(isToggleable()).performClick()
            ui.runOnIdle { assertEquals(entry, toggled) }
        } finally {
            PresentationSkinRegistry.useMemo(previousRenderer)
        }
    }

}
