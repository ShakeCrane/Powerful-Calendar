package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecurringEditScopeInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun explicitlySelectedFutureScopeSplitsOnlyFromTheTappedOccurrence() {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-rec-scope-${UUID.randomUUID()}.db"
        val zone = ZoneId.of("Asia/Shanghai")
        val firstStart = LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val selectedKey = "2026-10-21T07:30"
        val store = PowerStore(context, databaseName)
        var session: EditSession? = null
        var visibleForm by mutableStateOf<DraftForm?>(null)
        var outcome: PowerStore.RecurrenceSplitOutcome? by mutableStateOf(null)
        try {
            val card = store.newDraft(DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "AVD 原系列",
                startText = DraftForm.formatInstant(firstStart, zone),
                endText = DraftForm.formatInstant(firstStart + 3_600_000L, zone),
                zoneId = zone.id,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
            ))
            assertTrue(store.commit(card.draftId, actor = "instrumented-test") is PowerStore.CommitOutcome.Committed)
            val formal = requireNotNull(store.get(card.eventSeq))
            session = EditSession.open(store, formal, selectedKey)
            val activeSession = requireNotNull(session)
            visibleForm = activeSession.form

            compose.setContent {
                PowerCalTheme {
                    DirectEventEditorContent(
                        session = activeSession,
                        form = visibleForm ?: activeSession.form,
                        error = activeSession.lastError,
                        hasChanges = activeSession.meaningfulDiff,
                        onChange = { next -> activeSession.edit(next); visibleForm = next },
                        onScopeChange = { selected ->
                            activeSession.selectRecurrenceScope(selected)
                            visibleForm = activeSession.form
                        },
                        onSave = {
                            outcome = activeSession.saveThisAndFuture()
                        },
                        onExit = {},
                        onDiscardChanges = {},
                        onDelete = {}
                    )
                }
            }

            compose.onNodeWithText("保存修改").assertIsNotEnabled()
            compose.onNodeWithText("本次及以后").assertIsDisplayed().performClick()
            compose.onNode(hasSetTextAction() and hasText("AVD 原系列"))
                .performTextReplacement("AVD 后续系列")
            compose.onNodeWithText("保存修改").performClick()
            compose.waitForIdle()

            assertTrue(outcome is PowerStore.RecurrenceSplitOutcome.Applied)
            val old = requireNotNull(store.get(formal.eventSeq))
            val future = requireNotNull(store.get(2L))
            assertEquals(1, old.recurrence.count)
            assertEquals("AVD 后续系列", future.title)
            assertEquals(2, future.recurrence.count)
            assertEquals(listOf("2026-10-20T07:30"),
                dev.powerfulcalendar.core.RecurrenceEngine.occurrences(
                    old,
                    firstStart,
                    firstStart + 4 * 86_400_000L
                ).map { it.key })
            assertEquals(listOf(selectedKey, "2026-10-22T07:30"),
                dev.powerfulcalendar.core.RecurrenceEngine.occurrences(
                    future,
                    firstStart,
                    firstStart + 4 * 86_400_000L
                ).map { it.key })
            assertEquals(2L, store.identityCount())
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            session?.close()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
