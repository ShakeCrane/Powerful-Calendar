package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.core.app.ActivityOptionsCompat
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRestoreHostTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var storage: Context
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var sourceStore: PowerStore
    private lateinit var backup: JSONObject
    private lateinit var namespace: String
    private lateinit var databaseName: String
    private lateinit var sourceDatabaseName: String
    private val resultRegistry = RecordingActivityResultRegistry()
    private val resultRegistryOwner = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = resultRegistry
    }

    @Before fun setUp() {
        storage = ApplicationProvider.getApplicationContext()
        namespace = "backup-restore-" + UUID.randomUUID()
        databaseName = "$namespace-target.db"
        sourceDatabaseName = "$namespace-source.db"
        context = IsolatedContext(ui.activity, storage, namespace)
        store = PowerStore(storage, databaseName)
        store.readableDatabase
        sourceStore = PowerStore(storage, sourceDatabaseName)
        sourceStore.readableDatabase
        backup = createSourceBackup()
        PowerCalSkinRegistry.reset()
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        if (::sourceStore.isInitialized) sourceStore.close()
        if (::store.isInitialized) store.close()
        if (::storage.isInitialized) {
            if (::sourceDatabaseName.isInitialized) storage.deleteDatabase(sourceDatabaseName)
            if (::databaseName.isInitialized) storage.deleteDatabase(databaseName)
        }
        listOf(
            "powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-ai",
            "powercal-profile", "powercal-import-checkpoints"
        ).forEach { storage.deleteSharedPreferences("$namespace-$it") }
        File(storage.cacheDir, namespace).deleteRecursively()
        PowerCalSkinRegistry.reset()
    }

    @Test fun clearRestore() {
        val inputUri = Uri.parse("content://$namespace/verified.json")
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(inputUri) {
            ByteArrayInputStream(backup.toString().toByteArray(Charsets.UTF_8))
        }
        showApp()

        launchBackupRestore()
        assertEquals("first restore action opens one picker: ${resultRegistry.launchedActions}",
            1, resultRegistry.launchCount)
        launchBackupRestore()
        assertEquals("a restore picker can only be outstanding once", 1, resultRegistry.launchCount)
        val launched = requireNotNull(resultRegistry.lastIntent) {
            "Clear semantic restore option did not launch the native OpenDocument picker"
        }
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched.action)
        assertTrue(launched.type == "*/*" || launched.type == "application/json")
        assertTrue(resultRegistry.dispatch(inputUri))

        ui.waitUntil(30_000) {
            ui.onAllNodesWithText("已恢复 1 个事件", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val record = store.all().single()
        assertEquals("Restored formal alarm", record.title)
        assertEquals(2L, store.draftCount())
        assertEquals("Pending restored memo", store.pendingDrafts().single().form.title)
        val reminder = requireNotNull(store.intentFor(record.eventSeq))
        assertTrue(reminder.desired in setOf(
            PowerStore.DesiredSchedule.SCHEDULED, PowerStore.DesiredSchedule.BLOCKED
        ))
        assertTrue(ui.onAllNodesWithText("旧授权与旧执行记录未恢复", substring = true)
            .fetchSemanticsNodes().isNotEmpty())

        val restoredState = captureStoreState()
        launchBackupRestore()
        assertEquals(2, resultRegistry.launchCount)
        assertTrue("the second restore attempt is returned through a fresh picker result",
            resultRegistry.dispatch(inputUri))
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("恢复失败：", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(restoredState, captureStoreState())
    }

    @Test fun cancelRestore() {
        val before = captureStoreState()
        showApp()

        launchBackupRestore()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, resultRegistry.lastIntent?.action)
        assertTrue(resultRegistry.cancel())
        ui.waitForIdle()

        assertEquals(before, captureStoreState())
    }

    @Test fun restoreRejectedWhileDeckIsOpen() {
        showApp()
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("openDeck")) }
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("没有待处理卡片").fetchSemanticsNodes().isNotEmpty()
        }

        launchBackupRestore()

        assertEquals(0, resultRegistry.launchCount)
        assertEquals(null, resultRegistry.lastIntent)
        assertTrue(store.all().isEmpty())
    }

    @Test fun restoreRejectedWhileDeckOpens() {
        showApp()
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("openDeck"))
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.restore"))
        }
        ui.waitForIdle()

        assertEquals(0, resultRegistry.launchCount)
        assertEquals(null, resultRegistry.lastIntent)
        assertTrue(store.all().isEmpty())
    }

    @Test fun nonEmptyTarget() {
        store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "Existing target draft",
            zoneId = "UTC"
        ))
        val before = captureStoreState()
        val inputUri = Uri.parse("content://$namespace/non-empty-target.json")
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(inputUri) {
            ByteArrayInputStream(backup.toString().toByteArray(Charsets.UTF_8))
        }
        showApp()

        launchBackupRestore()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, resultRegistry.lastIntent?.action)
        assertTrue(resultRegistry.dispatch(inputUri))
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("目标数据库不是空库", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(before, captureStoreState())
        assertEquals("Existing target draft", store.pendingDrafts().single().form.title)
        assertFalse(store.all().any { it.title == "Restored formal alarm" })
    }

    @Test fun invalidHash() {
        val corrupted = JSONObject(backup.toString())
        corrupted.getJSONObject("payload").put("nextSeq",
            corrupted.getJSONObject("payload").getLong("nextSeq") + 1L)
        val before = captureStoreState()
        val inputUri = Uri.parse("content://$namespace/hash-mismatch.json")
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(inputUri) {
            ByteArrayInputStream(corrupted.toString().toByteArray(Charsets.UTF_8))
        }
        showApp()

        launchBackupRestore()
        assertTrue(resultRegistry.dispatch(inputUri))
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("备份完整性校验失败", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(before, captureStoreState())
        assertTrue(store.all().isEmpty())
    }

    private fun createSourceBackup(): JSONObject {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2035-01-01T09:00:00Z").toEpochMilli()
        val alarm = sourceStore.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "Restored formal alarm",
            startText = DraftForm.formatInstant(start, zone),
            reminderMinutes = 5,
            zoneId = zone.id
        ))
        check(sourceStore.commit(alarm.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
        sourceStore.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "Pending restored memo",
            zoneId = zone.id
        ))
        return sourceStore.exportBackup()
    }

    private fun showApp() {
        ui.activity.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides resultRegistryOwner
            ) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()
    }

    private fun launchBackupRestore() {
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.restore"))
        }
        ui.waitForIdle()
    }

    private fun captureStoreState() = StoreState(
        records = store.all(),
        drafts = store.pendingDrafts().map { it.draftId to it.form },
        draftCount = store.draftCount(),
        identityCount = store.identityCount(),
        nextSequence = store.nextSequencePreview(),
        commitCount = store.commitCount(),
        reminders = PowerStore.DesiredSchedule.entries.flatMap { store.intentsInState(it) }
    )

    private data class StoreState(
        val records: List<dev.powerfulcalendar.core.TemporalRecord>,
        val drafts: List<Pair<Long, DraftForm>>,
        val draftCount: Long,
        val identityCount: Long,
        val nextSequence: Long,
        val commitCount: Long,
        val reminders: List<PowerStore.ScheduleIntent>
    )

    private class RecordingActivityResultRegistry : ActivityResultRegistry() {
        private var requestCode: Int? = null
        var launchCount = 0
            private set
        var lastIntent: Intent? = null
            private set
        val launchedActions = mutableListOf<String?>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            launchCount++
            this.requestCode = requestCode
            lastIntent = contract.createIntent(ApplicationProvider.getApplicationContext(), input)
            launchedActions += lastIntent?.action
        }

        fun dispatch(uri: Uri): Boolean = dispatchResult(
            requireNotNull(requestCode) { "No OpenDocument launch was recorded" }, uri
        )

        fun cancel(): Boolean = dispatchResult(
            requireNotNull(requestCode) { "No OpenDocument launch was recorded" }, null
        )
    }

    private class IsolatedContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply { check(mkdirs() || isDirectory) }
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = backing.getDatabasePath("$namespace-$name")
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences("$namespace-$name", mode)
    }
}
