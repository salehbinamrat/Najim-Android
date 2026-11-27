package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.net.URI

internal enum class GatewayLoginResult {
    INVALID_INPUT, NETWORK_CHANGED, INSECURE_HTTP_NOT_APPROVED, ALREADY_CONNECTED, BASELINE_UNKNOWN,
    UNSUPPORTED_PORTAL, REQUEST_REJECTED, TRANSPORT_UNAVAILABLE,
    ACCOUNT_MISMATCH, SESSION_UNVERIFIED, MATCHING_SESSION, LEGACY_MATCHING_SESSION,
    ROUTER_USER_NOT_FOUND,
    ROUTER_INVALID_PASSWORD,
    ROUTER_INVALID_CREDENTIALS,
    ROUTER_DATA_LIMIT,
    ROUTER_TIME_LIMIT,
    ROUTER_ACCOUNT_EXPIRED,
    ROUTER_SESSION_LIMIT,
    ROUTER_ALREADY_LOGGED_IN,
    ROUTER_DEVICE_RESTRICTED,
    ROUTER_RADIUS_TIMEOUT,
    ROUTER_NO_VALID_PROFILE
}

/**
 * One explicit protocol attempt. No retry, external probe, redirect following or logout.
 * MATCHING_SESSION is matching account evidence, not a guarantee of Internet access.
 */
internal class GatewayLoginFlow(
    private val read: (DiscoveryState.Ready, GatewayPage) -> ExchangeResult,
    private val submit: (DiscoveryState.Ready, Preparation.Ready) -> ExchangeResult,
    private val current: () -> DiscoveryState,
    private val protocol: LoginProtocol = LoginProtocol.HTTPS_PAP,
    private val allowInsecureHttp: Boolean = false
) {
    fun login(selected: DiscoveryState.Ready, username: String, password: String?): GatewayLoginResult {
        val initial = LoginRequest.validateInput(selected, current(), username, password, protocol, allowInsecureHttp)
        if (initial != null) return preparationFailure(initial)
        val origin = URI(protocol.scheme, null, selected.gateway, -1, "/", null, null)

        // An explicit logged-out baseline is required. A generic /login page is not enough.
        val baseline = read(selected, GatewayPage.STATUS)
        if (baseline !is ExchangeResult.Received) return preflightFailure(baseline)
        val before = parse(origin, GatewayPage.STATUS, baseline)
        if (current() != selected) return GatewayLoginResult.NETWORK_CHANGED
        if (before is PortalPage.AccountStatus) {
            return GatewayLoginResult.ALREADY_CONNECTED
        }
        val loggedOut = SessionRedirect.loggedOut(origin, baseline)
        if (current() != selected) return GatewayLoginResult.NETWORK_CHANGED
        if (!loggedOut) return GatewayLoginResult.BASELINE_UNKNOWN

        // Read the portal immediately before submitting. Never change the selected protocol.
        val login = read(selected, GatewayPage.LOGIN)
        if (login !is ExchangeResult.Received) return preflightFailure(login)
        val form = parse(origin, GatewayPage.LOGIN, login)
        if (current() != selected) return GatewayLoginResult.NETWORK_CHANGED
        val challenge = when (protocol) {
            LoginProtocol.HTTP_CHAP -> (form as? PortalPage.ChapForm)?.challenge
                ?: return GatewayLoginResult.UNSUPPORTED_PORTAL
            LoginProtocol.HTTP_PAP, LoginProtocol.HTTPS_PAP -> {
                if (form != PortalPage.LoginForm) return GatewayLoginResult.UNSUPPORTED_PORTAL
                null
            }
        }
        val prepared = LoginRequest.prepare(selected, current(), username, password, protocol, allowInsecureHttp, challenge)
        if (prepared is Preparation.Rejected) return preparationFailure(prepared.reason)
        if (prepared !is Preparation.Ready) return GatewayLoginResult.INVALID_INPUT

        val posted = submit(selected, prepared)
        // A request can reach the gateway even if its response is lost. Never auto-retry.
        if (posted !is ExchangeResult.Received) return GatewayLoginResult.SESSION_UNVERIFIED
        if (current() != selected) return GatewayLoginResult.SESSION_UNVERIFIED
        val rejection = RouterErrorParser.parse(origin, posted)
        if (current() != selected) return GatewayLoginResult.SESSION_UNVERIFIED
        if (rejection != null) return rejection
        if (posted.statusCode !in 200..399) return GatewayLoginResult.REQUEST_REJECTED

        // Independent local status fetch; never follow the POST redirect.
        val after = read(selected, GatewayPage.STATUS)
        if (after !is ExchangeResult.Received) return GatewayLoginResult.SESSION_UNVERIFIED
        val observed = parse(origin, GatewayPage.STATUS, after)
        return when (SessionVerifier.evaluate(selected, current(), username, form, observed)) {
            SessionEvidence.MATCHING_STATUS -> if (protocol.requiresConsent)
                GatewayLoginResult.LEGACY_MATCHING_SESSION else GatewayLoginResult.MATCHING_SESSION
            SessionEvidence.ACCOUNT_MISMATCH -> GatewayLoginResult.ACCOUNT_MISMATCH
            else -> GatewayLoginResult.SESSION_UNVERIFIED
        }
    }

    private fun preparationFailure(reason: PreparationError): GatewayLoginResult = when (reason) {
        PreparationError.NETWORK_CHANGED -> GatewayLoginResult.NETWORK_CHANGED
        PreparationError.INSECURE_HTTP_NOT_APPROVED -> GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED
        else -> GatewayLoginResult.INVALID_INPUT
    }

    private fun parse(origin: URI, page: GatewayPage, result: ExchangeResult.Received): PortalPage =
        PortalParser.parse(origin, origin.resolve(page.path), result.statusCode, result.body)


    private fun preflightFailure(result: ExchangeResult): GatewayLoginResult =
        if (result == ExchangeResult.NetworkChanged) GatewayLoginResult.NETWORK_CHANGED
        else GatewayLoginResult.TRANSPORT_UNAVAILABLE
}
