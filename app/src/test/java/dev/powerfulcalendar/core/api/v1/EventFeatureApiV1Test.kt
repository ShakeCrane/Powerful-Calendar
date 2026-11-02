package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.RecurrenceEngine
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1
    private lateinit var api: EventFeatureApiV1
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "event-feature-api-${UUID.randomUUID()}.db")
        core = PowerCalCoreFeatureApiV1(store, clock)
        api = core.events
    }

    @After
    fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test
    fun convertFormKindUsesCoreRulesAndReturnsRequiredFields() {
        val alarm = DraftForm(
            kind = TemporalKind.ALARM,
            title = "Morning alarm",
            startText = "2026-10-22 07:30",
            reminderMinutes = -1,
            source = "imported-ics",
            zoneId = zone.id
        ).toEventFormV1()

        val activity = api.convertFormKind(alarm, EventKindV1.ACTIVITY)
        assertEquals(EventKindV1.ACTIVITY, activity.form.kind)
        assertEquals("2026-10-22 07:30", activity.form.startText)
        assertEquals("", activity.form.endText)
        assertEquals("imported-ics", activity.form.source)
        assertEquals(zone.id, activity.form.zoneId)
        assertEquals(listOf("END"), activity.requiredFields)
        assertEquals("转换为活动需要补全结束时间；原有时刻已保留为开始。", activity.note)

        val dateDeadline = DraftForm(
            kind = TemporalKind.DEADLINE,
            title = "Submit form",
            endText = "2026-10-23",
            reminderMinutes = 0,
            zoneId = zone.id
        ).toEventFormV1()
        val alarmFromDate = api.convertFormKind(dateDeadline, EventKindV1.ALARM)
        assertEquals("", alarmFromDate.form.startText)
        assertEquals("", alarmFromDate.form.endText)
        assertEquals(listOf("START"), alarmFromDate.requiredFields)

        val unchanged = api.convertFormKind(alarm, EventKindV1.ALARM)
        assertEquals(alarm, unchanged.form)
        assertTrue(unchanged.requiredFields.isEmpty())
        assertEquals("", unchanged.note)
    }

    @Test
    fun memoCompletionTrackingIsIntrinsicAcrossFeatureFormUpdates() {
        val newEditor = openNewEvent(EventKindV1.MEMO)
        val newUpdate = api.updateNewEvent(
            newEditor.handle,
            newEditor.form.copy(title = "Intrinsic completion tracking", trackingEnabled = false)
        )
        assertEquals(EventEditorUpdateStatusV1.UPDATED, newUpdate.status)
        val normalizedNewForm = requireNotNull(api.editorSnapshot(newEditor.handle)).form
        assertTrue(normalizedNewForm.trackingEnabled)

        val created = api.saveNewEvent(
            newEditor.handle, normalizedNewForm, "memo-intrinsic-tracking-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val event = requireNotNull(store.get(requireNotNull(created.eventSeq)))
        assertTrue(event.trackingEnabled)

        val formalEditor = openFormalEvent(event.eventSeq, event.revision)
        val formalUpdate = api.updateFormalEvent(
            formalEditor.handle, formalEditor.form.copy(trackingEnabled = false)
        )
        assertEquals(EventEditorUpdateStatusV1.UPDATED, formalUpdate.status)
        assertTrue(requireNotNull(api.editorSnapshot(formalEditor.handle)).form.trackingEnabled)
        api.closeFormalEvent(formalEditor.handle)
    }

    @Test
    fun recurrenceEditorSnapshotOwnsSingleOccurrenceFieldCapability() {
        val start = LocalDate.of(2026, 10, 20).atTime(7, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "Recurring capability event",
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id,
            recurrence = dev.powerfulcalendar.core.Recurrence(
                freq = dev.powerfulcalendar.core.RecurrenceFreq.DAILY,
                count = 3
            )
        ))
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)
        val formal = requireNotNull(store.get(card.eventSeq))
        val key = "2026-10-21T07:30"
        val editor = opened(api.openFormalEvent(formal.eventSeq, formal.revision, key))
        assertTrue(EventCapabilityV1.RECURRENCE_EDIT in editor.capabilities)
        assertFalse(
            EventCapabilityV1.EDIT_SINGLE_OCCURRENCE_TITLE_AND_TIME_ONLY in editor.capabilities
        )

        assertEquals(
            EventEditorUpdateStatusV1.UPDATED,
            api.selectFormalRecurrenceScope(
                editor.handle, EventRecurrenceScopeV1.SINGLE_OCCURRENCE
            ).status
        )
        val single = requireNotNull(api.editorSnapshot(editor.handle))
        assertEquals(
            EventRecurrenceScopeV1.SINGLE_OCCURRENCE,
            single.form.recurrenceScope
        )
        assertTrue(
            EventCapabilityV1.EDIT_SINGLE_OCCURRENCE_TITLE_AND_TIME_ONLY in single.capabilities
        )

        assertEquals(
            EventEditorUpdateStatusV1.UPDATED,
            api.selectFormalRecurrenceScope(
                editor.handle, EventRecurrenceScopeV1.WHOLE_SERIES
            ).status
        )
        val whole = requireNotNull(api.editorSnapshot(editor.handle))
        assertFalse(
            EventCapabilityV1.EDIT_SINGLE_OCCURRENCE_TITLE_AND_TIME_ONLY in whole.capabilities
        )

        assertEquals(
            EventEditorUpdateStatusV1.UPDATED,
            api.selectFormalRecurrenceScope(
                editor.handle, EventRecurrenceScopeV1.THIS_AND_FUTURE
            ).status
        )
        val future = requireNotNull(api.editorSnapshot(editor.handle))
        assertFalse(
            EventCapabilityV1.EDIT_SINGLE_OCCURRENCE_TITLE_AND_TIME_ONLY in future.capabilities
        )
        api.closeFormalEvent(editor.handle)
    }

    @Test
    fun oneFeatureApiSupportsQueryCreateEditDeleteAndIdempotentReplay() {
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = api.subscribe(changes::add)
        try {
            assertEquals(1, api.apiVersion)
            assertTrue(api.queryEvents().events.isEmpty())

            val createEditor = openNewEvent(
                EventKindV1.ACTIVITY,
                EventSelectionV1(
                    instantMillis = Instant.parse("2026-10-26T01:00:00Z").toEpochMilli()
                )
            )
            assertEquals(clock.millis(), createEditor.defaultNowMillis)
            val proposed = createEditor.form.copy(
                title = "Core API event",
                startText = "2026-10-26 09:00",
                endText = "2026-10-26 10:00",
                source = "untrusted-skin",
                zoneId = "UTC",
                completedKeys = setOf("2026-10-26T09:00")
            )
            assertEquals(
                EventEditorUpdateStatusV1.UPDATED,
                api.updateNewEvent(createEditor.handle, proposed).status
            )
            val sanitized = requireNotNull(api.editorSnapshot(createEditor.handle)).form
            assertEquals("local", sanitized.source)
            assertEquals(zone.id, sanitized.zoneId)
            assertTrue(sanitized.completedKeys.isEmpty())

            val created = api.saveNewEvent(createEditor.handle, proposed, "create-1")
            assertEquals(EventMutationStatusV1.CREATED, created.status)
            assertEquals(1L, created.eventSeq)
            assertEquals(1L, created.revision)
            assertEquals("Core API event", store.get(1L)?.title)
            assertEquals(zone.id, store.get(1L)?.zoneId)
            assertEquals(1L, store.identityCount())
            assertEquals(1L, store.commitCount())
            val query = api.queryEvents()
            assertEquals(1L, query.events.single().eventSeq)
            assertEquals("local", query.events.single().source)
            assertEquals(null, api.editorSnapshot(createEditor.handle))

            val replayedCreate = api.saveNewEvent(createEditor.handle, proposed, "create-1")
            assertEquals(EventMutationStatusV1.CREATED, replayedCreate.status)
            assertTrue(replayedCreate.duplicate)
            assertEquals(1L, store.identityCount())
            assertEquals(1L, store.commitCount())

            assertEquals(
                EventEditorOpenResultV1.Stale(1L),
                api.openFormalEvent(1L, expectedRevision = 2L)
            )
            val editEditor = openFormalEvent(1L, expectedRevision = 1L)
            val edited = editEditor.form.copy(
                title = "Updated through shared API",
                source = "untrusted-skin",
                zoneId = "UTC",
                completedKeys = setOf("forged")
            )
            assertEquals(
                EventEditorUpdateStatusV1.UPDATED,
                api.updateFormalEvent(editEditor.handle, edited).status
            )
            val safeEdit = requireNotNull(api.editorSnapshot(editEditor.handle)).form
            assertEquals("local", safeEdit.source)
            assertEquals(zone.id, safeEdit.zoneId)
            assertTrue(safeEdit.completedKeys.isEmpty())

            val updated = api.saveFormalEvent(editEditor.handle, edited, "edit-1")
            assertEquals(EventMutationStatusV1.UPDATED, updated.status)
            assertEquals(2L, updated.revision)
            assertEquals("Updated through shared API", store.get(1L)?.title)
            assertEquals(2L, store.commitCount())
            api.closeFormalEvent(editEditor.handle)
            val replayedUpdate = api.saveFormalEvent(editEditor.handle, edited, "edit-1")
            assertEquals(EventMutationStatusV1.UPDATED, replayedUpdate.status)
            assertTrue(replayedUpdate.duplicate)
            assertEquals(2L, store.commitCount())

            val deleteEditor = openFormalEvent(1L, expectedRevision = 2L)
            val forgedApproval = object : EventDeleteApprovalV1 {
                override val eventSeq = 1L
                override val expectedRevision = 2L
            }
            val rejectedDelete = api.deleteFormalEvent(deleteEditor.handle, forgedApproval, "forged")
            assertEquals(EventMutationStatusV1.NOT_AUTHORIZED, rejectedDelete.status)
            assertEquals("Updated through shared API", store.get(1L)?.title)

            val approval = requireNotNull(core.issueFormalDeleteApproval(deleteEditor.handle))
            val deleted = api.deleteFormalEvent(deleteEditor.handle, approval, "delete-1")
            assertEquals(EventMutationStatusV1.DELETED, deleted.status)
            assertEquals(3L, deleted.revision)
            assertTrue(store.isRetired(1L))
            assertTrue(api.queryEvents().events.isEmpty())
            assertEquals(3L, store.commitCount())

            api.closeFormalEvent(deleteEditor.handle)
            val replayedDelete = api.deleteFormalEvent(deleteEditor.handle, approval, "delete-1")
            assertEquals(EventMutationStatusV1.DELETED, replayedDelete.status)
            assertTrue(replayedDelete.duplicate)
            assertEquals(3L, store.commitCount())
            assertEquals(
                listOf(
                    EventFeatureChangeTypeV1.FORMAL_CREATED,
                    EventFeatureChangeTypeV1.FORMAL_UPDATED,
                    EventFeatureChangeTypeV1.FORMAL_DELETED
                ),
                changes.map { it.type }.filter { it != EventFeatureChangeTypeV1.DRAFTS_CHANGED }
            )
            assertTrue(changes.zipWithNext().all { (a, b) -> b.featureRevision > a.featureRevision })
            assertFalse(api.isFormalEventCurrent(deleteEditor.handle))
        } finally {
            subscription.close()
        }
    }

    @Test
    fun queryEventReturnsFreshDetachedRecordAndHidesMissingOrRetiredEvents() {
        val createEditor = openNewEvent(EventKindV1.ACTIVITY)
        val created = api.saveNewEvent(
            createEditor.handle, createEditor.form.copy(title = "Queryable event"),
            "query-event-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val eventSeq = requireNotNull(created.eventSeq)
        val original = requireNotNull(api.queryEvent(eventSeq))
        assertEquals("Queryable event", original.title)
        assertEquals(created.revision, original.revision)
        assertEquals(null, api.queryEvent(Long.MAX_VALUE))
        assertEquals(null, api.queryEvent(0L))

        val edit = openFormalEvent(eventSeq, original.revision)
        val updated = api.saveFormalEvent(
            edit.handle, edit.form.copy(title = "Updated event"), "query-event-update"
        )
        assertEquals(EventMutationStatusV1.UPDATED, updated.status)
        assertEquals("Queryable event", original.title)
        api.closeFormalEvent(edit.handle)
        val fresh = requireNotNull(api.queryEvent(eventSeq))
        assertEquals("Updated event", fresh.title)
        assertEquals(updated.revision, fresh.revision)

        val deleteEditor = openFormalEvent(eventSeq, requireNotNull(updated.revision))
        val approval = requireNotNull(core.issueFormalDeleteApproval(deleteEditor.handle))
        val deleted = api.deleteFormalEvent(deleteEditor.handle, approval, "query-event-delete")
        assertEquals(EventMutationStatusV1.DELETED, deleted.status)
        assertEquals(null, api.queryEvent(eventSeq))
    }

    @Test
    fun storeBackedApiRecoversOnlyForeignStaleHumanEditLeases() {
        val database = "event-lease-recovery-${UUID.randomUUID()}.db"
        val owner = PowerStore(context, database, "lease-owner")
        val reaper = PowerStore(context, database, "lease-reaper")
        val eventApi = PowerCalCoreFeatureApiV1(reaper, clock).events
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = eventApi.subscribe(changes::add)
        try {
            assertFalse(eventApi.recoverAbandonedHumanEdit())

            owner.beginHumanEdit()
            assertFalse(eventApi.recoverAbandonedHumanEdit())
            assertTrue(owner.hasHumanEditor())
            assertTrue(runCatching {
                eventApi.recoverAbandonedHumanEdit(staleAfterMillis = 59_999L)
            }.isFailure)
            assertTrue(owner.hasHumanEditor())

            owner.writableDatabase.execSQL(
                "UPDATE human_editor SET touched_ms=? WHERE id=1",
                arrayOf(System.currentTimeMillis() - 4 * 60_000L)
            )
            assertFalse(eventApi.recoverAbandonedHumanEdit())
            assertTrue(owner.hasHumanEditor())

            owner.writableDatabase.execSQL(
                "UPDATE human_editor SET touched_ms=? WHERE id=1",
                arrayOf(System.currentTimeMillis() - 6 * 60_000L)
            )
            assertTrue(eventApi.recoverAbandonedHumanEdit())
            assertFalse(owner.hasHumanEditor())
            assertFalse(eventApi.recoverAbandonedHumanEdit())

            owner.beginHumanEdit()
            owner.writableDatabase.execSQL(
                "UPDATE human_editor SET touched_ms=? WHERE id=1",
                arrayOf(System.currentTimeMillis() - 2 * 60_000L)
            )
            assertTrue(eventApi.recoverAbandonedHumanEdit(staleAfterMillis = 60_000L))
            assertFalse(owner.hasHumanEditor())

            assertEquals(0L, eventApi.featureRevision)
            assertTrue(changes.isEmpty())
        } finally {
            subscription.close()
            owner.close()
            reaper.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun timelineReplacementChecksRevisionAndReplaysOneDraftReceipt() {
        val createEditor = openNewEvent(EventKindV1.ACTIVITY)
        val form = createEditor.form.copy(
            title = "Move candidate",
            startText = "2026-10-26 09:00",
            endText = "2026-10-26 10:00"
        )
        val created = api.saveNewEvent(createEditor.handle, form, "create-for-move")
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val record = requireNotNull(store.get(requireNotNull(created.eventSeq)))
        val editEditor = openFormalEvent(record.eventSeq, record.revision)
        val moved = editEditor.form.copy(
            startText = "2026-10-26 11:00",
            endText = "2026-10-26 12:00"
        )

        val stale = api.stageFormalReplacement(
            eventSeq = record.eventSeq,
            expectedRevision = record.revision + 1,
            form = moved,
            requestId = "stale-move"
        )
        assertEquals(EventMutationStatusV1.CONFLICT, stale.status)
        assertTrue(store.pendingDraftsFor(record.eventSeq).isEmpty())

        val staged = api.stageFormalReplacement(
            record.eventSeq, record.revision, moved, "stage-move"
        )
        assertEquals(EventMutationStatusV1.STAGED, staged.status)
        assertEquals(record.eventSeq, staged.eventSeq)
        assertEquals(record.revision, store.get(record.eventSeq)?.revision)
        assertEquals(
            "2026-10-26 09:00",
            DraftForm.from(requireNotNull(store.get(record.eventSeq))).startText
        )
        assertEquals(
            "2026-10-26 11:00",
            store.pendingDraftsFor(record.eventSeq).single().form.startText
        )

        val replay = api.stageFormalReplacement(
            record.eventSeq, record.revision, moved, "stage-move"
        )
        assertEquals(EventMutationStatusV1.STAGED, replay.status)
        assertTrue(replay.duplicate)
        assertEquals(1, store.pendingDraftsFor(record.eventSeq).size)
    }

    @Test
    fun timelineMoveApiPreservesElapsedDurationAndChecksRevision() {
        val moveZone = ZoneId.of("America/Los_Angeles")
        val start = java.time.LocalDateTime.of(2030, 10, 21, 9, 0)
            .atZone(moveZone).toInstant().toEpochMilli()
        val editor = opened(api.openNewEvent(EventKindV1.ACTIVITY, moveZone.id))
        val created = api.saveNewEvent(
            editor.handle,
            editor.form.copy(
                title = "Activity move",
                startText = DraftForm.formatInstant(start, moveZone),
                endText = DraftForm.formatInstant(start + 5_400_000L, moveZone),
                zoneId = moveZone.id
            ),
            "timeline-move-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val record = requireNotNull(store.get(requireNotNull(created.eventSeq)))
        val target = start + 7_200_000L

        val editorBeforeConcurrentWrite = openFormalEvent(record.eventSeq, record.revision)
        val advanced = api.saveFormalEvent(
            editorBeforeConcurrentWrite.handle,
            editorBeforeConcurrentWrite.form.copy(notes = "revision advanced"),
            "timeline-move-advance-revision"
        )
        assertEquals(EventMutationStatusV1.UPDATED, advanced.status)
        val current = requireNotNull(store.get(record.eventSeq))
        assertEquals(record.revision + 1, current.revision)

        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = api.subscribe(changes::add)
        try {
            val stale = api.stageTimelineMove(
                record.eventSeq, record.revision, target, "timeline-move-stale"
            )
            assertEquals(EventMutationStatusV1.CONFLICT, stale.status)
            assertTrue(stale.refreshRequired)
            assertTrue(store.pendingDraftsFor(record.eventSeq).isEmpty())
            assertTrue(changes.isEmpty())

            val staged = api.stageTimelineMove(
                record.eventSeq, current.revision, target, "timeline-move-activity"
            )
            assertEquals(EventMutationStatusV1.STAGED, staged.status)
            val candidate = store.pendingDraftsFor(record.eventSeq).single()
            assertEquals(DraftForm.formatInstant(target, moveZone), candidate.form.startText)
            assertEquals(DraftForm.formatInstant(target + 5_400_000L, moveZone), candidate.form.endText)
            assertEquals(moveZone.id, candidate.form.zoneId)
            assertEquals(current.source, candidate.form.source)
            assertEquals("revision advanced", candidate.form.notes)
            assertEquals(current, store.get(record.eventSeq))
            assertEquals(1, changes.size)
            assertEquals(EventFeatureChangeTypeV1.DRAFTS_CHANGED, changes.single().type)

            val replay = api.stageTimelineMove(
                record.eventSeq, current.revision, target, "timeline-move-activity"
            )
            assertEquals(EventMutationStatusV1.STAGED, replay.status)
            assertTrue(replay.duplicate)
            assertEquals(staged.draftId, replay.draftId)
            assertEquals(1, changes.size)
            assertEquals(1, store.pendingDraftsFor(record.eventSeq).size)

            val rebound = api.stageTimelineMove(
                record.eventSeq, current.revision, target + 3_600_000L,
                "timeline-move-activity"
            )
            assertEquals(EventMutationStatusV1.DUPLICATE_REQUEST_ID, rebound.status)
            assertEquals(candidate.form, store.pendingDraftsFor(record.eventSeq).single().form)
            assertEquals(1, changes.size)
        } finally {
            subscription.close()
        }
    }

    @Test
    fun timelineMoveApiAllowsDateDeadlineAndKeepsDatePrecision() {
        val moveZone = ZoneId.of("America/New_York")
        val editor = opened(api.openNewEvent(EventKindV1.DEADLINE, moveZone.id))
        val created = api.saveNewEvent(
            editor.handle,
            editor.form.copy(
                title = "Date deadline move",
                endText = "2030-10-22",
                zoneId = moveZone.id
            ),
            "timeline-move-date-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val record = requireNotNull(store.get(requireNotNull(created.eventSeq)))

        val sameDayTarget = Instant.parse("2030-10-22T20:00:00Z").toEpochMilli()
        val unchanged = api.stageTimelineMove(
            record.eventSeq, record.revision, sameDayTarget, "timeline-move-date-unchanged"
        )
        assertEquals(EventMutationStatusV1.NO_CHANGE, unchanged.status)
        assertTrue(store.pendingDraftsFor(record.eventSeq).isEmpty())

        val nextDayTarget = Instant.parse("2030-10-24T01:00:00Z").toEpochMilli()
        val staged = api.stageTimelineMove(
            record.eventSeq, record.revision, nextDayTarget, "timeline-move-date"
        )
        assertEquals(EventMutationStatusV1.STAGED, staged.status)
        val candidate = store.pendingDraftsFor(record.eventSeq).single().form
        assertEquals("2030-10-23", candidate.endText)
        assertTrue(candidate.parseDeadline() is dev.powerfulcalendar.core.DeadlineValue.Date)
        assertEquals("2030-10-22", record.deadlineDate)
        assertEquals(record, store.get(record.eventSeq))
    }

    @Test
    fun timelineMoveDateDeadlineHasNo1900LowerBoundWhenTheFormIsValid() {
        val moveZone = ZoneId.of("UTC")
        val editor = opened(api.openNewEvent(EventKindV1.DEADLINE, moveZone.id))
        val created = api.saveNewEvent(
            editor.handle,
            editor.form.copy(
                title = "Date deadline before former UI range",
                endText = "1900-01-01",
                zoneId = moveZone.id
            ),
            "timeline-move-date-lower-bound-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val record = requireNotNull(store.get(requireNotNull(created.eventSeq)))
        val targetMillis = LocalDate.of(1899, 12, 31).atTime(12, 0)
            .atZone(moveZone).toInstant().toEpochMilli()

        val moved = api.stageTimelineMove(
            record.eventSeq, record.revision, targetMillis, "timeline-move-date-1899"
        )
        assertEquals(EventMutationStatusV1.STAGED, moved.status)
        val candidate = store.pendingDraftsFor(record.eventSeq).single().form
        assertEquals("1899-12-31", candidate.endText)
        assertTrue(candidate.parseDeadline() is dev.powerfulcalendar.core.DeadlineValue.Date)
        val validated = candidate.build(record.eventSeq, record.revision + 1)
        assertTrue(validated is dev.powerfulcalendar.core.FormBuild.Ready)
        val movedRecord = (validated as dev.powerfulcalendar.core.FormBuild.Ready).record
        assertEquals("1899-12-31", movedRecord.deadlineDate)
        assertNull(movedRecord.deadlineMillis)
        assertEquals(record, store.get(record.eventSeq))
    }

    @Test
    fun timelineMoveApiRejectsRepeatingEventsAndMemosWithoutWrites() {
        val start = java.time.LocalDateTime.of(2030, 10, 21, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val repeatingEditor = openNewEvent(EventKindV1.ACTIVITY)
        val repeatingCreated = api.saveNewEvent(
            repeatingEditor.handle,
            repeatingEditor.form.copy(
                title = "Repeating move",
                startText = DraftForm.formatInstant(start, zone),
                endText = DraftForm.formatInstant(start + 3_600_000L, zone),
                recurrence = EventRecurrenceV1(
                    frequency = EventRecurrenceFrequencyV1.DAILY,
                    interval = 1,
                    count = 2
                )
            ),
            "timeline-move-repeating-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, repeatingCreated.status)
        val repeating = requireNotNull(store.get(requireNotNull(repeatingCreated.eventSeq)))

        val datedMemoEditor = openNewEvent(EventKindV1.MEMO)
        val datedMemoCreated = api.saveNewEvent(
            datedMemoEditor.handle,
            datedMemoEditor.form.copy(title = "Dated memo", startText = "2030-10-21"),
            "timeline-move-dated-memo-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, datedMemoCreated.status)
        val datedMemo = requireNotNull(store.get(requireNotNull(datedMemoCreated.eventSeq)))

        val undatedMemoEditor = openNewEvent(EventKindV1.MEMO)
        val undatedMemoCreated = api.saveNewEvent(
            undatedMemoEditor.handle,
            undatedMemoEditor.form.copy(title = "Undated memo"),
            "timeline-move-undated-memo-create"
        )
        assertEquals(EventMutationStatusV1.CREATED, undatedMemoCreated.status)
        val undatedMemo = requireNotNull(store.get(requireNotNull(undatedMemoCreated.eventSeq)))

        val beforeDrafts = store.draftCount()
        val beforeIdentities = store.identityCount()
        val beforeOrder = store.nextQueueOrderPreview()
        for (record in listOf(repeating, datedMemo, undatedMemo)) {
            val rejected = api.stageTimelineMove(
                record.eventSeq, record.revision, start + 7_200_000L,
                "timeline-move-reject-${record.eventSeq}"
            )
            assertEquals(EventMutationStatusV1.INVALID, rejected.status)
            assertEquals("TIMELINE_MOVE_UNSUPPORTED", rejected.errorCode)
            assertEquals(record, store.get(record.eventSeq))
            assertTrue(store.pendingDraftsFor(record.eventSeq).isEmpty())
        }
        assertEquals(beforeDrafts, store.draftCount())
        assertEquals(beforeIdentities, store.identityCount())
        assertEquals(beforeOrder, store.nextQueueOrderPreview())
    }

    @Test
    fun formalSearchPreservesMatchingOrderAndReturnsBoundedDisplayHits() {
        repeat(10) { index ->
            val editor = openNewEvent(EventKindV1.ACTIVITY)
            val day = java.time.LocalDate.of(2026, 10, 26).plusDays(index.toLong())
            val form = editor.form.copy(
                title = "Alpha ${index + 1}",
                startText = "$day 09:00",
                endText = "$day 10:00"
            )
            assertEquals(
                EventMutationStatusV1.CREATED,
                api.saveNewEvent(editor.handle, form, "search-create-${index + 1}").status
            )
        }

        val snapshot = api.queryEvents()
        val titleMatches = api.searchEvents("aLpHa", snapshot)
        assertEquals((1L..8L).toList(), titleMatches.hits.map { it.eventSeq })
        assertEquals("Alpha 1", titleMatches.hits.first().title)
        assertEquals(EventKindV1.ACTIVITY, titleMatches.hits.first().kind)
        assertEquals(1L, titleMatches.hits.first().revision)
        assertEquals(listOf(1L), api.searchEvents("#0001", snapshot).hits.map { it.eventSeq })
        assertTrue(api.searchEvents(" \t", snapshot).hits.isEmpty())
        assertEquals(snapshot.featureRevision, titleMatches.featureRevision)
    }

    @Test
    fun storeBackedPagesKeepAxisOrderAndRejectCursorsFromOtherRevisionsOrInstances() = runBlocking {
        val startsAndEnds = listOf(
            "09:00" to "10:00", "09:00" to "10:00",
            "08:00" to "09:00", "10:00" to "11:00"
        )
        startsAndEnds.forEachIndexed { index, (start, end) ->
            val draft = store.newDraft(DraftForm(
                kind = TemporalKind.ACTIVITY,
                title = "Axis result ${index + 1}",
                startText = "2026-10-26 $start",
                endText = "2026-10-26 $end",
                zoneId = zone.id
            ))
            assertTrue(store.commit(draft.draftId, actor = "paging-test") is PowerStore.CommitOutcome.Committed)
        }
        val undated = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO, title = "Axis undated", zoneId = zone.id
        ))
        assertTrue(store.commit(undated.draftId, actor = "paging-test") is PowerStore.CommitOutcome.Committed)

        val expected = api.queryEvents()
        assertEquals(listOf(3L, 1L, 2L, 4L, 5L), expected.events.map { it.eventSeq })
        val pages = mutableListOf<EventRecordV1>()
        var cursor: EventQueryCursorV1? = null
        var firstCursor: EventQueryCursorV1? = null
        while (true) {
            val page = api.queryEventsPage(cursor, limit = 2)
            assertFalse(page.stale)
            assertEquals(expected.featureRevision, page.featureRevision)
            assertEquals(expected.events.size, page.totalEvents)
            assertTrue(page.events.size <= 2)
            pages += page.events
            cursor = page.nextCursor ?: break
            if (firstCursor == null) firstCursor = cursor
        }
        assertEquals(expected.events, pages)

        val otherInstance = PowerCalCoreFeatureApiV1(store).events
        assertTrue(otherInstance.queryEventsPage(firstCursor, limit = 2).stale)

        val created = openNewEvent(EventKindV1.ACTIVITY)
        assertEquals(
            EventMutationStatusV1.CREATED,
            api.saveNewEvent(created.handle, created.form.copy(title = "Axis later"), "paging-mutation").status
        )
        assertTrue(api.queryEventsPage(firstCursor, limit = 2).stale)

        val current = api.queryEvents()
        assertEquals(
            api.searchEvents("Axis", current).hits,
            api.searchEvents("Axis").hits
        )
    }

    @Test
    fun occurrenceCompletionUsesExpectedRevisionStateAndOneChangeReceipt() {
        val editor = openNewEvent(EventKindV1.MEMO)
        val form = editor.form.copy(
            title = "Completion API memo",
            startText = "2026-10-26",
            trackingEnabled = true
        )
        val created = api.saveNewEvent(editor.handle, form, "completion-create")
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val eventSeq = requireNotNull(created.eventSeq)
        val before = requireNotNull(store.get(eventSeq))
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = api.subscribe(changes::add)
        try {
            val completed = api.setOccurrenceCompletion(
                eventSeq, before.revision, "single", expectedCompleted = false,
                completed = true, requestId = "completion-one"
            )
            assertEquals(EventMutationStatusV1.UPDATED, completed.status)
            assertEquals(before.revision + 1L, completed.revision)
            val completedRevision = requireNotNull(completed.revision)
            assertEquals(2L, api.featureRevision)
            assertEquals(setOf("single"), store.get(eventSeq)?.completedKeys)
            assertEquals(2L, store.commitCount())
            assertEquals(listOf(EventFeatureChangeTypeV1.FORMAL_UPDATED), changes.map { it.type })

            val replay = api.setOccurrenceCompletion(
                eventSeq, before.revision, "single", expectedCompleted = false,
                completed = true, requestId = "completion-one"
            )
            assertEquals(EventMutationStatusV1.UPDATED, replay.status)
            assertTrue(replay.duplicate)
            assertEquals(2L, store.commitCount())
            assertEquals(1, changes.size)

            val reused = api.setOccurrenceCompletion(
                eventSeq, completedRevision, "single", expectedCompleted = true,
                completed = false, requestId = "completion-one"
            )
            assertEquals(EventMutationStatusV1.DUPLICATE_REQUEST_ID, reused.status)

            val noChange = api.setOccurrenceCompletion(
                eventSeq, completedRevision, "single", expectedCompleted = true,
                completed = true, requestId = "completion-no-change"
            )
            assertEquals(EventMutationStatusV1.NO_CHANGE, noChange.status)
            assertEquals(2L, store.commitCount())
            assertEquals(1, changes.size)

            val stale = api.setOccurrenceCompletion(
                eventSeq, before.revision, "single", expectedCompleted = false,
                completed = true, requestId = "completion-stale"
            )
            assertEquals(EventMutationStatusV1.CONFLICT, stale.status)
            assertEquals(setOf("single"), store.get(eventSeq)?.completedKeys)
            assertEquals(2L, store.commitCount())
            assertEquals(1, changes.size)

            val invalidOccurrence = api.setOccurrenceCompletion(
                eventSeq, completedRevision, "not-an-occurrence", expectedCompleted = false,
                completed = true, requestId = "completion-invalid-slot"
            )
            assertEquals(EventMutationStatusV1.INVALID, invalidOccurrence.status)
        } finally {
            subscription.close()
        }
    }

    @Test
    fun recurringCompletionUsesOriginalSlotKeysAndCanReopenOneSlot() {
        val editor = openNewEvent(EventKindV1.MEMO)
        val form = editor.form.copy(
            title = "Repeating completion memo",
            startText = "2026-10-26",
            trackingEnabled = true,
            recurrence = EventRecurrenceV1(
                frequency = EventRecurrenceFrequencyV1.DAILY,
                interval = 1,
                count = 3,
                activeFromDate = "2026-10-26"
            )
        )
        val created = api.saveNewEvent(editor.handle, form, "recurring-completion-create")
        assertEquals(EventMutationStatusV1.CREATED, created.status)
        val record = requireNotNull(store.get(requireNotNull(created.eventSeq)))
        val anchor = requireNotNull(record.anchorMillis())
        val keys = RecurrenceEngine.occurrences(
            record, anchor - 1L, anchor + 4L * 86_400_000L, limit = 10
        ).map { it.key }
        assertTrue("Repeating memo should expose multiple original slots", keys.size >= 2)

        val slotKey = keys[1]
        val completed = api.setOccurrenceCompletion(
            record.eventSeq, record.revision, slotKey, expectedCompleted = false,
            completed = true, requestId = "recurring-completion-slot"
        )
        assertEquals(EventMutationStatusV1.UPDATED, completed.status)
        assertEquals(setOf(slotKey), store.get(record.eventSeq)?.completedKeys)

        val reopened = api.setOccurrenceCompletion(
            record.eventSeq, requireNotNull(completed.revision), slotKey,
            expectedCompleted = true, completed = false,
            requestId = "recurring-reopen-slot"
        )
        assertEquals(EventMutationStatusV1.UPDATED, reopened.status)
        assertTrue(store.get(record.eventSeq)?.completedKeys.orEmpty().isEmpty())
        assertEquals(3L, store.get(record.eventSeq)?.revision)
    }

    private fun openNewEvent(
        kind: EventKindV1,
        selection: EventSelectionV1? = null
    ): EventEditorSnapshotV1 = opened(api.openNewEvent(kind, zone.id, selection))

    private fun openFormalEvent(
        eventSeq: Long,
        expectedRevision: Long
    ): EventEditorSnapshotV1 =
        opened(api.openFormalEvent(eventSeq, expectedRevision))

    private fun opened(result: EventEditorOpenResultV1): EventEditorSnapshotV1 =
        (result as? EventEditorOpenResultV1.Opened)?.editor
            ?: error("Expected editor to open, got $result")
}
