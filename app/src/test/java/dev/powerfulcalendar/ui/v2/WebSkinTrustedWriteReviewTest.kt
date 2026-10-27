package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebSkinTrustedWriteReviewTest {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var service: WebSkinEditService

    private fun id() = UUID.randomUUID().toString()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "web-write-review-${id()}.db")
        service = WebSkinEditService(store, DeckController(StoreDeckPersistence(store)))
    }

    @After fun tearDown() {
        val database = store.databaseName
        store.close()
        context.deleteDatabase(database)
    }

    @Test fun ordinaryFormalEditWaitsForHostReviewBeforeChangingTheRecord() = runBlocking {
        val candidate = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "原标题"))
        assertTrue(store.commit(candidate.draftId) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(candidate.eventSeq))

        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            id(), before.eventSeq, before.revision
        )).getString("status"))
        val session = service.snapshot().getJSONObject("editor").getString("sessionId")
        assertEquals("edited", service.execute(WebEditCommand.PatchEvent(
            id(), session, "title", "新标题"
        )).getString("status"))

        val save = WebEditCommand.SaveEvent(id(), session)
        val reply = service.execute(save)
        assertEquals("confirmation_required", reply.getString("status"))
        assertEquals(before, store.get(before.eventSeq))
        assertEquals(1, store.all().size)
        assertEquals(1L, store.commitCount())
        val review = requireNotNull(service.pendingWriteReview)
        assertTrue(!service.snapshot().toString().contains(review.token))
        assertEquals("saved", service.resolveFormalWriteReview(review.token, true)
            .getString("status"))
        assertEquals(before.revision + 1, store.get(before.eventSeq)!!.revision)
        assertEquals("新标题", store.get(before.eventSeq)!!.title)
        assertEquals("saved", service.execute(save).getString("status"))
    }

    @Test fun publishingANewSkinDraftWaitsForHostReviewBeforeCreatingAFormalEvent() = runBlocking {
        val opened = service.execute(WebEditCommand.CreateDeck(id(), TemporalKind.ALARM))
        assertEquals("created", opened.getString("status"))
        val clientDraftId = opened.getString("detail").toLong()
        val deck = service.snapshot().getJSONObject("deck")
        val version = deck.getLong("version")
        val start = deck.getJSONObject("form").getString("startText")
        assertTrue(start.isNotBlank())

        assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
            id(), clientDraftId, version, "title", "待确认提醒"
        )).getString("status"))
        val reply = service.execute(WebEditCommand.CommitDeck(id(), clientDraftId, version))

        assertEquals("confirmation_required", reply.getString("status"))
        assertTrue(store.all().isEmpty())
        assertEquals(0, store.commitCount())
        val review = requireNotNull(service.pendingWriteReview)
        assertEquals("cancelled", service.resolveFormalWriteReview(review.token, false)
            .getString("status"))
        assertTrue(store.all().isEmpty())

        val retry = WebEditCommand.CommitDeck(id(), clientDraftId, version)
        assertEquals("confirmation_required", service.execute(retry).getString("status"))
        val approved = requireNotNull(service.pendingWriteReview)
        assertEquals("committed", service.resolveFormalWriteReview(approved.token, true)
            .getString("status"))
        assertEquals(1, store.all().size)
        assertEquals("待确认提醒", store.all().single().title)
        assertEquals(1, store.commitCount())
    }

    @Test fun publishingAnExistingEditDraftRechecksTheReviewedDraftSnapshot() = runBlocking {
        val created = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "正式标题"))
        assertTrue(store.commit(created.draftId) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(created.eventSeq))
        val edit = store.proposeReplace(before.eventSeq)
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(id(), edit.draftId))
            .getString("status"))
        val state = service.snapshot().getJSONObject("deck")
        val version = state.getLong("version")
        assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
            id(), edit.draftId, version, "title", "皮肤拟修改"
        )).getString("status"))
        val publish = WebEditCommand.CommitDeck(id(), edit.draftId, version)
        assertEquals("confirmation_required", service.execute(publish).getString("status"))
        assertEquals(before, store.get(before.eventSeq))

        val review = requireNotNull(service.pendingWriteReview)
        val captured = requireNotNull(review.deckCard)
        val external = captured.form.copy(location = "审查期间被外部修改")
        assertTrue(store.stageDraft(captured.draftId, external, captured.draftVersion)
            is PowerStore.StageResult.Saved)
        assertEquals("conflict", service.resolveFormalWriteReview(review.token, true)
            .getString("status"))
        assertEquals(before, store.get(before.eventSeq))
    }

    @Test fun deletingAFormalEventFromTheDeckRequiresHostReview() = runBlocking {
        val created = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "待删除"))
        assertTrue(store.commit(created.draftId) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(created.eventSeq))
        val deletion = store.proposeReplace(before.eventSeq, DraftOperation.DELETE)
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(id(), deletion.draftId))
            .getString("status"))
        val state = service.snapshot().getJSONObject("deck")
        val commit = WebEditCommand.CommitDeck(id(), deletion.draftId, state.getLong("version"))

        assertEquals("confirmation_required", service.execute(commit).getString("status"))
        assertEquals(before, store.get(before.eventSeq))
        val review = requireNotNull(service.pendingWriteReview)
        assertEquals(DraftOperation.DELETE, review.operation)
        assertEquals(null, review.proposed)
        assertEquals("committed", service.resolveFormalWriteReview(review.token, true)
            .getString("status"))
        assertEquals(null, store.get(before.eventSeq))
    }
}
