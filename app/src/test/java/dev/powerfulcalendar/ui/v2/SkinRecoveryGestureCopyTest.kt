package dev.powerfulcalendar.ui.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SkinRecoveryGestureCopyTest {
    @Test
    fun warningDescribesAlternatingLocationsWithoutClaimingFingerIdentity() {
        assertTrue(SKIN_RECOVERY_GESTURE_COPY.contains("两处位置交替"))
        assertTrue(SKIN_RECOVERY_GESTURE_COPY.contains("连续轻点 8 下"))
        assertFalse(SKIN_RECOVERY_GESTURE_COPY.contains("双指"))
        assertFalse(SKIN_RECOVERY_GESTURE_COPY.contains("每指"))
    }
}
