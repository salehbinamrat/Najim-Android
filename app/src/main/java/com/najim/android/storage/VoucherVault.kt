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

/** Not connected to login/UI yet. No automatic credential persistence. */
internal class VoucherVault(private val backend: VaultBackend, private val keys: VaultKeys) {
    fun list(profile: UUID): VaultResult<List<VaultRecord>> = synchronized(lock) {
        guarded { VaultResult.Ok(load().filter { it.profileId == profile }) }
    }

    fun put(record: VaultRecord): VaultResult<Unit> = synchronized(lock) {
        guarded {
            val old = load()
            val existing = old.firstOrNull { it.id == record.id }
            require(existing == null || existing.profileId == record.profileId)
            val next = old.filterNot { it.id == record.id } + record
            if (next.size > VaultLimits.MAX_RECORDS) VaultResult.Full
            else {
                persist(next)
                VaultResult.Ok(Unit)
            }
        }
    }

    fun remove(profile: UUID, id: UUID): VaultResult<Boolean> = synchronized(lock) {
        guarded {
            val old = load()
            val next = old.filterNot { it.profileId == profile && it.id == id }
            if (next.size == old.size) VaultResult.Ok(false)
            else {
                persist(next)
                VaultResult.Ok(true)
            }
        }
    }

    private fun load(): List<VaultRecord> {
        val envelope = backend.read() ?: return emptyList()
        // Never generate a replacement key over existing ciphertext.
        val key = keys.existing() ?: error("Vault key unavailable")
        val plain = VaultCipher.open(key, envelope)
        return try { VaultCodec.decode(plain) } finally { plain.fill(0) }
    }

    private fun persist(records: List<VaultRecord>) {
        val key = keys.existing() ?: keys.create()
        val plain = VaultCodec.encode(records)
        try { backend.replace(VaultCipher.seal(key, plain)) }
        finally { plain.fill(0) }
    }

    private inline fun <T> guarded(block: () -> VaultResult<T>): VaultResult<T> =
        try { block() } catch (_: Exception) { VaultResult.Unavailable }

    companion object {
        private val lock = Any()
    }
}
