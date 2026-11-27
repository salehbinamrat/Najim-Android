package com.najim.android.storage

import com.najim.android.auth.LoginProtocol
import com.najim.android.auth.LoginRequest
import com.najim.android.network.DiscoveryState
import java.util.UUID

/** Explicit profile identity, not an ephemeral Android network handle or a gateway alone. */
internal class VaultRecord(
    val id: UUID,
    val profileId: UUID,
    val gateway: String,
    val protocol: LoginProtocol,
    val username: String,
    val password: String?
) {
    init {
        require(LoginRequest.validateInput(DiscoveryState.Ready(0, gateway),
            DiscoveryState.Ready(0, gateway), username, password, protocol, true) == null)
        require(com.najim.android.domain.VoucherInput.normalize(username) == username)
    }

    override fun toString() = "VaultRecord(redacted)"
}

internal object VaultLimits {
    const val MAX_RECORDS = 5
    // Read existing files without discarding cards saved by pre-limit builds.
    const val MAX_LEGACY_RECORDS = 20
    // Up to 100 active records plus at most 20 retained pre-release records.
    const val MAX_DOCUMENT_RECORDS = 120
    const val MAX_PLAINTEXT = 256 * 1024
    const val MAX_ENVELOPE = MAX_PLAINTEXT + 64
}
