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
        private val failWrite: Boolean = false
    ) : HttpURLConnection(URL("https://10.0.3.1/login")) {
        val sent = ByteArrayOutputStream()
        var reads = 0
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getOutputStream() = if (failWrite) throw IOException("test") else sent
        override fun getInputStream(): ByteArrayInputStream {
            reads++
            return ByteArrayInputStream(response)
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
}
