package dev.powerfulcalendar.ui.v2

import android.content.Context
import dev.powerfulcalendar.ui.v2.nativeui.NativePickerSkin
import java.time.LocalDate
import java.time.ZoneId

/**
 * Host-owned request for one of the Android platform pickers.
 * Skin implementations may present it, but selected values flow back through Host-owned callbacks.
 */
enum class PickerKind { DATE, TIME }

class PickerVisualContext(
    val kind: PickerKind,
    val initialDate: LocalDate,
    val initialHour: Int,
    val initialMinute: Int,
    val zone: ZoneId,
    val androidContext: Context,
    val onDate: (LocalDate) -> Unit,
    val onTime: (Int, Int) -> Unit
) {
    /** Use the first-party platform picker while preserving Host-owned callbacks. */
    fun Native() = NativePickerSkin.Render(this)
}

interface PickerVisualRenderer {
    val id: String
    fun Render(context: PickerVisualContext)
}
