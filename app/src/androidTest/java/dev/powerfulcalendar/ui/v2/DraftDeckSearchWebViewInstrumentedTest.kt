package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.PixelCopy
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
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DraftDeckSearchWebViewInstrumentedTest {
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

        private fun isolatedDatabasePath(name: String) =
            storage.getDatabasePath(databaseAlias(name)).also {
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
        override fun deleteDatabase(name: String): Boolean =
            storage.deleteDatabase(databaseAlias(name))
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
        val completed = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(expression) { result ->
                value.set(result)
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out: $expression",
            completed.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJs(web: WebView, expression: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(web, expression) == "true") return
            Thread.sleep(100)
        }
        val diagnostic = runCatching {
            evaluate(web, "JSON.stringify({sheet:document.querySelector('#skinSheet')?.hidden," +
                "search:document.querySelector('.deck-search input')?.value," +
                "cards:document.querySelectorAll('.sheet-cards .sheet-card').length," +
                "formal:document.querySelectorAll('.deck-formal-results .deck-result').length," +
                "title:document.querySelector('#sheetFields [data-field=title]')?.value})")
        }.getOrDefault("unavailable")
        throw AssertionError("WebView condition timed out: $expression; state=$diagnostic")
    }

    private fun submitSearch(web: WebView, query: String) {
        val value = JSONObject.quote(query)
        assertEquals("true", evaluate(web,
            "(()=>{const form=document.querySelector('.deck-search');" +
                "const input=form?.querySelector('input[type=search]');" +
                "if(!form||!input)return false;input.value=$value;form.requestSubmit();return true})()"))
    }

    private fun assertSheetVisiblyOpen(web: WebView) {
        val visible = evaluate(web,
            "(()=>{const e=document.querySelector('#skinSheet');" +
                "const p=e?.querySelector('.sheet-panel');" +
                "const r=e?.getBoundingClientRect();const pr=p?.getBoundingClientRect();" +
                "return !!e&&!e.hidden&&getComputedStyle(e).display!=='none'&&" +
                "getComputedStyle(e).visibility==='visible'&&r.width>=innerWidth-1&&" +
                "r.height>=innerHeight-1&&!!p&&getComputedStyle(p).display!=='none'&&" +
                "getComputedStyle(p).visibility==='visible'&&Number(getComputedStyle(p).opacity)>0&&" +
                "pr.width>=innerWidth*.5&&pr.height>=innerHeight*.5})()")
        val state = evaluate(web,
            "(()=>{const e=document.querySelector('#skinSheet');const p=e?.querySelector('.sheet-panel');" +
                "const r=e?.getBoundingClientRect();const pr=p?.getBoundingClientRect();" +
                "const s=p&&getComputedStyle(p);" +
                "return JSON.stringify({hidden:e?.hidden,display:e&&getComputedStyle(e).display," +
                "visibility:e&&getComputedStyle(e).visibility,inner:[innerWidth,innerHeight]," +
                "sheet:r&&[r.x,r.y,r.width,r.height],panelDisplay:s?.display," +
                "panel:pr&&[pr.x,pr.y,pr.width,pr.height]," +
                "minHeight:s?.minHeight,maxHeight:s?.maxHeight," +
                "title:document.querySelector('#sheetTitle')?.textContent})})()")
        assertEquals("Clear sheet must be visibly rendered; state=$state", "true", visible)
    }

    private fun saveScreenshot(name: String, web: WebView): File {
        awaitNextWebViewFrame(web)
        val visualState = CountDownLatch(1)
        compose.runOnUiThread {
            web.postVisualStateCallback(SystemClock.uptimeMillis(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestedState: Long) {
                        visualState.countDown()
                    }
                })
        }
        assertTrue("WebView did not finish drawing before screenshot",
            visualState.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertSheetVisiblyOpen(web)
        val visible = AtomicReference<Boolean>()
        compose.runOnUiThread {
            val bounds = android.graphics.Rect()
            visible.set(web.isShown && web.getGlobalVisibleRect(bounds) &&
                bounds.width() > 0 && bounds.height() > 0)
        }
        assertTrue("WebView is not visible on screen", requireNotNull(visible.get()))
        val file = File(requireNotNull(compose.activity.externalCacheDir), name)
        val bitmapRef = AtomicReference<Bitmap>()
        val pixelCopyResult = AtomicReference<Int>()
        val copyCompleted = CountDownLatch(1)
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            bitmapRef.set(bitmap)
            PixelCopy.request(compose.activity.window, bitmap, { result ->
                pixelCopyResult.set(result)
                copyCompleted.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("PixelCopy did not capture the rendered Activity window",
            copyCompleted.await(10, TimeUnit.SECONDS))
        assertEquals("PixelCopy must complete successfully", PixelCopy.SUCCESS,
            requireNotNull(pixelCopyResult.get()))
        val bitmap = requireNotNull(bitmapRef.get())
        try {
            file.outputStream().use { output ->
                assertTrue("screenshot must encode", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        assertTrue("screenshot must contain rendered pixels", file.length() > 0L)
        println("S0_DK01_SCREENSHOT=${file.absolutePath} bytes=${file.length()}")
        return file
    }

    private fun awaitNextWebViewFrame(web: WebView) {
        assertEquals("true", evaluate(web,
            "(()=>{window.__powerCalVisualTestReady=false;" +
                "requestAnimationFrame(()=>requestAnimationFrame(()=>" +
                "window.__powerCalVisualTestReady=true));return true})()"))
        awaitJs(web, "window.__powerCalVisualTestReady===true")
    }

    @Test
    fun actualClearWebViewSearchSelectNavigateAndProposeReplaceWithoutPublishing() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-s0-dk01-${UUID.randomUUID()}"
        val context = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "deck.db"
        val store = PowerStore(context, databaseName)
        val zone = ZoneId.systemDefault()
        val titles = listOf("DK01_PENDING_ALPHA", "DK01_PENDING_BETA", "DK01_OTHER")
        val drafts = mutableListOf<dev.powerfulcalendar.core.DraftCard>()

        try {
            store.readableDatabase
            titles.forEach { title ->
                drafts += store.newDraft(
                    DraftForm(kind = TemporalKind.MEMO, title = title, zoneId = zone.id)
                )
            }
            val formal = store.createFormalEvent(
                DraftForm(kind = TemporalKind.MEMO, title = "DK01_FORMAL_REPLACE_TARGET", zoneId = zone.id),
                idempotencyKey = "seed-${UUID.randomUUID()}"
            ) as PowerStore.CommitOutcome.Committed
            val original = requireNotNull(store.get(formal.eventSeq))
            val initialDraftCount = store.draftCount()
            val initialPendingCount = store.pendingCount()
            val initialIdentityCount = store.identityCount()
            val initialCommitCount = store.commitCount()
            val initialSequence = store.nextSequencePreview()
            val initialQueueOrder = store.nextQueueOrderPreview()
            val queueOrderBefore = drafts.map { requireNotNull(store.draft(it.draftId)).queueOrder }

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
            val view = requireNotNull(web)
            awaitJs(view, "!!document.querySelector('[data-action=\\\"openDeck\\\"]')")
            awaitJs(view,
                "window.PowerCalSkin&&document.querySelector('#pendingCounter')?.textContent==='3'")
            assertEquals("true", evaluate(view,
                "document.querySelector('[data-action=\\\"openDeck\\\"]').click();true"))
            awaitJs(view, "!!document.querySelector('.deck-search input[type=search]') &&" +
                "document.querySelector('#skinSheet')?.hidden===false &&" +
                "document.querySelectorAll('.sheet-cards .sheet-card').length===3")
            assertEquals(initialDraftCount, store.draftCount())
            assertEquals(initialPendingCount, store.pendingCount())
            assertEquals(initialIdentityCount, store.identityCount())
            assertEquals(initialCommitCount, store.commitCount())

            assertEquals("true", evaluate(view,
                "document.querySelector('.sheet-deck-navigation [data-do=\\\"jump\\\"][data-steps=\\\"1\\\"]')?.click();true"))
            awaitJs(view,
                "document.querySelector('#sheetFields [data-field=\\\"title\\\"]')?.value===" +
                    JSONObject.quote(titles[1]))
            assertEquals("navigation must not reorder persistent queue identities",
                queueOrderBefore, drafts.map { requireNotNull(store.draft(it.draftId)).queueOrder })

            submitSearch(view, "PENDING")
            awaitJs(view, "document.querySelectorAll('.sheet-cards .sheet-card').length===2 &&" +
                "document.querySelectorAll('.deck-formal-results .deck-result').length===0")
            val secondDraftId = drafts[1].draftId.toString()
            assertEquals("true", evaluate(view,
                "Array.from(document.querySelectorAll('.sheet-cards .sheet-card'))." +
                    "find(button=>button.dataset.draftId===$secondDraftId)?.click();true"))
            awaitJs(view,
                "document.querySelector('#sheetFields [data-field=\\\"title\\\"]')?.value===" +
                    JSONObject.quote(titles[1]))
            assertEquals(initialDraftCount, store.draftCount())
            assertEquals(initialPendingCount, store.pendingCount())
            assertEquals(initialIdentityCount, store.identityCount())
            assertEquals(initialCommitCount, store.commitCount())

            val formattedSequence = "#" + formal.eventSeq.toString().padStart(4, '0')
            submitSearch(view, formattedSequence)
            awaitJs(view,
                "document.querySelectorAll('.deck-formal-results [data-do=\\\"replace\\\"]').length===1")
            val formalResult = evaluate(view,
                "document.querySelector('.deck-formal-results [data-do=\\\"replace\\\"]').textContent")
            assertTrue("formatted sequence search must show the formal result: $formalResult",
                formalResult.contains(formal.eventSeq.toString().padStart(4, '0')))
            val formalSearchScreenshot = saveScreenshot("s0-dk01-formal-search.png", view)

            assertEquals("true", evaluate(view,
                "document.querySelector('.deck-formal-results [data-do=\\\"replace\\\"]').click();true"))
            compose.waitUntil(15_000) {
                store.pendingDraftsFor(formal.eventSeq).any { it.operation == DraftOperation.REPLACE }
            }
            awaitJs(view,
                "document.querySelector('#sheetFields [data-field=\\\"title\\\"]')?.value===" +
                    JSONObject.quote(original.title))
            awaitJs(view,
                "document.querySelector('#sheetSubtitle')?.textContent?.startsWith(" +
                    JSONObject.quote("${initialDraftCount + 1} 张") + ")===true")

            val proposal = store.pendingDraftsFor(formal.eventSeq)
                .single { it.operation == DraftOperation.REPLACE }
            assertEquals(original.revision, proposal.baseRevision)
            assertEquals(original, store.get(formal.eventSeq))
            assertEquals("search, navigation and proposal must not publish", initialCommitCount, store.commitCount())
            assertEquals("replacement proposal reuses the formal identity", initialIdentityCount, store.identityCount())
            assertEquals(initialDraftCount + 1, store.draftCount())
            assertEquals(initialPendingCount + 1, store.pendingCount())
            assertEquals(initialSequence, store.nextSequencePreview())
            assertEquals(initialQueueOrder + 1, store.nextQueueOrderPreview())
            assertEquals(queueOrderBefore, drafts.map { requireNotNull(store.draft(it.draftId)).queueOrder })
            assertNotNull(store.draft(proposal.draftId))
            val replacementScreenshot = saveScreenshot("s0-dk01-replacement-proposal.png", view)
            assertFalse("search result and replacement proposal must be visibly distinct",
                formalSearchScreenshot.readBytes().contentEquals(replacementScreenshot.readBytes()))

            println(
                "S0_DK01_WEBVIEW_API35 formalSeq=${formal.eventSeq} revision=${original.revision} " +
                    "proposalId=${proposal.draftId} pending=${store.pendingCount()} commits=${store.commitCount()}"
            )
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
