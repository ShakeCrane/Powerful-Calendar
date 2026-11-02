package dev.powerfulcalendar.core.api.v1

import android.content.Context
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.interop.ImportCheckpoint
import dev.powerfulcalendar.interop.IcsCodec
import dev.powerfulcalendar.interop.IcsImportPlanner
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.util.UUID

/** Store-backed adapter. Import transactions and backup verification remain owned by Core. */
internal class StoreImportExportFeatureApiV1(
    private val store: PowerStore,
    private val draftReview: DraftReviewFeatureApiV1,
    private val events: StoreEventFeatureApiV1,
    importCheckpointContext: Context?
) : ImportExportFeatureApiV1 {
    private val importCheckpointContext = importCheckpointContext?.applicationContext
    override val apiVersion: Int = 1

    override fun prepareIcsImport(content: String, zoneId: String): IcsImportPlanV1 {
        val plan = IcsImportPlanner.prepare(content, ZoneId.of(zoneId))
        return IcsImportPlanV1(
            fingerprint = plan.fingerprint,
            candidates = plan.candidates.map { IcsImportCandidateV1(it.sourceId, it.form.toEventFormV1()) },
            problems = plan.problems.map { IcsImportProblemV1(it.sourceId, it.reason, it.evidence) },
            units = plan.units
        )
    }

    @Synchronized
    override fun duplicateIcsCandidateSourceIds(
        candidates: List<IcsImportCandidateV1>
    ): Set<String> {
        val known = (
            store.all().map { DraftForm.from(it) } +
                store.pendingDrafts().filter { it.operation == DraftOperation.CREATE }
                    .map { it.form }
            ).mapTo(mutableSetOf(), ::icsIdentity)
        val duplicates = linkedSetOf<String>()
        candidates.forEach { candidate ->
            if (!known.add(icsIdentity(candidate.form.toCoreDraftForm()))) {
                duplicates += candidate.sourceId
            }
        }
        return duplicates
    }

    override fun existingDraftIds(draftIds: List<Long>): Set<Long> =
        draftIds.asSequence().filter { it > 0L && store.draft(it) != null }.toSet()

    override fun queryIcsImportCheckpoint(fingerprint: String): IcsImportCheckpointV1? {
        val context = importCheckpointContext ?: return null
        return ImportCheckpoint.recent(context).firstOrNull { it.fingerprint == fingerprint }
            ?.let { IcsImportCheckpointV1(it.fingerprint, it.draftIds, it.problemCount) }
    }

    override fun recordIcsImportCheckpoint(checkpoint: IcsImportCheckpointV1) {
        val context = checkNotNull(importCheckpointContext) {
            "ICS 导入恢复收据存储不可用"
        }
        ImportCheckpoint.record(context, ImportCheckpoint.Snapshot(
            checkpoint.fingerprint, checkpoint.draftIds, checkpoint.problemCount
        ))
    }

    override suspend fun createIcsDraft(
        candidate: IcsImportCandidateV1,
        requestId: String
    ): DraftReviewReceiptV1 = draftReview.createDraft(candidate.form, requestId)

    override fun exportActivitiesIcs(): IcsExportDocumentV1 {
        val activities = store.all().filter { it.kind == TemporalKind.ACTIVITY }
        return IcsExportDocumentV1(
            content = IcsCodec.exportActivities(activities),
            activityCount = activities.size
        )
    }

    override fun beginStructuredImport(source: String): StructuredImportBatchHandleV1 {
        require(source.isNotBlank()) { "结构化导入来源不能为空" }
        return StructuredImportBatchHandleV1(store.beginMigration(source))
    }

    override fun importStructuredEvent(
        batch: StructuredImportBatchHandleV1,
        source: String,
        externalId: String,
        event: StructuredCalendarEventV1
    ): StructuredImportReceiptV1 {
        require(batch.value.isNotBlank()) { "结构化导入批次无效" }
        val record = event.toTemporalRecord(source)
        return when (val outcome = store.importStructured(
            record = record,
            externalId = externalId,
            batchId = batch.value,
            source = source
        )) {
            is PowerStore.ImportOutcome.Imported -> {
                events.publishDeckFormalChange(EventFeatureChangeTypeV1.FORMAL_CREATED, outcome.eventSeq)
                StructuredImportReceiptV1(
                    StructuredImportStatusV1.IMPORTED,
                    eventSeq = outcome.eventSeq,
                    enabled = outcome.enabled
                )
            }
            is PowerStore.ImportOutcome.AlreadyImported -> StructuredImportReceiptV1(
                StructuredImportStatusV1.ALREADY_IMPORTED,
                eventSeq = outcome.eventSeq
            )
            is PowerStore.ImportOutcome.Rejected -> StructuredImportReceiptV1(
                StructuredImportStatusV1.REJECTED,
                reason = outcome.reason
            )
        }
    }

    override fun finishStructuredImport(batch: StructuredImportBatchHandleV1, warnings: List<String>) {
        require(batch.value.isNotBlank()) { "结构化导入批次无效" }
        store.finishMigration(batch.value, warnings)
    }

    override fun exportBackup(): BackupExportDocumentV1 {
        val json = store.exportBackup().toString(2)
        requireWithinBackupLimit(json)
        return BackupExportDocumentV1(json)
    }

    override fun verifyBackupReadback(
        exported: BackupExportDocumentV1,
        readbackJson: String
    ): BackupReadbackVerificationV1 {
        requireWithinBackupLimit(exported.json)
        requireWithinBackupLimit(readbackJson)
        val original = JSONObject(exported.json)
        val verified = BackupEnvelope.verifyReadback(original, JSONObject(readbackJson), PowerStore.VERSION)
        return BackupReadbackVerificationV1(
            verified = true,
            storeLineageId = verified.storeLineageId,
            payloadSha256 = original.getString("payloadSha256")
        )
    }

    override fun restoreBackup(json: String, allowLegacyV2: Boolean): BackupRestoreReceiptV1 {
        requireWithinBackupLimit(json)
        val parsed = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("备份文件不是有效 JSON", it) }
        val report = store.restoreBackup(parsed, allowLegacyV2)
        events.publishStoreRestored()
        return BackupRestoreReceiptV1(
            identities = report.identities,
            objects = report.objects,
            drafts = report.drafts,
            commits = report.commits,
            legacyUnverified = report.legacyUnverified,
            warnings = report.warnings.toList()
        )
    }

    private fun icsIdentity(form: DraftForm): List<Any?> = listOf(
        form.kind,
        form.title.trim(),
        form.parseStart(),
        form.parseEnd(),
        form.parseDeadline(),
        form.recurrence,
        form.zoneId,
        form.location.trim(),
        form.notes.trim(),
        form.reminderMinutes
    )

    private fun StructuredCalendarEventV1.toTemporalRecord(source: String) = TemporalRecord(
        eventSeq = 0L,
        revision = 0L,
        kind = TemporalKind.ACTIVITY,
        title = title,
        notes = notes,
        location = location,
        startMillis = startMillis,
        endMillis = endMillis.coerceAtLeast(startMillis + 60_000L),
        reminderMinutes = reminderMinutes,
        enabled = enabled,
        source = source,
        zoneId = zoneId
    )

    private fun requireWithinBackupLimit(json: String) {
        require(json.toByteArray(StandardCharsets.UTF_8).size <= BackupEnvelope.MAX_BYTES) {
            "备份超过 64 MiB 安全上限"
        }
    }
}

internal fun newImportRequestId(): String = "import-${UUID.randomUUID()}"
