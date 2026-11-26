package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.net.URI

internal enum class GatewayLoginResult {
    INVALID_INPUT, NETWORK_CHANGED, ALREADY_CONNECTED, BASELINE_UNKNOWN,
    UNSUPPORTED_PORTAL, REQUEST_REJECTED, TRANSPORT_UNAVAILABLE,
    ACCOUNT_MISMATCH, SESSION_UNVERIFIED, MATCHING_SESSION
}

/**
 * One explicit HTTPS attempt. No retry, external probe, redirect following or logout.
 * MATCHING_SESSION is matching account evidence, not a guarantee of Internet access.
 */
internal class GatewayLoginFlow(
    private val read: (DiscoveryState.Ready, GatewayPage) -> ExchangeResult,
    private val submit: (DiscoveryState.Ready, Preparation.Ready) -> ExchangeResult,
    private val current: () -> DiscoveryState
) {
    fun login(selected: DiscoveryState.Ready, username: String, password: String?): GatewayLoginResult {
        val initial = LoginRequest.prepare(selected, current(), username, password, LoginProtocol.HTTPS_PAP)
        if (initial !is Preparation.Ready) {
            return if (initial == Preparation.Rejected(PreparationError.NETWORK_CHANGED))
                GatewayLoginResult.NETWORK_CHANGED else GatewayLoginResult.INVALID_INPUT
        }
        val origin = URI("https", null, selected.gateway, -1, "/", null, null)

        // An explicit logged-out baseline is required. A generic /login page is not enough.
        val baseline = read(selected, GatewayPage.STATUS)
        if (baseline !is ExchangeResult.Received) return preflightFailure(baseline)
        if (parse(origin, GatewayPage.STATUS, baseline) is PortalPage.AccountStatus) {
            return GatewayLoginResult.ALREADY_CONNECTED
        }
        if (!loggedOutRedirect(origin, baseline)) return GatewayLoginResult.BASELINE_UNKNOWN

        // Read the portal immediately before submitting. Legacy CHAP is not silently downgraded.
        val login = read(selected, GatewayPage.LOGIN)
        if (login !is ExchangeResult.Received) return preflightFailure(login)
        val form = parse(origin, GatewayPage.LOGIN, login)
        if (form != PortalPage.LoginForm) return GatewayLoginResult.UNSUPPORTED_PORTAL
        val prepared = LoginRequest.prepare(selected, current(), username, password, LoginProtocol.HTTPS_PAP)
        if (prepared !is Preparation.Ready) return GatewayLoginResult.NETWORK_CHANGED

        val posted = submit(selected, prepared)
        // A request can reach the gateway even if its response is lost. Never auto-retry.
        if (posted !is ExchangeResult.Received) return GatewayLoginResult.SESSION_UNVERIFIED
        if (posted.statusCode !in 200..399) return GatewayLoginResult.REQUEST_REJECTED
        if (current() != selected) return GatewayLoginResult.SESSION_UNVERIFIED

        // Independent local status fetch; never follow the POST redirect.
        val after = read(selected, GatewayPage.STATUS)
        if (after !is ExchangeResult.Received) return GatewayLoginResult.SESSION_UNVERIFIED
        return when (SessionVerifier.evaluate(selected, current(), username, form,
            parse(origin, GatewayPage.STATUS, after))) {
            SessionEvidence.MATCHING_STATUS -> GatewayLoginResult.MATCHING_SESSION
            SessionEvidence.ACCOUNT_MISMATCH -> GatewayLoginResult.ACCOUNT_MISMATCH
            else -> GatewayLoginResult.SESSION_UNVERIFIED
        }
    }

    private fun parse(origin: URI, page: GatewayPage, result: ExchangeResult.Received): PortalPage =
        PortalParser.parse(origin, origin.resolve(page.path), result.statusCode, result.body)

    private fun loggedOutRedirect(origin: URI, result: ExchangeResult.Received): Boolean {
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

    private fun preflightFailure(result: ExchangeResult): GatewayLoginResult =
        if (result == ExchangeResult.NetworkChanged) GatewayLoginResult.NETWORK_CHANGED
        else GatewayLoginResult.TRANSPORT_UNAVAILABLE
}
