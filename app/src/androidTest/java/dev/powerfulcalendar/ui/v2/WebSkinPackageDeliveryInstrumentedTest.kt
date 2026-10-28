package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import dev.powerfulcalendar.MainActivity
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

private const val DELIVERY_TEST_NAMESPACE = "pc-skin-delivery-cold-restart"
private const val DELIVERY_PACKAGE_ID = "pc-delivery-fixture"
private const val DELIVERY_PACKAGE_SHA256 = "e7f200d4ddb6cb64cea3eda71f770b9dd920657f615d524319a3858897e5bddf"
private const val SKIN_CHOICE_PREFERENCES = "powercal-skin-choice"

/** A new value is generated only when the Android process loads this test class. */
private object DeliveryTestProcess {
    val token: String = UUID.randomUUID().toString()
}

@RunWith(AndroidJUnit4::class)
class WebSkinPackageDeliveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun requireIsolatedSkinStore(storage: Context) {
        val manager = SkinPackageManager(storage)
        assumeTrue("package delivery test is restricted to Android emulators",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("requires the original Native skin to be selected",
            manager.currentId() == "powercal-default")
        val packageDir = File(storage.filesDir, "skin-packs")
        assumeTrue("requires an empty skin package directory",
            packageDir.listFiles().orEmpty().isEmpty())
    }

