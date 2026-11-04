package dev.powerfulcalendar.ui.v2

/** Registers the first-party Compose renderers required by trusted Host and recovery surfaces. */
object ActiveVisualSkin {
    /** There is no product-selectable compiled profile; normal Skin activation is package based. */
    fun install() = NativeSkinHostRegistration.install()
}
