package dev.powerfulcalendar.ui.v2

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiChatMessage
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PowerCalAiProposalReviewWebViewInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONObject(JSONArray("[$encoded]").getString(0))
    }

    private fun tapWebSelector(
        web: WebView,
        selector: String,
        requireTrustedClick: Boolean = true
    ): JSONObject {
        val selectorLiteral = JSONObject.quote(selector)
        val probe = evaluateObject(web, """(() => {
          const target=document.querySelector($selectorLiteral);
          if(!target)return {found:false};
          const fixedTabbar=target.closest('.clear-tabbar');
          if(!fixedTabbar)target.scrollIntoView({block:'center',inline:'nearest'});
          const rect=target.getBoundingClientRect(),style=getComputedStyle(target);
          const tabbarRect=fixedTabbar?.getBoundingClientRect();
          const hit=document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2);
          const receipt={trustedDown:false,trustedUp:false,trustedClick:false,pointerType:'',documentDown:null};
          window.__aiParityTrustedTap=receipt;
          document.addEventListener('pointerdown',event=>{
            const node=event.target;
            receipt.documentDown={trusted:event.isTrusted,pointerType:String(event.pointerType),
              tag:node?.tagName||'',action:node?.dataset?.action||'',className:String(node?.className||'')};
          },{capture:true,once:true});
          target.addEventListener('pointerdown',event=>{
            receipt.trustedDown=event.isTrusted;receipt.pointerType=String(event.pointerType);
          },{once:true});
          target.addEventListener('pointerup',event=>{receipt.trustedUp=event.isTrusted},{once:true});
          target.addEventListener('click',event=>{receipt.trustedClick=event.isTrusted},{once:true});
          return {found:true,x:rect.left+rect.width/2,y:rect.top+rect.height/2,
            cssWidth:document.documentElement.clientWidth,cssHeight:window.innerHeight,
            visible:style.display!=='none'&&style.visibility!=='hidden'&&rect.width>0&&rect.height>0&&
              rect.bottom>0&&rect.top<window.innerHeight&&rect.right>0&&
              rect.left<document.documentElement.clientWidth,
            scrollY:window.scrollY,tabbarPosition:fixedTabbar?getComputedStyle(fixedTabbar).position:'',
            tabbarTop:tabbarRect?.top??null,hitTag:hit?.tagName||'',hitAction:hit?.dataset?.action||'',
            hitClass:String(hit?.className||'')};
        })()""")
        assertTrue("AI Clear control should be visible in the WebView viewport: $selector $probe",
            probe.optBoolean("found") && probe.optBoolean("visible"))
        val location = IntArray(2)
        val size = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            size.set(web.width to web.height)
        }
        val (widthPx, heightPx) = requireNotNull(size.get())
        val scaleX = widthPx / probe.getDouble("cssWidth")
        val scaleY = heightPx / probe.getDouble("cssHeight")
        assertEquals("Clear touch mapping must preserve WebView aspect ratio", scaleX, scaleY, 0.02)
        val x = location[0] + (probe.getDouble("x") * scaleX).toFloat()
        val y = location[1] + (probe.getDouble("y") * scaleY).toFloat()
        probe.put("webScreenX", location[0]).put("webScreenY", location[1])
            .put("webWidthPx", widthPx).put("webHeightPx", heightPx)
            .put("scaleX", scaleX).put("scaleY", scaleY).put("touchX", x).put("touchY", y)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Android accepted Clear AI touchscreen down",
                automation.injectInputEvent(down, true))
            SystemClock.sleep(80)
            assertTrue("Android accepted Clear AI touchscreen up",
                automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        var receipt = evaluateObject(web, "window.__aiParityTrustedTap")
        if (requireTrustedClick) {
            val clickDeadline = SystemClock.uptimeMillis() + 1_500
            while (!receipt.optBoolean("trustedClick") &&
                SystemClock.uptimeMillis() < clickDeadline) {
                SystemClock.sleep(50)
                receipt = evaluateObject(web, "window.__aiParityTrustedTap")
            }
        }
        assertTrue("Clear AI control must receive trusted touchscreen pointerdown: " +
            "selector=$selector probe=$probe receipt=$receipt", receipt.optBoolean("trustedDown"))
        assertTrue("Clear AI control must receive trusted touchscreen pointerup: " +
            "selector=$selector probe=$probe receipt=$receipt", receipt.optBoolean("trustedUp"))
        if (requireTrustedClick) {
            assertTrue("Clear AI control must receive trusted click: selector=$selector probe=$probe receipt=$receipt",
                receipt.optBoolean("trustedClick"))
        }
        return probe.put("screenX", x).put("screenY", y)
            .put("scaleX", scaleX).put("scaleY", scaleY)
    }

    private fun findSystemNode(
        root: AccessibilityNodeInfo?,
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null,
        textContains: String? = null
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if ((text == null || root.text?.toString() == text) &&
            (contentDescription == null || root.contentDescription?.toString() == contentDescription) &&
            (viewId == null || root.viewIdResourceName == viewId) &&
            (textContains == null || root.text?.toString()?.contains(textContains) == true ||
                root.contentDescription?.toString()?.contains(textContains) == true)
        ) return root
        for (index in 0 until root.childCount) {
            findSystemNode(root.getChild(index), text, contentDescription, viewId, textContains)
                ?.let { return it }
        }
        return null
    }

    private fun waitForSystemNode(
        packageName: String,
        text: String? = null,
        contentDescription: String? = null,
        viewId: String? = null,
        textContains: String? = null,
        timeoutMillis: Long = 15_000
    ): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString() == packageName) {
                findSystemNode(root, text, contentDescription, viewId, textContains)
                    ?.let { return it }
            }
            SystemClock.sleep(120)
        }
        val activeRoot = automation.rootInActiveWindow
        val visibleNodes = buildList {
            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null || size >= 80) return
                val label = listOfNotNull(
                    node.text?.toString()?.takeIf(String::isNotBlank),
                    node.contentDescription?.toString()?.takeIf(String::isNotBlank),
                    node.viewIdResourceName?.takeIf(String::isNotBlank)
                ).distinct().joinToString("|")
                if (label.isNotEmpty() || node.isClickable) {
                    val bounds = Rect().also(node::getBoundsInScreen)
                    add("$label:${node.className}:click=${node.isClickable}:$bounds")
                }
                for (index in 0 until node.childCount) visit(node.getChild(index))
            }
            visit(activeRoot)
        }.joinToString(" < ")
        throw AssertionError("System picker node not found: package=$packageName text=$text " +
            "description=$contentDescription viewId=$viewId textContains=$textContains " +
            "active=${activeRoot?.packageName}; " +
            "visible=$visibleNodes")
    }

    private fun tapSystemNode(node: AccessibilityNodeInfo) {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            SystemClock.sleep(200)
            return
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        assertFalse("Picker node must have visible bounds", bounds.isEmpty)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val x = bounds.exactCenterX().toInt()
        val y = bounds.exactCenterY().toInt()
        val command = instrumentation.uiAutomation.executeShellCommand("input tap $x $y")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        SystemClock.sleep(200)
    }

    private fun visiblePhotoPickerImages(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val images = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.packageName?.toString() == "com.google.android.providers.media.module" &&
                node.isClickable &&
                node.contentDescription?.toString()?.startsWith("Photo taken on ") == true
            ) {
                images += node
            }
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(root)
        return images
    }

    private fun selectImageFromAndroidPicker(displayName: String) {
        val pickerPackage = "com.google.android.providers.media.module"
        waitForSystemNode(pickerPackage, viewId =
            "com.google.android.providers.media.module:id/picker_tab_recyclerview")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var pickerRoot: AccessibilityNodeInfo? = null
        var imageNodes = emptyList<AccessibilityNodeInfo>()
        while (SystemClock.elapsedRealtime() < deadline && imageNodes.isEmpty()) {
            pickerRoot = automation.rootInActiveWindow
            imageNodes = visiblePhotoPickerImages(pickerRoot)
            if (imageNodes.isEmpty()) SystemClock.sleep(120)
        }
        assertTrue("Android Photo Picker must expose the isolated image fixture $displayName; " +
            "visible=${pickerRoot?.packageName}; tiles=${imageNodes.size}", imageNodes.isNotEmpty())
        tapSystemNode(imageNodes.first())

        val returnDeadline = SystemClock.elapsedRealtime() + 12_000
        while (SystemClock.elapsedRealtime() < returnDeadline &&
            automation.rootInActiveWindow?.packageName?.toString() != compose.activity.packageName
        ) {
            SystemClock.sleep(120)
        }
        val activeRoot = automation.rootInActiveWindow
        val activeNodes = buildList {
            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null || size >= 100) return
                val label = listOfNotNull(
                    node.text?.toString()?.takeIf(String::isNotBlank),
                    node.contentDescription?.toString()?.takeIf(String::isNotBlank),
                    node.viewIdResourceName?.takeIf(String::isNotBlank)
                ).distinct().joinToString("|")
                if (label.isNotEmpty() || node.isClickable) {
                    val bounds = Rect().also(node::getBoundsInScreen)
                    add("$label:${node.className}:click=${node.isClickable}:$bounds")
                }
                for (index in 0 until node.childCount) visit(node.getChild(index))
            }
            visit(activeRoot)
        }.joinToString(" < ")
        assertTrue("Selecting the image must return the result to the PowerCal Host; " +
            "expected=${compose.activity.packageName}; active=${activeRoot?.packageName}; " +
            "nodes=$activeNodes",
            activeRoot?.packageName?.toString() == compose.activity.packageName)
    }

    @Test
    fun clearAiTemporaryCacheUsesHostActionAndPreservesChatAndCalendarStore() {
        val storage: Context = compose.activity.applicationContext
        val context = IsolatedAppContext(compose.activity, storage,
            "pc-ai-cache-clear-${UUID.randomUUID()}")
        val store = PowerStore(context, "cache-clear.db")
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        val draft = store.newDraft(DraftForm(kind = TemporalKind.MEMO,
            title = "cache-clear-store-sentinel", zoneId = ZoneId.systemDefault().id))
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        val storeBefore = listOf(store.draftCount(), store.pendingCount().toLong(),
            store.identityCount(), store.commitCount())
        val chat = listOf(AiChatMessage(role = "user", content = "keep this chat"))
        AiChatHistoryStore.save(context, chat)
        val cacheFile = File(context.cacheDir, "powercal-ai-cache-clear-test.bin")
        cacheFile.writeText("temporary test evidence")
        assertTrue(cacheFile.isFile)
        var web: WebView? = null

        try {
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                SkinPackageManager(context).select("powercal-clear-default")
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides context) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            awaitJs(view, "!!window.PowerCalSkin?.receive && !!document.querySelector('#aiPage')")
            tapWebSelector(view, ".clear-tabbar [data-action='ai']")
            awaitJs(view, "!document.querySelector('#aiPage').hidden")
            tapWebSelector(view, "#aiOverflow > summary")
            awaitJs(view, "document.querySelector('#aiOverflow').open")
            tapWebSelector(view, ".clear-tabbar [data-action='month']")
            awaitJs(view, "document.body.dataset.page==='month' && !document.querySelector('#aiOverflow').open")
            tapWebSelector(view, ".clear-tabbar [data-action='ai']")
            awaitJs(view, "document.body.dataset.page==='ai' && !document.querySelector('#aiPage').hidden")
            tapWebSelector(view, "#aiOverflow > summary")
            awaitJs(view, "document.querySelector('#aiOverflow').open")
            val menuTouch = tapWebSelector(view, "#chatClearCache")
            awaitJs(view, "document.querySelector('#hostNotice').textContent.includes('已清理 1 个应用私有 AI 临时缓存文件')")

            assertTrue("Host action must delete only the prefixed app-private cache file",
                !cacheFile.exists())
            assertEquals("Temporary cache clearing must preserve chat history", chat,
                AiChatHistoryStore.load(context))
            assertEquals("Temporary cache clearing must not write Core calendar data",
                storeBefore, listOf(store.draftCount(), store.pendingCount().toLong(),
                    store.identityCount(), store.commitCount()))
            println("NATIVE095_AI_CACHE_MENU hostAction=clearAiTemporaryCache " +
                "cacheDeleted=true chatPreserved=true storeUnchanged=true trustedTouch=$menuTouch")
        } finally {
            cacheFile.delete()
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
        }
    }

    @Test
    fun nativeAndClearRealChatSendMatchHistoryAndRespectDisabledContextSharing() {
        val storage: Context = compose.activity.applicationContext
        val nativeNamespace = "pc-ai-chat-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-ai-chat-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "chat-parity-native.db"
        val clearDatabase = "chat-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val prompt = "Compare chat parity"
        val assistantReply = "The same deterministic reply; no calendar action."
        val privateSentinel = "PRIVATE_AI_CONTEXT_SENTINEL"
        val serverFailure = AtomicReference<Throwable?>()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val served = CountDownLatch(2)

        fun initialize(context: IsolatedAppContext, store: PowerStore) {
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())
            val draft = store.newDraft(DraftForm(kind = TemporalKind.MEMO,
                title = privateSentinel, zoneId = ZoneId.systemDefault().id))
            assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        }

        fun storeSnapshot(store: PowerStore): List<Long> = listOf(
            store.draftCount(), store.pendingCount().toLong(), store.identityCount(), store.commitCount()
        )

        try {
            initialize(nativeContext, nativeStore)
            initialize(clearContext, clearStore)
            val nativeBefore = storeSnapshot(nativeStore)
            val clearBefore = storeSnapshot(clearStore)

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                val endpoint = "http://127.0.0.1:${server.localPort}/v1"
                listOf(nativeContext, clearContext).forEach { context ->
                    AiSettingsStore.save(context, AiSettings(endpoint = endpoint,
                        model = "local-stub:free", apiKey = "LOCAL_TEST_ONLY",
                        enabled = true, shareContext = false))
                }
                val serverThread = Thread {
                    try {
                        repeat(2) {
                            server.accept().use { socket ->
                                requests += readRequest(socket)
                                writeReply(socket, assistantReply)
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-native-clear-chat-parity"; isDaemon = true; start() }

                compose.runOnUiThread {
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides nativeContext) {
                            PowerCalTheme { PowerCalApp(nativeStore) }
                        }
                    }
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("AI 助手").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("AI 助手").performClick()
                compose.onNodeWithText("发送消息…").performTextInput(prompt)
                compose.onNodeWithText("发送").performClick()
                compose.waitUntil(20_000) {
                    serverFailure.get() != null ||
                        compose.onAllNodesWithText(assistantReply).fetchSemanticsNodes().isNotEmpty()
                }
                assertNull("local Native chat provider failed", serverFailure.get())
                val nativeHistory = AiChatHistoryStore.load(nativeContext)
                    .map { it.role to it.content }
                assertEquals(listOf("user" to prompt, "assistant" to assistantReply), nativeHistory)
                assertEquals("Native chat must not publish or stage calendar data", nativeBefore,
                    storeSnapshot(nativeStore))

                compose.runOnUiThread {
                    compose.activity.setContent { }
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(clearContext).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides clearContext) {
                            PowerCalTheme { PowerCalApp(clearStore) }
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                val aiTabTouch = tapWebSelector(view, ".clear-tabbar [data-action=ai]")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                // The text field's behavior is focus + real keyboard entry; WebView does not
                // consistently synthesize a DOM click for an otherwise trusted touch sequence.
                val inputTouch = tapWebSelector(view, "#chatInput", requireTrustedClick = false)
                InstrumentationRegistry.getInstrumentation().sendStringSync(prompt)
                awaitJs(view, "document.querySelector('#chatInput')?.value===${JSONObject.quote(prompt)}")
                val sendTouch = tapWebSelector(view, "#chatSend")
                awaitJs(view, "document.querySelector('#chatMessages')?.textContent.includes("+
                    "${JSONObject.quote(assistantReply)})")
                val hostProjectionCount = WebSkinBridgeRuntime.state()
                    .optJSONArray("chatMessages")?.length() ?: -1
                val historyFile = File(clearContext.filesDir, "powercal-chat-history.json")
                println("SEP09_CLEAR_CHAT_BEFORE_PERSIST hostProjectionCount=$hostProjectionCount " +
                    "historyFileExists=${historyFile.isFile} historyFileBytes=${historyFile.length()}")
                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(clearContext).size == nativeHistory.size
                }
                assertTrue("both Native and Clear local-provider requests must complete",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local paired chat server failed", serverFailure.get())
                assertEquals(2, requests.size)
                val requestTurns = requests.map { body ->
                    val messages = JSONObject(body).getJSONArray("messages")
                    (0 until messages.length()).mapNotNull { index ->
                        val message = messages.getJSONObject(index)
                        val role = message.optString("role")
                        if (role == "user" || role == "assistant")
                            role to message.optString("content") else null
                    }
                }
                assertEquals("Native and Clear must send equivalent chat turns",
                    listOf(listOf("user" to prompt), listOf("user" to prompt)), requestTurns)

                val clearHistory = AiChatHistoryStore.load(clearContext).map { it.role to it.content }
                assertEquals("Native and Clear must persist the same user/assistant history",
                    nativeHistory, clearHistory)
                val clearProjection = WebSkinBridgeRuntime.state().getJSONArray("chatMessages")
                val projectedHistory = (0 until clearProjection.length()).map { index ->
                    val message = clearProjection.getJSONObject(index)
                    message.getString("role") to message.getString("content")
                }
                assertEquals("Clear must render the same Host-projected history", nativeHistory,
                    projectedHistory)
                for (request in requests) {
                    assertTrue("each provider request must contain the same user prompt",
                        request.contains(prompt))
                    assertTrue("disabled context sharing must exclude the private event fixture",
                        !request.contains(privateSentinel))
                }
                assertEquals("Clear chat must not publish or stage calendar data", clearBefore,
                    storeSnapshot(clearStore))
                assertEquals("Native and Clear fixture Stores remain equivalent", nativeBefore,
                    storeSnapshot(clearStore))
                assertEquals("chat sends must preserve the same formal event in both Stores",
                    nativeStore.all().map { Triple(it.eventSeq, it.title, it.revision) },
                    clearStore.all().map { Triple(it.eventSeq, it.title, it.revision) })
                println("NATIVE_CLEAR_AI_CHAT_PARITY history=${nativeHistory.size} " +
                    "providerRequests=${requests.size} contextSharing=false privateSentinel=absent " +
                    "storeSnapshot=$nativeBefore trustedTouches=$aiTabTouch,$inputTouch,$sendTouch")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                    "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun clearChatMessageActionsAndHistorySurviveColdProcessRestart() {
        val args = InstrumentationRegistry.getArguments()
        val cycle = requireNotNull(args.getString("f07AiColdCycle")).toInt()
        assertTrue("F-07 cold restart cycle must be 0 or 1", cycle in 0..1)

        val storage = compose.activity.applicationContext
        val namespace = "f07-ai-chat-cold-restart"
        val root = File(storage.cacheDir, namespace)
        val database = "chat.db"
        val testPreferences = listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
            "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
        if (cycle == 0) {
            assertFalse("Refusing to overwrite an existing F-07 fixture", root.exists())
            assertFalse("Refusing to overwrite an existing F-07 database",
                storage.getDatabasePath("$namespace-$database").exists())
            testPreferences.forEach { name ->
                assertFalse("Refusing to overwrite F-07 preference $name",
                    File(storage.applicationInfo.dataDir,
                        "shared_prefs/$namespace-$name.xml").exists())
            }
        } else {
            assertTrue("The first F-07 cycle must leave its isolated fixture", root.isDirectory)
            assertTrue("The first F-07 cycle must leave its isolated Core Store",
                storage.getDatabasePath("$namespace-$database").isFile)
        }
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val historyFile = File(context.filesDir, "powercal-chat-history.json")
        val store = PowerStore(context, database)
        val replies = listOf(
            "F07 local stub reply one",
            "F07 local stub reply two",
            "F07 local stub reply after edit",
            "F07 local stub reply after retract"
        )
        val promptOne = "F07 first deterministic chat turn"
        val promptTwo = "F07 second deterministic chat turn"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = if (cycle == 0) 20_000 else 5_000
        }
        val served = CountDownLatch(if (cycle == 0) 4 else 1)
        var serverThread: Thread? = null
        var verified = false

        fun storeSnapshot() = listOf(
            store.all().size.toLong(), store.draftCount(), store.pendingCount().toLong(),
            store.commitCount(), store.identityCount(), store.nextSequencePreview()
        )

        try {
            val expectedHistory = listOf(
                "user" to promptOne,
                "assistant" to replies[2]
            )
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            if (cycle == 0) {
                assertFalse("The isolated chat history must begin empty", historyFile.exists())
                AiChatHistoryStore.save(context, emptyList())
            } else {
                assertEquals("The isolated selection must survive the cold process restart",
                    "powercal-clear-default", SkinPackageManager(context).currentId())
                assertEquals("Cold restart must retain only the final two turns",
                    expectedHistory, AiChatHistoryStore.load(context).map { it.role to it.content })
            }

            val endpoint = "http://127.0.0.1:${server.localPort}/v1"
            AiSettingsStore.save(context, AiSettings(
                endpoint = endpoint,
                model = "local-stub:free",
                apiKey = "F07_LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            ))
            val serverReplies = if (cycle == 0) replies else listOf("unexpected replay")
            serverThread = Thread {
                try {
                    repeat(serverReplies.size) { index ->
                        server.accept().use { socket ->
                            requests += readRequest(socket)
                            writeReply(socket, serverReplies[index])
                        }
                        served.countDown()
                    }
                } catch (error: java.net.SocketTimeoutException) {
                    if (cycle == 0) serverFailure.set(error)
                    served.countDown()
                } catch (error: Throwable) {
                    serverFailure.set(error)
                    while (served.count > 0) served.countDown()
                }
            }.apply { name = "f07-ai-cold-restart-stub"; isDaemon = true; start() }

            val before = storeSnapshot()
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                SkinPackageManager(context).select("powercal-clear-default")
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides context) {
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
            awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
            tapWebSelector(view, ".clear-tabbar [data-action=ai]")
            awaitJs(view, "!document.querySelector('#aiPage').hidden")

            if (cycle == 0) {
                fun sendTurn(prompt: String, reply: String) {
                    tapWebSelector(view, "#chatInput", requireTrustedClick = false)
                    InstrumentationRegistry.getInstrumentation().sendStringSync(prompt)
                    awaitJs(view, "document.querySelector('#chatInput')?.value===" +
                        "${JSONObject.quote(prompt)}")
                    tapWebSelector(view, "#chatSend")
                    awaitJs(view, "document.querySelector('#chatMessages')?.textContent.includes(" +
                        "${JSONObject.quote(reply)})")
                }

                sendTurn(promptOne, replies[0])
                sendTurn(promptTwo, replies[1])
                tapWebSelector(view,
                    "#chatMessages article.chat-bubble.user:nth-of-type(1) " +
                        "button[aria-label=\"编辑这条消息并移除后续对话\"]")
                awaitJs(view, "document.querySelector('#chatInput')?.value===" +
                    "${JSONObject.quote(promptOne)} && " +
                    "document.querySelectorAll('.chat-bubble').length===0")
                tapWebSelector(view, "#chatSend")
                awaitJs(view, "document.querySelector('#chatMessages')?.textContent.includes(" +
                    "${JSONObject.quote(replies[2])})")

                sendTurn(promptTwo, replies[3])
                tapWebSelector(view,
                    "#chatMessages article.chat-bubble.user:nth-of-type(3) " +
                        "button[aria-label=\"撤回这条消息及后续对话\"]")
                awaitJs(view, "document.querySelectorAll('.chat-bubble').length===2 && " +
                    "!document.querySelector('#chatMessages')?.textContent.includes(" +
                    "${JSONObject.quote(promptTwo)} )")
                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(context).map { it.role to it.content } == expectedHistory
                }
                assertTrue("The deterministic provider should receive all four intended turns",
                    served.await(20, TimeUnit.SECONDS))
                assertNull("F-07 local chat server failed", serverFailure.get())
                assertEquals(4, requests.size)
                val requestedTexts = requests.map { body ->
                    val messages = JSONObject(body).getJSONArray("messages")
                    (0 until messages.length()).mapNotNull { index ->
                        messages.getJSONObject(index).takeIf { it.optString("role") == "user" }
                            ?.optString("content")
                    }.lastOrNull()
                }
                assertEquals(listOf(promptOne, promptTwo, promptOne, promptTwo), requestedTexts)
                assertEquals("Chat, edit/resend, and retract must not write Core calendar data",
                    before, storeSnapshot())
                assertEquals(expectedHistory, AiChatHistoryStore.load(context)
                    .map { it.role to it.content })
                println("F07_CLEAR_CHAT_ACTIONS cycle=0 requests=${requests.size} " +
                    "historyMessages=${expectedHistory.size} coreStoreUnchanged=true")
            } else {
                awaitJs(view, "document.querySelector('#chatMessages')?.textContent.includes(" +
                    "${JSONObject.quote(replies[2])}) && " +
                    "document.querySelectorAll('.chat-bubble').length===2 && " +
                    "!document.querySelector('#chatMessages')?.textContent.includes(" +
                    "${JSONObject.quote(promptTwo)})")
                assertEquals("The reopened Host projection must match durable chat history",
                    expectedHistory, WebSkinBridgeRuntime.state().optJSONArray("chatMessages")!!
                        .let { items -> (0 until items.length()).map { index ->
                            items.getJSONObject(index).getString("role") to
                                items.getJSONObject(index).getString("content")
                        } })
                assertTrue("No provider request may be replayed on a cold start",
                    served.await(6, TimeUnit.SECONDS))
                assertTrue("Cold-start restoration must not replay chat", requests.isEmpty())
                assertEquals("Cold-start restoration must not mutate Core calendar data",
                    before, storeSnapshot())
                assertEquals(expectedHistory, AiChatHistoryStore.load(context)
                    .map { it.role to it.content })
                verified = true
                println("F07_CLEAR_CHAT_ACTIONS cycle=1 requests=0 " +
                    "historyMessages=${expectedHistory.size} coreStoreUnchanged=true")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            server.close()
            serverThread?.join(2_000)
            store.close()
            if (cycle == 1 && verified) {
                context.deleteDatabase(database)
                testPreferences.forEach { storage.deleteSharedPreferences("$namespace-$it") }
                val cacheRoot = storage.cacheDir.canonicalFile
                val fixtureRoot = root.canonicalFile
                check(fixtureRoot.parentFile == cacheRoot) {
                    "Refusing to remove an F-07 fixture outside the test cache"
                }
                check(root.deleteRecursively()) { "Could not remove the verified F-07 fixture" }
            }
        }
    }

    @Test
    fun nativeAndClearProviderFailureRequiresExplicitResendAndSanitizesHistory() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-ai-retry-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-ai-retry-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "ai-retry-native.db"
        val clearDatabase = "ai-retry-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val prompt = "summarize tomorrow's plan"
        val providerSecret = "PROVIDER_SECRET_ERROR_BODY_MUST_NOT_ESCAPE"
        val recoveredReply = "连接已恢复，我继续回答刚才的问题。"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val responseSent = CountDownLatch(4)
        val serverFailure = AtomicReference<Throwable?>()

        fun initialize(context: IsolatedAppContext, store: PowerStore) {
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())
            assertTrue("retry fixture Store must start empty", store.all().isEmpty())
        }

        fun storeSnapshot(store: PowerStore) = listOf(
            store.all().size.toLong(), store.draftCount(), store.pendingCount().toLong(),
            store.commitCount(), store.identityCount(), store.nextSequencePreview()
        )

        fun assertFailureIsSanitized(context: IsolatedAppContext, expectedRequests: Int) {
            compose.waitUntil(20_000) {
                requests.size >= expectedRequests && AiChatHistoryStore.load(context).any {
                    it.role == "assistant" && it.content.contains("HTTP 503")
                }
            }
            SystemClock.sleep(500)
            assertEquals("HTTP 503 must not trigger an automatic retry", expectedRequests,
                requests.size)
            assertFalse("provider error body must be absent from persisted history",
                AiChatHistoryStore.load(context).any { it.content.contains(providerSecret) })
        }

        try {
            initialize(nativeContext, nativeStore)
            initialize(clearContext, clearStore)
            val nativeBefore = storeSnapshot(nativeStore)
            val clearBefore = storeSnapshot(clearStore)

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 30_000
                val endpoint = "http://127.0.0.1:${server.localPort}/v1"
                listOf(nativeContext, clearContext).forEach { context ->
                    AiSettingsStore.save(context, AiSettings(
                        endpoint = endpoint,
                        model = "local-stub:free",
                        apiKey = "LOCAL_TEST_ONLY",
                        enabled = true,
                        shareContext = false
                    ))
                }
                val serverThread = Thread {
                    try {
                        repeat(4) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                if (index == 0 || index == 2) {
                                    writeFailure(socket, providerSecret)
                                } else {
                                    writeReply(socket, recoveredReply)
                                }
                            }
                            responseSent.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (responseSent.count > 0) responseSent.countDown()
                    }
                }.apply { name = "pc-native-clear-ai-error-resend"; isDaemon = true; start() }

                compose.runOnUiThread {
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides nativeContext) {
                            PowerCalTheme { PowerCalApp(nativeStore) }
                        }
                    }
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("AI 助手").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("AI 助手").performClick()
                compose.onNodeWithText("发送消息…").performTextInput(prompt)
                compose.onNodeWithText("发送").performClick()
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithText("HTTP 503", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                assertFailureIsSanitized(nativeContext, expectedRequests = 1)
                compose.onNodeWithText("AI 请求未完成", substring = true).assertExists()
                compose.onNodeWithText("发送消息…").performTextInput(prompt)
                compose.onNodeWithText("发送").performClick()
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithText(recoveredReply)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("only an explicit Native resend may issue the second request",
                    2, requests.size)
                val nativeHistory = AiChatHistoryStore.load(nativeContext)
                    .map { it.role to it.content }
                assertEquals(listOf("user", "assistant", "user", "assistant"),
                    nativeHistory.map { it.first })
                assertTrue(nativeHistory[1].second.contains("HTTP 503"))
                assertEquals(recoveredReply, nativeHistory[3].second)

                compose.runOnUiThread {
                    compose.activity.setContent { }
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(clearContext).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides clearContext) {
                            PowerCalTheme { PowerCalApp(clearStore) }
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                val aiTabTouch = tapWebSelector(view, ".clear-tabbar [data-action=ai]")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                val firstInputTouch = tapWebSelector(view, "#chatInput", requireTrustedClick = false)
                InstrumentationRegistry.getInstrumentation().sendStringSync(prompt)
                awaitJs(view, "document.querySelector('#chatInput')?.value===${JSONObject.quote(prompt)}")
                val firstSendTouch = tapWebSelector(view, "#chatSend")
                compose.waitUntil(20_000) {
                    requests.size >= 3 &&
                        !WebSkinBridgeRuntime.state().optBoolean("chatBusy") &&
                        evaluate(view, "document.querySelector('#chatMessages')?.textContent.includes('HTTP 503')") == "true"
                }
                assertFailureIsSanitized(clearContext, expectedRequests = 3)
                assertFalse("provider response body must not enter the Clear projection",
                    WebSkinBridgeRuntime.state().toString().contains(providerSecret))
                val secondInputTouch = tapWebSelector(view, "#chatInput", requireTrustedClick = false)
                InstrumentationRegistry.getInstrumentation().sendStringSync(prompt)
                awaitJs(view, "document.querySelector('#chatInput')?.value===${JSONObject.quote(prompt)}")
                val secondSendTouch = tapWebSelector(view, "#chatSend")
                awaitJs(view, "document.querySelector('#chatMessages')?.textContent.includes(" +
                    "${JSONObject.quote(recoveredReply)})")

                assertTrue("all four user-triggered provider responses must finish",
                    responseSent.await(2, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local provider server failed", serverFailure.get())
                assertEquals("Native and Clear each send once, then only after explicit resend", 4,
                    requests.size)
                compose.waitUntil(20_000) {
                    AiChatHistoryStore.load(clearContext).size == nativeHistory.size
                }
                val clearHistory = AiChatHistoryStore.load(clearContext).map { it.role to it.content }
                assertEquals("Native and Clear must persist the same failed and recovered turns",
                    nativeHistory, clearHistory)
                val projected = WebSkinBridgeRuntime.state().getJSONArray("chatMessages")
                val clearProjection = (0 until projected.length()).map { index ->
                    val message = projected.getJSONObject(index)
                    message.getString("role") to message.getString("content")
                }
                assertEquals(nativeHistory, clearProjection)
                assertTrue("the sanitized error must remain available for explicit retry context",
                    requests[1].contains("HTTP 503") && requests[3].contains("HTTP 503"))
                assertTrue("both retry requests must retain the original and resent prompt",
                    listOf(requests[1], requests[3]).all { body ->
                        body.windowed(prompt.length).count { it == prompt } >= 2
                    })
                assertFalse("provider error body must not leak into either retry request",
                    requests.toString().contains(providerSecret))
                assertFalse("provider error body must not leak into Clear's final history",
                    clearHistory.any { it.second.contains(providerSecret) })
                assertEquals("Native chat failure and resend must not write calendar data",
                    nativeBefore, storeSnapshot(nativeStore))
                assertEquals("Clear chat failure and resend must not write calendar data",
                    clearBefore, storeSnapshot(clearStore))
                assertEquals("Native and Clear stores must remain equivalent",
                    nativeBefore, clearBefore)
                println("NATIVE_CLEAR_AI_PROVIDER_FAILURE_RESEND " +
                    "http503=true automaticRetry=false explicitRequests=4 sanitizedBody=true " +
                    "historyParity=true storeWrite=false trustedTouches=" +
                    "$aiTabTouch,$firstInputTouch,$firstSendTouch,$secondInputTouch,$secondSendTouch")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                    "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun nativeAndClearImageUploadRequiresConsentAndNeverPersistsRawBytes() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-ai-image-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-ai-image-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "ai-image-native.db"
        val clearDatabase = "ai-image-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val imageBuffer = ByteArrayOutputStream()
        val imageBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(32, 96, 160))
        }
        try {
            assertTrue("test image must encode as PNG",
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, imageBuffer))
        } finally {
            imageBitmap.recycle()
        }
        val imageBytes = imageBuffer.toByteArray()
        val encodedImage = android.util.Base64.encodeToString(imageBytes,
            android.util.Base64.NO_WRAP)
        val imageName = "powercal-ai-image-${UUID.randomUUID()}.png"
        val imageValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/PowerCalTest/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var imageUri: android.net.Uri? = null
        val assistantReply = "测试图片已读取；没有生成日历操作。"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val requestReceived = CountDownLatch(2)
        val serverFailure = AtomicReference<Throwable?>()

        fun initialize(context: IsolatedAppContext, store: PowerStore) {
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())
            assertTrue("image fixture Stores must begin empty", store.all().isEmpty())
        }

        fun storeSnapshot(store: PowerStore): List<Long> = listOf(
            store.all().size.toLong(), store.draftCount(), store.pendingCount().toLong(),
            store.commitCount(), store.identityCount(), store.nextSequencePreview()
        )

        fun waitForImageConsent() {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("图片识别方式").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("the selected image must remain locked behind Host consent",
                WebSkinBridgeRuntime.state().optBoolean("chatAttachmentBusy"))
        }

        fun launchNativeImagePicker() {
            compose.onNodeWithContentDescription("添加图片或文件").performClick()
            compose.onNodeWithText("添加图片 · 智能识别").performClick()
            waitForSystemNode("com.google.android.providers.media.module",
                contentDescription = "More options")
        }

        fun launchClearImagePicker(web: WebView) {
            awaitJs(web, "document.querySelector('#aiPage') && !document.querySelector('#aiPage').hidden")
            tapWebSelector(web, ".chat-attachment-trigger")
            tapWebSelector(web,
                ".chat-attachment-controls button[aria-label='选择图片附件']")
            compose.waitUntil(15_000) {
                WebSkinBridgeRuntime.state().optBoolean("chatAttachmentBusy")
            }
            waitForSystemNode("com.google.android.providers.media.module",
                contentDescription = "More options")
        }

        fun cancelAndroidPicker() {
            tapSystemNode(waitForSystemNode("com.google.android.providers.media.module",
                contentDescription = "Cancel"))
            compose.waitUntil(15_000) {
                compose.activity.packageName ==
                    InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                        ?.packageName?.toString()
            }
        }

        fun cancelImageConsent() {
            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("图片识别方式").fetchSemanticsNodes().isEmpty()
            }
        }

        fun authorizeAndWaitForReply(context: IsolatedAppContext, store: PowerStore,
            clearWeb: WebView? = null): List<Pair<String, String>> {
            val before = storeSnapshot(store)
            val requestCountBefore = requests.size
            val expectedPendingRequests = 1L - requestCountBefore.toLong()
            compose.onNodeWithText("发送原图给 AI").performClick()
            compose.waitUntil(20_000) {
                serverFailure.get() != null ||
                    (requests.size == requestCountBefore + 1 &&
                        AiChatHistoryStore.load(context).any { it.content == assistantReply })
            }
            assertNull("local image provider failed", serverFailure.get())
            compose.waitUntil(20_000) {
                requestReceived.count == expectedPendingRequests
            }
            assertEquals("image analysis must not write formal calendar data", before,
                storeSnapshot(store))
            if (clearWeb != null) {
                compose.waitUntil(10_000) {
                    !WebSkinBridgeRuntime.state().optBoolean("chatBusy")
                }
                val bridge = WebSkinBridgeRuntime.state().toString()
                assertFalse("the WebView projection must not contain raw image bytes",
                    bridge.contains(encodedImage))
                assertFalse("the WebView projection must not reveal the AI key",
                    bridge.contains("LOCAL_TEST_ONLY"))
            }
            val history = AiChatHistoryStore.load(context)
            assertTrue("accepted image must leave an explicit consent marker", history.any {
                it.role == "user" && it.content.contains("已授权 AI 读取图片")
            })
            assertTrue("raw image bytes must never enter chat history",
                history.all { !it.content.contains(encodedImage) })
            val historyFile = File(context.filesDir, "powercal-chat-history.json")
            compose.waitUntil(10_000) {
                historyFile.isFile && !historyFile.readText(Charsets.UTF_8).contains(encodedImage)
            }
            assertEquals("Native and Clear must append the same image consent and reply",
                listOf("user" to "已授权 AI 读取图片（原始图片不写入聊天历史）",
                    "assistant" to assistantReply),
                history.map { it.role to it.content })
            return history.map { it.role to it.content }
        }

        try {
            initialize(nativeContext, nativeStore)
            initialize(clearContext, clearStore)
            val nativeBefore = storeSnapshot(nativeStore)
            val clearBefore = storeSnapshot(clearStore)

            imageUri = requireNotNull(storage.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues
            )) { "Could not create isolated image fixture in Pictures" }
            storage.contentResolver.openOutputStream(imageUri!!)?.use { it.write(imageBytes) }
                ?: error("Could not write isolated image fixture")
            val publishedFixture = storage.contentResolver.update(imageUri!!,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            assertEquals("Photo Picker fixture must become visible", 1, publishedFixture)
            val fixtureName = requireNotNull(storage.contentResolver.query(
                imageUri!!, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
            )).use { cursor ->
                assertTrue("MediaStore must return the isolated image row", cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals("MediaStore must preserve the unique fixture filename",
                imageName, fixtureName)

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 30_000
                val endpoint = "http://127.0.0.1:${server.localPort}/v1"
                AiSettingsStore.save(nativeContext, AiSettings(endpoint = endpoint,
                    model = "local-stub:free", apiKey = "LOCAL_TEST_ONLY",
                    enabled = true, shareContext = false))
                AiSettingsStore.save(clearContext, AiSettings(endpoint = endpoint,
                    model = "local-stub:free", apiKey = "LOCAL_TEST_ONLY",
                    enabled = true, shareContext = false))
                val serverThread = Thread {
                    try {
                        repeat(2) {
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, assistantReply)
                            }
                            requestReceived.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (requestReceived.count > 0) requestReceived.countDown()
                    }
                }.apply { name = "pc-native-clear-ai-image-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides nativeContext) {
                            PowerCalTheme { PowerCalApp(nativeStore) }
                        }
                    }
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("AI 助手").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("AI 助手").performClick()
                launchNativeImagePicker()
                cancelAndroidPicker()
                assertEquals("canceling the Android picker must not send or write",
                    nativeBefore, storeSnapshot(nativeStore))
                assertTrue("canceling the picker must leave chat history empty",
                    AiChatHistoryStore.load(nativeContext).isEmpty())

                launchNativeImagePicker()
                selectImageFromAndroidPicker(imageName)
                waitForImageConsent()
                assertTrue("Native image selection must remain read-only before consent",
                    nativeBefore == storeSnapshot(nativeStore))
                assertFalse("Provider must not receive the image before the user approves",
                    requestReceived.await(300, TimeUnit.MILLISECONDS))
                cancelImageConsent()
                assertTrue("canceling image consent must not call the provider",
                    requestReceived.count == 2L && requests.isEmpty())
                assertEquals("canceling image consent must not change calendar data",
                    nativeBefore, storeSnapshot(nativeStore))
                assertTrue("canceling image consent must not persist image history",
                    AiChatHistoryStore.load(nativeContext).isEmpty())

                launchNativeImagePicker()
                selectImageFromAndroidPicker(imageName)
                waitForImageConsent()
                val nativeHistory = authorizeAndWaitForReply(nativeContext, nativeStore)
                val nativeRequest = JSONObject(requests.single())
                val nativeMessages = nativeRequest.getJSONArray("messages")
                val nativeUser = (0 until nativeMessages.length()).map {
                    nativeMessages.getJSONObject(it)
                }.last { it.getString("role") == "user" }
                val nativeParts = nativeUser.getJSONArray("content")
                val nativeImage = (0 until nativeParts.length()).map {
                    nativeParts.getJSONObject(it)
                }.first { it.optString("type") == "image_url" }
                assertEquals("Native consent must send the selected image bytes to the local stub",
                    "data:image/png;base64,$encodedImage",
                    nativeImage.getJSONObject("image_url").getString("url"))

                compose.runOnUiThread {
                    compose.activity.setContent { }
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(clearContext).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides clearContext) {
                            PowerCalTheme { PowerCalApp(clearStore) }
                        }
                    }
                }
                var clearWeb: WebView? = null
                compose.waitUntil(30_000) {
                    compose.runOnUiThread {
                        clearWeb = findWebView(compose.activity.findViewById(android.R.id.content))
                    }
                    clearWeb != null
                }
                val web = requireNotNull(clearWeb)
                awaitJs(web, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                tapWebSelector(web, ".clear-tabbar [data-action=ai]")
                awaitJs(web,
                    "document.querySelector('#aiPage') && !document.querySelector('#aiPage').hidden")
                awaitJs(web, "!!document.querySelector('#chatAttachmentControls')")
                val clearBefore = storeSnapshot(clearStore)
                launchClearImagePicker(web)
                cancelAndroidPicker()
                compose.waitUntil(10_000) {
                    !WebSkinBridgeRuntime.state().optBoolean("chatAttachmentBusy")
                }
                assertTrue("Clear picker cancel must not write Store data",
                    clearBefore == storeSnapshot(clearStore))
                assertTrue("Clear picker cancel must not write chat history",
                    AiChatHistoryStore.load(clearContext).isEmpty())

                launchClearImagePicker(web)
                selectImageFromAndroidPicker(imageName)
                waitForImageConsent()
                assertEquals("Clear image selection must remain read-only before consent",
                    clearBefore, storeSnapshot(clearStore))
                assertFalse("Clear provider must not receive image before approval",
                    requestReceived.await(300, TimeUnit.MILLISECONDS))
                cancelImageConsent()
                assertTrue("Clear consent cancel must not call provider",
                    requestReceived.count == 1L)
                assertEquals("Clear consent cancel must not write calendar data",
                    clearBefore, storeSnapshot(clearStore))
                assertTrue("Clear consent cancel must not persist image history",
                    AiChatHistoryStore.load(clearContext).isEmpty())

                launchClearImagePicker(web)
                selectImageFromAndroidPicker(imageName)
                waitForImageConsent()
                val clearHistory = authorizeAndWaitForReply(clearContext, clearStore, web)
                assertEquals("Native and Clear must persist the same image interaction history",
                    nativeHistory, clearHistory)
                val clearProjection = WebSkinBridgeRuntime.state().getJSONArray("chatMessages")
                val projectedHistory = (0 until clearProjection.length()).map { index ->
                    val item = clearProjection.getJSONObject(index)
                    item.getString("role") to item.getString("content")
                }
                assertEquals("Clear must render Host-projected image history", nativeHistory,
                    projectedHistory)
                assertEquals("one local vision request must be sent per explicitly approved image",
                    2, requests.size)
                requests.forEach { body ->
                    val request = JSONObject(body)
                    val messages = request.getJSONArray("messages")
                    val user = (0 until messages.length()).map { messages.getJSONObject(it) }
                        .last { it.getString("role") == "user" }
                    val image = (0 until user.getJSONArray("content").length()).map {
                        user.getJSONArray("content").getJSONObject(it)
                    }.first { it.optString("type") == "image_url" }
                    assertEquals("both skins must send the same selected image bytes",
                        "data:image/png;base64,$encodedImage",
                        image.getJSONObject("image_url").getString("url"))
                }
                assertNull("local image provider must complete both requests", serverFailure.get())
                assertEquals("Native and Clear must leave their isolated Stores unchanged",
                    nativeBefore, storeSnapshot(nativeStore))
                assertEquals("Clear must have no formal or draft writes", clearBefore,
                    storeSnapshot(clearStore))
                assertEquals("Native/Clear image history and request count match",
                    nativeHistory, clearHistory)
                serverThread.join(2_000)
                println("NATIVE_CLEAR_AI_IMAGE_PARITY picker=Android15_PhotoPicker " +
                    "consentRequired=true providerRequests=${requests.size} imageBytesPersisted=false " +
                    "storeSnapshots=$nativeBefore/$clearBefore")
            }
        } finally {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            repeat(3) {
                val packageName = automation.rootInActiveWindow?.packageName?.toString()
                if (packageName == "com.google.android.documentsui" ||
                    packageName == "com.google.android.providers.media.module") {
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(
                        android.view.KeyEvent.KEYCODE_BACK)
                }
            }
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore.close()
            clearStore.close()
            storage.deleteDatabase("$nativeNamespace-$nativeDatabase")
            storage.deleteDatabase("$clearNamespace-$clearDatabase")
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                    "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
            imageUri?.let { storage.contentResolver.delete(it, null, null) }
        }
    }

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = runCatching {
            evaluate(web, "JSON.stringify({page:document.querySelector('.page:not([hidden])')?.id," +
                "items:document.querySelectorAll('.ai-proposal-item').length," +
                "chat:document.querySelector('#chatMessages')?.textContent?.slice(-500)})")
        }.getOrDefault("unavailable")
        throw AssertionError("WebView condition timed out: $expression; state=$diagnostic")
    }

    @Test
    fun actualClearWebViewPublishesReplaceAndDeleteOnlyAfterHostConfirmation() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "review.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val requestPrefix = "帮我创建一个正式事项处理提案，2026-12-01："

        try {
            store.readableDatabase
            val replaceOutcome = store.createFormalEvent(
                DraftForm(kind = TemporalKind.MEMO, title = "原会议标题", zoneId = zone.id),
                idempotencyKey = "seed-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val deleteOutcome = store.createFormalEvent(
                DraftForm(kind = TemporalKind.MEMO, title = "过期提醒", zoneId = zone.id),
                idempotencyKey = "seed-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val originalReplace = requireNotNull(store.get(replaceOutcome.eventSeq))
            val originalDelete = requireNotNull(store.get(deleteOutcome.eventSeq))
            val userText = "$requestPrefix 把备忘 #${replaceOutcome.eventSeq} " +
                "标题改成项目回顾，并删除备忘 #${deleteOutcome.eventSeq}。"
            val structured = JSONObject().put("events", JSONArray()
                .put(JSONObject()
                    .put("operation", "REPLACE")
                    .put("eventSeq", replaceOutcome.eventSeq)
                    .put("kind", "MEMO")
                    .put("title", "项目回顾"))
                .put(JSONObject()
                    .put("operation", "DELETE")
                    .put("eventSeq", deleteOutcome.eventSeq)
                    .put("kind", "MEMO")
                    .put("title", originalDelete.title))).toString()
            val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
            val serverFailure = AtomicReference<Throwable?>()
            val served = CountDownLatch(2)
            val commitsBeforeReview = store.commitCount()
            val identitiesBeforeReview = store.identityCount()
            val draftsBeforeReview = store.draftCount()
            val pendingBeforeReview = store.pendingCount()

            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = true
                ))
                val responses = listOf(
                    "我会生成两项待审核操作。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    structured
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-review-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "input.value=${JSONObject.quote(userText)};" +
                    "document.querySelector('#chatForm').requestSubmit();return true})()")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===2")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-title')).some(e=>e.textContent.includes('REPLACE')) &&" +
                    "Array.from(document.querySelectorAll('.ai-proposal-title')).some(e=>e.textContent.includes('DELETE'))")
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertTrue("both requests must reach the device-local stub",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(2, requests.size)
                assertEquals(2, store.all().size)
                assertEquals(commitsBeforeReview, store.commitCount())
                assertEquals(identitiesBeforeReview, store.identityCount())
                assertEquals(draftsBeforeReview, store.draftCount())
                assertEquals(pendingBeforeReview, store.pendingCount())

                val titles = evaluate(view,
                    "Array.from(document.querySelectorAll('.ai-proposal-title')).map(e=>e.textContent).join('\\n')")
                assertTrue("skin must show the target identity/revision in the review: $titles",
                    titles.contains("#${replaceOutcome.eventSeq} · 版本 ${originalReplace.revision}"))
                assertTrue("skin must show the deletion target snapshot: $titles",
                    titles.contains("#${deleteOutcome.eventSeq} · 版本 ${originalDelete.revision}"))

                evaluate(view, "document.querySelector('.ai-proposal-group .ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 2 项").assertExists()
                assertEquals("review is read-only before native Host confirmation",
                    commitsBeforeReview, store.commitCount())
                assertEquals(originalReplace, store.get(replaceOutcome.eventSeq))
                assertEquals(originalDelete, store.get(deleteOutcome.eventSeq))

                compose.onNodeWithText("确认并提交 2 项").performClick()
                compose.waitUntil(30_000) {
                    store.get(replaceOutcome.eventSeq)?.title == "项目回顾" &&
                        store.get(deleteOutcome.eventSeq) == null
                }
                assertEquals(originalReplace.eventSeq, store.get(replaceOutcome.eventSeq)?.eventSeq)
                assertEquals(originalReplace.revision + 1, store.get(replaceOutcome.eventSeq)?.revision)
                assertEquals(1, store.all().size)
                assertEquals(0, store.pendingCount())
                assertEquals(commitsBeforeReview + 2, store.commitCount())
                assertEquals(identitiesBeforeReview, store.identityCount())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name ->
                storage.deleteSharedPreferences("$namespace-$name")
            }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun actualClearWebViewStagesRejectsAndSupplementsThroughHostWithoutUnapprovedWrites() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-decisions-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "decisions.db"
        val store = PowerStore(context, database)
        val beforeDrafts = store.draftCount()
        val beforeIdentities = store.identityCount()
        val beforeCommits = store.commitCount()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(6)
        val firstPrompt = "创建两条独立备忘：第一条和第二条。"
        val initialProposals = JSONObject().put("events", JSONArray()
            .put(JSONObject().put("operation", "CREATE").put("kind", "MEMO")
                .put("title", "第一条备忘"))
            .put(JSONObject().put("operation", "CREATE").put("kind", "MEMO")
                .put("title", "第二条备忘"))).toString()
        val firstSupplementPrompt = "帮我创建一个活动，2026-12-01 19:00 开始，标题是接口评审。"
        val completedActivity = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE").put("kind", "ACTIVITY")
            .put("title", "接口评审")
            .put("start", "2026-12-01 19:00")
            .put("end", "2026-12-01 20:00"))).toString()

        try {
            store.readableDatabase
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = false
                ))
                val responses = listOf(
                    "我会生成两项待核对提案。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    initialProposals,
                    "活动还缺少结束时间，请补充几点结束。",
                    JSONObject().put("events", JSONArray()).toString(),
                    "收到补充信息，我会重新核对候选。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    completedActivity
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-decisions-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")

                fun sendChat(text: String) {
                    val sent = evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                        "if(!input)return false;input.value=${JSONObject.quote(text)};" +
                        "document.querySelector('#chatForm').requestSubmit();return true})()")
                    assertEquals("true", sent)
                }

                fun clickReviewAction(label: String) {
                    val clicked = evaluate(view, "(()=>{const button=Array.from(" +
                        "document.querySelectorAll('.ai-proposal-actions button')).find(" +
                        "item=>item.textContent.trim()===${JSONObject.quote(label)});" +
                        "if(!button||button.disabled)return false;button.click();return true})()")
                    assertEquals("button '$label' must be enabled in Clear", "true", clicked)
                }

                sendChat(firstPrompt)
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===2")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='写入草稿'&&!button.disabled)")
                assertNull("local AI stub failed before partial stage: ${serverFailure.get()}",
                    serverFailure.get())
                assertEquals("review must not write before a decision", beforeDrafts, store.draftCount())
                assertEquals(beforeIdentities, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                val deselected = evaluate(view, "(()=>{const items=" +
                    "Array.from(document.querySelectorAll('.ai-proposal-item input[type=checkbox]'));" +
                    "if(items.length!==2)return false;if(items[1].checked)items[1].click();" +
                    "return !Array.from(document.querySelectorAll(" +
                    "'.ai-proposal-item input[type=checkbox]'))[1].checked})()")
                assertEquals("the second independent candidate must be deselected before staging", "true", deselected)
                clickReviewAction("写入草稿")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-title')?.textContent.includes('第二条备忘')")
                assertEquals(1L, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                clickReviewAction("批量拒绝")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
                assertEquals("reject must leave the staged draft untouched", 1L, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                sendChat(firstSupplementPrompt)
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='补充细节'&&!button.disabled)")
                val beforeSupplement = store.draftCount()
                clickReviewAction("补充细节")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('#chatMessages').textContent.includes('请在下方聊天框补充')")
                assertEquals("supplement choice is review-only", beforeSupplement, store.draftCount())
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                sendChat("请在 20:00 结束。")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-item-body')?.textContent.includes('2026-12-01 20:00')")
                assertTrue("all six local AI calls must finish", served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(6, requests.size)
                assertTrue("follow-up interpretation must include both user turns",
                    requests.last().contains(firstSupplementPrompt) &&
                        requests.last().contains("请在 20:00 结束。"))
                assertEquals("supplemented candidate remains a proposal until an explicit decision",
                    beforeSupplement, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())

                clickReviewAction("批量拒绝")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
                assertEquals("final rejection must preserve only the explicitly staged draft",
                    1L, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(0, store.all().size)
                assertEquals(beforeIdentities + 1, store.identityCount())
                assertEquals(beforeCommits, store.commitCount())
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun nativeAndClearFormalReplaceDeleteProposalsMatchAfterScopedApproval() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-ai-replace-delete-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-ai-replace-delete-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "ai-replace-delete-native.db"
        val clearDatabase = "ai-replace-delete-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val zone = ZoneId.systemDefault()
        val promptPrefix = "请创建一份正式备忘操作提案："
        val marker = "我会准备两项待审核操作。" +
            "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(4)

        data class SeededStore(
            val replaceSeq: Long,
            val deleteSeq: Long,
            val originalReplace: dev.powerfulcalendar.core.TemporalRecord,
            val originalDelete: dev.powerfulcalendar.core.TemporalRecord
        )

        fun seed(context: IsolatedAppContext, store: PowerStore): SeededStore {
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())
            val replace = store.createFormalEvent(
                DraftForm(kind = TemporalKind.MEMO, title = "原会议标题", zoneId = zone.id),
                idempotencyKey = "replace-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val delete = store.createFormalEvent(
                DraftForm(kind = TemporalKind.MEMO, title = "过期提醒", zoneId = zone.id),
                idempotencyKey = "delete-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            return SeededStore(
                replace.eventSeq,
                delete.eventSeq,
                requireNotNull(store.get(replace.eventSeq)),
                requireNotNull(store.get(delete.eventSeq))
            )
        }

        fun storeSnapshot(store: PowerStore) = listOf(
            store.all().size.toLong(), store.draftCount(), store.pendingCount().toLong(),
            store.commitCount(), store.identityCount(), store.nextSequencePreview()
        )

        fun formalSnapshot(store: PowerStore) = store.all().map {
            "${it.eventSeq}|${it.kind}|${it.title}|${it.revision}|${it.startMillis}|${it.endMillis}"
        }

        fun proposalFor(seeded: SeededStore) = JSONObject().put("events", JSONArray()
            .put(JSONObject()
                .put("operation", "REPLACE")
                .put("eventSeq", seeded.replaceSeq)
                .put("kind", "MEMO")
                .put("title", "项目回顾"))
            .put(JSONObject()
                .put("operation", "DELETE")
                .put("eventSeq", seeded.deleteSeq)
                .put("kind", "MEMO")
                .put("title", seeded.originalDelete.title))).toString()

        fun lastUserMessage(body: String): String? {
            val messages = JSONObject(body).getJSONArray("messages")
            for (index in messages.length() - 1 downTo 0) {
                val message = messages.getJSONObject(index)
                if (message.optString("role") == "user") return message.optString("content")
            }
            return null
        }

        try {
            val nativeSeed = seed(nativeContext, nativeStore)
            val clearSeed = seed(clearContext, clearStore)
            assertEquals("isolated Native and Clear Stores assign matching target identities",
                listOf(nativeSeed.replaceSeq, nativeSeed.deleteSeq),
                listOf(clearSeed.replaceSeq, clearSeed.deleteSeq))
            assertEquals(DraftForm.from(nativeSeed.originalReplace),
                DraftForm.from(clearSeed.originalReplace))
            assertEquals(DraftForm.from(nativeSeed.originalDelete),
                DraftForm.from(clearSeed.originalDelete))
            val nativeBefore = storeSnapshot(nativeStore)
            val clearBefore = storeSnapshot(clearStore)
            val nativeFormalBefore = formalSnapshot(nativeStore)
            val clearFormalBefore = formalSnapshot(clearStore)
            val nativePrompt = "$promptPrefix #${nativeSeed.replaceSeq} 改成“项目回顾”，" +
                "并删除 #${nativeSeed.deleteSeq}。"
            val clearPrompt = "$promptPrefix #${clearSeed.replaceSeq} 改成“项目回顾”，" +
                "并删除 #${clearSeed.deleteSeq}。"
            val structuredNative = proposalFor(nativeSeed)
            val structuredClear = proposalFor(clearSeed)

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 30_000
                val endpoint = "http://127.0.0.1:${server.localPort}/v1"
                listOf(nativeContext, clearContext).forEach { context ->
                    AiSettingsStore.save(context, AiSettings(
                        endpoint = endpoint,
                        model = "local-stub:free",
                        apiKey = "LOCAL_TEST_ONLY",
                        enabled = true,
                        shareContext = true
                    ))
                }
                val responses = listOf(marker, structuredNative, marker, structuredClear)
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 20_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-native-clear-replace-delete"; isDaemon = true; start() }

                compose.runOnUiThread {
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides nativeContext) {
                            PowerCalTheme { PowerCalApp(nativeStore) }
                        }
                    }
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("AI 助手").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("AI 助手").performClick()
                compose.onNodeWithText("发送消息…").performTextInput(nativePrompt)
                compose.onNodeWithText("发送").performClick()
                compose.waitUntil(30_000) {
                    requests.size >= 2 || serverFailure.get() != null
                }
                assertNull("local Native proposal provider failed", serverFailure.get())
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText(
                        "识别到修改 / 删除 · 是否创建修改提案？"
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(nativeContext).size >= 2
                }
                val nativeHistoryBeforeApproval = AiChatHistoryStore.load(nativeContext)
                    .take(2).map { it.role to it.content }
                assertEquals("Native review remains read-only until the owner approves",
                    nativeBefore, storeSnapshot(nativeStore))
                assertEquals(nativeFormalBefore, formalSnapshot(nativeStore))
                compose.onNodeWithText("批量同意").performClick()
                compose.waitUntil(30_000) {
                    nativeStore.get(nativeSeed.replaceSeq)?.title == "项目回顾" &&
                        nativeStore.get(nativeSeed.deleteSeq) == null
                }
                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(nativeContext).any {
                        it.role == "assistant" && it.content.contains("结构化操作结果")
                    }
                }

                compose.runOnUiThread {
                    compose.activity.setContent { }
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(clearContext).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides clearContext) {
                            PowerCalTheme { PowerCalApp(clearStore) }
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                val aiTouch = tapWebSelector(view, ".clear-tabbar [data-action=ai]")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                val composerValue = evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "if(!input)return false;input.value=${JSONObject.quote(clearPrompt)};" +
                    "input.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "return input.value===${JSONObject.quote(clearPrompt)}})()")
                assertEquals("Clear must retain the exact Native request text", "true", composerValue)
                awaitJs(view, "document.querySelector('#chatInput')?.value===${JSONObject.quote(clearPrompt)}")
                val sendTouch = tapWebSelector(view, "#chatSend")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===2")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-title')).some(e=>e.textContent.includes('REPLACE')) &&" +
                    "Array.from(document.querySelectorAll('.ai-proposal-title')).some(e=>e.textContent.includes('DELETE'))")
                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(clearContext).size >= nativeHistoryBeforeApproval.size
                }
                val clearHistoryBeforeApproval = AiChatHistoryStore.load(clearContext)
                    .take(2).map { it.role to it.content }
                assertEquals("Native and Clear chat history must match before decision",
                    nativeHistoryBeforeApproval, clearHistoryBeforeApproval)
                assertEquals("Clear review must not write before Host confirmation",
                    clearBefore, storeSnapshot(clearStore))
                assertEquals(clearFormalBefore, formalSnapshot(clearStore))
                val projection = WebSkinBridgeRuntime.state().getJSONObject("aiProposalReview")
                    .getJSONArray("proposals")
                assertEquals(2, projection.length())
                assertEquals("REPLACE", projection.getJSONObject(0).getString("operation"))
                assertEquals(nativeSeed.replaceSeq,
                    projection.getJSONObject(0).getLong("eventSeq"))
                assertEquals(nativeSeed.originalReplace.revision,
                    projection.getJSONObject(0).getLong("targetRevision"))
                assertEquals("DELETE", projection.getJSONObject(1).getString("operation"))
                assertEquals(nativeSeed.deleteSeq,
                    projection.getJSONObject(1).getLong("eventSeq"))
                assertEquals(nativeSeed.originalDelete.revision,
                    projection.getJSONObject(1).getLong("targetRevision"))

                tapWebSelector(view, ".ai-proposal-group .ai-proposal-actions button.primary")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 2 项").assertExists()
                assertEquals("the skin request must remain read-only until Host approval",
                    clearBefore, storeSnapshot(clearStore))
                assertEquals(clearFormalBefore, formalSnapshot(clearStore))
                compose.onNodeWithText("确认并提交 2 项").performClick()
                compose.waitUntil(30_000) {
                    clearStore.get(clearSeed.replaceSeq)?.title == "项目回顾" &&
                        clearStore.get(clearSeed.deleteSeq) == null
                }
                assertTrue("all paired chat and interpretation calls must reach the stub",
                    served.await(2, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local pair provider failed", serverFailure.get())
                assertEquals(4, requests.size)
                assertEquals(nativePrompt, lastUserMessage(requests[0]))
                assertEquals(clearPrompt, lastUserMessage(requests[2]))
                assertEquals("clear and native requests are scoped to equivalent targets",
                    nativePrompt.replace("#${nativeSeed.replaceSeq}", "#EVENT_SEQ")
                        .replace("#${nativeSeed.deleteSeq}", "#EVENT_SEQ"),
                    clearPrompt.replace("#${clearSeed.replaceSeq}", "#EVENT_SEQ")
                        .replace("#${clearSeed.deleteSeq}", "#EVENT_SEQ"))
                assertEquals("REPLACE must keep the same identity and advance one revision",
                    nativeSeed.originalReplace.eventSeq,
                    nativeStore.get(nativeSeed.replaceSeq)?.eventSeq)
                assertEquals(nativeSeed.originalReplace.revision + 1,
                    nativeStore.get(nativeSeed.replaceSeq)?.revision)
                val nativeFinalRecords = nativeStore.all().map {
                    Triple(it.eventSeq, it.revision, DraftForm.from(it))
                }
                val clearFinalRecords = clearStore.all().map {
                    Triple(it.eventSeq, it.revision, DraftForm.from(it))
                }
                assertEquals("all persisted business fields, identities and revisions match",
                    nativeFinalRecords, clearFinalRecords)
                assertEquals("only the Replace target should remain", 1, nativeStore.all().size)
                val nativeAfter = storeSnapshot(nativeStore)
                val clearAfter = storeSnapshot(clearStore)
                assertEquals("the two approved operations leave exactly two resolved proposal records",
                    nativeBefore[1] + 2L, nativeAfter[1])
                assertEquals("Clear must retain the same resolved proposal history",
                    clearBefore[1] + 2L, clearAfter[1])
                assertEquals(0, nativeAfter[2])
                assertEquals(0, clearAfter[2])
                assertEquals(nativeBefore[3] + 2, nativeAfter[3])
                assertEquals(clearBefore[3] + 2, clearAfter[3])
                assertEquals(nativeBefore[4], nativeAfter[4])
                assertEquals(clearBefore[4], clearAfter[4])
                assertEquals(nativeBefore[5], nativeAfter[5])
                assertEquals(clearBefore[5], clearAfter[5])
                println("NATIVE_CLEAR_AI_REPLACE_DELETE_PARITY " +
                    "reviewZeroWrite=true nativeApproval=true clearHostApproval=true " +
                    "replaceRevisionAdvanced=true deleteTargetOnly=true formalParity=true " +
                    "storeWritesPerSide=2 trustedClearTouches=$aiTouch,$sendTouch")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                    "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun nativeAndClearProposalReviewStageSameCreateWithEquivalentDraftAndNoFormalWrites() {
        val storage: Context = compose.activity.applicationContext
        val nativeNamespace = "pc-ai-review-stage-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-ai-review-stage-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "ai-review-stage-native.db"
        val clearDatabase = "ai-review-stage-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val title = "AI parity candidate"
        val prompt = "请新建活动：2026-12-01 19:00 开始，20:00 结束，标题是$title。"
        val privateSentinel = "AI_REVIEW_PRIVATE_CONTEXT_SENTINEL"
        val proposal = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE")
            .put("kind", "ACTIVITY")
            .put("title", title)
            .put("start", "2026-12-01 19:00")
            .put("end", "2026-12-01 20:00"))).toString()
        val marker = "我会准备一项待核对提案。" +
            "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(4)

        fun initialize(context: IsolatedAppContext, store: PowerStore) {
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())
            val fixture = store.newDraft(DraftForm(kind = TemporalKind.MEMO,
                title = privateSentinel, zoneId = ZoneId.systemDefault().id))
            assertTrue(store.commit(fixture.draftId) is PowerStore.CommitOutcome.Committed)
        }

        fun storeSnapshot(store: PowerStore): List<Long> = listOf(
            store.draftCount(), store.pendingCount().toLong(), store.identityCount(),
            store.commitCount(), store.nextSequencePreview()
        )

        fun formalSnapshot(store: PowerStore): List<String> = store.all().map {
            "${it.eventSeq}|${it.kind}|${it.title}|${it.revision}|${it.startMillis}|${it.endMillis}"
        }

        fun lastUserMessage(body: String): String? {
            val messages = JSONObject(body).getJSONArray("messages")
            for (index in messages.length() - 1 downTo 0) {
                val message = messages.getJSONObject(index)
                if (message.optString("role") == "user") return message.optString("content")
            }
            return null
        }

        try {
            initialize(nativeContext, nativeStore)
            initialize(clearContext, clearStore)
            val nativeBefore = storeSnapshot(nativeStore)
            val clearBefore = storeSnapshot(clearStore)
            val nativeFormalBefore = formalSnapshot(nativeStore)
            val clearFormalBefore = formalSnapshot(clearStore)
            assertEquals(nativeBefore, clearBefore)
            assertEquals(nativeFormalBefore, clearFormalBefore)

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 30_000
                val endpoint = "http://127.0.0.1:${server.localPort}/v1"
                listOf(nativeContext, clearContext).forEach { context ->
                    AiSettingsStore.save(context, AiSettings(endpoint = endpoint,
                        model = "local-stub:free", apiKey = "LOCAL_TEST_ONLY",
                        enabled = true, shareContext = false))
                }
                val serverThread = Thread {
                    try {
                        repeat(4) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 20_000
                                requests += readRequest(socket)
                                writeReply(socket, if (index % 2 == 0) marker else proposal)
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-native-clear-review-stage"; isDaemon = true; start() }

                compose.runOnUiThread {
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides nativeContext) {
                            PowerCalTheme { PowerCalApp(nativeStore) }
                        }
                    }
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("AI 助手").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("AI 助手").performClick()
                compose.onNodeWithText("发送消息…").performTextInput(prompt)
                compose.onNodeWithText("发送").performClick()
                compose.waitUntil(30_000) {
                    serverFailure.get() != null || requests.size >= 2 &&
                        compose.onAllNodesWithText("$title · 活动", substring = true)
                            .fetchSemanticsNodes().isNotEmpty()
                }
                assertNull("local Native proposal provider failed", serverFailure.get())
                compose.onNodeWithText("识别到新增 · 是否创建草稿？").assertExists()
                compose.onNodeWithText("写入草稿").assertExists()
                compose.waitUntil(30_000) { AiChatHistoryStore.load(nativeContext).size == 2 }
                val nativeHistoryBeforeDecision = AiChatHistoryStore.load(nativeContext)
                    .map { it.role to it.content }
                assertEquals("Native proposal review is read-only before STAGE",
                    nativeBefore, storeSnapshot(nativeStore))
                compose.onNodeWithText("写入草稿").performClick()
                compose.waitUntil(30_000) { nativeStore.pendingCount() == 1 }
                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(nativeContext).any {
                        it.content.contains("结构化操作结果")
                    }
                }
                val nativeDraft = nativeStore.pendingDrafts().single()
                val nativeStageResult = AiChatHistoryStore.load(nativeContext)
                    .last { it.role == "assistant" }
                assertEquals(nativeFormalBefore, formalSnapshot(nativeStore))

                compose.runOnUiThread {
                    compose.activity.setContent { }
                    PowerCalSkinRegistry.reset()
                    SkinPackageManager(clearContext).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides clearContext) {
                            PowerCalTheme { PowerCalApp(clearStore) }
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                tapWebSelector(view, ".clear-tabbar [data-action=ai]")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                val composerValue = evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "if(!input)return false;input.value=${JSONObject.quote(prompt)};" +
                    "input.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "return input.value===${JSONObject.quote(prompt)}})()")
                assertEquals("Clear composer must retain the exact paired user prompt", "true", composerValue)
                awaitJs(view, "document.querySelector('#chatInput')?.value===${JSONObject.quote(prompt)}")
                // The chat command itself uses a trusted Android touch; SEP-09 separately proves
                // Android keyboard text entry into this same composer.
                tapWebSelector(view, "#chatSend")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-title')?.textContent.includes(" +
                    "${JSONObject.quote(title)})")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='写入草稿'&&!button.disabled)")
                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(clearContext).size == nativeHistoryBeforeDecision.size
                }
                val clearHistoryBeforeDecision = AiChatHistoryStore.load(clearContext)
                    .map { it.role to it.content }
                assertEquals("chat prompt and proposal response must match before either decision",
                    nativeHistoryBeforeDecision, clearHistoryBeforeDecision)

                assertNull("local Clear proposal provider failed", serverFailure.get())
                assertEquals("Clear proposal review is read-only before STAGE",
                    clearBefore, storeSnapshot(clearStore))
                assertEquals(clearFormalBefore, formalSnapshot(clearStore))
                val projectedReview = WebSkinBridgeRuntime.state().getJSONObject("aiProposalReview")
                val projectedProposal = projectedReview.getJSONArray("proposals").getJSONObject(0)
                assertEquals("CREATE", projectedProposal.getString("operation"))
                assertEquals("ACTIVITY", projectedProposal.getString("kind"))
                assertEquals(title, projectedProposal.getString("title"))
                assertEquals("2026-12-01 19:00", projectedProposal.getString("startText"))
                assertEquals("2026-12-01 20:00", projectedProposal.getString("endText"))

                tapWebSelector(view, ".ai-proposal-actions button:nth-child(2)")
                compose.waitUntil(30_000) { clearStore.pendingCount() == 1 }
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
                awaitJs(view, "document.querySelector('#chatMessages')?.textContent.includes(" +
                    "'已写入 1 张待处理草稿')")
                assertTrue("both skins must send chat and structured proposal requests",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local paired proposal server failed", serverFailure.get())
                assertEquals(4, requests.size)
                assertEquals(prompt, lastUserMessage(requests[0]))
                assertEquals(prompt, lastUserMessage(requests[2]))
                assertTrue("disabled context sharing must exclude the private event fixture",
                    requests.none { it.contains(privateSentinel) })

                val clearDraft = clearStore.pendingDrafts().single()
                assertEquals("same AI proposal must stage the same Host form",
                    nativeDraft.form, clearDraft.form)
                assertEquals(nativeDraft.eventSeq, clearDraft.eventSeq)
                assertEquals(nativeBefore[0] + 1, nativeStore.draftCount())
                assertEquals(nativeBefore[0] + 1, clearStore.draftCount())
                assertEquals(nativeBefore[1] + 1, nativeStore.pendingCount().toLong())
                assertEquals(nativeBefore[1] + 1, clearStore.pendingCount().toLong())
                assertEquals(nativeBefore[2] + 1, nativeStore.identityCount())
                assertEquals(nativeBefore[2] + 1, clearStore.identityCount())
                assertEquals(nativeBefore[3], nativeStore.commitCount())
                assertEquals(nativeBefore[3], clearStore.commitCount())
                assertEquals(nativeBefore[4] + 1, nativeStore.nextSequencePreview())
                assertEquals(nativeBefore[4] + 1, clearStore.nextSequencePreview())
                assertEquals(nativeFormalBefore, formalSnapshot(nativeStore))
                assertEquals(clearFormalBefore, formalSnapshot(clearStore))

                compose.waitUntil(30_000) {
                    AiChatHistoryStore.load(clearContext).any {
                        it.role == "assistant" && it.content.contains("已写入 1 张待处理草稿")
                    }
                }
                val clearStageResult = AiChatHistoryStore.load(clearContext)
                    .last { it.role == "assistant" }
                val clearHistory = AiChatHistoryStore.load(clearContext).map { it.role to it.content }
                assertTrue("Native outcome must report one staged draft and zero publishes",
                    nativeStageResult.content.contains("待处理草稿 1") &&
                        nativeStageResult.content.contains("正式发布 0"))
                assertTrue("Clear outcome must report one staged draft",
                    clearStageResult.content.contains("已写入 1 张待处理草稿"))
                val clearProjectedHistory = WebSkinBridgeRuntime.state().getJSONArray("chatMessages")
                val projectedHistory = (0 until clearProjectedHistory.length()).map { index ->
                    val message = clearProjectedHistory.getJSONObject(index)
                    message.getString("role") to message.getString("content")
                }
                assertEquals(clearHistory, projectedHistory)
                assertEquals("the pre-decision chat history must match across both skins",
                    nativeHistoryBeforeDecision, clearHistoryBeforeDecision)
                println("SEP10_NATIVE_CLEAR_CREATE_STAGE_PARITY title=$title " +
                    "form=${nativeDraft.form} eventSeq=${nativeDraft.eventSeq} " +
                    "storeSnapshot=${storeSnapshot(nativeStore)} trustedStage=true " +
                    "providerRequests=${requests.size} privateSentinel=absent")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                    "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun actualClearWebViewPublishesCreateOnlyAfterHostConfirmation() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-create-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "create-review.db"
        val store = PowerStore(context, database)
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(2)
        val title = "可信发布活动"
        val prompt = "请新建活动：2026-12-01 19:00 开始，20:00 结束，标题是可信发布活动。"
        val proposal = JSONObject().put("events", JSONArray().put(JSONObject()
            .put("operation", "CREATE")
            .put("kind", "ACTIVITY")
            .put("title", title)
            .put("start", "2026-12-01 19:00")
            .put("end", "2026-12-01 20:00"))).toString()

        try {
            store.readableDatabase
            val formalBefore = store.all()
            val draftsBefore = store.draftCount()
            val pendingBefore = store.pendingCount()
            val identitiesBefore = store.identityCount()
            val commitsBefore = store.commitCount()
            val sequenceBefore = store.nextSequencePreview()
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = false
                ))
                val responses = listOf(
                    "我会准备一项待核对提案。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    proposal
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-create-review-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "input.value=${JSONObject.quote(prompt)};" +
                    "document.querySelector('#chatForm').requestSubmit();return true})()")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-title')?.textContent.includes(${JSONObject.quote(title)})")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertTrue("both AI requests must reach the local free stub",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(2, requests.size)

                assertEquals(formalBefore, store.all())
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").assertExists()
                assertEquals("Host review is read-only before approval", formalBefore, store.all())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                compose.onNodeWithText("取消").performClick()
                compose.waitForIdle()
                assertEquals("cancelling Host review must not publish", formalBefore, store.all())
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").performClick()
                compose.waitUntil(30_000) {
                    store.all().singleOrNull()?.let {
                        it.kind == TemporalKind.ACTIVITY && it.title == title &&
                            it.startMillis == java.time.LocalDateTime.parse("2026-12-01T19:00")
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() &&
                            it.endMillis == java.time.LocalDateTime.parse("2026-12-01T20:00")
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } == true
                }
                // A committed AI CREATE keeps one resolved audit receipt in drafts; it is not pending.
                assertEquals(draftsBefore + 1, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(commitsBefore + 1, store.commitCount())
                assertEquals(identitiesBefore + 1, store.identityCount())
                assertEquals(sequenceBefore + 1, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun actualClearWebViewConvertsExistingEventOnlyAfterHostConfirmation() {
        val storage: Context = compose.activity.applicationContext
        val namespace = "pc-ai-convert-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "convert-review.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val startMillis = java.time.LocalDateTime.parse("2026-12-01T19:00")
            .atZone(zone).toInstant().toEpochMilli()
        val endMillis = java.time.LocalDateTime.parse("2026-12-01T20:00")
            .atZone(zone).toInstant().toEpochMilli()
        val title = "待转换闹钟"
        val prompt = "把闹钟 #1 转换为活动，保留 2026-12-01 19:00 开始，结束时间为 20:00。"
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverFailure = AtomicReference<Throwable?>()
        val served = CountDownLatch(2)

        try {
            val seeded = store.createFormalEvent(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = title,
                    startText = DraftForm.formatInstant(startMillis, zone),
                    reminderMinutes = -1,
                    zoneId = zone.id
                ),
                idempotencyKey = "seed-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val original = requireNotNull(store.get(seeded.eventSeq))
            val proposal = JSONObject().put("events", JSONArray().put(JSONObject()
                .put("operation", "CONVERT")
                .put("eventSeq", seeded.eventSeq)
                .put("kind", "ACTIVITY")
                .put("title", title)
                .put("start", "2026-12-01 19:00")
                .put("end", "2026-12-01 20:00"))).toString()
            val formalBefore = store.all()
            val draftsBefore = store.draftCount()
            val pendingBefore = store.pendingCount()
            val commitsBefore = store.commitCount()
            val identitiesBefore = store.identityCount()
            val sequenceBefore = store.nextSequencePreview()
            val userText = prompt.replace("#1", "#${seeded.eventSeq}")

            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            AiChatHistoryStore.save(context, emptyList())
            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 20_000
                AiSettingsStore.save(context, AiSettings(
                    endpoint = "http://127.0.0.1:${server.localPort}/v1",
                    model = "local-stub:free",
                    apiKey = "LOCAL_TEST_ONLY",
                    enabled = true,
                    shareContext = true
                ))
                val responses = listOf(
                    "我会检查目标事项并准备类型转换提案。" +
                        "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
                    proposal
                )
                val serverThread = Thread {
                    try {
                        repeat(responses.size) { index ->
                            server.accept().use { socket ->
                                socket.soTimeout = 15_000
                                requests += readRequest(socket)
                                writeReply(socket, responses[index])
                            }
                            served.countDown()
                        }
                    } catch (error: Throwable) {
                        serverFailure.set(error)
                        while (served.count > 0) served.countDown()
                    }
                }.apply { name = "pc-ai-convert-review-webview-local-stub"; isDaemon = true; start() }

                compose.runOnUiThread {
                    SkinPackageManager(context).select("powercal-clear-default")
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides context) {
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
                awaitJs(view, "!!document.querySelector('.clear-tabbar [data-action=ai]')")
                awaitJs(view, "document.querySelector('#dateSubtitle')?.textContent !== '日历 · Clear'")
                evaluate(view, "document.querySelector('.clear-tabbar [data-action=ai]').click(); true")
                awaitJs(view, "!document.querySelector('#aiPage').hidden")
                evaluate(view, "(()=>{const input=document.querySelector('#chatInput');" +
                    "input.value=${JSONObject.quote(userText)};" +
                    "document.querySelector('#chatForm').requestSubmit();return true})()")
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "document.querySelector('.ai-proposal-title')?.textContent.includes('CONVERT')")
                awaitJs(view, "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertTrue("both AI requests must reach the local free stub",
                    served.await(1, TimeUnit.SECONDS))
                serverThread.join(2_000)
                assertNull("local AI stub failed: ${serverFailure.get()}", serverFailure.get())
                assertEquals(2, requests.size)
                assertTrue("AI context must include the conversion target",
                    requests.last().contains(title))
                assertEquals(formalBefore, store.all())
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").assertExists()
                assertEquals("Host review stays read-only until explicit approval",
                    original, store.get(seeded.eventSeq))
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())

                compose.onNodeWithText("取消").performClick()
                compose.waitForIdle()
                assertEquals("cancelling Host review must not convert the event",
                    original, store.get(seeded.eventSeq))
                assertEquals(draftsBefore, store.draftCount())
                assertEquals(pendingBefore, store.pendingCount())
                assertEquals(commitsBefore, store.commitCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===1 && " +
                    "Array.from(document.querySelectorAll('.ai-proposal-actions button'))" +
                    ".some(button=>button.textContent.trim()==='批量同意'&&!button.disabled)")

                evaluate(view,
                    "document.querySelector('.ai-proposal-actions button.primary').click(); true")
                compose.onNodeWithText("确认 AI 批量正式操作").assertExists()
                compose.onNodeWithText("确认并提交 1 项").performClick()
                compose.waitUntil(30_000) {
                    val saved = store.get(seeded.eventSeq)
                    saved?.kind == TemporalKind.ACTIVITY && saved.title == title &&
                        saved.startMillis == startMillis && saved.endMillis == endMillis
                }
                val converted = requireNotNull(store.get(seeded.eventSeq))
                assertEquals(seeded.eventSeq, converted.eventSeq)
                assertEquals(original.revision + 1, converted.revision)
                assertEquals(commitsBefore + 1, store.commitCount())
                assertEquals(0, store.pendingCount())
                assertEquals(identitiesBefore, store.identityCount())
                assertEquals(sequenceBefore, store.nextSequencePreview())
                awaitJs(view, "document.querySelectorAll('.ai-proposal-item').length===0")
                println("CLEAR_AI_CONVERT_VERIFIED=eventSeq=${converted.eventSeq}; revision=${converted.revision}; " +
                    "kind=${converted.kind}; commits=${store.commitCount()}; identities=${store.identityCount()}; " +
                    "sequence=${store.nextSequencePreview()}; cancel-preserved=true")
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-ai", "powercal-onboarding", "powercal-ui",
                "powercal-skin-choice", "powercal-profile", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    private fun readRequest(socket: java.net.Socket): String {
        val input = socket.getInputStream().buffered()
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                require(byte >= 0) { "truncated HTTP request header" }
                if (byte == '\n'.code) break
                if (byte != '\r'.code) bytes.write(byte)
                require(bytes.size() <= 8_192) { "oversized HTTP header" }
            }
            return bytes.toString(Charsets.US_ASCII.name())
        }
        require(line().startsWith("POST /v1/chat/completions HTTP/1.1"))
        var contentLength = -1
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true))
                contentLength = header.substringAfter(':').trim().toInt()
        }
        require(contentLength in 1..100_000)
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            require(count > 0) { "truncated HTTP request body" }
            offset += count
        }
        return String(body, Charsets.UTF_8)
    }

    private fun writeReply(socket: java.net.Socket, content: String) {
        val body = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("role", "assistant").put("content", content))
            .put("finish_reason", "stop"))).toString().toByteArray(Charsets.UTF_8)
        val headers = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(body); flush() }
    }

    private fun writeFailure(socket: java.net.Socket, providerBodyMarker: String) {
        val body = JSONObject().put("error", providerBodyMarker).toString()
            .toByteArray(Charsets.UTF_8)
        val headers = ("HTTP/1.1 503 Service Unavailable\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(body); flush() }
    }

    private class IsolatedAppContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }
        private fun alias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File = backing.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(name: String, mode: Int,
            factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(alias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = backing.deleteDatabase(alias(name))
    }
}
