package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

private const val BLANK_GATE_ASSET = "skin-studio-blank-five-page-gate.pcskin"
private const val BLANK_GATE_ID = "powercal-blank-skin"
private const val BLANK_GATE_SHA256 = "1f7c1053022342d3ab78c4f549fc2719f211e616b256f761c907cdfabb79f492"
private const val BLANK_GATE_NAMESPACE = "pc-blank-studio-android-gate"
private const val SKIN_CHOICE_PREFERENCES = "powercal-skin-choice"
private val REQUIRED_ROUTES = listOf("TIMELINE", "MONTH", "MEMO", "AI", "SETTINGS")
private val REQUIRED_COMPONENTS = setOf(
    "text", "container", "button", "scroll", "form-field", "timeline", "event-card",
    "month-grid", "date-button", "memo-row", "chat-item", "settings-row", "resource-layer"
)
private object BlankStudioGateProcess {
    val token: String = UUID.randomUUID().toString()
}

private data class ComponentNode(
    val id: String,
    val type: String,
    val parent: String?,
    val text: String
)

private data class CoreStoreSnapshot(
    val identities: Long,
    val drafts: Long,
    val commits: Long,
    val nextSequence: Long
)

@RunWith(AndroidJUnit4::class)
class BlankStudioSkinAndroidGateInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun studioGeneratedFivePagePackageRendersAndSurvivesColdRestart() {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("blankStudioGatePhase", "single")
        require(phase in setOf("single", "prepare", "restore")) { "Unknown gate phase: $phase" }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val storage = compose.activity.applicationContext
        assumeTrue("generated skin runtime gate is restricted to Android emulators",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")

        val packageDir = File(storage.filesDir, "skin-packs")
        val manager = SkinPackageManager(storage)
        val markerDir = File(storage.cacheDir, BLANK_GATE_NAMESPACE)
        val packageFile = File(storage.cacheDir, "$BLANK_GATE_NAMESPACE.pcskin")
        val packageBytes = instrumentation.context.assets.open(BLANK_GATE_ASSET).use { it.readBytes() }
        val packageSha = sha256(packageBytes)
        assertEquals(BLANK_GATE_SHA256, packageSha)
        val expectedFiles = readRuntimeFiles(packageBytes)
        val manifest = readManifest(packageBytes)
        val project = JSONObject(requireNotNull(expectedFiles["project.json"]).toString(Charsets.UTF_8))
        assertEquals(BLANK_GATE_ID, manifest.getString("id"))
        assertEquals(3, manifest.getInt("schemaVersion"))
        assertEquals("web/index.html", manifest.getString("entrypoint"))
        assertEquals(setOf("host.read"), stringSet(manifest.getJSONArray("capabilities")))
        assertEquals(REQUIRED_ROUTES, routes(project))
        assertEquals(REQUIRED_COMPONENTS, componentTypes(project))
        assertTrue(expectedFiles.keys.containsAll(setOf(
            "web/index.html", "web/style.css", "web/app.js", "web/project-tree-v1.js", "project.json"
        )))
        assertTrue("uploaded Studio PNG must be part of the exported package",
            expectedFiles.containsKey("assets/probe.png"))
        assertTrue("Studio font must be packaged in the generated skin",
            expectedFiles.containsKey("assets/qa-font.woff2"))
        assertTrue("Studio-authored JavaScript must be packaged and referenced",
            expectedFiles.containsKey("web/components/qa-probe.js") &&
                expectedFiles.getValue("web/index.html").toString(Charsets.UTF_8)
                    .contains("components/qa-probe.js"))
        assertTrue("arbitrary UTF-8 source must survive package creation",
            expectedFiles.getValue("web/components/notes.pcal-source")
                .toString(Charsets.UTF_8).contains("保存并往返后保持不变"))
        val hashes = manifest.getJSONObject("sha256")
        assertEquals(expectedFiles.keys, hashes.keys().asSequence().toSet())
        expectedFiles.forEach { (path, bytes) ->
            assertEquals("Android package file hash differs for $path", sha256(bytes), hashes.getString(path))
        }

        val keepPackageForColdRestart = phase == "prepare"
        var prepared = false
        var mainActivity: ActivityScenario<MainActivity>? = null
        var webView: WebView? = null
        val failed = AtomicBoolean(false)
        try {
            when (phase) {
                "single", "prepare" -> {
                    assertEquals("the isolated QA app must start from Native recovery",
                        "powercal-default", manager.currentId())
                    assertTrue("the test app skin package directory must be empty",
                        packageDir.listFiles().orEmpty().isEmpty())
                    cleanupSkinChoice(storage)
                    markerDir.deleteRecursively()
                    PowerCalSkinRegistry.reset()
                    val store = PowerCalStores.get(storage)
                    val before = storeSnapshot(store)

                    packageFile.writeBytes(packageBytes)
                    val selectedBeforeInstall = manager.currentId()
                    val installed = manager.install(Uri.fromFile(packageFile))
                    assertEquals(BLANK_GATE_ID, installed.id)
                    assertEquals("package installation must not activate the skin",
                        selectedBeforeInstall, manager.currentId())
                    assertTrue("install must retain the exact Studio export",
                        File(packageDir, "$BLANK_GATE_ID.pcskin").readBytes().contentEquals(packageBytes))

                    manager.select(BLANK_GATE_ID)
                    val bundle = requireNotNull(PowerCalSkinRegistry.active.web)
                    assertEquals(BLANK_GATE_ID, PowerCalSkinRegistry.active.key)
                    assertEquals(setOf("host.read"), bundle.capabilities)
                    assertTrue("read-only Skin may use the state projection",
                        WebSkinCapabilityPolicy.mayRead(bundle))
                    assertFalse("read-only Skin must not receive Host action/write authority",
                        WebSkinCapabilityPolicy.mayAct(bundle))
                    assertRuntimeFilesEqual(expectedFiles, bundle)

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
                    compose.waitUntil(30_000) {
                        compose.runOnUiThread {
                            webView = findWebView(compose.activity.findViewById(android.R.id.content))
                        }
                        webView != null
                    }
                    verifyWebView(requireNotNull(webView), project, expectedFiles)
                    assertFalse("Android WebView reported a package/runtime failure", failed.get())
                    assertEquals("component/page interaction must not mutate Core business state",
                        before, storeSnapshot(store))

                    if (keepPackageForColdRestart) {
                        check(markerDir.mkdirs() || markerDir.isDirectory)
                        File(markerDir, "process-token").writeText(BlankStudioGateProcess.token)
                        File(markerDir, "process-pid").writeText(Process.myPid().toString())
                        prepared = true
                    }
                }

                "restore" -> {
                    assertTrue("prepare phase process marker is missing",
                        File(markerDir, "process-token").isFile && File(markerDir, "process-pid").isFile)
                    assertEquals(BLANK_GATE_ID, manager.currentId())
                    val token = File(markerDir, "process-token").readText()
                    val previousPid = File(markerDir, "process-pid").readText().toInt()
                    assertNotEquals("cold start must use a new process token", token, BlankStudioGateProcess.token)
                    assertNotEquals("cold start must use a new Android process", previousPid, Process.myPid())
                    assertArrayEquals(packageBytes,
                        File(packageDir, "$BLANK_GATE_ID.pcskin").readBytes())

                    PowerCalSkinRegistry.reset()
                    mainActivity = ActivityScenario.launch(MainActivity::class.java)
                    assertEquals(BLANK_GATE_ID, manager.currentId())
                    val bundle = requireNotNull(PowerCalSkinRegistry.active.web)
                    assertEquals(BLANK_GATE_ID, PowerCalSkinRegistry.active.key)
                    assertEquals(setOf("host.read"), bundle.capabilities)
                    assertFalse(WebSkinCapabilityPolicy.mayAct(bundle))
                    assertRuntimeFilesEqual(expectedFiles, bundle)

                    val webRef = AtomicReference<WebView?>()
                    compose.waitUntil(30_000) {
                        mainActivity?.onActivity { activity ->
                            webRef.set(findWebView(activity.findViewById(android.R.id.content)))
                        }
                        webRef.get() != null
                    }
                    webView = requireNotNull(webRef.get())
                    val store = PowerCalStores.get(storage)
                    val before = storeSnapshot(store)
                    verifyWebView(webView!!, project, expectedFiles)
                    assertEquals("cold-start page navigation must not mutate Core business state",
                        before, storeSnapshot(store))
                }
            }
        } finally {
            mainActivity?.close()
            compose.runOnUiThread { PowerCalSkinRegistry.reset() }
            compose.waitForIdle()
            if (!prepared || phase == "restore" || phase == "single") {
                runCatching { manager.select("powercal-default") }
                runCatching { manager.delete(BLANK_GATE_ID) }
                packageFile.delete()
                markerDir.deleteRecursively()
                cleanupSkinChoice(storage)
                assertFalse("generated skin package remained after gate cleanup",
                    File(packageDir, "$BLANK_GATE_ID.pcskin").exists())
            }
        }
    }

