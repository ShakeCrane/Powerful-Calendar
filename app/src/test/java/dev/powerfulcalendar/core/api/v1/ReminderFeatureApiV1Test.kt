package dev.powerfulcalendar.core.api.v1

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.powerfulcalendar.core.DraftForm
import dev.powerfulcalendar.core.PowerStore
import dev.powerfulcalendar.core.ReminderMethod
import dev.powerfulcalendar.core.TemporalKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderFeatureApiV1Test {
    private lateinit var context: Context
    private lateinit var store: PowerStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        store = PowerStore(context, "reminder-feature-api-${UUID.randomUUID()}.db")
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val databaseName = store.databaseName
        store.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun hostApiReconcilesDurableIntentAndReturnsValueOnlyStatus() {
        val eventSeq = createExactAlarm()
        val core = PowerCalCoreFeatureApiV1(store, reminderContext = context)
        val before = core.reminders.queryEvent(eventSeq)

        assertEquals(1, core.reminders.apiVersion)
        assertEquals(1L, before.currentRevision)
        assertEquals(ReminderScheduleStateV1.PENDING, before.schedule?.state)
        assertEquals(ReminderTriggerRequirementV1.EXACT, before.schedule?.requirement)
        assertTrue(core.reminders.queryPermissions().exactAlarm.required == true)

        val commitsBefore = store.commitCount()
        val receipt = core.reminders.reconcile()
        val after = core.reminders.queryEvent(eventSeq)

        assertEquals(ReminderReconcileModeV1.PENDING, receipt.mode)
        assertEquals(ReminderReconcileStatusV1.COMPLETE, receipt.status)
        assertEquals(1, receipt.attempted)
        assertEquals(0, receipt.failed)
        assertEquals(ReminderScheduleStateV1.SCHEDULED, after.schedule?.state)
        assertEquals(after.currentRevision, after.schedule?.revision)
        assertEquals(commitsBefore, store.commitCount())
        assertEquals(1, Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)!!)
            .scheduledAlarms.size)
    }

    @Test
    fun deniedExactPermissionStaysBlockedAndPermissionGrantRetriesSameIntent() {
        val eventSeq = createExactAlarm()
        val core = PowerCalCoreFeatureApiV1(store, reminderContext = context)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val blocked = core.reminders.reconcile()
        val blockedStatus = core.reminders.queryEvent(eventSeq)
        assertEquals(ReminderReconcileStatusV1.BLOCKED, blocked.status)
        assertEquals(1, blocked.blockedCountCapped)
        assertEquals(ReminderScheduleStateV1.BLOCKED, blockedStatus.schedule?.state)
        assertTrue(blockedStatus.schedule?.blockedHint.orEmpty().contains("精确闹钟权限"))
        assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)!!)
            .scheduledAlarms.isEmpty())

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val recovered = core.reminders.reconcile(ReminderReconcileModeV1.PENDING)

        assertEquals(ReminderReconcileStatusV1.COMPLETE, recovered.status)
        assertEquals(ReminderScheduleStateV1.SCHEDULED,
            core.reminders.queryEvent(eventSeq).schedule?.state)
        assertEquals(1, Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)!!)
            .scheduledAlarms.size)
    }

    @Test
    fun missingAndroidHostContextIsReportedWithoutChangingDurableSchedule() {
        val eventSeq = createExactAlarm()
        val core = PowerCalCoreFeatureApiV1(store)

        val permissions = core.reminders.queryPermissions()
        val receipt = core.reminders.reconcile(ReminderReconcileModeV1.EXHAUSTIVE)

        assertNull(permissions.exactAlarm.required)
        assertNull(permissions.exactAlarm.allowed)
        assertEquals(ReminderReconcileModeV1.EXHAUSTIVE, receipt.mode)
        assertEquals(ReminderReconcileStatusV1.UNAVAILABLE, receipt.status)
        assertEquals(1, receipt.pendingCountCapped)
        assertEquals(ReminderScheduleStateV1.PENDING,
            core.reminders.queryEvent(eventSeq).schedule?.state)
    }

    @Test
    fun eventStatusRetainsCurrentRevisionWhenThereIsNoReminderIntent() {
        val card = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "No reminder",
                startText = DraftForm.formatInstant(
                    System.currentTimeMillis() + 60 * 60_000L,
                    ZoneId.of("UTC")
                ),
                reminderMinutes = -1,
                zoneId = "UTC"
            )
        )
        assertTrue(store.commit(card.draftId) is PowerStore.CommitOutcome.Committed)

        val status = PowerCalCoreFeatureApiV1(store, reminderContext = context)
            .reminders.queryEvent(card.eventSeq)

        assertEquals(card.eventSeq, status.eventSeq)
        assertEquals(1L, status.currentRevision)
        assertNull(status.schedule)
        assertNull(status.lastExecutionResult)
    }

    private fun createExactAlarm(): Long {
        val draft = store.newDraft(
            DraftForm(
                kind = TemporalKind.ALARM,
                title = "Feature API reminder",
                startText = DraftForm.formatInstant(
                    System.currentTimeMillis() + 60 * 60_000L,
                    ZoneId.of("UTC")
                ),
                reminderMinutes = 0,
                reminderMethod = ReminderMethod.ALARM,
                zoneId = "UTC"
            )
        )
        assertTrue(store.commit(draft.draftId) is PowerStore.CommitOutcome.Committed)
        return draft.eventSeq
    }
}
