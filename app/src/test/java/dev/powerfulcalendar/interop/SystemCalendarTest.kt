package dev.powerfulcalendar.interop

import android.provider.AlarmClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemCalendarTest {
    @Test
    fun showingSystemAlarmsOnlyCreatesThePlatformViewerIntent() {
        val intent = SystemCalendar.showSystemAlarms()

        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, intent.action)
        assertNull(intent.data)
        assertNull(intent.component)
        assertNull(intent.extras)
    }

    @Test
    fun settingSystemAlarmRetainsPlatformDefaultInteraction() {
        val intent = SystemCalendar.setSystemAlarm(8, 30, "核心边界测试")

        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(8, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals("核心边界测试", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }
}
