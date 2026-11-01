package dev.powerfulcalendar.core.api.v1

data class SettingsDataSnapshotV1(
    val featureRevision: Long,
    val profileDisplayName: String,
    val systemCalendarImportPromptShown: Boolean,
    val available: Boolean
)

enum class SettingsDataMutationStatusV1 { UPDATED, NO_CHANGE, INVALID, UNAVAILABLE }

data class SettingsDataMutationReceiptV1(
    val status: SettingsDataMutationStatusV1,
    val featureRevision: Long,
    val message: String? = null
)

enum class SettingsDataChangeTypeV1 {
    PROFILE_DISPLAY_NAME_CHANGED,
    SYSTEM_CALENDAR_IMPORT_PROMPT_MARKED
}

data class SettingsDataChangeV1(
    val featureRevision: Long,
    val type: SettingsDataChangeTypeV1
)

/**
 * Local profile and onboarding preferences shared by Native and Web Host adapters.
 * Android storage, AI credentials and system capabilities are intentionally outside this contract.
 */
interface SettingsDataFeatureApiV1 {
    val apiVersion: Int
    val featureRevision: Long

    fun querySettings(): SettingsDataSnapshotV1

    /** Empty names are allowed; values over 80 UTF-16 code units or containing controls are rejected. */
    fun saveProfileDisplayName(displayName: String): SettingsDataMutationReceiptV1

    /** Records the one-time system-calendar import prompt as handled. This operation is idempotent. */
    fun markSystemCalendarImportPromptShown(): SettingsDataMutationReceiptV1

    fun subscribe(listener: (SettingsDataChangeV1) -> Unit): AutoCloseable
}
