package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.reminders.ReminderScheduler
import org.json.JSONObject
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
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinReminderDiagnosticsIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val storage: Context = ApplicationProvider.getApplicationContext()
    private val namespace = "skin-reminders-" + UUID.randomUUID()
    // Robolectric includes this test name in a long Windows temp path; keep the DB leaf short.
    private val databaseName = "r.db"
    private val context = object : ContextWrapper(storage) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
    }
    private val store = PowerStore(storage, databaseName)
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry
            get() = ui.activity.activityResultRegistry
    }
    private val controlledCalls = AtomicInteger()

    @Volatile
    private var nextRun: (() -> ReminderScheduler.ReconcileReport)? = null

    private val runner: (Context, PowerStore) -> ReminderScheduler.ReconcileReport = { _, _ ->
        nextRun?.invoke() ?: ReminderScheduler.ReconcileReport(0, 0, 0, 0, 0)
    }

    @Before fun setUp() {
        PowerCalSkinRegistry.reset()
        store.readableDatabase
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread {
            ui.activity.setContent { }
            PowerCalSkinRegistry.reset()
        }
        ui.waitForIdle()
        store.close()
        storage.deleteDatabase(databaseName)
        listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
            .forEach { storage.deleteSharedPreferences("$namespace-$it") }
    }

    @Test fun realHostBridgeProjectsOnlyBoundedSettingsDataAndReconcileIsIdempotentlyGuarded() {
        seedConfiguredReminders(enabledCount = 41, disabledCount = 1)
        setApp()
        ui.waitUntil(20_000) {
            WebSkinBridgeRuntime.state().optJSONArray("events")?.length() == 42
        }

        val timeline = WebSkinBridgeRuntime.state()
        assertEquals(42, timeline.getJSONArray("events").length())
        assertTrue(timeline.isNull("reminderDiagnostics"))
        assertTrue(timeline.isNull("reminderReconcile"))
        assertEquals("TIMELINE", timeline.getString("page"))

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("skinSettings")) }
        ui.waitUntil(10_000) { WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" }

        val settings = WebSkinBridgeRuntime.state()
        val diagnosticPayload = settings.getJSONObject("reminderDiagnostics")
        assertEquals(40, diagnosticPayload.getInt("itemLimit"))
        assertTrue("41 eligible reminders exceed the 40-row projection" , diagnosticPayload.getBoolean("truncated"))
        assertEquals(40, diagnosticPayload.getJSONArray("items").length())
        assertEquals("IDLE", settings.getJSONObject("reminderReconcile").getString("status"))
        assertEquals(0, settings.getJSONArray("events").length())

        val diagnosticText = diagnosticPayload.toString()
        assertTrue(diagnosticText.contains("VISIBLE_REMINDER_TITLE"))
        assertFalse(diagnosticText.contains("PRIVATE_NOTE"))
        assertFalse(diagnosticText.contains("PRIVATE_LOCATION"))
        assertFalse(diagnosticText.contains("credential"))
        assertFalse(diagnosticText.contains("occurrenceKey"))
        assertFalse(diagnosticText.contains("eventSeq"))
        assertEquals(
            setOf("displayId", "title", "titleTruncated", "kind", "desiredState", "androidHint", "lastExecution"),
            diagnosticPayload.getJSONArray("items").getJSONObject(0).keys().asSequence().toSet()
        )
        assertTrue(diagnosticPayload.getJSONArray("items").getJSONObject(0).getString("title").length <= 120)
        assertFalse(diagnosticText.contains("HIDDEN_DISABLED_REMINDER"))

        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        controlledCalls.set(0)
        nextRun = {
            controlledCalls.incrementAndGet()
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "test reconcile gate timed out" }
            ReminderScheduler.ReconcileReport(3, 2, 1, 0, 0)
        }
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("reminders.reconcile"))
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("reminders.reconcile"))
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        ui.waitUntil(5_000) {
            WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile").getString("status") == "RUNNING"
        }
        assertEquals(1, controlledCalls.get())
        assertFalse(optionEnabled(WebSkinBridgeRuntime.state(), "reminders.reconcile"))
        release.countDown()
        ui.waitUntil(10_000) {
            WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile").getString("status") == "SUCCEEDED"
        }
        val succeeded = WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
        assertEquals(3, succeeded.getInt("checked"))
        assertEquals(2, succeeded.getInt("scheduled"))
        assertEquals(1, succeeded.getInt("blocked"))
        assertEquals(0, succeeded.getInt("pending"))
        assertEquals(0, succeeded.getInt("failed"))
        assertFalse(succeeded.getBoolean("truncated"))
        nextRun = null

        nextRun = { ReminderScheduler.ReconcileReport(7, 5, 0, 2, 0) }
        invokeReconcile()
        ui.waitUntil(10_000) {
            val state = WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
            state.getInt("checked") == 7 && state.getString("status") == "FAILED"
        }
        assertEquals(2, WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile").getInt("pending"))

        nextRun = { throw IllegalStateException("PRIVATE_FAILURE_DETAIL") }
        invokeReconcile()
        ui.waitUntil(10_000) {
            val state = WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
            state.getInt("failed") == 1 && state.getString("status") == "FAILED"
        }
        assertFalse(WebSkinBridgeRuntime.state().toString().contains("PRIVATE_FAILURE_DETAIL"))
        assertTrue(WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile").getString("status") != "RUNNING")
    }

    private fun invokeReconcile() {
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("reminders.reconcile"))
        }
    }

    private fun setApp() {
        ui.activity.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner
            ) {
                PowerCalTheme { PowerCalAppWithReminderReconciler(store, runner) }
            }
        }
    }

    private fun seedConfiguredReminders(enabledCount: Int, disabledCount: Int) {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = System.currentTimeMillis()
        repeat(enabledCount + disabledCount) { index ->
            val disabled = index >= enabledCount
            val title = when {
                disabled -> "HIDDEN_DISABLED_REMINDER"
                index == 0 -> "VISIBLE_REMINDER_TITLE_" + "L".repeat(150)
                else -> "Visible reminder $index"
            }
            val draft = store.newDraft(DraftForm(
                kind = TemporalKind.ALARM,
                title = title,
                startText = DraftForm.formatInstant(now + 3_600_000L + index * 60_000L, zone),
                notes = "PRIVATE_NOTE credential=never-project",
                location = "PRIVATE_LOCATION api-key=never-project",
                reminderMinutes = 0,
                enabled = !disabled,
                zoneId = zone.id
            ))
            assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        }
    }

    private fun optionEnabled(state: JSONObject, id: String): Boolean {
        val groups = state.getJSONArray("optionGroups")
        for (groupIndex in 0 until groups.length()) {
            val items = groups.getJSONObject(groupIndex).getJSONArray("items")
            for (itemIndex in 0 until items.length()) {
                val item = items.getJSONObject(itemIndex)
                if (item.getString("id") == id) return item.getBoolean("enabled")
            }
        }
        error("Missing Host option $id")
    }
}
