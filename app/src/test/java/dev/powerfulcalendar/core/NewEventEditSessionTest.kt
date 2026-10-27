package dev.powerfulcalendar.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class NewEventEditSessionTest {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private val zone = ZoneId.of("Asia/Shanghai")
    private val fixedClock = Clock.fixed(Instant.parse("2026-10-25T15:07:42Z"), ZoneOffset.UTC)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "new-event-session-${UUID.randomUUID()}.db")
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    private data class StoreCounts(
        val drafts: Long,
        val pending: Int,
        val formal: Int,
        val identities: Long,
        val nextSequence: Long,
        val nextQueueOrder: Long,
        val commits: Long
    )

    private fun counts() = StoreCounts(
        drafts = store.draftCount(),
        pending = store.pendingCount(),
        formal = store.all().size,
        identities = store.identityCount(),
        nextSequence = store.nextSequencePreview(),
        nextQueueOrder = store.nextQueueOrderPreview(),
        commits = store.commitCount()
    )

    @Test
    fun webSessionDraftIdRoundTripsExactlyThroughJavaScriptNumber() {
        val session = NewEventEditSession.open(TemporalKind.ACTIVITY, fixedClock, zone)
        val clientDraftId = session.clientDraftId

        assertTrue(clientDraftId > 0L)
        assertTrue(clientDraftId <= 9_007_199_254_740_991L)
        assertEquals(clientDraftId, clientDraftId.toDouble().toLong())
    }

    @Test
    fun openingAndClosingEveryKindTwentyTimesHasZeroPersistentSideEffects() {
        val before = counts()
        repeat(20) {
            TemporalKind.entries.forEach { kind ->
                val session = NewEventEditSession.open(kind, fixedClock, zone)
                assertFalse(session.meaningfulDiff)
                assertNull(session.checkpoint(store))
                assertNull(session.close(store))
                assertNull(session.close(store))
            }
        }
        assertEquals(before, counts())
    }

    @Test
    fun restoredBusinessValuesAreCleanAndOneRealChangeStagesOnlyOnce() {
        val before = counts()
        val clean = NewEventEditSession.open(TemporalKind.ACTIVITY, fixedClock, zone)
        val original = clean.form
        clean.edit(original.copy(title = "temporary"))
        assertTrue(clean.meaningfulDiff)
        clean.edit(original.copy(startText = "2026-10-25T23:08:00"))
        assertFalse(clean.meaningfulDiff)
        assertNull(clean.close(store))
        assertEquals(before, counts())

        val changed = NewEventEditSession.open(TemporalKind.MEMO, fixedClock, zone)
        changed.edit(changed.form.copy(notes = "keep this note"))
        val staged = assertNotNullAndReturn(changed.close(store))
        assertEquals(staged, changed.close(store))
        assertEquals(1, store.pendingCount())
        assertEquals(1, store.draftCount())
        assertEquals(before.identities + 1, store.identityCount())
        assertEquals(before.nextSequence + 1, store.nextSequencePreview())
        assertEquals(before.nextQueueOrder + 1, store.nextQueueOrderPreview())
        assertEquals(before.commits, store.commitCount())
        assertEquals(1L, staged.queueOrder)
        assertEquals("keep this note", store.draft(staged.draftId)?.form?.notes)
    }

    @Test
    fun cleanAndChangedLifecycleCheckpointsAreIdempotentAndRecoverable() {
        val before = counts()
        val clean = NewEventEditSession.open(TemporalKind.ALARM, fixedClock, zone)
        assertNull(clean.checkpoint(store))
        assertEquals(before, counts())

        val session = NewEventEditSession.open(TemporalKind.ACTIVITY, fixedClock, zone)
        session.edit(session.form.copy(title = "survives stop"))
        val first = assertNotNullAndReturn(session.checkpoint(store))
        val repeated = assertNotNullAndReturn(session.checkpoint(store))
        assertEquals(first.draftId, repeated.draftId)
        assertEquals(first.draftVersion, repeated.draftVersion)
        assertEquals(1, store.draftCount())

        session.edit(session.form.copy(notes = "updated after resume"))
        val updated = assertNotNullAndReturn(session.checkpoint(store))
        assertEquals(first.draftId, updated.draftId)
        assertEquals(first.draftVersion + 1, updated.draftVersion)
        assertEquals(updated, session.close(store))
        assertEquals(1, store.pendingCount())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val recovered = store.pendingDrafts().single()
        assertEquals("survives stop", recovered.form.title)
        assertEquals("updated after resume", recovered.form.notes)
        assertEquals(updated.draftId, recovered.draftId)
    }

    @Test
    fun explicitCompleteSaveCreatesFormalEventWithoutAnIntermediateDraft() {
        val before = counts()
        val session = NewEventEditSession.open(TemporalKind.ALARM, fixedClock, zone)
        session.edit(session.form.copy(title = "publish directly"))

        val first = session.save(store) as NewEventEditSession.SaveOutcome.Published
        val committed = first.outcome as PowerStore.CommitOutcome.Committed
        assertEquals("publish directly", store.get(committed.eventSeq)?.title)
        assertEquals(before.drafts, store.draftCount())
        assertEquals(before.pending, store.pendingCount())
        assertEquals(before.formal + 1, store.all().size)
        assertEquals(before.identities + 1, store.identityCount())
        assertEquals(before.nextSequence + 1, store.nextSequencePreview())
        assertEquals(before.nextQueueOrder, store.nextQueueOrderPreview())
        assertEquals(before.commits + 1, store.commitCount())

        val replay = session.save(store) as NewEventEditSession.SaveOutcome.Published
        assertEquals(committed, replay.outcome)
        assertEquals(before.identities + 1, store.identityCount())
        assertEquals(before.commits + 1, store.commitCount())
        assertNull(session.close(store))
    }

    @Test
    fun incompleteSaveStagesOnceThenPublishesTheSameDraft() {
        val session = NewEventEditSession.open(TemporalKind.DEADLINE, fixedClock, zone)
        session.edit(session.form.copy(notes = "unfinished but meaningful"))

        val first = session.save(store) as NewEventEditSession.SaveOutcome.Staged
        val again = session.save(store) as NewEventEditSession.SaveOutcome.Staged
        assertEquals(first.card.draftId, again.card.draftId)
        assertEquals(first.card.draftVersion, again.card.draftVersion)
        assertEquals(1, store.pendingCount())
        assertEquals(1, store.draftCount())
        assertEquals(2L, store.nextQueueOrderPreview())
        assertEquals(0, store.commitCount())

        session.edit(session.form.copy(title = "finish later"))
        val saved = session.save(store) as NewEventEditSession.SaveOutcome.Published
        val committed = saved.outcome as PowerStore.CommitOutcome.Committed
        assertEquals(first.card.eventSeq, committed.eventSeq)
        assertEquals("finish later", store.get(committed.eventSeq)?.title)
        assertEquals(1, store.draftCount())
        assertEquals(0, store.pendingCount())
        assertEquals(2L, store.nextQueueOrderPreview())
        assertEquals(1, store.commitCount())
        assertEquals(1, store.draftsByStatus(DraftStatus.COMMITTED).size)
    }

    @Test
    fun revertingToBaselineAfterARecoveryCheckpointRemovesTheActionableDraft() {
        val session = NewEventEditSession.open(TemporalKind.MEMO, fixedClock, zone)
        val baseline = session.form
        session.edit(baseline.copy(title = "temporary"))
        val staged = assertNotNullAndReturn(session.checkpoint(store))
        session.edit(baseline)

        assertFalse(session.meaningfulDiff)
        assertEquals(NewEventEditSession.SaveOutcome.NoChanges, session.save(store))
        assertNull(store.pendingDrafts().firstOrNull())
        assertEquals(1, store.draftsByStatus(DraftStatus.DISCARDED).size)
        assertEquals(staged.eventSeq, store.draftsByStatus(DraftStatus.DISCARDED).single().eventSeq)
        assertEquals(1, store.identityCount())
        assertEquals(2L, store.nextQueueOrderPreview())
        assertEquals(0, store.commitCount())
    }

    private fun assertNotNullAndReturn(card: DraftCard?): DraftCard {
        assertNotNull(card)
        return requireNotNull(card)
    }
}
