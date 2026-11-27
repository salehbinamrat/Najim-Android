package com.najim.android.auth

import com.najim.android.domain.VoucherInput
import com.najim.android.network.DiscoveryState
import java.net.URLDecoder

/** Narrow contract from Najmhotspot status.html/login.html, not general JSON or JavaScript. */
internal object NajmCallback {
    // The reference emits a flat object of string values, URL-escaped identity/error.
    // Reject duplicate fields, malformed escapes, trailing content and other shapes.
    private val member = Regex("""[ \t\r\n]*"([a-z_]+)"[ \t\r\n]*:[ \t\r\n]*"([^"\\\x00-\x1f]*)"[ \t\r\n]*([,}])""")

    private fun fields(response: ExchangeResult, codes: Set<Int>): Map<String, String>? {
        val received = response as? ExchangeResult.Received ?: return null
        if (received.statusCode !in codes || received.body.isEmpty() ||
            received.body.size > GatewayTransport.MAX_RESPONSE_BYTES) return null
        val text = received.body.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        if (!text.startsWith("{") || '\uFFFD' in text) return null
        val values = linkedMapOf<String, String>()
        var cursor = 1
        repeat(16) {
            val match = member.matchAt(text, cursor) ?: return null
            if (values.put(match.groupValues[1], match.groupValues[2]) != null) return null
            cursor = match.range.last + 1
            if (match.groupValues[3] == "}") return values.takeIf { cursor == text.length }
        }
        return null
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8").takeUnless { '\uFFFD' in it }
    } catch (_: IllegalArgumentException) { null }

    fun status(response: ExchangeResult, legacy: Boolean): SessionStatus? {
        val data = fields(response, setOf(200)) ?: return null
        if (data["action"] != "onStatusQuery") return null
        if (data["logged_in"] == "no") {
            return if (data["username"].isNullOrEmpty()) SessionStatus.LoggedOut else SessionStatus.Unknown
        }
        if (data["logged_in"] != "yes") return SessionStatus.Unknown
        val user = data["username"]?.let(::decode) ?: return SessionStatus.Unknown
        if (VoucherInput.validate(user).error != null || VoucherInput.normalize(user) != user)
            return SessionStatus.Unknown
        fun amount(key: String) = data[key]?.let(StatusFields::parseAmount)
        fun time(key: String) = data[key]?.let(StatusFields::parseDuration)
        return SessionStatus.Active(PortalPage.AccountStatus(user, SessionMetrics(
            uploaded = amount("bytes_in"), downloaded = amount("bytes_out"),
            remainingTotal = amount("remain_bytes_total"), uptimeSeconds = time("uptime"),
            timeLeftSeconds = time("session_time_left")?.takeIf { it > 0L }
        )), legacy)
    }

    fun rejection(response: ExchangeResult): GatewayLoginResult? {
        val data = fields(response, setOf(200, 400, 401, 403)) ?: return null
        if (data["action"] != "onLoginError" || data["logged_in"] != "no") return null
        val raw = data["error_orig"]?.takeIf { it.isNotBlank() } ?: data["error"].orEmpty()
        val reason = decode(raw) ?: return GatewayLoginResult.REQUEST_REJECTED
        return RouterErrorParser.classifyExplicit(reason)
    }

    fun acknowledged(response: ExchangeResult): Boolean {
        val data = fields(response, setOf(200)) ?: return false
        return when (data["action"]) {
            "onLoginStart" -> data["logged_in"] in setOf("yes", "no")
            "onLoggedIn" -> data["logged_in"] == "yes"
            else -> false
        }
    }
}

/** Null means the callback contract was not recognized BEFORE any credential POST. */
internal class NajmCallbackFlow(
    private val read: (DiscoveryState.Ready) -> ExchangeResult,
    private val submit: (DiscoveryState.Ready, Preparation.Ready) -> ExchangeResult,
    private val current: () -> DiscoveryState,
    private val protocol: LoginProtocol,
    private val consent: Boolean,
    private val pause: (Long) -> Unit = { Thread.sleep(it) }
) {
    fun login(selected: DiscoveryState.Ready, username: String, password: String?): GatewayLoginResult? {
        val invalid = LoginRequest.validateInput(selected, current(), username, password, protocol, consent)
        if (invalid != null) return when (invalid) {
            PreparationError.NETWORK_CHANGED -> GatewayLoginResult.NETWORK_CHANGED
            PreparationError.INSECURE_HTTP_NOT_APPROVED -> GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED
            else -> GatewayLoginResult.INVALID_INPUT
        }
        val baseline = read(selected)
        if (current() != selected || baseline == ExchangeResult.NetworkChanged) return GatewayLoginResult.NETWORK_CHANGED
        when (NajmCallback.status(baseline, protocol.requiresConsent)) {
            null -> return null
            is SessionStatus.Active -> return GatewayLoginResult.ALREADY_CONNECTED
            SessionStatus.LoggedOut -> Unit
            else -> return GatewayLoginResult.BASELINE_UNKNOWN
        }
        // Reference is PAP only; do not send plaintext credentials when CHAP was selected.
        if (protocol == LoginProtocol.HTTP_CHAP) return GatewayLoginResult.UNSUPPORTED_PORTAL
        val prepared = LoginRequest.prepare(selected, current(), username, password, protocol, consent)
        if (prepared !is Preparation.Ready) return GatewayLoginResult.NETWORK_CHANGED
        // Match the reference: omit password entirely in username-only mode.
        val request = if (password == null) Preparation.Ready(prepared.networkId, prepared.endpoint,
            prepared.formBody.substringBefore("&password="), protocol) else prepared
        val posted = submit(selected, request)
        if (current() != selected) return GatewayLoginResult.SESSION_UNVERIFIED
        NajmCallback.rejection(posted)?.let {
            return if (current() == selected) it else GatewayLoginResult.SESSION_UNVERIFIED
        }
        // Never POST again, including when the response was lost. Only confirm a
        // recognized acknowledgement with bounded independent read-only status queries.
        if (!NajmCallback.acknowledged(posted)) return GatewayLoginResult.SESSION_UNVERIFIED
        for (delay in listOf(0L, 400L, 950L)) {
            if (current() != selected) return GatewayLoginResult.SESSION_UNVERIFIED
            if (delay > 0) try { pause(delay) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return GatewayLoginResult.SESSION_UNVERIFIED
            }
            if (current() != selected) return GatewayLoginResult.SESSION_UNVERIFIED
            val observed = NajmCallback.status(read(selected), protocol.requiresConsent)
            if (current() != selected) return GatewayLoginResult.SESSION_UNVERIFIED
            when (observed) {
                is SessionStatus.Active -> return if (observed.account.username != VoucherInput.normalize(username))
                    GatewayLoginResult.ACCOUNT_MISMATCH
                else if (protocol.requiresConsent) GatewayLoginResult.LEGACY_MATCHING_SESSION
                else GatewayLoginResult.MATCHING_SESSION
                SessionStatus.LoggedOut -> Unit
                else -> return GatewayLoginResult.SESSION_UNVERIFIED
            }
        }
        return GatewayLoginResult.SESSION_UNVERIFIED
    }
}
