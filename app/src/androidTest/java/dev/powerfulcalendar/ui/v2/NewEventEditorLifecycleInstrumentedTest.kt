package dev.powerfulcalendar.ui.v2


import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.EventEditorSnapshotV1
import dev.powerfulcalendar.core.api.v1.EventKindV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.toCoreDraftForm
import dev.powerfulcalendar.core.api.v1.toEventFormV1
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class NewEventEditorLifecycleInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openingAndClosingAllKindsInComposeDoesNotAllocatePersistentRowsOrSequences() {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-phase1-clean-${java.util.UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val fixedClock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)
        val zone = ZoneId.of("Asia/Shanghai")
        val eventApi = PowerCalCoreFeatureApiV1(store, fixedClock).events
        var activeEditor by mutableStateOf<EventEditorSnapshotV1?>(null)
        var visibleForm by mutableStateOf<DraftForm?>(null)
        try {
            compose.setContent {
                PowerCalTheme {
                    activeEditor?.let { editor ->
                        NewEventEditorDialog(
                            eventFeatureApi = eventApi,
                            editor = editor,
                            form = visibleForm ?: editor.form.toCoreDraftForm(),
                            error = null,
                            busy = false,
                            onFormChange = { next ->
                                visibleForm = next
                                eventApi.updateNewEvent(editor.handle, next.toEventFormV1())
                                activeEditor = eventApi.editorSnapshot(editor.handle)
                            },
                            onSave = {},
                            onExit = {
                                eventApi.closeNewEvent(editor.handle)
                                activeEditor = null
                                visibleForm = null
                            },
                            onDiscard = {
                                eventApi.discardNewEvent(editor.handle)
                                activeEditor = null
                                visibleForm = null
                            }
                        )
                    }
                }
            }

            EventKindV1.entries.forEach { kind ->
                val editor = when (val opened = eventApi.openNewEvent(kind, zone.id)) {
                    is EventEditorOpenResultV1.Opened -> opened.editor
                    else -> error("Expected new-event editor to open, got $opened")
                }
                compose.runOnUiThread {
                    activeEditor = editor
                    visibleForm = editor.form.toCoreDraftForm()
                }
                compose.waitForIdle()
                compose.onNodeWithText("新建事件").assertIsDisplayed()
                assertEquals(
                    false,
                    requireNotNull(eventApi.editorSnapshot(editor.handle)).meaningfulChanges
                )
                compose.onNodeWithTag("new-event-close").performClick()
                compose.waitForIdle()

                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(0L, store.identityCount())
                assertEquals(1L, store.nextSequencePreview())
                assertEquals(1L, store.nextQueueOrderPreview())
                assertEquals(0L, store.commitCount())
            }
        } finally {
            compose.runOnUiThread {
                activeEditor?.let { eventApi.discardNewEvent(it.handle) }
                activeEditor = null
                visibleForm = null
                compose.activity.setContent {}
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun changingTitleThenClosingInComposeStagesExactlyOneDraft() {
        val context: Context = compose.activity.applicationContext
        val databaseName = "pc-phase1-dirty-${java.util.UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val fixedClock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)
        val zone = ZoneId.of("Asia/Shanghai")
        val eventApi = PowerCalCoreFeatureApiV1(store, fixedClock).events
        var activeEditor by mutableStateOf<EventEditorSnapshotV1?>(null)
        var visibleForm by mutableStateOf<DraftForm?>(null)
        try {
            compose.setContent {
                PowerCalTheme {
                    activeEditor?.let { editor ->
                        NewEventEditorDialog(
                            eventFeatureApi = eventApi,
                            editor = editor,
                            form = visibleForm ?: editor.form.toCoreDraftForm(),
                            error = null,
                            busy = false,
                            onFormChange = { next ->
                                visibleForm = next
                                eventApi.updateNewEvent(editor.handle, next.toEventFormV1())
                                activeEditor = eventApi.editorSnapshot(editor.handle)
                            },
                            onSave = {},
                            onExit = {
                                eventApi.closeNewEvent(editor.handle)
                                activeEditor = null
                                visibleForm = null
                            },
                            onDiscard = {
                                eventApi.discardNewEvent(editor.handle)
                                activeEditor = null
                                visibleForm = null
                            }
                        )
                    }
                }
            }
            val editor = when (
                val opened = eventApi.openNewEvent(EventKindV1.MEMO, zone.id)
            ) {
                is EventEditorOpenResultV1.Opened -> opened.editor
                else -> error("Expected new-event editor to open, got $opened")
            }
            compose.runOnUiThread {
                activeEditor = editor
                visibleForm = editor.form.toCoreDraftForm()
            }
            compose.waitForIdle()
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("DEVICE_DIRTY_MEMO")
            compose.onNodeWithTag("new-event-close").performClick()
            compose.waitForIdle()

            assertEquals(1L, store.draftCount())
            assertEquals(1, store.pendingCount())
            assertEquals(1L, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertEquals(2L, store.nextQueueOrderPreview())
            assertEquals(0L, store.commitCount())
            assertEquals("DEVICE_DIRTY_MEMO", store.pendingDrafts().single().form.title)
        } finally {
            compose.runOnUiThread {
                activeEditor?.let { eventApi.discardNewEvent(it.handle) }
                activeEditor = null
                visibleForm = null
                compose.activity.setContent {}
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
