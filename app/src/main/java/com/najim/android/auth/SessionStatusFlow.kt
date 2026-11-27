package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.net.URI

internal sealed interface SessionStatus {
    data object NetworkChanged : SessionStatus
    data object ConsentRequired : SessionStatus
    data object Unavailable : SessionStatus
    data object Unknown : SessionStatus
    data object LoggedOut : SessionStatus
    class Active(val account: PortalPage.AccountStatus, val legacy: Boolean) : SessionStatus {
        override fun toString() = "SessionStatus.Active(redacted)"
    }
}

/** Read only. Never triggers a login, external probe, logout or redirect. */
internal class SessionStatusFlow(
    private val read: (DiscoveryState.Ready, GatewayPage) -> ExchangeResult,
    private val current: () -> DiscoveryState,
    private val protocol: LoginProtocol = LoginProtocol.HTTPS_PAP,
    private val allowInsecureHttp: Boolean = false
) {
    fun refresh(selected: DiscoveryState.Ready): SessionStatus {
        if (current() != selected) return SessionStatus.NetworkChanged
        if (protocol.requiresConsent && !allowInsecureHttp) return SessionStatus.ConsentRequired
        if (!LoginRequest.isGateway(selected.gateway)) return SessionStatus.Unknown
        val response = read(selected, GatewayPage.STATUS)
        if (current() != selected || response == ExchangeResult.NetworkChanged) return SessionStatus.NetworkChanged
        if (response !is ExchangeResult.Received) return SessionStatus.Unavailable
        val origin = URI(protocol.scheme, null, selected.gateway, -1, "/", null, null)
        if (SessionRedirect.loggedOut(origin, response)) return SessionStatus.LoggedOut
        val page = PortalParser.parse(origin, origin.resolve("/status"), response.statusCode, response.body)
        if (current() != selected) return SessionStatus.NetworkChanged
        return if (page is PortalPage.AccountStatus) SessionStatus.Active(page, protocol.requiresConsent)
            else SessionStatus.Unknown
    }
}

internal object SessionRedirect {
    fun loggedOut(origin: URI, result: ExchangeResult.Received): Boolean {
        if (result.statusCode !in setOf(301, 302, 303, 307, 308)) return false
        val raw = result.location ?: return false
        if (raw.isBlank() || raw.any { it.isISOControl() || it == '\\' }) return false
        val destination = try { origin.resolve(URI(raw)) } catch (_: java.net.URISyntaxException) {
            return false
        } catch (_: IllegalArgumentException) { return false }
        // Query is evidence only, never used as a destination or forwarded.
        return destination.scheme == origin.scheme && destination.host == origin.host &&
            destination.port == -1 && destination.rawUserInfo == null &&
            destination.rawPath == "/login" && destination.rawFragment == null
    }
}
