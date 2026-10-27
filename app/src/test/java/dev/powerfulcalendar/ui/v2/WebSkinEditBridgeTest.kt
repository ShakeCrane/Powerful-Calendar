package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class WebSkinEditBridgeTest {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var service: WebSkinEditService
    private fun request()=UUID.randomUUID().toString()
    @Before fun setup(){
        context=ApplicationProvider.getApplicationContext()
        store=PowerStore(context,"web-edit-"+request()+".db")
        service=WebSkinEditService(store,DeckController(StoreDeckPersistence(store)))
    }
    @After fun teardown(){
        val db=store.databaseName
        store.close();context.deleteDatabase(db)
    }
    private fun wire(name:String,extra:String=""):String {
        val payload=JSONObject().put("name",name).put("requestId",request())
        if(extra.isNotEmpty()) {
            val extraJson=JSONObject("{"+extra.removePrefix(",")+"}")
            val keys=extraJson.keys()
            while(keys.hasNext()) {
                val key=keys.next()
                payload.put(key,extraJson.get(key))
            }
        }
        return JSONObject().put("version",1).put("type","command")
            .put("payload",payload).toString()
    }

    @Test fun closedSchemaRejectsMetadataOverwritesAndUntrustedInput(){
        assertNotNull(WebEditWire.decode(wire("editor.open", ""","eventSeq":42,"expectedRevision":1""")))
        assertNull(WebEditWire.decode(wire("editor.open", ""","eventSeq":"42","expectedRevision":1""")))
        assertNull(WebEditWire.decode(wire("editor.open", ""","eventSeq":42.5,"expectedRevision":1""")))
        assertNull(WebEditWire.decode(wire("editor.open", ""","eventSeq":42,"expectedRevision":1,"force":true""")))
        val sid=request()
        assertNull(WebEditWire.decode(wire("editor.patch", ""","sessionId":""""+sid+
            """","field":"source","value":"hacked"""")))
        assertNull(WebEditWire.decode(wire("deck.create", ""","kind":"UNSUPPORTED"""")))
        assertNull(WebEditWire.decode(wire("deck.commit", ""","draftId":1,"draftVersion":-1""")))
        assertNull(WebEditWire.decode(wire("deck.discard", ""","draftId":1,"draftVersion":0,"approve":true""")))
        assertNotNull(WebEditWire.decode(wire("deck.open", ""","draftId":null""")))
        assertNull(WebEditWire.decode(wire("deck.patch", ""","draftId":1,"draftVersion":0,"field":"recurrence","value":"RAW"""")))
        assertNull(WebEditWire.decode(wire("editor.save", ""","sessionId":""""+sid+
            """","actor":"ai"""")))
        assertNull(WebEditWire.decode(wire("editor.save", ""","sessionId":"bad"""")))
        assertNull(WebEditWire.decode(wire("deck.flush", ""","unexpected":false""")))
        assertNull(WebEditWire.decode(wire("deck.confirmConflict")))
        assertNull(WebEditWire.decode(wire("deck.confirmRestore")))
        assertNull(WebEditWire.decode(wire("editor.delete")))

        assertNull(WebEditWire.decode(wire("deck.create").replace("\"command\"","\"other\"")))
        assertNull(WebEditWire.decode("x".repeat(8193)))
    }

    @Test fun semanticFieldsPreserveProtectedMetadataAndRejectOutOfRangeValues() {
        val original=DraftForm(kind=TemporalKind.ACTIVITY,title="Original",
            source="imported-ics",zoneId="Asia/Shanghai")
        val reminded=WebEditWire.patch(original,"reminderMinutes","15")
        assertEquals(15,reminded.reminderMinutes)
        val timed=WebEditWire.patch(reminded,"reminderTiming","DAY_START")
        assertEquals("DAY_START",timed.reminderTiming.name)
        val daily=WebEditWire.patch(timed,"recurrenceFreq","DAILY")
        val interval=WebEditWire.patch(daily,"recurrenceInterval","2")
        val from=WebEditWire.patch(interval,"recurrenceFrom","2026-10-22")
        assertEquals("DAILY",from.recurrence.freq.name)
        assertEquals(2,from.recurrence.interval)
        assertEquals("2026-10-22",from.recurrence.activeFromDate)
        assertEquals("imported-ics",from.source)
        assertEquals("Asia/Shanghai",from.zoneId)
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original,"reminderMinutes","10081")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original,"recurrenceInterval","0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original,"enabled","yes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original,"source","forged")
        }
        assertNull(WebEditWire.decode(wire("deck.patch",
            ""","draftId":42,"draftVersion":1,"field":"zoneId","value":"Etc/UTC"""")))
        assertNotNull(WebEditWire.decode(wire("deck.patch",
            ""","draftId":42,"draftVersion":1,"field":"recurrenceFreq","value":"DAILY"""")))
    }

    @Test fun officialEditorSavesExactlyOnceAndKeepsLeasePrivate()= runBlocking {
        val card=store.newDraft(DraftForm(kind=TemporalKind.MEMO,title="original"))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val original=requireNotNull(store.get(card.eventSeq))
        assertEquals("opened",service.execute(WebEditCommand.OpenEvent(
            request(),card.eventSeq,original.revision)).getString("status"))
        val state=service.snapshot().getJSONObject("editor")
        val sid=state.getString("sessionId")
        assertFalse(state.toString().contains("lease"))
        assertEquals("locked",service.execute(WebEditCommand.OpenEvent(
            request(),card.eventSeq,original.revision)).getString("status"))
        assertEquals("edited",service.execute(WebEditCommand.PatchEvent(
            request(),sid,"title","Updated")).getString("status"))
        val cmd=WebEditCommand.SaveEvent(request(),sid)
        assertEquals("confirmation_required",service.execute(cmd).getString("status"))
        assertEquals(original,store.get(card.eventSeq))
        assertEquals("saved",service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token,true).getString("status"))
        assertEquals("saved",service.execute(cmd).getString("status"))
        assertEquals("duplicate_request_id",service.execute(
            WebEditCommand.CloseEvent(cmd.requestId,sid)).getString("status"))
        assertEquals(original.revision+1,store.get(card.eventSeq)!!.revision)
        assertEquals("Updated",store.get(card.eventSeq)!!.title)
        assertEquals("closed",service.execute(WebEditCommand.CloseEvent(request(),sid))
            .getString("status"))
        assertFalse(service.snapshot().getJSONObject("editor").getBoolean("open"))
    }

    @Test fun deckUsesOriginalControllerForDurableStageCommitAndReplay()= runBlocking {
        val identitiesBefore=store.identityCount()
        val draftsBefore=store.draftCount()
        val created=service.execute(WebEditCommand.CreateDeck(request(),TemporalKind.MEMO))
        assertEquals("created",created.getString("status"))
        val id=created.getString("detail").toLong()
        val state=service.snapshot().getJSONObject("deck")
        val version=state.getLong("version")
        assertEquals(JSONObject.NULL, state.getJSONArray("cards").getJSONObject(0).get("eventSeq"))
        assertEquals(draftsBefore,store.draftCount())
        assertEquals(identitiesBefore,store.identityCount())
        assertEquals("stale",service.execute(WebEditCommand.PatchDeck(
            request(),id+99,version,"title","wrong card")).getString("status"))
        assertEquals("edited",service.execute(WebEditCommand.PatchDeck(
            request(),id,version,"title","Memo from Web")).getString("status"))
        assertEquals(draftsBefore,store.draftCount())
        assertEquals(identitiesBefore,store.identityCount())
        assertEquals(0,store.pendingCount())
        assertEquals("flushed",service.execute(WebEditCommand.FlushDeck(request()))
            .getString("status"))
        val persisted=store.pendingDrafts().single()
        assertEquals("Memo from Web",persisted.form.title)
        val savedVersion=service.snapshot().getJSONObject("deck").getLong("version")
        val cmd=WebEditCommand.CommitDeck(request(),id,savedVersion)
        assertEquals("confirmation_required",service.execute(cmd).getString("status"))
        assertTrue(store.get(persisted.eventSeq)==null)
        assertEquals("committed",service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token,true).getString("status"))
        assertEquals("committed",service.execute(cmd).getString("status"))
        assertEquals("Memo from Web",store.get(persisted.eventSeq)!!.title)
        assertEquals(1,store.draftCount())
        assertEquals("stale",service.execute(WebEditCommand.DiscardDeck(
            request(),id,savedVersion)).getString("status"))
        assertEquals("closed",service.execute(WebEditCommand.CloseDeck(request()))
            .getString("status"))
    }

    @Test fun switchingSkinsDurablyFlushesCurrentDraftBeforeEndingWebSession()= runBlocking {
        val created=service.execute(WebEditCommand.CreateDeck(request(),TemporalKind.MEMO))
        val id=created.getString("detail").toLong()
        val version=service.snapshot().getJSONObject("deck").getLong("version")
        service.execute(WebEditCommand.PatchDeck(request(),id,version,
            "title","Preserved before switch"))
        assertTrue(service.hasLiveEditor())
        assertTrue(service.prepareSkinChange())
        assertFalse(service.hasLiveEditor())
        val persisted=store.pendingDrafts().single()
        assertEquals("Preserved before switch",persisted.form.title)
        assertEquals("opened",service.execute(WebEditCommand.OpenDeck(request(),persisted.draftId))
            .getString("status"))
        assertEquals("Preserved before switch",
            service.snapshot().getJSONObject("deck").getJSONObject("form").getString("title"))
    }

    @Test fun secondDeckOpenDoesNotOverwriteUnsavedForm()= runBlocking {
        val created=service.execute(WebEditCommand.CreateDeck(request(),TemporalKind.MEMO))
        val id=created.getString("detail").toLong()
        val version=service.snapshot().getJSONObject("deck").getLong("version")
        service.execute(WebEditCommand.PatchDeck(request(),id,version,
            "title","Unflushed in-memory input"))
        assertEquals("opened",service.execute(WebEditCommand.OpenDeck(request(),null))
            .getString("status"))
        assertEquals("Unflushed in-memory input",
            service.snapshot().getJSONObject("deck").getJSONObject("form").getString("title"))
        assertEquals("closed",service.execute(WebEditCommand.CloseDeck(request()))
            .getString("status"))
        assertEquals("Unflushed in-memory input",store.pendingDrafts().single().form.title)
    }

    @Test fun openingAndClosingFourKindsTwentyTimesDoesNotAllocateAnything()= runBlocking {
        val identities=store.identityCount()
        val nextSequence=store.nextSequencePreview()
        val commits=store.commitCount()
        val drafts=store.draftCount()
        repeat(20) {
            TemporalKind.entries.forEach { kind ->
                val opened=service.execute(WebEditCommand.CreateDeck(request(),kind))
                assertEquals("created",opened.getString("status"))
                val id=opened.getString("detail").toLong()
                assertTrue(id>0)
                assertEquals(0,service.snapshot().getJSONObject("deck").getJSONObject("form")
                    .getString("title").length)
                assertEquals("closed",service.execute(WebEditCommand.CloseDeck(request()))
                    .getString("status"))
                assertEquals("closed",service.execute(WebEditCommand.CloseDeck(request()))
                    .getString("status"))
            }
        }
        assertEquals(identities,store.identityCount())
        assertEquals(nextSequence,store.nextSequencePreview())
        assertEquals(commits,store.commitCount())
        assertEquals(drafts,store.draftCount())
        assertEquals(0,store.pendingCount())
        assertTrue(store.all().isEmpty())
    }

    @Test fun explicitWebPublishWritesFormalEventWithoutAQueuedDraft()= runBlocking {
        val created=service.execute(WebEditCommand.CreateDeck(request(),TemporalKind.ALARM))
        val id=created.getString("detail").toLong()
        val version=service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("edited",service.execute(WebEditCommand.PatchDeck(request(),id,version,
            "title","Published directly")).getString("status"))
        val command=WebEditCommand.CommitDeck(request(),id,version)
        assertEquals("confirmation_required",service.execute(command).getString("status"))
        assertTrue(store.all().isEmpty())
        assertEquals("committed",service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token,true).getString("status"))
        assertEquals("committed",service.execute(command).getString("status"))
        assertEquals(1,store.all().size)
        assertEquals("Published directly",store.all().single().title)
        assertEquals(0,store.draftCount())
        assertEquals(0,store.pendingCount())
        assertEquals(1,store.identityCount())
        assertEquals(1,store.commitCount())
    }

    @Test fun incompleteWebPublishStagesOneRecoverableDraft()= runBlocking {
        val created=service.execute(WebEditCommand.CreateDeck(request(),TemporalKind.MEMO))
        val id=created.getString("detail").toLong()
        val version=service.snapshot().getJSONObject("deck").getLong("version")
        service.execute(WebEditCommand.PatchDeck(request(),id,version,"notes","unfinished"))
        val result=service.execute(WebEditCommand.CommitDeck(request(),id,version))
        assertEquals("staged",result.getString("status"))
        assertEquals(id.toString(),result.getString("detail"))
        assertEquals(1,store.pendingCount())
        assertEquals(1,store.draftCount())
        assertEquals(0,store.commitCount())
        assertEquals("unfinished",store.pendingDrafts().single().form.notes)

        val currentVersion=service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("edited",service.execute(WebEditCommand.PatchDeck(
            request(),id,currentVersion,"title","completed later")).getString("status"))
        val publish=WebEditCommand.CommitDeck(request(),id,currentVersion)
        assertEquals("confirmation_required",service.execute(publish).getString("status"))
        assertEquals("committed",service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token,true).getString("status"))
        assertEquals("completed later",store.all().single().title)
        assertEquals(0,store.pendingCount())
        assertEquals(1,store.draftCount())
        assertEquals(1,store.commitCount())
    }

    @Test fun checkpointPersistsWebChangesAcrossClose()= runBlocking {
        val card=store.newDraft(DraftForm(kind=TemporalKind.MEMO,title="baseline"))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val rec=requireNotNull(store.get(card.eventSeq))
        service.execute(WebEditCommand.OpenEvent(request(),card.eventSeq,rec.revision))
        val sid=service.snapshot().getJSONObject("editor").getString("sessionId")
        service.execute(WebEditCommand.PatchEvent(request(),sid,"notes","checkpoint"))
        assertTrue(service.checkpoint())
        assertNotNull(store.pendingDraftsFor(card.eventSeq).firstOrNull())
        assertEquals("closed",service.execute(WebEditCommand.CloseEvent(request(),sid))
            .getString("status"))
        assertEquals("stale",service.execute(WebEditCommand.OpenEvent(
            request(),card.eventSeq,rec.revision+10)).getString("status"))
    }
}
