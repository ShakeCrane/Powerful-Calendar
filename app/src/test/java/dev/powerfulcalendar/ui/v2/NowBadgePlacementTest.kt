package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.findUnblockedLabelTopLeft

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBadgePlacementTest {
    @Test fun movesBadgeAwayFromTickTextWithoutLeavingViewport() {
        val preferred = Offset(110f, 85f)
        val labelSize = Size(80f, 30f)
        val obstacle = Rect(90f, 75f, 130f, 125f)

        val placed = findUnblockedLabelTopLeft(
            preferred = preferred,
            labelSize = labelSize,
            canvasSize = Size(320f, 220f),
            obstacles = listOf(obstacle),
            stepPx = 4f,
            gapPx = 2f
        )

        assertFalse(overlaps(placed, labelSize, obstacle, 2f))
        assertTrue(placed.x >= 0f && placed.y >= 0f)
        assertTrue(placed.x + labelSize.width <= 320f)
        assertTrue(placed.y + labelSize.height <= 220f)
    }

    @Test fun searchesForClearSpaceWhenOneSideContainsAnEventCard() {
        val preferred = Offset(110f, 85f)
        val labelSize = Size(80f, 30f)
        val tick = Rect(90f, 75f, 130f, 125f)
        val rightEvent = Rect(135f, 70f, 250f, 130f)

        val placed = findUnblockedLabelTopLeft(
            preferred = preferred,
            labelSize = labelSize,
            canvasSize = Size(320f, 220f),
            obstacles = listOf(tick, rightEvent),
            stepPx = 4f,
            gapPx = 2f
        )

        assertFalse(overlaps(placed, labelSize, tick, 2f))
        assertFalse(overlaps(placed, labelSize, rightEvent, 2f))
        assertTrue(placed.x != preferred.x || placed.y != preferred.y)
    }

    private fun overlaps(label: Offset, size: Size, obstacle: Rect, gap: Float): Boolean =
        label.x < obstacle.right + gap && label.x + size.width > obstacle.left - gap &&
            label.y < obstacle.bottom + gap && label.y + size.height > obstacle.top - gap
}
