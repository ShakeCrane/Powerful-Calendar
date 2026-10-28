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
import java.util.zip.ZipInputStream
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

    @Test fun bundledClearIsVerifiedProtectedAndOptInOnly() {
        manager.select("powercal-default")
        assertEquals("powercal-default", manager.currentId())
        val builtin = manager.installed().single { it.id == "powercal-clear-default" }
        assertTrue(builtin.builtIn)
        assertThrows(IllegalArgumentException::class.java) {
            manager.delete("powercal-clear-default")
        }
        try {
            manager.select("powercal-clear-default")
            assertEquals("powercal-clear-default", manager.currentId())
            assertNotNull(PowerCalSkinRegistry.active.web)
            assertTrue(PowerCalSkinRegistry.active.web!!.fullCanvas)
            manager.restoreSelection()
            assertEquals("powercal-clear-default", PowerCalSkinRegistry.active.key)
        } finally {
            manager.select("powercal-default")
        }
    }

    /**
     * Factory-vs-user A/B regression: only the manifest identity is different.
     * Both packages must pass the same v3 validator and produce equal executable
     * content, host capabilities, and rendering contract after skin selection.
     */
    @Test fun officialClearAndReidentifiedThirdPartyCloneHaveIdenticalRuntime() {
        val factoryBytes = context.assets.open("powercal-clear-default.pcskin")
            .use { it.readBytes() }
        val id = "parity-" + UUID.randomUUID().toString().take(24)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(factoryBytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
        assertEquals("powercal-clear-default", manifest.getString("id"))
        manifest.put("id", id).put("name", "Imported Clear Clone")
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        val cloneBytes = archive(*entries.map { it.key to it.value }.toTypedArray())
        val cloneFile = File.createTempFile("powercal-clear-parity-", ".pcskin", context.cacheDir)
        try {
            cloneFile.writeBytes(cloneBytes)
            manager.select("powercal-clear-default")
            val official = PowerCalSkinRegistry.active
            val officialWeb = requireNotNull(official.web)
            assertTrue(officialWeb.fullCanvas)
            val imported = manager.install(Uri.fromFile(cloneFile))
            assertEquals(id, imported.id)
            assertFalse(imported.builtIn)
            // Installation is a distribution action, never a skin activation.
            assertEquals("powercal-clear-default", manager.currentId())
            manager.select(id)
            val thirdParty = PowerCalSkinRegistry.active
            val thirdPartyWeb = requireNotNull(thirdParty.web)

            assertEquals(officialWeb.entrypoint, thirdPartyWeb.entrypoint)
            assertEquals(officialWeb.capabilities, thirdPartyWeb.capabilities)
            assertEquals(officialWeb.fullCanvas, thirdPartyWeb.fullCanvas)
            assertEquals(WebSkinCapabilityPolicy.mayRead(officialWeb),
                WebSkinCapabilityPolicy.mayRead(thirdPartyWeb))
            assertEquals(WebSkinCapabilityPolicy.mayAct(officialWeb),
                WebSkinCapabilityPolicy.mayAct(thirdPartyWeb))
            assertEquals(officialWeb.files.keys, thirdPartyWeb.files.keys)
            officialWeb.files.forEach { (path, content) ->
                assertArrayEquals("Divergent factory/third-party runtime asset: $path",
                    content, thirdPartyWeb.files.getValue(path))
            }
            assertEquals(official.runtime, thirdParty.runtime)
            assertEquals(official.visual, thirdParty.visual)
            assertEquals(official.lightBitmaps.keys, thirdParty.lightBitmaps.keys)
            assertEquals(official.darkBitmaps.keys, thirdParty.darkBitmaps.keys)
            assertEquals(id, PowerCalSkinRegistry.active.key)

            manager.restoreSelection()
            assertEquals(id, PowerCalSkinRegistry.active.key)
            val resumed = requireNotNull(PowerCalSkinRegistry.active.web)
            assertEquals(officialWeb.capabilities, resumed.capabilities)
            assertEquals(officialWeb.files.keys, resumed.files.keys)
            manager.select("powercal-clear-default")
            assertEquals("powercal-clear-default", PowerCalSkinRegistry.active.key)
            assertEquals(id, imported.id)
        } finally {
            manager.select("powercal-default")
            runCatching { manager.delete(id) }
            cloneFile.delete()
        }
    }

    @Test fun v3UnknownManifestMetadataIsAcceptedAndDoesNotGrantHostAccess() {
        val factoryBytes = context.assets.open("powercal-clear-default.pcskin")
            .use { it.readBytes() }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(factoryBytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }

        val manifest = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8))
            .put("id", "opaque-" + UUID.randomUUID().toString().take(20))
            .put("name", "Opaque Metadata Test")
            .put("capabilities", org.json.JSONArray())
            .put("x-vendor-metadata", JSONObject()
                .put("publisher", "fixture")
                .put("custom", JSONObject().put("labels",
                    org.json.JSONArray().put("preserve").put("only")))
                .put("claims", org.json.JSONArray().put("host.action")))
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)

        val id = manifest.getString("id")
        val packageBytes = archive(*entries.map { it.key to it.value }.toTypedArray())
        val unsupportedManifest = JSONObject(manifest.toString())
            .put("capabilities", org.json.JSONArray().put("host.delete"))
        val unsupportedEntries = LinkedHashMap(entries).apply {
            put("manifest.json", unsupportedManifest.toString().toByteArray(Charsets.UTF_8))
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(archive(*unsupportedEntries.map { it.key to it.value }.toTypedArray()), false)
        }

        val source = File.createTempFile("powercal-opaque-metadata-", ".pcskin", context.cacheDir)
        val database = context.getDatabasePath("powercal-v2.db")
        val databaseLengthBefore = if (database.isFile) database.length() else -1L
        try {
            source.writeBytes(packageBytes)
            assertEquals(id, manager.install(Uri.fromFile(source)).id)
            val installedBytes = File(context.filesDir, "skin-packs/$id.pcskin").readBytes()
            assertArrayEquals(packageBytes, installedBytes)

            manager.select(id)
            val web = requireNotNull(PowerCalSkinRegistry.active.web)
            assertTrue(web.capabilities.isEmpty())
            assertFalse(WebSkinCapabilityPolicy.mayRead(web))
            assertFalse(WebSkinCapabilityPolicy.mayAct(web))
            assertEquals(databaseLengthBefore, if (database.isFile) database.length() else -1L)
        } finally {
            manager.select("powercal-default")
            runCatching { manager.delete(id) }
            source.delete()
        }
    }

    @Test fun webBridgeCapabilitiesDependOnDeclaredPermissionsNotSkinIdentity() {
        val html = mapOf("web/index.html" to "<main/>".toByteArray())
        val noPermissions = WebSkinBundle("web/index.html", html, emptySet(), true)
        val readOnly = WebSkinBundle("web/index.html", html, setOf("host.read"), true)
        val actionOnly = WebSkinBundle("web/index.html", html, setOf("host.action"), true)
        val editOnly = WebSkinBundle("web/index.html", html, setOf("host.edit"), true)
        val readAndEdit = WebSkinBundle("web/index.html", html, setOf("host.read", "host.edit"), true)
        assertFalse(WebSkinCapabilityPolicy.mayRead(noPermissions))
        assertFalse(WebSkinCapabilityPolicy.mayAct(noPermissions))
        assertTrue(WebSkinCapabilityPolicy.mayRead(readOnly))
        assertFalse(WebSkinCapabilityPolicy.mayAct(readOnly))
        assertFalse(WebSkinCapabilityPolicy.mayRead(actionOnly))
        assertTrue(WebSkinCapabilityPolicy.mayAct(actionOnly))
        assertFalse(WebSkinCapabilityPolicy.mayRead(editOnly))
        assertFalse(WebSkinCapabilityPolicy.mayAct(editOnly))
        assertTrue(WebSkinCapabilityPolicy.mayRead(readAndEdit))
        assertFalse(WebSkinCapabilityPolicy.mayAct(readAndEdit))
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
