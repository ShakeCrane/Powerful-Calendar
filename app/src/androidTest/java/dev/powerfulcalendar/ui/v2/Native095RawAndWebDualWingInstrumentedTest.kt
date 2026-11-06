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
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.MainActivity
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineLayout
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

/**
 * Captures the same deterministic eight-event Timeline fixture from the frozen APK's Native
 * renderer and the current official Web v3 skin. The raw APK case uses a newly created disposable
 * QA AVD's empty default Store; the Web case uses a unique temporary Store removed in finally.
 */
@RunWith(AndroidJUnit4::class)
class Native095RawAndWebDualWingInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private data class StoreCounts(val drafts: Long, val commits: Long, val identities: Long)

    /** Isolate databases, preferences, and app files while retaining the real APK resources. */
    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun alias(name: String) = "$namespace-$name"
        private fun isolatedDatabase(name: String): File =
            storage.getDatabasePath(alias(name)).also {
                check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
            }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = isolatedDatabase(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            isolatedDatabase(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            isolatedDatabase(name)
            return storage.openOrCreateDatabase(alias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(alias(name))
        fun deleteRoot() { root.deleteRecursively() }
    }

    @Test
    fun frozen095NativeApkDualWingFixtureAndEventTouch() = runFixture(
        skinId = "powercal-default",
        screenshotName = "native095-raw-apk-dual-wing.png"
    )

    @Test
    fun currentWebV3DualWingFixtureAndEventTouch() = runFixture(
        skinId = "powercal-clear-default",
        screenshotName = "native095-web-v3-dual-wing.png"
    )

    private fun runFixture(skinId: String, screenshotName: String) {
        val frozenNative = skinId == "powercal-default"
        val storage = compose.activity.applicationContext
        val namespace = "native095-dual-wing-${UUID.randomUUID()}"
        val databaseName = "fixture.db"
        val context = if (frozenNative) null else IsolatedAppContext(compose.activity, storage, namespace)
        val store = if (frozenNative) PowerCalStores.get(storage)
            else PowerStore(requireNotNull(context), databaseName)
        var restoreWebRuntime: (() -> Unit)? = null
        try {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now(zone)
            val expectedTitles = buildSet {
                for (lane in 0..3) add("WING_LEFT_$lane")
                for (lane in 0..3) add("WING_RIGHT_$lane")
            }
            val preexistingFixture = store.all()
            val nativeFixtureSeededDuringThisTest = frozenNative && preexistingFixture.isEmpty()
            if (frozenNative) {
                assertTrue("Disposable raw AVD may contain only this test's exact fixture",
                    preexistingFixture.isEmpty() || preexistingFixture.map { it.title }.toSet() == expectedTitles)
                assertTrue("Raw AVD fixture must have exactly eight rows",
                    preexistingFixture.isEmpty() || preexistingFixture.size == 8)
                assertEquals(0, store.draftCount())
                assertTrue("Raw AVD must not have a prior skin selection",
                    storage.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
                        .getString("selected", null).let { it == null || it == "powercal-default" })
                storage.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                    .edit().putBoolean("system-calendar-import-asked", true).commit()
            }
            val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            val end = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            val sequences = linkedMapOf<String, Long>()
            if (preexistingFixture.isEmpty()) {
                for (lane in 0..3) {
                    val title = "WING_LEFT_$lane"
                    val result = store.createFormalEvent(
                        DraftForm(
                            kind = TemporalKind.ALARM,
                            title = title,
                            startText = DraftForm.formatInstant(start, zone),
                            reminderMinutes = -1,
                            zoneId = zone.id
                        ),
                        "native095-left-$lane"
                    ) as? PowerStore.CommitOutcome.Committed
                        ?: throw AssertionError("Could not seed $title")
                    sequences[title] = result.eventSeq
                }
                for (lane in 0..3) {
                    val title = "WING_RIGHT_$lane"
                    val result = store.createFormalEvent(
                        DraftForm(
                            kind = TemporalKind.ACTIVITY,
                            title = title,
                            startText = DraftForm.formatInstant(start, zone),
                            endText = DraftForm.formatInstant(end, zone),
                            reminderMinutes = -1,
                            zoneId = zone.id
                        ),
                        "native095-right-$lane"
                    ) as? PowerStore.CommitOutcome.Committed
                        ?: throw AssertionError("Could not seed $title")
                    sequences[title] = result.eventSeq
                }
            } else {
                preexistingFixture.forEach { record ->
                    val expectedKind = if (record.title.startsWith("WING_LEFT_")) TemporalKind.ALARM
                        else TemporalKind.ACTIVITY
                    assertEquals(expectedKind, record.kind)
                    sequences[record.title] = record.eventSeq
                }
            }

            val layout = TimelineLayout.layout(store.all())
            assertEquals((0..3).toSet(), layout.items.filter { it.side.name == "LEFT" }.map { it.lane }.toSet())
            assertEquals((0..3).toSet(), layout.items.filter { it.side.name == "RIGHT" }.map { it.lane }.toSet())
            val seedCounts = counts(store)
            assertEquals(8, seedCounts.commits)
            assertEquals(8, seedCounts.identities)
            assertEquals(0, seedCounts.drafts)

            if (nativeFixtureSeededDuringThisTest) {
                compose.runOnUiThread { compose.activity.recreate() }
            } else if (!frozenNative) {
                val webContext = requireNotNull(context)
                webContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                    .edit().putBoolean("system-calendar-import-asked", true).commit()
                webContext.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
                    .edit().putString("selected", skinId).commit()
                assertEquals(skinId, webContext.getSharedPreferences("powercal-skin-choice", Context.MODE_PRIVATE)
                    .getString("selected", "powercal-default"))
                val activeBefore = PowerCalSkinRegistry.active
                restoreWebRuntime = { PowerCalSkinRegistry.activate(activeBefore) }
                SkinPackageManager(webContext).restoreSelection()
                assertEquals(skinId, PowerCalSkinRegistry.active.key)
                assertTrue(PowerCalSkinRegistry.active.web?.fullCanvas == true)
                compose.runOnUiThread {
                    compose.activity.setContent {
                        CompositionLocalProvider(LocalContext provides webContext) {
                            PowerCalTheme { PowerCalApp(store) }
                        }
                    }
                }
            }
            if (frozenNative) {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                        .fetchSemanticsNodes().isNotEmpty()
                }
            } else {
                compose.waitUntil(30_000) { findWebView(compose.activity.window.decorView) != null }
            }
            var web: WebView? = null
            if (skinId == "powercal-default") {
                compose.onNodeWithContentDescription("今日 · 复位为全天").performTouchInput { click() }
                compose.waitUntil(20_000) {
                    compose.onAllNodes(hasContentDescription("WING_RIGHT_1", substring = true) and
                        hasClickAction()).fetchSemanticsNodes().isNotEmpty()
                }
            } else {
                web = findWebView(compose.activity.window.decorView)
                    ?: throw AssertionError("Current skin did not attach a WebView")
                waitForJs(web, "!!document.querySelector(\"[data-action='today']\")")
                tapWeb(web, "[data-action='today']")
                waitForJs(web, "!!document.querySelector('#timelineRight .tEvent')")
                waitForJs(web, "Array.from(document.querySelectorAll('#timelineRight .tEvent')).some(e=>e.dataset.lane==='1')")
            }

            val rootOffset = IntArray(2)
            val viewport = compose.activity.findViewById<View>(android.R.id.content)
            viewport.getLocationOnScreen(rootOffset)
            val statusInset = compose.activity.window.decorView.rootWindowInsets
                ?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: -1
            val leftTitle = "WING_LEFT_3"
            val leftSeq = requireNotNull(sequences[leftTitle])
            val rightSeq = requireNotNull(sequences["WING_RIGHT_1"])
            val leftRecord = store.all().single { it.title == leftTitle }
            val leftGeometry: JSONObject
            val rightGeometry: JSONObject
            val leftRange: JSONObject
            val rightRange: JSONObject
            if (skinId == "powercal-default") {
                val left = nativeBounds(leftTitle)
                val right = nativeBounds("WING_RIGHT_1")
                val leftWing = compose.onNodeWithTag("timeline-left-lanes").fetchSemanticsNode().boundsInRoot
                val rightWing = compose.onNodeWithTag("timeline-right-lanes").fetchSemanticsNode().boundsInRoot
                val leftScroll = scrollRange("timeline-left-lanes")
                val rightScroll = scrollRange("timeline-right-lanes")
                leftGeometry = rectJson(left, rootOffset)
                rightGeometry = rectJson(right, rootOffset)
                val titleBounds = nativeTitleBounds(leftRecord)
                leftRange = JSONObject().put("min", leftScroll.first).put("max", leftScroll.second)
                rightRange = JSONObject().put("min", rightScroll.first).put("max", rightScroll.second)
                val screenshot = saveScreenshot(screenshotName)
                println("NATIVE095_CAPTURE skin=$skinId date=$date zone=${zone.id} " +
                    "screen=${viewport.width}x${viewport.height} statusInset=$statusInset " +
                    "leftWing=${rectJson(leftWing, rootOffset)} rightWing=${rectJson(rightWing, rootOffset)} " +
                    "leftEvent=$leftGeometry leftTitle=${rectJson(titleBounds, rootOffset)} " +
                    "rightEvent=$rightGeometry leftScroll=$leftRange " +
                    "rightScroll=$rightRange sequences=$sequences screenshot=${screenshot.absolutePath}")

                assertEquals(seedCounts, counts(store))
                swipeNative("timeline-right-lanes", -1)
                compose.waitForIdle()
                val rightAfter = nativeBounds("WING_RIGHT_1")
                val leftAfterRightSwipe = nativeBounds(leftTitle)
                assertTrue("Native right wing did not scroll: before=$right after=$rightAfter",
                    rightAfter.left < right.left - 10f)
                assertEquals(left.left, leftAfterRightSwipe.left, 1f)
                assertEquals(left.right, leftAfterRightSwipe.right, 1f)
                swipeNative("timeline-left-lanes", -1)
                compose.waitForIdle()
                val leftAfter = nativeBounds(leftTitle)
                val rightAfterLeftSwipe = nativeBounds("WING_RIGHT_1")
                assertTrue("Native left wing did not scroll: before=$leftAfterRightSwipe after=$leftAfter",
                    leftAfter.right < leftAfterRightSwipe.right - 10f)
                assertEquals(leftAfterRightSwipe.left, leftAfter.left, 1f)
                assertEquals(rightAfter.left, rightAfterLeftSwipe.left, 1f)
                assertEquals(rightAfter.right, rightAfterLeftSwipe.right, 1f)
            } else {
                val actualWeb = requireNotNull(web)
                val screenshot = saveScreenshot(screenshotName, actualWeb)
                val runtime = evaluate(actualWeb, "JSON.stringify({ready:document.readyState,title:document.title,href:location.href,hasLeft:!!document.getElementById('timelineLeft'),hasRight:!!document.getElementById('timelineRight')})")
                File(requireNotNull(compose.activity.externalCacheDir), "native095-web-runtime.json")
                    .writeText(runtime)
                leftGeometry = webEventSnapshot(actualWeb, "LEFT", leftSeq)
                rightGeometry = webEventSnapshot(actualWeb, "RIGHT", rightSeq)
                val pointLabel = webPointLabelSnapshot(actualWeb, "LEFT", leftSeq)
                assertEquals("Native ALARM title font size", "13px", pointLabel.getString("fontSize"))
                assertEquals("Native ALARM title line height", "24px", pointLabel.getString("lineHeight"))
                assertEquals("Native ALARM title letter spacing", "0.5px", pointLabel.getString("letterSpacing"))
                assertEquals("Native ALARM title color", "rgb(61, 95, 216)", pointLabel.getString("color"))
                assertEquals("Native ALARM title box height in CSS pixels", 24.0,
                    pointLabel.getDouble("labelHeight"), 0.1)
                assertEquals("Native ALARM title top relative to point-line top", 2.75,
                    pointLabel.getDouble("labelTopMinusLineTop"), 0.1)
                leftRange = wingSnapshot(actualWeb, "LEFT")
                rightRange = wingSnapshot(actualWeb, "RIGHT")
                assertEquals("Frozen Native left wing starts at its outer edge", 0.0,
                    leftRange.getDouble("scrollLeft"), 1.0)
                assertEquals("Frozen Native right wing starts at its outer edge", 0.0,
                    rightRange.getDouble("scrollLeft"), 1.0)
                val webRect = webViewRect(actualWeb)
                val scaleX = (webRect.getDouble("right") - webRect.getDouble("left")) /
                    leftRange.getDouble("viewportWidth")
                val scaleY = (webRect.getDouble("bottom") - webRect.getDouble("top")) /
                    leftRange.getDouble("viewportHeight")
                assertEquals("Native ALARM title screen x", 40.0,
                    webRect.getDouble("left") + pointLabel.getDouble("labelLeft") * scaleX, 1.5)
                assertEquals("Native ALARM title screen y", 1047.0,
                    webRect.getDouble("top") + pointLabel.getDouble("labelTop") * scaleY, 1.5)
                assertEquals("Native ALARM title physical width", 186.0,
                    pointLabel.getDouble("labelWidth") * scaleX, 2.0)
                assertEquals("Native ALARM title physical height", 63.0,
                    pointLabel.getDouble("labelHeight") * scaleY, 1.5)
                assertEquals("Native reference left event screen x", 32.0,
                    webRect.getDouble("left") + leftGeometry.getDouble("eventLeft") * scaleX, 4.0)
                assertEquals("Native reference right event screen x", 824.0,
                    webRect.getDouble("left") + rightGeometry.getDouble("eventLeft") * scaleX, 4.0)
                println("NATIVE095_CAPTURE skin=$skinId date=$date zone=${zone.id} " +
                    "screen=${viewport.width}x${viewport.height} statusInset=$statusInset " +
                    "webView=${webViewRect(actualWeb)} leftEvent=$leftGeometry " +
                    "leftPointTitle=$pointLabel " +
                    "rightEvent=$rightGeometry leftScroll=$leftRange rightScroll=$rightRange " +
                    "sequences=$sequences screenshot=${screenshot.absolutePath}")

                tapWebEvent(actualWeb, "RIGHT", rightSeq)
                waitForJs(actualWeb, "!document.querySelector('#skinSheet').hidden")
                waitForJs(actualWeb, "document.querySelector('#sheetTitle').textContent.includes('编辑')")
                assertEquals(seedCounts, counts(store))
                tapWeb(actualWeb, "#sheetClose")
                waitForJs(actualWeb, "document.querySelector('#skinSheet').hidden")
                assertEquals(seedCounts, counts(store))
                val rightBefore = webEventSnapshot(actualWeb, "RIGHT", rightSeq)
                val leftBefore = webEventSnapshot(actualWeb, "LEFT", leftSeq)
                val rightAfter = swipeWeb(actualWeb, "RIGHT", rightSeq, -1)
                val leftUnchanged = webEventSnapshot(actualWeb, "LEFT", leftSeq)
                assertTrue("Web right wing did not scroll: before=$rightBefore after=$rightAfter",
                    rightAfter.getDouble("eventLeft") < rightBefore.getDouble("eventLeft") - 10.0)
                assertEquals(leftBefore.getDouble("eventLeft"), leftUnchanged.getDouble("eventLeft"), 1.0)
                val leftAfter = swipeWeb(actualWeb, "LEFT", leftSeq, -1)
                val rightUnchanged = webEventSnapshot(actualWeb, "RIGHT", rightSeq)
                assertTrue("Web left wing did not scroll: before=$leftBefore after=$leftAfter",
                    leftAfter.getDouble("eventLeft") < leftBefore.getDouble("eventLeft") - 10.0)
                assertEquals(rightAfter.getDouble("eventLeft"), rightUnchanged.getDouble("eventLeft"), 1.0)
            }
            assertEquals(seedCounts, counts(store))
            println("NATIVE095_FIXTURE_RESULT skin=$skinId date=$date zone=${zone.id} " +
                "records=${store.all().size} counts=$seedCounts writesOutsideFixture=0 " +
                "leftEvent=$leftGeometry rightEvent=$rightGeometry leftScroll=$leftRange rightScroll=$rightRange")
        } finally {
            if (!frozenNative) {
                val webContext = requireNotNull(context)
                compose.runOnUiThread { compose.activity.setContent { } }
                compose.waitForIdle()
                restoreWebRuntime?.invoke()
                store.close()
                webContext.deleteDatabase(databaseName)
                listOf("powercal-onboarding", "powercal-ui", "powercal-skin-choice", "powercal-profile")
                    .forEach { storage.getSharedPreferences("$namespace-$it", Context.MODE_PRIVATE).edit().clear().commit() }
                webContext.deleteRoot()
            }
        }
    }

    private fun counts(store: PowerStore) = StoreCounts(
        drafts = store.draftCount(), commits = store.commitCount(), identities = store.identityCount()
    )

    private fun nativeBounds(title: String): Rect {
        val nodes = compose.onAllNodes(hasContentDescription(title, substring = true) and hasClickAction())
            .fetchSemanticsNodes()
        assertEquals("Expected exactly one visible/clickable Native event $title", 1, nodes.size)
        return nodes.single().boundsInRoot
    }

    private fun nativeTitleBounds(record: TemporalRecord): Rect {
        val localStart = java.time.Instant.ofEpochMilli(requireNotNull(record.startMillis))
            .atZone(record.zone).toLocalDateTime()
        val occurrenceKey = "${record.eventSeq}:${RecurrenceEngine.keyFor(localStart)}"
        val stableId = "${record.eventSeq}:$occurrenceKey"
        val tag = "timeline-title-${record.eventSeq}-${stableId.hashCode().toUInt().toString(16)}"
        return compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    }

    private fun scrollRange(tag: String): Pair<Float, Float> {
        val axis = compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        return axis.value() to axis.maxValue()
    }

    private fun rectJson(rect: Rect, origin: IntArray) = JSONObject()
        .put("left", origin[0] + rect.left).put("top", origin[1] + rect.top)
        .put("right", origin[0] + rect.right).put("bottom", origin[1] + rect.bottom)

    private fun swipeNative(tag: String, direction: Int) {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val origin = IntArray(2)
        compose.activity.findViewById<View>(android.R.id.content).getLocationOnScreen(origin)
        val startFraction = if (direction < 0) 0.78f else 0.22f
        val startX = origin[0] + bounds.left + bounds.width * startFraction
        val endX = startX + bounds.width * 0.38f * direction
        val y = origin[1] + bounds.top + bounds.height * 0.80f
        val command = "input swipe ${startX.toInt()} ${y.toInt()} ${endX.toInt()} ${y.toInt()} 600"
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun webViewRect(web: WebView): JSONObject {
        val origin = IntArray(2)
        web.getLocationOnScreen(origin)
        return JSONObject().put("left", origin[0]).put("top", origin[1])
            .put("right", origin[0] + web.width).put("bottom", origin[1] + web.height)
    }

    private fun evaluate(web: WebView, script: String): String {
        val result = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread { web.evaluateJavascript(script) { value -> result.set(value); complete.countDown() } }
        assertTrue("WebView script timed out", complete.await(10, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        val decoded = JSONArray("[$encoded]").get(0)
        if (decoded !is String || decoded == "null") {
            throw AssertionError("WebView did not return a JSON object (result=$encoded, expression=$expression)")
        }
        return JSONObject(decoded)
    }

    private fun waitForJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        throw AssertionError("Web condition timed out: $expression")
    }

    private fun webEventSnapshot(web: WebView, side: String, eventSeq: Long) = evaluateObject(web, """(() => {
      const id=${JSONObject.quote(if (side == "LEFT") "timelineLeft" else "timelineRight")};
      const wing=document.getElementById(id);
      const event=wing?.querySelector(${JSONObject.quote(".tEvent[data-event-seq='$eventSeq']")});
      if(!wing||!event)return {found:false,id,eventSeq:${eventSeq},hasWing:!!wing};
      const r=event.getBoundingClientRect();
      return {found:true,eventSeq:${eventSeq},lane:Number(event.dataset.lane),eventLeft:r.left,eventTop:r.top,
        eventRight:r.right,eventBottom:r.bottom,eventWidth:r.width,eventHeight:r.height,
        scrollLeft:wing.scrollLeft,maxScroll:wing.scrollWidth-wing.clientWidth};
    })()""")

    private fun webPointLabelSnapshot(web: WebView, side: String, eventSeq: Long) = evaluateObject(web, """(() => {
      const wing=document.getElementById(${JSONObject.quote(if (side == "LEFT") "timelineLeft" else "timelineRight")});
      const event=wing?.querySelector(${JSONObject.quote(".tEvent[data-event-seq='$eventSeq']")});
      const label=event?.querySelector(".tPointLabel"),line=event?.querySelector(".tPointLine");
      if(!event||!label||!line)return {found:false};
      const r=label.getBoundingClientRect(),l=line.getBoundingClientRect(),s=getComputedStyle(label);
      return {found:true,labelLeft:r.left,labelTop:r.top,labelWidth:r.width,labelHeight:r.height,
        fontSize:s.fontSize,lineHeight:s.lineHeight,letterSpacing:s.letterSpacing,color:s.color,
        labelTopMinusLineTop:r.top-l.top,
        lineTop:l.top,lineHeightPx:l.height};
    })()""")

    private fun wingSnapshot(web: WebView, side: String) = evaluateObject(web, """(() => {
      const wing=document.getElementById(${JSONObject.quote(if (side == "LEFT") "timelineLeft" else "timelineRight")});
      const r=wing.getBoundingClientRect();
      return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:wing.clientWidth,
        height:wing.clientHeight,scrollLeft:wing.scrollLeft,maxScroll:wing.scrollWidth-wing.clientWidth,
        viewportWidth:innerWidth,viewportHeight:innerHeight};
    })()""")

    private fun tapWeb(web: WebView, selector: String) {
        val point = evaluateObject(web, """(() => {
          const e=document.querySelector(${JSONObject.quote(selector)});if(!e)return {found:false};
          const r=e.getBoundingClientRect();return {found:true,x:r.left+r.width/2,y:r.top+r.height/2,
            width:r.width,height:r.height,viewportWidth:innerWidth,viewportHeight:innerHeight};
        })()""")
        assertTrue("Missing Web target $selector: $point", point.optBoolean("found"))
        sendWebPointer(web, point.getDouble("x"), point.getDouble("y"), point.getDouble("viewportWidth"),
            point.getDouble("viewportHeight"), 0.0, 0.0)
    }

    private fun tapWebEvent(web: WebView, side: String, eventSeq: Long) {
        val snap = webEventSnapshot(web, side, eventSeq)
        assertTrue("Web event not found: $snap", snap.optBoolean("found"))
        val viewport = wingSnapshot(web, side)
        val x = (snap.getDouble("eventLeft") + snap.getDouble("eventRight")) / 2.0
        val y = (snap.getDouble("eventTop") + snap.getDouble("eventBottom")) / 2.0
        sendWebPointer(web, x, y, viewport.getDouble("viewportWidth"), viewport.getDouble("viewportHeight"), 0.0, 0.0)
    }

    private fun swipeWeb(web: WebView, side: String, eventSeq: Long, direction: Int): JSONObject {
        val before = wingSnapshot(web, side)
        assertTrue("Wing has no horizontal overflow: $before", before.getDouble("maxScroll") > 20.0)
        val x = before.getDouble("left") + before.getDouble("width") * if (direction < 0) 0.78 else 0.22
        val y = before.getDouble("top") + before.getDouble("height") * 0.8
        sendWebPointer(web, x, y, before.getDouble("viewportWidth"), before.getDouble("viewportHeight"),
            before.getDouble("width") * 0.38 * direction, 0.0)
        waitForJs(web, "document.getElementById(${JSONObject.quote(if (side == "LEFT") "timelineLeft" else "timelineRight")}).scrollLeft " +
            if (direction < 0) "> 2" else "< ${before.getDouble("scrollLeft")} - 2")
        return webEventSnapshot(web, side, eventSeq)
    }

    private fun sendWebPointer(
        web: WebView,
        cssX: Double,
        cssY: Double,
        viewportWidth: Double,
        viewportHeight: Double,
        deltaCssX: Double,
        deltaCssY: Double
    ) {
        val origin = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread { web.getLocationOnScreen(origin); dimensions.set(web.width to web.height) }
        val (widthPx, heightPx) = requireNotNull(dimensions.get())
        val scaleX = widthPx / viewportWidth
        val scaleY = heightPx / viewportHeight
        assertTrue("Web CSS-to-screen scales differ: $scaleX/$scaleY", kotlin.math.abs(scaleX - scaleY) < 0.02)
        val startX = origin[0] + (cssX * scaleX).toFloat()
        val startY = origin[1] + (cssY * scaleY).toFloat()
        val endX = startX + (deltaCssX * scaleX).toFloat()
        val endY = startY + (deltaCssY * scaleY).toFloat()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, eventTime: Long, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event)
            } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, downTime, startX, startY)
        if (deltaCssX != 0.0 || deltaCssY != 0.0) {
            repeat(8) { index ->
                SystemClock.sleep(90)
                val f = (index + 1) / 8f
                send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(),
                    startX + (endX - startX) * f, startY + (endY - startY) * f)
            }
            SystemClock.sleep(90)
        }
        send(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), endX, endY)
    }

    private fun saveScreenshot(name: String, web: WebView? = null): File {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        if (web != null) {
            val visualStateReady = CountDownLatch(1)
            compose.runOnUiThread {
                web.postVisualStateCallback(SystemClock.uptimeMillis(), object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        visualStateReady.countDown()
                    }
                })
            }
            assertTrue("WebView visual state timed out", visualStateReady.await(10, TimeUnit.SECONDS))
            SystemClock.sleep(250)
        }
        val file = File(requireNotNull(compose.activity.externalCacheDir), name)
        val bitmap = AtomicReference<Bitmap>()
        val result = AtomicReference<Int>()
        val completed = CountDownLatch(1)
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val frame = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            bitmap.set(frame)
            PixelCopy.request(compose.activity.window, frame, { status ->
                result.set(status); completed.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("PixelCopy timed out", completed.await(10, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, requireNotNull(result.get()))
        val frame = requireNotNull(bitmap.get())
        try { file.outputStream().use { assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { frame.recycle() }
        return file
    }
}
