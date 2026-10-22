package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkinPackageManagerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = SkinPackageManager(context)

    private fun manifest(id: String = "unit-skin"): JSONObject = JSONObject()
        .put("format", "powercal-skin").put("schemaVersion", 1)
        .put("id", id).put("name", "Test Skin")
        .put("light", JSONObject()).put("dark", JSONObject())
        .put("layout", JSONObject())
        .put("imagesLight", JSONObject()).put("imagesDark", JSONObject())
        .put("sha256", JSONObject())

    private fun archive(vararg values: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            values.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun pack(json: JSONObject, vararg extras: Pair<String, ByteArray>): ByteArray =
        archive("manifest.json" to json.toString().toByteArray(Charsets.UTF_8), *extras)

    @Test fun minimalArchiveAcceptsThemeWithNoImages() {
        val json = manifest().put("light", JSONObject()
            .put("primary", "#123456").put("onPrimary", "#FFFFFF")
            .put("alarm", "#ABCDEF"))
            .put("layout", JSONObject().put("cardRadiusDp", 32)
                .put("pointLineDp", 3).put("fontScale", 1.12))
        val verified = manager.decode(pack(json), false)
        assertEquals("unit-skin", verified.pack.key)
        assertEquals(32f, verified.pack.visual!!.cardRadiusDp)
        assertEquals(1.12f, verified.pack.visual!!.fontScale)
        assertEquals(3f, verified.pack.visual!!.pointLineDp)
    }

    @Test fun unsupportedSchemaAndScriptEntriesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(pack(manifest().put("schemaVersion", 9)), false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(pack(manifest(), "scripts/hack.js" to "alert()".toByteArray()), false)
        }
    }

    @Test fun zipTraversalAndUnreferencedImagesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(pack(manifest(), "../escape.txt" to "x".toByteArray()), false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(pack(manifest(), "images/unused.png" to byteArrayOf(1,2,3)), false)
        }
    }

    @Test fun colorPairsAndGeometryAreValidated() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(pack(manifest().put("light",
                JSONObject().put("primary", "#112233"))), false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(pack(manifest().put("layout",
                JSONObject().put("cardRadiusDp", 999))), false)
        }
    }

    @Test fun missingImageHashNeverAllowsImport() {
        val json = manifest()
        json.put("imagesLight", JSONObject().put("BRAND_MARK", "images/logo.png"))
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(pack(json, "images/logo.png" to byteArrayOf(1, 2, 3)), true)
        }
    }

    @Test fun installChooseRestartAndDeleteDoNotModifyCalendarDatabase() {
        val id = "test-" + UUID.randomUUID().toString().take(24)
        val archive = pack(manifest(id))
        val source = File.createTempFile("powercal-skin-test-", ".pcskin", context.cacheDir)
        val db = context.getDatabasePath("powercal-v2.db")
        val dbSizeBefore = if (db.isFile) db.length() else -1L
        try {
            source.writeBytes(archive)
            // Installing any skin must leave the current selection untouched.
            manager.select("powercal-default")
            val entry = manager.install(Uri.fromFile(source))
            assertEquals(id, entry.id)
            assertEquals("powercal-default", manager.currentId())
            assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
            manager.select(id)
            assertEquals(id, manager.currentId())
            assertEquals(id, PowerCalSkinRegistry.active.key)
            manager.restoreSelection()
            assertEquals(id, PowerCalSkinRegistry.active.key)
            manager.delete(id)
            assertEquals("powercal-default", manager.currentId())
            assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
            assertEquals(dbSizeBefore, if (db.isFile) db.length() else -1L)
        } finally {
            source.delete()
            runCatching { manager.delete(id) }
            manager.select("powercal-default")
        }
    }
}
