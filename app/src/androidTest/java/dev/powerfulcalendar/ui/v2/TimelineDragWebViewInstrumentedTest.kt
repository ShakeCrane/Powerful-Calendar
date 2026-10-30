package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TimelineDragWebViewInstrumentedTest {
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

        private fun isolatedPath(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
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
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(web: WebView, expression: String): String {
        val value = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) {
                value.set(it)
                complete.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out", complete.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun evaluateObject(web: WebView, expression: String): JSONObject {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONObject(JSONArray("[$encoded]").getString(0))
    }

    private fun evaluateArray(web: WebView, expression: String): JSONArray {
        val encoded = evaluate(web, "JSON.stringify($expression)")
        return JSONArray(JSONArray("[$encoded]").getString(0))
    }

    private fun awaitJs(web: WebView, expression: String, timeoutMs: Long = 30_000L) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = runCatching {
            evaluate(web, "JSON.stringify({sheetHidden:document.querySelector('#skinSheet')?.hidden," +
                "moveStatus:document.querySelector('#timelineMoveStatus')?.textContent||''," +
                "range:document.querySelector('#timeRangeLabel')?.textContent||''," +
                "pageHidden:document.querySelector('#timelinePage')?.hidden," +
                "surface:document.querySelector('#timelineSurface')?.getBoundingClientRect().toJSON?.()," +
                "events:Array.from(document.querySelectorAll('.tEvent')).map(b=>({seq:b.dataset.eventSeq," +
                "title:b.dataset.eventTitle,start:b.dataset.startMillis,rect:b.getBoundingClientRect().toJSON()}))," +
                "trace:window.__s0Tl04Touches||[],posts:window.__s0Tl04Posts||[]})")
        }.getOrDefault("unavailable")
        val host = runCatching { hostState().toString() }.getOrDefault("unavailable")
        throw AssertionError("WebView condition timed out: $expression; state=$diagnostic; host=$host")
    }

    private fun hostState(): JSONObject {
        val value = AtomicReference<String>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            value.set(WebSkinBridgeRuntime.state().toString())
            complete.countDown()
        }
        assertTrue("Host state snapshot timed out", complete.await(10, TimeUnit.SECONDS))
        return JSONObject(value.get())
    }

    private fun awaitHostResult(status: String, timeoutMs: Long = 10_000L): JSONObject {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var latest = hostState()
        while (SystemClock.uptimeMillis() < deadline) {
            val result = latest.optJSONObject("editing")?.optJSONObject("lastResult")
            if (result?.optString("status") == status) return latest
            Thread.sleep(100)
            latest = hostState()
        }
        return latest
    }

    private fun awaitHostResultAfter(requestId: String, status: String, timeoutMs: Long = 10_000L): JSONObject {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var latest = hostState()
        while (SystemClock.uptimeMillis() < deadline) {
            val result = latest.optJSONObject("editing")?.optJSONObject("lastResult")
            if (result?.optString("requestId") != requestId && result?.optString("status") == status) {
                return latest
            }
            Thread.sleep(100)
            latest = hostState()
        }
        return latest
    }

    private fun eventProbe(web: WebView, eventSeq: Long): JSONObject {
        val selector = JSONObject.quote(".tEvent[data-event-seq='$eventSeq']")
        return evaluateObject(web, """
            (()=>{
              const button=document.querySelector($selector);
              if(!button)return {found:false,eventSeq:$eventSeq};
              const rect=button.getBoundingClientRect();
              const surface=document.querySelector('#timelineSurface').getBoundingClientRect();
              const x=rect.left+rect.width/2,y=rect.top+rect.height/2;
              const hit=document.elementFromPoint(x,y);
              return {found:true,eventSeq:$eventSeq,title:button.dataset.eventTitle||'',
                revision:Number(button.dataset.revision),startMillis:Number(button.dataset.startMillis),
                x,y,cssWidth:innerWidth,cssHeight:innerHeight,surfaceHeight:surface.height,
                rect:{left:rect.left,top:rect.top,right:rect.right,bottom:rect.bottom},
                hitTag:hit?.tagName||'',hitClass:hit?.className||'',
                hitButton:hit?.closest('.tEvent')===button};
            })()
        """.trimIndent())
    }

    private data class ScreenPoint(val x: Float, val y: Float, val cssPerPixel: Float)

    private fun screenPoint(web: WebView, probe: JSONObject): ScreenPoint {
        val location = IntArray(2)
        val dimensions = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread {
            web.getLocationOnScreen(location)
            dimensions.set(web.width to web.height)
        }
        val (widthPx, heightPx) = requireNotNull(dimensions.get())
        val cssWidth = probe.getDouble("cssWidth")
        val cssHeight = probe.getDouble("cssHeight")
        val scaleX = widthPx / cssWidth
        val scaleY = heightPx / cssHeight
        assertTrue("WebView coordinate scale should be finite", scaleX.isFinite() && scaleY.isFinite())
        assertEquals("Touch map should preserve the WebView CSS aspect ratio", scaleX, scaleY, 0.02)
        return ScreenPoint(
            location[0] + (probe.getDouble("x") * scaleX).toFloat(),
            location[1] + (probe.getDouble("y") * scaleY).toFloat(),
            scaleY.toFloat()
        )
    }

    private fun sendTouch(
        web: WebView,
        probe: JSONObject,
        holdMillis: Long,
        deltaCssY: Double = 0.0
    ): JSONObject {
        assertTrue("Event button was not rendered: $probe", probe.getBoolean("found"))
        assertTrue("Touch point must hit the formal event button: $probe", probe.getBoolean("hitButton"))
        val point = screenPoint(web, probe)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, eventTime: Long, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, downTime, point.x, point.y)
        if (holdMillis > 0) SystemClock.sleep(holdMillis)
        val endY = point.y + (deltaCssY * point.cssPerPixel).toFloat()
        if (deltaCssY != 0.0) {
            val firstMoveTime = SystemClock.uptimeMillis()
            send(MotionEvent.ACTION_MOVE, firstMoveTime, point.x,
                point.y + ((deltaCssY * 0.35) * point.cssPerPixel).toFloat())
            SystemClock.sleep(40)
            send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(), point.x, endY)
        } else {
            SystemClock.sleep(70)
        }
        val upTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_UP, upTime, point.x, endY)
        return JSONObject().put("startScreenPx", JSONArray(listOf(point.x, point.y)))
            .put("endScreenPx", JSONArray(listOf(point.x, endY)))
            .put("cssDeltaY", deltaCssY).put("holdMillis", holdMillis)
            .put("eventSeq", probe.getLong("eventSeq"))
    }

    private fun saveWindowScreenshot(file: File) {
        val bitmapRef = AtomicReference<Bitmap>()
        val statusRef = AtomicReference<Int>()
        val complete = CountDownLatch(1)
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            bitmapRef.set(bitmap)
            PixelCopy.request(compose.activity.window, bitmap, { status ->
                statusRef.set(status)
                complete.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("PixelCopy timed out", complete.await(10, TimeUnit.SECONDS))
        val bitmap = requireNotNull(bitmapRef.get())
        try {
            assertEquals(PixelCopy.SUCCESS, requireNotNull(statusRef.get()))
            file.outputStream().use { output ->
                assertTrue("Screenshot encoding failed", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun touchscreenTapOpensEditorAndLongPressStagesOnlyOneReplacementDraft() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-s0-tl04-" + UUID.randomUUID()
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "timeline.db"
        val store = PowerStore(context, databaseName)
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val activityStart = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val repeatStart = date.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
        val screenshot = File(storage.cacheDir, "$namespace-staged-deck.png")
        try {
            store.readableDatabase
            fun createFormal(title: String, start: Long, recurrence: Recurrence = Recurrence.NONE): Long {
                val form = DraftForm(
                    kind = TemporalKind.ACTIVITY,
                    title = title,
                    startText = DraftForm.formatInstant(start, zone),
                    endText = DraftForm.formatInstant(start + 3_600_000L, zone),
                    zoneId = zone.id,
                    recurrence = recurrence
                )
                val outcome = store.createFormalEvent(form, "s0-tl04-" + UUID.randomUUID())
                assertTrue("Formal fixture must be committed: $title",
                    outcome is PowerStore.CommitOutcome.Committed)
                return (outcome as PowerStore.CommitOutcome.Committed).eventSeq
            }
            val activitySeq = createFormal("WEB_MOVE_ACTIVITY", activityStart)
            val recurringSeq = createFormal("WEB_REPEAT_ACTIVITY", repeatStart,
                Recurrence(freq = RecurrenceFreq.DAILY, count = 3))
            val original = requireNotNull(store.get(activitySeq))
            val recurringOriginal = requireNotNull(store.get(recurringSeq))
            val countsBefore = Triple(store.draftCount(), store.commitCount(), store.identityCount())
            val nextSequenceBefore = store.nextSequencePreview()
            val nextOrderBefore = store.nextQueueOrderPreview()
            val pendingBefore = store.pendingCount()

            context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            SkinPackageManager(context).select("powercal-clear-default")
            assertEquals("Clear fixture skin should be selected", "powercal-clear-default",
                SkinPackageManager(context).currentId())
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
            val view = requireNotNull(web)
            awaitJs(view, "!!window.PowerCalSkin && !!document.querySelector('#timelineSurface')")
            val initialHost = hostState()
            assertEquals("Timeline starts with its 24-hour camera", 24.0,
                initialHost.getJSONObject("timeline").getDouble("hours"), 0.001)
            println("S0_TL04_INITIAL_HOST=$initialHost")
            println("S0_TL04_INITIAL_WEB=" + evaluate(view,
                "JSON.stringify({range:document.querySelector('#timeRangeLabel')?.textContent||''," +
                    "events:Array.from(document.querySelectorAll('.tEvent')).map(b=>({seq:b.dataset.eventSeq," +
                    "title:b.dataset.eventTitle,start:b.dataset.startMillis}))})"))
            awaitJs(view, "!!document.querySelector('.tEvent[data-event-seq=\"$activitySeq\"]') && " +
                "!!document.querySelector('.tEvent[data-event-seq=\"$recurringSeq\"]')", 8_000L)

            evaluate(view, """
                (()=>{
                  window.__s0Tl04Touches=[];window.__s0Tl04Posts=[];window.__s0Tl04MoveCalls=[];
                  window.__s0Tl04DragRemovals=[];
                  const remove=DOMTokenList.prototype.remove;
                  DOMTokenList.prototype.remove=function(...tokens){
                    if(tokens.includes('timeline-dragging'))window.__s0Tl04DragRemovals.push({
                      target:this.value||'',stack:(new Error()).stack||''});
                    return remove.apply(this,tokens);
                  };
                  const move=window.PowerCalEdit?.moveTimelineEvent;
                  if(typeof move==='function')window.PowerCalEdit.moveTimelineEvent=async(...args)=>{
                    window.__s0Tl04MoveCalls.push({phase:'called',eventSeq:Number(args[0]?.eventSeq),
                      revision:Number(args[0]?.revision),targetMillis:Number(args[1])});
                    const result=await move(...args);
                    window.__s0Tl04MoveCalls.push({phase:'resolved',result});
                    return result;
                  };
                  const points=list=>Array.from(list||[],touch=>({id:touch.identifier,
                    x:touch.clientX,y:touch.clientY}));
                  for(const name of ['touchstart','touchmove','touchend','touchcancel'])
                    document.addEventListener(name,event=>{
                      window.__s0Tl04Touches.push({type:name,touches:points(event.touches),
                        changed:points(event.changedTouches),target:event.target?.className||event.target?.tagName||'',
                        prevented:event.defaultPrevented});
                      if(name==='touchmove'||name==='touchend'||name==='touchcancel')setTimeout(()=>{
                        const button=event.target?.closest?.('.tEvent');
                        window.__s0Tl04Touches.push({type:name+'-after',status:
                          document.querySelector('#timelineMoveStatus')?.textContent||'',
                          transform:button?.style.transform||'',dragging:button?.classList.contains('timeline-dragging')||false,
                          prevented:event.defaultPrevented,scrollTop:document.querySelector('#axisScroll')?.scrollTop||0});
                      },0);
                    },true);
                  const original=MessagePort.prototype.postMessage;
                  MessagePort.prototype.postMessage=function(message,...args){
                    window.__s0Tl04Posts.push(typeof message==='string'?message:String(message));
                    return original.call(this,message,...args);
                  };
                  return true;
                })()
            """.trimIndent())

            // A normal short touch opens the editor and leaves every persistence counter unchanged.
            val activityProbe = eventProbe(view, activitySeq)
            val normalTap = sendTouch(view, activityProbe, holdMillis = 0)
            awaitJs(view, "!document.querySelector('#skinSheet').hidden")
            val editor = hostState().getJSONObject("editing").getJSONObject("editor")
            assertTrue("Normal tap opens the formal editor", editor.getBoolean("open"))
            assertEquals(activitySeq, editor.getLong("eventSeq"))
            assertEquals("A normal tap must not create a draft or commit", countsBefore,
                Triple(store.draftCount(), store.commitCount(), store.identityCount()))
            evaluate(view, "document.querySelector('#sheetClose').click(); true")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")

            // A pre-long-press vertical swipe must cancel without opening or writing an edit.
            evaluate(view, "document.querySelector('[data-action=zoomTimeline][data-direction=in]').click(); true")
            awaitJs(view, "true")
            val zoomDeadline = SystemClock.uptimeMillis() + 8_000L
            while (hostState().getJSONObject("timeline").getDouble("hours") >= 24.0 &&
                SystemClock.uptimeMillis() < zoomDeadline) Thread.sleep(100)
            val zoomedHost = hostState()
            val zoomedTimeline = zoomedHost.getJSONObject("timeline")
            assertTrue("Timeline zoom should narrow the camera before the drag gesture",
                zoomedTimeline.getDouble("hours") < 24.0)
            val zoomedItems = zoomedTimeline.getJSONArray("items")
            assertTrue("The recurring card remains projected after zoom: $zoomedTimeline",
                (0 until zoomedItems.length()).any {
                    zoomedItems.getJSONObject(it).optLong("eventSeq") == recurringSeq
                })
            println("S0_TL04_ZOOM_HOST=" + JSONObject().put("anchorMillis", zoomedTimeline.getLong("anchorMillis"))
                .put("hours", zoomedTimeline.getDouble("hours")).put("items", zoomedItems))
            val panProbe = eventProbe(view, activitySeq)
            val panGesture = sendTouch(view, panProbe, holdMillis = 80, deltaCssY = 48.0)
            assertTrue("A pre-long-press vertical swipe must not open the editor",
                evaluate(view, "document.querySelector('#skinSheet').hidden").toBoolean())
            assertEquals("A pre-long-press swipe must not stage a draft or write a formal revision", countsBefore,
                Triple(store.draftCount(), store.commitCount(), store.identityCount()))
            println("S0_TL04_PRE_LONG_PRESS_SWIPE=" + JSONObject().put("gesture", panGesture)
                .put("editorOpen", !evaluate(view, "document.querySelector('#skinSheet').hidden").toBoolean())
                .put("countsUnchanged", countsBefore ==
                    Triple(store.draftCount(), store.commitCount(), store.identityCount())))

            // Repeating events traverse the same touch gesture but are refused by the Host.
            val previousRequestId = hostState().getJSONObject("editing").getJSONObject("lastResult")
                .optString("requestId")
            val recurringProbe = eventProbe(view, recurringSeq)
            val recurringDrag = sendTouch(view, recurringProbe, holdMillis = 700, deltaCssY = 60.0)
            var repeatedHost = awaitHostResultAfter(previousRequestId, "unsupported")
            var repeatedResult = repeatedHost.getJSONObject("editing").getJSONObject("lastResult")
            val repeatedEditing = repeatedHost.getJSONObject("editing")
            println("S0_TL04_REPEAT_DIAGNOSTIC=" + JSONObject()
                .put("hostResult", repeatedResult)
                .put("editorOpen", repeatedEditing.getJSONObject("editor").optBoolean("open"))
                .put("deckOpen", repeatedEditing.getJSONObject("deck").optBoolean("open"))
                .put("pendingCount", store.pendingCount())
                .put("counts", JSONArray(listOf(store.draftCount(), store.commitCount(), store.identityCount())))
                .put("web", evaluateObject(view, """
                    ({sheetHidden:document.querySelector('#skinSheet').hidden,
                      moveStatus:document.querySelector('#timelineMoveStatus')?.textContent||'',
                      touchEvents:window.__s0Tl04Touches||[],moveCalls:window.__s0Tl04MoveCalls||[],
                      dragRemovals:window.__s0Tl04DragRemovals||[],
                      bridgePosts:window.__s0Tl04Posts||[]})
                """.trimIndent()))
                .put("probe", recurringProbe).put("gesture", recurringDrag))
            assertEquals("unsupported", repeatedResult.getString("status"))
            assertTrue("A repeated event gesture must produce a fresh Host result",
                repeatedResult.getString("requestId") != previousRequestId)
            assertTrue("Host explains why the repeat cannot move", repeatedResult.optString("detail")
                .contains("重复事件暂不支持拖动"))
            awaitJs(view, "document.querySelector('#timelineMoveStatus').textContent.includes('重复事件暂不支持拖动')")
            assertFalse("Rejected repeat does not open the deck",
                repeatedHost.getJSONObject("editing").getJSONObject("deck").getBoolean("open"))
            assertEquals(countsBefore, Triple(store.draftCount(), store.commitCount(), store.identityCount()))
            assertEquals(recurringOriginal, store.get(recurringSeq))

            // This is a touchscreen ACTION_DOWN held beyond the 500ms threshold, then moved and lifted.
            val previousMoveRequestId = hostState().getJSONObject("editing").getJSONObject("lastResult")
                .optString("requestId")
            val moveProbe = eventProbe(view, activitySeq)
            val drag = sendTouch(view, moveProbe, holdMillis = 700, deltaCssY = 60.0)
            val stagedHost = awaitHostResultAfter(previousMoveRequestId, "staged")
            val editing = stagedHost.getJSONObject("editing")
            val result = editing.getJSONObject("lastResult")
            val deck = editing.getJSONObject("deck")
            assertEquals("staged", result.getString("status"))
            assertTrue("A successful move opens the existing draft Deck", deck.getBoolean("open"))
            assertFalse("The replacement card is persistent, not a transient new-event form",
                deck.optBoolean("transient", true))
            assertEquals(1, deck.getJSONArray("cards").length())
            assertEquals("WEB_MOVE_ACTIVITY", deck.getJSONObject("form").getString("title"))
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value==='WEB_MOVE_ACTIVITY' && " +
                "document.querySelector('#timelineMoveStatus').textContent.includes('正式事件尚未改变')")

            val timeline = stagedHost.getJSONObject("timeline")
            val hours = timeline.getDouble("hours")
            val moveCalls = evaluateArray(view, "window.__s0Tl04MoveCalls")
            val moveCommand = (0 until moveCalls.length()).map { moveCalls.getJSONObject(it) }
                .first { it.optString("phase") == "called" && it.optLong("eventSeq") == activitySeq }
            val targetMillis = moveCommand.getLong("targetMillis")
            val expectedStart = TimelineCoordinate.snap(targetMillis, hours, zone)
            val pending = store.pendingDraftsFor(activitySeq)
            println("S0_TL04_MOVE_MATH=" + JSONObject().put("cameraHours", hours)
                .put("targetMillis", targetMillis).put("expectedStart", DraftForm.formatInstant(expectedStart, zone))
                .put("actualStart", pending.singleOrNull()?.form?.startText ?: "missing")
                .put("calls", moveCalls))
            assertTrue("The long press must invoke and resolve through the Host Bridge: $moveCalls",
                moveCalls.length() >= 2 && moveCalls.getJSONObject(0).optString("phase") == "called" &&
                    moveCalls.getJSONObject(moveCalls.length() - 1).optJSONObject("result")
                        ?.optString("status") == "staged")
            assertEquals("One gesture creates exactly one replacement draft", 1, pending.size)
            val draft = pending.single()
            assertEquals(DraftOperation.REPLACE, draft.operation)
            assertEquals(original.revision, draft.baseRevision)
            assertEquals(DraftForm.formatInstant(expectedStart, zone), draft.form.startText)
            assertEquals(DraftForm.formatInstant(expectedStart + 3_600_000L, zone), draft.form.endText)
            assertEquals("The formal event remains unchanged until explicit review/publish", original,
                store.get(activitySeq))
            assertEquals("Both formal fixture rows remain present", 2, store.all().size)
            assertEquals(countsBefore.first + 1, store.draftCount())
            assertEquals(countsBefore.second, store.commitCount())
            assertEquals(countsBefore.third, store.identityCount())
            assertEquals(pendingBefore + 1, store.pendingCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())
            assertEquals(nextOrderBefore + 1, store.nextQueueOrderPreview())

            saveWindowScreenshot(screenshot)
            val bridgePosts = evaluateArray(view, "window.__s0Tl04Posts")
            val touches = evaluateArray(view, "window.__s0Tl04Touches")
            val evidence = JSONObject().put("environment", "Android 15 API 35; Clear WebView")
                .put("zoneId", zone.id).put("cameraHours", hours)
                .put("activitySeq", activitySeq).put("recurringSeq", recurringSeq)
                .put("originalStartMillis", original.startMillis)
                .put("surfaceHeightCss", moveProbe.getDouble("surfaceHeight"))
                .put("normalTap", normalTap).put("repeatDrag", recurringDrag)
                .put("repeatResult", repeatedResult).put("activityDrag", drag)
                .put("expectedMovedStartMillis", expectedStart)
                .put("stagedStartText", draft.form.startText).put("stagedEndText", draft.form.endText)
                .put("bridgePosts", bridgePosts).put("touchEvents", touches).put("moveCalls", moveCalls)
                .put("deckOpen", deck.getBoolean("open")).put("formalUnchanged", store.get(activitySeq) == original)
                .put("countsAfter", JSONArray(listOf(store.draftCount(), store.commitCount(), store.identityCount())))
                .put("screenshot", screenshot.absolutePath)
            println("S0_TL04_ANDROID_DRAG=$evidence")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-skin-registry",
                "powercal-ui", "powercal-profile", "powercal-ai", "powercal-file-ai-consent")
                .forEach { name -> storage.deleteSharedPreferences("$namespace-$name") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }
}
