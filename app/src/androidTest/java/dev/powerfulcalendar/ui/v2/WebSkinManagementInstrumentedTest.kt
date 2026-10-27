package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.PowerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
}
