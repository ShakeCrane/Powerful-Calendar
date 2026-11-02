package dev.powerfulcalendar.core.api.v1

import android.content.Context
import dev.powerfulcalendar.core.BackupEnvelope
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.DraftOperation
import dev.powerfulcalendar.core.LegacyBackupConsentRequired
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import dev.powerfulcalendar.interop.ImportCheckpoint
import dev.powerfulcalendar.interop.IcsCodec
import dev.powerfulcalendar.interop.IcsImportPlanner
import org.json.JSONObject
import org.json.JSONException
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Store-backed adapter. Import transactions and backup verification remain owned by Core. */
internal class StoreImportExportFeatureApiV1(
    private val store: PowerStore,
    private val draftReview: DraftReviewFeatureApiV1,
    private val events: StoreEventFeatureApiV1,
    importCheckpointContext: Context?
) : ImportExportFeatureApiV1 {
    private val importCheckpointContext = importCheckpointContext?.applicationContext
    private val icsImportMutex = Mutex()
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
        draftIds.asSequence().filter { id -> store.draft(id)?.isActionable == true }.toSet()

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

    override suspend fun importIcsToDrafts(
        content: String,
        zoneId: String
    ): IcsImportExecutionV1 = icsImportMutex.withLock {
        checkNotNull(importCheckpointContext) { "ICS 导入恢复收据存储不可用" }
        val plan = prepareIcsImport(content, zoneId)
        if (plan.units == 0) return@withLock IcsImportExecutionV1(
            plan = plan,
            draftIds = emptyList(),
            duplicateCount = 0,
            failedCount = 0
        )

        val duplicateSourceIds = duplicateIcsCandidateSourceIds(plan.candidates)
        var duplicateCount = 0
        var failedCount = 0
        val draftIds = mutableListOf<Long>()
        queryIcsImportCheckpoint(plan.fingerprint)?.draftIds
            ?.let(::existingDraftIds)
            ?.forEach(draftIds::add)

        plan.candidates.forEach { candidate ->
            if (candidate.sourceId in duplicateSourceIds) {
                duplicateCount++
                return@forEach
            }
            val receipt = try {
                createIcsDraft(candidate, newImportRequestId())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failedCount++
                return@forEach
            }
            val draftId = receipt.draftId
            if (receipt.succeeded && draftId != null) {
                if (store.draft(draftId)?.isActionable == true) draftIds += draftId
                recordIcsImportCheckpoint(IcsImportCheckpointV1(
                    plan.fingerprint, draftIds.distinct(), plan.problems.size
                ))
            } else failedCount++
        }
        recordIcsImportCheckpoint(IcsImportCheckpointV1(
            plan.fingerprint, draftIds.distinct(), plan.problems.size
        ))
        IcsImportExecutionV1(
            plan = plan,
            draftIds = draftIds.distinct(),
            duplicateCount = duplicateCount,
            failedCount = failedCount
        )
    }

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
        event: StructuredCalendarEventV1,
        additionalIdentities: List<StructuredImportIdentityV1>
    ): StructuredImportReceiptV1 {
        require(batch.value.isNotBlank()) { "结构化导入批次无效" }
        val record = event.toTemporalRecord(source)
        return when (val outcome = store.importStructured(
            record = record,
            externalId = externalId,
            batchId = batch.value,
            source = source,
            additionalIdentities = additionalIdentities.map {
                PowerStore.SourceIdentity(it.source, it.externalId)
            }
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
        if (json.toByteArray(StandardCharsets.UTF_8).size > BackupEnvelope.MAX_BYTES) {
            throw BackupRestoreExceptionV1(BackupRestoreFailureKindV1.INPUT_TOO_LARGE)
        }
        val parsed = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw BackupRestoreExceptionV1(BackupRestoreFailureKindV1.INVALID_JSON)
        }
        val report = try {
            store.restoreBackup(parsed, allowLegacyV2)
        } catch (_: LegacyBackupConsentRequired) {
            throw BackupRestoreExceptionV1(BackupRestoreFailureKindV1.LEGACY_CONSENT_REQUIRED)
        } catch (_: PowerStore.NonEmptyRestoreTargetException) {
            throw BackupRestoreExceptionV1(BackupRestoreFailureKindV1.TARGET_NOT_EMPTY)
        } catch (error: IllegalArgumentException) {
            val kind = if (error.causeChainHasHashFailure()) {
                BackupRestoreFailureKindV1.INTEGRITY_FAILURE
            } else {
                BackupRestoreFailureKindV1.INVALID_BACKUP
            }
            throw BackupRestoreExceptionV1(kind)
        } catch (_: Exception) {
            throw BackupRestoreExceptionV1(BackupRestoreFailureKindV1.RESTORE_UNAVAILABLE)
        }
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

    private fun Throwable.causeChainHasHashFailure(): Boolean =
        generateSequence(this) { it.cause }.take(8).any { error ->
            error.message?.let { message ->
                message.contains("SHA-256", ignoreCase = true) ||
                    message.contains("hash", ignoreCase = true)
            } == true
        }
}

internal fun newImportRequestId(): String = "import-${UUID.randomUUID()}"
