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
    class Received(val statusCode: Int, val body: ByteArray) : ExchangeResult {
        val outcome: LoginOutcome get() = LoginResponsePolicy.classify(statusCode)
        override fun toString() = "GatewayResponse(status=$statusCode, body=redacted)"
    }
}

/** Synchronous, worker-thread-only transport. No automatic application-level retry. */
internal class GatewayTransport(
    private val stillSelected: (DiscoveryState.Ready) -> Boolean,
    private val openBound: (DiscoveryState.Ready, URI) -> HttpURLConnection?
) {
    fun submit(selected: DiscoveryState.Ready, request: Preparation.Ready): ExchangeResult {
        if (request.networkId != selected.networkId ||
            request.endpoint.scheme != "https" ||
            request.endpoint.host != selected.gateway ||
            request.endpoint.rawUserInfo != null ||
            request.endpoint.port != -1 ||
            request.endpoint.rawPath != "/login" ||
            request.endpoint.rawQuery != null ||
            request.endpoint.rawFragment != null) return ExchangeResult.UnsupportedEndpoint

        var connection: HttpURLConnection? = null
        val bytes = request.formBody.toByteArray(Charsets.UTF_8)
        try {
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged
            val target = openBound(selected, request.endpoint) ?: return ExchangeResult.NetworkChanged
            connection = target
            target.instanceFollowRedirects = false
            target.useCaches = false
            target.connectTimeout = 5000
            target.readTimeout = 5000
            target.requestMethod = "POST"
            target.doOutput = true
            target.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            target.setRequestProperty("Cache-Control", "no-store")
            target.setFixedLengthStreamingMode(bytes.size)

            // Verify again before handing the request body to the Wi-Fi-bound connection.
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged
            target.outputStream.use { output -> output.write(bytes) }
            val status = target.responseCode
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged

            // Redirects are not followed, and their destinations are never used as login targets.
            val body = if (status in 300..399) {
                ByteArray(0)
            } else {
                val stream = if (status >= 400) target.errorStream else target.inputStream
                val limited = stream?.use { readBounded(it) } ?: if (stream == null) ByteArray(0) else null
                limited ?: return ExchangeResult.ResponseTooLarge
            }
            if (!stillSelected(selected)) return ExchangeResult.NetworkChanged
            return ExchangeResult.Received(status, body)
        } catch (_: IOException) {
            return ExchangeResult.TransportFailure
        } catch (_: SecurityException) {
            return ExchangeResult.TransportFailure
        } finally {
            bytes.fill(0)
            connection?.disconnect()
        }
    }

    private fun readBounded(stream: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) return output.toByteArray()
            if (count == 0) throw IOException("Empty stream read")
            if (count > MAX_RESPONSE_BYTES - output.size()) return null
            output.write(buffer, 0, count)
        }
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
