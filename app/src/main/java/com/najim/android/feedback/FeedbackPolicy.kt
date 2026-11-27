package com.najim.android.feedback

import com.najim.android.auth.GatewayLoginResult
import com.najim.android.auth.LoginAttemptState
import com.najim.android.auth.LogoutResult
import com.najim.android.auth.SessionViewState

/** No credential, account, router response or image data is passed to audio output. */
internal enum class FeedbackCue { CONFIRMED, CAUTION, FAILED, SCAN_ACCEPTED }

internal object FeedbackPolicy {
    fun login(state: LoginAttemptState): FeedbackCue? = when (state) {
        is LoginAttemptState.Finished -> when (state.result) {
            GatewayLoginResult.MATCHING_SESSION -> FeedbackCue.CONFIRMED
            GatewayLoginResult.LEGACY_MATCHING_SESSION,
            GatewayLoginResult.ALREADY_CONNECTED,
            GatewayLoginResult.SESSION_UNVERIFIED -> FeedbackCue.CAUTION
            else -> FeedbackCue.FAILED
        }
        else -> null
    }

    fun session(state: SessionViewState): FeedbackCue? = when (state) {
        is SessionViewState.LogoutFinished -> when (state.result) {
            LogoutResult.LOGGED_OUT -> FeedbackCue.CONFIRMED
            LogoutResult.LEGACY_LOGGED_OUT, LogoutResult.ALREADY_LOGGED_OUT,
            LogoutResult.UNVERIFIED -> FeedbackCue.CAUTION
            else -> FeedbackCue.FAILED
        }
        else -> null
    }
}
