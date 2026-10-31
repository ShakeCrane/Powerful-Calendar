package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class ClearActivityRecreationRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class IsolatedContext(
        target: Context,
        private val storage: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(storage.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun alias(name: String) = "$namespace-$name"
        private fun databasePath(name: String) = storage.getDatabasePath(alias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            storage.getSharedPreferences(alias(name), mode)
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
            return storage.openOrCreateDatabase(alias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: android.database.DatabaseErrorHandler?
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

    private fun evaluate(web: WebView, script: String): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript(script) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript response timed out: $script",
            completed.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun readyWebView(): WebView {
        var result: WebView? = null
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                result = findWebView(compose.activity.findViewById(android.R.id.content))
            }
            result != null && evaluate(requireNotNull(result),
                "!!window.PowerCalSkin && !!window.PowerCalSkin.receive && " +
                    "!!document.querySelector('[data-action=\\\"createDraft\\\"]')") == "true"
        }
        return requireNotNull(result)
    }

    private fun attachClear(context: Context, store: PowerStore) {
        compose.runOnUiThread {
            SkinPackageManager(context).select("powercal-clear-default")
            compose.activity.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    PowerCalTheme { PowerCalApp(store) }
                }
            }
        }
        readyWebView()
    }

    private fun editorSnapshot(): JSONObject = WebSkinBridgeRuntime.state()
        .getJSONObject("editing").getJSONObject("deck")

    private fun openTransientActivity(web: WebView) {
        evaluate(web, "document.querySelector('[data-action=\\\"createDraft\\\"]').click(); true")
        compose.waitUntil(10_000) {
            evaluate(web, "!!document.querySelector('#timelineCreateDialog[open]')") == "true"
        }
        evaluate(web, "document.querySelector('[data-timeline-create-kind=\\\"ACTIVITY\\\"]').click(); true")
        compose.waitUntil(15_000) {
            evaluate(web, "!document.querySelector('#skinSheet').hidden && " +
                "!!document.querySelector('#sheetFields [data-field=title]" +
                "')") == "true"
        }
    }

    private fun recreateActivity() {
        val oldActivity = compose.activity
        compose.runOnUiThread { oldActivity.recreate() }
        compose.waitUntil(30_000) {
            compose.activity !== oldActivity &&
                compose.activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
        }
    }

    @Test
    fun clearTransientEditorHasNoWriteWhenCleanAndDirtyInputReturnsAfterActivityRecreation() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-clear-activity-recovery-${UUID.randomUUID()}"
        val databaseName = "recovery.db"
        var context = IsolatedContext(compose.activity, storage, namespace)
        val store = PowerStore(context, databaseName)
        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()

        try {
            store.readableDatabase
            attachClear(context, store)

            var web = readyWebView()
            openTransientActivity(web)
            assertEquals(0L, store.draftCount())
            assertEquals(0L, store.identityCount())
            assertEquals(0L, store.commitCount())
            assertEquals(1L, store.nextSequencePreview())

            recreateActivity()
            context = IsolatedContext(compose.activity, storage, namespace)
            attachClear(context, store)
            assertEquals("clean editor recreation must remain transient", 0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertTrue(store.all().isEmpty())
            assertEquals(0L, store.identityCount())
            assertEquals(1L, store.nextSequencePreview())
            assertEquals(1L, store.nextQueueOrderPreview())
            assertEquals(0L, store.commitCount())

            web = readyWebView()
            openTransientActivity(web)
            val title = "PC_S14_CLEAR_ACTIVITY_RECREATE"
            evaluate(web, "(()=>{const field=document.querySelector('#sheetFields [data-field=title]');" +
                "field.value='$title';field.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
            compose.waitUntil(15_000) {
                editorSnapshot().optJSONObject("form")?.optString("title") == title
            }

            recreateActivity()
            compose.waitUntil(20_000) { store.pendingCount() == 1 }
            context = IsolatedContext(compose.activity, storage, namespace)
            attachClear(context, store)

            web = readyWebView()
            evaluate(web, "document.querySelector('[data-action=\\\"openDeck\\\"]').click(); true")
            compose.waitUntil(15_000) {
                val deck = editorSnapshot()
                deck.optBoolean("open") && deck.optJSONArray("cards")?.length() == 1 &&
                    deck.getJSONArray("cards").getJSONObject(0).optString("title") == title &&
                    deck.optJSONObject("form")?.optString("title") == title
            }

            assertEquals(1L, store.draftCount())
            assertEquals(1, store.pendingCount())
            assertEquals(1L, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertEquals(2L, store.nextQueueOrderPreview())
            assertEquals(0L, store.commitCount())
            assertTrue(store.all().isEmpty())
            assertNotNull(editorSnapshot().optJSONObject("form"))
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }

    @Test
    fun completedRecurringMemoProjectionSurvivesClearActivityRecreation() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-clear-memo-activity-recovery-${UUID.randomUUID()}"
        val databaseName = "memo-recovery.db"
        var context = IsolatedContext(compose.activity, storage, namespace)
        val store = PowerStore(context, databaseName)
        val zone = ZoneId.of("Asia/Shanghai")
        val firstDate = LocalDate.now(zone).plusDays(1)
        val occurrenceKey = "${firstDate}T00:00"
        val created = store.createFormalEvent(
            DraftForm(
                kind = TemporalKind.MEMO,
                title = "Clear Memo projection after Activity recreation",
                startText = firstDate.toString(),
                zoneId = zone.id,
                trackingEnabled = true,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
            ),
            "clear-memo-activity-recovery-$namespace"
        ) as PowerStore.CommitOutcome.Committed
        assertEquals(1L, created.eventSeq)
        assertTrue(store.setCompleted(created.eventSeq, occurrenceKey, true, created.revision))
        val completed = requireNotNull(store.get(created.eventSeq))
        assertEquals(2L, completed.revision)
        assertEquals(setOf(occurrenceKey), completed.completedKeys)

        val onboarding = context.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
        onboarding.edit().putBoolean("system-calendar-import-asked", true).commit()
        try {
            attachClear(context, store)

            fun assertMemoProjection(web: WebView) {
                val toggleSelector = "#memoList [data-action='toggleMemo']" +
                    "[data-event-seq='${created.eventSeq}'][data-occurrence-key='$occurrenceKey']"
                val quotedToggleSelector = JSONObject.quote(toggleSelector)
                evaluate(web, "document.querySelector('.clear-tabbar [data-action=memo]').click();true")
                compose.waitUntil(20_000) {
                    evaluate(web, "!document.querySelector('#memoPage').hidden && " +
                        "document.querySelector('#memoCounter')?.textContent==='2' && " +
                        "document.querySelectorAll('#memoList .memo-row').length===2 && " +
                        "!document.querySelector($quotedToggleSelector)") == "true"
                }

                evaluate(web, "document.querySelector('.tabs [data-filter=done]').click();true")
                compose.waitUntil(15_000) {
                    evaluate(web, "document.querySelectorAll('#memoList .memo-row').length===1 && " +
                        "document.querySelector($quotedToggleSelector)?.getAttribute('aria-label')==='恢复未完成'") == "true"
                }
            }

            var web = readyWebView()
            assertMemoProjection(web)
            assertEquals("opening the Memo projection must not create a commit", 2L, store.commitCount())

            recreateActivity()
            context = IsolatedContext(compose.activity, storage, namespace)
            attachClear(context, store)
            web = readyWebView()
            assertMemoProjection(web)

            assertEquals("recreation must preserve the exact completed owner snapshot", completed,
                store.get(created.eventSeq))
            assertEquals(2L, store.commitCount())
            assertEquals(1, store.identityCount())
            assertEquals(2L, store.nextSequencePreview())
            assertEquals(0L, store.draftCount())
            assertEquals(0, store.pendingCount())
            assertEquals(listOf("CREATE", "COMPLETE"),
                store.history(created.eventSeq).asReversed().map { it.operation })
            println("SEP03_MEMO_ACTIVITY_RECREATE eventSeq=${created.eventSeq} " +
                "completedOccurrence=$occurrenceKey revision=${completed.revision} " +
                "commits=${store.commitCount()} identities=${store.identityCount()} " +
                "openOccurrences=2 doneOccurrences=1 writesAfterRecreate=0")
        } finally {
            compose.runOnUiThread {
                compose.activity.setContent { }
                PowerCalSkinRegistry.reset()
            }
            compose.waitForIdle()
            store.close()
            context.deleteDatabase(databaseName)
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-profile")
                .forEach { storage.deleteSharedPreferences("$namespace-$it") }
            File(storage.cacheDir, namespace).deleteRecursively()
        }
    }
}
