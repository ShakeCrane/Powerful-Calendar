package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.TimelineScreen

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TimelineDateOnlyDeadlineAccessibilityInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val dueDate = LocalDate.of(2026, 10, 24)
    private val deadline = TemporalRecord(
        eventSeq = 520,
        revision = 1,
        kind = TemporalKind.DEADLINE,
        title = "DATE_ONLY_DEADLINE_A11Y",
        deadlineDate = dueDate.toString(),
        zoneId = zone.id
    )

    @Test
    fun dateOnlyDeadlineKeepsDatePrecisionInAccessibilityAcrossZoomAndOpen() {
        val cameraAnchor = dueDate.atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val camera = mutableStateOf(TimelineCoordinate.Camera(cameraAnchor, 4.0))
        val visibleDateBandLabel = "${dueDate} · 截止，无具体时刻"
        var opened: TemporalRecord? = null
        var expectedBounds: Rect? = null

        compose.runOnUiThread {
            compose.activity.setContent {
                PowerCalTheme {
                    TimelineScreen(
                        records = listOf(deadline),
                        camera = camera.value,
                        nowMillis = cameraAnchor,
                        zone = zone,
                        pendingCount = 0,
                        aiLocked = false,
                        onCameraChange = { camera.value = it },
                        onOpenEvent = { opened = it },
                        onMoveEvent = { _, _ -> },
                        onCreate = { _, _ -> },
                        onToday = {},
                        onOpenDeck = {},
                        onOpenTools = {}
                    )
                }
            }
        }
        compose.waitForIdle()

        val clockTime = Regex("(?<!\\d)\\d{1,2}:\\d{2}(?!\\d)")
        for (hours in listOf(4.0, 24.0, 48.0)) {
            compose.runOnIdle { camera.value = camera.value.copy(hours = hours) }
            compose.waitForIdle()

            val node = compose.onNodeWithText(visibleDateBandLabel).assertExists().assertHasClickAction()
            val semanticText = node.fetchSemanticsNode().config[SemanticsProperties.Text]
                .joinToString(" ") { it.text }
            val bounds = node.fetchSemanticsNode().boundsInRoot
            println("DATE_ONLY_DEADLINE_A11Y hours=$hours text=$semanticText bounds=$bounds")

            if (expectedBounds == null) expectedBounds = bounds
            else assertEquals("date-band node geometry must stay stable at $hours h", expectedBounds, bounds)
            assertTrue("full title must remain accessible at $hours h: $semanticText", semanticText.contains(deadline.title))
            assertTrue("date must remain announced at $hours h: $semanticText", semanticText.contains(dueDate.toString()))
            assertTrue("date precision must be announced at $hours h: $semanticText", semanticText.contains("截止，无具体时刻"))
            assertFalse("date-only deadline must not invent a clock time at $hours h: $semanticText", clockTime.containsMatchIn(semanticText))
        }

        compose.onNodeWithText(visibleDateBandLabel).performClick()
        compose.runOnIdle {
            assertEquals(deadline.eventSeq, opened?.eventSeq)
            assertEquals(deadline.title, opened?.title)
            assertEquals(dueDate.toString(), opened?.deadlineDate)
            assertNull(opened?.deadlineMillis)
        }
    }
}
