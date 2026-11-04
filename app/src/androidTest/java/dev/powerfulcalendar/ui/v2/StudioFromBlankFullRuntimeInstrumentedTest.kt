package dev.powerfulcalendar.ui.v2

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.accessibilityservice.AccessibilityService
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.ai.AiChatHistoryStore
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.api.v1.EventEditorOpenResultV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val S06_SKIN_ID = "powercal-from-blank-functional"
private const val S06_SKIN_ASSET = "from-blank-functional.pcskin"
private const val S06_SKIN_SHA256 = "e810bc0897cd3b35985067dd23c077394c0a131381795fd8136c616db71751b7"
private const val S06_COLD_NAMESPACE = "pc-s06-from-blank-cold"

@RunWith(AndroidJUnit4::class)
class StudioFromBlankFullRuntimeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedS06Context(
        target: Context,
        private val storage: Context,
        private val namespace: String,
        private val shareSkinInstallState: Boolean = false
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun alias(name: String) = "$namespace-$name"
        private fun databasePath(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            if (shareSkinInstallState && name == "powercal-skin-choice") {
                storage.getSharedPreferences(name, mode)
            } else storage.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = if (shareSkinInstallState) storage.filesDir
            else File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            databasePath(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            databasePath(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(alias(name))
    }

    private data class Harness(
        val namespace: String,
        val storage: Context,
        val context: IsolatedS06Context,
        val store: PowerStore,
        val manager: SkinPackageManager,
        val database: String,
        val web: WebView
    )

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun currentWebView(fallback: WebView): WebView {
        val candidates = mutableListOf<WebView>()
        fun collect(view: View) {
            if (view is WebView) candidates += view
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        compose.runOnUiThread {
            collect(compose.activity.findViewById(android.R.id.content))
        }
        return candidates.lastOrNull { it.isAttachedToWindow && it.isShown && it.alpha > 0f }
            ?: fallback
    }

    private fun evaluate(web: WebView, source: String): String {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        val activeWeb = currentWebView(web)
        compose.runOnUiThread {
            activeWeb.evaluateJavascript(source) { value -> result.set(value); latch.countDown() }
        }
        assertTrue("WebView JavaScript timed out: $source", latch.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun evaluateString(web: WebView, source: String): String =
        JSONArray("[${evaluate(web, source)}]").getString(0)

    private fun evaluateObject(web: WebView, source: String): JSONObject =
        JSONObject(evaluateString(web, "JSON.stringify($source)"))

    private fun waitJs(web: WebView, expression: String, message: String) {
        compose.waitUntil(30_000) { evaluate(web, expression) == "true" }
        assertTrue(message, evaluate(web, expression) == "true")
    }

    /** Injects a real touchscreen sequence at a visible WebView target. */
    private fun tap(web: WebView, selector: String) {
        val activeWeb = currentWebView(web)
        if (activeWeb !== web) {
            println("S06_WEBVIEW_REBOUND previousAttached=${web.isAttachedToWindow} " +
                "currentAttached=${activeWeb.isAttachedToWindow}")
        }
        assertTrue("Current Skin WebView must be attached for $selector; " +
            "attached=${activeWeb.isAttachedToWindow} shown=${activeWeb.isShown} " +
            "window=${activeWeb.windowVisibility}",
            activeWeb.isAttachedToWindow && activeWeb.isShown)
        focusWebViewForTouch(activeWeb)
        val quoted = JSONObject.quote(selector)
        val target = evaluateObject(activeWeb, """(()=>{
            const e=document.querySelector($quoted);if(!e)return {found:false};
            e.scrollIntoView({block:'center',inline:'nearest'});
            const r=e.getBoundingClientRect(),hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
            window.__s06LastClick=null;e.addEventListener('click',event=>{
              window.__s06LastClick={trusted:event.isTrusted,id:e.id,tag:e.tagName};
            },{once:true,capture:true});
            return {found:true,x:r.left+r.width/2,y:r.top+r.height/2,w:innerWidth,h:innerHeight,
              visible:r.width>0&&r.height>0&&r.top>=0&&r.bottom<=innerHeight&&r.left>=0&&r.right<=innerWidth,
              hit:hit===e||e.contains(hit),disabled:e.disabled===true};
        })()""")
        assertTrue("Android Skin touch target unavailable: $selector $target",
            target.optBoolean("found") && target.optBoolean("visible") &&
                target.optBoolean("hit") && !target.optBoolean("disabled"))
        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        val windowState = AtomicReference<String>()
        compose.runOnUiThread {
            activeWeb.getLocationOnScreen(location)
            dimensions.set(activeWeb.width to activeWeb.height)
            val visibleRect = android.graphics.Rect()
            val globalVisible = activeWeb.getGlobalVisibleRect(visibleRect)
            windowState.set("activityFocus=${compose.activity.window.decorView.hasWindowFocus()} " +
                "webFocus=${activeWeb.hasWindowFocus()} shown=${activeWeb.isShown} " +
                "attached=${activeWeb.isAttachedToWindow} globalVisible=$globalVisible rect=$visibleRect")
        }
        val (width, height) = requireNotNull(dimensions.get())
        val sx = width / target.getDouble("w")
        val sy = height / target.getDouble("h")
        assertEquals("WebView touch coordinate scale", sx, sy, 0.03)
        val x = location[0] + (target.getDouble("x") * sx).toFloat()
        val y = location[1] + (target.getDouble("y") * sy).toFloat()
        val deliveredTouches = java.util.concurrent.atomic.AtomicInteger()
        compose.runOnUiThread {
            activeWeb.setOnTouchListener { _, _ -> deliveredTouches.incrementAndGet(); false }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Android rejected touchscreen down", automation.injectInputEvent(down, true))
            assertTrue("Android rejected touchscreen up", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
            compose.runOnUiThread { activeWeb.setOnTouchListener(null) }
        }
        assertTrue("Android touchscreen sequence must reach the WebView for $selector; target=$target " +
            "screen=($x,$y) location=${location.toList()} size=${width}x$height " +
            "attached=${activeWeb.isAttachedToWindow} shown=${activeWeb.isShown} " +
            "window=${activeWeb.windowVisibility} $windowState " +
            "deliveredTouches=${deliveredTouches.get()}", deliveredTouches.get() >= 2)
    }

    private fun launchHarness(
        namespace: String,
        beforeContent: (IsolatedS06Context) -> Unit = {},
        install: Boolean = true
    ): Harness {
        assumeTrue("S-06 Android runtime gate requires API 35", Build.VERSION.SDK_INT == 35)
        val storage = compose.activity.applicationContext
        val context = IsolatedS06Context(compose.activity, storage, namespace)
        val database = "s06-runtime.db"
        val store = PowerStore(context, database)
        val manager = SkinPackageManager(context)
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open(S06_SKIN_ASSET).use { it.readBytes() }
        assertEquals(S06_SKIN_SHA256, sha256(bytes))
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        if (install) {
            val archive = File(context.cacheDir, "$S06_SKIN_ID.pcskin").apply { writeBytes(bytes) }
            assertEquals(S06_SKIN_ID, manager.install(Uri.fromFile(archive)).id)
        }
        beforeContent(context)
        manager.select(S06_SKIN_ID)
        assertEquals(S06_SKIN_ID, manager.currentId())
        compose.runOnUiThread {
            PowerCalSkinRegistry.reset()
            manager.select(S06_SKIN_ID)
            compose.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
        }
        val ref = AtomicReference<WebView?>()
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                ref.set(findWebView(compose.activity.findViewById(android.R.id.content)))
            }
            ref.get() != null
        }
        val web = requireNotNull(ref.get())
        waitJs(web, "!!window.PowerCalSkin&&!!window.__POWERCAL_PAGE_TREE_V1__&&" +
            "!!window.__fromBlankRuntime?.state?.hostDataReady&&" +
            "document.querySelectorAll('#page-navigation [data-route]').length===5",
            "Studio-exported Skin did not initialize the real Android Host Bridge")
        assertEquals(S06_SKIN_ID, PowerCalSkinRegistry.active.key)
        assertEquals(setOf("host.read", "host.action"),
            requireNotNull(PowerCalSkinRegistry.active.web).capabilities)
        return Harness(namespace, storage, context, store, manager, database, web)
    }

    private fun closeHarness(h: Harness, removePackage: Boolean = true) {
        compose.runOnUiThread {
            compose.activity.setContent { }
            PowerCalSkinRegistry.reset()
        }
        compose.waitForIdle()
        if (removePackage) {
            runCatching { h.manager.select("powercal-default") }
            runCatching { h.manager.delete(S06_SKIN_ID) }
        }
        h.store.close()
        h.context.deleteDatabase(h.database)
        listOf("powercal-skin-choice", "powercal-ui", "powercal-profile",
            "powercal-onboarding", "powercal-ai", "powercal-file-ai-consent")
            .forEach { h.storage.deleteSharedPreferences("${h.namespace}-$it") }
        File(h.storage.cacheDir, h.namespace).deleteRecursively()
    }

    private fun setEditorField(web: WebView, field: String, value: String) {
        val selector = "#fb-edit-$field"
        val quotedField = JSONObject.quote(field)
        val quotedValue = JSONObject.quote(value)
        assertEquals("Blank Skin editor field exists: $field", "true", evaluate(web,
            "!!document.querySelector(${JSONObject.quote(selector)})"))
        evaluate(web, "(()=>{const e=document.querySelector(${JSONObject.quote(selector)});" +
            "e.value=$quotedValue;e.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
        waitJs(web, "(()=>{const x=window.__fromBlankRuntime?.state?.editing||{};" +
            "const f=x.deck?.form||x.editor?.form;return !!f&&String(f[$quotedField])===$quotedValue})()",
            "Core Host did not return the edited $field value")
    }

    private fun state(web: WebView): JSONObject =
        evaluateObject(web, "window.__fromBlankRuntime.state")

    private fun focusWebViewForTouch(web: WebView) {
        compose.runOnUiThread {
            val inputMethod = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            inputMethod.hideSoftInputFromWindow(web.windowToken, 0)
            compose.activity.window.decorView.requestFocus()
            web.requestFocus()
        }
        compose.waitUntil(10_000) {
            val focused = AtomicReference<Pair<Boolean, Boolean>>()
            compose.runOnUiThread {
                focused.set(compose.activity.window.decorView.hasWindowFocus() to web.hasWindowFocus())
            }
            focused.get() == (true to true)
        }
    }

    private fun findAccessibilityText(
        root: AccessibilityNodeInfo?,
        labels: Set<String>
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() in labels) return root
        for (index in 0 until root.childCount) {
            findAccessibilityText(root.getChild(index), labels)?.let { return it }
        }
        return null
    }

    private fun clickAccessibilityNode(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue("Android system prompt action must be clickable",
            target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun waitForExternalUi(packageFragment: String) {
        compose.waitUntil(15_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                ?.packageName?.toString()?.contains(packageFragment, ignoreCase = true) == true
        }
    }

    private fun returnFromExternalUi() {
        assertTrue("System picker should accept Back as cancellation",
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK
            ))
        compose.waitUntil(15_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                ?.packageName?.toString() == compose.activity.packageName
        }
    }

    private fun storeSnapshot(store: PowerStore): List<Any> = listOf(
        store.all().toList(), store.draftCount(), store.pendingCount(), store.commitCount(),
        store.identityCount(), store.nextSequencePreview()
    )
    private fun approve(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Host approval prompt not visible: $text",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("确认").performClick()
    }

    private fun createEvent(
        h: Harness,
        kind: TemporalKind,
        title: String,
        repeating: Boolean = false
    ): TemporalRecord {
        val web = h.web
        val before = h.store.all().toList()
        val commits = h.store.commitCount()
        val identities = h.store.identityCount()
        val namespace = kind.name.lowercase()
        tap(web, "#fb-create-$namespace")
        waitJs(web, "!!document.querySelector('#fb-event-editor')&&!!document.querySelector('#fb-edit-title')",
            "Blank Skin did not open the $kind editor")
        val current = state(web).getJSONObject("editing")
        assertEquals(kind.name, current.getJSONObject("deck").getJSONObject("form").getString("kind"))
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).plusMinutes(45).withSecond(0).withNano(0)
        when (kind) {
            TemporalKind.ACTIVITY -> {
                setEditorField(web, "startText", DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone))
                setEditorField(web, "endText", DraftForm.formatInstant(start.plusHours(1).toInstant().toEpochMilli(), zone))
            }
            TemporalKind.ALARM ->
                setEditorField(web, "startText", DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone))
            TemporalKind.DEADLINE -> setEditorField(web, "endText", LocalDate.now(zone).plusDays(1).toString())
            TemporalKind.MEMO -> Unit
        }
        setEditorField(web, "title", title)
        if (repeating) {
            assertEquals(TemporalKind.ACTIVITY, kind)
            val from = LocalDate.now(zone)
            setEditorField(web, "recurrenceFreq", "DAILY")
            setEditorField(web, "recurrenceInterval", "1")
            setEditorField(web, "recurrenceFrom", from.toString())
            setEditorField(web, "recurrenceUntil", from.plusDays(4).toString())
        }
        assertEquals("transient editor must not write before explicit Host confirmation", before, h.store.all())
        assertEquals(commits, h.store.commitCount())
        assertEquals(identities, h.store.identityCount())
        tap(web, "#fb-edit-save")
        approve("确认发布正式事件")
        compose.waitUntil(30_000) { h.store.all().any { it.title == title && it.kind == kind } }
        val saved = requireNotNull(h.store.all().singleOrNull { it.title == title && it.kind == kind })
        assertEquals(commits + 1, h.store.commitCount())
        assertEquals(identities + 1, h.store.identityCount())
        assertEquals(before.size + 1, h.store.all().size)
        return saved
    }

    @Test
    fun blankStudioSkinRunsFivePagesCalendarFourKindsEditorMemoAndSettingsOnAndroid() {
        val h = launchHarness("pc-s06-functional-${UUID.randomUUID()}")
        try {
            val web = h.web
            val routes = listOf("timeline", "month", "memo", "ai", "settings")
            for (route in routes) {
                tap(web, "#fb-nav-$route")
                waitJs(web, "window.__fromBlankRuntime?.page===${JSONObject.quote(route.uppercase())}",
                    "Android Skin route did not become visible: $route")
                assertTrue("page component tree must remain attached for $route",
                    evaluate(web, "!!document.querySelector('#fb-page-${route} [data-component-id]')") == "true")
            }

            tap(web, "#fb-nav-timeline")
            val initialAnchor = state(web).getJSONObject("timeline").getLong("anchorMillis")
            tap(web, "#fb-runtime-pan")
            compose.waitUntil(10_000) {
                state(web).getJSONObject("timeline").getLong("anchorMillis") != initialAnchor
            }
            tap(web, "#fb-runtime-today")
            val todayAnchor = state(web).getJSONObject("timeline").getLong("anchorMillis")
            val expectedToday = LocalDate.now(ZoneId.systemDefault()).atTime(12, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals("Today returns the Core camera to local noon", expectedToday, todayAnchor)
            tap(web, "#fb-runtime-reset")
            compose.waitUntil(10_000) { state(web).getJSONObject("timeline").getDouble("hours") == 24.0 }

            tap(web, "#fb-nav-month")
            waitJs(web, "window.__fromBlankRuntime?.page==='MONTH'", "Month navigation was not applied")
            val monthBefore = state(web).getJSONObject("month").getString("month")
            tap(web, "#fb-month-next-runtime")
            compose.waitUntil(10_000) {
                state(web).getJSONObject("month").getString("month") != monthBefore
            }
            val selectedDate = state(web).getJSONObject("month").getString("selectedDate")
            val nextSelectedDate = LocalDate.parse(selectedDate).plusDays(1).toString()
            tap(web, "#fb-date-$nextSelectedDate")
            compose.waitUntil(10_000) {
                state(web).getJSONObject("month").getString("selectedDate") == nextSelectedDate &&
                    evaluate(web, "document.querySelector('#fb-date-$nextSelectedDate')?.getAttribute('aria-pressed')==='true'") == "true"
            }

            val created = linkedMapOf<TemporalKind, TemporalRecord>()
            for (kind in TemporalKind.entries) {
                tap(web, "#fb-nav-timeline")
                waitJs(web, "window.__fromBlankRuntime?.page==='TIMELINE'", "Timeline navigation was not applied")
                created[kind] = createEvent(h, kind, "S06 blank ${kind.name}")
            }
            val activity = requireNotNull(created[TemporalKind.ACTIVITY])

            tap(web, "#fb-nav-timeline")
            waitJs(web, "!!document.querySelector(`[data-event-seq='${activity.eventSeq}'] button`)",
                "Created Activity is absent from the Host timeline projection")
            val editCommandTrace = AtomicReference<String?>()
            val originalOnEdit = AtomicReference<suspend (WebEditCommand) -> JSONObject>()
            compose.runOnUiThread {
                val delegate = WebSkinBridgeRuntime.onEdit
                originalOnEdit.set(delegate)
                WebSkinBridgeRuntime.onEdit = { command ->
                    editCommandTrace.set(command.toString())
                    delegate(command)
                }
            }
            val editorOpened = try {
                tap(web, "#fb-timeline-events [data-event-seq='${activity.eventSeq}'] button")
                compose.waitForIdle()
                runCatching {
                    compose.waitUntil(5_000) {
                        evaluate(web, "window.__fromBlankRuntime?.state?.editing?.editor?.open===true") == "true"
                    }
                    true
                }.getOrDefault(false)
            } finally {
                compose.runOnUiThread { WebSkinBridgeRuntime.onEdit = originalOnEdit.get() }
            }
            if (!editorOpened) {
                val directOpen = runCatching {
                    val api = PowerCalCoreFeatureApiV1(h.store).events
                    when (val result = api.openFormalEvent(activity.eventSeq, activity.revision)) {
                        is EventEditorOpenResultV1.Opened -> {
                            api.closeFormalEvent(result.editor.handle)
                            "Opened"
                        }
                        else -> result.toString()
                    }
                }.getOrElse { "${it::class.simpleName}:${it.message}" }
                val diagnostic = runCatching {
                    evaluateString(web, "JSON.stringify({page:window.__fromBlankRuntime?.page,hostNotice:window.__fromBlankRuntime?.state?.hostNotice,aiLocked:window.__fromBlankRuntime?.state?.aiLocked,editing:window.__fromBlankRuntime?.state?.editing,lastClick:window.__s06LastClick,hostEditCommandTrace:${JSONObject.quote(editCommandTrace.get().orEmpty())},directCoreOpen:${JSONObject.quote(directOpen)},row:document.querySelector(`[data-event-seq='${activity.eventSeq}']`)?.outerHTML})")
                }.getOrElse { "state unavailable: ${it::class.simpleName}" }
                throw AssertionError("Skin did not open projected Activity via Host API: $diagnostic")
            }
            val unchanged = storeSnapshot(h.store)
            val originalTitle = h.store.get(activity.eventSeq)?.title
            setEditorField(web, "title", "S06 cancelled title must not persist")
            assertEquals("Skin edits remain transient until explicit save", unchanged,
                storeSnapshot(h.store))
            tap(web, "#fb-edit-cancel")
            waitJs(web, "window.__fromBlankLastResult?.status==='discarded_changes'&&" +
                "window.__fromBlankRuntime?.state?.editing?.editor?.form?.title===${JSONObject.quote(requireNotNull(originalTitle))}",
                "Host did not discard the transient edit")
            tap(web, "#fb-edit-close")
            waitJs(web, "!document.querySelector('#fb-event-editor')", "No-change close did not close the editor")
            assertEquals("cancel and no-change close must not write Core Store", unchanged,
                storeSnapshot(h.store))

            tap(web, "#fb-timeline-events [data-event-seq='${activity.eventSeq}'] button")
            waitJs(web, "window.__fromBlankRuntime?.state?.editing?.editor?.open===true",
                "Skin could not reopen Activity")
            val editedTitle = "S06 blank Activity edited"
            setEditorField(web, "title", editedTitle)
            val beforeEdit = requireNotNull(h.store.get(activity.eventSeq))
            tap(web, "#fb-edit-save")
            approve("确认保存正式事件修改")
            compose.waitUntil(30_000) { h.store.get(activity.eventSeq)?.title == editedTitle }
            assertEquals(beforeEdit.revision + 1, h.store.get(activity.eventSeq)?.revision)

            tap(web, "#fb-edit-delete")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertNotNull(h.store.get(activity.eventSeq))
            compose.onNodeWithText("取消").performClick()
            assertNotNull("cancelled deletion must retain the formal event", h.store.get(activity.eventSeq))
            tap(web, "#fb-edit-delete")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认删除").performClick()
            compose.waitUntil(30_000) { h.store.get(activity.eventSeq) == null }

            val memo = requireNotNull(created[TemporalKind.MEMO])
            tap(web, "#fb-nav-memo")
            waitJs(web, "!!document.querySelector('#fb-memo-toggle-${memo.eventSeq}')",
                "Memo Core projection is absent from the blank Skin")
            val memoBefore = requireNotNull(h.store.get(memo.eventSeq))
            tap(web, "#fb-memo-toggle-${memo.eventSeq}")
            compose.waitUntil(20_000) {
                h.store.get(memo.eventSeq)?.completedKeys?.isNotEmpty() == true
            }
            val memoDone = requireNotNull(h.store.get(memo.eventSeq))
            assertEquals(memoBefore.revision + 1, memoDone.revision)
            waitJs(web, "document.querySelector('#fb-memo-toggle-${memo.eventSeq}')?.textContent==='撤销完成'",
                "Completed Memo state was not rendered by the Skin")
            tap(web, "#fb-memo-toggle-${memo.eventSeq}")
            compose.waitUntil(20_000) {
                h.store.get(memo.eventSeq)?.completedKeys?.isEmpty() == true
            }
            assertEquals(memoDone.revision + 1, h.store.get(memo.eventSeq)?.revision)

            tap(web, "#fb-nav-timeline")
            val recurring = createEvent(h, TemporalKind.ACTIVITY, "S06 blank recurring Activity", repeating = true)
            assertTrue("repeat series must be created through the blank Skin editor",
                requireNotNull(h.store.get(recurring.eventSeq)).recurrence.isRepeating)
            tap(web, "#fb-nav-month")
            waitJs(web, "!!document.querySelector('#fb-month-events')", "Month page did not render")
            val monthItems = state(web).getJSONObject("month").getJSONArray("items")
            assertTrue("Core Month projection must contain the authored recurring series",
                (0 until monthItems.length()).any {
                    monthItems.getJSONObject(it).optLong("eventSeq") == recurring.eventSeq
                })
            for (scope in listOf("SINGLE_OCCURRENCE", "THIS_AND_FUTURE", "WHOLE_SERIES")) {
                val occurrence = state(web).getJSONObject("month").getJSONArray("items")
                val index = (0 until occurrence.length()).first {
                    occurrence.getJSONObject(it).optLong("eventSeq") == recurring.eventSeq
                }
                val date = occurrence.getJSONObject(index).getString("date")
                val selected = state(web).getJSONObject("month").getString("selectedDate")
                if (date != selected) tap(web, "#fb-date-$date")
                waitJs(web, "!!document.querySelector('#fb-month-events button')",
                    "Recurring occurrence cannot be opened for scope $scope")
                tap(web, "#fb-month-events [data-event-seq='${recurring.eventSeq}'] button")
                val scopeOpened = runCatching {
                    compose.waitUntil(5_000) {
                        evaluate(web, "window.__fromBlankRuntime?.state?.editing?.editor?.requiresRecurrenceScope===true") == "true"
                    }
                    true
                }.getOrDefault(false)
                if (!scopeOpened) {
                    val diagnostic = evaluateString(web,
                        "JSON.stringify({editing:window.__fromBlankRuntime?.state?.editing," +
                            "lastResult:window.__fromBlankLastResult," +
                            "selectedDate:window.__fromBlankRuntime?.state?.month?.selectedDate," +
                            "occurrences:(window.__fromBlankRuntime?.state?.month?.items||[]).filter(x=>x.eventSeq===${recurring.eventSeq})})")
                    throw AssertionError("Host did not require explicit recurrence scope $scope: $diagnostic")
                }
                tap(web, "#fb-scope-$scope")
                waitJs(web, "window.__fromBlankLastResult?.status==='selected'&&" +
                    "window.__fromBlankRuntime?.state?.editing?.editor?.recurrenceScope===${JSONObject.quote(scope)}",
                    "Host rejected or failed to render recurrence scope $scope")
                assertEquals("scope selection is transient until Save is reviewed", recurring,
                    h.store.get(recurring.eventSeq))
                tap(web, "#fb-edit-close")
                waitJs(web, "!document.querySelector('#fb-event-editor')", "Scope review close failed")
            }

            tap(web, "#fb-nav-settings")
            waitJs(web, "!!document.querySelector('#fb-settings-diagnostics')&&" +
                "!!window.__fromBlankRuntime?.state?.notificationPermission&&" +
                "!!window.__fromBlankRuntime?.state?.exactAlarmPermission",
                "Settings did not show Host-owned permission state")
            tap(web, "#fb-reconcile")
            compose.waitUntil(20_000) {
                state(web).getJSONObject("reminderReconcile").optString("status") != "RUNNING"
            }
            assertTrue("Settings should expose functional Host option rows",
                state(web).optJSONArray("optionGroups")?.length()?.let { it > 0 } == true)
            println("S06_FROM_BLANK_ANDROID PASS skinSha256=$S06_SKIN_SHA256 " +
                "routes=5 eventKinds=4 memoToggleUndo=true recurrenceScopes=3 " +
                "storeRows=${h.store.all().size} reminders=${state(web).getJSONObject("reminderReconcile")}")
        } finally {
            closeHarness(h)
        }
    }

    @Test
    fun blankStudioSkinUsesHostPermissionAndFileActionsWithoutWritesOnCancel() {
        val h = launchHarness("pc-s06-settings-${UUID.randomUUID()}")
        try {
            val web = h.web
            tap(web, "#fb-nav-settings")
            waitJs(web, "window.__fromBlankRuntime?.page==='SETTINGS'&&" +
                "!!document.querySelector('#fb-settings-diagnostics')",
                "Blank Skin Settings page did not render")
            val before = storeSnapshot(h.store)

            tap(web, "#fb-option-calendar-import-file")
            waitForExternalUi("documentsui")
            assertEquals("ICS file selection must not write before a file is chosen",
                before, storeSnapshot(h.store))
            returnFromExternalUi()
            assertEquals("Cancelling ICS selection must leave the Core Store unchanged",
                before, storeSnapshot(h.store))

            tap(web, "#fb-option-data-backup-export")
            waitForExternalUi("documentsui")
            assertEquals("Backup picker launch is read-only",
                before, storeSnapshot(h.store))
            returnFromExternalUi()
            assertEquals("Cancelling backup export must not write Core data",
                before, storeSnapshot(h.store))

            tap(web, "#fb-notification")
            waitForExternalUi("permissioncontroller")
            val permissionRoot = InstrumentationRegistry.getInstrumentation()
                .uiAutomation.rootInActiveWindow
            val deny = findAccessibilityText(permissionRoot,
                setOf("Don't allow", "Don’t allow", "不允许", "拒绝"))
            assertNotNull("Android notification permission prompt must expose a deny action", deny)
            clickAccessibilityNode(requireNotNull(deny))
            compose.waitUntil(15_000) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                    ?.packageName?.toString() == compose.activity.packageName &&
                    state(web).getJSONObject("notificationPermission").optBoolean("allowed", true).not()
            }
            assertEquals("Permission denial must not mutate calendar data", before, storeSnapshot(h.store))
            println("S06_FROM_BLANK_SETTINGS PASS permission=denied icsPicker=cancelled " +
                "backupPicker=cancelled storeWrites=0")
        } finally {
            closeHarness(h)
        }
    }

    @Test
    fun blankStudioSkinSendsChatAndRequiresHumanAiProposalReviewWithLocalStub() {
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 20_000
        val responses = listOf(
            "Local QA assistant prepared one candidate. " +
                "[[POWERCAL_DRAFT]]{\"type\":\"draft\",\"intent\":\"explicit\"}[[/POWERCAL_DRAFT]]",
            JSONObject().put("events", JSONArray().put(JSONObject()
                .put("operation", "CREATE").put("kind", "MEMO").put("title", "S06 AI proposal"))).toString()
        )
        val failure = AtomicReference<Throwable?>()
        val served = CountDownLatch(responses.size)
        val requestCount = AtomicReference(0)
        val thread = Thread {
            try {
                for (content in responses) {
                    server.accept().use { socket ->
                        socket.soTimeout = 15_000
                        readHttpRequest(socket)
                        requestCount.updateAndGet { it + 1 }
                        writeChatCompletion(socket, content)
                    }
                    served.countDown()
                }
            } catch (error: Throwable) {
                failure.set(error)
                while (served.count > 0) served.countDown()
            }
        }.apply { name = "s06-from-blank-ai-local-stub"; isDaemon = true; start() }
        val h = launchHarness("pc-s06-ai-${UUID.randomUUID()}", beforeContent = { context ->
            AiChatHistoryStore.save(context, emptyList())
            AiSettingsStore.save(context, AiSettings(
                endpoint = "http://127.0.0.1:${server.localPort}/v1",
                model = "local-stub:free",
                apiKey = "LOCAL_TEST_ONLY",
                enabled = true,
                shareContext = false
            ))
        })
        try {
            val web = h.web
            tap(web, "#fb-nav-ai")
            waitJs(web, "window.__fromBlankRuntime?.page==='AI'&&!!document.querySelector('#fb-ai-input')",
                "Blank Skin AI route or input did not render")
            evaluate(web, "(()=>{const e=document.querySelector('#fb-ai-input');" +
                "e.value='Create a memo for the S06 local review';return true})()")
            tap(web, "#fb-ai-send-runtime")
            waitJs(web, "window.__fromBlankRuntime?.state?.aiProposalReview?.proposals?.length===1&&" +
                "window.__fromBlankRuntime?.state?.chatMessages?.some(x=>x.role==='user')",
                "Local AI response did not reach the Skin proposal review")
            assertTrue("AI proposal review must not write Core data before a human decision",
                h.store.all().isEmpty() && h.store.draftCount() == 0L &&
                    h.store.commitCount() == 0L && h.store.identityCount() == 0L)
            tap(web, "#fb-ai-reject-0")
            waitJs(web, "window.__fromBlankRuntime?.state?.aiProposalReview==null&&" +
                "window.__fromBlankRuntime?.state?.chatMessages?.some(x=>x.content.includes('已拒绝'))",
                "Skin's explicit proposal rejection did not complete")
            assertTrue("rejected AI proposal must leave the Store untouched",
                h.store.all().isEmpty() && h.store.draftCount() == 0L &&
                    h.store.commitCount() == 0L && h.store.identityCount() == 0L)
            assertTrue("two deterministic local model calls should complete", served.await(2, TimeUnit.SECONDS))
            thread.join(2_000)
            assertEquals(null, failure.get())
            assertEquals(2, requestCount.get())
            println("S06_FROM_BLANK_AI PASS localStubRequests=${requestCount.get()} " +
                "proposal=CREATE/MEMO decision=REJECT coreWrites=0")
        } finally {
            closeHarness(h)
            runCatching { server.close() }
        }
    }

    @Test
    fun blankStudioSkinRestoresItsPackageAfterARealForceStop() {
        assumeTrue("cold restart phase is only run against the isolated API 35 emulator",
            Build.VERSION.SDK_INT == 35 && (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"))
        val phase = InstrumentationRegistry.getArguments().getString("s06Phase", "single")
        require(phase in setOf("single", "prepare", "restore")) { "Unknown S-06 phase: $phase" }
        val storage = compose.activity.applicationContext
        val context = IsolatedS06Context(
            compose.activity, storage, S06_COLD_NAMESPACE, shareSkinInstallState = true
        )
        val markerDir = File(storage.cacheDir, "$S06_COLD_NAMESPACE-marker")
        val packageOwnershipFile = markerDir.resolve("package-created-by-s06")
        var packageOwnedByS06 = packageOwnershipFile.takeIf { it.isFile }
            ?.readText()?.toBoolean() ?: false
        require(phase != "restore" || packageOwnershipFile.isFile) {
            "Missing S-06 package ownership snapshot"
        }
        val selectionPrefs = storage.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
        val onboardingPrefs = storage.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val selectionPresentFile = markerDir.resolve("original-selection-present")
        val hasSavedOriginal = selectionPresentFile.isFile
        require(phase != "restore" || hasSavedOriginal) { "Missing S-06 original selection snapshot" }
        val originalSelectionPresent = if (hasSavedOriginal) {
            selectionPresentFile.readText().toBoolean()
        } else selectionPrefs.contains("selected")
        val originalSelection = if (hasSavedOriginal) {
            markerDir.resolve("original-selection").readText().ifEmpty { null }
        } else selectionPrefs.getString("selected", null)
        val onboardingPresentFile = markerDir.resolve("original-onboarding-present")
        val hasSavedOnboarding = onboardingPresentFile.isFile
        val originalOnboardingPresent = if (hasSavedOnboarding) {
            onboardingPresentFile.readText().toBoolean()
        } else onboardingPrefs.contains("system-calendar-import-asked")
        val originalOnboarding = if (hasSavedOnboarding) {
            markerDir.resolve("original-onboarding").readText().toBoolean()
        } else onboardingPrefs.getBoolean("system-calendar-import-asked", false)
        val database = "s06-cold.db"
        val store = PowerStore(context, database)
        val manager = SkinPackageManager(context)
        var keep = false
        try {
            if (phase == "single" || phase == "prepare") {
                markerDir.deleteRecursively()
                if (phase == "prepare") {
                    markerDir.mkdirs()
                    markerDir.resolve("original-selection-present")
                        .writeText(originalSelectionPresent.toString())
                    markerDir.resolve("original-selection").writeText(originalSelection.orEmpty())
                    markerDir.resolve("original-onboarding-present")
                        .writeText(originalOnboardingPresent.toString())
                    markerDir.resolve("original-onboarding").writeText(originalOnboarding.toString())
                }
                val bytes = InstrumentationRegistry.getInstrumentation().context.assets
                    .open(S06_SKIN_ASSET).use { it.readBytes() }
                assertEquals(S06_SKIN_SHA256, sha256(bytes))
                val installed = File(storage.filesDir, "skin-packs/$S06_SKIN_ID.pcskin")
                if (installed.isFile) {
                    assertEquals("refusing to overwrite a non-S-06 package",
                        S06_SKIN_SHA256, sha256(installed.readBytes()))
                } else {
                    val archive = File(storage.cacheDir, "$S06_SKIN_ID.pcskin").apply { writeBytes(bytes) }
                    assertEquals(S06_SKIN_ID, manager.install(Uri.fromFile(archive)).id)
                    archive.delete()
                    packageOwnedByS06 = true
                }
                if (phase == "prepare") {
                    markerDir.resolve("package-created-by-s06")
                        .writeText(packageOwnedByS06.toString())
                }
                manager.select(S06_SKIN_ID)
                assertTrue(selectionPrefs.edit().putString("selected", S06_SKIN_ID).commit())
            } else {
                assertTrue("prepared package disappeared before cold restore",
                    File(storage.filesDir, "skin-packs/$S06_SKIN_ID.pcskin").isFile)
                assertEquals(S06_SKIN_ID, manager.currentId())
                val oldPid = markerDir.resolve("pid").readText().toInt()
                val currentTargetPid = targetProcessPid(storage)
                assertTrue(
                    "force-stop must create a new PowerCal app process (old=$oldPid current=$currentTargetPid)",
                    oldPid != currentTargetPid
                )
                PowerCalSkinRegistry.reset()
                manager.restoreSelection()
                assertEquals(S06_SKIN_ID, PowerCalSkinRegistry.active.key)
            }
            assertTrue(onboardingPrefs.edit()
                .putBoolean("system-calendar-import-asked", true).commit())
            assertTrue(context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit())
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                manager.restoreSelection()
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides context) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            val ref = AtomicReference<WebView?>()
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    ref.set(findWebView(compose.activity.findViewById(android.R.id.content)))
                }
                ref.get() != null
            }
            val web = requireNotNull(ref.get())
            waitJs(web, "!!window.__fromBlankRuntime?.state?.hostDataReady&&" +
                "!!document.querySelector('#fb-page-timeline')",
                "Blank Skin did not render after package restore")
            tap(web, "#fb-nav-month")
            waitJs(web, "window.__fromBlankRuntime?.page==='MONTH'&&" +
                "!!document.querySelector('#fb-month-grid-runtime')",
                "Blank Skin did not accept a real touch after process restore")
            if (phase == "prepare") {
                markerDir.mkdirs()
                markerDir.resolve("pid").writeText(targetProcessPid(storage).toString())
                markerDir.resolve("skin.sha256").writeText(S06_SKIN_SHA256)
                keep = true
            }
            assertEquals(S06_SKIN_ID, PowerCalSkinRegistry.active.key)
            println("S06_FROM_BLANK_COLD phase=$phase skinSha256=$S06_SKIN_SHA256 " +
                "targetPid=${targetProcessPid(storage)} active=${manager.currentId()} page=${state(web).optString("page")}")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            if (!keep) {
                if (packageOwnedByS06) {
                    val installed = File(storage.filesDir, "skin-packs/$S06_SKIN_ID.pcskin")
                    if (installed.isFile) {
                        assertEquals("refusing to remove a non-S-06 package",
                            S06_SKIN_SHA256, sha256(installed.readBytes()))
                        if (manager.currentId() == S06_SKIN_ID) manager.select("powercal-default")
                        manager.delete(S06_SKIN_ID)
                    }
                }
                restoreS06AppState(
                    manager, selectionPrefs, onboardingPrefs,
                    originalSelectionPresent, originalSelection,
                    originalOnboardingPresent, originalOnboarding
                )
                markerDir.deleteRecursively()
                context.cacheDir.deleteRecursively()
                storage.deleteSharedPreferences("$S06_COLD_NAMESPACE-powercal-onboarding")
            }
        }
    }

    private fun restoreS06AppState(
        manager: SkinPackageManager,
        selectionPrefs: android.content.SharedPreferences,
        onboardingPrefs: android.content.SharedPreferences,
        selectionWasPresent: Boolean,
        previousSelection: String?,
        onboardingWasPresent: Boolean,
        previousOnboarding: Boolean
    ) {
        val validIds = manager.installed().map { it.id }.toSet()
        val restoreId = previousSelection?.takeIf { it in validIds } ?: "powercal-default"
        runCatching { manager.select(restoreId) }.onFailure { manager.select("powercal-default") }
        val selectionEdit = selectionPrefs.edit()
        if (selectionWasPresent && previousSelection != null) {
            selectionEdit.putString("selected", previousSelection)
        } else {
            selectionEdit.remove("selected")
        }
        check(selectionEdit.commit())
        val onboardingEdit = onboardingPrefs.edit()
        if (onboardingWasPresent) {
            onboardingEdit.putBoolean("system-calendar-import-asked", previousOnboarding)
        } else {
            onboardingEdit.remove("system-calendar-import-asked")
        }
        check(onboardingEdit.commit())
    }

    private fun targetProcessPid(context: Context): Int {
        val targetProcess = context.applicationInfo.processName
        return (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .runningAppProcesses.orEmpty()
            .firstOrNull { it.processName == targetProcess }?.pid
            ?: error("PowerCal app process $targetProcess is not running")
    }

    private fun readHttpRequest(socket: java.net.Socket) {
        val input = socket.getInputStream().buffered()
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                require(byte >= 0) { "truncated HTTP request" }
                if (byte == 10) break
                if (byte != 13) bytes.write(byte)
                require(bytes.size() <= 8192) { "oversized HTTP header" }
            }
            return bytes.toString(Charsets.US_ASCII.name())
        }
        require(line().startsWith("POST /v1/chat/completions HTTP/1.1"))
        var length = -1
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                length = header.substringAfter(':').trim().toInt()
            }
        }
        require(length in 1..100_000) { "invalid AI stub request body length" }
        val body = ByteArray(length)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            require(count > 0) { "truncated AI stub body" }
            offset += count
        }
    }

    private fun writeChatCompletion(socket: java.net.Socket, content: String) {
        val body = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("role", "assistant").put("content", content))
            .put("finish_reason", "stop"))).toString().toByteArray(Charsets.UTF_8)
        val headers = ("HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        socket.getOutputStream().apply { write(headers); write(body); flush() }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
