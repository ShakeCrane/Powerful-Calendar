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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class WebSkinRecurrenceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, condition) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = runCatching {
            evaluate(web, "JSON.stringify({subtitle:document.querySelector('#sheetSubtitle')?.textContent, " +
                "status:document.querySelector('#sheetStatus')?.textContent, " +
                "title:document.querySelector('#sheetFields [data-field=title]')?.value, " +
                "cards:Array.from(document.querySelectorAll('#sheetFields .sheet-cards [data-do=select]'))" +
                ".map(node=>node.textContent), saveDisabled:document.querySelector('#sheetSave')?.disabled, " +
                "hidden:document.querySelector('#skinSheet')?.hidden})")
        }.getOrDefault("unavailable")
        throw AssertionError("WebView condition timed out: $condition; state=$diagnostic")
    }

    @Test fun actualWebViewMonthOccurrenceOpenPreservesOriginalSlotForSingleEdit() {
        val context: Context = compose.activity.applicationContext
        val database = "skin-rec-${UUID.randomUUID()}.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val first = today.minusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val card = store.newDraft(DraftForm(kind = TemporalKind.ALARM, title = "重复测试",
            startText = DraftForm.formatInstant(first, zone), zoneId = zone.id,
            reminderMinutes = -1, recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 4)))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(card.eventSeq))
        val key = today.toString() + "T09:00"
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()

        try {
            compose.runOnUiThread {
                SkinPackageManager(context).select("powercal-clear-default")
                compose.activity.setContent { PowerCalTheme { PowerCalApp(store) } }
            }
            var web: WebView? = null
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    web = findWebView(compose.activity.findViewById(android.R.id.content))
                }
                web != null
            }
            assertNotNull(web)
            val view = requireNotNull(web)
            val monthTab = "document.querySelector(\".clear-tabbar [data-action='month']\")"
            awaitJs(view, "!!$monthTab")
            evaluate(view, "$monthTab.click(); true")
            awaitJs(view, "!document.querySelector('#monthPage').hidden")
            evaluate(view, "document.querySelector(\".month-cell[data-date='$today']\").click(); true")
            awaitJs(view, "document.querySelector('#monthAgenda h2')?.textContent==='$today'")
            val agendaEvent = "document.querySelector(\"#monthAgenda .agenda-event" +
                "[data-event-seq='${card.eventSeq}'][data-original-occurrence-key='$key']\")"
            awaitJs(view, "!!$agendaEvent")
            evaluate(view, "$agendaEvent.click(); true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden")
            var openedEditor: org.json.JSONObject? = null
            compose.runOnIdle {
                openedEditor = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("editor")
            }
            val editorBeforeScope = requireNotNull(openedEditor)
            assertEquals(card.eventSeq, editorBeforeScope.getLong("eventSeq"))
            assertTrue("Month open must carry a valid original occurrence key",
                editorBeforeScope.getBoolean("canEditSelectedOccurrence"))
            assertEquals("true", evaluate(view, "document.querySelector('#sheetSave').disabled"))
            assertEquals("true", evaluate(view, "!document.querySelector('#sheetFields [data-field=title]')"))
            evaluate(view, "document.querySelector('[data-scope=SINGLE_OCCURRENCE]').click(); true")
            awaitJs(view, "document.querySelector('[data-scope=SINGLE_OCCURRENCE]').getAttribute('aria-pressed')==='true'")
            var selectedEditor: org.json.JSONObject? = null
            compose.runOnIdle {
                selectedEditor = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("editor")
            }
            val selectedForm = requireNotNull(selectedEditor).getJSONObject("form")
            assertEquals("SINGLE_OCCURRENCE", requireNotNull(selectedEditor)
                .getString("recurrenceScope"))
            assertEquals(DraftForm.formatInstant(today.atTime(9, 0).atZone(zone)
                .toInstant().toEpochMilli(), zone), selectedForm.getString("startText"))
            assertEquals("true", evaluate(view, "!document.querySelector('#sheetFields [data-field=notes]')"))
            evaluate(view, "const input=document.querySelector('#sheetFields [data-field=title]'); input.value='仅本次 WebView'; input.dispatchEvent(new Event('change',{bubbles:true})); true")
            awaitJs(view, "!!document.querySelector('[data-do=discardChanges]')")
            evaluate(view, "document.querySelector('#sheetSave').click(); true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(card.eventSeq))
            compose.onNodeWithText("取消").performClick()
            awaitJs(view, "document.querySelector('#sheetStatus').textContent.includes('已取消保存')")
            assertEquals(original, store.get(card.eventSeq))
            evaluate(view, "document.querySelector('#sheetSave').click(); true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(card.eventSeq))
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                store.get(card.eventSeq)?.recurrence?.overrides?.get(key)?.title == "仅本次 WebView"
            }
            val saved = requireNotNull(store.get(card.eventSeq))
            assertEquals(original.revision + 1, saved.revision)
            assertEquals(original.title, saved.title)
            assertEquals(1, saved.recurrence.overrides.size)
            assertEquals(1, store.all().size)
            // The SQLite write can finish before the Host publishes the result;
            // the sheet intentionally ignores another action while it is busy.
            awaitJs(view, "document.querySelector('#sheetStatus').textContent.includes('事件已保存')")
            evaluate(view, "document.querySelector('#sheetClose').click(); true")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
        }
    }

    @Test fun webFormalEditAndNewPublishWaitForNativeHostApproval() {
        val context: Context = compose.activity.applicationContext
        val database = "skin-write-${UUID.randomUUID()}.db"
        val store = PowerStore(context, database)
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val card = store.newDraft(DraftForm(kind = TemporalKind.ALARM, title = "宿主审查前",
            startText = DraftForm.formatInstant(start, zone), zoneId = zone.id,
            reminderMinutes = -1))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(card.eventSeq))
        val commitsBefore = store.commitCount()
        val identitiesBefore = store.identityCount()
        val draftsBefore = store.draftCount()
        val pendingBefore = store.pendingCount()
        context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()

        try {
            compose.runOnUiThread {
                SkinPackageManager(context).select("powercal-clear-default")
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
            val event = "document.querySelector('[data-event-seq=\\\"${card.eventSeq}\\\"]')"
            awaitJs(view, "!!$event")
            evaluate(view, "$event.click(); true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden")
            evaluate(view, "(()=>{const input=document.querySelector('#sheetFields [data-field=title]'); input.value='宿主确认后的编辑'; input.dispatchEvent(new Event('change',{bubbles:true})); return true})()")
            awaitJs(view, "!!document.querySelector('[data-do=discardChanges]')")
            evaluate(view, "document.querySelector('#sheetSave').click(); true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(card.eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                store.get(card.eventSeq)?.title == "宿主确认后的编辑"
            }
            assertEquals(original.revision + 1, store.get(card.eventSeq)!!.revision)
            awaitJs(view, "document.querySelector('#sheetStatus').textContent.includes('事件已保存')")
            val commitsAfterFormalEdit = store.commitCount()
            evaluate(view, "document.querySelector('#sheetClose').click(); true")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")

            evaluate(view, "document.querySelector('[data-action=createDraft]').click(); true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && !!document.querySelector('#sheetFields [data-field=title]')")
            evaluate(view, "(()=>{const input=document.querySelector('#sheetFields [data-field=title]'); input.value='皮肤新建待发布'; input.dispatchEvent(new Event('change',{bubbles:true})); return true})()")
            awaitJs(view, "document.querySelector('#sheetSubtitle').textContent.includes('尚未暂存')")
            evaluate(view, "document.querySelector('#sheetSave').click(); true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, store.all().size)
            assertEquals(commitsAfterFormalEdit, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(draftsBefore, store.draftCount())
            assertEquals(pendingBefore, store.pendingCount())
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                store.all().any { it.title == "皮肤新建待发布" }
            }
            assertEquals(2, store.all().size)
            assertEquals(commitsAfterFormalEdit + 1, store.commitCount())
            assertEquals(identitiesBefore + 1, store.identityCount())
            assertEquals(draftsBefore, store.draftCount())
            assertEquals(pendingBefore, store.pendingCount())
            awaitJs(view, "document.querySelector('#sheetStatus').textContent.includes('草稿已发布')")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(database)
        }
    }
}
