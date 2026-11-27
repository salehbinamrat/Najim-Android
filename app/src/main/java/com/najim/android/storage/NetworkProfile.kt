package com.najim.android.storage

import com.najim.android.auth.LoginRequest
import java.util.UUID

/** A user-selected label and gateway, not a cryptographic Wi-Fi identity. */
internal class NetworkProfile(val id: UUID, val name: String, val gateway: String) {
    init {
        require(name.length in 1..64 && name == name.trim())
        require(name.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() })
        require(LoginRequest.isGateway(gateway))
    }

    override fun toString() = "NetworkProfile(redacted)"
}

internal class VaultDocument(
    val records: List<VaultRecord> = emptyList(),
    val profiles: List<NetworkProfile> = emptyList()
) {
    init {
        require(records.size <= VaultLimits.MAX_RECORDS)
        require(profiles.size <= MAX_PROFILES)
        require(records.map { it.id }.distinct().size == records.size)
        require(profiles.map { it.id }.distinct().size == profiles.size)
        require(profiles.map { it.name.lowercase() }.distinct().size == profiles.size)
        val byId = profiles.associateBy { it.id }
        require(records.all { record ->
            byId[record.profileId]?.let { it.gateway == record.gateway } ?: true
        })
    }

    override fun toString() = "VaultDocument(redacted)"

    companion object {
        const val MAX_PROFILES = 20
    }
}
