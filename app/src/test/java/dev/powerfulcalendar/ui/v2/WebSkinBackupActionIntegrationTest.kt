package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.data.LegacyImporter
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

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

    @Test fun skinBackupActionsExportAndRestoreThroughHostFileResults() {
        val draft = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "ROUNDTRIP_PRIVATE_TITLE",
            notes = "roundtrip private notes"
        ))
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        val sourceRecord = store.all().single()
        val expectedPayload = store.exportBackup().getJSONObject("payload")
        val sourceCounts = counts()
        val authority = namespace.replace("-", "")
        val backupUri = Uri.parse("content://$authority/PowerCal-backup.json")
        val output = ByteArrayOutputStream()
        val readOverride = AtomicReference<ByteArray?>(null)
        listOf(context.contentResolver, ui.activity.contentResolver).distinct().forEach { resolver ->
            shadowOf(resolver).registerOutputStreamSupplier(backupUri) { output }
            shadowOf(resolver).registerInputStreamSupplier(backupUri) {
                (readOverride.get() ?: output.toByteArray()).inputStream()
            }
        }

        renderAndOpenSkinSettings()
        registry.clear()
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.export"))
        }
        ui.waitUntil(5_000) { registry.lastIntent?.action == Intent.ACTION_CREATE_DOCUMENT }
        assertTrue("cancelling the export picker must be accepted", registry.dispatch(null))
        assertEquals("cancelling export writes no bytes", 0, output.size())
        assertEquals("cancelling export does not mutate the store", sourceCounts, counts())

        registry.clear()
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.export"))
        }
        ui.waitUntil(5_000) { registry.lastIntent?.action == Intent.ACTION_CREATE_DOCUMENT }
        assertTrue(registry.dispatch(backupUri))
        var visibleFailure: String? = null
        ui.waitUntil(10_000) {
            if (output.size() > 0) return@waitUntil true
            val failure = ui.onAllNodesWithText("备份失败", substring = true)
                .fetchSemanticsNodes().firstOrNull()
            if (failure != null) {
                visibleFailure = failure.config[SemanticsProperties.Text]
                    .joinToString(separator = "") { it.text }
                return@waitUntil true
            }
            false
        }
        assertTrue("Host export did not write a file; visible Host message=$visibleFailure", output.size() > 0)
        val exportedText = output.toByteArray().toString(Charsets.UTF_8)
        val exportedEnvelope = JSONObject(exportedText)
        val verifiedExport = BackupEnvelope.verify(exportedEnvelope, PowerStore.VERSION)
        assertEquals(
            "Host export contains the source store's complete logical payload",
            BackupEnvelope.canonicalJson(expectedPayload),
            BackupEnvelope.canonicalJson(verifiedExport.payload)
        )
        ui.waitUntil(5_000) {
            ui.onAllNodesWithText("原生备份已导出并回读校验", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("export does not mutate the source store", sourceCounts, counts())

        val restoredDatabaseName = "$namespace-restored.db"
        val restoredStore = PowerStore(context, restoredDatabaseName)
        try {
            renderAndOpenSkinSettings(restoredStore)
            val emptyCounts = counts(restoredStore)
            registry.clear()
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.restore"))
            }
            ui.waitUntil(5_000) { registry.lastIntent?.action == Intent.ACTION_OPEN_DOCUMENT }

            assertTrue("cancelling restore must be accepted", registry.dispatch(null))
            assertEquals("cancel leaves restore target empty", emptyCounts, counts(restoredStore))

            val tamperedEnvelope = JSONObject(exportedText).put("payloadSha256", "0".repeat(64))
            readOverride.set(tamperedEnvelope.toString().toByteArray(Charsets.UTF_8))
            registry.clear()
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.restore"))
            }
            ui.waitUntil(5_000) { registry.lastIntent?.action == Intent.ACTION_OPEN_DOCUMENT }
            assertTrue(registry.dispatch(backupUri))
            ui.waitUntil(10_000) {
                ui.onAllNodesWithText("恢复失败", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("tampered backup must not mutate the target", emptyCounts, counts(restoredStore))

            readOverride.set(null)
            registry.clear()
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.restore"))
            }
            ui.waitUntil(5_000) { registry.lastIntent?.action == Intent.ACTION_OPEN_DOCUMENT }
            assertTrue(registry.dispatch(backupUri))
            ui.waitUntil(10_000) {
                restoredStore.all().size == 1 &&
                    restoredStore.identityCount() == store.identityCount() &&
                    restoredStore.commitCount() == store.commitCount()
            }

            val restoredRecord = restoredStore.all().single()
            assertEquals(sourceRecord.eventSeq, restoredRecord.eventSeq)
            assertEquals(sourceRecord.kind, restoredRecord.kind)
            assertEquals(sourceRecord.title, restoredRecord.title)
            assertEquals(sourceRecord.notes, restoredRecord.notes)
            assertEquals(sourceRecord.startMillis, restoredRecord.startMillis)
            assertEquals(sourceRecord.endMillis, restoredRecord.endMillis)
            assertEquals(store.identityCount(), restoredStore.identityCount())
            assertEquals(store.commitCount(), restoredStore.commitCount())
            assertEquals(store.draftCount(), restoredStore.draftCount())
            assertEquals(store.pendingCount(), restoredStore.pendingCount())
            assertEquals(sourceCounts, counts())
            ui.waitUntil(5_000) {
                ui.onAllNodesWithText("已恢复", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            val restoredCounts = counts(restoredStore)
            registry.clear()
            ui.runOnIdle {
                WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.restore"))
            }
            ui.waitUntil(5_000) { registry.lastIntent?.action == Intent.ACTION_OPEN_DOCUMENT }
            assertTrue(registry.dispatch(backupUri))
            ui.waitUntil(10_000) {
                ui.onAllNodesWithText("恢复失败", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("restore into a nonempty target must not overwrite or duplicate data",
                restoredCounts, counts(restoredStore))
        } finally {
            ui.activity.runOnUiThread { ui.activity.setContent { } }
            ui.waitForIdle()
            restoredStore.close()
            context.deleteDatabase(restoredDatabaseName)
        }
    }

    private fun renderAndOpenSkinSettings(hostStore: PowerStore = store) {
        ui.activity.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner
            ) {
                PowerCalTheme { PowerCalApp(hostStore) }
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

    private fun counts(hostStore: PowerStore = store) = listOf(
        hostStore.draftCount(), hostStore.pendingCount(), hostStore.identityCount(),
        hostStore.commitCount(), hostStore.all().size.toLong()
    )

    private class RecordingActivityResultRegistry : ActivityResultRegistry() {
        private var requestCode: Int? = null
        var lastIntent: Intent? = null
            private set

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            this.requestCode = requestCode
            lastIntent = contract.createIntent(ApplicationProvider.getApplicationContext(), input)
        }

        fun dispatch(uri: Uri?): Boolean = dispatchResult(
            requireNotNull(requestCode) { "No Host picker launch was recorded" }, uri
        )

        fun clear() {
            requestCode = null
            lastIntent = null
        }
    }

}
