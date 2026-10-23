package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreedyTitleFoldingTest {
    private fun title(
        id: String,
        top: Float,
        bottom: Float,
        left: Float = 20f,
        right: Float = 120f,
        seq: Long = id.first().code.toLong(),
        region: String = "right-lane-0"
    ) = TimelineTitleCandidate(id, seq, region, Rect(left, top, right, bottom))

    @Test
    fun hiddenTitleDoesNotBlockNextTitleFromBecomingTheLead() {
        val result = GreedyTitleFolding.fold(listOf(
            title("A", 0f, 10f),
            title("B", 8f, 18f),
            title("C", 11f, 21f),
            title("D", 19f, 29f)
        ))

        assertEquals(setOf("A", "C"), result.visibleIds)
        assertEquals(setOf("B", "D"), result.hiddenIds)
        assertEquals(
            listOf("A:show:-", "B:hide:A", "C:show:A", "D:hide:C"),
            result.decisions.map {
                "${it.candidate.stableId}:${if (it.visible) "show" else "hide"}:${it.comparedTo ?: "-"}"
            }
        )
    }

    @Test
    fun repeatedConflictRunsKeepOnlyEachVisibleLead() {
        val candidates = listOf(
            title("A", 0f, 12f), title("B", 4f, 16f),
            title("C", 14f, 26f), title("D", 18f, 30f),
            title("E", 29f, 41f), title("F", 33f, 45f)
        )

        val result = GreedyTitleFolding.fold(candidates)
        assertEquals(setOf("A", "C", "E"), result.visibleIds)
        assertEquals(setOf("B", "D", "F"), result.hiddenIds)
        assertEquals(listOf("A", "C", "E"), result.decisions.filter { it.visible }
            .map { it.candidate.stableId })
    }

    @Test
    fun exactlyTouchingTextRectanglesDoNotCollide() {
        val result = GreedyTitleFolding.fold(listOf(
            title("A", 10f, 20f),
            title("B", 20f, 30f),
            title("C", 30f, 40f)
        ))

        assertEquals(setOf("A", "B", "C"), result.visibleIds)
        assertTrue(result.hiddenIds.isEmpty())
    }

    @Test
    fun independentNonOccludingLanesKeepTheirOwnVisibleTitles() {
        val result = GreedyTitleFolding.fold(listOf(
            title("left", 10f, 25f, left = 0f, right = 45f, region = "left-1"),
            title("right", 10f, 25f, left = 50f, right = 95f, region = "right-0")
        ))

        assertEquals(setOf("left", "right"), result.visibleIds)
        assertTrue(result.hiddenIds.isEmpty())
    }

    @Test
    fun actualScreenTextOverlapFoldsEvenWhenEventTimesWouldNotOverlap() {
        // Candidates deliberately carry no event interval: rendered screen rectangles decide.
        val first = title("earlier-event", 100f, 116f)
        val later = title("later-event", 108f, 124f)

        val result = GreedyTitleFolding.fold(listOf(first, later))
        assertEquals(setOf("earlier-event"), result.visibleIds)
        assertEquals(setOf("later-event"), result.hiddenIds)
        assertEquals("earlier-event", result.decisions.last().comparedTo)
    }

    @Test
    fun stableEventIdBreaksSameYOrderAndInputOrderDoesNotMatter() {
        val first = title("event-10", 10f, 24f, seq = 10)
        val second = title("event-20", 10f, 24f, seq = 20)

        val one = GreedyTitleFolding.fold(listOf(second, first))
        val two = GreedyTitleFolding.fold(listOf(first, second))
        assertEquals(listOf("event-10", "event-20"), one.decisions.map { it.candidate.stableId })
        assertEquals(one, two)
        assertEquals(setOf("event-10"), one.visibleIds)
        assertEquals(setOf("event-20"), one.hiddenIds)
        assertNull(one.decisions.first().comparedTo)
    }

    @Test
    fun scrollZoomAndDayResetRecomputeToStableGeometryDecisions() {
        val base = listOf(title("A", 10f, 22f), title("B", 18f, 30f), title("C", 25f, 37f))
        fun transform(dx: Float, dy: Float, scale: Float) = base.map { candidate ->
            val rect = candidate.screenRect
            candidate.copy(screenRect = Rect(
                rect.left + dx, rect.top * scale + dy,
                rect.right + dx, rect.bottom * scale + dy
            ))
        }

        val expected = GreedyTitleFolding.fold(base).visibleIds
        assertEquals(expected, GreedyTitleFolding.fold(transform(-240f, 35f, 1f)).visibleIds)
        assertEquals(expected, GreedyTitleFolding.fold(transform(0f, 0f, 1.5f)).visibleIds)
        assertEquals(expected, GreedyTitleFolding.fold(transform(0f, 0f, 1f)).visibleIds)
    }

    @Test
    fun sameScreenCoordinatesStillUseTheMeasuredTextBoundsNotLaneWidth() {
        val result = GreedyTitleFolding.fold(listOf(
            title("short", 0f, 10f, left = 10f, right = 22f),
            title("far-to-right", 5f, 15f, left = 23f, right = 75f)
        ))

        assertEquals(setOf("short", "far-to-right"), result.visibleIds)
        assertFalse(result.decisions.any { !it.visible })
    }
}
