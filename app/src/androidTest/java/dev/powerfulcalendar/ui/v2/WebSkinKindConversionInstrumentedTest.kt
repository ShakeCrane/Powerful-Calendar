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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class WebSkinKindConversionInstrumentedTest {
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
        private fun isolatedDatabasePath(name: String) = storage.getDatabasePath(databaseAlias(name)).also {
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
            errorHandler: android.database.DatabaseErrorHandler?
        ): SQLiteDatabase {
            isolatedDatabasePath(name)
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
        assertTrue("WebView JavaScript response timed out", done.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun awaitJs(web: WebView, condition: String) {
        compose.waitUntil(30_000) { evaluate(web, condition) == "true" }
    }

    @Test
    fun clearConversionPickerHonorsRecurrenceScopeAndWaitsForHostApproval() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-web-kind-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "kind-conversion.db"
        val store = PowerStore(isolated, databaseName)
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).withSecond(0).withNano(0).toInstant().toEpochMilli()
        val draft = store.newDraft(
            DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "WEB_KIND_CONVERSION_FIXTURE",
                startText = DraftForm.formatInstant(start, zone),
                endText = DraftForm.formatInstant(start + 3_600_000L, zone),
                zoneId = zone.id,
                reminderMinutes = -1,
                recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
            )
        )
        assertTrue(store.commit(draft.draftId, actor = "instrumented-test") is PowerStore.CommitOutcome.Committed)
        val eventSeq = draft.eventSeq
        val original = requireNotNull(store.get(eventSeq))
        val commitsBefore = store.commitCount()
        val identitiesBefore = store.identityCount()
        val draftsBefore = store.draftCount()

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
            val eventSelector = "document.querySelector('[data-event-seq=\\\"$eventSeq\\\"]')"
            awaitJs(view, "!!$eventSelector")

            evaluate(view, "$eventSelector.click(); true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && !!document.querySelector('[data-do=\\\"scope\\\"]')")
            evaluate(view, "document.querySelector(\"[data-do='scope'][data-scope='SINGLE_OCCURRENCE']\").click(); true")
            awaitJs(view, "document.querySelector('[data-scope=\\\"SINGLE_OCCURRENCE\\\"]').getAttribute('aria-pressed')==='true'")
            assertEquals("false", evaluate(view, "!!document.querySelector('#sheetFields [data-field=\\\"kind\\\"]')"))
            evaluate(view, "document.querySelector('#sheetClose').click(); true")
            awaitJs(view, "document.querySelector('#skinSheet').hidden")
            assertEquals("single-occurrence close must not write", original, store.get(eventSeq))
            assertEquals(commitsBefore, store.commitCount())

            evaluate(view, "$eventSelector.click(); true")
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && !!document.querySelector('[data-do=\\\"scope\\\"]')")
            evaluate(view, "document.querySelector(\"[data-do='scope'][data-scope='WHOLE_SERIES']\").click(); true")
            awaitJs(view, "document.querySelector('[data-scope=\\\"WHOLE_SERIES\\\"]').getAttribute('aria-pressed')==='true' && !!document.querySelector('#sheetFields [data-field=\\\"kind\\\"]')")
            assertEquals("\"ALARM,ACTIVITY,DEADLINE,MEMO\"", evaluate(view,
                "[...document.querySelector('#sheetFields [data-field=\\\"kind\\\"]').options].map(o=>o.value).join(',')"))

            evaluate(view, "const kind=document.querySelector('#sheetFields [data-field=\\\"kind\\\"]');kind.value='ALARM';kind.dispatchEvent(new Event('change',{bubbles:true}));true")
            awaitJs(view, "document.querySelector('#sheetFields [data-field=\\\"kind\\\"]')?.value==='ALARM' && !document.querySelector('#sheetFields [data-field=\\\"endText\\\"]')")
            compose.runOnIdle {
                val projected = WebSkinBridgeRuntime.state().getJSONObject("editing")
                    .getJSONObject("editor").getJSONObject("form")
                assertEquals("ALARM", projected.getString("kind"))
                assertEquals(original.startMillis, DraftForm.parseInstantIn(
                    projected.getString("startText"), zone
                ))
            }
            assertEquals("conversion must remain pending until Host confirmation", original, store.get(eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(draftsBefore, store.draftCount())

            evaluate(view, "document.querySelector('#sheetSave').click(); true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            assertNotNull(store.get(eventSeq))
            assertEquals(original, store.get(eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            compose.onNodeWithText("取消").performClick()
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作') && document.querySelector('#sheetStatus').textContent.includes('内容仍保留')")
            assertEquals("cancelled review must preserve the formal event", original, store.get(eventSeq))
            assertEquals(commitsBefore, store.commitCount())

            evaluate(view, "document.querySelector('#sheetSave').click(); true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认保存正式事件修改").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                val saved = store.get(eventSeq)
                saved?.kind == TemporalKind.ALARM && saved.revision == original.revision + 1
            }

            val saved = requireNotNull(store.get(eventSeq))
            assertEquals(eventSeq, saved.eventSeq)
            assertEquals(TemporalKind.ALARM, saved.kind)
            assertEquals(original.startMillis, saved.startMillis)
            assertNull(saved.endMillis)
            assertEquals(original.recurrence, saved.recurrence)
            assertEquals(commitsBefore + 1L, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(draftsBefore, store.draftCount())
            assertFalse(saved.recurrence.freq == RecurrenceFreq.NONE)
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
    fun clearDeckConversionWaitsForHostApprovalAndCancellationPreservesCandidate() {
        val storage = compose.activity.applicationContext
        val namespace = "pc-web-deck-convert-${UUID.randomUUID()}"
        val isolated = IsolatedAppContext(compose.activity, storage, namespace)
        val databaseName = "deck-convert.db"
        val store = PowerStore(isolated, databaseName)
        val zone = ZoneId.systemDefault()
        val start = (System.currentTimeMillis() / 60_000L) * 60_000L + 2 * 3_600_000L
        val initial = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "WEB_DECK_CONVERT_FIXTURE",
            startText = DraftForm.formatInstant(start, zone),
            reminderMinutes = -1,
            zoneId = zone.id
        ))
        assertTrue(store.commit(initial.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(initial.eventSeq))
        val conversion = TemporalConversion.convert(DraftForm.from(original), TemporalKind.ACTIVITY)
        val end = start + 45 * 60_000L
        val candidate = store.newDraft(
            conversion.form.copy(endText = DraftForm.formatInstant(end, zone)),
            eventSeq = original.eventSeq,
            operation = DraftOperation.CONVERT,
            expectedRevision = original.revision
        )
        val commitsBefore = store.commitCount()
        val identitiesBefore = store.identityCount()
        val sequenceBefore = store.nextSequencePreview()
        val draftCountBefore = store.draftCount()
        val pendingBefore = store.pendingCount()

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
            awaitJs(view, "!!document.querySelector('[data-action=\\\"openDeck\\\"]')")
            awaitJs(view, "window.PowerCalSkin && document.querySelector('#pendingCounter')?.textContent==='1'")
            evaluate(view, "document.querySelector('[data-action=\\\"openDeck\\\"]').click();true")
            awaitJs(view, "!!document.querySelector('.deck-search input[type=search]') && " +
                "document.querySelector('#skinSheet')?.hidden===false && " +
                "document.querySelector('#sheetFields [data-field=title]')?.value==='WEB_DECK_CONVERT_FIXTURE'")

            evaluate(view, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("CONVERT must not modify the formal event before Host approval",
                original, store.get(original.eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())
            assertEquals(draftCountBefore, store.draftCount())
            assertEquals(pendingBefore, store.pendingCount())

            compose.onNodeWithText("取消").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("cancelling review preserves the formal event",
                original, store.get(original.eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())
            assertEquals(1, store.pendingCount())
            assertEquals(DraftOperation.CONVERT, store.draft(candidate.draftId)?.operation)
            awaitJs(view, "!document.querySelector('#skinSheet').hidden && " +
                "document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作')")

            evaluate(view, "document.querySelector('#sheetSave').click();true")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("确认发布正式事件").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, store.get(original.eventSeq))
            assertEquals(commitsBefore, store.commitCount())
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(30_000) {
                val saved = store.get(original.eventSeq)
                saved?.kind == TemporalKind.ACTIVITY && saved.revision == original.revision + 1
            }

            val converted = requireNotNull(store.get(original.eventSeq))
            assertEquals(original.eventSeq, converted.eventSeq)
            assertEquals(TemporalKind.ACTIVITY, converted.kind)
            assertEquals(start, converted.startMillis)
            assertEquals(end, converted.endMillis)
            assertEquals(commitsBefore + 1L, store.commitCount())
            assertEquals(identitiesBefore, store.identityCount())
            assertEquals(sequenceBefore, store.nextSequencePreview())
            assertEquals(draftCountBefore, store.draftCount())
            assertEquals(0, store.pendingCount())
            println("CLEAR_DECK_CONVERT_VERIFIED=eventSeq=${converted.eventSeq}; revision=${converted.revision}; " +
                "kind=${converted.kind}; commits=${store.commitCount()}; identities=${store.identityCount()}; " +
                "sequence=${store.nextSequencePreview()}; cancel-preserved=true")
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
