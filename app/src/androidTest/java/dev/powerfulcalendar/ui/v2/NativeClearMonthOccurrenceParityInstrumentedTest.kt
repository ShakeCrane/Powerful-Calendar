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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
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
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NativeClearMonthOccurrenceParityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply { check(mkdirs() || isDirectory) }
        private fun alias(name: String) = "$namespace-$name"
        private fun isolatedPath(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
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
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        compose.runOnUiThread { web.evaluateJavascript(expression) { value -> result.set(value); done.countDown() } }
        assertTrue("WebView JavaScript response timed out", done.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun awaitJs(web: WebView, condition: String) {
        compose.waitUntil(30_000) { evaluate(web, condition) == "true" }
    }

    private fun tapClear(web: WebView, selector: String): JSONObject {
        val selectorLiteral = JSONObject.quote(selector)
        val encoded = evaluate(web, """(()=>{
            const target=document.querySelector($selectorLiteral);
            if(!target)return JSON.stringify({found:false});
            const rect=target.getBoundingClientRect();
            const hit=document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2);
            window.__monthOccurrenceTap=null;
            target.addEventListener('click',event=>{
              window.__monthOccurrenceTap={trusted:event.isTrusted};
            },{once:true,capture:true});
            return JSON.stringify({found:true,x:rect.left+rect.width/2,y:rect.top+rect.height/2,
              cssWidth:innerWidth,cssHeight:innerHeight,
              visible:rect.width>0&&rect.height>0&&rect.left>=0&&rect.top>=0&&
                rect.right<=innerWidth&&rect.bottom<=innerHeight,
              hitTarget:hit===target||target.contains(hit),
              disabled:target.disabled===true||target.getAttribute('aria-disabled')==='true'});
        })()""")
        val target = JSONObject(JSONArray("[$encoded]").getString(0))
        assertTrue("Clear target is not touchable: $selector $target",
            target.optBoolean("found") && target.optBoolean("visible") &&
                target.optBoolean("hitTarget") && !target.optBoolean("disabled"))

        val location = IntArray(2)
        val bounds = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread { web.getLocationOnScreen(location); bounds.set(web.width to web.height) }
        val (width, height) = requireNotNull(bounds.get())
        val scaleX = width / target.getDouble("cssWidth")
        val scaleY = height / target.getDouble("cssHeight")
        assertEquals("WebView input mapping must preserve aspect ratio", scaleX, scaleY, 0.03)
        val x = location[0] + (target.getDouble("x") * scaleX).toFloat()
        val y = location[1] + (target.getDouble("y") * scaleY).toFloat()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Android rejected touchscreen down", automation.injectInputEvent(down, true))
            assertTrue("Android rejected touchscreen up", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        awaitJs(web, "window.__monthOccurrenceTap?.trusted===true")
        return JSONObject(JSONArray("[${evaluate(web, "JSON.stringify(window.__monthOccurrenceTap)")}]" )
            .getString(0))
    }

    @Test
    fun sameSelectedRecurringOccurrenceOpensEquivalentNativeAndClearMonthEditor() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-month-open-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-month-open-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val database = "month-occurrence.db"
        val nativeStore = PowerStore(nativeContext, database)
        val clearStore = PowerStore(clearContext, database)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDate = today.plusDays(1)
        val selectedDate = firstDate.plusDays(1)
        val targetMonth = YearMonth.from(selectedDate)
        val monthSteps = java.time.temporal.ChronoUnit.MONTHS.between(
            YearMonth.from(today), targetMonth
        ).toInt()
        val title = "Native Clear 月历重复实例"
        val firstStart = firstDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val selectedKey = "${selectedDate}T09:00"
        var web: WebView? = null

        fun seed(store: PowerStore) {
            val draft = store.newDraft(DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = title,
                startText = DraftForm.formatInstant(firstStart, zone),
                endText = DraftForm.formatInstant(firstStart + 3_600_000L, zone),
                zoneId = zone.id,
                reminderMinutes = -1,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 4)
            ), actor = "instrumented-test")
            assertTrue(store.commit(draft.draftId, actor = "instrumented-test") is
                PowerStore.CommitOutcome.Committed)
        }

        fun counts(store: PowerStore) = listOf(
            store.draftCount(), store.pendingCount(), store.identityCount(), store.commitCount(),
            store.nextSequencePreview(), store.nextQueueOrderPreview(), store.all().size.toLong()
        )

        try {
            seed(nativeStore)
            seed(clearStore)
            val nativeCountsBeforeOpen = counts(nativeStore)
            val clearCountsBeforeOpen = counts(clearStore)
            assertEquals("both isolated stores start from the same recurrence fixture",
                nativeCountsBeforeOpen, clearCountsBeforeOpen)
            val nativeRecordBefore = requireNotNull(nativeStore.get(1L))
            val clearRecordBefore = requireNotNull(clearStore.get(1L))
            assertEquals(DraftForm.from(nativeRecordBefore), DraftForm.from(clearRecordBefore))
            assertEquals(1, nativeRecordBefore.revision)
            assertEquals(1L, nativeRecordBefore.eventSeq)

            nativeContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            SkinPackageManager(nativeContext).selectNativeForEmergencyRecovery()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides nativeContext) {
                        PowerCalTheme { PowerCalApp(nativeStore) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("日历").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("日历").performTouchInput { click() }
            repeat(monthSteps) {
                compose.onNodeWithContentDescription("下个月").performTouchInput { click() }
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("$selectedDate，1 项")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("$selectedDate，1 项")
                .performTouchInput { click() }
            val nativeRow = "活动 · $title"
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(nativeRow))
            compose.onNodeWithText(nativeRow).performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("仅本次").performTouchInput { click() }
            val expectedStart = DraftForm.formatInstant(
                selectedDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), zone
            )
            val expectedEnd = DraftForm.formatInstant(
                selectedDate.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(), zone
            )
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasSetTextAction() and hasText(expectedStart))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction() and hasText(expectedEnd)).assertExists()

            assertEquals("opening a Native Month occurrence is read-only",
                nativeCountsBeforeOpen, counts(nativeStore))
            assertEquals(nativeRecordBefore, nativeStore.get(1L))

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()

            clearContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            SkinPackageManager(clearContext).select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides clearContext) {
                        PowerCalTheme { PowerCalApp(clearStore) }
                    }
                }
            }
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val clearWeb = requireNotNull(web)
            awaitJs(clearWeb, "!!window.PowerCalSkin && " +
                "!!document.querySelector(\".clear-tabbar [data-action='month']\")")
            val monthTap = tapClear(clearWeb, ".clear-tabbar [data-action='month']")
            awaitJs(clearWeb, "!document.querySelector('#monthPage').hidden")
            val monthNavigationTaps = mutableListOf<JSONObject>()
            repeat(monthSteps) {
                monthNavigationTaps += tapClear(clearWeb,
                    ".month-bar [data-action='browseMonth'][data-delta-months='1']")
            }
            val dateTap = tapClear(clearWeb, ".month-cell[data-date='$selectedDate']")
            awaitJs(clearWeb, "document.querySelector('#monthAgenda h2')?.textContent==='$selectedDate'")
            val rowSelector = "#monthAgenda .agenda-event[data-event-seq='1']" +
                "[data-original-occurrence-key='$selectedKey']"
            val eventTap = tapClear(clearWeb, rowSelector)
            awaitJs(clearWeb, "!document.querySelector('#skinSheet').hidden")
            val opened = WebSkinBridgeRuntime.state().getJSONObject("editing")
                .getJSONObject("editor")
            assertEquals(1L, opened.getLong("eventSeq"))
            assertTrue("Clear Month row must open a valid selected occurrence",
                opened.getBoolean("canEditSelectedOccurrence"))
            assertEquals("true", evaluate(clearWeb, "document.querySelector('#sheetSave').disabled"))
            assertEquals("true", evaluate(clearWeb,
                "!document.querySelector('#sheetFields [data-field=title]')"))
            val scopeTap = tapClear(clearWeb, "[data-scope='SINGLE_OCCURRENCE']")
            awaitJs(clearWeb,
                "document.querySelector('[data-scope=SINGLE_OCCURRENCE]')?.getAttribute('aria-pressed')==='true'")
            val selected = WebSkinBridgeRuntime.state().getJSONObject("editing")
                .getJSONObject("editor")
            assertEquals("SINGLE_OCCURRENCE", selected.getString("recurrenceScope"))
            val clearForm = selected.getJSONObject("form")
            assertEquals(expectedStart, clearForm.getString("startText"))
            assertEquals(expectedEnd, clearForm.getString("endText"))
            assertEquals(title, clearForm.getString("title"))
            assertEquals("Native and Clear open the same selected Month occurrence start",
                expectedStart, clearForm.getString("startText"))
            assertEquals("Clear Month open and scope selection are actual trusted Android touches",
                true, (listOf(monthTap, dateTap, eventTap, scopeTap) + monthNavigationTaps)
                    .all { it.optBoolean("trusted") })

            assertEquals("opening the Clear Month occurrence is read-only",
                clearCountsBeforeOpen, counts(clearStore))
            assertEquals(nativeCountsBeforeOpen, counts(nativeStore))
            assertEquals(clearRecordBefore, clearStore.get(1L))
            assertEquals(nativeRecordBefore, nativeStore.get(1L))
            println("NATIVE_CLEAR_PARITY operation=MONTH_OCCURRENCE_OPEN kind=ACTIVITY " +
                "selectedKey=$selectedKey start=$expectedStart end=$expectedEnd " +
                "nativeRecordUnchanged=true clearRecordUnchanged=true " +
                "monthSteps=$monthSteps counts=${counts(clearStore)} trustedTouches=" +
                (listOf(monthTap, dateTap, eventTap, scopeTap) + monthNavigationTaps)
                    .map { it.optBoolean("trusted") })
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(database)
            clearContext.deleteDatabase(database)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }
}
