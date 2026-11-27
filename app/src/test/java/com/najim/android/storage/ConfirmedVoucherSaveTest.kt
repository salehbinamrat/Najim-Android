package com.najim.android.storage

import com.najim.android.auth.GatewayLoginResult
import com.najim.android.auth.LoginProtocol
import com.najim.android.network.DiscoveryState
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ConfirmedVoucherSaveTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private val profile = NetworkProfile(UUID.randomUUID(), "شبكة نجم", wifi.gateway)
    private fun authorize(result: GatewayLoginResult = GatewayLoginResult.MATCHING_SESSION,
                          current: DiscoveryState = wifi, approved: Boolean = true,
                          mode: LoginProtocol = LoginProtocol.HTTPS_PAP, legacy: Boolean = false,
                          chosen: NetworkProfile = profile, username: String = "٠٠١٢") =
        ConfirmedVoucherSave.authorize(chosen, wifi, current, username, null, mode, result, approved, legacy)

    @Test fun matchingApprovedHttpsPreservesNormalizedVoucher() {
        val save = requireNotNull(authorize())
        assertEquals("0012", save.record.username)
        assertEquals(profile.id, save.record.profileId)
        assertEquals(profile.name, save.profileName)
        assertEquals("ConfirmedVoucherSave(redacted)", save.toString())
    }
    @Test fun approvalRequired() = assertNull(authorize(approved = false))
    @Test fun onlyMatchingResultEligible() {
        for (result in GatewayLoginResult.entries.filter { it != GatewayLoginResult.MATCHING_SESSION })
            assertNull(authorize(result = result))
    }
    @Test fun changedNetworkEvenWithSameGatewayRejected() =
        assertNull(authorize(current = wifi.copy(networkId = 8)))
    @Test fun missingNetworkRejected() = assertNull(authorize(current = DiscoveryState.NoWifi))
    @Test fun gatewayMismatchRejected() =
        assertNull(authorize(chosen = NetworkProfile(UUID.randomUUID(), "Other", "10.0.0.2")))
    @Test fun legacyRequiresIndependentAcknowledgement() {
        assertNull(authorize(result = GatewayLoginResult.LEGACY_MATCHING_SESSION, mode = LoginProtocol.HTTP_CHAP))
        assertNotNull(authorize(result = GatewayLoginResult.LEGACY_MATCHING_SESSION, mode = LoginProtocol.HTTP_CHAP, legacy = true))
    }
    @Test fun mismatchedEvidenceAndProtocolRejected() =
        assertNull(authorize(mode = LoginProtocol.HTTP_PAP, legacy = true))
    @Test fun invalidCredentialsRejected() = assertNull(authorize(username = "12 34"))
    @Test fun profileValidationRejectsInvalidNamesAndGateway() {
        for (name in listOf("", " ", " trailing ", "a\n", "a\u200B", "x".repeat(65)))
            assertThrows(IllegalArgumentException::class.java) { NetworkProfile(UUID.randomUUID(), name, wifi.gateway) }
        assertThrows(IllegalArgumentException::class.java) { NetworkProfile(UUID.randomUUID(), "Name", "example.test") }
    }
}
