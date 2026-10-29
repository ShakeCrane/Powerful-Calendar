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
import dev.powerfulcalendar.core.BackupEnvelope
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SemanticBackupExportIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var storage: Context
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var namespace: String
    private lateinit var databaseName: String
    private val resultRegistry = RecordingActivityResultRegistry()
    private val resultRegistryOwner = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = resultRegistry
    }

    @Before fun setUp() {
        storage = ApplicationProvider.getApplicationContext()
        namespace = "backup-export-" + UUID.randomUUID()
        databaseName = "$namespace.db"
        context = IsolatedContext(ui.activity, storage, namespace)
        store = PowerStore(storage, databaseName)
        store.readableDatabase
        seedStore()
        PowerCalSkinRegistry.reset()
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        store.close()
        storage.deleteDatabase(databaseName)
        listOf(
            "powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-ai",
            "powercal-profile", "powercal-import-checkpoints"
        ).forEach { storage.deleteSharedPreferences("$namespace-$it") }
        File(storage.cacheDir, namespace).deleteRecursively()
        PowerCalSkinRegistry.reset()
    }

    @Test
    fun verifiedBackupExportRoundTrips() {
        val before = captureStoreState()
        val expectedPayload = BackupEnvelope.verify(store.exportBackup(), PowerStore.VERSION).payload
        val outputUri = Uri.parse("content://powercal-backup-export-$namespace/verified.json")
        val output = ByteArrayOutputStream()
        shadowOf(ui.activity.contentResolver).registerOutputStreamSupplier(outputUri) { output }
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(outputUri) {
            ByteArrayInputStream(output.toByteArray())
        }
        showApp()

        launchBackupExport()
        val launched = requireNotNull(resultRegistry.lastIntent) {
            "Clear semantic backup option did not launch CreateDocument"
        }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched.action)
        assertEquals("application/json", launched.type)
        assertEquals("powercal-backup.json", launched.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(resultRegistry.dispatch(outputUri))

        val success = "原生备份已导出并回读校验（SHA-256 匹配）；文件为明文敏感数据"
        ui.waitUntil(20_000) {
            output.size() > 0 && ui.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty()
        }

        val encoded = output.toString(Charsets.UTF_8.name())
        val envelope = JSONObject(encoded)
        val verified = BackupEnvelope.verify(envelope, PowerStore.VERSION)
        assertEquals(BackupEnvelope.FORMAT, envelope.getString("format"))
        assertEquals(BackupEnvelope.FORMAT_VERSION, envelope.getInt("backupFormatVersion"))
        assertEquals(expectedPayload.getJSONArray("objects").length(),
            verified.payload.getJSONArray("objects").length())
        assertEquals(expectedPayload.getJSONArray("drafts").length(),
            verified.payload.getJSONArray("drafts").length())
        assertEquals(BackupEnvelope.canonicalJson(expectedPayload),
            BackupEnvelope.canonicalJson(verified.payload))
        BackupEnvelope.verifyReadback(envelope, JSONObject(encoded), PowerStore.VERSION)
        assertEquals(before, captureStoreState())
    }

    @Test
    fun cancelingClearBackupExportDoesNotWriteOrMutateStore() {
        val before = captureStoreState()
        showApp()

        launchBackupExport()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, resultRegistry.lastIntent?.action)
        assertTrue(resultRegistry.cancel())
        ui.waitForIdle()

        assertEquals(before, captureStoreState())
    }

    @Test
    fun outputFailureIsReportedWithoutClaimingBackupSuccess() {
        val before = captureStoreState()
        val outputUri = Uri.parse("content://powercal-backup-export-$namespace/output-failure.json")
        shadowOf(ui.activity.contentResolver).registerOutputStreamSupplier(outputUri) {
            throw IOException("injected output failure")
        }
        showApp()

        launchBackupExport()
        assertTrue(resultRegistry.dispatch(outputUri))
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("备份失败：", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(ui.onAllNodesWithText("原生备份已导出并回读校验").fetchSemanticsNodes().isNotEmpty())
        assertEquals(before, captureStoreState())
    }

    @Test
    fun unavailableReadbackIsClearlyReportedAsUnverified() {
        val before = captureStoreState()
        val outputUri = Uri.parse("content://powercal-backup-export-$namespace/unreadable.json")
        val output = ByteArrayOutputStream()
        shadowOf(ui.activity.contentResolver).registerOutputStreamSupplier(outputUri) { output }
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(outputUri) {
            throw IOException("injected readback unavailable")
        }
        showApp()

        launchBackupExport()
        assertTrue(resultRegistry.dispatch(outputUri))
        val unverified = "备份已写入，但该文件提供方不支持回读校验；请自行保留并验证文件"
        ui.waitUntil(20_000) {
            output.size() > 0 && ui.onAllNodesWithText(unverified).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(ui.onAllNodesWithText("原生备份已导出并回读校验").fetchSemanticsNodes().isNotEmpty())
        assertEquals(before, captureStoreState())
    }

    @Test
    fun readbackWatermarkMismatchIsReportedAsFailure() {
        val before = captureStoreState()
        val outputUri = Uri.parse("content://powercal-backup-export-$namespace/mismatch.json")
        val output = ByteArrayOutputStream()
        shadowOf(ui.activity.contentResolver).registerOutputStreamSupplier(outputUri) { output }
        shadowOf(ui.activity.contentResolver).registerInputStreamSupplier(outputUri) {
            val original = JSONObject(output.toString(Charsets.UTF_8.name()))
            val payload = JSONObject(original.getJSONObject("payload").toString())
            payload.put("nextSeq", payload.getLong("nextSeq") + 1L)
            BackupEnvelope.create(
                payload = payload,
                databaseSchemaVersion = original.getInt("databaseSchemaVersion"),
                appVersionCode = original.getLong("appVersionCode"),
                exportedAtEpochMs = original.getLong("exportedAtEpochMs"),
                storeLineageId = original.getString("storeLineageId")
            ).toString().toByteArray(Charsets.UTF_8).inputStream()
        }
        showApp()

        launchBackupExport()
        assertTrue(resultRegistry.dispatch(outputUri))
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("备份失败：", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(ui.onAllNodesWithText("原生备份已导出并回读校验").fetchSemanticsNodes().isNotEmpty())
        assertEquals(before, captureStoreState())
    }

    private fun seedStore() {
        val zone = ZoneId.systemDefault()
        val start = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        val formal = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Backed up formal activity",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 60 * 60 * 1000L, zone),
            notes = "Backup fixture note",
            zoneId = zone.id
        ))
        check(store.commit(formal.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
        store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "Backed up pending alarm",
            startText = DraftForm.formatInstant(start + 3 * 60 * 60 * 1000L, zone),
            zoneId = zone.id
        ))
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

    private fun launchBackupExport() {
        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("data.backup.export"))
        }
        ui.waitForIdle()
    }

    private fun captureStoreState() = StoreState(
        records = store.all(),
        histories = store.all().associate { it.eventSeq to store.history(it.eventSeq) },
        draftCount = store.draftCount(),
        identityCount = store.identityCount(),
        nextSequence = store.nextSequencePreview(),
        commitCount = store.commitCount()
    )

    private data class StoreState(
        val records: List<dev.powerfulcalendar.core.TemporalRecord>,
        val histories: Map<Long, List<*>>,
        val draftCount: Long,
        val identityCount: Long,
        val nextSequence: Long,
        val commitCount: Long
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

        fun dispatch(uri: Uri): Boolean = dispatchResult(
            requireNotNull(requestCode) { "No CreateDocument launch was recorded" }, uri
        )

        fun cancel(): Boolean = dispatchResult(
            requireNotNull(requestCode) { "No CreateDocument launch was recorded" }, null
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
