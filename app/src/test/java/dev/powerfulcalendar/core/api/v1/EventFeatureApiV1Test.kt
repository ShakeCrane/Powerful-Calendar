package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
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
