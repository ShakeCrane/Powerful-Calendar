package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.geometry.Rect

/** A single already-measured, already-clipped title in common screen coordinates. */
data class TimelineTitleCandidate(
    val stableId: String,
    val eventSeq: Long,
    /** Physically independent strip (normally one side/lane). */
    val regionId: String,
    val screenRect: Rect
)

data class TimelineTitleDecision(
    val candidate: TimelineTitleCandidate,
    val visible: Boolean,
    /** The visible lead that was checked; hidden titles never become a lead. */
    val comparedTo: String?
)

data class TimelineTitleFoldResult(val decisions: List<TimelineTitleDecision>) {
    val visibleIds: Set<String> get() = decisions.asSequence()
        .filter { it.visible }.mapTo(LinkedHashSet()) { it.candidate.stableId }

    val hiddenIds: Set<String> get() = decisions.asSequence()
        .filterNot { it.visible }.mapTo(LinkedHashSet()) { it.candidate.stableId }
}

/**
 * Deterministic greedy folding over actual rendered text rectangles.
 *
 * Each region is an independent horizontal title strip. Rectangles are still compared in screen
 * coordinates, so clips and either wing's horizontal scroll are reflected in the decision.
 * Merely touching edges do not collide. A hidden title never replaces or blocks its visible lead.
 */
object GreedyTitleFolding {
    fun fold(candidates: List<TimelineTitleCandidate>): TimelineTitleFoldResult {
        require(candidates.map { it.stableId }.distinct().size == candidates.size) {
            "Timeline title candidate IDs must be unique"
        }
        candidates.forEach { candidate ->
            val rect = candidate.screenRect
            require(rect.left.isFinite() && rect.top.isFinite() &&
                rect.right.isFinite() && rect.bottom.isFinite()) {
                "Timeline title rectangles must use finite screen coordinates"
            }
            require(rect.width > 0f && rect.height > 0f) {
                "Timeline title rectangles must have visible area"
            }
        }

        val decisions = ArrayList<TimelineTitleDecision>(candidates.size)
        candidates.groupBy { it.regionId }.toSortedMap().forEach { (_, region) ->
            val ordered = region.sortedWith(
                compareBy<TimelineTitleCandidate>({ it.screenRect.top }, { it.eventSeq }, { it.stableId })
            )
            var lead: TimelineTitleCandidate? = null
            ordered.forEach { candidate ->
                val currentLead = lead
                if (currentLead == null) {
                    decisions += TimelineTitleDecision(candidate, visible = true, comparedTo = null)
                    lead = candidate
                } else if (overlaps(currentLead.screenRect, candidate.screenRect)) {
                    decisions += TimelineTitleDecision(
                        candidate, visible = false, comparedTo = currentLead.stableId
                    )
                } else {
                    decisions += TimelineTitleDecision(
                        candidate, visible = true, comparedTo = currentLead.stableId
                    )
                    lead = candidate
                }
            }
        }
        return TimelineTitleFoldResult(decisions)
    }

    private fun overlaps(first: Rect, second: Rect): Boolean =
        first.left < second.right && second.left < first.right &&
            first.top < second.bottom && second.top < first.bottom
}
