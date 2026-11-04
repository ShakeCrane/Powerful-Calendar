package dev.powerfulcalendar.ui.v2

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.MainActivity
import dev.powerfulcalendar.core.PowerStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val HOST_BINDING_PACKAGE_ID = "powercal-host-binding-gate"
private const val READONLY_PACKAGE_ID = "powercal-readonly-write-gate"
private const val HOST_BINDING_PACKAGE_SHA256 = "10570d2ae43afafd443c0fa453c6b22b753e94f968ae8094411ec88410c38be4"
private const val READONLY_PACKAGE_SHA256 = "34d287d9a6c267b9c0162bbd8b4943b2fb8e91ed58fdcf11d06becba8a147570"

private data class HostBindingStoreSnapshot(
    val identities: Long,
    val drafts: Long,
    val commits: Long,
    val nextSequence: Long
)

@RunWith(AndroidJUnit4::class)
class HostBindingCatalogInstrumentedTest {
    @Test
    fun studioBindingsReachHostAndReadOnlyWriteRequestsAreRejected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val storage = instrumentation.targetContext.applicationContext
        assumeTrue("requires the isolated Android QA emulator", Build.HARDWARE == "ranchu" ||
            Build.HARDWARE == "goldfish")
        assertEquals("the Host binding runtime gate is pinned to API 35", 35, Build.VERSION.SDK_INT)

        val manager = SkinPackageManager(storage)
        assumeTrue("requires the isolated QA app's Native recovery selection",
            manager.currentId() == "powercal-default")
        val packageDir = File(storage.filesDir, "skin-packs")
        assumeTrue("requires an empty isolated QA skin package directory",
            packageDir.listFiles().orEmpty().isEmpty())

        val prefs = storage.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val priorSelection = prefs.getString("selected", null)
        val store = PowerCalStores.get(storage)
        val storeBefore = snapshot(store)
        val installedIds = mutableSetOf<String>()
        val cacheFiles = mutableListOf<File>()
        var activity: ActivityScenario<MainActivity>? = null

        try {
            val hostPackage = readFixture(storage, "skin-studio-host-binding-v1.pcskin",
                HOST_BINDING_PACKAGE_SHA256)
            val hostFile = File(storage.cacheDir, "$HOST_BINDING_PACKAGE_ID.pcskin")
            cacheFiles += hostFile
            hostFile.writeBytes(hostPackage)
            assertEquals(HOST_BINDING_PACKAGE_ID, manager.install(Uri.fromFile(hostFile)).id)
            installedIds += HOST_BINDING_PACKAGE_ID
            manager.select(HOST_BINDING_PACKAGE_ID)

            activity = ActivityScenario.launch(MainActivity::class.java)
            val hostWebView = awaitConnectedWebView(activity!!)
            val hostBundle = requireNotNull(PowerCalSkinRegistry.active.web)
            assertEquals(HOST_BINDING_PACKAGE_ID, PowerCalSkinRegistry.active.key)
            assertTrue(WebSkinCapabilityPolicy.mayRead(hostBundle))
            assertTrue(WebSkinCapabilityPolicy.mayAct(hostBundle))
            assertEquals("true", evaluate(hostWebView,
                "document.querySelector('[data-host-action=\\\"today\\\"]')!==null && " +
                    "document.querySelector('[data-host-action=\\\"panTimeline\\\"]')!==null && " +
                    "document.querySelector('[data-host-state-binding=\\\"timeline.anchorMillis\\\"] .pc-bound-value')!==null"))

            val initialState = hostState(activity!!)
            val initialAnchor = initialState.getJSONObject("timeline").getLong("anchorMillis")
            click(hostWebView, "panTimeline")
            awaitCondition("panTimeline Host action did not advance the camera") {
                hostState(activity!!).getJSONObject("timeline").optLong("anchorMillis") ==
                    initialAnchor + 60 * 60_000L
            }
            assertTrue("the state-bound component must render the updated Host camera",
                evaluate(hostWebView,
                    "document.querySelector('[data-host-state-binding=\\\"timeline.anchorMillis\\\"] .pc-bound-value')?.textContent===" +
                        "'${initialAnchor + 60 * 60_000L}'") == "true")

            click(hostWebView, "today")
            awaitCondition("today Host action did not update the camera to local noon") {
                val state = hostState(activity!!)
                val timeline = state.getJSONObject("timeline")
                val zone = ZoneId.of(timeline.getString("zoneId"))
                val expected = LocalDate.now(zone).atTime(LocalTime.NOON).atZone(zone)
                    .toInstant().toEpochMilli()
                timeline.optLong("anchorMillis") == expected && timeline.optDouble("hours") == 24.0
            }
            val todayTimeline = hostState(activity!!).getJSONObject("timeline")
            assertEquals(24.0, todayTimeline.getDouble("hours"), 0.0)
            assertEquals("Core Store must not change for camera navigation", storeBefore, snapshot(store))
            println("S03_ANDROID_HOST_ACTION=today;anchor=${todayTimeline.getLong("anchorMillis")};" +
                "zone=${todayTimeline.getString("zoneId")};hours=${todayTimeline.getDouble("hours")}")

            activity!!.close()
            activity = null
            manager.select("powercal-default")
            manager.delete(HOST_BINDING_PACKAGE_ID)
            installedIds -= HOST_BINDING_PACKAGE_ID

            val readonlyPackage = readFixture(storage, "skin-studio-readonly-write-v1.pcskin",
                READONLY_PACKAGE_SHA256)
            val readonlyFile = File(storage.cacheDir, "$READONLY_PACKAGE_ID.pcskin")
            cacheFiles += readonlyFile
            readonlyFile.writeBytes(readonlyPackage)
            assertEquals(READONLY_PACKAGE_ID, manager.install(Uri.fromFile(readonlyFile)).id)
            installedIds += READONLY_PACKAGE_ID
            manager.select(READONLY_PACKAGE_ID)

            activity = ActivityScenario.launch(MainActivity::class.java)
            val readonlyWebView = awaitConnectedWebView(activity!!)
            val readonlyBundle = requireNotNull(PowerCalSkinRegistry.active.web)
            assertEquals(READONLY_PACKAGE_ID, PowerCalSkinRegistry.active.key)
            assertTrue(WebSkinCapabilityPolicy.mayRead(readonlyBundle))
            assertFalse("read-only Skin must not have the Host action capability",
                WebSkinCapabilityPolicy.mayAct(readonlyBundle))
            assertEquals("true", evaluate(readonlyWebView,
                "document.querySelector('[data-host-action=\\\"createDraft\\\"]')!==null"))

            val beforeRejectedWrite = hostState(activity!!)
            assertFalse(beforeRejectedWrite.getJSONObject("editing").getJSONObject("editor")
                .optBoolean("open"))
            click(readonlyWebView, "createDraft")
            SystemClock.sleep(400)
            val afterRejectedWrite = hostState(activity!!)
            assertEquals("Host must ignore write actions without host.action",
                beforeRejectedWrite.getString("page"), afterRejectedWrite.getString("page"))
            assertFalse(afterRejectedWrite.getJSONObject("editing").getJSONObject("editor")
                .optBoolean("open"))
            assertEquals("Read-only write attempt must leave Core Store unchanged",
                storeBefore, snapshot(store))
            println("S03_ANDROID_READONLY=createDraft;host.action=false;editorOpen=false;storeUnchanged=true")
        } finally {
            activity?.close()
            runCatching { manager.select("powercal-default") }
            installedIds.forEach { id -> runCatching { manager.delete(id) } }
            cacheFiles.forEach(File::delete)
            val restore = prefs.edit()
            if (priorSelection == null) restore.remove("selected") else restore.putString("selected", priorSelection)
            check(restore.commit()) { "could not restore the isolated QA skin choice" }
            PowerCalSkinRegistry.reset()
        }

