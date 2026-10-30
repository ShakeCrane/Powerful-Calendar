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
import dev.powerfulcalendar.core.NewEventEditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
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
        var activeSession by mutableStateOf<NewEventEditSession?>(null)
        var visibleForm by mutableStateOf<DraftForm?>(null)
        try {
            compose.setContent {
                PowerCalTheme {
                    activeSession?.let { session ->
                        NewEventEditorDialog(
                            session = session,
                            form = visibleForm ?: session.form,
                            error = null,
                            busy = false,
                            onFormChange = { next -> session.edit(next); visibleForm = next },
                            onSave = {},
                            onExit = {
                                session.close(store)
                                activeSession = null
                                visibleForm = null
                            },
                            onDiscard = { session.discard(store); activeSession = null }
                        )
                    }
                }
            }
            val fixedClock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)
            val zone = ZoneId.of("Asia/Shanghai")

            TemporalKind.entries.forEach { kind ->
                val session = NewEventEditSession.open(kind, fixedClock, zone)
                compose.runOnUiThread { activeSession = session; visibleForm = session.form }
                compose.waitForIdle()
                compose.onNodeWithText("新建事件").assertIsDisplayed()
                assertEquals(false, session.meaningfulDiff)
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
            compose.runOnUiThread { activeSession = null; visibleForm = null; compose.activity.setContent {} }
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
        var activeSession by mutableStateOf<NewEventEditSession?>(null)
        var visibleForm by mutableStateOf<DraftForm?>(null)
        try {
            compose.setContent {
                PowerCalTheme {
                    activeSession?.let { session ->
                        NewEventEditorDialog(
                            session = session,
                            form = visibleForm ?: session.form,
                            error = null,
                            busy = false,
                            onFormChange = { next -> session.edit(next); visibleForm = next },
                            onSave = {},
                            onExit = {
                                session.close(store)
                                activeSession = null
                                visibleForm = null
                            },
                            onDiscard = { session.discard(store); activeSession = null }
                        )
                    }
                }
            }
            val session = NewEventEditSession.open(
                TemporalKind.MEMO,
                Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC),
                ZoneId.of("Asia/Shanghai")
            )
            compose.runOnUiThread { activeSession = session; visibleForm = session.form }
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
            compose.runOnUiThread { activeSession = null; visibleForm = null; compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
