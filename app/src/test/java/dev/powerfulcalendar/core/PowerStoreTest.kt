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
import dev.powerfulcalendar.ai.AiDraftService
import dev.powerfulcalendar.ai.AiInterpretation
import dev.powerfulcalendar.ai.AiProposal
import dev.powerfulcalendar.ai.AiResponseParser
import dev.powerfulcalendar.interop.IcsCodec
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
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
            reminderMinutes = 0,
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

    @Test
    fun directEditorCheckpointsOnceAndNoChangeSaveClearsItWithoutRevision() {
        val created = store.newDraft(activityForm("formal"))
        assertTrue(commit(created) is PowerStore.CommitOutcome.Committed)
        val record = store.get(created.eventSeq)!!
        val identityCountBefore = store.identityCount()
        val nextSequenceBefore = store.nextSequencePreview()
        val session = EditSession.open(store, record)
        try {
            assertEquals(0, store.pendingDraftsFor(record.eventSeq).size)
            assertEquals(identityCountBefore, store.identityCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())

            session.edit(session.form.copy(title = "edited in memory"))
            assertEquals(0, store.pendingDraftsFor(record.eventSeq).size)
            assertEquals(identityCountBefore, store.identityCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())
            val checkpoint = session.checkpoint()
            assertNotNull(checkpoint)
            assertEquals(1, store.pendingDraftsFor(record.eventSeq).size)
            assertEquals(checkpoint!!.draftId, session.checkpoint()!!.draftId)
            assertEquals(record.eventSeq, checkpoint.eventSeq)
            assertEquals(identityCountBefore, store.identityCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())

            session.discardEditingChanges()
            assertNull(session.checkpoint())
            assertEquals(0, store.pendingDraftsFor(record.eventSeq).size)
            val outcome = session.save()
            assertTrue(outcome is PowerStore.DirectEditOutcome.Saved && !outcome.changed)
            assertEquals(record.revision, store.get(record.eventSeq)!!.revision)
            assertEquals(0, store.pendingDraftsFor(record.eventSeq).size)
            assertEquals(identityCountBefore, store.identityCount())
            assertEquals(nextSequenceBefore, store.nextSequencePreview())
        } finally {
            session.close()
        }
    }

    @Test
    fun skippingOneRecurringOccurrencePersistsWithoutChangingTheSeriesAcrossRestart() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstLocal = LocalDateTime.of(2026, 10, 20, 7, 30)
        val firstMillis = firstLocal.atZone(shanghai).toInstant().toEpochMilli()
        val recurrence = Recurrence(freq = RecurrenceFreq.DAILY, interval = 1, count = 5)
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "单次跳过持久化",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            reminderMinutes = -1,
            zoneId = shanghai.id,
            recurrence = recurrence
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)

        val before = requireNotNull(store.get(card.eventSeq))
        val from = LocalDateTime.of(2026, 10, 20, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val until = LocalDateTime.of(2026, 10, 26, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val originalKeys = RecurrenceEngine.occurrences(before, from, until).map { it.key }
        val skippedKey = RecurrenceEngine.keyFor(LocalDateTime.of(2026, 10, 21, 7, 30))
        assertEquals(
            (20..24).map { "2026-10-%02dT07:30".format(it) },
            originalKeys
        )

        val session = EditSession.open(store, before)
        try {
            session.edit(session.form.copy(
                recurrence = session.form.recurrence.copy(exceptions = setOf(skippedKey))
            ))
            val saved = session.save()
            assertTrue("skip exception must be committed", saved is PowerStore.DirectEditOutcome.Saved && saved.changed)
        } finally {
            session.close()
        }

        val after = requireNotNull(store.get(card.eventSeq))
        assertEquals(before.eventSeq, after.eventSeq)
        assertEquals(before.revision + 1, after.revision)
        assertEquals(before.startMillis, after.startMillis)
        assertEquals(before.recurrence.copy(exceptions = setOf(skippedKey)), after.recurrence)
        assertEquals(
            originalKeys.filterNot { it == skippedKey },
            RecurrenceEngine.occurrences(after, from, until).map { it.key }
        )

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = requireNotNull(store.get(card.eventSeq))
        assertEquals(after, reopened)
        assertEquals(
            originalKeys.filterNot { it == skippedKey },
            RecurrenceEngine.occurrences(reopened, from, until).map { it.key }
        )
        assertEquals(1L, store.identityCount())
        assertEquals(2, store.history(card.eventSeq).size)
    }

    @Test
    fun editingOneRecurringOccurrencePersistsOverrideWithoutChangingOtherSlotsAcrossRestart() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstLocal = LocalDateTime.of(2026, 10, 20, 7, 30)
        val firstMillis = firstLocal.atZone(shanghai).toInstant().toEpochMilli()
        val recurrence = Recurrence(freq = RecurrenceFreq.DAILY, interval = 1, count = 5)
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "单次改期持久化",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            reminderMinutes = -1,
            zoneId = shanghai.id,
            recurrence = recurrence
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)

        val before = requireNotNull(store.get(card.eventSeq))
        val from = LocalDateTime.of(2026, 10, 20, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val until = LocalDateTime.of(2026, 10, 26, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val original = RecurrenceEngine.occurrences(before, from, until)
        val originalKeys = original.map { it.key }
        val selectedKey = RecurrenceEngine.keyFor(LocalDateTime.of(2026, 10, 21, 7, 30))
        val movedStart = LocalDateTime.of(2026, 10, 21, 9, 15)
            .atZone(shanghai).toInstant().toEpochMilli()
        assertEquals((20..24).map { "2026-10-%02dT07:30".format(it) }, originalKeys)

        val session = EditSession.open(store, before)
        try {
            session.edit(session.form.copy(
                recurrence = session.form.recurrence.copy(
                    overrides = mapOf(selectedKey to OccurrenceOverride(movedStart, null))
                )
            ))
            val saved = session.save()
            assertTrue(
                "single-instance override must be committed",
                saved is PowerStore.DirectEditOutcome.Saved && saved.changed
            )
        } finally {
            session.close()
        }

        val after = requireNotNull(store.get(card.eventSeq))
        assertEquals(before.eventSeq, after.eventSeq)
        assertEquals(before.revision + 1, after.revision)
        assertEquals(before.startMillis, after.startMillis)
        assertEquals(
            before.recurrence.copy(overrides = mapOf(
                selectedKey to OccurrenceOverride(movedStart, null)
            )),
            after.recurrence
        )
        val projected = RecurrenceEngine.occurrences(after, from, until)
        assertEquals(originalKeys, projected.map { it.key })
        assertEquals(movedStart, projected.single { it.key == selectedKey }.startMillis)
        original.filterNot { it.key == selectedKey }.forEach { unchanged ->
            assertEquals(
                "non-selected slot ${unchanged.key} must retain its time",
                unchanged.startMillis,
                projected.single { it.key == unchanged.key }.startMillis
            )
        }

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = requireNotNull(store.get(card.eventSeq))
        assertEquals(after, reopened)
        val reopenedProjection = RecurrenceEngine.occurrences(reopened, from, until)
        assertEquals(projected, reopenedProjection)
        assertEquals(1L, store.identityCount())
        assertEquals(2, store.history(card.eventSeq).size)
    }

    @Test
    fun wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstLocal = LocalDateTime.of(2026, 10, 20, 7, 30)
        val firstMillis = firstLocal.atZone(shanghai).toInstant().toEpochMilli()
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "整组编辑前",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            reminderMinutes = -1,
            zoneId = shanghai.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 5)
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)

        val before = requireNotNull(store.get(card.eventSeq))
        val from = LocalDateTime.of(2026, 10, 20, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val until = LocalDateTime.of(2026, 10, 26, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val editedRule = before.recurrence.copy(
            count = 4,
            exceptions = setOf("2026-10-21T07:30")
        )

        val session = EditSession.open(store, before)
        try {
            session.edit(session.form.copy(
                title = "整组编辑后",
                recurrence = editedRule
            ))
            val result = session.save()
            assertTrue(result is PowerStore.DirectEditOutcome.Saved && result.changed)
        } finally {
            session.close()
        }

        val after = requireNotNull(store.get(before.eventSeq))
        val expectedKeys = listOf(
            "2026-10-20T07:30",
            "2026-10-22T07:30",
            "2026-10-23T07:30"
        )
        assertEquals(before.eventSeq, after.eventSeq)
        assertEquals(before.revision + 1, after.revision)
        assertEquals(before.startMillis, after.startMillis)
        assertEquals("整组编辑后", after.title)
        assertEquals(editedRule, after.recurrence)
        assertEquals(expectedKeys, RecurrenceEngine.occurrences(after, from, until).map { it.key })
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(2, store.history(before.eventSeq).size)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = requireNotNull(store.get(before.eventSeq))
        assertEquals(after, reopened)
        assertEquals(expectedKeys, RecurrenceEngine.occurrences(reopened, from, until).map { it.key })
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(2, store.history(before.eventSeq).size)
    }

    @Test
    fun thisAndFutureRecurringEditSplitsIdentityAndMigratesOnlyCompatibleFutureState() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstMillis = LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val selectedKey = "2026-10-22T07:30"
        val overriddenKey = "2026-10-23T07:30"
        val skippedKey = "2026-10-24T07:30"
        val priorCompletedKey = "2026-10-20T07:30"
        val futureCompletedKey = selectedKey
        val movedStart = LocalDateTime.of(2026, 10, 23, 10, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val recurrence = Recurrence(
            freq = RecurrenceFreq.DAILY,
            count = 5,
            exceptions = setOf(skippedKey),
            overrides = mapOf(overriddenKey to OccurrenceOverride(movedStart, null))
        )
        val form = DraftForm(
            kind = TemporalKind.ALARM,
            title = "原始系列",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            reminderMinutes = -1,
            zoneId = shanghai.id,
            recurrence = recurrence,
            trackingEnabled = true,
            completedKeys = setOf(priorCompletedKey, futureCompletedKey)
        )
        val card = store.newDraft(form)
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(card.eventSeq))
        val from = LocalDateTime.of(2026, 10, 20, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()
        val until = LocalDateTime.of(2026, 10, 26, 0, 0)
            .atZone(shanghai).toInstant().toEpochMilli()

        val lease = store.beginHumanEdit(before.eventSeq)
        val outcome = try {
            val selectedMillis = LocalDateTime.of(2026, 10, 22, 7, 30)
                .atZone(shanghai).toInstant().toEpochMilli()
            store.saveThisAndFutureRecurringEdit(
                eventSeq = before.eventSeq,
                expectedRevision = before.revision,
                selectedOccurrenceKey = selectedKey,
                form = DraftForm.from(before).copy(
                    title = "从所选实例开始",
                    startText = DraftForm.formatInstant(selectedMillis, shanghai)
                ),
                editorToken = lease.token
            )
        } finally {
            store.endHumanEdit(lease.token)
        }

        assertTrue("future-scope edit should split atomically", outcome is PowerStore.RecurrenceSplitOutcome.Applied)
        outcome as PowerStore.RecurrenceSplitOutcome.Applied
        assertEquals(before.eventSeq, outcome.oldEventSeq)
        assertEquals(before.revision + 1, outcome.oldRevision)
        assertEquals(2L, outcome.newEventSeq)
        assertEquals(1L, outcome.newRevision)

        val oldSeries = requireNotNull(store.get(outcome.oldEventSeq))
        assertEquals(before.startMillis, oldSeries.startMillis)
        assertEquals(2, oldSeries.recurrence.count)
        assertEquals("2026-10-21", oldSeries.recurrence.activeUntilDate)
        assertEquals(emptySet<String>(), oldSeries.recurrence.exceptions)
        assertTrue(oldSeries.recurrence.overrides.isEmpty())
        assertEquals(setOf(priorCompletedKey), oldSeries.completedKeys)
        assertEquals(
            listOf("2026-10-20T07:30", "2026-10-21T07:30"),
            RecurrenceEngine.occurrences(oldSeries, from, until).map { it.key }
        )

        val newSeries = requireNotNull(store.get(outcome.newEventSeq))
        assertEquals("从所选实例开始", newSeries.title)
        assertEquals(1L, newSeries.revision)
        assertEquals(3, newSeries.recurrence.count)
        assertEquals(selectedKey, RecurrenceEngine.keyFor(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(newSeries.startMillis!!), shanghai)
        ))
        assertEquals(setOf(skippedKey), newSeries.recurrence.exceptions)
        assertEquals(mapOf(overriddenKey to OccurrenceOverride(movedStart, null)), newSeries.recurrence.overrides)
        assertEquals(setOf(futureCompletedKey), newSeries.completedKeys)
        val newOccurrences = RecurrenceEngine.occurrences(newSeries, from, until)
        assertEquals(listOf(selectedKey, overriddenKey), newOccurrences.map { it.key })
        assertEquals(movedStart, newOccurrences.single { it.key == overriddenKey }.startMillis)
        assertEquals(2L, store.identityCount())
        assertEquals(3L, store.nextSequencePreview())
        assertEquals(2, store.history(outcome.oldEventSeq).size)
        assertEquals(1, store.history(outcome.newEventSeq).size)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        assertEquals(oldSeries, store.get(outcome.oldEventSeq))
        assertEquals(newSeries, store.get(outcome.newEventSeq))
        assertEquals(listOf("2026-10-20T07:30", "2026-10-21T07:30"),
            RecurrenceEngine.occurrences(store.get(outcome.oldEventSeq)!!, from, until).map { it.key })
        assertEquals(listOf(selectedKey, overriddenKey),
            RecurrenceEngine.occurrences(store.get(outcome.newEventSeq)!!, from, until).map { it.key })
    }

    @Test
    fun thisAndFutureRecurringEditRollsBackBothSeriesWhenNewIdentityInsertFails() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstMillis = LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "原始系列",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            reminderMinutes = -1,
            zoneId = shanghai.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 5)
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(card.eventSeq))
        val selectedKey = "2026-10-22T07:30"
        val selectedMillis = LocalDateTime.of(2026, 10, 22, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val lease = store.beginHumanEdit(before.eventSeq)
        try {
            store.writableDatabase.execSQL(
                "CREATE TRIGGER fail_future_series_insert BEFORE INSERT ON objects " +
                    "WHEN NEW.event_seq=2 BEGIN SELECT RAISE(ABORT, 'injected split failure'); END"
            )
            val attempt = runCatching {
                store.saveThisAndFutureRecurringEdit(
                    eventSeq = before.eventSeq,
                    expectedRevision = before.revision,
                    selectedOccurrenceKey = selectedKey,
                    form = DraftForm.from(before).copy(
                        title = "不应落盘",
                        startText = DraftForm.formatInstant(selectedMillis, shanghai)
                    ),
                    editorToken = lease.token
                )
            }
            assertTrue("injected new-object failure should escape the transaction", attempt.isFailure)
            store.writableDatabase.execSQL("DROP TRIGGER fail_future_series_insert")
        } finally {
            store.endHumanEdit(lease.token)
        }

        assertEquals(before, store.get(before.eventSeq))
        assertNull(store.get(2L))
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(1, store.history(before.eventSeq).size)
    }

    @Test
    fun thisAndFutureEditOnFirstActiveSlotKeepsOneSeriesIdentity() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstMillis = LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "首项前无旧序列",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            reminderMinutes = -1,
            zoneId = shanghai.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY, count = 3)
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(card.eventSeq))
        val lease = store.beginHumanEdit(before.eventSeq)
        val outcome = try {
            store.saveThisAndFutureRecurringEdit(
                eventSeq = before.eventSeq,
                expectedRevision = before.revision,
                selectedOccurrenceKey = "2026-10-20T07:30",
                form = DraftForm.from(before).copy(title = "首项起应用新规则"),
                editorToken = lease.token
            )
        } finally {
            store.endHumanEdit(lease.token)
        }

        assertEquals(
            PowerStore.RecurrenceSplitOutcome.Applied(before.eventSeq, 2L, before.eventSeq, 2L),
            outcome
        )
        assertEquals("首项起应用新规则", store.get(before.eventSeq)!!.title)
        assertEquals(1, store.all().size)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(2, store.history(before.eventSeq).size)
    }

    @Test
    fun thisAndFutureEditOnFirstNonSkippedSlotKeepsOneSeriesIdentity() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val firstMillis = LocalDateTime.of(2026, 10, 20, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "前两项已跳过",
            startText = DraftForm.formatInstant(firstMillis, shanghai),
            reminderMinutes = -1,
            zoneId = shanghai.id,
            recurrence = Recurrence(
                freq = RecurrenceFreq.DAILY,
                count = 3,
                exceptions = setOf("2026-10-20T07:30", "2026-10-21T07:30")
            )
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val before = requireNotNull(store.get(card.eventSeq))
        val selectedMillis = LocalDateTime.of(2026, 10, 22, 7, 30)
            .atZone(shanghai).toInstant().toEpochMilli()
        val lease = store.beginHumanEdit(before.eventSeq)
        val outcome = try {
            store.saveThisAndFutureRecurringEdit(
                eventSeq = before.eventSeq,
                expectedRevision = before.revision,
                selectedOccurrenceKey = "2026-10-22T07:30",
                form = DraftForm.from(before).copy(
                    title = "首个有效实例起应用新规则",
                    startText = DraftForm.formatInstant(selectedMillis, shanghai)
                ),
                editorToken = lease.token
            )
        } finally {
            store.endHumanEdit(lease.token)
        }

        assertEquals(
            PowerStore.RecurrenceSplitOutcome.Applied(before.eventSeq, 2L, before.eventSeq, 2L),
            outcome
        )
        assertEquals("首个有效实例起应用新规则", store.get(before.eventSeq)!!.title)
        assertEquals(1, store.get(before.eventSeq)!!.recurrence.count)
        assertTrue(store.get(before.eventSeq)!!.recurrence.exceptions.isEmpty())
        assertEquals(
            listOf("2026-10-22T07:30"),
            RecurrenceEngine.occurrences(
                store.get(before.eventSeq)!!,
                selectedMillis - 1,
                selectedMillis + 3 * 24 * 60 * 60 * 1000L
            ).map { it.key }
        )
        assertEquals(1, store.all().size)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(2, store.history(before.eventSeq).size)
    }

    @Test
    fun humanLeaseNeverLeaksAcrossCallersOrProcessesAndOnlyForeignStaleLeaseRecovers() {
        val database = "lease-" + UUID.randomUUID() + ".db"
        val owner = PowerStore(context, database, "process-owner")
        val intruder = PowerStore(context, database, "process-intruder")
        try {
            val lease = owner.beginHumanEdit()
            assertTrue(runCatching { owner.beginHumanEdit() }.isFailure)
            assertEquals(lease, owner.beginHumanEdit(authenticatedToken = lease.token))
            assertTrue(runCatching { intruder.beginHumanEdit() }.isFailure)
            assertFalse(intruder.touchHumanEdit(lease.token))
            assertFalse(intruder.endHumanEdit(lease.token))
            assertFalse(intruder.recoverAbandonedHumanEdit(60_000L))
            assertTrue(owner.hasHumanEditor())

            owner.writableDatabase.execSQL(
                "UPDATE human_editor SET touched_ms=? WHERE id=1",
                arrayOf(System.currentTimeMillis() - 120_000L)
            )
            assertTrue(intruder.recoverAbandonedHumanEdit(60_000L))
            assertFalse(owner.hasHumanEditor())
        } finally {
            owner.close(); intruder.close(); context.deleteDatabase(database)
        }
    }

    @Test
    fun humanEditorBlocksAiMutationAndDirectDeleteCleansDrafts() {
        val created = store.newDraft(activityForm("delete me"))
        assertTrue(commit(created) is PowerStore.CommitOutcome.Committed)
        val target = store.get(created.eventSeq)!!
        val batchCard = store.newDraft(alarmForm("batch"))
        val session = EditSession.open(store, target)
        try {
            assertTrue(runCatching { store.newDraft(alarmForm("AI"), actor = "ai") }.isFailure)
            assertTrue(runCatching {
                store.beginAiBatch(mapOf(batchCard.draftId to batchCard.draftVersion),
                    setOf(DraftOperation.CREATE))
            }.isFailure)
            session.edit(session.form.copy(notes = "checkpoint"))
            assertNotNull(session.checkpoint())
            assertTrue(session.delete() is PowerStore.DirectEditOutcome.Saved)
            assertNull(store.get(target.eventSeq))
            assertTrue(store.isRetired(target.eventSeq))
            assertEquals(0, store.pendingDraftsFor(target.eventSeq).size)
        } finally {
            session.close()
        }
    }

    @Test
    fun undatedMemoPersistsAndCompletionCanBeReopenedAfterRestart() {
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO, title = "无日期事项", zoneId = zone.id
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val memo = store.get(card.eventSeq)!!
        assertNull(memo.anchorMillis())
        assertTrue(memo.trackingEnabled)
        assertTrue(store.all().any { it.eventSeq == card.eventSeq })
        assertTrue(store.setCompleted(card.eventSeq, "single", true, memo.revision))
        assertFalse(store.setCompleted(card.eventSeq, "single", false, memo.revision))

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(card.eventSeq)!!
        assertTrue("single" in reopened.completedKeys)
        assertEquals(2L, reopened.revision)
        assertTrue(store.setCompleted(card.eventSeq, "single", false, reopened.revision))
        val pending = store.get(card.eventSeq)!!
        assertTrue(pending.completedKeys.isEmpty())
        assertEquals(3L, pending.revision)
        assertEquals(3, store.history(card.eventSeq).size)
    }

    @Test
    fun undatedMemoKeepsDateAndTimeFieldsAbsentAcrossRestart() {
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO, title = "未排期备忘", zoneId = zone.id
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)

        val memo = store.get(card.eventSeq)!!
        assertEquals(TemporalKind.MEMO, memo.kind)
        assertNull(memo.memoStartDate)
        assertNull(memo.memoEndDate)
        assertNull(memo.startMillis)
        assertNull(memo.endMillis)
        assertNull(memo.deadlineDate)
        assertNull(memo.deadlineMillis)
        assertNull(memo.anchorMillis())
        assertNull(memo.spanEndMillis())
        assertEquals("无日期 · 待办", TemporalText.summarize(memo))

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopened = store.get(card.eventSeq)!!
        assertEquals(TemporalKind.MEMO, reopened.kind)
        assertNull(reopened.memoStartDate)
        assertNull(reopened.memoEndDate)
        assertNull(reopened.startMillis)
        assertNull(reopened.endMillis)
        assertNull(reopened.deadlineDate)
        assertNull(reopened.deadlineMillis)
        assertNull(reopened.anchorMillis())
        assertNull(reopened.spanEndMillis())
        assertEquals("无日期 · 待办", TemporalText.summarize(reopened))
    }

    @Test
    fun spanningMemoHasOneCompletionInstanceAndKeepsItAcrossRestart() {
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "跨日期待办",
            startText = "2026-10-21",
            endText = "2026-10-23",
            zoneId = zone.id
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val memo = store.get(card.eventSeq)!!

        assertEquals(TemporalKind.MEMO, memo.kind)
        assertEquals("2026-10-21", memo.memoStartDate)
        assertEquals("2026-10-23", memo.memoEndDate)
        assertNull(memo.startMillis)
        assertNull(memo.endMillis)
        assertNull(memo.deadlineDate)
        assertNull(memo.deadlineMillis)
        assertTrue(memo.trackingEnabled)
        assertTrue(memo.completedKeys.isEmpty())
        assertFalse(store.setCompleted(card.eventSeq, "2026-10-22", true, memo.revision))
        assertEquals(memo.revision, store.get(card.eventSeq)!!.revision)
        assertTrue(store.setCompleted(card.eventSeq, "single", true, memo.revision))

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(card.eventSeq)!!

        assertEquals(TemporalKind.MEMO, reopened.kind)
        assertEquals("2026-10-21", reopened.memoStartDate)
        assertEquals("2026-10-23", reopened.memoEndDate)
        assertNull(reopened.startMillis)
        assertNull(reopened.endMillis)
        assertNull(reopened.deadlineDate)
        assertNull(reopened.deadlineMillis)
        assertEquals(setOf("single"), reopened.completedKeys)
        assertEquals(memo.revision + 1, reopened.revision)
    }

    @Test
    fun unboundedRecurringMemoStoresOneSeriesAndProjectsOnlyTheRequestedWindow() {
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "每日待办",
            startText = "2024-01-01",
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY)
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val memo = store.get(card.eventSeq)!!
        val from = LocalDate.parse("2026-10-20").atStartOfDay(zone).toInstant().toEpochMilli()
        val until = LocalDate.parse("2026-10-23").atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(TemporalKind.MEMO, memo.kind)
        assertNull(memo.endMillis)
        assertNull(memo.memoEndDate)
        assertEquals(RecurrenceFreq.DAILY, memo.recurrence.freq)
        assertEquals(1L, store.identityCount())
        assertEquals(1, store.all().size)
        assertEquals(listOf("2026-10-20T00:00", "2026-10-21T00:00", "2026-10-22T00:00"),
            RecurrenceEngine.occurrences(memo, from, until, limit = 10).map { it.key })
        assertEquals("projection must not create event rows", 1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(card.eventSeq)!!
        assertEquals(1L, store.identityCount())
        assertEquals(1, store.all().size)
        assertEquals(RecurrenceFreq.DAILY, reopened.recurrence.freq)
        assertEquals(2L, store.nextSequencePreview())
    }

    @Test
    fun unboundedRecurringMemoKeepsPastIncompleteOccurrenceAcrossRestart() {
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "未完成的旧待办",
            startText = "2024-01-01",
            zoneId = zone.id,
            recurrence = Recurrence(freq = RecurrenceFreq.DAILY)
        ))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val pastDate = LocalDate.parse("2025-03-14")
        val from = pastDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = pastDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val pastKey = "2025-03-14T00:00"
        val beforeRestart = store.get(card.eventSeq)!!

        assertTrue(beforeRestart.completedKeys.isEmpty())
        assertEquals(listOf(pastKey), RecurrenceEngine.occurrences(
            beforeRestart, from, until, limit = 10
        ).map { it.key })
        assertEquals(1L, store.identityCount())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(card.eventSeq)!!
        assertTrue(reopened.completedKeys.isEmpty())
        assertEquals(listOf(pastKey), RecurrenceEngine.occurrences(
            reopened, from, until, limit = 10
        ).map { it.key })
        assertEquals(1, store.all().size)
    }

    @Test
    fun changingReminderPreservesOptionalCompletionTracking() {
        listOf(false, true).forEachIndexed { index, trackingEnabled ->
            val card = store.newDraft(activityForm("提醒设置 $index").copy(
                trackingEnabled = trackingEnabled
            ))
            assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
            val current = store.get(card.eventSeq)!!
            val reminderMinutes = 10 + index
            val edit = store.newDraft(
                DraftForm.from(current).copy(reminderMinutes = reminderMinutes),
                current.eventSeq,
                DraftOperation.REPLACE
            )

            assertTrue(commit(edit) is PowerStore.CommitOutcome.Committed)
            val updated = store.get(card.eventSeq)!!
            assertEquals(trackingEnabled, updated.trackingEnabled)
            assertEquals(reminderMinutes, updated.reminderMinutes)
            assertTrue(updated.completedKeys.isEmpty())
        }
    }

    @Test
    fun changingCompletionTrackingPreservesEventEnabledState() {
        listOf(true to false, false to true).forEachIndexed { index, (enabled, trackingEnabled) ->
            val card = store.newDraft(activityForm("启用状态 $index").copy(
                enabled = enabled,
                trackingEnabled = trackingEnabled
            ))
            assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
            val current = store.get(card.eventSeq)!!
            val edit = store.newDraft(
                DraftForm.from(current).copy(trackingEnabled = !trackingEnabled),
                current.eventSeq,
                DraftOperation.REPLACE
            )

            assertTrue(commit(edit) is PowerStore.CommitOutcome.Committed)
            val updated = store.get(card.eventSeq)!!
            assertEquals(enabled, updated.enabled)
            assertEquals(!trackingEnabled, updated.trackingEnabled)
            assertEquals(current.reminderMinutes, updated.reminderMinutes)
        }
    }

    @Test
    fun changingCompletionTrackingPreservesReminderIntentAcrossRestart() {
        val expectedRecords = listOf(false, true).mapIndexed { index, trackingEnabled ->
            val card = store.newDraft(activityForm("跟踪与提醒独立 $index", 90).copy(
                reminderMinutes = 15,
                trackingEnabled = trackingEnabled
            ))
            assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
            val before = requireNotNull(store.get(card.eventSeq))
            val beforeIntent = requireNotNull(store.intentFor(card.eventSeq))
            assertEquals(PowerStore.DesiredSchedule.PENDING, beforeIntent.desired)

            val edit = store.newDraft(
                DraftForm.from(before).copy(trackingEnabled = !trackingEnabled),
                before.eventSeq,
                DraftOperation.REPLACE
            )
            assertTrue(commit(edit) is PowerStore.CommitOutcome.Committed)

            val after = requireNotNull(store.get(card.eventSeq))
            val afterIntent = requireNotNull(store.intentFor(card.eventSeq))
            assertEquals(before.enabled, after.enabled)
            assertEquals(before.reminderMinutes, after.reminderMinutes)
            assertEquals(before.reminderMethod, after.reminderMethod)
            assertEquals(before.reminderTiming, after.reminderTiming)
            assertEquals(!trackingEnabled, after.trackingEnabled)
            assertTrue(after.completedKeys.isEmpty())
            assertEquals(
                beforeIntent.copy(revision = after.revision),
                afterIntent
            )
            assertEquals(2, store.history(card.eventSeq).size)
            after
        }

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        expectedRecords.forEach { expected ->
            assertEquals(expected, store.get(expected.eventSeq))
            assertEquals(
                expected.revision,
                requireNotNull(store.intentFor(expected.eventSeq)).revision
            )
            assertEquals(
                PowerStore.DesiredSchedule.PENDING,
                store.intentFor(expected.eventSeq)!!.desired
            )
            assertEquals(2, store.history(expected.eventSeq).size)
        }
        assertEquals(2L, store.identityCount())
        assertEquals(3L, store.nextSequencePreview())
    }

    // ---------------------------------------------------------------- permanent identity

    @Test
    fun stagedLastCharacterSurvivesStoreReopen() {
        val card = store.newDraft(activityForm("前缀"))
        val latest = card.form.copy(title = "最后一个字：好")
        val staged = store.stageDraft(card.draftId, latest, card.draftVersion)
        assertTrue(staged is PowerStore.StageResult.Saved)

        // Simulates a process restart boundary after the durable stage (not an OS force-kill test).
        val name = store.databaseName
        store.close()
        store = PowerStore(context, name)
        val reopened = store.draft(card.draftId)!!
        assertEquals("最后一个字：好", reopened.form.title)
        assertEquals(1L, reopened.draftVersion)
        assertEquals(card.eventSeq, reopened.eventSeq)
    }

    @Test
    fun stagingDraftEditKeepsDraftAndEventIdentitiesAcrossRestart() {
        val original = store.newDraft(activityForm("暂存前"))
        val revised = original.form.copy(title = "暂存后")

        assertTrue(store.stageDraft(original.draftId, revised, original.draftVersion)
            is PowerStore.StageResult.Saved)
        val staged = store.draft(original.draftId)!!
        assertEquals(original.draftId, staged.draftId)
        assertEquals(original.eventSeq, staged.eventSeq)
        assertEquals("暂存后", staged.form.title)
        assertEquals(1L, staged.draftVersion)
        assertEquals(1L, store.identityCount())
        assertEquals(original.eventSeq + 1, store.nextSequencePreview())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopened = store.draft(original.draftId)!!
        assertEquals(original.draftId, reopened.draftId)
        assertEquals(original.eventSeq, reopened.eventSeq)
        assertEquals("暂存后", reopened.form.title)
        assertEquals(1L, reopened.draftVersion)
        assertEquals(1L, store.identityCount())
        assertEquals(original.eventSeq + 1, store.nextSequencePreview())
        assertEquals(listOf(original.draftId), store.pendingDrafts().map { it.draftId })
    }

    @Test
    fun indefiniteIcsSeriesUsesOnePermanentEventIdentityForAllOccurrences() {
        val text = listOf(
            "BEGIN:VCALENDAR", "BEGIN:VEVENT",
            "DTSTART:20261019T100000Z", "DTEND:20261019T110000Z",
            "SUMMARY:Weekly Research", "RRULE:FREQ=WEEKLY",
            "END:VEVENT", "END:VCALENDAR"
        ).joinToString("\r\n")
        val imported = IcsCodec.parse(text)
        assertTrue(imported.warnings.toString(), imported.warnings.isEmpty())
        assertEquals(1, imported.series.size)
        val card = store.newDraft(imported.series.single().form)
        val committed = commit(card)
        assertTrue(committed is PowerStore.CommitOutcome.Committed)
        assertEquals(card.eventSeq, (committed as PowerStore.CommitOutcome.Committed).eventSeq)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(1, store.all().size)
        val record = store.get(card.eventSeq)!!
        assertNull(record.recurrence.count)
        assertNull(record.recurrence.untilMillis)
        val start = LocalDate.of(2026, 10, 19).atStartOfDay(ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val end = LocalDate.of(2027, 1, 19).atStartOfDay(ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val occurrences = RecurrenceEngine.occurrences(record, start, end, 80)
        assertTrue("Window should contain many occurrences", occurrences.size > 10)
        assertEquals(occurrences.size, occurrences.map { it.key }.toSet().size)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
    }

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
    fun failedFirstDraftInsertRetiresItsAllocatedSequence() {
        store.writableDatabase.execSQL(
            """CREATE TRIGGER fail_first_draft_insert BEFORE INSERT ON drafts
               BEGIN SELECT RAISE(ABORT,'injected first draft failure'); END"""
        )

        val failure = runCatching { store.newDraft(alarmForm("失败后不复用")) }.exceptionOrNull()
        assertNotNull("the injected trigger must abort the first draft insert", failure)
        assertTrue(failure.toString(), failure.toString().contains("injected first draft failure"))
        assertEquals(0, store.pendingCount())
        assertEquals(1L, store.identityCount())
        store.readableDatabase.rawQuery(
            "SELECT retired_ms FROM identities WHERE event_seq=1",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertFalse(cursor.isNull(0))
        }
        assertEquals(2L, store.nextSequencePreview())

        store.writableDatabase.execSQL("DROP TRIGGER fail_first_draft_insert")
        val retry = store.newDraft(alarmForm("第二次尝试"))
        assertEquals(2L, retry.eventSeq)
        store.readableDatabase.rawQuery(
            "SELECT retired_ms FROM identities WHERE event_seq=?",
            arrayOf(retry.eventSeq.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
    }

    @Test
    fun independentCreateDraftsKeepUniqueDraftIdsAcrossRestart() {
        val first = store.newDraft(activityForm("第一项提案"))
        val second = store.newDraft(activityForm("第二项提案"))

        assertTrue(first.draftId != second.draftId)
        assertTrue(first.eventSeq != second.eventSeq)
        assertEquals(first.eventSeq, store.draft(first.draftId)!!.eventSeq)
        assertEquals(second.eventSeq, store.draft(second.draftId)!!.eventSeq)
        assertEquals(setOf(first.draftId, second.draftId),
            store.pendingDrafts().map { it.draftId }.toSet())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopenedFirst = store.draft(first.draftId)!!
        val reopenedSecond = store.draft(second.draftId)!!
        assertEquals(first.draftId, reopenedFirst.draftId)
        assertEquals(second.draftId, reopenedSecond.draftId)
        assertEquals(first.eventSeq, reopenedFirst.eventSeq)
        assertEquals(second.eventSeq, reopenedSecond.eventSeq)
        assertTrue(reopenedFirst.draftId != reopenedSecond.draftId)
        assertTrue(reopenedFirst.eventSeq != reopenedSecond.eventSeq)
        assertEquals(setOf(first.draftId, second.draftId),
            store.pendingDrafts().map { it.draftId }.toSet())
    }

    @Test
    fun oneEventReusesOneActionableCardAndItsQueueSlot() {
        val base = store.newDraft(activityForm())
        val committed = commit(base) as PowerStore.CommitOutcome.Committed
        val seq = committed.eventSeq
        val form = DraftForm.from(store.get(seq)!!)
        val d1 = store.newDraft(form.copy(title = "first"), seq, DraftOperation.REPLACE)
        val d2 = store.newDraft(form.copy(title = "last"), seq, DraftOperation.REPLACE)
        assertEquals(d1.draftId, d2.draftId)
        assertEquals(d1.queueOrder, d2.queueOrder)
        assertEquals(1, store.pendingDraftsFor(seq).size)
        assertEquals("last", store.draft(d1.draftId)!!.form.title)
        assertEquals(d1.draftVersion + 1, d2.draftVersion)
        assertTrue(d1.queueOrder > base.queueOrder)
        assertTrue(store.discard(d2.draftId))
        val d3 = store.newDraft(form.copy(title = "fresh"), seq, DraftOperation.REPLACE)
        assertTrue(d3.draftId != d2.draftId)
        assertFalse("Undo cannot produce two actionable drafts", store.undoDiscard(d2.draftId))
    }

    @Test
    fun repeatedSameEventProposalReusesOneActionableDraftAcrossRestart() {
        val base = store.newDraft(activityForm("formal"))
        assertTrue(commit(base) is PowerStore.CommitOutcome.Committed)
        val seq = base.eventSeq
        val form = DraftForm.from(store.get(seq)!!)

        val first = store.newDraft(form.copy(title = "first"), seq, DraftOperation.REPLACE)
        val latest = store.newDraft(form.copy(title = "latest"), seq, DraftOperation.REPLACE)
        assertEquals(first.draftId, latest.draftId)
        assertEquals(first.queueOrder, latest.queueOrder)
        assertEquals(first.draftVersion + 1, latest.draftVersion)
        assertEquals(1, store.pendingDraftsFor(seq).size)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopened = store.pendingDraftsFor(seq).single()
        assertEquals(latest.draftId, reopened.draftId)
        assertEquals(latest.queueOrder, reopened.queueOrder)
        assertEquals(latest.draftVersion, reopened.draftVersion)
        assertEquals("latest", reopened.form.title)
        assertEquals(1, store.pendingDraftsFor(seq).count { it.isActionable })

        assertTrue(store.discard(reopened.draftId))
        val replacement = store.newDraft(form.copy(title = "replacement"), seq, DraftOperation.REPLACE)
        assertTrue(replacement.draftId != reopened.draftId)
        assertEquals(1, store.pendingDraftsFor(seq).size)
        assertFalse("Undo cannot produce two actionable drafts", store.undoDiscard(reopened.draftId))
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

    @Test
    fun independentQueueOrdersPersistAcrossRestartAndDoNotFollowEventNumbers() {
        val eventA = store.newDraft(activityForm("A"))
        assertTrue(commit(eventA) is PowerStore.CommitOutcome.Committed)
        val eventB = store.newDraft(activityForm("B"))
        assertTrue(commit(eventB) is PowerStore.CommitOutcome.Committed)

        val proposalB = store.newDraft(
            DraftForm.from(store.get(eventB.eventSeq)!!).copy(title = "B proposal"),
            eventB.eventSeq,
            DraftOperation.REPLACE
        )
        val proposalA = store.newDraft(
            DraftForm.from(store.get(eventA.eventSeq)!!).copy(title = "A proposal"),
            eventA.eventSeq,
            DraftOperation.REPLACE
        )
        assertTrue(proposalB.queueOrder > 0)
        assertTrue(proposalA.queueOrder > proposalB.queueOrder)
        assertTrue(proposalB.eventSeq > proposalA.eventSeq)
        assertTrue(proposalA.queueOrder != proposalB.queueOrder)
        assertEquals(listOf(proposalB.draftId, proposalA.draftId),
            store.pendingDrafts().map { it.draftId })

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopened = store.pendingDrafts()
        assertEquals(listOf(proposalB.draftId, proposalA.draftId), reopened.map { it.draftId })
        assertEquals(listOf(proposalB.queueOrder, proposalA.queueOrder),
            reopened.map { it.queueOrder })
        assertEquals(listOf(eventB.eventSeq, eventA.eventSeq), reopened.map { it.eventSeq })
        assertEquals(2, reopened.map { it.queueOrder }.toSet().size)
    }

    @Test
    fun openingDeckAfterStoreReopenFocusesOldestQueueOrder() = kotlinx.coroutines.runBlocking {
        val oldest = store.newDraft(activityForm("oldest"), actor = "user")
        val newer = store.newDraft(activityForm("newer"), actor = "user")
        assertTrue(oldest.queueOrder < newer.queueOrder)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val controller = DeckController(
            StoreDeckPersistence(store),
            { 1_000L },
            kotlinx.coroutines.Dispatchers.Unconfined
        )
        controller.open()

        assertEquals(listOf(oldest.draftId, newer.draftId), controller.state.cards.map { it.draftId })
        assertEquals(oldest.draftId, controller.state.currentId)
        assertEquals(oldest.queueOrder, controller.state.current!!.queueOrder)
    }

    @Test
    fun changingDraftEventDateDoesNotReorderQueueAfterRestart() {
        val oldest = store.newDraft(activityForm("oldest queue item", 60), actor = "user")
        val newer = store.newDraft(activityForm("newer queue item", 120), actor = "user")
        assertTrue(oldest.queueOrder < newer.queueOrder)
        assertTrue(oldest.form.startText < newer.form.startText)

        val movedStart = System.currentTimeMillis() + 180 * 60_000L
        val movedStartText = DraftForm.formatInstant(movedStart, zone)
        val movedEndText = DraftForm.formatInstant(movedStart + 3_600_000L, zone)
        assertTrue(store.stageDraft(
            oldest.draftId,
            oldest.form.copy(startText = movedStartText, endText = movedEndText),
            oldest.draftVersion
        ) is PowerStore.StageResult.Saved)

        val changed = store.draft(oldest.draftId)!!
        assertTrue(changed.form.startText > newer.form.startText)
        assertEquals(listOf(oldest.draftId, newer.draftId), store.pendingDrafts().map { it.draftId })

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopened = store.pendingDrafts()
        assertEquals(listOf(oldest.draftId, newer.draftId), reopened.map { it.draftId })
        assertEquals(listOf(oldest.queueOrder, newer.queueOrder), reopened.map { it.queueOrder })
        assertEquals(movedStartText, reopened.first().form.startText)
    }

    @Test
    fun aiProposalAppendsAtQueueTailAndRefreshPreservesCurrentFocus() =
        kotlinx.coroutines.runBlocking {
            val first = store.newDraft(activityForm("first"), actor = "user")
            val second = store.newDraft(activityForm("second"), actor = "user")
            val controller = DeckController(
                StoreDeckPersistence(store),
                { 1_000L },
                kotlinx.coroutines.Dispatchers.Unconfined
            )
            controller.open()
            assertTrue(controller.move(1))
            val focusedId = controller.state.currentId
            assertEquals(second.draftId, focusedId)

            val created = AiDraftService(store).createDrafts(
                AiInterpretation(
                    proposals = listOf(
                        AiProposal(
                            operation = DraftOperation.CREATE,
                            eventSeq = null,
                            form = activityForm("AI proposal")
                        )
                    )
                )
            )

            assertTrue(created.warnings.isEmpty())
            assertEquals(1, created.draftIds.size)
            val aiDraftId = created.draftIds.single()
            val queue = store.pendingDrafts()
            assertEquals(listOf(first.draftId, second.draftId, aiDraftId), queue.map { it.draftId })
            assertTrue(queue.last().queueOrder > second.queueOrder)

            controller.refresh()
            assertEquals(focusedId, controller.state.currentId)
            assertEquals("second", controller.state.form!!.title)
            assertEquals(queue.map { it.draftId }, controller.state.cards.map { it.draftId })
        }

    @Test
    fun parsedAiCreateAndReplaceRemainDraftsUntilExplicitCommit() {
        val originalDraft = store.newDraft(activityForm("formal before AI"), actor = "user")
        assertTrue(commit(originalDraft) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(originalDraft.eventSeq))
        val originalSeq = original.eventSeq

        val raw = """
            {"events":[
              {"operation":"CREATE","kind":"ACTIVITY","title":"AI create",
               "start":"2026-10-24 09:00","end":"2026-10-24 10:00"},
              {"operation":"REPLACE","eventSeq":$originalSeq,"kind":"ACTIVITY",
               "title":"AI replacement","start":"2026-10-24 11:00","end":"2026-10-24 12:00"}
            ]}
        """.trimIndent()
        val interpretation = AiResponseParser.parse(raw, listOf(original), zone)

        assertTrue(interpretation.warnings.toString(), interpretation.warnings.isEmpty())
        assertEquals(listOf(DraftOperation.CREATE, DraftOperation.REPLACE),
            interpretation.proposals.map { it.operation })
        assertEquals(originalSeq, interpretation.proposals[1].eventSeq)

        val result = AiDraftService(store).createDrafts(interpretation)

        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        assertEquals(2, result.draftIds.size)
        val drafts = result.draftIds.map { requireNotNull(store.draft(it)) }
        val createDraft = drafts.single { it.operation == DraftOperation.CREATE }
        val replaceDraft = drafts.single { it.operation == DraftOperation.REPLACE }
        assertEquals(originalSeq, replaceDraft.eventSeq)
        assertEquals("AI create", createDraft.form.title)
        assertEquals("AI replacement", replaceDraft.form.title)

        // Parsing and proposal-card creation must not publish either operation as a formal event.
        assertNull(store.get(createDraft.eventSeq))
        val unchangedOriginal = requireNotNull(store.get(originalSeq))
        assertEquals(original.title, unchangedOriginal.title)
        assertEquals(original.revision, unchangedOriginal.revision)
        assertEquals(original.startMillis, unchangedOriginal.startMillis)
        assertEquals(listOf(createDraft.draftId, replaceDraft.draftId).toSet(),
            store.pendingDrafts().map { it.draftId }.toSet())
    }

    @Test
    fun parsedAiCreateProposalPersistsAsAnUnpublishedDraftAcrossRestart() {
        val raw = """
            {"events":[{"operation":"CREATE","kind":"ACTIVITY","title":"AI 待审草稿",
              "start":"2026-10-28 09:00","end":"2026-10-28 10:00"}]}
        """.trimIndent()
        val interpretation = AiResponseParser.parse(raw, emptyList(), zone)
        assertTrue(interpretation.warnings.toString(), interpretation.warnings.isEmpty())

        val created = AiDraftService(store).createDrafts(interpretation)
        assertTrue(created.warnings.toString(), created.warnings.isEmpty())
        val draft = requireNotNull(store.draft(created.draftIds.single()))
        assertEquals(DraftOperation.CREATE, draft.operation)
        assertEquals(DraftStatus.PENDING, draft.status)
        assertEquals("AI 待审草稿", draft.form.title)
        assertNull(store.get(draft.eventSeq))
        assertTrue(store.history(draft.eventSeq).isEmpty())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val restored = requireNotNull(store.draft(draft.draftId))
        assertEquals(draft.eventSeq, restored.eventSeq)
        assertEquals(draft.draftVersion, restored.draftVersion)
        assertEquals(draft.operation, restored.operation)
        assertEquals(draft.form, restored.form)
        assertEquals(DraftStatus.PENDING, restored.status)
        assertEquals(listOf(draft.draftId), store.pendingDrafts().map { it.draftId })
        assertNull(store.get(draft.eventSeq))
        assertTrue(store.history(draft.eventSeq).isEmpty())
    }

    @Test
    fun nextDraftQueueOrderAdvancesPastCommittedAndDiscardedDraftsAfterRestart() {
        val committed = store.newDraft(activityForm("committed"))
        assertTrue(committed.queueOrder > 0)
        assertTrue(commit(committed) is PowerStore.CommitOutcome.Committed)

        val discarded = store.newDraft(activityForm("discarded"))
        assertTrue(discarded.queueOrder > committed.queueOrder)
        assertTrue(store.discard(discarded.draftId))

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        assertEquals(DraftStatus.DISCARDED, store.draft(discarded.draftId)!!.status)

        val next = store.newDraft(activityForm("next"))
        assertTrue(next.queueOrder > discarded.queueOrder)
        assertTrue(next.queueOrder > committed.queueOrder)
        assertEquals(listOf(next.draftId), store.pendingDrafts().map { it.draftId })
    }

    @Test
    fun humanProposalAppendsAfterExistingQueueAcrossRestart() {
        val first = store.newDraft(activityForm("first"), actor = "user")
        val second = store.newDraft(activityForm("second"), actor = "user")
        val humanProposal = store.newDraft(activityForm("human proposal"), actor = "user")

        assertTrue(second.queueOrder > first.queueOrder)
        assertTrue(humanProposal.queueOrder > second.queueOrder)
        assertEquals(listOf(first.draftId, second.draftId, humanProposal.draftId),
            store.pendingDrafts().map { it.draftId })

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopened = store.pendingDrafts()
        assertEquals(listOf(first.draftId, second.draftId, humanProposal.draftId),
            reopened.map { it.draftId })
        assertEquals(listOf(first.queueOrder, second.queueOrder, humanProposal.queueOrder),
            reopened.map { it.queueOrder })
        assertEquals(listOf("first", "second", "human proposal"),
            reopened.map { it.form.title })
    }

    @Test
    fun updatingOlderDraftTimestampDoesNotChangeQueueOrderAcrossRestart() {
        fun waitPast(timestamp: Long) {
            val deadlineNanos = System.nanoTime() + 2_000_000_000L
            while (System.currentTimeMillis() <= timestamp && System.nanoTime() < deadlineNanos) {
                Thread.sleep(1)
            }
            assertTrue("system clock did not advance past $timestamp",
                System.currentTimeMillis() > timestamp)
        }

        val older = store.newDraft(activityForm("older queue item"))
        waitPast(older.updatedAtMillis)
        val newer = store.newDraft(activityForm("newer queue item"))
        assertTrue(newer.updatedAtMillis > older.updatedAtMillis)

        waitPast(newer.updatedAtMillis)
        assertTrue(store.stageDraft(
            older.draftId,
            older.form.copy(title = "edited older queue item"),
            older.draftVersion
        ) is PowerStore.StageResult.Saved)
        val updatedOlder = requireNotNull(store.draft(older.draftId))

        assertTrue(updatedOlder.updatedAtMillis > newer.updatedAtMillis)
        assertEquals(older.queueOrder, updatedOlder.queueOrder)
        assertEquals(listOf(older.draftId, newer.draftId),
            store.pendingDrafts().map { it.draftId })

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val reopened = store.pendingDrafts()
        assertEquals(listOf(older.draftId, newer.draftId), reopened.map { it.draftId })
        assertEquals(listOf(older.queueOrder, newer.queueOrder), reopened.map { it.queueOrder })
        assertEquals(listOf(updatedOlder.updatedAtMillis, newer.updatedAtMillis),
            reopened.map { it.updatedAtMillis })
        assertTrue(reopened.first().updatedAtMillis > reopened.last().updatedAtMillis)
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
    fun wholeSnapshotReplacementPersistsAcrossRestartWithoutFieldMerge() {
        val originalCard = store.newDraft(
            activityForm("Original").copy(location = "Old room", notes = "Old notes")
        )
        assertTrue(commit(originalCard) is PowerStore.CommitOutcome.Committed)
        val original = requireNotNull(store.get(originalCard.eventSeq))
        val proposalForm = DraftForm.from(original).copy(
            title = "Replacement",
            location = "",
            notes = "",
            enabled = false
        )
        val replacement = store.newDraft(
            proposalForm,
            original.eventSeq,
            DraftOperation.REPLACE
        )

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val committed = store.commit(replacement.draftId)
        assertTrue(committed is PowerStore.CommitOutcome.Committed)
        val result = requireNotNull(store.get(original.eventSeq))
        assertEquals(original.eventSeq, result.eventSeq)
        assertEquals(original.createdAtMillis, result.createdAtMillis)
        assertEquals(2L, result.revision)
        assertEquals("Replacement", result.title)
        assertEquals("", result.location)
        assertEquals("", result.notes)
        assertFalse(result.enabled)
        assertEquals(original.startMillis, result.startMillis)
        assertEquals(original.endMillis, result.endMillis)
    }

    @Test
    fun stagedMutationAdvancesFormalRevisionOnlyWhenCommittedAcrossRestart() {
        val original = store.newDraft(activityForm("revision one"))
        assertEquals(1L, (commit(original) as PowerStore.CommitOutcome.Committed).revision)
        val eventSeq = original.eventSeq
        val formal = requireNotNull(store.get(eventSeq))

        val proposal = store.newDraft(
            DraftForm.from(formal).copy(title = "proposal before staging"),
            eventSeq,
            DraftOperation.REPLACE
        )
        assertEquals(1L, store.get(eventSeq)!!.revision)
        assertEquals("revision one", store.get(eventSeq)!!.title)

        assertTrue(store.stageDraft(
            proposal.draftId,
            proposal.form.copy(title = "staged proposal"),
            proposal.draftVersion
        ) is PowerStore.StageResult.Saved)
        assertEquals(1L, store.get(eventSeq)!!.revision)
        assertEquals("revision one", store.get(eventSeq)!!.title)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val formalAfterRestart = requireNotNull(store.get(eventSeq))
        val stagedAfterRestart = requireNotNull(store.draft(proposal.draftId))
        assertEquals(1L, formalAfterRestart.revision)
        assertEquals("revision one", formalAfterRestart.title)
        assertEquals(1L, stagedAfterRestart.baseRevision)
        assertEquals("staged proposal", stagedAfterRestart.form.title)

        val committed = store.commit(stagedAfterRestart.draftId)
            as PowerStore.CommitOutcome.Committed
        assertEquals(2L, committed.revision)
        assertEquals(2L, store.get(eventSeq)!!.revision)
        assertEquals("staged proposal", store.get(eventSeq)!!.title)
    }

    @Test
    fun multipleCommitsKeepOneCurrentFormalSnapshotAcrossRestart() {
        val created = store.newDraft(activityForm("revision one"))
        assertTrue(commit(created) is PowerStore.CommitOutcome.Committed)
        val seq = created.eventSeq

        val second = store.newDraft(
            DraftForm.from(store.get(seq)!!).copy(title = "revision two"),
            seq,
            DraftOperation.REPLACE
        )
        assertTrue(commit(second) is PowerStore.CommitOutcome.Committed)
        val third = store.newDraft(
            DraftForm.from(store.get(seq)!!).copy(title = "revision three"),
            seq,
            DraftOperation.REPLACE
        )
        assertTrue(commit(third) is PowerStore.CommitOutcome.Committed)

        val currentBeforeRestart = store.all().filter { it.eventSeq == seq }
        assertEquals(1, currentBeforeRestart.size)
        assertEquals("revision three", currentBeforeRestart.single().title)
        assertEquals(3L, currentBeforeRestart.single().revision)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val currentAfterRestart = store.all().filter { it.eventSeq == seq }
        assertEquals(1, currentAfterRestart.size)
        assertEquals("revision three", currentAfterRestart.single().title)
        assertEquals(3L, currentAfterRestart.single().revision)
        assertEquals(3, store.history(seq).size)
    }

    @Test
    fun staleCardCannotSilentlyOverwriteAndRequiresExplicitApproval() {
        val card = store.newDraft(activityForm("Base").copy(trackingEnabled = true))
        commit(card)
        val seq = card.eventSeq
        val v1 = store.get(seq)!!
        val stale = store.newDraft(DraftForm.from(v1).copy(title = "Edited"), seq,
            DraftOperation.REPLACE)
        assertTrue(store.setCompleted(seq, "single", true, expectedRevision = v1.revision))
        assertTrue(store.commit(stale.draftId) is PowerStore.CommitOutcome.Conflict)
        assertEquals(DraftStatus.CONFLICT, store.draft(stale.draftId)!!.status)
        assertEquals("Base", store.get(seq)!!.title)
        assertTrue(store.commit(stale.draftId, approveStale = true, actor = "test")
            is PowerStore.CommitOutcome.Committed)
        assertEquals("Edited", store.get(seq)!!.title)
        assertEquals(3L, store.get(seq)!!.revision)
        assertEquals(0, store.pendingDraftsFor(seq).size)
    }

    @Test
    fun staleBaseRevisionReturnsConflictWithoutOverwritingAcrossRestart() {
        val card = store.newDraft(activityForm("Base").copy(trackingEnabled = true))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val eventSeq = card.eventSeq
        val base = requireNotNull(store.get(eventSeq))
        val stale = store.newDraft(
            DraftForm.from(base).copy(title = "stale replacement"),
            eventSeq,
            DraftOperation.REPLACE
        )

        assertTrue(store.setCompleted(eventSeq, "single", true, expectedRevision = base.revision))
        assertEquals(2L, store.get(eventSeq)!!.revision)
        val historyBeforeRestart = store.history(eventSeq)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val outcome = store.commit(stale.draftId)
        assertTrue(outcome is PowerStore.CommitOutcome.Conflict)
        assertEquals(DraftStatus.CONFLICT, store.draft(stale.draftId)!!.status)
        val formalAfterConflict = requireNotNull(store.get(eventSeq))
        assertEquals(2L, formalAfterConflict.revision)
        assertEquals("Base", formalAfterConflict.title)
        assertTrue(formalAfterConflict.completedKeys.contains("single"))
        assertEquals(historyBeforeRestart, store.history(eventSeq))
    }

    @Test
    fun staleDraftRequiresExplicitReapprovalAfterRestart() {
        val card = store.newDraft(activityForm("Base").copy(trackingEnabled = true))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val eventSeq = card.eventSeq
        val base = requireNotNull(store.get(eventSeq))
        val stale = store.newDraft(
            DraftForm.from(base).copy(title = "Reapproved replacement"),
            eventSeq,
            DraftOperation.REPLACE
        )
        assertTrue(store.setCompleted(eventSeq, "single", true,
            expectedRevision = base.revision))

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        assertTrue(store.commit(stale.draftId) is PowerStore.CommitOutcome.Conflict)
        val beforeApproval = requireNotNull(store.get(eventSeq))
        assertEquals("Base", beforeApproval.title)
        assertEquals(2L, beforeApproval.revision)
        assertTrue(beforeApproval.completedKeys.contains("single"))
        assertEquals(DraftStatus.CONFLICT, store.draft(stale.draftId)!!.status)

        val approved = store.commit(stale.draftId, approveStale = true, actor = "human-review")
        assertTrue(approved is PowerStore.CommitOutcome.Committed)
        val afterApproval = requireNotNull(store.get(eventSeq))
        assertEquals("Reapproved replacement", afterApproval.title)
        assertEquals(3L, afterApproval.revision)
        assertFalse(afterApproval.completedKeys.contains("single"))
        assertEquals(DraftStatus.COMMITTED, store.draft(stale.draftId)!!.status)
        assertTrue(store.pendingDraftsFor(eventSeq).isEmpty())
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
    fun explicitCommitIdRetryAfterRestartDoesNotRepeatSideEffects() {
        val card = store.newDraft(alarmForm("idempotent retry"))
        val idempotencyKey = "freeze-commit-id-retry"
        val first = store.commit(card.draftId, idempotencyKey = idempotencyKey)
            as PowerStore.CommitOutcome.Committed
        assertFalse(first.duplicate)
        assertEquals(idempotencyKey, first.commitId)

        val historyBeforeRestart = store.history(card.eventSeq)
        val intentsBeforeRestart = store.dueIntents()
        assertEquals(1, historyBeforeRestart.size)
        assertEquals(1, store.stats(sinceMillis = 0, actor = "user").published)
        assertEquals(1, intentsBeforeRestart.size)
        assertEquals(0, store.pendingCount())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val retry = store.commit(card.draftId, idempotencyKey = idempotencyKey)
            as PowerStore.CommitOutcome.Committed
        assertTrue(retry.duplicate)
        assertEquals(first.eventSeq, retry.eventSeq)
        assertEquals(first.revision, retry.revision)
        assertEquals(first.commitId, retry.commitId)
        assertEquals(historyBeforeRestart, store.history(card.eventSeq))
        assertEquals(intentsBeforeRestart, store.dueIntents())
        assertEquals(1, store.stats(sinceMillis = 0, actor = "user").published)
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun separateCommitOperationsReceiveUniqueCommitIdsAcrossRestart() {
        val initial = store.newDraft(activityForm("initial"))
        val first = commit(initial) as PowerStore.CommitOutcome.Committed
        assertTrue(first.commitId.isNotBlank())

        val eventSeq = initial.eventSeq
        val replacement = store.newDraft(
            DraftForm.from(store.get(eventSeq)!!).copy(title = "replacement"),
            eventSeq,
            DraftOperation.REPLACE
        )
        val second = commit(replacement) as PowerStore.CommitOutcome.Committed
        assertTrue(second.commitId.isNotBlank())
        assertTrue(first.commitId != second.commitId)

        val historyBeforeRestart = store.history(eventSeq).map { it.commitId }
        assertEquals(2, historyBeforeRestart.size)
        assertEquals(2, historyBeforeRestart.toSet().size)
        assertTrue(historyBeforeRestart.contains(first.commitId))
        assertTrue(historyBeforeRestart.contains(second.commitId))

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        val historyAfterRestart = store.history(eventSeq).map { it.commitId }
        assertEquals(historyBeforeRestart.toSet(), historyAfterRestart.toSet())
        assertEquals(2, historyAfterRestart.toSet().size)
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
    fun deleteRetiresTheNumberAndClearsTheOnlyDraft() {
        val card = store.newDraft(activityForm("????"))
        commit(card)
        val seq = card.eventSeq
        val lateEdit = store.newDraft(DraftForm.from(store.get(seq)!!), seq,
            DraftOperation.REPLACE)
        val deleteDraft = store.newDraft(DraftForm.from(store.get(seq)!!), seq,
            DraftOperation.DELETE)
        assertEquals(lateEdit.draftId, deleteDraft.draftId)
        assertTrue(commit(deleteDraft) is PowerStore.CommitOutcome.Committed)
        assertNull(store.get(seq))
        assertTrue(store.isRetired(seq))
        assertEquals(0, store.pendingDraftsFor(seq).size)
        assertEquals(DraftStatus.COMMITTED, store.draft(lateEdit.draftId)!!.status)
        val createAgain = store.newDraft(activityForm("???"))
        assertTrue(createAgain.eventSeq != seq)
        assertTrue(store.restoreDraft(lateEdit.draftId, DraftForm.from(
            store.getIncludingRetired(seq)!!)) is PowerStore.CommitOutcome.Invalid)
    }

    @Test
    fun deletedIdentityRemainsRetiredAndIsNotReusedAfterRestart() {
        val original = store.newDraft(activityForm("跨重启删除"))
        assertTrue(commit(original) is PowerStore.CommitOutcome.Committed)
        val retiredSeq = original.eventSeq
        val delete = store.newDraft(
            DraftForm.from(store.get(retiredSeq)!!), retiredSeq, DraftOperation.DELETE
        )
        assertTrue(commit(delete) is PowerStore.CommitOutcome.Committed)

        assertNull(store.get(retiredSeq))
        assertEquals(retiredSeq, store.getIncludingRetired(retiredSeq)!!.eventSeq)
        assertTrue(store.isRetired(retiredSeq))
        assertEquals(1L, store.identityCount())
        assertEquals(retiredSeq + 1, store.nextSequencePreview())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        assertNull(store.get(retiredSeq))
        assertEquals(retiredSeq, store.getIncludingRetired(retiredSeq)!!.eventSeq)
        assertTrue(store.isRetired(retiredSeq))
        assertEquals(1L, store.identityCount())
        assertEquals(retiredSeq + 1, store.nextSequencePreview())

        val next = store.newDraft(activityForm("删除后的新事件"))
        assertEquals(retiredSeq + 1, next.eventSeq)
        assertNotNull(store.getIncludingRetired(retiredSeq))
        assertTrue(store.isRetired(retiredSeq))
        assertNull(store.get(retiredSeq))
        assertEquals(2L, store.identityCount())
        assertEquals(retiredSeq + 2, store.nextSequencePreview())
    }

    @Test
    fun explicitRestoreProposalKeepsTombstoneUntilHumanConfirmationAndReusesIdentity() {
        val original = store.newDraft(alarmForm("恢复后仍是同一闹钟"))
        assertTrue(commit(original) is PowerStore.CommitOutcome.Committed)
        val eventSeq = original.eventSeq
        val originalRecord = store.get(eventSeq)!!
        val delete = store.newDraft(
            DraftForm.from(originalRecord), eventSeq, DraftOperation.DELETE
        )
        assertTrue(commit(delete) is PowerStore.CommitOutcome.Committed)

        val tombstone = store.getIncludingRetired(eventSeq)!!
        val nextSeqBeforeProposal = store.nextSequencePreview()
        assertTrue(store.isRetired(eventSeq))
        assertNull(store.get(eventSeq))

        val proposal = store.proposeRestoreDraft(eventSeq)
        assertEquals(eventSeq, proposal.eventSeq)
        assertEquals(DraftOperation.REPLACE, proposal.operation)
        assertEquals(tombstone.revision, proposal.baseRevision)
        assertEquals(DraftForm.from(tombstone), proposal.form)
        assertEquals(proposal.draftId, store.proposeRestoreDraft(eventSeq).draftId)
        assertEquals(nextSeqBeforeProposal, store.nextSequencePreview())
        assertTrue(store.isRetired(eventSeq))
        assertNull(store.get(eventSeq))

        assertTrue(commit(proposal) is PowerStore.CommitOutcome.NeedsRestore)
        assertTrue(store.isRetired(eventSeq))
        assertTrue(store.restoreDraft(
            proposal.draftId,
            proposal.form.copy(title = "用户确认恢复"),
            actor = "ai"
        ) is PowerStore.CommitOutcome.NotAuthorized)
        assertTrue(store.isRetired(eventSeq))

        val restored = store.restoreDraft(
            proposal.draftId,
            proposal.form.copy(title = "用户确认恢复")
        ) as PowerStore.CommitOutcome.Committed
        assertEquals(eventSeq, restored.eventSeq)
        assertEquals(tombstone.revision + 1, restored.revision)
        assertEquals(nextSeqBeforeProposal, store.nextSequencePreview())
        assertFalse(store.isRetired(eventSeq))
        assertEquals("用户确认恢复", store.get(eventSeq)!!.title)
        assertEquals(originalRecord.createdAtMillis, store.get(eventSeq)!!.createdAtMillis)
        val intent = store.intentFor(eventSeq)
        assertNotNull(intent)
        assertEquals(restored.revision, intent!!.revision)
        assertEquals(PowerStore.DesiredSchedule.PENDING, intent.desired)
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
            mapOf(card.draftId to card.draftVersion),
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
        val session = store.beginAiBatch(
            mapOf(inside.draftId to inside.draftVersion),
            setOf(DraftOperation.CREATE)
        )

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
            mapOf(first.draftId to first.draftVersion, second.draftId to second.draftVersion),
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
        val session = store.beginAiBatch(
            mapOf(card.draftId to card.draftVersion),
            setOf(DraftOperation.CREATE)
        )
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

    @Test
    fun cancelledAiBatchRunnerReleasesLockAndFencesRemainingScopeAcrossRestart() =
        kotlinx.coroutines.runBlocking {
            val first = store.newDraft(activityForm("已完成项"))
            val second = store.newDraft(activityForm("取消后保留项"))
            val session = store.beginAiBatch(
                linkedMapOf(
                    first.draftId to first.draftVersion,
                    second.draftId to second.draftVersion
                ),
                setOf(DraftOperation.CREATE)
            )

            val cancellation = runCatching {
                dev.powerfulcalendar.ai.AiBatchRunner(store).run(
                    session,
                    dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH
                ) { done, _ ->
                    if (done == 1) throw kotlinx.coroutines.CancellationException("test cancellation")
                }
            }.exceptionOrNull()

            assertTrue(cancellation is kotlinx.coroutines.CancellationException)
            assertFalse(store.isLocked())
            assertEquals(PowerStore.AiState.CANCELLED, store.currentAiSession()!!.state)
            assertEquals(1, store.all().size)
            assertEquals(1, store.pendingDrafts().size)

            val database = store.databaseName
            store.close()
            store = PowerStore(context, database)

            assertFalse(store.isLocked())
            assertEquals(PowerStore.AiState.CANCELLED, store.currentAiSession()!!.state)
            val remaining = store.pendingDrafts().single()
            assertEquals(second.draftId, remaining.draftId)
            assertTrue(
                store.commit(remaining.draftId, actor = "ai", aiSession = session)
                    is PowerStore.CommitOutcome.NotAuthorized
            )
            assertNull(store.get(remaining.eventSeq))
        }

    // ---------------------------------------------------------------- imports

    @Test
    fun structuredImportIsIdempotentAndDisablesImportedAlarms() {
        val batch = store.beginMigration("test")
        val record = TemporalRecord(
            eventSeq = 0, revision = 0, kind = TemporalKind.ALARM, title = "旧闹钟",
            startMillis = System.currentTimeMillis() + 3_600_000L, enabled = true,
            reminderMinutes = 0, reminderMethod = ReminderMethod.ALARM
        )
        val first = store.importStructured(record, "ext-1", batch, "test")
        assertTrue(first is PowerStore.ImportOutcome.Imported)
        val imported = first as PowerStore.ImportOutcome.Imported
        assertFalse("imported alarms must wait for handoff", imported.enabled)
        assertFalse(store.get(imported.eventSeq)!!.enabled)
        assertNull("disabled imported alarm must not create a desired schedule", store.intentFor(imported.eventSeq))

        val second = store.importStructured(record, "ext-1", batch, "test")
        assertTrue(second is PowerStore.ImportOutcome.AlreadyImported)
        assertEquals(1L, store.identityCount())
        assertEquals(1, store.history(imported.eventSeq).size)
        store.finishMigration(batch)
        val summary = store.migrationSummary(batch)!!
        assertEquals(1, summary.first)
        assertEquals(1, summary.second)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        assertFalse(requireNotNull(store.get(imported.eventSeq)).enabled)
        assertNull(store.intentFor(imported.eventSeq))
        assertEquals(1L, store.identityCount())
        assertEquals(1, store.history(imported.eventSeq).size)
    }

    @Test
    fun structuredImportSourceMetadataDoesNotBecomeEventIdentity() {
        val batch = store.beginMigration("calendar-import")
        val startMillis = (System.currentTimeMillis() / 60_000L) * 60_000L + 3_600_000L
        val record = TemporalRecord(
            eventSeq = 0L,
            revision = 0L,
            kind = TemporalKind.ACTIVITY,
            title = "导入活动",
            startMillis = startMillis,
            endMillis = startMillis + 45 * 60_000L,
            source = "payload-origin"
        )

        fun import(source: String, externalId: String): Long {
            val outcome = store.importStructured(
                record, externalId, batch, source = source, alarmNeedsHandoff = false
            )
            assertTrue(outcome is PowerStore.ImportOutcome.Imported)
            return (outcome as PowerStore.ImportOutcome.Imported).eventSeq
        }

        val fromCalendarA = import("calendar-a", "same-external-id")
        val fromCalendarB = import("calendar-b", "same-external-id")
        val secondFromCalendarA = import("calendar-a", "different-external-id")

        assertTrue(fromCalendarA != fromCalendarB)
        assertTrue(fromCalendarA != secondFromCalendarA)
        assertTrue(fromCalendarB != secondFromCalendarA)
        assertEquals("calendar-a", store.get(fromCalendarA)!!.source)
        assertEquals("calendar-b", store.get(fromCalendarB)!!.source)
        assertEquals("calendar-a", store.get(secondFromCalendarA)!!.source)
        assertEquals(3L, store.identityCount())
        assertEquals(4L, store.nextSequencePreview())

        val duplicate = store.importStructured(
            record, "same-external-id", batch, source = "calendar-a", alarmNeedsHandoff = false
        )
        assertTrue(duplicate is PowerStore.ImportOutcome.AlreadyImported)
        assertEquals(fromCalendarA, (duplicate as PowerStore.ImportOutcome.AlreadyImported).eventSeq)
        assertEquals(3L, store.identityCount())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        assertEquals("calendar-a", store.get(fromCalendarA)!!.source)
        assertEquals("calendar-b", store.get(fromCalendarB)!!.source)
        assertEquals("calendar-a", store.get(secondFromCalendarA)!!.source)
        assertEquals(3L, store.identityCount())
        assertEquals(4L, store.nextSequencePreview())
    }

    @Test
    fun rejectedStructuredImportDoesNotConsumeSequenceBeforeValidRetry() {
        val batch = store.beginMigration("calendar-import")
        val startMillis = (System.currentTimeMillis() / 60_000L) * 60_000L + 3_600_000L
        val incomplete = TemporalRecord(
            eventSeq = 0L,
            revision = 0L,
            kind = TemporalKind.ACTIVITY,
            title = "待补结束时刻",
            startMillis = startMillis
        )

        val rejected = store.importStructured(
            incomplete, "activity-1", batch, source = "calendar-a", alarmNeedsHandoff = false
        )
        assertTrue(rejected is PowerStore.ImportOutcome.Rejected)
        assertEquals(0L, store.identityCount())
        assertEquals(1L, store.nextSequencePreview())
        assertNull(store.get(1L))

        val valid = incomplete.copy(endMillis = startMillis + 45 * 60_000L)
        val retried = store.importStructured(
            valid, "activity-1", batch, source = "calendar-a", alarmNeedsHandoff = false
        )
        assertTrue(retried is PowerStore.ImportOutcome.Imported)
        val eventSeq = (retried as PowerStore.ImportOutcome.Imported).eventSeq
        assertEquals(1L, eventSeq)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(1, store.history(eventSeq).size)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(eventSeq)!!
        assertEquals(TemporalKind.ACTIVITY, reopened.kind)
        assertEquals(startMillis, reopened.startMillis)
        assertEquals(valid.endMillis, reopened.endMillis)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(1, store.history(eventSeq).size)
    }

    @Test
    fun schedulingIntentFollowsTheCommittedRevision() {
        val card = store.newDraft(alarmForm(minutesAhead = 90))
        commit(card)
        val intent = store.intentFor(card.eventSeq)
        assertNotNull(intent)
        assertEquals(card.eventSeq, intent!!.eventSeq)
        assertEquals(PowerStore.DesiredSchedule.PENDING, intent.desired)
        assertEquals(PowerStore.TriggerRequirement.INEXACT, intent.requirement)

        val deleteForm = DraftForm.from(store.get(card.eventSeq)!!)
        val delete = store.newDraft(deleteForm, card.eventSeq, DraftOperation.DELETE)
        commit(delete)
        assertEquals(PowerStore.DesiredSchedule.CANCELLED, store.intentFor(card.eventSeq)!!.desired)
    }

    @Test
    fun commitRollsBackFormalEventHistoryAndScheduleWhenDesiredIntentWriteFailsThenRecovers() {
        val card = store.newDraft(alarmForm("事务原子性", minutesAhead = 90))
        store.writableDatabase.execSQL(
            """CREATE TRIGGER fail_desired_schedule_insert
               BEFORE INSERT ON schedule_intents
               BEGIN SELECT RAISE(ABORT,'injected desired schedule insert failure'); END"""
        )

        val failure = runCatching { commit(card) }.exceptionOrNull()
        assertNotNull("the injected schedule write failure must abort commit", failure)
        assertTrue(failure.toString(), failure.toString().contains("injected desired schedule insert failure"))
        assertNull(store.get(card.eventSeq))
        assertTrue(store.all().isEmpty())
        assertTrue(store.history(card.eventSeq).isEmpty())
        assertNull(store.intentFor(card.eventSeq))
        assertEquals(1, store.pendingDrafts().size)
        assertEquals(DraftStatus.PENDING, store.pendingDrafts().single().status)

        store.writableDatabase.execSQL("DROP TRIGGER fail_desired_schedule_insert")
        val committed = commit(card) as PowerStore.CommitOutcome.Committed
        val formal = requireNotNull(store.get(card.eventSeq))
        val intent = requireNotNull(store.intentFor(card.eventSeq))
        assertEquals(1L, formal.revision)
        assertEquals(formal.revision, intent.revision)
        assertEquals(PowerStore.DesiredSchedule.PENDING, intent.desired)
        assertEquals(listOf(committed.commitId), store.history(card.eventSeq).map { it.commitId })
        assertTrue(store.pendingDrafts().isEmpty())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        assertEquals(formal, store.get(card.eventSeq))
        assertEquals(intent, store.intentFor(card.eventSeq))
        assertEquals(listOf(committed.commitId), store.history(card.eventSeq).map { it.commitId })
    }

    @Test
    fun blockedScheduleStateDoesNotCompleteOrDeleteItsFormalEventAcrossRestart() {
        val card = store.newDraft(alarmForm("权限受阻的闹钟").copy(trackingEnabled = true))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val formal = requireNotNull(store.get(card.eventSeq))
        val intent = requireNotNull(store.intentFor(card.eventSeq))
        assertTrue(formal.completedKeys.isEmpty())

        assertTrue(store.markIntent(
            card.eventSeq,
            intent.triggerId,
            PowerStore.DesiredSchedule.BLOCKED,
            "缺少精确闹钟权限",
            intent.revision,
            intent.occurrenceKey
        ))

        val blocked = requireNotNull(store.intentFor(card.eventSeq))
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, blocked.desired)
        assertEquals("缺少精确闹钟权限", blocked.androidHint)
        assertEquals(formal, store.get(card.eventSeq))
        assertEquals(listOf(card.eventSeq), store.all().map { it.eventSeq })

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)

        assertEquals(blocked, store.intentFor(card.eventSeq))
        assertEquals(formal, store.get(card.eventSeq))
        assertTrue(requireNotNull(store.get(card.eventSeq)).completedKeys.isEmpty())
        assertEquals(1, store.history(card.eventSeq).size)
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
    fun completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart() {
        val card = store.newDraft(
            DraftForm(
                kind = TemporalKind.DEADLINE,
                title = "提交材料",
                endText = "2026-10-24",
                trackingEnabled = true,
                reminderMinutes = -1
            )
        )
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)

        val before = requireNotNull(store.get(card.eventSeq))
        assertEquals(TemporalKind.DEADLINE, before.kind)
        assertEquals("2026-10-24", before.deadlineDate)
        assertNull(before.deadlineMillis)
        assertNull(before.startMillis)
        assertNull(before.endMillis)
        assertEquals(TimePrecision.DATE, before.precision)

        assertTrue(store.setCompleted(
            card.eventSeq,
            occurrenceKey = "single",
            completed = true,
            expectedRevision = before.revision
        ))

        val completed = requireNotNull(store.get(card.eventSeq))
        assertEquals(before.revision + 1, completed.revision)
        assertEquals(setOf("single"), completed.completedKeys)
        assertEquals("2026-10-24", completed.deadlineDate)
        assertNull(completed.deadlineMillis)
        assertNull(completed.startMillis)
        assertNull(completed.endMillis)
        assertEquals(TimePrecision.DATE, completed.precision)
        assertEquals(2, store.history(card.eventSeq).size)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = requireNotNull(store.get(card.eventSeq))
        assertEquals(completed, reopened)
        assertEquals(setOf("single"), reopened.completedKeys)
        assertEquals(TimePrecision.DATE, reopened.precision)
        assertNull(reopened.deadlineMillis)
        assertNull(reopened.startMillis)
        assertNull(reopened.endMillis)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
        assertEquals(2, store.history(card.eventSeq).size)
    }

    @Test
    fun icsActivityProjectionDoesNotCreateSecondFormalEventAcrossRestart() {
        val card = store.newDraft(activityForm("ICS 只读投影", 120))
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)

        val formal = requireNotNull(store.get(card.eventSeq))
        val formalRows = store.all()
        val history = store.history(card.eventSeq)
        val intent = store.intentFor(card.eventSeq)
        val exported = IcsCodec.exportActivities(formalRows)
        val parsed = IcsCodec.parse(exported)

        assertTrue(parsed.warnings.isEmpty())
        assertEquals(1, parsed.events.size)
        assertEquals(formal.title, parsed.events.single().title)
        assertEquals(formal.startMillis, parsed.events.single().startMillis)
        assertEquals(formal.endMillis, parsed.events.single().endMillis)
        assertEquals(formalRows, store.all())
        assertEquals(history, store.history(card.eventSeq))
        assertEquals(intent, store.intentFor(card.eventSeq))
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        assertEquals(listOf(formal), store.all())
        assertEquals(history, store.history(card.eventSeq))
        assertEquals(intent, store.intentFor(card.eventSeq))
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
    }

    @Test
    fun incompleteActivityCommitStaysUnpublishedAndCanBeRepairedAfterRestart() {
        val incompleteForm = DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "会议",
            startText = "2026-10-20 09:00",
            zoneId = zone.id
        )
        val card = store.newDraft(incompleteForm)

        assertTrue(commit(card) is PowerStore.CommitOutcome.Invalid)
        assertNull(store.get(card.eventSeq))
        val invalid = store.pendingDrafts().single()
        assertEquals(DraftStatus.INVALID, invalid.status)
        assertEquals(incompleteForm, invalid.form)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val recovered = store.pendingDrafts().single()
        assertEquals(card.draftId, recovered.draftId)
        assertEquals(DraftStatus.INVALID, recovered.status)
        assertNull(store.get(card.eventSeq))

        val repairedForm = incompleteForm.copy(endText = "2026-10-20 10:00")
        val staged = store.stageDraft(recovered.draftId, repairedForm, recovered.draftVersion)
        assertTrue(staged is PowerStore.StageResult.Saved)
        val repaired = store.draft(recovered.draftId)!!
        assertEquals(DraftStatus.PENDING, repaired.status)
        assertTrue(commit(repaired) is PowerStore.CommitOutcome.Committed)
        assertEquals(TemporalKind.ACTIVITY, store.get(card.eventSeq)!!.kind)
        assertTrue(store.pendingDrafts().isEmpty())
    }

    @Test
    fun activityCommitPreservesItsStartAndEndIntervalAcrossRestart() {
        val startMillis = (System.currentTimeMillis() / 60_000L) * 60_000L + 3_600_000L
        val endMillis = startMillis + 90 * 60_000L
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.ACTIVITY,
            title = "区间活动",
            startText = DraftForm.formatInstant(startMillis, zone),
            endText = DraftForm.formatInstant(endMillis, zone),
            zoneId = zone.id
        ))

        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val activity = store.get(card.eventSeq)!!
        assertEquals(TemporalKind.ACTIVITY, activity.kind)
        assertEquals(startMillis, activity.startMillis)
        assertEquals(endMillis, activity.endMillis)
        assertEquals(endMillis, activity.spanEndMillis())
        assertNull(activity.deadlineDate)
        assertNull(activity.deadlineMillis)

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(card.eventSeq)!!
        assertEquals(TemporalKind.ACTIVITY, reopened.kind)
        assertEquals(startMillis, reopened.startMillis)
        assertEquals(endMillis, reopened.endMillis)
        assertEquals(endMillis, reopened.spanEndMillis())
        assertNull(reopened.deadlineDate)
        assertNull(reopened.deadlineMillis)
    }

    @Test
    fun convertingAlarmToActivityKeepsEventIdentityAcrossRestart() {
        val startMillis = (System.currentTimeMillis() / 60_000L) * 60_000L + 3_600_000L
        val alarm = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "转换后活动",
            startText = DraftForm.formatInstant(startMillis, zone),
            reminderMinutes = 0,
            zoneId = zone.id
        ))
        assertTrue(commit(alarm) is PowerStore.CommitOutcome.Committed)
        val original = store.get(alarm.eventSeq)!!
        val conversion = TemporalConversion.convert(DraftForm.from(original), TemporalKind.ACTIVITY)
        assertEquals(listOf(DraftField.END), conversion.required)

        val endMillis = startMillis + 45 * 60_000L
        val convertedCard = store.newDraft(
            conversion.form.copy(endText = DraftForm.formatInstant(endMillis, zone)),
            original.eventSeq,
            DraftOperation.CONVERT
        )
        assertEquals(original.eventSeq, convertedCard.eventSeq)
        assertTrue(commit(convertedCard) is PowerStore.CommitOutcome.Committed)

        val converted = store.get(original.eventSeq)!!
        assertEquals(TemporalKind.ACTIVITY, converted.kind)
        assertEquals(original.eventSeq, converted.eventSeq)
        assertEquals(2L, converted.revision)
        assertEquals(startMillis, converted.startMillis)
        assertEquals(endMillis, converted.endMillis)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(original.eventSeq)!!
        assertEquals(TemporalKind.ACTIVITY, reopened.kind)
        assertEquals(original.eventSeq, reopened.eventSeq)
        assertEquals(2L, reopened.revision)
        assertEquals(startMillis, reopened.startMillis)
        assertEquals(endMillis, reopened.endMillis)
        assertEquals(1L, store.identityCount())
        assertEquals(2L, store.nextSequencePreview())
    }

    @Test
    fun oneDateMemoRemainsOneLocalDateAcrossRestart() {
        val date = "2026-10-24"
        val card = store.newDraft(DraftForm(
            kind = TemporalKind.MEMO,
            title = "单日待办",
            startText = date,
            zoneId = zone.id
        ))

        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val memo = store.get(card.eventSeq)!!
        assertEquals(TemporalKind.MEMO, memo.kind)
        assertEquals(date, memo.memoStartDate)
        assertNull(memo.memoEndDate)
        assertNull(memo.startMillis)
        assertNull(memo.endMillis)
        assertNull(memo.deadlineDate)
        assertNull(memo.deadlineMillis)
        assertEquals("$date · 待办", TemporalText.summarize(memo))

        val database = store.databaseName
        store.close()
        store = PowerStore(context, database)
        val reopened = store.get(card.eventSeq)!!
        assertEquals(TemporalKind.MEMO, reopened.kind)
        assertEquals(date, reopened.memoStartDate)
        assertNull(reopened.memoEndDate)
        assertNull(reopened.startMillis)
        assertNull(reopened.endMillis)
        assertEquals("$date · 待办", TemporalText.summarize(reopened))
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
        val session = store.beginAiBatch(
            mapOf(pending.draftId to pending.draftVersion),
            setOf(DraftOperation.CREATE)
        )

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
        val session = store.beginAiBatch(
            mapOf(card.draftId to card.draftVersion),
            setOf(DraftOperation.CREATE)
        )
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
    fun timezoneChangeRebuildsDailyAlarmIntentInCurrentLocalZone() {
        val previousDefault = TimeZone.getDefault()
        val newYork = ZoneId.of("America/New_York")
        val tokyo = ZoneId.of("Asia/Tokyo")
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(newYork))
            val anchorDate = LocalDate.now(newYork).minusDays(3)
            val card = store.newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "每日闹钟",
                    startText = DraftForm.formatInstant(
                        anchorDate.atTime(7, 30).atZone(newYork).toInstant().toEpochMilli(),
                        newYork
                    ),
                    reminderMinutes = 0,
                    reminderMethod = ReminderMethod.ALARM,
                    zoneId = newYork.id,
                    recurrence = Recurrence(freq = RecurrenceFreq.DAILY)
                )
            )
            assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
            val originalIntent = store.intentFor(card.eventSeq)!!
            val revision = store.get(card.eventSeq)!!.revision

            TimeZone.setDefault(TimeZone.getTimeZone(tokyo))
            val now = LocalDate.now(tokyo).plusDays(1).atStartOfDay(tokyo).toInstant().toEpochMilli()
            val record = store.get(card.eventSeq)!!
            val expected = RecurrenceEngine.nextOccurrence(record, now, tokyo)!!
            assertEquals(1, store.rebuildLocalDailyAlarmSchedules(tokyo, now))

            val refreshed = store.intentFor(card.eventSeq)!!
            assertEquals(expected.key, refreshed.occurrenceKey)
            assertEquals(expected.startMillis, refreshed.instantMillis)
            assertTrue(originalIntent.instantMillis != refreshed.instantMillis)
            assertEquals(revision, store.get(card.eventSeq)!!.revision)
            assertEquals(1, store.all().size)
        } finally {
            TimeZone.setDefault(previousDefault)
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
    fun aDateOnlyDeadlineCanScheduleRelativeReminderWithoutChangingDatePrecision() {
        val card = store.newDraft(
            DraftForm(
                TemporalKind.DEADLINE, "交材料",
                endText = "2026-12-24",
                reminderMinutes = 30,
                zoneId = zone.id
            )
        )
        assertTrue(commit(card) is PowerStore.CommitOutcome.Committed)
        val saved = store.get(card.eventSeq)!!
        assertEquals("2026-12-24", saved.deadlineDate)
        assertNull(saved.deadlineMillis)
        val expected = java.time.LocalDate.of(2026, 12, 24)
            .atStartOfDay(zone).toInstant().toEpochMilli() - 30 * 60_000L
        assertEquals(expected, store.intentFor(card.eventSeq)?.instantMillis)
    }

    @Test
    fun aStaleSameProcessBatchIsReleasedByTheLease() {
        val card = store.newDraft(activityForm("僵死批次"))
        val session = store.beginAiBatch(
            mapOf(card.draftId to card.draftVersion),
            setOf(DraftOperation.CREATE)
        )
        // A live batch with a fresh heartbeat is not an orphan.
        assertFalse(store.recoverInterruptedAi())
        // Simulate a cancelled runner: same owner, heartbeat long past the lease.
        store.writableDatabase.execSQL(
            "UPDATE ai_sessions SET updated_ms=? WHERE session_id=?",
            arrayOf<Any?>(System.currentTimeMillis() - 2 * PowerStore.DEFAULT_STALE_MS, session.sessionId)
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
            mapOf(first.draftId to first.draftVersion, second.draftId to second.draftVersion),
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
    fun theAiBatchDiscardOnlyRejectsProposalAndPreservesFormalEventAcrossRestart() =
        kotlinx.coroutines.runBlocking {
            val originalDraft = store.newDraft(activityForm("正式事件原文"))
            assertTrue(commit(originalDraft) is PowerStore.CommitOutcome.Committed)
            val original = requireNotNull(store.get(originalDraft.eventSeq))
            val originalHistory = store.history(originalDraft.eventSeq)
            val proposal = store.newDraft(
                DraftForm.from(original).copy(title = "AI 修改提案"),
                original.eventSeq,
                DraftOperation.REPLACE,
                actor = "ai"
            )
            val session = store.beginAiBatch(
                mapOf(proposal.draftId to proposal.draftVersion),
                setOf(DraftOperation.REPLACE)
            )

            val report = dev.powerfulcalendar.ai.AiBatchRunner(store)
                .run(session, dev.powerfulcalendar.ai.AiBatchRunner.Mode.DISCARD)

            assertEquals(1, report.discarded)
            assertEquals(0, report.published)
            assertEquals(0, report.kept)
            assertFalse(report.cancelled)
            assertEquals(DraftStatus.DISCARDED, store.draft(proposal.draftId)!!.status)
            assertEquals(original, store.get(original.eventSeq))
            assertEquals(originalHistory, store.history(original.eventSeq))
            assertEquals(PowerStore.AiState.COMPLETED, store.currentAiSession()!!.state)
            assertFalse(store.isLocked())

            val database = store.databaseName
            store.close()
            store = PowerStore(context, database)

            assertEquals(original, store.get(original.eventSeq))
            assertEquals(originalHistory, store.history(original.eventSeq))
            assertEquals(DraftStatus.DISCARDED, store.draft(proposal.draftId)!!.status)
            assertEquals(PowerStore.AiState.COMPLETED, store.currentAiSession()!!.state)
            assertFalse(store.isLocked())
        }

    @Test
    fun theAiRunnerContinuesIndependentItemsAfterOneCommitFailureAndPersistsPartialReport() =
        kotlinx.coroutines.runBlocking {
            val first = store.newDraft(alarmForm("独立项 A", minutesAhead = 200))
            val failed = store.newDraft(alarmForm("独立项 B 失败", minutesAhead = 260))
            val last = store.newDraft(alarmForm("独立项 C", minutesAhead = 320))
            val trigger = "fail_one_ai_batch_schedule"
            store.writableDatabase.execSQL(
                """CREATE TRIGGER $trigger
                   BEFORE INSERT ON schedule_intents
                   WHEN NEW.event_seq=${failed.eventSeq}
                   BEGIN SELECT RAISE(ABORT,'injected independent batch item failure'); END"""
            )
            try {
                val session = store.beginAiBatch(
                    linkedMapOf(
                        first.draftId to first.draftVersion,
                        failed.draftId to failed.draftVersion,
                        last.draftId to last.draftVersion
                    ),
                    setOf(DraftOperation.CREATE)
                )

                val report = dev.powerfulcalendar.ai.AiBatchRunner(store)
                    .run(session, dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH)

                assertEquals(2, report.published)
                assertEquals(1, report.kept)
                assertFalse(report.cancelled)
                assertEquals(1, report.failures.size)
                assertTrue(report.failures.single().contains("injected independent batch item failure"))
                assertNotNull(store.get(first.eventSeq))
                assertNull(store.get(failed.eventSeq))
                assertNotNull(store.get(last.eventSeq))
                assertNull(store.intentFor(failed.eventSeq))
                assertEquals(listOf(failed.draftId), store.pendingDrafts().map { it.draftId })
                assertEquals(PowerStore.AiState.FAILED, store.currentAiSession()!!.state)
                assertFalse(store.isLocked())

                val database = store.databaseName
                store.close()
                store = PowerStore(context, database)

                val persistedSession = requireNotNull(store.currentAiSession())
                assertEquals(PowerStore.AiState.FAILED, persistedSession.state)
                val persistedReport = JSONObject(requireNotNull(persistedSession.report))
                assertEquals(2, persistedReport.getInt("published"))
                assertEquals(1, persistedReport.getInt("kept"))
                assertFalse(persistedReport.getBoolean("cancelled"))
                assertTrue(persistedReport.getString("failures").contains("injected independent batch item failure"))
                assertEquals(setOf(first.eventSeq, last.eventSeq), store.all().map { it.eventSeq }.toSet())
                assertEquals(listOf(failed.draftId), store.pendingDrafts().map { it.draftId })
                assertFalse(store.isLocked())
                assertTrue(
                    store.commit(failed.draftId, actor = "ai", aiSession = session)
                        is PowerStore.CommitOutcome.NotAuthorized
                )

                store.writableDatabase.execSQL("DROP TRIGGER $trigger")
                val retry = store.beginAiBatch(
                    mapOf(failed.draftId to store.draft(failed.draftId)!!.draftVersion),
                    setOf(DraftOperation.CREATE)
                )
                val retryReport = dev.powerfulcalendar.ai.AiBatchRunner(store)
                    .run(retry, dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH)
                assertEquals(1, retryReport.published)
                assertEquals(0, retryReport.kept)
                assertTrue(retryReport.failures.isEmpty())
                assertEquals(PowerStore.AiState.COMPLETED, store.currentAiSession()!!.state)
                assertTrue(store.pendingDrafts().isEmpty())
                assertFalse(store.isLocked())
            } finally {
                runCatching { store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS $trigger") }
            }
        }

    @Test
    fun theAiRunnerStopsAfterUserCancelAndPersistsPartialOutcomeAcrossRestart() =
        kotlinx.coroutines.runBlocking {
            val first = store.newDraft(activityForm("取消前已提交"))
            val second = store.newDraft(activityForm("取消后保留"))
            val session = store.beginAiBatch(
                linkedMapOf(
                    first.draftId to first.draftVersion,
                    second.draftId to second.draftVersion
                ),
                setOf(DraftOperation.CREATE)
            )

            val report = dev.powerfulcalendar.ai.AiBatchRunner(store)
                .run(session, dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH) { done, _ ->
                    if (done == 1) assertTrue(store.requestAiCancel(session.sessionId))
                }

            assertEquals(1, report.published)
            assertEquals(0, report.kept)
            assertTrue(report.cancelled)
            assertFalse(store.isLocked())
            assertEquals(PowerStore.AiState.CANCELLED, store.currentAiSession()!!.state)
            assertEquals(1, store.all().size)
            assertEquals(1, store.pendingDrafts().size)

            val persistedReport = org.json.JSONObject(requireNotNull(store.currentAiSession()?.report))
            assertEquals(1, persistedReport.getInt("published"))
            assertEquals(0, persistedReport.getInt("kept"))
            assertTrue(persistedReport.getBoolean("cancelled"))
            assertTrue(
                store.commit(second.draftId, actor = "ai", aiSession = session)
                    is PowerStore.CommitOutcome.NotAuthorized
            )

            val database = store.databaseName
            store.close()
            store = PowerStore(context, database)

            assertFalse(store.isLocked())
            val reopenedSession = requireNotNull(store.currentAiSession())
            assertEquals(PowerStore.AiState.CANCELLED, reopenedSession.state)
            val reopenedReport = org.json.JSONObject(requireNotNull(reopenedSession.report))
            assertEquals(1, reopenedReport.getInt("published"))
            assertEquals(0, reopenedReport.getInt("kept"))
            assertTrue(reopenedReport.getBoolean("cancelled"))
            assertEquals(first.eventSeq, store.all().single().eventSeq)
            assertEquals(second.draftId, store.pendingDrafts().single().draftId)
            assertTrue(
                store.commit(second.draftId, actor = "ai", aiSession = session)
                    is PowerStore.CommitOutcome.NotAuthorized
            )
            assertNull(store.get(second.eventSeq))
        }

    @Test
    fun theAiRunnerKeepsAConflictedCardForHumanReview() = kotlinx.coroutines.runBlocking {
        val base = store.newDraft(activityForm("Base").copy(trackingEnabled = true))
        commit(base)
        val seq = base.eventSeq
        val current = store.get(seq)!!
        val stale = store.newDraft(DraftForm.from(current).copy(title = "AI ???"),
            seq, DraftOperation.REPLACE)
        assertTrue(store.setCompleted(seq, "single", true,
            expectedRevision = current.revision))
        val session = store.beginAiBatch(mapOf(stale.draftId to stale.draftVersion),
            DraftOperation.entries.toSet())
        val report = dev.powerfulcalendar.ai.AiBatchRunner(store)
            .run(session, dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH)
        assertEquals(0, report.published)
        assertEquals(1, report.kept)
        assertEquals("Base", store.get(seq)!!.title)
        assertEquals(DraftStatus.CONFLICT, store.draft(stale.draftId)!!.status)
        assertFalse(store.isLocked())
    }

    @Test
    fun aiConflictDraftRemainsAvailableForHumanResolutionAcrossRestart() =
        kotlinx.coroutines.runBlocking {
            val base = store.newDraft(activityForm("Base").copy(trackingEnabled = true))
            assertTrue(commit(base) is PowerStore.CommitOutcome.Committed)
            val seq = base.eventSeq
            val current = requireNotNull(store.get(seq))
            val proposal = DraftForm.from(current).copy(title = "AI proposal")
            val stale = store.newDraft(proposal, seq, DraftOperation.REPLACE)
            assertTrue(store.setCompleted(seq, "single", true,
                expectedRevision = current.revision))

            val session = store.beginAiBatch(
                mapOf(stale.draftId to stale.draftVersion),
                DraftOperation.entries.toSet()
            )
            val report = dev.powerfulcalendar.ai.AiBatchRunner(store)
                .run(session, dev.powerfulcalendar.ai.AiBatchRunner.Mode.PUBLISH)
            assertEquals(0, report.published)
            assertEquals(1, report.kept)
            assertFalse(store.isLocked())
            assertEquals(DraftStatus.CONFLICT, store.draft(stale.draftId)!!.status)
            val queueOrder = store.draft(stale.draftId)!!.queueOrder

            val database = store.databaseName
            store.close()
            store = PowerStore(context, database)

            val retained = store.draftsByStatus(DraftStatus.CONFLICT).single()
            assertEquals(stale.draftId, retained.draftId)
            assertEquals(seq, retained.eventSeq)
            assertEquals(queueOrder, retained.queueOrder)
            assertEquals(DraftOperation.REPLACE, retained.operation)
            assertEquals(current.revision, retained.baseRevision)
            assertEquals(proposal, retained.form)
            assertTrue(store.pendingDrafts().any { it.draftId == stale.draftId })
            assertEquals("Base", store.get(seq)!!.title)
            assertTrue(store.get(seq)!!.completedKeys.contains("single"))
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
        assertTrue(JSONObject(store.exportBackup().toString()).getJSONObject("payload").has("objects"))
    }
}
