package dev.powerfulcalendar.ui.v2.nativeui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import dev.powerfulcalendar.ui.v2.PickerKind
import dev.powerfulcalendar.ui.v2.PickerVisualContext
import dev.powerfulcalendar.ui.v2.PickerVisualRenderer
import java.time.LocalDate

/** First-party system picker renderer. The Host retains all business callbacks and time-zone rules. */
object NativePickerSkin : PickerVisualRenderer {
    override val id: String = "native/picker"

    override fun Render(context: PickerVisualContext) {
        when (context.kind) {
            PickerKind.DATE -> {
                val date = context.initialDate
                DatePickerDialog(
                    context.androidContext,
                    { _, year, month, day -> context.onDate(LocalDate.of(year, month + 1, day)) },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth
                ).show()
            }
            PickerKind.TIME -> TimePickerDialog(
                context.androidContext,
                { _, hour, minute -> context.onTime(hour, minute) },
                context.initialHour,
                context.initialMinute,
                true
            ).show()
        }
    }
}
