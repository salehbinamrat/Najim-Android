package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import com.najim.android.storage.ConfirmedVoucherSave
import com.najim.android.storage.NetworkProfile
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Synthetic protocol scenarios, not captured RouterOS firmware responses. */
class CompatibilityMatrixTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private val profile = NetworkProfile(UUID.randomUUID(), "Lab", wifi.gateway)
    private val pap = """<form name="login" method="post" action="/login"><input name="username"><input name="password" type="password"></form>"""
    private val chap = """<form name="sendin" method="post" action="/login"><input type="hidden" name="username"><input type="hidden" name="password"></form>
        <script>function doLogin(){document.sendin.password.value = hexMD5('\200' + document.login.password.value + '\000\001\002\003\004\005\006\007\010\011\012\013\014\015\016\017');}</script>""" +
        pap.replace("method=\"post\"", "method=\"post\" onsubmit=\"return doLogin()\"")
    private val loggedOut = ExchangeResult.Received(302, ByteArray(0), "/login?dst=status")
    private val redirect = ExchangeResult.Received(302, ByteArray(0), "/status")
    private fun html(value: String) = ExchangeResult.Received(200, value.toByteArray(Charsets.UTF_8))
    private fun active(account: String = "0012") = html("<h1>Welcome, $account!</h1><form action='/logout'></form>")
    private fun form(protocol: LoginProtocol) = html(if (protocol == LoginProtocol.HTTP_CHAP) chap else pap)
    private inner class Fixture(
        val mode: LoginProtocol,
        responses: List<ExchangeResult>,
        val reply: ExchangeResult = redirect
    ) {
        val pending = ArrayDeque(responses)
        var network: DiscoveryState = wifi
        var onRead: (GatewayPage, Int) -> Unit = { _, _ -> }
        var onPost: () -> Unit = {}
        val calls = mutableListOf<String>()
        private val flow = GatewayLoginFlow(
            read = { selected, page ->
                assertEquals(wifi, selected)
                calls.add(page.name)
                onRead(page, calls.size)
                pending.removeFirst()
            },
            submit = { selected, request ->
                assertEquals(wifi, selected)
                assertEquals(mode, request.protocol)
                assertEquals(mode.scheme + "://10.0.0.1/login", request.endpoint.toString())
                calls.add("POST")
                onPost()
                reply
            },
            current = { network },
            protocol = mode,
            allowInsecureHttp = mode.requiresConsent
        )
        fun run() = flow.login(wifi, "0012", "1234")
        fun authorize(result: GatewayLoginResult, approval: Boolean = true, legacyApproval: Boolean = true) =
            ConfirmedVoucherSave.authorize(profile, wifi, network, "0012", "1234", mode, result, approval, legacyApproval)
    }

    @Test fun allProtocolsRequireExactIndependentAccountEvidence() {
        for (mode in LoginProtocol.entries) {
            val f = Fixture(mode, listOf(loggedOut, form(mode), active()))
            val expected = if (mode.requiresConsent) GatewayLoginResult.LEGACY_MATCHING_SESSION
                else GatewayLoginResult.MATCHING_SESSION
            assertEquals(expected, f.run())
            assertEquals(listOf("STATUS", "LOGIN", "POST", "STATUS"), f.calls)
            assertNotNull(f.authorize(expected))
            assertNull(f.authorize(expected, approval = false))
            if (mode.requiresConsent) assertNull(f.authorize(expected, legacyApproval = false))
        }
    }

    @Test fun explicitRejectionsAcrossProtocolsNeverAuthorizeSave() {
        val errors = mapOf(
            "user not found" to GatewayLoginResult.ROUTER_USER_NOT_FOUND,
            "invalid password" to GatewayLoginResult.ROUTER_INVALID_PASSWORD,
            "invalid username or password" to GatewayLoginResult.ROUTER_INVALID_CREDENTIALS,
            "transfer limit reached" to GatewayLoginResult.ROUTER_DATA_LIMIT,
            "no more online time" to GatewayLoginResult.ROUTER_TIME_LIMIT,
            "account expired" to GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED,
            "no more sessions" to GatewayLoginResult.ROUTER_SESSION_LIMIT,
            "already logged in" to GatewayLoginResult.ROUTER_ALREADY_LOGGED_IN,
            "invalid calling-station-id" to GatewayLoginResult.ROUTER_DEVICE_RESTRICTED,
            "RADIUS timeout" to GatewayLoginResult.ROUTER_RADIUS_TIMEOUT,
            "no valid profile found" to GatewayLoginResult.ROUTER_NO_VALID_PROFILE,
            "انتهت صلاحية الحساب" to GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED,
            "الحساب لم يستجب، ولا يعني ذلك انتهاء الكرت" to GatewayLoginResult.ROUTER_RADIUS_TIMEOUT
        )
        for (mode in LoginProtocol.entries) for ((message, expected) in errors) {
            val post = html(pap + "<p class='error'>$message</p>")
            val f = Fixture(mode, listOf(loggedOut, form(mode)), post)
            val result = f.run()
            assertEquals(expected, result)
            assertNull(f.authorize(result))
            assertEquals(listOf("STATUS", "LOGIN", "POST"), f.calls)
        }
    }

    @Test fun allNonMatchingResultsAreIneligibleForStorage() {
        for (mode in LoginProtocol.entries) {
            val f = Fixture(mode, emptyList())
            for (result in GatewayLoginResult.entries) {
                val expected = if (mode.requiresConsent) result == GatewayLoginResult.LEGACY_MATCHING_SESSION
                    else result == GatewayLoginResult.MATCHING_SESSION
                assertEquals(expected, f.authorize(result) != null)
            }
        }
    }

    @Test fun sameGatewayDifferentNetworkDuringBaselineStopsBeforeLogin() {
        for (mode in LoginProtocol.entries) {
            val f = Fixture(mode, listOf(active()))
            f.onRead = { _, _ -> f.network = wifi.copy(networkId = 8L) }
            assertEquals(GatewayLoginResult.NETWORK_CHANGED, f.run())
            assertEquals(listOf("STATUS"), f.calls)
        }
    }

    @Test fun sameGatewayDifferentNetworkDuringFormStopsBeforePost() {
        val f = Fixture(LoginProtocol.HTTPS_PAP, listOf(loggedOut, html(pap)))
        f.onRead = { page, _ -> if (page == GatewayPage.LOGIN) f.network = wifi.copy(networkId = 8L) }
        assertEquals(GatewayLoginResult.NETWORK_CHANGED, f.run())
        assertEquals(listOf("STATUS", "LOGIN"), f.calls)
    }

    @Test fun sameGatewayDifferentNetworkAfterPostOrStatusNeverSaves() {
        for (mode in LoginProtocol.entries) {
            val f = Fixture(mode, listOf(loggedOut, form(mode), active()))
            f.onRead = { _, count -> if (count == 4) f.network = wifi.copy(networkId = 8L) }
            val result = f.run()
            assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, result)
            assertNull(f.authorize(result))
            assertEquals(listOf("STATUS", "LOGIN", "POST", "STATUS"), f.calls)
        }
    }

    @Test fun falseSuccessAndConcealedIdentityNeverSave() {
        for (response in listOf(html("OK"), active("00&#8203;12"), active("<span hidden>0012</span>"), loggedOut)) {
            val f = Fixture(LoginProtocol.HTTPS_PAP, listOf(loggedOut, html(pap), response))
            val result = f.run()
            assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, result)
            assertNull(f.authorize(result))
        }
    }

    @Test fun malformedOrUnknownErrorRemainsNeutral() {
        for (message in listOf("account ex&#8203;pired", "account ex&shy;pired", "invalid",
            "radius timeout / account expired")) {
            val f = Fixture(LoginProtocol.HTTPS_PAP, listOf(loggedOut, html(pap)),
                html(pap + "<p class='error'>$message</p>"))
            val result = f.run()
            assertEquals(GatewayLoginResult.REQUEST_REJECTED, result)
            assertNull(f.authorize(result))
        }
    }

    @Test fun unavailableRadiusTransportIsNotExpiryAndNeverRetries() {
        for (mode in LoginProtocol.entries) {
            val f = Fixture(mode, listOf(loggedOut, form(mode)), ExchangeResult.TransportFailure)
            val result = f.run()
            assertEquals(GatewayLoginResult.SESSION_UNVERIFIED, result)
            assertNull(f.authorize(result))
            assertEquals(listOf("STATUS", "LOGIN", "POST"), f.calls)
        }
    }

    @Test fun differentAccountWithSameGatewayNeverSaves() {
        val f = Fixture(LoginProtocol.HTTPS_PAP, listOf(loggedOut, html(pap), active("0013")))
        val result = f.run()
        assertEquals(GatewayLoginResult.ACCOUNT_MISMATCH, result)
        assertNull(f.authorize(result))
    }
}
