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
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceEngine
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebSkinRecurringScopeParityInstrumentedTest {
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
        private fun databasePath(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = root
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

        fun clearPreferences(name: String) {
            getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }

        fun deletePrivateFiles() {
            root.deleteRecursively()
        }
    }

    private data class StoredForm(val eventSeq: Long, val revision: Long, val form: DraftForm)

    @Test
    fun sameDailyAlarmFixtureProducesEquivalentFutureAndWholeSeriesEdits() {
        val storage = compose.activity.applicationContext
        val zone = ZoneId.systemDefault()
        val selectedDate = LocalDate.now(zone)
        val firstDate = selectedDate.minusDays(1)
        val firstStart = firstDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val selectedKey = "${selectedDate}T09:00"
        val baselineTitle = "SEP06 范围基线"

        listOf(RecurrenceEditScope.THIS_AND_FUTURE, RecurrenceEditScope.WHOLE_SERIES)
            .forEach { scope ->
                verifyScopePair(
                    storage = storage,
                    zone = zone,
                    selectedDate = selectedDate,
                    firstDate = firstDate,
                    firstStart = firstStart,
                    selectedKey = selectedKey,
                    baselineTitle = baselineTitle,
                    scope = scope
                )
            }
    }

    private fun verifyScopePair(
        storage: Context,
        zone: ZoneId,
        selectedDate: LocalDate,
        firstDate: LocalDate,
        firstStart: Long,
        selectedKey: String,
        baselineTitle: String,
        scope: RecurrenceEditScope
    ) {
        val namespace = "pc-sep06-${scope.name.lowercase()}-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, "$namespace-native")
        val clearContext = IsolatedAppContext(compose.activity, storage, "$namespace-clear")
        val nativeDatabase = "scope.db"
        val clearDatabase = "scope.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val changedTitle = "${scope.name} 已修改"
        val sourceForm = DraftForm(
            kind = TemporalKind.ALARM,
            title = baselineTitle,
            startText = DraftForm.formatInstant(firstStart, zone),
            zoneId = zone.id,
            reminderMinutes = -1,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 4)
        )
        var web: WebView? = null
        try {
            val nativeSequence = seed(nativeStore, sourceForm, "sep06-native-${scope.name}")
            val clearSequence = seed(clearStore, sourceForm, "sep06-clear-${scope.name}")
            assertEquals(1L, nativeSequence)
            assertEquals(nativeSequence, clearSequence)
            assertEquals(DraftForm.from(requireNotNull(nativeStore.get(nativeSequence))),
                DraftForm.from(requireNotNull(clearStore.get(clearSequence))))

            performNativeEdit(
                context = nativeContext,
                store = nativeStore,
                selectedDate = selectedDate,
                baselineTitle = baselineTitle,
                changedTitle = changedTitle,
                scope = scope
            )

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()

            web = showClear(clearContext, clearStore)
            val traces = performClearEdit(
                web = requireNotNull(web),
                store = clearStore,
                zone = zone,
                firstDate = firstDate,
                selectedDate = selectedDate,
                selectedKey = selectedKey,
                baselineTitle = baselineTitle,
                changedTitle = changedTitle,
                scope = scope
            )

            assertEquivalentResults(
                scope = scope,
                zone = zone,
                firstDate = firstDate,
                selectedDate = selectedDate,
                baselineTitle = baselineTitle,
                changedTitle = changedTitle,
                nativeStore = nativeStore,
                clearStore = clearStore
            )
            Log.i("SEP06_SCOPE_PARITY", JSONObject()
                .put("scope", scope.name)
                .put("selectedOccurrenceKey", selectedKey)
                .put("nativeEventSeqs", JSONArray(nativeStore.all().map { it.eventSeq }))
                .put("clearEventSeqs", JSONArray(clearStore.all().map { it.eventSeq }))
                .put("trustedClearTouches", JSONArray(traces))
                .put("commitCount", clearStore.commitCount())
                .put("identityCount", clearStore.identityCount())
                .put("nextSequence", clearStore.nextSequencePreview())
                .toString())
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
            listOf("powercal-onboarding").forEach {
                nativeContext.clearPreferences(it)
                clearContext.clearPreferences(it)
            }
            nativeContext.deletePrivateFiles()
            clearContext.deletePrivateFiles()
        }
    }

    private fun seed(store: PowerStore, form: DraftForm, actor: String): Long {
        val outcome = store.createFormalEvent(form, "sep06/$actor")
        assertTrue("scope fixture must create one formal record without a draft row",
            outcome is PowerStore.CommitOutcome.Committed)
        return requireNotNull(store.all().singleOrNull()).eventSeq
    }

    private fun performNativeEdit(
        context: Context,
        store: PowerStore,
        selectedDate: LocalDate,
        baselineTitle: String,
        changedTitle: String,
        scope: RecurrenceEditScope
    ) {
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        SkinPackageManager(context).selectNativeForEmergencyRecovery()
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("日历").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("日历").performClick()

        val dateCell = hasContentDescription(selectedDate.toString(), substring = true) and hasClickAction()
        compose.waitUntil(30_000) {
            compose.onAllNodes(dateCell).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(dateCell).performClick()

        val eventRow = hasText(baselineTitle, substring = true) and hasClickAction()
        compose.waitUntil(30_000) {
            compose.onAllNodes(eventRow).fetchSemanticsNodes().size == 1
        }
        compose.onNode(eventRow).performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(scope.toUserLabel()).performClick()
        val titleInput = hasSetTextAction() and hasText(baselineTitle)
        compose.onNode(titleInput).performTextReplacement(changedTitle)
        compose.onNodeWithText("保存修改").performClick()
        waitForSavedState(store, scope, changedTitle)

        if (scope == RecurrenceEditScope.WHOLE_SERIES) {
            compose.onNodeWithText("存档并退出").performClick()
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isEmpty()
        }
        assertEquals("Native scope edit leaves no temporary drafts", 0L, store.draftCount())
        assertEquals(0, store.pendingCount())
    }

    private fun showClear(context: Context, store: PowerStore): WebView {
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
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
        val result = requireNotNull(web)
        awaitJs(result, "!!window.PowerCalSkin && !!document.querySelector('#timelinePage')")
        return result
    }

    private fun performClearEdit(
        web: WebView,
        store: PowerStore,
        zone: ZoneId,
        firstDate: LocalDate,
        selectedDate: LocalDate,
        selectedKey: String,
        baselineTitle: String,
        changedTitle: String,
        scope: RecurrenceEditScope
    ): List<JSONObject> {
        awaitJs(web, "!!document.querySelector(\".clear-tabbar [data-action='month']\")")
        val traces = mutableListOf<JSONObject>()
        traces += tapWebSelector(web, ".clear-tabbar [data-action='month']")
        awaitJs(web, "!document.querySelector('#monthPage').hidden")

        val cell = ".month-cell[data-date='${selectedDate}']"
        awaitJs(web, "!!document.querySelector(${JSONObject.quote(cell)})")
        traces += tapWebSelector(web, cell)
        awaitJs(web, "document.querySelector('#monthAgenda h2')?.textContent==='${selectedDate}'")

        val event = "#monthAgenda .agenda-event[data-event-seq='1']" +
            "[data-original-occurrence-key='$selectedKey']"
        awaitJs(web, "!!document.querySelector(${JSONObject.quote(event)})")
        val eventTrace = tapWebSelector(web, event)
        traces += eventTrace
        assertEquals("Clear touch must open the tapped original occurrence",
            selectedKey, eventTrace.optString("occurrenceKey"))
        awaitJs(web, "!document.querySelector('#skinSheet').hidden")

        val editingBeforeScope = WebSkinBridgeRuntime.state().getJSONObject("editing")
            .getJSONObject("editor")
        assertTrue(editingBeforeScope.getBoolean("requiresRecurrenceScope"))
        assertTrue(editingBeforeScope.getBoolean("canEditSelectedOccurrence"))
        assertEquals(JSONObject.NULL, editingBeforeScope.get("recurrenceScope"))

        val scopeSelector = "[data-scope='${scope.name}']"
        awaitJs(web, "!!document.querySelector(${JSONObject.quote(scopeSelector)}) && " +
            "!document.querySelector(${JSONObject.quote(scopeSelector)}).disabled")
        val scopeTrace = tapWebSelector(web, scopeSelector)
        assertEquals(scope.name, scopeTrace.getString("scope"))
        traces += scopeTrace
        awaitJs(web, "document.querySelector(${JSONObject.quote(scopeSelector)})" +
            "?.getAttribute('aria-pressed')==='true'")
        val selectedEditor = WebSkinBridgeRuntime.state().getJSONObject("editing")
            .getJSONObject("editor")
        assertEquals(scope.name, selectedEditor.getString("recurrenceScope"))
        val expectedFormDate = if (scope == RecurrenceEditScope.WHOLE_SERIES) firstDate else selectedDate
        assertEquals("scope selection must bind the form to the tapped occurrence; " +
            "key=$selectedKey open=${editingBeforeScope.getJSONObject("form").optString("startText")}; " +
            "scopeTouch=$scopeTrace selected=${selectedEditor.getJSONObject("form").optString("startText")}",
            DraftForm.formatInstant(
                expectedFormDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), zone
            ), selectedEditor.getJSONObject("form").getString("startText"))

        val titleValue = JSONObject.quote(changedTitle)
        assertEquals("true", evaluate(web, """
            (()=>{
              const input=document.querySelector('#sheetFields [data-field=title]');
              if(!input)return false;
              input.value=$titleValue;
              input.dispatchEvent(new Event('input',{bubbles:true}));
              input.dispatchEvent(new Event('change',{bubbles:true}));
              return true;
            })()
        """.trimIndent()))
        awaitJs(web, "!document.querySelector('#sheetSave').disabled && " +
            "document.querySelector('#sheetFields [data-field=title]')?.value===" + titleValue)

        assertUnchangedBeforeApproval(store, baselineTitle)
        traces += tapWebSelector(web, "#sheetSave")
        awaitHostFormalReview()
        assertUnchangedBeforeApproval(store, baselineTitle)

        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isEmpty()
        }
        awaitJs(web, "document.querySelector('#sheetStatus')?.textContent.includes(" +
            "'已取消宿主操作，内容仍保留')")
        assertUnchangedBeforeApproval(store, baselineTitle)

        traces += tapWebSelector(web, "#sheetSave")
        awaitHostFormalReview()
        assertUnchangedBeforeApproval(store, baselineTitle)
        compose.onNodeWithText("确认").performClick()
        waitForSavedState(store, scope, changedTitle)
        return traces
    }

    private fun awaitHostFormalReview() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertUnchangedBeforeApproval(store: PowerStore, baselineTitle: String) {
        assertEquals("formal event cannot change before Host approval", baselineTitle,
            store.get(1L)?.title)
        assertEquals(1L, store.commitCount())
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(0L, store.draftCount())
        assertEquals(0, store.pendingCount())
    }

    private fun waitForSavedState(store: PowerStore, scope: RecurrenceEditScope, title: String) {
        compose.waitUntil(30_000) {
            when (scope) {
                RecurrenceEditScope.THIS_AND_FUTURE -> store.all().size == 2 &&
                    store.get(2L)?.title == title
                RecurrenceEditScope.WHOLE_SERIES -> store.all().size == 1 &&
                    store.get(1L)?.title == title
                RecurrenceEditScope.SINGLE_OCCURRENCE -> false
            }
        }
    }

    private fun assertEquivalentResults(
        scope: RecurrenceEditScope,
        zone: ZoneId,
        firstDate: LocalDate,
        selectedDate: LocalDate,
        baselineTitle: String,
        changedTitle: String,
        nativeStore: PowerStore,
        clearStore: PowerStore
    ) {
        val native = nativeStore.all().sortedBy { it.eventSeq }
        val clear = clearStore.all().sortedBy { it.eventSeq }
        fun stored(records: List<dev.powerfulcalendar.core.TemporalRecord>) = records.map {
            StoredForm(it.eventSeq, it.revision, DraftForm.from(it))
        }
        assertEquals("same occurrence scope must create equivalent persisted records",
            stored(native), stored(clear))
        assertEquals(if (scope == RecurrenceEditScope.THIS_AND_FUTURE) 3L else 2L,
            nativeStore.commitCount())
        assertEquals(nativeStore.commitCount(), clearStore.commitCount())
        assertEquals(native.size.toLong(), nativeStore.identityCount())
        assertEquals(nativeStore.identityCount(), clearStore.identityCount())
        assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
        assertEquals(0L, nativeStore.draftCount())
        assertEquals(nativeStore.draftCount(), clearStore.draftCount())
        assertEquals(0, nativeStore.pendingCount())
        assertEquals(nativeStore.pendingCount(), clearStore.pendingCount())

        val end = firstDate.plusDays(7).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val nativeKeys = native.flatMap { record ->
            RecurrenceEngine.occurrences(record, firstDate.minusDays(1).atStartOfDay(zone)
                .toInstant().toEpochMilli(), end).map { it.key }
        }.sorted()
        val clearKeys = clear.flatMap { record ->
            RecurrenceEngine.occurrences(record, firstDate.minusDays(1).atStartOfDay(zone)
                .toInstant().toEpochMilli(), end).map { it.key }
        }.sorted()
        assertEquals(nativeKeys, clearKeys)

        when (scope) {
            RecurrenceEditScope.THIS_AND_FUTURE -> {
                assertEquals(2, native.size)
                val old = requireNotNull(nativeStore.get(1L))
                val future = requireNotNull(nativeStore.get(2L))
                assertEquals(baselineTitle, old.title)
                assertEquals(1, old.recurrence.count)
                assertEquals(changedTitle, future.title)
                assertEquals(3, future.recurrence.count)
                assertEquals(2L, old.revision)
                assertEquals(1L, future.revision)
                assertEquals(listOf("${firstDate}T09:00"), RecurrenceEngine.occurrences(
                    old, firstDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), end
                ).map { it.key })
                assertEquals((0L..2L).map { "${selectedDate.plusDays(it)}T09:00" },
                    RecurrenceEngine.occurrences(future,
                        firstDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), end
                    ).map { it.key })
                assertEquals(3L, nativeStore.nextSequencePreview())
                assertEquals(2L, nativeStore.identityCount())
            }
            RecurrenceEditScope.WHOLE_SERIES -> {
                assertEquals(1, native.size)
                val series = requireNotNull(nativeStore.get(1L))
                assertEquals(changedTitle, series.title)
                assertEquals(4, series.recurrence.count)
                assertEquals(2L, series.revision)
                assertEquals((0L..3L).map { "${firstDate.plusDays(it)}T09:00" },
                    RecurrenceEngine.occurrences(series,
                        firstDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), end
                    ).map { it.key })
                assertEquals(2L, nativeStore.nextSequencePreview())
                assertEquals(1L, nativeStore.identityCount())
            }
            RecurrenceEditScope.SINGLE_OCCURRENCE -> error("single scope is verified separately")
        }
    }

    private fun tapWebSelector(web: WebView, selector: String): JSONObject {
        val imm = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        compose.runOnUiThread {
            imm.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
            compose.activity.window.decorView.requestFocus()
            web.requestFocus()
        }
        compose.waitUntil(10_000) {
            compose.activity.window.decorView.hasWindowFocus() && web.hasWindowFocus()
        }
        val selectorLiteral = JSONObject.quote(selector)
        val target = evaluateObject(web, """
            (()=>{
              const element=document.querySelector($selectorLiteral);
              if(!element)return {found:false};
              element.scrollIntoView({block:"center",inline:"center",behavior:"instant"});
              const rect=element.getBoundingClientRect();
              const x=rect.left+rect.width/2,y=rect.top+rect.height/2;
              const hit=document.elementFromPoint(x,y);
              window.__pcSep06Touch=null;
              element.addEventListener("click",event=>{
               window.__pcSep06Touch={trusted:event.isTrusted,scope:element.dataset.scope||"",
                   action:element.dataset.do||element.dataset.action||"",selector:$selectorLiteral,
                   eventSeq:element.dataset.eventSeq||"",
                   occurrenceKey:element.dataset.originalOccurrenceKey||""};
              },{once:true,capture:true});
              return {found:true,x,y,cssWidth:innerWidth,cssHeight:innerHeight,
                visible:rect.width>0&&rect.height>0&&rect.left>=0&&rect.top>=0&&
                  rect.right<=innerWidth&&rect.bottom<=innerHeight,
                hitTarget:hit===element||element.contains(hit)||
                  hit?.closest("button")==element||hit?.closest('[role="button"]')===element,
                disabled:element.disabled===true};
            })()
        """.trimIndent())
        assertTrue("Clear touchscreen target missing, clipped or disabled: $selector $target",
            target.optBoolean("found") && target.optBoolean("visible") &&
                target.optBoolean("hitTarget") && !target.optBoolean("disabled"))

        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (width, height) = requireNotNull(dimensions.get())
        val scaleX = width / target.getDouble("cssWidth")
        val scaleY = height / target.getDouble("cssHeight")
        assertTrue("WebView CSS/pixel scale must stay positive", scaleX > 0 && scaleY > 0)
        assertTrue("WebView touch mapping must preserve aspect ratio", kotlin.math.abs(scaleX - scaleY) < 0.04)
        val x = location[0] + (target.getDouble("x") * scaleX).toFloat()
        val y = location[1] + (target.getDouble("y") * scaleY).toFloat()
        val delivered = AtomicInteger()
        compose.runOnUiThread { web.setOnTouchListener { _, _ -> delivered.incrementAndGet(); false } }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Android accepted Clear touchscreen down", automation.injectInputEvent(down, true))
            assertTrue("Android accepted Clear touchscreen up", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
            compose.runOnUiThread { web.setOnTouchListener(null) }
        }
        assertTrue("touch did not reach the target WebView: selector=$selector, delivered=${delivered.get()}",
            delivered.get() >= 2)
        val trace = evaluateObject(web, "window.__pcSep06Touch||{}")
        assertTrue("Clear action must arise from a trusted touchscreen click: $trace",
            trace.optBoolean("trusted"))
        return trace
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
        val completed = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value -> result.set(value); completed.countDown() }
        }
        assertTrue("Clear JavaScript response timed out: $expression",
            completed.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject =
        JSONObject(JSONArray("[${evaluate(web, "JSON.stringify($expression)")}]" ).getString(0))

    private fun awaitJs(web: WebView, condition: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, condition) == "true") return
            Thread.sleep(100)
        }
        val state = runCatching {
            evaluate(web, "JSON.stringify({page:window.PowerCalSkin?.state?.page," +
                "sheetHidden:document.querySelector('#skinSheet')?.hidden," +
                "status:document.querySelector('#sheetStatus')?.textContent})")
        }.getOrDefault("unavailable")
        throw AssertionError("Clear JavaScript condition timed out: $condition; state=$state")
    }

    private fun RecurrenceEditScope.toUserLabel() = when (this) {
        RecurrenceEditScope.SINGLE_OCCURRENCE -> "仅本次"
        RecurrenceEditScope.THIS_AND_FUTURE -> "本次及以后"
        RecurrenceEditScope.WHOLE_SERIES -> "整个系列"
    }
}
