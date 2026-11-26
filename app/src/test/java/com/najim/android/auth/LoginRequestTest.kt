package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import org.junit.Assert.*
import org.junit.Test

class LoginRequestTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.3.1")

    private fun prepare(
        username: String = "٠٠Ab",
        password: String? = null,
        protocol: LoginProtocol = LoginProtocol.HTTPS_PAP,
        consent: Boolean = false,
        challenge: ChapChallenge? = null,
        selected: DiscoveryState.Ready = wifi,
        current: DiscoveryState = wifi
    ) = LoginRequest.prepare(selected, current, username, password, protocol, consent, challenge)

    @Test fun usernameOnly() {
        val request = prepare() as Preparation.Ready
        assertEquals("https://10.0.3.1/login", request.endpoint.toString())
        assertEquals(7L, request.networkId)
        assertEquals("username=00Ab&password=", request.formBody)
    }

    @Test fun separatePasswordEncodedWithoutNormalization() {
        val request = prepare("A&B=1", " x+١&=") as Preparation.Ready
        assertEquals("username=A%26B%3D1&password=+x%2B%D9%A1%26%3D", request.formBody)
    }

    @Test fun missingOrChangedNetworkRejected() {
        for (current in listOf(DiscoveryState.NoWifi, DiscoveryState.Ready(8L, wifi.gateway),
            DiscoveryState.Ready(7L, "10.0.4.1"))) {
            assertEquals(Preparation.Rejected(PreparationError.NETWORK_CHANGED), prepare(current = current))
        }
    }

    @Test fun unsafeGatewayRejected() {
        for (host in listOf("10.0.0.1@evil.test", "10.0.0.1:443", "evil.test",
            "127.0.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "010.0.0.1", "10.0.0.256")) {
            val network = DiscoveryState.Ready(7L, host)
            assertEquals(Preparation.Rejected(PreparationError.INVALID_GATEWAY),
                prepare(selected = network, current = network))
        }
    }

    @Test fun insecureProtocolsRequireConsent() {
        for (protocol in listOf(LoginProtocol.HTTP_PAP, LoginProtocol.HTTP_CHAP)) {
            assertEquals(Preparation.Rejected(PreparationError.INSECURE_HTTP_NOT_APPROVED),
                prepare(protocol = protocol))
        }
    }

    @Test fun approvedHttpPap() {
        val request = prepare(password = "pass", protocol = LoginProtocol.HTTP_PAP, consent = true) as Preparation.Ready
        assertEquals("http://10.0.3.1/login", request.endpoint.toString())
        assertEquals("username=00Ab&password=pass", request.formBody)
        assertFalse(request.toString().contains("pass"))
    }

    @Test fun invalidUsernameRejected() {
        assertEquals(Preparation.Rejected(PreparationError.INVALID_USERNAME), prepare(username = " "))
    }

    @Test fun invalidPasswordRejected() {
        assertEquals(Preparation.Rejected(PreparationError.INVALID_PASSWORD), prepare(password = "a\nb"))
        assertEquals(Preparation.Rejected(PreparationError.INVALID_PASSWORD), prepare(password = "a".repeat(129)))
    }

    @Test fun chapMatchesIndependentMd5Vector() {
        val bytes = ByteArray(16) { it.toByte() }
        val challenge = ChapChallenge(128, bytes)
        bytes.fill(0)
        val request = prepare(password = "1234", protocol = LoginProtocol.HTTP_CHAP,
            consent = true, challenge = challenge) as Preparation.Ready
        assertEquals("username=00Ab&password=d7a567cb9ba72549c682a6d9857c9b77", request.formBody)
    }

    @Test fun invalidChapRejectedWithoutPapFallback() {
        for (challenge in listOf(null, ChapChallenge(256, ByteArray(16)), ChapChallenge(1, ByteArray(1)))) {
            assertEquals(Preparation.Rejected(PreparationError.INVALID_CHALLENGE),
                prepare(protocol = LoginProtocol.HTTP_CHAP, consent = true, challenge = challenge))
        }
    }

    @Test fun chapDoesNotSilentlyAlterUnicodePasswords() {
        assertEquals(Preparation.Rejected(PreparationError.INVALID_PASSWORD),
            prepare(password = "١٢", protocol = LoginProtocol.HTTP_CHAP, consent = true,
                challenge = ChapChallenge(1, ByteArray(16))))
    }

    @Test fun challengeCannotBeDowngradedToPap() {
        assertEquals(Preparation.Rejected(PreparationError.INVALID_CHALLENGE),
            prepare(challenge = ChapChallenge(1, ByteArray(16))))
    }

    @Test fun http200DoesNotProveLogin() {
        assertEquals(LoginOutcome.PendingSessionVerification, LoginResponsePolicy.classify(200))
    }

    @Test fun redirectsAreNotProofOrPermissionToFollow() {
        assertEquals(LoginOutcome.RedirectRequiresVerification, LoginResponsePolicy.classify(302))
    }

    @Test fun statusCodeAloneDoesNotMeanWrongPassword() {
        assertEquals(LoginOutcome.HttpFailure(403), LoginResponsePolicy.classify(403))
    }

    @Test fun connectionFailureIsNotVoucherFailure() {
        assertEquals(LoginOutcome.TransportFailure, LoginResponsePolicy.classify(null))
    }
}
