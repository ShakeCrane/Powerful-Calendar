package dev.powerfulcalendar.core.api.v1

/** Read-only Store diagnostics used by system-status presentation. */
data class CoreDiagnosticsV1(
    val nextSequencePreview: Long,
    val identityCount: Long,
    val databaseVersion: Int,
    val databaseName: String
)

/** Versioned, platform-neutral queries for non-business Store diagnostics. */
interface CoreDiagnosticsFeatureApiV1 {
    val apiVersion: Int

    /** Reads Store identity and schema metadata without changing Core state. */
    fun queryDiagnostics(): CoreDiagnosticsV1
}
