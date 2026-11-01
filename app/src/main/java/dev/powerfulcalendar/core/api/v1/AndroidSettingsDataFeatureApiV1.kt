package dev.powerfulcalendar.core.api.v1

import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/** Android preference adapter behind the platform-neutral Settings/Data contract. */
internal class AndroidSettingsDataFeatureApiV1(context: Context?) : SettingsDataFeatureApiV1 {
    private val appContext = context?.applicationContext
    private val revision = AtomicLong(0L)
    private val listeners = CopyOnWriteArraySet<(SettingsDataChangeV1) -> Unit>()

    override val apiVersion: Int = 1
    override val featureRevision: Long get() = revision.get()

    @Synchronized
    override fun querySettings(): SettingsDataSnapshotV1 {
        val preferences = appContext?.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        val onboarding = appContext?.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        return SettingsDataSnapshotV1(
            featureRevision = featureRevision,
            profileDisplayName = preferences?.getString(PROFILE_NAME_KEY, "").orEmpty(),
            systemCalendarImportPromptShown = onboarding?.getBoolean(
                CALENDAR_IMPORT_PROMPT_KEY, false
            ) ?: false,
            available = preferences != null && onboarding != null
        )
    }

    override fun saveProfileDisplayName(displayName: String): SettingsDataMutationReceiptV1 {
        if (displayName.length > MAX_PROFILE_NAME_LENGTH || displayName.any(Char::isISOControl)) {
            return SettingsDataMutationReceiptV1(
                SettingsDataMutationStatusV1.INVALID,
                featureRevision,
                "个人名称格式无效"
            )
        }
        val preferences = appContext?.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            ?: return unavailableReceipt()
        var change: SettingsDataChangeV1? = null
        val receipt = synchronized(this) {
            val current = preferences.getString(PROFILE_NAME_KEY, "").orEmpty()
            if (current == displayName) {
                SettingsDataMutationReceiptV1(
                    SettingsDataMutationStatusV1.NO_CHANGE,
                    featureRevision
                )
            } else {
                preferences.edit().putString(PROFILE_NAME_KEY, displayName).apply()
                val nextRevision = revision.incrementAndGet()
                change = SettingsDataChangeV1(
                    nextRevision,
                    SettingsDataChangeTypeV1.PROFILE_DISPLAY_NAME_CHANGED
                )
                SettingsDataMutationReceiptV1(SettingsDataMutationStatusV1.UPDATED, nextRevision)
            }
        }
        change?.let(::publish)
        return receipt
    }

    override fun markSystemCalendarImportPromptShown(): SettingsDataMutationReceiptV1 {
        val onboarding = appContext?.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
            ?: return unavailableReceipt()
        var change: SettingsDataChangeV1? = null
        val receipt = synchronized(this) {
            if (onboarding.getBoolean(CALENDAR_IMPORT_PROMPT_KEY, false)) {
                SettingsDataMutationReceiptV1(
                    SettingsDataMutationStatusV1.NO_CHANGE,
                    featureRevision
                )
            } else {
                onboarding.edit().putBoolean(CALENDAR_IMPORT_PROMPT_KEY, true).apply()
                val nextRevision = revision.incrementAndGet()
                change = SettingsDataChangeV1(
                    nextRevision,
                    SettingsDataChangeTypeV1.SYSTEM_CALENDAR_IMPORT_PROMPT_MARKED
                )
                SettingsDataMutationReceiptV1(SettingsDataMutationStatusV1.UPDATED, nextRevision)
            }
        }
        change?.let(::publish)
        return receipt
    }

    override fun subscribe(listener: (SettingsDataChangeV1) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    private fun unavailableReceipt() = SettingsDataMutationReceiptV1(
        SettingsDataMutationStatusV1.UNAVAILABLE,
        featureRevision,
        "设置存储不可用"
    )

    private fun publish(change: SettingsDataChangeV1) {
        listeners.forEach { listener -> runCatching { listener(change) } }
    }

    private companion object {
        const val PROFILE_PREFS = "powercal-profile"
        const val PROFILE_NAME_KEY = "displayName"
        const val ONBOARDING_PREFS = "powercal-onboarding"
        const val CALENDAR_IMPORT_PROMPT_KEY = "system-calendar-import-asked"
        const val MAX_PROFILE_NAME_LENGTH = 80
    }
}
