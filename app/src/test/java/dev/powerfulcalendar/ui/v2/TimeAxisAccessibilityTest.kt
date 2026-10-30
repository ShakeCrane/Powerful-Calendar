package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.ui.v2.nativeui.timeAxisResetSemantics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeAxisAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rulerExposesAnAccessibleResetAction() {
        val resetCount = AtomicInteger()
        compose.setContent {
            Box(Modifier.size(25.dp, 200.dp).timeAxisResetSemantics { resetCount.incrementAndGet() })
        }

        compose.onNodeWithContentDescription("双击当前时间刻度尺返回24小时视图")
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> assertTrue(click()) }

        assertEquals(1, resetCount.get())
    }
}
