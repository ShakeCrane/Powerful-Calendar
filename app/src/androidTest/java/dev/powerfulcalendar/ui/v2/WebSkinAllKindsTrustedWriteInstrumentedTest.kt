package dev.powerfulcalendar.ui.v2

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
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
}
