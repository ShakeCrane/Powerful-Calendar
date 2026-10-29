package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
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
import java.util.UUID

/** Exercises the production PowerCalApp lifecycle observer rather than calling session.checkpoint directly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-xhdpi")
class PowerCalAppLifecycleCheckpointTest {
    @get:Rule val ui = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var appContext: Context
    private lateinit var store: PowerStore
    private lateinit var databaseName: String
    private lateinit var skinPreferences: android.content.SharedPreferences
    private lateinit var onboardingPreferences: android.content.SharedPreferences
    private var hadSelectedSkin = false
    private var previousSelectedSkin: String? = null
    private var hadAskedCalendarImport = false
    private var previousAskedCalendarImport = false

    @Before fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        databaseName = "ed03-${UUID.randomUUID()}.db"
        store = PowerStore(appContext, databaseName)
        store.readableDatabase

        skinPreferences = appContext.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        hadSelectedSkin = skinPreferences.contains("selected")
        previousSelectedSkin = runCatching { skinPreferences.getString("selected", null) }.getOrNull()
        skinPreferences.edit().putString("selected", "powercal-default").commit()

        onboardingPreferences = appContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        hadAskedCalendarImport = onboardingPreferences.contains("system-calendar-import-asked")
        previousAskedCalendarImport = onboardingPreferences.getBoolean("system-calendar-import-asked", false)
        onboardingPreferences.edit().putBoolean("system-calendar-import-asked", true).commit()
        PowerCalSkinRegistry.reset()
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        store.close()
        appContext.deleteDatabase(databaseName)
        skinPreferences.edit().apply {
            if (hadSelectedSkin) putString("selected", previousSelectedSkin)
            else remove("selected")
        }.commit()
        onboardingPreferences.edit().apply {
            if (hadAskedCalendarImport) putBoolean("system-calendar-import-asked", previousAskedCalendarImport)
            else remove("system-calendar-import-asked")
        }.commit()
        PowerCalSkinRegistry.reset()
    }

    @Test fun cleanTransientEditorDoesNotCreateRowsWhenPowerCalAppStops() {
        showApp()
        openNewEditor(TemporalKind.MEMO)

        stopActivity()
        Thread.sleep(250)

        assertEquals(0L, store.draftCount())
        assertEquals(0, store.pendingCount())
        assertTrue(store.all().isEmpty())
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())
        assertEquals(1L, store.nextQueueOrderPreview())
        assertEquals(0L, store.commitCount())
    }

    @Test fun dirtyTransientEditorIsCheckpointedOnceAcrossRepeatedStopCycles() {
        showApp()
        openNewEditor(TemporalKind.ALARM)
        ui.onAllNodes(hasSetTextAction())[0].performTextReplacement("ED03_STOP_RECOVERY")
        ui.waitForIdle()

        stopActivity()
        ui.waitUntil(10_000) { store.pendingCount() == 1 }

        val first = store.pendingDrafts().single()
        assertEquals("ED03_STOP_RECOVERY", first.form.title)
        assertEquals(1L, store.draftCount())
        assertTrue(store.all().isEmpty())
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(2L, store.nextQueueOrderPreview())
        assertEquals(0L, store.commitCount())

        startActivity()
        stopActivity()
        Thread.sleep(250)

        val afterRepeatedStop = store.pendingDrafts().single()
        assertEquals(first.draftId, afterRepeatedStop.draftId)
        assertEquals(first.draftVersion, afterRepeatedStop.draftVersion)
        assertEquals(first.queueOrder, afterRepeatedStop.queueOrder)
        assertEquals("ED03_STOP_RECOVERY", afterRepeatedStop.form.title)
        assertEquals(1L, store.draftCount())
        assertEquals(1L, store.identityCount())
        assertEquals(0L, store.commitCount())
    }

    @Test fun dirtyTransientEditorIsDurableAfterActivityRecreation() {
        showApp()
        openNewEditor(TemporalKind.MEMO)
        ui.onAllNodes(hasSetTextAction())[0].performTextReplacement("ED03_ACTIVITY_RECREATE")
        ui.waitForIdle()

        val previousActivity = ui.activity
        ui.runOnUiThread { previousActivity.recreate() }
        ui.waitUntil(20_000) {
            ui.activity !== previousActivity &&
                ui.activity.lifecycle.currentState == Lifecycle.State.RESUMED
        }
        assertEquals(Lifecycle.State.DESTROYED, previousActivity.lifecycle.currentState)
        ui.waitUntil(10_000) { store.pendingCount() == 1 }

        store.close()
        store = PowerStore(appContext, databaseName)
        val recovered = store.pendingDrafts().single()
        assertEquals("ED03_ACTIVITY_RECREATE", recovered.form.title)
        assertEquals(1L, store.draftCount())
        assertTrue(store.all().isEmpty())
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(2L, store.nextQueueOrderPreview())
        assertEquals(0L, store.commitCount())
    }

    private fun showApp() {
        ui.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
        ui.waitUntil(20_000) {
            ui.onAllNodesWithContentDescription("新建事件").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openNewEditor(kind: TemporalKind) {
        ui.onNodeWithContentDescription("新建事件").performClick()
        ui.onNodeWithText(kind.label).performClick()
        ui.onNodeWithText("新建事件").assertIsDisplayed()
    }

    private fun stopActivity() {
        ui.runOnUiThread {
            val lifecycle = ui.activity.lifecycle as LifecycleRegistry
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        ui.waitForIdle()
    }

    private fun startActivity() {
        ui.runOnUiThread {
            val lifecycle = ui.activity.lifecycle as LifecycleRegistry
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        ui.waitForIdle()
    }
}
