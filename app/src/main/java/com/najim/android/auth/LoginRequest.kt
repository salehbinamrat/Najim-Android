package com.najim.android.auth

import com.najim.android.domain.VoucherInput
import com.najim.android.network.DiscoveryState
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

internal enum class LoginProtocol { HTTPS_PAP, HTTP_PAP, HTTP_CHAP }

internal enum class PreparationError {
    NETWORK_CHANGED, INVALID_GATEWAY, INSECURE_HTTP_NOT_APPROVED,
    INVALID_USERNAME, INVALID_PASSWORD, INVALID_CHALLENGE
}

/** Passwords and CHAP responses must not be included in logs or saved instance state. */
internal sealed interface Preparation {
    data class Rejected(val reason: PreparationError) : Preparation
    class Ready(val networkId: Long, val endpoint: URI, val formBody: String) : Preparation {
        override fun toString() = "LoginRequest(redacted)"
    }
}

internal class ChapChallenge(identifier: Int, bytes: ByteArray) {
    private val id = identifier
    private val challenge = bytes.copyOf()

    fun response(password: String): String? {
        if (id !in 0..255 || challenge.size != 16) return null
        // The standard RouterOS login template hashes byte strings.
        // Reject unsupported non-ASCII CHAP passwords rather than changing them.
        if (password.any { it.code > 127 }) return null
        val digest = MessageDigest.getInstance("MD5")
        digest.update(id.toByte())
        digest.update(password.toByteArray(Charsets.US_ASCII))
        digest.update(challenge)
        val hex = "0123456789abcdef"
        return buildString(32) {
            for (byte in digest.digest()) {
                val value = byte.toInt() and 0xff
                append(hex[value shr 4])
                append(hex[value and 15])
            }
        }
    }

    override fun toString() = "ChapChallenge(redacted)"
}

/** Pure request preparation; never performs I/O or treats a response as authentication. */
internal object LoginRequest {
    fun prepare(
        selected: DiscoveryState.Ready,
        current: DiscoveryState,
        username: String,
        password: String?,
        protocol: LoginProtocol,
        allowInsecureHttp: Boolean = false,
        challenge: ChapChallenge? = null
    ): Preparation {
        if (selected != current) return reject(PreparationError.NETWORK_CHANGED)
        if (!isGateway(selected.gateway)) return reject(PreparationError.INVALID_GATEWAY)
        if (protocol != LoginProtocol.HTTPS_PAP && !allowInsecureHttp) {
            return reject(PreparationError.INSECURE_HTTP_NOT_APPROVED)
        }
        val user = VoucherInput.validate(username)
        if (user.error != null) return reject(PreparationError.INVALID_USERNAME)
        // Username-only is explicitly empty password; never retry with username as password.
        val secret = password.orEmpty()
        if (secret.length > 128 || secret.any { it.isISOControl() }) {
            return reject(PreparationError.INVALID_PASSWORD)
        }
        val wireSecret = if (protocol == LoginProtocol.HTTP_CHAP) {
            if (secret.any { it.code > 127 }) return reject(PreparationError.INVALID_PASSWORD)
            challenge?.response(secret) ?: return reject(PreparationError.INVALID_CHALLENGE)
        } else {
            if (challenge != null) return reject(PreparationError.INVALID_CHALLENGE)
            secret
        }
        val scheme = if (protocol == LoginProtocol.HTTPS_PAP) "https" else "http"
        val endpoint = URI(scheme, null, selected.gateway, -1, "/login", null, null)
        val body = "username=" + encode(user.value) + "&password=" + encode(wireSecret)
        return Preparation.Ready(selected.networkId, endpoint, body)
    }

    private fun reject(reason: PreparationError) = Preparation.Rejected(reason)

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    internal fun isGateway(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' } ||
                (part.length > 1 && part.startsWith('0'))) return false
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        return octets.first() in 1..223 && octets.first() != 127
    }
}
