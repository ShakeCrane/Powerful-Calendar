package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DraftDeckImeInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun publishActionScrollsAboveImeAndPublishesLongNotesDraft() = runLongNotesImeAction("发布")

    @Test
    fun discardActionScrollsAboveImeAndDiscardsLongNotesDraft() = runLongNotesImeAction("丢弃")

    private fun runLongNotesImeAction(actionLabel: String) {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-d01b-ime-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val now = System.currentTimeMillis() + 2 * 60 * 60_000L
            val notes = (1..28).joinToString("\n") { "D01B_LONG_NOTE_ROW_$it" }
            val form = DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "D01B_IME_隔离发布测试",
                startText = DraftForm.formatInstant(now, zone),
                endText = DraftForm.formatInstant(now + 60 * 60_000L, zone),
                notes = notes,
                zoneId = zone.id
            )
            val card = store.newDraft(form)
            val controller = DeckController(
                StoreDeckPersistence(store),
                ioDispatcher = Dispatchers.Unconfined
            )
            runBlocking { controller.open(card.draftId) }

            compose.activity.runOnUiThread {
                compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            }
            compose.setContent {
                PowerCalTheme {
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
            compose.onNode(hasSetTextAction() and hasText("D01B_LONG_NOTE_ROW_28", substring = true))
                .performScrollTo()
                .performClick()
            compose.onNode(hasSetTextAction() and hasText("D01B_LONG_NOTE_ROW_28", substring = true))
                .performTextInput(" D01B_IME_EDIT")
            compose.waitUntil(10_000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            compose.waitForIdle()

            val imeInsets = requireNotNull(
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            )
            val imeBottomPx = imeInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val rootBounds = requireNotNull(
                compose.onAllNodes(isRoot()).fetchSemanticsNodes()
                    .maxByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
            ).boundsInRoot
            val imeTopInRoot = rootBounds.bottom - imeBottomPx
            val publishNode = compose.onNodeWithText("发布")
            val discardNode = compose.onNodeWithText("丢弃")
            val beforeScrollPublish = publishNode.fetchSemanticsNode()
            val beforeScrollDiscard = discardNode.fetchSemanticsNode()
            assertTrue("IME must be visible", imeBottomPx > 0)
            println(
                "D01B_IME_BEFORE_SCROLL action=$actionLabel root=$rootBounds imeBottomPx=$imeBottomPx " +
                    "imeTop=$imeTopInRoot publish=${beforeScrollPublish.boundsInRoot} " +
                    "discard=${beforeScrollDiscard.boundsInRoot}"
            )
            publishNode.performScrollTo()
            discardNode.performScrollTo()
            compose.waitForIdle()
            val publish = publishNode.fetchSemanticsNode()
            val discard = discardNode.fetchSemanticsNode()
            println(
                "D01B_IME_AFTER_SCROLL action=$actionLabel root=$rootBounds imeTop=$imeTopInRoot " +
                    "publish=${publish.boundsInRoot} discard=${discard.boundsInRoot}"
            )
            publishNode.assertIsDisplayed().assertIsEnabled()
            discardNode.assertIsDisplayed().assertIsEnabled()
            assertTrue(
                "publish must remain above IME: root=$rootBounds imeTop=$imeTopInRoot publish=${publish.boundsInRoot}",
                publish.boundsInRoot.bottom <= imeTopInRoot + 2f)
            assertTrue(
                "discard must remain above IME: root=$rootBounds imeTop=$imeTopInRoot discard=${discard.boundsInRoot}",
                discard.boundsInRoot.bottom <= imeTopInRoot + 2f)
            compose.onNodeWithText(actionLabel).performClick()
            if (actionLabel == "发布") {
                compose.waitUntil(10_000) { store.get(card.eventSeq) != null }
                assertTrue("visible publish action must commit the isolated event", store.get(card.eventSeq) != null)
            } else {
                compose.waitUntil(10_000) { store.draft(card.draftId)?.status == DraftStatus.DISCARDED }
                assertTrue("visible discard action must discard only the isolated draft",
                    store.draft(card.draftId)?.status == DraftStatus.DISCARDED && store.get(card.eventSeq) == null)
            }
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
