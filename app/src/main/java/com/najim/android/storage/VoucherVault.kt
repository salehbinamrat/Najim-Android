package com.najim.android.storage

import java.util.UUID
import javax.crypto.SecretKey

/** Backend must provide atomic replacement. All access is serialized by the vault owner. */
internal interface VaultBackend {
    fun read(): ByteArray?
    fun replace(envelope: ByteArray)
}

internal interface VaultKeys {
    fun existing(): SecretKey?
    fun create(): SecretKey
}

internal sealed interface VaultResult<out T> {
    class Ok<T>(val value: T) : VaultResult<T> {
        override fun toString() = "VaultResult.Ok(redacted)"
    }
    data object Unavailable : VaultResult<Nothing>
    data object Full : VaultResult<Nothing>
}

/** No automatic persistence. Production save path requires a confirmed-save authorization. */
internal class VoucherVault(private val backend: VaultBackend, private val keys: VaultKeys) {
    fun list(profile: UUID): VaultResult<List<VaultRecord>> = synchronized(lock) {
        guarded { VaultResult.Ok(load().records.filter { it.profileId == profile }) }
    }

    fun profiles(): VaultResult<List<NetworkProfile>> = synchronized(lock) {
        guarded { VaultResult.Ok(load().profiles) }
    }

    fun catalog(): VaultResult<VaultCatalog> = synchronized(lock) {
        guarded {
            val document = load()
            val ids = document.profiles.map { it.id }.toSet()
            VaultResult.Ok(VaultCatalog(document.profiles,
                document.records.filter { it.profileId in ids }.map(SavedVoucher::from), document.records.size))
        }
    }

    /** Read only the explicitly requested record, checking its persisted profile. */
    fun readSelected(profile: NetworkProfile, id: UUID): VaultResult<VaultRecord?> = synchronized(lock) {
        guarded {
            val document = load()
            val stored = document.profiles.singleOrNull { it.id == profile.id }
            if (stored == null || stored.name != profile.name || stored.gateway != profile.gateway)
                return@guarded VaultResult.Unavailable
            VaultResult.Ok(document.records.singleOrNull {
                it.id == id && it.profileId == profile.id && it.gateway == profile.gateway
            })
        }
    }

    fun createProfile(profile: NetworkProfile): VaultResult<Unit> = synchronized(lock) {
        guarded {
            val old = load()
            require(old.profiles.none { it.id == profile.id || it.name.equals(profile.name, ignoreCase = true) })
            // Do not silently adopt orphaned v1 records into a newly created profile.
            require(old.records.none { it.profileId == profile.id })
            if (old.profiles.size == VaultDocument.MAX_PROFILES) VaultResult.Full
            else {
                persist(VaultDocument(old.records, old.profiles + profile))
                VaultResult.Ok(Unit)
            }
        }
    }

    fun setRetention(profile: NetworkProfile, mode: RetentionMode): VaultResult<Unit> = synchronized(lock) {
        guarded {
            val old = load()
            val stored = old.profiles.singleOrNull { it.id == profile.id }
                ?: return@guarded VaultResult.Unavailable
            if (stored.name != profile.name || stored.gateway != profile.gateway ||
                stored.retention != profile.retention) return@guarded VaultResult.Unavailable
            val updated = NetworkProfile(stored.id, stored.name, stored.gateway, mode)
            persist(VaultDocument(old.records, old.profiles.map { if (it.id == stored.id) updated else it }))
            VaultResult.Ok(Unit)
        }
    }

    fun saveConfirmed(save: ConfirmedVoucherSave): VaultResult<Unit> = synchronized(lock) {
        guarded {
            val old = load()
            val profile = old.profiles.singleOrNull { it.id == save.record.profileId }
                ?: return@guarded VaultResult.Unavailable
            if (profile.gateway != save.record.gateway || profile.name != save.profileName ||
                profile.retention != save.retention)
                return@guarded VaultResult.Unavailable
            val duplicates = old.records.filter {
                it.profileId == profile.id && it.username == save.record.username
            }
            // Legacy versions could store the same account under different protocols.
            // Do not silently choose or delete one of those credentials.
            if (duplicates.size > 1) return@guarded VaultResult.Unavailable
            val duplicate = duplicates.singleOrNull()
            val record = if (duplicate == null) save.record else VaultRecord(duplicate.id, profile.id,
                profile.gateway, save.record.protocol, save.record.username, save.record.password)
            putIn(old, record, profile.retention)
        }
    }

    // Low-level API retained for storage migration/testing; UI must use saveConfirmed.
    fun put(record: VaultRecord): VaultResult<Unit> = synchronized(lock) {
        guarded {
            putIn(load(), record)
        }
    }

    private fun putIn(old: VaultDocument, record: VaultRecord,
                      mode: RetentionMode = RetentionMode.MANUAL): VaultResult<Unit> {
        val existing = old.records.firstOrNull { it.id == record.id }
        require(existing == null || existing.profileId == record.profileId)
        val remaining = old.records.filterNot { it.id == record.id }
        val sameProfile = remaining.filter { it.profileId == record.profileId }
        // The stored sequence is oldest to newest by last successful save. No clock dependency.
        val evicted = if (existing == null && sameProfile.size >= VaultLimits.MAX_RECORDS) {
            if (mode != RetentionMode.AUTO_OLDEST) return VaultResult.Full
            sameProfile.take(sameProfile.size - VaultLimits.MAX_RECORDS + 1).map { it.id }.toSet()
        } else emptySet()
        val next = remaining.filterNot { it.id in evicted } + record
        if (next.size > VaultLimits.MAX_DOCUMENT_RECORDS) return VaultResult.Full
        // Eviction and insertion are a single authenticated atomic replacement.
        persist(VaultDocument(next, old.profiles))
        return VaultResult.Ok(Unit)
    }

    fun remove(profile: UUID, id: UUID): VaultResult<Boolean> = synchronized(lock) {
        guarded {
            val old = load()
            val next = old.records.filterNot { it.profileId == profile && it.id == id }
            if (next.size == old.records.size) VaultResult.Ok(false)
            else {
                persist(VaultDocument(next, old.profiles))
                VaultResult.Ok(true)
            }
        }
    }

    private fun load(): VaultDocument {
        val envelope = backend.read() ?: return VaultDocument()
        // Never generate a replacement key over existing ciphertext.
        val key = keys.existing() ?: error("Vault key unavailable")
        val plain = VaultCipher.open(key, envelope)
        return try { VaultCodec.decodeDocument(plain) } finally { plain.fill(0) }
    }

    private fun persist(document: VaultDocument) {
        val key = keys.existing() ?: keys.create()
        val plain = VaultCodec.encodeDocument(document)
        try { backend.replace(VaultCipher.seal(key, plain)) }
        finally { plain.fill(0) }
    }

    private inline fun <T> guarded(block: () -> VaultResult<T>): VaultResult<T> =
        try { block() } catch (_: Exception) { VaultResult.Unavailable }

    companion object {
        private val lock = Any()
    }
}
