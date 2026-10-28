package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.data.LegacyImporter
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
    private val legacyPath = context.getDatabasePath(LegacyImporter.LEGACY_DATABASE)
    private val store = PowerStore(context, databaseName)
    private val registry = RecordingActivityResultRegistry()
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = registry
    }

    @Before fun setUp() {
        PowerCalSkinRegistry.reset()
        context.deleteDatabase(LegacyImporter.LEGACY_DATABASE)
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
        context.deleteDatabase(LegacyImporter.LEGACY_DATABASE)
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

    @Test fun skinLegacyMigrationRequiresHostConfirmationAndKeepsOldDatabase() {
        createLegacyDatabase()
        val originalLegacySize = legacyPath.length()

        renderAndOpenSkinSettings()
        val settings = WebSkinBridgeRuntime.state()
        val options = settings.getJSONArray("optionGroups")
        val migrationOption = (0 until options.length()).asSequence()
            .flatMap { options.getJSONObject(it).getJSONArray("items").let { items ->
                (0 until items.length()).asSequence().map(items::getJSONObject)
            } }
            .single { it.getString("id") == "data.legacy.migrate" }
        assertEquals("迁移旧版 PowerCal 数据", migrationOption.getString("label"))
        assertFalse(settings.toString().contains("PRIVATE_LEGACY_EVENT_TITLE"))
        val before = counts()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.legacy.migrate"))
        }
        ui.onNodeWithText("迁移旧版事件？").assertIsDisplayed()
        assertEquals("a skin action only requests a trusted confirmation", before, counts())
        ui.onNodeWithText("取消").performClick()
        ui.waitUntil(5_000) {
            ui.onAllNodesWithText("迁移旧版事件？").fetchSemanticsNodes().isEmpty()
        }
        assertEquals("cancel must not import or allocate persistent identity", before, counts())

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.legacy.migrate"))
        }
        ui.onNodeWithText("开始迁移").performClick()
        ui.waitUntil(10_000) { store.all().size == 1 }
        val imported = store.all().single()
        assertEquals("PRIVATE_LEGACY_EVENT_TITLE", imported.title)
        assertEquals(LegacyImporter.SOURCE, imported.source)
        assertEquals(1, imported.eventSeq)
        assertEquals(originalLegacySize, legacyPath.length())
        assertEquals(1, legacyEventCount())
    }

    private fun renderAndOpenSkinSettings() {
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
    }

    private fun createLegacyDatabase() {
        val db = SQLiteDatabase.openOrCreateDatabase(legacyPath, null)
        db.execSQL(
            """CREATE TABLE events (
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 title TEXT NOT NULL,
                 start_ms INTEGER NOT NULL,
                 end_ms INTEGER NOT NULL,
                 notes TEXT NOT NULL DEFAULT '',
                 location TEXT NOT NULL DEFAULT '',
                 priority TEXT NOT NULL DEFAULT 'NORMAL',
                 reminder_minutes INTEGER NOT NULL DEFAULT 10,
                 all_day INTEGER NOT NULL DEFAULT 0,
                 source TEXT NOT NULL DEFAULT 'local',
                 external_id TEXT
               )"""
        )
        db.execSQL(
            """INSERT INTO events(title,start_ms,end_ms,notes,location,priority,
               reminder_minutes,all_day,source,external_id) VALUES(?,?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>("PRIVATE_LEGACY_EVENT_TITLE", 1_790_000_000_000L,
                1_790_003_600_000L, "notes", "location", "NORMAL", 10, 0, "local", null)
        )
        db.close()
    }

    private fun legacyEventCount(): Int {
        val db = SQLiteDatabase.openDatabase(legacyPath.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery("SELECT COUNT(*) FROM events", null).use {
                check(it.moveToFirst())
                it.getInt(0)
            }
        } finally {
            db.close()
        }
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
