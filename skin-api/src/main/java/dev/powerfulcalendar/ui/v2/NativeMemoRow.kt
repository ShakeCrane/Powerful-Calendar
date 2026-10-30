package dev.powerfulcalendar.ui.v2

/**
 * Display-only Memo row crossing the Host/Native module boundary. `rowKey` is opaque to the
 * renderer; only the Host resolves it to an event and occurrence before performing an action.
 */
data class NativeMemoRow(
    val rowKey: String,
    val title: String,
    val completed: Boolean,
    val kindLabel: String,
    val dateLabel: String,
    val repeating: Boolean,
    val sequenceLabel: String
)
