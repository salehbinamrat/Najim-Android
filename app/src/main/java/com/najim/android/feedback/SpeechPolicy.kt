package com.najim.android.feedback

import com.najim.android.auth.LoginAttemptState
import com.najim.android.auth.SessionViewState

/** Separate fixed phrases for login and logout; never interpolate any account information. */
internal enum class SpeechCue {
    LOGIN_CONFIRMED, LOGIN_CAUTION, LOGIN_FAILED,
    LOGOUT_CONFIRMED, LOGOUT_CAUTION, LOGOUT_FAILED, SCAN_ACCEPTED
}

internal object SpeechPolicy {
    fun login(state: LoginAttemptState): SpeechCue? = when (FeedbackPolicy.login(state)) {
        FeedbackCue.CONFIRMED -> SpeechCue.LOGIN_CONFIRMED
        FeedbackCue.CAUTION -> SpeechCue.LOGIN_CAUTION
        FeedbackCue.FAILED -> SpeechCue.LOGIN_FAILED
        else -> null
    }

    fun session(state: SessionViewState): SpeechCue? = when (FeedbackPolicy.session(state)) {
        FeedbackCue.CONFIRMED -> SpeechCue.LOGOUT_CONFIRMED
        FeedbackCue.CAUTION -> SpeechCue.LOGOUT_CAUTION
        FeedbackCue.FAILED -> SpeechCue.LOGOUT_FAILED
        else -> null
    }

    fun tone(cue: SpeechCue): FeedbackCue = when (cue) {
        SpeechCue.LOGIN_CONFIRMED, SpeechCue.LOGOUT_CONFIRMED -> FeedbackCue.CONFIRMED
        SpeechCue.LOGIN_CAUTION, SpeechCue.LOGOUT_CAUTION -> FeedbackCue.CAUTION
        SpeechCue.LOGIN_FAILED, SpeechCue.LOGOUT_FAILED -> FeedbackCue.FAILED
        SpeechCue.SCAN_ACCEPTED -> FeedbackCue.SCAN_ACCEPTED
    }
}

/** No pending events. Speech is attempted only after explicit opt-in and foreground check. */
internal class GuidanceGate(
    private val speak: (SpeechCue) -> Boolean,
    private val tone: (FeedbackCue) -> Unit
) {
    private var foreground = false
    private var enabled = false
    private var speech = false
    fun configure(enabled: Boolean, speech: Boolean) {
        this.enabled = enabled
        this.speech = speech
    }
    fun foreground(value: Boolean) { foreground = value }

    fun emit(cue: SpeechCue?) {
        if (!foreground || !enabled || cue == null) return
        if (!speech || !speak(cue)) tone(SpeechPolicy.tone(cue))
    }
}
