package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

internal sealed interface ExchangeResult {
    data object NetworkChanged : ExchangeResult
    data object UnsupportedEndpoint : ExchangeResult
    data object ResponseTooLarge : ExchangeResult
    data object TransportFailure : ExchangeResult

    /** Contains portal content that may include credentials or personal information; never log it. */
    class Received(val statusCode: Int, val body: ByteArray, val location: String? = null) : ExchangeResult {
        val outcome: LoginOutcome get() = LoginResponsePolicy.classify(statusCode)
        override fun toString() = "GatewayResponse(status=$statusCode, body=redacted)"
    }
}

/** Synchronous, worker-thread-only transport. No automatic application-level retry. */
internal class GatewayTransport(
    private val stillSelected: (DiscoveryState.Ready) -> Boolean,
    private val openBound: (DiscoveryState.Ready, URI) -> HttpURLConnection?,
    private val protocol: LoginProtocol = LoginProtocol.HTTPS_PAP,
    private val allowInsecureHttp: Boolean = false,
    private val cancelled: () -> Boolean = { false },
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val wallMillis: () -> Long = { System.currentTimeMillis() }
) {
    fun submit(selected: DiscoveryState.Ready, request: Preparation.Ready): ExchangeResult {
        if (request.networkId != selected.networkId || request.protocol != protocol ||
            !validEndpoint(selected, request.endpoint, "/login")) return ExchangeResult.UnsupportedEndpoint
        return exchange(selected, request.endpoint, request.formBody)
    }

    // Najm's documented callback endpoints. No caller-supplied query or URL.
    fun readCallbackStatus(selected: DiscoveryState.Ready): ExchangeResult {
        if (!permitted() || !LoginRequest.isGateway(selected.gateway)) return ExchangeResult.UnsupportedEndpoint
        return exchange(selected, URI(protocol.scheme, null, selected.gateway, -1,
            "/status", "var=callBack&t=" + wallMillis(), null), null)
    }

    fun submitCallback(selected: DiscoveryState.Ready, request: Preparation.Ready): ExchangeResult {
        if (protocol == LoginProtocol.HTTP_CHAP || request.networkId != selected.networkId ||
            request.protocol != protocol || !validEndpoint(selected, request.endpoint, "/login"))
            return ExchangeResult.UnsupportedEndpoint
        return exchange(selected, URI(protocol.scheme, null, selected.gateway, -1,
            "/login", "var=callBack", null), request.formBody)
    }

    fun logoutCallback(selected: DiscoveryState.Ready): ExchangeResult {
        if (!permitted() || !LoginRequest.isGateway(selected.gateway)) return ExchangeResult.UnsupportedEndpoint
        return exchange(selected, URI(protocol.scheme, null, selected.gateway, -1,
            "/logout", "erase-cookie=true&var=callBack", null), null)
    }

    fun read(selected: DiscoveryState.Ready, page: GatewayPage): ExchangeResult {
        if (!permitted() || !LoginRequest.isGateway(selected.gateway)) return ExchangeResult.UnsupportedEndpoint
        val endpoint = URI(protocol.scheme, null, selected.gateway, -1, page.path, null, null)
        return exchange(selected, endpoint, null)
    }

    /** Fixed local endpoint, never accepts a caller-provided logout URL or credentials. */
    fun logout(selected: DiscoveryState.Ready): ExchangeResult {
        if (!permitted() || !LoginRequest.isGateway(selected.gateway)) return ExchangeResult.UnsupportedEndpoint
        val endpoint = URI(protocol.scheme, null, selected.gateway, -1, "/logout", null, null)
        return exchange(selected, endpoint, "erase-cookie=on")
    }

    private fun permitted() = !protocol.requiresConsent || allowInsecureHttp

    private fun validEndpoint(selected: DiscoveryState.Ready, endpoint: URI, path: String): Boolean =
        permitted() && LoginRequest.isGateway(selected.gateway) && endpoint.scheme == protocol.scheme &&
            endpoint.host == selected.gateway && endpoint.rawUserInfo == null &&
            endpoint.port == -1 && endpoint.rawPath == path &&
            endpoint.rawQuery == null && endpoint.rawFragment == null

    private fun exchange(selected: DiscoveryState.Ready, endpoint: URI, formBody: String?): ExchangeResult {
        var connection: HttpURLConnection? = null
        val bytes = formBody?.toByteArray(Charsets.UTF_8)
        val started = nowMillis()
        try {
            checkBudget(started)
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged
            val target = openBound(selected, endpoint) ?: return ExchangeResult.NetworkChanged
            connection = target
            checkBudget(started)
            target.instanceFollowRedirects = false
            target.useCaches = false
            target.connectTimeout = 5000
            target.readTimeout = 5000
            target.requestMethod = if (bytes == null) "GET" else "POST"
            target.setRequestProperty("Cache-Control", "no-store, no-cache")
            target.setRequestProperty("Accept", if (endpoint.rawQuery != null) "application/json, text/plain, text/html" else "text/html")
            if (bytes != null) {
                target.doOutput = true
                target.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                target.setFixedLengthStreamingMode(bytes.size)
            }

            // Revalidate immediately before any network I/O.
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged
            checkBudget(started)
            if (bytes != null) target.outputStream.use { output ->
                checkBudget(started)
                output.write(bytes)
            }
            checkBudget(started)
            val status = target.responseCode
            checkBudget(started)
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged

            // Redirects are not followed, and their destinations are never used as login targets.
            val body = if (status in 300..399) {
                ByteArray(0)
            } else {
                val stream = if (status >= 400) target.errorStream else target.inputStream
                val limited = stream?.use { readBounded(it, started) } ?: if (stream == null) ByteArray(0) else null
                limited ?: return ExchangeResult.ResponseTooLarge
            }
            checkBudget(started)
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged
            val location = target.getHeaderField("Location")
            checkBudget(started)
            return ExchangeResult.Received(status, body, location)
        } catch (_: ExchangeCancelled) {
            return ExchangeResult.NetworkChanged
        } catch (_: IOException) {
            return ExchangeResult.TransportFailure
        } catch (_: SecurityException) {
            return ExchangeResult.TransportFailure
        } finally {
            bytes?.fill(0)
            connection?.disconnect()
        }
    }

    private class ExchangeCancelled : IOException()

    // Cooperative total budget, checked between blocking operations. Socket timeouts
    // still apply; this cannot forcibly interrupt a blocked platform call.
    private fun checkBudget(started: Long) {
        if (cancelled()) throw ExchangeCancelled()
        if (nowMillis() - started >= EXCHANGE_BUDGET_MS) throw IOException("Gateway exchange budget exceeded")
    }

    private fun readBounded(stream: InputStream, started: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        try {
            while (true) {
                checkBudget(started)
                val count = stream.read(buffer)
                checkBudget(started)
                if (count == -1) return output.toByteArray()
                if (count == 0) throw IOException("Empty stream read")
                if (count > MAX_RESPONSE_BYTES - output.size()) return null
                output.write(buffer, 0, count)
            }
        } finally { buffer.fill(0) }
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val EXCHANGE_BUDGET_MS = 15_000L
    }
}

internal enum class GatewayPage(val path: String) { LOGIN("/login"), STATUS("/status") }
