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
import org.junit.Assert.assertTrue
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
