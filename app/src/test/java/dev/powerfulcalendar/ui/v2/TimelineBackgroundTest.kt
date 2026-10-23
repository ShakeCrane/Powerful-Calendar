package dev.powerfulcalendar.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TimelineBackgroundTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun timelineBackgroundHasNoHorizontalWashOrBand() {
        var actual: Brush? = null
        var expectedBackground: androidx.compose.ui.graphics.Color? = null
        ui.setContent {
            PowerCalTheme {
                actual = axisBackground()
                expectedBackground = MaterialTheme.colorScheme.background
            }
        }
        ui.waitForIdle()

        assertTrue("timeline background must not contain position-based color stops", actual is SolidColor)
        assertEquals(expectedBackground, (actual as SolidColor).value)
    }
}
