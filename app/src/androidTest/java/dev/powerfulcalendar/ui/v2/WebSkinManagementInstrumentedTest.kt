package dev.powerfulcalendar.ui.v2

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class WebSkinManagementInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun isolatedDatabasePath(name: String) =
            storage.getDatabasePath(databaseAlias(name)).also {
                check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
            }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = isolatedDatabasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(databaseAlias(name))
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val done = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { reply ->
                value.set(reply)
                done.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out", done.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJs(web: WebView, condition: String) {
        try {
            compose.waitUntil(30_000) { evaluate(web, condition) == "true" }
        } catch (failure: Throwable) {
            val snapshot = runCatching {
                evaluate(web, "JSON.stringify({url:location.href,title:document.title,header:document.querySelector('#extraOptions h1')?.textContent,optionsHidden:document.querySelector('#extraOptions')?.hidden,body:document.body.innerText.slice(0,1800)})")
            }.getOrDefault("<WebView snapshot unavailable>")
            throw AssertionError("WebView condition was not met: $condition; snapshot=$snapshot", failure)
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String =
        node.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: node.contentDescription?.toString()?.trim().orEmpty()

    private fun findClickableLabel(
        node: AccessibilityNodeInfo,
        labels: Set<String>
    ): AccessibilityNodeInfo? {
        val label = nodeLabel(node)
        if (labels.any { label.equals(it, ignoreCase = true) }) {
            var candidate: AccessibilityNodeInfo? = node
            while (candidate != null) {
                if (candidate.isClickable && candidate.isEnabled) return candidate
                candidate = candidate.parent
            }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findClickableLabel(child, labels)?.let { return it }
        }
        return null
    }

    private fun describeAccessibilityTree(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 18) return ""
        val own = nodeLabel(node).takeIf { it.isNotEmpty() }
            ?.let { "  ".repeat(depth) + it + if (node.isClickable) " [clickable]" else "" }
            .orEmpty()
        return (listOf(own) + (0 until node.childCount).mapNotNull { index ->
            node.getChild(index)?.let { describeAccessibilityTree(it, depth + 1) }
        }).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun clickPickerLabel(labels: Set<String>, timeoutMs: Long = 20_000) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastTree = "<no active accessibility root>"
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                lastTree = describeAccessibilityTree(root)
                val target = findClickableLabel(root, labels)
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            }
            SystemClock.sleep(200)
        }
        throw AssertionError("Picker control not found: $labels\n$lastTree")
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findEditableNode(child)?.let { return it }
        }
        return null
    }

    private fun setPickerFileName(value: String, timeoutMs: Long = 15_000) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastTree = "<no active accessibility root>"
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                lastTree = describeAccessibilityTree(root)
                val field = findEditableNode(root)
                if (field != null && field.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                value
                            )
                        }
                    )
                ) return
            }
            SystemClock.sleep(200)
        }
        throw AssertionError("Picker filename field not found\n$lastTree")
    }

    private fun awaitForegroundPackage(expected: String, timeoutMs: Long = 20_000) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var actual = "<no active accessibility root>"
        while (SystemClock.uptimeMillis() < deadline) {
            actual = automation.rootInActiveWindow?.packageName?.toString().orEmpty()
            if (actual == expected) return
            SystemClock.sleep(200)
        }
        throw AssertionError("Expected foreground package $expected, got $actual")
    }

    private fun openDownloadsRoot() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val root = automation.rootInActiveWindow
        val current = root?.let(::describeAccessibilityTree).orEmpty()
        if ("Downloads" in current || "下载" in current) {
            val direct = findClickableLabel(root!!, setOf("Downloads", "下载"))
            if (direct != null) {
                direct.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
        clickPickerLabel(setOf("Show roots", "Show navigation drawer", "Open navigation drawer", "导航菜单"))
        clickPickerLabel(setOf("Downloads", "下载"))
    }

    private fun v3Package(id: String): ByteArray {
        val files = linkedMapOf(
            "web/index.html" to "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><main>fixture</main><script src=\"app.js\"></script></body></html>".toByteArray(),
            "web/style.css" to "body { margin: 0; }".toByteArray(),
            "web/app.js" to "'use strict';".toByteArray(),
            "project.json" to "{\"projectVersion\":3,\"mode\":\"simple\",\"slots\":[]}".toByteArray()
        )
        val hashes = JSONObject()
        files.forEach { (path, bytes) ->
            hashes.put(path, MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) })
        }
        val manifest = JSONObject()
            .put("format", "powercal-skin")
            .put("schemaVersion", 3)
            .put("id", id)
            .put("name", "Provider fixture")
            .put("entrypoint", "web/index.html")
            .put("project", "project.json")
            .put("capabilities", JSONArray())
            .put("fullCanvas", true)
            .put("sha256", hashes)
        return java.io.ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
                files.forEach { (path, content) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
    }

    private fun seedDownloadsPackage(context: Context, id: String): android.net.Uri {
        // DocumentsUI applies the ZIP MIME extension when it materializes an application/zip row.
        val displayName = "$id.pcskin.zip"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = requireNotNull(context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        )) { "Unable to create isolated Downloads provider fixture" }
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(v3Package(id)) }
        } catch (failure: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw failure
        }
        return uri
    }

    private fun deleteDownloadsName(context: Context, displayName: String) {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            arrayOf(displayName, Environment.DIRECTORY_DOWNLOADS + "/"),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) ids += cursor.getLong(idColumn)
        }
        ids.forEach { rowId ->
            context.contentResolver.delete(
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, rowId),
                null,
                null
            )
        }
    }

    @Test
    fun clearSettingsShowHostCatalogueAndRequireConfirmationToSelectSkin() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-skin-management-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "skin-management.db"
        val store = PowerStore(isolated, databaseName)

        try {
            PowerCalSkinRegistry.reset()
            val skins = SkinPackageManager(isolated)
            skins.select("powercal-clear-default")
            isolated.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()

            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=\\\"settings\\\"]')")
            awaitJs(view, "!Array.from(document.querySelectorAll('#optionGroups [data-option-id]')).some(b=>['skin.import','skin.exportClear'].includes(b.dataset.optionId))")
            // The static DOM can exist before the Host has attached its MessagePort. This
            // deliberately unsupported edit command is rejected by both closed decoders and
            // is used only to wait for the transport; it cannot write calendar or skin data.
            awaitJs(view, "window.PowerCalSkin.sendCommand('bridge.readiness.probe','bridge_probe')===true")
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=\\\"settings\\\"]').click(); true")

            awaitJs(view, "!document.querySelector('#extraOptions').hidden && document.querySelector('#extraOptions h1').textContent==='设置与数据工具'")
            awaitJs(view, "document.querySelectorAll('#optionGroups .skinPackageRow').length===2")
            awaitJs(view, "!!document.querySelector('#optionGroups button[data-action=\\\"selectSkin\\\"][data-skin-id=\\\"powercal-default\\\"]')")
            awaitJs(view, "!!document.querySelector('#optionGroups button[data-action=\\\"invokeOption\\\"][data-option-id=\\\"skin.import\\\"]')")
            awaitJs(view, "!!document.querySelector('#optionGroups button[data-action=\\\"invokeOption\\\"][data-option-id=\\\"skin.exportClear\\\"]')")
            awaitJs(view, "Array.from(document.querySelectorAll('#optionGroups button[data-action=\\\"selectSkin\\\"]')).some(b=>b.getAttribute('aria-label')?.endsWith('，启用'))")

            evaluate(view, "document.querySelector('#optionGroups button[data-action=\\\"selectSkin\\\"][data-skin-id=\\\"powercal-default\\\"]').click(); true")
            compose.onNodeWithText("切换皮肤前请确认").assertExists()
            assertEquals("powercal-clear-default", skins.currentId())
            assertTrue(store.all().isEmpty())
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())

            compose.onNodeWithText("取消").performClick()
            compose.waitForIdle()
            assertEquals("powercal-clear-default", skins.currentId())
            awaitJs(view, "document.querySelector('#extraOptions h1').textContent==='设置与数据工具'")
            assertTrue(store.all().isEmpty())
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            isolated.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun skinPackageImportAndExportUseTheSystemDocumentsProvider() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-skin-provider-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "skin-provider.db"
        val store = PowerStore(isolated, databaseName)
        val skinId = "provider-${UUID.randomUUID().toString().take(8)}"
        val exportedName = "$skinId-clear-export.zip"
        val fixtureUri = seedDownloadsPackage(storage, skinId)
        var skinWasInstalled = false

        try {
            PowerCalSkinRegistry.reset()
            val skins = SkinPackageManager(isolated)
            skins.select("powercal-clear-default")
            isolated.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()

            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=\\\"settings\\\"]')")
            awaitJs(view, "window.PowerCalSkin.sendCommand('bridge.readiness.probe','bridge_probe')===true")
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=\\\"settings\\\"]').click(); true")
            awaitJs(view, "!document.querySelector('#extraOptions').hidden && document.querySelector('#extraOptions h1').textContent==='设置与数据工具'")
            awaitJs(view, "!!document.querySelector('#optionGroups button[data-action=\\\"invokeOption\\\"][data-option-id=\\\"skin.import\\\"]')")

            evaluate(view, "document.querySelector('#optionGroups button[data-action=\\\"invokeOption\\\"][data-option-id=\\\"skin.import\\\"]').click(); true")
            openDownloadsRoot()
            clickPickerLabel(setOf("$skinId.pcskin.zip"))
            val appPackage = compose.activity.packageName
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            if (automation.rootInActiveWindow?.packageName?.toString() != appPackage) {
                runCatching { clickPickerLabel(setOf("Open", "打开"), timeoutMs = 3_000) }
            }
            awaitForegroundPackage(appPackage)
            compose.waitUntil(20_000) { skins.installed().any { it.id == skinId } }
            skinWasInstalled = true

            assertEquals("powercal-clear-default", skins.currentId())
            assertEquals(0, store.all().size)
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
            assertEquals(0L, store.commitCount())
            val installedIdsBeforeExport = skins.installed().map { it.id }.sorted()

            evaluate(view, "document.querySelector('#optionGroups button[data-action=\\\"invokeOption\\\"][data-option-id=\\\"skin.exportClear\\\"]').click(); true")
            openDownloadsRoot()
            setPickerFileName(exportedName)
            clickPickerLabel(setOf("Save", "保存"))
            awaitForegroundPackage(appPackage)
            compose.waitForIdle()
            assertEquals("powercal-clear-default", skins.currentId())
            assertTrue(skins.installed().any { it.id == skinId })
            assertEquals(installedIdsBeforeExport, skins.installed().map { it.id }.sorted())
            assertEquals(0, store.all().size)
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
            assertEquals(0L, store.commitCount())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            if (skinWasInstalled) runCatching { SkinPackageManager(isolated).delete(skinId) }
            store.close()
            isolated.deleteDatabase(databaseName)
            storage.contentResolver.delete(fixtureUri, null, null)
            runCatching { deleteDownloadsName(storage, exportedName) }
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }
}
