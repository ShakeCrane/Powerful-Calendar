package dev.powerfulcalendar.ui.v2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TimelineFontScaleComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun doubledSystemFontScaleKeepsTimelineToolbarActionsReachable() {
        val zone = ZoneId.of("Asia/Shanghai")
        val noon = LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(zone)
            .toInstant().toEpochMilli()
        var observedFontScale = 0f
        var cameraHours = 24.0
        var todayCalls = 0
        var memoCalls = 0
        var deckCalls = 0
        var createdKind: TemporalKind? = null

        compose.setContent {
            val systemDensity = LocalDensity.current
            val camera = remember { mutableStateOf(TimelineCoordinate.Camera(noon, 24.0)) }
            CompositionLocalProvider(
                LocalDensity provides Density(systemDensity.density, fontScale = 2f)
            ) {
                val effectiveFontScale = LocalDensity.current.fontScale
                SideEffect { observedFontScale = effectiveFontScale }
                PowerCalTheme {
                    TimelineScreen(
                        records = emptyList(),
                        camera = camera.value,
                        nowMillis = noon,
                        zone = zone,
                        pendingCount = 2,
                        aiLocked = false,
                        onCameraChange = {
                            camera.value = it
                            cameraHours = it.hours
                        },
                        onOpenEvent = {},
                        onMoveEvent = { _, _ -> },
                        onCreate = { kind, _ -> createdKind = kind },
                        onToday = { todayCalls++ },
                        onOpenDeck = { deckCalls++ },
                        onOpenTools = {},
                        onOpenMemo = { memoCalls++ },
                        memoCount = 3,
                        modifier = androidx.compose.ui.Modifier.testTag("font-scale-timeline")
                    )
                }
            }
        }
        compose.waitForIdle()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val controls = listOf(
            "今日 · 复位为全天",
            "Memo 未完成 3",
            "待处理 2",
            "新建事件",
            "缩小，显示更长时间",
            "放大，显示更短时间"
        )
        val bounds = controls.associateWith { description ->
            val node = compose.onNodeWithContentDescription(description)
                .assertExists().assertHasClickAction()
            node.fetchSemanticsNode().boundsInRoot
        }

        compose.onNodeWithContentDescription("今日 · 复位为全天").performClick()
        compose.onNodeWithContentDescription("Memo 未完成 3").performClick()
        compose.onNodeWithContentDescription("待处理 2").performClick()
        compose.onNodeWithContentDescription("缩小，显示更长时间").performClick()
        compose.onNodeWithContentDescription("放大，显示更短时间").performClick()
        compose.onNodeWithContentDescription("新建事件").performClick()
        compose.onNodeWithText("活动").assertExists().assertHasClickAction().performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(2f, observedFontScale, 0f)
            assertEquals(1, todayCalls)
            assertEquals(1, memoCalls)
            assertEquals(1, deckCalls)
            assertEquals(TemporalKind.ACTIVITY, createdKind)
            assertEquals(24.0, cameraHours, 0.0)
        }
        bounds.forEach { (label, rect) ->
            assertTrue("$label must have positive bounds: $rect", rect.width > 0f && rect.height > 0f)
            assertTrue("$label is clipped on the left: $rect in $root", rect.left >= root.left)
            assertTrue("$label is clipped on the top: $rect in $root", rect.top >= root.top)
            assertTrue("$label is clipped on the right: $rect in $root", rect.right <= root.right)
            assertTrue("$label is clipped on the bottom: $rect in $root", rect.bottom <= root.bottom)
        }
        println("TIMELINE_FONT_SCALE_2X viewport=$root controls=$bounds")
    }
}
