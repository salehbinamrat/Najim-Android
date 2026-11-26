package com.najim.android.auth

/** Transport evidence alone is insufficient to prove a new authenticated session. */
internal sealed interface LoginOutcome {
    data object PendingSessionVerification : LoginOutcome
    data object RedirectRequiresVerification : LoginOutcome
    data class HttpFailure(val statusCode: Int) : LoginOutcome
    data object TransportFailure : LoginOutcome
}

internal object LoginResponsePolicy {
    fun classify(statusCode: Int?): LoginOutcome = when (statusCode) {
        null -> LoginOutcome.TransportFailure
        in 200..299 -> LoginOutcome.PendingSessionVerification
        in 300..399 -> LoginOutcome.RedirectRequiresVerification
        else -> LoginOutcome.HttpFailure(statusCode)
    }
}
