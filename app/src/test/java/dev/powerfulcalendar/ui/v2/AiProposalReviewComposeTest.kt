package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.TemporalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-xhdpi")
class AiProposalReviewComposeTest {
    @get:Rule val ui = createComposeRule()

    private val preview = AiInterpretation(proposals = listOf(
        AiProposal(
            operation = DraftOperation.CREATE,
            eventSeq = null,
            form = DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "14514",
                startText = "2026-10-25 01:45",
                endText = "2026-10-25 02:15"
            ),
            sourceIndex = 3
        )
    ))

    @Test
    fun previewWaitsForExplicitPublishOrDraftChoice() {
        var action: Pair<Set<Int>, Boolean>? = null
        ui.setContent {
            PowerCalTheme {
                AiProposalReview(
                    interpretation = preview,
                    busy = false,
                    onApply = { selected, publish -> action = selected to publish },
                    onDiscard = {},
                    onSupplement = {}
                )
            }
        }

        ui.onNodeWithText("识别到新增 · 是否创建草稿？").assertIsDisplayed()
        ui.onNodeWithText("14514 · 活动", substring = true).assertIsDisplayed()
        assertNull(action)

        ui.onNodeWithText("写入草稿").assertIsDisplayed().performClick()
        assertEquals(setOf(0) to false, action)

        action = null
        ui.onNodeWithText("批量同意").performClick()
        assertEquals(setOf(0) to true, action)
    }
}
