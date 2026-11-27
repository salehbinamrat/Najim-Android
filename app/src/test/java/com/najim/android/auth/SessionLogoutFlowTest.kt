package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import org.junit.Assert.*
import org.junit.Test

class SessionLogoutFlowTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private fun active(user: String = "0012", legacy: Boolean = false) =
        SessionStatus.Active(PortalPage.AccountStatus(user), legacy)

    private inner class Fixture(reads: List<SessionStatus>, legacy: Boolean = false,
                                consent: Boolean = false,
                                reply: ExchangeResult = ExchangeResult.Received(200, ByteArray(0))) {
        private val queue = ArrayDeque(reads)
        var current: DiscoveryState = wifi
        var duringSubmit: () -> Unit = {}
        val calls = mutableListOf<String>()
        private val flow = SessionLogoutFlow(
            { selected -> assertEquals(wifi, selected); calls.add("STATUS"); queue.removeFirst() },
            { selected -> assertEquals(wifi, selected); calls.add("POST"); duringSubmit(); reply },
            { current }, legacy, consent)
        fun run() = flow.logout(wifi, "0012")
    }
    @Test fun confirmsWithIndependentStatus() {
        val f = Fixture(listOf(active(), SessionStatus.LoggedOut))
        assertEquals(LogoutResult.LOGGED_OUT, f.run())
        assertEquals(listOf("STATUS", "POST", "STATUS"), f.calls)
    }
    @Test fun existingLoggedOutDoesNotPost() {
        val f = Fixture(listOf(SessionStatus.LoggedOut))
        assertEquals(LogoutResult.ALREADY_LOGGED_OUT, f.run())
        assertEquals(listOf("STATUS"), f.calls)
    }
    @Test fun wrongAccountDoesNotPost() {
        val f = Fixture(listOf(active("other")))
        assertEquals(LogoutResult.ACCOUNT_CHANGED, f.run())
        assertEquals(listOf("STATUS"), f.calls)
    }
    @Test fun unknownBaselineDoesNotPost() {
        val f = Fixture(listOf(SessionStatus.Unknown))
        assertEquals(LogoutResult.PREFLIGHT_UNKNOWN, f.run())
        assertEquals(listOf("STATUS"), f.calls)
    }
    @Test fun noLegacyConsentDoesNotRead() {
        val f = Fixture(emptyList(), legacy = true)
        assertEquals(LogoutResult.CONSENT_REQUIRED, f.run())
        assertTrue(f.calls.isEmpty())
    }
    @Test fun legacyEvidenceSeparate() {
        val f = Fixture(listOf(active(legacy = true), SessionStatus.LoggedOut), legacy = true, consent = true)
        assertEquals(LogoutResult.LEGACY_LOGGED_OUT, f.run())
    }
    @Test fun http200DoesNotProveLogout() {
        val f = Fixture(listOf(active(), active()))
        assertEquals(LogoutResult.STILL_ACTIVE, f.run())
    }
    @Test fun lostReplyChecksStatusWithoutRetry() {
        val f = Fixture(listOf(active(), SessionStatus.LoggedOut), reply = ExchangeResult.TransportFailure)
        assertEquals(LogoutResult.LOGGED_OUT, f.run())
        assertEquals(listOf("STATUS", "POST", "STATUS"), f.calls)
    }
    @Test fun unavailableAfterPostIsUnverified() {
        val f = Fixture(listOf(active(), SessionStatus.Unavailable))
        assertEquals(LogoutResult.UNVERIFIED, f.run())
    }
    @Test fun networkChangeDuringPostStopsVerification() {
        val f = Fixture(listOf(active()))
        f.duringSubmit = { f.current = DiscoveryState.NoWifi }
        assertEquals(LogoutResult.UNVERIFIED, f.run())
        assertEquals(listOf("STATUS", "POST"), f.calls)
    }
    @Test fun differentAccountAfterPostIsNotConfirmed() {
        assertEquals(LogoutResult.ACCOUNT_CHANGED, Fixture(listOf(active(), active("other"))).run())
    }
    @Test fun changedNetworkBeforeReadStops() {
        val f = Fixture(emptyList())
        f.current = DiscoveryState.NoWifi
        assertEquals(LogoutResult.NETWORK_CHANGED, f.run())
        assertTrue(f.calls.isEmpty())
    }
}
