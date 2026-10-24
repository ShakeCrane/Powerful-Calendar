package dev.powerfulcalendar.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmContractGraceWindowTest {
    @Test
    fun graceBoundaryIsInclusiveAndOneMillisecondBeyondIsMissed() {
        val now = 1_800_000_000_000L
        val boundary = now - AlarmContract.GRACE_MILLIS

        assertFalse(AlarmContract.isOutsideGraceWindow(boundary, now))
        assertTrue(AlarmContract.isOutsideGraceWindow(boundary - 1L, now))
        assertFalse(AlarmContract.isOutsideGraceWindow(boundary + 1L, now))
    }
}
