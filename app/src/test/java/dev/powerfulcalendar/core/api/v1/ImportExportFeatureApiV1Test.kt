package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
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
}
