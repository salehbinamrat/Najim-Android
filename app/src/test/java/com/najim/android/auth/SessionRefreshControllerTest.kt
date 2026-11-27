package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.*
import org.junit.Test

class SessionRefreshControllerTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private inner class Fixture {
        val work = ArrayDeque<Runnable>()
        val callbacks = ArrayDeque<() -> Unit>()
        val states = mutableListOf<SessionViewState>()
        var calls = 0
        var logouts = 0
        var mode: LoginProtocol? = null
        var approved = false
        var duringRead: (() -> Boolean) -> Unit = {}
        val controller = SessionRefreshController(Executor { work.addLast(it) },
            { callbacks.addLast(it) },
            { _, protocol, consent, cancelled ->
                calls++
                mode = protocol
                approved = consent
                duringRead(cancelled)
                SessionStatus.Active(PortalPage.AccountStatus("000123"), protocol.requiresConsent)
            }, { states.add(it) }, { _, account, _, _, _ ->
                assertEquals("000123", account)
                logouts++
                LogoutResult.LOGGED_OUT
            })
        fun finish() {
            work.removeFirst().run()
            callbacks.removeFirst().invoke()
        }
    }
    @Test fun noDuplicateRefresh() {
        val f = Fixture()
        assertTrue(f.controller.refresh(wifi, false, false))
        assertFalse(f.controller.refresh(wifi, false, false))
        assertTrue(f.controller.busy)
        f.finish()
        assertFalse(f.controller.busy)
        assertEquals(1, f.calls)
        assertEquals(LoginProtocol.HTTPS_PAP, f.mode)
        assertFalse((f.states.last() as SessionViewState.Snapshot).legacy)
    }
    @Test fun explicitLegacyConsentRequired() {
        val f = Fixture()
        assertFalse(f.controller.refresh(wifi, true, false))
        assertEquals(SessionStatus.ConsentRequired, (f.states.last() as SessionViewState.Snapshot).status)
        assertTrue(f.work.isEmpty())
        assertTrue(f.controller.refresh(wifi, true, true))
        f.finish()
        assertEquals(LoginProtocol.HTTP_PAP, f.mode)
        assertTrue(f.approved)
        assertTrue((f.states.last() as SessionViewState.Snapshot).legacy)
        assertFalse(f.controller.refresh(wifi, true, false))
    }
    @Test fun noWifiNeverStarts() {
        val f = Fixture()
        assertFalse(f.controller.refresh(DiscoveryState.NoWifi, false, false))
        assertTrue(f.work.isEmpty())
    }
    @Test fun cancellationBeforeWorkDoesNotRead() {
        val f = Fixture()
        f.controller.refresh(wifi, false, false)
        f.controller.invalidate()
        assertEquals(SessionViewState.Cancelling, f.states.last())
        assertFalse(f.controller.refresh(wifi, false, false))
        f.finish()
        assertEquals(0, f.calls)
        assertEquals(SessionViewState.Idle, f.states.last())
    }
    @Test fun lateResultAfterNetworkChangeDiscarded() {
        val f = Fixture()
        f.controller.refresh(wifi, false, false)
        f.duringRead = { cancelled ->
            f.controller.networkChanged(wifi.copy(networkId = 8))
            assertTrue(cancelled())
        }
        f.finish()
        assertEquals(SessionViewState.Idle, f.states.last())
    }
    @Test fun finishedSnapshotClearedOnNetworkChange() {
        val f = Fixture()
        f.controller.refresh(wifi, false, false)
        f.finish()
        f.controller.networkChanged(wifi.copy())
        assertTrue(f.states.last() is SessionViewState.Snapshot)
        f.controller.networkChanged(DiscoveryState.NoWifi)
        assertEquals(SessionViewState.Idle, f.states.last())
    }
    @Test fun explicitClearDiscardsSnapshot() {
        val f = Fixture()
        f.controller.refresh(wifi, false, false)
        f.finish()
        f.controller.invalidate()
        assertEquals(SessionViewState.Idle, f.states.last())
    }
    @Test fun disposalRejectsLateCallbacks() {
        val f = Fixture()
        f.controller.refresh(wifi, false, false)
        f.controller.dispose()
        f.finish()
        assertEquals(listOf(SessionViewState.Loading), f.states)
        assertFalse(f.controller.refresh(wifi, false, false))
    }
    @Test fun rejectedExecutorReleasesBusyState() {
        val states = mutableListOf<SessionViewState>()
        val controller = SessionRefreshController(Executor { throw RejectedExecutionException() },
            { it() }, { _, _, _, _ -> error("must not read") }, { states.add(it) })
        assertFalse(controller.refresh(wifi, false, false))
        assertFalse(controller.busy)
        assertEquals(SessionStatus.Unavailable, (states.last() as SessionViewState.Snapshot).status)
    }
    @Test fun maskShortAccountsCompletely() {
        for (user in listOf("1", "1234")) assertEquals("••••", SessionDisplay.maskAccount(user))
        assertEquals("•••• 0123", SessionDisplay.maskAccount("000123"))
    }
    @Test fun durationPreservesHoursBeyondOneDay() {
        assertEquals("25:01:01", SessionDisplay.duration(90061))
        assertEquals("00:00:00", SessionDisplay.duration(0))
    }
    @Test fun unknownAndNegativeDurationsAreNotInvented() {
        assertNull(SessionDisplay.duration(null))
        assertNull(SessionDisplay.duration(-1))
    }
    @Test fun logoutRequiresCurrentSnapshotAndCannotOverlap() {
        val f = Fixture()
        f.controller.refresh(wifi, false, false)
        f.finish()
        val snapshot = f.states.last() as SessionViewState.Snapshot
        assertTrue(f.controller.logout(wifi, snapshot, false))
        assertFalse(f.controller.logout(wifi, snapshot, false))
        assertFalse(f.controller.refresh(wifi, false, false))
        f.finish()
        assertEquals(1, f.logouts)
        assertEquals(SessionViewState.LogoutFinished(LogoutResult.LOGGED_OUT), f.states.last())
        assertFalse(f.controller.logout(wifi, snapshot, false))
    }
    @Test fun staleSnapshotCannotLogout() {
        val f = Fixture()
        f.controller.refresh(wifi, false, false)
        f.finish()
        val snapshot = f.states.last() as SessionViewState.Snapshot
        f.controller.invalidate()
        assertFalse(f.controller.logout(wifi, snapshot, false))
    }
    @Test fun legacyLogoutRequiresNewConsent() {
        val f = Fixture()
        f.controller.refresh(wifi, true, true)
        f.finish()
        val snapshot = f.states.last() as SessionViewState.Snapshot
        assertFalse(f.controller.logout(wifi, snapshot, false))
        assertTrue(f.controller.logout(wifi, snapshot, true))
        f.finish()
        assertEquals(1, f.logouts)
    }
}
