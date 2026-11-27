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
    @Test fun concurrentAdmissionAcceptsExactlyOneFrame() {
        val gate = FrameGate()
        val ready = java.util.concurrent.CountDownLatch(8)
        val go = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val results = List(8) {
                pool.submit(java.util.concurrent.Callable {
                    ready.countDown()
                    check(go.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    gate.tryStart(0)
                })
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
            go.countDown()
            assertEquals(1, results.count { it.get(5, java.util.concurrent.TimeUnit.SECONDS) })
        } finally {
            go.countDown()
            pool.shutdownNow()
        }
    }
    @Test fun mainStopIsVisibleToWorkerAndFinishCannotReopenGate() {
        val gate = FrameGate()
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            assertTrue(pool.submit(java.util.concurrent.Callable { gate.tryStart(0) })
                .get(5, java.util.concurrent.TimeUnit.SECONDS))
            gate.stop()
            assertFalse(pool.submit(java.util.concurrent.Callable {
                gate.finish()
                gate.mayPublish() || gate.tryStart(1000)
            }).get(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }
}
