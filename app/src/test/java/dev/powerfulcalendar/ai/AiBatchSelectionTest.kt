package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.DraftCard
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiBatchSelectionTest {
    private val cards = listOf(
        DraftCard(12L, 12L, 1L, DraftOperation.CREATE,
            DraftForm(kind = TemporalKind.ACTIVITY, title = "组会"),
            DraftStatus.PENDING, null, 2L),
        DraftCard(18L, 18L, 2L, DraftOperation.DELETE,
            DraftForm(kind = TemporalKind.ALARM, title = "旧闹钟"),
            DraftStatus.CONFLICT, 4L, 5L),
        DraftCard(22L, 22L, 3L, DraftOperation.CREATE,
            DraftForm(kind = TemporalKind.ACTIVITY, title = "失效"),
            DraftStatus.DISCARDED, null, 3L)
    )

    @Test fun exactSelectionRequiresExistingActionableDraftIds() {
        val result = AiBatchSelectionParser.parse(
            """{"mode":"SELECT","draftIds":[12,18],"reply":"两张"}""", cards)
        assertTrue(result.ready)
        assertEquals(listOf(12L, 18L), result.selectedIds)
    }

    @Test fun unknownDiscardedAndRepeatedIdsAllFailClosed() {
        listOf("[12,88]", "[22]", "[12,12]", "[0]", "[\"not-number\"]").forEach { ids ->
            val result = AiBatchSelectionParser.parse(
                """{"mode":"SELECT","draftIds":""" + ids + "}", cards)
            assertFalse(ids, result.ready)
            assertTrue(ids, result.selectedIds.isEmpty())
        }
    }

    @Test fun unclearIntentReturnsQuestionNotAuthorization() {
        val result = AiBatchSelectionParser.parse(
            """{"mode":"CLARIFY","question":"哪几张？","reply":"需要范围"}""", cards)
        assertFalse(result.ready)
        assertEquals("哪几张？", result.question)
    }

    @Test fun arbitraryChatJsonDoesNotBecomeSelection() {
        val result = AiBatchSelectionParser.parse(
            """{"mode":"CHAT","draftIds":[12]}""", cards)
        assertFalse(result.ready)
        assertTrue(result.selectedIds.isEmpty())
    }
}
