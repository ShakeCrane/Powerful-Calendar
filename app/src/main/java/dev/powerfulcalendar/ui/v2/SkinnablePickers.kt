package dev.powerfulcalendar.ui.v2

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Covers the two legacy Android platform pickers which aren't Compose AlertDialog instances.
 * Skin code may display any trusted Android/Compose dialog; host owns the selected date callback.
 * Skin implementations must invoke onDate or onTime only after explicit user confirmation.
 */
internal enum class PickerKind { DATE, TIME }

internal class PickerVisualContext internal constructor(
    val kind: PickerKind,
    val initialDate: LocalDate,
    val initialHour: Int,
    val initialMinute: Int,
    val zone: ZoneId,
    val androidContext: Context,
    val onDate: (LocalDate) -> Unit,
    val onTime: (Int, Int) -> Unit,
    private val fallback: () -> Unit
) {
    fun Native() = fallback()
}

internal interface PickerVisualRenderer {
    val id: String
    fun Render(context: PickerVisualContext)
}

internal object SkinPickers {
    fun pickDate(context: Context, baseMillis: Long, zone: ZoneId, onPick: (LocalDate) -> Unit) {
        val base = Instant.ofEpochMilli(baseMillis).atZone(zone)
        val date = base.toLocalDate()
        val native = {
            DatePickerDialog(context, { _, year, month, day ->
                onPick(LocalDate.of(year, month + 1, day))
            }, base.year, base.monthValue - 1, base.dayOfMonth).show()
        }
        val renderer = PresentationSkinRegistry.picker
        val runtime = RuntimeSkinSurfaces.enabled("picker")
        if (!runtime && renderer == null) native()
        else {
            val request = PickerVisualContext(
            kind = PickerKind.DATE, initialDate = date, initialHour = base.hour,
            initialMinute = base.minute, zone = zone, androidContext = context,
            onDate = onPick, onTime = { _, _ -> }, fallback = native
            )
            if (runtime) RuntimePickerRequest.show(request) else renderer!!.Render(request)
        }
    }

    fun pickTime(context: Context, baseMillis: Long, zone: ZoneId, onPick: (Int, Int) -> Unit) {
        val base = Instant.ofEpochMilli(baseMillis).atZone(zone)
        val native = {
            TimePickerDialog(context, { _, hour, minute -> onPick(hour, minute) },
                base.hour, base.minute, true).show()
        }
        val renderer = PresentationSkinRegistry.picker
        val runtime = RuntimeSkinSurfaces.enabled("picker")
        if (!runtime && renderer == null) native()
        else {
            val request = PickerVisualContext(
            kind = PickerKind.TIME, initialDate = base.toLocalDate(),
            initialHour = base.hour, initialMinute = base.minute, zone = zone,
            androidContext = context, onDate = {}, onTime = onPick, fallback = native
            )
            if (runtime) RuntimePickerRequest.show(request) else renderer!!.Render(request)
        }
    }
}
