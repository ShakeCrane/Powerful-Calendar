package dev.powerfulcalendar.ui.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DeckController
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftStatus
import dev.powerfulcalendar.core.DeadlineValue
import dev.powerfulcalendar.core.EditorSelection
import dev.powerfulcalendar.core.FormBuild
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceEditScope
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.StoreDeckPersistence
import dev.powerfulcalendar.core.TemporalConversion
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.core.TimelineCoordinate
import dev.powerfulcalendar.core.api.v1.AiBatchStateSnapshotV1
import dev.powerfulcalendar.core.api.v1.AiFeatureApiV1
import dev.powerfulcalendar.core.api.v1.DraftReviewCardV1
import dev.powerfulcalendar.core.api.v1.DraftReviewFeatureApiV1
import dev.powerfulcalendar.core.api.v1.DraftReviewReceiptStatusV1
import dev.powerfulcalendar.core.api.v1.EventFeatureApiV1
import dev.powerfulcalendar.core.api.v1.EventFeatureChangeTypeV1
import dev.powerfulcalendar.core.api.v1.EventEditorHandleV1
import dev.powerfulcalendar.core.api.v1.EventEditorUpdateReceiptV1
import dev.powerfulcalendar.core.api.v1.EventMutationReceiptV1
import dev.powerfulcalendar.core.api.v1.EventMutationStatusV1
import dev.powerfulcalendar.core.api.v1.EventRecurrenceScopeV1
import dev.powerfulcalendar.core.api.v1.EventRecordV1
import dev.powerfulcalendar.core.api.v1.PowerCalCoreFeatureApiV1
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class WebSkinEditBridgeTest {
    private data class RecordedTimelineMove(
        val eventSeq: Long,
        val expectedRevision: Long,
        val targetMillis: Long,
        val requestId: String,
        val receipt: EventMutationReceiptV1
    )

    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var controller: DeckController
    private lateinit var service: WebSkinEditService
    private lateinit var coreFeatureApi: PowerCalCoreFeatureApiV1
    private val queriedEventSequences = mutableListOf<Long>()
    private val queriedDraftIds = mutableListOf<Long>()
    private val queriedBatchLockStates = mutableListOf<Boolean>()
    private val selectedRecurrenceScopes = mutableListOf<EventRecurrenceScopeV1>()
    private val savedNewEventReceipts = mutableListOf<EventMutationReceiptV1>()
    private val timelineMoveCalls = mutableListOf<RecordedTimelineMove>()
    private val testZone = ZoneId.of("Asia/Shanghai")
    private fun request()=UUID.randomUUID().toString()
    @Before fun setup(){
        context=ApplicationProvider.getApplicationContext()
        store=PowerStore(context,"web-edit-"+request()+".db")
        controller=DeckController(StoreDeckPersistence(store))
        coreFeatureApi=PowerCalCoreFeatureApiV1(store)
        val recordingEvents = object : EventFeatureApiV1 by coreFeatureApi.events {
            override fun queryEvent(eventSeq: Long): EventRecordV1? {
                queriedEventSequences += eventSeq
                return coreFeatureApi.events.queryEvent(eventSeq)
            }

            override fun stageTimelineMove(
                eventSeq: Long,
                expectedRevision: Long,
                targetMillis: Long,
                requestId: String
            ): EventMutationReceiptV1 {
                val receipt = coreFeatureApi.events.stageTimelineMove(
                    eventSeq, expectedRevision, targetMillis, requestId
                )
                timelineMoveCalls += RecordedTimelineMove(
                    eventSeq, expectedRevision, targetMillis, requestId, receipt
                )
                return receipt
            }

            override fun selectFormalRecurrenceScope(
                handle: EventEditorHandleV1,
                scope: EventRecurrenceScopeV1
            ): EventEditorUpdateReceiptV1 {
                selectedRecurrenceScopes += scope
                return coreFeatureApi.events.selectFormalRecurrenceScope(handle, scope)
            }

            override fun saveNewEvent(
                handle: EventEditorHandleV1,
                form: dev.powerfulcalendar.core.api.v1.EventFormV1,
                requestId: String
            ): EventMutationReceiptV1 {
                val receipt = coreFeatureApi.events.saveNewEvent(handle, form, requestId)
                savedNewEventReceipts += receipt
                return receipt
            }
        }
        val draftReviewApi = coreFeatureApi.adaptDeckController(controller)
        val recordingDraftReview = object : DraftReviewFeatureApiV1 by draftReviewApi {
            override fun queryDraft(draftId: Long): DraftReviewCardV1? {
                queriedDraftIds += draftId
                return draftReviewApi.queryDraft(draftId)
            }
        }
        val recordingAi = object : AiFeatureApiV1 by coreFeatureApi.ai {
            override fun queryBatchState(): AiBatchStateSnapshotV1 {
                val state = coreFeatureApi.ai.queryBatchState()
                queriedBatchLockStates += state.locked
                return state
            }
        }
        service=WebSkinEditService(
            recordingDraftReview, coreFeatureApi=coreFeatureApi,
            trustedDeleteApprovalIssuer = coreFeatureApi::issueFormalDeleteApproval,
            eventFeatures=recordingEvents, aiFeatures=recordingAi
        )
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

    private fun patchForm(form: DraftForm, field: String, value: String): DraftForm =
        WebEditWire.patch(form, field, value, coreFeatureApi.events)

    @Test fun recurrenceScopeEligibilityIsDecidedByCoreWithoutChangingBridgeResponses() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2030, 10, 21, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val oneOff = commitFormal(DraftForm(
            kind = TemporalKind.ALARM,
            title = "One-off event",
            startText = DraftForm.formatInstant(start, zone),
            zoneId = zone.id
        ))
        val repeating = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Recurring event",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        val commitsBefore = store.commitCount()
        val draftsBefore = store.draftCount()

        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            request(), oneOff.eventSeq, oneOff.revision
        )).getString("status"))
        val oneOffSession = service.snapshot().getJSONObject("editor").getString("sessionId")
        val nonRepeatingScope = service.execute(WebEditCommand.SelectScope(
            request(), oneOffSession, RecurrenceEditScope.WHOLE_SERIES
        ))
        assertEquals("invalid", nonRepeatingScope.getString("status"))
        assertEquals("请先放弃修改并选择有效的重复范围", nonRepeatingScope.getString("detail"))
        assertEquals("closed", service.execute(WebEditCommand.CloseEvent(
            request(), oneOffSession
        )).getString("status"))

        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            request(), repeating.eventSeq, repeating.revision
        )).getString("status"))
        val repeatingSession = service.snapshot().getJSONObject("editor").getString("sessionId")
        val missingOccurrence = service.execute(WebEditCommand.SelectScope(
            request(), repeatingSession, RecurrenceEditScope.SINGLE_OCCURRENCE
        ))
        assertEquals("invalid", missingOccurrence.getString("status"))
        assertEquals("请先放弃修改并选择有效的重复范围", missingOccurrence.getString("detail"))

        assertEquals("selected", service.execute(WebEditCommand.SelectScope(
            request(), repeatingSession, RecurrenceEditScope.WHOLE_SERIES
        )).getString("status"))
        assertEquals("edited", service.execute(WebEditCommand.PatchEvent(
            request(), repeatingSession, "title", "Changed in editor"
        )).getString("status"))
        val dirtyScope = service.execute(WebEditCommand.SelectScope(
            request(), repeatingSession, RecurrenceEditScope.WHOLE_SERIES
        ))
        assertEquals("invalid", dirtyScope.getString("status"))
        assertEquals("请先放弃修改并选择有效的重复范围", dirtyScope.getString("detail"))

        assertEquals(listOf(
            EventRecurrenceScopeV1.WHOLE_SERIES,
            EventRecurrenceScopeV1.SINGLE_OCCURRENCE,
            EventRecurrenceScopeV1.WHOLE_SERIES,
            EventRecurrenceScopeV1.WHOLE_SERIES
        ), selectedRecurrenceScopes)
        assertEquals(oneOff, store.get(oneOff.eventSeq))
        assertEquals(repeating, store.get(repeating.eventSeq))
        assertEquals(commitsBefore, store.commitCount())
        assertEquals(draftsBefore, store.draftCount())
        assertTrue(store.pendingDraftsFor(oneOff.eventSeq).isEmpty())
        assertTrue(store.pendingDraftsFor(repeating.eventSeq).isEmpty())

        service.execute(WebEditCommand.DiscardEventChanges(request(), repeatingSession))
        service.execute(WebEditCommand.CloseEvent(request(), repeatingSession))
        Unit
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
        assertNull(WebEditWire.decode(wire("deck.patch", ""","draftId":1,"draftVersion":0,"stateRevision":0,"field":"recurrence","value":"RAW"""")))
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

    @Test fun timelineMoveUsesCoreEligibilityAndMovesDateDeadlineWithoutLosingPrecision() = runBlocking {
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
        assertEquals(1, timelineMoveCalls.size)
        assertEquals(repeating.eventSeq, timelineMoveCalls.single().eventSeq)
        assertEquals(EventMutationStatusV1.INVALID, timelineMoveCalls.single().receipt.status)
        assertEquals("TIMELINE_MOVE_UNSUPPORTED", timelineMoveCalls.single().receipt.errorCode)
        assertEquals(repeating, store.get(repeating.eventSeq))
        assertTrue(store.pendingDraftsFor(repeating.eventSeq).isEmpty())

        val movedDate = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), dateDeadline.eventSeq, dateDeadline.revision, target
        ), timelineMoveContext = camera)
        assertEquals("staged", movedDate.getString("status"))
        assertEquals(2, timelineMoveCalls.size)
        assertEquals(EventMutationStatusV1.STAGED, timelineMoveCalls.last().receipt.status)
        val dateCandidate = store.pendingDraftsFor(dateDeadline.eventSeq).single()
        assertEquals("2030-10-21", dateCandidate.form.endText)
        assertTrue(dateCandidate.form.parseDeadline() is DeadlineValue.Date)
        assertEquals(dateDeadline, store.get(dateDeadline.eventSeq))
        assertEquals("closed", service.execute(WebEditCommand.CloseDeck(request()))
            .getString("status"))

        val unsupportedMemo = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), memo.eventSeq, memo.revision, target
        ), timelineMoveContext = camera)
        assertEquals("unsupported", unsupportedMemo.getString("status"))
        assertEquals(3, timelineMoveCalls.size)
        assertEquals(EventMutationStatusV1.INVALID, timelineMoveCalls.last().receipt.status)
        assertEquals("TIMELINE_MOVE_UNSUPPORTED", timelineMoveCalls.last().receipt.errorCode)
        assertEquals(memo, store.get(memo.eventSeq))
        assertTrue(store.pendingDraftsFor(memo.eventSeq).isEmpty())
        val outsideTimeline = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), repeating.eventSeq, repeating.revision, target
        ))
        assertEquals("unsupported", outsideTimeline.getString("status"))
        assertEquals(drafts + 1, store.draftCount())
        assertEquals(identities, store.identityCount())
        assertEquals(commits, store.commitCount())
        assertEquals(seq, store.nextSequencePreview())
        assertEquals(order + 1, store.nextQueueOrderPreview())
    }

    @Test fun timelineMoveBeyond2200StagesDateDeadlineThroughCoreAndPreservesDatePrecision() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val originalDate = LocalDate.of(2200, 12, 31)
        val start = originalDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.DEADLINE,
            title = "Date deadline beyond bridge range",
            endText = originalDate.toString(),
            zoneId = zone.id
        ))
        val camera = TimelineMoveContext(TimelineCoordinate.Camera(start, 24.0), zone,
            mapOf(original.eventSeq to original.revision))
        val targetDate = LocalDate.of(2201, 1, 1)
        val target = targetDate.atTime(10, 8).atZone(zone).toInstant().toEpochMilli()
        val expectedTarget = TimelineCoordinate.snap(target, 24.0, zone)
        val requestId = request()

        val result = service.execute(WebEditCommand.MoveTimelineEvent(
            requestId, original.eventSeq, original.revision, target
        ), timelineMoveContext = camera)

        assertEquals("staged", result.getString("status"))
        assertEquals(1, timelineMoveCalls.size)
        val apiCall = timelineMoveCalls.single()
        assertEquals(original.eventSeq, apiCall.eventSeq)
        assertEquals(original.revision, apiCall.expectedRevision)
        assertEquals(expectedTarget, apiCall.targetMillis)
        assertEquals(requestId, apiCall.requestId)
        assertEquals(EventMutationStatusV1.STAGED, apiCall.receipt.status)
        assertEquals(result.getString("detail").toLong(), apiCall.receipt.draftId ?: -1L)

        val candidate = store.pendingDraftsFor(original.eventSeq).single()
        assertEquals(DraftOperation.REPLACE, candidate.operation)
        assertEquals(targetDate.toString(), candidate.form.endText)
        assertTrue(candidate.form.parseDeadline() is DeadlineValue.Date)
        val validated = candidate.form.build(original.eventSeq, original.revision + 1)
        assertTrue("2201 pure date deadline should pass Core form validation", validated is FormBuild.Ready)
        val moved = (validated as FormBuild.Ready).record
        assertEquals(targetDate.toString(), moved.deadlineDate)
        assertNull(moved.deadlineMillis)
        assertEquals(original, store.get(original.eventSeq))
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
        queriedDraftIds.clear()
        assertEquals("confirmation_required", service.execute(review).getString("status"))
        assertEquals(listOf(proposal.draftId), queriedDraftIds)
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
        queriedEventSequences.clear()
        val result = service.execute(WebEditCommand.MoveTimelineEvent(
            request(), original.eventSeq, original.revision, target
        ), timelineMoveContext = TimelineMoveContext(
            TimelineCoordinate.Camera(start, 24.0), zone,
            mapOf(original.eventSeq to original.revision)
        ))
        assertEquals("stale", result.getString("status"))
        assertEquals(listOf(original.eventSeq), queriedEventSequences)
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())
        assertEquals(beforeDrafts, store.draftCount())
        assertEquals(current, store.get(original.eventSeq))
    }

    @Test fun semanticFieldsPreserveProtectedMetadataAndRejectOutOfRangeValues() {
        val original=DraftForm(kind=TemporalKind.ACTIVITY,title="Original",
            source="imported-ics",zoneId="Asia/Shanghai")
        val reminded=patchForm(original,"reminderMinutes","15")
        assertEquals(15,reminded.reminderMinutes)
        val timed=patchForm(reminded,"reminderTiming","DAY_START")
        assertEquals("DAY_START",timed.reminderTiming.name)
        val daily=patchForm(timed,"recurrenceFreq","DAILY")
        val interval=patchForm(daily,"recurrenceInterval","2")
        val from=patchForm(interval,"recurrenceFrom","2026-10-22")
        assertEquals("DAILY",from.recurrence.freq.name)
        assertEquals(2,from.recurrence.interval)
        assertEquals("2026-10-22",from.recurrence.activeFromDate)
        assertEquals("imported-ics",from.source)
        assertEquals("Asia/Shanghai",from.zoneId)
        assertThrows(IllegalArgumentException::class.java) {
            patchForm(original,"reminderMinutes","10081")
        }
        assertThrows(IllegalArgumentException::class.java) {
            patchForm(original,"recurrenceInterval","0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            patchForm(original,"enabled","yes")
        }
        assertThrows(IllegalArgumentException::class.java) {
            patchForm(original,"source","forged")
        }
        assertNull(WebEditWire.decode(wire("deck.patch",
            ""","draftId":42,"draftVersion":1,"stateRevision":3,"field":"zoneId","value":"Etc/UTC"""")))
        assertNotNull(WebEditWire.decode(wire("deck.patch",
            ""","draftId":42,"draftVersion":1,"stateRevision":3,"field":"recurrenceFreq","value":"DAILY"""")))
    }

    @Test fun kindPatchUsesCoreConversionForBothEditorAndDeckCommands() {
        val original = DraftForm(kind = TemporalKind.ALARM, title = "Alarm",
            startText = "2026-10-22 07:30", reminderMinutes = -1,
            source = "imported-ics", zoneId = "Asia/Shanghai")
        TemporalKind.entries.forEach { target ->
            assertEquals(TemporalConversion.convert(original, target).form,
                patchForm(original, "kind", target.name))
        }
        assertThrows(IllegalArgumentException::class.java) {
            patchForm(original, "kind", "UNSUPPORTED")
        }
        val editorPayload = JSONObject().put("version", 1).put("type", "command")
            .put("payload", JSONObject().put("name", "editor.patch").put("requestId", request())
                .put("sessionId", request()).put("field", "kind").put("value", "ACTIVITY"))
        val deckPayload = JSONObject().put("version", 1).put("type", "command")
            .put("payload", JSONObject().put("name", "deck.patch").put("requestId", request())
                .put("draftId", 42).put("draftVersion", 1).put("stateRevision", 3).put("field", "kind")
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

    @Test fun formalRecurringDeleteCancelsTheWholeSeriesAndDiscardsItsPendingEdit() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val nextDate = LocalDate.now(zone).plusDays(1)
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "recurring series to delete",
            startText = "$nextDate 08:00",
            endText = "$nextDate 09:00",
            zoneId = zone.id,
            reminderMinutes = -1,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        val pending = store.newDraft(
            DraftForm.from(original).copy(title = "pending series edit"),
            original.eventSeq,
            DraftOperation.REPLACE
        )
        val commitsBeforeDelete = store.commitCount()
        val identitiesBeforeDelete = store.identityCount()

        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            request(), original.eventSeq, original.revision
        )).getString("status"))
        val sessionId = service.snapshot().getJSONObject("editor").getString("sessionId")
        val cancelled = WebEditCommand.DeleteEvent(request(), sessionId)
        assertEquals("confirmation_required", service.execute(cancelled).getString("status"))
        val cancellationReview = requireNotNull(service.pendingWriteReview)
        assertEquals(DraftOperation.DELETE, cancellationReview.operation)
        val reviewRecord = requireNotNull(cancellationReview.current)
        assertEquals(original, reviewRecord)
        assertEquals(original.recurrence, reviewRecord.recurrence)
        assertEquals("cancelled", service.resolveFormalWriteReview(
            cancellationReview.token, approved = false
        ).getString("status"))
        assertEquals(original, store.get(original.eventSeq))
        assertEquals(DraftStatus.PENDING, store.draft(pending.draftId)?.status)
        assertEquals(commitsBeforeDelete, store.commitCount())
        assertEquals(identitiesBeforeDelete, store.identityCount())
        assertTrue(service.snapshot().getJSONObject("editor").getBoolean("open"))

        val approved = WebEditCommand.DeleteEvent(request(), sessionId)
        assertEquals("confirmation_required", service.execute(approved).getString("status"))
        val approvalReview = requireNotNull(service.pendingWriteReview)
        assertEquals("saved", service.resolveFormalWriteReview(
            approvalReview.token, approved = true
        ).getString("status"))

        assertNull(store.get(original.eventSeq))
        assertTrue(store.all().isEmpty())
        assertTrue(store.isRetired(original.eventSeq))
        val tombstone = requireNotNull(store.getIncludingRetired(original.eventSeq))
        assertEquals(original.revision + 1, tombstone.revision)
        assertEquals(original.recurrence, tombstone.recurrence)
        assertEquals(DraftStatus.DISCARDED, store.draft(pending.draftId)?.status)
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())
        assertEquals(commitsBeforeDelete + 1, store.commitCount())
        assertEquals(identitiesBeforeDelete, store.identityCount())
        assertFalse(service.snapshot().getJSONObject("editor").getBoolean("open"))
        assertEquals("saved", service.execute(approved).getString("status"))
        assertEquals(commitsBeforeDelete + 1, store.commitCount())
    }

    @Test fun formalDeleteReviewRejectsChangedRevision() = runBlocking {
        val card = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "Concurrent edit"))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(card.eventSeq))
        val commitsBeforeDelete = store.commitCount()
        val identitiesBeforeDelete = store.identityCount()
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

        val closed = service.execute(WebEditCommand.CloseEvent(request(), sessionId))
        assertEquals("a clean stale editor must be dismissible without writing over the new revision",
            "closed", closed.getString("status"))
        assertFalse(service.snapshot().getJSONObject("editor").getBoolean("open"))
        assertEquals(latest, store.get(original.eventSeq))
        assertEquals(commitsBeforeDelete + 1, store.commitCount())
        assertEquals(identitiesBeforeDelete, store.identityCount())
    }

    @Test fun formalEditorSaveReviewRejectsChangedRevisionAndKeepsTheProposal() = runBlocking {
        val original = commitFormal(DraftForm(
            kind = TemporalKind.MEMO,
            title = "formal baseline",
            zoneId = "UTC"
        ))
        assertEquals("opened", service.execute(WebEditCommand.OpenEvent(
            request(), original.eventSeq, original.revision
        )).getString("status"))
        val sessionId = service.snapshot().getJSONObject("editor").getString("sessionId")
        assertEquals("edited", service.execute(WebEditCommand.PatchEvent(
            request(), sessionId, "title", "unapproved proposal"
        )).getString("status"))

        val save = WebEditCommand.SaveEvent(request(), sessionId)
        assertEquals("confirmation_required", service.execute(save).getString("status"))
        val review = requireNotNull(service.pendingWriteReview)
        val commitsBeforeConcurrentChange = store.commitCount()
        assertTrue(store.setCompleted(
            original.eventSeq, "single", completed = true, expectedRevision = original.revision
        ))

        val reply = service.resolveFormalWriteReview(review.token, approved = true)

        assertEquals("conflict", reply.getString("status"))
        val latest = requireNotNull(store.get(original.eventSeq))
        assertEquals(original.revision + 1, latest.revision)
        assertEquals(original.title, latest.title)
        assertEquals(setOf("single"), latest.completedKeys)
        assertEquals(commitsBeforeConcurrentChange + 1, store.commitCount())
        assertEquals(1L, store.identityCount())
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())

        val editor = service.snapshot().getJSONObject("editor")
        assertTrue(editor.getBoolean("open"))
        assertEquals("unapproved proposal", editor.getJSONObject("form").getString("title"))
        assertEquals("conflict", service.execute(save).getString("status"))

        assertEquals("a dirty stale editor must retain its form when close cannot checkpoint",
            "failed", service.execute(WebEditCommand.CloseEvent(request(), sessionId))
                .getString("status"))
        assertTrue(service.snapshot().getJSONObject("editor").getBoolean("open"))
        assertEquals("unapproved proposal", service.snapshot().getJSONObject("editor")
            .getJSONObject("form").getString("title"))
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

    @Test fun bridgeNewEventDefaultsUseOneCapturedClockAndCloseWithoutWrites() = runBlocking {
        val zone = ZoneId.of("Asia/Tokyo")
        val fixedClock = Clock.fixed(Instant.parse("2026-10-25T15:07:42.987Z"), ZoneOffset.UTC)
        val identities = store.identityCount()
        val nextSequence = store.nextSequencePreview()
        val nextQueueOrder = store.nextQueueOrderPreview()
        val commits = store.commitCount()
        val drafts = store.draftCount()

        coreFeatureApi = PowerCalCoreFeatureApiV1(store, fixedClock)
        service = WebSkinEditService(
            coreFeatureApi.adaptDeckController(controller), coreFeatureApi = coreFeatureApi,
            trustedDeleteApprovalIssuer = coreFeatureApi::issueFormalDeleteApproval
        )

        for (kind in TemporalKind.entries) {
            val opened = service.execute(
                WebEditCommand.CreateDeck(request(), kind),
                newEventZone = zone
            )
            assertEquals("created", opened.getString("status"))
            val deck = service.snapshot().getJSONObject("deck")
            val form = deck.getJSONObject("form")
            assertFalse("$kind defaults are not user edits", deck.getBoolean("dirty"))
            assertEquals(kind.name, form.getString("kind"))
            assertEquals(zone.id, form.getString("zoneId"))
            when (kind) {
                TemporalKind.ALARM -> assertEquals("2026-10-26 00:08", form.getString("startText"))
                TemporalKind.ACTIVITY -> {
                    assertEquals("2026-10-26 00:08", form.getString("startText"))
                    assertEquals("2026-10-26 01:08", form.getString("endText"))
                }
                TemporalKind.DEADLINE -> {
                    assertEquals("2026-10-26", form.getString("endText"))
                    assertEquals(0, form.getInt("reminderMinutes"))
                    assertEquals("DAY_START", form.getString("reminderTiming"))
                }
                TemporalKind.MEMO -> {
                    assertEquals("", form.getString("startText"))
                    assertEquals("", form.getString("endText"))
                }
            }
            assertEquals("closed", service.execute(WebEditCommand.CloseDeck(request()))
                .getString("status"))
        }

        assertEquals(identities, store.identityCount())
        assertEquals(nextSequence, store.nextSequencePreview())
        assertEquals(nextQueueOrder, store.nextQueueOrderPreview())
        assertEquals(commits, store.commitCount())
        assertEquals(drafts, store.draftCount())
        assertEquals(0, store.pendingCount())
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

    @Test fun unchangedWebNewEventUsesCoreNoChangeReceipt() = runBlocking {
        val created = service.execute(WebEditCommand.CreateDeck(request(), TemporalKind.ACTIVITY))
        val draftId = created.getString("detail").toLong()
        val version = service.snapshot().getJSONObject("deck").getLong("version")
        val requestId = request()
        val command = WebEditCommand.CommitDeck(requestId, draftId, version)

        assertEquals("no_changes", service.execute(command).getString("status"))
        assertEquals(1, savedNewEventReceipts.size)
        assertEquals(requestId, savedNewEventReceipts.single().requestId)
        assertEquals(EventMutationStatusV1.NO_CHANGE, savedNewEventReceipts.single().status)
        assertTrue(store.all().isEmpty())
        assertEquals(0, store.draftCount())
        assertEquals(0, store.identityCount())

        assertEquals("no_changes", service.execute(command).getString("status"))
        assertEquals("Exact Web request replay must not call the Core API again", 1,
            savedNewEventReceipts.size)
    }

    @Test fun equivalentReplacementStillUsesCoreReviewedCommitAndAdvancesRevision() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2031, 6, 12, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val original = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Unchanged replacement",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id
        ))
        val replacement = store.newDraft(
            DraftForm.from(original),
            eventSeq = original.eventSeq,
            operation = DraftOperation.REPLACE,
            expectedRevision = original.revision
        )
        val commitsBefore = store.commitCount()

        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(
            request(), replacement.draftId
        )).getString("status"))
        val version = service.snapshot().getJSONObject("deck").getLong("version")
        val command = WebEditCommand.CommitDeck(request(), replacement.draftId, version)

        assertEquals("confirmation_required", service.execute(command).getString("status"))
        assertEquals(original, store.get(original.eventSeq))
        assertEquals(commitsBefore, store.commitCount())

        val review = requireNotNull(service.pendingWriteReview)
        assertEquals("committed", service.resolveFormalWriteReview(review.token, approved = true)
            .getString("status"))
        val updated = requireNotNull(store.get(original.eventSeq))
        assertEquals(original.revision + 1, updated.revision)
        assertEquals(original.title, updated.title)
        assertEquals(commitsBefore + 1, store.commitCount())
        assertTrue(store.pendingDraftsFor(original.eventSeq).isEmpty())
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

    @Test fun incompletePersistedDraftUsesCoreReadinessWithoutOpeningApproval() = runBlocking {
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Incomplete persisted draft",
            startText = "2026-11-02 09:00",
            zoneId = testZone.id
        ))
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(
            request(), card.draftId
        )).getString("status"))
        val changes = mutableListOf<dev.powerfulcalendar.core.api.v1.EventFeatureChangeV1>()
        val subscription = coreFeatureApi.events.subscribe(changes::add)

        val version = service.snapshot().getJSONObject("deck").getLong("version")
        val result = service.execute(WebEditCommand.CommitDeck(request(), card.draftId, version))

        assertEquals("invalid", result.getString("status"))
        assertNull(service.pendingWriteReview)
        assertEquals(DraftStatus.INVALID, store.draft(card.draftId)?.status)
        assertNull(store.get(card.eventSeq))
        assertEquals(0, store.commitCount())
        assertEquals("INVALID", coreFeatureApi.adaptDeckController(controller)
            .snapshot.current?.status?.name)
        val draftChanges = changes.filter { it.type == EventFeatureChangeTypeV1.DRAFTS_CHANGED }
        assertEquals(1, draftChanges.size)
        assertNull(draftChanges.single().eventSeq)
        subscription.close()
    }

    @Test fun deleteReadinessDoesNotRequireItsFormAndStillWaitsForHostApproval() = runBlocking {
        val formal = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Delete target",
            startText = "2026-11-02 09:00",
            endText = "2026-11-02 10:00",
            zoneId = testZone.id
        ))
        val commitsBeforeDelete = store.commitCount()
        val delete = store.newDraft(
            DraftForm(kind = TemporalKind.ACTIVITY, zoneId = testZone.id),
            formal.eventSeq, DraftOperation.DELETE, expectedRevision = formal.revision
        )
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(
            request(), delete.draftId
        )).getString("status"))

        val version = service.snapshot().getJSONObject("deck").getLong("version")
        val publish = WebEditCommand.CommitDeck(request(), delete.draftId, version)
        assertEquals("confirmation_required", service.execute(publish).getString("status"))
        assertEquals(formal, store.get(formal.eventSeq))
        assertEquals(commitsBeforeDelete, store.commitCount())
        assertEquals("committed", service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token, approved = true
        ).getString("status"))
        assertNull(store.get(formal.eventSeq))
        assertTrue(store.isRetired(formal.eventSeq))
        assertEquals(commitsBeforeDelete + 1, store.commitCount())
    }

    @Test fun scopedDeleteReadinessStopsBeforeHostApproval() = runBlocking {
        val formal = commitFormal(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Scoped delete target",
            startText = "2026-11-02 09:00",
            endText = "2026-11-02 10:00",
            zoneId = testZone.id
        ))
        val commitsBeforeDelete = store.commitCount()
        val delete = store.newDraft(
            DraftForm(kind = TemporalKind.ACTIVITY, zoneId = testZone.id),
            formal.eventSeq, DraftOperation.DELETE, expectedRevision = formal.revision
        )
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(
            request(), delete.draftId
        )).getString("status"))

        val api = coreFeatureApi.adaptDeckController(controller)
        val beforeEdit = api.snapshot
        val scopedForm = requireNotNull(beforeEdit.form).copy(
            recurrenceScope = EventRecurrenceScopeV1.SINGLE_OCCURRENCE,
            editOccurrenceKey = "2026-10-26T09:00"
        )
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.editCurrentDraft(
            delete.draftId, beforeEdit.version, beforeEdit.stateRevision, scopedForm
        ).status)
        assertEquals(DraftReviewReceiptStatusV1.APPLIED, api.flush("stage-scoped-delete").status)
        assertEquals("INVALID", api.snapshot.currentReadiness?.status?.name)

        val version = service.snapshot().getJSONObject("deck").getLong("version")
        val result = service.execute(WebEditCommand.CommitDeck(request(), delete.draftId, version))
        assertEquals("invalid", result.getString("status"))
        assertNull(service.pendingWriteReview)
        assertEquals(formal, store.get(formal.eventSeq))
        assertEquals(commitsBeforeDelete, store.commitCount())
    }

    @Test fun incompleteWebPublishStagesAndResumesEveryEventKindWithoutDuplicateWrites() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)
        val zone = ZoneId.of("Asia/Tokyo")
        coreFeatureApi = PowerCalCoreFeatureApiV1(store, clock)
        service = WebSkinEditService(
            coreFeatureApi.adaptDeckController(controller), coreFeatureApi = coreFeatureApi,
            trustedDeleteApprovalIssuer = coreFeatureApi::issueFormalDeleteApproval
        )

        TemporalKind.entries.forEachIndexed { index, kind ->
            val created = service.execute(
                WebEditCommand.CreateDeck(request(), kind),
                newEventZone = zone
            )
            assertEquals("created", created.getString("status"))
            val clientId = created.getString("detail").toLong()
            var version = service.snapshot().getJSONObject("deck").getLong("version")
            val note = "recoverable-${kind.name}"
            assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
                request(), clientId, version, "notes", note
            )).getString("status"))

            val stagedReply = service.execute(WebEditCommand.CommitDeck(request(), clientId, version))
            assertEquals("staged", stagedReply.getString("status"))
            assertEquals(clientId.toString(), stagedReply.getString("detail"))
            val staged = requireNotNull(store.pendingDrafts().singleOrNull { it.form.notes == note })
            assertEquals(kind, staged.form.kind)
            assertEquals("incomplete $kind must remain unpublished", index, store.all().size)
            assertEquals(index.toLong(), store.commitCount())
            assertEquals(1, store.pendingCount())
            assertEquals(index + 1L, store.draftCount())
            assertEquals(index + 1L, store.identityCount())

            version = service.snapshot().getJSONObject("deck").getLong("version")
            val title = "完成-${kind.name}"
            assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
                request(), clientId, version, "title", title
            )).getString("status"))
            version = service.snapshot().getJSONObject("deck").getLong("version")
            val publish = WebEditCommand.CommitDeck(request(), clientId, version)
            assertEquals("confirmation_required", service.execute(publish).getString("status"))
            assertEquals("$kind must not publish before trusted Host approval", index,
                store.all().size)
            assertEquals("committed", service.resolveFormalWriteReview(
                requireNotNull(service.pendingWriteReview).token, approved = true
            ).getString("status"))
            assertEquals("committed", service.execute(publish).getString("status"))

            val record = requireNotNull(store.get(staged.eventSeq))
            assertEquals(kind, record.kind)
            assertEquals(title, record.title)
            assertEquals(note, record.notes)
            assertEquals(zone.id, record.zoneId)
            when (kind) {
                TemporalKind.ALARM -> {
                    assertNotNull(record.startMillis)
                    assertNull(record.endMillis)
                }
                TemporalKind.ACTIVITY -> {
                    assertTrue(requireNotNull(record.endMillis) > requireNotNull(record.startMillis))
                }
                TemporalKind.DEADLINE -> {
                    assertNotNull(record.deadlineDate)
                    assertNull(record.deadlineMillis)
                }
                TemporalKind.MEMO -> {
                    assertNull(record.memoStartDate)
                    assertNull(record.memoEndDate)
                }
            }
            assertTrue("$kind should leave no pending draft after publish",
                store.pendingDrafts().isEmpty())
        }

        assertEquals(TemporalKind.entries.size, store.all().size)
        assertEquals(TemporalKind.entries.size.toLong(), store.draftCount())
        assertEquals(0, store.pendingCount())
        assertEquals(TemporalKind.entries.size.toLong(), store.identityCount())
        assertEquals(TemporalKind.entries.size.toLong(), store.commitCount())
        assertEquals(TemporalKind.entries.size.toLong() + 1L, store.nextSequencePreview())
        assertEquals(TemporalKind.entries.size.toLong() + 1L, store.nextQueueOrderPreview())
    }

    @Test fun incompleteTransientSaveReportsAiLockAndRecoversTheSameFormAfterUnlock() = runBlocking {
        val created = service.execute(WebEditCommand.CreateDeck(request(), TemporalKind.MEMO))
        val clientId = created.getString("detail").toLong()
        val version = service.snapshot().getJSONObject("deck").getLong("version")
        val note = "must survive the AI batch lock"
        assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
            request(), clientId, version, "notes", note
        )).getString("status"))

        val seed = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "AI batch seed"))
        val ai = store.beginAiBatch(
            mapOf(seed.draftId to seed.draftVersion), setOf(DraftOperation.CREATE)
        )
        val sequenceBefore = store.nextSequencePreview()
        val queueBefore = store.nextQueueOrderPreview()
        queriedBatchLockStates.clear()
        val blocked = service.execute(WebEditCommand.CommitDeck(request(), clientId, version))
        assertEquals("locked", blocked.getString("status"))
        assertEquals(listOf(true), queriedBatchLockStates)
        assertNull(service.pendingWriteReview)
        assertTrue(store.isLocked())
        assertEquals(1L, store.draftCount())
        assertEquals(1L, store.identityCount())
        assertEquals(sequenceBefore, store.nextSequencePreview())
        assertEquals(queueBefore, store.nextQueueOrderPreview())
        assertEquals(0L, store.commitCount())
        val lockedDeck = service.snapshot().getJSONObject("deck")
        assertTrue(lockedDeck.getBoolean("open"))
        assertEquals(note, lockedDeck.getJSONObject("form").getString("notes"))

        assertTrue(store.finishAiBatch(ai.sessionId))
        queriedBatchLockStates.clear()
        val stagedReply = service.execute(WebEditCommand.CommitDeck(request(), clientId, version))
        assertEquals("staged", stagedReply.getString("status"))
        assertEquals(listOf(false), queriedBatchLockStates)
        val staged = requireNotNull(store.pendingDrafts().singleOrNull { it.form.notes == note })
        val nextVersion = service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
            request(), clientId, nextVersion, "title", "完成恢复"
        )).getString("status"))

        val publish = WebEditCommand.CommitDeck(
            request(), clientId, service.snapshot().getJSONObject("deck").getLong("version")
        )
        assertEquals("confirmation_required", service.execute(publish).getString("status"))
        assertEquals("committed", service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token, approved = true
        ).getString("status"))
        assertEquals(staged.eventSeq, store.all().single().eventSeq)
        assertEquals("完成恢复", store.get(staged.eventSeq)?.title)
        assertEquals(note, store.get(staged.eventSeq)?.notes)
        assertEquals(2L, store.draftCount())
        assertEquals(1, store.pendingCount())
        assertEquals(2L, store.identityCount())
        assertEquals(1L, store.commitCount())
        assertEquals(sequenceBefore + 1L, store.nextSequencePreview())
        assertEquals(queueBefore + 1L, store.nextQueueOrderPreview())
    }

    @Test fun completeTransientPublishWaitsForAiLockToClearBeforeHostReview() = runBlocking {
        val created = service.execute(WebEditCommand.CreateDeck(request(), TemporalKind.ALARM))
        val clientId = created.getString("detail").toLong()
        val version = service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
            request(), clientId, version, "title", "发布应等待解锁"
        )).getString("status"))

        val seed = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "AI batch seed"))
        val ai = store.beginAiBatch(
            mapOf(seed.draftId to seed.draftVersion), setOf(DraftOperation.CREATE)
        )
        val sequenceBefore = store.nextSequencePreview()
        val queueBefore = store.nextQueueOrderPreview()
        val blocked = service.execute(WebEditCommand.CommitDeck(request(), clientId, version))
        assertEquals("locked", blocked.getString("status"))
        assertNull(service.pendingWriteReview)
        assertTrue(store.all().isEmpty())
        assertEquals(1L, store.draftCount())
        assertEquals(1L, store.identityCount())
        assertEquals(sequenceBefore, store.nextSequencePreview())
        assertEquals(queueBefore, store.nextQueueOrderPreview())

        assertTrue(store.finishAiBatch(ai.sessionId))
        val publish = WebEditCommand.CommitDeck(request(), clientId, version)
        assertEquals("confirmation_required", service.execute(publish).getString("status"))
        assertTrue(store.all().isEmpty())
        assertEquals("committed", service.resolveFormalWriteReview(
            requireNotNull(service.pendingWriteReview).token, approved = true
        ).getString("status"))
        assertEquals("committed", service.execute(publish).getString("status"))
        assertEquals("发布应等待解锁", store.all().single().title)
        assertEquals(1, store.all().size)
        assertEquals(1L, store.draftCount())
        assertEquals(1, store.pendingCount())
        assertEquals(2L, store.identityCount())
        assertEquals(1L, store.commitCount())
        assertEquals(sequenceBefore + 1L, store.nextSequencePreview())
        assertEquals(queueBefore, store.nextQueueOrderPreview())
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

    @Test fun clearDeckCheckpointRecoversThroughFreshBridgeAfterStoreReopen() = runBlocking {
        val card = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "before checkpoint"))
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), card.draftId))
            .getString("status"))
        val version = service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("edited", service.execute(WebEditCommand.PatchDeck(
            request(), card.draftId, version, "title", "checkpoint survives bridge recreation"
        )).getString("status"))
        assertTrue(service.checkpoint())
        val database = store.databaseName
        assertEquals("checkpoint survives bridge recreation", store.draft(card.draftId)!!.form.title)
        assertEquals(0, store.all().size)
        assertEquals(0, store.commitCount())

        store.close()
        store = PowerStore(context, database)
        controller = DeckController(StoreDeckPersistence(store))
        coreFeatureApi = PowerCalCoreFeatureApiV1(store)
        service = WebSkinEditService(
            coreFeatureApi.adaptDeckController(controller), coreFeatureApi = coreFeatureApi,
            trustedDeleteApprovalIssuer = coreFeatureApi::issueFormalDeleteApproval
        )

        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), card.draftId))
            .getString("status"))
        val recovered = service.snapshot().getJSONObject("deck")
        assertEquals(card.draftId, recovered.getLong("currentId"))
        assertEquals("checkpoint survives bridge recreation",
            recovered.getJSONObject("form").getString("title"))
        assertEquals(1L, recovered.getLong("version"))
        assertEquals(1, recovered.getInt("cardCount"))
        assertEquals(1, store.pendingCount())
        assertEquals(0, store.commitCount())
        assertTrue(store.all().isEmpty())
    }

    @Test fun clearDeckProjectsCompletionAndKeepsUndoSessionScoped() = runBlocking {
        var now = 1_000L
        controller = DeckController(StoreDeckPersistence(store), clock = { now })
        service = WebSkinEditService(
            coreFeatureApi.adaptDeckController(controller), coreFeatureApi = coreFeatureApi,
            trustedDeleteApprovalIssuer = coreFeatureApi::issueFormalDeleteApproval
        )
        val card = store.newDraft(DraftForm(kind = TemporalKind.MEMO, title = "only pending draft"))
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), card.draftId))
            .getString("status"))
        val version = service.snapshot().getJSONObject("deck").getLong("version")
        assertEquals("discarded", service.execute(WebEditCommand.DiscardDeck(
            request(), card.draftId, version
        )).getString("status"))

        val discarded = service.snapshot().getJSONObject("deck")
        assertEquals(0, discarded.getInt("cardCount"))
        assertTrue(discarded.isNull("currentId"))
        assertEquals(card.draftId, discarded.getLong("lastDiscardId"))
        assertEquals(1, discarded.getJSONObject("completion").getInt("discarded"))
        assertEquals(DraftStatus.DISCARDED, store.draft(card.draftId)!!.status)

        assertEquals("restored", service.execute(WebEditCommand.UndoDeck(request()))
            .getString("status"))
        val restored = service.snapshot().getJSONObject("deck")
        assertEquals(card.draftId, restored.getLong("currentId"))
        assertEquals(1, restored.getInt("cardCount"))
        assertEquals("only pending draft", restored.getJSONObject("form").getString("title"))
        assertTrue(restored.isNull("completion"))
        assertTrue(restored.isNull("lastDiscardId"))
        assertEquals(DraftStatus.PENDING, store.draft(card.draftId)!!.status)
        assertEquals(0, store.commitCount())
        assertTrue(store.all().isEmpty())

        now += 601L // advance the controller's deterministic duplicate-action guard
        val restoredVersion = restored.getLong("version")
        assertEquals("discarded", service.execute(WebEditCommand.DiscardDeck(
            request(), card.draftId, restoredVersion
        )).getString("status"))
        assertEquals("closed", service.execute(WebEditCommand.CloseDeck(request()))
            .getString("status"))
        assertEquals("opened", service.execute(WebEditCommand.OpenDeck(request(), card.draftId))
            .getString("status"))
        val reopened = service.snapshot().getJSONObject("deck")
        assertTrue(reopened.isNull("lastDiscardId"))
        assertTrue(reopened.isNull("completion"))
        assertTrue(reopened.isNull("currentId"))
        assertNotEquals("restored", service.execute(WebEditCommand.UndoDeck(request()))
            .getString("status"))
        assertEquals(DraftStatus.DISCARDED, store.draft(card.draftId)!!.status)
        assertEquals(0, store.commitCount())
        assertTrue(store.all().isEmpty())
    }
}
