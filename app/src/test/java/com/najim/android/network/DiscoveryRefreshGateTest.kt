package com.najim.android.network

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class DiscoveryRefreshGateTest {
    @Test fun callbackBurstCoalescesUntilConsumed() {
        val gate = DiscoveryRefreshGate()
        val lifetime = gate.start()
        assertTrue(gate.request(lifetime))
        repeat(50) { assertFalse(gate.request(lifetime)) }
        assertTrue(gate.consume(lifetime))
        assertFalse(gate.consume(lifetime))
        assertTrue(gate.request(lifetime))
        assertTrue(gate.consume(lifetime))
    }

    @Test fun stoppedListenerCannotScheduleOrDrain() {
        val gate = DiscoveryRefreshGate()
        val lifetime = gate.start()
        assertTrue(gate.request(lifetime))
        gate.stop()
        assertFalse(gate.request(lifetime))
        assertFalse(gate.consume(lifetime))
    }

    @Test fun oldListenerCannotConsumeOrReplaceNewPendingRefresh() {
        val gate = DiscoveryRefreshGate()
        val old = gate.start()
        assertTrue(gate.request(old))
        gate.stop()
        val current = gate.start()
        assertNotSame(old, current)
        assertTrue(gate.request(current))
        assertFalse(gate.request(old))
        assertFalse(gate.consume(old))
        assertFalse(gate.request(current))
        assertTrue(gate.consume(current))
    }

    @Test fun callbackDuringDrainCanScheduleOneFollowUp() {
        val gate = DiscoveryRefreshGate()
        val lifetime = gate.start()
        assertTrue(gate.request(lifetime))
        assertTrue(gate.consume(lifetime))
        assertTrue(gate.request(lifetime))
        assertFalse(gate.request(lifetime))
        assertTrue(gate.consume(lifetime))
    }

    @Test fun repeatedStopDoesNotPoisonNextLifetime() {
        val gate = DiscoveryRefreshGate()
        gate.stop()
        val old = gate.start()
        gate.stop()
        gate.stop()
        val next = gate.start()
        assertFalse(gate.request(old))
        assertTrue(gate.request(next))
        assertTrue(gate.consume(next))
    }

    @Test fun concurrentCallbackAdmissionIsBounded() {
        val gate = DiscoveryRefreshGate()
        val lifetime = gate.start()
        val ready = CountDownLatch(8)
        val go = CountDownLatch(1)
        val admitted = AtomicInteger()
        val threads = List(8) {
            Thread {
                ready.countDown()
                go.await()
                if (gate.request(lifetime)) admitted.incrementAndGet()
            }.apply { isDaemon = true; start() }
        }
        try {
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            go.countDown()
        }
        threads.forEach { it.join(5000); assertFalse(it.isAlive) }
        assertEquals(1, admitted.get())
        assertTrue(gate.consume(lifetime))
    }
}
