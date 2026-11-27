package com.najim.android.feedback

import com.najim.android.auth.*
import org.junit.Assert.*
import org.junit.Test

class FeedbackPolicyTest {
    @Test fun onlyVerifiedHttpsLoginGetsConfirmed() {
        for (result in GatewayLoginResult.entries) {
            val cue = FeedbackPolicy.login(LoginAttemptState.Finished(result))
            assertEquals(result == GatewayLoginResult.MATCHING_SESSION, cue == FeedbackCue.CONFIRMED)
        }
    }
    @Test fun legacyAndUncertainAreCaution() {
        for (result in listOf(GatewayLoginResult.LEGACY_MATCHING_SESSION,
            GatewayLoginResult.SESSION_UNVERIFIED, GatewayLoginResult.ALREADY_CONNECTED))
            assertEquals(FeedbackCue.CAUTION, FeedbackPolicy.login(LoginAttemptState.Finished(result)))
    }
    @Test fun noToneForTransientOrCancelledLogin() {
        for (state in listOf(LoginAttemptState.Idle, LoginAttemptState.Running,
            LoginAttemptState.Cancelling, LoginAttemptState.Cancelled))
            assertNull(FeedbackPolicy.login(state))
    }
    @Test fun logoutEvidenceIsDistinguished() {
        for (result in LogoutResult.entries) {
            assertEquals(result == LogoutResult.LOGGED_OUT,
                FeedbackPolicy.session(SessionViewState.LogoutFinished(result)) == FeedbackCue.CONFIRMED)
        }
        assertEquals(FeedbackCue.CAUTION, FeedbackPolicy.session(SessionViewState.LogoutFinished(LogoutResult.LEGACY_LOGGED_OUT)))
    }
    @Test fun statusRefreshNeverProducesSuccessTone() {
        assertNull(FeedbackPolicy.session(SessionViewState.Loading))
        assertNull(FeedbackPolicy.session(SessionViewState.Snapshot(SessionStatus.LoggedOut, false)))
    }
    @Test fun defaultDisabledAndBackgroundEventsNotReplayed() {
        val played = mutableListOf<FeedbackCue>()
        val gate = FeedbackGate({ played.add(it) }, {})
        gate.resume()
        gate.emit(FeedbackCue.CONFIRMED)
        gate.pause()
        gate.setEnabled(true)
        gate.emit(FeedbackCue.CONFIRMED)
        gate.resume()
        assertTrue(played.isEmpty())
        gate.emit(FeedbackCue.SCAN_ACCEPTED)
        assertEquals(listOf(FeedbackCue.SCAN_ACCEPTED), played)
    }
    @Test fun pauseAndDisableStopAndSuppress() {
        val played = mutableListOf<FeedbackCue>()
        var stops = 0
        val gate = FeedbackGate({ played.add(it) }, { stops++ })
        gate.setEnabled(true)
        gate.resume()
        gate.emit(FeedbackCue.CONFIRMED)
        gate.pause()
        gate.emit(FeedbackCue.FAILED)
        gate.resume()
        gate.setEnabled(false)
        gate.emit(FeedbackCue.FAILED)
        assertEquals(2, stops)
        assertEquals(listOf(FeedbackCue.CONFIRMED), played)
    }
    @Test fun nullCueIsIgnored() {
        val gate = FeedbackGate({ error("must not play") }, {})
        gate.resume()
        gate.setEnabled(true)
        gate.emit(null)
    }
}
