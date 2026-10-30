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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
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

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val state = runCatching {
            evaluate(web, "JSON.stringify({count:document.querySelectorAll('#timelineRight .tEvent').length," +
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

    private fun overlaps(first: JSONObject, second: JSONObject): Boolean =
        first.getDouble("left") < second.getDouble("right") &&
            second.getDouble("left") < first.getDouble("right") &&
            first.getDouble("top") < second.getDouble("bottom") &&
            second.getDouble("top") < first.getDouble("bottom")

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

            val screenshot = saveScreenshot("s0-tl05-greedy-folding-api35.png", view)
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