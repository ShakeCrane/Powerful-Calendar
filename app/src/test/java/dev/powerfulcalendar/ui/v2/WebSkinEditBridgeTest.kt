package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.NewEventEditSession
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TimelineCoordinate
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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

    @Test fun documentedEditCommandPayloadExamplesMatchTheHostDecoder() {
        val payloadSchemas = WebSkinBridgeContractFixture.load()
            .getJSONObject("\$defs").getJSONObject("commandPayloads")
        val names = payloadSchemas.keys()
        var commandCount = 0
        while (names.hasNext()) {
            val name = names.next()
            if ('.' !in name) continue
            commandCount++
            val schema = payloadSchemas.getJSONObject(name)
            val requiredArray = schema.getJSONArray("required")
            val required = (0 until requiredArray.length()).map { requiredArray.getString(it) }
            val properties = schema.getJSONObject("properties")
            val examples = schema.getJSONArray("examples")
            for (index in 0 until examples.length()) {
                val payload = examples.getJSONObject(index)
                val payloadKeys = payload.keys().asSequence().toSet()
                assertTrue("$name example has all declared required fields",
                    required.all(payloadKeys::contains))
                assertTrue("$name example contains only declared properties",
                    payloadKeys.all(properties::has))
                val wire = JSONObject().put("version", 1).put("type", "command")
                    .put("payload", payload)
                assertTrue("JSON Schema example for $name must reach WebEditWire",
                    WebEditWire.decode(wire.toString()) != null)

                for (field in required) {
                    val missing = JSONObject(wire.toString())
                    missing.getJSONObject("payload").remove(field)
                    assertNull("$name must reject missing required field $field",
                        WebEditWire.decode(missing.toString()))
                }
                val extra = JSONObject(wire.toString())
                extra.getJSONObject("payload").put("untrustedExtra", true)
                assertNull("$name must reject fields outside its schema",
                    WebEditWire.decode(extra.toString()))
            }
        }
        assertEquals("Every edit command definition must be exercised", 18, commandCount)
    }

    @Test fun documentedPatchFieldExamplesMatchSemanticHostValidation() {
        val definitions = WebSkinBridgeContractFixture.load()
            .getJSONObject("\$defs").getJSONObject("commandPayloads")
        val patchFields = definitions.getJSONObject("patchField").getJSONArray("enum")
        val branches = definitions.getJSONObject("patchValueLengthByField")
            .getJSONArray("allOf")
        val rules = mutableMapOf<String, JSONObject>()
        for (index in 0 until branches.length()) {
            val branch = branches.getJSONObject(index)
            val field = branch.getJSONObject("if").getJSONObject("properties")
                .getJSONObject("field").getString("const")
            assertNull("Patch field must have one schema rule: $field", rules.put(field, branch))
        }
        assertEquals((0 until patchFields.length()).map { patchFields.getString(it) }.toSet(),
            rules.keys)

        val wireValueSchema = definitions.getJSONObject("editor.patch")
            .getJSONObject("properties").getJSONObject("value")
        val noNul = java.util.regex.Pattern.compile(wireValueSchema.getString("pattern"))
        assertTrue(noNul.matcher("ordinary text").matches())
        assertFalse(noNul.matcher("text\u0000with NUL").matches())

        val original = DraftForm(kind = TemporalKind.ACTIVITY, zoneId = "UTC")
        for ((field, branch) in rules) {
            val valueSchema = branch.getJSONObject("then").getJSONObject("properties")
                .getJSONObject("value")
            val runtimeMax = valueSchema.getInt("x-runtimeMaxUtf16CodeUnits")
            val schemaMax = valueSchema.optInt("maxLength", -1)
            if (schemaMax >= 0) {
                assertEquals("Schema's standard and host length bounds must agree for $field",
                    schemaMax, runtimeMax)
            } else {
                val enum = valueSchema.optJSONArray("enum")
                assertNotNull("A field without maxLength must have a bounded enum: $field", enum)
                assertTrue("Enum values must fit the Host length bound for $field",
                    (0 until enum!!.length()).all { enum.getString(it).length <= runtimeMax })
            }
            val values = valueSchema.getJSONArray("examples")
            assertTrue("Patch field $field must have a valid example", values.length() > 0)
            for (index in 0 until values.length()) {
                val value = values.getString(index)
                valueSchema.optString("pattern", "").takeIf(String::isNotEmpty)?.let {
                    assertTrue("Schema pattern for $field accepts its example $value",
                        java.util.regex.Pattern.compile(it).matcher(value).matches())
                }
                valueSchema.optJSONArray("enum")?.let { enumValues ->
                    assertTrue("Schema enum for $field contains $value",
                        (0 until enumValues.length()).any { enumValues.getString(it) == value })
                }

                val payload = JSONObject().put("name", "editor.patch")
                    .put("requestId", "req_12345678")
                    .put("sessionId", "00000000-0000-0000-0000-000000000001")
                    .put("field", field).put("value", value)
                val wire = JSONObject().put("version", 1).put("type", "command")
                    .put("payload", payload)
                assertNotNull("Documented $field patch reaches the Host decoder",
                    WebEditWire.decode(wire.toString()))
                assertNotNull("Documented $field value passes Host patch validation",
                    WebEditWire.patch(original, field, value))
            }
        }

        val reminderPattern = java.util.regex.Pattern.compile(
            rules.getValue("reminderMinutes").getJSONObject("then")
                .getJSONObject("properties").getJSONObject("value").getString("pattern"))
        assertTrue(reminderPattern.matcher("10080").matches())
        assertTrue(reminderPattern.matcher("-1").matches())
        assertFalse(reminderPattern.matcher("10081").matches())
        assertFalse(reminderPattern.matcher("-2").matches())
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original, "reminderMinutes", "10081")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original, "reminderMinutes", "-2")
        }

        val intervalPattern = java.util.regex.Pattern.compile(
            rules.getValue("recurrenceInterval").getJSONObject("then")
                .getJSONObject("properties").getJSONObject("value").getString("pattern"))
        assertTrue(intervalPattern.matcher("1").matches())
        assertTrue(intervalPattern.matcher("365").matches())
        assertFalse(intervalPattern.matcher("0").matches())
        assertFalse(intervalPattern.matcher("366").matches())
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original, "recurrenceInterval", "366")
        }

        val nulPayload = JSONObject().put("name", "editor.patch")
            .put("requestId", "req_12345678")
            .put("sessionId", "00000000-0000-0000-0000-000000000001")
            .put("field", "title").put("value", "text\u0000with NUL")
        assertNull(WebEditWire.decode(JSONObject().put("version", 1).put("type", "command")
            .put("payload", nulPayload).toString()))
        val longPayload = JSONObject(nulPayload.toString()).put("value", "x".repeat(241))
        assertNull(WebEditWire.decode(JSONObject().put("version", 1).put("type", "command")
            .put("payload", longPayload).toString()))
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
        assertNotNull(WebEditWire.decode(wire("deck.reviewConflict")))
        assertNotNull(WebEditWire.decode(wire("deck.reviewRestore")))
        assertNull(WebEditWire.decode(wire("deck.reviewConflict", ",\"approve\":true")))
        assertNull(WebEditWire.decode(wire("deck.reviewRestore", ",\"approve\":true")))
        val deleteSession = request()
        assertNotNull(WebEditWire.decode(wire("editor.delete",
            ",\"sessionId\":\"$deleteSession\"")))
        assertNull(WebEditWire.decode(wire("editor.delete",
            ",\"sessionId\":\"$deleteSession\",\"force\":true")))

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

    @Test fun kindPatchUsesCoreConversionForBothEditorAndDeckCommands() {
        val original = DraftForm(kind = TemporalKind.ALARM, title = "Alarm",
            startText = "2026-10-22 07:30", reminderMinutes = -1,
            source = "imported-ics", zoneId = "Asia/Shanghai")
        TemporalKind.entries.forEach { target ->
            assertEquals(TemporalConversion.convert(original, target).form,
                WebEditWire.patch(original, "kind", target.name))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebEditWire.patch(original, "kind", "UNSUPPORTED")
        }
        val editorPayload = JSONObject().put("version", 1).put("type", "command")
            .put("payload", JSONObject().put("name", "editor.patch").put("requestId", request())
                .put("sessionId", request()).put("field", "kind").put("value", "ACTIVITY"))
        val deckPayload = JSONObject().put("version", 1).put("type", "command")
            .put("payload", JSONObject().put("name", "deck.patch").put("requestId", request())
                .put("draftId", 42).put("draftVersion", 1).put("field", "kind")
                .put("value", "DEADLINE"))
        assertNotNull(WebEditWire.decode(editorPayload.toString()))
        assertNotNull(WebEditWire.decode(deckPayload.toString()))
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

    @Test fun formalDeleteRequiresHostReviewAndIsIdempotent() = runBlocking {
        val card = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "Delete me"))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(card.eventSeq))
        val pending = store.newDraft(DraftForm.from(original).copy(title = "Pending edit"),
            original.eventSeq, DraftOperation.REPLACE)
        val commitsBeforeDelete = store.commitCount()
        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            request(), original.eventSeq, original.revision)).getString("status"))
        val sessionId = service.snapshot().getJSONObject("editor").getString("sessionId")
        val decoded = WebEditWire.decode(wire("editor.delete",
            ",\"sessionId\":\"$sessionId\""))
        assertTrue(decoded is WebEditCommand.DeleteEvent)
        val cancelCommand = decoded as WebEditCommand.DeleteEvent

        assertEquals("confirmation_required", service.execute(cancelCommand).getString("status"))
        val cancelReview = requireNotNull(service.pendingWriteReview)
        assertEquals(DraftOperation.DELETE, cancelReview.operation)
        assertEquals(original, cancelReview.current)
        assertEquals(original, store.get(original.eventSeq))
        assertEquals(pending, store.draft(pending.draftId))
        assertEquals("cancelled", service.resolveFormalWriteReview(
            cancelReview.token, approved = false).getString("status"))
        assertEquals(original, store.get(original.eventSeq))
        assertEquals(pending, store.draft(pending.draftId))
        assertTrue(service.snapshot().getJSONObject("editor").getBoolean("open"))
        assertEquals("cancelled", service.execute(cancelCommand).getString("status"))

        val deleteCommand = WebEditCommand.DeleteEvent(request(), sessionId)
        assertEquals("confirmation_required", service.execute(deleteCommand).getString("status"))
        val deleteReview = requireNotNull(service.pendingWriteReview)
        assertEquals("saved", service.resolveFormalWriteReview(
            deleteReview.token, approved = true).getString("status"))
        assertNull(store.get(original.eventSeq))
        assertEquals(DraftStatus.DISCARDED, store.draft(pending.draftId)?.status)
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())
        assertTrue(store.isRetired(original.eventSeq))
        assertEquals(commitsBeforeDelete + 1, store.commitCount())
        assertFalse(service.snapshot().getJSONObject("editor").getBoolean("open"))
        assertEquals("saved", service.execute(deleteCommand).getString("status"))
        assertNull(store.get(original.eventSeq))
        assertEquals(commitsBeforeDelete + 1, store.commitCount())
    }

    @Test fun formalDeleteReviewRejectsChangedRevision() = runBlocking {
        val card = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "Concurrent edit"))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(card.eventSeq))
        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            request(), original.eventSeq, original.revision)).getString("status"))
        val sessionId = service.snapshot().getJSONObject("editor").getString("sessionId")
        val command = WebEditCommand.DeleteEvent(request(), sessionId)

        assertEquals("confirmation_required", service.execute(command).getString("status"))
        val review = requireNotNull(service.pendingWriteReview)
        assertTrue(store.setCompleted(original.eventSeq, "single", true, original.revision))
        val result = service.resolveFormalWriteReview(review.token, approved = true)

        assertEquals("conflict", result.getString("status"))
        val latest = requireNotNull(store.get(original.eventSeq))
        assertEquals(original.revision + 1, latest.revision)
        assertEquals(setOf("single"), latest.completedKeys)
        assertFalse(store.isRetired(original.eventSeq))
        assertTrue(service.snapshot().getJSONObject("editor").getBoolean("open"))
        assertEquals("conflict", service.execute(command).getString("status"))
    }

    @Test fun formalEditorKindConversionRequiresHostReviewAndKeepsIdentity() = runBlocking {
        val card = store.newDraft(DraftForm(kind = TemporalKind.ALARM, title = "Wake up",
            startText = "2026-10-22 07:30", reminderMinutes = -1, zoneId = "UTC"))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(card.eventSeq))
        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            request(), card.eventSeq, before.revision)).getString("status"))
        val sessionId = service.snapshot().getJSONObject("editor").getString("sessionId")

        assertEquals("edited", service.execute(WebEditCommand.PatchEvent(
            request(), sessionId, "kind", "DEADLINE")).getString("status"))
        val proposed = service.snapshot().getJSONObject("editor").getJSONObject("form")
        assertEquals("DEADLINE", proposed.getString("kind"))
        assertEquals("", proposed.getString("startText"))
        assertEquals("2026-10-22 07:30", proposed.getString("endText"))

        val save = WebEditCommand.SaveEvent(request(), sessionId)
        assertEquals("confirmation_required", service.execute(save).getString("status"))
        assertEquals(DraftOperation.CONVERT, service.pendingWriteReview?.operation)
        assertEquals(before, store.get(card.eventSeq))
        assertEquals("saved", service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token, true).getString("status"))

        val after = requireNotNull(store.get(card.eventSeq))
        assertEquals(before.eventSeq, after.eventSeq)
        assertEquals(before.revision + 1, after.revision)
        assertEquals(TemporalKind.DEADLINE, after.kind)
        assertEquals("2026-10-22 07:30", DraftForm.formatInstant(
            requireNotNull(after.deadlineMillis), after.zone))
        assertEquals(2, store.commitCount())
        assertTrue(store.pendingDraftsFor(card.eventSeq).isEmpty())
    }

    @Test fun deckKindPatchStagesConvertedFormThroughExistingController() = runBlocking {
        val card = store.newDraft(DraftForm(kind = TemporalKind.ALARM, title = "Wake up",
            startText = "2026-10-22 07:30", reminderMinutes = -1, zoneId = "UTC",
            source = "human"))
        val identities = store.identityCount()
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(
            request(), card.draftId)).getString("status"))
        val version = service.snapshot().getJSONObject("deck").getLong("version")

        assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
            request(), card.draftId, version, "kind", "DEADLINE")).getString("status"))
        assertEquals("DEADLINE", service.snapshot().getJSONObject("deck")
            .getJSONObject("form").getString("kind"))
        assertEquals("flushed", service.execute(WebEditCommand.FlushDeck(request()))
            .getString("status"))

        val persisted = requireNotNull(store.draft(card.draftId))
        assertEquals(card.eventSeq, persisted.eventSeq)
        assertEquals(TemporalKind.DEADLINE, persisted.form.kind)
        assertEquals("2026-10-22 07:30", persisted.form.endText)
        assertEquals("human", persisted.form.source)
        assertEquals(identities, store.identityCount())
        assertEquals(0, store.commitCount())
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

    @Test fun webConflictResolutionRequiresFreshHostReviewAndKeepsTheDraft() = runBlocking {
        val zone=ZoneId.of("UTC")
        val start=System.currentTimeMillis()+3_600_000L
        val originalDraft=store.newDraft(DraftForm(kind=TemporalKind.ACTIVITY,
            title="当前正式内容",startText=DraftForm.formatInstant(start,zone),
            endText=DraftForm.formatInstant(start+3_600_000L,zone),
            trackingEnabled=true,zoneId=zone.id))
        assertTrue(store.commit(originalDraft.draftId) is PowerStore.CommitOutcome.Committed)
        val original=requireNotNull(store.get(originalDraft.eventSeq))
        val proposal=store.newDraft(DraftForm.from(original).copy(title="草稿完整快照"),
            original.eventSeq,DraftOperation.REPLACE)

        assertTrue(store.setCompleted(original.eventSeq,"single",true,original.revision))
        val firstConcurrent=requireNotNull(store.get(original.eventSeq))
        assertEquals("opened",service.execute(WebEditCommand.OpenDeck(
            request(),proposal.draftId)).getString("status"))
        var version=service.snapshot().getJSONObject("deck").getLong("version")
        val publish=WebEditCommand.CommitDeck(request(),proposal.draftId,version)
        assertEquals("confirmation_required",service.execute(publish).getString("status"))
        assertEquals("conflict",service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token,true).getString("status"))

        val blocked=service.snapshot().getJSONObject("deck")
        assertTrue(blocked.getBoolean("hasConflict"))
        assertEquals(firstConcurrent,store.get(original.eventSeq))
        assertEquals(DraftStatus.CONFLICT,store.draft(proposal.draftId)?.status)

        val reviewCommand=requireNotNull(WebEditWire.decode(wire("deck.reviewConflict")))
        assertEquals("confirmation_required",service.execute(reviewCommand).getString("status"))
        val firstReview=service.pendingDeckResolutionReview as WebDeckResolutionReview.Conflict
        assertEquals(firstConcurrent,firstReview.prompt.current)
        assertEquals(proposal.form,firstReview.prompt.approvedForm)
        assertEquals("locked",service.execute(WebEditCommand.PatchDeck(
            request(),proposal.draftId,version,"title","不能绕过宿主确认")).getString("status"))

        // A concurrent change after the dialog opens invalidates the exact snapshot shown.
        assertTrue(store.setCompleted(original.eventSeq,"single",false,firstConcurrent.revision))
        val beforeStaleApproval=store.commitCount()
        assertEquals("conflict",service.resolveDeckResolutionReview(
            firstReview.token,true).getString("status"))
        assertEquals(beforeStaleApproval,store.commitCount())
        assertTrue(service.snapshot().getJSONObject("deck").getBoolean("hasConflict"))

        val freshReviewCommand=requireNotNull(WebEditWire.decode(wire("deck.reviewConflict")))
        assertEquals("confirmation_required",service.execute(freshReviewCommand).getString("status"))
        val freshReview=service.pendingDeckResolutionReview as WebDeckResolutionReview.Conflict
        assertEquals(requireNotNull(store.get(original.eventSeq)).revision,
            freshReview.prompt.currentRevision)
        assertEquals("committed",service.resolveDeckResolutionReview(
            freshReview.token,true).getString("status"))
        assertEquals("草稿完整快照",store.get(original.eventSeq)?.title)
        assertEquals(0,store.pendingCount())
        assertEquals(1,store.commitCount()-beforeStaleApproval)
        assertFalse(service.snapshot().getJSONObject("deck").getBoolean("hasConflict"))
        assertEquals("committed",service.execute(freshReviewCommand).getString("status"))
        assertEquals(1,store.commitCount()-beforeStaleApproval)
    }

    @Test fun webDeletedEventRestoreRequiresHostConfirmationAndPreservesIdentity() = runBlocking {
        val created=store.newDraft(DraftForm(kind=TemporalKind.MEMO,title="恢复目标"))
        assertTrue(store.commit(created.draftId) is PowerStore.CommitOutcome.Committed)
        val original=requireNotNull(store.get(created.eventSeq))
        val delete=store.newDraft(DraftForm.from(original),original.eventSeq,DraftOperation.DELETE)
        assertTrue(store.commit(delete.draftId) is PowerStore.CommitOutcome.Committed)
        assertNull(store.get(original.eventSeq))
        val restore=store.proposeRestoreDraft(original.eventSeq)
        val identities=store.identityCount()

        assertEquals("opened",service.execute(WebEditCommand.OpenDeck(
            request(),restore.draftId)).getString("status"))
        var version=service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("restore_required",service.execute(WebEditCommand.CommitDeck(
            request(),restore.draftId,version)).getString("status"))
        assertTrue(service.snapshot().getJSONObject("deck").getBoolean("hasRestore"))

        val firstRequest=requireNotNull(WebEditWire.decode(wire("deck.reviewRestore")))
        assertEquals("confirmation_required",service.execute(firstRequest).getString("status"))
        val firstReview=service.pendingDeckResolutionReview as WebDeckResolutionReview.Restore
        assertEquals(original.eventSeq,firstReview.prompt.eventSeq)
        val commitsBeforeCancel=store.commitCount()
        assertEquals("cancelled",service.resolveDeckResolutionReview(
            firstReview.token,false).getString("status"))
        assertNull(store.get(original.eventSeq))
        assertEquals(commitsBeforeCancel,store.commitCount())
        assertEquals(restore,store.draft(restore.draftId))

        version=service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("restore_required",service.execute(WebEditCommand.CommitDeck(
            request(),restore.draftId,version)).getString("status"))
        val secondRequest=requireNotNull(WebEditWire.decode(wire("deck.reviewRestore")))
        assertEquals("confirmation_required",service.execute(secondRequest).getString("status"))
        val secondReview=service.pendingDeckResolutionReview as WebDeckResolutionReview.Restore
        assertEquals("restored",service.resolveDeckResolutionReview(
            secondReview.token,true).getString("status"))
        val recovered=requireNotNull(store.get(original.eventSeq))
        assertEquals(original.eventSeq,recovered.eventSeq)
        assertEquals(original.revision+2,recovered.revision)
        assertEquals(original.title,recovered.title)
        assertEquals(identities,store.identityCount())
        assertEquals(commitsBeforeCancel+1,store.commitCount())
        assertEquals(0,store.pendingCount())
        assertEquals("restored",service.execute(secondRequest).getString("status"))
        assertEquals(commitsBeforeCancel+1,store.commitCount())
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

    @Test fun persistedDraftDeckDiscardUndoAndPublishMatchNativeAcrossReopen() = runBlocking {
        val nativeDatabase = "native-skin-deck-replay-" + UUID.randomUUID() + ".db"
        val skinDatabase = store.databaseName
        var nativeStore = PowerStore(context, nativeDatabase)
        var now = 10_000L
        fun controller(target: PowerStore) = DeckController(
            StoreDeckPersistence(target), clock = { now }
        )
        fun normalizedDrafts(target: PowerStore) = target.pendingDrafts()
            .sortedBy { it.queueOrder }
            .map { it.copy(createdAtMillis = 0L, updatedAtMillis = 0L) }
        fun normalizedRecords(target: PowerStore) = target.all()
            .sortedBy { it.eventSeq }
            .map { it.copy(createdAtMillis = 0L, updatedAtMillis = 0L) }
        fun counts(target: PowerStore) = listOf(
            target.draftCount(), target.pendingCount().toLong(), target.all().size.toLong(),
            target.identityCount(), target.nextSequencePreview(), target.nextQueueOrderPreview(),
            target.commitCount()
        )

        val nativeDeck = controller(nativeStore)
        val skinDeck = controller(store)
        val skinService = WebSkinEditService(
            store, skinDeck,
            clock = Clock.fixed(Instant.parse("2026-10-28T15:07:42Z"), ZoneOffset.UTC)
        )
        fun assertDeckParity(activeNative: DeckController, activeSkin: WebSkinEditService) {
            val projection = activeSkin.snapshot().getJSONObject("deck")
            val skinCardIds = projection.getJSONArray("cards").let { cards ->
                (0 until cards.length()).map { cards.getJSONObject(it).getLong("draftId") }
            }
            assertEquals(activeNative.state.cards.map { it.draftId }, skinCardIds)
            val skinCurrentId = if (projection.isNull("currentId")) null
                else projection.getLong("currentId")
            assertEquals(activeNative.state.currentId, skinCurrentId)
            assertEquals(activeNative.state.version, projection.getLong("version"))
            assertEquals(activeNative.state.dirty, projection.getBoolean("dirty"))
            val skinLastDiscardId = if (projection.isNull("lastDiscardId")) null
                else projection.getLong("lastDiscardId")
            assertEquals(activeNative.state.lastDiscardId, skinLastDiscardId)
            if (activeNative.state.form == null) assertTrue(projection.isNull("form"))
            else assertEquals(WebEditWire.form(activeNative.state.form!!).toString(),
                projection.getJSONObject("form").toString())
        }

        try {
            val firstForm = DraftForm(kind = TemporalKind.MEMO, title = "First draft")
            val secondForm = DraftForm(kind = TemporalKind.MEMO, title = "Second draft")
            val nativeFirst = nativeStore.newDraft(firstForm)
            val skinFirst = store.newDraft(firstForm)
            val nativeSecond = nativeStore.newDraft(secondForm)
            val skinSecond = store.newDraft(secondForm)
            assertEquals(nativeFirst.draftId, skinFirst.draftId)
            assertEquals(nativeSecond.draftId, skinSecond.draftId)

            nativeDeck.open(nativeFirst.draftId)
            assertEquals("opened", skinService.execute(WebEditCommand.OpenDeck(
                request(), skinFirst.draftId
            )).getString("status"))
            assertDeckParity(nativeDeck, skinService)

            val firstEdited = firstForm.copy(title = "First latest text", notes = "flush on select")
            nativeDeck.edit(firstEdited)
            assertEquals("edited", skinService.execute(WebEditCommand.PatchDeck(
                request(), skinFirst.draftId, skinDeck.state.version, "title", firstEdited.title
            )).getString("status"))
            assertEquals("edited", skinService.execute(WebEditCommand.PatchDeck(
                request(), skinFirst.draftId, skinDeck.state.version, "notes", firstEdited.notes
            )).getString("status"))

            assertTrue(nativeDeck.select(nativeSecond.draftId))
            assertEquals("selected", skinService.execute(WebEditCommand.SelectDeck(
                request(), skinSecond.draftId
            )).getString("status"))
            assertDeckParity(nativeDeck, skinService)
            assertEquals(firstEdited, nativeStore.draft(nativeFirst.draftId)!!.form)
            assertEquals(firstEdited, store.draft(skinFirst.draftId)!!.form)

            val secondEdited = secondForm.copy(title = "Second durable text", notes = "survives reopen")
            nativeDeck.edit(secondEdited)
            assertEquals("edited", skinService.execute(WebEditCommand.PatchDeck(
                request(), skinSecond.draftId, skinDeck.state.version, "title", secondEdited.title
            )).getString("status"))
            assertEquals("edited", skinService.execute(WebEditCommand.PatchDeck(
                request(), skinSecond.draftId, skinDeck.state.version, "notes", secondEdited.notes
            )).getString("status"))
            assertTrue(nativeDeck.flush())
            assertEquals("flushed", skinService.execute(WebEditCommand.FlushDeck(request()))
                .getString("status"))
            assertDeckParity(nativeDeck, skinService)
            assertEquals(normalizedDrafts(nativeStore), normalizedDrafts(store))
            assertEquals("closed", skinService.execute(WebEditCommand.CloseDeck(request()))
                .getString("status"))

            // Close and reopen both SQLite files to exercise the durable recovery boundary.
            // This is not an Android process-death test.
            nativeStore.close()
            store.close()
            nativeStore = PowerStore(context, nativeDatabase)
            store = PowerStore(context, skinDatabase)
            service = WebSkinEditService(store, DeckController(StoreDeckPersistence(store)))
            val nativeAfterReopen = controller(nativeStore)
            val skinAfterReopen = WebSkinEditService(store, controller(store),
                clock = Clock.fixed(Instant.parse("2026-10-28T15:07:42Z"), ZoneOffset.UTC))
            nativeAfterReopen.open(nativeSecond.draftId)
            assertEquals("opened", skinAfterReopen.execute(WebEditCommand.OpenDeck(
                request(), skinSecond.draftId
            )).getString("status"))
            assertDeckParity(nativeAfterReopen, skinAfterReopen)
            assertEquals(secondEdited, nativeAfterReopen.state.form)
            assertEquals(secondEdited, store.draft(skinSecond.draftId)!!.form)

            val staleVersion = skinAfterReopen.snapshot().getJSONObject("deck").getLong("version") - 1L
            assertEquals("stale", skinAfterReopen.execute(WebEditCommand.PatchDeck(
                request(), skinSecond.draftId, staleVersion, "title", "stale overwrite"
            )).getString("status"))
            assertDeckParity(nativeAfterReopen, skinAfterReopen)

            assertTrue(nativeAfterReopen.discard())
            val discard = WebEditCommand.DiscardDeck(request(), skinSecond.draftId,
                skinAfterReopen.snapshot().getJSONObject("deck").getLong("version"))
            assertEquals("discarded", skinAfterReopen.execute(discard).getString("status"))
            assertDeckParity(nativeAfterReopen, skinAfterReopen)
            assertEquals(normalizedDrafts(nativeStore), normalizedDrafts(store))
            assertEquals(nativeFirst.draftId, nativeAfterReopen.state.currentId)
            assertEquals(nativeFirst.draftId, skinAfterReopen.snapshot().getJSONObject("deck")
                .getLong("currentId"))

            assertTrue(nativeAfterReopen.undoDiscard())
            assertEquals("restored", skinAfterReopen.execute(WebEditCommand.UndoDeck(request()))
                .getString("status"))
            assertDeckParity(nativeAfterReopen, skinAfterReopen)
            assertEquals(normalizedDrafts(nativeStore), normalizedDrafts(store))
            assertEquals(skinSecond.draftId, skinAfterReopen.snapshot().getJSONObject("deck")
                .getLong("currentId"))

            now = 20_000L
            assertTrue(nativeAfterReopen.commit())
            val commit = WebEditCommand.CommitDeck(request(), skinSecond.draftId,
                skinAfterReopen.snapshot().getJSONObject("deck").getLong("version"))
            assertEquals("confirmation_required", skinAfterReopen.execute(commit).getString("status"))
            val review = requireNotNull(skinAfterReopen.pendingWriteReview)
            assertNull(store.get(skinSecond.eventSeq))
            assertEquals("committed", skinAfterReopen.resolveFormalWriteReview(
                review.token, approved = true
            ).getString("status"))
            assertEquals("committed", skinAfterReopen.execute(commit).getString("status"))

            assertDeckParity(nativeAfterReopen, skinAfterReopen)
            assertEquals(normalizedDrafts(nativeStore), normalizedDrafts(store))
            assertEquals(normalizedRecords(nativeStore), normalizedRecords(store))
            assertEquals(counts(nativeStore), counts(store))
            assertEquals(secondEdited.title, nativeStore.get(nativeSecond.eventSeq)!!.title)
            assertEquals(secondEdited.title, store.get(skinSecond.eventSeq)!!.title)
        } finally {
            nativeStore.close()
            context.deleteDatabase(nativeDatabase)
        }
    }

    @Test fun nativeAndSkinNewEventReplayMatchesDefaultsAndDurableBusinessResults() = runBlocking {
        val nativeDatabase = "native-skin-replay-${UUID.randomUUID()}.db"
        val nativeStore = PowerStore(context, nativeDatabase)
        val clock = Clock.fixed(Instant.parse("2026-10-28T15:07:42Z"), ZoneOffset.UTC)
        val zone = ZoneId.of("America/Los_Angeles")
        val replayService = WebSkinEditService(
            store, DeckController(StoreDeckPersistence(store)), clock = clock
        )

        fun counts(target: PowerStore) = listOf(
            target.draftCount(), target.pendingCount().toLong(), target.all().size.toLong(),
            target.identityCount(), target.nextSequencePreview(),
            target.nextQueueOrderPreview(), target.commitCount()
        )
        fun normalizedRecords(target: PowerStore) = target.all()
            .sortedBy { it.eventSeq }
            .map { it.copy(createdAtMillis = 0L, updatedAtMillis = 0L) }
        fun normalizedDrafts(target: PowerStore) = target.pendingDrafts()
            .map { it.copy(createdAtMillis = 0L, updatedAtMillis = 0L) }

        try {
            val cleanCounts = counts(nativeStore)
            repeat(20) {
                TemporalKind.entries.forEach { kind ->
                    val native = NewEventEditSession.open(kind, clock, zone)
                    val opened = replayService.execute(
                        WebEditCommand.CreateDeck(request(), kind), newEventZone = zone
                    )
                    assertEquals("created", opened.getString("status"))
                    val skinForm = replayService.snapshot().getJSONObject("deck")
                        .getJSONObject("form")
                    assertEquals(WebEditWire.form(native.baseline).toString(), skinForm.toString())

                    assertNull(native.close(nativeStore))
                    val draftId = opened.getString("detail").toLong()
                    assertEquals("closed", replayService.execute(
                        WebEditCommand.CloseDeck(request())
                    ).getString("status"))
                    assertEquals("closed", replayService.execute(
                        WebEditCommand.CloseDeck(request())
                    ).getString("status"))
                    assertEquals(cleanCounts, counts(nativeStore))
                    assertEquals(cleanCounts, counts(store))
                    assertTrue(draftId > 0L)
                }
            }

            val explicitInstant = Instant.parse("2026-10-28T17:35:27Z").toEpochMilli()
            val explicitDate = java.time.LocalDate.of(2026, 10, 30)
            val cases = TemporalKind.entries.flatMap { kind ->
                listOf(kind to null, kind to when (kind) {
                    TemporalKind.MEMO -> EditorSelection.OnDate(explicitDate)
                    else -> EditorSelection.AtInstant(explicitInstant)
                })
            }

            cases.forEachIndexed { index, (kind, selection) ->
                val title = "Parity ${index + 1} ${kind.name}"
                val native = NewEventEditSession.open(kind, clock, zone, selection)
                val opened = replayService.execute(
                    WebEditCommand.CreateDeck(request(), kind),
                    newEventZone = zone,
                    newEventSelection = selection
                )
                assertEquals("created", opened.getString("status"))
                val draftId = opened.getString("detail").toLong()
                val skinForm = replayService.snapshot().getJSONObject("deck")
                    .getJSONObject("form")
                assertEquals("$kind defaults", WebEditWire.form(native.baseline).toString(),
                    skinForm.toString())

                native.edit(native.form.copy(title = title))
                assertTrue(native.save(nativeStore) is NewEventEditSession.SaveOutcome.Published)

                val patch = replayService.execute(WebEditCommand.PatchDeck(
                    request(), draftId, 0L, "title", title
                ))
                assertEquals("edited", patch.getString("status"))
                val version = replayService.snapshot().getJSONObject("deck").getLong("version")
                val pending = replayService.execute(WebEditCommand.CommitDeck(
                    request(), draftId, version
                ))
                assertEquals("confirmation_required", pending.getString("status"))
                val review = requireNotNull(replayService.pendingWriteReview)
                assertEquals("committed", replayService.resolveFormalWriteReview(
                    review.token, approved = true
                ).getString("status"))

                assertEquals(normalizedRecords(nativeStore), normalizedRecords(store))
                assertEquals(counts(nativeStore), counts(store))
                assertEquals(0L, store.draftCount())
                assertEquals(0, store.pendingCount())
                assertEquals(1L, store.nextQueueOrderPreview())
            }

            val incompleteNative = NewEventEditSession.open(
                TemporalKind.DEADLINE, clock, zone
            )
            incompleteNative.edit(incompleteNative.form.copy(notes = "incomplete but meaningful"))
            val nativeStaged = requireNotNull(incompleteNative.close(nativeStore))

            val incompleteSkin = replayService.execute(WebEditCommand.CreateDeck(
                request(), TemporalKind.DEADLINE
            ), newEventZone = zone)
            val incompleteId = incompleteSkin.getString("detail").toLong()
            assertEquals("edited", replayService.execute(WebEditCommand.PatchDeck(
                request(), incompleteId, 0L, "notes", "incomplete but meaningful"
            )).getString("status"))
            val skinClosed = replayService.execute(WebEditCommand.CloseDeck(request()))
            assertEquals("closed", skinClosed.getString("status"))

            assertEquals(nativeStaged.copy(createdAtMillis = 0L, updatedAtMillis = 0L),
                normalizedDrafts(store).single())
            assertEquals(normalizedRecords(nativeStore), normalizedRecords(store))
            assertEquals(counts(nativeStore), counts(store))
            assertEquals(1, store.pendingCount())
        } finally {
            nativeStore.close()
            context.deleteDatabase(nativeDatabase)
        }
    }

    @Test fun timelineCreateUsesSnappedAnchorAndConfiguredZoneForEveryKindWithoutWrites() = runBlocking {
        val zone = ZoneId.of("America/Los_Angeles")
        val anchor = Instant.parse("2026-10-22T16:08:00Z").toEpochMilli()
        val camera = TimelineCoordinate.Camera(anchor, 24.0)
        val expectedStart = TimelineCoordinate.snap(camera.anchorMillis, camera.hours, zone)
        val selection = newEventSelectionForPage("TIMELINE", camera, zone)
        assertEquals(EditorSelection.AtInstant(expectedStart), selection)
        assertEquals(
            EditorSelection.OnDate(Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate()),
            newEventSelectionForPage("MONTH", camera, zone)
        )
        assertNull(newEventSelectionForPage("MEMO", camera, zone))

        val identities = store.identityCount()
        val commits = store.commitCount()
        val drafts = store.draftCount()
        val pending = store.pendingCount()
        for (kind in TemporalKind.entries) {
            val opened = service.execute(
                WebEditCommand.CreateDeck(request(), kind),
                newEventZone = zone,
                newEventSelection = selection
            )
            assertEquals("created", opened.getString("status"))
            val form = service.snapshot().getJSONObject("deck").getJSONObject("form")
            assertEquals(kind.name, form.getString("kind"))
            assertEquals(zone.id, form.getString("zoneId"))
            when (kind) {
                TemporalKind.ALARM -> assertEquals(
                    DraftForm.formatInstant(expectedStart, zone), form.getString("startText"))
                TemporalKind.ACTIVITY -> {
                    assertEquals(DraftForm.formatInstant(expectedStart, zone), form.getString("startText"))
                    assertEquals(DraftForm.formatInstant(expectedStart + 3_600_000L, zone),
                        form.getString("endText"))
                }
                TemporalKind.DEADLINE -> {
                    assertEquals(DraftForm.formatInstant(expectedStart, zone), form.getString("endText"))
                    assertEquals(60, form.getInt("reminderMinutes"))
                }
                TemporalKind.MEMO -> assertEquals(
                    Instant.ofEpochMilli(expectedStart).atZone(zone).toLocalDate().toString(),
                    form.getString("startText"))
            }
            assertEquals("closed", service.execute(WebEditCommand.CloseDeck(request()))
                .getString("status"))
        }
        assertEquals(identities, store.identityCount())
        assertEquals(commits, store.commitCount())
        assertEquals(drafts, store.draftCount())
        assertEquals(pending, store.pendingCount())
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
