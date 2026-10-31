package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.AxisSide
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class TimelineReturnViewportInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private data class CameraSnapshot(
        val page: String,
        val anchorMillis: Long,
        val hours: Double
    )

    /** App resources/system services come from the target Activity; all app-owned files are isolated. */
    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory) { "unable to create isolated instrumentation storage" }
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun isolatedDatabasePath(name: String): File =
            storage.getDatabasePath(databaseAlias(name)).also {
                check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true) {
                    "unable to create isolated instrumentation database directory"
                }
            }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File =
            File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = isolatedDatabasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(databaseAlias(name))
    }

    private fun cameraSnapshot(): CameraSnapshot {
        var snapshot: CameraSnapshot? = null
        compose.runOnIdle {
            val appState = WebSkinBridgeRuntime.state()
            val timeline = appState.getJSONObject("timeline")
            snapshot = CameraSnapshot(
                page = appState.getString("page"),
                anchorMillis = timeline.getLong("anchorMillis"),
                hours = timeline.getDouble("hours")
            )
        }
        return requireNotNull(snapshot)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val finished = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value -> result.set(value); finished.countDown() }
        }
        assertTrue("WebView script callback should complete", finished.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONObject(JSONArray("[$encoded]").getString(0))
    }

    private fun tapWebSelector(web: WebView, selector: String): JSONObject {
        val selectorLiteral = JSONObject.quote(selector)
        val probe = evaluateObject(web, """(() => {
          const element=document.querySelector($selectorLiteral);
          if(!element)return {found:false};
          const rect=element.getBoundingClientRect();
          const style=getComputedStyle(element);
          return {found:true,x:rect.left+rect.width/2,y:rect.top+rect.height/2,
            width:rect.width,height:rect.height,cssWidth:document.documentElement.clientWidth,
            cssHeight:window.innerHeight,visible:style.display!=='none'&&style.visibility!=='hidden'&&
              rect.width>0&&rect.height>0};
        })()""")
        assertTrue("Clear target should exist and be visible: $selector, $probe",
            probe.optBoolean("found") && probe.optBoolean("visible"))
        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (widthPx, heightPx) = requireNotNull(dimensions.get())
        val scaleX = widthPx / probe.getDouble("cssWidth")
        val scaleY = heightPx / probe.getDouble("cssHeight")
        assertEquals("WebView touch coordinates preserve aspect ratio", scaleX, scaleY, 0.02)
        val x = location[0] + (probe.getDouble("x") * scaleX).toFloat()
        val y = location[1] + (probe.getDouble("y") * scaleY).toFloat()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, eventTime: Long) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, downTime)
        SystemClock.sleep(80)
        send(MotionEvent.ACTION_UP, SystemClock.uptimeMillis())
        return probe.put("screenX", x).put("screenY", y)
            .put("scaleX", scaleX).put("scaleY", scaleY)
    }

    private fun swipeClearTimelineByMinutes(web: WebView, hours: Double, minutes: Int): JSONObject {
        val probe = evaluateObject(web, """(() => {
          const axis=document.querySelector('#axisScroll');
          if(!axis)return {found:false};
          if(!window.__clearCameraPanProbe){
            const probe={trustedDown:false,pointerType:'',eventClass:'',targetId:'',button:-1,
              sourceTouch:false,moves:0,trustedMove:false,trustedUp:false,cancelled:false,pointerId:-1};
            window.__clearCameraPanProbe=probe;
            axis.addEventListener('pointerdown',event=>{
              probe.trustedDown=event.isTrusted;probe.pointerType=String(event.pointerType);
              probe.eventClass=event.constructor?.name||'';probe.targetId=event.target?.id||'';
              probe.button=event.button;probe.sourceTouch=!!event.sourceCapabilities?.firesTouchEvents;
              probe.pointerId=event.pointerId;
            },{once:true});
            document.addEventListener('pointermove',event=>{
              if(event.pointerId!==probe.pointerId)return;
              probe.moves++;probe.trustedMove=probe.trustedMove||event.isTrusted;
            },true);
            document.addEventListener('pointerup',event=>{
              if(event.pointerId===probe.pointerId)probe.trustedUp=event.isTrusted;
            },true);
            document.addEventListener('pointercancel',event=>{
              if(event.pointerId===probe.pointerId)probe.cancelled=true;
            },true);
          }
          const rect=axis.getBoundingClientRect();
          return {found:true,x:rect.left+rect.width/2,y:rect.top+axis.clientHeight*.75,
            height:axis.clientHeight,cssWidth:document.documentElement.clientWidth,
            cssHeight:window.innerHeight,scrollTop:axis.scrollTop};
        })()""")
        assertTrue("Clear timeline viewport should exist", probe.optBoolean("found"))
        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (widthPx, heightPx) = requireNotNull(dimensions.get())
        val scaleX = widthPx / probe.getDouble("cssWidth")
        val scaleY = heightPx / probe.getDouble("cssHeight")
        assertEquals("WebView touch coordinates preserve aspect ratio", scaleX, scaleY, 0.02)
        val startX = location[0] + (probe.getDouble("x") * scaleX).toFloat()
        val startY = location[1] + (probe.getDouble("y") * scaleY).toFloat()
        val deltaY = (-probe.getDouble("height") * minutes / (hours * 60.0) * scaleY).toFloat()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float, eventTime: Long) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, startX, startY, downTime)
        repeat(8) { index ->
            SystemClock.sleep(120)
            val fraction = (index + 1) / 8f
            send(MotionEvent.ACTION_MOVE, startX, startY + deltaY * fraction,
                SystemClock.uptimeMillis())
        }
        SystemClock.sleep(120)
        send(MotionEvent.ACTION_UP, startX, startY + deltaY, SystemClock.uptimeMillis())
        val result = evaluateObject(web, """(() => {
          const axis=document.querySelector('#axisScroll');
          return {probe:window.__clearCameraPanProbe,scrollTop:axis?.scrollTop};
        })()""")
        val gesture = requireNotNull(result.optJSONObject("probe"))
        assertTrue("Clear must receive a real trusted touchscreen pointerdown", gesture.optBoolean("trustedDown"))
        assertTrue("Clear must identify touchscreen input (API 35 WebView reports an empty " +
            "pointerType for injected SOURCE_TOUCHSCREEN events): $gesture",
            gesture.optString("pointerType") == "touch" || gesture.optString("pointerType").isEmpty())
        assertTrue("Clear must receive trusted pointer movement", gesture.optBoolean("trustedMove"))
        assertTrue("Clear must process the entire slow pan", gesture.optInt("moves") >= 6)
        assertTrue("Clear pan must end normally", gesture.optBoolean("trustedUp"))
        assertTrue("Clear pan must not be handed to browser scrolling", !gesture.optBoolean("cancelled"))
        assertEquals("camera panning must not scroll the timeline DOM", probe.getDouble("scrollTop"),
            result.getDouble("scrollTop"), 0.0)
        return JSONObject().put("minutes", minutes).put("startX", startX).put("startY", startY)
            .put("deltaYpx", deltaY).put("scaleY", scaleY).put("probe", gesture)
    }

    private fun nativeEventBounds(title: String): Rect {
        val matcher = hasContentDescription(title, substring = true) and hasClickAction()
        val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
        assertEquals("Native fixture event should be uniquely accessible: $title", 1, nodes.size)
        return nodes.single().boundsInRoot
    }

    private fun nativeHorizontalScrollRange(testTag: String): Pair<Float, Float> {
        val range = compose.onNodeWithTag(testTag).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        return range.value() to range.maxValue()
    }

    private fun clearWingSnapshot(web: WebView, side: String, eventSeq: Long): JSONObject {
        val wingId = if (side == "LEFT") "timelineLeft" else "timelineRight"
        val expression = """(() => {
          const wing=document.querySelector(${JSONObject.quote("#$wingId")});
          const event=wing?.querySelector(${JSONObject.quote(".tEvent[data-event-seq='$eventSeq']")});
          if(!wing||!event)return {found:false,wing:"$wingId",eventSeq:$eventSeq};
          const rect=event.getBoundingClientRect();
          return {found:true,wing:"$wingId",eventSeq:$eventSeq,scrollLeft:wing.scrollLeft,
            maxScroll:wing.scrollWidth-wing.clientWidth,clientWidth:wing.clientWidth,
            clientHeight:wing.clientHeight,eventLeft:rect.left,eventTop:rect.top,
            eventWidth:rect.width,lane:Number(event.dataset.lane),axisScrollTop:
              document.querySelector('#axisScroll').scrollTop};
        })()"""
        return evaluateObject(web, expression)
    }

    private fun swipeNativeWing(testTag: String, direction: Int) {
        require(direction == -1 || direction == 1)
        compose.onNodeWithTag(testTag).performTouchInput {
            val y = height * 0.8f
            val dx = width * 0.38f * direction
            down(Offset(width * if (direction < 0) 0.78f else 0.22f, y))
            repeat(8) {
                moveBy(Offset(dx / 8f, 0f))
                advanceEventTime(100)
            }
            up()
        }
    }

    private fun swipeClearWing(web: WebView, side: String, eventSeq: Long, direction: Int): JSONObject {
        require(side == "LEFT" || side == "RIGHT")
        require(direction == -1 || direction == 1)
        val wingId = if (side == "LEFT") "timelineLeft" else "timelineRight"
        val probe = evaluateObject(web, """(() => {
          const wing=document.querySelector(${JSONObject.quote("#$wingId")});
          const event=wing?.querySelector(${JSONObject.quote(".tEvent[data-event-seq='$eventSeq']")});
          if(!wing||!event)return {found:false};
          const rect=wing.getBoundingClientRect();
          return {found:true,left:rect.left,top:rect.top,width:wing.clientWidth,
            height:wing.clientHeight,scrollLeft:wing.scrollLeft,maxScroll:wing.scrollWidth-wing.clientWidth,
            eventLeft:event.getBoundingClientRect().left,
            cssWidth:document.documentElement.clientWidth,cssHeight:window.innerHeight};
        })()""")
        assertTrue("Clear wing event should be rendered: $probe", probe.optBoolean("found"))
        assertTrue("Clear wing needs horizontal overflow before touch: $probe",
            probe.getDouble("maxScroll") > 20.0)
        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (widthPx, heightPx) = requireNotNull(dimensions.get())
        val scaleX = widthPx / probe.getDouble("cssWidth")
        val scaleY = heightPx / probe.getDouble("cssHeight")
        assertEquals("WebView touch coordinates preserve aspect ratio", scaleX, scaleY, 0.02)
        val startCssX = probe.getDouble("left") + probe.getDouble("width") *
            if (direction < 0) 0.78 else 0.22
        val startCssY = probe.getDouble("top") + probe.getDouble("height") * 0.8
        val deltaCssX = probe.getDouble("width") * 0.38 * direction
        val startX = location[0] + (startCssX * scaleX).toFloat()
        val startY = location[1] + (startCssY * scaleY).toFloat()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, eventTime: Long, x: Float) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, startY, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, downTime, startX)
        repeat(8) { step ->
            SystemClock.sleep(100)
            val x = startX + (deltaCssX * (step + 1) / 8.0 * scaleX).toFloat()
            send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(), x)
        }
        SystemClock.sleep(100)
        send(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), startX + (deltaCssX * scaleX).toFloat())
        return probe.put("direction", direction).put("deltaCssX", deltaCssX)
            .put("scaleX", scaleX).put("scaleY", scaleY)
    }

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = runCatching {
            evaluate(web, "JSON.stringify({page:document.querySelector('.page:not([hidden])')?.id," +
                "range:document.querySelector('#timeRangeLabel')?.textContent})")
        }.getOrDefault("unavailable")
        throw AssertionError("Clear condition timed out: $expression; state=$diagnostic")
    }

    private fun launchClear(context: IsolatedAppContext, store: PowerStore): WebView {
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
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
        return requireNotNull(web).also {
            awaitJs(it, "!!window.PowerCalSkin && !!document.querySelector('#timelinePage') && " +
                "!!document.querySelector('[data-action=\\\"today\\\"]')")
        }
    }

    private fun awaitCamera(anchorMillis: Long, hours: Double): CameraSnapshot {
        compose.waitUntil(15_000) {
            val latest = cameraSnapshot()
            latest.anchorMillis == anchorMillis && latest.hours == hours
        }
        return cameraSnapshot()
    }

    @Test
    fun returningFromMemoPreservesTimelineAnchorAndZoom() {
        val testStorage = compose.activity.applicationContext
        val namespace = "pc-issue-153-" + UUID.randomUUID().toString()
        val databaseName = "$namespace.db"
        val isolatedContext = IsolatedAppContext(compose.activity, testStorage, namespace)
        val store = PowerStore(isolatedContext, databaseName)
        isolatedContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()

        try {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolatedContext) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }
            compose.waitForIdle()

            val initial = cameraSnapshot()
            assertEquals("TIMELINE", initial.page)
            assertEquals(24.0, initial.hours, 0.0)

            compose.onNodeWithContentDescription("放大，显示更短时间")
                .assertExists().performClick()
            compose.waitForIdle()

            compose.onNodeWithTag("timeline-viewport").assertExists()
                .performTouchInput { swipeUp() }
            compose.waitForIdle()

            val beforeNavigation = cameraSnapshot()
            assertEquals("TIMELINE", beforeNavigation.page)
            assertEquals(16.0, beforeNavigation.hours, 0.0)
            assertNotEquals("pan action should move the camera anchor", initial.anchorMillis,
                beforeNavigation.anchorMillis)

            compose.onNodeWithContentDescription("Memo 未完成 0")
                .assertExists().performClick()
            compose.onNodeWithText("Memo · 未完成").assertExists()
            compose.waitForIdle()
            assertEquals("MEMO", cameraSnapshot().page)

            compose.onNodeWithContentDescription("返回").assertExists().performClick()
            compose.onNodeWithContentDescription(
                "中央时间轴，左侧闹钟，右侧活动和截止"
            ).assertExists()
            compose.waitForIdle()

            val afterReturn = cameraSnapshot()
            println(
                "TIMELINE_RETURN_CAMERA initial=$initial before=$beforeNavigation after=$afterReturn"
            )
            assertEquals("TIMELINE", afterReturn.page)
            assertEquals("returning from Memo must preserve the panned anchor",
                beforeNavigation.anchorMillis, afterReturn.anchorMillis)
            assertEquals("returning from Memo must preserve zoom",
                beforeNavigation.hours, afterReturn.hours, 0.0)
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            store.close()
            testStorage.deleteDatabase("$namespace-$databaseName")
            listOf("powercal-onboarding", "powercal-ui", "powercal-skin-choice", "powercal-profile")
                .forEach { testStorage.deleteSharedPreferences("$namespace-$it") }
            File(testStorage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun nativeAndClearTodayZoomAndResetProduceTheSameCamera() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-timeline-camera-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-timeline-camera-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "timeline-camera-native.db"
        val clearDatabase = "timeline-camera-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        try {
            val zone = java.time.ZoneId.systemDefault()
            val expectedTodayNoon = java.time.LocalDate.now(zone).atTime(12, 0)
                .atZone(zone).toInstant().toEpochMilli()
            SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
            nativeContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides nativeContext) {
                        PowerCalTheme { PowerCalApp(nativeStore) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天").performClick()
            val nativeToday = awaitCamera(expectedTodayNoon, 24.0)
            assertEquals("Native Today uses local noon", expectedTodayNoon, nativeToday.anchorMillis)
            compose.onNodeWithContentDescription("放大，显示更短时间").performClick()
            val nativeZoomed = awaitCamera(nativeToday.anchorMillis, 16.0)
            assertEquals(nativeToday.anchorMillis, nativeZoomed.anchorMillis)
            compose.onNodeWithContentDescription("双击当前时间刻度尺返回24小时视图")
                .performTouchInput { doubleClick() }
            val nativeReset = awaitCamera(nativeZoomed.anchorMillis, 24.0)
            assertEquals("Native reset changes zoom without moving the anchor",
                nativeZoomed.anchorMillis, nativeReset.anchorMillis)

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()

            val web = launchClear(clearContext, clearStore)
            evaluate(web, "document.querySelector('[data-action=\\\"today\\\"]').click();true")
            val clearToday = awaitCamera(expectedTodayNoon, 24.0)
            assertEquals("Clear Today uses the same local-noon anchor",
                expectedTodayNoon, clearToday.anchorMillis)
            evaluate(web, "document.querySelector('[data-action=\\\"zoomTimeline\\\"]" +
                "[data-direction=\\\"in\\\"]').click();true")
            val clearZoomed = awaitCamera(clearToday.anchorMillis, 16.0)
            assertEquals(clearToday.anchorMillis, clearZoomed.anchorMillis)
            evaluate(web, "document.querySelector('[data-action=\\\"resetTimelineZoom\\\"]').click();true")
            val clearReset = awaitCamera(clearZoomed.anchorMillis, 24.0)
            assertEquals("Clear reset changes zoom without moving the anchor",
                clearZoomed.anchorMillis, clearReset.anchorMillis)

            assertEquals("Native and Clear Today must produce the same camera", nativeToday, clearToday)
            assertEquals("Native and Clear zoom must produce the same camera", nativeZoomed, clearZoomed)
            assertEquals("Native axis double-tap and Clear reset control must agree",
                nativeReset, clearReset)
            println("NATIVE_CLEAR_CAMERA_PARITY today=$nativeToday zoom=$nativeZoomed reset=$nativeReset")
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
                listOf("powercal-onboarding", "powercal-ui", "powercal-skin-choice", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun nativeVerticalPanAndClearLaterControlProduceTheSameCamera() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-timeline-pan-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-timeline-pan-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "timeline-pan-native.db"
        val clearDatabase = "timeline-pan-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        try {
            val zone = java.time.ZoneId.systemDefault()
            val todayNoon = java.time.LocalDate.now(zone).atTime(12, 0)
                .atZone(zone).toInstant().toEpochMilli()
            val expectedLater = todayNoon + 180L * 60_000L
            nativeContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides nativeContext) {
                        PowerCalTheme { PowerCalApp(nativeStore) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天").performClick()
            val nativeToday = awaitCamera(todayNoon, 24.0)
            val nativeViewport = compose.onNodeWithTag("timeline-viewport").fetchSemanticsNode()
            val nativeHeight = nativeViewport.boundsInRoot.height
            assertTrue("Native timeline viewport must have measurable height", nativeHeight > 0f)
            val nativePanDistance = nativeHeight / 8f
            val nativeStart = Offset(nativeViewport.boundsInRoot.center.x,
                nativeViewport.boundsInRoot.top + nativeHeight * 0.75f)
            compose.onNodeWithTag("timeline-viewport").performTouchInput {
                down(Offset(nativeStart.x - nativeViewport.boundsInRoot.left, nativeStart.y -
                    nativeViewport.boundsInRoot.top))
                repeat(8) {
                    moveBy(Offset(0f, -nativePanDistance / 8f))
                    advanceEventTime(250)
                }
                up()
            }
            val nativePanned = awaitCamera(expectedLater, 24.0)
            assertEquals("Native vertical pan maps one eighth of a 24h viewport to 3h",
                expectedLater, nativePanned.anchorMillis)
            assertEquals(nativeToday.page, nativePanned.page)

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            val web = launchClear(clearContext, clearStore)
            val clearTodayTouch = tapWebSelector(web, "[data-action='today']")
            val clearToday = awaitCamera(todayNoon, 24.0)
            assertEquals(nativeToday, clearToday)
            val clearLaterTouch = tapWebSelector(web,
                "[data-action='panTimeline'][data-delta-minutes='180']")
            val clearPanned = awaitCamera(expectedLater, 24.0)
            assertEquals("Native and Clear must produce the same 3-hour later camera",
                nativePanned, clearPanned)
            assertEquals(0, nativeStore.commitCount())
            assertEquals(0, clearStore.commitCount())
            assertEquals(0, nativeStore.draftCount())
            assertEquals(0, clearStore.draftCount())
            println("NATIVE_CLEAR_TIMELINE_PAN_PARITY today=$todayNoon target=$expectedLater " +
                "native=$nativePanned clear=$clearPanned nativeViewportPx=$nativeHeight " +
                "nativeDeltaPx=$nativePanDistance clearTodayTouch=$clearTodayTouch " +
                "clearLaterTouch=$clearLaterTouch commits=0 drafts=0")
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
                listOf("powercal-onboarding", "powercal-ui", "powercal-skin-choice", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun nativeAndClearSingleFingerVerticalPanProduceTheSameCamera() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-timeline-touch-pan-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-timeline-touch-pan-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "timeline-touch-pan-native.db"
        val clearDatabase = "timeline-touch-pan-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val shiftMinutes = 180
        try {
            val zone = java.time.ZoneId.systemDefault()
            val todayNoon = java.time.LocalDate.now(zone).atTime(12, 0)
                .atZone(zone).toInstant().toEpochMilli()
            val targetAnchor = todayNoon + shiftMinutes * 60_000L
            nativeContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides nativeContext) {
                        PowerCalTheme { PowerCalApp(nativeStore) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天").performClick()
            awaitCamera(todayNoon, 24.0)
            compose.onNodeWithContentDescription("放大，显示更短时间").performClick()
            awaitCamera(todayNoon, 16.0)
            val nativeViewport = compose.onNodeWithTag("timeline-viewport").fetchSemanticsNode()
            val nativeHeight = nativeViewport.boundsInRoot.height
            assertTrue("Native timeline viewport must have measurable height", nativeHeight > 0f)
            val nativeDeltaY = nativeHeight * shiftMinutes / (16f * 60f)
            val nativeStart = Offset(nativeViewport.boundsInRoot.center.x,
                nativeViewport.boundsInRoot.top + nativeHeight * 0.75f)
            compose.onNodeWithTag("timeline-viewport").performTouchInput {
                down(Offset(nativeStart.x - nativeViewport.boundsInRoot.left,
                    nativeStart.y - nativeViewport.boundsInRoot.top))
                repeat(8) {
                    moveBy(Offset(0f, -nativeDeltaY / 8f))
                    advanceEventTime(350)
                }
                up()
            }
            println("NATIVE_TIMELINE_TOUCH_PAN cameraAfterTouch=${cameraSnapshot()} " +
                "expectedAnchor=$targetAnchor viewportHeight=$nativeHeight deltaPx=$nativeDeltaY")
            val nativePanned = awaitCamera(targetAnchor, 16.0)

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            val web = launchClear(clearContext, clearStore)
            tapWebSelector(web, "[data-action='today']")
            awaitCamera(todayNoon, 24.0)
            tapWebSelector(web, "[data-action='zoomTimeline'][data-direction='in']")
            awaitCamera(todayNoon, 16.0)
            val touch = swipeClearTimelineByMinutes(web, 16.0, shiftMinutes)
            val clearPanned = awaitCamera(targetAnchor, 16.0)
            assertEquals("single-finger pan must yield the same Native and Clear camera",
                nativePanned, clearPanned)
            assertEquals(0, nativeStore.commitCount())
            assertEquals(0, clearStore.commitCount())
            assertEquals(0, nativeStore.draftCount())
            assertEquals(0, clearStore.draftCount())
            println("NATIVE_CLEAR_TIMELINE_TOUCH_PAN_PARITY today=$todayNoon target=$targetAnchor " +
                "native=$nativePanned clear=$clearPanned nativeViewportPx=$nativeHeight " +
                "nativeDeltaPx=$nativeDeltaY clearTouch=$touch commits=0 drafts=0")
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
                listOf("powercal-onboarding", "powercal-ui", "powercal-skin-choice", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun nativeAndClearLeftAndRightTimelineWingsScrollIndependently() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-timeline-wings-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-timeline-wings-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "timeline-wings-native.db"
        val clearDatabase = "timeline-wings-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        try {
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now(zone)
            val start = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            val end = start + 3_600_000L
            fun seed(store: PowerStore, key: String): Map<String, Long> {
                val result = linkedMapOf<String, Long>()
                for (lane in 0..3) {
                    val title = "WING_LEFT_$lane"
                    val outcome = store.createFormalEvent(
                        DraftForm(kind = TemporalKind.ALARM, title = title,
                            startText = DraftForm.formatInstant(start, zone),
                            reminderMinutes = -1, zoneId = zone.id),
                        "$key-left-$lane"
                    ) as? PowerStore.CommitOutcome.Committed
                        ?: throw AssertionError("Unable to seed left wing event $title")
                    result[title] = outcome.eventSeq
                }
                for (lane in 0..3) {
                    val title = "WING_RIGHT_$lane"
                    val outcome = store.createFormalEvent(
                        DraftForm(kind = TemporalKind.ACTIVITY, title = title,
                            startText = DraftForm.formatInstant(start, zone),
                            endText = DraftForm.formatInstant(end, zone),
                            reminderMinutes = -1, zoneId = zone.id),
                        "$key-right-$lane"
                    ) as? PowerStore.CommitOutcome.Committed
                        ?: throw AssertionError("Unable to seed right wing event $title")
                    result[title] = outcome.eventSeq
                }
                return result
            }
            val nativeSequences = seed(nativeStore, nativeNamespace)
            val clearSequences = seed(clearStore, clearNamespace)
            assertEquals(nativeSequences.keys, clearSequences.keys)
            val nativeLayout = TimelineLayout.layout(nativeStore.all())
            assertEquals("Fixture must force four right-side lanes", (0..3).toSet(),
                nativeLayout.items.filter { it.side == AxisSide.RIGHT }.map { it.lane }.toSet())
            assertEquals("Fixture must force four left-side lanes", (0..3).toSet(),
                nativeLayout.items.filter { it.side == AxisSide.LEFT }.map { it.lane }.toSet())
            val nativeCounts = Triple(nativeStore.draftCount(), nativeStore.commitCount(),
                nativeStore.identityCount())
            val clearCounts = Triple(clearStore.draftCount(), clearStore.commitCount(),
                clearStore.identityCount())

            nativeContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides nativeContext) {
                        PowerCalTheme { PowerCalApp(nativeStore) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天").performClick()
            val todayNoon = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val nativeCamera = awaitCamera(todayNoon, 24.0)
            val rightSeq = requireNotNull(nativeSequences["WING_RIGHT_1"])
            val leftSeq = requireNotNull(nativeSequences["WING_LEFT_0"])
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasContentDescription("WING_RIGHT_1", substring = true) and
                    hasClickAction()).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(hasContentDescription("WING_LEFT_0", substring = true) and
                        hasClickAction()).fetchSemanticsNodes().isNotEmpty()
            }
            val nativeRightBefore = nativeEventBounds("WING_RIGHT_1")
            val nativeLeftBefore = nativeEventBounds("WING_LEFT_0")
            val nativeRightRangeBefore = nativeHorizontalScrollRange("timeline-right-lanes")
            val nativeLeftRangeBefore = nativeHorizontalScrollRange("timeline-left-lanes")
            println("NATIVE_WING_INITIAL rightEvent=$nativeRightBefore rightRange=$nativeRightRangeBefore " +
                "leftEvent=$nativeLeftBefore leftRange=$nativeLeftRangeBefore")
            assertTrue("Native left fixture event must be initially visible: $nativeLeftBefore",
                nativeLeftBefore.width > 0f && nativeLeftBefore.height > 0f)
            assertTrue("Native right fixture event must be initially visible: $nativeRightBefore",
                nativeRightBefore.width > 0f && nativeRightBefore.height > 0f)
            assertTrue("Native left wing starts at its center-proximal maximum: $nativeLeftRangeBefore",
                nativeLeftRangeBefore.second > 0f &&
                    abs(nativeLeftRangeBefore.first - nativeLeftRangeBefore.second) <= 1f)
            assertEquals("Native right wing starts at its outer edge", 0f,
                nativeRightRangeBefore.first, 1f)
            swipeNativeWing("timeline-right-lanes", direction = -1)
            compose.waitUntil(10_000) {
                abs(nativeEventBounds("WING_RIGHT_1").left - nativeRightBefore.left) > 10f
            }
            val nativeRightAfter = nativeEventBounds("WING_RIGHT_1")
            val nativeLeftAfterRightScroll = nativeEventBounds("WING_LEFT_0")
            val nativeRightRangeAfter = nativeHorizontalScrollRange("timeline-right-lanes")
            val nativeLeftRangeAfterRightScroll = nativeHorizontalScrollRange("timeline-left-lanes")
            assertTrue("Native right wing touch must move its lane horizontally",
                nativeRightAfter.left < nativeRightBefore.left - 10f)
            assertTrue("Native right scroll range must change", nativeRightRangeAfter.first >
                nativeRightRangeBefore.first + 10f)
            assertEquals("Native right wing scroll must not move the left wing event",
                nativeLeftBefore.left, nativeLeftAfterRightScroll.left, 1f)
            assertEquals("Native right wing scroll must not change left scroll state",
                nativeLeftRangeBefore, nativeLeftRangeAfterRightScroll)
            assertEquals("Native horizontal scroll leaves the time camera unchanged",
                nativeCamera, cameraSnapshot())

            swipeNativeWing("timeline-left-lanes", direction = 1)
            compose.waitForIdle()
            val nativeLeftAfter = nativeEventBounds("WING_LEFT_0")
            val nativeLeftRangeAfter = nativeHorizontalScrollRange("timeline-left-lanes")
            val nativeRightRangeAfterLeftScroll = nativeHorizontalScrollRange("timeline-right-lanes")
            println("NATIVE_LEFT_WING_SCROLL_TRACE before=$nativeLeftAfterRightScroll " +
                "after=$nativeLeftAfter rangeBefore=$nativeLeftRangeBefore " +
                "rangeAfter=$nativeLeftRangeAfter")
            assertTrue("Native left wing touch must move its lane horizontally",
                nativeLeftAfter.left > nativeLeftAfterRightScroll.left + 10f)
            assertTrue("Native left scroll range must change",
                nativeLeftRangeAfter.first < nativeLeftRangeBefore.first - 10f)
            val nativeRightAfterLeftScroll = nativeEventBounds("WING_RIGHT_1")
            assertEquals("Native left wing scroll must not move the right wing event",
                nativeRightAfter.left, nativeRightAfterLeftScroll.left, 1f)
            assertEquals("Native left wing scroll must not change right scroll state",
                nativeRightRangeAfter, nativeRightRangeAfterLeftScroll)
            assertEquals("Native wing scrolling leaves the time camera unchanged",
                nativeCamera, cameraSnapshot())

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            val web = launchClear(clearContext, clearStore)
            val todayTouch = tapWebSelector(web, "[data-action='today']")
            val clearCamera = awaitCamera(todayNoon, 24.0)
            assertEquals("Native and Clear start the lane replay at the same camera",
                nativeCamera, clearCamera)
            val clearRightSeq = requireNotNull(clearSequences["WING_RIGHT_1"])
            val clearLeftSeq = requireNotNull(clearSequences["WING_LEFT_0"])
            awaitJs(web, "!!document.querySelector('#timelineRight .tEvent[data-event-seq=\"$clearRightSeq\"]')")
            awaitJs(web, "!!document.querySelector('#timelineLeft .tEvent[data-event-seq=\"$clearLeftSeq\"]')")
            val clearRightBefore = clearWingSnapshot(web, "RIGHT", clearRightSeq)
            val clearLeftBefore = clearWingSnapshot(web, "LEFT", clearLeftSeq)
            assertTrue("Clear right wing must expose four-lane overflow: $clearRightBefore",
                clearRightBefore.getDouble("maxScroll") > 20.0)
            assertTrue("Clear left wing must expose four-lane overflow: $clearLeftBefore",
                clearLeftBefore.getDouble("maxScroll") > 20.0)
            assertEquals("Clear left wing initializes at its center-proximal maximum",
                clearLeftBefore.getDouble("maxScroll"), clearLeftBefore.getDouble("scrollLeft"), 1.0)
            assertEquals("Clear right wing initializes at its outer edge", 0.0,
                clearRightBefore.getDouble("scrollLeft"), 1.0)
            assertTrue("Clear lane event fixture must preserve measured lane 1",
                clearRightBefore.getInt("lane") == 1)
            assertTrue("Clear lane event fixture must preserve measured lane 0",
                clearLeftBefore.getInt("lane") == 0)

            val clearRightTouch = swipeClearWing(web, "RIGHT", clearRightSeq, direction = -1)
            awaitJs(web, "document.querySelector('#timelineRight').scrollLeft > " +
                (clearRightBefore.getDouble("scrollLeft") + 2.0))
            val clearRightAfter = clearWingSnapshot(web, "RIGHT", clearRightSeq)
            val clearLeftAfterRightScroll = clearWingSnapshot(web, "LEFT", clearLeftSeq)
            assertTrue("Clear right wing touch must scroll its event toward the center",
                clearRightAfter.getDouble("eventLeft") < clearRightBefore.getDouble("eventLeft") - 10.0)
            assertEquals("Clear right wing scroll must not move left scroll offset",
                clearLeftBefore.getDouble("scrollLeft"),
                clearLeftAfterRightScroll.getDouble("scrollLeft"), 1.0)
            assertEquals("Clear right wing scroll must not move left event geometry",
                clearLeftBefore.getDouble("eventLeft"),
                clearLeftAfterRightScroll.getDouble("eventLeft"), 1.0)
            assertEquals("Clear wing scrolling leaves the time camera unchanged",
                clearCamera, cameraSnapshot())

            val clearLeftTouch = swipeClearWing(web, "LEFT", clearLeftSeq, direction = 1)
            awaitJs(web, "document.querySelector('#timelineLeft').scrollLeft < " +
                (clearLeftBefore.getDouble("scrollLeft") - 2.0))
            val clearLeftAfter = clearWingSnapshot(web, "LEFT", clearLeftSeq)
            val clearRightAfterLeftScroll = clearWingSnapshot(web, "RIGHT", clearRightSeq)
            assertTrue("Clear left wing touch must move its event away from the center",
                clearLeftAfter.getDouble("eventLeft") > clearLeftAfterRightScroll.getDouble("eventLeft") + 10.0)
            assertEquals("Clear left wing scroll must not move right scroll offset",
                clearRightAfter.getDouble("scrollLeft"),
                clearRightAfterLeftScroll.getDouble("scrollLeft"), 1.0)
            assertEquals("Clear left wing scroll must not move right event geometry",
                clearRightAfter.getDouble("eventLeft"),
                clearRightAfterLeftScroll.getDouble("eventLeft"), 1.0)
            assertEquals("Native and Clear wing scrolling leaves equivalent camera state",
                nativeCamera, cameraSnapshot())

            assertEquals(nativeCounts, Triple(nativeStore.draftCount(), nativeStore.commitCount(),
                nativeStore.identityCount()))
            assertEquals(clearCounts, Triple(clearStore.draftCount(), clearStore.commitCount(),
                clearStore.identityCount()))
            assertEquals(nativeStore.all().size, clearStore.all().size)
            println("NATIVE_CLEAR_WING_SCROLL_PARITY camera=$nativeCamera nativeRight=" +
                "before=${nativeRightBefore.left} after=${nativeRightAfter.left} " +
                "nativeLeftBefore=${nativeLeftBefore.left} nativeLeftAfter=${nativeLeftAfter.left} " +
                "clearRightBefore=$clearRightBefore clearRightAfter=$clearRightAfter " +
                "clearLeftBefore=$clearLeftBefore clearLeftAfter=$clearLeftAfter " +
                "clearTodayTouch=$todayTouch clearRightTouch=$clearRightTouch " +
                "clearLeftTouch=$clearLeftTouch formalEvents=${nativeStore.all().size} writes=0")
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
                listOf("powercal-onboarding", "powercal-ui", "powercal-skin-choice", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }
}
