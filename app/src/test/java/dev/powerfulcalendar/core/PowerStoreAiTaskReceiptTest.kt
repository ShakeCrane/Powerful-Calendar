package dev.powerfulcalendar.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiConversationTaskSnapshot
import dev.powerfulcalendar.ai.AiDraftService
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.ai.AiTaskEvidence
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.Clock
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerStoreAiTaskReceiptTest {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private val stores = mutableListOf<PowerStore>()
    private val databaseNames = mutableSetOf<String>()
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = openStore("ai-task-receipt-${UUID.randomUUID()}.db")
    }

    @After
    fun tearDown() {
        stores.forEach { runCatching { it.close() } }
        databaseNames.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun stageReplayUsesTheSameDraftAndRejectsChangedContentAtTheSameRevision() {
        val first = stage("stable-task", 1, 0, form()) as PowerStore.AiTaskProposalStage.Pending
        val firstCounters = counters(store)
        assertFalse(first.reused)
        assertEquals(1, receiptCount(store))

        val replay = stage("stable-task", 1, 0, form()) as PowerStore.AiTaskProposalStage.Pending
        assertTrue(replay.reused)
        assertEquals(first.draft.draftId, replay.draft.draftId)
        assertEquals(first.draft.eventSeq, replay.draft.eventSeq)
        assertEquals(first.idempotencyKey, replay.idempotencyKey)
        assertEquals(firstCounters, counters(store))

        val changed = runCatching {
            stage("stable-task", 1, 0, form(title = "Changed result"))
        }
        assertTrue("A changed model response must not replace reviewed content", changed.isFailure)
        assertEquals(firstCounters, counters(store))
        assertEquals("First result", store.draft(first.draft.draftId)?.form?.title)
    }

    @Test
    fun commitAndProcessRestartReplayReturnTheOriginalCommitReceipt() {
        val pending = stage("publish-task", 2, 0, form()) as PowerStore.AiTaskProposalStage.Pending
        val committed = store.commit(
            pending.draft.draftId,
            actor = "user",
            idempotencyKey = pending.idempotencyKey
        ) as PowerStore.CommitOutcome.Committed
        val duplicate = store.commit(
            pending.draft.draftId,
            actor = "user",
            idempotencyKey = pending.idempotencyKey
        ) as PowerStore.CommitOutcome.Committed
        assertTrue(duplicate.duplicate)

        val name = store.databaseName
        store.close()
        store = openStore(name)
        val replay = stage("publish-task", 2, 0, form()) as PowerStore.AiTaskProposalStage.Committed

        assertEquals(committed.eventSeq, replay.eventSeq)
        assertEquals(committed.revision, replay.revision)
        assertEquals(committed.commitId, replay.commitId)
        assertEquals(1, store.all().size)
        assertEquals(1L, store.identityCount())
        assertEquals(1L, store.commitCount())
        assertEquals(1, receiptCount(store))
    }

    @Test
    fun discardAndUndoRetainTheSameReceiptAndIdentity() {
        val pending = stage("discard-task", 1, 0, form()) as PowerStore.AiTaskProposalStage.Pending
        assertTrue(store.discard(pending.draft.draftId))
        assertEquals(PowerStore.AiTaskProposalStage.Discarded, stage("discard-task", 1, 0, form()))

        assertTrue(store.undoDiscard(pending.draft.draftId))
        val restored = stage("discard-task", 1, 0, form()) as PowerStore.AiTaskProposalStage.Pending

        assertTrue(restored.reused)
        assertEquals(pending.draft.draftId, restored.draft.draftId)
        assertEquals(pending.draft.eventSeq, restored.draft.eventSeq)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(1, receiptCount(store))
    }

    @Test
    fun backupRestoreKeepsPendingAndCommittedTaskReceipts() {
        val pendingForm = form("Pending AI item")
        val committedForm = form("Committed AI item")
        val pending = stage("backup-task", 3, 0, pendingForm)
            as PowerStore.AiTaskProposalStage.Pending
        val committedPending = stage("backup-task", 3, 1, committedForm)
            as PowerStore.AiTaskProposalStage.Pending
        store.commit(
            committedPending.draft.draftId,
            actor = "user",
            idempotencyKey = committedPending.idempotencyKey
        ) as PowerStore.CommitOutcome.Committed

        val envelope = store.exportBackup()
        val payload = envelope.getJSONObject("payload")
        val receipts = payload.getJSONArray(BackupEnvelope.AI_TASK_RECEIPTS)
        assertEquals(2, receipts.length())
        assertEquals(0, receipts.getJSONObject(0).optInt("itemIndex"))
        assertEquals("PENDING", receipts.getJSONObject(0).optString("state"))
        assertEquals(1, receipts.getJSONObject(1).optInt("itemIndex"))
        assertEquals("COMMITTED", receipts.getJSONObject(1).optString("state"))

        val restored = openStore("ai-task-restore-${UUID.randomUUID()}.db")
        restored.restoreBackup(JSONObject(envelope.toString()))
        val pendingReplay = restored.stageAiTaskProposal(
            "backup-task", 3, 0, pendingForm, DraftOperation.CREATE, null
        ) as PowerStore.AiTaskProposalStage.Pending
        val committedReplay = restored.stageAiTaskProposal(
            "backup-task", 3, 1, committedForm, DraftOperation.CREATE, null
        ) as PowerStore.AiTaskProposalStage.Committed

        assertTrue(pendingReplay.reused)
        assertEquals(pending.draft.draftId, pendingReplay.draft.draftId)
        assertEquals(committedPending.draft.eventSeq, committedReplay.eventSeq)
        assertEquals(1, restored.pendingCount())
        assertEquals(1, restored.all().size)
        assertEquals(2, receiptCount(restored))
    }

    @Test
    fun schemaFourDatabaseAndEnvelopeUpgradeWithoutLosingExistingRows() {
        val pending = stage("schema-task", 1, 0, form()) as PowerStore.AiTaskProposalStage.Pending
        val name = store.databaseName
        store.close()
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { database ->
            database.execSQL("DROP TABLE ai_task_receipts")
            database.version = 4
        }

        store = openStore(name)
        assertEquals(5, store.readableDatabase.version)
        assertEquals(0, receiptCount(store))
        assertEquals(pending.draft.draftId, store.pendingDrafts().single().draftId)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())

        val v4Source = openStore("ai-task-v4-backup-${UUID.randomUUID()}.db")
        val v4Export = v4Source.exportBackup()
        val v4Payload = JSONObject(v4Export.getJSONObject("payload").toString())
        v4Payload.remove(BackupEnvelope.AI_TASK_RECEIPTS)
        val v4Envelope = BackupEnvelope.create(
            payload = v4Payload,
            databaseSchemaVersion = 4,
            appVersionCode = v4Export.getLong("appVersionCode"),
            exportedAtEpochMs = v4Export.getLong("exportedAtEpochMs"),
            storeLineageId = v4Export.getString("storeLineageId")
        )
        val v4Restore = openStore("ai-task-v4-restore-${UUID.randomUUID()}.db")
        v4Restore.restoreBackup(v4Envelope)
        assertEquals(0, receiptCount(v4Restore))
    }

    @Test
    fun expiredReviewedTaskCannotStageOrPublishItsProposal() {
        val anchor = 1_798_200_000_000L
        val task = AiConversationTaskSnapshot(
            taskId = "expired-task",
            revision = 1,
            anchorMillis = anchor,
            zoneId = zone.id,
            userEvidence = listOf(AiTaskEvidence(
                messageId = "expired-task",
                text = "今天创建一项活动 12:00 到 13:00。",
                createdAtMillis = anchor,
                zoneId = zone.id
            )),
            assistantSuggestions = emptyList()
        )
        val interpretation = AiInterpretation(
            proposals = listOf(AiProposal(
                operation = DraftOperation.CREATE,
                eventSeq = null,
                form = form(),
                sourceIndex = 0
            )),
            taskSnapshot = task
        )
        val expiryMillis = anchor + AiConversationTaskSnapshot.MAX_TASK_AGE_MILLIS + 1
        val expiredService = AiDraftService(
            store,
            Clock.fixed(Instant.ofEpochMilli(expiryMillis), zone)
        )

        val result = expiredService.applyReviewedProposals(
            interpretation, setOf(0), publish = true)

        assertTrue(result.warnings.single().contains("已过期"))
        assertTrue(result.handledIndices.isEmpty())
        assertEquals(0, result.stagedCount)
        assertEquals(0, result.publishedCount)
        assertEquals(0, store.draftCount())
        assertEquals(0, store.all().size)
        assertEquals(0L, store.identityCount())
        assertEquals(0L, store.commitCount())
        assertEquals(1L, store.nextSequencePreview())
        assertEquals(1L, store.nextQueueOrderPreview())
    }

    private fun stage(
        taskId: String,
        revision: Int,
        index: Int,
        form: DraftForm
    ) = store.stageAiTaskProposal(
        taskId = taskId,
        taskRevision = revision,
        itemIndex = index,
        form = form,
        operation = DraftOperation.CREATE,
        eventSeq = null
    )

    private fun form(title: String = "First result") = DraftForm(
        kind = TemporalKind.ACTIVITY,
        title = title,
        startText = "2026-10-25 10:00",
        endText = "2026-10-25 11:00",
        source = "ai",
        zoneId = zone.id
    )

    private fun counters(target: PowerStore) = listOf(
        target.draftCount(), target.pendingCount().toLong(), target.all().size.toLong(),
        target.identityCount(), target.nextSequencePreview(), target.nextQueueOrderPreview(),
        target.commitCount()
    )

    private fun receiptCount(target: PowerStore): Int = target.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM ai_task_receipts", null
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun openStore(name: String): PowerStore = PowerStore(context, name).also {
        stores += it
        databaseNames += name
    }
}
