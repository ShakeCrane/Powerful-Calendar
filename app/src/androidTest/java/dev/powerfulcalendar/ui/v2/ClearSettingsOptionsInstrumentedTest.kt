package dev.powerfulcalendar.ui.v2

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ClearSettingsOptionsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value ->
                result.set(value)
                complete.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out", complete.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    @Test
    fun packagedClearSettingsExposeHostOptionsAndKeepMigrationBehindConfirmation() {
        val context = compose.activity.applicationContext
        val databaseName = "clear-settings-options-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }

            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                val state = AtomicReference<String>()
                compose.runOnUiThread { state.set(WebSkinBridgeRuntime.state().optString("page")) }
                state.get() == "SETTINGS"
            }
            val hostOptionCount = AtomicReference<Int>()
            compose.runOnUiThread {
                hostOptionCount.set(WebSkinBridgeRuntime.state().optJSONArray("optionGroups")
                    ?.let { groups -> (0 until groups.length()).sumOf { index ->
                        groups.optJSONObject(index)?.optJSONArray("items")?.length() ?: 0
                    } } ?: 0)
            }
            assertEquals("Host must project all registered calendar/data options", 6, hostOptionCount.get())
            try {
                compose.waitUntil(15_000) {
                    val visible = evaluate(view,
                        "!document.querySelector('#settingsPage').hidden && " +
                            "document.querySelectorAll('#settingsOptionGroups [data-option-id]').length===6")
                    if (visible != "true") SystemClock.sleep(150)
                    visible == "true"
                }
            } catch (error: Throwable) {
                val webState = runCatching {
                    evaluate(view, "JSON.stringify({settings:!!document.querySelector('#settingsPage')," +
                        "hidden:document.querySelector('#settingsPage')?.hidden," +
                        "options:document.querySelectorAll('#settingsOptionGroups [data-option-id]').length," +
                        "pageText:document.querySelector('#settingsPage')?.innerText?.slice(-500)})")
                }.getOrDefault("unavailable")
                throw AssertionError("Host option count=" + hostOptionCount.get() + ", WebView=" + webState, error)
            }
            assertEquals("true", evaluate(view, "[" +
                "'calendar.import.file','calendar.import.system','data.legacy.migrate'," +
                "'calendar.export.ics','data.backup.export','data.backup.restore'" +
                "].every(id=>Array.from(document.querySelectorAll(" +
                "'#settingsOptionGroups [data-option-id]')).some(el=>el.dataset.optionId===id))"))
            assertTrue(evaluate(view,
                "document.querySelector('[data-action=nativeSettings]')?.isConnected===true") == "true")

            val projectedSkins = requireNotNull(WebSkinBridgeRuntime.state().optJSONArray("skinChoices"))
            val projectedSkinIds = (0 until projectedSkins.length()).mapNotNull { index ->
                projectedSkins.optJSONObject(index)?.optString("id")
            }
            assertTrue("Settings must expose the Native recovery skin", "powercal-default" in projectedSkinIds)
            assertTrue("Settings must expose the active Clear skin", "powercal-clear-default" in projectedSkinIds)
            assertEquals("The selected skin is host-projected", "powercal-clear-default",
                WebSkinBridgeRuntime.state().optString("selectedSkinId"))
            assertTrue("The skin projection must contain catalogue metadata only",
                projectedSkins.toString().contains("powercal-clear-default") &&
                    !projectedSkins.toString().contains("path") &&
                    !projectedSkins.toString().contains("endpoint") &&
                    !projectedSkins.toString().contains("apiKey"))
            assertEquals("true", evaluate(view,
                "document.querySelectorAll('#skinChoices [data-action=selectSkin]').length>=2"))
            assertEquals("true", evaluate(view,
                "document.querySelector('#skinChoices [data-skin-id=powercal-clear-default]')?.disabled===true"))
            evaluate(view, "document.querySelector('#skinChoices [data-skin-id=powercal-default]').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("切换皮肤前请确认").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("切换皮肤前请确认").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("Cancelling the Host confirmation must keep Clear active",
                "powercal-clear-default", skinManager.currentId())

            val initialFormal = store.all()
            val initialPending = store.pendingCount()
            val initialCommits = store.commitCount()
            val initialIdentities = store.identityCount()
            val initialSequence = store.nextSequencePreview()
            evaluate(view, "Array.from(document.querySelectorAll(" +
                "'#settingsOptionGroups [data-option-id]')).find(" +
                "el=>el.dataset.optionId==='data.legacy.migrate').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("迁移旧版事件？").fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals("Opening migration review must not write formal records", initialFormal, store.all())
            assertEquals(initialPending, store.pendingCount())
            assertEquals(initialCommits, store.commitCount())
            assertEquals(initialIdentities, store.identityCount())
            assertEquals(initialSequence, store.nextSequencePreview())

            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("迁移旧版事件？").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("SETTINGS", WebSkinBridgeRuntime.state().optString("page"))
            assertEquals(initialFormal, store.all())
            assertEquals(initialPending, store.pendingCount())
            assertEquals(initialCommits, store.commitCount())
            assertEquals(initialIdentities, store.identityCount())
            assertEquals(initialSequence, store.nextSequencePreview())

            evaluate(view, "document.querySelector('#skinChoices [data-skin-id=powercal-default]').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("切换皮肤前请确认").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认切换").performClick()
            compose.waitUntil(15_000) { skinManager.currentId() == "powercal-default" }
            assertEquals("Confirmed activation uses the existing Host package manager",
                "powercal-default", skinManager.currentId())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearSettingsRoutesAiCredentialsAndContextControlsToProtectedHostUi() {
        val context = compose.activity.applicationContext
        val databaseName = "clear-ai-settings-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            compose.waitUntil(10_000) {
                evaluate(view, "!!document.querySelector('#openAiSettings')") == "true"
            }

            evaluate(view, "document.querySelector('#openAiSettings').click(); true")
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("单一 AI").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("配置端点、模型与密钥").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("允许按次共享事件上下文")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(compose.onAllNodesWithText("端点 · https://api.example.com/v1")
                .fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodesWithText("启用 AI").fetchSemanticsNodes().isNotEmpty())
            assertFalse("Credentials must not be projected to the Clear WebView",
                WebSkinBridgeRuntime.state().toString().contains("apiKey"))
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    @Test
    fun clearSkinCatalogUsesHostPickersAndConfirmedDeleteWithoutCalendarWrites() {
        assumeTrue("skin catalogue fixture must run on an isolated Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = compose.activity.applicationContext
        val databaseName = "clear-skin-catalog-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val existingId = "qa-clear-${UUID.randomUUID().toString().replace("-", "")}"
        val importId = "qa-clear-${UUID.randomUUID().toString().replace("-", "")}"
        val existingInput = File(context.cacheDir, "$existingId.pcskin")
        val importInput = File(context.cacheDir, "$importId.pcskin")
        val clearOutput = File(context.cacheDir, "${UUID.randomUUID()}-clear-export.pcskin")
        val installedOutput = File(context.cacheDir, "${UUID.randomUUID()}-installed-export.pcskin")
        val existingArchive = skinPackageFixture(existingId)
        val importArchive = skinPackageFixture(importId)
        var existingInstalled = false
        var importedInstalled = false
        try {
            assumeTrue("test fixture IDs must not already be installed",
                skinManager.installed().none { it.id == existingId || it.id == importId })
            assumeTrue("installed skin capacity must be available",
                skinManager.installed().count { !it.builtIn } < 12)
            assertFalse(clearOutput.exists())
            assertFalse(installedOutput.exists())
            existingInput.writeBytes(existingArchive)
            importInput.writeBytes(importArchive)
            assertEquals(existingId, skinManager.install(Uri.fromFile(existingInput)).id)
            existingInstalled = true
            skinManager.select("powercal-clear-default")
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && !!window.PowerCalSkin.receive") == "true"
            }
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=settings]').click(); true")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optString("page") == "SETTINGS"
            }
            compose.waitUntil(15_000) {
                evaluate(view, "!!document.querySelector('#skinChoices [data-skin-id=$existingId]')") == "true"
            }

            fun dispatchPickerResult(action: String, uri: Uri, clickExpression: String) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val filter = IntentFilter(action).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addDataType("*/*")
                }
                val monitor = instrumentation.addMonitor(
                    filter,
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)),
                    true
                )
                try {
                    evaluate(view, "$clickExpression; true")
                    compose.waitUntil(10_000) { monitor.hits > 0 }
                    instrumentation.waitForIdleSync()
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
            }

            dispatchPickerResult(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(importInput),
                "document.querySelector('#importSkinButton').click()")
            compose.waitUntil(20_000) { skinManager.installed().any { it.id == importId } }
            importedInstalled = true
            assertEquals("Import never activates the candidate skin",
                "powercal-clear-default", skinManager.currentId())
            compose.waitUntil(15_000) {
                evaluate(view, "!!document.querySelector('#skinChoices [data-skin-id=$importId]')") == "true"
            }

            dispatchPickerResult(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(clearOutput),
                "document.querySelector('#exportClearSkinButton').click()")
            compose.waitUntil(20_000) { clearOutput.isFile && clearOutput.length() > 0 }
            val bundledClear = context.assets.open("powercal-clear-default.pcskin").use { it.readBytes() }
            assertTrue("Clear export returns the canonical bundle", bundledClear.contentEquals(clearOutput.readBytes()))

            dispatchPickerResult(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(installedOutput),
                "document.querySelector('#skinChoices [data-action=exportInstalledSkin][data-skin-id=$existingId]').click()")
            compose.waitUntil(20_000) { installedOutput.isFile && installedOutput.length() > 0 }
            assertTrue("installed export preserves the original Studio package",
                existingArchive.contentEquals(installedOutput.readBytes()))

            val formalBefore = store.all()
            val commitsBefore = store.commitCount()
            val identitiesBefore = store.identityCount()
            val sequenceBefore = store.nextSequencePreview()
            evaluate(view,
                "document.querySelector('#skinChoices [data-action=deleteSkin][data-skin-id=$existingId]').click()")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("删除皮肤？").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("删除皮肤？").fetchSemanticsNodes().isEmpty()
            }
            assertTrue("Cancel keeps the third-party archive installed",
                File(context.filesDir, "skin-packs/$existingId.pcskin").isFile)
            evaluate(view,
                "document.querySelector('#skinChoices [data-action=deleteSkin][data-skin-id=$existingId]').click()")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("删除皮肤？").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认删除").performClick()
            compose.waitUntil(15_000) {
                skinManager.installed().none { it.id == existingId } &&
                    evaluate(view, "!document.querySelector('#skinChoices [data-skin-id=$existingId]')") == "true"
            }
            existingInstalled = false
            assertEquals("Management actions never modify formal events", formalBefore, store.all())
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())
            assertEquals("Deleting another skin leaves active Clear selected",
                "powercal-clear-default", skinManager.currentId())
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            if (existingInstalled) runCatching { skinManager.delete(existingId) }
            if (importedInstalled) runCatching { skinManager.delete(importId) }
            existingInput.delete()
            importInput.delete()
            clearOutput.delete()
            installedOutput.delete()
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) {
                restoreOnboarding.putBoolean("system-calendar-import-asked", askedForCalendarImport)
            } else {
                restoreOnboarding.remove("system-calendar-import-asked")
            }
            restoreOnboarding.commit()
        }
    }

    private fun skinPackageFixture(id: String): ByteArray {
        val original = InstrumentationRegistry.getInstrumentation().context.assets
            .open("skin-delivery-fixture.pcskin").use { it.readBytes() }
        val result = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(original)).use { input ->
            ZipOutputStream(result).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name
                    val source = input.readBytes()
                    input.closeEntry()
                    val content = if (name == "manifest.json") {
                        JSONObject(String(source, Charsets.UTF_8))
                            .put("id", id)
                            .put("name", "Skin Settings QA Fixture")
                            .toString().toByteArray(Charsets.UTF_8)
                    } else source
                    output.putNextEntry(ZipEntry(name))
                    output.write(content)
                    output.closeEntry()
                }
            }
        }
        return result.toByteArray()
    }
}
