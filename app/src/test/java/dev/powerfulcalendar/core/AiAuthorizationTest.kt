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
import java.time.ZoneId
import java.util.UUID

/**
 * DSH-08 — a batch authorization is a decision about a **specific reviewed snapshot**.
 *
 * Whatever the user reviewed must still be exactly what is authorized. Any edit, discard or
 * operation change between review and authorization has to invalidate the old decision instead of
 * being silently included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiAuthorizationTest {

    private lateinit var context: Context
    private lateinit var store: PowerStore
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "test-" + UUID.randomUUID() + ".db")
    }

    @After
    fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    private fun activityForm(title: String = "会议", minutesAhead: Long = 60): DraftForm {
        val start = System.currentTimeMillis() + minutesAhead * 60_000L
        return DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = title,
            startText = DraftForm.formatInstant(start, zone),
            endText = DraftForm.formatInstant(start + 3_600_000L, zone),
            zoneId = zone.id
        )
    }

    private fun reviewedVersions(vararg cards: DraftCard): Map<Long, Long> =
        cards.associate { it.draftId to it.draftVersion }

    @Test
    fun authorizationMustRejectContentChangedAfterReview() {
        val card = store.newDraft(activityForm("审阅时的内容"))
        val reviewed = reviewedVersions(card)
        val edited = store.stageDraft(
            card.draftId,
            card.form.copy(title = "审阅之后才改的内容"),
            card.draftVersion
        )
        assertTrue(edited is PowerStore.StageResult.Saved)

        val outcome = runCatching {
            store.beginAiBatch(reviewed, setOf(DraftOperation.CREATE))
        }

        assertTrue(
            "authorization must be refused because the card changed after review",
            outcome.isFailure
        )
        assertFalse(store.isLocked())

        // A rejected attempt leaves nothing behind: re-review and authorize again successfully.
        val fresh = store.pendingDrafts().single()
        val session = store.beginAiBatch(
            mapOf(fresh.draftId to fresh.draftVersion),
            setOf(DraftOperation.CREATE)
        )
        assertTrue(store.isLocked())
        assertTrue(store.finishAiBatch(session.sessionId))
    }

    @Test
    fun authorizationMustRejectADiscardedCard() {
        val card = store.newDraft(activityForm("会被丢弃"))
        val reviewed = reviewedVersions(card)
        assertTrue(store.discard(card.draftId))

        val outcome = runCatching { store.beginAiBatch(reviewed, setOf(DraftOperation.CREATE)) }

        assertTrue("a discarded card cannot be part of a reviewed batch", outcome.isFailure)
        assertFalse(store.isLocked())
    }

    @Test
    fun authorizationMustRejectAnOperationOutsideTheReviewedSet() {
        val card = store.newDraft(activityForm("新建草稿"))
        val reviewed = reviewedVersions(card)

        val outcome = runCatching { store.beginAiBatch(reviewed, setOf(DraftOperation.DELETE)) }

        assertTrue(outcome.isFailure)
        assertFalse(store.isLocked())
    }

    @Test
    fun authorizationAcceptsTheExactReviewedSnapshot() {
        val card = store.newDraft(activityForm("未改动"))
        val session = store.beginAiBatch(reviewedVersions(card), setOf(DraftOperation.CREATE))
        assertTrue(store.isLocked())
        assertEquals(setOf(card.draftId), session.scope)
        assertEquals(card.draftVersion, session.reviewedVersions[card.draftId])
    }

    @Test
    fun contentEditedAfterAuthorizationCannotBeCommittedUnderTheOldSession() {
        val card = store.newDraft(activityForm("原始内容"))
        val session = store.beginAiBatch(reviewedVersions(card), setOf(DraftOperation.CREATE))

        // The AI session itself must not be able to rewrite the reviewed card and then commit it.
        val staged = store.stageDraft(
            card.draftId,
            card.form.copy(title = "授权后才出现的内容"),
            card.draftVersion,
            aiSession = session
        )
        assertTrue(staged is PowerStore.StageResult.NotAuthorized)

        val committed = store.commit(card.draftId, actor = "ai", aiSession = session)
        assertTrue(committed is PowerStore.CommitOutcome.Committed)
        assertEquals("原始内容", store.get(card.eventSeq)!!.title)
    }

    @Test
    fun authorizationScopeNeverGrowsWithNewCards() {
        val first = store.newDraft(activityForm("第一张"))
        val session = store.beginAiBatch(reviewedVersions(first), setOf(DraftOperation.CREATE))
        // Cards cannot even be created while the deck is locked; simulate a card that appeared
        // before the lock but was not part of the review.
        assertNotNull(session)

        val rejected = runCatching { store.newDraft(activityForm("范围外")) }
        assertTrue(rejected.isFailure)
        assertEquals(1, session.scope.size)
    }

    @Test
    fun aiActorCannotCommitWithoutScopedOneUseAuthorization() {
        val card = store.newDraft(activityForm("需要授权的 AI 提案"))

        val outcome = store.commit(card.draftId, actor = "ai")

        assertTrue(outcome is PowerStore.CommitOutcome.NotAuthorized)
        assertNull(store.get(card.eventSeq))
        assertNull(store.intentFor(card.eventSeq))
        assertTrue(store.history(card.eventSeq).isEmpty())
        assertEquals(DraftStatus.PENDING, store.draft(card.draftId)!!.status)
        assertFalse(store.isLocked())
    }

    @Test
    fun completedBatchApprovalCannotBeReplayed() {
        val card = store.newDraft(activityForm("需要一次性授权"))
        val reviewed = reviewedVersions(card)
        val firstApproval = store.beginAiBatch(reviewed, setOf(DraftOperation.CREATE))

        assertTrue(store.finishAiBatch(firstApproval.sessionId))
        assertFalse(store.finishAiBatch(firstApproval.sessionId))
        val replayAfterFinish = store.commit(
            card.draftId, actor = "ai", aiSession = firstApproval
        )
        assertTrue(replayAfterFinish is PowerStore.CommitOutcome.NotAuthorized)
        assertNull(store.get(card.eventSeq))
        assertEquals(DraftStatus.PENDING, store.draft(card.draftId)!!.status)

        val secondApproval = store.beginAiBatch(reviewed, setOf(DraftOperation.CREATE))
        assertTrue(secondApproval.sessionId != firstApproval.sessionId)
        assertTrue(secondApproval.lockEpoch > firstApproval.lockEpoch)
        val staleEpochReplay = store.commit(
            card.draftId, actor = "ai", aiSession = firstApproval
        )
        assertTrue(staleEpochReplay is PowerStore.CommitOutcome.NotAuthorized)
        assertNull(store.get(card.eventSeq))

        val approvedCommit = store.commit(
            card.draftId, actor = "ai", aiSession = secondApproval
        )
        assertTrue(approvedCommit is PowerStore.CommitOutcome.Committed)
        assertEquals("需要一次性授权", store.get(card.eventSeq)!!.title)
        assertTrue(store.finishAiBatch(secondApproval.sessionId))
    }

    @Test
    fun upgradingAVersionOneDatabaseAddsTheReviewTableWithoutDataLoss() {
        // Simulate a database created by the previous build: same schema minus the new table.
        val name = store.databaseName
        val preUpgrade = store.newDraft(activityForm("升级前的既有草稿"))
        store.close()
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )
        raw.execSQL("DROP TABLE ai_session_drafts")
        raw.version = 1
        raw.close()

        store = PowerStore(context, name)
        val card = store.pendingDrafts().single()
        assertEquals(preUpgrade.draftId, card.draftId)
        assertEquals("升级前的既有草稿", card.form.title)
        val session = store.beginAiBatch(reviewedVersions(card), setOf(DraftOperation.CREATE))
        assertTrue(store.isLocked())
        assertEquals(card.draftVersion, session.reviewedVersions[card.draftId])
        assertTrue(store.finishAiBatch(session.sessionId))
    }
}
