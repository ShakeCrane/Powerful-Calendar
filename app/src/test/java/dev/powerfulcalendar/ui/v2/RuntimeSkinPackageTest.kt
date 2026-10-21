package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeSkinPackageTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = SkinPackageManager(context)

    private fun hash(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString("") { "%02x".format(it) }

    private fun node(type: String, vararg children: JSONObject): JSONObject =
        JSONObject().put("type", type).apply {
            if (children.isNotEmpty()) put("children", JSONArray().apply {
                children.forEach { put(it) }
            })
        }

    private fun document(): JSONObject = JSONObject()
        .put("format", "powercal-runtime-ui")
        .put("version", 1)
        .put("transition", "slide-left")
        .put("durationMs", 280)
        .put("screens", JSONObject()
            .put("timeline", node("column",
                node("text").put("text", "Aurora"),
                node("button").put("text", "Today").put("action", "today"),
                node("events")))
            .put("navigation", node("nav-items"))
            .put("chrome", node("box", node("native")))
            .put("overlay", node("native")))

    private fun zip(ui: JSONObject, validHash: Boolean = true, manifestVersion: Int = 2): ByteArray {
        val bytes = ui.toString().toByteArray(Charsets.UTF_8)
        val hashValue = hash(bytes)
        val manifest = JSONObject()
            .put("format", "powercal-skin")
            .put("schemaVersion", manifestVersion)
            .put("id", "runtime-unit-v2")
            .put("name", "Runtime unit")
            .put("runtimeUi", "ui.json")
            .put("light", JSONObject()).put("dark", JSONObject())
            .put("layout", JSONObject())
            .put("imagesLight", JSONObject()).put("imagesDark", JSONObject())
            .put("sha256", JSONObject().put("ui.json",
                if (validHash) hashValue else "0".repeat(64)))
        return ByteArrayOutputStream().also { data ->
            ZipOutputStream(data).use { zip ->
                fun add(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                add("manifest.json", manifest.toString().toByteArray(Charsets.UTF_8))
                add("ui.json", bytes)
            }
        }.toByteArray()
    }

    @Test fun v2ActualAndroidDecoderLoadsAnimatedTree() {
        val verified = manager.decode(zip(document()), false)
        assertEquals("runtime-unit-v2", verified.pack.key)
        assertEquals("slide-left", verified.pack.runtime!!.transition)
        assertEquals(280, verified.pack.runtime!!.durationMs)
        assertEquals("column", verified.pack.runtime!!.screens["timeline"]!!.type)
    }

    @Test fun rejectedTamperAndBackwardsCompatibility() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(zip(document(), validHash = false), false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(zip(document(), manifestVersion = 1), false)
        }
    }

    @Test fun rejectedUnauthorizedActionAndMalformedRecovery() {
        val ui = document()
        val screens = ui.getJSONObject("screens")
        screens.put("timeline", node("button").put("text", "Danger")
            .put("action", "commit"))
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(zip(ui), false)
        }
        screens.put("timeline", node("text").put("text", "Okay"))
        screens.put("chrome", node("text").put("text", "Hide the app"))
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(zip(ui), false)
        }
        screens.put("chrome", node("native"))
        screens.put("overlay", node("card", node("overlay-title")))
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(zip(ui), false)
        }
    }

    @Test fun rejectedExcessivelyNestedUi() {
        val ui = document()
        var deep = node("text").put("text", "end")
        repeat(12) { deep = node("box", deep) }
        ui.getJSONObject("screens").put("timeline", deep)
        assertThrows(IllegalArgumentException::class.java) {
            manager.decode(zip(ui), false)
        }
    }

    @Test fun v2InstallSelectionRestartAndRecovery() {
        val id = "runtime-" + UUID.randomUUID().toString().take(15)
        val json = document()
        val originalZip = zip(json)
        // Rename only the manifest ID; document content and hash stay unchanged.
        val contents = java.util.zip.ZipInputStream(originalZip.inputStream()).use { zip ->
            val files = linkedMapOf<String, ByteArray>()
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes()
            }
            files
        }
        val manifest = JSONObject(String(contents.getValue("manifest.json"), Charsets.UTF_8))
            .put("id", id)
        val archive = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                for ((name, bytes) in contents) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(if (name == "manifest.json")
                        manifest.toString().toByteArray(Charsets.UTF_8) else bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val temp = File.createTempFile("skin-v2-", ".pcskin", context.cacheDir)
        try {
            temp.writeBytes(archive)
            val installed = manager.install(Uri.fromFile(temp))
            assertEquals(id, installed.id)
            manager.select(id)
            assertNotNull(PowerCalSkinRegistry.active.runtime)
            manager.restoreSelection()
            assertNotNull(PowerCalSkinRegistry.active.runtime)
            manager.select("powercal-default")
            assertNull(PowerCalSkinRegistry.active.runtime)
        } finally {
            temp.delete()
            runCatching { manager.delete(id) }
            manager.select("powercal-default")
        }
    }
}
