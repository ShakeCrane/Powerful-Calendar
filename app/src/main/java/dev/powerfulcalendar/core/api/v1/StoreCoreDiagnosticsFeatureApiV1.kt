package dev.powerfulcalendar.core.api.v1

import dev.powerfulcalendar.core.PowerStore

/** Store adapter for the platform-neutral Core diagnostics query. */
internal class StoreCoreDiagnosticsFeatureApiV1(
    private val store: PowerStore
) : CoreDiagnosticsFeatureApiV1 {
    override val apiVersion: Int = 1

    override fun queryDiagnostics(): CoreDiagnosticsV1 = CoreDiagnosticsV1(
        nextSequencePreview = store.nextSequencePreview(),
        identityCount = store.identityCount()
    )
}
