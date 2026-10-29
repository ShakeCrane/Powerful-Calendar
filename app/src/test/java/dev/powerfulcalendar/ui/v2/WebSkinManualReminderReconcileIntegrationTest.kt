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
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.reminders.ReminderScheduler
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
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinManualReminderReconcileIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private val backing by lazy { ApplicationProvider.getApplicationContext<android.app.Application>() }
    private lateinit var store: PowerStore
    private lateinit var databaseName: String
    private var storeClosed = false

    private fun mountSettingsHost() {
        databaseName = "manual-reconcile-${UUID.randomUUID().toString().take(8)}.db"
        store = PowerStore(ui.activity, databaseName)
        store.readableDatabase
        ui.activity.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        PowerCalSkinRegistry.reset()
        shadowOf(backing).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

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
        ui.waitUntil(20_000) { WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" }
    }

    @Test
    fun manualReconcileIsSettingsOnlyAndReportsBlockedThenSuccessfulRecovery() {
        mountSettingsHost()
        val zone = ZoneId.of("UTC")
        val draft = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "Manual reconcile integration reminder",
                startText = DraftForm.formatInstant(System.currentTimeMillis() + 60 * 60_000L, zone),
                reminderMinutes = 0,
                reminderMethod = ReminderMethod.ALARM,
                zoneId = zone.id
            )
        )
        val eventSeq = draft.eventSeq
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        val record = store.get(eventSeq)!!
        val commits = store.commitCount()
        val identities = store.identityCount()
        val nextSequence = store.nextSequencePreview()
        ReminderScheduler.reconcile(ui.activity, store)
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, store.intentFor(eventSeq)!!.desired)

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("timeline")) }
        ui.waitUntil(20_000) { WebSkinBridgeRuntime.state().optString("page") == "TIMELINE" }
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.ReconcileReminders) }
        ui.waitForIdle()
        // Reconcile status is deliberately projected only on Settings. Outside that page the
        // state object has no status field, and the action must leave the persisted row alone.
        assertEquals("", WebSkinBridgeRuntime.state()
            .getJSONObject("reminderReconcile").optString("status"))
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, store.intentFor(eventSeq)!!.desired)

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("settings")) }
        ui.waitUntil(20_000) { WebSkinBridgeRuntime.state().optString("page") == "SETTINGS" }
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.ReconcileReminders) }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
                .optString("status") in setOf("BLOCKED", "ERROR", "SUCCESS")
        }
        val blockedResult = WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
        assertEquals("unexpected reconcile result: $blockedResult", "BLOCKED", blockedResult.getString("status"))
        assertTrue(blockedResult.getString("message").contains("1 项提醒仍受阻"))
        assertTrue(blockedResult.getString("message").contains("精确闹钟权限"))

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.ReconcileReminders) }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
                .optString("status") in setOf("BLOCKED", "ERROR", "SUCCESS")
        }
        val reconcileResult = WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
        assertEquals("unexpected reconcile result: $reconcileResult", "SUCCESS", reconcileResult.getString("status"))
        assertEquals(PowerStore.DesiredSchedule.SCHEDULED, store.intentFor(eventSeq)!!.desired)
        assertEquals(record, store.get(eventSeq))
        assertEquals(commits, store.commitCount())
        assertEquals(identities, store.identityCount())
        assertEquals(nextSequence, store.nextSequencePreview())
    }

    @Test
    fun manualReconcileReportsStoreFailureInsteadOfFalseSuccess() {
        mountSettingsHost()
        ui.waitForIdle()
        // Use this test's disposable database to inject a deterministic store read failure.
        // Closing SQLiteOpenHelper alone is not a failure: it simply reopens on the next query.
        store.writableDatabase.execSQL("DROP TABLE schedule_intents")

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.ReconcileReminders) }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
                .optString("status") == "ERROR"
        }
        val result = WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
        assertTrue(result.getString("message").contains("重建失败"))
        assertFalse(result.getString("message").contains("已按当前数据重建"))
    }

    @Test
    fun manualReconcileProcessesMoreThanLegacyDueAndCancelledBatchLimits() {
        mountSettingsHost()
        ui.waitForIdle()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val commitCount = store.commitCount()
        val identityCount = store.identityCount()
        val nextSequence = store.nextSequencePreview()
        val now = System.currentTimeMillis()
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            repeat(405) { index ->
                db.execSQL(
                    """INSERT INTO schedule_intents(
                        event_seq,trigger_id,revision,occurrence_key,instant_ms,requirement,
                        desired_state,android_hint,updated_ms
                    ) VALUES(?,?,?,?,?,?,?,?,?)""",
                    arrayOf(
                        100_000L + index, "primary", 1L, "orphan-$index",
                        now + (index + 60L) * 60_000L, "EXACT", "PENDING", null, now
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.ReconcileReminders) }
        ui.waitUntil(20_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WebSkinBridgeRuntime.state().getJSONObject("reminderReconcile")
                .optString("status") == "SUCCESS"
        }

        val remaining = store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM schedule_intents", null
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        assertEquals(0, remaining)
        assertEquals(commitCount, store.commitCount())
        assertEquals(identityCount, store.identityCount())
        assertEquals(nextSequence, store.nextSequencePreview())
    }

    @After
    fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        if (::store.isInitialized) {
            if (!storeClosed) store.close()
            if (::databaseName.isInitialized) ui.activity.deleteDatabase(databaseName)
        }
        if (::databaseName.isInitialized) {
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-ai")
                .forEach { backing.deleteSharedPreferences(it) }
        }
        PowerCalSkinRegistry.reset()
    }
}
