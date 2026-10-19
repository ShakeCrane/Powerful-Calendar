package dev.powerfulcalendar.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
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
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The protocol invariants that must never regress. These run against the real SQLite store under
 * Robolectric, because most of the guarantees are about transactions, uniqueness and fencing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerStoreTest {

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

    private fun newStore(): PowerStore = PowerStore(context, "test-" + UUID.randomUUID() + ".db")

    private fun alarmForm(title: String = "起床", minutesAhead: Long = 60): DraftForm {
        val start = System.currentTimeMillis() + minutesAhead * 60_000L
        return DraftForm(
            kind = TemporalKind.ALARM,
            title = title,
            startText = DraftForm.formatInstant(start, zone),
            zoneId = zone.id
        )
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

    private fun commit(card: DraftCard): PowerStore.CommitOutcome =
        store.commit(card.draftId, actor = "test")

    // ---------------------------------------------------------------- permanent identity

    @Test
    fun firstDraftAllocatesASequenceEvenIfItIsDiscarded() {
        val first = store.newDraft(alarmForm())
        assertEquals(1L, first.eventSeq)
        assertTrue(store.discard(first.draftId))
        assertEquals(0, store.pendingCount())
        // #0001 is retired forever, the allocator still advanced.
        val second = store.newDraft(alarmForm("第二个"))
        assertEquals(2L, second.eventSeq)
        assertEquals(3L, store.nextSequencePreview())
        assertEquals(2L, store.identityCount())
    }

    @Test
    fun oneEventMayOwnManyCardsWithIndependentQueueOrder() {
        val base = store.newDraft(activityForm())
        val committed = commit(base) as PowerStore.CommitOutcome.Committed
        val seq = committed.eventSeq

        val d1 = store.newDraft(DraftForm.from(store.get(seq)!!), seq, DraftOperation.REPLACE)
        val d2 = store.newDraft(DraftForm.from(store.get(seq)!!), seq, DraftOperation.REPLACE)

        assertEquals(seq, d1.eventSeq)
        assertEquals(seq, d2.eventSeq)
        assertEquals(seq, base.eventSeq)
        assertTrue(d1.draftId != d2.draftId)
        assertTrue(d2.queueOrder > d1.queueOrder)
        // eventSeq does not order the queue.
        assertTrue(d1.queueOrder > base.queueOrder)
    }

    @Test
    fun queueOrderIsIndependentOfEventNumber() {
        val a = store.newDraft(activityForm("A"))
        commit(a)
        val b = store.newDraft(activityForm("B"))
        commit(b)
        val later = store.newDraft(DraftForm.from(store.get(a.eventSeq)!!), a.eventSeq, DraftOperation.REPLACE)

        val queue = store.pendingDrafts()
        assertEquals(listOf(later.draftId), queue.map { it.draftId })
        assertTrue(later.queueOrder > b.queueOrder)
        assertTrue(later.eventSeq < b.eventSeq)
    }

    // ---------------------------------------------------------------- whole-snapshot commits

    @Test
    fun lastValidCommitWinsWithTheWholeSnapshot() {
        val card = store.newDraft(activityForm("周会").copy(location = "会议室 A"))
        val first = commit(card) as PowerStore.CommitOutcome.Committed
        assertEquals(1L, first.revision)

        val form = DraftForm.from(store.get(card.eventSeq)!!).copy(
            location = "会议室 B",
            notes = "整份替换"
        )
        val edit = store.newDraft(form, card.eventSeq, DraftOperation.REPLACE)
        val second = commit(edit) as PowerStore.CommitOutcome.Committed
        assertEquals(2L, second.revision)

        val current = store.get(card.eventSeq)!!
        assertEquals("会议室 B", current.location)
        assertEquals("整份替换", current.notes)
        assertEquals(2L, current.revision)
    }

    @Test
    fun staleCardCannotSilentlyOverwriteAndRequiresExplicitApproval() {
        val card = store.newDraft(activityForm("评审"))
        commit(card)
        val seq = card.eventSeq

        val v1 = store.get(seq)!!
        val d1 = store.newDraft(DraftForm.from(v1).copy(title = "评审 D1"), seq, DraftOperation.REPLACE)
        val d2 = store.newDraft(DraftForm.from(v1).copy(title = "评审 D2"), seq, DraftOperation.REPLACE)

        assertTrue(commit(d2) is PowerStore.CommitOutcome.Committed)
        assertEquals("评审 D2", store.get(seq)!!.title)

        val conflict = commit(d1)
        assertTrue(conflict is PowerStore.CommitOutcome.Conflict)
        assertEquals("评审 D2", store.get(seq)!!.title)
        assertEquals(DraftStatus.CONFLICT, store.draft(d1.draftId)!!.status)

        // The user explicitly approves replacing the now-current revision.
        val approved = store.commit(d1.draftId, approveStale = true, actor = "test")
        assertTrue(approved is PowerStore.CommitOutcome.Committed)
        assertEquals("评审 D1", store.get(seq)!!.title)
        assertEquals(3L, store.get(seq)!!.revision)
        assertEquals(DraftStatus.COMMITTED, store.draft(d1.draftId)!!.status)
    }

    @Test
    fun repeatedCommitReturnsTheOriginalOutcome() {
        val card = store.newDraft(activityForm())
        val first = commit(card) as PowerStore.CommitOutcome.Committed
        val second = commit(card) as PowerStore.CommitOutcome.Committed
        assertTrue(second.duplicate)
        assertEquals(first.revision, second.revision)
        assertEquals(first.commitId, second.commitId)
        assertEquals(1, store.history(card.eventSeq).size)
    }

    @Test
    fun replacementPreservesProvenanceAndCreationTime() {
        val card = store.newDraft(
            activityForm("导入的会").copy(source = "ics", zoneId = "Asia/Tokyo")
        )
        commit(card)
        val original = store.get(card.eventSeq)!!
        assertEquals("ics", original.source)
        assertEquals("Asia/Tokyo", original.zoneId)

        val edit = store.newDraft(DraftForm.from(original).copy(notes = "改备注"), card.eventSeq, DraftOperation.REPLACE)
        commit(edit)
        val updated = store.get(card.eventSeq)!!
        assertEquals("ics", updated.source)
        assertEquals("Asia/Tokyo", updated.zoneId)
        assertEquals(original.createdAtMillis, updated.createdAtMillis)
        assertEquals("改备注", updated.notes)
    }

    // ---------------------------------------------------------------- retirement / restore

    @Test
    fun deleteRetiresTheNumberAndLateEditsNeedExplicitRestore() {
        val card = store.newDraft(activityForm("将被删除"))
        commit(card)
        val seq = card.eventSeq
        val lateEdit = store.newDraft(DraftForm.from(store.get(seq)!!), seq, DraftOperation.REPLACE)

        val deleteDraft = store.newDraft(DraftForm.from(store.get(seq)!!), seq, DraftOperation.DELETE)
        assertTrue(commit(deleteDraft) is PowerStore.CommitOutcome.Committed)
        assertNull(store.get(seq))
        assertTrue(store.isRetired(seq))

        val blocked = commit(lateEdit)
        assertTrue(blocked is PowerStore.CommitOutcome.NeedsRestore)
        assertNull(store.get(seq))

        // CREATE cannot resurrect a retired number.
        val createAgain = store.newDraft(activityForm("新事件"))
        assertTrue(createAgain.eventSeq != seq)

        // Restoration is driven by the card that proposed it and removes that card from the queue.
        val restored = store.restoreDraft(
            lateEdit.draftId,
            DraftForm.from(store.getIncludingRetired(seq)!!).copy(title = "复活")
        )
        assertTrue(restored is PowerStore.CommitOutcome.Committed)
        assertEquals(seq, (restored as PowerStore.CommitOutcome.Committed).eventSeq)
        assertFalse(store.isRetired(seq))
        assertEquals("复活", store.get(seq)!!.title)
        assertEquals(3L, store.get(seq)!!.revision)
        assertEquals(DraftStatus.COMMITTED, store.draft(lateEdit.draftId)!!.status)
        assertEquals(0, store.pendingDraftsFor(seq).size)
        // The restored audit trail keeps the card linkage.
        assertEquals(1, store.history(seq).count { it.operation == "RESTORE" })
    }

    // ---------------------------------------------------------------- optimistic staging

    @Test
    fun stagingRejectsAnOutdatedDraftVersion() {
        val card = store.newDraft(activityForm("并发"))
        val saved = store.stageDraft(card.draftId, card.form.copy(title = "第一版"), card.draftVersion)
        assertTrue(saved is PowerStore.StageResult.Saved)
        val version = (saved as PowerStore.StageResult.Saved).draftVersion

        val stale = store.stageDraft(card.draftId, card.form.copy(title = "旧版本覆盖"), card.draftVersion)
        assertTrue(stale is PowerStore.StageResult.VersionMismatch)
        assertEquals("第一版", store.draft(card.draftId)!!.form.title)

        val current = store.stageDraft(card.draftId, card.form.copy(title = "第二版"), version)
        assertTrue(current is PowerStore.StageResult.Saved)
        assertEquals("第二版", store.draft(card.draftId)!!.form.title)
    }

    // ---------------------------------------------------------------- AI exclusion

    @Test
    fun aiBatchLocksEveryHumanDeckWrite() {
        val card = store.newDraft(activityForm("AI 处理"))
        val session = store.beginAiBatch(
            setOf(card.draftId),
            setOf(DraftOperation.CREATE, DraftOperation.REPLACE)
        )
        assertTrue(store.isLocked())

        assertTrue(store.stageDraft(card.draftId, card.form.copy(title = "用户偷偷改"), 0) is PowerStore.StageResult.Locked)
        assertFalse(store.discard(card.draftId))
        assertFalse(store.undoDiscard(card.draftId))
        assertTrue(store.commit(card.draftId, actor = "user") is PowerStore.CommitOutcome.Locked)
        assertTrue(runCatching { store.newDraft(activityForm("插入")) }.isFailure)

        // The AI itself may commit inside its scope.
        val outcome = store.commit(card.draftId, actor = "ai", aiSession = session)
        assertTrue(outcome is PowerStore.CommitOutcome.Committed)

        assertTrue(store.finishAiBatch(session.sessionId))
        assertFalse(store.isLocked())
        assertEquals(PowerStore.AiState.COMPLETED, store.currentAiSession()!!.state)
    }

    @Test
    fun aiCannotWriteOutsideItsScopeOrAfterTheEpochIsFenced() {
        val inside = store.newDraft(activityForm("在范围内"))
        val outside = store.newDraft(activityForm("不在范围内"))
        val session = store.beginAiBatch(setOf(inside.draftId), setOf(DraftOperation.CREATE))

        val denied = store.commit(outside.draftId, actor = "ai", aiSession = session)
        assertTrue(denied is PowerStore.CommitOutcome.NotAuthorized)

        assertTrue(store.finishAiBatch(session.sessionId))
        val late = store.commit(inside.draftId, actor = "ai", aiSession = session)
        assertTrue(late is PowerStore.CommitOutcome.NotAuthorized)
        // A late AI response cannot mutate the formal store either.
        assertNull(store.get(inside.eventSeq))
    }

    @Test
    fun cancelStopsFurtherAiWritesAndKeepsFinishedOnes() {
        val first = store.newDraft(activityForm("第一张"))
        val second = store.newDraft(activityForm("第二张"))
        val session = store.beginAiBatch(
            setOf(first.draftId, second.draftId),
            setOf(DraftOperation.CREATE)
        )
        assertTrue(store.commit(first.draftId, actor = "ai", aiSession = session) is PowerStore.CommitOutcome.Committed)
        assertTrue(store.requestAiCancel(session.sessionId))
        val afterCancel = store.commit(second.draftId, actor = "ai", aiSession = session)
        assertTrue(afterCancel is PowerStore.CommitOutcome.NotAuthorized)
        // Already committed work stands.
        assertNotNull(store.get(first.eventSeq))
        assertNull(store.get(second.eventSeq))
        assertNotNull(store.draft(second.draftId))
    }

    @Test
    fun orphanedBatchIsFencedAndUnlockedOnRecovery() {
        val card = store.newDraft(activityForm("孤儿批次"))
        val session = store.beginAiBatch(setOf(card.draftId), setOf(DraftOperation.CREATE))
        // A live batch owned by THIS process must not be treated as an orphan.
        assertFalse(store.recoverInterruptedAi())
        assertTrue(store.isLocked())

        // Simulate a session left behind by an earlier process.
        store.writableDatabase.execSQL(
            "UPDATE ai_sessions SET owner_token='previous-process' WHERE session_id=?",
            arrayOf(session.sessionId)
        )
        assertTrue(store.recoverInterruptedAi())
        assertFalse(store.isLocked())
        assertEquals(PowerStore.AiState.INTERRUPTED, store.currentAiSession()!!.state)
        // Every write authorized by the dead epoch is now rejected.
        val late = store.commit(card.draftId, actor = "ai", aiSession = session)
        assertTrue(late is PowerStore.CommitOutcome.NotAuthorized)
    }

    // ---------------------------------------------------------------- imports

    @Test
    fun structuredImportIsIdempotentAndDisablesImportedAlarms() {
        val batch = store.beginMigration("test")
        val record = TemporalRecord(
            eventSeq = 0, revision = 0, kind = TemporalKind.ALARM, title = "旧闹钟",
            startMillis = System.currentTimeMillis() + 3_600_000L, enabled = true
        )
        val first = store.importStructured(record, "ext-1", batch, "test")
        assertTrue(first is PowerStore.ImportOutcome.Imported)
        val imported = first as PowerStore.ImportOutcome.Imported
        assertFalse("imported alarms must wait for handoff", imported.enabled)
        assertFalse(store.get(imported.eventSeq)!!.enabled)

        val second = store.importStructured(record, "ext-1", batch, "test")
        assertTrue(second is PowerStore.ImportOutcome.AlreadyImported)
        assertEquals(1L, store.identityCount())
        assertEquals(1, store.history(imported.eventSeq).size)
        store.finishMigration(batch)
        val summary = store.migrationSummary(batch)!!
        assertEquals(1, summary.first)
        assertEquals(1, summary.second)
    }

    @Test
    fun schedulingIntentFollowsTheCommittedRevision() {
        val card = store.newDraft(alarmForm(minutesAhead = 90))
        commit(card)
        val intent = store.intentFor(card.eventSeq)
        assertNotNull(intent)
        assertEquals(card.eventSeq, intent!!.eventSeq)
        assertEquals(PowerStore.DesiredSchedule.PENDING, intent.desired)
        assertEquals(PowerStore.TriggerRequirement.EXACT, intent.requirement)

        val deleteForm = DraftForm.from(store.get(card.eventSeq)!!)
        val delete = store.newDraft(deleteForm, card.eventSeq, DraftOperation.DELETE)
        commit(delete)
        assertEquals(PowerStore.DesiredSchedule.CANCELLED, store.intentFor(card.eventSeq)!!.desired)
    }

    // ---------------------------------------------------------------- statistics

    @Test
    fun dateOnlyDeadlineCommitsWithoutInventingATime() {
        val card = store.newDraft(
            DraftForm(TemporalKind.DEADLINE, "交材料", endText = "2026-10-24")
        )
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val record = store.get(card.eventSeq)!!
        assertEquals("2026-10-24", record.deadlineDate)
        assertNull(record.deadlineMillis)
        assertEquals(TimePrecision.DATE, record.precision)
        assertEquals(TemporalKind.DEADLINE, record.kind)
    }

    @Test
    fun statsAreDerivedFromDurableRecords() {
        val since = System.currentTimeMillis() - 1_000
        val published = store.newDraft(activityForm("已发布"))
        commit(published)
        val discarded = store.newDraft(activityForm("已丢弃"))
        assertTrue(store.discard(discarded.draftId))
        val stats = store.stats(since)
        assertEquals(1, stats.published)
        assertEquals(1, stats.discarded)
    }

    // ---------------------------------------------------------------- backup

    @Test
    fun nativeBackupRestoresIdentityOrderAndNeverAnAiLock() {
        val committed = store.newDraft(activityForm("备份已提交"))
        commit(committed)
        val pending = store.newDraft(activityForm("备份待处理"))
        store.discard(store.newDraft(activityForm("已丢弃")).draftId)
        val session = store.beginAiBatch(setOf(pending.draftId), setOf(DraftOperation.CREATE))

        val snapshot = store.exportBackup()
        assertFalse(snapshot.toString().contains("owner_token"))
        val restored = newStore()
        try {
            val report = restored.restoreBackup(snapshot)
            assertEquals(1, report.objects)
            assertEquals(3, report.drafts)
            assertEquals(committed.eventSeq, restored.get(committed.eventSeq)!!.eventSeq)
            assertEquals("备份已提交", restored.get(committed.eventSeq)!!.title)
            assertEquals(store.nextSequencePreview(), restored.nextSequencePreview())
            assertEquals(1, restored.pendingCount())
            assertFalse(restored.isLocked())
            assertNull(restored.currentAiSession())
            // The pending card keeps its identity and queue order.
            assertEquals(pending.queueOrder, restored.pendingDrafts().single().queueOrder)
            // A restore must not overwrite existing data.
            assertTrue(runCatching { restored.restoreBackup(snapshot) }.isFailure)
        } finally {
            val name = restored.databaseName
            restored.close()
            context.deleteDatabase(name)
        }
        store.finishAiBatch(session.sessionId)
    }

    @Test
    fun restorationDoesNotResurrectStaleClearedAuthorization() {
        val card = store.newDraft(activityForm("授权不复活"))
        val session = store.beginAiBatch(setOf(card.draftId), setOf(DraftOperation.CREATE))
        val snapshot = store.exportBackup()
        store.finishAiBatch(session.sessionId)
        val restored = newStore()
        try {
            restored.restoreBackup(snapshot)
            assertFalse(restored.isLocked())
            // The old session tokens do not exist in the restored database.
            assertTrue(
                restored.commit(card.draftId, actor = "ai", aiSession = session) is
                    PowerStore.CommitOutcome.NotAuthorized
            )
        } finally {
            val name = restored.databaseName
            restored.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun scheduledIntentIsNotMislabelledWhenTheRowWasReplaced() {
        val card = store.newDraft(alarmForm(minutesAhead = 120))
        commit(card)
        val intent = store.intentFor(card.eventSeq)!!
        // A stale scheduler pass must not claim it scheduled/cancelled a newer revision.
        assertFalse(
            store.markIntent(
                card.eventSeq, intent.triggerId, PowerStore.DesiredSchedule.CANCELLED,
                "stale", revision = intent.revision + 1, occurrenceKey = intent.occurrenceKey
            )
        )
        assertEquals(PowerStore.DesiredSchedule.PENDING, store.intentFor(card.eventSeq)!!.desired)
        assertTrue(
            store.markIntent(
                card.eventSeq, intent.triggerId, PowerStore.DesiredSchedule.SCHEDULED,
                null, revision = intent.revision, occurrenceKey = intent.occurrenceKey
            )
        )
        assertEquals(PowerStore.DesiredSchedule.SCHEDULED, store.intentFor(card.eventSeq)!!.desired)
    }

    @Test
    fun aDateOnlyDeadlineWithAReminderGetsNoFabricatedMidnightAlarm() {
        val card = store.newDraft(
            DraftForm(
                TemporalKind.DEADLINE, "交材料",
                endText = "2026-12-24",
                reminderMinutes = 30,
                zoneId = zone.id
            )
        )
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        assertNull(store.intentFor(card.eventSeq))
    }

    @Test
    fun aStaleSameProcessBatchIsReleasedByTheLease() {
        val card = store.newDraft(activityForm("僵死批次"))
        val session = store.beginAiBatch(setOf(card.draftId), setOf(DraftOperation.CREATE))
        // A live batch with a fresh heartbeat is not an orphan.
        assertFalse(store.recoverInterruptedAi())
        // Simulate a cancelled runner: same owner, heartbeat long past the lease.
        store.writableDatabase.execSQL(
            "UPDATE ai_sessions SET updated_ms=? WHERE session_id=?",
            arrayOf(System.currentTimeMillis() - 2 * PowerStore.DEFAULT_STALE_MS, session.sessionId)
        )
        assertTrue(store.recoverInterruptedAi())
        assertFalse(store.isLocked())
        assertEquals(PowerStore.AiState.INTERRUPTED, store.currentAiSession()!!.state)
        // The deck is usable again immediately.
        assertNotNull(store.newDraft(activityForm("之后")))
    }

    @Test
    fun aRestoredBackupRebuildsItsDesiredSchedule() {
        val card = store.newDraft(alarmForm(minutesAhead = 300))
        commit(card)
        val snapshot = store.exportBackup()
        val restored = newStore()
        try {
            restored.restoreBackup(snapshot)
            val intent = restored.intentFor(card.eventSeq)
            assertNotNull("restore must rebuild schedule intents", intent)
            assertEquals(PowerStore.DesiredSchedule.PENDING, intent!!.desired)
        } finally {
            val name = restored.databaseName
            restored.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun theAiRunnerReleasesTheLockAndPublishesOnlyItsScope() = kotlinx.coroutines.runBlocking {
        val first = store.newDraft(alarmForm("A", minutesAhead = 200))
        val second = store.newDraft(alarmForm("B", minutesAhead = 260))
        val session = store.beginAiBatch(
            setOf(first.draftId, second.draftId),
            DraftOperation.entries.toSet()
        )
        val report = dev.powerfulcalendar.ai.AiBatchRunner(store)
            .run(session, dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH)
        assertEquals(2, report.published)
        assertEquals(0, report.kept)
        assertFalse(store.isLocked())
        assertEquals(0, store.pendingCount())
        assertNotNull(store.get(first.eventSeq))
        assertNotNull(store.get(second.eventSeq))
        assertEquals(PowerStore.AiState.COMPLETED, store.currentAiSession()!!.state)
    }

    @Test
    fun theAiRunnerKeepsAConflictedCardForHumanReview() = kotlinx.coroutines.runBlocking {
        val base = store.newDraft(activityForm("冲突"))
        commit(base)
        val seq = base.eventSeq
        val stale = store.newDraft(
            DraftForm.from(store.get(seq)!!).copy(title = "AI 旧提案"), seq, DraftOperation.REPLACE
        )
        // Another card commits first, making `stale` outdated.
        val winner = store.newDraft(
            DraftForm.from(store.get(seq)!!).copy(title = "人工胜出"), seq, DraftOperation.REPLACE
        )
        commit(winner)

        val session = store.beginAiBatch(setOf(stale.draftId), DraftOperation.entries.toSet())
        val report = dev.powerfulcalendar.ai.AiBatchRunner(store)
            .run(session, dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH)
        assertEquals(0, report.published)
        assertEquals(1, report.kept)
        // Never silently overwritten; the card is retained and flagged.
        assertEquals("人工胜出", store.get(seq)!!.title)
        assertEquals(DraftStatus.CONFLICT, store.draft(stale.draftId)!!.status)
        assertFalse(store.isLocked())
    }

    @Test
    fun identityHighWaterSurvivesBackup() {
        repeat(3) { store.newDraft(alarmForm("E$it")) }
        val snapshot = store.exportBackup()
        val restored = newStore()
        try {
            restored.restoreBackup(snapshot)
            assertEquals(4L, restored.nextSequencePreview())
            val next = restored.newDraft(alarmForm("after restore"))
            assertEquals(4L, next.eventSeq)
        } finally {
            val name = restored.databaseName
            restored.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun timestampsAndZoneRoundTripThroughThePayload() {
        val card = store.newDraft(activityForm("时区").copy(notes = "备注", zoneId = zone.id))
        commit(card)
        val record = store.get(card.eventSeq)!!
        assertEquals(zone.id, record.zoneId)
        assertEquals("备注", record.notes)

        // A record pinned to another zone parses and stores its wall-clock text in THAT zone.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val zoned = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "东京闹钟",
                startText = "2030-10-21 07:30",
                zoneId = tokyo.id
            )
        )
        commit(zoned)
        val stored = store.get(zoned.eventSeq)!!
        assertEquals(tokyo.id, stored.zoneId)
        assertEquals(
            java.time.LocalDateTime.of(2030, 10, 21, 7, 30)
                .atZone(tokyo).toInstant().toEpochMilli(),
            stored.startMillis
        )
        assertTrue(JSONObject(store.exportBackup().toString()).has("objects"))
    }
}
