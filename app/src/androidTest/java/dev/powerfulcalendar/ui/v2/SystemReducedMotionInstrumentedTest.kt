package dev.powerfulcalendar.ui.v2

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemReducedMotionInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemZeroAnimatorScaleDisablesGlassMotionAndBlur() {
        val context = compose.activity
        assertEquals(
            "AVD must be configured with reduced motion for this isolated run",
            0f,
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ),
            0f
        )

        var observedReduceMotion = false
        var observedBlur = true
        var observedAnimationValue = -1f
        compose.setContent {
            val systemEffects = rememberSystemGlassEffects()
            PowerCalTheme(effects = systemEffects) {
                val effects = LocalGlassEffects.current
                var target by remember { mutableFloatStateOf(0f) }
                val animationValue by animateFloatAsState(
                    targetValue = target,
                    animationSpec = glassMotion(),
                    label = "reducedMotionProbe"
                )
                LaunchedEffect(Unit) { target = 1f }
                SideEffect {
                    observedReduceMotion = effects.reduceMotion
                    observedBlur = effects.blur
                    observedAnimationValue = animationValue
                }
                Text(
                    "reduceMotion=${effects.reduceMotion}; blur=${effects.blur}; " +
                        "target=$target; animation=$animationValue"
                )
            }
        }

        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        println(
            "SYSTEM_REDUCED_MOTION reduce=$observedReduceMotion blur=$observedBlur " +
                "animation=$observedAnimationValue"
        )
        compose.onNode(hasText("reduceMotion=true; blur=false", substring = true)).assertExists()
        compose.runOnIdle {
            assertTrue("system animation scale zero must be recognized", observedReduceMotion)
            assertFalse("blur must be disabled while reduced motion is active", observedBlur)
            assertEquals("glass motion must snap to its target", 1f, observedAnimationValue, 0f)
        }
    }
}
