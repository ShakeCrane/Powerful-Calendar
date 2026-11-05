package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.api.v1.CalendarWindowQueryV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
import dev.powerfulcalendar.core.api.v1.RecurrenceOccurrenceWindowQueryV1
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
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

    private object ColdMonthProcessIdentity {
        val token: String = UUID.randomUUID().toString()
    }

    private fun coldMonthRunId(): String = requireNotNull(
        InstrumentationRegistry.getArguments().getString("monthOccurrenceRunId")
    ).also { require(it.matches(Regex("[a-f0-9-]{36}"))) }

    private fun coldMonthPhase(): String = requireNotNull(
        InstrumentationRegistry.getArguments().getString("monthOccurrencePhase")
    )

    private fun coldMonthNamespace(runId: String) = "pc-month-occurrence-$runId"

    private fun saveColdMonthMarker(file: File, value: JSONObject) {
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        FileOutputStream(file).use { output ->
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun cleanupColdMonthFixture(
        storage: Context,
        context: IsolatedAppContext,
        namespace: String,
        database: String,
        marker: File
    ) {
        context.deleteDatabase(database)
        listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
            .forEach { storage.deleteSharedPreferences("$namespace-$it") }
        File(storage.cacheDir, namespace).deleteRecursively()
        marker.parentFile?.deleteRecursively()
    }

    private fun attachClearMonth(context: IsolatedAppContext, store: PowerStore): WebView {
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
            awaitJs(it, "!!window.PowerCalSkin && " +
                "!!document.querySelector(\".clear-tabbar [data-action='month']\")")
        }
    }

    private fun currentBridgeState(): JSONObject {
        val state = AtomicReference<JSONObject>()
        compose.runOnIdle { state.set(WebSkinBridgeRuntime.state()) }
        return requireNotNull(state.get())
    }

    private fun assertCrossMonthOccurrenceRendering(web: WebView, store: PowerStore) {
        val eventSeq = 1L
        val eventZone = ZoneId.of("Asia/Shanghai")
        val appZone = ZoneId.systemDefault()
        val ownerBefore = requireNotNull(store.get(eventSeq))
        val countsBefore = listOf(
            store.draftCount(), store.pendingCount(), store.identityCount(), store.commitCount(),
            store.nextSequencePreview(), store.nextQueueOrderPreview(), store.all().size.toLong()
        )
        val gridFrom = LocalDate.of(2026, 10, 26)
        val gridTo = LocalDate.of(2026, 12, 7)
        val fromMillis = gridFrom.atStartOfDay(appZone).toInstant().toEpochMilli()
        val toMillis = gridTo.atStartOfDay(appZone).toInstant().toEpochMilli()
        val core = PowerCalCoreFeatureApiV1(store)
        val coreWindow = core.calendar.queryWindow(CalendarWindowQueryV1(
            fromMillis = fromMillis,
            toMillis = toMillis,
            zoneId = appZone.id
        ))
        assertEquals("Core month window contains the owner exactly once", listOf(eventSeq),
            coreWindow.events.map { it.eventSeq })
        val coreOwner = coreWindow.events.single()
        assertEquals(ownerBefore.revision, coreOwner.revision)
        val coreOccurrences = core.recurrence.queryOccurrencesInWindow(
            RecurrenceOccurrenceWindowQueryV1(coreOwner, fromMillis, toMillis)
        )
        assertEquals(eventSeq, coreOccurrences.eventSeq)
        assertEquals(ownerBefore.revision, coreOccurrences.eventRevision)
        val targetDates = (2..5).map { LocalDate.of(2026, 11, it) }
        val coreByDate = coreOccurrences.occurrences.associateBy { occurrence ->
            Instant.ofEpochMilli(occurrence.startMillis).atZone(eventZone).toLocalDate()
        }
        val expected = targetDates.associateWith { date ->
            requireNotNull(coreByDate[date]) { "Core did not return occurrence for $date" }
        }
        assertEquals(targetDates, expected.keys.toList())

        tapClear(web, ".clear-tabbar [data-action='month']")
        awaitJs(web, "!document.querySelector('#monthPage').hidden")
        tapClear(web, "#monthYearTitle")
        awaitJs(web, "document.querySelector('#monthYearPickerDialog')?.open===true")
        evaluate(web, "(()=>{const year=document.querySelector('#monthYear');" +
            "year.value='2026';year.dispatchEvent(new Event('input',{bubbles:true}));" +
            "return true})()")
        val yearSubmit = tapClear(web, "#monthYearPickerForm button[type='submit']")
        assertTrue("Year selection is a trusted Android touch", yearSubmit.optBoolean("trusted"))
        tapClear(web, "#monthNumberTitle")
        awaitJs(web, "document.querySelector('#monthPickerDialog')?.open===true")
        val monthSubmit = tapClear(web, "#monthPickerForm [data-month-number='11']")
        assertTrue("Month selection is a trusted Android touch", monthSubmit.optBoolean("trusted"))
        awaitJs(web, "document.querySelector('#monthTitle')?.dataset.month==='2026-11'")

        fun assertHostRows() {
            val month = currentBridgeState().getJSONObject("month")
            assertEquals("2026-11", month.getString("month"))
            assertEquals(appZone.id, month.getString("zoneId"))
            val items = month.getJSONArray("items")
            for ((date, occurrence) in expected) {
                val rows = (0 until items.length()).map(items::getJSONObject).filter {
                    it.getString("date") == date.toString()
                }
                assertEquals("Host state must contain one row for $date", 1, rows.size)
                val row = rows.single()
                assertEquals(eventSeq, row.getLong("eventSeq"))
                assertEquals(ownerBefore.revision, row.getLong("revision"))
                assertEquals("$eventSeq:${occurrence.key}", row.getString("occurrenceKey"))
                assertEquals(occurrence.key, row.getString("originalOccurrenceKey"))
                assertEquals("ACTIVITY", row.getString("kind"))
            }
        }

        fun waitForHostRows(stage: String) {
            val lastFailure = AtomicReference("not evaluated")
            try {
                compose.waitUntil(30_000) {
                    runCatching { assertHostRows() }.fold(
                        onSuccess = { true },
                        onFailure = { error -> lastFailure.set(error.toString()); false }
                    )
                }
            } catch (timeout: Throwable) {
                val state = runCatching { currentBridgeState().toString() }
                    .getOrElse { "state read failed: $it" }
                throw AssertionError(
                    "Host occurrence rows did not converge at $stage; " +
                        "last assertion=${lastFailure.get()}; bridgeState=$state",
                    timeout
                )
            }
        }
        waitForHostRows("initial November selection")
        assertHostRows()

        for ((date, occurrence) in expected) {
            val dateText = date.toString()
            val cellSelector = "#monthGrid .month-cell[data-date='$dateText']"
            val cellTap = tapClear(web, cellSelector)
            assertTrue("Month date $date must use trusted Android input",
                cellTap.optBoolean("trusted"))
            awaitJs(web, "document.querySelector('#monthAgenda h2')?.dataset.date==='$dateText'")
            val rowSelector = "#monthAgenda .agenda-event[data-event-seq='$eventSeq']" +
                "[data-revision='${ownerBefore.revision}']" +
                "[data-original-occurrence-key='${occurrence.key}']"
            awaitJs(web, "!!document.querySelector(${JSONObject.quote(rowSelector)})")
            val serialized = evaluate(web, "(()=>{const cell=document.querySelector(" +
                "${JSONObject.quote(cellSelector)});const row=document.querySelector(" +
                "${JSONObject.quote(rowSelector)});return JSON.stringify({date:cell?.dataset.date," +
                "countText:cell?.querySelector('.month-count')?.textContent," +
                "title:row?.querySelector('.agenda-event-title')?.textContent," +
                "summary:row?.querySelector('.agenda-event-summary')?.textContent," +
                "sequence:row?.querySelector('.agenda-event-sequence')?.textContent," +
                "eventSeq:row?.dataset.eventSeq,revision:row?.dataset.revision," +
                "occurrenceKey:row?.dataset.originalOccurrenceKey})})()")
            val dom = JSONObject(JSONArray("[$serialized]").getString(0))
            assertEquals(dateText, dom.getString("date"))
            assertEquals("1 项", dom.getString("countText"))
            assertEquals("活动 · ${ownerBefore.title}", dom.getString("title"))
            assertTrue(dom.getString("summary").contains("09:00"))
            assertEquals(ownerBefore.displaySeq(), dom.getString("sequence"))
            assertEquals(eventSeq.toString(), dom.getString("eventSeq"))
            assertEquals(ownerBefore.revision.toString(), dom.getString("revision"))
            assertEquals(occurrence.key, dom.getString("occurrenceKey"))

            if (date == LocalDate.of(2026, 11, 3)) {
                val eventTap = tapClear(web, rowSelector)
                assertTrue("Occurrence row must use trusted Android input",
                    eventTap.optBoolean("trusted"))
                awaitJs(web, "!document.querySelector('#skinSheet').hidden")
                val editor = currentBridgeState().getJSONObject("editing")
                    .getJSONObject("editor")
                assertEquals(eventSeq, editor.getLong("eventSeq"))
                assertTrue(editor.getBoolean("canEditSelectedOccurrence"))
                val scopeTap = tapClear(web, "[data-scope='SINGLE_OCCURRENCE']")
                assertTrue("Single occurrence scope must use trusted Android input",
                    scopeTap.optBoolean("trusted"))
                awaitJs(web, "document.querySelector('[data-scope=SINGLE_OCCURRENCE]')" +
                    "?.getAttribute('aria-pressed')==='true'")
                val scopedEditor = currentBridgeState().getJSONObject("editing")
                    .getJSONObject("editor")
                assertEquals("SINGLE_OCCURRENCE", scopedEditor.getString("recurrenceScope"))
                assertEquals(DraftForm.formatInstant(occurrence.startMillis, eventZone),
                    scopedEditor.getJSONObject("form").getString("startText"))
                assertEquals(ownerBefore.title,
                    scopedEditor.getJSONObject("form").getString("title"))
                evaluate(web, "document.querySelector('#sheetClose').click();true")
                awaitJs(web, "document.querySelector('#skinSheet').hidden")
            }
        }

        tapClear(web, ".month-bar [data-action='browseMonth'][data-delta-months='-1']")
        awaitJs(web, "document.querySelector('#monthTitle')?.dataset.month==='2026-10'")
        tapClear(web, ".month-bar [data-action='browseMonth'][data-delta-months='1']")
        awaitJs(web, "document.querySelector('#monthTitle')?.dataset.month==='2026-11'")
        waitForHostRows("return from October to November")
        assertHostRows()
        assertEquals("Reading and opening monthly occurrences must not write the Store",
            countsBefore, listOf(
                store.draftCount(), store.pendingCount(), store.identityCount(), store.commitCount(),
                store.nextSequencePreview(), store.nextQueueOrderPreview(), store.all().size.toLong()
            ))
        assertEquals(ownerBefore, store.get(eventSeq))
        println("MONTH_OCCURRENCE_PARITY dates=${expected.keys} " +
            "keys=${expected.values.map { it.key }} eventSeq=$eventSeq revision=${ownerBefore.revision} " +
            "coreHostDomMatch=true monthSwitch=true storeUnchanged=true")
    }

    @Test fun prepareCrossMonthDailyOccurrenceForColdRestart() {
        assumeTrue("run prepare phase with -e monthOccurrencePhase prepare",
            coldMonthPhase() == "prepare")
        val runId = coldMonthRunId()
        val namespace = coldMonthNamespace(runId)
        val storage = compose.activity.applicationContext
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "cross-month-$runId.db"
        val markerDir = File(storage.noBackupFilesDir, namespace)
        val marker = File(markerDir, "session.json")
        assertFalse("UUID-scoped month fixture must be new", marker.exists())
        val store = PowerStore(context, database)
        var prepared = false
        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val startDate = LocalDate.of(2026, 10, 31)
            val start = startDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            val draft = store.newDraft(DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "F-03 deterministic cross-month daily activity",
                startText = DraftForm.formatInstant(start, zone),
                endText = DraftForm.formatInstant(start + 3_600_000L, zone),
                zoneId = zone.id,
                reminderMinutes = -1,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 8)
            ), actor = "f03-month-instrumented-test")
            assertTrue(store.commit(draft.draftId, actor = "f03-month-instrumented-test") is
                PowerStore.CommitOutcome.Committed)
            assertEquals(1L, draft.eventSeq)
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            val manager = SkinPackageManager(context)
            manager.select("powercal-clear-default")
            assertEquals("powercal-clear-default", manager.currentId())
            val web = attachClearMonth(context, store)
            assertCrossMonthOccurrenceRendering(web, store)
            saveColdMonthMarker(marker, JSONObject()
                .put("runId", runId)
                .put("namespace", namespace)
                .put("database", database)
                .put("eventSeq", draft.eventSeq)
                .put("revision", store.get(draft.eventSeq)?.revision)
                .put("processPid", android.os.Process.myPid())
                .put("processToken", ColdMonthProcessIdentity.token))
            prepared = true
            println("MONTH_OCCURRENCE_COLD_PREPARED runId=$runId pid=${android.os.Process.myPid()} " +
                "processToken=${ColdMonthProcessIdentity.token}")
        } finally {
            compose.runOnUiThread { compose.activity.setContent { } }
            compose.waitForIdle()
            PowerCalSkinRegistry.reset()
            store.close()
            if (!prepared) cleanupColdMonthFixture(storage, context, namespace, database, marker)
        }
    }

    @Test fun verifyCrossMonthDailyOccurrenceAfterColdRestart() {
        assumeTrue("run restore phase with -e monthOccurrencePhase restore",
            coldMonthPhase() == "restore")
        val runId = coldMonthRunId()
        val namespace = coldMonthNamespace(runId)
        val storage = compose.activity.applicationContext
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val markerDir = File(storage.noBackupFilesDir, namespace)
        val marker = File(markerDir, "session.json")
        assertTrue("prepare phase must leave the cold restart marker", marker.isFile)
        val metadata = JSONObject(marker.readText(Charsets.UTF_8))
        assertEquals(runId, metadata.getString("runId"))
        val database = metadata.getString("database")
        val store = PowerStore(context, database)
        try {
            assertNotEquals("force-stop must produce a new process token",
                metadata.getString("processToken"), ColdMonthProcessIdentity.token)
            assertEquals(metadata.getLong("eventSeq"), store.get(metadata.getLong("eventSeq"))
                ?.eventSeq)
            assertEquals(metadata.getLong("revision"), store.get(metadata.getLong("eventSeq"))
                ?.revision)
            val manager = SkinPackageManager(context)
            assertEquals("powercal-clear-default", manager.currentId())
            PowerCalSkinRegistry.reset()
            manager.restoreSelection()
            assertEquals("powercal-clear-default", PowerCalSkinRegistry.active.key)
            assertCrossMonthOccurrenceRendering(attachClearMonth(context, store), store)
            println("MONTH_OCCURRENCE_COLD_RESTORED runId=$runId pid=${android.os.Process.myPid()} " +
                "processToken=${ColdMonthProcessIdentity.token}")
        } finally {
            compose.runOnUiThread { compose.activity.setContent { } }
            compose.waitForIdle()
            PowerCalSkinRegistry.reset()
            store.close()
            cleanupColdMonthFixture(storage, context, namespace, database, marker)
        }
    }

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

    private fun captureWindowScreenshot(web: WebView, name: String): File {
        evaluate(web, "window.__native095ScreenshotFrameReady=false;" +
            "requestAnimationFrame(()=>requestAnimationFrame(()=>window.__native095ScreenshotFrameReady=true));true")
        awaitJs(web, "window.__native095ScreenshotFrameReady===true")
        val visualStateDrawn = CountDownLatch(1)
        compose.runOnUiThread {
            web.postVisualStateCallback(SystemClock.uptimeMillis(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        visualStateDrawn.countDown()
                    }
                })
        }
        assertTrue("WebView visual state must be drawn before screenshot capture",
            visualStateDrawn.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val file = File(requireNotNull(compose.activity.externalCacheDir), name)
        val bitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        )
        try {
            file.outputStream().use { output ->
                assertTrue("Month WebView screenshot must encode",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        println("NATIVE_095_MONTH_WEB_SCREENSHOT=${file.absolutePath} bytes=${file.length()}")
        return file
    }

    private fun tapClear(web: WebView, selector: String): JSONObject {
        val selectorLiteral = JSONObject.quote(selector)
        val encoded = evaluate(web, """(()=>{
            const target=document.querySelector($selectorLiteral);
            if(!target)return JSON.stringify({found:false});
            const rect=target.getBoundingClientRect();
            const hit=document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2);
            const panel=document.querySelector('#skinSheet .sheet-panel');
            const shade=document.querySelector('#sheetShade');
            const panelRect=panel?.getBoundingClientRect();
            window.__monthOccurrenceTap=null;
            target.addEventListener('click',event=>{
              window.__monthOccurrenceTap={trusted:event.isTrusted};
            },{once:true,capture:true});
            return JSON.stringify({found:true,x:rect.left+rect.width/2,y:rect.top+rect.height/2,
              cssWidth:innerWidth,cssHeight:innerHeight,
              targetTag:target.tagName,targetClass:String(target.className||""),
              hitTag:hit?.tagName||null,hitId:hit?.id||null,hitClass:String(hit?.className||""),
              targetZ:getComputedStyle(target).zIndex,panelZ:panel?getComputedStyle(panel).zIndex:null,
              shadeZ:shade?getComputedStyle(shade).zIndex:null,
              panelBounds:panelRect?{top:panelRect.top,bottom:panelRect.bottom,left:panelRect.left,right:panelRect.right}:null,
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

    private fun injectHorizontalSwipe(startX: Float, endX: Float, y: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()

        fun inject(action: Int, eventTime: Long, x: Float) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                assertTrue("Android rejected touchscreen swipe event action=$action",
                    automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }

        inject(MotionEvent.ACTION_DOWN, downTime, startX)
        repeat(8) { index ->
            SystemClock.sleep(24)
            val fraction = (index + 1) / 8f
            inject(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(),
                startX + (endX - startX) * fraction)
        }
        SystemClock.sleep(24)
        inject(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), endX)
    }

    private fun swipeClearMonthGrid(web: WebView, forward: Boolean): JSONObject {
        val rawProbe = evaluate(web, """(()=>{
          const grid=document.querySelector('#monthGrid');
          if(!grid)return JSON.stringify({found:false});
          const rect=grid.getBoundingClientRect();
          window.__monthSwipeProbe={down:false,up:false,cancelled:false,downTrusted:false,upTrusted:false,pointerId:null};
          document.addEventListener('pointerdown',event=>{
            const target=event.target instanceof Element?event.target:event.target?.parentElement;
            if(target?.closest('#monthGrid')){
              window.__monthSwipeProbe.down=true;
              window.__monthSwipeProbe.downTrusted=event.isTrusted;
              window.__monthSwipeProbe.pointerId=event.pointerId;
            }
          },true);
          document.addEventListener('pointerup',event=>{
            if(window.__monthSwipeProbe?.pointerId===event.pointerId){
              window.__monthSwipeProbe.up=true;
              window.__monthSwipeProbe.upTrusted=event.isTrusted;
            }
          },true);
          document.addEventListener('pointercancel',event=>{
            if(window.__monthSwipeProbe?.pointerId===event.pointerId){
              window.__monthSwipeProbe.cancelled=true;
              window.__monthSwipeProbe.cancelX=event.clientX;
              window.__monthSwipeProbe.cancelY=event.clientY;
            }
          },true);
          return JSON.stringify({found:true,left:rect.left,right:rect.right,top:rect.top,
            height:rect.height,width:rect.width,cssWidth:innerWidth,cssHeight:innerHeight,
            gridTouchAction:getComputedStyle(grid).touchAction,
            cellTouchAction:getComputedStyle(grid.querySelector('button')).touchAction});
        })()""")
        val probe = JSONObject(JSONArray("[$rawProbe]").getString(0))
        assertTrue("Clear month grid is missing or not laid out: $probe",
            probe.optBoolean("found") && probe.getDouble("width") > 100 &&
                probe.getDouble("height") > 100)

        val location = IntArray(2)
        val webSize = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            webSize.set(web.width to web.height)
        }
        val (width, height) = requireNotNull(webSize.get())
        val scaleX = width / probe.getDouble("cssWidth")
        val scaleY = height / probe.getDouble("cssHeight")
        assertEquals("WebView touch mapping must preserve aspect ratio", scaleX, scaleY, 0.03)
        val edgeGuard = 80.0
        val startCssX = if (forward) {
            minOf(probe.getDouble("right") - 12, probe.getDouble("cssWidth") - edgeGuard)
        } else {
            maxOf(probe.getDouble("left") + 12, edgeGuard)
        }
        val endCssX = if (forward) {
            maxOf(probe.getDouble("left") + 12, edgeGuard)
        } else {
            minOf(probe.getDouble("right") - 12, probe.getDouble("cssWidth") - edgeGuard)
        }
        val yCss = probe.getDouble("top") + probe.getDouble("height") / 2
        injectHorizontalSwipe(
            location[0] + (startCssX * scaleX).toFloat(),
            location[0] + (endCssX * scaleX).toFloat(),
            location[1] + (yCss * scaleY).toFloat()
        )
        val completed = runCatching {
            compose.waitUntil(3_000) {
                evaluate(web, "window.__monthSwipeProbe?.down===true&&" +
                    "(window.__monthSwipeProbe?.up===true||window.__monthSwipeProbe?.cancelled===true)") == "true"
            }
        }.isSuccess
        val result = evaluate(web, "JSON.stringify(window.__monthSwipeProbe)")
        val details = JSONObject(JSONArray("[$result]").getString(0))
            .put("startCssX", startCssX).put("endCssX", endCssX).put("yCss", yCss)
            .put("gridTouchAction", probe.getString("gridTouchAction"))
            .put("cellTouchAction", probe.getString("cellTouchAction"))
            .put("gestureCompleted", completed)
        println("CLEAR_MONTH_SWIPE_TOUCH $details")
        assertTrue("Clear grid did not receive a complete trusted pointer swipe: $details",
            completed && details.optBoolean("downTrusted") && details.optBoolean("upTrusted"))
        return details
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
            awaitJs(clearWeb, "document.querySelector('#monthAgenda h2')?.dataset.date==='$selectedDate'")
            val rowSelector = "#monthAgenda .agenda-event[data-event-seq='1']" +
                "[data-original-occurrence-key='$selectedKey']"
            compose.waitUntil(30_000) {
                val items = currentBridgeState().optJSONObject("month")
                    ?.optJSONArray("items") ?: return@waitUntil false
                (0 until items.length()).any { index ->
                    val item = items.getJSONObject(index)
                    val dates = item.optJSONArray("visibleDateKeys") ?: JSONArray()
                    item.optLong("eventSeq") == 1L &&
                        item.optString("originalOccurrenceKey") == selectedKey &&
                        (0 until dates.length()).any { dates.optString(it) == selectedDate.toString() }
                }
            }
            awaitJs(clearWeb, "!!document.querySelector(${JSONObject.quote(rowSelector)})")
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

    @Test
    fun monthSelectedDayRadiusMatchesFrozenNativeChipAndTapKeepsStoreUnchanged() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-month-radius-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "month-radius.db"
        val store = PowerStore(context, database)
        val selectedDate = LocalDate.of(2026, 11, 5)
        val zone = ZoneId.of("Asia/Shanghai")
        var web: WebView? = null
        try {
            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            val fixture = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, selectedDate, zone).copy(
                title = "MonthFixture",
                startText = "2026-11-05 12:00",
                endText = "2026-11-05 13:00"
            ))
            assertTrue("the isolated Month fixture must commit",
                store.commit(fixture.draftId) is PowerStore.CommitOutcome.Committed)
            val countsBefore = listOf(
                store.all().size.toLong(), store.draftCount(), store.pendingCount().toLong(),
                store.commitCount(), store.identityCount(), store.nextSequencePreview()
            )
            SkinPackageManager(context).select("powercal-clear-default")
            val clearWeb = attachClearMonth(context, store).also { web = it }
            evaluate(clearWeb,
                "document.querySelector('.clear-tabbar [data-action=\\\"month\\\"]')?.click();true")
            awaitJs(clearWeb, "!document.querySelector('#monthPage').hidden")
            val monthGeometry = evaluate(clearWeb, """(()=>{
                const rect=s=>{const r=document.querySelector(s)?.getBoundingClientRect();return r?{
                    x:r.x,y:r.y,width:r.width,height:r.height,top:r.top,bottom:r.bottom}:null};
                const grid=document.querySelector('#monthGrid'),r=grid.getBoundingClientRect();
                const panel=document.querySelector('.month-calendar-panel'),p=panel.getBoundingClientRect();
                const before=getComputedStyle(panel,'::before');
                const beforeWidth=parseFloat(before.width),beforeTop=parseFloat(before.top),
                    beforeBottom=parseFloat(before.bottom);
                const panelStyle=getComputedStyle(panel);
                return JSON.stringify({dpr:devicePixelRatio,visualScale:visualViewport?.scale,
                    rootZoom:getComputedStyle(document.documentElement).zoom,
                    panelBorderWidth:panelStyle.borderTopWidth,
                    viewport:{width:innerWidth,height:innerHeight},
                    page:rect('#monthPage'),panel:rect('.month-calendar-panel'),bar:rect('.month-bar'),
                    grid:rect('#monthGrid'),surface:{x:p.left+p.width/2-beforeWidth/2,
                        y:p.top+beforeTop,width:beforeWidth,height:p.height-beforeTop-beforeBottom},
                    firstCell:rect('#monthGrid .month-cell'),lastCell:rect('#monthGrid .month-cell:last-of-type')});
            })()""")
            println("NATIVE_095_MONTH_WEB_GEOMETRY=$monthGeometry")
            tapClear(clearWeb, "#monthYearTitle")
            awaitJs(clearWeb, "document.querySelector('#monthYearPickerDialog')?.open===true")
            evaluate(clearWeb, "(()=>{const year=document.querySelector('#monthYear');" +
                "year.value='2026';year.dispatchEvent(new Event('input',{bubbles:true}));true})()")
            tapClear(clearWeb, "#monthYearPickerForm button[type='submit']")
            awaitJs(clearWeb, "document.querySelector('#monthTitle')?.dataset.month?.startsWith('2026-')===true")
            tapClear(clearWeb, "#monthNumberTitle")
            awaitJs(clearWeb, "document.querySelector('#monthPickerDialog')?.open===true")
            tapClear(clearWeb, "#monthPickerForm [data-month-number='11']")
            awaitJs(clearWeb, "document.querySelector('#monthTitle')?.dataset.month==='2026-11'")
            val dateTap = tapClear(clearWeb, "#monthGrid .month-cell[data-date='$selectedDate']")
            assertTrue("selected-day tap must be a trusted Android input", dateTap.optBoolean("trusted"))
            awaitJs(clearWeb,
                "document.querySelector('#monthAgenda h2')?.dataset.date==='$selectedDate'")
            compose.waitUntil(30_000) {
                evaluate(clearWeb,
                    "!!document.querySelector('#monthAgenda .agenda-event-title')?.textContent.includes('MonthFixture')") == "true"
            }
            val agendaCardStyle = evaluate(clearWeb, """(()=>{
                const card=document.querySelector('#monthAgenda .agenda-event.activity');
                const title=card?.querySelector('.agenda-event-title');
                const summary=card?.querySelector('.agenda-event-summary');
                const sequence=card?.querySelector('.agenda-event-sequence');
                if(!card||!title||!summary||!sequence)return null;
                const c=getComputedStyle(card),t=getComputedStyle(title),s=getComputedStyle(summary),
                    q=getComputedStyle(sequence),r=card.getBoundingClientRect();
                return JSON.stringify({fill:c.backgroundColor,radius:c.borderTopLeftRadius,
                    shadow:c.boxShadow,padding:c.padding,gap:c.rowGap,titleColor:t.color,
                    titleSize:t.fontSize,titleWeight:t.fontWeight,titleLine:t.lineHeight,
                    summarySize:s.fontSize,summaryLine:s.lineHeight,sequenceSize:q.fontSize,
                    sequenceLine:q.lineHeight,bounds:{x:r.x,y:r.y,width:r.width,height:r.height}});
            })()""")
            assertEquals("MonthFixture agenda card must exist in the host-projected Web state",
                false, agendaCardStyle == "null")
            assertEquals("agenda card face must match Native surfaceContainerLow",
                "\"rgb(246, 248, 252)\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event.activity')).backgroundColor"))
            assertEquals("agenda row shape must match Native 20dp bar shape", "\"20px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event.activity')).borderTopLeftRadius"))
            assertEquals("Native Month Surface has no card shadow", "\"none\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event.activity')).boxShadow"))
            assertEquals("agenda content uses the Native 16dp inset", "\"16px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event.activity')).paddingTop"))
            assertEquals("activity title uses the frozen event-kind color", "\"rgb(23, 122, 99)\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event.activity .agenda-event-title')).color"))
            assertEquals("Native titleMedium line height", "\"24px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event-title')).lineHeight"))
            assertEquals("Native bodyMedium line height", "\"20px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event-summary')).lineHeight"))
            assertEquals("Native labelSmall size", "\"11px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event-sequence')).fontSize"))
            assertEquals("Native labelSmall line height", "\"16px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda .agenda-event-sequence')).lineHeight"))
            assertEquals("Month agenda begins at the Native 12dp item spacing", "\"12px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda')).paddingTop"))
            assertEquals("Native selected-date titleMedium size", "\"16px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda h2')).fontSize"))
            assertEquals("Native selected-date titleMedium weight", "\"500\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda h2')).fontWeight"))
            assertEquals("Native selected-date titleMedium line height", "\"24px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda h2')).lineHeight"))
            assertEquals("selected-date titleMedium letter spacing", "\"0.15px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda h2')).letterSpacing"))
            assertEquals("selected-date title-to-card gap matches Native screenshot geometry", "\"11px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('#monthAgenda h2')).marginBottom"))
            println("NATIVE_095_MONTH_AGENDA_CARD_STYLE=$agendaCardStyle")
            val radius = evaluate(clearWeb,
                "getComputedStyle(document.querySelector('#monthGrid .month-cell.selected')).borderTopLeftRadius")
            assertEquals("CSS chip corner must match frozen GlassShapes.chip = 14.dp", "\"14px\"", radius)
            val gridSurface = evaluate(clearWeb,
                "getComputedStyle(document.querySelector('.month-calendar-panel'),'::before').backgroundColor")
            assertEquals("month grid face must preserve the measured frozen Native composite",
                "\"rgb(253, 253, 254)\"", gridSurface)
            assertEquals("outer panel fill must match the frozen Native month surface",
                "\"rgb(242, 243, 243)\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('.month-calendar-panel')).backgroundColor"))
            assertEquals("outer border must use outlineVariant at the frozen 0.55 alpha",
                "\"rgba(195, 202, 216, 0.55)\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('.month-calendar-panel')).borderTopColor"))
            assertEquals("Native 1dp outline rasterizes to three pixels on this 420dpi AVD",
                "\"3\"",
                evaluate(clearWeb, "Math.round(parseFloat(getComputedStyle(document.querySelector('.month-calendar-panel')).borderTopWidth)*devicePixelRatio).toString()"))
            assertEquals("outer card corner must preserve the frozen 28dp card radius",
                "\"28px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('.month-calendar-panel')).borderTopLeftRadius"))
            assertEquals("2dp Native elevation maps to the calibrated CSS shadow preset", "true",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('.month-calendar-panel')).boxShadow.includes('0px 1px 3px 0px')"))
            assertEquals("surface must align with frozen native fill start", "\"27px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('.month-calendar-panel'),'::before').top"))
            assertEquals("surface must stop at frozen native fill lower edge", "\"29px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('.month-calendar-panel'),'::before').bottom"))
            assertEquals("visible fill width must match measured frozen native bounds", "\"331px\"",
                evaluate(clearWeb, "getComputedStyle(document.querySelector('.month-calendar-panel'),'::before').width"))
            val selectedForegroundMatchesNative = evaluate(clearWeb, """(()=>{
                const selected=document.querySelector('#monthGrid .month-cell.selected');
                const expected=getComputedStyle(document.documentElement)
                    .getPropertyValue('--on-accent-container').trim().toLowerCase()==='#dce5ff'
                    ?'rgb(220, 229, 255)':'rgb(11, 42, 102)';
                return [selected,selected?.querySelector('.month-day-number'),
                    selected?.querySelector('.month-count')].every(node=>node &&
                    getComputedStyle(node).color===expected);
            })()""")
            assertEquals("selected day number/count inherit frozen onPrimaryContainer", "true",
                selectedForegroundMatchesNative)
            assertEquals("selected agenda must retain the deterministic fixture", "true",
                evaluate(clearWeb,
                    "document.querySelector('#monthAgenda .agenda-event-title')?.textContent.includes('MonthFixture')"))
            val bounds = evaluate(clearWeb, "(()=>{const r=document.querySelector('#monthGrid .month-cell.selected')" +
                ".getBoundingClientRect();return JSON.stringify({x:r.x,y:r.y,width:r.width,height:r.height})})()")
            captureWindowScreenshot(clearWeb, "native095-month-agenda-web-20261106.png")
            assertEquals("month selection must not write Calendar Store data", countsBefore, listOf(
                store.all().size.toLong(), store.draftCount(), store.pendingCount().toLong(),
                store.commitCount(), store.identityCount(), store.nextSequencePreview()
            ))
            println("NATIVE_095_MONTH_SELECTED_SHAPE radius=$radius gridSurface=$gridSurface " +
                "foregroundMatchesNative=" +
                "$selectedForegroundMatchesNative bounds=$bounds date=$selectedDate " +
                "geometry=$monthGeometry fixture=MonthFixture trustedTouch=true storeUnchanged=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-month-nav-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-month-nav-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val database = "month-navigation.db"
        val nativeStore = PowerStore(nativeContext, database)
        val clearStore = PowerStore(clearContext, database)
        val zone = ZoneId.systemDefault()
        val targetDate = LocalDate.of(2027, 6, 19)
        val targetMonth = YearMonth.from(targetDate)
        var web: WebView? = null

        fun counts(store: PowerStore) = listOf(
            store.draftCount(), store.pendingCount(), store.identityCount(), store.commitCount(),
            store.nextSequencePreview(), store.nextQueueOrderPreview(), store.all().size.toLong()
        )

        fun timeline(state: JSONObject) = state.getJSONObject("timeline")

        try {
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
            val nativeBefore = WebSkinBridgeRuntime.state()
            val nativeCamera = timeline(nativeBefore)
            val nativeAnchor = nativeCamera.getLong("anchorMillis")
            val nativeHours = nativeCamera.getDouble("hours")
            val nativeCounts = counts(nativeStore)
            val nativeInitialMonth = YearMonth.from(
                java.time.Instant.ofEpochMilli(nativeAnchor).atZone(zone).toLocalDate()
            )

            compose.onNodeWithText("日历").performTouchInput { click() }
            compose.onNodeWithText("${nativeInitialMonth.year} 年")
                .performTouchInput { click() }
            compose.onNodeWithText("选择年份").assertExists()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(targetMonth.year.toString())
            compose.onNodeWithText("跳转").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("${targetMonth.year} 年")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("${nativeInitialMonth.monthValue} 月")
                .performTouchInput { click() }
            compose.onNodeWithText("${targetMonth.monthValue} 月")
                .performTouchInput { click() }
            val nativeTargetDescription = "$targetDate，0 项"
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(nativeTargetDescription)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(nativeTargetDescription)
                .performTouchInput { click() }
                .assertIsSelected()
            val nativeAfterDate = WebSkinBridgeRuntime.state()
            assertEquals("year/month picker must remain on Month", "MONTH",
                nativeAfterDate.getString("page"))
            assertEquals("Native month date selection must not move the timeline camera",
                nativeAnchor, timeline(nativeAfterDate).getLong("anchorMillis"))
            assertEquals(nativeHours, timeline(nativeAfterDate).getDouble("hours"), 0.0)

            val nativeToday = LocalDate.now(zone)
            compose.onNodeWithText("今天").performTouchInput { click() }
            val nativeTodayDescription = "$nativeToday，0 项，今天"
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(nativeTodayDescription)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(nativeTodayDescription).assertIsSelected()
            val nativeAfterToday = WebSkinBridgeRuntime.state()
            assertEquals("Native Today remains in Month", "MONTH",
                nativeAfterToday.getString("page"))
            assertEquals(nativeAnchor, timeline(nativeAfterToday).getLong("anchorMillis"))
            assertEquals(nativeHours, timeline(nativeAfterToday).getDouble("hours"), 0.0)
            compose.onNodeWithText("在时间轴查看这一天").performScrollTo()
                .performTouchInput { click() }
            compose.waitUntil(10_000) {
                WebSkinBridgeRuntime.state().optString("page") == "TIMELINE"
            }
            val expectedTodayNoon = nativeToday.atTime(12, 0).atZone(zone)
                .toInstant().toEpochMilli()
            val nativeAfterTimelineJump = WebSkinBridgeRuntime.state()
            assertEquals(expectedTodayNoon,
                timeline(nativeAfterTimelineJump).getLong("anchorMillis"))
            assertEquals(nativeHours,
                timeline(nativeAfterTimelineJump).getDouble("hours"), 0.0)
            assertEquals(nativeCounts, counts(nativeStore))

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
                "!!document.querySelector('.clear-tabbar [data-action=month]')")
            val clearInitial = WebSkinBridgeRuntime.state()
            val clearCamera = timeline(clearInitial)
            val clearAnchor = clearCamera.getLong("anchorMillis")
            val clearHours = clearCamera.getDouble("hours")
            val clearCounts = counts(clearStore)

            tapClear(clearWeb, ".clear-tabbar [data-action='month']")
            awaitJs(clearWeb, "!document.querySelector('#monthPage').hidden")
            tapClear(clearWeb, "#monthYearTitle")
            awaitJs(clearWeb, "document.querySelector('#monthYearPickerDialog')?.open===true")
            assertEquals("year field is available in the real Skin dialog", "true",
                evaluate(clearWeb, "!!document.querySelector('#monthYear')"))
            evaluate(clearWeb, "(()=>{const year=document.querySelector('#monthYear');" +
                "year.value='${targetMonth.year}';year.dispatchEvent(new Event('input',{bubbles:true}));" +
                "return true})()")
            val yearSubmit = tapClear(clearWeb, "#monthYearPickerForm button[type='submit']")
            assertTrue("Year selection must be a trusted Android touch", yearSubmit.optBoolean("trusted"))
            awaitJs(clearWeb, "document.querySelector('#monthTitle')?.dataset.month?.startsWith('${targetMonth.year}-')===true")
            tapClear(clearWeb, "#monthNumberTitle")
            awaitJs(clearWeb, "document.querySelector('#monthPickerDialog')?.open===true")
            val monthSubmit = tapClear(clearWeb,
                "#monthPickerForm [data-month-number='${targetMonth.monthValue}']")
            assertTrue("Month selection must be a trusted Android touch", monthSubmit.optBoolean("trusted"))
            awaitJs(clearWeb, "document.querySelector('#monthTitle')?.dataset.month==='${targetMonth}'")
            val clearFirstDay = WebSkinBridgeRuntime.state().getJSONObject("month")
            assertEquals(targetMonth.toString(), clearFirstDay.getString("month"))
            assertEquals(targetMonth.atDay(1).toString(), clearFirstDay.getString("selectedDate"))
            val clearDateTap = tapClear(clearWeb,
                "#monthGrid .month-cell[data-date='$targetDate']")
            awaitJs(clearWeb,
                "document.querySelector('#monthAgenda h2')?.dataset.date==='$targetDate'")
            val clearAfterDate = WebSkinBridgeRuntime.state()
            val selectedMonthState = clearAfterDate.getJSONObject("month")
            assertEquals(targetMonth.toString(), selectedMonthState.getString("month"))
            assertEquals(targetDate.toString(), selectedMonthState.getString("selectedDate"))
            assertEquals(clearAnchor, timeline(clearAfterDate).getLong("anchorMillis"))
            assertEquals(clearHours, timeline(clearAfterDate).getDouble("hours"), 0.0)

            val todayTap = tapClear(clearWeb, "#monthPage [data-action='today']")
            val clearAfterToday = WebSkinBridgeRuntime.state()
            val clearTodayTimeline = timeline(clearAfterToday)
            val clearToday = java.time.Instant.ofEpochMilli(clearTodayTimeline.getLong("nowMillis"))
                .atZone(zone).toLocalDate()
            val clearTodayMonth = clearAfterToday.getJSONObject("month")
            assertEquals(clearToday.toString(), clearTodayMonth.getString("selectedDate"))
            assertEquals(YearMonth.from(clearToday).toString(), clearTodayMonth.getString("month"))
            assertEquals(clearAnchor, clearTodayTimeline.getLong("anchorMillis"))
            assertEquals(clearHours, clearTodayTimeline.getDouble("hours"), 0.0)
            awaitJs(clearWeb, "!document.querySelector('#monthPage').hidden")

            assertEquals(nativeToday.toString(), clearToday.toString())
            val timelineJumpTap = tapClear(clearWeb, "#viewMonthDate")
            assertTrue("Month-to-Timeline jump must use a trusted Android touch",
                timelineJumpTap.optBoolean("trusted"))
            awaitJs(clearWeb, "document.querySelector('#monthPage').hidden&&" +
                "!document.querySelector('#timelinePage').hidden")
            val clearAfterTimelineJump = currentBridgeState()
            assertEquals("TIMELINE", clearAfterTimelineJump.getString("page"))
            assertEquals(expectedTodayNoon,
                timeline(clearAfterTimelineJump).getLong("anchorMillis"))
            assertEquals(nativeHours,
                timeline(clearAfterTimelineJump).getDouble("hours"), 0.0)
            assertEquals("Clear picker, date, Today and Timeline jump are trusted touches", true,
                listOf(monthSubmit, clearDateTap, todayTap, timelineJumpTap)
                    .all { it.optBoolean("trusted") })
            assertEquals(clearCounts, counts(clearStore))
            assertEquals(nativeCounts, counts(nativeStore))
            println("NATIVE_CLEAR_PARITY operation=MONTH_NAVIGATION_AND_TIMELINE_JUMP kind=ACTUAL_TOUCH " +
                "pickedMonth=$targetMonth selectedDate=$targetDate today=$clearToday " +
                "nativeAndClearJumpAnchor=$expectedTodayNoon nativeCameraMatched=true " +
                "storesUnchanged=true trustedTouches=" +
                listOf(monthSubmit, clearDateTap, todayTap, timelineJumpTap)
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

    @Test
    fun nativeAndClearMonthGridSwipesNavigateOneMonthWithoutSelectingOrWriting() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-month-swipe-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-month-swipe-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val database = "month-swipe.db"
        val nativeStore = PowerStore(nativeContext, database)
        val clearStore = PowerStore(clearContext, database)
        val zone = ZoneId.systemDefault()
        var web: WebView? = null

        fun counts(store: PowerStore) = listOf(
            store.draftCount(), store.pendingCount(), store.identityCount(), store.commitCount(),
            store.nextSequencePreview(), store.nextQueueOrderPreview(), store.all().size.toLong()
        )

        fun timeline(state: JSONObject) = state.getJSONObject("timeline")

        fun awaitNativeMonth(month: YearMonth) {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("${month.year} 年").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("${month.monthValue} 月").fetchSemanticsNodes().isNotEmpty()
            }
        }

        try {
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
            val nativeInitial = WebSkinBridgeRuntime.state()
            val nativeCamera = timeline(nativeInitial)
            val nativeAnchor = nativeCamera.getLong("anchorMillis")
            val nativeHours = nativeCamera.getDouble("hours")
            val nativeCounts = counts(nativeStore)
            val initialMonth = YearMonth.from(
                java.time.Instant.ofEpochMilli(nativeAnchor).atZone(zone).toLocalDate()
            )

            compose.onNodeWithText("日历").performTouchInput { click() }
            awaitNativeMonth(initialMonth)
            val monthScrollNodes = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
            assertEquals("Native Month should expose one swipeable calendar list", 1, monthScrollNodes.size)
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeLeft() }
            val nextMonth = initialMonth.plusMonths(1)
            awaitNativeMonth(nextMonth)
            compose.onNodeWithContentDescription("${nextMonth.atDay(1)}，0 项").assertIsSelected()
            val nativeAfterNext = WebSkinBridgeRuntime.state()
            assertEquals(nativeAnchor, timeline(nativeAfterNext).getLong("anchorMillis"))
            assertEquals(nativeHours, timeline(nativeAfterNext).getDouble("hours"), 0.0)
            assertEquals(nativeCounts, counts(nativeStore))

            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeRight() }
            awaitNativeMonth(initialMonth)
            compose.onNodeWithContentDescription("${initialMonth.atDay(1)}，0 项").assertIsSelected()
            val nativeAfterPrevious = WebSkinBridgeRuntime.state()
            assertEquals(nativeAnchor, timeline(nativeAfterPrevious).getLong("anchorMillis"))
            assertEquals(nativeHours, timeline(nativeAfterPrevious).getDouble("hours"), 0.0)
            assertEquals(nativeCounts, counts(nativeStore))

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
            awaitJs(clearWeb, "!!window.PowerCalSkin&&!!document.querySelector('.clear-tabbar [data-action=month]')")
            val clearBefore = WebSkinBridgeRuntime.state()
            val clearCamera = timeline(clearBefore)
            val clearAnchor = clearCamera.getLong("anchorMillis")
            val clearHours = clearCamera.getDouble("hours")
            val clearCounts = counts(clearStore)
            tapClear(clearWeb, ".clear-tabbar [data-action='month']")
            awaitJs(clearWeb, "!document.querySelector('#monthPage').hidden")
            val clearInitialMonth = YearMonth.parse(
                WebSkinBridgeRuntime.state().getJSONObject("month").getString("month")
            )
            assertEquals("Both skins begin at the same current month", initialMonth, clearInitialMonth)

            val clearNextTouch = swipeClearMonthGrid(clearWeb, forward = true)
            awaitJs(clearWeb, "document.querySelector('#monthTitle')?.dataset.month==='$nextMonth'")
            val nextMonthState = WebSkinBridgeRuntime.state().getJSONObject("month")
            assertEquals(nextMonth, YearMonth.parse(nextMonthState.getString("month")))
            assertEquals(nextMonth.atDay(1).toString(), nextMonthState.getString("selectedDate"))
            assertTrue("Clear swipe must arrive as trusted touchscreen down/up: $clearNextTouch",
                clearNextTouch.optBoolean("downTrusted") && clearNextTouch.optBoolean("upTrusted"))
            val clearAfterNext = WebSkinBridgeRuntime.state()
            assertEquals(clearAnchor, timeline(clearAfterNext).getLong("anchorMillis"))
            assertEquals(clearHours, timeline(clearAfterNext).getDouble("hours"), 0.0)
            assertEquals(clearCounts, counts(clearStore))

            val clearPreviousTouch = swipeClearMonthGrid(clearWeb, forward = false)
            awaitJs(clearWeb, "document.querySelector('#monthTitle')?.dataset.month==='$initialMonth'")
            val previousMonthState = WebSkinBridgeRuntime.state().getJSONObject("month")
            assertEquals(initialMonth, YearMonth.parse(previousMonthState.getString("month")))
            assertEquals(initialMonth.atDay(1).toString(), previousMonthState.getString("selectedDate"))
            assertTrue("Clear reverse swipe must also be trusted: $clearPreviousTouch",
                clearPreviousTouch.optBoolean("downTrusted") && clearPreviousTouch.optBoolean("upTrusted"))
            val clearAfterPrevious = WebSkinBridgeRuntime.state()
            assertEquals(clearAnchor, timeline(clearAfterPrevious).getLong("anchorMillis"))
            assertEquals(clearHours, timeline(clearAfterPrevious).getDouble("hours"), 0.0)
            assertEquals(clearCounts, counts(clearStore))
            assertEquals(nativeCounts, counts(nativeStore))
            println("NATIVE_CLEAR_PARITY operation=MONTH_GRID_SWIPE " +
                "month=$initialMonth next=$nextMonth back=$initialMonth " +
                "nativeSelected=${initialMonth.atDay(1)} clearSelected=${previousMonthState.getString("selectedDate")} " +
                "camerasUnchanged=true storesUnchanged=true " +
                "clearTrusted=${clearNextTouch.optBoolean("downTrusted")}/${clearNextTouch.optBoolean("upTrusted")}," +
                "${clearPreviousTouch.optBoolean("downTrusted")}/${clearPreviousTouch.optBoolean("upTrusted")}")
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