    private fun verifyWebView(web: WebView, project: JSONObject, expectedFiles: Map<String, ByteArray>) {
        awaitJs(web, "document.readyState==='complete' && " +
            "!!window.__POWERCAL_PAGE_TREE_V1__ && !!document.querySelector('#page-navigation')")
        assertEquals("https://skin.powercal.local/web/style.css",
            evaluate(web, "document.styleSheets[0].href").trim('"'))
        assertEquals("Android WebView did not load the packaged PowerCalBlankFixture FontFace", "true", evaluate(web,
            "document.querySelector('script[src$=\\\"project-tree-v1.js\\\"]')!==null"))
        assertEquals("https://skin.powercal.local/web/app.js",
            evaluate(web, "[...document.querySelectorAll('script[src]')].find(x=>x.src.endsWith('/web/app.js')).src")
                .trim('"'))
        assertTrue(expectedFiles["web/project-tree-v1.js"]!!.isNotEmpty())
        assertEquals(REQUIRED_ROUTES, jsonStrings(JSONArray(evaluateString(web,
            "JSON.stringify(window.__POWERCAL_PAGE_TREE_V1__.pages.map(page=>page.route))"))))
        assertEquals("loaded", evaluateString(web,
            "document.documentElement.dataset.blankStudioProbe"))

        for (route in REQUIRED_ROUTES) {
            assertEquals("true", evaluate(web,
                "(()=>{const button=document.querySelector('[data-page-route=\\\"$route\\\"]');" +
                    "if(!button)return false;button.click();return true})()"))
            awaitJs(web, "document.querySelector('#content [data-page-title]')?.dataset.pageTitle==='$route'")
            val snapshot = evaluateObject(web, """
                JSON.stringify((()=>({
                  route:document.querySelector('#content [data-page-title]')?.dataset.pageTitle,
                  title:document.querySelector('#content [data-page-title]')?.textContent,
                  nav:[...document.querySelectorAll('#page-navigation [data-page-route]')].map(x=>x.dataset.pageRoute),
                  nodes:[...document.querySelectorAll('#content [data-component-id]')].map(node=>({
                    id:node.dataset.componentId,
                    type:node.dataset.componentType,
                    parent:node.parentElement.closest('[data-component-id]')?.dataset.componentId||null,
                    text:node.dataset.componentType==='form-field'
                      ?node.querySelector(':scope > span')?.textContent||''
                      :[...node.childNodes].filter(child=>child.nodeType===Node.TEXT_NODE)
                        .map(child=>child.textContent).join(''),
                    color:getComputedStyle(node).color,
                    binding:node.dataset.binding||null
                  }))
                }))())
            """.trimIndent())
            assertEquals(route, snapshot.getString("route"))
            assertEquals(REQUIRED_ROUTES, jsonStrings(snapshot.getJSONArray("nav")))
            val expectedPage = project.getJSONArray("pages").getJSONObject(REQUIRED_ROUTES.indexOf(route))
            assertEquals(expectedPage.getString("title"), snapshot.getString("title"))
            val expectedNodes = flatten(expectedPage.getJSONArray("components"))
            val actualNodes = jsonNodes(snapshot.getJSONArray("nodes"))
            assertEquals("Android WebView component DOM differs from Studio project on $route",
                expectedNodes, actualNodes)
            println("S02_ANDROID_BLANK_PAGE=$route;nodes=${actualNodes.size};routes=${REQUIRED_ROUTES.size}")
        }
        awaitJs(web,
            "(()=>{document.fonts.load('12px PowerCalBlankFixture');return document.fonts.check('12px PowerCalBlankFixture') && " +
                "[...document.fonts].some(face=>face.family==='PowerCalBlankFixture' && face.status==='loaded')})()")
        assertEquals("true", evaluate(web,
            "[...document.fonts].some(face=>face.family==='PowerCalBlankFixture' && face.status==='loaded')"))
        assertTrue("Android WebView did not apply the packaged WOFF2 font",
            evaluateString(web,
                "getComputedStyle(document.querySelector('[data-component-type=\\\"resource-layer\\\"]')).fontFamily")
                .contains("PowerCalBlankFixture"))

        assertEquals("true", evaluate(web,
            "(()=>{document.querySelector('[data-page-route=\\\"MONTH\\\"]')?.click();return true})()"))
        assertEquals("true", evaluate(web, "document.querySelector('#content [data-binding=\\\"presentation.month.header\\\"]')!==null"))
        assertEquals("rgb(18, 52, 86)", evaluate(web,
            "getComputedStyle(document.querySelector('#content [data-binding=\\\"presentation.month.header\\\"]')).color")
            .trim('"'))
        assertTrue("Month grid must contain the reordered date buttons",
            evaluate(web, "document.querySelectorAll('#content [data-component-type=\\\"month-grid\\\"] > [data-component-type=\\\"date-button\\\"]').length===2") == "true")
        assertEquals("true", evaluate(web,
            "(()=>{document.querySelector('[data-page-route=\\\"AI\\\"]')?.click();return true})()"))
        awaitJs(web, "document.querySelector('#content [data-page-title]')?.dataset.pageTitle==='AI'")
        assertEquals("true", evaluate(web, "document.querySelector('#content input[readonly]')!==null"))
        assertEquals("true", evaluate(web,
            "(()=>{document.querySelector('[data-page-route=\\\"SETTINGS\\\"]')?.click();return true})()"))
        awaitJs(web, "document.querySelector('#content [data-page-title]')?.dataset.pageTitle==='SETTINGS'")
        assertEquals("true", evaluate(web,
            "getComputedStyle(document.querySelector('#content .qa-resource-probe')).backgroundImage.includes('https://skin.powercal.local/assets/probe.png')"))
        assertEquals("true", evaluate(web,
            "(()=>{const image=document.createElement('img');image.id='s02-asset-probe';" +
                "image.src='/assets/probe.png';document.body.appendChild(image);return true})()"))
        awaitJs(web, "document.querySelector('#s02-asset-probe')?.complete===true && " +
            "document.querySelector('#s02-asset-probe')?.naturalWidth===2")
        assertEquals("https://skin.powercal.local/assets/probe.png",
            evaluate(web, "document.querySelector('#s02-asset-probe').currentSrc").trim('"'))
        assertFalse("a component-only read-only skin must not carry Host write authority",
            WebSkinCapabilityPolicy.mayAct(requireNotNull(PowerCalSkinRegistry.active.web)))
        println("S02_ANDROID_BLANK_COMPONENTS=${REQUIRED_COMPONENTS.sorted().joinToString(",")};hostAction=false")
    }

