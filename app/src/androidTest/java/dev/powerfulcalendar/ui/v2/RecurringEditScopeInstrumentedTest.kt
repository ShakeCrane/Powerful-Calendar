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
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventMutationReceiptV1
import dev.powerfulcalendar.core.api.v1.EventMutationStatusV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toEventFormV1
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
        val eventApi = PowerCalCoreFeatureApiV1(store).events
        var activeEditor by mutableStateOf<EventEditorSnapshotV1?>(null)
        var visibleForm by mutableStateOf<DraftForm?>(null)
        var outcome: EventMutationReceiptV1? by mutableStateOf(null)
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
            val editor = when (
                val opened = eventApi.openFormalEvent(formal.eventSeq, formal.revision, selectedKey)
            ) {
                is EventEditorOpenResultV1.Opened -> opened.editor
                else -> error("Expected formal-event editor to open, got $opened")
            }
            compose.runOnUiThread {
                activeEditor = editor
                visibleForm = editor.form.toCoreDraftForm()
            }

            compose.setContent {
                PowerCalTheme {
                    activeEditor?.let { currentEditor ->
                        DirectEventEditorContent(
                            eventFeatureApi = eventApi,
                            editor = currentEditor,
                            form = visibleForm ?: currentEditor.form.toCoreDraftForm(),
                            error = currentEditor.lastError,
                            hasChanges = currentEditor.meaningfulChanges,
                            onChange = { next ->
                                visibleForm = next
                                eventApi.updateFormalEvent(
                                    currentEditor.handle, next.toEventFormV1()
                                )
                                eventApi.editorSnapshot(currentEditor.handle)?.let { updated ->
                                    activeEditor = updated
                                }
                            },
                            onScopeChange = { selected ->
                                eventApi.selectFormalRecurrenceScope(
                                    currentEditor.handle, selected
                                )
                                eventApi.editorSnapshot(currentEditor.handle)?.let { updated ->
                                    activeEditor = updated
                                    visibleForm = updated.form.toCoreDraftForm()
                                }
                            },
                            onSave = {
                                outcome = eventApi.saveFormalEvent(
                                    currentEditor.handle,
                                    (visibleForm ?: currentEditor.form.toCoreDraftForm())
                                        .toEventFormV1(),
                                    UUID.randomUUID().toString()
                                )
                            },
                            onExit = {},
                            onDiscardChanges = {
                                eventApi.discardFormalChanges(currentEditor.handle)
                                eventApi.editorSnapshot(currentEditor.handle)?.let { updated ->
                                    activeEditor = updated
                                    visibleForm = updated.form.toCoreDraftForm()
                                }
                            },
                            onDelete = {}
                        )
                    }
                }
            }

            compose.onNodeWithText("保存修改").assertIsNotEnabled()
            compose.onNodeWithText("本次及以后").assertIsDisplayed().performClick()
            compose.onNode(hasSetTextAction() and hasText("AVD 原系列"))
                .performTextReplacement("AVD 后续系列")
            compose.onNodeWithText("保存修改").performClick()
            compose.waitForIdle()

            assertEquals(EventMutationStatusV1.UPDATED, outcome?.status)
            val futureEventSeq = requireNotNull(outcome?.relatedEventSeq)
            val old = requireNotNull(store.get(formal.eventSeq))
            val future = requireNotNull(store.get(futureEventSeq))
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
            activeEditor?.let { eventApi.closeFormalEvent(it.handle) }
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
