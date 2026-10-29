package dev.powerfulcalendar.ui.v2

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.TemporalKind
import dev.powerfulcalendar.reminders.ReminderScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebSkinExactAlarmSettingsIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private val backing by lazy { ApplicationProvider.getApplicationContext<android.app.Application>() }
    private lateinit var context: IsolatedContext
    private lateinit var store: PowerStore
    private lateinit var namespace: String
    private var eventSeq: Long = 0

    @Test fun settingsOpensPackageExactAlarmPageAndResumeReconcilesBlockedReminder() {
        namespace = "exact-settings-${UUID.randomUUID().toString().take(10)}"
        context = IsolatedContext(ui.activity, backing, namespace)
        store = PowerStore(context, "events.db")
        store.readableDatabase
        ui.activity.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("system-calendar-import-asked", true).commit()
        PowerCalSkinRegistry.reset()
        shadowOf(backing).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2035-01-01T09:00:00Z").toEpochMilli()
        val draft = store.newDraft(DraftForm(
            kind = TemporalKind.ALARM,
            title = "Exact settings integration reminder",
            startText = DraftForm.formatInstant(start, zone),
            reminderMinutes = 5,
            reminderMethod = ReminderMethod.ALARM,
            zoneId = zone.id
        ))
        eventSeq = draft.eventSeq
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        val committedRecord = store.get(eventSeq)!!
        ReminderScheduler.reconcile(context, store)
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, store.intentFor(eventSeq)!!.desired)

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

        val deniedStatus = WebSkinBridgeRuntime.state().getJSONObject("exactAlarmPermission")
        assertTrue(deniedStatus.getBoolean("required"))
        assertFalse(deniedStatus.getBoolean("allowed"))
        val commitCount = store.commitCount()
        val identityCount = store.identityCount()
        val nextSequence = store.nextSequencePreview()

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.OpenExactAlarmSettings) }
        val launched = shadowOf(ui.activity).nextStartedActivity
        assertNotNull(launched)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, launched!!.action)
        assertEquals(Uri.parse("package:${context.packageName}"), launched.data)
        assertTrue(launched.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, store.intentFor(eventSeq)!!.desired)
        assertEquals(committedRecord, store.get(eventSeq))
        assertEquals(commitCount, store.commitCount())
        assertEquals(identityCount, store.identityCount())
        assertEquals(nextSequence, store.nextSequencePreview())

        ui.runOnIdle {
            WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("timeline"))
            WebSkinBridgeRuntime.onAction(WebSkinAction.OpenExactAlarmSettings)
        }
        ui.waitForIdle()
        assertEquals(null, shadowOf(ui.activity).nextStartedActivity)

        ui.runOnIdle { WebSkinBridgeRuntime.onAction(WebSkinAction.Routed("settings")) }
        val lifecycle = ui.activity.lifecycle as LifecycleRegistry
        ui.runOnIdle {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        ui.waitForIdle()
        assertEquals(PowerStore.DesiredSchedule.BLOCKED, store.intentFor(eventSeq)!!.desired)
        assertFalse(WebSkinBridgeRuntime.state().getJSONObject("exactAlarmPermission").getBoolean("allowed"))

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ui.runOnIdle {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        ui.waitUntil(20_000) {
            store.intentFor(eventSeq)?.desired == PowerStore.DesiredSchedule.SCHEDULED
        }
        val grantedStatus = WebSkinBridgeRuntime.state().getJSONObject("exactAlarmPermission")
        assertTrue(grantedStatus.getBoolean("required"))
        assertTrue(grantedStatus.getBoolean("allowed"))
        assertEquals(committedRecord, store.get(eventSeq))
        assertEquals(commitCount, store.commitCount())
        assertEquals(identityCount, store.identityCount())
        assertEquals(nextSequence, store.nextSequencePreview())
    }

    @After fun tearDown() {
        ui.activity.runOnUiThread { ui.activity.setContent {} }
        ui.waitForIdle()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        if (::store.isInitialized) {
            if (eventSeq > 0) ReminderScheduler.cancel(context, eventSeq)
            store.close()
            if (::context.isInitialized) context.deleteDatabase("events.db")
        }
        if (::namespace.isInitialized) {
            ui.activity.getSharedPreferences("powercal-onboarding", Context.MODE_PRIVATE)
                .edit().clear().commit()
            listOf("powercal-onboarding", "powercal-skin-choice", "powercal-ui", "powercal-ai")
                .forEach { backing.deleteSharedPreferences("$namespace-$it") }
            File(backing.cacheDir, namespace).deleteRecursively()
        }
        PowerCalSkinRegistry.reset()
    }

    private class IsolatedContext(
        target: Context,
        private val backing: Context,
        private val namespace: String
    ) : ContextWrapper(target) {
        private val root = File(backing.cacheDir, namespace).apply {
            check(mkdirs() || isDirectory)
        }

        private fun databaseAlias(name: String) = "$namespace-$name"
        private fun databasePath(name: String): File = backing.getDatabasePath(databaseAlias(name)).also {
            check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
        }

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            backing.getSharedPreferences("$namespace-$name", mode)
        override fun getFilesDir(): File = root
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getDir(name: String, mode: Int): File = File(root, "dirs/$name").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = databasePath(name)
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory)
        }
        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            databasePath(name)
            return backing.openOrCreateDatabase(databaseAlias(name), mode, factory, errorHandler)
        }
        override fun deleteDatabase(name: String): Boolean =
            backing.deleteDatabase(databaseAlias(name))
    }
}
