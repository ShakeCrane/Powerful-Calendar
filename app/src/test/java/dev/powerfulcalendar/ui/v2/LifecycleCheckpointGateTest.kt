package dev.powerfulcalendar.ui.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleCheckpointGateTest {
    @Test fun checkpointsExactlyOncePerStopCycleForLiveSession() {
        val gate = LifecycleCheckpointGate()
        val session = Any()
        assertTrue(gate.shouldCheckpoint(session))
        assertFalse(gate.shouldCheckpoint(session))
        gate.onStart()
        assertTrue(gate.shouldCheckpoint(session))
        assertFalse(gate.shouldCheckpoint(session))
    }
}
