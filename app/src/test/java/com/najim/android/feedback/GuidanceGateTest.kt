package com.najim.android.feedback

import com.najim.android.auth.*
import org.junit.Assert.*
import org.junit.Test

class GuidanceGateTest {
    @Test fun offByDefault() {
        val gate = GuidanceGate({ error("unexpected speech") }, { error("unexpected tone") })
        gate.foreground(true)
        gate.emit(SpeechCue.LOGIN_CONFIRMED)
    }
    @Test fun backgroundNeverPlaysOrReplays() {
        val gate = GuidanceGate({ error("unexpected speech") }, { error("unexpected tone") })
        gate.configure(true, true)
        gate.emit(SpeechCue.SCAN_ACCEPTED)
        gate.foreground(true)
        gate.foreground(false)
        gate.emit(SpeechCue.LOGOUT_FAILED)
    }
    @Test fun speechOptInUsesSpeechOnly() {
        val spoken = mutableListOf<SpeechCue>()
        val gate = GuidanceGate({ spoken.add(it); true }, { error("unexpected tone") })
        gate.foreground(true)
        gate.configure(true, true)
        gate.emit(SpeechCue.SCAN_ACCEPTED)
        assertEquals(listOf(SpeechCue.SCAN_ACCEPTED), spoken)
    }
    @Test fun unavailableSpeechFallsBackToTone() {
        val tones = mutableListOf<FeedbackCue>()
        val gate = GuidanceGate({ false }, { tones.add(it) })
        gate.foreground(true)
        gate.configure(true, true)
        gate.emit(SpeechCue.LOGIN_CAUTION)
        assertEquals(listOf(FeedbackCue.CAUTION), tones)
    }
    @Test fun toneOnlyDoesNotAccessSpeech() {
        val tones = mutableListOf<FeedbackCue>()
        val gate = GuidanceGate({ error("unexpected speech") }, { tones.add(it) })
        gate.foreground(true)
        gate.configure(true, false)
        gate.emit(SpeechCue.LOGIN_FAILED)
        assertEquals(listOf(FeedbackCue.FAILED), tones)
    }
    @Test fun loginAndLogoutHaveDifferentPhrases() {
        assertEquals(SpeechCue.LOGIN_CONFIRMED, SpeechPolicy.login(LoginAttemptState.Finished(GatewayLoginResult.MATCHING_SESSION)))
        assertEquals(SpeechCue.LOGOUT_CONFIRMED, SpeechPolicy.session(SessionViewState.LogoutFinished(LogoutResult.LOGGED_OUT)))
    }
    @Test fun legacyAndUnverifiedNeverSpeakConfirmation() {
        assertEquals(SpeechCue.LOGIN_CAUTION, SpeechPolicy.login(LoginAttemptState.Finished(GatewayLoginResult.LEGACY_MATCHING_SESSION)))
        assertEquals(SpeechCue.LOGOUT_CAUTION, SpeechPolicy.session(SessionViewState.LogoutFinished(LogoutResult.UNVERIFIED)))
    }
    @Test fun cancelledAndLoadingAreSilent() {
        assertNull(SpeechPolicy.login(LoginAttemptState.Cancelled))
        assertNull(SpeechPolicy.session(SessionViewState.Loading))
    }
}
