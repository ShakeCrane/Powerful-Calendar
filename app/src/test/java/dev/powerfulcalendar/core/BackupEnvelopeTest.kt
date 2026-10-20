package dev.powerfulcalendar.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
            assertTrue(runCatching { target.restoreBackup(envelope) }.isFailure)
            assertEquals(1L, target.identityCount())
        }
    }
}
