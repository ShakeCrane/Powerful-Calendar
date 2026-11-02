package dev.powerfulcalendar.core.api.v1

/** One locally parsed ICS candidate. The Host must keep it as a reviewable draft. */
data class IcsImportCandidateV1(
    val sourceId: String,
    val form: EventFormV1
)

/** Unresolved ICS input is untrusted evidence and is never sent to a provider by this API. */
data class IcsImportProblemV1(
    val sourceId: String,
    val reason: String,
    val evidence: String
)

data class IcsImportPlanV1(
    val fingerprint: String,
    val candidates: List<IcsImportCandidateV1>,
    val problems: List<IcsImportProblemV1>,
    val units: Int
)

/** Durable import recovery hint; it does not authorize skipping any candidate. */
data class IcsImportCheckpointV1(
    val fingerprint: String,
    val draftIds: List<Long>,
    val problemCount: Int
)

data class IcsExportDocumentV1(
    val content: String,
    val activityCount: Int
)

/** Platform-neutral value read from an OS calendar provider by trusted Host code. */
data class StructuredCalendarEventV1(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val notes: String = "",
    val location: String = "",
    val reminderMinutes: Int = 0,
    val source: String = "android-calendar",
    val zoneId: String,
    val enabled: Boolean = reminderMinutes >= 0
)

data class StructuredImportBatchHandleV1(val value: String)

/** A secondary stable source ID for the same structured import record. */
data class StructuredImportIdentityV1(val source: String, val externalId: String)

enum class StructuredImportStatusV1 { IMPORTED, ALREADY_IMPORTED, REJECTED }

data class StructuredImportReceiptV1(
    val status: StructuredImportStatusV1,
    val eventSeq: Long? = null,
    val enabled: Boolean? = null,
    val reason: String? = null
)

/** The JSON is opaque to presentation code; only the trusted Host writes it to a selected URI. */
data class BackupExportDocumentV1(val json: String)

data class BackupReadbackVerificationV1(
    val verified: Boolean,
    val storeLineageId: String,
    val payloadSha256: String
)

data class BackupRestoreReceiptV1(
    val identities: Int,
    val objects: Int,
    val drafts: Int,
    val commits: Int,
    val legacyUnverified: Boolean,
    val warnings: List<String>
)

enum class BackupRestoreFailureKindV1 {
    INPUT_TOO_LARGE,
    INVALID_JSON,
    LEGACY_CONSENT_REQUIRED,
    TARGET_NOT_EMPTY,
    INTEGRITY_FAILURE,
    INVALID_BACKUP,
    RESTORE_UNAVAILABLE
}

/** Stable restore failure categories; implementation exception types and messages stay in Core. */
class BackupRestoreExceptionV1(val kind: BackupRestoreFailureKindV1) :
    IllegalStateException("备份恢复未完成（${kind.name}）")

/**
 * Shared import/export operations for Native and Web Hosts. Android pickers, provider
 * permissions, readback I/O, user consent and post-restore scheduler recovery remain Host-owned.
 */
interface ImportExportFeatureApiV1 {
    val apiVersion: Int

    fun prepareIcsImport(content: String, zoneId: String): IcsImportPlanV1
    /** Returns source IDs that duplicate an existing formal/pending draft or an earlier candidate. */
    fun duplicateIcsCandidateSourceIds(candidates: List<IcsImportCandidateV1>): Set<String>
    fun existingDraftIds(draftIds: List<Long>): Set<Long>
    /** Looks up the bounded durable recovery hint for this exact input fingerprint. */
    fun queryIcsImportCheckpoint(fingerprint: String): IcsImportCheckpointV1?
    /** Persists a recovery hint synchronously; throws if durable Host storage is unavailable. */
    fun recordIcsImportCheckpoint(checkpoint: IcsImportCheckpointV1)
    suspend fun createIcsDraft(
        candidate: IcsImportCandidateV1,
        requestId: String
    ): DraftReviewReceiptV1

    fun exportActivitiesIcs(): IcsExportDocumentV1

    fun beginStructuredImport(source: String): StructuredImportBatchHandleV1
    fun importStructuredEvent(
        batch: StructuredImportBatchHandleV1,
        source: String,
        externalId: String,
        event: StructuredCalendarEventV1,
        additionalIdentities: List<StructuredImportIdentityV1> = emptyList()
    ): StructuredImportReceiptV1
    fun finishStructuredImport(batch: StructuredImportBatchHandleV1, warnings: List<String> = emptyList())

    fun exportBackup(): BackupExportDocumentV1
    fun verifyBackupReadback(
        exported: BackupExportDocumentV1,
        readbackJson: String
    ): BackupReadbackVerificationV1
    fun restoreBackup(json: String, allowLegacyV2: Boolean = false): BackupRestoreReceiptV1
}
