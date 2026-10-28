package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinManagementIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private val storage: Context = ApplicationProvider.getApplicationContext()
    private val namespace = "skin-management-" + UUID.randomUUID()
    private val databaseName = "$namespace.db"
    private val context = object : ContextWrapper(storage) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
    }
    private val store = PowerStore(context, databaseName)
    private val registry = RecordingActivityResultRegistry()
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = registry
    }

    @Before fun setUp() {
        PowerCalSkinRegistry.reset()
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread {
            ui.activity.setContent { }
            PowerCalSkinRegistry.reset()
        }
        ui.waitForIdle()
        store.close()
        context.deleteDatabase(databaseName)
        listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
            .forEach { storage.deleteSharedPreferences("$namespace-$it") }
    }

    @Test fun skinSettingsExposeHostCatalogueAndReuseProtectedPackageFlows() {
        ui.activity.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner
            ) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitUntil(20_000) { WebSkinBridgeRuntime.state().optJSONArray("events") != null }
        assertFalse("skin.import" in optionIds(WebSkinBridgeRuntime.state()))
        assertFalse("skin.exportClear" in optionIds(WebSkinBridgeRuntime.state()))
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("skinSettings")) }
        ui.waitUntil(10_000) {
            val state = WebSkinBridgeRuntime.state()
            state.optString("page") == "SETTINGS" && state.optJSONArray("installedSkins") != null
        }

        val initial = WebSkinBridgeRuntime.state()
        val installed = initial.getJSONArray("installedSkins")
        val ids = (0 until installed.length()).map { installed.getJSONObject(it).getString("id") }
        assertTrue("powercal-default" in ids)
        assertTrue("powercal-clear-default" in ids)
        assertTrue("skin.import" in optionIds(initial))
        assertTrue("skin.exportClear" in optionIds(initial))
        assertEquals(0, initial.getJSONArray("events").length())

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("skin.import")) }
        ui.waitUntil(5_000) { registry.lastIntent != null }
        val import = requireNotNull(registry.lastIntent)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, import.action)
        assertTrue("application/zip" in (import.getStringArrayExtra(Intent.EXTRA_MIME_TYPES) ?: emptyArray()))
        assertEquals(0, store.draftCount())
        registry.clear()

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.InvokeOption("skin.exportClear")) }
        ui.waitUntil(5_000) { registry.lastIntent != null }
        val export = requireNotNull(registry.lastIntent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, export.action)
        assertEquals("application/zip", export.type)
        assertEquals("PowerCal-Clear-Preview.pcskin", export.getStringExtra(Intent.EXTRA_TITLE))
        registry.clear()

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.SelectSkin("powercal-clear-default"))
        }
        ui.onNodeWithText("切换皮肤前请确认").assertExists()
        assertEquals("powercal-default", selectedId(WebSkinBridgeRuntime.state()))
        ui.onNodeWithText("取消").performClick()
        ui.waitForIdle()
        assertEquals("powercal-default", selectedId(WebSkinBridgeRuntime.state()))

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.DeleteSkin("powercal-default"))
        }
        ui.waitForIdle()
        ui.onNodeWithText("删除皮肤？").assertDoesNotExist()
        assertEquals(ids, installedIds(WebSkinBridgeRuntime.state()))

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("timeline"))
            WebSkinBridgeRuntime.onAction(WebSkinAction.SelectSkin("powercal-clear-default"))
        }
        ui.waitForIdle()
        ui.onNodeWithText("切换皮肤前请确认").assertDoesNotExist()
    }

    private fun optionIds(state: org.json.JSONObject): Set<String> {
        val groups = state.getJSONArray("optionGroups")
        return buildSet {
            for (groupIndex in 0 until groups.length()) {
                val items = groups.getJSONObject(groupIndex).getJSONArray("items")
                for (itemIndex in 0 until items.length()) {
                    add(items.getJSONObject(itemIndex).getString("id"))
                }
            }
        }
    }

    private fun installedIds(state: org.json.JSONObject): List<String> {
        val skins = state.getJSONArray("installedSkins")
        return (0 until skins.length()).map { skins.getJSONObject(it).getString("id") }
    }

    private fun selectedId(state: org.json.JSONObject): String {
        val skins = state.getJSONArray("installedSkins")
        for (index in 0 until skins.length()) {
            val item = skins.getJSONObject(index)
            if (item.getBoolean("selected")) return item.getString("id")
        }
        error("No selected skin in host projection")
    }

    private class RecordingActivityResultRegistry : ActivityResultRegistry() {
        var lastIntent: Intent? = null
            private set

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            lastIntent = contract.createIntent(ApplicationProvider.getApplicationContext(), input)
        }

        fun clear() { lastIntent = null }
    }
}
