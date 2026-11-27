package com.najim.android.auth

import com.najim.android.network.DiscoveryState

internal enum class LogoutResult {
    CONSENT_REQUIRED, NETWORK_CHANGED, ALREADY_LOGGED_OUT, ACCOUNT_CHANGED,
    PREFLIGHT_UNKNOWN, UNVERIFIED, STILL_ACTIVE, LOGGED_OUT, LEGACY_LOGGED_OUT
}

/** Explicit logout of the current device session, not a router administration operation. */
internal class SessionLogoutFlow(
    private val status: (DiscoveryState.Ready) -> SessionStatus,
    private val submit: (DiscoveryState.Ready) -> ExchangeResult,
    private val current: () -> DiscoveryState,
    private val legacy: Boolean,
    private val consent: Boolean
) {
    fun logout(selected: DiscoveryState.Ready, expectedAccount: String): LogoutResult {
        if (legacy && !consent) return LogoutResult.CONSENT_REQUIRED
        if (current() != selected) return LogoutResult.NETWORK_CHANGED
        val before = status(selected)
        if (current() != selected || before == SessionStatus.NetworkChanged) return LogoutResult.NETWORK_CHANGED
        if (before == SessionStatus.LoggedOut) return LogoutResult.ALREADY_LOGGED_OUT
        if (before !is SessionStatus.Active || before.legacy != legacy) return LogoutResult.PREFLIGHT_UNKNOWN
        if (before.account.username != expectedAccount) return LogoutResult.ACCOUNT_CHANGED
        if (current() != selected) return LogoutResult.NETWORK_CHANGED

        // Submit once. Even a lost response may mean the gateway received it.
        submit(selected)
        if (current() != selected) return LogoutResult.UNVERIFIED
        val after = status(selected)
        if (current() != selected) return LogoutResult.UNVERIFIED
        return when (after) {
            SessionStatus.LoggedOut -> if (legacy) LogoutResult.LEGACY_LOGGED_OUT else LogoutResult.LOGGED_OUT
            is SessionStatus.Active -> if (after.legacy != legacy) LogoutResult.UNVERIFIED
                else if (after.account.username == expectedAccount) LogoutResult.STILL_ACTIVE
                else LogoutResult.ACCOUNT_CHANGED
            else -> LogoutResult.UNVERIFIED
        }
    }
}
