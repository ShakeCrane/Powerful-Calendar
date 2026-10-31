package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STUDIO_AUTHORED_SKIN_ID = "pc-clear-studio-bridge-proof"
private const val STUDIO_AUTHORED_SKIN_ASSET = "skin-studio-authored-clear-bridge.pcskin"
private const val STUDIO_AUTHORED_SKIN_SHA256 =
    "3a754789878c3ab7042351b3af0cd58e204abad4770ba69eac25b940d156aaf7"

@RunWith(AndroidJUnit4::class)
class StudioAuthoredSkinBridgeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedStudioContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply { check(mkdirs() || isDirectory) }
        private fun alias(name: String) = "$namespace-$name"
        private fun databasePath(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
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
            web.evaluateJavascript(expression) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out", completed.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun tapBySelector(web: WebView, selector: String): JSONObject {
        val selectorLiteral = JSONObject.quote(selector)
        val encoded = evaluate(web, """(()=>{
            const target=document.querySelector($selectorLiteral);
            if(!target)return JSON.stringify({found:false});
            const rect=target.getBoundingClientRect();
            const x=rect.left+rect.width/2,y=rect.top+rect.height/2;
            const hit=document.elementFromPoint(x,y);
            window.__studioBridgeTap=null;
            target.addEventListener('click',event=>window.__studioBridgeTap={trusted:event.isTrusted},
              {once:true,capture:true});
            return JSON.stringify({found:true,x,y,cssWidth:innerWidth,cssHeight:innerHeight,
              visible:rect.width>0&&rect.height>0&&rect.left>=0&&rect.top>=0&&
                rect.right<=innerWidth&&rect.bottom<=innerHeight,
              hitTarget:hit===target||target.contains(hit),
              disabled:target.disabled===true||target.getAttribute('aria-disabled')==='true'});
        })()""")
        val target = JSONObject(JSONArray("[$encoded]").getString(0))
        assertTrue("Studio-authored Clear control is not touchable: $selector $target",
            target.optBoolean("found") && target.optBoolean("visible") &&
                target.optBoolean("hitTarget") && !target.optBoolean("disabled"))

        val location = IntArray(2)
        val size = AtomicReference<Pair<Int, Int>>()
        compose.runOnUiThread { web.getLocationOnScreen(location); size.set(web.width to web.height) }
        val (width, height) = requireNotNull(size.get())
        val scaleX = width / target.getDouble("cssWidth")
        val scaleY = height / target.getDouble("cssHeight")
        assertEquals("WebView touch mapping must preserve aspect ratio", scaleX, scaleY, 0.03)
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
        compose.waitUntil(10_000) { evaluate(web, "window.__studioBridgeTap?.trusted===true") == "true" }
        val tap = evaluate(web, "JSON.stringify(window.__studioBridgeTap)")
        return JSONObject(JSONArray("[$tap]").getString(0))
    }

    @Test
    fun studioAuthoredMonthControlUsesTheRealHostBridgeWithoutStoreWrites() {
        assumeTrue("requires Android 15/API 35 emulator", Build.VERSION.SDK_INT == 35 &&
            (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"))
        val storage = compose.activity.applicationContext
        val namespace = "pc-studio-authored-runtime-${UUID.randomUUID()}"
        val isolated = IsolatedStudioContext(compose.activity, storage, namespace)
        val databaseName = "studio-bridge.db"
        val store = PowerStore(isolated, databaseName)
        val manager = SkinPackageManager(isolated)
        val packageFile = File(isolated.cacheDir, "$STUDIO_AUTHORED_SKIN_ID.pcskin")
        var installed = false
        try {
            val packageBytes = InstrumentationRegistry.getInstrumentation().context.assets
                .open(STUDIO_AUTHORED_SKIN_ASSET).use { it.readBytes() }
            val packageSha = MessageDigest.getInstance("SHA-256").digest(packageBytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(STUDIO_AUTHORED_SKIN_SHA256, packageSha)
            assertTrue("the isolated Store must start empty", store.all().isEmpty())
            packageFile.writeBytes(packageBytes)

            val skin = manager.install(Uri.fromFile(packageFile))
            installed = true
            assertEquals(STUDIO_AUTHORED_SKIN_ID, skin.id)
            compose.runOnUiThread {
                PowerCalSkinRegistry.reset()
                manager.select(skin.id)
                isolated.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                    .edit().putBoolean("system-calendar-import-asked", true).commit()
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides isolated) {
                        PowerCalTheme { PowerCalApp(store) }
                    }
                }
            }

            val webReference = AtomicReference<WebView?>()
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    webReference.set(findWebView(compose.activity.findViewById(android.R.id.content)))
                }
                webReference.get() != null
            }
            val web = requireNotNull(webReference.get())
            compose.waitUntil(30_000) {
                evaluate(web, "!!window.PowerCalSkin&&" +
                    "!!document.querySelector('#studioAuthoredMonthAdvance')&&" +
                    "document.querySelector('#studioAuthoredMonthAdvance').textContent.trim()==='Studio 下一月'") == "true"
            }
            assertEquals("72px", evaluate(web,
                "getComputedStyle(document.querySelector('#studioAuthoredMonthAdvance')).minWidth")
                .trim('"'))
            assertEquals("Host Bridge client is present", "true",
                evaluate(web, "!!window.PowerCalSkin"))

            val monthTabTap = tapBySelector(web, ".clear-tabbar [data-action='month']")
            compose.waitUntil(10_000) { evaluate(web, "!document.querySelector('#monthPage').hidden") == "true" }
            assertTrue("month tab must use a real trusted touch", monthTabTap.optBoolean("trusted"))
            val beforeState = WebSkinBridgeRuntime.state()
            assertEquals("MONTH", beforeState.getString("page"))
            val beforeMonth = YearMonth.parse(beforeState.getJSONObject("month").getString("month"))
            val beforeTimeline = beforeState.getJSONObject("timeline")
            val anchorBefore = beforeTimeline.getLong("anchorMillis")
            val hoursBefore = beforeTimeline.getDouble("hours")
            val countsBefore = store.all().size

            val customTap = tapBySelector(web, "#studioAuthoredMonthAdvance")
            val expectedMonth = beforeMonth.plusMonths(1)
            compose.waitUntil(10_000) {
                WebSkinBridgeRuntime.state().optJSONObject("month")?.optString("month") ==
                    expectedMonth.toString()
            }
            val afterState = WebSkinBridgeRuntime.state()
            assertEquals("Month navigation stays on the Month page", "MONTH", afterState.getString("page"))
            assertEquals(expectedMonth.toString(), afterState.getJSONObject("month").getString("month"))
            assertEquals(anchorBefore, afterState.getJSONObject("timeline").getLong("anchorMillis"))
            assertEquals(hoursBefore, afterState.getJSONObject("timeline").getDouble("hours"), 0.0)
            assertEquals(countsBefore, store.all().size)
            assertTrue("the Studio-authored button must dispatch a trusted touch", customTap.optBoolean("trusted"))
            assertEquals("Studio Bridge action advanced exactly one month", true,
                expectedMonth.toString() == afterState.getJSONObject("month").getString("month"))
            println("STUDIO_RUNTIME_ACTION PASS packageSha256=$packageSha action=browseMonth " +
                "before=$beforeMonth after=$expectedMonth storeRows=${store.all().size} " +
                "trustedMonthTab=${monthTabTap.optBoolean("trusted")} " +
                "trustedCustomButton=${customTap.optBoolean("trusted")}")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            if (installed) {
                manager.select("powercal-default")
                manager.delete(STUDIO_AUTHORED_SKIN_ID)
            }
            store.close()
            isolated.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui",
                "powercal-profile").forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun studioAuthoredSkinAndNativeMemoNoDiffReopenAndCompletionUndo() {
        assumeTrue("requires Android 15/API 35 emulator", Build.VERSION.SDK_INT == 35 &&
            (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"))
        val storage = compose.activity.applicationContext
        val nativeNamespace = "pc-studio-memo-native-${UUID.randomUUID()}"
        val clearNamespace = "pc-studio-memo-clear-${UUID.randomUUID()}"
        val nativeContext = IsolatedStudioContext(compose.activity, storage, nativeNamespace)
        val clearContext = IsolatedStudioContext(compose.activity, storage, clearNamespace)
        val nativeDatabase = "studio-memo-native.db"
        val clearDatabase = "studio-memo-clear.db"
        val nativeStore = PowerStore(nativeContext, nativeDatabase)
        val clearStore = PowerStore(clearContext, clearDatabase)
        val nativeManager = SkinPackageManager(nativeContext)
        val clearManager = SkinPackageManager(clearContext)
        val packageFile = File(clearContext.cacheDir, "$STUDIO_AUTHORED_SKIN_ID.pcskin")
        val zone = ZoneId.systemDefault()
        val seed = DraftForm(
            kind = TemporalKind.MEMO,
            title = "Studio Native Memo open-close-reopen",
            startText = LocalDate.now(zone).plusDays(3).toString(),
            zoneId = zone.id
        )
        val nativeCreated = nativeStore.createFormalEvent(seed, "studio-memo-open-seed")
            as PowerStore.CommitOutcome.Committed
        val clearCreated = clearStore.createFormalEvent(seed, "studio-memo-open-seed")
            as PowerStore.CommitOutcome.Committed
        assertEquals(nativeCreated.eventSeq, clearCreated.eventSeq)
        val eventSeq = nativeCreated.eventSeq
        assertEquals("fixture must project exactly one unfinished Memo",
            1, TodoProjection.entries(nativeStore.all(), System.currentTimeMillis()).size)
        val nativeBaseline = memoStoreSnapshot(nativeStore, eventSeq)
        val clearBaseline = memoStoreSnapshot(clearStore, eventSeq)
        var installed = false
        try {
            assertEquals(DraftForm.from(requireNotNull(nativeStore.get(eventSeq))),
                DraftForm.from(requireNotNull(clearStore.get(eventSeq))))
            assertEquals(1L, nativeStore.commitCount())
            assertEquals(1L, clearStore.commitCount())

            nativeContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            PowerCalSkinRegistry.reset()
            assertTrue("Native recovery renderer selection must persist",
                nativeManager.selectNativeForEmergencyRecovery())
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides nativeContext) {
                        PowerCalTheme { PowerCalApp(nativeStore) }
                    }
                }
            }

            fun openNativeMemoEditor() {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("Memo · 未完成").fetchSemanticsNodes().isNotEmpty() ||
                        compose.onAllNodesWithContentDescription("Memo 未完成 1")
                            .fetchSemanticsNodes().isNotEmpty()
                }
                if (compose.onAllNodesWithText("Memo · 未完成").fetchSemanticsNodes().isEmpty()) {
                    compose.onNodeWithContentDescription("Memo 未完成 1")
                        .performTouchInput { click() }
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("Memo · 未完成").fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText(seed.title, substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(seed.title, substring = true).performTouchInput { click() }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("存档并退出").fetchSemanticsNodes().isNotEmpty()
                }
            }

            fun closeNativeMemoEditor() {
                compose.onNodeWithText("存档并退出").performTouchInput { click() }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("存档并退出").fetchSemanticsNodes().isEmpty()
                }
            }

            openNativeMemoEditor()
            assertEquals("Native open has no persistence side effect", nativeBaseline,
                memoStoreSnapshot(nativeStore, eventSeq))
            closeNativeMemoEditor()
            assertEquals("Native no-diff exit has no persistence side effect", nativeBaseline,
                memoStoreSnapshot(nativeStore, eventSeq))
            openNativeMemoEditor()
            assertEquals("Native reopen preserves the same untouched record", nativeBaseline,
                memoStoreSnapshot(nativeStore, eventSeq))
            closeNativeMemoEditor()
            assertEquals("Native second no-diff exit remains idempotent", nativeBaseline,
                memoStoreSnapshot(nativeStore, eventSeq))

            assertEquals("the unfinished Native Memo shows one completion control", 1,
                compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
            compose.onAllNodes(isToggleable()).onFirst().performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.get(eventSeq)?.completedKeys == setOf("single")
            }
            assertMemoCompletionState(nativeStore, eventSeq, 2L, setOf("single"), nativeBaseline[6] as Long)
            compose.onNodeWithText("查看已完成").performTouchInput { click() }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(seed.title, substring = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 1
            }
            compose.onAllNodes(isToggleable()).onFirst().performTouchInput { click() }
            compose.waitUntil(30_000) {
                nativeStore.get(eventSeq)?.completedKeys?.isEmpty() == true
            }
            assertMemoCompletionState(nativeStore, eventSeq, 3L, emptySet(), nativeBaseline[6] as Long)

            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()

            clearContext.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().putBoolean("system-calendar-import-asked", true).commit()
            val packageBytes = InstrumentationRegistry.getInstrumentation().context.assets
                .open(STUDIO_AUTHORED_SKIN_ASSET).use { it.readBytes() }
            val packageSha = MessageDigest.getInstance("SHA-256").digest(packageBytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(STUDIO_AUTHORED_SKIN_SHA256, packageSha)
            packageFile.writeBytes(packageBytes)
            val skin = clearManager.install(Uri.fromFile(packageFile))
            installed = true
            assertEquals(STUDIO_AUTHORED_SKIN_ID, skin.id)
            PowerCalSkinRegistry.reset()
            clearManager.select(skin.id)
            compose.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(LocalContext provides clearContext) {
                        PowerCalTheme { PowerCalApp(clearStore) }
                    }
                }
            }
            val webReference = AtomicReference<WebView?>()
            compose.waitUntil(30_000) {
                compose.runOnUiThread {
                    webReference.set(findWebView(compose.activity.findViewById(android.R.id.content)))
                }
                webReference.get() != null
            }
            val web = requireNotNull(webReference.get())
            compose.waitUntil(30_000) {
                evaluate(web, "!!window.PowerCalSkin") == "true"
            }
            val memoTabTouch = tapBySelector(web, ".clear-tabbar [data-action='memo']")
            compose.waitUntil(10_000) {
                evaluate(web, "!document.querySelector('#memoPage').hidden") == "true"
            }
            assertTrue("Memo navigation must be a trusted Android touchscreen tap",
                memoTabTouch.optBoolean("trusted"))
            val titleSelector = "#memoList .memo-title[data-event-seq='$eventSeq']"
            val encodedTitleSelector = JSONObject.quote(titleSelector)
            compose.waitUntil(10_000) {
                evaluate(web, "!!document.querySelector($encodedTitleSelector)") == "true"
            }
            assertEquals("Studio CSS edit must be active on the actual Memo component", "44px",
                evaluate(web, "getComputedStyle(document.querySelector($encodedTitleSelector)).minHeight")
                    .trim('"'))

            fun openClearMemoEditor(): JSONObject = tapBySelector(web, titleSelector)
            fun closeClearMemoEditor(): JSONObject = tapBySelector(web, "#sheetClose")
            fun awaitClearMemoEditor() {
                compose.waitUntil(10_000) {
                    evaluate(web, "!document.querySelector('#skinSheet').hidden && " +
                        "document.querySelector('#sheetFields [data-field=title]')?.value===" +
                        JSONObject.quote(seed.title)) == "true"
                }
            }
            fun awaitClearMemoClosed() {
                compose.waitUntil(10_000) {
                    evaluate(web, "document.querySelector('#skinSheet').hidden") == "true"
                }
            }

            val clearFirstOpen = openClearMemoEditor()
            awaitClearMemoEditor()
            assertTrue("Studio Clear Memo must open from a trusted touch",
                clearFirstOpen.optBoolean("trusted"))
            assertEquals("Studio Clear open has no persistence side effect", clearBaseline,
                memoStoreSnapshot(clearStore, eventSeq))
            val clearFirstClose = closeClearMemoEditor()
            awaitClearMemoClosed()
            assertTrue("Studio Clear no-diff exit must use a trusted touch",
                clearFirstClose.optBoolean("trusted"))
            assertEquals("Studio Clear no-diff exit has no persistence side effect", clearBaseline,
                memoStoreSnapshot(clearStore, eventSeq))

            val clearSecondOpen = openClearMemoEditor()
            awaitClearMemoEditor()
            assertTrue("Studio Clear editor must reopen from a trusted touch",
                clearSecondOpen.optBoolean("trusted"))
            assertEquals("Studio Clear reopen shows the original record without writes", clearBaseline,
                memoStoreSnapshot(clearStore, eventSeq))
            val clearSecondClose = closeClearMemoEditor()
            awaitClearMemoClosed()
            assertTrue("Studio Clear second no-diff exit must use a trusted touch",
                clearSecondClose.optBoolean("trusted"))
            assertEquals("Studio Clear second no-diff exit remains idempotent", clearBaseline,
                memoStoreSnapshot(clearStore, eventSeq))

            val toggleSelector = "#memoList [data-action='toggleMemo']" +
                "[data-event-seq='$eventSeq'][data-occurrence-key='single']"
            val encodedToggleSelector = JSONObject.quote(toggleSelector)
            val clearCompleteTouch = tapBySelector(web, toggleSelector)
            assertTrue("Studio Clear Memo completion must use a trusted touchscreen tap",
                clearCompleteTouch.optBoolean("trusted"))
            compose.waitUntil(30_000) {
                clearStore.get(eventSeq)?.completedKeys == setOf("single")
            }
            assertMemoCompletionState(clearStore, eventSeq, 2L, setOf("single"), clearBaseline[6] as Long)

            val clearDoneFilterTouch = tapBySelector(web, ".tabs [data-filter='done']")
            assertTrue("Studio Clear completed filter must use a trusted touchscreen tap",
                clearDoneFilterTouch.optBoolean("trusted"))
            compose.waitUntil(10_000) {
                evaluate(web, "!!document.querySelector($encodedTitleSelector) && " +
                    "document.querySelector($encodedTitleSelector).getAttribute('aria-label')===" +
                    JSONObject.quote("编辑事项：${seed.title}") + " && " +
                    "document.querySelector($encodedToggleSelector)?.getAttribute('aria-label')===" +
                    JSONObject.quote("恢复未完成")) == "true"
            }
            val clearReopenTouch = tapBySelector(web, toggleSelector)
            assertTrue("Studio Clear completed Memo must be reopenable with trusted touch",
                clearReopenTouch.optBoolean("trusted"))
            compose.waitUntil(30_000) {
                clearStore.get(eventSeq)?.completedKeys?.isEmpty() == true
            }
            assertMemoCompletionState(clearStore, eventSeq, 3L, emptySet(), clearBaseline[6] as Long)

            val clearOpenFilterTouch = tapBySelector(web, ".tabs [data-filter='open']")
            assertTrue("Studio Clear unfinished filter must use a trusted touchscreen tap",
                clearOpenFilterTouch.optBoolean("trusted"))
            compose.waitUntil(10_000) {
                evaluate(web, "!!document.querySelector($encodedTitleSelector)") == "true"
            }

            assertEquals("Native and Studio-authored Skin expose the same Memo business form",
                DraftForm.from(requireNotNull(nativeStore.get(eventSeq))),
                DraftForm.from(requireNotNull(clearStore.get(eventSeq))))
            println("MEMO_OPEN_REOPEN_UNDO_PARITY PASS packageSha256=$packageSha eventSeq=$eventSeq " +
                "nativeNoDiffExits=2 clearNoDiffExits=2 completionAndUndo=true commits=${clearStore.commitCount()} " +
                "identities=${clearStore.identityCount()} sequence=${clearStore.nextSequencePreview()} " +
                "customMemoMinHeight=44px trustedTouches=true")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            if (installed) {
                clearManager.select("powercal-default")
                clearManager.delete(STUDIO_AUTHORED_SKIN_ID)
            }
            nativeStore.close()
            clearStore.close()
            nativeContext.deleteDatabase(nativeDatabase)
            clearContext.deleteDatabase(clearDatabase)
            listOf(nativeNamespace, clearNamespace).forEach { namespace ->
                listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui",
                    "powercal-profile").forEach { storage.deleteSharedPreferences("$namespace-$it") }
                File(storage.cacheDir, namespace).deleteRecursively()
            }
        }
    }

    private fun memoStoreSnapshot(store: PowerStore, eventSeq: Long): List<Any?> = listOf(
        store.all(), store.get(eventSeq), store.history(eventSeq), store.commitCount(),
        store.identityCount(), store.nextSequencePreview(), store.nextQueueOrderPreview(),
        store.draftCount(), store.pendingCount()
    )

    private fun assertMemoCompletionState(
        store: PowerStore,
        eventSeq: Long,
        revision: Long,
        completedKeys: Set<String>,
        queueOrder: Long
    ) {
        val record = requireNotNull(store.get(eventSeq))
        assertEquals("Memo completion revision", revision, record.revision)
        assertEquals("Memo completion commits", revision, store.commitCount())
        assertEquals("Memo completion is scoped to its expected key", completedKeys,
            record.completedKeys)
        assertEquals("Memo remains the same persisted event", 1, store.all().size)
        assertEquals("completion does not allocate another identity", 1, store.identityCount())
        assertEquals("completion does not allocate another event sequence", 2L,
            store.nextSequencePreview())
        assertEquals("completion does not allocate draft queue order", queueOrder,
            store.nextQueueOrderPreview())
        assertEquals("completion does not create an editor draft", 0L, store.draftCount())
        assertEquals("completion does not create a pending proposal", 0, store.pendingCount())
        assertEquals("each state transition has one auditable commit", revision.toInt(),
            store.history(eventSeq).size)
        if (revision == 3L) {
            assertEquals(setOf("CREATE", "COMPLETE", "REOPEN"),
                store.history(eventSeq).map { it.operation }.toSet())
        }
    }
}
