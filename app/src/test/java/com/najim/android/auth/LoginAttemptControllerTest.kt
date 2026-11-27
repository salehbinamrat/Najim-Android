package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import com.najim.android.storage.NetworkProfile
import com.najim.android.storage.SaveChoice
import com.najim.android.storage.ConfirmedVoucherSave
import java.util.UUID
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class LoginAttemptControllerTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.3.1")
    private inner class Fixture {
        val work = ArrayDeque<Runnable>()
        val callbacks = ArrayDeque<() -> Unit>()
        val states = mutableListOf<LoginAttemptState>()
        var calls = 0
        val saved = mutableListOf<ConfirmedVoucherSave>()
        var loginResult = GatewayLoginResult.MATCHING_SESSION
        var duringLogin: (() -> Boolean) -> Unit = {}
        val controller = LoginAttemptController(
            worker = Executor { work.addLast(it) },
            dispatch = { callbacks.addLast(it) },
            login = { _, _, _, _, _, cancelled ->
                calls++
                duringLogin(cancelled)
                loginResult
            },
            publish = { states.add(it) }, saveConfirmed = { saved.add(it) }
        )
        fun start(mode: LoginProtocol = LoginProtocol.HTTPS_PAP, consent: Boolean = false) =
            controller.start(wifi, "0012", null, mode, consent)
        fun finish() {
            work.removeFirst().run()
            callbacks.removeFirst().invoke()
        }
    }

    @Test fun oneAttemptUntilCompletion() {
        val f = Fixture()
        assertTrue(f.start())
        assertFalse(f.start())
        f.finish()
        assertEquals(1, f.calls)
        assertEquals(LoginAttemptState.Finished(GatewayLoginResult.MATCHING_SESSION), f.states.last())
        assertTrue(f.start())
    }

    @Test fun httpConsentRequiredBeforeWorker() {
        val f = Fixture()
        assertFalse(f.start(LoginProtocol.HTTP_PAP))
        assertTrue(f.work.isEmpty())
        assertEquals(LoginAttemptState.Finished(GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED), f.states.last())
        assertTrue(f.start(LoginProtocol.HTTP_PAP, true))
    }

    @Test fun cancelledQueuedWorkNeverInvokesLogin() {
        val f = Fixture()
        f.start()
        f.controller.cancel()
        assertFalse(f.start())
        f.finish()
        assertEquals(0, f.calls)
        assertEquals(LoginAttemptState.Cancelled, f.states.last())
    }

    @Test fun cancelDuringLoginSuppressesLateSuccess() {
        val f = Fixture()
        f.duringLogin = { cancelled ->
            f.controller.cancel()
            assertTrue(cancelled())
        }
        f.start()
        f.finish()
        assertEquals(LoginAttemptState.Cancelled, f.states.last())
    }

    @Test fun networkChangeCancelsBeforeWorker() {
        val f = Fixture()
        f.start()
        f.controller.networkChanged(wifi.copy(networkId = 8))
        f.finish()
        assertEquals(0, f.calls)
        assertEquals(LoginAttemptState.Cancelled, f.states.last())
    }

    @Test fun sameNetworkDoesNotCancel() {
        val f = Fixture()
        f.start()
        f.controller.networkChanged(wifi.copy())
        f.finish()
        assertEquals(1, f.calls)
        assertEquals(LoginAttemptState.Finished(GatewayLoginResult.MATCHING_SESSION), f.states.last())
    }

    @Test fun changedNetworkClearsFinishedResult() {
        val f = Fixture()
        f.start()
        f.finish()
        f.controller.networkChanged(DiscoveryState.NoWifi)
        assertEquals(LoginAttemptState.Idle, f.states.last())
    }

    @Test fun disposedOwnerReceivesNoLateCallbacks() {
        val f = Fixture()
        f.start()
        f.controller.dispose()
        f.finish()
        assertEquals(listOf(LoginAttemptState.Running), f.states)
        assertFalse(f.start())
    }

    @Test fun missingNetworkCannotStart() {
        val f = Fixture()
        assertFalse(f.controller.start(DiscoveryState.NoWifi, "0012", null, LoginProtocol.HTTPS_PAP, false))
        assertTrue(f.work.isEmpty())
    }
    private fun choice(legacy: Boolean = false, gateway: String = wifi.gateway) =
        SaveChoice(NetworkProfile(UUID.randomUUID(), "Network", gateway), legacy)

    @Test fun onlyOptedInActualMatchingAttemptAuthorizesSave() {
        val f = Fixture()
        f.start()
        f.finish()
        assertTrue(f.saved.isEmpty())
        val choice = choice()
        assertTrue(f.controller.start(wifi, "٠٠١٢", "secret", LoginProtocol.HTTPS_PAP, false, choice))
        f.finish()
        assertEquals("0012", f.saved.single().record.username)
        assertEquals("secret", f.saved.single().record.password)
        assertEquals(choice.profile.id, f.saved.single().record.profileId)
    }
    @Test fun rejectedLoginDoesNotSave() {
        val f = Fixture()
        f.loginResult = GatewayLoginResult.ROUTER_INVALID_PASSWORD
        f.controller.start(wifi, "0012", null, LoginProtocol.HTTPS_PAP, false, choice())
        f.finish()
        assertTrue(f.saved.isEmpty())
    }
    @Test fun cancelledOrChangedAttemptCannotSave() {
        val f = Fixture()
        f.controller.start(wifi, "0012", null, LoginProtocol.HTTPS_PAP, false, choice())
        f.work.removeFirst().run()
        f.controller.networkChanged(wifi.copy(networkId = 99))
        f.callbacks.removeFirst().invoke()
        assertTrue(f.saved.isEmpty())
    }
    @Test fun legacySaveNeedsIndependentConsent() {
        val f = Fixture()
        f.loginResult = GatewayLoginResult.LEGACY_MATCHING_SESSION
        assertFalse(f.controller.start(wifi, "0012", null, LoginProtocol.HTTP_CHAP, true, choice()))
        assertTrue(f.controller.start(wifi, "0012", null, LoginProtocol.HTTP_CHAP, true, choice(true)))
        f.finish()
        assertEquals(LoginProtocol.HTTP_CHAP, f.saved.single().record.protocol)
    }
    @Test fun wrongProfileGatewayNeverStarts() {
        val f = Fixture()
        assertFalse(f.controller.start(wifi, "0012", null, LoginProtocol.HTTPS_PAP, false, choice(gateway = "10.0.0.99")))
        assertTrue(f.work.isEmpty())
    }
}
