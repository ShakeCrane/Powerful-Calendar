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
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SkinPackageExportFlowTest {
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
        namespace = "skin-export-" + UUID.randomUUID()
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
        listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-ai")
            .forEach { storage.deleteSharedPreferences("$namespace-$it") }
        File(storage.cacheDir, namespace).deleteRecursively()
        PowerCalSkinRegistry.reset()
    }

    @Test fun settingsExportsAnInstalledThirdPartyPackageThroughCreateDocumentUnchanged() {
        val packageId = "reedit-" + UUID.randomUUID().toString().take(18)
        val packageName = "Installed Third Party Fixture"
        val packageBytes = thirdPartyClone(packageId, packageName)
        val packageInput = File(context.cacheDir, "source.pcskin").apply {
            parentFile?.mkdirs()
            writeBytes(packageBytes)
        }
        val manager = SkinPackageManager(context)
        val installed = manager.install(Uri.fromFile(packageInput))
        val outputUri = Uri.parse("content://skin-export-$namespace/result/$packageId.pcskin")
        val output = ByteArrayOutputStream()
        val beforeSelection = manager.currentId()
        val databaseFile = File(store.readableDatabase.path)
        val beforeDatabaseLength = databaseFile.length()

        try {
            shadowOf(ui.activity.contentResolver)
                .registerOutputStreamSupplier(outputUri) { output }
            ui.activity.setContent {
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalActivityResultRegistryOwner provides resultRegistryOwner
                ) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.onNodeWithText("设置").performClick()
            ui.waitForIdle()
            ui.onNodeWithContentDescription("导出 $packageName 皮肤包")
                .performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
            ui.waitForIdle()

            ui.onNodeWithText("请选择保存位置以导出 $packageName").assertExists()
            val launched = requireNotNull(resultRegistry.lastIntent) {
                "installed skin action did not launch CreateDocument"
            }
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched.action)
            assertEquals("application/zip", launched.type)
            assertEquals("$packageId.pcskin",
                launched.getStringExtra(Intent.EXTRA_TITLE))
            assertTrue(resultRegistry.dispatch(outputUri))

            val success = "已导出皮肤包 $packageId；可在 Skin Studio 导入后继续编辑"
            ui.waitUntil(10_000) {
                ui.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty()
            }
            assertArrayEquals("SAF export must preserve the complete installed archive",
                packageBytes, output.toByteArray())
            assertEquals("export must not activate the installed package", beforeSelection,
                manager.currentId())
            assertEquals(packageId, installed.id)
            assertArrayEquals("export must not rewrite the installed source", packageBytes,
                File(context.filesDir, "skin-packs/$packageId.pcskin").readBytes())
            assertEquals("export must not change calendar persistence", beforeDatabaseLength,
                if (databaseFile.isFile) databaseFile.length() else -1L)
        } finally {
            ui.activity.runOnUiThread { PowerCalSkinRegistry.reset() }
            runCatching { manager.delete(packageId) }
            packageInput.delete()
        }
    }

    private fun thirdPartyClone(id: String, name: String): ByteArray {
        val bytes = context.assets.open("powercal-clear-default.pcskin").use { it.readBytes() }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
            .put("id", id).put("name", name)
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        val result = ByteArrayOutputStream()
        ZipOutputStream(result).use { zip ->
            entries.forEach { (path, data) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return result.toByteArray()
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
