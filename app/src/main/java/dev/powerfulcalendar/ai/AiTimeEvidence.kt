package dev.powerfulcalendar.ai

import dev.powerfulcalendar.core.DraftForm

/**
 * Deterministic rescue for *unambiguous* absolute timestamps accidentally put in prose.
 * Never guesses year, month, weekday, daypart, duration or a cross-midnight end.
 */
internal object AiTimeEvidence {
    data class Range(val start: String, val end: String?)

    private val absolute = Regex("""(?<!\d)(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})(?:日)?[ T\s]+([01]?\d|2[0-3])[:：]([0-5]\d)(?!\d)""")
    private val bareRangeEnd = Regex("""^\s*(?:-|–|—|~|～|至|到)\s*([01]?\d|2[0-3])[:：]([0-5]\d)(?!\d)""")

    fun recover(description: String): Range? {
        if (description.contains("原定") || description.contains("原计划") ||
            description.contains("改到") || description.contains("改为")) return null
        val found = absolute.findAll(description.take(6000)).toList()
        if (found.isEmpty() || found.size > 2) return null
        fun stamp(match: MatchResult): String? {
            val y = match.groupValues[1].toInt()
            val m = match.groupValues[2].toInt()
            val d = match.groupValues[3].toInt()
            val h = match.groupValues[4].toInt()
            val min = match.groupValues[5].toInt()
            val rendered = "%04d-%02d-%02d %02d:%02d".format(java.util.Locale.US, y,m,d,h,min)
            return if (DraftForm.parseLocalDateTime(rendered) != null) rendered else null
        }
        val start = stamp(found[0]) ?: return null
        val end = if (found.size == 2) stamp(found[1]) else {
            val tail = description.substring(found[0].range.last + 1)
            val match = bareRangeEnd.find(tail)
            match?.let {
                val ending = start.substring(0, 11) +
                    "%02d:%02d".format(java.util.Locale.US,
                        it.groupValues[1].toInt(), it.groupValues[2].toInt())
                if (DraftForm.parseLocalDateTime(ending) != null) ending else null
            }
        }
        if (end != null && end <= start) return null // could cross midnight, ask explicitly
        return Range(start, end)
    }
}
