package dev.powerfulcalendar.ui.v2

/** Chat text needed by the Native renderer; provider and persistence metadata stay in the Host. */
data class NativeAiMessage(
    val mine: Boolean,
    val content: String,
    val proposalSource: String? = null
)

enum class NativeAiBatchMode {
    PUBLISH,
    DISCARD
}
