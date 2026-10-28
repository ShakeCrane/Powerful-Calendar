package dev.powerfulcalendar.ui.v2

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exercises capability checks through SecureWebSkin's real WebMessageChannel. */
@RunWith(AndroidJUnit4::class)
class WebSkinCapabilityGateInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class CapabilityCase(
        val label: String,
        val capabilities: Set<String>,
        val mayRead: Boolean,
        val mayAct: Boolean,
        val declaresCapabilities: Boolean = true
    )

    private val cases = listOf(
        CapabilityCase("host.action", setOf("host.action"), mayRead = false, mayAct = true),
        CapabilityCase("missing", emptySet(), mayRead = false, mayAct = false,
            declaresCapabilities = false),
        CapabilityCase("host.read", setOf("host.read"), mayRead = true, mayAct = false),
        CapabilityCase("host.edit", setOf("host.edit"), mayRead = false, mayAct = false)
    )

    private fun fixture(capabilityCase: CapabilityCase): PowerCalSkinPack {
        val html = """<!doctype html>
            <html><head><meta charset="utf-8"><link rel="stylesheet" href="/web/style.css">
            <script src="/web/app.js"></script></head>
            <body>capability fixture</body></html>""".trimIndent()
        val script = """
            window.__hostPortReady = false;
            window.__hostStateFrames = 0;
            window.__latestHostState = null;
            window.__sentHostFrames = 0;
            window.addEventListener("message", function (event) {
              if (event.data !== "powercal-port" || event.ports.length !== 1) return;
              const port = event.ports[0];
              window.__powercalHostPort = port;
              port.onmessage = function (messageEvent) {
                const frame = JSON.parse(messageEvent.data);
                if (frame.type === "state") {
                  window.__latestHostState = frame.payload;
                  window.__hostStateFrames++;
                }
              };
              port.start();
              window.__hostPortReady = true;
            });
            window.__sendHostFrame = function (frame) {
              window.__powercalHostPort.postMessage(JSON.stringify(frame));
              window.__sentHostFrames++;
            };
        """.trimIndent()
        val resources = linkedMapOf(
            "web/index.html" to html.toByteArray(StandardCharsets.UTF_8),
            "web/style.css" to "body { font-family: sans-serif; }".toByteArray(StandardCharsets.UTF_8),
            "web/app.js" to script.toByteArray(StandardCharsets.UTF_8),
            "project.json" to JSONObject().put("projectVersion", 3)
                .put("mode", "advanced").toString().toByteArray(StandardCharsets.UTF_8)
        )
        val hashes = JSONObject()
        resources.forEach { (path, bytes) -> hashes.put(path, sha256(bytes)) }
        val manifest = JSONObject()
            .put("format", "powercal-skin")
            .put("schemaVersion", 3)
            .put("id", "capability-${capabilityCase.label.replace('.', '-')}")
            .put("name", "Capability ${capabilityCase.label}")
            .put("entrypoint", "web/index.html")
            .put("project", "project.json")
            .put("fullCanvas", true)
            .put("sha256", hashes)
        if (capabilityCase.declaresCapabilities) {
            manifest.put("capabilities", org.json.JSONArray().apply {
                capabilityCase.capabilities.sorted().forEach(::put)
            })
        }
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                fun add(path: String, content: ByteArray) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(content)
                    zip.closeEntry()
                }
                add("manifest.json", manifest.toString().toByteArray(StandardCharsets.UTF_8))
                resources.forEach(::add)
            }
        }.toByteArray()
        val pack = SkinPackageManager(compose.activity.applicationContext)
            .decode(archive, loadImages = true).pack
        assertNotNull("fixture manifest must produce a v3 Web bundle", pack.web)
        assertEquals(capabilityCase.capabilities, pack.web!!.capabilities)
        return pack
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val finished = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value ->
                result.set(value)
                finished.countDown()
            }
        }
        assertTrue("WebView JavaScript timed out: $expression",
            finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun awaitJs(web: WebView, expression: String) {
        compose.waitUntil(30_000) { evaluate(web, expression) == "true" }
    }

    private fun show(pack: PowerCalSkinPack): WebView {
        val bundle = requireNotNull(pack.web)
        compose.runOnUiThread {
            PowerCalSkinRegistry.activate(pack)
            compose.activity.setContent {
                WebSkinRoot(bundle, Modifier.fillMaxSize(), native = {}, onSkinFailure = {
                    throw AssertionError("capability fixture entered Native fallback")
                })
            }
        }
        val found = AtomicReference<WebView?>()
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                found.set(findWebView(compose.activity.findViewById(android.R.id.content)))
            }
            found.get() != null
        }
        return requireNotNull(found.get())
    }

    private fun sendAction(web: WebView) {
        val frame = JSONObject().put("version", 1).put("type", "action")
            .put("payload", JSONObject().put("name", "memo")).toString()
        evaluate(web, "window.__sendHostFrame(JSON.parse(${JSONObject.quote(frame)})); true")
    }

    private fun sendWrite(web: WebView) {
        // A syntactically valid deck.open command. The Host callback is replaced with a counter;
        // no PowerStore or persistent draft is involved in this test.
        val frame = JSONObject().put("version", 1).put("type", "command")
            .put("payload", JSONObject().put("name", "deck.open")
                .put("requestId", "capability01").put("draftId", JSONObject.NULL))
            .toString()
        evaluate(web, "window.__sendHostFrame(JSON.parse(${JSONObject.quote(frame)})); true")
    }

    @Test
    fun realWebViewMessagesAreGatedByManifestCapabilities() {
        val actionCalls = AtomicInteger()
        val writeCalls = AtomicInteger()
        val stateCalls = AtomicInteger()
        val observedAction = AtomicReference<WebSkinAction?>()
        val observedWrite = AtomicReference<WebEditCommand?>()
        val oldPack = PowerCalSkinRegistry.active
        val oldOwner = WebSkinBridgeRuntime.owner
        val oldAction = WebSkinBridgeRuntime.onAction
        val oldEdit = WebSkinBridgeRuntime.onEdit
        val oldState = WebSkinBridgeRuntime.state
        val oldRevision = WebSkinBridgeRuntime.stateRevision

        WebSkinBridgeRuntime.owner = Any()
        WebSkinBridgeRuntime.onAction = { action ->
            observedAction.set(action)
            actionCalls.incrementAndGet()
        }
        WebSkinBridgeRuntime.onEdit = { command ->
            observedWrite.set(command)
            writeCalls.incrementAndGet()
            JSONObject().put("requestId", command.requestId).put("status", "mocked")
        }
        WebSkinBridgeRuntime.state = {
            stateCalls.incrementAndGet()
            JSONObject().put("bridgeVersion", WebSkinBridgeRuntime.VERSION)
                .put("hostStateProbe", "visible-only-with-host-read")
        }

        try {
            cases.forEach { capabilityCase ->
                actionCalls.set(0)
                writeCalls.set(0)
                stateCalls.set(0)
                observedAction.set(null)
                observedWrite.set(null)

                val pack = fixture(capabilityCase)
                val web = show(pack)
                awaitJs(web, "window.__hostPortReady === true && window.__hostStateFrames > 0")

                assertEquals("bridge version must remain available (${capabilityCase.label})", "true",
                    evaluate(web, "window.__latestHostState.bridgeVersion === 1"))
                assertEquals("Host state visibility (${capabilityCase.label})", "${capabilityCase.mayRead}",
                    evaluate(web,
                        "window.__latestHostState.hostStateProbe === 'visible-only-with-host-read'"))
                assertEquals("Host state callback gate (${capabilityCase.label})",
                    capabilityCase.mayRead, stateCalls.get() > 0)

                if (capabilityCase.mayAct) {
                    sendAction(web)
                    awaitJs(web, "window.__sentHostFrames === 1")
                    compose.waitUntil(10_000) {
                        actionCalls.get() > 0
                    }
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .waitForIdleSync()
                    assertEquals("action must be delivered exactly once (${capabilityCase.label})",
                        1, actionCalls.get())
                    assertEquals(WebSkinAction.Routed("memo"), observedAction.get())

                    sendWrite(web)
                    awaitJs(web, "window.__sentHostFrames === 2")
                    compose.waitUntil(10_000) {
                        writeCalls.get() > 0
                    }
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .waitForIdleSync()
                    assertEquals("write must be delivered exactly once (${capabilityCase.label})",
                        1, writeCalls.get())
                    assertTrue("write mock should receive the valid WebView command",
                        observedWrite.get() is WebEditCommand.OpenDeck)
                } else {
                    sendAction(web)
                    sendWrite(web)
                    awaitJs(web, "window.__sentHostFrames === 2")
                    // The current bridge intentionally has no denial acknowledgement. Let the
                    // WebView/Android message queues settle before asserting there was no effect.
                    android.os.SystemClock.sleep(750)
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .waitForIdleSync()
                    assertEquals("action must be blocked (${capabilityCase.label})", 0,
                        actionCalls.get())
                    assertEquals("write must be blocked (${capabilityCase.label})", 0,
                        writeCalls.get())
                    assertEquals(null, observedAction.get())
                    assertEquals(null, observedWrite.get())
                }

                assertEquals("host.action does not imply host.read (${capabilityCase.label})",
                    capabilityCase.mayRead,
                    evaluate(web,
                        "window.__latestHostState.hostStateProbe === 'visible-only-with-host-read'") == "true")

                compose.runOnUiThread { compose.activity.setContent { } }
                compose.waitForIdle()
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.activate(oldPack)
            }
            compose.waitForIdle()
            WebSkinBridgeRuntime.owner = oldOwner
            WebSkinBridgeRuntime.onAction = oldAction
            WebSkinBridgeRuntime.onEdit = oldEdit
            WebSkinBridgeRuntime.state = oldState
            compose.runOnUiThread { WebSkinBridgeRuntime.stateRevision = oldRevision }
        }

        assertFalse("the test fixture itself should not include host.edit in the action case",
            "host.edit" in cases.first().capabilities)
        assertTrue("the fixture must include an inert host.edit-only case",
            cases.any { it.capabilities == setOf("host.edit") })
    }
}
