package dev.powerfulcalendar.ui.v2

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Looper
import dev.powerfulcalendar.ui.v2.nativeui.NativePickerSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativePickerModuleTest {
    private val date = LocalDate.of(2026, 10, 21)

    @Test
    fun nativeDatePickerUsesHostDateAndCallsBackOnlyOnConfirmation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var chosen: LocalDate? = null
        ShadowDialog.reset()

        NativePickerSkin.Render(
            PickerVisualContext(
                kind = PickerKind.DATE,
                initialDate = date,
                initialHour = 9,
                initialMinute = 37,
                zone = ZoneId.of("Asia/Shanghai"),
                androidContext = activity,
                onDate = { chosen = it },
                onTime = { _, _ -> error("date picker called time callback") }
            )
        )

        val dialog = ShadowDialog.getLatestDialog() as DatePickerDialog
        val shadow = Shadows.shadowOf(dialog)
        assertEquals(2026, shadow.year)
        assertEquals(9, shadow.monthOfYear)
        assertEquals(21, shadow.dayOfMonth)
        assertNull(chosen)

        dialog.datePicker.updateDate(2026, 10, 23)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(LocalDate.of(2026, 11, 23), chosen)
    }

    @Test
    fun nativeTimePickerUses24HourHostTimeAndCallsBackOnConfirmation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var chosen: Pair<Int, Int>? = null
        ShadowDialog.reset()

        NativePickerSkin.Render(
            PickerVisualContext(
                kind = PickerKind.TIME,
                initialDate = date,
                initialHour = 17,
                initialMinute = 42,
                zone = ZoneId.of("Asia/Shanghai"),
                androidContext = activity,
                onDate = { error("time picker called date callback") },
                onTime = { hour, minute -> chosen = hour to minute }
            )
        )

        val dialog = ShadowDialog.getLatestDialog() as TimePickerDialog
        val shadow = Shadows.shadowOf(dialog)
        assertEquals(17, shadow.hourOfDay)
        assertEquals(42, shadow.minute)
        assertEquals(true, shadow.is24HourView)
        assertNull(chosen)

        dialog.updateTime(6, 15)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        assertEquals(6 to 15, chosen)
    }
}
