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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TimelineCreateContextWebViewInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedAppContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun isolatedDatabasePath(name: String) = storage.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
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

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val done = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { reply -> value.set(reply); done.countDown() }
        }
        assertTrue("WebView JavaScript response timed out", done.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJs(web: WebView, condition: String) {
        compose.waitUntil(30_000) { evaluate(web, condition) == "true" }
    }

    private fun tapClearSelector(web: WebView, selector: String): JSONObject {
        val selectorLiteral = JSONObject.quote(selector)
        val encoded = evaluate(web, """(()=>{
            const target=document.querySelector($selectorLiteral);
            if(!target)return JSON.stringify({found:false});
            const rect=target.getBoundingClientRect();
            const hit=document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2);
            window.__pcMonthParityTouch=null;
            target.addEventListener('click',event=>{
                window.__pcMonthParityTouch={selector:$selectorLiteral,trusted:event.isTrusted};
            },{once:true,capture:true});
            return JSON.stringify({found:true,x:rect.left+rect.width/2,y:rect.top+rect.height/2,
                cssWidth:innerWidth,cssHeight:innerHeight,
                visible:rect.width>0&&rect.height>0&&rect.left>=0&&rect.top>=0&&
                    rect.right<=innerWidth&&rect.bottom<=innerHeight,
                hitTarget:hit===target||target.contains(hit),
                disabled:target.disabled===true||target.getAttribute('aria-disabled')==='true'});
        })()""")
        val target = JSONObject(JSONArray("[$encoded]").getString(0))
        assertTrue("Clear touchscreen target is unavailable: $selector $target",
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
        assertEquals("WebView input coordinates preserve aspect ratio", scaleX, scaleY, 0.03)
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
            assertTrue("Android accepted WebView touchscreen down", automation.injectInputEvent(down, true))
            assertTrue("Android accepted WebView touchscreen up", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        awaitJs(web, "window.__pcMonthParityTouch?.selector===$selectorLiteral && " +
            "window.__pcMonthParityTouch?.trusted===true")
        return JSONObject(JSONArray("[${evaluate(web, "JSON.stringify(window.__pcMonthParityTouch)")}]" )
            .getString(0))
    }

    @Test
    fun timelineChooserUsesTrustedCameraForAllKindsAndClosingDoesNotWrite() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-timeline-create-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "timeline.db"
        val store = PowerStore(isolated, databaseName)
        val skinManager = SkinPackageManager(isolated)
        try {
            skinManager.select("powercal-clear-default")
            isolated.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
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
            awaitJs(view, "!!document.querySelector('[data-action=\\\"createDraft\\\"]')")

            var cameraValues: Triple<Long, Double, String>? = null
            compose.runOnIdle {
                val timeline = WebSkinBridgeRuntime.state().getJSONObject("timeline")
                cameraValues = Triple(
                    timeline.getLong("anchorMillis"), timeline.getDouble("hours"),
                    timeline.getString("zoneId")
                )
            }
            val (anchorMillis, hours, zoneId) = requireNotNull(cameraValues)
            val zone = ZoneId.of(zoneId)
            val expectedStart = TimelineCoordinate.snap(anchorMillis, hours, zone)
            val countsBefore = listOf(
                store.draftCount(), store.pendingCount(), store.identityCount(),
                store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                store.all().size.toLong()
            )

            for (kind in TemporalKind.entries) {
                evaluate(view, "document.querySelector('[data-action=\\\"createDraft\\\"]').click(); true")
                awaitJs(view, "!!document.querySelector('#timelineCreateDialog[open]')")
                assertEquals(
                    "true",
                    evaluate(view, "!!document.querySelector('[data-timeline-create-kind=\\\"$kind\\\"]')")
                )
                evaluate(view, "document.querySelector('[data-timeline-create-kind=\\\"$kind\\\"]').click(); true")
                awaitJs(view, "!document.querySelector('#skinSheet').hidden && !!document.querySelector('#sheetFields [data-field=title]')")

                var formJson: org.json.JSONObject? = null
                compose.runOnIdle {
                    formJson = WebSkinBridgeRuntime.state().getJSONObject("editing")
                        .getJSONObject("deck").getJSONObject("form")
                }
                val form = requireNotNull(formJson)
                assertEquals(kind.name, form.getString("kind"))
                assertEquals(zone.id, form.getString("zoneId"))
                when (kind) {
                    TemporalKind.ALARM -> assertEquals(
                        DraftForm.formatInstant(expectedStart, zone), form.getString("startText")
                    )
                    TemporalKind.ACTIVITY -> {
                        assertEquals(DraftForm.formatInstant(expectedStart, zone), form.getString("startText"))
                        assertEquals(DraftForm.formatInstant(expectedStart + 3_600_000L, zone), form.getString("endText"))
                    }
                    TemporalKind.DEADLINE -> assertEquals(
                        DraftForm.formatInstant(expectedStart, zone), form.getString("endText")
                    )
                    TemporalKind.MEMO -> assertEquals(
                        Instant.ofEpochMilli(expectedStart).atZone(zone).toLocalDate().toString(),
                        form.getString("startText")
                    )
                }

                evaluate(view, "document.querySelector('#sheetClose').click(); true")
                awaitJs(view, "document.querySelector('#skinSheet').hidden")
                compose.waitUntil(10_000) {
                    var deckOpen = true
                    compose.runOnUiThread {
                        deckOpen = WebSkinBridgeRuntime.state().getJSONObject("editing")
                            .getJSONObject("deck").optBoolean("open", false)
                    }
                    !deckOpen
                }
                val countsAfter = listOf(
                    store.draftCount(), store.pendingCount(), store.identityCount(),
                    store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                    store.all().size.toLong()
                )
                assertEquals("closing $kind must remain transient", countsBefore, countsAfter)
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            isolated.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun monthSelectionAndActivityCreateUseSelectedDateWithoutMovingTimeline() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-month-selection-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "month.db"
        val store = PowerStore(isolated, databaseName)
        val skinManager = SkinPackageManager(isolated)
        try {
            skinManager.select("powercal-clear-default")
            isolated.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
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
            awaitJs(view, """!!document.querySelector(".clear-tabbar [data-action='month']")""")
            val countsBefore = listOf(
                store.draftCount(), store.pendingCount(), store.identityCount(),
                store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                store.all().size.toLong()
            )
            var initial: org.json.JSONObject? = null
            compose.runOnIdle { initial = WebSkinBridgeRuntime.state() }
            val initialState = requireNotNull(initial)
            val camera = initialState.getJSONObject("timeline")
            val initialAnchor = camera.getLong("anchorMillis")
            val initialHours = camera.getDouble("hours")
            val zone = ZoneId.of(camera.getString("zoneId"))

            evaluate(view, """document.querySelector(".clear-tabbar [data-action='month']").click(); true""")
            awaitJs(view, "!document.querySelector('#monthPage').hidden")
            var selectedMonthDate = ""
            compose.runOnIdle {
                selectedMonthDate = WebSkinBridgeRuntime.state().getJSONObject("month")
                    .getString("selectedDate")
            }
            val currentSelection = LocalDate.parse(selectedMonthDate)
            val selectedDate = if (currentSelection.dayOfMonth != 1)
                currentSelection.withDayOfMonth(1) else currentSelection.plusDays(1)
            evaluate(view, """document.querySelector(".month-cell[data-date='$selectedDate']").click(); true""")
            awaitJs(view,
                """document.querySelector('.month-cell.selected')?.dataset.date==='$selectedDate' && """ +
                    """document.querySelector('#monthAgenda h2')?.textContent==='$selectedDate'""")
            compose.waitUntil(10_000) {
                var state: org.json.JSONObject? = null
                compose.runOnUiThread { state = WebSkinBridgeRuntime.state() }
                val value = requireNotNull(state)
                value.getString("page") == "MONTH" &&
                    value.getJSONObject("month").getString("selectedDate") == selectedDate.toString() &&
                    value.getJSONObject("timeline").getLong("anchorMillis") == initialAnchor &&
                    value.getJSONObject("timeline").getDouble("hours") == initialHours
            }

            evaluate(view, """document.querySelector("#monthPage [data-action='createDraft']").click(); true""")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=title]')")
            var form: org.json.JSONObject? = null
            compose.runOnIdle {
                form = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").getJSONObject("form")
            }
            val createdForm = requireNotNull(form)
            assertEquals("ACTIVITY", createdForm.getString("kind"))
            assertEquals(selectedDate.toString(), createdForm.getString("startText").take(10))
            compose.runOnIdle {
                val afterCreateCamera = WebSkinBridgeRuntime.state().getJSONObject("timeline")
                assertEquals(initialAnchor, afterCreateCamera.getLong("anchorMillis"))
                assertEquals(initialHours, afterCreateCamera.getDouble("hours"), 0.0)
            }

            evaluate(view, "document.querySelector('#sheetClose').click(); true")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")
            compose.waitUntil(10_000) {
                var isOpen = true
                compose.runOnUiThread {
                    isOpen = WebSkinBridgeRuntime.state().getJSONObject("editing")
                        .getJSONObject("deck").optBoolean("open", false)
                }
                !isOpen
            }
            assertEquals("closing the transient Month Activity editor must not write",
                countsBefore, listOf(
                    store.draftCount(), store.pendingCount(), store.identityCount(),
                    store.commitCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
                    store.all().size.toLong()
                ))

            evaluate(view, "document.querySelector('#viewMonthDate').click(); true")
            awaitJs(view, "document.querySelector('#monthPage').hidden && " +
                "!document.querySelector('#timelinePage').hidden")
            compose.waitUntil(10_000) {
                var state: org.json.JSONObject? = null
                compose.runOnUiThread { state = WebSkinBridgeRuntime.state() }
                val timeline = requireNotNull(state).getJSONObject("timeline")
                requireNotNull(state).getString("page") == "TIMELINE" &&
                    timeline.getLong("anchorMillis") == selectedDate.atTime(12, 0).atZone(zone)
                        .toInstant().toEpochMilli()
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            isolated.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun timelineActivityCreateMatchesNativeAndClearSnappedCameraSelection() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-timeline-camera-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-timeline-camera-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "timeline-camera-native.db"
        val clearDatabase = "timeline-camera-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val zone = ZoneId.systemDefault()
            val expectedCamera = TimelineCoordinate.initialCamera(System.currentTimeMillis(), zone)
            val expectedStart = TimelineCoordinate.snap(
                expectedCamera.anchorMillis, expectedCamera.hours, zone
            )
            val title = "Native/Clear snapped timeline Activity"
            val nativeCountsBefore = listOf(
                nativeStore.draftCount(), nativeStore.pendingCount(), nativeStore.identityCount(),
                nativeStore.commitCount(), nativeStore.nextSequencePreview(),
                nativeStore.nextQueueOrderPreview(), nativeStore.all().size.toLong()
            )
            val clearCountsBefore = listOf(
                clearStore.draftCount(), clearStore.pendingCount(), clearStore.identityCount(),
                clearStore.commitCount(), clearStore.nextSequencePreview(),
                clearStore.nextQueueOrderPreview(), clearStore.all().size.toLong()
            )
            assertEquals(nativeCountsBefore, clearCountsBefore)
            assertEquals(0L, nativeStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())
            assertEquals(0L, nativeStore.identityCount())
            assertEquals(0L, nativeStore.commitCount())
            assertEquals(1L, nativeStore.nextSequencePreview())
            assertEquals(1L, nativeStore.nextQueueOrderPreview())
            assertTrue(nativeStore.all().isEmpty())

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
                compose.onAllNodesWithContentDescription("新建事件").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("新建事件").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(TemporalKind.ACTIVITY.label).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(TemporalKind.ACTIVITY.label).performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasSetTextAction() and hasText("标题"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction() and hasText("标题")).performTextInput(title)
            assertTrue("opening the Native camera-seeded editor must remain transient",
                nativeStore.all().isEmpty())
            assertEquals(nativeCountsBefore, listOf(
                nativeStore.draftCount(), nativeStore.pendingCount(), nativeStore.identityCount(),
                nativeStore.commitCount(), nativeStore.nextSequencePreview(),
                nativeStore.nextQueueOrderPreview(), nativeStore.all().size.toLong()
            ))
            compose.onNodeWithText("发布").performTouchInput { click() }
            compose.waitUntil(30_000) { nativeStore.all().singleOrNull()?.title == title }
            val nativeRecord = nativeStore.all().single()
            val nativeForm = DraftForm.from(nativeRecord)
            assertEquals("Native starts from the snapped visible camera anchor",
                DraftForm.formatInstant(expectedStart, zone), nativeForm.startText)
            assertEquals("Native suggests a one-hour Activity span",
                DraftForm.formatInstant(expectedStart + 3_600_000L, zone), nativeForm.endText)
            assertEquals(TemporalKind.ACTIVITY, nativeRecord.kind)
            assertEquals(1L, nativeStore.commitCount())
            assertEquals(1L, nativeStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())
            assertEquals(0L, nativeStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())

            resetClear()
            web = launchClear(clearContext, clearStore)
            var clearCamera: org.json.JSONObject? = null
            compose.runOnIdle {
                clearCamera = WebSkinBridgeRuntime.state().getJSONObject("timeline")
            }
            val clearTimeline = requireNotNull(clearCamera)
            assertEquals("both fixtures open on the same local date", expectedCamera.anchorMillis,
                clearTimeline.getLong("anchorMillis"))
            assertEquals(expectedCamera.hours, clearTimeline.getDouble("hours"), 0.0)
            assertEquals(zone.id, clearTimeline.getString("zoneId"))

            val addTouch = tapClearSelector(web, "[data-action='createDraft']")
            assertTrue("Clear timeline create entry uses Android touchscreen input",
                addTouch.optBoolean("trusted"))
            awaitJs(web, "!!document.querySelector('#timelineCreateDialog[open]')")
            val activityTouch = tapClearSelector(web,
                "#timelineCreateDialog [data-timeline-create-kind=\"ACTIVITY\"]")
            assertTrue("Clear ACTIVITY selection uses Android touchscreen input",
                activityTouch.optBoolean("trusted"))
            awaitJs(web, "!document.querySelector('#timelineCreateDialog[open]') && " +
                "!document.querySelector('#skinSheet').hidden")
            var clearFormJson: org.json.JSONObject? = null
            compose.runOnIdle {
                clearFormJson = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").getJSONObject("form")
            }
            val initialClearForm = requireNotNull(clearFormJson)
            assertEquals(TemporalKind.ACTIVITY.name, initialClearForm.getString("kind"))
            assertEquals(zone.id, initialClearForm.getString("zoneId"))
            assertEquals("Clear uses the same Host-owned snapped camera selection",
                nativeForm.startText, initialClearForm.getString("startText"))
            assertEquals(nativeForm.endText, initialClearForm.getString("endText"))
            assertTrue("opening and editing the Clear candidate must remain transient",
                clearStore.all().isEmpty())
            assertEquals(clearCountsBefore, listOf(
                clearStore.draftCount(), clearStore.pendingCount(), clearStore.identityCount(),
                clearStore.commitCount(), clearStore.nextSequencePreview(),
                clearStore.nextQueueOrderPreview(), clearStore.all().size.toLong()
            ))
            editTitleForParity(web, title)
            val saveTouch = tapClearSelector(web, "#sheetSave")
            assertTrue("Clear publish request uses Android touchscreen input",
                saveTouch.optBoolean("trusted"))
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Host review must not write before confirmation", clearStore.all().isEmpty())
            assertEquals(clearCountsBefore, listOf(
                clearStore.draftCount(), clearStore.pendingCount(), clearStore.identityCount(),
                clearStore.commitCount(), clearStore.nextSequencePreview(),
                clearStore.nextQueueOrderPreview(), clearStore.all().size.toLong()
            ))
            compose.onNodeWithText("确认").performTouchInput { click() }
            compose.waitUntil(30_000) { clearStore.all().singleOrNull()?.title == title }
            val clearRecord = clearStore.all().single()
            assertEquals("Native and Clear persist the same snapped camera-seeded Activity",
                nativeForm, DraftForm.from(clearRecord))
            assertEquals(nativeRecord.eventSeq, clearRecord.eventSeq)
            assertEquals(nativeRecord.revision, clearRecord.revision)
            assertEquals(1L, clearStore.commitCount())
            assertEquals(nativeStore.commitCount(), clearStore.commitCount())
            assertEquals(nativeStore.identityCount(), clearStore.identityCount())
            assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
            assertEquals(0L, clearStore.draftCount())
            assertEquals(0, clearStore.pendingCount())
            println("NATIVE_CLEAR_PARITY operation=TIMELINE_CREATE kind=ACTIVITY " +
                "cameraAnchor=${expectedCamera.anchorMillis} snap=$expectedStart " +
                "eventSeq=${clearRecord.eventSeq} revision=${clearRecord.revision} " +
                "commits=${clearStore.commitCount()} identities=${clearStore.identityCount()} " +
                "businessForm=${DraftForm.from(clearRecord)}")
        } finally {
            resetClear()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun timelineAlarmAndDeadlineCreateMatchNativeAndClearSnappedCameraSelection() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-timeline-clock-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-timeline-clock-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "timeline-clock-native.db"
        val clearDatabase = "timeline-clock-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        val kinds = listOf(TemporalKind.ALARM, TemporalKind.DEADLINE)
        try {
            for (kind in kinds) {
                val zone = ZoneId.systemDefault()
                val expectedCamera = TimelineCoordinate.initialCamera(System.currentTimeMillis(), zone)
                val expectedStart = TimelineCoordinate.snap(
                    expectedCamera.anchorMillis, expectedCamera.hours, zone
                )
                val title = "Native/Clear timeline ${kind.name} camera pair"
                val nativeBaseline = nativeStore.all().map { DraftForm.from(it) }
                val clearBaseline = clearStore.all().map { DraftForm.from(it) }
                assertEquals("paired stores begin each kind from equal records", nativeBaseline, clearBaseline)
                val nativeCountsBefore = listOf(
                    nativeStore.draftCount(), nativeStore.pendingCount(), nativeStore.identityCount(),
                    nativeStore.commitCount(), nativeStore.nextSequencePreview(),
                    nativeStore.nextQueueOrderPreview()
                )
                val clearCountsBefore = listOf(
                    clearStore.draftCount(), clearStore.pendingCount(), clearStore.identityCount(),
                    clearStore.commitCount(), clearStore.nextSequencePreview(),
                    clearStore.nextQueueOrderPreview()
                )
                assertEquals(nativeCountsBefore, clearCountsBefore)

                resetClear()
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
                    compose.onAllNodesWithContentDescription("新建事件").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithContentDescription("新建事件").performTouchInput { click() }
                val nativeKindOption = hasText(kind.label) and hasClickAction()
                compose.waitUntil(10_000) {
                    compose.onAllNodes(nativeKindOption).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(nativeKindOption).performTouchInput { click() }
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasSetTextAction() and hasText("标题"))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(hasSetTextAction() and hasText("标题")).performTextInput(title)
                compose.onNodeWithText("发布").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    nativeStore.all().any { it.kind == kind && it.title == title }
                }
                val nativeRecord = nativeStore.all().single { it.title == title }
                val nativeForm = DraftForm.from(nativeRecord)
                assertEquals(kind, nativeRecord.kind)
                assertEquals(zone.id, nativeForm.zoneId)
                when (kind) {
                    TemporalKind.ALARM -> assertEquals(
                        DraftForm.formatInstant(expectedStart, zone), nativeForm.startText
                    )
                    TemporalKind.DEADLINE -> {
                        assertEquals(
                            "timeline selection retains exact-instant deadline precision",
                            DraftForm.formatInstant(expectedStart, zone), nativeForm.endText
                        )
                        assertEquals("a timeline deadline is not silently downgraded to date-only",
                            null, nativeRecord.deadlineDate)
                    }
                    else -> error("this pair covers only the remaining clock-based timeline kinds")
                }
                assertEquals("Native publish advances one formal commit", (nativeCountsBefore[3] as Long) + 1L,
                    nativeStore.commitCount())
                assertTrue("Native timeline editing does not leave drafts or pending entries",
                    nativeStore.draftCount() == 0L && nativeStore.pendingCount() == 0)
                assertEquals("Clear has not written before its own operation", clearBaseline,
                    clearStore.all().map { DraftForm.from(it) })
                assertEquals(clearCountsBefore, listOf(
                    clearStore.draftCount(), clearStore.pendingCount(), clearStore.identityCount(),
                    clearStore.commitCount(), clearStore.nextSequencePreview(),
                    clearStore.nextQueueOrderPreview()
                ))

                web = launchClear(clearContext, clearStore)
                val clearCamera = WebSkinBridgeRuntime.state().getJSONObject("timeline")
                assertEquals("both apps use the same local date camera",
                    expectedCamera.anchorMillis, clearCamera.getLong("anchorMillis"))
                assertEquals(expectedCamera.hours, clearCamera.getDouble("hours"), 0.0)
                assertEquals(zone.id, clearCamera.getString("zoneId"))
                val createTouch = tapClearSelector(web, "[data-action='createDraft']")
                assertTrue("Clear timeline chooser is opened by touchscreen", createTouch.optBoolean("trusted"))
                awaitJs(web, "!!document.querySelector('#timelineCreateDialog[open]')")
                val kindSelector = "#timelineCreateDialog [data-timeline-create-kind=\"${kind.name}\"]"
                val kindTouch = tapClearSelector(web, kindSelector)
                assertTrue("Clear $kind selection is a trusted Android touch", kindTouch.optBoolean("trusted"))
                awaitJs(web, "!document.querySelector('#timelineCreateDialog[open]') && " +
                    "!document.querySelector('#skinSheet').hidden")
                val clearForm = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").getJSONObject("form")
                assertEquals(kind.name, clearForm.getString("kind"))
                assertEquals(zone.id, clearForm.getString("zoneId"))
                when (kind) {
                    TemporalKind.ALARM -> assertEquals(nativeForm.startText, clearForm.getString("startText"))
                    TemporalKind.DEADLINE -> assertEquals(nativeForm.endText, clearForm.getString("endText"))
                    else -> error("this pair covers only the remaining clock-based timeline kinds")
                }
                assertEquals("Clear is transient before title entry and Host review", clearBaseline,
                    clearStore.all().map { DraftForm.from(it) })
                assertEquals(clearCountsBefore, listOf(
                    clearStore.draftCount(), clearStore.pendingCount(), clearStore.identityCount(),
                    clearStore.commitCount(), clearStore.nextSequencePreview(),
                    clearStore.nextQueueOrderPreview()
                ))

                editTitleForParity(web, title)
                val saveTouch = tapClearSelector(web, "#sheetSave")
                assertTrue("Clear formal publish request uses touchscreen", saveTouch.optBoolean("trusted"))
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("Host confirmation is write-free until approval", clearBaseline,
                    clearStore.all().map { DraftForm.from(it) })
                assertEquals(clearCountsBefore, listOf(
                    clearStore.draftCount(), clearStore.pendingCount(), clearStore.identityCount(),
                    clearStore.commitCount(), clearStore.nextSequencePreview(),
                    clearStore.nextQueueOrderPreview()
                ))
                compose.onNodeWithText("确认").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    clearStore.all().any { it.kind == kind && it.title == title }
                }
                val clearRecord = clearStore.all().single { it.title == title }
                assertEquals("same camera selection and input produce equal business forms",
                    nativeForm, DraftForm.from(clearRecord))
                assertEquals(nativeRecord.eventSeq, clearRecord.eventSeq)
                assertEquals(nativeRecord.revision, clearRecord.revision)
                assertEquals("paired stores preserve full formal record sequence",
                    nativeStore.all().map { DraftForm.from(it) }, clearStore.all().map { DraftForm.from(it) })
                assertEquals(nativeStore.commitCount(), clearStore.commitCount())
                assertEquals(nativeStore.identityCount(), clearStore.identityCount())
                assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
                assertTrue("both accepted candidates leave no drafts or pending rows",
                    nativeStore.draftCount() == 0L && clearStore.draftCount() == 0L &&
                        nativeStore.pendingCount() == 0 && clearStore.pendingCount() == 0)
                println("NATIVE_CLEAR_PARITY operation=TIMELINE_CREATE kind=${kind.name} " +
                    "cameraAnchor=${expectedCamera.anchorMillis} snap=$expectedStart " +
                    "eventSeq=${clearRecord.eventSeq} revision=${clearRecord.revision} " +
                    "commits=${clearStore.commitCount()} identities=${clearStore.identityCount()} " +
                    "businessForm=${DraftForm.from(clearRecord)}")
            }
        } finally {
            resetClear()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    @Test
    fun monthSelectedActivityCreateMatchesNativeAndClearBusinessRecord() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-month-create-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-month-create-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "month-create-native.db"
        val clearDatabase = "month-create-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val selectedDate = if (today.dayOfMonth == 1) today.plusDays(1) else today.withDayOfMonth(1)
        val title = "Native/Clear selected Month Activity"
        try {
            listOf(nativeStore, clearStore).forEach { store ->
                assertTrue(store.all().isEmpty())
                assertEquals(0L, store.commitCount())
                assertEquals(0L, store.identityCount())
                assertEquals(1L, store.nextSequencePreview())
                assertEquals(0L, store.draftCount())
            }

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
            val monthTab = hasText("日历") and hasClickAction()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("新建事件").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(monthTab).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(monthTab).performClick()
            compose.onNodeWithContentDescription("$selectedDate，0 项").performClick()
            compose.onNodeWithText("为这一天新建活动").performScrollTo().performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasSetTextAction() and hasText("标题"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction() and hasText("标题")).performTextInput(title)
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("发布").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Month selection and opening Native editor remain transient", nativeStore.all().isEmpty())
            assertEquals(0L, nativeStore.commitCount())
            assertEquals(0L, nativeStore.identityCount())
            compose.onNodeWithText("发布").performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.all().singleOrNull()?.title == title
            }
            val nativeRecord = nativeStore.all().single()
            val nativeForm = DraftForm.from(nativeRecord)
            assertEquals(TemporalKind.ACTIVITY, nativeRecord.kind)
            assertEquals(selectedDate.toString(), nativeForm.startText.take(10))
            assertEquals(selectedDate.toString(), nativeForm.endText.take(10))
            assertEquals(1L, nativeStore.commitCount())
            assertEquals(1L, nativeStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())
            assertEquals(0L, nativeStore.draftCount())

            resetClear()
            val clearView = launchClear(clearContext, clearStore)
            awaitJs(clearView, "!!document.querySelector(\".clear-tabbar [data-action='month']\")")
            val monthTouch = tapClearSelector(clearView, ".clear-tabbar [data-action='month']")
            assertTrue("Month navigation uses a trusted touchscreen event", monthTouch.optBoolean("trusted"))
            awaitJs(clearView, "!document.querySelector('#monthPage').hidden")
            val dateTouch = tapClearSelector(clearView, ".month-cell[data-date=\"$selectedDate\"]")
            assertTrue("Selected month date uses a trusted touchscreen event", dateTouch.optBoolean("trusted"))
            awaitJs(clearView, "document.querySelector('.month-cell.selected')?.dataset.date===" +
                "${org.json.JSONObject.quote(selectedDate.toString())}")
            val createTouch = tapClearSelector(clearView, "#monthPage [data-action='createDraft']")
            assertTrue("Month Activity entry uses a trusted touchscreen event", createTouch.optBoolean("trusted"))
            awaitJs(clearView, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=title]')")

            var clearForm: org.json.JSONObject? = null
            compose.runOnIdle {
                clearForm = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").getJSONObject("form")
            }
            assertEquals("Month route must create an Activity from the selected date",
                TemporalKind.ACTIVITY.name, requireNotNull(clearForm).getString("kind"))
            assertEquals(selectedDate.toString(), requireNotNull(clearForm).getString("startText").take(10))
            editTitleForParity(clearView, title)
            listOf("startText" to nativeForm.startText, "endText" to nativeForm.endText)
                .filter { (_, value) -> value.isNotEmpty() }
                .forEach { (field, value) ->
                    val selector = "#sheetFields [data-field=${org.json.JSONObject.quote(field)}]"
                    evaluate(clearView, "(()=>{const input=document.querySelector(${org.json.JSONObject.quote(selector)});" +
                        "if(!input)return false;input.value=${org.json.JSONObject.quote(value)};" +
                        "input.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
                    awaitJs(clearView, "document.querySelector(${org.json.JSONObject.quote(selector)})?.value===" +
                        "${org.json.JSONObject.quote(value)}")
                }
            assertTrue("Clear Month editor remains write-free before Host review", clearStore.all().isEmpty())
            assertEquals(0L, clearStore.commitCount())
            assertEquals(0L, clearStore.identityCount())
            val saveTouch = tapClearSelector(clearView, "#sheetSave")
            assertTrue("Publish request uses a trusted touchscreen event", saveTouch.optBoolean("trusted"))
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Host review must not write the Clear Month candidate", clearStore.all().isEmpty())
            assertEquals(0L, clearStore.commitCount())
            compose.onNodeWithText("确认").performTouchInput { click() }
            compose.waitUntil(30_000) { clearStore.all().singleOrNull()?.title == title }
            val clearRecord = clearStore.all().single()
            awaitJs(clearView, "document.querySelector('#skinSheet').hidden")
            assertEquals(TemporalKind.ACTIVITY, clearRecord.kind)
            assertEquals(selectedDate.toString(), DraftForm.from(clearRecord).startText.take(10))
            assertEquals(selectedDate.toString(), DraftForm.from(clearRecord).endText.take(10))
            assertEquals(nativeForm, DraftForm.from(clearRecord))
            assertEquals(nativeRecord.eventSeq, clearRecord.eventSeq)
            assertEquals(nativeRecord.revision, clearRecord.revision)
            assertEquals(1L, clearStore.commitCount())
            assertEquals(1L, clearStore.identityCount())
            assertEquals(2L, clearStore.nextSequencePreview())
            assertEquals(0L, clearStore.draftCount())
            assertEquals(0, clearStore.pendingCount())
            println("NATIVE_CLEAR_PARITY operation=MONTH_CREATE kind=ACTIVITY date=$selectedDate " +
                "eventSeq=${clearRecord.eventSeq} revision=${clearRecord.revision} " +
                "commits=${clearStore.commitCount()} identities=${clearStore.identityCount()} " +
                "businessForm=${DraftForm.from(clearRecord)}")
        } finally {
            resetClear()
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                    .forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    private fun editTitleForParity(web: WebView, title: String) {
        evaluate(web, "(()=>{const input=document.querySelector('#sheetFields [data-field=title]');" +
            "input.value=${org.json.JSONObject.quote(title)};" +
            "input.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
        awaitJs(web, "document.querySelector('#sheetFields [data-field=title]')?.value===" +
            "${org.json.JSONObject.quote(title)} && !document.querySelector('#sheetSave').disabled")
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
            awaitJs(it, "!!window.PowerCalSkin && !!document.querySelector('[data-action=createDraft]')")
        }
    }

    private fun resetClear() {
        compose.runOnUiThread {
            compose.activity.setContent { }
            PowerCalSkinRegistry.reset()
        }
        compose.waitForIdle()
    }
}
