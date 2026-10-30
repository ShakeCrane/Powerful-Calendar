package dev.powerfulcalendar.ui.v2

import android.content.Context
import dev.powerfulcalendar.ui.v2.nativeui.NativePickerSkin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Host adapter that builds a zone-resolved request and routes it to the selected picker renderer. */
internal object SkinPickers {
    fun pickDate(context: Context, baseMillis: Long, zone: ZoneId, onPick: (LocalDate) -> Unit) {
        val base = Instant.ofEpochMilli(baseMillis).atZone(zone)
        val request = PickerVisualContext(
            kind = PickerKind.DATE,
            initialDate = base.toLocalDate(),
            initialHour = base.hour,
            initialMinute = base.minute,
            zone = zone,
            androidContext = context,
            onDate = onPick,
            onTime = { _, _ -> }
        )
        dispatch(request)
    }

    fun pickTime(context: Context, baseMillis: Long, zone: ZoneId, onPick: (Int, Int) -> Unit) {
        val base = Instant.ofEpochMilli(baseMillis).atZone(zone)
        val request = PickerVisualContext(
            kind = PickerKind.TIME,
            initialDate = base.toLocalDate(),
            initialHour = base.hour,
            initialMinute = base.minute,
            zone = zone,
            androidContext = context,
            onDate = {},
            onTime = onPick
        )
        dispatch(request)
    }

    private fun dispatch(request: PickerVisualContext) {
        if (RuntimeSkinSurfaces.enabled("picker")) {
            RuntimePickerRequest.show(request)
        } else {
            (PresentationSkinRegistry.picker ?: NativePickerSkin).Render(request)
        }
    }
}
