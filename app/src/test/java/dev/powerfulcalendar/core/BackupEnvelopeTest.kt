package dev.powerfulcalendar.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.ai.AiSettings
import dev.powerfulcalendar.ai.AiSettingsStore
import org.json.JSONArray
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
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Release P0: BackupEnvelope v1 shape, hash, strict versions, referential checks and rollback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupEnvelopeTest {
    private lateinit var context: Context
    private lateinit var source: PowerStore
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        source = PowerStore(context, "source-" + UUID.randomUUID() + ".db")
    }

    @After
    fun tearDown() {
        val name = source.databaseName
        source.close()
        context.deleteDatabase(name)
    }

    private fun newStore(): PowerStore = PowerStore(context, "target-" + UUID.randomUUID() + ".db")

    @Test
    fun boundedBackupReaderAcceptsLimitAndStopsOversizedInputAtFirstExcessByte() {
        val limit = 16
        val exact = "x".repeat(limit).toByteArray(Charsets.UTF_8)
        assertEquals("x".repeat(limit), BackupEnvelope.readUtf8Bounded(
            ByteArrayInputStream(exact), maxBytes = limit
        ))

        val oversized = object : ByteArrayInputStream(ByteArray(1024) { 'x'.code.toByte() }) {
            var bytesConsumed = 0

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { count ->
                    if (count > 0) bytesConsumed += count
                }
        }
        val failure = runCatching {
            BackupEnvelope.readUtf8Bounded(oversized, maxBytes = limit)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("上限"))
        assertEquals(limit + 1, oversized.bytesConsumed)
    }

    private fun usingStore(block: (PowerStore) -> Unit) {
        val store = newStore()
        try {
            block(store)
        } finally {
            closeStore(store)
        }
    }

    private fun closeStore(store: PowerStore) {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    /** activity seq, alarm seq, migration batch id. */
    private fun seed(): Triple<Long, Long, String> {
        val now = System.currentTimeMillis()
        val activity = source.newDraft(
            DraftForm(
                TemporalKind.ACTIVITY, "已提交活动",
                DraftForm.formatInstant(now + 3_600_000, zone),
                DraftForm.formatInstant(now + 7_200_000, zone),
                notes = "备注", zoneId = zone.id
            )
        )
        assertTrue(source.commit(activity.draftId) is PowerStore.CommitOutcome.Committed)
        source.newDraft(
            DraftForm.from(source.get(activity.eventSeq)!!).copy(title = "待处理草稿"),
            activity.eventSeq,
            DraftOperation.REPLACE
        )
        val alarm = source.newDraft(
            DraftForm(
                TemporalKind.ALARM, "已提交闹钟",
                DraftForm.formatInstant(now + 10_800_000, zone),
                reminderMinutes = 10,
                zoneId = zone.id
            )
        )
        assertTrue(source.commit(alarm.draftId) is PowerStore.CommitOutcome.Committed)
        source.recordExecution(alarm.eventSeq, 1L, "slot-a", "primary", "FIRED", "unit")
        val batch = source.beginMigration("unit-source")
        source.finishMigration(batch, listOf("warn"))
        return Triple(activity.eventSeq, alarm.eventSeq, batch)
    }

    private fun restoreIntoFresh(envelope: JSONObject): PowerStore {
        val target = newStore()
        target.restoreBackup(envelope)
        return target
    }

    private fun forged(payload: JSONObject, lineage: String): JSONObject = BackupEnvelope.create(
        payload = payload,
        databaseSchemaVersion = PowerStore.VERSION,
        appVersionCode = 1,
        exportedAtEpochMs = System.currentTimeMillis(),
        storeLineageId = lineage
    )

    @Test
    fun exportProducesEnvelopeV1WithMatchingCanonicalHash() {
        seed()
        val envelope = source.exportBackup()

        assertEquals("powercal-native", envelope.getString("format"))
        assertEquals(BackupEnvelope.FORMAT_VERSION, envelope.getInt("backupFormatVersion"))
        assertEquals(PowerStore.VERSION, envelope.getInt("databaseSchemaVersion"))
        assertTrue(envelope.getLong("appVersionCode") >= 0)
        assertTrue(envelope.getLong("exportedAtEpochMs") > 0)
        assertNotNull(UUID.fromString(envelope.getString("storeLineageId")))

        val payload = envelope.getJSONObject("payload")
        val expected = BackupEnvelope.sha256Hex(BackupEnvelope.canonicalPayloadBytes(payload))
        assertEquals(expected, envelope.getString("payloadSha256"))
        assertTrue(payload.getJSONArray("objects").length() >= 2)
        assertTrue(payload.getJSONArray("drafts").length() >= 1)
        assertTrue(payload.getJSONArray("commits").length() >= 2)
        assertFalse("execution history must stay on the old device", payload.has("executions"))
        assertTrue(payload.getJSONArray("migrations").length() >= 1)
        assertFalse(payload.has("ai_sessions"))
        assertFalse(envelope.toString().contains("owner_token"))
    }

    @Test
    fun roundTripPreservesEntitiesWatermarksLineageAndRebuildsSchedules() {
        val (activitySeq, alarmSeq, batchId) = seed()
        val envelope = source.exportBackup()
        val expectedNextSeq = envelope.getJSONObject("payload").getLong("nextSeq")
        val expectedNextOrder = envelope.getJSONObject("payload").getLong("nextOrder")
        val expectedLineage = envelope.getString("storeLineageId")

        val target = restoreIntoFresh(envelope)
        try {
            assertEquals("已提交活动", target.get(activitySeq)!!.title)
            assertEquals("备注", target.get(activitySeq)!!.notes)
            assertEquals(1, target.pendingDrafts().size)
            assertEquals("待处理草稿", target.pendingDrafts().single().form.title)
            assertEquals(expectedNextSeq, target.nextSequencePreview())
            assertEquals(expectedNextOrder, target.exportBackup().getJSONObject("payload").getLong("nextOrder"))
            assertNull("old execution history must not be imported", target.lastExecution(alarmSeq))
            assertNotNull("restored enabled alarm should be scheduled again", target.intentFor(alarmSeq))
            assertFalse(target.isLocked())
            assertNull(target.currentAiSession())
            val migration = target.migrationSummary(batchId)!!
            assertEquals(0, migration.first)
            assertEquals(listOf("warn"), migration.third)
            assertEquals(expectedLineage, target.exportBackup().getString("storeLineageId"))
        } finally {
            closeStore(target)
        }
    }

    @Test
    fun roundTripPreservesDraftIdentityQueueOrderAndCommittedRevisionHistory() {
        val (activitySeq, _, _) = seed()
        val firstRevisionDraft = source.pendingDrafts().single()
        assertTrue(source.commit(firstRevisionDraft.draftId) is PowerStore.CommitOutcome.Committed)
        val nextDraft = source.newDraft(
            DraftForm.from(source.get(activitySeq)!!).copy(title = "第二版后的待处理草稿"),
            activitySeq,
            DraftOperation.REPLACE
        )

        val envelope = source.exportBackup()
        val sourcePayload = envelope.getJSONObject("payload")
        val target = restoreIntoFresh(envelope)
        try {
            assertEquals(2L, target.get(activitySeq)!!.revision)
            assertEquals(nextDraft.draftId, target.pendingDrafts().single().draftId)
            assertEquals(nextDraft.queueOrder, target.pendingDrafts().single().queueOrder)

            val restoredPayload = target.exportBackup().getJSONObject("payload")
            assertEquals(
                BackupEnvelope.canonicalJson(sourcePayload.getJSONArray("drafts")),
                BackupEnvelope.canonicalJson(restoredPayload.getJSONArray("drafts"))
            )
            assertEquals(
                BackupEnvelope.canonicalJson(sourcePayload.getJSONArray("commits")),
                BackupEnvelope.canonicalJson(restoredPayload.getJSONArray("commits"))
            )
            val activityRevisions = (0 until restoredPayload.getJSONArray("commits").length())
                .map { restoredPayload.getJSONArray("commits").getJSONObject(it) }
                .filter { it.getLong("eventSeq") == activitySeq }
                .map { it.getLong("revision") }
                .sorted()
            assertEquals(listOf(1L, 2L), activityRevisions)
        } finally {
            closeStore(target)
        }
    }

    @Test
    fun retiredEventExportPreservesTombstoneAndSequenceWatermark() {
        val now = System.currentTimeMillis()
        val created = source.newDraft(
            DraftForm(
                TemporalKind.ACTIVITY,
                "需保留的墓碑",
                DraftForm.formatInstant(now + 3_600_000, zone),
                DraftForm.formatInstant(now + 7_200_000, zone),
                zoneId = zone.id
            )
        )
        assertTrue(source.commit(created.draftId) is PowerStore.CommitOutcome.Committed)
        val record = source.get(created.eventSeq)!!
        val deletion = source.newDraft(
            DraftForm.from(record), created.eventSeq, DraftOperation.DELETE
        )
        assertTrue(source.commit(deletion.draftId) is PowerStore.CommitOutcome.Committed)
        val expectedNextSeq = source.nextSequencePreview()
        val envelope = source.exportBackup()
        val payload = envelope.getJSONObject("payload")
        val identity = (0 until payload.getJSONArray("identities").length())
            .map { payload.getJSONArray("identities").getJSONObject(it) }
            .single { it.getLong("seq") == created.eventSeq }
        val objectRow = (0 until payload.getJSONArray("objects").length())
            .map { payload.getJSONArray("objects").getJSONObject(it) }
            .single { it.getJSONObject("payload").getLong("eventSeq") == created.eventSeq }

        assertFalse(identity.isNull("retiredMs"))
        assertEquals(1, objectRow.getInt("retired"))

        val target = restoreIntoFresh(envelope)
        try {
            assertTrue(target.isRetired(created.eventSeq))
            assertNull(target.get(created.eventSeq))
            assertEquals("需保留的墓碑", target.getIncludingRetired(created.eventSeq)!!.title)
            assertEquals(expectedNextSeq, target.nextSequencePreview())
        } finally {
            closeStore(target)
        }
    }

    @Test
    fun nativeBackupExcludesApiCredentialsAndSchedulerHandlesThenRebuildsSchedule() {
        val previousSettings = AiSettingsStore.load(context)
        val sentinelSecret = "pc-backup-secret-${UUID.randomUUID()}"
        AiSettingsStore.save(context, previousSettings.copy(apiKey = sentinelSecret))
        try {
            val (_, alarmSeq, _) = seed()
            assertNotNull(source.intentFor(alarmSeq))
            val envelope = source.exportBackup()
            val serialized = envelope.toString()
            val payload = envelope.getJSONObject("payload")

            assertFalse(serialized.contains(sentinelSecret))
            assertFalse(serialized.contains("api_key"))
            assertFalse(serialized.contains("apiKey"))
            assertFalse(serialized.contains("PendingIntent"))
            assertFalse(payload.has("schedule_intents"))
            assertFalse(payload.has("ai_sessions"))
            assertFalse(payload.has("ai_session_drafts"))
            assertFalse(payload.has("executions"))

            val target = restoreIntoFresh(envelope)
            try {
                assertEquals(sentinelSecret, AiSettingsStore.load(context).apiKey)
                assertNotNull("schedule intent must be rebuilt from restored alarm data", target.intentFor(alarmSeq))
                assertNull("old-device execution history must remain excluded", target.lastExecution(alarmSeq))
            } finally {
                closeStore(target)
            }
        } finally {
            AiSettingsStore.save(context, previousSettings)
        }
    }

    @Test
    fun tamperingWithPayloadIsRejectedBeforeAnyRowIsWritten() {
        seed()
        val envelope = source.exportBackup()
        envelope.getJSONObject("payload").getJSONArray("objects")
            .getJSONObject(0).getJSONObject("payload").put("title", "被篡改")

        usingStore { target ->
            assertTrue(runCatching { target.restoreBackup(envelope) }.isFailure)
            assertEquals(0L, target.identityCount())
            assertEquals(1L, target.nextSequencePreview())
        }
    }

    @Test
    fun unknownFutureBackupFormatVersionIsRejected() {
        seed()
        val envelope = source.exportBackup().put("backupFormatVersion", 99)
        usingStore { target ->
            assertTrue(runCatching { target.restoreBackup(envelope) }.isFailure)
            assertEquals(0L, target.identityCount())
        }
    }

    @Test
    fun unknownBackupFormatIsRejectedBeforeAnyRowIsWritten() {
        seed()
        val envelope = source.exportBackup().put("format", "powercal-other")
        usingStore { target ->
            val outcome = runCatching { target.restoreBackup(envelope) }
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull()!!.message!!.contains("不是 PowerCal 原生备份"))
            assertEquals(0L, target.identityCount())
            assertEquals(1L, target.nextSequencePreview())
        }
    }

    @Test
    fun backupFromANewerSchemaIsRejectedInsteadOfGuessed() {
        seed()
        val envelope = source.exportBackup().put("databaseSchemaVersion", PowerStore.VERSION + 1)
        usingStore { target ->
            assertTrue(runCatching { target.restoreBackup(envelope) }.isFailure)
            assertEquals(0L, target.identityCount())
        }
    }

    @Test
    fun anObjectWithoutItsIdentityIsRejectedEvenWithAValidHash() {
        seed()
        val envelope = source.exportBackup()
        val payload = JSONObject(envelope.getJSONObject("payload").toString())
        payload.put("identities", JSONArray())
        val invalid = forged(payload, envelope.getString("storeLineageId"))
        usingStore { target ->
            val outcome = runCatching { target.restoreBackup(invalid) }
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull()!!.message!!.contains("身份"))
            assertEquals(0L, target.identityCount())
        }
    }

    @Test
    fun aLowSequenceWatermarkIsRejected() {
        seed()
        val envelope = source.exportBackup()
        val payload = JSONObject(envelope.getJSONObject("payload").toString())
        payload.put("nextSeq", 1L)
        val invalid = forged(payload, envelope.getString("storeLineageId"))
        usingStore { target ->
            val outcome = runCatching { target.restoreBackup(invalid) }
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull()!!.message!!.contains("nextSeq"))
            assertEquals(1L, target.nextSequencePreview())
        }
    }

    @Test
    fun danglingDraftAndCommitReferencesAreRejectedBeforeAnyWrite() {
        seed()
        val envelope = source.exportBackup()
        val lineage = envelope.getString("storeLineageId")

        val danglingDraftPayload = JSONObject(envelope.getJSONObject("payload").toString())
        danglingDraftPayload.getJSONArray("drafts").getJSONObject(0)
            .put("eventSeq", 9_999_999L)
        val danglingDraft = forged(danglingDraftPayload, lineage)
        usingStore { target ->
            val outcome = runCatching { target.restoreBackup(danglingDraft) }
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull()!!.message!!.contains("不存在的身份"))
            assertEquals(0L, target.identityCount())
        }

        val danglingCommitPayload = JSONObject(envelope.getJSONObject("payload").toString())
        val existingDrafts = danglingCommitPayload.getJSONArray("drafts")
        val missingDraftId = (0 until existingDrafts.length())
            .maxOf { existingDrafts.getJSONObject(it).getLong("draftId") } + 1
        danglingCommitPayload.getJSONArray("commits").getJSONObject(0)
            .put("draftId", missingDraftId)
        val danglingCommit = forged(danglingCommitPayload, lineage)
        usingStore { target ->
            val outcome = runCatching { target.restoreBackup(danglingCommit) }
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull()!!.message!!.contains("不存在的草稿"))
            assertEquals(0L, target.identityCount())
        }
    }

    @Test
    fun aLowNextOrderWatermarkIsRejected() {
        seed()
        val envelope = source.exportBackup()
        val payload = JSONObject(envelope.getJSONObject("payload").toString())
        payload.put("nextOrder", 1L)
        val invalid = forged(payload, envelope.getString("storeLineageId"))
        usingStore { target ->
            val outcome = runCatching { target.restoreBackup(invalid) }
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull()!!.message!!.contains("nextOrder"))
            assertEquals(0L, target.identityCount())
            assertEquals(1L, target.nextSequencePreview())
        }
    }

    @Test
    fun aMidWriteConstraintFailureRollsBackTheWholeRestore() {
        val (activitySeq, alarmSeq, _) = seed()
        val envelope = source.exportBackup()
        val payload = JSONObject(envelope.getJSONObject("payload").toString())
        // Duplicate (source, external_id) is not hash-visible but violates SQLite's primary key, so
        // the failure happens after identities/objects/drafts have been inserted in the transaction.
        val duplicate = JSONObject().apply {
            put("source", "forced")
            put("externalId", "same")
            put("eventSeq", activitySeq)
        }
        payload.getJSONArray("sourceLinks").put(duplicate)
        payload.getJSONArray("sourceLinks").put(
            JSONObject(duplicate.toString()).put("eventSeq", alarmSeq)
        )
        val invalid = forged(payload, envelope.getString("storeLineageId"))

        usingStore { target ->
            val outcome = runCatching { target.restoreBackup(invalid) }
            assertTrue("duplicate source link must fail the insert", outcome.isFailure)
            assertEquals(0L, target.identityCount())
            assertEquals(1L, target.nextSequencePreview())
            assertTrue(target.all().isEmpty())
        }
    }

    @Test
    fun legacyFlatV2RequiresExplicitConsentThenRestoresAsNewLineage() {
        val (activitySeq, _, _) = seed()
        val payload = source.exportBackup().getJSONObject("payload")
        val legacy = JSONObject().apply {
            put("format", "powercal-native")
            put("version", 2)
            put("exportedMs", System.currentTimeMillis())
            payload.keys().forEach { key ->
                if (key != "nextSeq" && key != "nextOrder") put(key, payload.get(key))
            }
            put("nextSeq", payload.getLong("nextSeq"))
        }

        usingStore { target ->
            val refused = runCatching { target.restoreBackup(legacy) }
            assertTrue(refused.exceptionOrNull() is LegacyBackupConsentRequired)
            assertEquals(0L, target.identityCount())

            val report = target.restoreBackup(legacy, allowLegacyV2 = true)
            assertTrue(report.legacyUnverified)
            assertTrue(report.objects >= 2)
            assertEquals("已提交活动", target.get(activitySeq)!!.title)
            // Flat v2 had no lineage; the restored store must not pretend to be the source lineage.
            assertTrue(target.exportBackup().getString("storeLineageId") != source.exportBackup().getString("storeLineageId"))
        }
    }

    @Test
    fun canonicalHashIsIndependentOfObjectKeyOrder() {
        val first = JSONObject()
            .put("b", 1L)
            .put("a", "文本")
            .put("c", JSONArray().put(2).put(1))
        val second = JSONObject()
            .put("c", JSONArray().put(2).put(1))
            .put("a", "文本")
            .put("b", 1)

        assertEquals(BackupEnvelope.canonicalJson(first), BackupEnvelope.canonicalJson(second))
        assertEquals(
            BackupEnvelope.sha256Hex(BackupEnvelope.canonicalPayloadBytes(first)),
            BackupEnvelope.sha256Hex(BackupEnvelope.canonicalPayloadBytes(second))
        )
    }

    @Test
    fun missingHashOrPayloadIsRejected() {
        seed()
        val withoutHash = source.exportBackup().apply { remove("payloadSha256") }
        val withoutPayload = source.exportBackup().apply { remove("payload") }
        usingStore { target ->
            assertTrue(runCatching { target.restoreBackup(withoutHash) }.isFailure)
            assertTrue(runCatching { target.restoreBackup(withoutPayload) }.isFailure)
            assertEquals(0L, target.identityCount())
        }
    }

    @Test
    fun readbackVerificationComparesTableCountsAndWatermarks() {
        seed()
        val envelope = source.exportBackup()
        val alteredPayload = JSONObject(envelope.getJSONObject("payload").toString())
        alteredPayload.getJSONArray("drafts").remove(0)
        val altered = BackupEnvelope.create(
            alteredPayload,
            PowerStore.VERSION,
            1,
            envelope.getLong("exportedAtEpochMs"),
            envelope.getString("storeLineageId")
        )
        val outcome = runCatching {
            BackupEnvelope.verifyReadback(envelope, altered, PowerStore.VERSION)
        }
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()!!.message!!.contains("drafts"))
        // An untouched read-back still passes.
        BackupEnvelope.verifyReadback(envelope, JSONObject(envelope.toString()), PowerStore.VERSION)
    }

    @Test
    fun flatV1BackupIsExplicitlyRejectedAndEnvelopeCannotBeDowngradedToLegacy() {
        seed()
        val flatV1 = JSONObject()
            .put("format", "powercal-native")
            .put("version", 1)
            .put("identities", JSONArray())
        val stripped = JSONObject(source.exportBackup().toString()).apply {
            remove("backupFormatVersion")
            put("version", 2)
        }
        usingStore { target ->
            val v1 = runCatching { target.restoreBackup(flatV1, allowLegacyV2 = true) }
            assertTrue(v1.exceptionOrNull()!!.message!!.contains("flat backup v1"))

            val downgraded = runCatching { target.restoreBackup(stripped, allowLegacyV2 = true) }
            assertTrue("stripped envelope must not become an unverified legacy import", downgraded.isFailure)
            assertEquals(0L, target.identityCount())
        }
    }

    @Test
    fun legacyImportRejectsUnknownFieldsButPreservesItsNextOrder() {
        seed()
        val payload = source.exportBackup().getJSONObject("payload")
        fun legacy(): JSONObject = JSONObject().apply {
            put("format", "powercal-native")
            put("version", 2)
            put("exportedMs", System.currentTimeMillis())
            payload.keys().forEach { key ->
                if (key != "nextSeq" && key != "nextOrder") put(key, payload.get(key))
            }
            put("nextSeq", payload.getLong("nextSeq"))
            put("nextOrder", 50L)
        }

        usingStore { target ->
            val unknown = legacy().put("futureField", true)
            assertTrue(runCatching { target.restoreBackup(unknown, allowLegacyV2 = true) }.isFailure)

            target.restoreBackup(legacy(), allowLegacyV2 = true)
            assertEquals(50L, target.exportBackup().getJSONObject("payload").getLong("nextOrder"))
        }
    }

    @Test
    fun restoreIsRefusedWhenTheTargetAlreadyContainsUserData() {
        seed()
        val envelope = source.exportBackup()
        usingStore { target ->
            target.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.now(), zone))
            val restoreFailure = runCatching { target.restoreBackup(envelope) }.exceptionOrNull()
            assertTrue(restoreFailure is PowerStore.NonEmptyRestoreTargetException)
            assertEquals(1L, target.identityCount())
        }
    }

    @Test
    fun restoreRechecksAfterWriteBetweenPreflightAndTransaction() {
        val target = newStore()
        target.identityCount()
        val writer = PowerStore(context, target.databaseName)
        try {
            writer.identityCount()
            seed()
            var concurrentDraft: DraftCard? = null
            val outcome = runCatching {
                target.restoreBackupWithPreflightHookForTest(source.exportBackup()) {
                    concurrentDraft = writer.newDraft(
                        DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.now(), zone)
                    )
                }
            }

            assertTrue(concurrentDraft != null)
            assertTrue(outcome.exceptionOrNull() is PowerStore.NonEmptyRestoreTargetException)
            assertEquals(1L, target.identityCount())
            assertEquals(2L, target.nextSequencePreview())
            assertEquals(concurrentDraft!!.draftId, target.pendingDrafts().single().draftId)
            assertTrue(target.exportBackup().getJSONObject("payload").getJSONArray("sourceLinks").length() == 0)
        } finally {
            closeStore(writer)
            closeStore(target)
        }
    }
}
