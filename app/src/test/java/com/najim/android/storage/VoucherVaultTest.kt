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
    @Test fun profilesPersistWithoutCardsAndAreEncrypted() {
        val f = Fixture()
        val p = NetworkProfile(profile, "Private network", "10.0.0.1")
        assertTrue(f.vault.createProfile(p) is VaultResult.Ok)
        val loaded = (f.vault.profiles() as VaultResult.Ok).value.single()
        assertEquals(p.id, loaded.id)
        assertEquals(p.name, loaded.name)
        assertFalse(f.backend.bytes!!.toString(Charsets.UTF_8).contains(p.name))
        assertTrue(f.list().isEmpty())
    }
    @Test fun profilesCanShareGatewayButNotNameOrId() {
        val f = Fixture()
        assertTrue(f.vault.createProfile(NetworkProfile(profile, "One", "10.0.0.1")) is VaultResult.Ok)
        assertTrue(f.vault.createProfile(NetworkProfile(other, "Two", "10.0.0.1")) is VaultResult.Ok)
        assertEquals(VaultResult.Unavailable, f.vault.createProfile(NetworkProfile(UUID.randomUUID(), "one", "10.0.0.2")))
        assertEquals(VaultResult.Unavailable, f.vault.createProfile(NetworkProfile(profile, "Three", "10.0.0.2")))
    }
    @Test fun saveRequiresPersistedProfileAndDeduplicates() {
        val f = Fixture()
        val p = NetworkProfile(profile, "One", "10.0.0.1")
        val wifi = com.najim.android.network.DiscoveryState.Ready(7L, p.gateway)
        fun save(password: String) = requireNotNull(ConfirmedVoucherSave.authorize(p, wifi, wifi, "0012",
            password, LoginProtocol.HTTPS_PAP, com.najim.android.auth.GatewayLoginResult.MATCHING_SESSION, true))
        assertEquals(VaultResult.Unavailable, f.vault.saveConfirmed(save("first")))
        assertTrue(f.vault.createProfile(p) is VaultResult.Ok)
        assertTrue(f.vault.saveConfirmed(save("first")) is VaultResult.Ok)
        val id = f.list().single().id
        assertTrue(f.vault.saveConfirmed(save("second")) is VaultResult.Ok)
        assertEquals(id, f.list().single().id)
        assertEquals("second", f.list().single().password)
        assertTrue((f.vault.remove(profile, id) as VaultResult.Ok).value)
        assertEquals(profile, (f.vault.profiles() as VaultResult.Ok).value.single().id)
    }
    @Test fun profileGatewayCannotBeChangedByRecord() {
        val f = Fixture()
        f.vault.createProfile(NetworkProfile(profile, "One", "10.0.0.2"))
        assertEquals(VaultResult.Unavailable, f.vault.put(record()))
    }
    @Test fun legacyDocumentRemainsReadableAndCannotSilentlyAdoptOrphan() {
        val f = Fixture()
        val r = record()
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(1)
            out.writeUTF(r.id.toString())
            out.writeUTF(r.profileId.toString())
            out.writeUTF(r.gateway)
            out.writeUTF(r.protocol.name)
            out.writeUTF(r.username)
            out.writeBoolean(true)
            out.writeUTF(requireNotNull(r.password))
        }
        val key = f.keys.create()
        f.backend.replace(VaultCipher.seal(key, bytes.toByteArray()))
        assertEquals(r.id, f.list().single().id)
        assertTrue((f.vault.profiles() as VaultResult.Ok).value.isEmpty())
        assertEquals(VaultResult.Unavailable, f.vault.createProfile(NetworkProfile(profile, "One", r.gateway)))
        assertTrue(f.vault.createProfile(NetworkProfile(other, "Two", r.gateway)) is VaultResult.Ok)
        assertEquals(r.id, f.list().single().id)
    }
    @Test fun profileLimitDoesNotEvict() {
        val f = Fixture()
        repeat(VaultDocument.MAX_PROFILES) { index ->
            assertTrue(f.vault.createProfile(NetworkProfile(UUID.randomUUID(), "Network " + index, "10.0.0.1")) is VaultResult.Ok)
        }
        assertEquals(VaultResult.Full, f.vault.createProfile(NetworkProfile(UUID.randomUUID(), "Extra", "10.0.0.1")))
        assertEquals(VaultDocument.MAX_PROFILES, (f.vault.profiles() as VaultResult.Ok).value.size)
    }
    @Test fun userLimitIsFivePerProfile() {
        val f = Fixture()
        assertEquals(5, VaultLimits.MAX_RECORDS)
        repeat(5) { assertTrue(f.vault.put(record(profile, "100" + it)) is VaultResult.Ok) }
        repeat(5) { assertTrue(f.vault.put(record(other, "200" + it)) is VaultResult.Ok) }
        assertEquals(VaultResult.Full, f.vault.put(record(other, "9999")))
        assertEquals(5, f.list().size)
        assertEquals(5, f.list(other).size)
    }

    @Test fun existingCardCanBeUpdatedAtCapacity() {
        val f = Fixture()
        val first = record()
        assertTrue(f.vault.put(first) is VaultResult.Ok)
        repeat(4) { assertTrue(f.vault.put(record()) is VaultResult.Ok) }
        assertTrue(f.vault.put(VaultRecord(first.id, profile, first.gateway, first.protocol,
            first.username, "new-secret")) is VaultResult.Ok)
        assertEquals(5, f.list().size)
        assertEquals("new-secret", f.list().single { it.id == first.id }.password)
    }

    @Test fun preLimitVaultRemainsReadableWithoutSilentEviction() {
        val f = Fixture()
        val old = List(7) { record(user = "100" + it) }
        f.backend.replace(VaultCipher.seal(f.keys.create(), VaultCodec.encode(old)))
        val original = f.backend.bytes!!.copyOf()
        assertEquals(7, f.list().size)
        assertEquals(VaultResult.Full, f.vault.put(record(user = "9999")))
        assertArrayEquals(original, f.backend.bytes)
        assertTrue((f.vault.remove(profile, old[0].id) as VaultResult.Ok).value)
        assertTrue((f.vault.remove(profile, old[1].id) as VaultResult.Ok).value)
        assertEquals(5, f.list().size)
        assertEquals(VaultResult.Full, f.vault.put(record(user = "9999")))
        assertTrue((f.vault.remove(profile, old[2].id) as VaultResult.Ok).value)
        assertTrue(f.vault.put(record(user = "9999")) is VaultResult.Ok)
        assertEquals(5, f.list().size)
    }
    private fun confirmed(p: NetworkProfile, user: String, mode: LoginProtocol = LoginProtocol.HTTPS_PAP): ConfirmedVoucherSave {
        val wifi = com.najim.android.network.DiscoveryState.Ready(7L, p.gateway)
        val result = if (mode.requiresConsent) com.najim.android.auth.GatewayLoginResult.LEGACY_MATCHING_SESSION
            else com.najim.android.auth.GatewayLoginResult.MATCHING_SESSION
        return requireNotNull(ConfirmedVoucherSave.authorize(p, wifi, wifi, user, null, mode,
            result, true, mode.requiresConsent))
    }

    @Test fun automaticEvictsOldestOnlyWithinSelectedProfile() {
        val f = Fixture()
        val p = NetworkProfile(profile, "One", "10.0.0.1", RetentionMode.AUTO_OLDEST)
        val q = NetworkProfile(other, "Two", "10.0.0.1")
        f.vault.createProfile(p)
        f.vault.createProfile(q)
        repeat(5) {
            assertTrue(f.vault.saveConfirmed(confirmed(p, "100" + it)) is VaultResult.Ok)
            assertTrue(f.vault.saveConfirmed(confirmed(q, "200" + it)) is VaultResult.Ok)
        }
        assertTrue(f.vault.saveConfirmed(confirmed(p, "9999")) is VaultResult.Ok)
        assertEquals(listOf("1001", "1002", "1003", "1004", "9999"), f.list().map { it.username })
        assertEquals(List(5) { "200" + it }, f.list(other).map { it.username })
        assertEquals(VaultResult.Full, f.vault.saveConfirmed(confirmed(q, "8888")))
    }

    @Test fun policyChangePersistsWithoutEvictingAndRejectsStaleSave() {
        val f = Fixture()
        val p = NetworkProfile(profile, "One", "10.0.0.1")
        f.vault.createProfile(p)
        repeat(5) { f.vault.saveConfirmed(confirmed(p, "100" + it)) }
        assertTrue(f.vault.setRetention(p, RetentionMode.AUTO_OLDEST) is VaultResult.Ok)
        assertEquals(5, f.list().size)
        assertEquals(VaultResult.Unavailable, f.vault.saveConfirmed(confirmed(p, "9999")))
        val updated = (f.vault.profiles() as VaultResult.Ok).value.single()
        assertEquals(RetentionMode.AUTO_OLDEST, updated.retention)
        assertTrue(f.vault.saveConfirmed(confirmed(updated, "9999")) is VaultResult.Ok)
        assertTrue(f.vault.setRetention(updated, RetentionMode.MANUAL) is VaultResult.Ok)
        val manual = (f.vault.profiles() as VaultResult.Ok).value.single()
        assertEquals(VaultResult.Full, f.vault.saveConfirmed(confirmed(manual, "8888")))
    }

    @Test fun failedAutomaticWritePreservesAllOldCards() {
        val f = Fixture()
        val p = NetworkProfile(profile, "One", "10.0.0.1", RetentionMode.AUTO_OLDEST)
        f.vault.createProfile(p)
        repeat(5) { f.vault.saveConfirmed(confirmed(p, "100" + it)) }
        val before = f.backend.bytes!!.copyOf()
        f.backend.fail = true
        assertEquals(VaultResult.Unavailable, f.vault.saveConfirmed(confirmed(p, "9999")))
        assertArrayEquals(before, f.backend.bytes)
        assertEquals(List(5) { "100" + it }, f.list().map { it.username })
    }

    @Test fun repeatedAccountRefreshesOrderAndDoesNotDuplicateAcrossProtocols() {
        val f = Fixture()
        val p = NetworkProfile(profile, "One", "10.0.0.1", RetentionMode.AUTO_OLDEST)
        f.vault.createProfile(p)
        repeat(5) { f.vault.saveConfirmed(confirmed(p, "100" + it)) }
        val id = f.list().first().id
        assertTrue(f.vault.saveConfirmed(confirmed(p, "1000", LoginProtocol.HTTP_CHAP)) is VaultResult.Ok)
        assertEquals(id, f.list().last().id)
        assertEquals(LoginProtocol.HTTP_CHAP, f.list().last().protocol)
        f.vault.saveConfirmed(confirmed(p, "9999"))
        assertEquals(listOf("1002", "1003", "1004", "1000", "9999"), f.list().map { it.username })
    }

    @Test fun versionTwoDefaultsToManual() {
        val p = NetworkProfile(profile, "One", "10.0.0.1")
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(2)
            out.writeInt(1)
            out.writeUTF(p.id.toString())
            out.writeUTF(p.name)
            out.writeUTF(p.gateway)
            out.writeInt(0)
        }
        assertEquals(RetentionMode.MANUAL, VaultCodec.decodeDocument(bytes.toByteArray()).profiles.single().retention)
    }
    @Test fun allTwentyProfilesCanStoreFiveCardsEach() {
        val f = Fixture()
        repeat(VaultDocument.MAX_PROFILES) { index ->
            val p = NetworkProfile(UUID.randomUUID(), "Network " + index, "10.0.0.1")
            assertTrue(f.vault.createProfile(p) is VaultResult.Ok)
            repeat(5) { assertTrue(f.vault.saveConfirmed(confirmed(p, "100" + it)) is VaultResult.Ok) }
        }
        val catalog = (f.vault.catalog() as VaultResult.Ok).value
        assertEquals(100, catalog.cards.size)
        assertEquals(100, catalog.totalRecords)
        assertTrue(catalog.cards.groupBy { it.profileId }.values.all { it.size == 5 })
    }
    @Test fun legacyDuplicateAccountsRequireExplicitResolutionWithoutEviction() {
        val f = Fixture()
        val p = NetworkProfile(profile, "One", "10.0.0.1", RetentionMode.AUTO_OLDEST)
        val q = NetworkProfile(other, "Two", "10.0.0.1")
        f.vault.createProfile(p)
        f.vault.createProfile(q)
        val first = record(user = "0012")
        val duplicate = VaultRecord(UUID.randomUUID(), profile, p.gateway,
            LoginProtocol.HTTP_PAP, "0012", "other-password")
        assertTrue(f.vault.put(first) is VaultResult.Ok)
        assertTrue(f.vault.put(duplicate) is VaultResult.Ok)
        assertTrue(f.vault.put(record(other, "0012")) is VaultResult.Ok)
        val before = f.backend.bytes!!.copyOf()
        assertEquals(VaultResult.Unavailable, f.vault.saveConfirmed(confirmed(p, "0012")))
        assertArrayEquals(before, f.backend.bytes)
        assertEquals(2, f.list().size)
        assertEquals(1, f.list(other).size)
        assertTrue((f.vault.remove(profile, duplicate.id) as VaultResult.Ok).value)
        assertTrue(f.vault.saveConfirmed(confirmed(p, "0012")) is VaultResult.Ok)
        assertEquals(first.id, f.list().single().id)
        assertEquals(1, f.list(other).size)
    }

    @Test fun wipeableBufferRetainsReturnedCopyButClearsOwnedStorage() {
        val buffer = WipeableVaultBuffer()
        val bytes = ByteArray(5000) { 42 }
        buffer.write(bytes)
        val copy = buffer.toByteArray()
        buffer.wipe()
        assertEquals(0, buffer.size())
        assertArrayEquals(bytes, copy)
        buffer.write(7)
        assertArrayEquals(byteArrayOf(7), buffer.toByteArray())
    }

    @Test fun wipeableBufferRejectsGrowthBeyondPlaintextLimit() {
        val buffer = WipeableVaultBuffer()
        try {
            buffer.write(ByteArray(VaultLimits.MAX_PLAINTEXT))
            assertThrows(IllegalArgumentException::class.java) { buffer.write(1) }
            assertEquals(VaultLimits.MAX_PLAINTEXT, buffer.size())
        } finally { buffer.wipe() }
    }
}
