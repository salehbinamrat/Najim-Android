package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import org.junit.Assert.*
import org.junit.Test

class GatewayLoginFlowTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.3.1")
    private val loginHtml = """<form name="login" method="post" action="/login"><input name="username"><input name="password"></form>"""
    private val loggedOut = ExchangeResult.Received(302, ByteArray(0), "/login?dst=status")
    private val login = html(loginHtml)
    private val accepted = ExchangeResult.Received(302, ByteArray(0), "https://example.test/")
    private fun html(value: String) = ExchangeResult.Received(200, value.toByteArray(Charsets.UTF_8))
    private fun status(user: String) = html("""<h1>Welcome, $user!</h1><form action="/logout"></form>""")

    private class Fixture(
        val wifi: DiscoveryState.Ready,
        reads: List<ExchangeResult>,
        private val post: ExchangeResult
    ) {
        private val queue = ArrayDeque(reads)
        val calls = mutableListOf<String>()
        var state: DiscoveryState = wifi
        var onSubmit: () -> Unit = {}
        private val flow = GatewayLoginFlow(
            read = { selected, page ->
                assertEquals(wifi, selected)
                calls.add(page.name)
                queue.removeFirst()
            },
            submit = { selected, request ->
                assertEquals(wifi, selected)
                assertEquals("https://10.0.3.1/login", request.endpoint.toString())
                calls.add("POST")
                onSubmit()
                post
            },
            current = { state }
        )
        fun run(user: String = "00Ab") = flow.login(wifi, user, null)
    }

    @Test fun fullSequenceUsesLocalStatusNotPostRedirect() {
        val f = Fixture(wifi, listOf(loggedOut, login, status("00Ab")), accepted)
        assertEquals(GatewayLoginResult.MATCHING_SESSION, f.run("٠٠Ab"))
        assertEquals(listOf("STATUS", "LOGIN", "POST", "STATUS"), f.calls)
    }

    @Test fun invalidUsernameDoesNotReadNetwork() {
        val f = Fixture(wifi, emptyList(), accepted)
        assertEquals(GatewayLoginResult.INVALID_INPUT, f.run(" "))
        assertTrue(f.calls.isEmpty())
    }

    @Test fun existingAccountStopsBeforePost() {
        val f = Fixture(wifi, listOf(status("other")), accepted)
        assertEquals(GatewayLoginResult.ALREADY_CONNECTED, f.run())
        assertEquals(listOf("STATUS"), f.calls)
    }

    @Test fun existingSameAccountIsNotNewLogin() {
        val f = Fixture(wifi, listOf(status("00Ab")), accepted)
        assertEquals(GatewayLoginResult.ALREADY_CONNECTED, f.run())
        assertEquals(listOf("STATUS"), f.calls)
    }

    @Test fun unknownBaselineNeverSendsCredentials() {
        val f = Fixture(wifi, listOf(html("<h1>Unknown</h1>")), accepted)
        assertEquals(GatewayLoginResult.BASELINE_UNKNOWN, f.run())
        assertEquals(listOf("STATUS"), f.calls)
    }

    @Test fun externalOrDowngradedRedirectNeverSendsCredentials() {
        for (location in listOf("https://evil.test/login", "http://10.0.3.1/login", "//evil.test/login", "/other")) {
            val f = Fixture(wifi, listOf(ExchangeResult.Received(302, ByteArray(0), location)), accepted)
            assertEquals(GatewayLoginResult.BASELINE_UNKNOWN, f.run())
            assertEquals(listOf("STATUS"), f.calls)
        }
    }

    @Test fun unrecognizedLoginFormStopsBeforePost() {
        val f = Fixture(wifi, listOf(loggedOut, html(loginHtml.replace("/login", "//evil.test/login"))), accepted)
        assertEquals(GatewayLoginResult.UNSUPPORTED_PORTAL, f.run())
        assertEquals(listOf("STATUS", "LOGIN"), f.calls)
    }

    @Test fun chapMarkerDoesNotSilentlyUsePap() {
        val f = Fixture(wifi, listOf(loggedOut, html(loginHtml + "<script>hexMD5()</script>")), accepted)
        assertEquals(GatewayLoginResult.UNSUPPORTED_PORTAL, f.run())
        assertEquals(listOf("STATUS", "LOGIN"), f.calls)
    }

    @Test fun networkChangedBeforeAttemptStops() {
        val f = Fixture(wifi, emptyList(), accepted)
        f.state = DiscoveryState.NoWifi
        assertEquals(GatewayLoginResult.NETWORK_CHANGED, f.run())
        assertTrue(f.calls.isEmpty())
    }

    @Test fun lostPostResponseNeverRetries() {
        val f = Fixture(wifi, listOf(loggedOut, login), ExchangeResult.TransportFailure)
        assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, f.run())
        assertEquals(listOf("STATUS", "LOGIN", "POST"), f.calls)
    }

    @Test fun changedNetworkAfterPostIsUnknown() {
        val f = Fixture(wifi, listOf(loggedOut, login), accepted)
        f.onSubmit = { f.state = DiscoveryState.NoWifi }
        assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, f.run())
        assertEquals(listOf("STATUS", "LOGIN", "POST"), f.calls)
    }

    @Test fun mismatchedAccountIsNotSuccess() {
        val f = Fixture(wifi, listOf(loggedOut, login, status("00ab")), accepted)
        assertEquals(GatewayLoginResult.ACCOUNT_MISMATCH, f.run())
    }

    @Test fun http200AloneDoesNotProveSuccess() {
        val f = Fixture(wifi, listOf(loggedOut, login, loggedOut), html("OK"))
        assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, f.run())
    }

    @Test fun http403NotClassifiedAsInvalidVoucher() {
        val f = Fixture(wifi, listOf(loggedOut, login), ExchangeResult.Received(403, ByteArray(0)))
        assertEquals(GatewayLoginResult.REQUEST_REJECTED, f.run())
        assertEquals(listOf("STATUS", "LOGIN", "POST"), f.calls)
    }

    @Test fun unavailablePreflightNeverPosts() {
        val f = Fixture(wifi, listOf(ExchangeResult.TransportFailure), accepted)
        assertEquals(GatewayLoginResult.TRANSPORT_UNAVAILABLE, f.run())
        assertEquals(listOf("STATUS"), f.calls)
    }
}
