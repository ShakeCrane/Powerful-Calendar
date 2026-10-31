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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebSkinAllKindsTrustedWriteInstrumentedTest {
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
        private fun databasePath(name: String) = storage.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences("$namespace-$name", mode)
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
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            databasePath(name)
            return storage.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean = storage.deleteDatabase(databaseAlias(name))
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val done = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { reply -> value.set(reply); done.countDown() }
        }
        assertTrue("Clear WebView JavaScript response timed out: $expression",
            done.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONObject(JSONArray("[$encoded]").getString(0))
    }

    private fun tapWebSelector(web: WebView, selector: String): JSONObject {
        val selectorLiteral = JSONObject.quote(selector)
        val target = evaluateObject(web, """
            (()=>{
              const element=document.querySelector($selectorLiteral);
              if(!element)return {found:false};
              const rect=element.getBoundingClientRect();
              const x=rect.left+rect.width/2,y=rect.top+rect.height/2;
              const hit=document.elementFromPoint(x,y);
              window.__pcTrustedWriteTouch=null;
              element.addEventListener("click",event=>{
                window.__pcTrustedWriteTouch={trusted:event.isTrusted,action:element.dataset.do||""};
              },{once:true,capture:true});
              return {found:true,x,y,cssWidth:innerWidth,cssHeight:innerHeight,
                visible:rect.width>0&&rect.height>0&&rect.left>=0&&rect.top>=0&&
                  rect.right<=innerWidth&&rect.bottom<=innerHeight,
                hitTarget:hit===element||hit?.closest("button")===element,
                disabled:element.disabled===true};
            })()
        """.trimIndent())
        assertTrue("Clear touchscreen target missing or outside viewport: $selector $target",
            target.optBoolean("found") && target.optBoolean("visible") &&
                target.optBoolean("hitTarget") && !target.optBoolean("disabled"))
        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        val windowState = AtomicReference<String>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
            val visible = android.graphics.Rect()
            val globalVisible = web.getGlobalVisibleRect(visible)
            windowState.set("activityFocus=${compose.activity.window.decorView.hasWindowFocus()} " +
                "webFocus=${web.hasWindowFocus()} shown=${web.isShown} attached=${web.isAttachedToWindow} " +
                "globalVisible=$globalVisible rect=$visible")
        }
        val (width, height) = requireNotNull(dimensions.get())
        val scaleX = width / target.getDouble("cssWidth")
        val scaleY = height / target.getDouble("cssHeight")
        assertEquals("WebView touchscreen coordinates preserve aspect ratio", scaleX, scaleY, 0.03)
        val x = location[0] + (target.getDouble("x") * scaleX).toFloat()
        val y = location[1] + (target.getDouble("y") * scaleY).toFloat()
        val deliveredTouches = java.util.concurrent.atomic.AtomicInteger()
        compose.runOnUiThread {
            web.setOnTouchListener { _, _ -> deliveredTouches.incrementAndGet(); false }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Android accepted touchscreen down", automation.injectInputEvent(down, true))
            assertTrue("Android accepted touchscreen up", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
            compose.runOnUiThread { web.setOnTouchListener(null) }
        }
        assertTrue("Android touchscreen sequence must reach the Clear WebView; " +
            "window=$windowState deliveredTouches=${deliveredTouches.get()} " +
            "target=$target screen=($x,$y) webLocation=${location.toList()} webSize=${width}x${height}",
            deliveredTouches.get() >= 2)
        val click = evaluateObject(web, "window.__pcTrustedWriteTouch||{}")
        val dispatchState = if (click.optBoolean("trusted")) JSONObject() else evaluateObject(web, """
            (()=>{
              const element=document.querySelector($selectorLiteral);
              const rect=element?.getBoundingClientRect();
              const hit=rect&&document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2);
              return {sheetHidden:document.querySelector('#skinSheet')?.hidden,
                rect:rect&&{left:rect.left,top:rect.top,right:rect.right,bottom:rect.bottom},
                hitTarget:!!element&&(hit===element||hit?.closest('button')===element),
                scrollY:scrollY,activeTag:document.activeElement?.tagName,
                trustedTrace:window.__pcTrustedWriteTouch||null};
            })()
        """.trimIndent())
        assertTrue("Android touchscreen must dispatch a trusted Clear click: target=$target " +
            "screen=($x,$y) webLocation=${location.toList()} webSize=${width}x${height} " +
            "window=$windowState deliveredTouches=$deliveredTouches " +
            "dispatch=$dispatchState trace=$click",
            click.optBoolean("trusted"))
        return JSONObject().put("selector", selector).put("cssX", target.getDouble("x"))
            .put("cssY", target.getDouble("y")).put("screenX", x).put("screenY", y)
            .put("trustedClick", click)
    }

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = runCatching {
            evaluate(web, "JSON.stringify({page:document.querySelector('.page:not([hidden])')?.id," +
                "status:document.querySelector('#sheetStatus')?.textContent," +
                "title:document.querySelector('#sheetFields [data-field=title]')?.value," +
                "saveDisabled:document.querySelector('#sheetSave')?.disabled})")
        }.getOrDefault("unavailable")
        throw AssertionError("Clear condition timed out: $expression; state=$diagnostic")
    }

    private fun editTitle(web: WebView, title: String) {
        evaluate(web, "(()=>{const input=document.querySelector('#sheetFields [data-field=title]');" +
            "input.value=${JSONObject.quote(title)};" +
            "input.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
        awaitJs(web, "document.querySelector('#sheetFields [data-field=title]')?.value===" +
            "${JSONObject.quote(title)} && !document.querySelector('#sheetSave').disabled")
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

    private fun focusClearWindowForTouch(webView: WebView) {
        compose.runOnUiThread {
            val inputMethod = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            inputMethod.hideSoftInputFromWindow(webView.windowToken, 0)
            compose.activity.window.decorView.requestFocus()
            webView.requestFocus()
        }
        compose.waitUntil(10_000) {
            val focus = AtomicReference<Pair<Boolean, Boolean>>()
            compose.runOnUiThread {
                focus.set(compose.activity.window.decorView.hasWindowFocus() to
                    webView.hasWindowFocus())
            }
            focus.get() == (true to true)
        }
    }

    private fun resetClear() {
        compose.runOnUiThread {
            compose.activity.setContent { }
            PowerCalSkinRegistry.reset()
        }
        compose.waitForIdle()
    }

    @Test
    fun clearTransientFormalPublishForEveryKindWaitsForHostApproval() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-all-kind-publish-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "all-kind-publish.db"
        val store = PowerStore(context, database)
        var web: WebView? = null
        try {
            web = launchClear(context, store)
            var expectedNextSequence = store.nextSequencePreview()
            var expectedCommits = store.commitCount()
            var expectedIdentities = store.identityCount()

            for (kind in TemporalKind.entries) {
                val beforeRecords = store.all()
                val beforeDrafts = store.draftCount()
                val beforePending = store.pendingCount()
                evaluate(web, "document.querySelector('[data-action=createDraft]').click();true")
                awaitJs(web, "!!document.querySelector('#timelineCreateDialog[open]')")
                awaitJs(web, "!!document.querySelector('[data-timeline-create-kind=${JSONObject.quote(kind.name)}]')")
                evaluate(web, "document.querySelector('[data-timeline-create-kind=${JSONObject.quote(kind.name)}]').click();true")
                awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                    "!!document.querySelector('#sheetFields [data-field=title]')")

                var projectedKind = ""
                compose.runOnIdle {
                    projectedKind = WebSkinBridgeRuntime.state().getJSONObject("editing")
                        .getJSONObject("deck").getJSONObject("form").getString("kind")
                }
                assertEquals("Clear must route the requested kind through its real Host session",
                    kind.name, projectedKind)
                editTitle(web, "Clear ${kind.name} publication")
                assertEquals("opening and editing a transient form must not persist it", beforeRecords, store.all())
                assertEquals(beforeDrafts, store.draftCount())
                assertEquals(beforePending, store.pendingCount())
                assertEquals(expectedCommits, store.commitCount())
                assertEquals(expectedIdentities, store.identityCount())
                assertEquals(expectedNextSequence, store.nextSequencePreview())

                evaluate(web, "document.querySelector('#sheetSave').click();true")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("Host review must precede the first durable write for $kind",
                    beforeRecords, store.all())
                assertEquals(expectedCommits, store.commitCount())
                assertEquals(expectedIdentities, store.identityCount())
                compose.onNodeWithText("取消").performClick()
                awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                    "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
                assertEquals("cancel must preserve the formal store for $kind", beforeRecords, store.all())
                assertEquals(expectedCommits, store.commitCount())

                evaluate(web, "document.querySelector('#sheetSave').click();true")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(beforeRecords, store.all())
                compose.onNodeWithText("确认").performClick()
                compose.waitUntil(30_000) {
                    store.get(expectedNextSequence)?.title == "Clear ${kind.name} publication"
                }
                val published = requireNotNull(store.get(expectedNextSequence))
                assertEquals(kind, published.kind)
                assertEquals("Clear ${kind.name} publication", published.title)
                assertEquals(expectedCommits + 1, store.commitCount())
                assertEquals(expectedIdentities + 1, store.identityCount())
                assertEquals(expectedNextSequence + 1, store.nextSequencePreview())
                assertEquals(beforeDrafts, store.draftCount())
                assertEquals(beforePending, store.pendingCount())
                expectedCommits = store.commitCount()
                expectedIdentities = store.identityCount()
                expectedNextSequence = store.nextSequencePreview()
                awaitJs(web, "document.querySelector('#skinSheet').hidden")
            }
        } finally {
            resetClear()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun clearFormalEditForEveryKindWaitsForHostApprovalAndKeepsIdentity() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-all-kind-edit-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "all-kind-edit.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nearFuture = ZonedDateTime.now(zone).plusMinutes(30).withSecond(0).withNano(0)
        val forms = linkedMapOf(
            TemporalKind.ALARM to DraftForm(kind = TemporalKind.ALARM, title = "Seed ALARM",
                startText = DraftForm.formatInstant(nearFuture.toInstant().toEpochMilli(), zone),
                reminderMinutes = -1, zoneId = zone.id),
            TemporalKind.ACTIVITY to DraftForm(kind = TemporalKind.ACTIVITY, title = "Seed ACTIVITY",
                startText = DraftForm.formatInstant(nearFuture.toInstant().toEpochMilli(), zone),
                endText = DraftForm.formatInstant(nearFuture.plusHours(1).toInstant().toEpochMilli(), zone),
                reminderMinutes = -1, zoneId = zone.id),
            TemporalKind.DEADLINE to DraftForm(kind = TemporalKind.DEADLINE, title = "Seed DEADLINE",
                endText = today.toString(), reminderMinutes = -1, zoneId = zone.id),
            TemporalKind.MEMO to DraftForm(kind = TemporalKind.MEMO, title = "Seed MEMO",
                zoneId = zone.id)
        )
        val eventSequences = linkedMapOf<TemporalKind, Long>()
        forms.forEach { (kind, form) ->
            val created = store.createFormalEvent(form, "all-kind-edit-${kind.name.lowercase()}-$namespace")
            val committed = created as? PowerStore.CommitOutcome.Committed
                ?: throw AssertionError("Could not seed $kind formal event: $created")
            eventSequences[kind] = committed.eventSeq
        }
        var web: WebView? = null
        try {
            web = launchClear(context, store)
            var expectedCommits = store.commitCount()
            val expectedIdentities = store.identityCount()
            val expectedNextSequence = store.nextSequencePreview()
            val expectedDrafts = store.draftCount()
            val expectedPending = store.pendingCount()

            for (kind in TemporalKind.entries) {
                val sequence = requireNotNull(eventSequences[kind])
                val original = requireNotNull(store.get(sequence))
                if (kind == TemporalKind.MEMO) {
                    evaluate(web, "document.querySelector('.clear-tabbar [data-action=memo]').click();true")
                    awaitJs(web, "!document.querySelector('#memoPage').hidden")
                    awaitJs(web, "!!document.querySelector(\"#memoList .memo-title[data-event-seq='$sequence']\")")
                    evaluate(web, "document.querySelector(\"#memoList .memo-title[data-event-seq='$sequence']\").click();true")
                } else {
                    awaitJs(web, "!!document.querySelector(\"[data-event-seq='$sequence']\")")
                    evaluate(web, "document.querySelector(\"[data-event-seq='$sequence']\").click();true")
                }
                awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                    "document.querySelector('#sheetFields [data-field=title]')?.value===${JSONObject.quote(original.title)}")
                editTitle(web, "Clear edited ${kind.name}")
                evaluate(web, "document.querySelector('#sheetSave').click();true")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("Host review for $kind must be read-only", original, store.get(sequence))
                assertEquals(expectedCommits, store.commitCount())
                assertEquals(expectedIdentities, store.identityCount())
                compose.onNodeWithText("取消").performClick()
                awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
                assertEquals(original, store.get(sequence))
                assertEquals(expectedCommits, store.commitCount())

                evaluate(web, "document.querySelector('#sheetSave').click();true")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("retry must remain non-writing until approval for $kind",
                    original, store.get(sequence))
                compose.onNodeWithText("确认").performClick()
                compose.waitUntil(30_000) {
                    store.get(sequence)?.title == "Clear edited ${kind.name}"
                }
                val saved = requireNotNull(store.get(sequence))
                assertEquals(kind, saved.kind)
                assertEquals(original.eventSeq, saved.eventSeq)
                assertEquals(original.revision + 1, saved.revision)
                assertEquals(expectedCommits + 1, store.commitCount())
                assertEquals(expectedIdentities, store.identityCount())
                assertEquals(expectedNextSequence, store.nextSequencePreview())
                assertEquals(expectedDrafts, store.draftCount())
                assertEquals(expectedPending, store.pendingCount())
                expectedCommits = store.commitCount()
                evaluate(web, "document.querySelector('#sheetClose').click();true")
                awaitJs(web, "document.querySelector('#skinSheet').hidden")
            }
        } finally {
            resetClear()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun clearPersistedDeckCreateForEveryKindWaitsForHostApproval() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-all-kind-deck-create-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "all-kind-deck-create.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).plusHours(2).withSecond(0).withNano(0)
        val forms = linkedMapOf(
            TemporalKind.ALARM to DraftForm(kind = TemporalKind.ALARM, title = "Deck ALARM",
                startText = DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone),
                reminderMinutes = -1, zoneId = zone.id),
            TemporalKind.ACTIVITY to DraftForm(kind = TemporalKind.ACTIVITY, title = "Deck ACTIVITY",
                startText = DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone),
                endText = DraftForm.formatInstant(start.plusHours(1).toInstant().toEpochMilli(), zone),
                reminderMinutes = -1, zoneId = zone.id),
            TemporalKind.DEADLINE to DraftForm(kind = TemporalKind.DEADLINE, title = "Deck DEADLINE",
                endText = LocalDate.now(zone).plusDays(1).toString(), reminderMinutes = -1, zoneId = zone.id),
            TemporalKind.MEMO to DraftForm(kind = TemporalKind.MEMO, title = "Deck MEMO", zoneId = zone.id)
        )
        val cards = forms.mapValues { (_, form) -> store.newDraft(form) }
        var web: WebView? = null
        try {
            web = launchClear(context, store)
            awaitJs(web, "!!document.querySelector('[data-action=openDeck]') && " +
                "document.querySelector('#pendingCounter')?.textContent==='4'")
            evaluate(web, "document.querySelector('[data-action=openDeck]').click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelectorAll('.sheet-cards .sheet-card').length===4")

            var expectedCommits = store.commitCount()
            val expectedIdentities = store.identityCount()
            val expectedNextSequence = store.nextSequencePreview()
            val expectedDrafts = store.draftCount()
            var expectedPending = store.pendingCount()
            assertEquals(4, expectedPending)
            assertTrue("persisted CREATE cards must not be formal records before review", store.all().isEmpty())

            for ((kind, card) in cards) {
                val title = forms.getValue(kind).title
                val cardSelector = ".sheet-cards .sheet-card[data-draft-id='${card.draftId}']"
                awaitJs(web, "!!document.querySelector(${JSONObject.quote(cardSelector)})")
                evaluate(web, "document.querySelector(${JSONObject.quote(cardSelector)}).click();true")
                awaitJs(web, "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                    "${JSONObject.quote(title)}")
                var projectedKind = ""
                compose.runOnIdle {
                    projectedKind = WebSkinBridgeRuntime.state().getJSONObject("editing")
                        .getJSONObject("deck").getJSONObject("form").getString("kind")
                }
                assertEquals(kind.name, projectedKind)

                val recordsBefore = store.all()
                evaluate(web, "document.querySelector('#sheetSave').click();true")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("deck CREATE $kind must await trusted Host review", recordsBefore, store.all())
                assertEquals(expectedCommits, store.commitCount())
                assertEquals(expectedIdentities, store.identityCount())
                assertEquals(expectedPending, store.pendingCount())
                assertEquals(expectedNextSequence, store.nextSequencePreview())

                compose.onNodeWithText("取消").performClick()
                awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                    "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
                assertEquals("cancel must preserve the pending $kind card", recordsBefore, store.all())
                assertEquals(expectedCommits, store.commitCount())
                assertEquals(expectedPending, store.pendingCount())

                evaluate(web, "document.querySelector('#sheetSave').click();true")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(recordsBefore, store.all())
                compose.onNodeWithText("确认").performClick()
                try {
                    compose.waitUntil(30_000) {
                        store.get(card.eventSeq)?.title == title
                    }
                } catch (error: Throwable) {
                    val host = runCatching { WebSkinBridgeRuntime.state().toString() }
                        .getOrDefault("unavailable")
                    val webState = runCatching {
                        evaluate(web, "JSON.stringify({status:document.querySelector('#sheetStatus')?.textContent," +
                            "title:document.querySelector('#sheetFields [data-field=title]')?.value," +
                            "sheetHidden:document.querySelector('#skinSheet')?.hidden})")
                    }.getOrDefault("unavailable")
                    throw AssertionError("deck CREATE confirmation did not commit kind=$kind seq=${card.eventSeq}; " +
                        "records=${store.all().map { it.eventSeq to it.title }}; commits=${store.commitCount()}; " +
                        "pending=${store.pendingCount()}; web=$webState; host=$host", error)
                }
                val published = requireNotNull(store.get(card.eventSeq))
                assertEquals(kind, published.kind)
                assertEquals(title, published.title)
                assertEquals(card.eventSeq, published.eventSeq)
                assertEquals(expectedCommits + 1, store.commitCount())
                assertEquals(expectedIdentities, store.identityCount())
                assertEquals(expectedNextSequence, store.nextSequencePreview())
                assertEquals(expectedDrafts, store.draftCount())
                expectedPending -= 1
                assertEquals(expectedPending, store.pendingCount())
                expectedCommits = store.commitCount()
                awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('草稿已发布')")
                // DeckController intentionally blocks terminal actions within 600 ms to prevent
                // one double tap from publishing the next card. Model a deliberate next-card action.
                SystemClock.sleep(650)
            }
            assertEquals(4, store.all().size)
            assertEquals(0, store.pendingCount())
        } finally {
            resetClear()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun clearDeckReplacementProposalWaitsForHostApprovalAndReusesIdentity() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-deck-replace-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "deck-replace-review.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).plusHours(2).withSecond(0).withNano(0)
        val seed = DraftForm(kind = TemporalKind.ACTIVITY, title = "Replace original",
            startText = DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone),
            endText = DraftForm.formatInstant(start.plusHours(1).toInstant().toEpochMilli(), zone),
            reminderMinutes = -1, zoneId = zone.id)
        val created = store.createFormalEvent(seed, "replace-review-$namespace") as PowerStore.CommitOutcome.Committed
        val original = requireNotNull(store.get(created.eventSeq))
        var web: WebView? = null
        try {
            web = launchClear(context, store)
            val commitsBefore = store.commitCount()
            val identitiesBefore = store.identityCount()
            val nextSequenceBefore = store.nextSequencePreview()
            val draftsBefore = store.draftCount()

            evaluate(web, "document.querySelector('[data-action=openDeck]').click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && !!document.querySelector('.deck-search input')")
            val query = "#" + original.eventSeq.toString().padStart(4, '0')
            evaluate(web, "(()=>{const form=document.querySelector('.deck-search');" +
                "const input=form?.querySelector('input[type=search]');if(!form||!input)return false;" +
                "input.value=${JSONObject.quote(query)};form.requestSubmit();return true})()")
            awaitJs(web, "document.querySelectorAll('.deck-formal-results [data-do=replace]').length===1")
            evaluate(web, "document.querySelector('.deck-formal-results [data-do=replace]').click();true")
            awaitJs(web, "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                JSONObject.quote(original.title))
            val proposal = store.pendingDraftsFor(original.eventSeq).single()
            assertEquals(DraftOperation.REPLACE, proposal.operation)
            assertEquals(original, store.get(original.eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())
            assertEquals(draftsBefore + 1, store.draftCount())
            assertEquals(1, store.pendingCount())

            editTitle(web, "Replace approved through Clear")
            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("formal replacement is read-only before Host approval",
                original, store.get(original.eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            compose.onNodeWithText("取消").performClick()
            awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
            assertEquals(original, store.get(original.eventSeq))
            assertEquals(proposal.draftId, store.pendingDraftsFor(original.eventSeq).single().draftId)
            assertEquals("Replace approved through Clear", store.draft(proposal.draftId)?.form?.title)

            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(original.eventSeq))
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                store.get(original.eventSeq)?.title == "Replace approved through Clear"
            }
            val replacement = requireNotNull(store.get(original.eventSeq))
            assertEquals(original.eventSeq, replacement.eventSeq)
            assertEquals(original.revision + 1, replacement.revision)
            assertEquals(commitsBefore + 1, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())
            assertEquals(draftsBefore + 1, store.draftCount())
            assertEquals(0, store.pendingCount())
        } finally {
            resetClear()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun clearDeckDeleteCandidateWaitsForHostApprovalAndCancelPreservesEvent() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-deck-delete-review-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "deck-delete-review.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val created = store.createFormalEvent(
            DraftForm(kind = TemporalKind.MEMO, title = "Delete candidate target", zoneId = zone.id),
            "delete-review-$namespace"
        ) as PowerStore.CommitOutcome.Committed
        val original = requireNotNull(store.get(created.eventSeq))
        val candidate = store.newDraft(DraftForm.from(original), eventSeq = original.eventSeq,
            operation = DraftOperation.DELETE, expectedRevision = original.revision)
        var web: WebView? = null
        try {
            web = launchClear(context, store)
            val commitsBefore = store.commitCount()
            val identitiesBefore = store.identityCount()
            val nextSequenceBefore = store.nextSequencePreview()
            evaluate(web, "document.querySelector('[data-action=openDeck]').click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                JSONObject.quote(original.title))
            assertEquals(candidate.draftId, store.pendingDraftsFor(original.eventSeq).single().draftId)

            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("formal deletion is read-only before Host approval",
                original, store.get(original.eventSeq))
            assertFalse(store.isRetired(original.eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            compose.onNodeWithText("取消").performClick()
            awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
            assertEquals(original, store.get(original.eventSeq))
            assertEquals(candidate.draftId, store.pendingDraftsFor(original.eventSeq).single().draftId)
            assertEquals(commitsBefore, store.commitCount())

            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(original.eventSeq))
            compose.onNodeWithText("确认删除").performClick()
            compose.waitUntil(30_000) {
                store.get(original.eventSeq) == null && store.isRetired(original.eventSeq)
            }
            assertEquals(commitsBefore + 1, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())
            assertEquals(0, store.pendingCount())
        } finally {
            resetClear()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun clearFormalEditHostApprovalRejectsAConcurrentCompletionRevision() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-clear-formal-review-race-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "formal-review-race.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val created = store.createFormalEvent(
            DraftForm(kind = TemporalKind.MEMO, title = "Concurrent base", zoneId = zone.id),
            "formal-review-race-$namespace"
        ) as PowerStore.CommitOutcome.Committed
        val original = requireNotNull(store.get(created.eventSeq))
        val web: WebView
        try {
            web = launchClear(context, store)
            evaluate(web, "document.querySelector('.clear-tabbar [data-action=memo]').click();true")
            awaitJs(web, "!document.querySelector('#memoPage').hidden")
            awaitJs(web, "!!document.querySelector(\"#memoList .memo-title[data-event-seq='${original.eventSeq}']\")")
            evaluate(web, "document.querySelector(\"#memoList .memo-title[data-event-seq='${original.eventSeq}']\").click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value===${JSONObject.quote(original.title)}")
            editTitle(web, "Skin review proposal")
            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("opening Host review must not write the proposal", original,
                store.get(original.eventSeq))

            val commitsBeforeConcurrentWrite = store.commitCount()
            assertTrue("the independent completion write should advance the record version",
                store.setCompleted(original.eventSeq, "single", true, original.revision))
            val concurrent = requireNotNull(store.get(original.eventSeq))
            assertEquals(original.revision + 1, concurrent.revision)
            assertEquals(original.title, concurrent.title)
            assertTrue(concurrent.completedKeys.contains("single"))
            assertEquals(commitsBeforeConcurrentWrite + 1, store.commitCount())

            compose.onNodeWithText("确认").performClick()
            awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('存在版本冲突')")
            assertEquals("Host approval must reject the stale snapshot", concurrent,
                store.get(original.eventSeq))
            assertEquals("the proposed title must not replace the concurrent version",
                original.title, store.get(original.eventSeq)?.title)
            assertEquals("rejected Host review must add no commit",
                commitsBeforeConcurrentWrite + 1, store.commitCount())
            assertEquals(1, store.identityCount())
            assertEquals(0, store.pendingCount())
        } finally {
            resetClear()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun clearDeckHostApprovalRejectsAConcurrentDraftVersion() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-clear-deck-review-race-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val database = "deck-review-race.db"
        val store = PowerStore(context, database)
        val pending = store.newDraft(
            DraftForm(kind = TemporalKind.MEMO, title = "Pending base", zoneId = ZoneId.systemDefault().id)
        )
        var web: WebView? = null
        try {
            web = launchClear(context, store)
            val commitsBeforeReview = store.commitCount()
            val identitiesBeforeReview = store.identityCount()
            val nextSequenceBeforeReview = store.nextSequencePreview()
            evaluate(web, "document.querySelector('[data-action=openDeck]').click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value===${JSONObject.quote("Pending base")}")
            editTitle(web, "Skin reviewed draft")
            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Host review is read-only before approval", store.all().isEmpty())
            val reviewedCard = requireNotNull(store.draft(pending.draftId))
            assertEquals("Skin reviewed draft", reviewedCard.form.title)

            val concurrentForm = reviewedCard.form.copy(title = "Concurrent draft revision")
            val concurrentStage = store.stageDraft(
                reviewedCard.draftId, concurrentForm, reviewedCard.draftVersion
            )
            assertTrue("test writer must advance the persisted candidate", concurrentStage is PowerStore.StageResult.Saved)
            val concurrentCard = requireNotNull(store.draft(pending.draftId))
            assertEquals(reviewedCard.draftVersion + 1, concurrentCard.draftVersion)
            assertEquals("Concurrent draft revision", concurrentCard.form.title)

            compose.onNodeWithText("确认").performClick()
            awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('存在版本冲突')")
            assertTrue("stale approval must not publish a formal event", store.all().isEmpty())
            assertEquals(commitsBeforeReview, store.commitCount())
            assertEquals(identitiesBeforeReview, store.identityCount())
            assertEquals(nextSequenceBeforeReview, store.nextSequencePreview())
            assertEquals("the concurrent candidate must remain pending",
                concurrentCard, store.pendingDrafts().single())
        } finally {
            resetClear()
            store.close()
            context.deleteDatabase(database)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun sameEmptyMemoFixtureProducesEquivalentFormalRecordFromNativeAndClear() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-memo-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-memo-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "memo-parity-native.db"
        val clearDatabase = "memo-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            listOf(nativeStore, clearStore).forEach { store ->
                assertTrue("both renderers start from the same empty business fixture", store.all().isEmpty())
                assertEquals(0, store.commitCount())
                assertEquals(0, store.identityCount())
                assertEquals(1L, store.nextSequencePreview())
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("新建事件").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Memo 未完成 0").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("新增无日期 Memo")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("新增无日期 Memo").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasSetTextAction() and hasText("标题"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction() and hasText("标题")).performTextInput("Native/Clear parity Memo")
            compose.onNode(hasSetTextAction() and hasText("Native/Clear parity Memo")).assertExists()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("发布").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("native editing remains memory-only before publish", nativeStore.all().isEmpty())
            assertEquals(0, nativeStore.commitCount())
            assertEquals(0, nativeStore.identityCount())
            val publishNode = compose.onNodeWithText("发布")
            publishNode.assertIsDisplayed()
            publishNode.performTouchInput { click() }
            compose.waitUntil(30_000) { nativeStore.all().size == 1 }
            val nativeRecord = nativeStore.all().single()

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()

            web = launchClear(clearContext, clearStore)
            evaluate(web, "document.querySelector('.clear-tabbar [data-action=memo]').click();true")
            awaitJs(web, "!document.querySelector('#memoPage').hidden")
            evaluate(web, "document.querySelector('#memoPage [data-action=createMemo]').click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=title]')")
            editTitle(web, "Native/Clear parity Memo")
            assertTrue("Clear editing also remains memory-only before Host review", clearStore.all().isEmpty())
            assertEquals(0, clearStore.commitCount())
            assertEquals(0, clearStore.identityCount())
            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Clear Host review must not write before approval", clearStore.all().isEmpty())
            assertEquals(0, clearStore.commitCount())
            assertEquals(0, clearStore.identityCount())
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) { clearStore.all().size == 1 }
            val clearRecord = clearStore.all().single()

            assertEquals("same input through both real UI routes must persist equal business forms",
                DraftForm.from(nativeRecord), DraftForm.from(clearRecord))
            assertEquals(nativeRecord.eventSeq, clearRecord.eventSeq)
            assertEquals(nativeRecord.revision, clearRecord.revision)
            assertEquals(1L, nativeStore.commitCount())
            assertEquals(nativeStore.commitCount(), clearStore.commitCount())
            assertEquals(1, nativeStore.identityCount())
            assertEquals(nativeStore.identityCount(), clearStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())
            assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
            assertEquals(0, nativeStore.draftCount())
            assertEquals(nativeStore.draftCount(), clearStore.draftCount())
            println("NATIVE_CLEAR_PARITY kind=${clearRecord.kind} eventSeq=${clearRecord.eventSeq} " +
                "revision=${clearRecord.revision} commits=${clearStore.commitCount()} " +
                "identities=${clearStore.identityCount()} sequence=${clearStore.nextSequencePreview()} " +
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
    fun sameEmptyAlarmActivityAndDeadlineFixturesProduceEquivalentNativeAndClearRecords() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-create-parity-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-create-parity-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "create-parity-native.db"
        val clearDatabase = "create-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val kinds = listOf(TemporalKind.ALARM, TemporalKind.ACTIVITY, TemporalKind.DEADLINE)
        val nativeRecords = linkedMapOf<TemporalKind, TemporalRecord>()
        var web: WebView? = null
        try {
            listOf(nativeStore, clearStore).forEach { store ->
                assertTrue("each renderer starts from the same empty business fixture", store.all().isEmpty())
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("新建事件").fetchSemanticsNodes().isNotEmpty()
            }

            val nativeLabels = mapOf(
                TemporalKind.ALARM to "闹钟",
                TemporalKind.ACTIVITY to "活动",
                TemporalKind.DEADLINE to "截止"
            )
            for ((index, kind) in kinds.withIndex()) {
                val title = "Native/Clear create parity ${kind.name}"
                compose.onNodeWithContentDescription("新建事件").performClick()
                val kindOption = hasText(requireNotNull(nativeLabels[kind])) and hasClickAction()
                compose.waitUntil(10_000) {
                    compose.onAllNodes(kindOption).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(kindOption).performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(hasSetTextAction() and hasText("标题"))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(hasSetTextAction() and hasText("标题")).performTextInput(title)
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("发布").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("opening and filling Native $kind remains transient", index.toLong(), nativeStore.commitCount())
                assertEquals(index.toLong(), nativeStore.identityCount())
                assertEquals(0L, nativeStore.draftCount())
                compose.onNodeWithText("发布").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    nativeStore.all().size == index + 1 && nativeStore.all().lastOrNull()?.title == title
                }
                val record = nativeStore.all().last()
                assertEquals(kind, record.kind)
                assertEquals(index + 1L, record.eventSeq)
                assertEquals(1, record.revision)
                assertEquals(index + 1L, nativeStore.commitCount())
                assertEquals(index + 1L, nativeStore.identityCount())
                assertEquals(index + 2L, nativeStore.nextSequencePreview())
                assertEquals(0L, nativeStore.draftCount())
                nativeRecords[kind] = record
            }

            resetClear()
            web = launchClear(clearContext, clearStore)
            for ((index, kind) in kinds.withIndex()) {
                val nativeRecord = requireNotNull(nativeRecords[kind])
                val nativeForm = DraftForm.from(nativeRecord)
                val title = nativeRecord.title
                evaluate(web, "document.querySelector('[data-action=createDraft]').click();true")
                awaitJs(web, "!!document.querySelector('#timelineCreateDialog[open]')")
                evaluate(web, "document.querySelector('[data-timeline-create-kind=${JSONObject.quote(kind.name)}]').click();true")
                awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                    "!!document.querySelector('#sheetFields [data-field=title]')")
                editTitle(web, title)

                // Native and Clear open their defaults at different instants. Match the actual
                // Native form's visible time fields so this compares identical user input.
                listOf("startText" to nativeForm.startText, "endText" to nativeForm.endText)
                    .filter { (_, value) -> value.isNotEmpty() }
                    .forEach { (field, value) ->
                        val selector = "#sheetFields [data-field=${JSONObject.quote(field)}]"
                        evaluate(web, "(()=>{const input=document.querySelector(${JSONObject.quote(selector)});" +
                            "if(!input)return false;input.value=${JSONObject.quote(value)};" +
                            "input.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
                        awaitJs(web, "document.querySelector(${JSONObject.quote(selector)})?.value===" +
                            "${JSONObject.quote(value)}")
                    }

                assertEquals("Clear $kind remains transient before Host review", index.toLong(), clearStore.commitCount())
                assertEquals(index.toLong(), clearStore.identityCount())
                assertEquals(index, clearStore.all().size)
                assertEquals(0L, clearStore.draftCount())
                evaluate(web, "document.querySelector('#sheetSave').click();true")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("Host review must not write the Clear $kind candidate", index, clearStore.all().size)
                assertEquals(index.toLong(), clearStore.commitCount())
                assertEquals(index.toLong(), clearStore.identityCount())
                assertEquals(index + 1L, clearStore.nextSequencePreview())
                compose.onNodeWithText("确认").performClick()
                compose.waitUntil(30_000) {
                    clearStore.all().size == index + 1 && clearStore.all().lastOrNull()?.title == title
                }
                awaitJs(web, "document.querySelector('#skinSheet').hidden")
                val clearRecord = clearStore.all().last()
                assertEquals(kind, clearRecord.kind)
                assertEquals(nativeRecord.eventSeq, clearRecord.eventSeq)
                assertEquals(nativeRecord.revision, clearRecord.revision)
                assertEquals("same $kind input through Native and Clear must persist the same business form",
                    nativeForm, DraftForm.from(clearRecord))
                assertEquals(index + 1L, clearStore.commitCount())
                assertEquals(index + 1L, clearStore.identityCount())
                assertEquals(index + 2L, clearStore.nextSequencePreview())
                assertEquals(0L, clearStore.draftCount())
                assertEquals(0, clearStore.pendingCount())
                println("NATIVE_CLEAR_PARITY operation=CREATE kind=$kind eventSeq=${clearRecord.eventSeq} " +
                    "revision=${clearRecord.revision} commits=${clearStore.commitCount()} " +
                    "identities=${clearStore.identityCount()} sequence=${clearStore.nextSequencePreview()} " +
                    "businessForm=${DraftForm.from(clearRecord)}")
            }
            assertEquals(kinds.map { DraftForm.from(requireNotNull(nativeRecords[it])) },
                kinds.map { kind -> DraftForm.from(requireNotNull(clearStore.get(requireNotNull(nativeRecords[kind]).eventSeq))) })
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
    fun sameFormalMemoFixtureProducesEquivalentEditedRevisionFromNativeAndClear() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-memo-edit-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-memo-edit-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "memo-edit-parity-native.db"
        val clearDatabase = "memo-edit-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val seed = DraftForm(
                kind = TemporalKind.MEMO,
                title = "Native/Clear formal Memo seed",
                zoneId = ZoneId.systemDefault().id
            )
            val nativeCreated = nativeStore.createFormalEvent(seed, "memo-edit-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val clearCreated = clearStore.createFormalEvent(seed, "memo-edit-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val nativeOriginal = requireNotNull(nativeStore.get(nativeCreated.eventSeq))
            val clearOriginal = requireNotNull(clearStore.get(clearCreated.eventSeq))
            assertEquals("both paths begin from the same persisted business fixture",
                DraftForm.from(nativeOriginal), DraftForm.from(clearOriginal))
            assertEquals(nativeOriginal.eventSeq, clearOriginal.eventSeq)
            assertEquals(nativeOriginal.revision, clearOriginal.revision)
            assertEquals(1L, nativeStore.commitCount())
            assertEquals(nativeStore.commitCount(), clearStore.commitCount())
            assertEquals(1, nativeStore.identityCount())
            assertEquals(nativeStore.identityCount(), clearStore.identityCount())

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
                compose.onAllNodesWithContentDescription("Memo 未完成 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Memo 未完成 1").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(seed.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(seed.title).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction() and hasText(seed.title))
                .performTextReplacement("Native/Clear edited Memo")
            assertEquals("Native editor changes remain uncommitted until Save", nativeOriginal,
                nativeStore.get(nativeOriginal.eventSeq))
            assertEquals(1L, nativeStore.commitCount())
            compose.onNodeWithText("保存修改").performClick()
            compose.waitUntil(30_000) {
                nativeStore.get(nativeOriginal.eventSeq)?.title == "Native/Clear edited Memo"
            }
            val nativeEdited = requireNotNull(nativeStore.get(nativeOriginal.eventSeq))

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()

            web = launchClear(clearContext, clearStore)
            evaluate(web, "document.querySelector('.clear-tabbar [data-action=memo]').click();true")
            awaitJs(web, "!document.querySelector('#memoPage').hidden")
            awaitJs(web, "!!document.querySelector(\"#memoList .memo-title[data-event-seq='${clearOriginal.eventSeq}']\")")
            evaluate(web, "document.querySelector(\"#memoList .memo-title[data-event-seq='${clearOriginal.eventSeq}']\").click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value===${JSONObject.quote(seed.title)}")
            editTitle(web, "Native/Clear edited Memo")
            assertEquals("Clear proposal remains uncommitted before Host review", clearOriginal,
                clearStore.get(clearOriginal.eventSeq))
            assertEquals(1L, clearStore.commitCount())
            evaluate(web, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("opening Host approval must not write the Clear proposal", clearOriginal,
                clearStore.get(clearOriginal.eventSeq))
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                clearStore.get(clearOriginal.eventSeq)?.title == "Native/Clear edited Memo"
            }
            val clearEdited = requireNotNull(clearStore.get(clearOriginal.eventSeq))

            assertEquals("same edit through both real routes must persist equal business forms",
                DraftForm.from(nativeEdited), DraftForm.from(clearEdited))
            assertEquals(nativeOriginal.eventSeq, nativeEdited.eventSeq)
            assertEquals(nativeEdited.eventSeq, clearEdited.eventSeq)
            assertEquals(nativeOriginal.revision + 1, nativeEdited.revision)
            assertEquals(nativeEdited.revision, clearEdited.revision)
            assertEquals(2L, nativeStore.commitCount())
            assertEquals(nativeStore.commitCount(), clearStore.commitCount())
            assertEquals(1, nativeStore.identityCount())
            assertEquals(nativeStore.identityCount(), clearStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())
            assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
            assertEquals(0, nativeStore.draftCount())
            assertEquals(nativeStore.draftCount(), clearStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())
            assertEquals(nativeStore.pendingCount(), clearStore.pendingCount())
            println("NATIVE_CLEAR_PARITY operation=EDIT kind=${clearEdited.kind} " +
                "eventSeq=${clearEdited.eventSeq} revision=${clearEdited.revision} " +
                "commits=${clearStore.commitCount()} identities=${clearStore.identityCount()} " +
                "sequence=${clearStore.nextSequencePreview()} businessForm=${DraftForm.from(clearEdited)}")
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
    fun sameRecurringMemoCompletionProducesEquivalentNativeAndClearOccurrenceState() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-memo-completion-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-memo-completion-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "memo-completion-native.db"
        val clearDatabase = "memo-completion-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val zone = ZoneId.of("Asia/Shanghai")
            val firstDate = LocalDate.now(zone).plusDays(1)
            val occurrenceKey = "${firstDate}T00:00"
            val seed = DraftForm(
                kind = TemporalKind.MEMO,
                title = "Native/Clear recurring Memo completion",
                startText = firstDate.toString(),
                zoneId = zone.id,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3),
                trackingEnabled = true
            )
            val nativeCreated = nativeStore.createFormalEvent(seed, "memo-completion-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val clearCreated = clearStore.createFormalEvent(seed, "memo-completion-parity-seed")
                as PowerStore.CommitOutcome.Committed
            assertEquals(nativeCreated.eventSeq, clearCreated.eventSeq)
            assertEquals(DraftForm.from(requireNotNull(nativeStore.get(nativeCreated.eventSeq))),
                DraftForm.from(requireNotNull(clearStore.get(clearCreated.eventSeq))))
            val initialKeys = TodoProjection.entries(nativeStore.all(), System.currentTimeMillis(),
                includeCompleted = true).map { it.occurrenceKey }
            assertEquals("fixture must expose all three stable occurrence slots",
                listOf(0L, 1L, 2L).map { "${firstDate.plusDays(it)}T00:00" }, initialKeys)
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(1L, store.commitCount())
                assertEquals(1, store.identityCount())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
                assertEquals(2L, store.nextSequencePreview())
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("Memo 未完成 3").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Memo 未完成 3").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 3
            }
            compose.onAllNodes(isToggleable()).onFirst().performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.get(nativeCreated.eventSeq)?.completedKeys == setOf(occurrenceKey)
            }
            compose.waitUntil(30_000) {
                compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 2
            }
            val nativeCompleted = requireNotNull(nativeStore.get(nativeCreated.eventSeq))
            assertEquals(2L, nativeCompleted.revision)
            assertEquals(setOf(occurrenceKey), nativeCompleted.completedKeys)
            assertEquals(2L, nativeStore.commitCount())
            assertEquals(1, nativeStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())
            assertEquals(0L, nativeStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())

            compose.onNodeWithText("查看已完成").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(seed.title, substring = true).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 1
            }
            compose.onAllNodes(isToggleable()).onFirst().performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.get(nativeCreated.eventSeq)?.completedKeys?.isEmpty() == true
            }
            val nativeReopened = requireNotNull(nativeStore.get(nativeCreated.eventSeq))
            assertEquals(3L, nativeReopened.revision)
            assertEquals(emptySet<String>(), nativeReopened.completedKeys)
            assertEquals(3L, nativeStore.commitCount())
            assertEquals(1, nativeStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())
            assertEquals(0L, nativeStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())
            assertEquals(listOf("CREATE", "COMPLETE", "REOPEN"),
                nativeStore.history(nativeCreated.eventSeq).asReversed().map { it.operation })
            assertEquals(initialKeys,
                TodoProjection.entries(listOf(nativeReopened), System.currentTimeMillis())
                    .map { it.occurrenceKey })

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()

            web = launchClear(clearContext, clearStore)
            val memoTabTouch = tapWebSelector(web, ".clear-tabbar [data-action='memo']")
            assertTrue("Memo navigation must use a trusted Android touchscreen input",
                memoTabTouch.getJSONObject("trustedClick").optBoolean("trusted"))
            awaitJs(web, "!document.querySelector('#memoPage').hidden")
            awaitJs(web, "document.querySelector('#memoCounter')?.textContent==='3'")
            val clearToggleSelector = "#memoList [data-action='toggleMemo']" +
                "[data-event-seq='${clearCreated.eventSeq}'][data-occurrence-key='$occurrenceKey']"
            awaitJs(web, "!!document.querySelector(${JSONObject.quote(clearToggleSelector)})")
            val staleReplayHook = evaluateObject(web, """
                (()=>{
                  const channel=new MessageChannel();
                  const portPrototype=Object.getPrototypeOf(channel.port1);
                  channel.port1.close();channel.port2.close();
                  const hadOwn=Object.prototype.hasOwnProperty.call(portPrototype,"postMessage");
                  const descriptor=hadOwn?Object.getOwnPropertyDescriptor(portPrototype,"postMessage"):null;
                  const original=portPrototype.postMessage;
                  if(typeof original!=="function")return {installed:false,reason:"missing postMessage"};
                  const capture={eventSeq:${clearCreated.eventSeq},occurrenceKey:${JSONObject.quote(occurrenceKey)},
                    message:null,port:null,portPrototype,hadOwn,descriptor,original};
                  try{
                    Object.defineProperty(portPrototype,"postMessage",{
                      configurable:true,enumerable:descriptor?.enumerable??false,writable:true,
                      value:function(message,...args){
                        if(capture.message===null&&typeof message==="string"){
                          try{
                            const envelope=JSON.parse(message),payload=envelope?.payload;
                            if(envelope?.version===1&&envelope?.type==="action"&&
                              payload?.name==="toggleMemo"&&payload.eventSeq===capture.eventSeq&&
                              payload.occurrenceKey===capture.occurrenceKey){
                              capture.message=message;capture.port=this;
                            }
                          }catch{}
                        }
                        return Reflect.apply(capture.original,this,[message,...args]);
                      }
                    });
                    window.__pcMemoStaleReplay=capture;
                    return {installed:portPrototype.postMessage!==original,
                      prototype:"MessagePort",targetEventSeq:capture.eventSeq,
                      targetOccurrenceKey:capture.occurrenceKey};
                  }catch(error){
                    return {installed:false,reason:String(error)};
                  }
                })()
            """.trimIndent())
            assertTrue("Clear stale-message capture must wrap the real WebMessagePort method: $staleReplayHook",
                staleReplayHook.optBoolean("installed"))
            val completionTouch = tapWebSelector(web, clearToggleSelector)
            assertTrue("Memo completion must use a trusted Android touchscreen input",
                completionTouch.getJSONObject("trustedClick").optBoolean("trusted"))
            awaitJs(web, "document.querySelector('#memoCounter')?.textContent==='2' && " +
                "!document.querySelector(${JSONObject.quote(clearToggleSelector)})")
            compose.waitUntil(30_000) {
                clearStore.get(clearCreated.eventSeq)?.completedKeys == setOf(occurrenceKey)
            }
            val clearCompleted = requireNotNull(clearStore.get(clearCreated.eventSeq))

            val staleReplay = evaluateObject(web, """
                (()=>{
                  const capture=window.__pcMemoStaleReplay;
                  if(!capture?.message||!capture.port)return {sent:false,reason:"trusted action was not captured"};
                  const envelope=JSON.parse(capture.message),payload=envelope.payload;
                  if(payload.expectedRevision!==1||payload.completed!==false)
                    return {sent:false,reason:"captured action is not the pre-completion revision",payload};
                  if(capture.hadOwn)Object.defineProperty(capture.portPrototype,"postMessage",capture.descriptor);
                  else delete capture.portPrototype.postMessage;
                  const raw=capture.message,port=capture.port;
                  port.postMessage(raw);
                  return {sent:true,eventSeq:payload.eventSeq,expectedRevision:payload.expectedRevision,
                    occurrenceKey:payload.occurrenceKey,completed:payload.completed,
                    exactMessage:raw};
                })()
            """.trimIndent())
            assertTrue("Clear must replay the exact action emitted by the trusted completion tap: $staleReplay",
                staleReplay.optBoolean("sent"))
            assertEquals(clearCreated.eventSeq, staleReplay.getLong("eventSeq"))
            assertEquals(1L, staleReplay.getLong("expectedRevision"))
            assertEquals(occurrenceKey, staleReplay.getString("occurrenceKey"))
            assertFalse(staleReplay.getBoolean("completed"))
            awaitJs(web, "document.querySelector('#hostNotice')?.textContent.includes('Memo 已变化，请刷新')")
            assertEquals("processing the stale Clear bridge action must not alter the completed record",
                clearCompleted, clearStore.get(clearCreated.eventSeq))
            assertEquals("stale replay cannot add another commit", 2L, clearStore.commitCount())
            assertEquals(listOf("CREATE", "COMPLETE"),
                clearStore.history(clearCreated.eventSeq).asReversed().map { it.operation })
            assertEquals(1, clearStore.identityCount())
            assertEquals(2L, clearStore.nextSequencePreview())
            assertEquals(0L, clearStore.draftCount())
            assertEquals(0, clearStore.pendingCount())
            println("NATIVE_CLEAR_PARITY operation=STALE_REPLAY kind=MEMO eventSeq=${clearCreated.eventSeq} " +
                "expectedRevision=${staleReplay.getLong("expectedRevision")} currentRevision=${clearCompleted.revision} " +
                "occurrenceKey=$occurrenceKey accepted=false commits=${clearStore.commitCount()} " +
                "hostNotice=Memo已变化")

            assertEquals("Native and Clear completion states must match before undo",
                DraftForm.from(nativeCompleted), DraftForm.from(clearCompleted))
            assertEquals(nativeCompleted.eventSeq, clearCompleted.eventSeq)
            assertEquals(nativeCompleted.revision, clearCompleted.revision)
            assertEquals("other recurring slots remain incomplete",
                setOf(occurrenceKey), clearCompleted.completedKeys)
            assertEquals(2L, clearStore.commitCount())
            assertEquals(nativeStore.identityCount(), clearStore.identityCount())
            assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
            assertEquals(0L, clearStore.draftCount())
            assertEquals(0, clearStore.pendingCount())
            val remainingKeys = TodoProjection.entries(listOf(clearCompleted), System.currentTimeMillis())
                .map { it.occurrenceKey }
            assertEquals(listOf("${firstDate.plusDays(1)}T00:00", "${firstDate.plusDays(2)}T00:00"),
                remainingKeys)

            val doneFilterTouch = tapWebSelector(web, ".tabs [data-filter='done']")
            assertTrue("Clear Done filter must use trusted Android touchscreen input",
                doneFilterTouch.getJSONObject("trustedClick").optBoolean("trusted"))
            awaitJs(web, "document.querySelector(${JSONObject.quote(clearToggleSelector)})" +
                "?.getAttribute('aria-label')==='恢复未完成'")
            val reopenTouch = tapWebSelector(web, clearToggleSelector)
            assertTrue("Clear recurring Memo undo must use a trusted touchscreen tap",
                reopenTouch.getJSONObject("trustedClick").optBoolean("trusted"))
            compose.waitUntil(30_000) {
                clearStore.get(clearCreated.eventSeq)?.completedKeys?.isEmpty() == true
            }
            val clearReopened = requireNotNull(clearStore.get(clearCreated.eventSeq))
            assertEquals(3L, clearReopened.revision)
            assertEquals(emptySet<String>(), clearReopened.completedKeys)
            assertEquals(3L, clearStore.commitCount())
            assertEquals(nativeStore.commitCount(), clearStore.commitCount())
            assertEquals(nativeStore.identityCount(), clearStore.identityCount())
            assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
            assertEquals(0L, clearStore.draftCount())
            assertEquals(0, clearStore.pendingCount())
            assertEquals(listOf("CREATE", "COMPLETE", "REOPEN"),
                clearStore.history(clearCreated.eventSeq).asReversed().map { it.operation })
            assertEquals(initialKeys,
                TodoProjection.entries(listOf(clearReopened), System.currentTimeMillis())
                    .map { it.occurrenceKey })
            assertEquals("Native and Clear must persist the same reopened occurrence state",
                DraftForm.from(nativeReopened), DraftForm.from(clearReopened))
            assertEquals(nativeReopened.eventSeq, clearReopened.eventSeq)
            assertEquals(nativeReopened.revision, clearReopened.revision)

            val openFilterTouch = tapWebSelector(web, ".tabs [data-filter='open']")
            assertTrue("Clear Open filter must use trusted Android touchscreen input",
                openFilterTouch.getJSONObject("trustedClick").optBoolean("trusted"))
            awaitJs(web, "document.querySelector(${JSONObject.quote(clearToggleSelector)})" +
                "?.getAttribute('aria-label')==='完成 Memo'")
            println("NATIVE_CLEAR_PARITY operation=COMPLETE_REOPEN kind=MEMO eventSeq=${clearReopened.eventSeq} " +
                "occurrenceKey=$occurrenceKey revision=${clearReopened.revision} " +
                "history=${clearStore.history(clearReopened.eventSeq).map { it.operation }} " +
                "commits=${clearStore.commitCount()} identities=${clearStore.identityCount()} " +
                "remaining=$initialKeys trustedUndo=true")
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
    fun sameFormalAlarmActivityAndInstantDeadlineEditsProduceEquivalentNativeAndClearRecords() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-formal-edit-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-formal-edit-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "formal-edit-parity-native.db"
        val clearDatabase = "formal-edit-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val zone = ZoneId.systemDefault()
            val start = ZonedDateTime.now(zone).plusHours(2).withSecond(0).withNano(0)
            val startText = DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone)
            val endText = DraftForm.formatInstant(start.plusHours(1).toInstant().toEpochMilli(), zone)
            val forms = listOf(
                TemporalKind.ALARM to DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "Native/Clear edit parity ALARM seed",
                    startText = startText,
                    zoneId = zone.id,
                    reminderMinutes = -1
                ),
                TemporalKind.ACTIVITY to DraftForm(
                    kind = TemporalKind.ACTIVITY,
                    title = "Native/Clear edit parity ACTIVITY seed",
                    startText = startText,
                    endText = endText,
                    zoneId = zone.id,
                    reminderMinutes = -1
                ),
                TemporalKind.DEADLINE to DraftForm(
                    kind = TemporalKind.DEADLINE,
                    title = "Native/Clear edit parity exact DEADLINE seed",
                    endText = startText,
                    zoneId = zone.id,
                    reminderMinutes = -1
                )
            )
            val nativeSequences = linkedMapOf<TemporalKind, Long>()
            val clearSequences = linkedMapOf<TemporalKind, Long>()
            forms.forEach { (kind, form) ->
                val nativeCreated = nativeStore.createFormalEvent(
                    form, "formal-edit-parity-$kind-native-$nativeNamespace"
                ) as PowerStore.CommitOutcome.Committed
                val clearCreated = clearStore.createFormalEvent(
                    form, "formal-edit-parity-$kind-clear-$clearNamespace"
                ) as PowerStore.CommitOutcome.Committed
                nativeSequences[kind] = nativeCreated.eventSeq
                clearSequences[kind] = clearCreated.eventSeq
                assertEquals(nativeCreated.eventSeq, clearCreated.eventSeq)
                assertEquals(
                    DraftForm.from(requireNotNull(nativeStore.get(nativeCreated.eventSeq))),
                    DraftForm.from(requireNotNull(clearStore.get(clearCreated.eventSeq)))
                )
            }
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(3L, store.commitCount())
                assertEquals(3, store.identityCount())
                assertEquals(4L, store.nextSequencePreview())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
            }

            fun showNativeApp() {
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
                compose.onNodeWithContentDescription("今日 · 复位为全天")
                    .performTouchInput { click() }
            }

            val nativeEdited = linkedMapOf<TemporalKind, TemporalRecord>()
            showNativeApp()
            for ((kind, _) in forms) {
                val sequence = requireNotNull(nativeSequences[kind])
                val original = requireNotNull(nativeStore.get(sequence))
                val newTitle = "Native/Clear edited ${kind.name}"
                val eventMatcher = hasContentDescription(original.title, substring = true) and hasClickAction()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(eventMatcher).fetchSemanticsNodes().size == 1
                }
                compose.onNode(eventMatcher).performTouchInput { click() }
                try {
                    compose.waitUntil(30_000) {
                        compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isNotEmpty()
                    }
                } catch (error: Throwable) {
                    val editorFailures = compose.onAllNodesWithText(
                        "Unable to open editor", substring = true, useUnmergedTree = true
                    ).fetchSemanticsNodes().size
                    val visibleLabels = listOf("编辑事件", "删除事件", "保存修改", "保存检查点并退出")
                        .associateWith { label ->
                            compose.onAllNodesWithText(label, substring = true, useUnmergedTree = true)
                                .fetchSemanticsNodes().size
                        }
                    throw AssertionError(
                        "Native $kind editor did not open for seq=$sequence; " +
                            "editorFailures=$editorFailures visibleLabels=$visibleLabels " +
                            "form=${DraftForm.from(original)}",
                        error
                    )
                }
                compose.onNode(hasSetTextAction() and hasText(original.title))
                    .performTextReplacement(newTitle)
                assertEquals("Native $kind proposal is read-only before Save", original,
                    nativeStore.get(sequence))
                assertEquals(3L + nativeEdited.size, nativeStore.commitCount())
                compose.onNodeWithText("保存修改").performTouchInput { click() }
                compose.waitUntil(30_000) { nativeStore.get(sequence)?.title == newTitle }
                val saved = requireNotNull(nativeStore.get(sequence))
                assertEquals(kind, saved.kind)
                assertEquals(sequence, saved.eventSeq)
                assertEquals(original.revision + 1, saved.revision)
                assertEquals(4L + nativeEdited.size, nativeStore.commitCount())
                assertEquals(3, nativeStore.identityCount())
                assertEquals(4L, nativeStore.nextSequencePreview())
                assertEquals(0L, nativeStore.draftCount())
                assertEquals(0, nativeStore.pendingCount())
                nativeEdited[kind] = saved
                compose.onNodeWithText("存档并退出").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isEmpty()
                }
                assertEquals("exiting a clean saved Native session must not stage a draft", 0L,
                    nativeStore.draftCount())
                assertEquals(0, nativeStore.pendingCount())
            }

            compose.runOnUiThread {
                val focused = compose.activity.currentFocus
                val inputMethod = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                focused?.let {
                    inputMethod.hideSoftInputFromWindow(it.windowToken, 0)
                    it.clearFocus()
                }
            }
            resetClear()
            web = launchClear(clearContext, clearStore)
            val clearWindowFocus = AtomicReference<Pair<Boolean, Boolean>>()
            compose.waitUntil(10_000) {
                compose.runOnUiThread {
                    clearWindowFocus.set(
                        compose.activity.window.decorView.hasWindowFocus() to
                            requireNotNull(web).hasWindowFocus()
                    )
                }
                clearWindowFocus.get() == (true to true)
            }
            assertEquals("Clear Activity and WebView must be window-focused before edit replay",
                true to true, clearWindowFocus.get())
            val clearEdited = linkedMapOf<TemporalKind, TemporalRecord>()
            for ((kind, _) in forms) {
                val sequence = requireNotNull(clearSequences[kind])
                val original = requireNotNull(clearStore.get(sequence))
                val newTitle = "Native/Clear edited ${kind.name}"
                val eventSelector = "[data-event-seq='$sequence']"
                awaitJs(web, "!!document.querySelector(${JSONObject.quote(eventSelector)})")
                evaluate(web, "document.querySelector(${JSONObject.quote(eventSelector)}).click();true")
                awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                    "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                    "${JSONObject.quote(original.title)}")
                focusClearWindowForTouch(web)
                assertEquals("Clear $kind editor must not be covered by Native editor UI", 0,
                    compose.onAllNodesWithText("保存检查点并退出").fetchSemanticsNodes().size)
                editTitle(web, newTitle)
                assertEquals("Clear $kind proposal is read-only before Host review", original,
                    clearStore.get(sequence))
                assertEquals(3L + clearEdited.size, clearStore.commitCount())

                focusClearWindowForTouch(web)
                val cancelledSave = tapWebSelector(web, "#sheetSave")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(original, clearStore.get(sequence))
                assertEquals(3L + clearEdited.size, clearStore.commitCount())
                compose.onNodeWithText("取消").performTouchInput { click() }
                awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
                assertEquals("cancelling Clear $kind review must preserve the formal event", original,
                    clearStore.get(sequence))
                assertEquals(3L + clearEdited.size, clearStore.commitCount())

                focusClearWindowForTouch(web)
                val approvedSave = tapWebSelector(web, "#sheetSave")
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(original, clearStore.get(sequence))
                compose.onNodeWithText("确认").performTouchInput { click() }
                compose.waitUntil(30_000) { clearStore.get(sequence)?.title == newTitle }
                awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('事件已保存')")
                evaluate(web, "document.querySelector('#sheetClose').click();true")
                awaitJs(web, "document.querySelector('#skinSheet').hidden")
                val saved = requireNotNull(clearStore.get(sequence))
                assertEquals(kind, saved.kind)
                assertEquals(sequence, saved.eventSeq)
                assertEquals(original.revision + 1, saved.revision)
                assertEquals(4L + clearEdited.size, clearStore.commitCount())
                assertEquals(3, clearStore.identityCount())
                assertEquals(4L, clearStore.nextSequencePreview())
                assertEquals(0L, clearStore.draftCount())
                assertEquals(0, clearStore.pendingCount())
                clearEdited[kind] = saved
                println("NATIVE_CLEAR_PARITY operation=EDIT kind=$kind eventSeq=$sequence " +
                    "revision=${saved.revision} commits=${clearStore.commitCount()} " +
                    "identities=${clearStore.identityCount()} sequence=${clearStore.nextSequencePreview()} " +
                    "cancelledSave=$cancelledSave approvedSave=$approvedSave " +
                    "businessForm=${DraftForm.from(saved)}")
            }

            for ((kind, _) in forms) {
                val nativeRecord = nativeEdited.getValue(kind)
                val clearRecord = clearEdited.getValue(kind)
                assertEquals("Native and Clear $kind edits produce the same business form",
                    DraftForm.from(nativeRecord), DraftForm.from(clearRecord))
                assertEquals(nativeRecord.eventSeq, clearRecord.eventSeq)
                assertEquals(nativeRecord.revision, clearRecord.revision)
            }
            assertEquals(6L, nativeStore.commitCount())
            assertEquals(nativeStore.commitCount(), clearStore.commitCount())
            assertEquals(3, nativeStore.identityCount())
            assertEquals(nativeStore.identityCount(), clearStore.identityCount())
            assertEquals(4L, nativeStore.nextSequencePreview())
            assertEquals(nativeStore.nextSequencePreview(), clearStore.nextSequencePreview())
            assertEquals(0L, nativeStore.draftCount())
            assertEquals(nativeStore.draftCount(), clearStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())
            assertEquals(nativeStore.pendingCount(), clearStore.pendingCount())
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
    fun sameFormalAlarmConversionToActivityProducesEquivalentNativeAndClearRecords() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-kind-convert-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-kind-convert-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "kind-convert-native.db"
        val clearDatabase = "kind-convert-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val zone = ZoneId.systemDefault()
            val start = ZonedDateTime.now(zone).withHour(15).withMinute(0)
                .withSecond(0).withNano(0)
            val end = start.plusHours(1)
            val startText = DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone)
            val endText = DraftForm.formatInstant(end.toInstant().toEpochMilli(), zone)
            val seed = DraftForm(
                kind = TemporalKind.ALARM,
                title = "Native/Clear kind conversion parity",
                startText = startText,
                zoneId = zone.id,
                reminderMinutes = -1
            )
            val nativeCreated = nativeStore.createFormalEvent(seed, "kind-conversion-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val clearCreated = clearStore.createFormalEvent(seed, "kind-conversion-parity-seed")
                as PowerStore.CommitOutcome.Committed
            assertEquals(nativeCreated.eventSeq, clearCreated.eventSeq)
            val eventSeq = nativeCreated.eventSeq
            val nativeOriginal = requireNotNull(nativeStore.get(eventSeq))
            val clearOriginal = requireNotNull(clearStore.get(eventSeq))
            assertEquals(DraftForm.from(nativeOriginal), DraftForm.from(clearOriginal))
            assertEquals(nativeOriginal.revision, clearOriginal.revision)
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(1L, store.commitCount())
                assertEquals(1, store.identityCount())
                assertEquals(2L, store.nextSequencePreview())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天")
                .performTouchInput { click() }
            val eventMatcher = hasContentDescription(nativeOriginal.title, substring = true) and hasClickAction()
            compose.waitUntil(30_000) {
                compose.onAllNodes(eventMatcher).fetchSemanticsNodes().size == 1
            }
            compose.onNode(eventMatcher).performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("闹钟 ▾").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("闹钟 ▾").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("活动").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("活动").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("结束").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("结束").performScrollTo().performTextReplacement(endText)
            assertEquals("Native conversion proposal must not write before Save",
                nativeOriginal, nativeStore.get(eventSeq))
            assertEquals(1L, nativeStore.commitCount())
            compose.onNodeWithText("保存修改").performTouchInput { click() }
            compose.waitUntil(30_000) {
                val saved = nativeStore.get(eventSeq)
                saved?.kind == TemporalKind.ACTIVITY && saved.endMillis == end.toInstant().toEpochMilli()
            }
            val nativeSaved = requireNotNull(nativeStore.get(eventSeq))
            assertEquals(eventSeq, nativeSaved.eventSeq)
            assertEquals(nativeOriginal.revision + 1, nativeSaved.revision)
            assertEquals(2L, nativeStore.commitCount())
            assertEquals(1, nativeStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())
            assertEquals(0L, nativeStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())

            resetClear()
            web = launchClear(clearContext, clearStore)
            val clearEventSelector = "[data-event-seq='$eventSeq']"
            awaitJs(web, "!!document.querySelector(${JSONObject.quote(clearEventSelector)})")
            evaluate(web, "document.querySelector(${JSONObject.quote(clearEventSelector)}).click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=kind]')")
            evaluate(web, "(()=>{const field=document.querySelector('#sheetFields [data-field=kind]');" +
                "field.value='ACTIVITY';field.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
            awaitJs(web, "document.querySelector('#sheetFields [data-field=kind]')?.value==='ACTIVITY' && " +
                "!!document.querySelector('#sheetFields [data-field=endText]')")
            evaluate(web, "(()=>{const field=document.querySelector('#sheetFields [data-field=endText]');" +
                "field.value=${JSONObject.quote(endText)};" +
                "field.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
            awaitJs(web, "document.querySelector('#sheetFields [data-field=endText]')?.value===" +
                "${JSONObject.quote(endText)} && !document.querySelector('#sheetSave').disabled")
            compose.runOnIdle {
                val projected = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("editor").getJSONObject("form")
                assertEquals("ACTIVITY", projected.getString("kind"))
                assertEquals(startText, projected.getString("startText"))
                assertEquals(endText, projected.getString("endText"))
            }
            assertEquals("Clear conversion remains a proposal before Host review",
                clearOriginal, clearStore.get(eventSeq))
            assertEquals(1L, clearStore.commitCount())
            assertEquals(1, clearStore.identityCount())
            assertEquals(2L, clearStore.nextSequencePreview())
            assertEquals(0L, clearStore.draftCount())
            assertEquals(0, clearStore.pendingCount())

            focusClearWindowForTouch(web)
            val saveTouch = tapWebSelector(web, "#sheetSave")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(clearOriginal, clearStore.get(eventSeq))
            assertEquals(1L, clearStore.commitCount())
            compose.onNodeWithText("取消").performTouchInput { click() }
            awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
            assertEquals("Host review cancellation keeps the original kind", clearOriginal,
                clearStore.get(eventSeq))
            assertEquals(1L, clearStore.commitCount())

            focusClearWindowForTouch(web)
            tapWebSelector(web, "#sheetSave")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(clearOriginal, clearStore.get(eventSeq))
            compose.onNodeWithText("确认").performTouchInput { click() }
            compose.waitUntil(30_000) {
                val saved = clearStore.get(eventSeq)
                saved?.kind == TemporalKind.ACTIVITY && saved.endMillis == end.toInstant().toEpochMilli()
            }
            val clearSaved = requireNotNull(clearStore.get(eventSeq))
            assertEquals("Native and Clear conversion produce the same business form",
                DraftForm.from(nativeSaved), DraftForm.from(clearSaved))
            assertEquals(nativeSaved.eventSeq, clearSaved.eventSeq)
            assertEquals(nativeSaved.revision, clearSaved.revision)
            assertEquals(TemporalKind.ACTIVITY, clearSaved.kind)
            assertEquals(start.toInstant().toEpochMilli(), clearSaved.startMillis)
            assertEquals(end.toInstant().toEpochMilli(), clearSaved.endMillis)
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(2L, store.commitCount())
                assertEquals(1, store.identityCount())
                assertEquals(2L, store.nextSequencePreview())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
            }
            println("NATIVE_CLEAR_PARITY operation=CONVERT from=ALARM to=ACTIVITY " +
                "eventSeq=$eventSeq revision=${clearSaved.revision} commits=${clearStore.commitCount()} " +
                "identities=${clearStore.identityCount()} sequence=${clearStore.nextSequencePreview()} " +
                "cancelledHostReview=true trustedSave=${saveTouch.getJSONObject("trustedClick").optBoolean("trusted")} " +
                "businessForm=${DraftForm.from(clearSaved)}")
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
    fun samePersistedMemoDeckEditFlushReopenPublishMatchesNativeAndClear() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-deck-life-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-deck-life-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "deck-life-native.db"
        val clearDatabase = "deck-life-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val initialTitle = "Native/Clear persisted Memo draft"
            val updatedTitle = "Native/Clear flushed Memo draft"
            val form = DraftForm(
                kind = TemporalKind.MEMO,
                title = initialTitle,
                zoneId = ZoneId.systemDefault().id
            )
            val nativeCard = nativeStore.newDraft(form, actor = "deck-lifecycle-parity")
            val clearCard = clearStore.newDraft(form, actor = "deck-lifecycle-parity")
            assertEquals(nativeCard.draftId, clearCard.draftId)
            assertEquals(nativeCard.eventSeq, clearCard.eventSeq)
            assertEquals(form, nativeStore.draft(nativeCard.draftId)?.form)
            assertEquals(form, clearStore.draft(clearCard.draftId)?.form)
            listOf(nativeStore, clearStore).forEach { store ->
                assertTrue(store.all().isEmpty())
                assertEquals(1L, store.draftCount())
                assertEquals(1, store.pendingCount())
                assertEquals(1L, store.identityCount())
                assertEquals(2L, store.nextSequencePreview())
                assertEquals(0L, store.commitCount())
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("待处理 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("待处理 1").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("保存并退出待处理")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("展开完整编辑").performTouchInput { click() }
            val nativeTitleMatcher = hasSetTextAction() and hasText(initialTitle)
            compose.waitUntil(15_000) {
                compose.onAllNodes(nativeTitleMatcher).fetchSemanticsNodes().size == 1
            }
            compose.onNode(nativeTitleMatcher).performTextReplacement(updatedTitle)
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("1 / 1 · 待暂存").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Native keystrokes remain a proposal until the deck is closed",
                form, nativeStore.draft(nativeCard.draftId)?.form)
            compose.onNodeWithContentDescription("保存并退出待处理")
                .performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("保存并退出待处理")
                    .fetchSemanticsNodes().isEmpty() &&
                    nativeStore.draft(nativeCard.draftId)?.form?.title == updatedTitle
            }
            val nativeFlushed = requireNotNull(nativeStore.draft(nativeCard.draftId))
            assertEquals(1, nativeStore.pendingCount())
            assertEquals(0L, nativeStore.commitCount())
            assertTrue(nativeStore.all().isEmpty())

            compose.onNodeWithContentDescription("待处理 1").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(updatedTitle).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("发布").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("发布").performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.get(nativeCard.eventSeq)?.title == updatedTitle
            }
            val nativePublished = requireNotNull(nativeStore.get(nativeCard.eventSeq))
            assertEquals(TemporalKind.MEMO, nativePublished.kind)
            assertEquals(nativeCard.eventSeq, nativePublished.eventSeq)
            assertEquals(updatedTitle, nativePublished.title)
            assertEquals(1L, nativeStore.commitCount())
            assertEquals(1L, nativeStore.draftCount())
            assertEquals(0, nativeStore.pendingCount())
            assertEquals(1L, nativeStore.identityCount())
            assertEquals(2L, nativeStore.nextSequencePreview())

            resetClear()
            web = launchClear(clearContext, clearStore)
            awaitJs(web, "window.PowerCalSkin && document.querySelector('#pendingCounter')?.textContent==='1'")
            focusClearWindowForTouch(web)
            val openTouch = tapWebSelector(web, "[data-action=openDeck]")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelectorAll('.sheet-cards .sheet-card').length===1")
            val clearCardSelector = ".sheet-cards .sheet-card[data-draft-id='${clearCard.draftId}']"
            tapWebSelector(web, clearCardSelector)
            awaitJs(web, "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                "${JSONObject.quote(initialTitle)}")
            editTitle(web, updatedTitle)
            awaitJs(web, "document.querySelector('#sheetSubtitle')?.textContent.includes('尚未暂存')")
            assertEquals("Clear edits remain dirty in the real Host controller before flush",
                form, clearStore.draft(clearCard.draftId)?.form)
            focusClearWindowForTouch(web)
            val closeTouch = tapWebSelector(web, "#sheetClose")
            awaitJs(web, "document.querySelector('#skinSheet').hidden")
            compose.waitUntil(15_000) {
                clearStore.draft(clearCard.draftId)?.form?.title == updatedTitle
            }
            val clearFlushed = requireNotNull(clearStore.draft(clearCard.draftId))
            assertEquals("Native and Clear close flush the same persisted form",
                nativeFlushed.form, clearFlushed.form)
            assertEquals(nativeFlushed.draftVersion, clearFlushed.draftVersion)
            assertEquals(1, clearStore.pendingCount())
            assertEquals(0L, clearStore.commitCount())
            assertTrue(clearStore.all().isEmpty())

            focusClearWindowForTouch(web)
            tapWebSelector(web, "[data-action=openDeck]")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelectorAll('.sheet-cards .sheet-card').length===1")
            tapWebSelector(web, clearCardSelector)
            awaitJs(web, "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                "${JSONObject.quote(updatedTitle)}")
            val projectedDraftVersion = compose.runOnIdle {
                WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("deck").getLong("version")
            }
            assertEquals(clearFlushed.draftVersion, projectedDraftVersion)
            assertEquals(0L, clearStore.commitCount())
            assertEquals(1, clearStore.pendingCount())
            assertEquals("Host review may not publish the persisted deck candidate early",
                emptyList<TemporalRecord>(), clearStore.all())
            compose.runOnIdle { assertEquals(0L, clearStore.commitCount()) }
            focusClearWindowForTouch(web)
            val firstPublishTouch = tapWebSelector(web, "#sheetSave")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(clearStore.all().isEmpty())
            assertEquals(0L, clearStore.commitCount())
            compose.onNodeWithText("取消").performTouchInput { click() }
            awaitJs(web, "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
            assertEquals(clearCard.draftId, clearStore.pendingDrafts().single().draftId)
            assertEquals(updatedTitle, clearStore.draft(clearCard.draftId)?.form?.title)
            assertTrue(clearStore.all().isEmpty())
            assertEquals(0L, clearStore.commitCount())

            focusClearWindowForTouch(web)
            val approvedPublishTouch = tapWebSelector(web, "#sheetSave")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(clearStore.all().isEmpty())
            compose.onNodeWithText("确认").performTouchInput { click() }
            compose.waitUntil(30_000) {
                clearStore.get(clearCard.eventSeq)?.title == updatedTitle
            }
            val clearPublished = requireNotNull(clearStore.get(clearCard.eventSeq))
            assertEquals("Native and Clear deck lifecycle publish the same event",
                DraftForm.from(nativePublished), DraftForm.from(clearPublished))
            assertEquals(nativePublished.eventSeq, clearPublished.eventSeq)
            assertEquals(nativePublished.revision, clearPublished.revision)
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(1L, store.commitCount())
                assertEquals(1L, store.draftCount())
                assertEquals(0, store.pendingCount())
                assertEquals(1L, store.identityCount())
                assertEquals(2L, store.nextSequencePreview())
            }
            println("NATIVE_CLEAR_PARITY operation=DECK_LIFECYCLE kind=MEMO draftId=${clearCard.draftId} " +
                "eventSeq=${clearPublished.eventSeq} version=${clearFlushed.draftVersion} " +
                "commits=${clearStore.commitCount()} identities=${clearStore.identityCount()} " +
                "sequence=${clearStore.nextSequencePreview()} " +
                "openTouch=${openTouch.getJSONObject("trustedClick").optBoolean("trusted")} " +
                "closeTouch=${closeTouch.getJSONObject("trustedClick").optBoolean("trusted")} " +
                "cancelledPublish=${firstPublishTouch.getJSONObject("trustedClick").optBoolean("trusted")} " +
                "approvedPublish=${approvedPublishTouch.getJSONObject("trustedClick").optBoolean("trusted")} " +
                "businessForm=${DraftForm.from(clearPublished)}")
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
    fun sameFormalMemoDeleteProducesEquivalentNativeAndClearTombstones() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-memo-delete-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-memo-delete-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "memo-delete-parity-native.db"
        val clearDatabase = "memo-delete-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val seed = DraftForm(
                kind = TemporalKind.MEMO,
                title = "Native/Clear delete parity Memo",
                zoneId = ZoneId.systemDefault().id
            )
            val nativeCreated = nativeStore.createFormalEvent(seed, "memo-delete-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val clearCreated = clearStore.createFormalEvent(seed, "memo-delete-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val nativeOriginal = requireNotNull(nativeStore.get(nativeCreated.eventSeq))
            val clearOriginal = requireNotNull(clearStore.get(clearCreated.eventSeq))
            assertEquals(DraftForm.from(nativeOriginal), DraftForm.from(clearOriginal))
            assertEquals(nativeOriginal.eventSeq, clearOriginal.eventSeq)
            assertEquals(nativeOriginal.revision, clearOriginal.revision)
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(1L, store.commitCount())
                assertEquals(1, store.identityCount())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
                assertEquals(2L, store.nextSequencePreview())
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("Memo 未完成 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Memo 未完成 1").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(seed.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(seed.title).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("删除事件").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("删除事件").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Native delete prompt does not write", nativeOriginal,
                nativeStore.get(nativeOriginal.eventSeq))
            assertEquals(1L, nativeStore.commitCount())
            compose.onNodeWithText("取消").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("Native cancellation preserves the formal event", nativeOriginal,
                nativeStore.get(nativeOriginal.eventSeq))
            assertEquals(1L, nativeStore.commitCount())

            compose.onNodeWithText("删除事件").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认删除").performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.get(nativeOriginal.eventSeq) == null &&
                    nativeStore.isRetired(nativeOriginal.eventSeq)
            }
            val nativeTombstone = requireNotNull(nativeStore.getIncludingRetired(nativeOriginal.eventSeq))

            resetClear()
            web = launchClear(clearContext, clearStore)
            evaluate(web, "document.querySelector('.clear-tabbar [data-action=memo]').click();true")
            awaitJs(web, "!document.querySelector('#memoPage').hidden")
            val clearTitleSelector = "#memoList .memo-title[data-event-seq='${clearOriginal.eventSeq}']"
            awaitJs(web, "!!document.querySelector(${JSONObject.quote(clearTitleSelector)})")
            evaluate(web, "document.querySelector(${JSONObject.quote(clearTitleSelector)}).click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value===${JSONObject.quote(seed.title)}")

            val cancelTouch = tapWebSelector(web, "#sheetActions [data-do=deleteEvent]")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Clear Host review does not write before approval", clearOriginal,
                clearStore.get(clearOriginal.eventSeq))
            assertEquals(1L, clearStore.commitCount())
            compose.onNodeWithText("取消").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isEmpty()
            }
            awaitJs(web, "!document.querySelector('#skinSheet').hidden")
            assertEquals("Clear cancellation preserves the formal event", clearOriginal,
                clearStore.get(clearOriginal.eventSeq))
            assertEquals(1L, clearStore.commitCount())

            val approveTouch = tapWebSelector(web, "#sheetActions [data-do=deleteEvent]")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(clearOriginal, clearStore.get(clearOriginal.eventSeq))
            compose.onNodeWithText("确认删除").performTouchInput { click() }
            compose.waitUntil(30_000) {
                clearStore.get(clearOriginal.eventSeq) == null &&
                    clearStore.isRetired(clearOriginal.eventSeq)
            }
            awaitJs(web, "document.querySelector('#skinSheet').hidden")
            val clearTombstone = requireNotNull(clearStore.getIncludingRetired(clearOriginal.eventSeq))

            assertEquals("Native and Clear retire the same business record",
                DraftForm.from(nativeTombstone), DraftForm.from(clearTombstone))
            assertEquals(nativeOriginal.eventSeq, clearTombstone.eventSeq)
            assertEquals(nativeTombstone.revision, clearTombstone.revision)
            assertEquals(nativeTombstone.revision, nativeOriginal.revision + 1)
            for ((label, store) in listOf("Native" to nativeStore, "Clear" to clearStore)) {
                assertTrue("$label must retain the same event as a restorable tombstone",
                    store.isRetired(nativeOriginal.eventSeq))
                assertEquals(2L, store.commitCount())
                assertEquals("DELETE", store.history(nativeOriginal.eventSeq).first().operation)
                assertEquals(1, store.identityCount())
                assertEquals(2L, store.nextSequencePreview())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
            }
            println("NATIVE_CLEAR_PARITY operation=DELETE kind=MEMO eventSeq=${clearTombstone.eventSeq} " +
                "revision=${clearTombstone.revision} commits=${clearStore.commitCount()} " +
                "identities=${clearStore.identityCount()} sequence=${clearStore.nextSequencePreview()} " +
                "cancelTouch=$cancelTouch approveTouch=$approveTouch tombstone=${DraftForm.from(clearTombstone)}")
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
    fun sameFormalActivityDeleteProducesEquivalentNativeAndClearTombstones() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-activity-delete-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-activity-delete-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "activity-delete-parity-native.db"
        val clearDatabase = "activity-delete-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val zone = ZoneId.systemDefault()
            val start = ZonedDateTime.now(zone).plusMinutes(20).withSecond(0).withNano(0)
            val seed = DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "Native/Clear delete parity Activity",
                startText = DraftForm.formatInstant(start.toInstant().toEpochMilli(), zone),
                endText = DraftForm.formatInstant(start.plusHours(1).toInstant().toEpochMilli(), zone),
                zoneId = zone.id,
                reminderMinutes = -1
            )
            val nativeCreated = nativeStore.createFormalEvent(seed, "activity-delete-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val clearCreated = clearStore.createFormalEvent(seed, "activity-delete-parity-seed")
                as PowerStore.CommitOutcome.Committed
            val nativeOriginal = requireNotNull(nativeStore.get(nativeCreated.eventSeq))
            val clearOriginal = requireNotNull(clearStore.get(clearCreated.eventSeq))
            assertEquals(DraftForm.from(nativeOriginal), DraftForm.from(clearOriginal))
            assertEquals(nativeOriginal.eventSeq, clearOriginal.eventSeq)
            assertEquals(nativeOriginal.revision, clearOriginal.revision)
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(1L, store.commitCount())
                assertEquals(1, store.identityCount())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
                assertEquals(2L, store.nextSequencePreview())
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
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天").performClick()
            val eventMatcher = hasContentDescription(seed.title, substring = true) and hasClickAction()
            compose.waitUntil(30_000) {
                compose.onAllNodes(eventMatcher).fetchSemanticsNodes().size == 1
            }
            compose.onNode(eventMatcher).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("删除事件").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("删除事件").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Native Activity delete review does not write", nativeOriginal,
                nativeStore.get(nativeOriginal.eventSeq))
            assertEquals(1L, nativeStore.commitCount())
            compose.onNodeWithText("取消").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("Native Activity cancellation preserves the formal event", nativeOriginal,
                nativeStore.get(nativeOriginal.eventSeq))
            assertEquals(1L, nativeStore.commitCount())

            compose.onNodeWithText("删除事件").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认删除").performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.get(nativeOriginal.eventSeq) == null &&
                    nativeStore.isRetired(nativeOriginal.eventSeq)
            }
            val nativeTombstone = requireNotNull(nativeStore.getIncludingRetired(nativeOriginal.eventSeq))

            resetClear()
            web = launchClear(clearContext, clearStore)
            val eventSelector = "[data-event-seq='${clearOriginal.eventSeq}']"
            awaitJs(web, "!!document.querySelector(${JSONObject.quote(eventSelector)})")
            evaluate(web, "document.querySelector(${JSONObject.quote(eventSelector)}).click();true")
            awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value===${JSONObject.quote(seed.title)}")

            val cancelTouch = tapWebSelector(web, "#sheetActions [data-do=deleteEvent]")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Clear Activity review does not write before approval", clearOriginal,
                clearStore.get(clearOriginal.eventSeq))
            assertEquals(1L, clearStore.commitCount())
            compose.onNodeWithText("取消").performTouchInput { click() }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("Clear Activity cancellation preserves the formal event", clearOriginal,
                clearStore.get(clearOriginal.eventSeq))
            assertEquals(1L, clearStore.commitCount())

            val approveTouch = tapWebSelector(web, "#sheetActions [data-do=deleteEvent]")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(clearOriginal, clearStore.get(clearOriginal.eventSeq))
            compose.onNodeWithText("确认删除").performTouchInput { click() }
            compose.waitUntil(30_000) {
                clearStore.get(clearOriginal.eventSeq) == null &&
                    clearStore.isRetired(clearOriginal.eventSeq)
            }
            awaitJs(web, "document.querySelector('#skinSheet').hidden")
            val clearTombstone = requireNotNull(clearStore.getIncludingRetired(clearOriginal.eventSeq))

            assertEquals(DraftForm.from(nativeTombstone), DraftForm.from(clearTombstone))
            assertEquals(nativeOriginal.eventSeq, clearTombstone.eventSeq)
            assertEquals(nativeTombstone.revision, clearTombstone.revision)
            assertEquals(nativeTombstone.revision, nativeOriginal.revision + 1)
            for ((label, store) in listOf("Native" to nativeStore, "Clear" to clearStore)) {
                assertTrue("$label must retain the same event as a restorable tombstone",
                    store.isRetired(nativeOriginal.eventSeq))
                assertEquals(2L, store.commitCount())
                assertEquals("DELETE", store.history(nativeOriginal.eventSeq).first().operation)
                assertEquals(1, store.identityCount())
                assertEquals(2L, store.nextSequencePreview())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
            }
            println("NATIVE_CLEAR_PARITY operation=DELETE kind=ACTIVITY eventSeq=${clearTombstone.eventSeq} " +
                "revision=${clearTombstone.revision} commits=${clearStore.commitCount()} " +
                "identities=${clearStore.identityCount()} sequence=${clearStore.nextSequencePreview()} " +
                "cancelTouch=$cancelTouch approveTouch=$approveTouch tombstone=${DraftForm.from(clearTombstone)}")
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
    fun sameFormalAlarmAndInstantDeadlineDeleteProduceEquivalentNativeAndClearTombstones() {
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-native-clear-point-delete-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-native-clear-point-delete-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedAppContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedAppContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "point-delete-parity-native.db"
        val clearDatabase = "point-delete-parity-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        var web: WebView? = null
        try {
            val zone = ZoneId.systemDefault()
            val point = ZonedDateTime.now(zone).plusMinutes(20).withSecond(0).withNano(0)
            val pointText = DraftForm.formatInstant(point.toInstant().toEpochMilli(), zone)
            val forms = linkedMapOf(
                TemporalKind.ALARM to DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "Native/Clear delete parity Alarm",
                    startText = pointText,
                    zoneId = zone.id,
                    reminderMinutes = -1
                ),
                TemporalKind.DEADLINE to DraftForm(
                    kind = TemporalKind.DEADLINE,
                    title = "Native/Clear delete parity Deadline",
                    endText = pointText,
                    zoneId = zone.id,
                    reminderMinutes = -1
                )
            )
            val nativeOriginals = linkedMapOf<TemporalKind, TemporalRecord>()
            val clearOriginals = linkedMapOf<TemporalKind, TemporalRecord>()
            forms.forEach { (kind, form) ->
                val nativeCreated = nativeStore.createFormalEvent(form, "point-delete-${kind.name}-native")
                    as PowerStore.CommitOutcome.Committed
                val clearCreated = clearStore.createFormalEvent(form, "point-delete-${kind.name}-clear")
                    as PowerStore.CommitOutcome.Committed
                nativeOriginals[kind] = requireNotNull(nativeStore.get(nativeCreated.eventSeq))
                clearOriginals[kind] = requireNotNull(clearStore.get(clearCreated.eventSeq))
                assertEquals(DraftForm.from(nativeOriginals.getValue(kind)),
                    DraftForm.from(clearOriginals.getValue(kind)))
                assertEquals(nativeOriginals.getValue(kind).eventSeq,
                    clearOriginals.getValue(kind).eventSeq)
                assertEquals(kind, nativeOriginals.getValue(kind).kind)
                assertEquals(kind, clearOriginals.getValue(kind).kind)
            }
            listOf(nativeStore, clearStore).forEach { store ->
                assertEquals(2L, store.commitCount())
                assertEquals(2, store.identityCount())
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
                assertEquals(3L, store.nextSequencePreview())
            }

            val nativeTombstones = linkedMapOf<TemporalKind, TemporalRecord>()
            resetClear()
            web = launchClear(clearContext, clearStore)
            val clearTouchTraces = linkedMapOf<TemporalKind, Pair<JSONObject, JSONObject>>()
            val clearTombstones = linkedMapOf<TemporalKind, TemporalRecord>()
            for (kind in forms.keys) {
                val original = clearOriginals.getValue(kind)
                val before = clearStore.commitCount()
                val eventSelector = "[data-event-seq='${original.eventSeq}']"
                awaitJs(web, "!!document.querySelector(${JSONObject.quote(eventSelector)})")
                evaluate(web, "document.querySelector(${JSONObject.quote(eventSelector)}).click();true")
                awaitJs(web, "!document.querySelector('#skinSheet').hidden && " +
                    "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                    "${JSONObject.quote(original.title)}")
                focusClearWindowForTouch(web)
                assertEquals("Clear $kind editor must not be covered by Native editor UI", 0,
                    compose.onAllNodesWithText("保存检查点并退出").fetchSemanticsNodes().size)
                val activityFocused = AtomicReference<Boolean>()
                compose.runOnUiThread {
                    activityFocused.set(compose.activity.window.decorView.hasWindowFocus())
                }
                assertTrue("Clear $kind editor must retain Activity window focus", activityFocused.get())
                val deleteSelector = "#sheetActions [data-do=deleteEvent]"
                val cancelTouch = tapWebSelector(web, deleteSelector)
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("Clear $kind review must not write", original,
                    clearStore.get(original.eventSeq))
                assertEquals(before, clearStore.commitCount())
                assertEquals(2, clearStore.identityCount())
                assertEquals(3L, clearStore.nextSequencePreview())
                assertEquals(0L, clearStore.draftCount())
                assertEquals(0, clearStore.pendingCount())
                compose.onNodeWithText("取消").performTouchInput { click() }
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isEmpty()
                }
                assertEquals("Clear $kind cancellation preserves the formal event", original,
                    clearStore.get(original.eventSeq))
                assertEquals(before, clearStore.commitCount())
                val approveTouch = tapWebSelector(web, deleteSelector)
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(original, clearStore.get(original.eventSeq))
                compose.onNodeWithText("确认删除").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    clearStore.get(original.eventSeq) == null &&
                        clearStore.isRetired(original.eventSeq)
                }
                awaitJs(web, "document.querySelector('#skinSheet').hidden")
                clearTombstones[kind] = requireNotNull(
                    clearStore.getIncludingRetired(original.eventSeq)
                )
                clearTouchTraces[kind] = cancelTouch to approveTouch
                assertEquals(before + 1, clearStore.commitCount())
            }

            // Exercise Clear before opening Native's separate Compose editor in this
            // shared test Activity so its touchscreen path is independently exercised.
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
                compose.onAllNodesWithContentDescription("今日 · 复位为全天")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("今日 · 复位为全天").performClick()

            for (kind in forms.keys) {
                val original = nativeOriginals.getValue(kind)
                val before = nativeStore.commitCount()
                val eventMatcher = hasContentDescription(original.title, substring = true) and hasClickAction()
                compose.waitUntil(30_000) {
                    compose.onAllNodes(eventMatcher).fetchSemanticsNodes().size == 1
                }
                compose.onNode(eventMatcher).performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("删除事件").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("删除事件").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("Native $kind review must not write", original,
                    nativeStore.get(original.eventSeq))
                assertEquals(before, nativeStore.commitCount())
                compose.onNodeWithText("取消").performTouchInput { click() }
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isEmpty()
                }
                assertEquals("Native $kind cancellation preserves the formal event", original,
                    nativeStore.get(original.eventSeq))
                assertEquals(before, nativeStore.commitCount())
                compose.onNodeWithText("删除事件").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("确认删除事件？").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("确认删除").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    nativeStore.get(original.eventSeq) == null &&
                        nativeStore.isRetired(original.eventSeq)
                }
                nativeTombstones[kind] = requireNotNull(
                    nativeStore.getIncludingRetired(original.eventSeq)
                )
                assertEquals(before + 1, nativeStore.commitCount())
            }

            for (kind in forms.keys) {
                val original = nativeOriginals.getValue(kind)
                val nativeTombstone = nativeTombstones.getValue(kind)
                val clearTombstone = clearTombstones.getValue(kind)
                assertEquals("Native and Clear $kind tombstone forms match",
                    DraftForm.from(nativeTombstone), DraftForm.from(clearTombstone))
                assertEquals(original.eventSeq, clearTombstone.eventSeq)
                assertEquals(original.revision + 1, nativeTombstone.revision)
                assertEquals(nativeTombstone.revision, clearTombstone.revision)
                for ((label, store) in listOf("Native" to nativeStore, "Clear" to clearStore)) {
                    assertTrue("$label $kind remains as a restorable tombstone",
                        store.isRetired(original.eventSeq))
                    assertEquals("DELETE", store.history(original.eventSeq).first().operation)
                    assertEquals(4L, store.commitCount())
                    assertEquals(2, store.identityCount())
                    assertEquals(3L, store.nextSequencePreview())
                    assertEquals(0L, store.draftCount())
                    assertEquals(0, store.pendingCount())
                }
                val touches = clearTouchTraces.getValue(kind)
                println("NATIVE_CLEAR_PARITY operation=DELETE kind=$kind " +
                    "eventSeq=${clearTombstone.eventSeq} revision=${clearTombstone.revision} " +
                    "commits=${clearStore.commitCount()} identities=${clearStore.identityCount()} " +
                    "sequence=${clearStore.nextSequencePreview()} cancelTouch=${touches.first} " +
                    "approveTouch=${touches.second} tombstone=${DraftForm.from(clearTombstone)}")
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
}
