package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import org.junit.Assert.*
import org.junit.Test

class SessionStatusFlowTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private val active = ExchangeResult.Received(200,
        "<h1>Welcome, 0012!</h1><form action='/logout'></form>".toByteArray())
    private fun flow(response: ExchangeResult, protocol: LoginProtocol = LoginProtocol.HTTPS_PAP,
                     consent: Boolean = false) = SessionStatusFlow({ selected, page ->
        assertEquals(wifi, selected)
        assertEquals(GatewayPage.STATUS, page)
        response
    }, { wifi }, protocol, consent)

    @Test fun activeStatusHasIdentity() {
        val result = flow(active).refresh(wifi) as SessionStatus.Active
        assertEquals("0012", result.account.username)
        assertFalse(result.legacy)
    }
    @Test fun legacyStatusRequiresConsent() {
        val flow = SessionStatusFlow({ _, _ -> error("must not read") }, { wifi }, LoginProtocol.HTTP_PAP)
        assertEquals(SessionStatus.ConsentRequired, flow.refresh(wifi))
    }
    @Test fun legacyEvidenceClearlyMarked() {
        assertTrue((flow(active, LoginProtocol.HTTP_CHAP, true).refresh(wifi) as SessionStatus.Active).legacy)
    }
    @Test fun localLoginRedirectMeansLoggedOut() {
        assertEquals(SessionStatus.LoggedOut, flow(ExchangeResult.Received(302, ByteArray(0), "/login?dst=status")).refresh(wifi))
    }
    @Test fun externalRedirectIsUnknown() {
        assertEquals(SessionStatus.Unknown, flow(ExchangeResult.Received(302, ByteArray(0), "https://other.test/login")).refresh(wifi))
    }
    @Test fun networkChangeBeforeReadStops() {
        val flow = SessionStatusFlow({ _, _ -> error("must not read") }, { DiscoveryState.NoWifi })
        assertEquals(SessionStatus.NetworkChanged, flow.refresh(wifi))
    }
    @Test fun networkChangeDuringReadDiscardsResult() {
        var current: DiscoveryState = wifi
        val flow = SessionStatusFlow({ _, _ -> current = DiscoveryState.NoWifi; active }, { current })
        assertEquals(SessionStatus.NetworkChanged, flow.refresh(wifi))
    }
    @Test fun transportFailureDoesNotMeanLoggedOut() {
        assertEquals(SessionStatus.Unavailable, flow(ExchangeResult.TransportFailure).refresh(wifi))
    }
    @Test fun generic200NotLoggedIn() {
        assertEquals(SessionStatus.Unknown, flow(ExchangeResult.Received(200, "OK".toByteArray())).refresh(wifi))
    }
    @Test fun explicitLocalHtmlRedirectIndicatesLoggedOutWithoutFollowing() {
        assertEquals(SessionStatus.LoggedOut, flow(ExchangeResult.Received(200,
            "<meta http-equiv='refresh' content='0;url=/login?dst=status'>".toByteArray())).refresh(wifi))
    }
    @Test fun unsupportedHtmlRedirectsRemainUnknown() {
        for (html in listOf("<meta http-equiv='refresh' content='0;url=https://evil.test/login'>",
            "<meta http-equiv='refresh' content='30;url=/login'>",
            "<meta http-equiv='refresh' content='0;url=/login'><form action='/logout'></form>")) {
            assertEquals(SessionStatus.Unknown, flow(ExchangeResult.Received(200, html.toByteArray())).refresh(wifi))
        }
    }
    @Test fun standardLocalLoginFormAtStatusIsLoggedOut() {
        assertEquals(SessionStatus.LoggedOut, flow(ExchangeResult.Received(200,
            "<form method='post' action='/login'><input name='username'></form>".toByteArray())).refresh(wifi))
    }
    @Test fun localHtmlRedirectMayIncludeLocalManualLoginLink() {
        val html = "<meta http-equiv='refresh' content='0;url=/login'><a href='/login'>Login</a>"
        assertEquals(SessionStatus.LoggedOut, flow(ExchangeResult.Received(200, html.toByteArray())).refresh(wifi))
        assertEquals(SessionStatus.Unknown, flow(ExchangeResult.Received(200,
            html.replace("href='/login'", "href='https://evil.test/login'").toByteArray())).refresh(wifi))
    }
}
