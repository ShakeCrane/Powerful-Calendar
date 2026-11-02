package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportExportFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "ix-${UUID.randomUUID().toString().take(8)}.db")
        core = PowerCalCoreFeatureApiV1(store)
    }

    @After fun tearDown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test fun icsImportUsesReviewDraftApiAndDuplicateQuery() = runBlocking {
        val text = """BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:import-api-one
DTSTART:20261103T010000Z
DTEND:20261103T020000Z
SUMMARY:API imported activity
END:VEVENT
END:VCALENDAR
"""
        val api = core.importExport
        val plan = api.prepareIcsImport(text, zone.id)

        assertEquals(1, plan.units)
        assertEquals(1, plan.candidates.size)
        assertTrue(api.duplicateIcsCandidateSourceIds(plan.candidates).isEmpty())
        val receipt = api.createIcsDraft(plan.candidates.single(), "ics-import-api-1")

        assertTrue(receipt.succeeded)
        assertNotNull(receipt.draftId)
        assertTrue(store.all().isEmpty())
        assertEquals(1, store.pendingDrafts().size)
        assertEquals(setOf(plan.candidates.single().sourceId),
            api.duplicateIcsCandidateSourceIds(plan.candidates))
    }

    @Test fun icsPreflightKeepsValidUnitsSeparateFromUnresolvedSeries() {
        val seriesMaster = """
            BEGIN:VEVENT
            UID:course-series
            DTSTART:20261103T010000Z
            DTEND:20261103T020000Z
            RRULE:FREQ=WEEKLY;COUNT=6
            SUMMARY:Course
            END:VEVENT
        """.trimIndent()
        val seriesOverride = """
            BEGIN:VEVENT
            UID:course-series
            DTSTART:20261110T030000Z
            DTEND:20261110T040000Z
            RECURRENCE-ID:20261110T010000Z
            SUMMARY:Moved course
            END:VEVENT
        """.trimIndent()
        val independent = """
            BEGIN:VEVENT
            UID:independent
            DTSTART:20261104T010000Z
            DTEND:20261104T020000Z
            SUMMARY:Independent event
            END:VEVENT
        """.trimIndent()
        val text = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" +
            listOf(seriesMaster, seriesOverride, independent).joinToString("\r\n") +
            "\r\nEND:VCALENDAR\r\n"

        val plan = core.importExport.prepareIcsImport(text, zone.id)

        assertEquals(2, plan.units)
        assertEquals(listOf("uid:independent"), plan.candidates.map { it.sourceId })
        assertEquals(listOf("uid:course-series"), plan.problems.map { it.sourceId })
        assertTrue(plan.problems.single().reason.contains("多个组件"))
        assertTrue(plan.problems.single().evidence.contains("RECURRENCE-ID"))
    }

    @Test fun icsImportCheckpointIsDurableAndUpdatedThroughFeatureApi() {
        val api = PowerCalCoreFeatureApiV1(
            store, importCheckpointContext = context
        ).importExport
        val fingerprint = "b".repeat(64)
        val first = IcsImportCheckpointV1(fingerprint, listOf(17L), 2)
        val updated = IcsImportCheckpointV1(fingerprint, listOf(17L, 18L), 1)

        api.recordIcsImportCheckpoint(first)
        assertEquals(first, api.queryIcsImportCheckpoint(fingerprint))
        api.recordIcsImportCheckpoint(updated)

        val reopenedApi = PowerCalCoreFeatureApiV1(
            store, importCheckpointContext = context
        ).importExport
        assertEquals(updated, reopenedApi.queryIcsImportCheckpoint(fingerprint))
        assertNull(reopenedApi.queryIcsImportCheckpoint("c".repeat(64)))
    }

    @Test fun contextlessIcsCheckpointReadIsEmptyAndWriteFailsClosed() {
        val api = core.importExport
        val checkpoint = IcsImportCheckpointV1("d".repeat(64), emptyList(), 0)

        assertNull(api.queryIcsImportCheckpoint(checkpoint.fingerprint))
        try {
            api.recordIcsImportCheckpoint(checkpoint)
            throw AssertionError("checkpoint writes must fail when durable storage is unavailable")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("存储不可用"))
        }
    }

    @Test fun structuredImportPublishesOnceAndUsesPersistentSourceLink() {
        val api = core.importExport
        val event = StructuredCalendarEventV1(
            title = "Structured event",
            startMillis = 1_793_583_000_000L,
            endMillis = 1_793_586_600_000L,
            source = "test-calendar",
            zoneId = zone.id
        )
        val changes = mutableListOf<EventFeatureChangeV1>()
        val subscription = core.events.subscribe(changes::add)
        try {
            val firstBatch = api.beginStructuredImport("test-calendar")
            val first = api.importStructuredEvent(firstBatch, "test-calendar", "event-1", event)
            api.finishStructuredImport(firstBatch)
            val secondBatch = api.beginStructuredImport("test-calendar")
            val retry = api.importStructuredEvent(secondBatch, "test-calendar", "event-1", event)
            api.finishStructuredImport(secondBatch)

            assertEquals(StructuredImportStatusV1.IMPORTED, first.status)
            assertNotNull(first.eventSeq)
            assertEquals(StructuredImportStatusV1.ALREADY_IMPORTED, retry.status)
            assertEquals(1, store.all().size)
            assertEquals(1, store.migrationSummary(secondBatch.value)?.second)
            assertEquals(listOf(EventFeatureChangeTypeV1.FORMAL_CREATED), changes.map { it.type })
        } finally {
            subscription.close()
        }
    }

    @Test fun structuredImportPreservesExplicitEnabledValue() {
        val event = StructuredCalendarEventV1(
            title = "Explicit import metadata",
            startMillis = 1_793_583_000_000L,
            endMillis = 1_793_586_600_000L,
            reminderMinutes = -1,
            source = "test-calendar",
            zoneId = zone.id,
            enabled = true
        )
        val api = core.importExport
        val batch = api.beginStructuredImport("test-calendar")
        val receipt = api.importStructuredEvent(batch, "test-calendar", "explicit-1", event)
        api.finishStructuredImport(batch)

        assertEquals(StructuredImportStatusV1.IMPORTED, receipt.status)
        val imported = core.events.queryEvents().events.single()
        assertTrue(imported.enabled)
        assertEquals(-1, imported.reminderMinutes)
    }

    @Test fun backupReadbackAndRestoreUseTheSharedApi() {
        val card = store.newDraft(
            DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 11, 3), zone)
                .copy(title = "Backup API activity")
        )
        assertTrue(store.commit(card.draftId, actor = "test") is PowerStore.CommitOutcome.Committed)
        val api = core.importExport
        val exported = api.exportBackup()
        val verified = api.verifyBackupReadback(exported, exported.json)
        assertTrue(verified.verified)
        assertTrue(verified.payloadSha256.matches(Regex("[0-9a-f]{64}")))

        val target = PowerStore(context, "ix-r-${UUID.randomUUID().toString().take(8)}.db")
        try {
            val targetCore = PowerCalCoreFeatureApiV1(target)
            val changes = mutableListOf<EventFeatureChangeTypeV1>()
            val subscription = targetCore.events.subscribe { changes += it.type }
            try {
                val restored = targetCore.importExport.restoreBackup(exported.json)
                assertEquals(1, restored.objects)
                assertEquals("Backup API activity", targetCore.events.queryEvents().events.single().title)
                assertEquals(listOf(EventFeatureChangeTypeV1.STORE_RESTORED), changes)
            } finally {
                subscription.close()
            }
        } finally {
            val name = target.databaseName
            target.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun backupRestoreFailuresUseStableApiCategories() {
        val api = core.importExport
        val invalidJson = runCatching { api.restoreBackup("{") }.exceptionOrNull()
        assertTrue(invalidJson is BackupRestoreExceptionV1)
        assertEquals(BackupRestoreFailureKindV1.INVALID_JSON,
            (invalidJson as BackupRestoreExceptionV1).kind)

        val damaged = JSONObject(api.exportBackup().json).put("payloadSha256", "0".repeat(64))
        val integrityFailure = runCatching { api.restoreBackup(damaged.toString()) }.exceptionOrNull()
        assertTrue(integrityFailure is BackupRestoreExceptionV1)
        assertEquals(BackupRestoreFailureKindV1.INTEGRITY_FAILURE,
            (integrityFailure as BackupRestoreExceptionV1).kind)
    }

    @Test fun restoreApiClassifiesNonEmptyTargetWithoutChangingIt() {
        store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, LocalDate.of(2026, 11, 3), zone)
            .copy(title = "Keep this draft"))
        val backup = core.importExport.exportBackup().json

        val failure = runCatching { core.importExport.restoreBackup(backup) }.exceptionOrNull()

        assertTrue(failure is BackupRestoreExceptionV1)
        assertEquals(BackupRestoreFailureKindV1.TARGET_NOT_EMPTY,
            (failure as BackupRestoreExceptionV1).kind)
        assertEquals("Keep this draft", store.pendingDrafts().single().form.title)
    }

    @Test fun restoreApiReportsLegacyConsentRequirementWithoutStoreTypes() {
        val payload = JSONObject(core.importExport.exportBackup().json).getJSONObject("payload")
        val legacy = JSONObject().apply {
            put("format", "powercal-native")
            put("version", 2)
            put("exportedMs", System.currentTimeMillis())
            payload.keys().forEach { key ->
                if (key != "nextSeq" && key != "nextOrder" &&
                    key !in BackupEnvelope.OPTIONAL_TABLES
                ) put(key, payload.get(key))
            }
            put("nextSeq", payload.getLong("nextSeq"))
        }
        val target = PowerStore(context, "ix-legacy-${UUID.randomUUID().toString().take(8)}.db")
        try {
            val api = PowerCalCoreFeatureApiV1(target).importExport

            val failure = runCatching { api.restoreBackup(legacy.toString()) }.exceptionOrNull()

            assertTrue(failure is BackupRestoreExceptionV1)
            assertEquals(BackupRestoreFailureKindV1.LEGACY_CONSENT_REQUIRED,
                (failure as BackupRestoreExceptionV1).kind)
            assertTrue(target.all().isEmpty())
        } finally {
            val name = target.databaseName
            target.close()
            context.deleteDatabase(name)
        }
    }
}