    private fun flatten(nodes: JSONArray, parent: String? = null): List<ComponentNode> = buildList {
        for (index in 0 until nodes.length()) {
            val node = nodes.getJSONObject(index)
            val props = node.optJSONObject("props") ?: JSONObject()
            val text = listOf("label", "text", "title").firstNotNullOfOrNull { key ->
                props.optString(key).takeIf { props.has(key) && it.isNotEmpty() }
            }.orEmpty()
            add(ComponentNode(node.getString("id"), node.getString("type"), parent, text))
            addAll(flatten(node.optJSONArray("children") ?: JSONArray(), node.getString("id")))
        }
    }

    private fun jsonNodes(nodes: JSONArray): List<ComponentNode> = buildList {
        for (index in 0 until nodes.length()) {
            val node = nodes.getJSONObject(index)
            add(ComponentNode(node.getString("id"), node.getString("type"),
                node.optString("parent").takeIf { node.has("parent") && !node.isNull("parent") },
                node.optString("text")))
        }
    }

    private fun routes(project: JSONObject): List<String> = buildList {
        val pages = project.getJSONArray("pages")
        for (index in 0 until pages.length()) add(pages.getJSONObject(index).getString("route"))
    }

    private fun componentTypes(project: JSONObject): Set<String> {
        val result = linkedSetOf<String>()
        fun walk(nodes: JSONArray) {
            for (index in 0 until nodes.length()) {
                val node = nodes.getJSONObject(index)
                result += node.getString("type")
                walk(node.optJSONArray("children") ?: JSONArray())
            }
        }
        val pages = project.getJSONArray("pages")
        for (index in 0 until pages.length()) walk(pages.getJSONObject(index).getJSONArray("components"))
        return result
    }

