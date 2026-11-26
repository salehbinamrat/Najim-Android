package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionVerifierTest {
    private val selected = DiscoveryState.Ready(7L, "10.0.3.1")
    private val account = PortalPage.AccountStatus("00Ab")

    @Test fun matchesRequestedAccountAfterLoggedOutBaseline() = assertEquals(SessionEvidence.MATCHING_STATUS,
        SessionVerifier.evaluate(selected, selected, "٠٠Ab", PortalPage.LoginForm, account))
    @Test fun existingSessionIsNotNewLogin() = assertEquals(SessionEvidence.BASELINE_NOT_LOGGED_OUT,
        SessionVerifier.evaluate(selected, selected, "00Ab", account, account))
    @Test fun unknownBaselineCannotProveNewLogin() = assertEquals(SessionEvidence.BASELINE_NOT_LOGGED_OUT,
        SessionVerifier.evaluate(selected, selected, "00Ab", PortalPage.Unknown, account))
    @Test fun exactCaseAndLeadingZerosMatter() {
        for (user in listOf("00ab", "Ab", "00Ab2")) assertEquals(SessionEvidence.ACCOUNT_MISMATCH,
            SessionVerifier.evaluate(selected, selected, user, PortalPage.LoginForm, account))
    }
    @Test fun missingStatusNotSuccessful() = assertEquals(SessionEvidence.NO_MATCHING_STATUS,
        SessionVerifier.evaluate(selected, selected, "00Ab", PortalPage.LoginForm, PortalPage.Unknown))
    @Test fun changedNetworkInvalidatesMatchingStatus() = assertEquals(SessionEvidence.NETWORK_CHANGED,
        SessionVerifier.evaluate(selected, selected.copy(networkId = 8), "00Ab", PortalPage.LoginForm, account))
}
