package dev.powerfulcalendar.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderPersistenceTest {
    private lateinit var store: PowerStore
    private lateinit var context: Context
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = PowerStore(context, "policy-test-" + UUID.randomUUID() + ".db")
    }
    @After fun teardown() {
        val name = store.databaseName
        store.close()
        context.deleteDatabase(name)
    }

    @Test fun midnightDeadlineNotificationHasIntentButNoFakeDeadlineMillis() {
        val date = LocalDate.now(zone).plusDays(3)
        val form = DraftForm.fresh(TemporalKind.DEADLINE, date, zone)
            .copy(title = "交论文")
        val card = store.newDraft(form)
        val result = store.commit(card.draftId, actor = "test")
        assertTrue(result is PowerStore.CommitOutcome.Committed)
        val record = store.get(card.eventSeq)!!
        assertEquals(ReminderMethod.MESSAGE, record.reminderMethod)
        assertEquals(ReminderTiming.DAY_START, record.reminderTiming)
        assertNull(record.deadlineMillis)
        assertEquals(date.toString(), record.deadlineDate)
        val intent = store.intentFor(card.eventSeq)!!
        assertEquals(date.atStartOfDay(zone).toInstant().toEpochMilli(), intent.instantMillis)
        assertEquals(PowerStore.TriggerRequirement.INEXACT, intent.requirement)
    }

    @Test fun activityCanChooseExactAlarmOrMessageIndependentlyOfKind() {
        val date = LocalDate.now(zone).plusDays(3)
        val card = store.newDraft(DraftForm.fresh(TemporalKind.ACTIVITY, date, zone)
            .copy(title = "讨论", reminderMethod = ReminderMethod.ALARM,
                reminderMinutes = 30))
        val result = store.commit(card.draftId, actor = "test")
        assertTrue(result is PowerStore.CommitOutcome.Committed)
        assertEquals(PowerStore.TriggerRequirement.EXACT, store.intentFor(card.eventSeq)?.requirement)
        val record = store.get(card.eventSeq)!!
        assertEquals(ReminderMethod.ALARM, record.reminderMethod)
        assertEquals(30, record.reminderMinutes)
    }
}
