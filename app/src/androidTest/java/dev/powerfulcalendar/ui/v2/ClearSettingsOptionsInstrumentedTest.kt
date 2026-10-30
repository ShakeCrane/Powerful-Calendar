package dev.powerfulcalendar.ui.v2

import android.content.Context
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
import dev.powerfulcalendar.core.PowerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
}
