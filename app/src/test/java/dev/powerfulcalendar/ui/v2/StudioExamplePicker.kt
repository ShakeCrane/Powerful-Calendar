package dev.powerfulcalendar.ui.v2

/**
 * Date/time picker API smoke example. The custom skin may replace this with a native
 * Dialog or a ComposeView-backed custom Android modal while keeping onDate/onTime explicit.
 * We preserve the platform picker for now to ensure parity with the existing time editor.
 */
internal class StudioExamplePicker : PickerVisualRenderer {
    override val id = "studio-example/picker"
    override fun Render(context: PickerVisualContext) {
        context.Native()
    }
}
