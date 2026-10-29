package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class WebSkinEditBridgeTest {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var controller: DeckController
    private lateinit var service: WebSkinEditService
    private fun request()=UUID.randomUUID().toString()
    @Before fun setup(){
        context=ApplicationProvider.getApplicationContext()
        store=PowerStore(context,"web-edit-"+request()+".db")
        controller=DeckController(StoreDeckPersistence(store))
        service=WebSkinEditService(store,controller)
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

    private suspend fun commitFormal(form: DraftForm): TemporalRecord {
        val card = store.newDraft(form)
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        return requireNotNull(store.get(card.eventSeq))
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
        assertTrue(WebEditWire.decode(wire("deck.retired", ",\"offset\":50"))
            is WebEditCommand.LoadRetiredPage)
        assertNull(WebEditWire.decode(wire("deck.retired", ",\"offset\":-1")))
        assertNull(WebEditWire.decode(wire("deck.retired", ",\"offset\":1.5")))
        assertNull(WebEditWire.decode(wire("deck.retired", ",\"offset\":50,\"limit\":999")))
        assertTrue(WebEditWire.decode(wire("deck.beginRestore",
            ",\"eventSeq\":42,\"expectedRevision\":3")) is WebEditCommand.BeginRestoreDeck)
        for (name in listOf("deck.keepConflict", "deck.requestConflictReview",
                "deck.keepRestore", "deck.requestRestoreReview")) {
            assertNotNull(WebEditWire.decode(wire(name,
                ",\"draftId\":9,\"draftVersion\":0,\"eventSeq\":42,\"expectedRevision\":3")))
            assertNull(WebEditWire.decode(wire(name,
                ",\"draftId\":9,\"draftVersion\":0,\"eventSeq\":42,\"expectedRevision\":3,\"approved\":true")))
        }
        assertTrue(WebEditWire.decode(wire("deck.search", ",\"query\":\"needle\""))
            is WebEditCommand.SearchDeck)
        assertNull(WebEditWire.decode(wire("deck.search",
            ",\"query\":\"${"q".repeat(121)}\"")))
        assertNull(WebEditWire.decode(wire("deck.search",
            ",\"query\":\"needle\",\"unbounded\":true")))
        assertEquals(-12, (WebEditWire.decode(wire("deck.jump", ",\"steps\":-12"))
            as WebEditCommand.JumpDeck).steps)
        assertNull(WebEditWire.decode(wire("deck.jump", ",\"steps\":1001")))
        assertNull(WebEditWire.decode(wire("deck.jump", ",\"steps\":1.5")))
        assertTrue(WebEditWire.decode(wire("deck.proposeReplace",
            ",\"eventSeq\":42,\"expectedRevision\":3"))
            is WebEditCommand.ProposeReplaceDeck)
        assertNull(WebEditWire.decode(wire("deck.proposeReplace",
            ",\"eventSeq\":42,\"expectedRevision\":3,\"form\":{}")))
        val deleteSession = request()
        assertNotNull(WebEditWire.decode(wire("editor.delete",
            ",\"sessionId\":\"$deleteSession\"")))
        assertNull(WebEditWire.decode(wire("editor.delete",
            ",\"sessionId\":\"$deleteSession\",\"force\":true")))

        assertNull(WebEditWire.decode(wire("deck.create").replace("\"command\"","\"other\"")))
        assertNull(WebEditWire.decode("x".repeat(8193)))
    }

    @Test fun timelineMoveWireUsesClosedSchemaAndSignedIntegerTarget() {
        val decoded = WebEditWire.decode(wire("timeline.move",
            ",\"eventSeq\":42,\"expectedRevision\":3,\"targetMillis\":-123456"))
        assertTrue(decoded is WebEditCommand.MoveTimelineEvent)
        assertEquals(-123456L, (decoded as WebEditCommand.MoveTimelineEvent).targetMillis)
        assertNull(WebEditWire.decode(wire("timeline.move",
            ",\"eventSeq\":42,\"expectedRevision\":3,\"targetMillis\":1.5")))
        assertNull(WebEditWire.decode(wire("timeline.move",
            ",\"eventSeq\":42,\"expectedRevision\":3,\"targetMillis\":\"123\"")))
        assertNull(WebEditWire.decode(wire("timeline.move",
            ",\"eventSeq\":42,\"expectedRevision\":3")))
        assertNull(WebEditWire.decode(wire("timeline.move",
            ",\"eventSeq\":42,\"expectedRevision\":3,\"targetMillis\":123,\"zoneId\":\"UTC\"")))
        assertNull(WebEditWire.decode(wire("timeline.move",
            ",\"eventSeq\":42,\"expectedRevision\":0,\"targetMillis\":123")))
    }

    @Test fun timelineMoveStagesOneRevisionGuardedReplacementAndPreservesDuration() = runBlocking {
        val zone = ZoneId.of("America/Los_Angeles")
        val start = LocalDateTime.of(2030, 10, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Move me",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id
        ))
        val context = TimelineMoveContext(TimelineCoordinate.Camera(start, 24.0), zone,
            mapOf(original.eventSeq to original.revision))
        val initialDrafts = store.draftCount()
        val initialIdentities = store.identityCount()
        val initialCommits = store.commitCount()
        val initialNextSequence = store.nextSequencePreview()
        val initialNextOrder = store.nextQueueOrderPreview()
        val target = LocalDateTime.of(2030, 10, 21, 10, 8)
            .atZone(zone).toInstant().toEpochMilli()
        val expectedStart = TimelineCoordinate.snap(target, 24.0, zone)
        val unchanged = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), original.eventSeq, original.revision, start + 60_000L
        ), timelineMoveContext = context)
        assertEquals("unchanged", unchanged.getString("status"))
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())
        assertEquals(initialDrafts, store.draftCount())
        val command = WebEditCommand.MoveTimelineEvent(
            request(), original.eventSeq, original.revision, target
        )

        val first = service.execute(command, timelineMoveContext = context)
        assertEquals("staged", first.getString("status"))
        val nextQueueOrderAfterStage = initialNextOrder + 1
        val draftId = first.getString("detail").toLong()
        val staged = store.pendingDraftsFor(original.eventSeq).single()
        assertEquals(draftId, staged.draftId)
        assertEquals(DraftOperation.REPLACE, staged.operation)
        assertEquals(original.revision, staged.baseRevision)
        assertEquals(DraftForm.formatInstant(expectedStart, zone), staged.form.startText)
        assertEquals(DraftForm.formatInstant(expectedStart + 3_600_000L, zone), staged.form.endText)
        assertEquals(original, store.get(original.eventSeq))
        assertTrue(service.snapshot().getJSONObject("deck").getBoolean("open"))
        assertEquals(draftId, service.snapshot().getJSONObject("deck").getLong("currentId"))
        assertEquals(initialDrafts + 1, store.draftCount())
        assertEquals(initialIdentities, store.identityCount())
        assertEquals(initialCommits, store.commitCount())
        assertEquals(initialNextSequence, store.nextSequencePreview())
        assertEquals(nextQueueOrderAfterStage, store.nextQueueOrderPreview())

        val replay = service.execute(command, timelineMoveContext = context)
        assertEquals(first.toString(), replay.toString())
        assertEquals(initialDrafts + 1, store.draftCount())
        assertEquals(1, store.pendingDraftsFor(original.eventSeq).size)

        assertEquals("closed", service.execute(WebEditCommand.CloseDeck(request()))
            .getString("status"))
        val secondTarget = LocalDateTime.of(2030, 10, 21, 11, 2)
            .atZone(zone).toInstant().toEpochMilli()
        val second = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), original.eventSeq, original.revision, secondTarget
        ), timelineMoveContext = context)
        assertEquals("staged", second.getString("status"))
        assertEquals(draftId, second.getString("detail").toLong())
        assertEquals(1, store.pendingDraftsFor(original.eventSeq).size)
        assertEquals(initialDrafts + 1, store.draftCount())
        assertEquals(initialIdentities, store.identityCount())
        assertEquals(initialCommits, store.commitCount())
        assertEquals(initialNextSequence, store.nextSequencePreview())
        assertEquals(nextQueueOrderAfterStage, store.nextQueueOrderPreview())
        assertEquals(original, store.get(original.eventSeq))
    }

    @Test fun timelineMoveRefusesRepeatingAndNonTimelineItemsWithoutWrites() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2030, 10, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val repeating = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Repeating",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        val dateDeadline = commitFormal(DraftForm(
            kind = TemporalKind.DEADLINE,
            title = "Date only",
            endText = "2030-10-22",
            zoneId = zone.id
        ))
        val memo = commitFormal(DraftForm(
            kind = TemporalKind.MEMO,
            title = "Undated memo",
            zoneId = zone.id
        ))
        val camera = TimelineMoveContext(TimelineCoordinate.Camera(start, 24.0), zone,
            mapOf(repeating.eventSeq to repeating.revision,
                dateDeadline.eventSeq to dateDeadline.revision, memo.eventSeq to memo.revision))
        val drafts = store.draftCount()
        val identities = store.identityCount()
        val commits = store.commitCount()
        val seq = store.nextSequencePreview()
        val order = store.nextQueueOrderPreview()
        val target = start + 7_200_000L

        val refused = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), repeating.eventSeq, repeating.revision, target
        ), timelineMoveContext = camera)
        assertEquals("unsupported", refused.getString("status"))
        assertEquals(repeating, store.get(repeating.eventSeq))
        assertTrue(store.pendingDraftsFor(repeating.eventSeq).isEmpty())

        for (record in listOf(dateDeadline, memo)) {
            val unsupported = service.execute(WebEditCommand.MoveTimelineEvent(
                request(), record.eventSeq, record.revision, target
            ), timelineMoveContext = camera)
            assertEquals("unsupported", unsupported.getString("status"))
            assertEquals(record, store.get(record.eventSeq))
            assertTrue(store.pendingDraftsFor(record.eventSeq).isEmpty())
        }
        val outsideTimeline = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), repeating.eventSeq, repeating.revision, target
        ))
        assertEquals("unsupported", outsideTimeline.getString("status"))
        assertEquals(drafts, store.draftCount())
        assertEquals(identities, store.identityCount())
        assertEquals(commits, store.commitCount())
        assertEquals(seq, store.nextSequencePreview())
        assertEquals(order, store.nextQueueOrderPreview())
    }

    @Test fun timelineMoveRequiresOwnerProjectionForTheDisplayedRevision() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2030, 10, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ALARM,
            title = "Not projected",
            startText = DraftForm.formatInstant(start, zone),
            zoneId = zone.id
        ))
        val beforeDrafts = store.draftCount()
        val beforeCommits = store.commitCount()
        val result = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), original.eventSeq, original.revision, start + 3_600_000L
        ), timelineMoveContext = TimelineMoveContext(
            TimelineCoordinate.Camera(start, 24.0), zone, emptyMap()
        ))
        assertEquals("stale", result.getString("status"))
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())
        assertEquals(beforeDrafts, store.draftCount())
        assertEquals(beforeCommits, store.commitCount())
        assertEquals(original, store.get(original.eventSeq))
    }

    @Test fun deckSearchReturnsBoundedDraftAndFormalMatchesWithoutWrites() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2030, 10, 21, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val formalRecords = mutableListOf<TemporalRecord>()
        repeat(5) { index ->
            formalRecords += commitFormal(DraftForm(
                kind = TemporalKind.ALARM,
                title = "Target formal $index",
                startText = DraftForm.formatInstant(start + index * 3_600_000L, zone),
                zoneId = zone.id
            ))
            store.newDraft(DraftForm(kind = TemporalKind.MEMO,
                title = "Queue needle $index", zoneId = zone.id))
        }
        val drafts = store.draftCount()
        val identities = store.identityCount()
        val commits = store.commitCount()
        val nextSequence = store.nextSequencePreview()
        val nextQueueOrder = store.nextQueueOrderPreview()
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), null))
            .getString("status"))

        assertEquals("searched", service.execute(WebEditCommand.SearchDeck(request(), "needle"))
            .getString("status"))
        val draftSearch = service.snapshot().getJSONObject("deck")
        assertEquals(5, draftSearch.getInt("cardCount"))
        assertEquals(4, draftSearch.getJSONArray("cards").length())
        assertTrue(draftSearch.getBoolean("truncated"))
        assertEquals(0, draftSearch.getJSONArray("formalMatches").length())

        assertEquals("searched", service.execute(WebEditCommand.SearchDeck(request(), "Target"))
            .getString("status"))
        val formalSearch = service.snapshot().getJSONObject("deck")
        assertEquals(0, formalSearch.getJSONArray("cards").length())
        assertEquals(4, formalSearch.getJSONArray("formalMatches").length())
        val resultKeys = formalSearch.getJSONArray("formalMatches").getJSONObject(0)
            .keys().asSequence().toSet()
        assertEquals(setOf("eventSeq", "revision", "title", "kind"), resultKeys)

        val formattedSequence = TemporalRecord.formatSeq(formalRecords.first().eventSeq)
        assertEquals("searched", service.execute(WebEditCommand.SearchDeck(
            request(), formattedSequence
        )).getString("status"))
        val formattedSearch = service.snapshot().getJSONObject("deck").getJSONArray("formalMatches")
        assertEquals(1, formattedSearch.length())
        assertEquals(formalRecords.first().eventSeq,
            formattedSearch.getJSONObject(0).getLong("eventSeq"))

        assertEquals(drafts, store.draftCount())
        assertEquals(identities, store.identityCount())
        assertEquals(commits, store.commitCount())
        assertEquals(nextSequence, store.nextSequencePreview())
        assertEquals(nextQueueOrder, store.nextQueueOrderPreview())
    }

    @Test fun conflictSnapshotRequiresHostReviewAndReplacesOnlyAfterRevalidation() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2032, 4, 5, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY, title = "Conflict original",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000, zone),
            zoneId = zone.id, trackingEnabled = true
        ))
        val proposal = store.newDraft(DraftForm.from(original).copy(title = "Conflict proposal"),
            original.eventSeq, DraftOperation.REPLACE, expectedRevision = original.revision)
        assertTrue(store.setCompleted(original.eventSeq, "single", true, original.revision))
        val current = requireNotNull(store.get(original.eventSeq))
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), proposal.draftId))
            .getString("status"))
        val initial = service.snapshot().getJSONObject("deck")
        val publish = WebEditCommand.CommitDeck(request(), proposal.draftId, initial.getLong("version"))
        assertEquals("confirmation_required", service.execute(publish).getString("status"))
        assertEquals("conflict", service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token, approved = true).getString("status"))

        var conflict = service.snapshot().getJSONObject("deck").getJSONObject("conflict")
        assertEquals(proposal.draftId, conflict.getLong("draftId"))
        assertEquals(original.eventSeq, conflict.getLong("eventSeq"))
        assertEquals(current.revision, conflict.getLong("currentRevision"))
        assertEquals("Conflict original", conflict.getJSONObject("current").getString("title"))
        assertEquals("Conflict proposal", conflict.getJSONObject("proposed").getString("title"))
        assertTrue((0 until conflict.getJSONArray("changes").length()).any {
            conflict.getJSONArray("changes").getJSONObject(it).getString("label") == "标题"
        })
        val commitsBeforeConflictReview = store.commitCount()
        assertEquals(current, store.get(original.eventSeq))

        val args = WebEditCommand.RequestConflictReviewDeck(request(), proposal.draftId,
            conflict.getLong("draftVersion"), original.eventSeq, current.revision)
        assertEquals("confirmation_required", service.execute(args).getString("status"))
        val review = requireNotNull(service.pendingWriteReview)
        assertEquals(current, review.current)
        assertEquals(DraftForm.from(current), review.before?.let { DraftForm.from(it) })
        assertEquals(DraftForm.from(original).copy(title = "Conflict proposal"), review.proposed)
        val commitsBeforeApproval = store.commitCount()
        assertEquals(commitsBeforeConflictReview, commitsBeforeApproval)
        assertEquals("replaced", service.resolveFormalWriteReview(review.token, approved = true)
            .getString("status"))

        val replaced = requireNotNull(store.get(original.eventSeq))
        assertEquals("Conflict proposal", replaced.title)
        assertEquals(current.revision + 1, replaced.revision)
        assertEquals(commitsBeforeApproval + 1, store.commitCount())
        assertFalse(store.isRetired(original.eventSeq))
        assertEquals(JSONObject.NULL, service.snapshot().getJSONObject("deck").get("conflict"))
    }

    @Test fun staleHostConflictReviewCannotOverwriteAndKeepLeavesDraftUntouched() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2032, 4, 5, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY, title = "Keep original",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000, zone),
            zoneId = zone.id, trackingEnabled = true
        ))
        val proposal = store.newDraft(DraftForm.from(original).copy(title = "Keep proposal"),
            original.eventSeq, DraftOperation.REPLACE, expectedRevision = original.revision)
        assertTrue(store.setCompleted(original.eventSeq, "single", true, original.revision))
        val firstCurrent = store.get(original.eventSeq)!!
        service.execute(WebEditCommand.OpenDeck(request(), proposal.draftId))
        val version = service.snapshot().getJSONObject("deck").getLong("version")
        val publish = WebEditCommand.CommitDeck(request(), proposal.draftId, version)
        assertEquals("confirmation_required", service.execute(publish).getString("status"))
        assertEquals("conflict", service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token, true).getString("status"))
        val conflict = service.snapshot().getJSONObject("deck").getJSONObject("conflict")
        val cancelReview = WebEditCommand.RequestConflictReviewDeck(request(), proposal.draftId,
            conflict.getLong("draftVersion"), original.eventSeq, firstCurrent.revision)
        assertEquals("confirmation_required", service.execute(cancelReview).getString("status"))
        assertEquals("cancelled", service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token, approved = false).getString("status"))
        assertNotNull(service.snapshot().getJSONObject("deck").get("conflict"))

        val review = WebEditCommand.RequestConflictReviewDeck(request(), proposal.draftId,
            conflict.getLong("draftVersion"), original.eventSeq, firstCurrent.revision)
        assertEquals("confirmation_required", service.execute(review).getString("status"))
        val token = requireNotNull(service.pendingWriteReview).token
        assertTrue(store.setCompleted(original.eventSeq, "single", false, firstCurrent.revision))
        val newer = requireNotNull(store.get(original.eventSeq))
        val commitsBeforeRejectedReview = store.commitCount()

        assertEquals("conflict", service.resolveFormalWriteReview(token, true).getString("status"))
        assertEquals(newer, store.get(original.eventSeq))
        assertEquals(commitsBeforeRejectedReview, store.commitCount())
        assertEquals(DraftStatus.CONFLICT, store.draft(proposal.draftId)!!.status)

        val kept = service.execute(WebEditCommand.KeepConflictDeck(request(), proposal.draftId,
            conflict.getLong("draftVersion"), original.eventSeq, firstCurrent.revision))
        assertEquals("kept", kept.getString("status"))
        assertEquals(newer, store.get(original.eventSeq))
        assertEquals(DraftStatus.CONFLICT, store.draft(proposal.draftId)!!.status)
        assertEquals(commitsBeforeRejectedReview, store.commitCount())
    }

    @Test fun retiredPickerCreatesRecoverableDraftAndRestoreNeedsSeparateHostApproval() = runBlocking {
        val zone = ZoneId.of("Asia/Tokyo")
        val start = LocalDateTime.of(2032, 4, 5, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY, title = "Restore from picker",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000, zone),
            zoneId = zone.id, trackingEnabled = true
        ))
        val deletion = store.newDraft(DraftForm.from(original), original.eventSeq,
            DraftOperation.DELETE, expectedRevision = original.revision)
        assertTrue(store.commit(deletion.draftId) is PowerStore.CommitOutcome.Committed)
        val tombstone = requireNotNull(store.getIncludingRetired(original.eventSeq))
        assertTrue(store.isRetired(original.eventSeq))
        val ids = store.identityCount()
        val nextSeq = store.nextSequencePreview()
        val queueOrder = store.nextQueueOrderPreview()
        val commitsAfterDelete = store.commitCount()

        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), null))
            .getString("status"))
        var deck = service.snapshot().getJSONObject("deck")
        assertTrue(deck.getBoolean("retiredLoaded"))
        assertEquals(1, deck.getInt("retiredTotal"))
        val listed = deck.getJSONArray("retiredResults").getJSONObject(0)
        assertEquals(original.eventSeq, listed.getLong("eventSeq"))
        assertEquals(tombstone.revision, listed.getLong("revision"))
        assertEquals("Restore from picker", listed.getString("title"))
        val beforeStaleSelection = store.draftCount()
        assertEquals("stale", service.execute(WebEditCommand.BeginRestoreDeck(
            request(), original.eventSeq, tombstone.revision + 1)).getString("status"))
        assertEquals(beforeStaleSelection, store.draftCount())
        assertEquals("loaded", service.execute(WebEditCommand.LoadRetiredPage(request(), 999))
            .getString("status"))
        deck = service.snapshot().getJSONObject("deck")
        assertEquals(0, deck.getInt("retiredOffset"))

        val proposal = WebEditCommand.BeginRestoreDeck(request(), original.eventSeq, tombstone.revision)
        assertEquals("opened", service.execute(proposal).getString("status"))
        deck = service.snapshot().getJSONObject("deck")
        val draftId = deck.getLong("currentId")
        assertEquals(tombstone.revision, store.draft(draftId)!!.baseRevision)
        assertEquals(tombstone.title, deck.getJSONObject("form").getString("title"))
        assertEquals(ids, store.identityCount())
        assertEquals(nextSeq, store.nextSequencePreview())
        assertEquals(queueOrder + 1, store.nextQueueOrderPreview())
        assertEquals(commitsAfterDelete, store.commitCount())

        val firstCommit = WebEditCommand.CommitDeck(request(), draftId, deck.getLong("version"))
        assertEquals("restore_required", service.execute(firstCommit).getString("status"))
        var prompt = service.snapshot().getJSONObject("deck").getJSONObject("restore")
        assertEquals(tombstone.revision, prompt.getLong("expectedRevision"))
        assertEquals("Restore from picker", prompt.getJSONObject("current").getString("title"))
        assertEquals("Restore from picker", prompt.getJSONObject("proposed").getString("title"))

        val keep = WebEditCommand.KeepRestoreDeck(request(), draftId,
            prompt.getLong("draftVersion"), original.eventSeq, tombstone.revision)
        assertEquals("kept", service.execute(keep).getString("status"))
        assertTrue(store.isRetired(original.eventSeq))
        assertEquals(DraftStatus.PENDING, store.draft(draftId)!!.status)
        assertEquals(JSONObject.NULL, service.snapshot().getJSONObject("deck").get("restore"))

        deck = service.snapshot().getJSONObject("deck")
        val retryCommit = WebEditCommand.CommitDeck(request(), draftId, deck.getLong("version"))
        assertEquals("restore_required", service.execute(retryCommit).getString("status"))
        prompt = service.snapshot().getJSONObject("deck").getJSONObject("restore")
        val cancel = WebEditCommand.RequestRestoreReviewDeck(request(), draftId,
            prompt.getLong("draftVersion"), original.eventSeq, tombstone.revision)
        assertEquals("confirmation_required", service.execute(cancel).getString("status"))
        val cancelToken = requireNotNull(service.pendingWriteReview).token
        assertEquals("cancelled", service.resolveFormalWriteReview(cancelToken, approved = false)
            .getString("status"))
        assertTrue(store.isRetired(original.eventSeq))
        assertEquals(commitsAfterDelete, store.commitCount())
        assertEquals(DraftStatus.PENDING, store.draft(draftId)!!.status)

        deck = service.snapshot().getJSONObject("deck")
        assertEquals(original.eventSeq, deck.getJSONObject("restore").getLong("eventSeq"))
        prompt = service.snapshot().getJSONObject("deck").getJSONObject("restore")
        val approve = WebEditCommand.RequestRestoreReviewDeck(request(), draftId,
            prompt.getLong("draftVersion"), original.eventSeq, tombstone.revision)
        assertEquals("confirmation_required", service.execute(approve).getString("status"))
        val review = requireNotNull(service.pendingWriteReview)
        assertEquals(tombstone, review.current)
        assertEquals("restored", service.resolveFormalWriteReview(review.token, true)
            .getString("status"))

        val restored = requireNotNull(store.get(original.eventSeq))
        assertEquals(original.eventSeq, restored.eventSeq)
        assertEquals(tombstone.revision + 1, restored.revision)
        assertEquals(tombstone.title, restored.title)
        assertFalse(store.isRetired(original.eventSeq))
        assertEquals(nextSeq, store.nextSequencePreview())
        assertEquals(commitsAfterDelete + 1, store.commitCount())
    }

    @Test fun restoreHostReviewRejectsConcurrentRestorationOfTheSameTombstone() = runBlocking {
        val zone = ZoneId.of("Asia/Tokyo")
        val start = LocalDateTime.of(2032, 4, 6, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY, title = "Concurrent restore",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000, zone),
            zoneId = zone.id, trackingEnabled = true
        ))
        val deletion = store.newDraft(DraftForm.from(original), original.eventSeq,
            DraftOperation.DELETE, expectedRevision = original.revision)
        assertTrue(store.commit(deletion.draftId) is PowerStore.CommitOutcome.Committed)
        val tombstone = store.getIncludingRetired(original.eventSeq)!!
        service.execute(WebEditCommand.OpenDeck(request(), null))
        val listed = service.snapshot().getJSONObject("deck").getJSONArray("retiredResults")
            .getJSONObject(0)
        assertEquals(original.eventSeq, listed.getLong("eventSeq"))
        assertEquals("opened", service.execute(WebEditCommand.BeginRestoreDeck(
            request(), original.eventSeq, tombstone.revision)).getString("status"))
        val deck = service.snapshot().getJSONObject("deck")
        val draftId = deck.getLong("currentId")
        assertEquals("restore_required", service.execute(WebEditCommand.CommitDeck(
            request(), draftId, deck.getLong("version"))).getString("status"))
        val prompt = service.snapshot().getJSONObject("deck").getJSONObject("restore")
        val requestReview = WebEditCommand.RequestRestoreReviewDeck(request(), draftId,
            prompt.getLong("draftVersion"), original.eventSeq, tombstone.revision)
        assertEquals("confirmation_required", service.execute(requestReview).getString("status"))
        val review = requireNotNull(service.pendingWriteReview)

        val concurrent = store.restoreDraft(draftId, DraftForm.from(tombstone)
            .copy(title = "Restored by the other host")) as PowerStore.CommitOutcome.Committed
        val deckAfterConcurrentRestore = service.snapshot().getJSONObject("deck")
        val commitsAfterConcurrentRestore = store.commitCount()
        assertEquals("conflict", service.resolveFormalWriteReview(review.token, approved = true)
            .getString("status"))
        assertEquals(concurrent.eventSeq, original.eventSeq)
        assertEquals("Restored by the other host", store.get(original.eventSeq)?.title)
        assertFalse(store.isRetired(original.eventSeq))
        assertEquals(commitsAfterConcurrentRestore, store.commitCount())
        assertEquals(DraftStatus.COMMITTED, store.draft(draftId)!!.status)
        assertTrue(deckAfterConcurrentRestore.getBoolean("hasRestore"))
    }

    @Test fun deckNavigationUsesControllerAndKeepsQueueOrder() = runBlocking {
        val cards = listOf("First", "Second", "Third").map { title ->
            store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = title))
        }
        val queueBefore = cards.map { store.draft(it.draftId)!!.queueOrder }
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), null))
            .getString("status"))
        assertEquals(cards.first().draftId,
            service.snapshot().getJSONObject("deck").getLong("currentId"))

        assertEquals("moved", service.execute(WebEditCommand.JumpDeck(request(), 2))
            .getString("status"))
        var deck = service.snapshot().getJSONObject("deck")
        assertEquals(cards.last().draftId, deck.getLong("currentId"))
        assertEquals(3, deck.getInt("position"))
        assertTrue(deck.getBoolean("canGoPrevious"))
        assertFalse(deck.getBoolean("canGoNext"))

        assertEquals("moved", service.execute(WebEditCommand.JumpDeck(request(), -1))
            .getString("status"))
        deck = service.snapshot().getJSONObject("deck")
        assertEquals(cards[1].draftId, deck.getLong("currentId"))
        assertEquals(queueBefore, cards.map { store.draft(it.draftId)!!.queueOrder })
        assertEquals(cards.map { 0L }, cards.map { store.draft(it.draftId)!!.draftVersion })
    }

    @Test fun formalSearchSelectionReusesRevisionCheckedReplaceDraft() = runBlocking {
        val zone = ZoneId.of("Asia/Tokyo")
        val start = LocalDateTime.of(2030, 10, 21, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Renewal target",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            trackingEnabled = true
        ))
        store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "Unrelated draft"))
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), null))
            .getString("status"))
        assertEquals("searched", service.execute(WebEditCommand.SearchDeck(request(), "Renewal"))
            .getString("status"))
        val beforeStale = store.draftCount()
        assertTrue(store.setCompleted(original.eventSeq, "single", true, original.revision))
        val current = requireNotNull(store.get(original.eventSeq))
        assertTrue(current.revision > original.revision)
        val stale = service.execute(WebEditCommand.ProposeReplaceDeck(
            request(), original.eventSeq, original.revision
        ))
        assertEquals("stale", stale.getString("status"))
        assertEquals(beforeStale, store.draftCount())
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())

        assertEquals("searched", service.execute(WebEditCommand.SearchDeck(request(), "Renewal"))
            .getString("status"))
        val command = WebEditCommand.ProposeReplaceDeck(request(), original.eventSeq, current.revision)
        val opened = service.execute(command)
        assertEquals("opened", opened.getString("status"))
        val draftId = opened.getString("detail").toLong()
        val candidate = requireNotNull(store.draft(draftId))
        assertEquals(DraftOperation.REPLACE, candidate.operation)
        assertEquals(current.revision, candidate.baseRevision)
        assertEquals(current.title, candidate.form.title)
        assertEquals(current, store.get(original.eventSeq))
        assertEquals(draftId, service.snapshot().getJSONObject("deck").getLong("currentId"))

        val replay = service.execute(command)
        assertEquals(opened.toString(), replay.toString())
        assertEquals(beforeStale + 1, store.draftCount())
        assertEquals(candidate.queueOrder, store.draft(draftId)!!.queueOrder)
    }

    @Test fun timelineMoveRejectsStaleOwnerRevisionWithoutStaging() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2030, 10, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Stale move",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            trackingEnabled = true
        ))
        assertTrue(store.setCompleted(original.eventSeq, "single", true, original.revision))
        val current = requireNotNull(store.get(original.eventSeq))
        assertTrue(current.revision > original.revision)
        val beforeDrafts = store.draftCount()
        val target = start + 7_200_000L
        val result = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), original.eventSeq, original.revision, target
        ), timelineMoveContext = TimelineMoveContext(
            TimelineCoordinate.Camera(start, 24.0), zone,
            mapOf(original.eventSeq to original.revision)
        ))
        assertEquals("stale", result.getString("status"))
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())
        assertEquals(beforeDrafts, store.draftCount())
        assertEquals(current, store.get(original.eventSeq))
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

    @Test fun transientNewEventDeckDoesNotExposePersistentDeckSearch() = runBlocking {
        val identities = store.identityCount()
        val nextSequence = store.nextSequencePreview()
        val nextQueueOrder = store.nextQueueOrderPreview()
        val commits = store.commitCount()
        val drafts = store.draftCount()
        val pending = store.pendingCount()

        assertEquals("created", service.execute(
            WebEditCommand.CreateDeck(request(), TemporalKind.MEMO)
        ).getString("status"))
        var deck = service.snapshot().getJSONObject("deck")
        assertTrue(deck.getBoolean("transient"))
        assertEquals(1, deck.getInt("cardCount"))
        assertEquals("unsupported", service.execute(
            WebEditCommand.SearchDeck(request(), "existing draft")
        ).getString("status"))
        deck = service.snapshot().getJSONObject("deck")
        assertTrue(deck.getBoolean("transient"))
        assertEquals("", deck.getString("searchQuery"))

        assertEquals("closed", service.execute(WebEditCommand.CloseDeck(request()))
            .getString("status"))
        assertEquals(identities, store.identityCount())
        assertEquals(nextSequence, store.nextSequencePreview())
        assertEquals(nextQueueOrder, store.nextQueueOrderPreview())
        assertEquals(commits, store.commitCount())
        assertEquals(drafts, store.draftCount())
        assertEquals(pending, store.pendingCount())
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
        assertEquals(EditorSelection.OnDate(LocalDate.of(2027, 4, 19)),
            newEventSelectionForPage("MONTH", camera, zone, LocalDate.of(2027, 4, 19)))
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