        assertEquals("all Studio gate package files must be removed after the test", 0,
            packageDir.listFiles().orEmpty().size)
        assertEquals("Core Store must remain unchanged after both Host checks", storeBefore, snapshot(store))
    }

    private fun readFixture(context: Context, name: String, expectedSha: String): ByteArray {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }
        val actualSha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals("AndroidTest Studio package fixture hash changed: $name", expectedSha, actualSha)
        return bytes
    }

    private fun snapshot(store: PowerStore) = HostBindingStoreSnapshot(
        identities = store.identityCount(),
        drafts = store.draftCount(),
        commits = store.commitCount(),
        nextSequence = store.nextSequencePreview()
    )

    private fun hostState(activity: ActivityScenario<MainActivity>): JSONObject {
        val result = AtomicReference<JSONObject>()
        activity.onActivity { result.set(WebSkinBridgeRuntime.state()) }
        return requireNotNull(result.get())
    }

    private fun awaitConnectedWebView(activity: ActivityScenario<MainActivity>): WebView {
        val found = AtomicReference<WebView?>()
        awaitCondition("MainActivity did not show its selected Web skin") {
            activity.onActivity { current -> found.set(findWebView(current.window.decorView)) }
            found.get() != null
        }
        val web = requireNotNull(found.get())
        awaitCondition("Studio package WebView did not connect to Core Host state") {
            evaluate(web, "document.readyState==='complete' && " +
                "document.querySelector('#status')?.textContent.includes('Core 状态已连接')") == "true"
        }
        awaitCondition("Host state did not publish the test Skin page") {
            hostState(activity).optString("page") == "TIMELINE"
        }
        return web
    }

    private fun click(web: WebView, action: String) {
        assertEquals("the generated Skin action button must be clickable", "true",
            evaluate(web, "(()=>{const button=document.querySelector('[data-host-action=\\\"$action\\\"]');" +
                "if(!button)return false;button.click();return true})()"))
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            web.evaluateJavascript(expression) { result ->
                value.set(result)
                done.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out: $expression",
            done.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitCondition(message: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError(message)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