    private fun readManifest(bytes: ByteArray): JSONObject = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: error("manifest.json is missing")
            val contents = zip.readBytes()
            zip.closeEntry()
            if (entry.name == "manifest.json") return@use JSONObject(contents.toString(Charsets.UTF_8))
        }
        error("manifest.json is missing")
    }

    private fun readRuntimeFiles(bytes: ByteArray): Map<String, ByteArray> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
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

    private fun assertRuntimeFilesEqual(expected: Map<String, ByteArray>, bundle: WebSkinBundle) {
        assertEquals(expected.keys, bundle.files.keys)
        expected.forEach { (path, bytes) ->
            assertArrayEquals("Android parser changed Studio package resource $path", bytes, bundle.files[path])
        }
    }

    private fun storeSnapshot(store: dev.powerfulcalendar.core.PowerStore) = CoreStoreSnapshot(
        identities = store.identityCount(), drafts = store.draftCount(),
        commits = store.commitCount(), nextSequence = store.nextSequencePreview()
    )

    private fun cleanupSkinChoice(storage: Context) {
        storage.getSharedPreferences(SKIN_CHOICE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        storage.deleteSharedPreferences(SKIN_CHOICE_PREFERENCES)
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
        val finished = CountDownLatch(1)
        compose.runOnUiThread { web.evaluateJavascript(expression) { result ->
            value.set(result)
            finished.countDown()
        } }
        assertTrue("Android WebView evaluation timed out: $expression", finished.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun evaluateString(web: WebView, expression: String): String =
        JSONArray("[${evaluate(web, expression)}]").getString(0)

    private fun evaluateObject(web: WebView, expression: String): JSONObject =
        JSONObject(evaluateString(web, expression))

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        throw AssertionError("Android WebView blank skin did not become ready: $expression")
    }

    private fun stringSet(array: JSONArray): Set<String> = buildSet {
        for (index in 0 until array.length()) add(array.getString(index))
    }

    private fun jsonStrings(array: JSONArray): List<String> = buildList {
        for (index in 0 until array.length()) add(array.getString(index))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
