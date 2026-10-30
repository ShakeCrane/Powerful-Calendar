package dev.powerfulcalendar.ui.v2


import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DraftDeckThemeSwitchInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingLightDarkThemeDuringDraftEditKeepsCardTextAndFocus() {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-issue-160-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val draft = store.newDraft(
                DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 10, 25), zone)
                    .copy(title = "THEME_SWITCH_INITIAL")
            )
            val controller = DeckController(
                StoreDeckPersistence(store), ioDispatcher = Dispatchers.Unconfined
            )
            runBlocking { controller.open(draft.draftId) }

            var darkTheme by mutableStateOf(false)
            var renderedBackground by mutableStateOf(Color.Unspecified)
            compose.setContent {
                PowerCalTheme(darkTheme = darkTheme) {
                    val background = MaterialTheme.colorScheme.background
                    SideEffect { renderedBackground = background }
                    NativeDraftDeckDialog(
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

            val titleField = compose.onNode(hasSetTextAction() and hasText("标题"))
            titleField.performClick()
            titleField.assertIsFocused()
            titleField.performTextReplacement("THEME_SWITCH_DIRTY_TITLE")
            compose.waitForIdle()

            val originalId = controller.state.currentId
            assertEquals(draft.draftId, originalId)
            assertEquals("THEME_SWITCH_DIRTY_TITLE", controller.state.form?.title)
            val lightBackground = compose.runOnIdle { renderedBackground }

            compose.runOnIdle { darkTheme = true }
            compose.waitForIdle()

            val darkBackground = compose.runOnIdle { renderedBackground }
            assertNotEquals("theme state should switch from light to dark", lightBackground, darkBackground)
            assertEquals(originalId, controller.state.currentId)
            assertEquals("THEME_SWITCH_DIRTY_TITLE", controller.state.form?.title)
            compose.onNodeWithText("THEME_SWITCH_DIRTY_TITLE").assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("THEME_SWITCH_DIRTY_TITLE")).assertIsFocused()

            println(
                "DRAFT_THEME_SWITCH draftId=${controller.state.currentId} " +
                    "title=${controller.state.form?.title} light=$lightBackground dark=$darkBackground focused=true"
            )
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
