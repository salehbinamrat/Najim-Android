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
        val gate = GuidanceGate({ error("unexpected speech") }, { played.add(it) })
        gate.foreground(true)
        gate.emit(SpeechCue.LOGIN_CONFIRMED)
        gate.foreground(false)
        gate.configure(true, false)
        gate.emit(SpeechCue.LOGIN_CONFIRMED)
        gate.foreground(true)
        assertTrue(played.isEmpty())
        gate.emit(SpeechCue.SCAN_ACCEPTED)
        assertEquals(listOf(FeedbackCue.SCAN_ACCEPTED), played)
    }
    @Test fun pauseAndDisableSuppress() {
        val played = mutableListOf<FeedbackCue>()
        val gate = GuidanceGate({ error("unexpected speech") }, { played.add(it) })
        gate.configure(true, false)
        gate.foreground(true)
        gate.emit(SpeechCue.LOGIN_CONFIRMED)
        gate.foreground(false)
        gate.emit(SpeechCue.LOGIN_FAILED)
        gate.foreground(true)
        gate.configure(false, false)
        gate.emit(SpeechCue.LOGIN_FAILED)
        assertEquals(listOf(FeedbackCue.CONFIRMED), played)
    }
    @Test fun nullCueIsIgnored() {
        val gate = GuidanceGate({ error("must not speak") }, { error("must not play") })
        gate.foreground(true)
        gate.configure(true, true)
        gate.emit(null)
    }
}
