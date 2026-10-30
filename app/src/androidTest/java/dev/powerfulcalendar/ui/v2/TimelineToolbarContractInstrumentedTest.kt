package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.TimelineScreen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TimelineToolbarContractInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.of(2026, 10, 25).atTime(12, 0).atZone(zone)
        .toInstant().toEpochMilli()

    private fun renderToolbar(
        pendingCount: Int,
        memoCount: Int,
        onToday: () -> Unit = {},
        onMemo: () -> Unit = {},
        onDeck: () -> Unit = {},
        onCreate: (TemporalKind, Long) -> Unit = { _, _ -> }
    ) {
        compose.setContent {
            PowerCalTheme {
                TimelineScreen(
                    records = emptyList(),
                    camera = TimelineCoordinate.Camera(noon, 24.0),
                    nowMillis = noon,
                    zone = zone,
                    pendingCount = pendingCount,
                    aiLocked = false,
                    onCameraChange = {},
                    onOpenEvent = {},
                    onMoveEvent = { _, _ -> },
                    onCreate = onCreate,
                    onToday = onToday,
                    onOpenDeck = onDeck,
                    onOpenTools = {},
                    onOpenMonth = {},
                    onOpenMemo = onMemo,
                    memoCount = memoCount
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun toolbarActionsRenderAndActivateInContractOrder() {
        val actions = mutableListOf<String>()
        var createdKind: TemporalKind? = null
        renderToolbar(
            pendingCount = 2,
            memoCount = 3,
            onToday = { actions.add("today") },
            onMemo = { actions.add("memo") },
            onDeck = { actions.add("todo") },
            onCreate = { kind, _ -> actions.add("new"); createdKind = kind }
        )

        val descriptions = listOf(
            "今日 · 复位为全天",
            "Memo 未完成 3",
            "待处理 2",
            "新建事件"
        )
        val centers = descriptions.map { description ->
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot.center.x
        }
        assertTrue("toolbar order should be Today, Memo, Todo, New: $centers",
            centers.zipWithNext().all { (left, right) -> left < right })

        compose.onNodeWithContentDescription(descriptions[0]).performClick()
        compose.onNodeWithContentDescription(descriptions[1]).performClick()
        compose.onNodeWithContentDescription(descriptions[2]).performClick()
        compose.onNodeWithContentDescription(descriptions[3]).performClick()
        compose.onNodeWithText("活动").performClick()
        compose.runOnIdle {
            assertEquals(listOf("today", "memo", "todo", "new"), actions)
            assertEquals(TemporalKind.ACTIVITY, createdKind)
        }
        println("TIMELINE_TOOLBAR_ORDER centers=$centers callbacks=$actions created=$createdKind")
    }

    @Test
    fun memoAndPendingBadgesShowTheirRemainingCounts() {
        renderToolbar(pendingCount = 7, memoCount = 12)

        compose.onNodeWithContentDescription("Memo 未完成 12").assertIsDisplayed()
        compose.onNodeWithContentDescription("待处理 7").assertIsDisplayed()
        compose.onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("7", useUnmergedTree = true).assertIsDisplayed()
        println("TIMELINE_BADGE_COUNTS memo=12 pending=7")
    }
}
