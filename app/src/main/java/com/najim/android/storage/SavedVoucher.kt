package com.najim.android.storage

import com.najim.android.auth.LoginProtocol
import com.najim.android.auth.SessionDisplay
import java.util.UUID

/** Presentation model deliberately excludes raw username and password. */
internal class SavedVoucher(
    val id: UUID, val profileId: UUID, val maskedAccount: String,
    val protocol: LoginProtocol
) {
    override fun toString() = "SavedVoucher(redacted)"

    companion object {
        fun from(record: VaultRecord) = SavedVoucher(record.id, record.profileId,
            SessionDisplay.maskAccount(record.username), record.protocol)
    }
}

internal class VaultCatalog(val profiles: List<NetworkProfile>, val cards: List<SavedVoucher>, val totalRecords: Int) {
    override fun toString() = "VaultCatalog(redacted)"
}
