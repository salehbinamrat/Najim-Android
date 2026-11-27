package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import org.junit.Assert.*
import org.junit.Test

class NajmCallbackTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private fun reply(json: String, code: Int = 200) = ExchangeResult.Received(code, json.toByteArray())
    private val out = reply("""{"logged_in":"no","action":"onStatusQuery"}""")
    private val ack = reply("""{"logged_in":"no","action":"onLoginStart"}""")
    private fun active(user: String = "0012") = reply("""{"logged_in":"yes","username":"$user","bytes_in":"123","bytes_out":"456","remain_bytes_total":"1024","session_time_left":"01:02:03","uptime":"5m","action":"onStatusQuery"}""")

    @Test fun actualTemplateFieldsAndExactIdentity() {
        val status = NajmCallback.status(active("00%31%32"), true) as SessionStatus.Active
        assertEquals("0012", status.account.username)
        assertEquals(123L, status.account.metrics.uploaded?.exactBytes)
        assertEquals(456L, status.account.metrics.downloaded?.exactBytes)
        assertEquals(1024L, status.account.metrics.remainingTotal?.exactBytes)
        assertEquals(3723L, status.account.metrics.timeLeftSeconds)
        assertEquals(300L, status.account.metrics.uptimeSeconds)
        assertTrue(status.legacy)
    }

    @Test fun malformedOrDuplicateJsonCannotConfirm() {
        for (json in listOf("""{"logged_in":"yes","username":"0012","action":"onStatusQuery","logged_in":"no"}""",
            """{"logged_in":"yes","username":"0012","action":"onStatusQuery"} extra""",
            """{"logged_in":"yes","username":"0012","action":"onLoggedIn"}"""))
            assertNull(NajmCallback.status(reply(json), true))
        for (user in listOf("00%ZZ12", "00%E2%80%8B12", "00+12", "00%FF12"))
            assertEquals(SessionStatus.Unknown, NajmCallback.status(active(user), true))
    }

    @Test fun usernameOnlyAndPasswordModesUseSinglePostAndIndependentConfirmation() {
        for (password in listOf(null, "pass")) {
            val reads = ArrayDeque(listOf(out, out, active()))
            var posts = 0
            val flow = NajmCallbackFlow({ reads.removeFirst() }, { _, request ->
                posts++
                assertEquals(if (password == null) "username=0012" else "username=0012&password=pass", request.formBody)
                ack
            }, { wifi }, LoginProtocol.HTTP_PAP, true, {})
            assertEquals(GatewayLoginResult.LEGACY_MATCHING_SESSION, flow.login(wifi, "0012", password))
            assertEquals(1, posts)
            assertTrue(reads.isEmpty())
        }
    }

    @Test fun activeSessionNeverPosts() {
        val flow = NajmCallbackFlow({ active() }, { _, _ -> error("must not post") }, { wifi },
            LoginProtocol.HTTP_PAP, true)
        assertEquals(GatewayLoginResult.ALREADY_CONNECTED, flow.login(wifi, "0012", null))
    }

    @Test fun wrongAccountAndLostResponseNeverConfirmOrRetry() {
        for (post in listOf(ack, ExchangeResult.TransportFailure)) {
            var posts = 0
            var reads = 0
            val flow = NajmCallbackFlow({ if (++reads == 1) out else active("other") },
                { _, _ -> posts++; post }, { wifi }, LoginProtocol.HTTP_PAP, true, {})
            assertEquals(if (post == ack) GatewayLoginResult.ACCOUNT_MISMATCH else GatewayLoginResult.SESSION_UNVERIFIED,
                flow.login(wifi, "0012", null))
            assertEquals(1, posts)
        }
    }

    @Test fun originalErrorPreferredOverTranslatedError() {
        val error = reply("""{"logged_in":"no","error_orig":"RADIUS%20timeout","error":"account%20expired","action":"onLoginError"}""", 403)
        assertEquals(GatewayLoginResult.ROUTER_RADIUS_TIMEOUT, NajmCallback.rejection(error))
    }

    @Test fun noHttpConsentOrChapPlaintextSubmission() {
        val blocked = NajmCallbackFlow({ error("must not read") }, { _, _ -> error("must not post") },
            { wifi }, LoginProtocol.HTTP_PAP, false)
        assertEquals(GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED, blocked.login(wifi, "0012", null))
        val chap = NajmCallbackFlow({ out }, { _, _ -> error("must not post") }, { wifi }, LoginProtocol.HTTP_CHAP, true)
        assertEquals(GatewayLoginResult.UNSUPPORTED_PORTAL, chap.login(wifi, "0012", null))
    }

    @Test fun htmlFallsBackOnlyBeforePostAndNetworkChangeStops() {
        val fallback = NajmCallbackFlow({ reply("<html>ordinary portal</html>") },
            { _, _ -> error("must not post") }, { wifi }, LoginProtocol.HTTP_PAP, true)
        assertNull(fallback.login(wifi, "0012", null))
        var current: DiscoveryState = wifi
        val changed = NajmCallbackFlow({ current = wifi.copy(networkId = 8); out },
            { _, _ -> error("must not post") }, { current }, LoginProtocol.HTTP_PAP, true)
        assertEquals(GatewayLoginResult.NETWORK_CHANGED, changed.login(wifi, "0012", null))
    }
}
