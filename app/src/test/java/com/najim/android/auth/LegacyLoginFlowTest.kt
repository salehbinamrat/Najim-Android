package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import org.junit.Assert.*
import org.junit.Test

class LegacyLoginFlowTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.3.1")
    private val pap = """<form name="login" action="/login" method="post"><input name="username"><input name="password"></form>"""
    private val chap = """<form name="sendin" action="/login" method="post"><input type="hidden" name="username"><input type="hidden" name="password"></form>
        <script>function doLogin(){ document.sendin.password.value = hexMD5('\200' + document.login.password.value + '\000\001\002\003\004\005\006\007\010\011\012\013\014\015\016\017'); }</script>""" +
        pap.replace("method=\"post\"", "method=\"post\" onsubmit=\"return doLogin()\"")
    private fun html(value: String) = ExchangeResult.Received(200, value.toByteArray(Charsets.UTF_8))
    private val loggedOut = ExchangeResult.Received(302, ByteArray(0), "/login?dst=status")
    private val matching = html("""<h1>Welcome, 00Ab!</h1><form action="/logout"></form>""")
    private val accepted = ExchangeResult.Received(302, ByteArray(0), "/status")

    private inner class Fixture(
        protocol: LoginProtocol,
        consent: Boolean,
        reads: List<ExchangeResult>,
        post: ExchangeResult = accepted
    ) {
        private val queue = ArrayDeque(reads)
        val calls = mutableListOf<String>()
        var state: DiscoveryState = wifi
        var submitted: Preparation.Ready? = null
        var onSubmit: () -> Unit = {}
        private val flow = GatewayLoginFlow(
            read = { _, page -> calls.add(page.name); queue.removeFirst() },
            submit = { _, request -> calls.add("POST"); submitted = request; onSubmit(); post },
            current = { state },
            protocol = protocol,
            allowInsecureHttp = consent
        )
        fun run(password: String? = "1234") = flow.login(wifi, "٠٠Ab", password)
    }

    @Test fun noConsentNeverReadsOrPosts() {
        for (mode in listOf(LoginProtocol.HTTP_PAP, LoginProtocol.HTTP_CHAP)) {
            val fixture = Fixture(mode, false, emptyList())
            assertEquals(GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED, fixture.run())
            assertTrue(fixture.calls.isEmpty())
        }
    }

    @Test fun explicitPapHasSeparateLegacyEvidence() {
        val fixture = Fixture(LoginProtocol.HTTP_PAP, true, listOf(loggedOut, html(pap), matching))
        assertEquals(GatewayLoginResult.LEGACY_MATCHING_SESSION, fixture.run())
        assertEquals(listOf("STATUS", "LOGIN", "POST", "STATUS"), fixture.calls)
        val request = requireNotNull(fixture.submitted)
        assertEquals("http://10.0.3.1/login", request.endpoint.toString())
        assertEquals("username=00Ab&password=1234", request.formBody)
        assertEquals(LoginProtocol.HTTP_PAP, request.protocol)
    }

    @Test fun freshChapChallengeProducesHashNotPassword() {
        val fixture = Fixture(LoginProtocol.HTTP_CHAP, true, listOf(loggedOut, html(chap), matching))
        assertEquals(GatewayLoginResult.LEGACY_MATCHING_SESSION, fixture.run())
        val request = requireNotNull(fixture.submitted)
        assertEquals("username=00Ab&password=d7a567cb9ba72549c682a6d9857c9b77", request.formBody)
        assertEquals(LoginProtocol.HTTP_CHAP, request.protocol)
    }

    @Test fun chapCannotDowngradeToPap() {
        val fixture = Fixture(LoginProtocol.HTTP_CHAP, true, listOf(loggedOut, html(pap)))
        assertEquals(GatewayLoginResult.UNSUPPORTED_PORTAL, fixture.run())
        assertEquals(listOf("STATUS", "LOGIN"), fixture.calls)
    }

    @Test fun papDoesNotSendPasswordToChapForm() {
        val fixture = Fixture(LoginProtocol.HTTP_PAP, true, listOf(loggedOut, html(chap)))
        assertEquals(GatewayLoginResult.UNSUPPORTED_PORTAL, fixture.run())
        assertNull(fixture.submitted)
    }

    @Test fun legacyConsentDoesNotDowngradeHttps() {
        val fixture = Fixture(LoginProtocol.HTTPS_PAP, true, listOf(
            ExchangeResult.Received(302, ByteArray(0), "http://10.0.3.1/login")))
        assertEquals(GatewayLoginResult.BASELINE_UNKNOWN, fixture.run())
        assertNull(fixture.submitted)
    }

    @Test fun legacyRedirectToHttpsRequiresNewAttempt() {
        val fixture = Fixture(LoginProtocol.HTTP_PAP, true, listOf(
            ExchangeResult.Received(302, ByteArray(0), "https://10.0.3.1/login")))
        assertEquals(GatewayLoginResult.BASELINE_UNKNOWN, fixture.run())
        assertNull(fixture.submitted)
    }

    @Test fun invalidChapPasswordStopsBeforeNetwork() {
        val fixture = Fixture(LoginProtocol.HTTP_CHAP, true, emptyList())
        assertEquals(GatewayLoginResult.INVALID_INPUT, fixture.run("١٢٣٤"))
        assertTrue(fixture.calls.isEmpty())
    }

    @Test fun existingAccountDoesNotTriggerPost() {
        val fixture = Fixture(LoginProtocol.HTTP_CHAP, true, listOf(matching))
        assertEquals(GatewayLoginResult.ALREADY_CONNECTED, fixture.run())
        assertEquals(listOf("STATUS"), fixture.calls)
    }

    @Test fun lostLegacyResponseDoesNotRetry() {
        val fixture = Fixture(LoginProtocol.HTTP_PAP, true, listOf(loggedOut, html(pap)), ExchangeResult.TransportFailure)
        assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, fixture.run())
        assertEquals(listOf("STATUS", "LOGIN", "POST"), fixture.calls)
    }

    @Test fun networkChangeInvalidatesLegacyResult() {
        val fixture = Fixture(LoginProtocol.HTTP_CHAP, true, listOf(loggedOut, html(chap)))
        fixture.onSubmit = { fixture.state = DiscoveryState.NoWifi }
        assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, fixture.run())
        assertEquals(listOf("STATUS", "LOGIN", "POST"), fixture.calls)
    }

    @Test fun usernameOnlyLegacyDoesNotInventPassword() {
        val fixture = Fixture(LoginProtocol.HTTP_PAP, true, listOf(loggedOut, html(pap), matching))
        assertEquals(GatewayLoginResult.LEGACY_MATCHING_SESSION, fixture.run(null))
        assertEquals("username=00Ab&password=", fixture.submitted?.formBody)
    }
}
