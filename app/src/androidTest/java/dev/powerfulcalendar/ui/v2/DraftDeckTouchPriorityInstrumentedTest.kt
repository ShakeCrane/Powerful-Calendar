package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DraftDeckTouchPriorityInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 25)

    private fun openController(store: PowerStore, draftId: Long): DeckController =
        DeckController(StoreDeckPersistence(store), ioDispatcher = Dispatchers.Unconfined).also {
            runBlocking { it.open(draftId) }
        }

    private fun renderExpandedDeck(controller: DeckController) {
        compose.setContent {
            PowerCalTheme {
                DraftDeckDialog(
                    controller = controller,
                    aiSession = null,
                    onDismissRequest = {},
                    onOpenFormalEvent = {},
                    onCancelAiBatch = {},
                    onDeckChanged = {},
                    formalSearch = { emptyList() },
                    initialEditorOpen = true
                )
            }
        }
        compose.waitForIdle()
    }

    private inline fun withIsolatedStore(block: (PowerStore, Context, String) -> Unit) {
        val context = compose.activity.applicationContext
        val databaseName = "pc-issue-157-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            block(store, context, databaseName)
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun horizontalGestureOnEditedTextFieldDoesNotPageTheDraftDeck() = withIsolatedStore { store, _, _ ->
        val cards = (1..4).map { index ->
            store.newDraft(
                DraftForm.fresh(TemporalKind.ACTIVITY, day, zone)
                    .copy(title = "TOUCH_PRIORITY_$index")
            )
        }
        val controller = openController(store, cards.first().draftId)
        renderExpandedDeck(controller)

        val titleField = compose.onNode(hasSetTextAction() and hasText("标题"))
        titleField.performTextReplacement("TYPED_TITLE_SURVIVES_SWIPE")
        compose.waitForIdle()
        titleField.performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(cards.first().draftId, controller.state.currentId)
        assertEquals("TYPED_TITLE_SURVIVES_SWIPE", controller.state.form?.title)
        compose.onNodeWithText("TYPED_TITLE_SURVIVES_SWIPE").assertIsDisplayed()
        println(
            "DRAFT_TEXT_TOUCH_PRIORITY currentId=${controller.state.currentId} " +
                "title=${controller.state.form?.title} queueSize=${controller.state.cards.size}"
        )
    }

    @Test
    fun verticalGestureOnExpandedContentScrollsFormWithoutChangingDraft() = withIsolatedStore { store, _, _ ->
        val longNotes = (1..32).joinToString("\n") { "SCROLL_NOTE_ROW_$it" }
        val card = store.newDraft(
            DraftForm.fresh(TemporalKind.ACTIVITY, day, zone)
                .copy(title = "SCROLL_FORM_TITLE", notes = longNotes)
        )
        val controller = openController(store, card.draftId)
        renderExpandedDeck(controller)

        val deck = compose.onNodeWithTag("draft-card")
        val cardBounds = deck.fetchSemanticsNode().boundsInRoot
        val startLabel = compose.onNodeWithText("开始")
        startLabel.assertIsDisplayed()
        val startBounds = startLabel.fetchSemanticsNode().boundsInRoot
        val titleField = compose.onNode(hasSetTextAction() and hasText("标题"))
        val titleBefore = titleField.fetchSemanticsNode().boundsInRoot
        val start = Offset(
            (startBounds.center.x - cardBounds.left).coerceIn(2f, cardBounds.width - 2f),
            (startBounds.center.y - cardBounds.top).coerceIn(2f, cardBounds.height - 2f)
        )
        val end = Offset(start.x, (start.y - 260f).coerceAtLeast(2f))
        deck.performTouchInput { swipe(start, end, durationMillis = 500) }
        compose.waitForIdle()

        val titleAfter = titleField.fetchSemanticsNode().boundsInRoot
        assertTrue("expanded form content should scroll upward: before=$titleBefore after=$titleAfter",
            titleAfter.top < titleBefore.top - 40f)
        assertEquals(card.draftId, controller.state.currentId)
        println(
            "DRAFT_EXPANDED_SCROLL cardId=${controller.state.currentId} " +
                "titleBefore=$titleBefore titleAfter=$titleAfter gestureStart=$start gestureEnd=$end"
        )
    }
}
