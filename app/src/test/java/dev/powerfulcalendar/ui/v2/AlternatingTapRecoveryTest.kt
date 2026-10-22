package dev.powerfulcalendar.ui.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternatingTapRecoveryTest {
    @Test fun eightAlternatingTapsRecoverExactlyOnce() {
        val recognizer = AlternatingTapRecovery(density = 2f)
        for (i in 0..6) {
            assertFalse(recognizer.completedTap(if (i % 2 == 0) 20f else 210f,
                200f, 100L + i * 170, 70))
        }
        assertTrue(recognizer.completedTap(210f, 200f, 100L + 7 * 170, 70))
        assertFalse(recognizer.completedTap(20f, 200f, 1450L, 70))
    }

    @Test fun repeatedTapsOnSameButtonNeverRecover() {
        val recognizer = AlternatingTapRecovery(density = 1f)
        repeat(30) {
            assertFalse(recognizer.completedTap(44f, 44f, 100L + 120L * it, 80))
        }
    }

    @Test fun spatiallyUnstableOrSlowSequenceCannotRecover() {
        val recognizer = AlternatingTapRecovery(density = 1f)
        val xs = floatArrayOf(20f, 200f, 20f, 300f, 20f, 200f, 20f, 200f)
        for (i in xs.indices) {
            assertFalse(recognizer.completedTap(xs[i], 20f, 100L + i * 120L, 60))
        }
        recognizer.reset()
        for (i in 0..7) {
            assertFalse(recognizer.completedTap(if (i % 2 == 0) 10f else 180f,
                20f, 100L + i * 410L, 70))
        }
    }

    @Test fun longPressCannotCountAsTap() {
        val recognizer = AlternatingTapRecovery(density = 1f)
        assertFalse(recognizer.completedTap(15f, 15f, 100, 600))
        for (i in 0..6) {
            assertFalse(recognizer.completedTap(if (i % 2 == 0) 20f else 200f,
                15f, 250L + i * 120, 50))
        }
    }

    @Test fun ordinaryScrollInterruptedSequenceCannotRecover() {
        val recognizer = AlternatingTapRecovery(density = 1f)
        repeat(4) {
            assertFalse(recognizer.completedTap(if (it % 2 == 0) 0f else 150f,
                0f, 100L + 140L * it, 65))
        }
        recognizer.reset()
        repeat(4) {
            assertFalse(recognizer.completedTap(if (it % 2 == 0) 0f else 150f,
                0f, 900L + 140L * it, 65))
        }
    }
}
