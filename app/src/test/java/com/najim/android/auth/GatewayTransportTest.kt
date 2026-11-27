package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class GatewayTransportTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.3.1")
    private val request = LoginRequest.prepare(wifi, wifi, "00Ab", "pass", LoginProtocol.HTTPS_PAP) as Preparation.Ready

    private class FakeConnection(
        private val code: Int = 200,
        private val response: ByteArray = "portal".toByteArray(),
        private val failWrite: Boolean = false,
        private val customStream: ByteArrayInputStream? = null,
        private val onResponse: () -> Unit = {}
    ) : HttpURLConnection(URL("https://10.0.3.1/login")) {
        val sent = ByteArrayOutputStream()
        var reads = 0
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int { onResponse(); return code }
        override fun getOutputStream() = if (failWrite) throw IOException("test") else sent
        override fun getInputStream(): ByteArrayInputStream {
            reads++
            return customStream ?: ByteArrayInputStream(response)
        }
        override fun getErrorStream() = ByteArrayInputStream(response)
    }

    private fun transport(connection: FakeConnection, valid: () -> Boolean = { true }) =
        GatewayTransport({ valid() }, { _, _ -> connection })

    @Test fun postsOnlyToSelectedGatewayWithRedirectsDisabled() {
        val connection = FakeConnection()
        val result = transport(connection).submit(wifi, request) as ExchangeResult.Received
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals(5000, connection.connectTimeout)
        assertEquals(5000, connection.readTimeout)
        assertEquals("username=00Ab&password=pass", connection.sent.toString("UTF-8"))
        assertEquals(LoginOutcome.PendingSessionVerification, result.outcome)
        assertTrue(connection.closed)
        assertFalse(result.toString().contains("portal"))
    }

    @Test fun refusesHttpEvenIfPreparedWithConsent() {
        val http = LoginRequest.prepare(wifi, wifi, "a", null, LoginProtocol.HTTP_PAP, true) as Preparation.Ready
        val connection = FakeConnection()
        assertEquals(ExchangeResult.UnsupportedEndpoint, transport(connection).submit(wifi, http))
        assertEquals(0, connection.sent.size())
    }

    @Test fun alteredHostOrPathRejectedBeforeOpen() {
        for (endpoint in listOf("https://example.com/login", "https://10.0.3.1/status",
            "https://10.0.3.1:8443/login", "https://10.0.3.1/login?q=1", "https://a@10.0.3.1/login")) {
            val forged = Preparation.Ready(7L, URI(endpoint), "secret")
            val connection = FakeConnection()
            assertEquals(ExchangeResult.UnsupportedEndpoint, transport(connection).submit(wifi, forged))
            assertEquals(0, connection.sent.size())
        }
    }

    @Test fun changedNetworkIdRejected() {
        val connection = FakeConnection()
        assertEquals(ExchangeResult.UnsupportedEndpoint, transport(connection).submit(wifi.copy(networkId = 8), request))
        assertEquals(0, connection.sent.size())
    }

    @Test fun missingNetworkNeverOpensConnection() {
        val transport = GatewayTransport({ false }, { _, _ -> error("must not open") })
        assertEquals(ExchangeResult.NetworkChanged, transport.submit(wifi, request))
    }

    @Test fun missingBoundConnectionDoesNotFallBack() {
        val transport = GatewayTransport({ true }, { _, _ -> null })
        assertEquals(ExchangeResult.NetworkChanged, transport.submit(wifi, request))
    }

    @Test fun networkChangeBeforeWriteDoesNotSendCredentials() {
        var calls = 0
        val connection = FakeConnection()
        assertEquals(ExchangeResult.NetworkChanged, transport(connection) { ++calls == 1 }.submit(wifi, request))
        assertEquals(0, connection.sent.size())
        assertTrue(connection.closed)
    }

    @Test fun networkChangeAfterWriteDoesNotReportSuccess() {
        var calls = 0
        val connection = FakeConnection()
        assertEquals(ExchangeResult.NetworkChanged, transport(connection) { ++calls <= 2 }.submit(wifi, request))
        assertTrue(connection.closed)
    }

    @Test fun redirectNotFollowedOrRead() {
        val connection = FakeConnection(302)
        val result = transport(connection).submit(wifi, request) as ExchangeResult.Received
        assertEquals(LoginOutcome.RedirectRequiresVerification, result.outcome)
        assertEquals(0, connection.reads)
        assertEquals(0, result.body.size)
        assertTrue(connection.closed)
    }

    @Test fun boundedBodyAllowsExactLimit() {
        val connection = FakeConnection(response = ByteArray(GatewayTransport.MAX_RESPONSE_BYTES))
        val result = transport(connection).submit(wifi, request) as ExchangeResult.Received
        assertEquals(GatewayTransport.MAX_RESPONSE_BYTES, result.body.size)
    }

    @Test fun oversizedBodyRejectedAndConnectionClosed() {
        val connection = FakeConnection(response = ByteArray(GatewayTransport.MAX_RESPONSE_BYTES + 1))
        assertEquals(ExchangeResult.ResponseTooLarge, transport(connection).submit(wifi, request))
        assertTrue(connection.closed)
    }

    @Test fun ioFailureDoesNotRetryOrLabelVoucherInvalid() {
        var opens = 0
        val connection = FakeConnection(failWrite = true)
        val transport = GatewayTransport({ true }, { _, _ -> opens++; connection })
        assertEquals(ExchangeResult.TransportFailure, transport.submit(wifi, request))
        assertEquals(1, opens)
        assertTrue(connection.closed)
    }

    @Test fun httpErrorIsNotAConfirmedVoucherError() {
        val result = transport(FakeConnection(403)).submit(wifi, request) as ExchangeResult.Received
        assertEquals(LoginOutcome.HttpFailure(403), result.outcome)
    }
    @Test fun getStatusUsesFixedEndpointWithoutCredentials() {
        val connection = FakeConnection()
        val transport = GatewayTransport({ true }, { selected, endpoint ->
            assertEquals(wifi, selected)
            assertEquals("https://10.0.3.1/status", endpoint.toString())
            connection
        })
        assertTrue(transport.read(wifi, GatewayPage.STATUS) is ExchangeResult.Received)
        assertEquals("GET", connection.requestMethod)
        assertFalse(connection.doOutput)
        assertEquals(0, connection.sent.size())
        assertTrue(connection.closed)
    }

    @Test fun getLoginRejectsInvalidGatewayBeforeOpening() {
        val transport = GatewayTransport({ true }, { _, _ -> error("must not open") })
        assertEquals(ExchangeResult.UnsupportedEndpoint,
            transport.read(wifi.copy(gateway = "evil.test"), GatewayPage.LOGIN))
    }
    @Test fun legacyHttpWithoutConsentRejectsGetAndPostBeforeOpening() {
        val legacy = LoginRequest.prepare(wifi, wifi, "a", null, LoginProtocol.HTTP_PAP, true) as Preparation.Ready
        val transport = GatewayTransport({ true }, { _, _ -> error("must not open") }, LoginProtocol.HTTP_PAP)
        assertEquals(ExchangeResult.UnsupportedEndpoint, transport.read(wifi, GatewayPage.STATUS))
        assertEquals(ExchangeResult.UnsupportedEndpoint, transport.submit(wifi, legacy))
    }

    @Test fun approvedLegacyUsesOnlyHttpGateway() {
        val legacy = LoginRequest.prepare(wifi, wifi, "a", null, LoginProtocol.HTTP_PAP, true) as Preparation.Ready
        val connection = FakeConnection()
        val transport = GatewayTransport({ true }, { _, endpoint ->
            assertEquals("http://10.0.3.1/login", endpoint.toString())
            connection
        }, LoginProtocol.HTTP_PAP, true)
        assertTrue(transport.submit(wifi, legacy) is ExchangeResult.Received)
        assertEquals("username=a&password=", connection.sent.toString("UTF-8"))
        assertFalse(connection.instanceFollowRedirects)
    }

    @Test fun protocolMismatchDoesNotOpenEvenWhenBothAreHttp() {
        val legacy = LoginRequest.prepare(wifi, wifi, "a", null, LoginProtocol.HTTP_PAP, true) as Preparation.Ready
        val transport = GatewayTransport({ true }, { _, _ -> error("must not open") }, LoginProtocol.HTTP_CHAP, true)
        assertEquals(ExchangeResult.UnsupportedEndpoint, transport.submit(wifi, legacy))
        assertEquals(ExchangeResult.UnsupportedEndpoint, transport.submit(wifi, request))
    }

    @Test fun legacyStatusReadUsesFixedOriginAndNoBody() {
        val connection = FakeConnection()
        val transport = GatewayTransport({ true }, { _, endpoint ->
            assertEquals("http://10.0.3.1/status", endpoint.toString())
            connection
        }, LoginProtocol.HTTP_CHAP, true)
        assertTrue(transport.read(wifi, GatewayPage.STATUS) is ExchangeResult.Received)
        assertEquals("GET", connection.requestMethod)
        assertEquals(0, connection.sent.size())
    }

    @Test fun approvedHttpStillRejectsAlteredEndpoint() {
        val transport = GatewayTransport({ true }, { _, _ -> error("must not open") }, LoginProtocol.HTTP_PAP, true)
        for (endpoint in listOf("http://evil.test/login", "http://10.0.3.1:8080/login",
            "http://10.0.3.1/status", "http://10.0.3.1/login?next=1", "https://10.0.3.1/login")) {
            val forged = Preparation.Ready(7L, URI(endpoint), "secret", LoginProtocol.HTTP_PAP)
            assertEquals(ExchangeResult.UnsupportedEndpoint, transport.submit(wifi, forged))
        }
    }
    @Test fun logoutPostsFixedPathAndCookieParameter() {
        val connection = FakeConnection()
        val transport = GatewayTransport({ true }, { _, endpoint ->
            assertEquals("https://10.0.3.1/logout", endpoint.toString())
            connection
        })
        assertTrue(transport.logout(wifi) is ExchangeResult.Received)
        assertEquals("POST", connection.requestMethod)
        assertEquals("erase-cookie=on", connection.sent.toString("UTF-8"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.closed)
    }

    @Test fun logoutHttpWithoutConsentIsRejectedBeforeOpen() {
        val transport = GatewayTransport({ true }, { _, _ -> error("must not open") }, LoginProtocol.HTTP_PAP)
        assertEquals(ExchangeResult.UnsupportedEndpoint, transport.logout(wifi))
    }
    @Test fun expiredBudgetBeforeOpeningNeverSends() {
        var calls = 0
        val transport = GatewayTransport({ true }, { _, _ -> error("must not open") },
            nowMillis = { if (calls++ == 0) 0L else GatewayTransport.EXCHANGE_BUDGET_MS })
        assertEquals(ExchangeResult.TransportFailure, transport.submit(wifi, request))
    }

    @Test fun slowDripBodyCannotExtendTotalBudget() {
        var clock = 0L
        var reads = 0
        val stream = object : ByteArrayInputStream(ByteArray(30)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                clock += 1000
                return super.read(b, off, minOf(len, 1))
            }
        }
        val connection = FakeConnection(customStream = stream)
        val transport = GatewayTransport({ true }, { _, _ -> connection }, nowMillis = { clock })
        assertEquals(ExchangeResult.TransportFailure, transport.read(wifi, GatewayPage.STATUS))
        assertEquals(15, reads)
        assertTrue(connection.closed)
    }

    @Test fun cancellationDuringResponseDiscardsPartialBody() {
        var cancelled = false
        var reads = 0
        val stream = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                cancelled = true
                return super.read(b, off, len)
            }
        }
        val connection = FakeConnection(customStream = stream)
        val transport = GatewayTransport({ true }, { _, _ -> connection }, cancelled = { cancelled })
        assertEquals(ExchangeResult.NetworkChanged, transport.read(wifi, GatewayPage.STATUS))
        assertEquals(1, reads)
        assertTrue(connection.closed)
    }

    @Test fun expiredPostResponseDoesNotRetryOrConfirm() {
        var clock = 0L
        var opens = 0
        val connection = FakeConnection(onResponse = { clock = GatewayTransport.EXCHANGE_BUDGET_MS })
        val transport = GatewayTransport({ true }, { _, _ -> opens++; connection }, nowMillis = { clock })
        assertEquals(ExchangeResult.TransportFailure, transport.submit(wifi, request))
        assertEquals(1, opens)
        assertTrue(connection.sent.size() > 0)
        assertTrue(connection.closed)
    }

    @Test fun cancellationBeforeOpeningDoesNotSend() {
        val transport = GatewayTransport({ true }, { _, _ -> error("must not open") }, cancelled = { true })
        assertEquals(ExchangeResult.NetworkChanged, transport.submit(wifi, request))
    }

    @Test fun timelyResponseWithinBudgetStillSucceeds() {
        var clock = 0L
        val connection = FakeConnection(onResponse = { clock = GatewayTransport.EXCHANGE_BUDGET_MS - 1 })
        val transport = GatewayTransport({ true }, { _, _ -> connection }, nowMillis = { clock })
        assertTrue(transport.read(wifi, GatewayPage.STATUS) is ExchangeResult.Received)
        assertTrue(connection.closed)
    }
    @Test fun callbackEndpointsAreFixedAndConsentBound() {
        val urls = mutableListOf<String>()
        val transport = GatewayTransport({ true }, { _, endpoint ->
            urls.add(endpoint.toString())
            FakeConnection()
        }, LoginProtocol.HTTP_PAP, true, wallMillis = { 123456L })
        val request = LoginRequest.prepare(wifi, wifi, "0012", null, LoginProtocol.HTTP_PAP, true) as Preparation.Ready
        assertTrue(transport.readCallbackStatus(wifi) is ExchangeResult.Received)
        assertTrue(transport.submitCallback(wifi, request) is ExchangeResult.Received)
        assertTrue(transport.logoutCallback(wifi) is ExchangeResult.Received)
        assertEquals(listOf("http://10.0.3.1/status?var=callBack&t=123456", "http://10.0.3.1/login?var=callBack",
            "http://10.0.3.1/logout?erase-cookie=true&var=callBack"), urls)
        val unapproved = GatewayTransport({ true }, { _, _ -> error("must not open") }, LoginProtocol.HTTP_PAP)
        assertEquals(ExchangeResult.UnsupportedEndpoint, unapproved.readCallbackStatus(wifi))
        assertEquals(ExchangeResult.UnsupportedEndpoint, unapproved.submitCallback(wifi, request))
    }
}
