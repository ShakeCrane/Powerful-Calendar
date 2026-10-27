package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.EditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebSkinRecurrenceBridgeTest {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var service: WebSkinEditService
    private fun id() = UUID.randomUUID().toString()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "web-recurrence-${id()}.db")
        service = WebSkinEditService(store, DeckController(StoreDeckPersistence(store)))
    }
    @After fun teardown() {
        val database = store.databaseName
        store.close()
        context.deleteDatabase(database)
    }
    private fun wire(name: String, vararg fields: Pair<String, Any>): String {
        val payload = JSONObject().put("name", name).put("requestId", id())
        fields.forEach { (key, value) -> payload.put(key, value) }
        return JSONObject().put("version", 1).put("type", "command").put("payload", payload).toString()
    }
    private fun command(name: String, vararg fields: Pair<String, Any>): WebEditCommand =
        requireNotNull(WebEditWire.decode(wire(name, *fields))) { "Bridge rejected $name" }

    private fun daily(target: PowerStore = store, tracking: Boolean = false): Long {
        val card = target.newDraft(DraftForm(kind = TemporalKind.ALARM, title = "每日闹钟",
            startText = "2026-10-20 07:30", zoneId = "Asia/Shanghai", reminderMinutes = -1,
            trackingEnabled = tracking,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 4)))
        assertTrue(target.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        return card.eventSeq
    }

    @Test fun singleOccurrenceRequiresScopeAndSurvivesCheckpoint() = runBlocking {
        val seq = daily()
        val initial = requireNotNull(store.get(seq))
        val key = "2026-10-21T07:30"
        val open = command("editor.open", "eventSeq" to seq, "expectedRevision" to initial.revision,
            "occurrenceKey" to key)
        assertEquals("opened", service.execute(open).getString("status"))
        var editor = service.snapshot().getJSONObject("editor")
        val session = editor.getString("sessionId")
        assertTrue(editor.getBoolean("requiresRecurrenceScope"))
        assertTrue(editor.getBoolean("canEditSelectedOccurrence"))
        assertEquals("invalid", service.execute(command("editor.patch", "sessionId" to session,
            "field" to "title", "value" to "too early")).getString("status"))
        assertEquals("selected", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "SINGLE_OCCURRENCE")).getString("status"))
        editor = service.snapshot().getJSONObject("editor")
        assertEquals("2026-10-21 07:30", editor.getJSONObject("form").getString("startText"))
        assertEquals("invalid", service.execute(command("editor.patch", "sessionId" to session,
            "field" to "notes", "value" to "not permitted for single occurrence")).getString("status"))
        assertEquals("edited", service.execute(command("editor.patch", "sessionId" to session,
            "field" to "title", "value" to "仅这一次")).getString("status"))
        assertEquals("invalid", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "WHOLE_SERIES")).getString("status"))
        assertEquals("discarded_changes", service.execute(command("editor.discardChanges",
            "sessionId" to session)).getString("status"))
        assertEquals("selected", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "WHOLE_SERIES")).getString("status"))
        assertEquals("selected", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "SINGLE_OCCURRENCE")).getString("status"))
        assertEquals("edited", service.execute(command("editor.patch", "sessionId" to session,
            "field" to "title", "value" to "仅这一次")).getString("status"))
        assertEquals("closed", service.execute(command("editor.close", "sessionId" to session)).getString("status"))
        assertEquals(key, store.pendingDraftsFor(seq).single().form.editOccurrenceKey)
        assertEquals("opened", service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to initial.revision, "occurrenceKey" to key)).getString("status"))
        editor = service.snapshot().getJSONObject("editor")
        assertEquals("SINGLE_OCCURRENCE", editor.getString("recurrenceScope"))
        assertTrue(editor.getBoolean("dirty"))
        val resumed = editor.getString("sessionId")
        val save = command("editor.save", "sessionId" to resumed)
        assertEquals("confirmation_required", service.execute(save).getString("status"))
        assertEquals(initial, store.get(seq))
        val review = requireNotNull(service.pendingWriteReview)
        assertFalse(service.snapshot().toString().contains(review.token))
        val before = requireNotNull(review.before)
        val proposed = requireNotNull(review.proposed)
        assertEquals("2026-10-21 07:30", DraftForm.formatInstant(before.startMillis,
            ZoneId.of("Asia/Shanghai")))
        val rows=snapshotRows(before,proposed)
        assertEquals(25,rows.size)
        assertTrue(rows.any { it.label=="标题" && it.before=="每日闹钟" && it.after=="仅这一次" })
        assertEquals("saved", service.resolveFormalWriteReview(review.token, true).getString("status"))
        assertEquals("saved", service.execute(save).getString("status"))
        val saved = requireNotNull(store.get(seq))
        assertEquals(initial.revision + 1, saved.revision)
        assertEquals("每日闹钟", saved.title)
        assertEquals("仅这一次", saved.recurrence.overrides[key]?.title)
        assertEquals(1, store.all().size)
    }

    @Test fun futureScopeSplitsOnceAndRejectsForgedOccurrence() = runBlocking {
        val seq = daily()
        val initial = requireNotNull(store.get(seq))
        assertEquals("invalid", service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to initial.revision, "occurrenceKey" to "2026-10-30T07:30"))
            .getString("status"))
        val key = "2026-10-22T07:30"
        assertEquals("opened", service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to initial.revision, "occurrenceKey" to key)).getString("status"))
        val session = service.snapshot().getJSONObject("editor").getString("sessionId")
        assertEquals("selected", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "THIS_AND_FUTURE")).getString("status"))
        assertEquals("edited", service.execute(command("editor.patch", "sessionId" to session,
            "field" to "title", "value" to "后续系列")).getString("status"))
        assertEquals("invalid", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "WHOLE_SERIES")).getString("status"))
        val save = command("editor.save", "sessionId" to session)
        assertEquals("confirmation_required", service.execute(save).getString("status"))
        assertEquals(1, store.all().size)
        val review = requireNotNull(service.pendingWriteReview)
        assertEquals("saved", service.resolveFormalWriteReview(review.token, true).getString("status"))
        assertEquals("saved", service.execute(save).getString("status"))
        assertFalse(service.snapshot().getJSONObject("editor").getBoolean("open"))
        val records = store.all()
        assertEquals(2, records.size)
        assertEquals("每日闹钟", records.single { it.eventSeq == seq }.title)
        assertEquals("后续系列", records.single { it.eventSeq != seq }.title)
    }

    @Test fun scopeCommandsAreClosedAndLegacyOpenStillDecodes() {
        assertNotNull(WebEditWire.decode(wire("editor.open", "eventSeq" to 1L, "expectedRevision" to 1L)))
        assertNull(WebEditWire.decode(wire("editor.open", "eventSeq" to 1L, "expectedRevision" to 1L,
            "occurrenceKey" to "../../invalid")))
        assertNull(WebEditWire.decode(wire("editor.scope", "sessionId" to id(), "scope" to "EVERYTHING")))
        assertNull(WebEditWire.decode(wire("editor.scope", "sessionId" to id(),
            "scope" to "WHOLE_SERIES", "confirm" to true)))
    }

    @Test fun untrustedSaveRequestCannotPublishWithoutHostReview() = runBlocking {
        val seq = daily()
        val before = requireNotNull(store.get(seq))
        assertEquals("opened", service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to before.revision, "occurrenceKey" to "2026-10-21T07:30"))
            .getString("status"))
        val session = service.snapshot().getJSONObject("editor").getString("sessionId")
        assertEquals("selected", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "SINGLE_OCCURRENCE")).getString("status"))
        assertEquals("edited", service.execute(command("editor.patch", "sessionId" to session,
            "field" to "title", "value" to "需宿主确认")).getString("status"))
        val save=command("editor.save", "sessionId" to session)
        assertEquals("confirmation_required", service.execute(save).getString("status"))
        assertEquals(before, store.get(seq))
        assertNull(WebEditWire.decode(wire("editor.confirm", "sessionId" to session, "approve" to true)))
        val review=requireNotNull(service.pendingWriteReview)
        assertEquals("stale",service.resolveFormalWriteReview(id(),true).getString("status"))
        assertEquals("locked",service.execute(command("editor.patch", "sessionId" to session,
            "field" to "title", "value" to "changed during review")).getString("status"))
        assertEquals(before,store.get(seq))
        assertEquals("cancelled",service.resolveFormalWriteReview(review.token,false).getString("status"))
        assertEquals("cancelled",service.execute(save).getString("status"))
        assertEquals(before,store.get(seq))
    }

    @Test fun singleOccurrenceCannotChangeEventKind() = runBlocking {
        val seq = daily()
        val before = requireNotNull(store.get(seq))
        val key = "2026-10-21T07:30"
        assertEquals("opened", service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to before.revision, "occurrenceKey" to key)).getString("status"))
        val session = service.snapshot().getJSONObject("editor").getString("sessionId")
        assertEquals("selected", service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "SINGLE_OCCURRENCE")).getString("status"))

        assertEquals("invalid", service.execute(command("editor.patch", "sessionId" to session,
            "field" to "kind", "value" to "DEADLINE")).getString("status"))
        assertEquals("ALARM", service.snapshot().getJSONObject("editor")
            .getJSONObject("form").getString("kind"))
        assertEquals(before, store.get(seq))
        assertTrue(store.pendingDraftsFor(seq).isEmpty())
        assertEquals("closed", service.execute(command("editor.close", "sessionId" to session))
            .getString("status"))
        assertTrue(store.pendingDraftsFor(seq).isEmpty())
    }

    @Test fun addingRecurrenceToASingleEventAlsoRequiresHostReview() = runBlocking {
        val card=store.newDraft(DraftForm(kind=TemporalKind.ALARM,title="单次",
            startText="2026-10-20 07:30",zoneId="Asia/Shanghai",reminderMinutes=-1))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val before=requireNotNull(store.get(card.eventSeq))
        assertEquals("opened",service.execute(command("editor.open", "eventSeq" to card.eventSeq,
            "expectedRevision" to before.revision)).getString("status"))
        val session=service.snapshot().getJSONObject("editor").getString("sessionId")
        assertEquals("edited",service.execute(command("editor.patch", "sessionId" to session,
            "field" to "recurrenceFreq", "value" to "DAILY")).getString("status"))
        assertEquals("confirmation_required",service.execute(command("editor.save",
            "sessionId" to session)).getString("status"))
        assertEquals(before,store.get(card.eventSeq))
        val review=requireNotNull(service.pendingWriteReview)
        assertNull(review.scope)
        assertEquals("saved",service.resolveFormalWriteReview(review.token,true).getString("status"))
        assertEquals(RecurrenceFreq.DAILY,requireNotNull(store.get(card.eventSeq)).recurrence.freq)
    }

    @Test fun legacyOpenWithoutOccurrenceCanOnlyChooseWholeSeries() = runBlocking {
        val seq=daily()
        val before=requireNotNull(store.get(seq))
        assertEquals("opened",service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to before.revision)).getString("status"))
        val state=service.snapshot().getJSONObject("editor")
        assertTrue(state.getBoolean("requiresRecurrenceScope"))
        assertFalse(state.getBoolean("canEditSelectedOccurrence"))
        val session=state.getString("sessionId")
        assertEquals("invalid",service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "SINGLE_OCCURRENCE")).getString("status"))
        assertEquals("selected",service.execute(command("editor.scope", "sessionId" to session,
            "scope" to "WHOLE_SERIES")).getString("status"))
        assertEquals("edited",service.execute(command("editor.patch", "sessionId" to session,
            "field" to "title", "value" to "整组修改")).getString("status"))
        assertEquals("confirmation_required",service.execute(command("editor.save", "sessionId" to session))
            .getString("status"))
        assertEquals("saved",service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token,true).getString("status"))
        assertEquals("整组修改",requireNotNull(store.get(seq)).title)
    }

    @Test fun changingSkinCancelsPendingReviewButKeepsTheEditedDraft() = runBlocking {
        val seq=daily()
        val before=requireNotNull(store.get(seq))
        val key="2026-10-21T07:30"
        assertEquals("opened",service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to before.revision, "occurrenceKey" to key)).getString("status"))
        val session=service.snapshot().getJSONObject("editor").getString("sessionId")
        service.execute(command("editor.scope", "sessionId" to session, "scope" to "SINGLE_OCCURRENCE"))
        service.execute(command("editor.patch", "sessionId" to session, "field" to "title", "value" to "保留输入"))
        val save=command("editor.save", "sessionId" to session)
        assertEquals("confirmation_required",service.execute(save).getString("status"))
        val token=requireNotNull(service.pendingWriteReview).token
        assertTrue(service.prepareSkinChange())
        assertNull(service.pendingWriteReview)
        assertEquals("stale",service.resolveFormalWriteReview(token,true).getString("status"))
        assertEquals("cancelled",service.execute(save).getString("status"))
        assertEquals(before,store.get(seq))
        assertEquals("保留输入",store.pendingDraftsFor(seq).single().form.title)
        assertEquals(key,store.pendingDraftsFor(seq).single().form.editOccurrenceKey)
    }

    @Test fun recordChangedDuringHostReviewIsNotOverwritten() = runBlocking {
        val seq=daily(tracking=true)
        val before=requireNotNull(store.get(seq))
        val key="2026-10-21T07:30"
        assertEquals("opened",service.execute(command("editor.open", "eventSeq" to seq,
            "expectedRevision" to before.revision, "occurrenceKey" to key)).getString("status"))
        val session=service.snapshot().getJSONObject("editor").getString("sessionId")
        service.execute(command("editor.scope", "sessionId" to session, "scope" to "SINGLE_OCCURRENCE"))
        service.execute(command("editor.patch", "sessionId" to session, "field" to "title", "value" to "过期修改"))
        val save=command("editor.save", "sessionId" to session)
        assertEquals("confirmation_required",service.execute(save).getString("status"))
        val token=requireNotNull(service.pendingWriteReview).token
        assertTrue(store.setCompleted(seq,key,true,before.revision))
        val changed=requireNotNull(store.get(seq))
        assertEquals("conflict",service.resolveFormalWriteReview(token,true).getString("status"))
        assertEquals("conflict",service.execute(save).getString("status"))
        assertEquals(changed,store.get(seq))
        assertTrue(requireNotNull(store.get(seq)).completedKeys.contains(key))
    }

    @Test fun nativeAndWebEditingProduceTheSameRecordsForEachScope() = runBlocking {
        for (scope in RecurrenceEditScope.entries) {
            val native = PowerStore(context, "native-pair-${id()}.db")
            val web = PowerStore(context, "web-pair-${id()}.db")
            val nativeDb = native.databaseName
            val webDb = web.databaseName
            try {
                val nativeSeq = daily(native)
                val webSeq = daily(web)
                val key = "2026-10-22T07:30"
                val original = requireNotNull(native.get(nativeSeq))
                val edit = EditSession.open(native, original, key)
                try {
                    edit.selectRecurrenceScope(scope)
                    edit.edit(edit.form.copy(title = "同一修改"))
                    if (scope == RecurrenceEditScope.THIS_AND_FUTURE)
                        assertTrue(edit.saveThisAndFuture() is PowerStore.RecurrenceSplitOutcome.Applied)
                    else assertTrue(edit.save() is PowerStore.DirectEditOutcome.Saved)
                } finally { edit.close() }

                val bridge = WebSkinEditService(web, DeckController(StoreDeckPersistence(web)))
                assertEquals("opened", bridge.execute(command("editor.open", "eventSeq" to webSeq,
                    "expectedRevision" to requireNotNull(web.get(webSeq)).revision,
                    "occurrenceKey" to key)).getString("status"))
                val session = bridge.snapshot().getJSONObject("editor").getString("sessionId")
                assertEquals("selected", bridge.execute(command("editor.scope", "sessionId" to session,
                    "scope" to scope.name)).getString("status"))
                assertEquals("edited", bridge.execute(command("editor.patch", "sessionId" to session,
                    "field" to "title", "value" to "同一修改")).getString("status"))
                val save=command("editor.save", "sessionId" to session)
                assertEquals("confirmation_required", bridge.execute(save).getString("status"))
                assertEquals("saved", bridge.resolveFormalWriteReview(
                    requireNotNull(bridge.pendingWriteReview).token, true).getString("status"))
                if (scope != RecurrenceEditScope.THIS_AND_FUTURE)
                    bridge.execute(command("editor.close", "sessionId" to session))

                // Independent stores have different wall-clock audit timestamps; every
                // business field, identity, version, recurrence and reminder remains compared.
                val left = native.all().sortedBy { it.eventSeq }
                    .map { it.copy(createdAtMillis = 0, updatedAtMillis = 0) }
                val right = web.all().sortedBy { it.eventSeq }
                    .map { it.copy(createdAtMillis = 0, updatedAtMillis = 0) }
                assertEquals("scope=$scope", left, right)
                assertEquals(native.identityCount(), web.identityCount())
                assertEquals(native.draftCount(), web.draftCount())
                assertEquals(native.commitCount(), web.commitCount())
                assertEquals(native.pendingCount(), web.pendingCount())
            } finally {
                native.close(); web.close()
                context.deleteDatabase(nativeDb); context.deleteDatabase(webDb)
            }
        }
    }
}
