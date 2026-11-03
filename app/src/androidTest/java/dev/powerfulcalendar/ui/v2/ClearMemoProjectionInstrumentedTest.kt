package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ClearMemoProjectionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val result = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { value ->
                result.set(value)
                complete.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out", complete.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONObject(org.json.JSONArray("[$encoded]").getString(0))
    }

    private fun tapWebSelector(web: WebView, selector: String): JSONObject {
        val quotedSelector = JSONObject.quote(selector)
        val target = evaluateObject(web, """
            (()=>{
              const element=document.querySelector($quotedSelector);
              if(!element)return {found:false};
              const rect=element.getBoundingClientRect();
              const x=rect.left+rect.width/2,y=rect.top+rect.height/2;
              const hit=document.elementFromPoint(x,y);
              window.__f04Tap=null;
              element.addEventListener("click",event=>{
                window.__f04Tap={trusted:event.isTrusted};
              },{once:true,capture:true});
              return {found:true,x,y,width:innerWidth,height:innerHeight,
                visible:rect.width>0&&rect.height>0&&rect.left>=0&&rect.top>=0&&
                  rect.right<=innerWidth&&rect.bottom<=innerHeight,
                hitTarget:hit===element||hit?.closest("button")===element,
                disabled:element.disabled===true};
            })()
        """.trimIndent())
        assertTrue("Memo target must be visible and hit-testable: $selector $target",
            target.optBoolean("found") && target.optBoolean("visible") &&
                target.optBoolean("hitTarget") && !target.optBoolean("disabled"))

        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (width, height) = requireNotNull(dimensions.get())
        val scaleX = width / target.getDouble("width")
        val scaleY = height / target.getDouble("height")
        assertEquals("WebView touch coordinates preserve aspect ratio", scaleX, scaleY, 0.03)
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
            assertTrue("Android accepted Memo touch down", automation.injectInputEvent(down, true))
            assertTrue("Android accepted Memo touch up", automation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        compose.waitUntil(5_000) {
            evaluate(web, "window.__f04Tap?.trusted===true") == "true"
        }
        return JSONObject().put("selector", selector).put("trusted", true)
    }

    private fun memoSignatures(entries: org.json.JSONArray): List<String> =
        (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            "${entry.getLong("eventSeq")}:${entry.getString("occurrenceKey")}:" +
                "${entry.getLong("revision")}:${entry.getBoolean("completed")}"
        }

    @Test
    fun packagedClearProjectsPersistedRecurringMemoIntoItsWebView() {
        val storage = compose.activity.applicationContext
        val context = storage
        val databaseName = "clear-memo-${UUID.randomUUID()}.db"
        var store = PowerStore(context, databaseName)
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val zone = ZoneId.of("Asia/Shanghai")
        val firstDate = LocalDate.now(zone).plusDays(2)
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "Clear recurring memo smoke",
            startText = firstDate.toString(),
            zoneId = zone.id,
            trackingEnabled = true,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        assertEquals(3, queryMemoEntriesForTest(store, System.currentTimeMillis()).size)

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                skinManager.select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && " +
                    "!!document.querySelector('.clear-tabbar [data-action=memo]') && " +
                    "!!document.querySelector('#memoCounter')") == "true"
            }

            val initialHostState = AtomicReference<JSONObject>()
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=memo]').click(); true")
            try {
                compose.waitUntil(10_000) {
                    compose.runOnUiThread { initialHostState.set(WebSkinBridgeRuntime.state()) }
                    val snapshot = initialHostState.get()
                    snapshot.optString("page") == "MEMO" &&
                        snapshot.optBoolean("hostDataReady") &&
                        snapshot.optJSONArray("events")?.length() == 1 &&
                        snapshot.optInt("memoCount") == 3 &&
                        snapshot.optJSONArray("memoEntries")?.length() == 3
                }
            } catch (error: Throwable) {
                throw AssertionError(
                    "First Memo navigation did not converge to its durable Host projection: " +
                        "persisted=${store.all().size}; state=${initialHostState.get()}",
                    error
                )
            }

            // Exercise the explicit pre-load protocol as a separate second navigation.
            evaluate(view, "document.querySelector('.clear-tabbar [data-action=timeline]').click(); true")
            compose.waitUntil(10_000) {
                compose.runOnUiThread { initialHostState.set(WebSkinBridgeRuntime.state()) }
                initialHostState.get().optString("page") == "TIMELINE"
            }

            // Hold the Web client at the pre-load Host state so the action queue is tested
            // deterministically even when this emulator loads the Store before the WebView.
            val delayedState = WebSkinBridgeRuntime.state().put("hostDataReady", false)
            val delayedFrame = JSONObject().put("version", 1).put("type", "state")
                .put("payload", delayedState)
            val delayedWire = JSONObject.quote(delayedFrame.toString())
            evaluate(view, "window.PowerCalSkin.receive($delayedWire); " +
                "document.querySelector('.clear-tabbar [data-action=memo]').click(); true")
            val beforeRelease = AtomicReference<JSONObject>()
            compose.runOnUiThread { beforeRelease.set(WebSkinBridgeRuntime.state()) }
            assertEquals("TIMELINE", beforeRelease.get().getString("page"))

            val readyState = JSONObject(delayedState.toString()).put("hostDataReady", true)
            val readyFrame = JSONObject().put("version", 1).put("type", "state")
                .put("payload", readyState)
            evaluate(view, "window.PowerCalSkin.receive(${JSONObject.quote(readyFrame.toString())}); true")
            val hostState = AtomicReference<JSONObject>()
            try {
                compose.waitUntil(10_000) {
                    compose.runOnUiThread { hostState.set(WebSkinBridgeRuntime.state()) }
                    val snapshot = hostState.get()
                    snapshot.optString("page") == "MEMO" &&
                        snapshot.optJSONArray("events")?.length() == 1 &&
                        snapshot.optInt("memoCount") == 3 &&
                        snapshot.optJSONArray("memoEntries")?.length() == 3
                }
            } catch (error: Throwable) {
                throw AssertionError(
                    "Clear did not deliver its first page action and projection: persisted=${store.all().size}; " +
                        "state=${hostState.get()}",
                    error
                )
            }
            val state = requireNotNull(hostState.get())
            assertTrue(state.getBoolean("hostDataReady"))
            assertEquals(1, state.getJSONArray("events").length())
            assertEquals(3, state.getInt("memoCount"))

            try {
                compose.waitUntil(10_000) {
                    evaluate(view, "!document.querySelector('#memoPage').hidden && " +
                        "document.querySelector('#memoCounter').textContent==='3' && " +
                        "document.querySelector('#memoList .memo-title')?.textContent.includes('Clear recurring memo smoke')") == "true"
                }
            } catch (error: Throwable) {
                val webState = runCatching {
                    evaluate(view, "JSON.stringify({memoHidden:document.querySelector('#memoPage')?.hidden," +
                        "counter:document.querySelector('#memoCounter')?.textContent," +
                        "rows:document.querySelector('#memoList')?.innerHTML," +
                        "bridgeReady:!!window.PowerCalSkin})")
                }.getOrDefault("unavailable")
                throw AssertionError("Clear Memo page failed to render: web=$webState; " +
                    "initial=${initialHostState.get()}; host=${WebSkinBridgeRuntime.state()}", error)
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) restoreOnboarding.putBoolean(
                "system-calendar-import-asked", askedForCalendarImport
            ) else restoreOnboarding.remove("system-calendar-import-asked")
            restoreOnboarding.commit()
        }
    }

    @Test
    fun packagedClearQueuesDraftPublishAfterImmediateFieldChange() {
        val context = compose.activity.applicationContext
        val databaseName = "clear-editor-queue-${UUID.randomUUID()}.db"
        val store = PowerStore(context, databaseName)
        val draft = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "Before queued publish",
            zoneId = "Asia/Shanghai"
        ))
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)

        try {
            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }

            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && " +
                    "!!document.querySelector('[data-action=openDeck]')") == "true"
            }
            // This test targets command ordering after an edit, not the separate pre-ready
            // outbox case covered by the Memo startup test above.
            compose.waitUntil(30_000) {
                evaluate(view, "document.querySelector('#pendingCounter')?.textContent==='1'") == "true"
            }
            evaluate(view, "document.querySelector('[data-action=openDeck]').click(); true")
            try {
                compose.waitUntil(15_000) {
                    evaluate(view, "!document.querySelector('#skinSheet').hidden && " +
                        "document.querySelector('#sheetFields [data-field=title]')?.value === " +
                        "'Before queued publish'") == "true"
                }
            } catch (error: Throwable) {
                val hostState = AtomicReference<JSONObject>()
                compose.runOnUiThread { hostState.set(WebSkinBridgeRuntime.state()) }
                val webState = runCatching {
                    evaluate(view, "JSON.stringify({sheetHidden:document.querySelector('#skinSheet')?.hidden," +
                        "title:document.querySelector('#sheetFields [data-field=title]')?.value," +
                        "deck:!!document.querySelector('.deck-search')," +
                        "ready:!!window.PowerCalSkin,body:document.body?.innerText?.slice(0,400)})")
                }.getOrDefault("unavailable")
                throw AssertionError("Clear did not open the persisted draft: host=${hostState.get()}; web=$webState",
                    error)
            }

            // The patch acknowledgement and updated Host projection have not been awaited.
            evaluate(view, "(()=>{const input=document.querySelector('#sheetFields [data-field=title]');" +
                "input.value='After queued publish';" +
                "input.dispatchEvent(new Event('change',{bubbles:true}));" +
                "document.querySelector('#sheetSave').click(); return true})()")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }

            assertTrue("the Host approval must still guard formal writes", store.all().isEmpty())
            assertEquals("the queued patch must reach the durable draft before approval",
                "After queued publish", store.draft(draft.draftId)?.form?.title)
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                store.all().singleOrNull()?.title == "After queued publish"
            }
            assertEquals(0, store.pendingCount())
            assertEquals(1, store.all().size)
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) restoreOnboarding.putBoolean(
                "system-calendar-import-asked", askedForCalendarImport
            ) else restoreOnboarding.remove("system-calendar-import-asked")
            restoreOnboarding.commit()
        }
    }

    @Test
    fun packagedClearPages251MemosAndKeepsTrustedTitleAndCompletionActionsDistinct() {
        val context = compose.activity.applicationContext
        val databaseName = "clear-memo-pages-${UUID.randomUUID()}.db"
        var store = PowerStore(context, databaseName)
        var storeOpen = true
        val skinManager = SkinPackageManager(context)
        val originalSkin = skinManager.currentId()
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        val hadAskedForCalendarImport = onboarding.contains("system-calendar-import-asked")
        val askedForCalendarImport = onboarding.getBoolean("system-calendar-import-asked", false)
        val zone = ZoneId.of("Asia/Shanghai")
        val dateText = LocalDate.now(zone).plusDays(1).toString()
        val firstEventSeq = 1L

        fun hostState(): JSONObject {
            val state = AtomicReference<JSONObject>()
            compose.runOnUiThread { state.set(WebSkinBridgeRuntime.state()) }
            return requireNotNull(state.get())
        }

        fun launchClearMemo(): WebView {
            PowerCalSkinRegistry.reset()
            skinManager.select("powercal-clear-default")
            compose.runOnUiThread {
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            val view = requireNotNull(web)
            compose.waitUntil(30_000) {
                evaluate(view, "!!window.PowerCalSkin && " +
                    "!!document.querySelector('.clear-tabbar [data-action=memo]')") == "true"
            }
            val navigation = tapWebSelector(view, ".clear-tabbar [data-action='memo']")
            assertTrue("Memo navigation is a trusted touchscreen action", navigation.getBoolean("trusted"))
            compose.waitUntil(30_000) {
                val state = hostState()
                state.optString("page") == "MEMO" && state.optBoolean("hostDataReady") &&
                    state.optJSONArray("memoEntries")?.length() == 250 &&
                    state.optInt("memoRemainingCount") == 1 && state.optBoolean("memoTruncated")
            }
            return view
        }

        try {
            repeat(250) { index ->
                val outcome = store.createFormalEvent(
                    DraftForm(
                        kind = TemporalKind.MEMO,
                        title = "F04 Memo ${index.toString().padStart(3, '0')}",
                        startText = dateText,
                        zoneId = zone.id,
                        trackingEnabled = true
                    ),
                    "f04-clear-memo-page-$index"
                )
                assertTrue("dated Memo fixture $index must be committed", outcome is PowerStore.CommitOutcome.Committed)
            }
            val undated = store.createFormalEvent(
                DraftForm(
                    kind = TemporalKind.MEMO,
                    title = "F04 No-date Memo 251",
                    zoneId = zone.id,
                    trackingEnabled = true
                ),
                "f04-clear-memo-page-no-date"
            )
            assertTrue("undated Memo fixture must be committed", undated is PowerStore.CommitOutcome.Committed)
            assertEquals(251, store.all().size)
            assertEquals(251L, store.commitCount())
            assertEquals(252L, store.nextSequencePreview())

            onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
            var web = launchClearMemo()

            val initialEntries = queryMemoEntriesForTest(
                store, System.currentTimeMillis(), includeCompleted = true
            )
            assertEquals(251, initialEntries.size)
            assertEquals("single", initialEntries.last().occurrenceKey)
            assertEquals(null, initialEntries.last().startMillis)
            val initialStateRows = hostState().getJSONArray("memoEntries")
            assertEquals(initialEntries.take(250).map {
                "${it.rowKey}:${it.revision}:${it.completed}"
            }, memoSignatures(initialStateRows))
            assertEquals(250, initialStateRows.length())
            assertEquals("250", evaluate(web, "document.querySelectorAll('#memoList .memo-row').length"))
            assertEquals("true", evaluate(web,
                "!document.querySelector('#memoLimit').hidden && " +
                    "document.querySelector('#memoLimit').textContent.includes('还有 1 项')"))

            val beforeTitleOpen = requireNotNull(store.get(firstEventSeq))
            val commitsBeforeTitleOpen = store.commitCount()
            val openTitle = tapWebSelector(web,
                "#memoList .memo-title[data-event-seq='$firstEventSeq']")
            assertTrue("Memo title must open through a trusted touch", openTitle.getBoolean("trusted"))
            compose.waitUntil(15_000) {
                evaluate(web, "!document.querySelector('#skinSheet').hidden && " +
                    "document.querySelector('#sheetFields [data-field=title]')?.value==='F04 Memo 000'") == "true"
            }
            assertEquals("title-open does not complete or mutate a Memo", beforeTitleOpen,
                store.get(firstEventSeq))
            assertEquals(commitsBeforeTitleOpen, store.commitCount())
            val closeTouch = tapWebSelector(web, "#sheetClose")
            assertTrue("Memo detail closes through a trusted touch", closeTouch.getBoolean("trusted"))
            compose.waitUntil(10_000) {
                evaluate(web, "document.querySelector('#skinSheet').hidden") == "true"
            }

            val completionTouch = tapWebSelector(web,
                "#memoList [data-action='toggleMemo'][data-event-seq='$firstEventSeq'][data-occurrence-key='single']")
            assertTrue("Memo completion uses a trusted touch", completionTouch.getBoolean("trusted"))
            compose.waitUntil(30_000) {
                store.get(firstEventSeq)?.let { it.revision == 2L && "single" in it.completedKeys } == true
            }
            compose.waitUntil(15_000) {
                evaluate(web, "document.querySelectorAll('#memoList .memo-row').length") == "249"
            }
            val doneFilter = tapWebSelector(web, ".tabs [data-filter='done']")
            assertTrue("Done filter uses a trusted touch", doneFilter.getBoolean("trusted"))
            compose.waitUntil(15_000) {
                evaluate(web, "document.querySelectorAll('#memoList .memo-row').length===1 && " +
                    "document.querySelector('#memoList .memo-title')?.textContent==='F04 Memo 000'") == "true"
            }

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            storeOpen = false
            store = PowerStore(context, databaseName)
            storeOpen = true
            val reopenedRecord = requireNotNull(store.get(firstEventSeq))
            assertEquals(2L, reopenedRecord.revision)
            assertEquals(setOf("single"), reopenedRecord.completedKeys)
            assertEquals(251, queryMemoEntriesForTest(
                store, System.currentTimeMillis(), includeCompleted = true
            ).size)

            web = launchClearMemo()
            val reopenedDoneFilter = tapWebSelector(web, ".tabs [data-filter='done']")
            assertTrue("Done filter after reopening the database uses a trusted touch",
                reopenedDoneFilter.getBoolean("trusted"))
            compose.waitUntil(15_000) {
                evaluate(web, "document.querySelectorAll('#memoList .memo-row').length===1 && " +
                    "document.querySelector('#memoList .memo-title')?.textContent==='F04 Memo 000'") == "true"
            }
            val commitsBeforeReopenedTitle = store.commitCount()
            val completedBeforeReopenedTitle = requireNotNull(store.get(firstEventSeq))
            val reopenedTitle = tapWebSelector(web,
                "#memoList .memo-title[data-event-seq='$firstEventSeq']")
            assertTrue("completed Memo title opens through a trusted touch", reopenedTitle.getBoolean("trusted"))
            compose.waitUntil(15_000) {
                evaluate(web, "!document.querySelector('#skinSheet').hidden && " +
                    "document.querySelector('#sheetFields [data-field=title]')?.value==='F04 Memo 000'") == "true"
            }
            assertEquals(completedBeforeReopenedTitle, store.get(firstEventSeq))
            assertEquals(commitsBeforeReopenedTitle, store.commitCount())
            val reopenedClose = tapWebSelector(web, "#sheetClose")
            assertTrue(reopenedClose.getBoolean("trusted"))
            compose.waitUntil(10_000) {
                evaluate(web, "document.querySelector('#skinSheet').hidden") == "true"
            }

            val undoTouch = tapWebSelector(web,
                "#memoList [data-action='toggleMemo'][data-event-seq='$firstEventSeq'][data-occurrence-key='single']")
            assertTrue("Memo undo uses a trusted touch", undoTouch.getBoolean("trusted"))
            compose.waitUntil(30_000) {
                store.get(firstEventSeq)?.let { it.revision == 3L && it.completedKeys.isEmpty() } == true
            }
            val openFilter = tapWebSelector(web, ".tabs [data-filter='open']")
            assertTrue("Open filter uses a trusted touch", openFilter.getBoolean("trusted"))
            compose.waitUntil(15_000) {
                evaluate(web, "document.querySelectorAll('#memoList .memo-row').length===250 && " +
                    "!document.querySelector('#memoLimit').hidden") == "true"
            }
            evaluate(web, "document.querySelector('#memoLimit').scrollIntoView({block:'center'}); true")
            val loadMoreTouch = tapWebSelector(web, "#memoLimit")
            assertTrue("Load more uses a trusted touch", loadMoreTouch.getBoolean("trusted"))
            compose.waitUntil(30_000) {
                val entries = hostState().optJSONArray("memoEntries")
                entries?.length() == 251 &&
                    hostState().optInt("memoRemainingCount") == 0 &&
                    !hostState().optBoolean("memoTruncated")
            }

            val finalEntries = queryMemoEntriesForTest(
                store, System.currentTimeMillis(), includeCompleted = true
            )
            assertEquals(251, finalEntries.size)
            assertTrue("undo persists as an open occurrence", finalEntries.none { it.completed })
            assertEquals("No-date entry must sort after dated page entries", "F04 No-date Memo 251",
                finalEntries.last().title)
            val finalHostState = hostState()
            assertEquals(finalEntries.map { "${it.rowKey}:${it.revision}:${it.completed}" },
                memoSignatures(finalHostState.getJSONArray("memoEntries")))
            assertEquals("251", evaluate(web, "document.querySelectorAll('#memoList .memo-row').length"))
            assertEquals("true", evaluate(web, "document.querySelector('#memoList .memo-row:last-child .memo-title')" +
                "?.textContent==='F04 No-date Memo 251' && document.querySelector('#memoLimit').hidden"))
            assertEquals(253L, store.commitCount())
            assertEquals(251, store.identityCount())
            assertEquals(252L, store.nextSequencePreview())
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            println("F04_MEMO_PAGING apiRows=${finalEntries.size} hostRows=" +
                "${finalHostState.getJSONArray("memoEntries").length()} domRows=251 " +
                "openDone=true titleZeroWrite=true reopenedCompletionRevision=2 undoRevision=3 " +
                "noDateRow=${finalEntries.last().rowKey} commits=${store.commitCount()}")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            if (storeOpen) store.close()
            context.deleteDatabase(databaseName)
            skinManager.select(originalSkin)
            val restoreOnboarding = onboarding.edit()
            if (hadAskedForCalendarImport) restoreOnboarding.putBoolean(
                "system-calendar-import-asked", askedForCalendarImport
            ) else restoreOnboarding.remove("system-calendar-import-asked")
            restoreOnboarding.commit()
        }
    }
}
