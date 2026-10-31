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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.PowerStore
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
}
