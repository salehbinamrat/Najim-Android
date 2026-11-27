package com.najim.android.auth

/** Safe, bounded metadata for the field report; never contains body, URL, account or secret. */
internal data class GatewayDiagnostic(val protocol: LoginProtocol, val kind: Kind, val httpCode: Int? = null) {
    enum class Kind { VPN, NETWORK_CHANGED, NO_RESPONSE, TOO_LARGE, ENDPOINT_REJECTED,
        CALLBACK_ACTIVE, CALLBACK_LOGGED_OUT, CALLBACK_UNKNOWN, TRADITIONAL, UNRECOGNIZED }

    companion object {
        fun from(protocol: LoginProtocol, reply: ExchangeResult, parsed: SessionStatus?,
                 fallback: Boolean): GatewayDiagnostic {
            val kind = when (reply) {
                ExchangeResult.NetworkChanged -> Kind.NETWORK_CHANGED
                ExchangeResult.TransportFailure -> Kind.NO_RESPONSE
                ExchangeResult.ResponseTooLarge -> Kind.TOO_LARGE
                ExchangeResult.UnsupportedEndpoint -> Kind.ENDPOINT_REJECTED
                is ExchangeResult.Received -> when {
                    parsed is SessionStatus.Active -> Kind.CALLBACK_ACTIVE
                    parsed == SessionStatus.LoggedOut -> Kind.CALLBACK_LOGGED_OUT
                    parsed != null -> Kind.CALLBACK_UNKNOWN
                    fallback -> Kind.TRADITIONAL
                    else -> Kind.UNRECOGNIZED
                }
            }
            return GatewayDiagnostic(protocol, kind, (reply as? ExchangeResult.Received)?.statusCode
                ?.takeIf { it in 100..599 })
        }
    }
}
