package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class FactorySkinBootInstrumentedTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val prefs get() = context.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)

    private fun storeSnapshot(): String {
        val db = PowerCalStores.get(context).readableDatabase
        return listOf("meta", "identities", "objects", "drafts", "schedule_intents", "ai_sessions", "commits")
            .joinToString("|") { table ->
                db.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { cursor ->
                    buildString { while (cursor.moveToNext()) { for (i in 0 until cursor.columnCount) { append(cursor.getString(i)).append('|') }; append('\n') } }
                }
            }
    }

    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) webView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun js(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        instrument.runOnMainSync { web.evaluateJavascript(expression) { result.set(it); done.countDown() } }
        assertTrue("WebView JS timed out", done.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun assertWebActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val ref = AtomicReference<WebView?>()
            val deadline = SystemClock.uptimeMillis() + 30000
            while (ref.get() == null && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { ref.set(webView(it.findViewById(android.R.id.content))) }
                if (ref.get() == null) SystemClock.sleep(100)
            }
            val web = requireNotNull(ref.get()) { "factory WebView missing" }
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                ready = js(web, "document.readyState === 'complete' && document.title === 'PowerCal Clear' && !!document.querySelector('#timelinePage')") == "true"
                if (!ready) SystemClock.sleep(100)
            }
            assertTrue("default timeline did not load", ready)
            assertEquals("\"https://skin.powercal.local/web/index.html\"", js(web, "location.href"))
        }
    }

    @Test fun freshQaFactoryBootRendersRealWebView() {
        val hadSelection = prefs.contains("selected")
        val previousSelection = prefs.getString("selected", null)
        try {
            check(prefs.edit().remove("selected").commit())
            PowerCalSkinRegistry.reset()
            val before = storeSnapshot()
            val manager = SkinPackageManager(context)
            assertTrue(manager.factoryPackageAvailable())
            instrument.runOnMainSync { manager.restoreSelection(SkinBootPolicy.QA_FRESH_FACTORY) }
            assertEquals(before, storeSnapshot())
            assertEquals("powercal-clear-default", manager.currentId())
            assertEquals("powercal-clear-default", PowerCalSkinRegistry.active.key)
            val web = requireNotNull(PowerCalSkinRegistry.active.web)
            var index: ByteArray? = null
            context.assets.open("powercal-clear-default.pcskin").use { asset ->
                ZipInputStream(asset).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == "web/index.html") index = zip.readBytes()
                    }
                }
            }
            assertArrayEquals(requireNotNull(index), web.files["web/index.html"])
            assertWebActivity()
        } finally {
            val restore = prefs.edit()
            if (hadSelection) restore.putString("selected", previousSelection)
            else restore.remove("selected")
            check(restore.commit())
            PowerCalSkinRegistry.reset()
            if (hadSelection) SkinPackageManager(context).restoreSelection()
        }
    }

    @Test fun persistedFactoryColdBoot() {
        assertEquals("powercal-clear-default", prefs.getString("selected", null))
        val before = storeSnapshot()
        assertWebActivity()
        assertEquals("powercal-clear-default", prefs.getString("selected", null))
        assertEquals("powercal-clear-default", PowerCalSkinRegistry.active.key)
        assertEquals(before, storeSnapshot())
    }

    private fun assertFactoryFailureFallsBackToNative(factoryBytes: () -> ByteArray) {
        val hadSelection = prefs.contains("selected")
        val previousSelection = prefs.getString("selected", null)
        val migrationKey = SkinPackageManager.SINGLE_DEFAULT_MIGRATION_KEY
        val hadMigration = prefs.contains(migrationKey)
        val previousMigration = prefs.getString(migrationKey, null)
        try {
            check(prefs.edit().remove("selected").commit())
            PowerCalSkinRegistry.reset()
            val before = storeSnapshot()
            var reads = 0
            val manager = SkinPackageManager(context, factoryPackageBytes = {
                reads++
                factoryBytes()
            })
            instrument.runOnMainSync { manager.restoreSelection(SkinBootPolicy.QA_FRESH_FACTORY) }
            assertEquals("powercal-default", manager.currentId())
            assertEquals("powercal-default", prefs.getString("selected", null))
            // Represent an already-migrated installation entering the explicit recovery path.
            // Otherwise the one-time upgrade migration correctly treats an old Native daily
            // selection as legacy and moves it to the factory skin.
            check(prefs.edit().putString(migrationKey, "complete").commit())
            instrument.runOnMainSync { manager.restoreSelection(SkinBootPolicy.QA_FRESH_FACTORY) }
            assertEquals(1, reads)
            assertEquals(before, storeSnapshot())
            repeat(2) {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity {
                        assertEquals("powercal-default", PowerCalSkinRegistry.active.key)
                        assertNull(PowerCalSkinRegistry.active.web)
                    }
                }
            }
            assertEquals("powercal-default", prefs.getString("selected", null))
        } finally {
            val restore = prefs.edit()
            if (hadSelection) restore.putString("selected", previousSelection)
            else restore.remove("selected")
            if (hadMigration) restore.putString(migrationKey, previousMigration)
            else restore.remove(migrationKey)
            check(restore.commit())
            PowerCalSkinRegistry.reset()
            if (hadSelection) SkinPackageManager(context).restoreSelection()
        }
    }

    @Test fun corruptFactoryColdBootFallsBackToNative() {
        assertFactoryFailureFallsBackToNative { byteArrayOf(1, 2, 3) }
    }

    @Test fun missingFactoryColdBootFallsBackToNative() {
        assertFactoryFailureFallsBackToNative {
            throw java.io.FileNotFoundException("isolated missing factory fixture")
        }
    }
}
