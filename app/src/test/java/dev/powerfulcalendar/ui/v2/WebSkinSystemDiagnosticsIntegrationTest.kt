package dev.powerfulcalendar.ui.v2

import android.Manifest
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.Recurrence
import dev.powerfulcalendar.core.RecurrenceFreq
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.core.TemporalRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinSystemDiagnosticsIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private val backing by lazy { ApplicationProvider.getApplicationContext<android.app.Application>() }
    private lateinit var store: PowerStore
    private lateinit var databaseName: String

    private fun openSettingsHost() {
        databaseName = "system-diagnostics-${UUID.randomUUID().toString().take(8)}.db"
        store = PowerStore(ui.activity, databaseName)
        store.readableDatabase
        seedFormalRecords()
        ui.activity.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        PowerCalSkinRegistry.reset()
        shadowOf(backing).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        ui.activity.setContent {
            CompositionLocalProvider(LocalContext provides ui.activity) {
                PowerCalTheme { PowerCalApp(store) }
            }
        }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.owner != null
        }
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("settings")) }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" &&
                WebSkinBridgeRuntime.state().optJSONObject("systemDiagnostics")
                    ?.optInt("formalCount") == 5
        }
    }

    private fun seedFormalRecords() {
        val zone = ZoneId.of("UTC")
        val baseDate = LocalDate.now(zone).minusDays(2)
        repeat(4) { index ->
            val start = baseDate.atTime(LocalTime.of(8 + index, 0))
                .atZone(zone).toInstant().toEpochMilli()
            val draft = store.newDraft(
                DraftForm(
                    kind = TemporalKind.ALARM,
                    title = "private recurrence title $index",
                    startText = DraftForm.formatInstant(start, zone),
                    recurrence = Recurrence(RecurrenceFreq.DAILY),
                    zoneId = zone.id
                )
            )
            assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        }
        val ordinaryStart = baseDate.plusDays(1).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val ordinary = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "private ordinary title",
                startText = DraftForm.formatInstant(ordinaryStart, zone),
                zoneId = zone.id
            )
        )
        assertTrue(store.commit(ordinary.draftId) is PowerStore.CommitOutcome.Committed)
    }

    @Test
    fun settingsProjectsNativeDiagnosticsAndTimelineReceivesNoSystemDiagnostics() {
        openSettingsHost()
        val commits = store.commitCount()
        val identities = store.identityCount()
        val nextSequence = store.nextSequencePreview()
        val records = store.all()

        val diagnostic = WebSkinBridgeRuntime.state().getJSONObject("systemDiagnostics")
        assertEquals(5, diagnostic.getInt("formalCount"))
        assertEquals(TemporalRecord.formatSeq(nextSequence), diagnostic.getString("nextSequence"))
        assertEquals(identities.toString(), diagnostic.getString("identityCount"))
        assertEquals(PowerStore.VERSION, diagnostic.getInt("databaseVersion"))
        assertEquals(PowerStore.DATABASE_NAME, diagnostic.getString("databaseName"))
        assertTrue(diagnostic.has("blurEnabled"))
        assertTrue(diagnostic.has("reduceMotion"))
        assertFalse(diagnostic.toString().contains("private recurrence title"))
        assertFalse(diagnostic.toString().contains("private ordinary title"))
        val recurrences = diagnostic.getJSONArray("recurrences")
        assertEquals(3, recurrences.length())
        assertEquals("重复 #1", recurrences.getJSONObject(0).getString("label"))
        assertTrue(recurrences.getJSONObject(0).getString("value").startsWith("下一次 "))

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("timeline")) }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.state().optString("page") == "TIMELINE"
        }
        assertEquals(0, WebSkinBridgeRuntime.state().getJSONObject("systemDiagnostics").length())
        assertEquals(commits, store.commitCount())
        assertEquals(identities, store.identityCount())
        assertEquals(nextSequence, store.nextSequencePreview())
        assertEquals(records, store.all())
    }

    @After
    fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        if (::store.isInitialized) {
            store.close()
            ui.activity.deleteDatabase(databaseName)
        }
        if (::databaseName.isInitialized) {
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-ai")
                .forEach { backing.deleteSharedPreferences(it) }
        }
        PowerCalSkinRegistry.reset()
    }
}
