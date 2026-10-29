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
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.interop.IcsCodec
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
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SemanticIcsExportIntegrationTest {
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
        namespace = "ics-export-" + UUID.randomUUID()
        databaseName = "$namespace.db"
        context = IsolatedContext(ui.activity, storage, namespace)
        store = PowerStore(storage, databaseName)
        store.readableDatabase
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
    fun clearHostExportsOnlyActivitiesAndDisclosesLossWithoutMutatingStore() {
        seedAllEventKinds()
        val beforeRecords = store.all()
        val beforeHistories = beforeRecords.associate { it.eventSeq to store.history(it.eventSeq) }
        val beforeDraftCount = store.draftCount()
        val beforeIdentityCount = store.identityCount()
        val beforeSequence = store.nextSequencePreview()
        val beforeCommitCount = store.commitCount()
        val outputUri = Uri.parse("content://powercal-ics-export-$namespace/result.ics")
        val output = ByteArrayOutputStream()
        shadowOf(ui.activity.contentResolver).registerOutputStreamSupplier(outputUri) { output }

        ui.activity.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides resultRegistryOwner
            ) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitForIdle()

        val option = SemanticOptions.groups.flatMap { it.items }
            .single { it.id == "calendar.export.ics" }
        assertTrue(SemanticOptions.supports(option.id))
        assertTrue(option.detail.contains("活动启用/停用状态"))
        assertTrue(option.detail.contains("活动提醒"))
        assertTrue(option.detail.contains("闹钟"))
        assertTrue(option.detail.contains("截止"))
        assertTrue(option.detail.contains("备忘"))
        assertTrue(option.detail.contains("完成状态"))

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("month")) }
        ui.waitForIdle()
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption(option.id)) }
        ui.waitForIdle()

        val launched = requireNotNull(resultRegistry.lastIntent) {
            "Clear semantic export option did not launch CreateDocument"
        }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched.action)
        assertEquals("text/calendar", launched.type)
        assertEquals("PowerfulCalendar-activities.ics",
            launched.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(resultRegistry.dispatch(outputUri))

        val success = "已导出活动 ICS；未包含活动启用/停用状态、活动提醒、闹钟、截止、备忘与完成状态。请使用原生备份保留完整数据"
        ui.waitUntil(20_000) {
            output.size() > 0 &&
                ui.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty()
        }

        val ics = output.toString(Charsets.UTF_8.name())
        val parsed = IcsCodec.parse(ics)
        assertTrue(parsed.warnings.toString(), parsed.warnings.isEmpty())
        assertEquals(1, parsed.events.size)
        assertEquals("Export activity", parsed.events.single().title)
        assertFalse("ICS does not serialize Activity reminders", ics.contains("VALARM"))
        assertFalse(ics.contains("Do not export alarm"))
        assertFalse(ics.contains("Do not export deadline"))
        assertFalse(ics.contains("Do not export memo"))

        assertEquals(beforeRecords, store.all())
        assertEquals(beforeHistories, store.all().associate { it.eventSeq to store.history(it.eventSeq) })
        assertEquals(beforeDraftCount, store.draftCount())
        assertEquals(beforeIdentityCount, store.identityCount())
        assertEquals(beforeSequence, store.nextSequencePreview())
        assertEquals(beforeCommitCount, store.commitCount())
    }

    @Test
    fun cancelingClearIcsExportLeavesStoreAndOutputUntouched() {
        seedAllEventKinds()
        val before = store.all()
        val identities = store.identityCount()
        val sequence = store.nextSequencePreview()
        val commits = store.commitCount()
        val output = ByteArrayOutputStream()
        val outputUri = Uri.parse("content://powercal-ics-export-$namespace/cancelled.ics")
        shadowOf(ui.activity.contentResolver).registerOutputStreamSupplier(outputUri) { output }
        showApp()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("calendar.export.ics"))
        }
        ui.waitForIdle()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, resultRegistry.lastIntent?.action)
        assertTrue(resultRegistry.cancel())
        ui.waitForIdle()

        assertTrue(output.toByteArray().isEmpty())
        assertEquals(before, store.all())
        assertEquals(identities, store.identityCount())
        assertEquals(sequence, store.nextSequencePreview())
        assertEquals(commits, store.commitCount())
    }

    @Test
    fun clearIcsExportOutputFailureIsReportedAndStoreRemainsUnchanged() {
        seedAllEventKinds()
        val before = store.all()
        val identities = store.identityCount()
        val sequence = store.nextSequencePreview()
        val commits = store.commitCount()
        val outputUri = Uri.parse("content://powercal-ics-export-$namespace/failing.ics")
        shadowOf(ui.activity.contentResolver).registerOutputStreamSupplier(outputUri) {
            throw IOException("injected output failure")
        }
        showApp()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("calendar.export.ics"))
        }
        ui.waitForIdle()
        assertTrue(resultRegistry.dispatch(outputUri))
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("ICS 导出受阻：injected output failure")
                .fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(before, store.all())
        assertEquals(identities, store.identityCount())
        assertEquals(sequence, store.nextSequencePreview())
        assertEquals(commits, store.commitCount())
    }

    private fun seedAllEventKinds() {
        val zone = ZoneId.systemDefault()
        val startMillis = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        val start = DraftForm.formatInstant(startMillis, zone)
        val end = DraftForm.formatInstant(startMillis + 60 * 60 * 1000L, zone)
        val tomorrow = LocalDate.now(zone).plusDays(1).toString()
        listOf(
            DraftForm(kind = TemporalKind.ACTIVITY, title = "Export activity",
                startText = start, endText = end, reminderMinutes = 15,
                enabled = false, zoneId = zone.id),
            DraftForm(kind = TemporalKind.ALARM, title = "Do not export alarm",
                startText = start, zoneId = zone.id),
            DraftForm(kind = TemporalKind.DEADLINE, title = "Do not export deadline",
                endText = tomorrow, zoneId = zone.id),
            DraftForm(kind = TemporalKind.MEMO, title = "Do not export memo", zoneId = zone.id)
        ).forEach { form ->
            val draft = store.newDraft(form)
            assertTrue(store.commit(draft.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
        }
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
