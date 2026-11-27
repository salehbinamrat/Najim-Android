package com.najim.android.storage

import com.najim.android.auth.LoginProtocol
import java.io.IOException
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

class VoucherVaultTest {
    private val profile = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private fun record(profileId: UUID = profile, user: String = "001234", password: String? = "secret-42") =
        VaultRecord(UUID.randomUUID(), profileId, "10.0.0.1", LoginProtocol.HTTPS_PAP, user, password)
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private class Backend : VaultBackend {
        var bytes: ByteArray? = null
        var fail = false
        override fun read() = bytes?.copyOf()
        override fun replace(envelope: ByteArray) {
            if (fail) throw IOException("write failed")
            bytes = envelope.copyOf()
        }
    }
    private inner class Keys : VaultKeys {
        var stored: SecretKey? = null
        var created = 0
        override fun existing() = stored
        override fun create(): SecretKey {
            created++
            return key().also { stored = it }
        }
    }
    private inner class Fixture {
        val backend = Backend()
        val keys = Keys()
        val vault = VoucherVault(backend, keys)
        fun list(profileId: UUID = profile) =
            (vault.list(profileId) as VaultResult.Ok).value
    }

    @Test fun emptyVaultDoesNotGenerateKey() {
        val f = Fixture()
        assertTrue(f.list().isEmpty())
        assertEquals(0, f.keys.created)
    }
    @Test fun roundTripPreservesExactCredentialsAndProfile() {
        val f = Fixture()
        val record = record()
        assertTrue(f.vault.put(record) is VaultResult.Ok)
        val loaded = f.list().single()
        assertEquals(record.id, loaded.id)
        assertEquals(record.profileId, loaded.profileId)
        assertEquals("001234", loaded.username)
        assertEquals("secret-42", loaded.password)
        assertEquals(1, f.keys.created)
        assertFalse(f.backend.bytes!!.toString(Charsets.UTF_8).contains("secret-42"))
    }
    @Test fun sameGatewayDoesNotMixProfiles() {
        val f = Fixture()
        f.vault.put(record())
        f.vault.put(record(other, "009999"))
        assertEquals("001234", f.list().single().username)
        assertEquals("009999", f.list(other).single().username)
    }
    @Test fun usernameOnlyPreservesNullPassword() {
        val f = Fixture()
        f.vault.put(record(password = null))
        assertNull(f.list().single().password)
    }
    @Test fun updateDoesNotDuplicateId() {
        val f = Fixture()
        val r = record()
        f.vault.put(r)
        f.vault.put(VaultRecord(r.id, profile, r.gateway, r.protocol, r.username, "changed"))
        assertEquals("changed", f.list().single().password)
    }
    @Test fun recordCannotMoveAcrossProfiles() {
        val f = Fixture()
        val r = record()
        f.vault.put(r)
        assertEquals(VaultResult.Unavailable, f.vault.put(VaultRecord(r.id, other, r.gateway, r.protocol, r.username, null)))
        assertEquals(1, f.list().size)
        assertTrue(f.list(other).isEmpty())
    }
    @Test fun deleteScopedToProfile() {
        val f = Fixture()
        val r = record()
        f.vault.put(r)
        assertFalse((f.vault.remove(other, r.id) as VaultResult.Ok).value)
        assertTrue((f.vault.remove(profile, r.id) as VaultResult.Ok).value)
        assertTrue(f.list().isEmpty())
    }
    @Test fun failedWritePreservesPreviousRecord() {
        val f = Fixture()
        f.vault.put(record())
        f.backend.fail = true
        assertEquals(VaultResult.Unavailable, f.vault.put(record(user = "1111")))
        assertEquals("001234", f.list().single().username)
    }
    @Test fun missingKeyNeverReplacesCiphertext() {
        val f = Fixture()
        f.vault.put(record())
        val before = f.backend.bytes!!.copyOf()
        f.keys.stored = null
        assertEquals(VaultResult.Unavailable, f.vault.list(profile))
        assertEquals(VaultResult.Unavailable, f.vault.put(record()))
        assertEquals(1, f.keys.created)
        assertArrayEquals(before, f.backend.bytes)
    }
    @Test fun modifiedCiphertextFailsClosed() {
        val f = Fixture()
        f.vault.put(record())
        val bytes = f.backend.bytes!!
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val before = bytes.copyOf()
        assertEquals(VaultResult.Unavailable, f.vault.list(profile))
        assertEquals(VaultResult.Unavailable, f.vault.remove(profile, UUID.randomUUID()))
        assertArrayEquals(before, f.backend.bytes)
    }
    @Test fun wrongKeyFailsClosed() {
        val f = Fixture()
        f.vault.put(record())
        f.keys.stored = key()
        assertEquals(VaultResult.Unavailable, f.vault.list(profile))
    }
    @Test fun freshIvForIdenticalPlaintext() {
        val key = key()
        val plain = "test".toByteArray()
        val a = VaultCipher.seal(key, plain)
        val b = VaultCipher.seal(key, plain)
        assertFalse(a.copyOfRange(1, 13).contentEquals(b.copyOfRange(1, 13)))
        assertArrayEquals(plain, VaultCipher.open(key, a))
    }
    @Test fun unknownEnvelopeVersionRejected() {
        val f = Fixture()
        f.vault.put(record())
        f.backend.bytes!![0] = 99
        assertEquals(VaultResult.Unavailable, f.vault.list(profile))
    }
    @Test fun sizeLimitDoesNotEvictExistingCards() {
        val f = Fixture()
        repeat(VaultLimits.MAX_RECORDS) { assertTrue(f.vault.put(record()) is VaultResult.Ok) }
        assertEquals(VaultResult.Full, f.vault.put(record()))
        assertEquals(VaultLimits.MAX_RECORDS, f.list().size)
    }
    @Test fun invalidRecordRejected() {
        assertThrows(IllegalArgumentException::class.java) { record(user = "12 34") }
        assertThrows(IllegalArgumentException::class.java) { record(user = "١٢٣٤") }
        assertThrows(IllegalArgumentException::class.java) { record(password = "bad\n") }
    }
    @Test fun codecRejectsTrailingBytesAndDuplicateIds() {
        val r = record()
        assertThrows(IllegalArgumentException::class.java) { VaultCodec.encode(listOf(r, r)) }
        assertThrows(IllegalArgumentException::class.java) { VaultCodec.decode(VaultCodec.encode(listOf(r)) + byteArrayOf(0)) }
    }
    @Test fun redactedRepresentations() {
        assertEquals("VaultRecord(redacted)", record().toString())
        assertEquals("VaultResult.Ok(redacted)", VaultResult.Ok(record()).toString())
    }
}
