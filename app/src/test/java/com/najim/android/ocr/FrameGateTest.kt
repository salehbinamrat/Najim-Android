package com.najim.android.ocr

import org.junit.Assert.*
import org.junit.Test

class FrameGateTest {
    @Test fun firstFrameIsAcceptedAtZero() = assertTrue(FrameGate().tryStart(0))
    @Test fun onlyOneInFlightEvenAfterInterval() {
        val gate = FrameGate()
        assertTrue(gate.tryStart(0))
        assertFalse(gate.tryStart(1000))
        gate.finish()
        assertTrue(gate.tryStart(1000))
    }
    @Test fun completedFrameStillThrottled() {
        val gate = FrameGate()
        assertTrue(gate.tryStart(100))
        gate.finish()
        assertFalse(gate.tryStart(349))
        assertTrue(gate.tryStart(350))
    }
    @Test fun stopBlocksLateResultsAndFutureFrames() {
        val gate = FrameGate()
        gate.tryStart(0)
        gate.stop()
        assertFalse(gate.mayPublish())
        gate.finish()
        assertFalse(gate.tryStart(1000))
    }
    @Test fun newSessionHasNoOldState() {
        val gate = FrameGate()
        gate.stop()
        val next = FrameGate()
        assertTrue(next.tryStart(0))
        assertTrue(next.mayPublish())
    }
}
