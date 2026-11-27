package com.najim.android.auth

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class PortalTextTest {
    @Test fun malformedUtf8IsRejectedRatherThanReplaced() {
        for (bad in listOf(byteArrayOf(0xC0.toByte(), 0xAF.toByte()),
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()),
            byteArrayOf(0xE2.toByte(), 0x82.toByte()))) {
            assertNull(PortalText.decode(bad))
        }
        assertEquals("رقم الكرت", PortalText.decode("رقم الكرت".toByteArray()))
        assertNull(PortalText.decode(ByteArray(GatewayTransport.MAX_RESPONSE_BYTES + 1)))
    }

    @Test fun malformedHtmlCannotBecomeActiveOrLoggedOutEvidence() {
        val origin = URI("http://10.0.0.1/")
        val bad = byteArrayOf(0xC0.toByte(), 0xAF.toByte())
        val status = "<h1>Welcome, 0012!</h1><a href='/logout'>Logout</a>".toByteArray() + bad
        assertEquals(PortalPage.Unknown, PortalParser.parse(origin, origin.resolve("/status"), 200, status))
        val redirect = "<meta http-equiv='refresh' content='0;url=/login'>".toByteArray() + bad
        assertFalse(SessionRedirect.loggedOut(origin, ExchangeResult.Received(200, redirect)))
        val login = "<form name='login' action='/login' method='post'><input name='username'><p class='error'>invalid password</p></form>".toByteArray() + bad
        assertNull(RouterErrorParser.parse(origin, ExchangeResult.Received(200, login)))
    }

    @Test fun malformedCallbackCannotTriggerTraditionalFallback() {
        val response = ExchangeResult.Received(200, "<html>".toByteArray() + byteArrayOf(0xFF.toByte()))
        assertNull(NajmCallback.status(response, true))
        assertFalse(NajmCallback.allowsTraditionalFallback(response))
    }
}
