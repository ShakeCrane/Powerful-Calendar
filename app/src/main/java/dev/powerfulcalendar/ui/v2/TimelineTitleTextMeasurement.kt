package dev.powerfulcalendar.ui.v2

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import java.text.BreakIterator
import java.util.Locale

internal data class TimelineTitleTextMeasurement(
    val layout: TextLayoutResult,
    val drawWidthPx: Int,
    val fontSizeSp: Float,
    val displayText: String
)

/** Measures one-line titles at the SAME design font size on both wings and all event kinds. */
internal fun measureTimelineTitleText(
    textMeasurer: TextMeasurer,
    text: String,
    baseStyle: TextStyle,
    availableWidthPx: Int
): TimelineTitleTextMeasurement? {
    if (availableWidthPx <= 0) return null

    val singleLineText = text.replace('\n', ' ').replace('\r', ' ')
    // Never change the font size based on title length: both wings and every kind share it.
    val fontSizeSp = baseStyle.fontSize.value
    val titleStyle = baseStyle
    fun measureSingleLine(candidate: String) = textMeasurer.measure(
        text = candidate,
        style = titleStyle,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints()
    )

    val fullLayout = measureSingleLine(singleLineText)
    val displayText: String
    val layout: TextLayoutResult
    if (fullLayout.size.width <= availableWidthPx) {
        displayText = singleLineText
        layout = fullLayout
    } else {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(singleLineText)
        val boundaries = mutableListOf(0)
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            if (boundary > 0) boundaries += boundary
            boundary = iterator.next()
        }

        var low = 0
        var high = boundaries.lastIndex
        var bestText: String? = null
        var bestLayout: TextLayoutResult? = null
        while (low <= high) {
            val middle = (low + high) ushr 1
            val candidate = singleLineText.substring(0, boundaries[middle]) + ELLIPSIS
            val candidateLayout = measureSingleLine(candidate)
            if (candidateLayout.size.width <= availableWidthPx) {
                bestText = candidate
                bestLayout = candidateLayout
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        displayText = bestText ?: return null
        layout = bestLayout ?: return null
    }

    val drawWidthPx = layout.size.width
    if (drawWidthPx <= 0 || layout.size.height <= 0) return null
    return TimelineTitleTextMeasurement(layout, drawWidthPx, fontSizeSp, displayText)
}

private const val ELLIPSIS = "…"
