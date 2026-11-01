package dev.powerfulcalendar.core.api.v1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.PowerStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDataFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore
    private lateinit var core: PowerCalCoreFeatureApiV1

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        store = PowerStore(context, "settings-api-${UUID.randomUUID().toString().take(8)}.db")
        core = PowerCalCoreFeatureApiV1(store, settingsContext = context)
    }

    @After fun tearDown() {
        val databaseName = store.databaseName
        store.close()
        context.deleteDatabase(databaseName)
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun apiReadsExistingPreferenceKeysAndCoreExposesOneSharedService() {
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(PROFILE_NAME_KEY, "existing local name").commit()
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(CALENDAR_IMPORT_PROMPT_KEY, true).commit()

        assertSame(core.settingsData, core.settingsData)
        val snapshot = core.settingsData.querySettings()

        assertEquals(1, core.settingsData.apiVersion)
        assertTrue(snapshot.available)
        assertEquals("existing local name", snapshot.profileDisplayName)
        assertTrue(snapshot.systemCalendarImportPromptShown)
    }

    @Test fun profileAndPromptCommandsAreValidatedIdempotentAndPublishRevisions() {
        val changes = mutableListOf<SettingsDataChangeV1>()
        val subscription = core.settingsData.subscribe(changes::add)
        try {
            val profile = core.settingsData.saveProfileDisplayName("小明")
            assertEquals(SettingsDataMutationStatusV1.UPDATED, profile.status)
            assertEquals(1L, profile.featureRevision)
            assertEquals(
                SettingsDataMutationStatusV1.NO_CHANGE,
                core.settingsData.saveProfileDisplayName("小明").status
            )

            assertEquals(
                SettingsDataMutationStatusV1.INVALID,
                core.settingsData.saveProfileDisplayName("bad\nname").status
            )
            assertEquals(
                SettingsDataMutationStatusV1.INVALID,
                core.settingsData.saveProfileDisplayName("x".repeat(81)).status
            )

            val prompt = core.settingsData.markSystemCalendarImportPromptShown()
            assertEquals(SettingsDataMutationStatusV1.UPDATED, prompt.status)
            assertEquals(2L, prompt.featureRevision)
            assertEquals(
                SettingsDataMutationStatusV1.NO_CHANGE,
                core.settingsData.markSystemCalendarImportPromptShown().status
            )
            assertEquals(
                listOf(
                    SettingsDataChangeTypeV1.PROFILE_DISPLAY_NAME_CHANGED,
                    SettingsDataChangeTypeV1.SYSTEM_CALENDAR_IMPORT_PROMPT_MARKED
                ),
                changes.map(SettingsDataChangeV1::type)
            )
            assertEquals(2L, core.settingsData.querySettings().featureRevision)
            assertEquals("小明", core.settingsData.querySettings().profileDisplayName)
            assertTrue(core.settingsData.querySettings().systemCalendarImportPromptShown)
        } finally {
            subscription.close()
        }
    }

    @Test fun contextlessAdapterReportsUnavailableAndContractHasNoAndroidOrSecretTypes() {
        val unavailable = AndroidSettingsDataFeatureApiV1(null)
        assertFalse(unavailable.querySettings().available)
        assertEquals(
            SettingsDataMutationStatusV1.UNAVAILABLE,
            unavailable.saveProfileDisplayName("name").status
        )
        assertEquals(
            SettingsDataMutationStatusV1.UNAVAILABLE,
            unavailable.markSystemCalendarImportPromptShown().status
        )

        val signatures = SettingsDataFeatureApiV1::class.java.declaredMethods
            .joinToString(" ") { it.genericReturnType.typeName + it.genericParameterTypes.joinToString() }
        assertFalse(signatures.contains("android.content.Context"))
        assertFalse(signatures.contains("SharedPreferences"))
        assertFalse(signatures.contains("apiKey", ignoreCase = true))
        assertFalse(signatures.contains("AiSettingsStore"))
    }

    private companion object {
        const val PROFILE_PREFS = "powercal-profile"
        const val PROFILE_NAME_KEY = "displayName"
        const val ONBOARDING_PREFS = "powercal-onboarding"
        const val CALENDAR_IMPORT_PROMPT_KEY = "system-calendar-import-asked"
    }
}
