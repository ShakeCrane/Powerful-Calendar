package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.NewEventEditorDialog

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.NewEventEditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
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
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "new-event-ui-${UUID.randomUUID()}.db")
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    private fun newSession() = NewEventEditSession.open(
        TemporalKind.ALARM,
        Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC),
        zone
    )

    private fun render(session: NewEventEditSession) {
        val form = mutableStateOf(session.form)
        ui.setContent { PowerCalTheme {
            NewEventEditorDialog(
                session = session,
                form = form.value,
                error = null,
                busy = false,
                onFormChange = { next -> session.edit(next); form.value = next },
                onSave = { session.save(store) },
                onExit = { session.close(store) },
                onDiscard = { session.discard(store) }
            )
        } }
    }

    @Test fun openingAndClosingTheNativeEditorLeavesNoDraftOrIdentity() {
        val session = newSession()
        render(session)
        ui.onNodeWithText("新建事件").assertIsDisplayed()
        assertEquals(false, session.meaningfulDiff)
        ui.onNodeWithTag("new-event-close").performClick()

        assertEquals(0L, store.draftCount())
        assertEquals(0, store.pendingCount())
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())
        assertEquals(0L, store.commitCount())
    }

    @Test fun nativeEditorStagesExactlyOneChangedDraftWhenClosedWithX() {
        val session = newSession()
        render(session)
        ui.onAllNodes(hasSetTextAction())[0].performTextReplacement("Changed title")
        assertEquals("Changed title", session.form.title)
        assertEquals(true, session.meaningfulDiff)
        ui.onNodeWithTag("new-event-close").performClick()

        assertEquals(1L, store.draftCount())
        assertEquals(1, store.pendingCount())
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(0L, store.commitCount())
        assertEquals("Changed title", store.pendingDrafts().single().form.title)
    }

    @Test fun recompositionKeepsEditedTimeAndTheSessionCapturedClock() {
        val session = NewEventEditSession.open(
            TemporalKind.ACTIVITY,
            Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC),
            zone
        )
        val form = mutableStateOf(session.form)
        val recomposition = mutableIntStateOf(0)
        val capturedNow = session.defaults.now
        ui.setContent {
            val revision = recomposition.intValue
            PowerCalTheme {
                androidx.compose.material3.Text("revision $revision")
                NewEventEditorDialog(
                    session = session,
                    form = form.value,
                    error = null,
                    busy = false,
                    onFormChange = { next -> session.edit(next); form.value = next },
                    onSave = { session.save(store) },
                    onExit = { session.close(store) },
                    onDiscard = { session.discard(store) }
                )
            }
        }

        ui.onAllNodes(hasSetTextAction())[1].performTextReplacement("2026-10-26 08:00")
        assertEquals("2026-10-26 08:00", session.form.startText)
        ui.runOnIdle { recomposition.intValue++ }
        ui.waitForIdle()

        val fieldValue = ui.onAllNodes(hasSetTextAction())[1].fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals("2026-10-26 08:00", fieldValue)
        assertEquals("2026-10-26 08:00", session.form.startText)
        assertEquals(capturedNow, session.defaults.now)
        assertTrue(session.meaningfulDiff)
        assertEquals(0L, store.draftCount())
    }
}
