package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinBackupActionIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private val storage: Context = ApplicationProvider.getApplicationContext()
    private val namespace = "skin-backup-" + UUID.randomUUID()
    private val databaseName = "$namespace.db"
    private val context = object : ContextWrapper(storage) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
    }
    private val store = PowerStore(context, databaseName)
    private val registry = RecordingActivityResultRegistry()
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = registry
    }

    @Before fun setUp() {
        PowerCalSkinRegistry.reset()
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
        context.deleteDatabase(databaseName)
        listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
            .forEach { storage.deleteSharedPreferences("$namespace-$it") }
    }

    @Test fun skinBackupRequestsUseHostPickersAndExposeNoBackupContents() {
        val privateTitle = "PRIVATE_BACKUP_ONLY_TITLE"
        val privateNotes = "PRIVATE_BACKUP_ONLY_NOTES"
        val draft = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = privateTitle,
            notes = privateNotes
        ))
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        val before = counts()

        ui.activity.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner
            ) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitUntil(20_000) {
            WebSkinBridgeRuntime.state().optJSONArray("events") != null
        }
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("skinSettings")) }
        ui.waitUntil(10_000) {
            val state = WebSkinBridgeRuntime.state()
            state.optString("page") == "SETTINGS" && state.optJSONArray("optionGroups") != null
        }

        val settings = WebSkinBridgeRuntime.state()
        assertEquals(0, settings.getJSONArray("events").length())
        assertEquals(0, settings.getJSONObject("timeline").length())
        assertFalse(settings.toString().contains(privateTitle))
        assertFalse(settings.toString().contains(privateNotes))

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.export"))
        }
        ui.waitUntil(5_000) { registry.lastIntent != null }
        val export = requireNotNull(registry.lastIntent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, export.action)
        assertEquals("application/json", export.type)
        assertEquals("PowerCal-backup.json", export.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(before, counts())

        registry.clear()
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.restore"))
        }
        ui.waitUntil(5_000) { registry.lastIntent != null }
        val restore = requireNotNull(registry.lastIntent)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, restore.action)
        assertTrue("application/json" in (restore.getStringArrayExtra(Intent.EXTRA_MIME_TYPES) ?: emptyArray()))
        assertEquals(before, counts())
    }

    private fun counts() = listOf(
        store.draftCount(), store.pendingCount(), store.identityCount(),
        store.commitCount(), store.all().size.toLong()
    )

    private class RecordingActivityResultRegistry : ActivityResultRegistry() {
        var lastIntent: Intent? = null
            private set

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            lastIntent = contract.createIntent(ApplicationProvider.getApplicationContext(), input)
        }

        fun clear() { lastIntent = null }
    }
}