    private fun cleanupSkinChoice(storage: Context) {
        check(storage.getSharedPreferences(SKIN_CHOICE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()) { "could not flush skin-choice cleanup" }
        check(storage.deleteSharedPreferences(SKIN_CHOICE_PREFERENCES)) {
            "could not delete skin-choice preferences"
        }
        check(!File(storage.applicationInfo.dataDir,
            "shared_prefs/$SKIN_CHOICE_PREFERENCES.xml").exists()) {
            "skin-choice preferences remain after cleanup"
        }
    }

    private fun cleanupPreparedSkin(
        storage: Context,
        manager: SkinPackageManager,
        packageFile: File
    ) {
        runCatching { manager.select("powercal-default") }
        runCatching { manager.delete(DELIVERY_PACKAGE_ID) }
        packageFile.delete()
        File(storage.cacheDir, DELIVERY_TEST_NAMESPACE).deleteRecursively()
        PowerCalSkinRegistry.reset()
        cleanupSkinChoice(storage)
        check(!File(storage.filesDir, "skin-packs/$DELIVERY_PACKAGE_ID.pcskin").exists()) {
            "installed delivery fixture remains after cleanup"
        }
        check(!File(storage.cacheDir, "$DELIVERY_TEST_NAMESPACE-studio-export.pcskin").exists()) {
            "temporary Studio export remains after cleanup"
        }
        check(!File(storage.cacheDir, DELIVERY_TEST_NAMESPACE).exists()) {
            "cold-restart markers remain after cleanup"
        }
    }

    private fun expectedRuntimeFiles(packageBytes: ByteArray): Map<String, ByteArray> =
        ZipInputStream(ByteArrayInputStream(packageBytes)).use { zip ->
            val files = linkedMapOf<String, ByteArray>()
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.startsWith("web/") || entry.name.startsWith("assets/") ||
                    entry.name == "project.json") {
                    files[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
            files
        }

    private fun assertRuntimeFilesEqual(
        expected: Map<String, ByteArray>,
        bundle: WebSkinBundle
    ) {
        assertEquals(expected.keys, bundle.files.keys)
        expected.forEach { (path, bytes) ->
            assertArrayEquals("Android runtime resource changed: $path", bytes, bundle.files[path])
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val completed = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { result ->
                value.set(result)
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out: $expression",
            completed.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val url = runCatching { evaluate(web, "location.href") }.getOrDefault("unavailable")
        throw AssertionError("WebView delivery timed out: $expression; url=$url")
    }

    private fun renderAndAssert(bundle: WebSkinBundle, failed: AtomicBoolean) {
        compose.runOnUiThread {
            compose.activity.setContent {
                WebSkinRoot(
                    bundle = bundle,
                    modifier = Modifier.fillMaxSize(),
                    native = {},
                    onSkinFailure = { failed.set(true) }
                )
            }
        }
        var webView: WebView? = null
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                webView = findWebView(compose.activity.findViewById(android.R.id.content))
            }
            webView != null
        }
        val web = requireNotNull(webView)
        awaitJs(web, "document.readyState === 'complete' && " +
            "document.documentElement.dataset.deliveryScriptProbe === 'studio-js-delivery-fixture-v1' && " +
            "document.querySelector('#assetProbe')?.complete === true")

        assertEquals("studio-html-delivery-fixture",
            evaluate(web, "document.querySelector('#htmlProbe').textContent" ).trim('"'))
        assertEquals("studio-js-delivery-fixture-v1",
            evaluate(web, "document.documentElement.dataset.deliveryScriptProbe").trim('"'))
        assertEquals("rgb(21, 67, 123)",
            evaluate(web, "getComputedStyle(document.documentElement).getPropertyValue('--delivery-css-probe').trim()")
                .trim('"'))
        assertEquals("true", evaluate(web,
            "document.querySelector('#assetProbe').complete && " +
                "document.querySelector('#assetProbe').naturalWidth === 1"))
        val styleUrl = evaluate(web, "document.styleSheets[0].href").trim('"')
        val scriptUrl = evaluate(web, "document.querySelector('script[src]').src").trim('"')
        val imageUrl = evaluate(web, "document.querySelector('#assetProbe').currentSrc").trim('"')
        assertEquals("https://skin.powercal.local/web/style.css", styleUrl)
        assertEquals("https://skin.powercal.local/web/app.js", scriptUrl)
        assertEquals("https://skin.powercal.local/assets/probe.png", imageUrl)
        assertFalse("package load triggered the Native fallback", failed.get())
    }

    @Test
    fun studioBuiltPackageInstallsActivatesAndRendersResourcesThroughAndroidWebView() {
        val keepForColdRestart = InstrumentationRegistry.getArguments()
            .getString("skinDeliveryPhase") == "prepare"
        val storage = compose.activity.applicationContext
        requireIsolatedSkinStore(storage)
        val isolatedRoot = File(storage.cacheDir, DELIVERY_TEST_NAMESPACE)
        isolatedRoot.deleteRecursively()
        cleanupSkinChoice(storage)
        val manager = SkinPackageManager(storage)
        val packageBytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("skin-delivery-fixture.pcskin").use { it.readBytes() }
        val packageSha = MessageDigest.getInstance("SHA-256").digest(packageBytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(DELIVERY_PACKAGE_SHA256, packageSha)
        val expectedFiles = expectedRuntimeFiles(packageBytes)

        val packageFile = File(storage.cacheDir, "$DELIVERY_TEST_NAMESPACE-studio-export.pcskin")
        val failed = AtomicBoolean(false)
        PowerCalSkinRegistry.reset()
        var prepared = false
        try {
            val selectedBeforeInstall = manager.currentId()
            packageFile.writeBytes(packageBytes)

            val installed = manager.install(Uri.fromFile(packageFile))
            assertEquals(DELIVERY_PACKAGE_ID, installed.id)
            assertEquals("installation must not activate the imported package",
                selectedBeforeInstall, manager.currentId())
            assertNull("installation must not replace the active presentation",
                PowerCalSkinRegistry.active.web)
            val stored = File(storage.filesDir, "skin-packs/$DELIVERY_PACKAGE_ID.pcskin")
            assertArrayEquals("Android must retain the exact Studio export", packageBytes,
                stored.readBytes())

            manager.select(installed.id)
            val selected = requireNotNull(PowerCalSkinRegistry.active.web)
            assertEquals(installed.id, PowerCalSkinRegistry.active.key)
            assertTrue(selected.fullCanvas)
            assertTrue(selected.capabilities.isEmpty())
            assertRuntimeFilesEqual(expectedFiles, selected)
            renderAndAssert(selected, failed)

            if (keepForColdRestart) {
                // Checked by a second instrumentation invocation after the host force-stops
                // the target package. That invocation must run in a different process.
                check(isolatedRoot.mkdirs() || isolatedRoot.isDirectory) {
                    "cannot create isolated process-marker directory"
                }
                File(isolatedRoot, "process-token").writeText(DeliveryTestProcess.token)
                File(isolatedRoot, "process-pid").writeText(android.os.Process.myPid().toString())
                prepared = true
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            if (!(keepForColdRestart && prepared)) {
                cleanupPreparedSkin(storage, manager, packageFile)
            }
        }
    }

    @Test
    fun afterColdProcessRestartRestoresAndServesTheExactStudioExport() {
        assumeTrue("run the restore phase with -e skinDeliveryPhase restore",
            InstrumentationRegistry.getArguments().getString("skinDeliveryPhase") == "restore")
        val storage = compose.activity.applicationContext
        val isolatedRoot = File(storage.cacheDir, DELIVERY_TEST_NAMESPACE)
        assumeTrue("the prepare phase must complete before restore",
            File(isolatedRoot, "process-token").isFile && File(isolatedRoot, "process-pid").isFile)
        val manager = SkinPackageManager(storage)
        val packageFile = File(storage.cacheDir, "$DELIVERY_TEST_NAMESPACE-studio-export.pcskin")
        var mainActivity: ActivityScenario<MainActivity>? = null
        try {
            val previousToken = File(isolatedRoot, "process-token").readText()
            val previousPid = File(isolatedRoot, "process-pid").readText().toInt()
            assertNotEquals("the host must terminate the first test process before restart",
                previousToken, DeliveryTestProcess.token)
            assertNotEquals("the Android target process must be new after force-stop",
                previousPid, android.os.Process.myPid())

            val packageBytes = InstrumentationRegistry.getInstrumentation().context.assets
                .open("skin-delivery-fixture.pcskin").use { it.readBytes() }
            val packageSha = MessageDigest.getInstance("SHA-256").digest(packageBytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(DELIVERY_PACKAGE_SHA256, packageSha)
            val expectedFiles = expectedRuntimeFiles(packageBytes)
            val stored = File(storage.filesDir, "skin-packs/$DELIVERY_PACKAGE_ID.pcskin")
            assertArrayEquals("installed export must survive process restart", packageBytes,
                stored.readBytes())

            PowerCalSkinRegistry.reset()
            mainActivity = ActivityScenario.launch(MainActivity::class.java)
            assertEquals("MainActivity must restore the persisted package selection",
                DELIVERY_PACKAGE_ID, manager.currentId())
            val restored = requireNotNull(PowerCalSkinRegistry.active.web)
            assertEquals(DELIVERY_PACKAGE_ID, PowerCalSkinRegistry.active.key)
            assertRuntimeFilesEqual(expectedFiles, restored)

            val webView = AtomicReference<WebView?>()
            compose.waitUntil(30_000) {
                mainActivity?.onActivity { activity ->
                    webView.set(findWebView(activity.findViewById(android.R.id.content)))
                }
                webView.get() != null
            }
            val actualWebView = requireNotNull(webView.get())
            awaitJs(actualWebView, "document.readyState === 'complete' && " +
                "document.documentElement.dataset.deliveryScriptProbe === 'studio-js-delivery-fixture-v1' && " +
                "document.querySelector('#assetProbe')?.complete === true")
            assertEquals("studio-html-delivery-fixture",
                evaluate(actualWebView, "document.querySelector('#htmlProbe').textContent").trim('"'))
            assertEquals("studio-js-delivery-fixture-v1",
                evaluate(actualWebView, "document.documentElement.dataset.deliveryScriptProbe").trim('"'))
            assertEquals("rgb(21, 67, 123)", evaluate(actualWebView,
                "getComputedStyle(document.documentElement).getPropertyValue('--delivery-css-probe').trim()")
                .trim('"'))
            assertEquals("true", evaluate(actualWebView,
                "document.querySelector('#assetProbe').complete && " +
                    "document.querySelector('#assetProbe').naturalWidth === 1"))
            assertEquals("https://skin.powercal.local/web/style.css",
                evaluate(actualWebView, "document.styleSheets[0].href").trim('"'))
            assertEquals("https://skin.powercal.local/web/app.js",
                evaluate(actualWebView, "document.querySelector('script[src]').src").trim('"'))
            assertEquals("https://skin.powercal.local/assets/probe.png",
                evaluate(actualWebView, "document.querySelector('#assetProbe').currentSrc").trim('"'))
        } finally {
            mainActivity?.close()
            compose.runOnUiThread { PowerCalSkinRegistry.reset() }
            compose.waitForIdle()
            cleanupPreparedSkin(storage, manager, packageFile)
        }
    }
}
