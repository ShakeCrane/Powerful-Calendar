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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
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
        assertTrue("Android touchscreen sequence must reach the Clear WebView",
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
            compose.onNodeWithText("发布").performScrollTo().performClick()
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
