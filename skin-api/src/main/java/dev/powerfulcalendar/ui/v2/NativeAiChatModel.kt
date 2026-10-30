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

enum class NativeAiProposalGroup { CREATE, EXISTING }

/** Display-only fields for one AI proposal; its original position keys Host callbacks. */
data class NativeAiProposalRow(
    val position: Int,
    val group: NativeAiProposalGroup,
    val title: String,
    val caption: String,
    val startText: String,
    val endText: String,
    val blockedReason: String?,
    val publishable: Boolean
)

/** No parser reply, AI object, DraftForm, or persistence identity crosses into Native rendering. */
data class NativeAiProposalReviewModel(
    val rows: List<NativeAiProposalRow>,
    val warnings: List<String>
)
