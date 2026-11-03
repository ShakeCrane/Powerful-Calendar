package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
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

    @Test fun nativeToolsHidesInstalledPackageExportWithoutDeletingOrActivatingIt() {
        val packageId = "reedit-" + UUID.randomUUID().toString().take(18)
        val packageName = "Installed Third Party Fixture"
        val packageBytes = thirdPartyClone(packageId, packageName)
        val packageInput = File(context.cacheDir, "source.pcskin").apply {
            parentFile?.mkdirs()
            writeBytes(packageBytes)
        }
        val manager = SkinPackageManager(context)
        val installed = manager.install(Uri.fromFile(packageInput))
        val beforeSelection = manager.currentId()
        val beforeRecords = store.all()
        val beforePending = store.pendingCount()
        val beforeSequence = store.nextSequencePreview()
        val databaseFile = File(store.readableDatabase.path)
        val beforeDatabaseLength = databaseFile.length()

        try {
            ui.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
            ui.waitForIdle()
            ui.onNodeWithText("设置").performClick()
            ui.waitForIdle()
            ui.onNodeWithContentDescription("导出 $packageName 皮肤包").assertDoesNotExist()
            ui.onNodeWithText("导入皮肤包").assertDoesNotExist()
            ui.onNodeWithText("导出 Clear 候选皮肤").assertDoesNotExist()
            assertTrue("ICS import remains available",
                ui.onAllNodesWithText("从 ICS 文件导入").fetchSemanticsNodes().isNotEmpty())
            assertTrue("backup remains available",
                ui.onAllNodesWithText("导出原生备份（Envelope v1）").fetchSemanticsNodes().isNotEmpty())
            assertTrue("reminder settings remain available",
                ui.onAllNodesWithText("精确闹钟设置").fetchSemanticsNodes().isNotEmpty())

            assertEquals("catalogue retirement must not activate the installed package", beforeSelection,
                manager.currentId())
            assertEquals(packageId, installed.id)
            assertArrayEquals("retirement must not delete or rewrite the installed fixture", packageBytes,
                File(context.filesDir, "skin-packs/$packageId.pcskin").readBytes())
            assertEquals(beforeRecords, store.all())
            assertEquals(beforePending, store.pendingCount())
            assertEquals(beforeSequence, store.nextSequencePreview())
            assertEquals("opening Native tools must not change calendar persistence", beforeDatabaseLength,
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
