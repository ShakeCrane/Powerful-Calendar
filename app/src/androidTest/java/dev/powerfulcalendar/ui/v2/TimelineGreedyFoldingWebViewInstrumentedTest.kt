package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TimelineGreedyFoldingWebViewInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun alias(name: String) = "$namespace-$name"

        private fun isolatedPath(name: String) =
            storage.getDatabasePath(alias(name)).also {
                check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
            }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = isolatedPath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            isolatedPath(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            isolatedPath(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(alias(name))
    }

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
        val value = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) {
                value.set(it)
                complete.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out", complete.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        val decoded = JSONArray("[$encoded]").getString(0)
        return JSONObject(decoded)
    }

    private fun hostBridgeState(): JSONObject {
        val value = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            value.set(WebSkinBridgeRuntime.state().toString())
            complete.countDown()
        }
        assertTrue("Host bridge state read timed out", complete.await(10, TimeUnit.SECONDS))
        return JSONObject(value.get())
    }

    private fun awaitHostTimelineHours(expected: Double, timeoutMs: Long = 5_000L): JSONObject {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var latest = hostBridgeState()
        while (SystemClock.uptimeMillis() < deadline) {
            val timeline = latest.optJSONObject("timeline")
            if (timeline != null && kotlin.math.abs(timeline.optDouble("hours", Double.NaN) - expected) < 0.001)
                return latest
            Thread.sleep(100)
            latest = hostBridgeState()
        }
        return latest
    }

    private fun timelineSummary(state: JSONObject): JSONObject {
        val timeline = state.getJSONObject("timeline")
        return JSONObject().put("page", state.optString("page"))
            .put("zoneId", timeline.optString("zoneId"))
            .put("hours", timeline.optDouble("hours"))
            .put("anchorMillis", timeline.optLong("anchorMillis"))
            .put("nowMillis", timeline.optLong("nowMillis"))
            .put("visibleStartMillis", timeline.optLong("visibleStartMillis"))
            .put("visibleEndMillis", timeline.optLong("visibleEndMillis"))
            .put("eventCount", timeline.optJSONArray("items")?.length() ?: 0)
    }

    private fun assertCurrentLineMatchesHost(web: WebView, state: JSONObject, contextLabel: String) {
        val timeline = state.getJSONObject("timeline")
        val expected = timeline.getLong("nowMillis") >= timeline.getLong("visibleStartMillis") &&
            timeline.getLong("nowMillis") < timeline.getLong("visibleEndMillis")
        val actual = evaluate(web, "!!document.querySelector('#timeNow .now-line')").toBoolean()
        assertEquals("$contextLabel: current-time marker follows the visible time range",
            expected, actual)
    }

    private fun awaitJs(web: WebView, expression: String, timeoutMs: Long = 30_000L) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val state = runCatching {
            evaluate(web, "JSON.stringify({count:document.querySelectorAll('#timelineRight .tEvent').length," +
                "range:document.querySelector('#timeRangeLabel')?.textContent||''," +
                "touchClickCount:window.__pcTestTouchClickCount??null," +
                "touchProbe:window.__pcTestTouchProbe??null," +
                "portPosts:window.__pcTestPortPosts??[]," +
                "states:Array.from(document.querySelectorAll('#timelineRight .timelineTitle')).map(e=>" +
                "({state:e.dataset.titleFoldState,comparedTo:e.dataset.titleComparedTo,rect:e.dataset.titleScreenRect}))})")
        }.getOrDefault("unavailable")
        throw AssertionError("Timeline WebView condition timed out: " + expression + "; state=" + state)
    }

    private fun saveScreenshot(name: String, web: WebView): File {
        val visualState = CountDownLatch(1)
        compose.runOnUiThread {
            web.postVisualStateCallback(SystemClock.uptimeMillis(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestedState: Long) {
                        visualState.countDown()
                    }
                })
        }
        assertTrue("WebView visual state timed out", visualState.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val file = File(requireNotNull(compose.activity.externalCacheDir), name)
        val bitmap = AtomicReference<Bitmap>()
        val result = AtomicReference<Int>()
        val copied = CountDownLatch(1)
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val frame = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            bitmap.set(frame)
            PixelCopy.request(compose.activity.window, frame, { status ->
                result.set(status)
                copied.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("PixelCopy timed out", copied.await(10, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, requireNotNull(result.get()))
        val frame = requireNotNull(bitmap.get())
        try {
            file.outputStream().use {
                assertTrue("PixelCopy screenshot encoding failed",
                    frame.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            frame.recycle()
        }
        println("S0_TL05_SCREENSHOT=" + file.absolutePath + " bytes=" + file.length())
        return file
    }

    private fun tap(web: WebView, cssX: Double, cssY: Double, cssWidth: Double, cssHeight: Double) {
        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (width, height) = requireNotNull(dimensions.get())
        val x = location[0] + (cssX * width / cssWidth).toFloat()
        val y = location[1] + (cssY * height / cssHeight).toFloat()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 70, MotionEvent.ACTION_UP, x, y, 0)
        try {
            down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun tapElementCenter(web: WebView, selector: String): JSONObject {
        val query = JSONObject.quote(selector)
        evaluate(web, """
            (()=>{
              if(window.__pcTestPortPosts)return true;
              window.__pcTestPortPosts=[];
              const original=MessagePort.prototype.postMessage;
              MessagePort.prototype.postMessage=function(message,...args){
                window.__pcTestPortPosts.push(typeof message==="string"?message:String(message));
                return original.call(this,message,...args);
              };
              return true;
            })()
        """.trimIndent())
        val target = evaluateObject(web, """
            (() => {
              const element=document.querySelector($query);
              if(!element)return {found:false};
              const rect=element.getBoundingClientRect();
              const x=rect.left+rect.width/2,y=rect.top+rect.height/2;
              const hit=document.elementFromPoint(x,y);
              window.__pcTestTouchClickCount=0;
              element.addEventListener("click",()=>window.__pcTestTouchClickCount++,true);
              const probe={found:true,x,y,width:innerWidth,height:innerHeight,
                rect:{left:rect.left,top:rect.top,right:rect.right,bottom:rect.bottom},
                hitTag:hit?.tagName||"",hitAction:hit?.closest("[data-action]")?.dataset.action||"",
                hitTarget:hit===element||hit?.closest("button")===element,
                disabled:element.disabled,display:getComputedStyle(element).display,
                visibility:getComputedStyle(element).visibility,
                touchAction:getComputedStyle(element).touchAction};
              window.__pcTestTouchProbe=probe;
              return probe;
            })()
        """.trimIndent())
        assertTrue("Visible touch target not found: $selector", target.getBoolean("found"))
        val rect = target.getJSONObject("rect")
        assertTrue("Touch target is outside the WebView viewport: $target",
            rect.getDouble("left") >= 0 && rect.getDouble("top") >= 0 &&
                rect.getDouble("right") <= target.getDouble("width") &&
                rect.getDouble("bottom") <= target.getDouble("height"))
        assertTrue("Touch center is not owned by its button: $target", target.getBoolean("hitTarget"))
        assertTrue("Touch target is disabled: $target", !target.getBoolean("disabled"))
        assertEquals("Timeline controls must avoid double-tap click suppression", "manipulation",
            target.optString("touchAction"))
        tap(web, target.getDouble("x"), target.getDouble("y"),
            target.getDouble("width"), target.getDouble("height"))
        val after = evaluateObject(web, """
            (()=>({target:$query,clickCount:window.__pcTestTouchClickCount||0,
              range:document.querySelector("#timeRangeLabel")?.textContent||"",
              portPosts:window.__pcTestPortPosts||[]}))()
        """.trimIndent())
        after.put("touchProbe", target)
        return after
    }

    private fun overlaps(first: JSONObject, second: JSONObject): Boolean =
        first.getDouble("left") < second.getDouble("right") &&
            second.getDouble("left") < first.getDouble("right") &&
            first.getDouble("top") < second.getDouble("bottom") &&
            second.getDouble("top") < first.getDouble("bottom")

    private fun nativeOccurrenceKey(record: TemporalRecord): String {
        val localStart = java.time.Instant.ofEpochMilli(requireNotNull(record.startMillis))
            .atZone(record.zone).toLocalDateTime()
        return "${record.eventSeq}:${RecurrenceEngine.keyFor(localStart)}"
    }

    private fun nativeTitleTag(record: TemporalRecord): String {
        val stableId = "${record.eventSeq}:${nativeOccurrenceKey(record)}"
        return "timeline-title-${record.eventSeq}-${stableId.hashCode().toUInt().toString(16)}"
    }

    private fun nativeEventTag(record: TemporalRecord): String {
        val occurrence = nativeOccurrenceKey(record)
        return "timeline-event-${record.eventSeq}-${occurrence.hashCode().toUInt().toString(16)}"
    }

    @Test
    fun actualClearZoomControlsUpdateHostAndWebViewTimelineScale() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-s0-zoom-" + UUID.randomUUID()
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val store = PowerStore(context, "timeline.db")
        try {
            store.readableDatabase
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            val skinManager = SkinPackageManager(context)
            skinManager.select("powercal-clear-default")
            assertEquals("Clear skin must be selected for the isolated zoom fixture",
                "powercal-clear-default", skinManager.currentId())
            compose.runOnUiThread {
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
            awaitJs(view, "!!window.PowerCalSkin && !!document.querySelector('#timelineSurface')")
            awaitJs(view, "document.querySelector('#timeRangeLabel')?.textContent==='24 小时'")
            val actualAction = AtomicReference<String>()
            compose.runOnUiThread {
                val previous = WebSkinBridgeRuntime.onAction
                WebSkinBridgeRuntime.onAction = { action ->
                    actualAction.set(action.javaClass.simpleName)
                    previous(action)
                }
            }
            val zoomTouch = tapElementCenter(view, "[data-action='zoomTimeline'][data-direction='in']")
            assertEquals("Android touchscreen must click zoom control", 1, zoomTouch.getInt("clickCount"))
            assertTrue("Zoom action was not sent across the host bridge: $zoomTouch",
                zoomTouch.getJSONArray("portPosts").toString().contains("zoomTimeline"))
            val actualHost = awaitHostTimelineHours(16.0)
            val actualHours = actualHost.getJSONObject("timeline").getDouble("hours")
            if (kotlin.math.abs(actualHours - 16.0) >= 0.001) {
                compose.runOnUiThread {
                    WebSkinBridgeRuntime.onAction(WebSkinAction.ZoomTimeline(TimelineZoomDirection.IN))
                }
                val directHost = awaitHostTimelineHours(16.0)
                assertEquals("WebView zoom failed; decoded=${actualAction.get()}, " +
                    "host=${timelineSummary(actualHost)}, directActionHost=${timelineSummary(directHost)}",
                    16.0, actualHours, 0.001)
            }
            val zoomedHost = actualHost
            assertEquals("Host timeline scale changes after zoom input; host=${timelineSummary(zoomedHost)}", 16.0,
                zoomedHost.getJSONObject("timeline").getDouble("hours"), 0.001)
            awaitJs(view, "document.querySelector('#timeRangeLabel')?.textContent==='16 小时'", 8_000L)
            val resetTouch = tapElementCenter(view, "[data-action='resetTimelineZoom']")
            assertEquals("Android touchscreen must click 24-hour reset", 1,
                resetTouch.getInt("clickCount"))
            val resetHost = awaitHostTimelineHours(24.0)
            assertEquals("Host returns to 24-hour scale after reset; host=${timelineSummary(resetHost)}", 24.0,
                resetHost.getJSONObject("timeline").getDouble("hours"), 0.001)
            awaitJs(view, "document.querySelector('#timeRangeLabel')?.textContent==='24 小时'", 8_000L)
            println("S0_TL05_ZOOM_API35=" + JSONObject().put("zoomHost", timelineSummary(zoomedHost))
                .put("resetHost", timelineSummary(resetHost)).put("zoomTouch", zoomTouch)
                .put("resetTouch", resetTouch))
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase("timeline.db")
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-skin-registry",
                "powercal-ui", "powercal-profile", "powercal-ai", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun actualClearTimelineFoldsOnlyCollidingMeasuredTitlesAndKeepsEventTapAndGeometry() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-s0-tl05-" + UUID.randomUUID()
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "timeline.db"
        val store = PowerStore(context, databaseName)
        val titles = listOf("Fold_A", "Fold_B", "Fold_C", "Fold_D")
        val zone = ZoneId.systemDefault()
        val noon = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val starts = listOf(noon, noon + 60_000L, noon + 7_200_000L, noon + 7_260_000L)
        val records = mutableListOf<dev.powerfulcalendar.core.TemporalRecord>()

        try {
            store.readableDatabase
            titles.forEachIndexed { index, title ->
                val start = starts[index]
                val form = DraftForm(
                    kind = TemporalKind.ACTIVITY,
                    title = title,
                    startText = DraftForm.formatInstant(start, zone),
                    endText = DraftForm.formatInstant(start + 60_000L, zone),
                    zoneId = zone.id
                )
                val outcome = store.createFormalEvent(
                    form,
                    idempotencyKey = "s0-tl05-" + UUID.randomUUID()
                )
                assertTrue("Fixture event should commit: " + title,
                    outcome is PowerStore.CommitOutcome.Committed)
                val committed = outcome as PowerStore.CommitOutcome.Committed
                records += requireNotNull(store.get(committed.eventSeq))
            }
            val commitsBefore = store.commitCount()
            val snapshotBefore = records.map { requireNotNull(store.get(it.eventSeq)) }

            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()

            // Replay this exact persisted fixture through the Native PowerCalApp route first.
            assertTrue("Native baseline should persist in the isolated fixture",
                SkinPackageManager(context).selectNativeForEmergencyRecovery())
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides context) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            fun nativeNodeExists(tag: String) = runCatching {
                compose.onAllNodesWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
            compose.waitUntil(30_000) {
                records.all { nativeNodeExists(nativeEventTag(it)) }
            }
            val nativeVisible = titles.filter { name ->
                val record = records[titles.indexOf(name)]
                nativeNodeExists(nativeTitleTag(record))
            }
            assertEquals("Native should use the same visible lead titles as Clear",
                listOf("Fold_A", "Fold_C"), nativeVisible)
            for ((index, name) in titles.withIndex()) {
                val record = records[index]
                val eventNode = compose.onNodeWithTag(nativeEventTag(record), useUnmergedTree = true)
                eventNode.assertExists().assertHasClickAction()
                val descriptions = eventNode.fetchSemanticsNode()
                    .config[SemanticsProperties.ContentDescription]
                assertTrue("Native event keeps the full accessible title: " + name,
                    descriptions.any { name in it })
                val titleNode = compose.onNodeWithTag(nativeTitleTag(record), useUnmergedTree = true)
                if (name in nativeVisible) titleNode.assertExists() else titleNode.assertDoesNotExist()
            }
            val nativeRectangles = nativeVisible.associateWith { name ->
                val record = records[titles.indexOf(name)]
                val bounds = compose.onNodeWithTag(nativeTitleTag(record), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                JSONObject().put("left", bounds.left).put("top", bounds.top)
                    .put("right", bounds.right).put("bottom", bounds.bottom)
            }
            val nativeEvidence = JSONObject()
                .put("visibleTitles", JSONArray(nativeVisible))
                .put("viewportPx", JSONObject().put("width", compose.activity.window.decorView.width)
                    .put("height", compose.activity.window.decorView.height))
                .put("density", compose.activity.resources.displayMetrics.density)
                .put("titleBoundsRootPx", JSONObject(nativeRectangles))
            println("S0_TL05_NATIVE_API35_TRACE=" + nativeEvidence)

            SkinPackageManager(context).select("powercal-clear-default")

            compose.runOnUiThread {
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
            awaitJs(view, "!!window.PowerCalSkin && !!document.querySelector('#timelineSurface')")
            awaitJs(view,
                "document.querySelectorAll('#timelineRight .tEvent').length===4 && " +
                    "['Fold_A','Fold_B','Fold_C','Fold_D'].every(name=>" +
                    "Array.from(document.querySelectorAll('#timelineRight .tEvent'))" +
                    ".some(button=>button.dataset.eventTitle===name))")
            awaitJs(view,
                "(()=>{const state=name=>Array.from(document.querySelectorAll('#timelineRight .tEvent'))" +
                    ".find(button=>button.dataset.eventTitle===name)?.querySelector('.timelineTitle')?.dataset.titleFoldState;" +
                    "return state('Fold_A')==='visible'&&state('Fold_B')==='folded'&&" +
                    "state('Fold_C')==='visible'&&state('Fold_D')==='folded'})()")

            val evidence = evaluateObject(view, """
                (() => {
                  const names = ["Fold_A","Fold_B","Fold_C","Fold_D"];
                  const rect = element => {
                    const r = element.getBoundingClientRect();
                    return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height};
                  };
                  const items = names.map(name => {
                    const button = Array.from(document.querySelectorAll("#timelineRight .tEvent"))
                      .find(candidate => candidate.dataset.eventTitle === name);
                    const title = button.querySelector(".timelineTitle");
                    const visual = button.querySelector(".tEventVisual");
                    return {
                      name, seq:Number(button.dataset.eventSeq), lane:Number(button.dataset.lane),
                      state:title.dataset.titleFoldState, comparedTo:title.dataset.titleComparedTo || "",
                      stableId:title.dataset.titleFoldId, source:title.dataset.titleSource,
                      measured:title.dataset.titleMeasuredRect, screen:title.dataset.titleScreenRect,
                      aria:button.getAttribute("aria-label"), titleAriaHidden:title.getAttribute("aria-hidden"),
                      titlePointerEvents:getComputedStyle(title).pointerEvents,
                      button:rect(button), visual:rect(visual),
                      start:Number(button.dataset.startMillis), end:Number(button.dataset.endMillis),
                      from:Number(button.dataset.visibleStartMillis), to:Number(button.dataset.visibleEndMillis),
                      geometry:button.dataset.temporalGeometry
                    };
                  });
                  const surface = rect(document.querySelector("#timelineSurface"));
                  return {
                    items, surface, viewport:{width:innerWidth,height:innerHeight},
                    axisTicks:document.querySelectorAll("#timeLabels .timeTick").length,
                    currentLine:!!document.querySelector("#timeNow .now-line"),
                    eventButtons:document.querySelectorAll("#timelineLeft .tEvent,#timelineRight .tEvent").length
                  };
                })()
            """.trimIndent())
            val items = evidence.getJSONArray("items")
            assertEquals(4, items.length())
            val byName = (0 until items.length()).associate { index ->
                val item = items.getJSONObject(index)
                item.getString("name") to item
            }
            val clearVisible = titles.filter { byName.getValue(it).getString("state") == "visible" }
            assertEquals("Native and Clear render the same visible greedy leads for one stored fixture",
                nativeVisible, clearVisible)
            val a = byName.getValue("Fold_A")
            val b = byName.getValue("Fold_B")
            val c = byName.getValue("Fold_C")
            val d = byName.getValue("Fold_D")
            val aRect = JSONObject(a.getString("screen"))
            val bRect = JSONObject(b.getString("screen"))
            val cRect = JSONObject(c.getString("screen"))
            val dRect = JSONObject(d.getString("screen"))

            assertEquals("visible", a.getString("state"))
            assertEquals("folded", b.getString("state"))
            assertEquals("visible", c.getString("state"))
            assertEquals("folded", d.getString("state"))
            assertEquals(a.getString("stableId"), b.getString("comparedTo"))
            assertEquals(a.getString("stableId"), c.getString("comparedTo"))
            assertEquals(c.getString("stableId"), d.getString("comparedTo"))
            assertTrue("A/B measured screen text rectangles must overlap", overlaps(aRect, bRect))
            assertTrue("A/C measured screen text rectangles must not overlap", !overlaps(aRect, cRect))
            assertTrue("C/D measured screen text rectangles must overlap", overlaps(cRect, dRect))
            assertEquals("Fold_B", b.getString("source"))
            assertEquals("true", b.getString("titleAriaHidden"))
            assertEquals("none", b.getString("titlePointerEvents"))
            assertEquals(0, a.getInt("lane"))
            assertEquals(0, b.getInt("lane"))
            assertEquals(0, c.getInt("lane"))
            assertEquals(0, d.getInt("lane"))
            assertEquals(4, evidence.getInt("eventButtons"))
            assertTrue("Timeline axis ticks remain rendered", evidence.getInt("axisTicks") > 0)
            assertTrue("Current-time marker remains rendered", evidence.getBoolean("currentLine"))

            val surface = evidence.getJSONObject("surface")
            val viewportSpan = b.getLong("to") - b.getLong("from")
            for (item in listOf(a, b, c, d)) {
                assertEquals("ACTIVITY uses true interval geometry", "interval", item.getString("geometry"))
                assertTrue("Accessible event name retains full title",
                    item.getString("aria").contains(item.getString("name")))
                assertTrue("Event interval must remain positive", item.getLong("end") > item.getLong("start"))
                val expectedTop = surface.getDouble("top") +
                    (item.getLong("start") - item.getLong("from")).toDouble() /
                    viewportSpan * surface.getDouble("height")
                val expectedBottom = surface.getDouble("top") +
                    (item.getLong("end") - item.getLong("from")).toDouble() /
                    viewportSpan * surface.getDouble("height")
                val visual = item.getJSONObject("visual")
                assertTrue("True start geometry changed for " + item.getString("name"),
                    kotlin.math.abs(visual.getDouble("top") - expectedTop) <= 1.25)
                assertTrue("True end geometry changed for " + item.getString("name"),
                    kotlin.math.abs(visual.getDouble("bottom") - expectedBottom) <= 1.25)
            }
            assertEquals("Adjacent A/B intervals stay adjacent", a.getLong("end"), b.getLong("start"))
            assertEquals("Adjacent C/D intervals stay adjacent", c.getLong("end"), d.getLong("start"))
            assertTrue("A/B/C/D event intervals do not overlap", b.getLong("end") <= c.getLong("start"))

            val evidenceSuffix = namespace.takeLast(8)
            val screenshot = saveScreenshot("s0-tl05-greedy-folding-api35-$evidenceSuffix.png", view)

            fun reflowTrace(): JSONObject = evaluateObject(view, """
                (() => {
                  const names=["Fold_A","Fold_B","Fold_C","Fold_D"];
                  const right=document.querySelector("#timelineRight");
                  const axis=document.querySelector("#axisScroll");
                  const surface=document.querySelector("#timelineSurface");
                  return {
                    range:document.querySelector("#timeRangeLabel").textContent,
                    rightScroll:{left:right.scrollLeft,width:right.scrollWidth,clientWidth:right.clientWidth},
                    axisScroll:{top:axis.scrollTop,height:axis.scrollHeight,clientHeight:axis.clientHeight},
                    surface:{top:surface.getBoundingClientRect().top,height:surface.getBoundingClientRect().height},
                    items:names.map(name=>{
                      const button=Array.from(document.querySelectorAll("#timelineRight .tEvent"))
                        .find(candidate=>candidate.dataset.eventTitle===name);
                      const title=button.querySelector(".timelineTitle");
                      return {name,state:title.dataset.titleFoldState,
                        stableId:title.dataset.titleFoldId||"",
                        comparedTo:title.dataset.titleComparedTo||"",
                        measured:title.dataset.titleMeasuredRect||"",
                        screen:title.dataset.titleScreenRect||""};
                    })
                  };
                })()
            """.trimIndent())
            fun item(trace: JSONObject, name: String): JSONObject {
                val values = trace.getJSONArray("items")
                return (0 until values.length()).map { values.getJSONObject(it) }
                    .first { it.getString("name") == name }
            }
            fun assertGreedyLeadTrace(trace: JSONObject, contextLabel: String) {
                val traceA = item(trace, "Fold_A")
                val traceB = item(trace, "Fold_B")
                val traceC = item(trace, "Fold_C")
                val traceD = item(trace, "Fold_D")
                assertEquals("$contextLabel: A remains a visible lead", "visible", traceA.getString("state"))
                assertEquals("$contextLabel: B folds against A", "folded", traceB.getString("state"))
                assertEquals("$contextLabel: C becomes the next lead", "visible", traceC.getString("state"))
                assertEquals("$contextLabel: D folds against C", "folded", traceD.getString("state"))
                assertEquals("$contextLabel: B references A", traceA.getString("stableId"),
                    traceB.getString("comparedTo"))
                assertEquals("$contextLabel: D references C", traceC.getString("stableId"),
                    traceD.getString("comparedTo"))
            }
            fun assertMeasuredRectsMatchBaseline(trace: JSONObject, contextLabel: String) {
                for (name in titles) {
                    val baseline = JSONObject(byName.getValue(name).getString("measured"))
                    val actualText = item(trace, name).getString("measured")
                    assertTrue("$contextLabel: measured title rect missing for $name", actualText.isNotEmpty())
                    val actual = JSONObject(actualText)
                    for (edge in listOf("left", "top", "right", "bottom")) {
                        assertEquals("$contextLabel: restored $edge for $name", baseline.getDouble(edge),
                            actual.getDouble(edge), 1.0)
                    }
                }
            }
            fun compactTrace(trace: JSONObject): JSONObject {
                val values = trace.getJSONArray("items")
                val compactItems = JSONArray()
                for (index in 0 until values.length()) {
                    val item = values.getJSONObject(index)
                    val measured = item.getString("measured")
                    val screen = item.getString("screen")
                    compactItems.put(JSONObject().put("name", item.getString("name"))
                        .put("state", item.getString("state"))
                        .put("stableId", item.getString("stableId"))
                        .put("comparedTo", item.getString("comparedTo"))
                        .put("measured", if (measured.isEmpty()) JSONObject.NULL else JSONObject(measured))
                        .put("screen", if (screen.isEmpty()) JSONObject.NULL else JSONObject(screen)))
                }
                return JSONObject().put("range", trace.getString("range"))
                    .put("rightScroll", trace.getJSONObject("rightScroll"))
                    .put("axisScroll", trace.getJSONObject("axisScroll"))
                    .put("items", compactItems)
            }

            val baselineRightLanes = evaluateObject(view, """
                (()=>{const right=document.querySelector("#timelineRight");
                  const lanes=right.querySelector(".timelineLanes");
                  return {width:lanes.style.width,clientWidth:right.clientWidth};})()
            """.trimIndent())
            val horizontalSetup = evaluateObject(view, """
                (()=>{const right=document.querySelector("#timelineRight");
                  const lanes=right.querySelector(".timelineLanes");
                  lanes.style.width=(right.clientWidth+360)+"px";
                  right.scrollLeft=8;
                  return {left:right.scrollLeft,max:right.scrollWidth-right.clientWidth};})()
            """.trimIndent())
            assertTrue("Right wing fixture must have actual horizontal scroll range",
                horizontalSetup.getInt("max") >= 300)
            awaitJs(view, "(()=>{const title=document.querySelector('#timelineRight [data-event-title=\"Fold_A\"] .timelineTitle');" +
                "return document.querySelector('#timelineRight').scrollLeft===8&&" +
                "title.dataset.titleFoldState==='visible'&&" +
                "Number(JSON.parse(title.dataset.titleMeasuredRect).left)<" +
                JSONObject(byName.getValue("Fold_A").getString("measured")).getDouble("left") + "-7})()")
            val horizontalScrolled = reflowTrace()
            assertGreedyLeadTrace(horizontalScrolled, "horizontal scroll")
            val baselineALeft = JSONObject(byName.getValue("Fold_A").getString("measured")).getDouble("left")
            val scrolledALeft = JSONObject(item(horizontalScrolled, "Fold_A").getString("measured")).getDouble("left")
            assertEquals("Horizontal scroll updates title coordinates in screen space", baselineALeft - 8.0,
                scrolledALeft, 1.0)

            evaluate(view, "(()=>{const right=document.querySelector('#timelineRight');" +
                "right.scrollLeft=right.scrollWidth;return right.scrollLeft})()")
            awaitJs(view, "Array.from(document.querySelectorAll('#timelineRight .timelineTitle'))" +
                ".length===4&&Array.from(document.querySelectorAll('#timelineRight .timelineTitle'))" +
                ".every(title=>title.dataset.titleFoldState==='clipped')")
            val horizontalClipped = reflowTrace()
            assertEquals("Horizontal clipping preserves all four event targets", 4,
                evaluate(view, "document.querySelectorAll('#timelineRight .tEvent').length").toInt())

            evaluate(view, "(()=>{const right=document.querySelector('#timelineRight');" +
                "right.scrollLeft=0;right.querySelector('.timelineLanes').style.width=" +
                JSONObject.quote(baselineRightLanes.getString("width")) + ";return true})()")
            awaitJs(view, "(()=>{const items=Array.from(document.querySelectorAll('#timelineRight .timelineTitle'));" +
                "return items.length===4&&items.every(title=>['visible','folded'].includes(title.dataset.titleFoldState))})()")
            val horizontalRestored = reflowTrace()
            assertGreedyLeadTrace(horizontalRestored, "horizontal return")
            assertMeasuredRectsMatchBaseline(horizontalRestored, "horizontal return")

            val baselineATop = JSONObject(byName.getValue("Fold_A").getString("measured")).getDouble("top")
            val verticalSetup = evaluateObject(view, """
                (()=>{const axis=document.querySelector("#axisScroll");
                  const surface=document.querySelector("#timelineSurface");
                  const oldHeight=surface.style.height;
                  surface.style.height=(axis.clientHeight+320)+"px";
                  axis.scrollTop=120;
                  return {oldHeight:oldHeight,top:axis.scrollTop};})()
            """.trimIndent())
            assertEquals("Vertical scroll fixture reaches requested offset", 120, verticalSetup.getInt("top"))
            awaitJs(view, "(()=>{const axis=document.querySelector('#axisScroll');" +
                "const title=document.querySelector('#timelineRight [data-event-title=\"Fold_A\"] .timelineTitle');" +
                "return axis.scrollTop===120&&title.dataset.titleFoldState==='visible'&&" +
                "Number(JSON.parse(title.dataset.titleMeasuredRect).top)<" +
                JSONObject(byName.getValue("Fold_A").getString("measured")).getDouble("top") + "-119})()")
            val verticalScrolled = reflowTrace()
            assertGreedyLeadTrace(verticalScrolled, "vertical scroll")
            val scrolledATop = JSONObject(item(verticalScrolled, "Fold_A").getString("measured")).getDouble("top")
            assertEquals("Vertical scroll updates title coordinates in screen space", baselineATop - 120.0,
                scrolledATop, 1.0)
            evaluate(view, "document.querySelector('#axisScroll').scrollTop=0")
            awaitJs(view, "(()=>{const axis=document.querySelector('#axisScroll');" +
                "const title=document.querySelector('#timelineRight [data-event-title=\"Fold_A\"] .timelineTitle');" +
                "return axis.scrollTop===0&&title.dataset.titleFoldState==='visible'&&" +
                "Math.abs(Number(JSON.parse(title.dataset.titleMeasuredRect).top)-" + baselineATop + ")<1})()")
            val verticalRestored = reflowTrace()
            assertGreedyLeadTrace(verticalRestored, "vertical return")
            assertMeasuredRectsMatchBaseline(verticalRestored, "vertical return")
            evaluate(view, "document.querySelector('#timelineSurface').style.height=" +
                JSONObject.quote(verticalSetup.getString("oldHeight")))

            val actionSeen = AtomicReference<String>()
            compose.runOnUiThread {
                val previous = WebSkinBridgeRuntime.onAction
                WebSkinBridgeRuntime.onAction = { action ->
                    actionSeen.set(action.javaClass.simpleName)
                    previous(action)
                }
            }
            val zoomTouch = tapElementCenter(view, "[data-action='zoomTimeline'][data-direction='in']")
            println("S0_TL05_ZOOM_TOUCH=" + zoomTouch)
            assertEquals("Android touchscreen must dispatch a real click to zoom in", 1,
                zoomTouch.getInt("clickCount"))
            val zoomPosts = zoomTouch.getJSONArray("portPosts")
            assertTrue("Zoom click must cross the WebView host bridge: $zoomTouch", zoomPosts.length() > 0)
            val zoomMessage = JSONObject(zoomPosts.getString(zoomPosts.length() - 1))
            assertEquals("action", zoomMessage.getString("type"))
            assertEquals("zoomTimeline", zoomMessage.getJSONObject("payload").getString("name"))
            val zoomHostState = awaitHostTimelineHours(16.0)
            assertEquals("Zoom click reaches the host action decoder", "ZoomTimeline", actionSeen.get())
            assertEquals("Zoom action updates host timeline scale; host=${timelineSummary(zoomHostState)} " +
                "action=${actionSeen.get()}", 16.0,
                zoomHostState.getJSONObject("timeline").getDouble("hours"), 0.001)
            awaitJs(view, "document.querySelector('#timeRangeLabel')?.textContent==='16 小时'", 8_000L)
            awaitJs(view, "(()=>{const items=Array.from(document.querySelectorAll('#timelineRight .timelineTitle'));" +
                "return items.length===4&&items.every(title=>['visible','folded'].includes(title.dataset.titleFoldState))})()")
            val zoomed = reflowTrace()
            assertGreedyLeadTrace(zoomed, "16-hour zoom")
            assertTrue("Zoom keeps central time axis and ticks",
                evaluate(view, "!!document.querySelector('#tlAxis')&&" +
                    "document.querySelectorAll('#timeLabels .timeTick').length>0").toBoolean())
            assertCurrentLineMatchesHost(view, zoomHostState, "16-hour zoom")

            val resetTouch = tapElementCenter(view, "[data-action='resetTimelineZoom']")
            println("S0_TL05_RESET_TOUCH=" + resetTouch)
            assertEquals("Android touchscreen must dispatch a real click to 24-hour reset", 1,
                resetTouch.getInt("clickCount"))
            awaitJs(view, "document.querySelector('#timeRangeLabel')?.textContent==='24 小时'", 8_000L)
            awaitJs(view, "(()=>{const items=Array.from(document.querySelectorAll('#timelineRight .timelineTitle'));" +
                "return items.length===4&&items.every(title=>['visible','folded'].includes(title.dataset.titleFoldState))})()")
            val reset24Hour = reflowTrace()
            assertGreedyLeadTrace(reset24Hour, "24-hour reset")
            assertMeasuredRectsMatchBaseline(reset24Hour, "24-hour reset")
            assertCurrentLineMatchesHost(view, hostBridgeState(), "24-hour reset")
            println("S0_TL05_REFLOW_HSCROLL=" + compactTrace(horizontalScrolled))
            println("S0_TL05_REFLOW_HCLIPPED=" + compactTrace(horizontalClipped))
            println("S0_TL05_REFLOW_HRESTORED=" + compactTrace(horizontalRestored))
            println("S0_TL05_REFLOW_VSCROLL=" + compactTrace(verticalScrolled))
            println("S0_TL05_REFLOW_VRESTORED=" + compactTrace(verticalRestored))
            println("S0_TL05_REFLOW_ZOOM16=" + compactTrace(zoomed))
            println("S0_TL05_REFLOW_RESET24=" + compactTrace(reset24Hour))

            val query = JSONObject.quote("Fold_B")
            val tapData = evaluateObject(view, """
                (() => {
                  const button = Array.from(document.querySelectorAll("#timelineRight .tEvent"))
                    .find(candidate => candidate.dataset.eventTitle === $query);
                  const title = button.querySelector(".timelineTitle");
                  const r = JSON.parse(title.dataset.titleScreenRect);
                  const hit = document.elementFromPoint(r.left + 2, r.top + 2);
                  const actualRect = element => {
                    const box = element.getBoundingClientRect();
                    return {left:box.left,top:box.top,right:box.right,bottom:box.bottom};
                  };
                  return {
                    hitTag:hit?.tagName || "",
                    hitClass:typeof hit?.className === "string" ? hit.className : "",
                    hitSeq:hit?.closest(".tEvent")?.dataset.eventSeq || "",
                    hitButton:hit?.closest(".tEvent") === button,
                    titleRect:r, buttonRect:actualRect(button),
                    cssX:r.left + 2, cssY:r.top + 2, cssWidth:innerWidth, cssHeight:innerHeight
                  };
                })()
            """.trimIndent())
            assertEquals("BUTTON", tapData.getString("hitTag"))
            assertTrue("Folded title must expose its event button to pointer hit testing: " + tapData + "",
                tapData.getBoolean("hitButton"))
            tap(view, tapData.getDouble("cssX"), tapData.getDouble("cssY"),
                tapData.getDouble("cssWidth"), tapData.getDouble("cssHeight"))
            awaitJs(view,
                "document.querySelector('#skinSheet')?.hidden===false && " +
                    "document.querySelector('#sheetFields [data-field=title]')?.value==='Fold_B'")
            assertEquals("Opening the folded event must not write a formal revision",
                snapshotBefore, records.map { requireNotNull(store.get(it.eventSeq)) })
            assertEquals("Opening the folded event must not add a commit",
                commitsBefore, store.commitCount())
            println("S0_TL05_API35_TRACE=" + evidence)
            println("S0_TL05_API35_HIT=" + tapData + " screenshot=" + screenshot.absolutePath)
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            listOf(
                "powercal-onboarding", "powercal-skin-choice", "powercal-skin-registry",
                "powercal-ui", "powercal-profile", "powercal-ai", "powercal-file-ai-consent"
            ).forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }
}
