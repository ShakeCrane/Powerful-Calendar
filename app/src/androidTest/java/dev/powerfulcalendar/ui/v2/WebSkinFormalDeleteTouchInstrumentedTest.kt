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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftStatus
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebSkinFormalDeleteTouchInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private fun alias(name: String) = "$namespace-$name"
        private fun databasePath(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = File(storage.cacheDir, namespace).apply { mkdirs() }
        override fun getCacheDir(): File = File(getFilesDir(), "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(getFilesDir(), "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(getFilesDir(), "dirs/$name").apply { mkdirs() }
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
        val finished = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) {
                value.set(it)
                finished.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out", finished.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONObject(JSONArray("[$encoded]").getString(0))
    }

    private fun awaitJs(web: WebView, condition: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, condition) == "true") return
            Thread.sleep(100)
        }
        val state = runCatching {
            evaluate(web, "JSON.stringify({sheetHidden:document.querySelector('#skinSheet')?.hidden," +
                "actions:Array.from(document.querySelectorAll('#sheetActions [data-do]')).map(x=>x.dataset.do)})")
        }.getOrDefault("unavailable")
        throw AssertionError("Timed out waiting for $condition; page=$state")
    }

    private fun tapElementCenter(web: WebView, selector: String): JSONObject {
        val selectorJson = JSONObject.quote(selector)
        val target = evaluateObject(web, """
            (()=>{
              const element=document.querySelector($selectorJson);
              if(!element)return {found:false};
              const rect=element.getBoundingClientRect();
              const x=rect.left+rect.width/2,y=rect.top+rect.height/2;
              const hit=document.elementFromPoint(x,y);
              window.__pcDeleteTouch=null;
              element.addEventListener("click",event=>{
                window.__pcDeleteTouch={trusted:event.isTrusted,action:element.dataset.do||element.dataset.action||""};
              },{once:true,capture:true});
              return {found:true,x,y,width:innerWidth,height:innerHeight,
                rect:{left:rect.left,top:rect.top,right:rect.right,bottom:rect.bottom},
                hitTarget:hit===element||hit?.closest("button")===element,
                disabled:element.disabled===true};
            })()
        """.trimIndent())
        assertTrue("Touch target missing: $selector", target.getBoolean("found"))
        val rect = target.getJSONObject("rect")
        assertTrue("Touch target is not visible in WebView: $target",
            rect.getDouble("left") >= 0 && rect.getDouble("top") >= 0 &&
                rect.getDouble("right") <= target.getDouble("width") &&
                rect.getDouble("bottom") <= target.getDouble("height"))
        assertTrue("Touch center is covered by another element: $target", target.getBoolean("hitTarget"))
        assertTrue("Touch target is disabled: $target", !target.getBoolean("disabled"))

        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (width, height) = requireNotNull(dimensions.get())
        val x = location[0] + (target.getDouble("x") * width / target.getDouble("width")).toFloat()
        val y = location[1] + (target.getDouble("y") * height / target.getDouble("height")).toFloat()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        val trace = evaluateObject(web, "window.__pcDeleteTouch||{}")
        assertTrue("Android touchscreen must dispatch a trusted WebView click: target=$target, " +
            "screen=($x,$y), trace=$trace",
            trace.optBoolean("trusted"))
        if (selector.contains("deleteEvent")) {
            assertEquals("Touch must invoke Clear's formal delete action", "deleteEvent",
                trace.optString("action"))
        }
        return JSONObject().put("target", selector).put("cssRect", rect)
            .put("trustedClick", trace).put("screenX", x).put("screenY", y)
    }

    @Test
    fun touchscreenDeleteWaitsForHostApprovalAndCancelPreservesSeries() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-web-delete-touch-${UUID.randomUUID()}"
        val isolated = IsolatedContext(compose.activity, storage, namespace)
        val databaseName = "formal-delete.db"
        val store = PowerStore(isolated, databaseName)
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).withSecond(0).withNano(0).toInstant().toEpochMilli()
        val draft = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "WEB_DELETE_TOUCH_FIXTURE",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            reminderMinutes = -1,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        val eventSeq = draft.eventSeq
        val original = requireNotNull(store.get(eventSeq))
        val commitsBefore = store.commitCount()
        val identitiesBefore = store.identityCount()

        try {
            SkinPackageManager(isolated).select("powercal-clear-default")
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
            val eventSelector = "[data-event-seq=\"$eventSeq\"]"
            awaitJs(view, "!!document.querySelector(${JSONObject.quote(eventSelector)})")
            val openTrace = tapElementCenter(view, eventSelector)
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetTitle').textContent==='编辑事件' && " +
                "!!document.querySelector('#sheetActions [data-do=deleteEvent]')")
            val deleteSelector = "#sheetActions [data-do=deleteEvent]"
            awaitJs(view, "(()=>{const e=document.querySelector(${
                JSONObject.quote(deleteSelector)});if(!e)return false;" +
                "e.scrollIntoView({block:'center',behavior:'instant'});" +
                "const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight})()")

            val cancelTrace = tapElementCenter(view, deleteSelector)
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Delete confirmation must not write before approval", original, store.get(eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(0, store.pendingCount())
            compose.onNodeWithText("取消").performClick()
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
            assertEquals("Cancel must preserve the formal series", original, store.get(eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isEmpty()
            }
            compose.waitForIdle()
            SystemClock.sleep(350)

            val approveTrace = tapElementCenter(view, deleteSelector)
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            compose.onNodeWithText("确认删除").performClick()
            compose.waitUntil(30_000) { store.get(eventSeq) == null }
            awaitJs(view, "document.querySelector('#skinSheet').hidden")

            assertTrue(store.all().isEmpty())
            assertEquals(commitsBefore + 1L, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(0, store.pendingCount())
            println("SEP07_TOUCH_DELETE_TRACE=" + JSONObject().put("open", openTrace)
                .put("cancel", cancelTrace).put("approve", approveTrace))
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
    fun staleDeleteApprovalPreservesConcurrentVersionAndRefreshesClearTimeline() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-web-delete-conflict-${UUID.randomUUID()}"
        val isolated = IsolatedContext(compose.activity, storage, namespace)
        val databaseName = "formal-delete-conflict.db"
        val store = PowerStore(isolated, databaseName)
        val concurrentStore = PowerStore(isolated, databaseName)
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).withSecond(0).withNano(0).toInstant().toEpochMilli()
        val initial = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "SEP07_CONFLICT_ORIGINAL",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            reminderMinutes = -1
        ))
        assertTrue(store.commit(initial.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(initial.eventSeq))
        val commitsBefore = store.commitCount()
        val identitiesBefore = store.identityCount()

        try {
            SkinPackageManager(isolated).select("powercal-clear-default")
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
            val eventSelector = "[data-event-seq=\"${original.eventSeq}\"]"
            val selectorJson = JSONObject.quote(eventSelector)
            awaitJs(view, "!!document.querySelector($selectorJson)")
            evaluate(view, "document.querySelector($selectorJson).click();true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetActions [data-do=deleteEvent]')")
            evaluate(view, "document.querySelector('#sheetActions [data-do=deleteEvent]').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(original.eventSeq))
            assertEquals(commitsBefore, store.commitCount())

            val newerTitle = "SEP07_CONFLICT_NEWER_VERSION"
            val newerDraft = concurrentStore.newDraft(
                DraftForm.from(original).copy(title = newerTitle),
                eventSeq = original.eventSeq,
                operation = DraftOperation.REPLACE,
                expectedRevision = original.revision
            )
            assertTrue(
                "the concurrent writer must commit against the exact current revision",
                concurrentStore.commit(
                    newerDraft.draftId,
                    expectedCurrentRevision = original.revision
                ) is PowerStore.CommitOutcome.Committed
            )
            val latest = requireNotNull(store.get(original.eventSeq))
            assertEquals(original.revision + 1, latest.revision)
            assertEquals(newerTitle, latest.title)

            compose.onNodeWithText("确认删除").performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isEmpty()
            }
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetStatus').textContent.includes('版本冲突')")

            assertEquals("a stale Host approval must preserve the concurrent formal version",
                latest, store.get(original.eventSeq))
            assertEquals("only the concurrent update may add a commit",
                commitsBefore + 1, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertTrue("the event must remain active", !store.isRetired(original.eventSeq))
            assertEquals(0, store.pendingCount())

            evaluate(view, "document.querySelector('#sheetClose').click();true")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")
            awaitJs(view, "document.querySelector($selectorJson)?.dataset.eventTitle===" +
                JSONObject.quote(newerTitle))
            println("SEP07_STALE_DELETE_CONFLICT=seq=${original.eventSeq}; " +
                "revision=${latest.revision}; title=${latest.title}; " +
                "commits=${store.commitCount()}; retired=${store.isRetired(original.eventSeq)}")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            concurrentStore.close()
            store.close()
            isolated.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun clearRestoreOfDeletedFormalEventWaitsForHostApprovalAndReusesIdentity() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-web-delete-restore-${UUID.randomUUID()}"
        val isolated = IsolatedContext(compose.activity, storage, namespace)
        val databaseName = "formal-delete-restore.db"
        val store = PowerStore(isolated, databaseName)
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).withSecond(0).withNano(0).toInstant().toEpochMilli()
        val initial = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "SEP07_RESTORE_FIXTURE",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            reminderMinutes = -1
        ))
        assertTrue(store.commit(initial.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(initial.eventSeq))
        val identitiesBefore = store.identityCount()
        val sequenceBefore = store.nextSequencePreview()
        val commitsBeforeDelete = store.commitCount()

        try {
            SkinPackageManager(isolated).select("powercal-clear-default")
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
            val selector = "[data-event-seq=\"${original.eventSeq}\"]"
            val selectorJson = JSONObject.quote(selector)
            awaitJs(view, "!!document.querySelector($selectorJson)")
            evaluate(view, "document.querySelector($selectorJson).click();true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetActions [data-do=deleteEvent]')")
            evaluate(view, "document.querySelector('#sheetActions [data-do=deleteEvent]').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认删除正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认删除").performClick()
            compose.waitUntil(30_000) { store.get(original.eventSeq) == null }
            assertTrue("approved deletion must leave a restorable tombstone",
                store.isRetired(original.eventSeq))
            val deletedTombstone = requireNotNull(store.getIncludingRetired(original.eventSeq))
            assertEquals(original.eventSeq, deletedTombstone.eventSeq)
            assertEquals(original.revision + 1, deletedTombstone.revision)
            assertEquals(original.title, deletedTombstone.title)
            val commitsAfterDelete = store.commitCount()
            assertEquals(commitsBeforeDelete + 1, commitsAfterDelete)
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())

            evaluate(view, "document.querySelector('[data-action=\\\"openDeck\\\"]').click();true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-do=beginRestore]')")
            val restoreButton = "#sheetFields [data-do=beginRestore]"
            assertEquals("true", evaluate(view,
                "document.querySelector('$restoreButton')?.dataset.eventSeq==='${original.eventSeq}'"))
            evaluate(view, "document.querySelector('$restoreButton').click();true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                "${JSONObject.quote(original.title)}")
            val restoreDraft = store.pendingDrafts().single()
            assertEquals(DraftOperation.REPLACE, restoreDraft.operation)
            assertEquals(original.eventSeq, restoreDraft.eventSeq)
            assertEquals(deletedTombstone, store.getIncludingRetired(original.eventSeq))
            assertTrue(store.isRetired(original.eventSeq))
            assertEquals(commitsAfterDelete, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())

            evaluate(view, "document.querySelector('#sheetSave').click();true")
            val requestRestore = "#sheetActions [data-do=requestRestoreReview]"
            awaitJs(view, "!!document.querySelector('$requestRestore')")
            evaluate(view, "document.querySelector('$requestRestore').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认恢复同一事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("restore request must not reactivate the tombstone before approval",
                null, store.get(original.eventSeq))
            assertTrue(store.isRetired(original.eventSeq))
            assertEquals(commitsAfterDelete, store.commitCount())
            assertEquals(DraftOperation.REPLACE, store.draft(restoreDraft.draftId)?.operation)
            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("确认恢复同一事件").fetchSemanticsNodes().isEmpty()
            }
            awaitJs(view, "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")
            assertEquals(null, store.get(original.eventSeq))
            assertTrue(store.isRetired(original.eventSeq))
            assertEquals(commitsAfterDelete, store.commitCount())
            assertEquals(DraftOperation.REPLACE, store.draft(restoreDraft.draftId)?.operation)
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())

            evaluate(view, "document.querySelector('$requestRestore').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认恢复同一事件").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                store.get(original.eventSeq)?.revision == original.revision + 2
            }
            val restored = requireNotNull(store.get(original.eventSeq))
            assertEquals(original.eventSeq, restored.eventSeq)
            assertEquals(original.revision + 2, restored.revision)
            assertEquals(original.title, restored.title)
            assertEquals(commitsAfterDelete + 1, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())
            assertEquals(0, store.pendingCount())
            assertTrue(!store.isRetired(original.eventSeq))
            assertEquals(DraftStatus.COMMITTED, store.draft(restoreDraft.draftId)?.status)

            println("SEP07_CLEAR_RESTORE=seq=${restored.eventSeq}; " +
                "revision=${restored.revision}; commits=${store.commitCount()}; " +
                "identities=${store.identityCount()}; sequence=${store.nextSequencePreview()}; " +
                "cancel-preserved-tombstone=true")
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
}
