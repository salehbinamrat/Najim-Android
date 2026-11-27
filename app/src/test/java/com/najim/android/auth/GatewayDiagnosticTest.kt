package com.najim.android.auth

import org.junit.Assert.*
import org.junit.Test

class GatewayDiagnosticTest {
    @Test fun reportsOnlyHttpCodeAndClassificationNeverRawResponse() {
        val response = ExchangeResult.Received(200, "secret-card".toByteArray(), "http://private.test")
        val report = GatewayDiagnostic.from(LoginProtocol.HTTP_PAP, response, null, false)
        assertEquals(GatewayDiagnostic.Kind.UNRECOGNIZED, report.kind)
        assertEquals(200, report.httpCode)
        assertFalse(report.toString().contains("secret-card"))
        assertFalse(report.toString().contains("private.test"))
    }
    @Test fun classifiesFailuresAndTraditionalFallbackWithoutGuessing() {
        assertEquals(GatewayDiagnostic.Kind.NO_RESPONSE,
            GatewayDiagnostic.from(LoginProtocol.HTTP_PAP, ExchangeResult.TransportFailure, null, false).kind)
        assertEquals(GatewayDiagnostic.Kind.TOO_LARGE,
            GatewayDiagnostic.from(LoginProtocol.HTTP_PAP, ExchangeResult.ResponseTooLarge, null, false).kind)
        assertEquals(GatewayDiagnostic.Kind.TRADITIONAL,
            GatewayDiagnostic.from(LoginProtocol.HTTP_PAP, ExchangeResult.Received(302, byteArrayOf()), null, true).kind)
        assertEquals(GatewayDiagnostic.Kind.CALLBACK_UNKNOWN,
            GatewayDiagnostic.from(LoginProtocol.HTTP_PAP, ExchangeResult.Received(200, byteArrayOf()), SessionStatus.Unknown, false).kind)
    }
}
