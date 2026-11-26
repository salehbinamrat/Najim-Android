package com.najim.android.auth

import com.najim.android.domain.VoucherInput
import com.najim.android.network.DiscoveryState

internal enum class SessionEvidence {
    NETWORK_CHANGED, BASELINE_NOT_LOGGED_OUT, ACCOUNT_MISMATCH,
    NO_MATCHING_STATUS, MATCHING_STATUS
}

/** A matching page is evidence only. Callers must supply fresh pages from the same verified route. */
internal object SessionVerifier {
    fun evaluate(
        selected: DiscoveryState.Ready,
        current: DiscoveryState,
        requestedUsername: String,
        before: PortalPage,
        after: PortalPage
    ): SessionEvidence {
        if (selected != current) return SessionEvidence.NETWORK_CHANGED
        if (before !is PortalPage.LoginForm && before !is PortalPage.ChapForm) {
            return SessionEvidence.BASELINE_NOT_LOGGED_OUT
        }
        if (after !is PortalPage.AccountStatus) return SessionEvidence.NO_MATCHING_STATUS
        val username = VoucherInput.validate(requestedUsername)
        if (username.error != null || username.value != after.username) {
            return SessionEvidence.ACCOUNT_MISMATCH
        }
        return SessionEvidence.MATCHING_STATUS
    }
}
