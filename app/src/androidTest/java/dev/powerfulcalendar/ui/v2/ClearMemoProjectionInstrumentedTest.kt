package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.os.SystemClock
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
        assertEquals(3, TodoProjection.entries(store.all(), System.currentTimeMillis()).size)

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
}
