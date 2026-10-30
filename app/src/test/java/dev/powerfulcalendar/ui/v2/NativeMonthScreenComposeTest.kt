package dev.powerfulcalendar.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import dev.powerfulcalendar.ui.v2.nativeui.NativeMonthScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeMonthScreenComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun hostRowsDriveDateCountsSelectionOpenCreateAndTimelineCallbacks() {
        val projectedMonths = mutableListOf<YearMonth>()
        val openedRows = mutableListOf<String>()
        val createdDates = mutableListOf<LocalDate>()
        val timelineDates = mutableListOf<LocalDate>()
        ui.setContent {
            MaterialTheme {
                NativeMonthScreen(
                    initialDate = LocalDate.of(2026, 10, 20),
                    today = LocalDate.of(2026, 10, 20),
                    aiLocked = false,
                    rowsForMonth = { month ->
                        projectedMonths += month
                        if (month == YearMonth.of(2026, 10)) listOf(
                            NativeMonthRow(
                                rowKey = "opaque-occurrence-token",
                                kind = NativeMonthKind.ACTIVITY,
                                kindLabel = "活动",
                                title = "跨日会议",
                                summary = "23:30 – 01:00",
                                sequenceLabel = "#0042",
                                visibleDateKeys = listOf("2026-10-23")
                            )
                        ) else emptyList()
                    },
                    onBack = {},
                    onDate = timelineDates::add,
                    onOpen = openedRows::add,
                    onCreate = createdDates::add,
                    appearance = appearance()
                )
            }
        }

        ui.onNodeWithContentDescription("2026-10-23，1 项").performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("活动 · 跨日会议"))
        ui.onNodeWithText("活动 · 跨日会议").assertIsDisplayed().performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("为这一天新建活动"))
        ui.onNodeWithText("为这一天新建活动").performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("在时间轴查看这一天"))
        ui.onNodeWithText("在时间轴查看这一天").performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("下个月"))
        ui.onNodeWithContentDescription("下个月").performClick()

        ui.runOnIdle {
            assertEquals(listOf("opaque-occurrence-token"), openedRows)
            assertEquals(listOf(LocalDate.of(2026, 10, 23)), createdDates)
            assertEquals(listOf(LocalDate.of(2026, 10, 23)), timelineDates)
            assertEquals(YearMonth.of(2026, 11), projectedMonths.last())
        }
    }

    @Test
    fun lockedMonthDisablesCreateButKeepsHostInspectionAndDialogSlots() {
        val openedRows = mutableListOf<String>()
        ui.setContent {
            MaterialTheme {
                NativeMonthScreen(
                    initialDate = LocalDate.of(2026, 10, 20),
                    today = LocalDate.of(2026, 10, 20),
                    aiLocked = true,
                    rowsForMonth = {
                        listOf(NativeMonthRow(
                            rowKey = "locked-row",
                            kind = NativeMonthKind.ALARM,
                            kindLabel = "闹钟",
                            title = "只读事项",
                            summary = "09:00",
                            sequenceLabel = "#0007",
                            visibleDateKeys = listOf("2026-10-20")
                        ))
                    },
                    onBack = {},
                    onDate = {},
                    onOpen = openedRows::add,
                    onCreate = { error("AI 锁定时不应创建事项") },
                    appearance = appearance()
                )
            }
        }

        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("闹钟 · 只读事项"))
        ui.onNodeWithText("闹钟 · 只读事项").assertIsDisplayed().performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("为这一天新建活动"))
        ui.onNodeWithText("为这一天新建活动").assertIsNotEnabled()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("2026 年"))
        ui.onNodeWithText("2026 年").performClick()
        ui.onNodeWithText("选择年份").assertIsDisplayed()
        ui.onNodeWithText("跳转").performClick()

        ui.runOnIdle { assertEquals(listOf("locked-row"), openedRows) }
    }

    private fun appearance() = NativeMonthAppearance(
        eventRowCornerRadiusDp = 20f,
        panel = { content ->
            Surface(shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(PaddingValues(0.dp)), content = { content() })
            }
        },
        showDialog = { dialog ->
            AlertDialog(
                onDismissRequest = dialog.onDismissRequest,
                title = dialog.title,
                text = dialog.text,
                confirmButton = dialog.confirmButton,
                dismissButton = dialog.dismissButton
            )
        },
        kindColors = NativeMonthKind.values().associateWith { Color.Blue.toArgb() }
    )
}
