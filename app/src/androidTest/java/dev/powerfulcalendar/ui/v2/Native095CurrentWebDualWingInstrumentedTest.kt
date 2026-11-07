package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.PixelCopy
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.CalendarWindowQueryV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real WebView parity check hosted in a clean ComponentActivity, separate from MainActivity's Store. */
@RunWith(AndroidJUnit4::class)
class Native095CurrentWebDualWingInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Counts(val drafts: Long, val commits: Long, val identities: Long)

    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply { check(mkdirs() || isDirectory) }
        private fun alias(name: String) = "$namespace-$name"
        private fun ensureDatabase(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = ensureDatabase(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            ensureDatabase(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            ensureDatabase(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(alias(name))
        fun deleteRoot() { root.deleteRecursively() }
    }

    @Test
    fun currentWebV3StartsBothWingsAtOuterEdgesAndKeepsThemIndependentlyTouchable() {
        val storage = compose.activity.applicationContext
        val namespace = "native095-web-dual-wing-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "dual-wing-fixture.db"
        val store = PowerStore(context, databaseName)
        val originalSkin = PowerCalSkinRegistry.active
        var web: WebView? = null
        try {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now(zone)
            val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            val end = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            val sequences = linkedMapOf<String, Long>()
            for (lane in 0..3) {
                val title = "WING_LEFT_$lane"
                val created = store.createFormalEvent(
                    DraftForm(
                        kind = TemporalKind.ALARM,
                        title = title,
                        startText = DraftForm.formatInstant(start, zone),
                        reminderMinutes = -1,
                        zoneId = zone.id
                    ), "native095-web-left-$lane-$namespace"
                ) as? PowerStore.CommitOutcome.Committed
                    ?: throw AssertionError("Could not seed $title")
                sequences[title] = created.eventSeq
            }
            for (lane in 0..3) {
                val title = "WING_RIGHT_$lane"
                val created = store.createFormalEvent(
                    DraftForm(
                        kind = TemporalKind.ACTIVITY,
                        title = title,
                        startText = DraftForm.formatInstant(start, zone),
                        endText = DraftForm.formatInstant(end, zone),
                        reminderMinutes = -1,
                        zoneId = zone.id
                    ), "native095-web-right-$lane-$namespace"
                ) as? PowerStore.CommitOutcome.Committed
                    ?: throw AssertionError("Could not seed $title")
                sequences[title] = created.eventSeq
            }
            val query = CalendarWindowQueryV1(
                date.minusDays(8).atStartOfDay(zone).toInstant().toEpochMilli(),
                date.plusDays(9).atStartOfDay(zone).toInstant().toEpochMilli(),
                zone.id
            )
            val core = PowerCalCoreFeatureApiV1(store)
            assertEquals((0..3).flatMap { listOf("WING_LEFT_$it", "WING_RIGHT_$it") }.toSet(),
                core.calendar.queryWindow(query).events.map { it.title }.toSet())
            val seededCounts = counts(store)

            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            ActiveVisualSkin.install()
            SkinPackageManager(context).select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.enableEdgeToEdge()
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides context) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                web = findWebView(compose.activity.findViewById(android.R.id.content))
                web != null
            }
            val actualWeb = requireNotNull(web)
            awaitJs(actualWeb, "!!(window.PowerCalSkin && document.querySelector(\"[data-action='today']\"))")
            awaitJs(actualWeb, "document.querySelectorAll('#timelineRight .tEvent').length===4")
            awaitJs(actualWeb, "Array.from(document.querySelectorAll('#timelineRight .tEvent')).some(e=>e.dataset.lane==='1')")

            val left = wingSnapshot(actualWeb, "LEFT")
            val right = wingSnapshot(actualWeb, "RIGHT")
            val leftSeq = requireNotNull(sequences["WING_LEFT_3"])
            val rightSeq = requireNotNull(sequences["WING_RIGHT_1"])
            val leftEvent = eventSnapshot(actualWeb, "LEFT", leftSeq)
            val rightEvent = eventSnapshot(actualWeb, "RIGHT", rightSeq)
            assertEquals("Frozen Native starts the left wing at its outer lane edge", 0.0,
                left.getDouble("scrollLeft"), 1.0)
            assertEquals("Frozen Native starts the right wing at lane zero", 0.0,
                right.getDouble("scrollLeft"), 1.0)
            assertEquals("Left outer lane begins at the outer viewport edge", left.getDouble("left"),
                leftEvent.getDouble("eventLeft"), 1.5)
            assertEquals(3, leftEvent.getInt("lane"))
            assertEquals(1, rightEvent.getInt("lane"))
            assertTrue(left.getDouble("maxScroll") > 100.0)
            assertTrue(right.getDouble("maxScroll") > 100.0)

            val webOrigin = IntArray(2)
            val webSize = AtomicReference<Pair<Int, Int>>()
            compose.runOnUiThread {
                actualWeb.getLocationOnScreen(webOrigin)
                webSize.set(actualWeb.width to actualWeb.height)
            }
            val (webWidthPx, webHeightPx) = requireNotNull(webSize.get())
            val scaleX = webWidthPx / left.getDouble("viewportWidth")
            val scaleY = webHeightPx / left.getDouble("viewportHeight")
            val leftScreen = JSONObject()
                .put("left", webOrigin[0] + leftEvent.getDouble("eventLeft") * scaleX)
                .put("top", webOrigin[1] + leftEvent.getDouble("eventTop") * scaleY)
                .put("width", (leftEvent.getDouble("eventRight") - leftEvent.getDouble("eventLeft")) * scaleX)
                .put("height", (leftEvent.getDouble("eventBottom") - leftEvent.getDouble("eventTop")) * scaleY)
            val rightScreenLeft = webOrigin[0] + rightEvent.getDouble("eventLeft") * scaleX
            assertEquals("Frozen raw APK left event x", 32.0, leftScreen.getDouble("left"), 4.0)
            assertEquals("Frozen raw APK left event width", 221.0, leftScreen.getDouble("width"), 4.0)
            assertEquals("Frozen raw APK event y at the captured local time", 1047.0,
                leftScreen.getDouble("top"), 15.0)
            assertEquals("Frozen raw APK right event x", 824.0, rightScreenLeft, 4.0)
            assertEquals("Frozen raw APK left wing width", 456.0, left.getDouble("width") * scaleX, 4.0)
            assertEquals("Frozen raw APK right wing x", 593.0,
                webOrigin[0] + right.getDouble("left") * scaleX, 4.0)

            val screenshot = captureScreenshot("native095-web-v3-dual-wing.png", actualWeb)
            println("NATIVE095_WEB_DUAL_WING skin=${PowerCalSkinRegistry.active.key} date=$date " +
                "zone=${zone.id} screen=${compose.activity.window.decorView.width}x" +
                "${compose.activity.window.decorView.height} left=$left leftLane3=$leftEvent " +
                "right=$right rightLane1=$rightEvent webOrigin=${webOrigin.contentToString()} " +
                "webSize=${webWidthPx}x${webHeightPx} leftPhysical=$leftScreen " +
                "rightPhysicalLeft=$rightScreenLeft screenshot=$screenshot")

            tapEvent(actualWeb, "RIGHT", rightSeq)
            awaitJs(actualWeb, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value==='WING_RIGHT_1'")
            assertEquals("Opening a right-wing event is read-only", seededCounts, counts(store))
            tapSelector(actualWeb, "#sheetClose")
            awaitJs(actualWeb, "document.querySelector('#skinSheet').hidden")
            assertEquals(seededCounts, counts(store))

            tapEvent(actualWeb, "LEFT", leftSeq)
            awaitJs(actualWeb, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value==='WING_LEFT_3'")
            assertEquals("Opening a left-wing point event is read-only", seededCounts, counts(store))
            tapSelector(actualWeb, "#sheetClose")
            awaitJs(actualWeb, "document.querySelector('#skinSheet').hidden")
            assertEquals(seededCounts, counts(store))

            val beforeLeft = wingSnapshot(actualWeb, "LEFT")
            val beforeRight = wingSnapshot(actualWeb, "RIGHT")
            swipeWing(actualWeb, "LEFT")
            val afterLeft = wingSnapshot(actualWeb, "LEFT")
            val rightUnchanged = wingSnapshot(actualWeb, "RIGHT")
            assertTrue("Left wing must reveal inner lanes independently",
                afterLeft.getDouble("scrollLeft") > beforeLeft.getDouble("scrollLeft") + 10.0)
            assertEquals(beforeRight.getDouble("scrollLeft"), rightUnchanged.getDouble("scrollLeft"), 1.0)
            swipeWing(actualWeb, "RIGHT")
            val afterRight = wingSnapshot(actualWeb, "RIGHT")
            val leftUnchanged = wingSnapshot(actualWeb, "LEFT")
            assertTrue("Right wing must scroll independently",
                afterRight.getDouble("scrollLeft") > beforeRight.getDouble("scrollLeft") + 10.0)
            assertEquals(afterLeft.getDouble("scrollLeft"), leftUnchanged.getDouble("scrollLeft"), 1.0)
            assertEquals(seededCounts, counts(store))
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.activate(originalSkin)
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-skin-registry",
                "powercal-ui", "powercal-profile", "powercal-ai", "powercal-file-ai-consent")
                .forEach { storage.getSharedPreferences("$namespace-$it", Context.MODE_PRIVATE)
                    .edit().clear().commit() }
            context.deleteRoot()
        }
    }

    private fun counts(store: PowerStore) = Counts(store.draftCount(), store.commitCount(), store.identityCount())

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        compose.runOnUiThread { web.evaluateJavascript(expression) { value -> result.set(value); done.countDown() } }
        assertTrue("WebView script timed out: $expression", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONObject(JSONArray("[$encoded]").getString(0))
    }

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = evaluate(web, """JSON.stringify({readyState:document.readyState,
          title:document.title,page:document.body?.dataset?.page,text:document.body?.innerText?.slice(0,500),
          html:document.documentElement?.outerHTML?.slice(0,1200)})""")
        val runtime = AtomicReference<Pair<String?, Int>>()
        compose.runOnUiThread { runtime.set(web.url to web.progress) }
        val (url, progress) = requireNotNull(runtime.get())
        throw AssertionError("WebView condition timed out: $expression; " +
            "skin=${PowerCalSkinRegistry.active.key} url=$url progress=$progress " +
            "diagnostic=$diagnostic")
    }

    private fun wingSnapshot(web: WebView, side: String) = evaluateObject(web, """(() => {
      const wing=document.getElementById(${JSONObject.quote(if (side == "LEFT") "timelineLeft" else "timelineRight")});
      const r=wing.getBoundingClientRect();
      return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:wing.clientWidth,
        height:wing.clientHeight,scrollLeft:wing.scrollLeft,maxScroll:wing.scrollWidth-wing.clientWidth,
        viewportWidth:innerWidth,viewportHeight:innerHeight};
    })()""")

    private fun eventSnapshot(web: WebView, side: String, eventSeq: Long) = evaluateObject(web, """(() => {
      const wing=document.getElementById(${JSONObject.quote(if (side == "LEFT") "timelineLeft" else "timelineRight")});
      const event=wing.querySelector(${JSONObject.quote(".tEvent[data-event-seq='$eventSeq']")});
      const r=event.getBoundingClientRect();
      return {lane:Number(event.dataset.lane),eventLeft:r.left,eventTop:r.top,eventRight:r.right,eventBottom:r.bottom};
    })()""")

    private fun tapSelector(web: WebView, selector: String) {
        val point = evaluateObject(web, """(() => {
          const e=document.querySelector(${JSONObject.quote(selector)});const r=e.getBoundingClientRect();
          return {x:r.left+r.width/2,y:r.top+r.height/2,viewportWidth:innerWidth,viewportHeight:innerHeight};
        })()""")
        sendTouch(web, point.getDouble("x"), point.getDouble("y"),
            point.getDouble("viewportWidth"), point.getDouble("viewportHeight"), 0.0)
    }

    private fun tapEvent(web: WebView, side: String, eventSeq: Long) {
        val event = eventSnapshot(web, side, eventSeq)
        val wing = wingSnapshot(web, side)
        sendTouch(web, (event.getDouble("eventLeft") + event.getDouble("eventRight")) / 2.0,
            (event.getDouble("eventTop") + event.getDouble("eventBottom")) / 2.0,
            wing.getDouble("viewportWidth"), wing.getDouble("viewportHeight"), 0.0)
    }

    private fun swipeWing(web: WebView, side: String) {
        val wing = wingSnapshot(web, side)
        val direction = -1.0
        val x = wing.getDouble("left") + wing.getDouble("width") * if (direction > 0) 0.22 else 0.78
        val y = wing.getDouble("top") + wing.getDouble("height") * 0.8
        sendTouch(web, x, y, wing.getDouble("viewportWidth"), wing.getDouble("viewportHeight"),
            wing.getDouble("width") * 0.38 * direction)
    }

    private fun sendTouch(
        web: WebView, cssX: Double, cssY: Double, viewportWidth: Double, viewportHeight: Double, deltaCssX: Double
    ) {
        val origin = IntArray(2)
        val size = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread { web.getLocationOnScreen(origin); size.set(web.width to web.height) }
        val (widthPx, heightPx) = requireNotNull(size.get())
        val scaleX = widthPx / viewportWidth
        val scaleY = heightPx / viewportHeight
        assertTrue("Web CSS-to-screen scale mismatch: $scaleX/$scaleY", kotlin.math.abs(scaleX - scaleY) < 0.02)
        val startX = origin[0] + (cssX * scaleX).toFloat()
        val startY = origin[1] + (cssY * scaleY).toFloat()
        val endX = startX + (deltaCssX * scaleX).toFloat()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, eventTime: Long, x: Float) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, startY, 0)
            try { event.source = InputDevice.SOURCE_TOUCHSCREEN; instrumentation.sendPointerSync(event) }
            finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, downTime, startX)
        if (deltaCssX != 0.0) {
            repeat(8) { index ->
                SystemClock.sleep(50)
                val fraction = (index + 1) / 8f
                send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(), startX + (endX - startX) * fraction)
            }
            SystemClock.sleep(50)
        }
        send(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), endX)
    }

    private fun captureScreenshot(name: String, web: WebView): String {
        val visual = CountDownLatch(1)
        compose.runOnUiThread {
            web.postVisualStateCallback(SystemClock.uptimeMillis(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { visual.countDown() }
            })
        }
        assertTrue("WebView visual state timed out", visual.await(10, TimeUnit.SECONDS))
        val decor = AtomicReference<View>()
        compose.runOnUiThread { decor.set(compose.activity.window.decorView) }
        val view = requireNotNull(decor.get())
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        val result = AtomicReference<Int>()
        compose.runOnUiThread {
            PixelCopy.request(compose.activity.window, bitmap, { status -> result.set(status); copied.countDown() },
                Handler(Looper.getMainLooper()))
        }
        assertTrue("PixelCopy timed out", copied.await(10, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, requireNotNull(result.get()))
        val file = File(requireNotNull(compose.activity.externalCacheDir), name)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(compose.activity.cacheDir, name).writeBytes(file.readBytes())
        bitmap.recycle()
        return file.absolutePath
    }
}
