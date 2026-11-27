package com.najim.android.storage

import com.najim.android.auth.GatewayLoginResult
import com.najim.android.auth.LoginProtocol
import com.najim.android.auth.LoginRequest
import com.najim.android.domain.VoucherInput
import com.najim.android.network.DiscoveryState
import java.util.UUID

/** Created only from the same explicitly approved attempt and its matching session result. */
internal class ConfirmedVoucherSave private constructor(val record: VaultRecord, val profileName: String,
                                                        val retention: RetentionMode) {
    override fun toString() = "ConfirmedVoucherSave(redacted)"

    companion object {
        fun authorize(profile: NetworkProfile, selected: DiscoveryState.Ready,
                      current: DiscoveryState, username: String, password: String?,
                      protocol: LoginProtocol, result: GatewayLoginResult,
                      saveApproved: Boolean, legacyEvidenceApproved: Boolean = false): ConfirmedVoucherSave? {
            if (!saveApproved || selected != current || profile.gateway != selected.gateway) return null
            val matches = when (protocol) {
                LoginProtocol.HTTPS_PAP -> result == GatewayLoginResult.MATCHING_SESSION
                LoginProtocol.HTTP_PAP, LoginProtocol.HTTP_CHAP ->
                    legacyEvidenceApproved && result == GatewayLoginResult.LEGACY_MATCHING_SESSION
            }
            if (!matches || LoginRequest.validateInput(selected, current, username, password, protocol,
                    legacyEvidenceApproved) != null) return null
            return ConfirmedVoucherSave(VaultRecord(UUID.randomUUID(), profile.id, profile.gateway,
                protocol, VoucherInput.normalize(username), password), profile.name, profile.retention)
        }
    }
}
