package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.EventValidationStatusV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-xhdpi")
class NewEventEditorComposeTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var eventApi: EventFeatureApiV1
    private lateinit var activeEditor: EventEditorSnapshotV1
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "new-event-ui-${UUID.randomUUID()}.db")
        eventApi = PowerCalCoreFeatureApiV1(store, clock).events
    }

    @After fun tearDown() {
        runCatching { eventApi.discardNewEvent(activeEditor.handle) }
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    private fun newEditor(kind: EventKindV1 = EventKindV1.ALARM): EventEditorSnapshotV1 =
        when (val opened = eventApi.openNewEvent(kind, zone.id)) {
            is EventEditorOpenResultV1.Opened -> opened.editor
            else -> error("Expected new-event editor to open, got $opened")
        }.also { activeEditor = it }

    private fun currentEditor(): EventEditorSnapshotV1 =
        requireNotNull(eventApi.editorSnapshot(activeEditor.handle)).also { activeEditor = it }

    private fun render(editor: EventEditorSnapshotV1) {
        val editorState = mutableStateOf(editor)
        val form = mutableStateOf(editor.form.toCoreDraftForm())
        fun refresh() {
            eventApi.editorSnapshot(editorState.value.handle)?.let { updated ->
                editorState.value = updated
                activeEditor = updated
            }
        }
        ui.setContent { PowerCalTheme {
            NewEventEditorDialog(
                eventFeatureApi = eventApi,
                editor = editorState.value,
                form = form.value,
                error = null,
                busy = false,
                onFormChange = { next ->
                    form.value = next
                    eventApi.updateNewEvent(editorState.value.handle, next.toEventFormV1())
                    refresh()
                },
                onSave = {
                    eventApi.saveNewEvent(
                        editorState.value.handle,
                        form.value.toEventFormV1(),
                        UUID.randomUUID().toString()
                    )
                },
                onExit = {
                    eventApi.closeNewEvent(editorState.value.handle)
                },
                onDiscard = {
                    eventApi.discardNewEvent(editorState.value.handle)
                }
            )
        } }
    }

    @Test fun openingAndClosingTheNativeEditorLeavesNoDraftOrIdentity() {
        val editor = newEditor()
        render(editor)
        ui.onNodeWithText("新建事件").assertIsDisplayed()
        assertFalse(currentEditor().meaningfulChanges)
        ui.onNodeWithTag("new-event-close").performClick()

        assertEquals(0L, store.draftCount())
        assertEquals(0, store.pendingCount())
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())
        assertEquals(0L, store.commitCount())
    }

    @Test fun nativeEditorStagesExactlyOneChangedDraftWhenClosedWithX() {
        val editor = newEditor()
        render(editor)
        ui.onAllNodes(hasSetTextAction())[0].performTextReplacement("Changed title")
        assertEquals("Changed title", currentEditor().form.title)
        assertTrue(currentEditor().meaningfulChanges)
        ui.onNodeWithTag("new-event-close").performClick()

        assertEquals(1L, store.draftCount())
        assertEquals(1, store.pendingCount())
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(0L, store.commitCount())
        assertEquals("Changed title", store.pendingDrafts().single().form.title)
    }

    @Test fun invalidCoreFormIsPresentedAsDraftInsteadOfPublish() {
        val editor = newEditor(EventKindV1.ACTIVITY)
        val invalidForm = editor.form.toCoreDraftForm().copy(
            title = "重复活动",
            startText = "2026-10-27 09:00",
            endText = "2026-10-27 10:00",
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, interval = 0)
        )
        assertTrue("shallow predicate does not validate recurrence", invalidForm.isPublishable())
        eventApi.updateNewEvent(editor.handle, invalidForm.toEventFormV1())
        val updated = currentEditor()
        val validation = eventApi.validateNewEvent(updated.handle)
        assertEquals(EventValidationStatusV1.INVALID, validation.status)

        render(updated)

        ui.onNodeWithText("保存草稿").assertIsDisplayed()
        ui.onNodeWithText("发布").assertDoesNotExist()
        ui.onNodeWithText("重复间隔必须至少为 1").performScrollTo().assertIsDisplayed()
    }

    @Test fun recompositionKeepsEditedTimeAndTheFeatureSessionCapturedClock() {
        val editor = newEditor(EventKindV1.ACTIVITY)
        val form = mutableStateOf(editor.form.toCoreDraftForm())
        val editorState = mutableStateOf(editor)
        val recomposition = mutableIntStateOf(0)
        val capturedNow = editor.defaultNowMillis
        ui.setContent {
            val revision = recomposition.intValue
            PowerCalTheme {
                androidx.compose.material3.Text("revision $revision")
                NewEventEditorDialog(
                    eventFeatureApi = eventApi,
                    editor = editorState.value,
                    form = form.value,
                    error = null,
                    busy = false,
                    onFormChange = { next ->
                        form.value = next
                        eventApi.updateNewEvent(editorState.value.handle, next.toEventFormV1())
                        eventApi.editorSnapshot(editorState.value.handle)?.let { updated ->
                            editorState.value = updated
                            activeEditor = updated
                        }
                    },
                    onSave = {
                        eventApi.saveNewEvent(
                            editorState.value.handle,
                            form.value.toEventFormV1(),
                            UUID.randomUUID().toString()
                        )
                    },
                    onExit = { eventApi.closeNewEvent(editorState.value.handle) },
                    onDiscard = { eventApi.discardNewEvent(editorState.value.handle) }
                )
            }
        }

        ui.onAllNodes(hasSetTextAction())[1].performTextReplacement("2026-10-26 08:00")
        assertEquals("2026-10-26 08:00", currentEditor().form.startText)
        ui.runOnIdle { recomposition.intValue++ }
        ui.waitForIdle()

        val fieldValue = ui.onAllNodes(hasSetTextAction())[1].fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals("2026-10-26 08:00", fieldValue)
        assertEquals("2026-10-26 08:00", currentEditor().form.startText)
        assertEquals(capturedNow, currentEditor().defaultNowMillis)
        assertTrue(currentEditor().meaningfulChanges)
        assertEquals(0L, store.draftCount())
    }
}
