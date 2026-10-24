package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
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
class DraftDeckDefaultSearchInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openingDeckSearchShowsPendingQueueBeforeFormalSearchResults() {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-issue-158-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val day = LocalDate.of(2026, 10, 25)
            val cards = listOf("PENDING_SEARCH_FIRST", "PENDING_SEARCH_SECOND").map { title ->
                store.newDraft(
                    DraftForm.fresh(TemporalKind.ACTIVITY, day, zone).copy(title = title)
                )
            }
            val controller = DeckController(
                StoreDeckPersistence(store), ioDispatcher = Dispatchers.Unconfined
            )
            runBlocking { controller.open(cards.first().draftId) }

            val formalSearchQueries = mutableListOf<String>()
            val formalOnlyResult = TemporalRecord(
                eventSeq = 900,
                revision = 1,
                kind = TemporalKind.ACTIVITY,
                title = "FORMAL_ONLY_SEARCH_RESULT",
                startMillis = day.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
                endMillis = day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli(),
                zoneId = zone.id
            )
            compose.setContent {
                PowerCalTheme {
                    DraftDeckDialog(
                        controller = controller,
                        aiSession = null,
                        onDismissRequest = {},
                        onOpenFormalEvent = {},
                        onCancelAiBatch = {},
                        onDeckChanged = {},
                        formalSearch = { query ->
                            formalSearchQueries.add(query)
                            listOf(formalOnlyResult)
                        }
                    )
                }
            }
            compose.waitForIdle()

            compose.onNodeWithText("查找草稿标题或事件序号").assertIsDisplayed()
            compose.onNodeWithText("PENDING_SEARCH_FIRST").assertIsDisplayed()
            compose.onNodeWithText("1 / 2 · 已暂存").assertIsDisplayed()
            compose.onNodeWithText("FORMAL_ONLY_SEARCH_RESULT").assertDoesNotExist()
            assertEquals(cards.map { it.draftId }, controller.state.cards.map { it.draftId })
            assertEquals(cards.first().draftId, controller.state.currentId)
            assertTrue("opening pending search must not request formal results", formalSearchQueries.isEmpty())
            println(
                "DRAFT_SEARCH_DEFAULT currentId=${controller.state.currentId} " +
                    "queue=${controller.state.cards.map { it.draftId }} " +
                    "formalSearchQueries=${formalSearchQueries.size}"
            )
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
